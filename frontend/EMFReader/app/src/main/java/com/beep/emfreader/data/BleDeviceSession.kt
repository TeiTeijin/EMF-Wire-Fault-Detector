package com.beep.emfreader.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * A BLE GATT session to the relay's Nordic UART Service.
 *
 * Flow: connect (10 s) -> MTU exchange -> discover services -> subscribe
 * to TX notifications. Subscribing touches the CCCD descriptor, which the
 * relay only accepts on an encrypted MITM link, so Android shows the
 * system type-in dialog for the pairing-window code automatically.
 *
 * Failure mapping:
 * - no GATT connection within 10 s              -> REFUSED
 * - pairing cancelled / wrong code (bond broke) -> PAIRING_FAILED
 * - link up but no status line within 6 s       -> SILENT
 * - link lost after data flowed                 -> LinkLost
 */
class BleDeviceSession(
    private val context: Context,
    private val device: EmfDevice
) : DeviceSession {

    private val lineBuffer = LineBuffer()

    private val lines = AtomicInteger()
    private val dropped = AtomicInteger()

    @Volatile
    private var gatt: BluetoothGatt? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun events(): Flow<SessionEvent> = channelFlow {
        val scope = this
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        if (manager == null) {
            send(SessionEvent.Failed(ConnectFailure.OUT_OF_RANGE))
            return@channelFlow
        }
        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) {
            send(SessionEvent.Failed(ConnectFailure.OUT_OF_RANGE))
            return@channelFlow
        }
        val remote = try {
            adapter.getRemoteDevice(device.address)
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "not a valid address: ${device.address}")
            send(SessionEvent.Failed(ConnectFailure.REFUSED))
            return@channelFlow
        }

        val callbacks = GattCallbacks(scope)
        val bondBroken = CompletableDeferred<Unit>()
        val bondReceiver = registerBondReceiver(remote, bondBroken)
        try {
            @SuppressLint("MissingPermission")
            val candidate = remote.connectGatt(
                context, false, callbacks, BluetoothDevice.TRANSPORT_LE
            )
            if (candidate == null) {
                send(SessionEvent.Failed(ConnectFailure.REFUSED))
                return@channelFlow
            }
            gatt = candidate

            // 1. Connection (10 s).
            val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { callbacks.connected.await() }
            if (connected == null) {
                send(
                    SessionEvent.Failed(
                        if (bondBroken.isCompleted) ConnectFailure.PAIRING_FAILED
                        else ConnectFailure.REFUSED
                    )
                )
                return@channelFlow
            }
            Log.i(TAG, "connected to ${device.name} (${device.address})")

            // 2. MTU exchange so a full status line fits one notification.
            @SuppressLint("MissingPermission")
            candidate.requestMtu(MTU_BYTES)
            withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { callbacks.mtuChanged.await() }

            // 3. Services.
            @SuppressLint("MissingPermission")
            candidate.discoverServices()
            val servicesOk = withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
                callbacks.servicesDiscovered.await()
            }
            if (servicesOk != true) {
                send(
                    SessionEvent.Failed(
                        if (bondBroken.isCompleted) ConnectFailure.PAIRING_FAILED
                        else ConnectFailure.REFUSED
                    )
                )
                return@channelFlow
            }

            // 4. Subscribe to TX; this access triggers the system passkey
            //    dialog on an unpaired phone.
            val tx = findTx(candidate) ?: run {
                Log.w(TAG, "${device.name} does not expose the EMF UART service")
                send(SessionEvent.Failed(ConnectFailure.REFUSED))
                return@channelFlow
            }
            if (!subscribe(candidate, tx)) {
                send(
                    SessionEvent.Failed(
                        if (bondBroken.isCompleted) ConnectFailure.PAIRING_FAILED
                        else ConnectFailure.REFUSED
                    )
                )
                return@channelFlow
            }

            Log.i(TAG, "subscribed to TX; waiting for the first status line")
            send(SessionEvent.Connected)

            // 5. First line within 6 s; watch for a cancelled/wrong code
            //    and for the link dropping during setup.
            val outcome = select<SetupOutcome> {
                callbacks.firstLine.onAwait { SetupOutcome.FIRST_LINE }
                bondBroken.onAwait { SetupOutcome.PAIRING_BROKEN }
                callbacks.linkLost.onAwait { SetupOutcome.LINK_GONE }
                onTimeout(EmfContract.FIRST_LINE_TIMEOUT_MILLIS) { SetupOutcome.SILENT }
            }
            if (outcome != SetupOutcome.FIRST_LINE) {
                // A disconnect during setup often races the bond-failure
                // broadcast (Android drops the link AND reports BOND_NONE
                // when a pairing is cancelled or the code was wrong). Give
                // the broadcast a moment to land so the honest reason wins.
                val pairingBroken =
                    if (outcome == SetupOutcome.LINK_GONE) {
                        withTimeoutOrNull(PAIRING_GRACE_MILLIS) { bondBroken.await() } != null
                    } else {
                        outcome == SetupOutcome.PAIRING_BROKEN
                    }
                Log.i(
                    TAG,
                    "no live session for ${device.name}: lines=${lines.get()} " +
                        "dropped=${dropped.get()} outcome=$outcome pairingBroken=$pairingBroken"
                )
                send(
                    SessionEvent.Failed(
                        if (pairingBroken) ConnectFailure.PAIRING_FAILED
                        else ConnectFailure.SILENT
                    )
                )
                return@channelFlow
            }

            // 6. Live: forward lines until the link drops.
            callbacks.linkLost.await()
            Log.i(
                TAG,
                "link to ${device.name} lost: lines=${lines.get()} dropped=${dropped.get()}"
            )
            send(SessionEvent.LinkLost)
        } finally {
            context.unregisterReceiver(bondReceiver)
            close()
        }
    }.flowOn(Dispatchers.IO)

    private fun registerBondReceiver(
        remote: BluetoothDevice,
        bondBroken: CompletableDeferred<Unit>
    ): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                val target = parcelableDevice(intent) ?: return
                if (target.address != remote.address) return
                val newState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE
                )
                val prevState = intent.getIntExtra(
                    BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE
                )
                if (prevState == BluetoothDevice.BOND_BONDING &&
                    newState == BluetoothDevice.BOND_NONE
                ) {
                    Log.w(TAG, "pairing with ${device.name} failed or was cancelled")
                    bondBroken.complete(Unit)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        return receiver
    }

    @Suppress("DEPRECATION")
    private fun parcelableDevice(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun findTx(target: BluetoothGatt): BluetoothGattCharacteristic? {
        val service = target.getService(EmfContract.NUS_SERVICE_UUID) ?: return null
        return service.getCharacteristic(EmfContract.NUS_TX_UUID)
    }

    @SuppressLint("MissingPermission")
    private fun subscribe(target: BluetoothGatt, tx: BluetoothGattCharacteristic): Boolean {
        target.setCharacteristicNotification(tx, true)
        val descriptor = tx.getDescriptor(EmfContract.CCCD_UUID) ?: run {
            Log.w(TAG, "TX characteristic has no CCCD descriptor")
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            target.writeDescriptor(
                descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            ) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            target.writeDescriptor(descriptor)
        }
    }

    override fun close() {
        val target = gatt
        gatt = null
        try {
            @SuppressLint("MissingPermission")
            target?.disconnect()
        } catch (_: SecurityException) {
        }
        try {
            target?.close()
        } catch (_: Exception) {
        }
    }

    private inner class GattCallbacks(
        private val scope: kotlinx.coroutines.channels.ProducerScope<SessionEvent>
    ) : BluetoothGattCallback() {
        val connected = CompletableDeferred<Unit>()
        val mtuChanged = CompletableDeferred<Unit>()
        val servicesDiscovered = CompletableDeferred<Boolean>()
        val firstLine = CompletableDeferred<Unit>()
        val linkLost = CompletableDeferred<Unit>()

        override fun onConnectionStateChange(target: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "link up to ${device.name}")
                    connected.complete(Unit)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "link down from ${device.name} (status=$status)")
                    if (connected.isCompleted) linkLost.complete(Unit)
                }
            }
        }

        override fun onMtuChanged(target: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "MTU negotiated: $mtu (status=$status)")
            mtuChanged.complete(Unit)
        }

        override fun onServicesDiscovered(target: BluetoothGatt, status: Int) {
            servicesDiscovered.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            target: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            lineBuffer.feed(value, value.size) { line ->
                val readings = EmfContract.parseLine(line)
                if (readings == null) {
                    if (dropped.get() < 2) Log.d(TAG, "skipped: '$line'")
                    dropped.incrementAndGet()
                } else {
                    if (lines.get() == 0) Log.i(TAG, "first line: $line")
                    lines.incrementAndGet()
                    scope.trySend(SessionEvent.Reading(readings))
                    firstLine.complete(Unit)
                }
            }
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onDescriptorWrite(
            target: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(TAG, "CCCD write failed with status=$status")
                if (connected.isCompleted && !firstLine.isCompleted) {
                    linkLost.complete(Unit)
                }
            }
        }
    }

    private enum class SetupOutcome {
        FIRST_LINE,
        PAIRING_BROKEN,
        LINK_GONE,
        SILENT
    }

    private companion object {
        const val TAG = "EmfBleSession"

        const val CONNECT_TIMEOUT_MILLIS = 10_000L
        const val MTU_BYTES = 185

        // How long a setup-phase disconnect waits for the bond-failure
        // broadcast before settling for SILENT.
        const val PAIRING_GRACE_MILLIS = 300L
    }
}
