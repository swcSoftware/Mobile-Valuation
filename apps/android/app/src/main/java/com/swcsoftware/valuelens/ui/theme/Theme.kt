package com.swcsoftware.valuelens.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.swcsoftware.valuelens.domain.Verdict

/** Mirrors apps/ios/ValueLens/DesignSystem/Theme.swift. Dark by default (blueprint §5). */
object VL {
    val background = Color(0xFF0B0D10)
    val surface = Color(0xFF15181D)
    val raised = Color(0xFF1E2229)
    val border = Color(0x14FFFFFF)
    val textPrimary = Color(0xFFF2F2F2)
    val textSecondary = Color(0xFF9EA3AB)
    val textTertiary = Color(0xFF6B7079)
    val price = Color(0xFFF5A623)
    val value = Color(0xFF2ED99E)
    val danger = Color(0xFFEF5450)
    val warning = Color(0xFFFAC73F)
    val info = Color(0xFF5C9EFA)

    fun verdictColor(v: Verdict): Color = when (v) {
        Verdict.DEEP_VALUE, Verdict.WITHIN_MARGIN -> value
        Verdict.THIN_MARGIN -> warning
        Verdict.ABOVE_INTRINSIC -> danger
        Verdict.INSUFFICIENT -> textTertiary
    }
}

private val Colors = darkColorScheme(
    background = VL.background, surface = VL.surface, surfaceVariant = VL.raised,
    primary = VL.value, onPrimary = VL.background, secondary = VL.info, tertiary = VL.price,
    onBackground = VL.textPrimary, onSurface = VL.textPrimary, onSurfaceVariant = VL.textSecondary,
    outline = VL.border, error = VL.danger,
)

private val Type = Typography(
    displaySmall = TextStyle(fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp),
)

val Mono = FontFamily.Monospace

@Composable
fun ValueLensTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme() // dark-first regardless; kept for future light variant
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
