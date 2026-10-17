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

/** Numeric readouts, so digits keep a fixed width while values change. */
val RobotoMonoFamily = FontFamily(
    Font(R.font.roboto_mono_regular, FontWeight.Normal),
    Font(R.font.roboto_mono_bold, FontWeight.Bold)
)

private fun inter(
    weight: FontWeight,
    size: Int,
    lineHeight: Int
) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp
)

val EmfTypography = Typography(
    titleLarge = inter(FontWeight.SemiBold, 20, 24),
    headlineSmall = inter(FontWeight.Normal, 24, 30),
    bodyMedium = inter(FontWeight.Normal, 16, 24),
    bodySmall = inter(FontWeight.Normal, 14, 20),
    labelMedium = inter(FontWeight.Normal, 13, 16),
    labelLarge = inter(FontWeight.Bold, 16, 20)
)
