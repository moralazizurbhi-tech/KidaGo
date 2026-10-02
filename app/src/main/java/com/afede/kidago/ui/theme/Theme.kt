package com.afede.kidago.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afede.kidago.R

/** Palette from Project UX Specification, UX Analysis (Colour). */
object KidaGoColors {
    val Background = Color(0xFFF7F6F3)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFD9D9D9)
    val Text = Color(0xFF1D1D1D)
    val Accent = Color(0xFFE3001B) // active/on and critical alert
    val SuccessText = Color(0xFF1B882B)
    val SuccessIcon = Color(0xFF06752D)
}

@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) =
    Font(resId, weight, variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)))

/** Manrope for all UI text, in two weights: regular body, bold heading. */
val Manrope = FontFamily(
    variableFont(R.font.manrope, FontWeight.Normal),
    variableFont(R.font.manrope, FontWeight.Bold),
)

/** Fredoka for the "KidaGo" wordmark only. */
val Fredoka = FontFamily(variableFont(R.font.fredoka, FontWeight.Bold))

val KidaGoWordmark = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.Bold, fontSize = 24.sp)

private val body = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Normal)
private val heading = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold)

val KidaGoTypography = Typography(
    headlineMedium = heading.copy(fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = heading.copy(fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = heading.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = body.copy(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = body.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = heading.copy(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = body.copy(fontSize = 12.sp, lineHeight = 16.sp),
)

/** Pill for controls, rounded card for containers (the default container shape). */
object KidaGoShapes {
    val Pill = CircleShape
    val Card = RoundedCornerShape(24.dp)
}

object KidaGoSpacing {
    val Outer = 32.dp // observed outer padding of the source design
    val Gap = 16.dp
}

val KidaGoColorScheme: ColorScheme = lightColorScheme(
    primary = KidaGoColors.Accent,
    onPrimary = KidaGoColors.Surface,
    error = KidaGoColors.Accent,
    background = KidaGoColors.Background,
    onBackground = KidaGoColors.Text,
    surface = KidaGoColors.Surface,
    onSurface = KidaGoColors.Text,
    outline = KidaGoColors.Border,
    outlineVariant = KidaGoColors.Border,
    surfaceVariant = KidaGoColors.Surface,
    onSurfaceVariant = KidaGoColors.Text,
    secondary = KidaGoColors.Text,
    onSecondary = KidaGoColors.Surface,
)

@Composable
fun KidaGoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KidaGoColorScheme,
        typography = KidaGoTypography,
        shapes = Shapes(
            extraSmall = KidaGoShapes.Pill,
            small = KidaGoShapes.Pill,
            medium = KidaGoShapes.Card,
            large = KidaGoShapes.Card,
            extraLarge = KidaGoShapes.Card,
        ),
        content = content,
    )
}
