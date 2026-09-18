package com.swcsoftware.valuelens.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Mirrors apps/ios/ValueLens/DesignSystem/Theme.swift — keep in sync.
private val ValueLensColors = darkColorScheme(
    background = Color(0xFF0B0D10),
    surface = Color(0xFF15181D),
    primary = Color(0xFF2ED99E),      // intrinsic value: mint
    tertiary = Color(0xFFF5A623),     // market price: amber
    onBackground = Color(0xFFF2F2F2),
)

/**
 * Sprint-1 placeholder. Screens to port from iOS (see docs/TASKS.md):
 * onboarding (identity + case study), watchlist, search, company detail (Model A/B toggle,
 * margin-of-safety gauge, metric disclosure), settings, PDF/share export.
 */
@Composable
fun ValueLensApp() {
    MaterialTheme(colorScheme = ValueLensColors) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(24.dp)) {
                Text("ValueLens", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Android client scaffold. The valuation engine contract lives in domain/Models.kt.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                )
            }
        }
    }
}
