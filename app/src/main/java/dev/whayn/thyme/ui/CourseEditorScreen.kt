package dev.whayn.thyme.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whayn.thyme.CourseEditorState
import dev.whayn.thyme.CourseEditorViewModel
import dev.whayn.thyme.EditableDose
import dev.whayn.thyme.RecurrenceChoice
import dev.whayn.thyme.data.Recurrence
import dev.whayn.thyme.data.Regimen
import dev.whayn.thyme.ui.theme.ThymeDimens
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private const val MILLIS_PER_DAY = 86_400_000L
private val editorTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val editorDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")
private val dayShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditorScreen(
    medicationId: Long,
    regimenId: Long?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val viewModel: CourseEditorViewModel = viewModel(
        key = "course-editor-$medicationId-${regimenId ?: "new"}",
        factory = CourseEditorViewModel.factory(context, medicationId, regimenId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTimePickerFor by remember { mutableStateOf<Int?>(null) }
    var dateTarget by remember { mutableStateOf<DateTarget?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (state.loading) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { Text("Loading course...") }
        return
    }

    val isEditing = regimenId != null
    fun requestBack() {
        if (state.dirty) showDiscardDialog = true else onBack()
    }

    BackHandler(onBack = ::requestBack)

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDiscard = { showDiscardDialog = false; onBack() },
            onKeepEditing = { showDiscardDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete course?") },
            text = { Text("This removes it from every date, including its history.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteDialog = false; viewModel.delete(onBack) },
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

    if (showTimePickerFor != null) {
        val index = showTimePickerFor!!
        val time = state.times[index].time
        TimePickerDialog(
            initialHour = time.hour,
            initialMinute = time.minute,
            onConfirm = { hour, minute ->
                viewModel.setTimes(state.times.mapIndexed { i, dose ->
                    if (i == index) dose.copy(time = LocalTime.of(hour, minute)) else dose
                })
                showTimePickerFor = null
            },
            onDismiss = { showTimePickerFor = null },
        )
    }

    dateTarget?.let { target ->
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = target.initialDate.toEpochDay() * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = LocalDate.ofEpochDay(millis / MILLIS_PER_DAY)
                        if (target.kind == DateTargetKind.Start) {
                            viewModel.setRegimen(state.regimen.copy(startDate = date))
                        } else {
                            viewModel.setRegimen(state.regimen.copy(endDate = date))
                        }
                    }
                    dateTarget = null
                }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { dateTarget = null }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Edit course" else "Add course") },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            // Just the one action. Two extra text buttons under the primary
            // button made the bar look like three competing calls to action,
            // with the destructive pair given the same weight as saving.
            EditorBottomBar {
                EditorPrimaryButton(
                    text = if (isEditing) "Save changes" else "Add course",
                    enabled = state.canSave,
                    onClick = { viewModel.save(onSaved) },
                )
            }
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(scaffoldPadding)
                .padding(horizontal = ThymeDimens.PageGutter, vertical = 20.dp),
        ) {
            SectionEyebrow("Times")
            Spacer(Modifier.height(8.dp))
            state.times.forEachIndexed { index, dose ->
                DoseTimeRow(
                    dose = dose,
                    canRemove = state.times.size > 1,
                    onTimeClick = { showTimePickerFor = index },
                    onQuantityChange = { text ->
                        // Store the raw text; parsing happens on save. Anything
                        // unparseable simply blocks saving rather than snapping
                        // the field back while you type.
                        val filtered = text.filter { it.isDigit() || it == '.' }
                        viewModel.setTimes(state.times.mapIndexed { i, current ->
                            if (i == index) current.copy(quantityText = filtered) else current
                        })
                    },
                    onRemove = {
                        viewModel.setTimes(state.times.filterIndexed { i, _ -> i != index })
                    },
                )
                Spacer(Modifier.height(6.dp))
            }
            TextButton(
                onClick = viewModel::addTime,
                contentPadding = PaddingValues(horizontal = 0.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Add another time")
            }
            ValidationHint(state)

            Spacer(Modifier.height(24.dp))
            SectionEyebrow("Recurrence")
            Spacer(Modifier.height(8.dp))
            RecurrenceControls(state, viewModel::setChoice, viewModel::setRegimen)

            Spacer(Modifier.height(24.dp))
            SectionEyebrow("Dates")
            Spacer(Modifier.height(8.dp))
            PickerField(
                label = "Starts",
                value = state.regimen.startDate.format(editorDateFormatter),
                icon = Icons.Filled.CalendarMonth,
                onClick = {
                    dateTarget = DateTarget(DateTargetKind.Start, state.regimen.startDate)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PickerField(
                label = "Ends",
                value = state.regimen.endDate?.format(editorDateFormatter) ?: "No end date",
                icon = Icons.Filled.CalendarMonth,
                onClick = {
                    dateTarget = DateTarget(
                        DateTargetKind.End,
                        state.regimen.endDate ?: state.regimen.startDate,
                    )
                },
                trailing = if (state.regimen.endDate != null) {
                    {
                        IconButton(
                            onClick = { viewModel.setRegimen(state.regimen.copy(endDate = null)) },
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear end date",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else null,
                modifier = Modifier.fillMaxWidth(),
            )

            // Only an existing course can be stopped or deleted, so a brand new
            // one has nothing to manage yet.
            if (isEditing) {
                Spacer(Modifier.height(28.dp))
                ManageCard(
                    stopLabel = "Stop",
                    onStop = { viewModel.stop(onBack) },
                    onDelete = { showDeleteDialog = true },
                    // The scrolling column already applies the page gutter.
                    gutter = 0.dp,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private enum class DateTargetKind { Start, End }

private data class DateTarget(
    val kind: DateTargetKind,
    val initialDate: LocalDate,
)

/** Says what is blocking the save button, rather than leaving it mysteriously grey. */
@Composable
private fun ValidationHint(state: CourseEditorState) {
    val message = when {
        state.hasDuplicateTimes -> "Two doses share the same time. Change or remove one."
        !state.quantitiesValid -> "Every dose needs a quantity above zero."
        !state.daysValid -> "Pick at least one day of the week."
        !state.cycleValid -> "A cycle needs both an on and an off length."
        state.endsBeforeItStarts -> "The end date is before the start date."
        else -> null
    } ?: return

    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun DoseTimeRow(
    dose: EditableDose,
    canRemove: Boolean,
    onTimeClick: () -> Unit,
    onQuantityChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PickerField(
            label = "Time",
            value = dose.time.format(editorTimeFormatter),
            icon = Icons.Filled.Schedule,
            onClick = onTimeClick,
            modifier = Modifier.weight(1.4f),
        )
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = dose.quantityText,
            onValueChange = onQuantityChange,
            label = { Text("Qty") },
            singleLine = true,
            isError = (dose.quantityText.toDoubleOrNull() ?: 0.0) <= 0.0,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(0.8f),
            colors = editorFieldColors(),
        )
        IconButton(onClick = onRemove, enabled = canRemove) {
            // Muted: removing a dose should not be the highest-contrast thing
            // in the section.
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Remove time",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecurrenceControls(
    state: CourseEditorState,
    onChoice: (RecurrenceChoice) -> Unit,
    onRegimen: (Regimen) -> Unit,
) {
    val choices = listOf(
        RecurrenceChoice.EveryDay to "Every day",
        RecurrenceChoice.CertainDays to "Certain days",
        RecurrenceChoice.EveryInterval to "Every N days",
        RecurrenceChoice.Cycle to "Cycle",
    )
    // Chips rather than a segmented row. A SingleChoiceSegmentedButtonRow is one
    // connected control, so splitting it over two lines reads as two independent
    // 2-way toggles and rounds the wrong corners.
    //
    // Laid out as a fixed 2x2 rather than a FlowRow: four chips of uneven width
    // wrapped as 3 + 1, leaving the fourth stranded on its own line beside a
    // wide gap.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { (choice, label) ->
                    val selected = state.recurrenceChoice == choice
                    FilterChip(
                        selected = selected,
                        onClick = { onChoice(choice) },
                        label = {
                            Text(
                                label,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(ThymeDimens.TouchTarget),
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    when (state.recurrenceChoice) {
        RecurrenceChoice.CertainDays -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Recurrence.orderedDays.forEach { day ->
                    val selected = Recurrence.contains(state.regimen.daysOfWeek, day)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(ThymeDimens.TouchTarget)
                            .clip(dayShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = dayShape,
                            )
                            .toggleable(
                                value = selected,
                                role = Role.Checkbox,
                                onValueChange = {
                                    onRegimen(
                                        state.regimen.copy(
                                            daysOfWeek = Recurrence.toggle(
                                                state.regimen.daysOfWeek,
                                                day
                                            ),
                                        )
                                    )
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            // Localised narrow name, not name.take(1): the latter
                            // is English-only and gives two "T"s and two "S"s.
                            text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        RecurrenceChoice.EveryInterval -> NumberField(
            label = "Days between doses",
            value = state.regimen.intervalDays.toString(),
            onValueChange = {
                onRegimen(
                    state.regimen.copy(
                        intervalDays = it.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    )
                )
            },
        )

        RecurrenceChoice.Cycle -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NumberField(
                label = "On days",
                value = state.regimen.cycleOnDays?.toString().orEmpty(),
                onValueChange = {
                    onRegimen(state.regimen.copy(cycleOnDays = it.toIntOrNull()?.coerceAtLeast(1)))
                },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Off days",
                value = state.regimen.cycleOffDays?.toString().orEmpty(),
                onValueChange = {
                    onRegimen(state.regimen.copy(cycleOffDays = it.toIntOrNull()?.coerceAtLeast(1)))
                },
                modifier = Modifier.weight(1f),
            )
        }

        RecurrenceChoice.EveryDay -> Unit
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        colors = editorFieldColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dose time") },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    periodSelectorSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(state.hour, state.minute)
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}