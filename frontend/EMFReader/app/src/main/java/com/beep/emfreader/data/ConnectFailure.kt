package com.beep.emfreader.data

/**
 * Why a connection attempt did not reach the live viewer. Each case needs
 * different advice, so they are not collapsed into one generic message.
 */
enum class ConnectFailure {
    REFUSED,
    BUSY,
    OUT_OF_RANGE,
    DISAPPEARED,

    /**
     * The socket opened but no line ever arrived. The ESP32 relays whatever
     * the Arduino sends, so this almost always means the Arduino is
     * unpowered or its UART link to the ESP32 is not connected.
     */
    SILENT
}

sealed interface ConnectResult {
    data object Connected : ConnectResult
    data class Failed(val reason: ConnectFailure) : ConnectResult
}