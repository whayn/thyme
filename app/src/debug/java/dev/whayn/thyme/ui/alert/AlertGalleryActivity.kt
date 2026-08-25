package dev.whayn.thyme.ui.alert

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
import dev.whayn.thyme.AlertUiState
import dev.whayn.thyme.data.ThymeThemeMode
import dev.whayn.thyme.ui.theme.ThymeDimens
import dev.whayn.thyme.ui.theme.ThymeTheme

/**
 * Renders every [AlertGallery] case on the device, with nothing behind it.
 *
 * Debug builds only. This is not a substitute for firing a real alert - it
 * cannot tell you whether the full-screen intent was granted, whether the
 * ringer started, or what the notification looks like in the shade, because
 * none of that is drawn by this process. It answers a narrower question much
 * faster: does the screen itself look right in all eighteen states, in both
 * themes, at a large font scale.
 *
 *   tools/alert screens      # the index
 *   tools/alert screen 6     # straight into one case
 */
class AlertGalleryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val deepLink = intent.getIntExtra(EXTRA_INDEX, -1)
            .takeIf { it in AlertGallery.cases.indices }

        setContent {
            var mode by remember { mutableStateOf(ThymeThemeMode.System) }
            var scale by remember { mutableStateOf(1f) }
            var open by remember { mutableStateOf(deepLink) }

            ThymeTheme(mode = mode) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // Font scale is faked rather than read from the system, so
                    // the accessibility check does not need a trip through
                    // Settings and back for every case.
                    val density = LocalDensity.current
                    CompositionLocalProvider(
                        LocalDensity provides androidx.compose.ui.unit.Density(
                            density = density.density,
                            fontScale = scale,
                        )
                    ) {
                        val index = open
                        if (index == null) {
                            Index(
                                mode = mode,
                                scale = scale,
                                onMode = { mode = it },
                                onScale = { scale = it },
                                onOpen = { open = it },
                            )
                        } else {
                            // Back returns to the index rather than leaving the
                            // gallery, so stepping through cases is one tap
                            // each way.
                            BackHandler { open = null }
                            Case(AlertGallery.cases[index].state)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Index(
        mode: ThymeThemeMode,
        scale: Float,
        onMode: (ThymeThemeMode) -> Unit,
        onScale: (Float) -> Unit,
        onOpen: (Int) -> Unit,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = ThymeDimens.PageGutter,
                vertical = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Alert screen states", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${AlertGallery.cases.size} cases. The notification, heads-up and " +
                        "full-screen takeover are drawn by the OS and are not in here - " +
                        "use `tools/alert fire` for those.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThymeThemeMode.entries.forEach {
                        FilterChip(
                            selected = mode == it,
                            onClick = { onMode(it) },
                            label = { Text(it.name) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1f, 1.3f, 1.8f).forEach {
                        FilterChip(
                            selected = scale == it,
                            onClick = { onScale(it) },
                            label = { Text("${it}x") },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            items(AlertGallery.cases.size) { index ->
                val case = AlertGallery.cases[index]
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(index) },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            index.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(case.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                case.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * The real [AlertScreen], with inert callbacks.
     *
     * Deliberately not wired to anything: the point is to look at a *fixed*
     * state, and a working Take button would immediately replace it with a
     * different one. Behaviour is what `tools/alert fire` is for.
     */
    @Composable
    private fun Case(state: AlertUiState) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            AlertScreen(
                state = state,
                onToggleDose = {},
                onTake = {},
                onSnooze = {},
                onSkip = {},
                onSilence = {},
                onChooseReason = {},
                onReasonNoteChange = {},
                onConfirmReason = {},
                onCancelReason = {},
            )
        }
    }

    companion object {
        const val EXTRA_INDEX = "index"

        fun intent(context: Context, index: Int? = null): Intent =
            Intent(context, AlertGalleryActivity::class.java)
                .apply { if (index != null) putExtra(EXTRA_INDEX, index) }
    }
}
