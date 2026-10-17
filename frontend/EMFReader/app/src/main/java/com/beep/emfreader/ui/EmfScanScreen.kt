package com.beep.emfreader.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.bluetooth.BluetoothAdapter
import com.beep.emfreader.R
import com.beep.emfreader.data.EmfDevice
import com.beep.emfreader.data.EmfSppContract
import com.beep.emfreader.data.PairedDeviceLoader
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfBackground
import com.beep.emfreader.ui.theme.EmfText
import com.beep.emfreader.ui.theme.EmfTextHint
import com.beep.emfreader.ui.theme.EmfTextMuted

val ScanIconWidth = 24.dp
val ScanIconHeight = 33.dp
val ScanBlockGap = 12.dp
val ScanTextGap = 8.dp
val ScanActionGap = 8.dp
val ScanSpinnerStroke = 2.dp
val ScanFoundCountGap = 6.dp
val ScanFoundListGap = 16.dp
val ScanDeviceRowGap = 10.dp

private const val STATE_FADE_IN_MILLIS = 220
private const val STATE_FADE_OUT_MILLIS = 150

sealed interface ScanUiState {
    /** Reading the adapter's bond table. Brief, but it is a platform call. */
    data object Loading : ScanUiState
    data class Found(val devices: List<EmfDevice>) : ScanUiState

    /** Nothing paired that this app can talk to. */
    data object NotFound : ScanUiState
}

@Composable
fun EmfScanScreen(
    state: ScanUiState,
    onRetry: () -> Unit,
    onDeviceSelected: (EmfDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EmfBackground)
            .padding(horizontal = ScreenMargin)
    ) {
        EmfTopBar()

        EmfNavDivider()

        Spacer(Modifier.height(ScanBlockGap))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(tween(STATE_FADE_IN_MILLIS)) +
                        slideInVertically(tween(STATE_FADE_IN_MILLIS)) { it / 12 }) togetherWith
                        fadeOut(tween(STATE_FADE_OUT_MILLIS))
                },
                label = "pairedState"
            ) { target ->
                when (target) {
                    ScanUiState.Loading -> ScanLoadingBlock()
                    is ScanUiState.Found -> ScanFoundBlock(target.devices, onDeviceSelected)
                    ScanUiState.NotFound -> ScanNotFoundBlock()
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = ScreenMargin),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                ScanUiState.Loading -> Unit
                is ScanUiState.Found -> Unit
                ScanUiState.NotFound ->
                    EmfPrimaryButton(
                        text = stringResource(R.string.scan_cta_again),
                        onClick = onRetry
                    )
            }
        }
    }
}

@Composable
private fun ScanLoadingBlock() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            modifier = Modifier.size(ScanIconWidth),
            color = EmfTextMuted,
            strokeWidth = ScanSpinnerStroke
        )
        ScanGap()
        Text(
            text = stringResource(R.string.scan_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = EmfTextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScanFoundBlock(
    devices: List<EmfDevice>,
    onDeviceSelected: (EmfDevice) -> Unit
) {
    // Fills the available height so the content lands at the top. The other
    // states keep their intrinsic height and stay vertically centred.
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = stringResource(R.string.scan_found_title),
            style = MaterialTheme.typography.headlineSmall,
            color = EmfText,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(ScanFoundCountGap))

        Text(
            text = pluralStringResource(
                R.plurals.scan_found_count,
                devices.size,
                devices.size
            ),
            style = MaterialTheme.typography.labelMedium,
            color = EmfTextHint,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(ScanFoundListGap))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ScanDeviceRowGap)
        ) {
            devices.forEach { device ->
                EmfDeviceRow(device = device, onConnect = { onDeviceSelected(device) })
            }
        }
    }
}

@Composable
private fun ScanNotFoundBlock() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ScanIcon()
        ScanGap()
        Text(
            text = stringResource(R.string.scan_notfound_title),
            style = MaterialTheme.typography.headlineSmall,
            color = EmfText,
            textAlign = TextAlign.Center
        )
        ScanGap()
        Text(
            text = stringResource(R.string.scan_notfound_body, EmfSppContract.RELAY_NAME),
            style = MaterialTheme.typography.bodyMedium,
            color = EmfTextMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScanIcon() {
    Image(
        painter = painterResource(R.drawable.bluetooth_scan),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(width = ScanIconWidth, height = ScanIconHeight)
    )
}

@Composable
private fun ScanGap() {
    Spacer(Modifier.height(ScanTextGap))
}

/**
 * Reads the paired-device list and hands the chosen sensor on.
 *
 * There is no scan here to time out and nothing to count down: the bond table
 * is already populated by the system, so this resolves as fast as the platform
 * answers. [loader] is injectable so previews and tests can substitute it.
 */
@Composable
fun EmfScanHost(
    onDeviceSelected: (EmfDevice) -> Unit,
    modifier: Modifier = Modifier,
    loader: (() -> List<EmfDevice>)? = null
) {
    val context = LocalContext.current
    val defaultLoader = remember(context) {
        { PairedDeviceLoader.load(BluetoothAdapter.getDefaultAdapter()) }
    }
    var attempt by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<ScanUiState>(ScanUiState.Loading) }

    LaunchedEffect(attempt) {
        state = ScanUiState.Loading
        val devices = (loader ?: defaultLoader)()
        state = if (devices.isEmpty()) ScanUiState.NotFound else ScanUiState.Found(devices)
    }

    EmfScanScreen(
        state = state,
        onRetry = { attempt++ },
        onDeviceSelected = onDeviceSelected,
        modifier = modifier
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun EmfScanLoadingPreview() {
    EMFReaderTheme {
        EmfScanScreen(state = ScanUiState.Loading, onRetry = {}, onDeviceSelected = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun EmfScanFoundPreview() {
    EMFReaderTheme {
        EmfScanScreen(
            state = ScanUiState.Found(
                listOf(
                    EmfDevice(EmfSppContract.RELAY_NAME, "20:50:0D:2A:61:B2"),
                    EmfDevice("Workshop Relay", "24:6F:28:11:22:33")
                )
            ),
            onRetry = {},
            onDeviceSelected = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun EmfScanNotFoundPreview() {
    EMFReaderTheme {
        EmfScanScreen(state = ScanUiState.NotFound, onRetry = {}, onDeviceSelected = {})
    }
}