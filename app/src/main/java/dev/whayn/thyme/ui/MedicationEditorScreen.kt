package dev.whayn.thyme.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whayn.thyme.EditableDose
import dev.whayn.thyme.MedicationEditorState
import dev.whayn.thyme.MedicationEditorViewModel
import dev.whayn.thyme.RecurrenceChoice
import dev.whayn.thyme.data.Recurrence
import dev.whayn.thyme.data.Regimen
import dev.whayn.thyme.ui.theme.MedicationColorNames
import dev.whayn.thyme.ui.theme.ThymeTheme
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
fun MedicationEditorScreen(
    medicationId: Long?,
    regimenId: Long?,
    onSaved: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val viewModel: MedicationEditorViewModel = viewModel(
        key = "medication-editor-${medicationId ?: "new"}-${regimenId ?: "new"}",
        factory = MedicationEditorViewModel.factory(context, medicationId, regimenId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showTimePickerFor by remember { mutableStateOf<Int?>(null) }
    var dateTarget by remember { mutableStateOf<DateTarget?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    if (state.loading || state.medication == null) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { Text("Loading medication...") }
        return
    }

    val medication = state.medication ?: return
    fun requestBack() {
        if (state.dirty) showDiscardDialog = true else onBack()
    }

    BackHandler(onBack = ::requestBack)

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Leave without saving?") },
            text = { Text("Your changes will be lost.") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onBack() }) {
                    Text("Discard changes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
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
                title = {
                    Text(
                        when {
                            medicationId == null -> "Add medication"
                            regimenId == null -> "Add course"
                            else -> "Edit course"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::requestBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
            ) {
                Button(
                    onClick = { viewModel.save(onSaved) },
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        when {
                            medicationId == null -> "Add medication"
                            regimenId == null -> "Add course"
                            else -> "Save changes"
                        }
                    )
                }
            }
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(scaffoldPadding)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            EditorSectionLabel("MEDICATION")
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = medication.name,
                onValueChange = { viewModel.setMedication(medication.copy(name = it)) },
                label = { Text("Name") },
                placeholder = { Text("Paracetamol") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = medication.strength.orEmpty(),
                onValueChange = { viewModel.setMedication(medication.copy(strength = it)) },
                label = { Text("Strength") },
                placeholder = { Text("50mg") },
                singleLine = true,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            EditorSectionLabel("COLOUR")
            Spacer(Modifier.height(8.dp))
            ColorPicker(
                selected = medication.colorIndex,
                onSelect = { viewModel.setMedication(medication.copy(colorIndex = it)) },
            )

            Spacer(Modifier.height(24.dp))
            EditorSectionLabel("TIMES")
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

            Spacer(Modifier.height(20.dp))
            EditorSectionLabel("RECURRENCE")
            Spacer(Modifier.height(8.dp))
            RecurrenceControls(state, viewModel::setChoice, viewModel::setRegimen)

            Spacer(Modifier.height(22.dp))
            EditorSectionLabel("DATES")
            Spacer(Modifier.height(8.dp))
            DateButton(
                label = "Starts",
                value = state.regimen.startDate.format(editorDateFormatter),
                onClick = {
                    dateTarget = DateTarget(DateTargetKind.Start, state.regimen.startDate)
                },
            )
            Spacer(Modifier.height(8.dp))
            DateButton(
                label = "Ends",
                value = state.regimen.endDate?.format(editorDateFormatter) ?: "No end date",
                onClick = {
                    dateTarget = DateTarget(
                        DateTargetKind.End,
                        state.regimen.endDate ?: state.regimen.startDate,
                    )
                },
                trailing = if (state.regimen.endDate != null) {
                    {
                        TextButton(
                            onClick = { viewModel.setRegimen(state.regimen.copy(endDate = null)) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("Clear") }
                    }
                } else null,
            )
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
private fun ValidationHint(state: MedicationEditorState) {
    val message = when {
        state.hasDuplicateTimes -> "Two doses share the same time — change or remove one."
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
        Box(Modifier.weight(1.4f)) {
            OutlinedTextField(
                value = dose.time.format(editorTimeFormatter),
                onValueChange = {},
                readOnly = true,
                label = { Text("Time") },
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(onClick = onTimeClick)
                    .semantics { contentDescription = "Change time" },
            )
        }
        Spacer(Modifier.size(8.dp))
        OutlinedTextField(
            value = dose.quantityText,
            onValueChange = onQuantityChange,
            label = { Text("Qty") },
            singleLine = true,
            isError = (dose.quantityText.toDoubleOrNull() ?: 0.0) <= 0.0,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(0.8f),
            colors = fieldColors(),
        )
        IconButton(onClick = onRemove, enabled = canRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove time")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurrenceControls(
    state: MedicationEditorState,
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
    // 2-way toggles and rounds the wrong corners. Chips are designed to wrap, and
    // match the vocabulary already used elsewhere in the app.
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { (choice, label) ->
            val selected = state.recurrenceChoice == choice
            FilterChip(
                selected = selected,
                onClick = { onChoice(choice) },
                label = { Text(label, maxLines = 1, softWrap = false) },
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
            )
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
                            .height(38.dp)
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
        colors = fieldColors(),
    )
}

@Composable
private fun DateButton(
    label: String,
    value: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        trailing?.invoke()
    }
}

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ThymeTheme.accents.medication.forEachIndexed { index, color ->
            val isSelected = selected == index
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(index) },
                        role = Role.RadioButton,
                    )
                    .semantics {
                        contentDescription = MedicationColorNames.getOrElse(index) { "Colour" }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
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
                onConfirm(
                    state.hour,
                    state.minute
                )
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditorSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
)

private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
