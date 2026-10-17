package com.beep.emfreader.data

enum class ConnectFailure {
    REFUSED,
    BUSY,
    OUT_OF_RANGE,
    DISAPPEARED,

    SILENT
}

sealed interface ConnectResult {
    data object Connected : ConnectResult
    data class Failed(val reason: ConnectFailure) : ConnectResult
}
