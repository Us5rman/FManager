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

val presetThemes = listOf(
    ThemeSpec(
        "glass", "Frost", false,
        primary = Color(0xFF5B6CFF),
        background = Color(0xFFDDE7FF),
        surface = Color(0x99FFFFFF),
        gradient = listOf(Color(0xFF9FD8FF), Color(0xFFD7B8FF), Color(0xFFFFC9E3))
    ),
    ThemeSpec(
        "midnight", "Void", true,
        primary = Color(0xFF00E5FF),
        background = Color(0xFF000000),
        surface = Color(0xFF0B0B0F)
    ),
    ThemeSpec(
        "sunset", "Ember", true,
        primary = Color(0xFFFFB74D),
        background = Color(0xFF2B1055),
        surface = Color(0x66000000),
        gradient = listOf(Color(0xFF2B1055), Color(0xFF8E2DE2), Color(0xFFFF6E7F))
    ),
    ThemeSpec(
        "forest", "Moss", true,
        primary = Color(0xFF7BE0A4),
        background = Color(0xFF0E1F17),
        surface = Color(0xFF16302A)
    ),
    ThemeSpec(
        "ocean", "Tide", false,
        primary = Color(0xFF0277BD),
        background = Color(0xFFEAF6FB),
        surface = Color(0xFFFFFFFF)
    )
)

object ThemeSettings {
    private var prefs: SharedPreferences? = null

    var themeId by mutableStateOf("default")
        private set
    var customAccent by mutableStateOf(0xFF2563EB.toInt())
        private set
    var customBackground by mutableStateOf(0xFFF8F9FB.toInt())
        private set

    fun init(context: Context) {
        val p = context.applicationContext
            .getSharedPreferences("fmanager_theme", Context.MODE_PRIVATE)
        prefs = p
        themeId = p.getString("theme", "default") ?: "default"
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

    fun defaultSpec(systemDark: Boolean) =
        if (systemDark) ThemeSpec(
            "default", "Auto", true,
            primary = Color(0xFF7AA2FF),
            background = Color(0xFF111318),
            surface = Color(0xFF1B1E25)
        ) else ThemeSpec(
            "default", "Auto", false,
            primary = Color(0xFF2563EB),
            background = Color(0xFFF8F9FB),
            surface = Color(0xFFEEF0F4)
        )

    fun customSpec(): ThemeSpec {
        val bg = Color(customBackground)
        val dark = bg.luminance() < 0.5f
        val on = if (dark) Color(0xFFECE6F0) else Color(0xFF1C1B1F)
        return ThemeSpec(
            "custom", "Yours", dark,
            primary = Color(customAccent),
            background = bg,
            surface = lerp(bg, on, 0.08f)
        )
    }

    fun specFor(systemDark: Boolean): ThemeSpec = when (themeId) {
        "default" -> defaultSpec(systemDark)
        "custom" -> customSpec()
        else -> presetThemes.firstOrNull { it.id == themeId } ?: defaultSpec(systemDark)
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
        outlineVariant = onSurface.copy(alpha = 0.2f),
        surfaceContainerLowest = tone(0.0f, 0.10f),
        surfaceContainerLow = tone(0.02f, 0.20f),
        surfaceContainer = tone(0.04f, 0.30f),
        surfaceContainerHigh = tone(0.08f, 0.40f),
        surfaceContainerHighest = tone(0.12f, 0.50f)
    )
}

@Composable
fun FManagerTheme(content: @Composable () -> Unit) {
    val spec = ThemeSettings.specFor(isSystemInDarkTheme())
    val scheme = remember(spec) { buildScheme(spec) }
    val bg = spec.gradient
        ?.let { Modifier.background(Brush.linearGradient(it)) }
        ?: Modifier.background(spec.background)

    MaterialTheme(colorScheme = scheme) {
        Box(Modifier.fillMaxSize().then(bg)) { content() }
    }
}
