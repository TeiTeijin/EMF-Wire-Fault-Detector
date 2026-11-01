package com.beep.emfreader.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.beep.emfreader.R

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

val RobotoMonoFamily = FontFamily(
    Font(R.font.roboto_mono_regular, FontWeight.Normal),
    Font(R.font.roboto_mono_bold, FontWeight.Bold)
)

private fun inter(
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Int = 0
) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)

val EmfTypography = Typography(
    headlineLarge = inter(FontWeight.Bold, 30, 36),
    headlineSmall = inter(FontWeight.Bold, 24, 30),
    titleLarge = inter(FontWeight.SemiBold, 22, 28),
    titleMedium = inter(FontWeight.SemiBold, 18, 24),
    bodyMedium = inter(FontWeight.Normal, 16, 24),
    bodySmall = inter(FontWeight.Normal, 14, 20),
    labelMedium = inter(FontWeight.Normal, 13, 16),
    labelLarge = inter(FontWeight.SemiBold, 16, 20)
)

// Uppercase section labels: FIELD / OP-AMP / JITTER / PEAK / LAST 15 S.
fun emfLabelStyle(size: Int = 12) = inter(
    FontWeight.SemiBold,
    size,
    size + 4,
    letterSpacing = 2
)
