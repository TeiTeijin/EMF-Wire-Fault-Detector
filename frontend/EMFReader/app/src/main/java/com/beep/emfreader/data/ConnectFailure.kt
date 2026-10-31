package com.beep.emfreader.data

enum class ConnectFailure {
    REFUSED,
    BUSY,
    OUT_OF_RANGE,
    DISAPPEARED,

    SILENT,

    // The phone's pairing dialog was cancelled or the typed code was
    // wrong (BLE static-passkey pairing). The device is still reachable:
    // open a fresh pairing window on the detector and retry.
    PAIRING_FAILED
}

sealed interface ConnectResult {
    data object Connected : ConnectResult
    data class Failed(val reason: ConnectFailure) : ConnectResult
}
