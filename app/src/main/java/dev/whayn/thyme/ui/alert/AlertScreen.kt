package dev.whayn.thyme.ui.alert

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.whayn.thyme.AlertUiState
import dev.whayn.thyme.SkipReason
import dev.whayn.thyme.data.TodayDose
import dev.whayn.thyme.ui.EditorBottomBar
import dev.whayn.thyme.ui.EditorPrimaryButton
import dev.whayn.thyme.ui.MedicationAvatar
import dev.whayn.thyme.ui.SectionCard
import dev.whayn.thyme.ui.SectionEyebrow
import dev.whayn.thyme.ui.editorFieldColors
import dev.whayn.thyme.ui.medicationColors
import dev.whayn.thyme.ui.theme.ThymeDimens
import dev.whayn.thyme.ui.theme.ThymeTheme
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val alertTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val alertDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE d MMMM")

/**
 * The full-screen alert.
 *
 * This is the one screen allowed to wear `Honey` at full strength. The palette
 * rule reserves it for "due" and keeps it out of the medication colours so no
 * pill can be mistaken for an overdue one - and a full-screen alarm *is* the due
 * state at its maximum, so using it here honours the rule rather than bending
 * it. What it must never do is tint the medication avatars: recognising your
 * pill by its shape and colour is the whole point of the wizard, and this is the
 * moment that matters most.
 */
@Composable
fun AlertScreen(
    state: AlertUiState,
    onToggleDose: (Long) -> Unit,
    onTake: () -> Unit,
    onSnooze: () -> Unit,
    onSkip: () -> Unit,
    onSilence: () -> Unit,
    onChooseReason: (SkipReason) -> Unit,
    onReasonNoteChange: (String) -> Unit,
    onConfirmReason: () -> Unit,
    onCancelReason: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.askingReason) {
        ReasonStep(
            state = state,
            onChooseReason = onChooseReason,
            onNoteChange = onReasonNoteChange,
            onConfirm = onConfirmReason,
            onBack = onCancelReason,
            modifier = modifier,
        )
        return
    }

    val unresolved = state.unresolved
    Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Spacer(Modifier.height(32.dp))
            Hero(state = state, onSilence = onSilence)
            Spacer(Modifier.height(24.dp))

            if (unresolved.size > 1) {
                SectionCard("${unresolved.size} medications") {
                    unresolved.forEachIndexed { index, dose ->
                        if (index > 0) Spacer(Modifier.height(4.dp))
                        DoseRow(
                            dose = dose,
                            checked = dose.scheduled.id in state.selected,
                            onToggle = { onToggleDose(dose.scheduled.id) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        EditorBottomBar {
            val count = state.selected.size
            EditorPrimaryButton(
                text = when {
                    unresolved.size <= 1 -> "Taken"
                    state.allSelected -> "Take all ($count)"
                    else -> "Take $count"
                },
                onClick = onTake,
                enabled = count > 0,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.snoozesLeft > 0) {
                    OutlinedButton(onClick = onSnooze, modifier = Modifier.weight(1f)) {
                        Text(
                            if (state.snoozesLeft == Int.MAX_VALUE) {
                                "Snooze ${state.snoozeMinutes} min"
                            } else {
                                "Snooze ${state.snoozeMinutes} min (${state.snoozesLeft})"
                            }
                        )
                    }
                }
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.weight(1f),
                    enabled = count > 0,
                    colors = ButtonDefaults.textButtonColors(
                        // Not `error`: skipping a dose is a decision, not a
                        // destructive act, and the app never scolds.
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    // Follows what is ticked: untick the critical dose and this
                    // becomes a plain Skip for the vitamin left behind.
                    Text(if (state.selectedCritical) "Can't take this now" else "Skip")
                }
            }
        }
    }

    // Sits over the bottom bar for the moment between snoozing and the screen
    // closing, so the deferral is confirmed rather than just happening.
    AnimatedVisibility(
        visible = state.snoozedUntilLabel != null,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ThymeDimens.PageGutter),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.inverseSurface,
            shadowElevation = 6.dp,
        ) {
            Text(
                text = "Snoozed until ${state.snoozedUntilLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
    }
}

@Composable
private fun Hero(state: AlertUiState, onSilence: () -> Unit) {
    val accents = ThymeTheme.accents
    val single = state.unresolved.singleOrNull()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ThymeDimens.PageGutter),
        shape = MaterialTheme.shapes.extraLarge,
        color = accents.dueContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionEyebrow("Due now", color = accents.due)
            Spacer(Modifier.height(10.dp))

            if (single != null) {
                // One dose: the pill is the hero, not the clock.
                val (left, right) = medicationColors(single.colorIndex, single.colorIndexRight)
                MedicationAvatar(
                    formIndex = single.form,
                    colorLeft = left,
                    colorRight = right,
                    containerSize = 84.dp,
                    iconSize = 60.dp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    single.medicationName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = accents.onDueContainer,
                    textAlign = TextAlign.Center,
                )
                val detail = listOfNotNull(
                    single.strength?.takeIf { it.isNotBlank() },
                    single.scheduled.time.format(alertTimeFormatter),
                ).joinToString(" · ")
                Spacer(Modifier.height(4.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = accents.onDueContainer.copy(alpha = 0.78f),
                )
            } else {
                Text(
                    state.doses.firstOrNull()?.scheduled?.time?.format(alertTimeFormatter).orEmpty(),
                    style = MaterialTheme.typography.displaySmall,
                    color = accents.onDueContainer,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    state.forDate.format(alertDateFormatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = accents.onDueContainer.copy(alpha = 0.78f),
                )
            }

            if (!state.silenced) {
                Spacer(Modifier.height(16.dp))
                // The humane release valve: stop the noise without pretending
                // anything was decided. Nothing is logged.
                TextButton(
                    onClick = onSilence,
                    colors = ButtonDefaults.textButtonColors(contentColor = accents.due),
                ) { Text("Silence") }
            }
        }
    }
}

@Composable
private fun DoseRow(dose: TodayDose, checked: Boolean, onToggle: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (left, right) = medicationColors(dose.colorIndex, dose.colorIndexRight)
    val shape = MaterialTheme.shapes.small

    Surface(
        // Clip before toggleable so the ripple matches the shape that looks
        // tappable, per the app's row-is-one-control rule.
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .toggleable(value = checked, onValueChange = { onToggle() }, role = Role.Checkbox),
        shape = shape,
        color = scheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ThymeDimens.TouchTarget)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MedicationAvatar(formIndex = dose.form, colorLeft = left, colorRight = right)
            Column(Modifier.weight(1f)) {
                Text(dose.medicationName, style = MaterialTheme.typography.titleLarge)
                val detail = dose.strength?.takeIf { it.isNotBlank() }
                if (detail != null) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (checked) scheme.primary else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (checked) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null, // the row announces its own state
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * The friction that `critical` buys.
 *
 * A second full-screen step rather than a bottom sheet: the app uses no bottom
 * sheets anywhere, the alert is already a full-screen modal so a modal inside a
 * modal reads as confused over a lock screen, and a whole step leaves room for
 * a reason list plus a deliberate confirm where a sheet pushes toward one tap.
 */
@Composable
private fun ReasonStep(
    state: AlertUiState,
    onChooseReason: (SkipReason) -> Unit,
    onNoteChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(48.dp))
            Column(Modifier.padding(horizontal = ThymeDimens.PageGutter)) {
                SectionEyebrow("Before you skip")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Why can't you take this?",
                    style = MaterialTheme.typography.displaySmall,
                )
            }
            Spacer(Modifier.height(20.dp))

            SectionCard("Reason") {
                // Chips in a FlowRow: this list wraps, and a segmented row does
                // not - chunking one renders as several disconnected toggles.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkipReason.entries.forEach { reason ->
                        FilterChip(
                            selected = state.chosenReason == reason,
                            onClick = { onChooseReason(reason) },
                            label = { Text(reason.label) },
                        )
                    }
                }
                if (state.chosenReason == SkipReason.Other) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = state.reasonNote,
                        onValueChange = onNoteChange,
                        label = { Text("Reason") },
                        singleLine = true,
                        colors = editorFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (state.chosenReason == SkipReason.AlreadyTook) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This records the dose as taken, not skipped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        EditorBottomBar {
            // Say what is missing rather than leaving a mysteriously grey button.
            if (!state.canConfirmReason) {
                Text(
                    if (state.chosenReason == null) "Choose a reason to continue"
                    else "Say what the reason is",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            EditorPrimaryButton(
                text = if (state.chosenReason?.resolvesAsTaken == true) {
                    "Mark as taken"
                } else {
                    "Skip this dose"
                },
                onClick = onConfirm,
                enabled = state.canConfirmReason,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Go back") }
        }
    }
}
