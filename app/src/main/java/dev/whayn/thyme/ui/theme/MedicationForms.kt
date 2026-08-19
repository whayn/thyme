package dev.whayn.thyme.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import dev.whayn.thyme.R

/**
 * How a medication is taken/shaped. `Medication.form` stores an index into
 * [entries].
 */
data class MedicationForm(
    val label: String,
    @DrawableRes val iconRes: Int,
    val supportsTwoSides: Boolean = true,
)

object MedicationForms {

    val entries: List<MedicationForm> = listOf(
        MedicationForm("Capsule", R.drawable.ic_form_capsule, supportsTwoSides = true),
        MedicationForm("Round", R.drawable.ic_form_tablet_round, supportsTwoSides = true),
        MedicationForm("Oval", R.drawable.ic_form_tablet_oval, supportsTwoSides = true),
        MedicationForm("Caplet", R.drawable.ic_form_tablet_caplet, supportsTwoSides = true),
        MedicationForm("Square", R.drawable.ic_form_tablet_square, supportsTwoSides = true),
        MedicationForm("Diamond", R.drawable.ic_form_tablet_diamond, supportsTwoSides = true),
        MedicationForm("Triangle", R.drawable.ic_form_tablet_triangle, supportsTwoSides = true),
        MedicationForm("Pentagon", R.drawable.ic_form_tablet_pentagon, supportsTwoSides = true),
        MedicationForm("Softgel", R.drawable.ic_form_softgel, supportsTwoSides = true),
        MedicationForm("Liquid", R.drawable.ic_form_liquid, supportsTwoSides = false),
        MedicationForm("Drops", R.drawable.ic_form_drops, supportsTwoSides = false),
        MedicationForm("Spray", R.drawable.ic_form_spray, supportsTwoSides = false),
        MedicationForm("Inhaler", R.drawable.ic_form_inhaler, supportsTwoSides = false),
        MedicationForm("Injection", R.drawable.ic_form_injection, supportsTwoSides = false),
        MedicationForm("Cream", R.drawable.ic_form_cream, supportsTwoSides = false),
        MedicationForm("Patch", R.drawable.ic_form_patch, supportsTwoSides = false),
        MedicationForm("Sachet", R.drawable.ic_form_sachet, supportsTwoSides = false),
        MedicationForm("Suppository", R.drawable.ic_form_suppository, supportsTwoSides = false),
        MedicationForm("Other", R.drawable.ic_form_other, supportsTwoSides = false),
    )

    fun entry(index: Int): MedicationForm = entries.getOrElse(index) { entries.first() }
}

private val LeftHalfShape = GenericShape { size, _ ->
    addRect(Rect(0f, 0f, size.width / 2f, size.height))
}
private val RightHalfShape = GenericShape { size, _ ->
    addRect(Rect(size.width / 2f, 0f, size.width, size.height))
}

/**
 * Renders the form icon with left and right side colors if split, or solid color.
 */
@Composable
fun MedicationPillIcon(
    formIndex: Int,
    colorLeft: Color,
    colorRight: Color = colorLeft,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val form = MedicationForms.entry(formIndex)
    if (!form.supportsTwoSides || colorLeft == colorRight) {
        Icon(
            painter = painterResource(form.iconRes),
            contentDescription = contentDescription ?: form.label,
            tint = colorLeft,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(LeftHalfShape),
            ) {
                Icon(
                    painter = painterResource(form.iconRes),
                    contentDescription = null,
                    tint = colorLeft,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RightHalfShape),
            ) {
                Icon(
                    painter = painterResource(form.iconRes),
                    contentDescription = null,
                    tint = colorRight,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
