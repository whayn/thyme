package dev.whayn.thyme.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * Forest green is Thyme's accent. A tonal ramp rather than one value, because a
 * single green cannot serve both schemes: on paper the accent is the deep forest
 * (Forest40), on ink it has to lift to Forest80 to stay legible, with the deep
 * tones moving to containers so the forest is still what you see on the FAB.
 */
val Forest10 = Color(0xFF04210F)
val Forest20 = Color(0xFF0D3A1F)
val Forest30 = Color(0xFF17512D)
val Forest40 = Color(0xFF22693C) // light-mode accent true forest green
val Forest50 = Color(0xFF2F844E)
val Forest70 = Color(0xFF5FB57C)
val Forest80 = Color(0xFF7ECD95) // dark-mode accent
val Forest90 = Color(0xFFA6E8B8)

// Dark surfaces  green-black, wet soil at night.
val Ink = Color(0xFF0D1411)
val InkDeep = Color(0xFF080E0B)
val Bed = Color(0xFF151E19)
val BedHigh = Color(0xFF1C2721)
val BedHighest = Color(0xFF232F29)
val Stem = Color(0xFF2A3A31)
val StemLit = Color(0xFF41584A)
val Bracken = Color(0xFF5A7263) // outline  must read as a tap affordance
val Chalk = Color(0xFFE6EDE4)
val Moss = Color(0xFF8DA087)
val Rust = Color(0xFFE59684)

// Light surfaces  warm paper with a green cast.
//
// A strictly monotonic ramp: the page is the lightest thing, and each container
// level steps *darker* so raising a surface raises its emphasis. That is the
// M3 convention, and it is what makes "taken" (low) recede while "untaken"
// (container) reads as a raised card. An earlier ramp had `container` and
// `containerLowest` both at pure white with `containerLow` darker between them,
// which inverted the pair and left taken cards two percent off the background.
val Paper = Color(0xFFF7FAF4)
val PaperLow = Color(0xFFF2F6EE)
val PaperBed = Color(0xFFEDF2E8)
val PaperHigh = Color(0xFFE6EDE0)
val PaperHighest = Color(0xFFDEE7D8)
val BarkDark = Color(0xFF18231D)
val Slate = Color(0xFF4A5A4E)
val Hedge = Color(0xFF72846D)

// "Due"  warm attention, deliberately never red. Missing a dose is not a failure.
val Honey = Color(0xFFE0B368)
val HoneyDeep = Color(0xFF483819)
val HoneyDark = Color(0xFF7A5514)
val HoneyLight = Color(0xFFFFE0AE)

// Thyme's actual blossom is a pale lilac.
val Flower = Color(0xFFC7A6BE)
val FlowerDeep = Color(0xFF423240)
val FlowerDark = Color(0xFF6E5169)
val FlowerLight = Color(0xFFF7D8EE)

/*
 * Medication colours. Stored on Medication as an *index*, not an ARGB value, so
 * one saved choice can render as a light tone on ink and a deep tone on paper.
 * Order matches Samsung Health palette: white, grey, yellow, beige, pink, lime,
 * green, teal, blue, purple, red, orange.
 */
// Deliberately no honey here: honey means "due", and a medication wearing it
// would be indistinguishable from an overdue one at a glance.
val MedicationColorsDark = listOf(
    Color(0xFFFFFFFF), // white
    Color(0xFFB0B3B8), // grey
    Color(0xFFF5D166), // yellow
    Color(0xFFE8B89B), // beige
    Color(0xFFF4A7B9), // pink
    Color(0xFFB8E362), // lime
    Color(0xFF68C97B), // green
    Color(0xFF68D4D6), // teal
    Color(0xFF708FF0), // blue
    Color(0xFFAC8CE8), // purple
    Color(0xFFF06C62), // red
    Color(0xFFED8537), // orange
)

val MedicationColorsLight = listOf(
    Color(0xFF8C918C), // white (muted outline/tone for visibility on light paper)
    Color(0xFF6B7280), // grey
    Color(0xFFD97706), // yellow
    Color(0xFFB45309), // beige
    Color(0xFFDB2777), // pink
    Color(0xFF65A30D), // lime
    Color(0xFF16A34A), // green
    Color(0xFF0D9488), // teal
    Color(0xFF2563EB), // blue
    Color(0xFF7C3AED), // purple
    Color(0xFFDC2626), // red
    Color(0xFFEA580C), // orange
)

val MedicationColorNames = listOf(
    "White", "Grey", "Yellow", "Beige", "Pink", "Lime",
    "Green", "Teal", "Blue", "Purple", "Red", "Orange",
)

val ThymeDarkScheme = darkColorScheme(
    primary = Forest80,
    onPrimary = Forest10,
    primaryContainer = Forest30,
    onPrimaryContainer = Forest90,
    inversePrimary = Forest40,

    secondary = Moss,
    onSecondary = Color(0xFF13201A),
    secondaryContainer = Color(0xFF26342B),
    onSecondaryContainer = Color(0xFFD3DECD),

    tertiary = Flower,
    onTertiary = Color(0xFF2A1E27),
    tertiaryContainer = FlowerDeep,
    onTertiaryContainer = Color(0xFFE9CDE1),

    background = Ink,
    onBackground = Chalk,
    surface = Ink,
    onSurface = Chalk,
    surfaceVariant = Stem,
    onSurfaceVariant = Moss,
    surfaceTint = Forest80,

    surfaceContainerLowest = InkDeep,
    surfaceContainerLow = Color(0xFF111915),
    surfaceContainer = Bed,
    surfaceContainerHigh = BedHigh,
    surfaceContainerHighest = BedHighest,

    outline = Bracken,
    outlineVariant = Stem,

    error = Rust,
    onError = Color(0xFF3C1109),
    errorContainer = Color(0xFF5E2417),
    onErrorContainer = Color(0xFFFFDAD2),

    inverseSurface = Chalk,
    inverseOnSurface = Ink,
    scrim = Color(0xFF000000),
)

val ThymeLightScheme = lightColorScheme(
    primary = Forest40,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Forest90,
    onPrimaryContainer = Forest10,
    inversePrimary = Forest80,

    secondary = Slate,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E3CF),
    onSecondaryContainer = Color(0xFF0F1E14),

    tertiary = FlowerDark,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = FlowerLight,
    onTertiaryContainer = Color(0xFF280F24),

    background = Paper,
    onBackground = BarkDark,
    surface = Paper,
    onSurface = BarkDark,
    surfaceVariant = PaperHighest,
    onSurfaceVariant = Slate,
    surfaceTint = Forest40,

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = PaperLow,
    surfaceContainer = PaperBed,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = PaperHighest,

    outline = Hedge,
    outlineVariant = Color(0xFFC4D1BF),

    error = Color(0xFF9C4230),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD2),
    onErrorContainer = Color(0xFF3C1109),

    inverseSurface = BarkDark,
    inverseOnSurface = Paper,
    scrim = Color(0xFF000000),
)
