package com.beep.emfreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beep.emfreader.R
import com.beep.emfreader.data.EmfAlertLevel
import com.beep.emfreader.data.EmfDevice
import com.beep.emfreader.data.EmfContract
import com.beep.emfreader.data.EmfReadings
import com.beep.emfreader.ui.theme.EMFReaderTheme
import com.beep.emfreader.ui.theme.EmfAccent
import com.beep.emfreader.ui.theme.EmfAccentBg
import com.beep.emfreader.ui.theme.EmfAccentText
import com.beep.emfreader.ui.theme.EmfBackground
import com.beep.emfreader.ui.theme.EmfGreenBg
import com.beep.emfreader.ui.theme.EmfGreenText
import com.beep.emfreader.ui.theme.EmfSignalGreen
import com.beep.emfreader.ui.theme.EmfSurface
import com.beep.emfreader.ui.theme.EmfSurfaceHigh
import com.beep.emfreader.ui.theme.EmfText
import com.beep.emfreader.ui.theme.EmfTextFaint
import com.beep.emfreader.ui.theme.EmfTextHint
import com.beep.emfreader.ui.theme.EmfTextMuted
import com.beep.emfreader.ui.theme.InterFamily
import com.beep.emfreader.ui.theme.emfLabelStyle

val LiveCardShape = RoundedCornerShape(22.dp)
val LiveCardPaddingH = 18.dp
val LiveCardPaddingV = 14.dp
val LiveCardGap = 14.dp
val LiveValueSize = 48.sp
val LiveSymbolSize = 15.sp
val LiveSymbolRise = 7.dp
val LiveLabelGap = 12.dp
val LiveHeadGap = 18.dp
val LiveDotSize = 12.dp
val LiveDotGap = 10.dp
val LiveBadgePaddingH = 14.dp
val LiveBadgePaddingV = 8.dp
val LiveBannerShape = RoundedCornerShape(18.dp)
val LiveBannerPaddingH = 16.dp
val LiveBannerPaddingV = 14.dp
val LiveTraceLabelSize = 12.sp
val LiveTracePeakSize = 26.sp
val LiveTraceGap = 10.dp

private val EmfAlertLevel.labelRes: Int
    get() = when (this) {
        EmfAlertLevel.CLEAR -> R.string.live_status_clear
        EmfAlertLevel.WIRE -> R.string.live_status_wire
        EmfAlertLevel.FAULT -> R.string.live_status_fault
    }

private val EmfAlertLevel.badgeBackground: Color
    get() = when (this) {
        EmfAlertLevel.CLEAR -> EmfGreenBg
        EmfAlertLevel.WIRE -> EmfSurfaceHigh
        EmfAlertLevel.FAULT -> EmfAccentBg
    }

private val EmfAlertLevel.badgeText: Color
    get() = when (this) {
        EmfAlertLevel.CLEAR -> EmfGreenText
        EmfAlertLevel.WIRE -> EmfText
        EmfAlertLevel.FAULT -> EmfAccentText
    }

private val EmfAlertLevel.accent: Color
    get() = when (this) {
        EmfAlertLevel.FAULT -> EmfAccent
        else -> EmfSignalGreen
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
            .padding(horizontal = ScreenMargin)
    ) {
        if (reconnectSeconds != null) {
            Spacer(Modifier.height(8.dp))
            ReconnectBanner(reconnectSeconds)
        }

        EmfTopBar(statusBarPadding = reconnectSeconds == null)

        EmfNavDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                accent = readings.alertLevel.accent
            )
            Spacer(Modifier.height(LiveCardGap))
            LiveChannelCard(
                labelRes = R.string.live_channel_opamp,
                percent = readings.opampPercent,
                accent = readings.alertLevel.accent
            )
            Spacer(Modifier.height(LiveCardGap))
            LiveChannelCard(
                labelRes = R.string.live_channel_jitter,
                percent = readings.jitterPercent,
                accent = readings.alertLevel.accent
            )

            Spacer(Modifier.height(LiveHeadGap))

            LiveTrace(
                values = traceValues,
                peak = peak,
                peakColor = readings.alertLevel.accent,
                windowSeconds = traceWindowSeconds
            )
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
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 8.dp)
            .background(EmfAccentBg, LiveBannerShape)
            .padding(horizontal = LiveBannerPaddingH, vertical = LiveBannerPaddingV),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(EmfAccent, RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(LiveDotGap))
        Text(
            text = stringResource(R.string.reconnect_banner, secondsRemaining),
            style = MaterialTheme.typography.bodyMedium,
            color = EmfAccentText
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
        Box(
            modifier = Modifier
                .size(LiveDotSize)
                .background(EmfSignalGreen, RoundedCornerShape(LiveDotSize / 2))
        )
        Spacer(Modifier.width(LiveDotGap))
        Text(
            text = name,
            fontSize = 19.sp,
            lineHeight = 24.sp,
            fontFamily = InterFamily,
            fontWeight = FontWeight.Bold,
            color = EmfText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onToggleMute,
            modifier = Modifier.size(30.dp),
            colors = IconButtonDefaults.iconButtonColors(contentColor = EmfTextHint)
        ) {
            Icon(
                painter = painterResource(
                    if (muted) R.drawable.ic_sound_off else R.drawable.ic_sound_on
                ),
                contentDescription = stringResource(
                    if (muted) R.string.live_cta_unmute_on else R.string.live_cta_mute_on
                ),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.width(4.dp))

        StatusBadge(alert)
    }
}

@Composable
private fun StatusBadge(alert: EmfAlertLevel) {
    Box(
        modifier = Modifier
            .background(alert.badgeBackground, RoundedCornerShape(50))
            .padding(horizontal = LiveBadgePaddingH, vertical = LiveBadgePaddingV)
    ) {
        Text(
            text = stringResource(alert.labelRes),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = alert.badgeText,
            maxLines = 1
        )
    }
}

@Composable
private fun LiveChannelCard(
    labelRes: Int,
    percent: Int,
    accent: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(EmfSurface, LiveCardShape)
            .padding(horizontal = LiveCardPaddingH, vertical = LiveCardPaddingV)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(labelRes).uppercase(),
                style = emfLabelStyle(),
                color = EmfTextMuted,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = percent.toString(),
                    fontSize = LiveValueSize,
                    lineHeight = LiveValueSize,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    color = EmfText
                )
                Text(
                    text = "%",
                    fontSize = LiveSymbolSize,
                    lineHeight = LiveSymbolSize,
                    fontFamily = InterFamily,
                    color = EmfTextMuted,
                    modifier = Modifier.padding(start = 2.dp, bottom = LiveSymbolRise)
                )
            }
        }

        Spacer(Modifier.height(LiveLabelGap))

        EmfSegmentMeter(percent = percent, filledColor = accent)
    }
}

@Composable
private fun LiveTrace(
    values: List<Int>,
    peak: Int,
    peakColor: Color,
    windowSeconds: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(R.string.live_trace_peak).uppercase(),
                    style = emfLabelStyle(LiveTraceLabelSize.value.toInt()),
                    color = EmfTextFaint,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = peak.toString(),
                    fontSize = LiveTracePeakSize,
                    lineHeight = LiveTracePeakSize,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Bold,
                    color = peakColor
                )
                Text(
                    text = "%",
                    fontSize = LiveTraceLabelSize,
                    lineHeight = LiveTracePeakSize,
                    fontFamily = InterFamily,
                    color = EmfTextMuted,
                    modifier = Modifier.padding(start = 1.dp, bottom = 4.dp)
                )
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = stringResource(R.string.live_trace_window, windowSeconds).uppercase(),
                style = emfLabelStyle(LiveTraceLabelSize.value.toInt()),
                color = EmfTextFaint,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        Spacer(Modifier.height(LiveTraceGap))

        EmfSparkline(
            values = values,
            modifier = Modifier
                .fillMaxWidth()
                .height(SparklineHeight)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun EmfLiveScreenPreview() {
    EMFReaderTheme {
        EmfLiveScreen(
            device = EmfDevice(EmfContract.RELAY_NAME, "20:50:0D:2A:61:B2"),
            readings = EmfReadings(fieldPercent = 18, opampPercent = 27, jitterPercent = 34),
            traceValues = listOf(18, 20, 24, 22, 30, 31, 28, 27),
            peak = 31,
            onDisconnect = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF181818)
@Composable
private fun EmfLiveScreenFaultPreview() {
    EMFReaderTheme {
        EmfLiveScreen(
            device = EmfDevice(EmfContract.RELAY_NAME, "20:50:0D:2A:61:B2"),
            readings = EmfReadings(fieldPercent = 62, opampPercent = 88, jitterPercent = 71),
            traceValues = listOf(20, 24, 33, 48, 71, 88, 74, 51, 38, 29, 34, 62, 88, 71, 55, 44),
            peak = 88,
            reconnectSeconds = 3,
            onDisconnect = {}
        )
    }
}
