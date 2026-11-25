package com.local.routepilot.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.local.routepilot.R

val Outfit = FontFamily(Font(R.font.outfit_variable, FontWeight.SemiBold))
val Satoshi = FontFamily(
  Font(R.font.satoshi_400, FontWeight.Normal),
  Font(R.font.satoshi_500, FontWeight.Medium),
  Font(R.font.satoshi_600, FontWeight.Bold),
)

val Typography = Typography(
  displayLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.02).sp),
  headlineLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 28.sp),
  titleLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
  titleMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
  bodyLarge = TextStyle(fontFamily = Satoshi, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
  bodyMedium = TextStyle(fontFamily = Satoshi, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
  labelLarge = TextStyle(fontFamily = Satoshi, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
  labelMedium = TextStyle(fontFamily = Satoshi, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.sp),
)
