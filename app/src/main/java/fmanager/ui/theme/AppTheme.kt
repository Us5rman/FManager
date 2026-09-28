package fmanager.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

// surface = base color for menus, sheets and dialogs (the top bar always uses the background)
data class ThemeSpec(
    val id: String,
    val title: String,
    val dark: Boolean,
    val primary: Color,
    val background: Color,
    val surface: Color,
    val gradient: List<Color>? = null
)

// Every preset has a light and a dark look. The one used follows the phone's setting.
data class ThemePreset(
    val id: String,
    val title: String,
    val light: ThemeSpec,
    val dark: ThemeSpec
) {
    fun spec(systemDark: Boolean) = if (systemDark) dark else light
}

val presetThemes = listOf(
    // Fluorite: emerald-green, like the app icon. This is the default theme.
    ThemePreset(
        "fluorite", "Fluorite",
        light = ThemeSpec(
            "fluorite", "Fluorite", false,
            primary = Color(0xFF10B981),
            background = Color(0xFFE6F8EE),
            surface = Color(0xFFCFF1DF),
            gradient = listOf(Color(0xFFE9FBF1), Color(0xFFCDEFDD), Color(0xFFA9E4C7))
        ),
        dark = ThemeSpec(
            "fluorite", "Fluorite", true,
            primary = Color(0xFF2ECC71),
            background = Color(0xFF06170F),
            surface = Color(0xFF123626),
            gradient = listOf(Color(0xFF041009), Color(0xFF0A2618), Color(0xFF104A31))
        )
    ),
    ThemePreset(
        "glass", "Glass",
        light = ThemeSpec(
            "glass", "Glass", false,
            primary = Color(0xFF5B6CFF),
            background = Color(0xFFDDE7FF),
            surface = Color(0x99FFFFFF),
            gradient = listOf(Color(0xFF9FD8FF), Color(0xFFD7B8FF), Color(0xFFFFC9E3))
        ),
        dark = ThemeSpec(
            "glass", "Glass", true,
            primary = Color(0xFF8C9BFF),
            background = Color(0xFF14172B),
            surface = Color(0x66FFFFFF),
            gradient = listOf(Color(0xFF16233F), Color(0xFF2A1F4A), Color(0xFF3F1F3A))
        )
    ),
    ThemePreset(
        "midnight", "Void",
        light = ThemeSpec(
            "midnight", "Void", false,
            primary = Color(0xFF00838F),
            background = Color(0xFFF5F7F8),
            surface = Color(0xFFFFFFFF)
        ),
        dark = ThemeSpec(
            "midnight", "Void", true,
            primary = Color(0xFF00E5FF),
            background = Color(0xFF000000),
            surface = Color(0xFF0B0B0F)
        )
    ),
    ThemePreset(
        "sunset", "Ember",
        light = ThemeSpec(
            "sunset", "Ember", false,
            primary = Color(0xFFE65100),
            background = Color(0xFFFFEFE3),
            surface = Color(0x99FFFFFF),
            gradient = listOf(Color(0xFFFFE0B2), Color(0xFFFFB199), Color(0xFFFF8FA3))
        ),
        dark = ThemeSpec(
            "sunset", "Ember", true,
            primary = Color(0xFFFFB74D),
            background = Color(0xFF2B1055),
            surface = Color(0x66000000),
            gradient = listOf(Color(0xFF2B1055), Color(0xFF8E2DE2), Color(0xFFFF6E7F))
        )
    ),
    ThemePreset(
        "ocean", "Tide",
        light = ThemeSpec(
            "ocean", "Tide", false,
            primary = Color(0xFF0277BD),
            background = Color(0xFFEAF6FB),
            surface = Color(0xFFFFFFFF)
        ),
        dark = ThemeSpec(
            "ocean", "Tide", true,
            primary = Color(0xFF4FC3F7),
            background = Color(0xFF07161F),
            surface = Color(0xFF0F2733)
        )
    )
)

object ThemeSettings {
    const val DEFAULT_ID = "fluorite"

    private var prefs: SharedPreferences? = null

    var themeId by mutableStateOf(DEFAULT_ID)
        private set
    var customAccent by mutableStateOf(0xFF10B981.toInt())
        private set
    var customBackground by mutableStateOf(0xFFE6F8EE.toInt())
        private set

    fun init(context: Context) {
        val p = context.applicationContext
            .getSharedPreferences("fmanager_theme", Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString("theme", DEFAULT_ID) ?: DEFAULT_ID
        // "default" was the old name of the Auto theme, which is now Fluorite
        themeId = if (saved == "default" || saved == "forest") DEFAULT_ID else saved
        customAccent = p.getInt("accent", customAccent)
        customBackground = p.getInt("background", customBackground)
    }

    fun setTheme(id: String) {
        themeId = id
        prefs?.edit()?.putString("theme", id)?.apply()
    }

    fun updateCustom(accent: Int? = null, background: Int? = null) {
        accent?.let { customAccent = it }
        background?.let { customBackground = it }
        themeId = "custom"
        prefs?.edit()
            ?.putString("theme", "custom")
            ?.putInt("accent", customAccent)
            ?.putInt("background", customBackground)
            ?.apply()
    }

    fun defaultSpec(systemDark: Boolean): ThemeSpec =
        presetThemes.first { it.id == DEFAULT_ID }.spec(systemDark)

    fun customSpec(): ThemeSpec {
        val bg = Color(customBackground)
        val dark = bg.luminance() < 0.5f
        val on = if (dark) Color(0xFFECE6F0) else Color(0xFF1C1B1F)
        return ThemeSpec(
            "custom", "Custom", dark,
            primary = Color(customAccent),
            background = bg,
            surface = lerp(bg, on, 0.08f)
        )
    }

    fun specFor(systemDark: Boolean): ThemeSpec = when (themeId) {
        "custom" -> customSpec()
        else -> presetThemes.firstOrNull { it.id == themeId }?.spec(systemDark)
            ?: defaultSpec(systemDark)
    }
}

private fun buildScheme(s: ThemeSpec): ColorScheme {
    val glass = s.gradient != null
    val onSurface = if (s.dark) Color(0xFFECE6F0) else Color(0xFF1C1B1F)
    val onPrimary = if (s.primary.luminance() > 0.5f) Color.Black else Color.White
    val solidBase = s.surface.copy(alpha = 1f)
    val primaryContainer = lerp(solidBase, s.primary, 0.3f)
    // The top bar (and anything using "surface") always matches the background
    val page = if (glass) Color.Transparent else s.background

    fun tone(solid: Float, glassAlpha: Float): Color =
        if (glass) s.surface.copy(alpha = (s.surface.alpha + glassAlpha).coerceAtMost(0.96f))
        else lerp(s.surface, onSurface, solid)

    val base = if (s.dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = s.primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onSurface,
        secondary = s.primary,
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onSurface,
        background = page,
        onBackground = onSurface,
        surface = page,
        onSurface = onSurface,
        surfaceTint = Color.Transparent,
        surfaceVariant = tone(0.08f, 0.15f),
        onSurfaceVariant = onSurface.copy(alpha = 0.7f),
        outline = onSurface.copy(alpha = 0.4f),
        outlineVariant = onSurface.copy(alpha = 0.2f)
    )
}

@Composable
fun FManagerTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val spec = ThemeSettings.specFor(systemDark)
    val scheme = remember(spec) { buildScheme(spec) }
    val bg = spec.gradient
        ?.let { Modifier.background(Brush.linearGradient(it)) }
        ?: Modifier.background(spec.background)

    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize().then(bg)) { content() }
    }
}
