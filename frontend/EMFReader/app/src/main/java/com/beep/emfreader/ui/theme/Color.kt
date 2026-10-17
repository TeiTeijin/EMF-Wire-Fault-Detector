package com.beep.emfreader.ui.theme

import androidx.compose.ui.graphics.Color

val EmfBackground = Color(0xFF1B1B1A)
val EmfText = Color(0xFFEDEDEA)
val EmfTextMuted = EmfText.copy(alpha = 0.72f)
val EmfTextHint = EmfText.copy(alpha = 0.65f)
val EmfTextFaint = EmfText.copy(alpha = 0.60f)

/** Border for reading containers, a step below the nav hairline. */
val EmfBorderSubtle = Color(0xFF373732)

/** Only colour in the app. Marks a channel as carrying signal. */
val EmfSignalGreen = Color(0xFF2E7D32)

/** Status only. Reserved so nothing else can read as an alert. */
val EmfStatusFault = Color(0xFFE53935)
