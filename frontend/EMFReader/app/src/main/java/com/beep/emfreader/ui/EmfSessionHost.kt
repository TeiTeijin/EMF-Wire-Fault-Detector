package com.beep.emfreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.beep.emfreader.R
import com.beep.emfreader.data.EmfDevice
import com.beep.emfreader.data.EmfSppContract
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfBackground
import com.beep.emfreader.ui.theme.EmfTextMuted

val SessionConnectingSpinnerSize = 24.dp
val SessionConnectingGap = 12.dp

sealed interface SessionPhase {
    data object Connecting : SessionPhase
    data object Live : SessionPhase
    data class Reconnecting(val secondsRemaining: Int) : SessionPhase
}

@Composable
fun EmfSessionHost(
    device: EmfDevice,
    onDisconnect: () -> Unit,
    onSessionLost: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EmfSessionViewModel = viewModel()
) {
    LaunchedEffect(device.address) { viewModel.start(device) }

    val state = viewModel.uiState
    LaunchedEffect(state.finished) {
        if (state.finished) onSessionLost()
    }

    val failure = state.failure
    if (failure != null) {
        ConnectionFailedScreen(
            device = device,
            reason = failure,
            onRetry = {
                viewModel.stop()
                viewModel.start(device)
            },
            onBack = {
                viewModel.stop()
                onDisconnect()
            },
            modifier = modifier
        )
    } else {
        when (val phase = state.phase) {
            SessionPhase.Connecting -> EmfConnectingScreen(
                deviceName = device.name,
                modifier = modifier
            )

            SessionPhase.Live, is SessionPhase.Reconnecting -> EmfLiveScreen(
                device = device,
                readings = state.readings,
                traceValues = state.traceValues,
                peak = state.peak,
                muted = state.muted,
                reconnectSeconds = state.reconnectSeconds,
                onToggleMute = viewModel::toggleMute,
                onDisconnect = {
                    viewModel.stop()
                    onDisconnect()
                },
                modifier = modifier
            )
        }
    }
}

@Composable
private fun EmfConnectingScreen(
    deviceName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EmfBackground)
            .padding(horizontal = ScreenMargin),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        EmfTopBar(modifier = Modifier.fillMaxWidth())

        EmfNavDivider(modifier = Modifier.fillMaxWidth())

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SessionConnectingSpinnerSize),
                    color = EmfTextMuted,
                    strokeWidth = ScanSpinnerStroke
                )

                Spacer(Modifier.height(SessionConnectingGap))

                Text(
                    text = stringResource(R.string.session_connecting, deviceName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EmfTextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(ScreenMargin)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun EmfConnectingScreenPreview() {
    EMFReaderTheme {
        EmfConnectingScreen(deviceName = EmfSppContract.RELAY_NAME)
    }
}
