package com.beep.emfreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beep.emfreader.R
import com.beep.emfreader.data.EmfAlertLevel
import com.beep.emfreader.data.EmfDevice
import com.beep.emfreader.data.EmfSppContract
import com.beep.emfreader.data.EmfReadings
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfBackground
import com.beep.emfreader.ui.theme.EmfBorderSubtle
import com.beep.emfreader.ui.theme.EmfSignalGreen
import com.beep.emfreader.ui.theme.EmfStatusFault
import com.beep.emfreader.ui.theme.EmfText
import com.beep.emfreader.ui.theme.EmfTextFaint
import com.beep.emfreader.ui.theme.EmfTextHint
import com.beep.emfreader.ui.theme.EmfTextMuted
import com.beep.emfreader.ui.theme.RobotoMonoFamily

val LiveCardWidth = 342.dp
val LiveCardHeight = 171.dp
val LiveCardBorderWidth = 1.dp
val LiveCardPadding = 16.dp
val LiveCardPercentSize = 54.sp
val LiveCardSymbolSize = 16.sp
val LiveCardGap = 12.dp
val LiveCardLabelGap = 6.dp
val LiveSymbolRise = 10.dp
val LiveHeadGap = 20.dp
val LiveHeadIconGap = 8.dp
val LiveCaptionSize = 10.sp
val LiveMuteTouchTarget = 40.dp
val LiveBannerPadding = 10.dp

val LiveTraceHeight = 96.dp
val LiveTraceLabelSize = 11.sp
val LiveTracePeakSize = 20.sp
val LiveTraceLabelGap = 8.dp
val LiveTraceSparkGap = 6.dp

private val ReconnectBannerShape = RoundedCornerShape(8.dp)

private const val CLEAR_TICK_AFTER = 1
private const val FAULT_TICK_AFTER = 4

private val EmfAlertLevel.labelRes: Int
    get() = when (this) {
        EmfAlertLevel.CLEAR -> R.string.live_status_clear
        EmfAlertLevel.WIRE -> R.string.live_status_wire
        EmfAlertLevel.FAULT -> R.string.live_status_fault
    }

private val EmfAlertLevel.statusColor: Color
    get() = when (this) {
        EmfAlertLevel.CLEAR -> EmfTextFaint
        EmfAlertLevel.WIRE -> EmfSignalGreen
        EmfAlertLevel.FAULT -> EmfStatusFault
    }

@Composable
fun EmfLiveScreen(
    device: EmfDevice,
    readings: EmfReadings,
    onDisconnect: () -> Unit,
    traceValues: List<Int> = emptyList(),
    peak: Int = 0,
    muted: Boolean = false,
    onToggleMute: () -> Unit = {},
    reconnectSeconds: Int? = null,
    traceWindowSeconds: Int = 15,
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

        if (reconnectSeconds != null) {
            ReconnectBanner(reconnectSeconds)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(LiveHeadGap))

            LiveHead(
                name = device.name,
                alert = readings.alertLevel,
                muted = muted,
                onToggleMute = onToggleMute
            )

            Spacer(Modifier.height(LiveHeadGap))

            LiveChannelCard(
                labelRes = R.string.live_channel_field,
                percent = readings.fieldPercent,
                thresholdAfterCells = CLEAR_TICK_AFTER,
                captionRes = R.string.live_caption_clear
            )
            Spacer(Modifier.height(LiveCardGap))
            LiveChannelCard(
                labelRes = R.string.live_channel_opamp,
                percent = readings.opampPercent,
                thresholdAfterCells = CLEAR_TICK_AFTER,
                captionRes = R.string.live_caption_clear
            )
            Spacer(Modifier.height(LiveCardGap))
            LiveChannelCard(
                labelRes = R.string.live_channel_jitter,
                percent = readings.jitterPercent,
                thresholdAfterCells = FAULT_TICK_AFTER,
                captionRes = R.string.live_caption_fault
            )

            Spacer(Modifier.height(LiveCardGap))

            LiveTrace(traceValues, peak, traceWindowSeconds)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = LiveCardGap, bottom = ScreenMargin),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmfSecondaryButton(
                text = stringResource(R.string.live_cta_disconnect),
                onClick = onDisconnect
            )
        }
    }
}

@Composable
private fun ReconnectBanner(secondsRemaining: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = LiveHeadGap)
            .background(EmfStatusFault.copy(alpha = 0.12f), ReconnectBannerShape)
            .padding(horizontal = LiveBannerPadding, vertical = LiveBannerPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(EmfStatusFault, RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.width(LiveHeadIconGap))
        Text(
            text = stringResource(R.string.reconnect_banner, secondsRemaining),
            style = MaterialTheme.typography.bodyMedium,
            color = EmfText
        )
    }
}

@Composable
private fun LiveHead(
    name: String,
    alert: EmfAlertLevel,
    muted: Boolean,
    onToggleMute: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            color = EmfText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onToggleMute,
            modifier = Modifier.size(LiveMuteTouchTarget),
            colors = IconButtonDefaults.iconButtonColors(contentColor = EmfTextHint)
        ) {
            Icon(
                painter = painterResource(
                    if (muted) R.drawable.ic_sound_off else R.drawable.ic_sound_on
                ),
                contentDescription = stringResource(
                    if (muted) R.string.live_cta_unmute_on else R.string.live_cta_mute_on
                )
            )
        }

        Text(
            text = stringResource(alert.labelRes),
            style = MaterialTheme.typography.titleLarge,
            color = alert.statusColor,
            maxLines = 1,
            modifier = Modifier.padding(start = LiveHeadIconGap)
        )
    }
}

@Composable
private fun LiveChannelCard(
    labelRes: Int,
    percent: Int,
    thresholdAfterCells: Int?,
    captionRes: Int
) {
    Box(
        modifier = Modifier
            .width(LiveCardWidth)
            .height(LiveCardHeight)
            .border(LiveCardBorderWidth, EmfBorderSubtle, EmfButtonShape)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(LiveCardPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = percent.toString(),
                    fontSize = LiveCardPercentSize,
                    lineHeight = LiveCardPercentSize,
                    fontFamily = RobotoMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = EmfText
                )
                Text(
                    text = "%",
                    fontSize = LiveCardSymbolSize,
                    lineHeight = LiveCardSymbolSize,
                    fontFamily = RobotoMonoFamily,
                    fontWeight = FontWeight.Normal,
                    color = EmfTextMuted,
                    modifier = Modifier.padding(start = 2.dp, bottom = LiveSymbolRise)
                )
            }

            Spacer(Modifier.height(LiveCardLabelGap))

            EmfSegmentMeter(percent = percent, thresholdAfterCells = thresholdAfterCells)

            Spacer(Modifier.height(LiveCardLabelGap))

            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = EmfTextMuted,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = stringResource(captionRes),
                fontSize = LiveCaptionSize,
                color = EmfTextFaint,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LiveTrace(values: List<Int>, peak: Int, windowSeconds: Int) {
    val peakIndex = values.indices.maxByOrNull { values[it] } ?: -1

    Column(modifier = Modifier.width(LiveCardWidth)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(R.string.live_trace_peak),
                    fontSize = LiveTraceLabelSize,
                    color = EmfTextFaint,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = peak.toString(),
                    fontSize = LiveTracePeakSize,
                    lineHeight = LiveTracePeakSize,
                    fontFamily = RobotoMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = EmfSignalGreen
                )
                Text(
                    text = "%",
                    fontSize = LiveTraceLabelSize,
                    lineHeight = LiveTracePeakSize,
                    fontFamily = RobotoMonoFamily,
                    color = EmfTextMuted,
                    modifier = Modifier.padding(start = 1.dp, bottom = 2.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(R.string.live_trace_window, windowSeconds),
                fontSize = LiveTraceLabelSize,
                color = EmfTextFaint,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        Spacer(Modifier.height(LiveTraceSparkGap))

        EmfSparkline(
            values = values,
            peakIndex = peakIndex,
            modifier = Modifier
                .fillMaxWidth()
                .height(SparklineHeight)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun EmfLiveScreenPreview() {
    EMFReaderTheme {
        EmfLiveScreen(
            device = EmfDevice(EmfSppContract.RELAY_NAME, "20:50:0D:2A:61:B2"),
            readings = EmfReadings(fieldPercent = 18, opampPercent = 27, jitterPercent = 34),
            traceValues = listOf(18, 20, 24, 22, 30, 31, 28, 27),
            peak = 31,
            onDisconnect = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1B1B1A)
@Composable
private fun EmfLiveScreenFaultPreview() {
    EMFReaderTheme {
        EmfLiveScreen(
            device = EmfDevice(EmfSppContract.RELAY_NAME, "20:50:0D:2A:61:B2"),
            readings = EmfReadings(fieldPercent = 62, opampPercent = 88, jitterPercent = 71),
            traceValues = listOf(20, 24, 33, 48, 71, 88, 74, 51, 38, 29, 34, 62, 88, 71, 55, 44),
            peak = 88,
            reconnectSeconds = 3,
            onDisconnect = {}
        )
    }
}
