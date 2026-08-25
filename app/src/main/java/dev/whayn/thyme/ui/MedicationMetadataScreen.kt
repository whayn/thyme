package dev.whayn.thyme.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.whayn.thyme.MedicationMetadataState
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.MedicationMetadataViewModel
import dev.whayn.thyme.ui.theme.MedicationColorNames
import dev.whayn.thyme.ui.theme.MedicationForms
import dev.whayn.thyme.ui.theme.MedicationPillIcon
import dev.whayn.thyme.ui.theme.ThymeDimens
import dev.whayn.thyme.ui.theme.ThymeTheme
import dev.whayn.thyme.ui.theme.rememberReducedMotion

private const val STEP_COUNT = 4

/**
 * Multi-step medication identity customization:
 * Step 0: Name & Strength
 * Step 1: Choose shape
 * Step 2: Choose colors
 *
 * Every step opens with the same [HeroPreview], so the thing being edited is
 * always the largest object on screen and the flow does not start on a page
 * that is mostly empty.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationMetadataScreen(
    medicationId: Long?,
    onSaved: (Long) -> Unit,
    onTestAlert: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current.applicationContext
    val viewModel: MedicationMetadataViewModel = viewModel(
        key = "medication-metadata-${medicationId ?: "new"}",
        factory = MedicationMetadataViewModel.factory(context, medicationId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current

    if (state.loading) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { Text("Loading medication...") }
        return
    }

    val isEditing = medicationId != null
    fun leaveEditor() {
        if (state.dirty) showDiscardDialog = true else onBack()
    }

    fun handleBack() {
        if (step > 0) step -= 1 else leaveEditor()
    }

    BackHandler(onBack = ::handleBack)

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onDiscard = { showDiscardDialog = false; onBack() },
            onKeepEditing = { showDiscardDialog = false },
        )
    }

    val titleText = when (step) {
        0 -> if (isEditing) "Edit medication" else "Add medication"
        1 -> "Choose shape"
        2 -> "Choose colours"
        else -> "How it alerts you"
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(titleText)
                            Text(
                                "Step ${step + 1} of $STEP_COUNT",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = ::handleBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                StepIndicator(step = step)
            }
        },
        bottomBar = {
            EditorBottomBar {
                EditorPrimaryButton(
                    text = when {
                        step < STEP_COUNT - 1 -> "Next"
                        isEditing -> "Save changes"
                        else -> "Add medication"
                    },
                    enabled = when (step) {
                        0 -> state.name.isNotBlank()
                        else -> state.canSave
                    },
                    onClick = {
                        focusManager.clearFocus()
                        if (step < STEP_COUNT - 1) step += 1 else viewModel.save(onSaved)
                    },
                )
            }
        },
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(scaffoldPadding)
                .padding(horizontal = ThymeDimens.PageGutter),
        ) {
            HeroPreview(
                formIndex = state.form,
                colorIndexLeft = state.colorIndex,
                colorIndexRight = state.colorIndexRight,
                name = state.name,
                strength = state.strength,
            )

            when (step) {
                0 -> DetailsStep(
                    state = state,
                    onName = viewModel::setName,
                    onStrength = viewModel::setStrength,
                )

                1 -> ShapeStep(state = state, onSelectForm = viewModel::setForm)

                2 -> ColorsStep(
                    state = state,
                    onSelectLeftColor = viewModel::setColor,
                    onSelectRightColor = viewModel::setColorRight,
                    onLinkedChange = viewModel::setLinkedColors,
                )

                else -> AlertStep(
                    state = state,
                    onSelectTier = viewModel::setAlertTier,
                    onCriticalChange = viewModel::setCritical,
                    onTestAlert = onTestAlert,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * How loudly this medication asks for attention, and whether it can be waved
 * away.
 *
 * A whole step rather than a chip row on the details page: this is the most
 * consequential choice in the app, and each level needs a sentence saying what
 * it actually does. "Strong" means nothing on its own.
 */
@Composable
private fun AlertStep(
    state: MedicationMetadataState,
    onSelectTier: (AlertTier) -> Unit,
    onCriticalChange: (Boolean) -> Unit,
    onTestAlert: () -> Unit,
) {
    EditorCard("Alert level") {
      Column {
        AlertTier.entries.forEach { tier ->
            val selected = state.alertTier == tier
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(MaterialTheme.shapes.small)
                    .selectable(
                        selected = selected,
                        onClick = { onSelectTier(tier) },
                        role = Role.RadioButton,
                    ),
                shape = MaterialTheme.shapes.small,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column(Modifier.weight(1f)) {
                        Text(tier.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            tier.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Critical", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (state.critical) "Skipping asks why, and records the reason"
                    else "Can be skipped with one tap",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.critical, onCheckedChange = onCriticalChange)
        }

        if (state.alertTier != AlertTier.NONE) {
            Spacer(Modifier.height(18.dp))
            Text(
                "Phones can silence apps in ways that only show up when it matters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onTestAlert) { Text("Test this alert") }
        }
      }
    }
}

/** Three segments under the app bar: how far in you are, without a paragraph about it. */
@Composable
private fun StepIndicator(step: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ThymeDimens.PageGutter, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(STEP_COUNT) { index ->
            val target =
                if (index <= step) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest
            val color by animateColorAsState(
                targetValue = target,
                animationSpec = if (rememberReducedMotion()) androidx.compose.animation.core.snap()
                else androidx.compose.animation.core.tween(220),
                label = "step",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

/**
 * Discarding is the destructive choice, so it takes the dismiss slot and the
 * error colour, matching the delete dialogs elsewhere in the app, which had it
 * right. Previously "Discard changes" sat in the trailing default-action
 * position with no colour at all.
 */
@Composable
internal fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Leave without saving?") },
        text = { Text("Your changes will be lost.") },
        confirmButton = {
            TextButton(onClick = onKeepEditing) { Text("Keep editing") }
        },
        dismissButton = {
            TextButton(
                onClick = onDiscard,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Discard changes") }
        },
    )
}

/** Step 0: Name & Strength inputs */
@Composable
private fun DetailsStep(
    state: MedicationMetadataState,
    onName: (String) -> Unit,
    onStrength: (String) -> Unit,
) {
    EditorCard(title = "Details") {
        OutlinedTextField(
            value = state.name,
            onValueChange = onName,
            label = { Text("Name") },
            placeholder = { Text("Paracetamol") },
            singleLine = true,
            colors = editorFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.strength,
            onValueChange = onStrength,
            label = { Text("Strength") },
            placeholder = { Text("500 mg") },
            singleLine = true,
            colors = editorFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Strength is one unit: one tablet, one spray. How many you take at a time is set on the course.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The card every step's controls sit in, so the four steps share one container. */
@Composable
private fun EditorCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            content()
        }
    }
}

/** Hero preview at top showing the current pill visual, name, and subtitle */
@Composable
private fun HeroPreview(
    formIndex: Int,
    colorIndexLeft: Int,
    colorIndexRight: Int,
    name: String,
    strength: String,
) {
    val accents = ThymeTheme.accents
    val colorLeft = accents.medicationColor(colorIndexLeft)
    val colorRight = accents.medicationColor(colorIndexRight)
    val form = MedicationForms.entry(formIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            MedicationPillIcon(
                formIndex = formIndex,
                colorLeft = colorLeft,
                colorRight = colorRight,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = name.ifBlank { "New medication" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (name.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        val subtitle = buildString {
            append(form.label)
            if (strength.isNotBlank()) {
                append(", ")
                append(strength)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Step 1: Choose Shape. A 4-column grid of every form. */
@Composable
private fun ShapeStep(state: MedicationMetadataState, onSelectForm: (Int) -> Unit) {
    // "Form", not "Pill shapes": ten of the nineteen entries are inhalers,
    // syringes and creams.
    EditorCard(title = "Form") {
        SelectionGrid(
            itemCount = MedicationForms.entries.size,
            columns = 4,
        ) { index ->
            ShapeButton(
                form = MedicationForms.entries[index],
                selected = index == state.form,
                onClick = { onSelectForm(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Rows of equal-weight cells with a trailing filler, used by both the shape and
 * colour grids. They previously used two different strategies (weights here,
 * hardcoded `0 until 6` ranges and `SpaceBetween` there) and the hardcoded one
 * silently dropped entries if the palette were ever resized.
 */
@Composable
private fun SelectionGrid(
    itemCount: Int,
    columns: Int,
    spacing: Dp = 10.dp,
    cell: @Composable RowScope.(index: Int) -> Unit,
) {
    val rows = (itemCount + columns - 1) / columns
    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing),
            ) {
                repeat(columns) { column ->
                    val index = row * columns + column
                    // The trailing filler keeps the last row's cells the same
                    // width as every other row's.
                    if (index < itemCount) cell(index) else Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ShapeButton(
    form: dev.whayn.thyme.ui.theme.MedicationForm,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container =
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val content =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(container)
            .then(
                if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                else Modifier
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The selected state is carried by the container and border. An earlier
        // version laid a translucent primary scrim *over* the icon, which dimmed
        // the one thing it was meant to emphasise.
        Icon(
            painter = painterResource(form.iconRes),
            contentDescription = form.label,
            tint = content,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Step 2: Choose Colours. One grid, or two when the halves are unlinked. */
@Composable
private fun ColorsStep(
    state: MedicationMetadataState,
    onSelectLeftColor: (Int) -> Unit,
    onSelectRightColor: (Int) -> Unit,
    onLinkedChange: (Boolean) -> Unit,
) {
    val form = MedicationForms.entry(state.form)
    val twoTone = form.supportsTwoSides && !state.linkedColors

    EditorCard(title = if (twoTone) "Left half" else "Colour") {
        ColorCirclesGrid(
            selectedColorIndex = state.colorIndex,
            onSelectColor = onSelectLeftColor,
        )

        if (twoTone) {
            Spacer(Modifier.height(22.dp))
            Text(
                text = "Right half",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 14.dp),
            )
            ColorCirclesGrid(
                selectedColorIndex = state.colorIndexRight,
                onSelectColor = onSelectRightColor,
            )
        }

        if (form.supportsTwoSides) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Same on both halves", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Turn off to colour each half of the ${form.label.lowercase()} separately",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(checked = state.linkedColors, onCheckedChange = onLinkedChange)
            }
        }
    }
}

/** Two rows of six swatches. */
@Composable
private fun ColorCirclesGrid(
    selectedColorIndex: Int,
    onSelectColor: (Int) -> Unit,
) {
    val colors = ThymeTheme.accents.medication
    SelectionGrid(itemCount = colors.size, columns = 6, spacing = 8.dp) { index ->
        ColorCircle(
            color = colors[index],
            name = MedicationColorNames.getOrElse(index) { "Colour" },
            selected = index == selectedColorIndex,
            onClick = { onSelectColor(index) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ColorCircle(
    color: Color,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The swatch stays visually small; the tap target around it is a full 48dp.
    Box(
        modifier = modifier
            .sizeIn(minHeight = ThymeDimens.TouchTarget)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(CircleShape)
                .background(color)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface
                    else Color.Black.copy(alpha = 0.15f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = name,
                    tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
