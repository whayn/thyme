package dev.whayn.thyme.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Expressive uses a wider spread of radii than baseline M3 so that shape itself
 * signals hierarchy: chips stay tight, cards get generous, the FAB goes nearly
 * round. Uniform 12dp everywhere is what makes Material apps read as templated.
 */
val ThymeShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)
