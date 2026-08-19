package dev.whayn.thyme.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.whayn.thyme.MedicationsState
import dev.whayn.thyme.data.MedicationWithRegimens
import dev.whayn.thyme.data.Recurrence
import dev.whayn.thyme.data.RegimenWithDoses
import dev.whayn.thyme.ui.theme.ThymeDimens
import dev.whayn.thyme.ui.theme.ThymeTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val courseTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
fun MedicationsScreen(
    state: MedicationsState,
    onOpenMedication: (medicationId: Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (state.loading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val medications = state.medications
    val today = remember { LocalDate.now() }

    if (medications.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("No medications", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Use the + button to build your daily schedule.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PageHeader(eyebrow = "Medications", title = "Your courses")
        }
        items(medications, key = { it.medication.id }) { item ->
            MedicationCard(
                item = item,
                today = today,
                onClick = { onOpenMedication(item.medication.id) },
                modifier = Modifier.padding(horizontal = ThymeDimens.PageGutter),
            )
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun MedicationCard(
    item: MedicationWithRegimens,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = ThymeTheme.accents.medicationColor(item.medication.colorIndex)
    val accentRight = ThymeTheme.accents.medicationColor(item.medication.colorIndexRight)
    // The list answers "what am I taking", so stopped courses are counted rather
    // than listed, because three ended courses used to fill the card and bury the one
    // that was actually running.
    val current = item.currentRegimens(today)
    val stoppedCount = item.stoppedRegimens(today).size

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        // One padding value on all four sides. The courses used to sit in a
        // nested surface inset 24dp on the left and 10dp on the right, which
        // read as visibly off-centre and cost roughly 100dp of height per card.
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MedicationAvatar(
                formIndex = item.medication.form,
                colorLeft = accent,
                colorRight = accentRight,
            )
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(item.medication.name, style = MaterialTheme.typography.titleLarge)
                item.medication.strength?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (current.isEmpty()) {
                    Text(
                        "Not currently taking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    current.forEachIndexed { index, course ->
                        // A rule between courses, because two of them stacked as
                        // plain text ran together into one four-line block with
                        // no way to see where the first ended.
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        CourseSummary(course = course, today = today)
                    }
                }
                if (stoppedCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (stoppedCount == 1) "1 stopped course"
                        else "$stoppedCount stopped courses",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * A course as one or two lines of text rather than a nested card.
 *
 * The medication card is already a container; putting a second surface inside it
 * to hold two short lines was box-in-a-box, and it is what made the list scroll
 * four medications to a screen.
 */
@Composable
private fun CourseSummary(
    course: RegimenWithDoses,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val times = course.activeDoses.joinToString(" · ") { it.time.format(courseTimeFormatter) }

    Column(modifier = modifier) {
        Text(
            text = Recurrence.summarise(course.regimen, today),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (times.isNotEmpty()) {
            Text(
                text = times,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
