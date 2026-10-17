package com.beep.emfreader.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.beep.emfreader.EmfVibration
import com.beep.emfreader.data.ConnectFailure
import com.beep.emfreader.data.DefaultSessionTiming
import com.beep.emfreader.data.EmfAlertLevel
import com.beep.emfreader.data.EmfDevice
import com.beep.emfreader.data.EmfReadings
import com.beep.emfreader.data.ReadingHistory
import com.beep.emfreader.data.SessionEvent
import com.beep.emfreader.data.SppDeviceSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SessionUiState(
    val phase: SessionPhase = SessionPhase.Connecting,
    val readings: EmfReadings = EmfReadings(),
    val traceValues: List<Int> = emptyList(),
    val peak: Int = 0,
    val muted: Boolean = false,
    val reconnectSeconds: Int? = null,
    /** Set when the link could not be opened, so the host can explain why. */
    val failure: ConnectFailure? = null,
    /** Set once the session is over, so the host can navigate back. */
    val finished: Boolean = false
)

/**
 * Owns the live link for as long as the Activity's configuration survives.
 * Keeping the session here is what stops a rotation from dropping the RFCOMM
 * socket and losing the running feed.
 */
class EmfSessionViewModel(application: Application) : AndroidViewModel(application) {

    private val history = ReadingHistory()
    private var session: SppDeviceSession? = null
    private var collectJob: Job? = null

    var uiState by mutableStateOf(SessionUiState())
        private set

    fun start(device: EmfDevice) {
        if (session != null) return

        val newSession = SppDeviceSession(device = device)
        session = newSession
        history.clear()
        uiState = SessionUiState()

        collectJob = viewModelScope.launch {
            var linkLost = false

            newSession.events().collect { event ->
                when (event) {
                    // The socket is open but the sketch may still be mute, so
                    // the screen stays on "connecting" until a line lands.
                    SessionEvent.Connected -> Unit

                    is SessionEvent.Reading -> {
                        history.add(event.readings)
                        val alert = event.readings.alertLevel
                        buzzOnEnteringFault(alert)
                        uiState = uiState.copy(
                            phase = SessionPhase.Live,
                            readings = event.readings,
                            traceValues = history.snapshot().map { it.signalPercent },
                            peak = history.peakSignal(),
                            reconnectSeconds = null
                        )
                    }

                    SessionEvent.LinkLost -> linkLost = true

                    is SessionEvent.Failed -> {
                        collectJob = null
                        session = null
                        newSession.close()
                        uiState = uiState.copy(failure = event.reason, finished = true)
                        return@collect
                    }
                }
            }

            if (linkLost) {
                for (second in DefaultSessionTiming.reconnectSeconds downTo 1) {
                    uiState = uiState.copy(
                        phase = SessionPhase.Reconnecting(second),
                        reconnectSeconds = second
                    )
                    delay(1_000)
                }
                // Release the link so a later start() is not treated as a
                // duplicate of the session that just ended.
                collectJob = null
                session?.close()
                session = null
                uiState = uiState.copy(finished = true)
            }
        }
    }

    fun toggleMute() {
        uiState = uiState.copy(muted = !uiState.muted)
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        session?.close()
        session = null
        history.clear()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    /** Fires on the transition into a fault, not on every reading that stays there. */
    private fun buzzOnEnteringFault(alert: EmfAlertLevel) {
        if (uiState.muted) return
        if (alert == EmfAlertLevel.FAULT && uiState.readings.alertLevel != EmfAlertLevel.FAULT) {
            EmfVibration.buzz(getApplication())
        }
    }
}