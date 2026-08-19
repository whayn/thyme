package dev.whayn.thyme.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.whayn.thyme.ui.theme.ThymeDimens

/** Small pieces shared by the metadata and course editors. */

/**
 * The docked action bar at the foot of a full-screen editor.
 *
 * `Scaffold` only insets a bottom bar that pads itself. `NavigationBar` and
 * `BottomAppBar` do it internally, a bare `Surface` does not. All three editors
 * used a bare `Surface`, so their primary button sat underneath the system
 * gesture pill. Doing the inset here means no screen has to remember.
 */
@Composable
internal fun EditorBottomBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = ThymeDimens.PageGutter, vertical = 12.dp),
            content = content,
        )
    }
}

/** The full-width primary action of an editor. Tall enough to read as the CTA. */
@Composable
internal fun EditorPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
internal fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
)

/**
 * A field that opens a picker instead of a keyboard.
 *
 * Same `OutlinedTextField` shell as the fields you actually type into, so the
 * editor reads as one set of controls, but with a trailing icon to say that
 * tapping it opens something. The course editor previously mixed three
 * different field shapes (typed fields, hand-built bordered rows, and chips)
 * with nothing to distinguish read-only from editable.
 */
@Composable
internal fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            colors = editorFieldColors(),
            trailingIcon = {
                if (trailing != null) trailing()
                else Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        // The text field swallows clicks even when read-only, so the tap target
        // is a transparent sibling laid over it. When there is a trailing
        // control, stop short of it so it stays reachable.
        Box(
            Modifier
                .matchParentSize()
                .padding(end = if (trailing != null) ThymeDimens.TouchTarget else 0.dp)
                .clickable(onClick = onClick)
                .semantics { contentDescription = "Change $label" },
        )
    }
}
