package com.beep.emfreader.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Scans for the relay over BLE. The relay advertises the Nordic UART
 * Service UUID, so the scan filter keys on that (more reliable than the
 * name, which some phones only report after a connection).
 *
 * API <= 30 needs ACCESS_FINE_LOCATION for BLE scanning; API 31+ needs
 * BLUETOOTH_SCAN. Both are gated by the app's permission flow before this
 * screen is reachable; if a grant is somehow missing, an empty list is
 * returned (the scan screen then shows the "not found" hint).
 */
class BleDeviceScanner(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun discover(timeoutMillis: Long = SCAN_TIMEOUT_MILLIS): List<EmfDevice> {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return emptyList()
        val adapter = manager.adapter
        if (adapter == null || !adapter.isEnabled) return emptyList()
        if (ContextCompat.checkSelfPermission(context, requiredScanPermission()) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "scan permission missing; reporting no devices")
            return emptyList()
        }
        val scanner = adapter.bluetoothLeScanner ?: return emptyList()

        val found = LinkedHashMap<String, EmfDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val remote = result.device
                found.putIfAbsent(
                    remote.address,
                    EmfDevice(name = remote.name ?: remote.address, address = remote.address)
                )
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "BLE scan failed with code $errorCode")
            }
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(EmfContract.NUS_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(listOf(filter), settings, callback)
            delay(timeoutMillis)
        } finally {
            try {
                scanner.stopScan(callback)
            } catch (_: IllegalStateException) {
            }
        }
        return found.values.sortedWith(
            compareBy(
                { it.name != EmfContract.RELAY_NAME },
                { it.name.lowercase() },
                { it.address }
            )
        )
    }

    private fun requiredScanPermission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

    private companion object {
        const val TAG = "EmfBleScan"
        const val SCAN_TIMEOUT_MILLIS = 10_000L
    }
}
