package com.beep.emfreader.data

import kotlinx.coroutines.flow.Flow

interface DeviceSession {
    fun events(): Flow<SessionEvent>

    fun close()
}

sealed interface SessionEvent {
    data object Connected : SessionEvent

    data class Reading(val readings: EmfReadings) : SessionEvent

    data object LinkLost : SessionEvent

    data class Failed(val reason: ConnectFailure) : SessionEvent
}

data class SessionTiming(
    val readingIntervalMillis: Long = EmfSppContract.SEND_INTERVAL_MILLIS,
    val historyPoints: Int = 30,
    val reconnectSeconds: Int = 5
)

val DefaultSessionTiming = SessionTiming()

class ReadingHistory(private val maxPoints: Int = DefaultSessionTiming.historyPoints) {
    private val points = ArrayDeque<EmfReadings>()

    fun add(readings: EmfReadings) {
        points.addLast(readings)
        while (points.size > maxPoints) points.removeFirst()
    }

    fun snapshot(): List<EmfReadings> = points.toList()

    fun peakSignal(): Int = points.maxOfOrNull { it.signalPercent } ?: 0

    fun clear() = points.clear()
}
