package dev.whayn.thyme.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import dev.whayn.thyme.data.ThymeThemeMode

/**
 * Material's scheme has no role meaning "overdue but not an error", so Thyme
 * adds its own. Extending the theme this way, rather than reaching for a
 * hardcoded Color at the call site, keeps every colour swappable at once.
 */
@Immutable
data class ThymeAccents(
    val due: Color,
    val onDue: Color,
    val dueContainer: Color,
    val stem: Color,
    val stemSpent: Color,
    /** Per-medication colours, indexed by `Medication.colorIndex`. */
    val medication: List<Color>,
) {
    /** Safe lookup: an index from an older build never crashes the list. */
    fun medicationColor(index: Int): Color =
        medication.getOrElse(index) { medication.first() }
}

private val DarkAccents = ThymeAccents(
    due = Honey,
    onDue = Color(0xFF3A2A0B),
    dueContainer = HoneyDeep,
    stem = Stem,
    stemSpent = StemLit,
    medication = MedicationColorsDark,
)

private val LightAccents = ThymeAccents(
    due = HoneyDark,
    onDue = Color(0xFFFFFFFF),
    dueContainer = HoneyLight,
    stem = Color(0xFFC4D1BF),
    stemSpent = Hedge,
    medication = MedicationColorsLight,
)

val LocalThymeAccents = staticCompositionLocalOf { DarkAccents }

/** Reads Thyme's own colour roles: `ThymeTheme.accents.due`. */
object ThymeTheme {
    val accents: ThymeAccents
        @Composable @ReadOnlyComposable get() = LocalThymeAccents.current
}

/**
 * True when the user has turned animations off system-wide (Accessibility →
 * Remove animations, or Developer options). Compose has no built-in flag, so
 * we read the platform setting and let callers snap instead of animate.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }
}

@Composable
fun ThymeTheme(
    mode: ThymeThemeMode = ThymeThemeMode.System,
    // Off by default: Thyme has a point of view about its own colour. The
    // settings screen can offer this as "match my wallpaper".
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThymeThemeMode.System -> isSystemInDarkTheme()
        ThymeThemeMode.Light -> false
        ThymeThemeMode.Dark -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        dark -> ThymeDarkScheme
        else -> ThymeLightScheme
    }

    CompositionLocalProvider(LocalThymeAccents provides if (dark) DarkAccents else LightAccents) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ThymeTypography,
            shapes = ThymeShapes,
            content = content,
        )
    }
}
