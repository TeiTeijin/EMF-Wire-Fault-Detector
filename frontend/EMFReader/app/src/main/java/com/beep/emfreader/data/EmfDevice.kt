package com.beep.emfreader.data

/**
 * A paired sensor, ready to connect over Bluetooth Classic.
 *
 * There is no RSSI here on purpose: a bonded RFCOMM peer is not discovered by
 * scanning, so there is no signal strength to report and a fake number would
 * only ever mislead.
 */
data class EmfDevice(
    val name: String,
    val address: String
)