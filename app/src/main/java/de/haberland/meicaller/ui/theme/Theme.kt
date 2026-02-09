package de.haberland.meicaller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        val v = clean.toLong(16)
        when (clean.length) {
            6 -> Color((0xFF000000 or v).toInt())
            8 -> Color(v.toInt())
            else -> fallback
        }
    } catch (_: Throwable) {
        fallback
    }
}

@Composable
fun MeiCallerTheme(
    primaryHex: String? = "#B39DDB",
    accentHex: String = "#7C4DFF",
    content: @Composable (() -> Unit)
) {
    val primary = parseHexColor(primaryHex ?: "#B39DDB", Color(0xFFB39DDB))
    val accent = parseHexColor(accentHex, Color(0xFF7C4DFF))

    val scheme = darkColorScheme(
        primary = primary,
        secondary = accent
    )

    MaterialTheme(
        colorScheme = scheme,
        typography = Typography,
        content = content
    )
}
