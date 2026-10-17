package com.beep.emfreader.ui

import android.Manifest
import android.os.Build

/**
 * The runtime permissions this app needs.
 *
 * The sensor speaks Bluetooth Classic and is only reachable once it has been
 * bonded, so this app never scans for devices. That removes BLUETOOTH_SCAN
 * entirely: reading the adapter's bond table and opening an RFCOMM socket are
 * both covered by BLUETOOTH_CONNECT, and there is no location grant anywhere in
 * the flow any more.
 */
fun requiredBluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        // Below Android 12 the Bluetooth permissions are install-time, so there
        // is nothing to ask for at runtime.
        emptyArray()
    }