package com.beep.emfreader.ui.theme

import androidx.compose.ui.graphics.Color

// Design palette (EMS sensor UI enhancement)
val EmfBackground = Color(0xFF181818)
val EmfSurface = Color(0xFF232323)
val EmfSurfaceHigh = Color(0xFF2D2D2D)
val EmfDivider = Color(0xFF2E2E2E)

val EmfText = Color(0xFFF4F4F1)
val EmfTextMuted = Color(0xFFA2A2A0)
val EmfTextHint = Color(0xFFC8C8C5)
val EmfTextFaint = Color(0xFF8A8A88)

val EmfBorderSubtle = Color(0xFF414141)

// Fault accent (coral), used for fault segments, peak value and badges.
val EmfAccent = Color(0xFFFF6E5B)
val EmfAccentText = Color(0xFFF1A99D)
val EmfAccentBg = Color(0xFF3A2420)

// Clear / wire accent (green).
val EmfSignalGreen = Color(0xFF57C15A)
val EmfGreenText = Color(0xFF8FD992)
val EmfGreenBg = Color(0xFF1F2A1F)

// Backwards-compatible alias used by older code paths.
val EmfStatusFault = EmfAccent
