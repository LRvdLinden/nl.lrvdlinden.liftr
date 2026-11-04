package nl.lrvdlinden.liftr.ui

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Liftr's look (Shared/Theme.swift): near-black surfaces, one loud orange, heavy condensed headings. */
object LiftrColors {
    val accent = Color(0xFFFF6B00)
    val accentDeep = Color(0xFFE65400)
    val background = Color(0xFF0E0E10)
    val card = Color(0xFF1B1B1E)
    val cardRaised = Color(0xFF27272A)
    val line = Color.White.copy(alpha = 0.08f)
    val text = Color.White
    val muted = Color(0xFF9E9EA8)
    val success = Color(0xFF33CC73)
    val danger = Color(0xFFFF453A)
    val pink = Color(0xFFFF375F)

    val accentGradient: Brush get() = Brush.linearGradient(listOf(accent, accentDeep))
}

object LiftrType {
    /** Condensed system font (Roboto Condensed on most phones). */
    val condensed: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

    /** Big, heavy, condensed heading (use with uppercase text). */
    fun display(size: TextUnit): TextStyle =
        TextStyle(fontFamily = condensed, fontWeight = FontWeight.Black, fontSize = size, letterSpacing = (-0.01).em,
            lineHeight = size * 1.02f, color = Color.White)

    fun heading(size: TextUnit = 20.sp): TextStyle =
        TextStyle(fontFamily = condensed, fontWeight = FontWeight.ExtraBold, fontSize = size, letterSpacing = 0.em,
            lineHeight = size * 1.1f, color = Color.White)
}

@Composable
fun LiftrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = LiftrColors.accent,
            onPrimary = Color.White,
            secondary = LiftrColors.accent,
            background = LiftrColors.background,
            surface = LiftrColors.card,
            surfaceVariant = LiftrColors.cardRaised,
            onSurface = Color.White,
            onSurfaceVariant = LiftrColors.muted,
            onBackground = Color.White,
            surfaceContainer = LiftrColors.card,
            surfaceContainerHigh = LiftrColors.cardRaised,
            surfaceContainerHighest = LiftrColors.cardRaised,
            surfaceContainerLow = LiftrColors.card,
            outline = LiftrColors.muted,
            outlineVariant = LiftrColors.line,
            error = LiftrColors.danger,
        ),
        content = content,
    )
}

/** Rounded dark card. */
fun Modifier.liftrCard(padding: Dp = 16.dp, radius: Dp = 20.dp, color: Color = LiftrColors.card): Modifier =
    this.background(color, RoundedCornerShape(radius)).padding(padding)

fun Modifier.liftrCardBordered(padding: Dp = 16.dp, radius: Dp = 20.dp, border: Color = LiftrColors.line): Modifier =
    this.background(LiftrColors.card, RoundedCornerShape(radius)).border(1.dp, border, RoundedCornerShape(radius)).padding(padding)

val PagePadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
