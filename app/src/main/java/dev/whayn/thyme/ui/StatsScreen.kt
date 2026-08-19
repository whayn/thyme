package dev.whayn.thyme.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.whayn.thyme.CalendarDay
import dev.whayn.thyme.MedicationAdherence
import dev.whayn.thyme.StatsState
import dev.whayn.thyme.StatsSummary
import dev.whayn.thyme.StatsWindow
import dev.whayn.thyme.ui.theme.ThymeDimens
import dev.whayn.thyme.ui.theme.ThymeTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
private val weekdayLabels = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

@Composable
fun StatsScreen(
    state: StatsState,
    window: StatsWindow,
    onSelectWindow: (StatsWindow) -> Unit,
    month: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (state.loading) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatsHeader() }
            item {
                Column(Modifier.padding(horizontal = ThymeDimens.PageGutter, vertical = 8.dp)) {
                    WindowSelector(window = window, onSelect = onSelectWindow)
                }
            }
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        return
    }

    val summary = state.summary
    val calendarDays = state.calendarDays

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { StatsHeader() }
        item {
            CalendarSection(
                month = month,
                days = calendarDays,
                onPrevious = onPreviousMonth,
                onNext = onNextMonth,
                onDayClick = onDayClick,
                modifier = Modifier.padding(horizontal = ThymeDimens.PageGutter),
            )
        }
        // The selector drives the overview and the per-medication rows, not the
        // calendar above it, so it sits directly on top of what it filters.
        item {
            Column(Modifier.padding(horizontal = ThymeDimens.PageGutter, vertical = 8.dp)) {
                WindowSelector(window = window, onSelect = onSelectWindow)
            }
        }
        item {
            OverviewCard(
                summary = summary,
                window = window,
                modifier = Modifier.padding(horizontal = ThymeDimens.PageGutter),
            )
        }
        if (summary.expected == 0) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Nothing tracked yet", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tick a dose on Today to start building history.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            items(summary.perMedication, key = { it.medicationId }) { adherence ->
                MedicationAdherenceRow(
                    adherence = adherence,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun StatsHeader() {
    PageHeader(eyebrow = "Stats", title = "Adherence")
}

@Composable
private fun WindowSelector(window: StatsWindow, onSelect: (StatsWindow) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        StatsWindow.entries.forEachIndexed { index, entry ->
            SegmentedButton(
                selected = window == entry,
                onClick = { onSelect(entry) },
                shape = SegmentedButtonDefaults.itemShape(index, StatsWindow.entries.size),
                label = { Text(entry.label) },
            )
        }
    }
}

@Composable
private fun CalendarSection(
    month: YearMonth,
    days: List<CalendarDay>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                Text(month.format(monthFormatter), style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            val today = LocalDate.now()
            days.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        DayCell(
                            day = day,
                            today = today,
                            onClick = { onDayClick(day.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * One ring per day, filled by the share of that day's doses that were taken.
 *
 * An earlier version drew one arc per dose in that medication's own colour. It
 * scaled properly, since a ring subdivides rather than running out of room the way a
 * dot row does, but with five or six medications every cell became six
 * unrelated hues, and nothing aggregated: you could not see a good week. The
 * month is a density read, so the cell encodes one number. Which medication was
 * missed is a question for the day itself, and tapping the cell goes there.
 *
 * Days after today draw the empty track only. Previously they drew a full set
 * of faded arcs, so the rest of the month looked comprehensively missed.
 */
@Composable
private fun DayCell(
    day: CalendarDay,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val isToday = day.date == today
    val isFuture = day.date.isAfter(today)
    val trackColor = scheme.surfaceContainerHighest
    val ringColor = scheme.primary

    val expected = day.doses.size
    val taken = day.doses.count { it.taken }
    val fraction = if (expected == 0) 0f else taken.toFloat() / expected

    Box(
        modifier = modifier
            .padding(2.dp)
            .sizeIn(minWidth = ThymeDimens.TouchTarget, minHeight = ThymeDimens.TouchTarget)
            .aspectRatio(1f)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .alpha(if (day.inMonth) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val strokeWidth = 3.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            if (!isFuture && expected > 0 && fraction > 0f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday) FontWeight.Bold else null,
            color = when {
                isToday -> scheme.primary
                isFuture -> scheme.onSurfaceVariant
                else -> scheme.onSurface
            },
        )
    }
}

/**
 * The window's headline: the rate, what it is a rate *of*, and the streak.
 *
 * The counts were computed all along and never shown, so the card was a large
 * number in a mostly empty box with nothing to say what period it covered.
 */
@Composable
private fun OverviewCard(
    summary: StatsSummary,
    window: StatsWindow,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "${(summary.percent * 100).roundToInt()}%",
                    style = MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${summary.taken} of ${summary.expected} doses · last ${window.days} days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (summary.streakDays == 0) {
                    // A bare "0" over "day streak" reads as a scolding, and an
                    // em-dash reads as missing data. Neither is what this means.
                    Text(
                        text = "No streak\nyet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                } else {
                    Text(
                        text = summary.streakDays.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = if (summary.streakDays == 1) "day streak" else "days streak",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationAdherenceRow(adherence: MedicationAdherence, modifier: Modifier = Modifier) {
    val accent = ThymeTheme.accents.medicationColor(adherence.colorIndex)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(accent))
                Spacer(Modifier.size(12.dp))
                Text(
                    text = adherence.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(adherence.percent * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { adherence.percent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                // M3's stop dot leaves a detached pip past the end of the bar,
                // which reads as a stray data point on a chart.
                drawStopIndicator = {},
            )
        }
    }
}
