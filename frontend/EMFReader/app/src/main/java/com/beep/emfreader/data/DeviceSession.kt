package com.beep.emfreader.data

import kotlinx.coroutines.flow.Flow

/**
 * A live link to one device. [SppDeviceSession] is the Bluetooth Classic
 * implementation; it is injected so the session screens can be previewed and
 * tested without hardware.
 */
interface DeviceSession {
    /** Emits connected, reading and link-loss events until the session closes. */
    fun events(): Flow<SessionEvent>

    /** Releases the link. Safe to call more than once. */
    fun close()
}

sealed interface SessionEvent {
    /** The RFCOMM channel is open. No reading has arrived yet. */
    data object Connected : SessionEvent

    data class Reading(val readings: EmfReadings) : SessionEvent

    /** The link dropped. The host decides whether to retry. */
    data object LinkLost : SessionEvent

    /** The link could not be established, or never produced a reading. */
    data class Failed(val reason: ConnectFailure) : SessionEvent
}

data class SessionTiming(
    /** How often the sketch sends, so the UI can describe the cadence. */
    val readingIntervalMillis: Long = EmfSppContract.SEND_INTERVAL_MILLIS,
    /** Points kept for the trace. 30 at 500ms gives a 15 second window. */
    val historyPoints: Int = 30,
    /** Retry grace period shown in the disconnected banner. */
    val reconnectSeconds: Int = 5
)

val DefaultSessionTiming = SessionTiming()

/** Rolling window of recent readings, oldest first. */
class ReadingHistory(private val maxPoints: Int = DefaultSessionTiming.historyPoints) {
    private val points = ArrayDeque<EmfReadings>()

    fun add(readings: EmfReadings) {
        points.addLast(readings)
        while (points.size > maxPoints) points.removeFirst()
    }

    fun snapshot(): List<EmfReadings> = points.toList()

    /** Highest headline signal in the window, or 0 when empty. */
    fun peakSignal(): Int = points.maxOfOrNull { it.signalPercent } ?: 0

    fun clear() = points.clear()
}