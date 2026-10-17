package com.beep.emfreader.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.beep.emfreader.ui.theme.EmfBackground
import com.beep.emfreader.ui.theme.EmfText
import com.beep.emfreader.ui.theme.EmfTextMuted

val ScreenMargin = 16.dp
val GateIconWidth = 26.dp
val GateIconHeight = 32.dp

val GateEntranceRise = 24.dp
const val GateEntranceMillis = 280

@Composable
fun EmfGateScreen(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    iconWidth: Dp = GateIconWidth,
    iconHeight: Dp = GateIconHeight,
    actions: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EmfBackground)
            .padding(horizontal = ScreenMargin)
    ) {
        EmfTopBar()
        EmfNavDivider()

        // One-shot entrance only. Nothing here is "in progress", so a looping
        // animation would just compete with the call to action.
        val entrance = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            entrance.animateTo(1f, tween(GateEntranceMillis, easing = FastOutSlowInEasing))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = entrance.value
                    translationY = (1f - entrance.value) * GateEntranceRise.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = iconWidth, height = iconHeight)
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = EmfText,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = EmfTextMuted,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = ScreenMargin),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            actions()
        }
    }
}
