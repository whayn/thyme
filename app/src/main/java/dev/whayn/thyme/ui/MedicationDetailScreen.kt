package dev.whayn.thyme.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.whayn.thyme.MedicationDetailState
import dev.whayn.thyme.MedicationHistory
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.MedicationWithRegimens
import dev.whayn.thyme.ui.theme.MedicationForms
import dev.whayn.thyme.ui.theme.ThymeDimens
import dev.whayn.thyme.ui.theme.ThymeTheme
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailScreen(
    state: MedicationDetailState,
    onEditMedication: () -> Unit,
    onAddCourse: () -> Unit,
    onEditCourse: (regimenId: Long) -> Unit,
    onStopAll: () -> Unit,
    onDeleteMedication: () -> Unit,
    onAlertSettingsChange: (AlertTier, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete medication?") },
            text = { Text("This hides it from every date. Its history will no longer be shown.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; onDeleteMedication() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (state.loading || state.medication == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { Text("Loading medication...") }
        return
    }

    val medication = state.medication
    val today = LocalDate.now()
    // Split rather than one list: a stopped course keeps its history and stays
    // listed, but it should not sit among the ones you are actually taking.
    val current = medication.currentRegimens(today)
    val stopped = medication.stoppedRegimens(today)
    val canStop = medication.stoppableRegimens(today).isNotEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                // The hero directly below carries the name at a readable size,
                // so repeating it here in 19sp is redundant chrome.
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditMedication) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit medication")
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        // `modifier` belongs to the Scaffold alone. Applying it here as well
        // meant any padding a caller passed was added twice.
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = scaffoldPadding,
        ) {
            item(key = "identity") {
                IdentityHero(medication)
            }
            item(key = "history") {
                HistoryCard(
                    history = state.history,
                    accent = ThymeTheme.accents.medicationColor(medication.medication.colorIndex),
                    modifier = Modifier.padding(
                        horizontal = ThymeDimens.PageGutter,
                        vertical = 4.dp,
                    ),
                )
            }
            item(key = "courses-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = ThymeDimens.PageGutter,
                            end = ThymeDimens.PageGutter - 8.dp,
                            top = 20.dp,
                            bottom = 4.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionEyebrow("Courses", modifier = Modifier.weight(1f))
                    TextButton(onClick = onAddCourse) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("Add course")
                    }
                }
            }

            if (current.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            if (stopped.isEmpty()) "No courses yet" else "Not currently taking",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Add a course to schedule when and how much to take.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onAddCourse) { Text("Add course") }
                    }
                }
            } else {
                items(current, key = { it.regimen.id }) { course ->
                    CourseRow(
                        course = course,
                        today = today,
                        onClick = { onEditCourse(course.regimen.id) },
                        trailing = { GoChevron() },
                        modifier = Modifier.padding(
                            horizontal = ThymeDimens.PageGutter,
                            vertical = 4.dp,
                        ),
                    )
                }
            }

            if (stopped.isNotEmpty()) {
                item(key = "stopped-header") {
                    SectionEyebrow(
                        "Stopped",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = ThymeDimens.PageGutter,
                            top = 24.dp,
                            bottom = 6.dp,
                        ),
                    )
                }
                items(stopped, key = { it.regimen.id }) { course ->
                    CourseRow(
                        course = course,
                        today = today,
                        onClick = { onEditCourse(course.regimen.id) },
                        trailing = { GoChevron() },
                        modifier = Modifier.padding(
                            horizontal = ThymeDimens.PageGutter,
                            vertical = 4.dp,
                        ),
                    )
                }
            }

            item(key = "reminders") {
                Spacer(Modifier.height(28.dp))
                RemindersCard(
                    tier = medication.medication.alertTier,
                    critical = medication.medication.critical,
                    onChange = onAlertSettingsChange,
                )
            }

            item(key = "manage") {
                Spacer(Modifier.height(28.dp))
                ManageCard(
                    stopLabel = if (canStop) "Stop taking" else null,
                    onStop = if (canStop) onStopAll else null,
                    onDelete = { showDeleteDialog = true },
                )
            }

            item(key = "tail") { Spacer(Modifier.height(32.dp)) }
        }
    }
}

/**
 * How this medication asks for attention.
 *
 * Chips in a FlowRow rather than a segmented row: four options do not fit on one
 * line on a narrow screen, and a SingleChoiceSegmentedButtonRow does not wrap -
 * chunking it renders as several disconnected toggles with the wrong rounding.
 *
 * The description line under the chips is the point of the card. "Strong" means
 * nothing on its own; "a real alarm, and it keeps ringing" is a decision someone
 * can actually make.
 */
@Composable
private fun RemindersCard(
    tier: AlertTier,
    critical: Boolean,
    onChange: (AlertTier, Boolean) -> Unit,
) {
    SectionCard("Reminders") {
        Text("Alert level", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AlertTier.entries.forEach { entry ->
                FilterChip(
                    selected = tier == entry,
                    onClick = { onChange(entry, critical) },
                    label = { Text(entry.label) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            tier.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Critical", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (critical) "Skipping asks why, and records the reason"
                    else "Can be skipped with one tap",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = critical, onCheckedChange = { onChange(tier, it) })
        }
    }
}

/** Says the row goes somewhere. The strip on the medications list does not. */
@Composable
private fun GoChevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The medication at the size the editor promises.
 *
 * This was a 22dp icon beside the strength, with the name hidden on the grounds
 * that the app bar already showed it, so the medication's own page was the one
 * place its pill was smallest.
 */
@Composable
private fun IdentityHero(medication: MedicationWithRegimens, modifier: Modifier = Modifier) {
    val med = medication.medication
    val accent = ThymeTheme.accents.medicationColor(med.colorIndex)
    val accentRight = ThymeTheme.accents.medicationColor(med.colorIndexRight)
    val strength = med.strength?.takeIf { it.isNotBlank() }
    val form = MedicationForms.entry(med.form)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ThymeDimens.PageGutter, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MedicationAvatar(
            formIndex = med.form,
            colorLeft = accent,
            colorRight = accentRight,
            containerSize = 72.dp,
            iconSize = 44.dp,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = med.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = listOfNotNull(form.label, strength).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Adherence and what is next: the numbers the app already had and never showed. */
@Composable
private fun HistoryCard(
    history: MedicationHistory,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    if (history.expected == 0) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${(history.percent * 100).roundToInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "${history.taken} of ${history.expected} doses · last ${history.windowDays} days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { history.percent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            history.nextDoseToday?.let { next ->
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ThymeTheme.accents.due),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Next dose at ${next.format(doseTimeFormatter)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
