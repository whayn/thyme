package dev.whayn.thyme.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.whayn.thyme.data.MedicationWithRegimens
import dev.whayn.thyme.data.Recurrence
import dev.whayn.thyme.data.RegimenWithDoses
import dev.whayn.thyme.ui.theme.ThymeTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val courseTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
fun MedicationsScreen(
    medications: List<MedicationWithRegimens>,
    onEditRegimen: (medicationId: Long, regimenId: Long) -> Unit,
    onAddCourse: (medicationId: Long) -> Unit,
    onStop: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var deleteId by remember { mutableStateOf<Long?>(null) }
    val today = remember { LocalDate.now() }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete medication?") },
            text = { Text("This hides it from every date. Its history will no longer be shown.") },
            confirmButton = {
                TextButton(onClick = { onDelete(id); deleteId = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } },
        )
    }

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
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)) {
                Text(
                    "MEDICATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text("Your courses", style = MaterialTheme.typography.displaySmall)
            }
        }
        items(medications, key = { it.medication.id }) { item ->
            MedicationCard(
                item = item,
                today = today,
                onEditRegimen = { regimenId -> onEditRegimen(item.medication.id, regimenId) },
                onAddCourse = { onAddCourse(item.medication.id) },
                onStop = { onStop(item.medication.id) },
                onDelete = { deleteId = item.medication.id },
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun MedicationCard(
    item: MedicationWithRegimens,
    today: LocalDate,
    onEditRegimen: (Long) -> Unit,
    onAddCourse: () -> Unit,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val accent = ThymeTheme.accents.medicationColor(item.medication.colorIndex)
    val courses = item.activeRegimens

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(start = 18.dp, top = 16.dp, end = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.medication.name, style = MaterialTheme.typography.titleLarge)
                    item.medication.strength?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        // A pencil promises editing; this menu also stops and deletes.
                        Icon(Icons.Filled.MoreVert, contentDescription = "Medication actions")
                    }
                    // No leading icons: there is no honest icon for "stop taking",
                    // and mixing icons with none leaves the labels misaligned.
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add another course") },
                            onClick = { menuExpanded = false; onAddCourse() },
                        )
                        DropdownMenuItem(
                            text = { Text("Stop taking") },
                            onClick = { menuExpanded = false; onStop() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            colors = MenuDefaults.itemColors(
                                textColor = MaterialTheme.colorScheme.error,
                            ),
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            if (courses.isEmpty()) {
                Text(
                    "No active course",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 10.dp, bottom = 4.dp),
                )
            } else {
                courses.forEach { course ->
                    CourseRow(
                        course = course,
                        today = today,
                        onClick = { onEditRegimen(course.regimen.id) },
                        modifier = Modifier.padding(start = 24.dp, end = 10.dp, bottom = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseRow(
    course: RegimenWithDoses,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A finished course stays listed — its history is still real — but recedes so
    // the list reads as "what I'm taking" at a glance.
    val finished = course.regimen.endDate?.isBefore(today) == true
    val times = course.activeDoses.joinToString(" · ") { it.time.format(courseTimeFormatter) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .alpha(if (finished) 0.55f else 1f),
        ) {
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
    }
}
