package com.beep.emfreader.ui

import android.Manifest
import android.os.Build

fun requiredBluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        // BLE scanning on Android 6–11 rides on the location permission.
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
