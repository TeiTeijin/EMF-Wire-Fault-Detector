package com.beep.emfreader.data

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import java.util.UUID

/**
 * Finds the paired sensors this app can connect to.
 *
 * Classic Bluetooth has no scan: the ESP32 is only reachable once the phone has
 * bonded with it, so the list comes from the adapter's bond table rather than
 * from the air. That also means pairing happens outside the app, in system
 * Settings, which is why [EmfSppContract.RELAY_NAME] is matched by name as well
 * as by service UUID.
 */
object PairedDeviceLoader {

    /**
     * Bonded devices that expose the SPP service, or that are the known relay.
     * Anything without either is a headset or a TV and would only fail later.
     *
     * Needs the BLUETOOTH_CONNECT grant on Android 12 and above.
     */
    @SuppressLint("MissingPermission")
    fun load(adapter: BluetoothAdapter?): List<EmfDevice> {
        val bonded = try {
            adapter?.bondedDevices.orEmpty()
        } catch (_: SecurityException) {
            // Permission revoked between the gate and this call.
            return emptyList()
        }

        return bonded
            .filter { isSensor(it.name, it.uuids?.map { uuid -> uuid.uuid }) }
            .map { EmfDevice(name = it.name ?: it.address, address = it.address) }
            .sortedWith(SENSOR_FIRST)
    }

    /** True for a device this app knows how to talk to. */
    fun isSensor(name: String?, uuids: Collection<UUID>?): Boolean =
        name == EmfSppContract.RELAY_NAME || uuids?.contains(EmfSppContract.SPP_UUID) == true

    /** The named relay first, so the obvious answer is the first row. */
    private val SENSOR_FIRST = compareBy<EmfDevice> { it.name != EmfSppContract.RELAY_NAME }
        .thenBy { it.name.lowercase() }
        .thenBy { it.address }
}