package dev.whayn.thyme.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.whayn.thyme.data.Recurrence
import dev.whayn.thyme.data.RegimenWithDoses
import dev.whayn.thyme.ui.theme.MedicationPillIcon
import dev.whayn.thyme.ui.theme.ThymeDimens
import dev.whayn.thyme.ui.theme.ThymeTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** One formatter for every list that prints a dose time. */
internal val doseTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

/**
 * The small uppercase label that introduces a block: "MORNING", "COURSES",
 * "TIMES". One composable so the style and colour cannot drift; previously the
 * tab headers coloured it `primary` and the editors `onSurfaceVariant`.
 */
@Composable
internal fun SectionEyebrow(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

/**
 * The masthead used by every tab: coloured eyebrow over a large title.
 *
 * Shared so the four tabs cannot drift onto different display sizes, which is
 * how Today ended up at `displayMedium` and the other three at `displaySmall`.
 */
@Composable
internal fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    /** Null for a title that stands on its own, like Settings. */
    eyebrow: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ThymeDimens.PageGutter,
                end = ThymeDimens.PageGutter,
                top = 20.dp,
                bottom = 8.dp,
            ),
    ) {
        if (eyebrow != null) {
            SectionEyebrow(eyebrow)
            Spacer(Modifier.height(4.dp))
        }
        Text(title, style = MaterialTheme.typography.displaySmall)
        content?.invoke()
    }
}

/**
 * The medication's pill, in the tinted circle it wears inside a card.
 *
 * Shape and colour are the whole recognition system, with a three step
 * editor devoted to picking them, so the icon is sized to be seen rather than
 * tucked in beside the name at the height of a single line of text.
 */
@Composable
internal fun MedicationAvatar(
    formIndex: Int,
    colorLeft: Color,
    colorRight: Color = colorLeft,
    modifier: Modifier = Modifier,
    containerSize: Dp = ThymeDimens.PillIconContainer,
    iconSize: Dp = ThymeDimens.PillIcon,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(containerSize)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        MedicationPillIcon(
            formIndex = formIndex,
            colorLeft = colorLeft,
            colorRight = colorRight,
            modifier = Modifier.size(iconSize),
            contentDescription = contentDescription,
        )
    }
}

/**
 * One course: its recurrence rule and the times inside it.
 *
 * Lives here because the medications list and the medication detail screen both
 * render it. They previously held byte-identical private copies that had already
 * started to diverge: only one of them was clickable.
 */
@Composable
internal fun CourseRow(
    course: RegimenWithDoses,
    today: LocalDate,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    // A finished course stays listed, since its history is still real, but recedes so
    // the list reads as "what I'm taking" at a glance.
    val finished = course.regimen.endDate?.isBefore(today) == true
    val times = course.activeDoses.joinToString(" · ") { it.time.format(doseTimeFormatter) }
    val shape = MaterialTheme.shapes.small

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)
                .alpha(if (finished) 0.55f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = Recurrence.summarise(course.regimen, today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (times.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = times,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke()
        }
    }
}

/**
 * An eyebrow over a card, the app's one way of grouping controls on a page.
 *
 * Settings had this shape first; sharing it means a new group of controls
 * cannot quietly arrive as bare rows on the background.
 */
@Composable
internal fun SectionCard(
    eyebrow: String,
    modifier: Modifier = Modifier,
    /** Zero when the caller already insets its content, as the editors do. */
    gutter: Dp = ThymeDimens.PageGutter,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.padding(horizontal = gutter)) {
        SectionEyebrow(eyebrow, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

/**
 * Stop and delete, for a medication or for one course.
 *
 * Deliberately the same shape as the Developer group in Settings: an outlined
 * button for the reversible action beside a text button in `error` for the one
 * that is not. Two earlier attempts (a docked bar of bare text buttons, then
 * full-width list rows) both invented a control the app uses nowhere else. This
 * one is already the app's vocabulary for exactly this pair.
 */
@Composable
internal fun ManageCard(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    stopLabel: String? = null,
    onStop: (() -> Unit)? = null,
    deleteLabel: String = "Delete",
    gutter: Dp = ThymeDimens.PageGutter,
) {
    SectionCard(eyebrow = "Manage", modifier = modifier, gutter = gutter) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (stopLabel != null && onStop != null) {
                OutlinedButton(onClick = onStop) { Text(stopLabel) }
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text(deleteLabel) }
        }
    }
}

/** The medication's colours, resolved for the current theme. */
@Composable
internal fun medicationColors(colorIndex: Int, colorIndexRight: Int): Pair<Color, Color> {
    val accents = ThymeTheme.accents
    return accents.medicationColor(colorIndex) to accents.medicationColor(colorIndexRight)
}
