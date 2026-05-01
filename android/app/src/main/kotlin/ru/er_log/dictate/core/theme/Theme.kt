package ru.er_log.dictate.core.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Main application theme composable.
 *
 * Applies Material 3 dynamic color on Android 12+ (API 31+) when available,
 * falling back to the seed-derived [LightColorScheme] / [DarkColorScheme] on
 * older API levels.
 *
 * @param darkTheme Whether to use the dark color scheme. Defaults to the
 *   system setting via [isSystemInDarkTheme].
 * @param content The composable content to be themed.
 */
@Composable
fun DictateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
