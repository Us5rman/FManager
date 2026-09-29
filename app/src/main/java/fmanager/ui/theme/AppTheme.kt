package fmanager.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

enum class RgbMode(val label: String) {
    SPECTRUM("Smooth Spectrum"),
    RIGHT_TO_LEFT("Right to Left"),
    LEFT_TO_RIGHT("Left to Right"),
    TOP_TO_BOTTOM("Top to Bottom"),
    BOTTOM_TO_TOP("Bottom to Top"),
    PULSE("Breathing Pulse"),
    CYBER_WAVE("Cyberpunk Wave")
}

data class ThemeSpec(
    val id: String,
    val title: String,
    val dark: Boolean,
    val primary: Color,
    val background: Color,
    val surface: Color,
    val gradient: List<Color>? = null
)

data class ThemePreset(
    val id: String,
    val title: String,
    val light: ThemeSpec,
    val dark: ThemeSpec
) {
    fun spec(systemDark: Boolean) = if (systemDark) dark else light
}

val presetThemes = listOf(
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
        "rgb", "RGB",
        light = ThemeSpec(
            "rgb", "RGB", false,
            primary = Color(0xFFFF0055),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFF2F2F7)
        ),
        dark = ThemeSpec(
            "rgb", "RGB", true,
            primary = Color(0xFF00FFCC),
            background = Color(0xFF000000),
            surface = Color(0xFF18181A)
        )
    ),
    ThemePreset(
        "glass", "Glass",
        light = ThemeSpec(
            "glass", "Glass", false,
            primary = Color(0xFF5B6CFF),
            background = Color(0xFFDDE7FF),
            surface = Color(0xFFEEF3FF),
            gradient = listOf(Color(0xFF9FD8FF), Color(0xFFD7B8FF), Color(0xFFFFC9E3))
        ),
        dark = ThemeSpec(
            "glass", "Glass", true,
            primary = Color(0xFF8C9BFF),
            background = Color(0xFF14172B),
            surface = Color(0xFF1E223D),
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
            surface = Color(0xFF121212)
        )
    ),
    ThemePreset(
        "sunset", "Ember",
        light = ThemeSpec(
            "sunset", "Ember", false,
            primary = Color(0xFFE65100),
            background = Color(0xFFFFEFE3),
            surface = Color(0xFFFFF2E8),
            gradient = listOf(Color(0xFFFFE0B2), Color(0xFFFFB199), Color(0xFFFF8FA3))
        ),
        dark = ThemeSpec(
            "sunset", "Ember", true,
            primary = Color(0xFFFFB74D),
            background = Color(0xFF2B1055),
            surface = Color(0xFF1E1035),
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

    private var _rgbMode by mutableStateOf(RgbMode.SPECTRUM)
    private var _rgbOutlineEnabled by mutableStateOf(false)
    // Animation speed in seconds, matching the slider on the Settings screen (3..30).
    private var _rgbSpeedSeconds by mutableStateOf(10f)

    val rgbMode: RgbMode get() = _rgbMode
    val rgbOutlineEnabled: Boolean get() = _rgbOutlineEnabled
    val rgbSpeedSeconds: Float get() = _rgbSpeedSeconds

    fun init(context: Context) {
        val p = context.applicationContext
            .getSharedPreferences("fmanager_theme", Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getString("theme", DEFAULT_ID) ?: DEFAULT_ID
        themeId = if (saved == "default" || saved == "forest") DEFAULT_ID else saved
        customAccent = p.getInt("accent", customAccent)
        customBackground = p.getInt("background", customBackground)

        val savedMode = p.getString("rgb_mode", RgbMode.SPECTRUM.name) ?: RgbMode.SPECTRUM.name
        _rgbMode = runCatching { RgbMode.valueOf(savedMode) }.getOrDefault(RgbMode.SPECTRUM)
        _rgbOutlineEnabled = p.getBoolean("rgb_outline", false)

        // Speed is stored by SettingsScreen under the shared "fmanager_settings" prefs file,
        // not "fmanager_theme", so read it from there too.
        val settingsPrefs = context.applicationContext
            .getSharedPreferences("fmanager_settings", Context.MODE_PRIVATE)
        _rgbSpeedSeconds = settingsPrefs.getFloat("rgb_speed", 10f)
    }

    fun setTheme(id: String) {
        themeId = id
        prefs?.edit()?.putString("theme", id)?.apply()
    }

    fun setRgbMode(mode: RgbMode) {
        _rgbMode = mode
        prefs?.edit()?.putString("rgb_mode", mode.name)?.apply()
    }

    fun setRgbOutline(enabled: Boolean) {
        _rgbOutlineEnabled = enabled
        prefs?.edit()?.putBoolean("rgb_outline", enabled)?.apply()
    }

    // Called by the Settings screen's slider so the live theme picks up the new speed immediately.
    fun setRgbSpeedSeconds(seconds: Float) {
        _rgbSpeedSeconds = seconds
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

@Composable
fun rememberRgbColor(
    mode: RgbMode = ThemeSettings.rgbMode,
    durationMillis: Int = (ThemeSettings.rgbSpeedSeconds * 1000).toInt().coerceAtLeast(500)
): Color {
    val transition = rememberInfiniteTransition(label = "rgb_anim")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    return when (mode) {
        RgbMode.SPECTRUM -> Color.hsv(progress * 360f, 0.85f, 1f)
        RgbMode.RIGHT_TO_LEFT -> Color.hsv((1f - progress) * 360f, 0.85f, 1f)
        RgbMode.LEFT_TO_RIGHT -> Color.hsv(progress * 360f, 0.85f, 1f)
        RgbMode.TOP_TO_BOTTOM -> Color.hsv((progress * 360f + 90f) % 360f, 0.85f, 1f)
        RgbMode.BOTTOM_TO_TOP -> Color.hsv((progress * 360f + 270f) % 360f, 0.85f, 1f)
        RgbMode.PULSE -> {
            val pulseVal = (kotlin.math.sin(progress * 2 * kotlin.math.PI).toFloat() + 1f) / 2f
            Color.hsv(280f, 0.4f + pulseVal * 0.6f, 0.6f + pulseVal * 0.4f)
        }
        RgbMode.CYBER_WAVE -> {
            val colors = listOf(Color(0xFFFF007F), Color(0xFF00E5FF), Color(0xFFFFEA00), Color(0xFF7C4DFF))
            val scaled = progress * (colors.size - 1)
            val idx = scaled.toInt()
            val fraction = scaled - idx
            val nextIdx = (idx + 1) % colors.size
            lerp(colors[idx], colors[nextIdx], fraction)
        }
    }
}

/**
 * Full rainbow ring used for the screen-edge glow, animated by rotating its
 * start angle over time. This is what actually gets drawn around the screen,
 * independent of whatever single flat color rememberRgbColor() returns.
 */
@Composable
private fun rememberRgbSweepRotation(durationMillis: Int): Float {
    val transition = rememberInfiniteTransition(label = "rgb_outline_anim")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    return rotation
}

private val rgbSweepColors = listOf(
    Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00FF00),
    Color(0xFF00FFFF), Color(0xFF0000FF), Color(0xFFFF00FF), Color(0xFFFF0000)
)

/**
 * Draws an animated neon-rainbow border around the full screen content,
 * matching a Pinterest-style "RGB edge glow" wallpaper: a rotating hue ring
 * traced along the outer edge with a soft outward bloom.
 */
@Composable
private fun Modifier.rgbScreenGlow(enabled: Boolean, durationSeconds: Float): Modifier {
    if (!enabled) return this
    val durationMillis = (durationSeconds * 1000).toInt().coerceAtLeast(500)
    val rotationDeg = rememberRgbSweepRotation(durationMillis)

    return this.drawWithContent {
        drawContent()

        val strokeWidthPx = 5.dp.toPx()
        val glowWidthPx = 18.dp.toPx()
        val diagonal = kotlin.math.sqrt(size.width * size.width + size.height * size.height)

        val brush = Brush.sweepGradient(
            colors = rgbSweepColors,
            center = Offset(size.width / 2f, size.height / 2f)
        )

        rotate(degrees = rotationDeg, pivot = Offset(size.width / 2f, size.height / 2f)) {
            // Soft outer bloom, then a crisp bright line on top of it.
            drawRect(
                brush = brush,
                topLeft = Offset.Zero,
                size = size,
                style = Stroke(width = glowWidthPx),
                alpha = 0.35f
            )
            drawRect(
                brush = brush,
                topLeft = Offset.Zero,
                size = size,
                style = Stroke(width = strokeWidthPx),
                alpha = 0.95f
            )
        }
    }
}

private fun buildScheme(s: ThemeSpec, activePrimary: Color, rgbOutline: Boolean): ColorScheme {
    val glass = s.gradient != null
    val onSurface = if (s.dark) Color(0xFFECE6F0) else Color(0xFF1C1B1F)
    val primaryColor = activePrimary
    val onPrimary = if (primaryColor.luminance() > 0.5f) Color.Black else Color.White

    val solidSurface = s.surface.copy(alpha = 1f)
    val primaryContainer = lerp(solidSurface, primaryColor, 0.25f)
    val page = if (glass) Color.Transparent else s.background

    val base = if (s.dark) darkColorScheme() else lightColorScheme()
    val outlineColor = if (rgbOutline) activePrimary.copy(alpha = 0.8f) else onSurface.copy(alpha = 0.4f)

    return base.copy(
        primary = primaryColor,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onSurface,
        secondary = primaryColor,
        onSecondary = onPrimary,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onSurface,
        background = page,
        onBackground = onSurface,
        surface = solidSurface,
        onSurface = onSurface,
        surfaceTint = Color.Transparent,
        surfaceVariant = lerp(solidSurface, onSurface, 0.08f),
        onSurfaceVariant = onSurface.copy(alpha = 0.7f),
        outline = outlineColor,
        outlineVariant = outlineColor.copy(alpha = 0.5f)
    )
}

@Composable
fun FManagerTheme(content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val spec = ThemeSettings.specFor(systemDark)
    val isRgb = ThemeSettings.themeId == "rgb"
    val dynamicRgbColor = rememberRgbColor()

    val currentPrimary = if (isRgb) dynamicRgbColor else spec.primary
    val rgbOutline = isRgb && ThemeSettings.rgbOutlineEnabled

    val scheme = remember(spec, currentPrimary, rgbOutline) {
        buildScheme(spec, currentPrimary, rgbOutline)
    }

    val bg = spec.gradient
        ?.let { Modifier.background(Brush.linearGradient(it)) }
        ?: Modifier.background(spec.background)

    MaterialTheme(colorScheme = scheme) {
        Box(
            Modifier
                .fillMaxSize()
                .then(bg)
                .rgbScreenGlow(enabled = rgbOutline, durationSeconds = ThemeSettings.rgbSpeedSeconds)
        ) { content() }
    }
}
