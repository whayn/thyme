package dev.whayn.thyme.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.whayn.thyme.DoseListState
import dev.whayn.thyme.data.TodayDose
import dev.whayn.thyme.ui.theme.ThymeDimens
import dev.whayn.thyme.ui.theme.ThymeTheme
import dev.whayn.thyme.ui.theme.rememberReducedMotion
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

// The stem lives in a fixed left gutter: time label, then the line, then cards.
private val GutterWidth = 88.dp
private val TimeWidth = 60.dp
private val StemX = 72.dp
private val NodeRadius = 7.dp
private val CardGap = 10.dp

/** The part-of-day icon that sits on the stem, and the hole punched for it. */
private val SectionIconSize = 18.dp
private val SectionBadgeRadius = 13.dp

/** Thyme's day starts at 05:00, so a 02:00 dose is tonight's last, not tomorrow's first. */
internal const val DAY_START_MINUTE = 5 * 60

private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val weekdayFormatter = DateTimeFormatter.ofPattern("EEEE")
private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMMM")
private val shortDayFormatter = DateTimeFormatter.ofPattern("EEE")

/**
 * The icon rides the stem as a bead, so the header reads as a station on the
 * timeline rather than a separate block of text. It also carries the meaning
 * that the label no longer has room to: the label sits out in the card column
 * now, because "MORNING" at 11sp with 1.4 tracking is wider than the 72dp of
 * gutter before the stem and used to run straight through the line.
 */
private enum class PartOfDay(val label: String, val icon: ImageVector) {
    Morning("Morning", Icons.Filled.WbTwilight),
    Midday("Midday", Icons.Filled.LightMode),
    // Chosen for optical balance as much as meaning: these sit centred on a 2dp
    // line, so a glyph whose mass hangs off to one side reads as crooked even
    // when its bounding box is exactly centred.
    Evening("Evening", Icons.Filled.Brightness4),
    Night("Night", Icons.Filled.Bedtime),
}

private fun partOf(time: LocalTime): PartOfDay = when (time.hour) {
    in 5..11 -> PartOfDay.Morning
    in 12..16 -> PartOfDay.Midday
    in 17..20 -> PartOfDay.Evening
    else -> PartOfDay.Night
}

/** Minutes since 05:00, so sorting and section order agree without special cases. */
internal fun dayOrder(time: LocalTime): Int {
    val minutes = time.hour * 60 + time.minute
    return if (minutes >= DAY_START_MINUTE) minutes - DAY_START_MINUTE
    else minutes + (24 * 60 - DAY_START_MINUTE)
}

/**
 * Where the displayed day sits relative to the real one. Past days are wholly
 * behind you and future days wholly ahead, whatever the clock says.
 */
internal enum class DayPosition { Past, Today, Future }

/**
 * Raw clock comparison, deliberately *not* [dayOrder]. That function rotates the
 * clock so 05:00 is zero, which is a valid sort key but not a valid "is this
 * before that" test: the rotation breaks the ordering for anything spanning it.
 * Using it here marked every dose overdue between 00:00 and 05:00, because
 * dayOrder(03:00) is 1320 and so compares as later than the whole day.
 */
internal fun isOverdue(
    time: LocalTime,
    now: LocalTime,
    position: DayPosition,
    resolved: Boolean,
): Boolean = !resolved && when (position) {
    DayPosition.Past -> true
    DayPosition.Future -> false
    DayPosition.Today -> time < now
}

/**
 * What the list actually renders, once headers and the now-marker are folded in.
 *
 * Every line carries what it needs to draw its slice of the stem, because the
 * stem has to be continuous from the first line to the last, and a header that
 * doesn't know it sits mid-thread is what previously cut the day into four
 * disconnected segments.
 */
private sealed interface Line {
    /** True once this line is behind the current moment, which dims its stem. */
    val elapsed: Boolean

    data class Header(val part: PartOfDay, override val elapsed: Boolean) : Line
    data class Dose(
        val item: TodayDose,
        override val elapsed: Boolean,
        /** False when the row above carries the same clock time. */
        val showTime: Boolean,
    ) : Line

    data class Now(override val elapsed: Boolean = true) : Line
}

@Composable
fun DoseListScreen(
    state: DoseListState,
    date: LocalDate,
    today: LocalDate,
    now: LocalTime,
    onToggle: (TodayDose) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onNavigateToAdd: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    // First load: keep the header and week strip (neither needs data), and hold
    // the list area with a spinner instead of flashing the empty state.
    if (state.loading) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "header") { DayHeader(date = date, taken = null, total = null) }
            item(key = "strip") { WeekStrip(selected = date, today = today, onSelect = onSelectDate) }
            item(key = "loading") {
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

    val doses = state.doses

    // Keep the strip visible even when this date is empty. A course can start in
    // the future (or only run on weekdays), and the user must still be able to
    // browse to the date where it applies.
    if (doses.isEmpty()) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            item(key = "header") { DayHeader(date = date, taken = 0, total = 0) }
            item(key = "strip") { WeekStrip(selected = date, today = today, onSelect = onSelectDate) }
            item(key = "empty") {
                EmptyState(
                    onAddMedication = onNavigateToAdd,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
                )
            }
        }
        return
    }

    // Only "today" has a present moment in it; past days are wholly elapsed,
    // future days wholly ahead. This used to be smuggled through a sentinel
    // time - 04:59 and 05:00, chosen to land at either end of dayOrder's
    // rotation - which only worked while elapsed/overdue went through dayOrder.
    // Say it directly instead, so the two can never drift apart again.
    val position = when {
        date == today -> DayPosition.Today
        date.isBefore(today) -> DayPosition.Past
        else -> DayPosition.Future
    }
    val lines = remember(doses, now, position) { buildLines(doses, now, position) }
    val takenCount = doses.count { it.taken }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "header") {
            DayHeader(date = date, taken = takenCount, total = doses.size)
        }
        item(key = "strip") {
            WeekStrip(selected = date, today = today, onSelect = onSelectDate)
        }

        items(
            count = lines.size,
            key = { index ->
                when (val line = lines[index]) {
                    is Line.Header -> "h-${line.part.name}"
                    is Line.Dose -> "d-${line.item.scheduled.id}"
                    is Line.Now -> "now"
                }
            },
        ) { index ->
            when (val line = lines[index]) {
                is Line.Header -> SectionHeader(line.part, line.elapsed)
                is Line.Now -> NowMarker()
                is Line.Dose -> DoseStemRow(
                    item = line.item,
                    now = now,
                    position = position,
                    elapsed = line.elapsed,
                    showTime = line.showTime,
                    onToggle = { onToggle(line.item) },
                )
            }
        }

        item(key = "tail") { Spacer(Modifier.height(96.dp)) }
    }
}

/**
 * Groups doses into parts of the day and slots the now-marker in at its true
 * position, including between sections, or at the very end once every dose is
 * behind you. Shown only on today, since other days have no "now".
 */
private fun buildLines(
    doses: List<TodayDose>,
    now: LocalTime,
    position: DayPosition,
): List<Line> {
    val ordered = doses.sortedBy { dayOrder(it.scheduled.time) }
    var markerPlaced = position != DayPosition.Today
    var currentPart: PartOfDay? = null
    var previousTime: LocalTime? = null

    return buildList {
        ordered.forEach { item ->
            val time = item.scheduled.time
            // Elapsed and the marker both read the raw clock, so they always
            // agree. Ordering still comes from dayOrder, and the two only
            // diverge for a dose before 05:00: it sorts to the bottom as
            // tonight's last pill while having already happened this morning.
            // That is the truth under calendar days, so let it show.
            val elapsed = item.resolved || when (position) {
                DayPosition.Past -> true
                DayPosition.Future -> false
                DayPosition.Today -> time <= now
            }

            if (!markerPlaced && time > now) {
                add(Line.Now())
                markerPlaced = true
            }
            val part = partOf(time)
            if (part != currentPart) {
                add(Line.Header(part, elapsed = elapsed))
                currentPart = part
                // A new section restarts the run, so the first dose under a
                // header always prints its time even if it repeats the last one.
                previousTime = null
            }
            // An overdue dose always prints its time, even when it repeats the
            // row above: "when was this due" is the whole question being asked.
            val overdue = isOverdue(time, now, position, item.resolved)
            add(Line.Dose(item, elapsed = elapsed, showTime = overdue || time != previousTime))
            previousTime = time
        }
        if (!markerPlaced) add(Line.Now())
    }
}

@Composable
private fun DayHeader(date: LocalDate, taken: Int?, total: Int?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ThymeDimens.PageGutter,
                end = ThymeDimens.PageGutter,
                top = 20.dp,
                bottom = 8.dp,
            )
    ) {
        SectionEyebrow(date.format(weekdayFormatter))
        Spacer(Modifier.height(4.dp))
        Text(
            // displaySmall, matching the other three tabs. Today used to sit a
            // step higher at displayMedium, so the four tabs opened at two
            // different sizes.
            text = date.format(dayMonthFormatter),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        if (taken != null && total != null) {
            Spacer(Modifier.height(18.dp))

            val progress = if (total == 0) 0f else taken.toFloat() / total
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = motionFloatSpec(),
                label = "progress",
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(ThymeTheme.accents.stem)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    total == 0 -> "Nothing scheduled"
                    taken == total -> "All $total taken"
                    else -> "$taken of $total taken"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekStrip(
    selected: LocalDate,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    // Three weeks back for history, a week ahead for planning.
    val days = remember(today) { (-21L..7L).map { today.plusDays(it) } }
    val listState = rememberLazyListState()
    val selectedIndex = remember(days, selected) {
        days.indexOf(selected).takeIf { it >= 0 } ?: 21
    }

    LaunchedEffect(selectedIndex) {
        listState.animateScrollToItem((selectedIndex - 2).coerceAtLeast(0))
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(days.size, key = { days[it].toEpochDay() }) { index ->
            val day = days[index]
            DayPill(
                day = day,
                selected = day == selected,
                isToday = day == today,
                onClick = { onSelect(day) },
            )
        }
    }
}

@Composable
private fun DayPill(
    day: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) scheme.primaryContainer else Color.Transparent,
        motionColorSpec(),
        label = "pill",
    )
    val content = when {
        selected -> scheme.onPrimaryContainer
        isToday -> scheme.primary
        else -> scheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .then(
                if (isToday && !selected) {
                    Modifier.border(1.dp, scheme.primary, MaterialTheme.shapes.large)
                } else Modifier
            )
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.format(shortDayFormatter).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = content,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = day.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = content,
        )
    }
}

/**
 * The unbroken vertical line in the left gutter.
 *
 * Every line type draws it, which is the whole point: the stem is the app's
 * signature element and is supposed to read as one continuous day. Section
 * headers used to be a bare [Text] with no gutter at all, so the thread was cut
 * once per part of day.
 */
@Composable
private fun StemGutter(
    elapsed: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val accents = ThymeTheme.accents
    val lineColor = if (elapsed) accents.stemSpent else accents.stem
    Box(
        modifier = modifier
            .width(GutterWidth)
            .fillMaxHeight(),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(GutterWidth)
        ) {
            val x = StemX.toPx()
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
        content()
    }
}

@Composable
private fun SectionHeader(part: PartOfDay, elapsed: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val accents = ThymeTheme.accents
    val lineColor = if (elapsed) accents.stemSpent else accents.stem

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // The stem runs the whole row, padding included, so it meets the rows
        // above and below without a seam.
        Canvas(
            modifier = Modifier
                .fillMaxHeight()
                .width(GutterWidth)
        ) {
            val x = StemX.toPx()
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }

        // Badge and label live in one centred Row, so they cannot drift apart.
        // Aligning the icon to the *row* instead put it 4dp above the text,
        // because the row's asymmetric padding moves the text's centre but not
        // the row's. The badge is drawn after the stem Canvas and therefore over
        // it. That opaque disc is what punches the line out behind the icon.
        Row(
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(GutterWidth)
                    .padding(start = StemX - SectionBadgeRadius),
            ) {
                Box(
                    modifier = Modifier
                        .size(SectionBadgeRadius * 2)
                        .clip(CircleShape)
                        .background(scheme.background),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = part.icon,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(SectionIconSize),
                    )
                }
            }
            // Aligned with the card column, where there is room for it.
            Text(
                text = part.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun NowMarker() {
    val due = ThymeTheme.accents.due
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Deliberately short. The marker is a hairline between two doses, and
            // when it lands next to a section header the two rows stack, so every
            // dp here was showing up as dead gutter between cards.
            .height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Keep the stem unbroken through the marker: it's one continuous day.
        StemGutter(elapsed = true) {
            Text(
                text = "NOW",
                style = MaterialTheme.typography.labelSmall,
                color = due,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(TimeWidth),
            )
        }
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(2.dp)
                .padding(end = 20.dp)
        ) {
            drawLine(
                color = due,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 9f)),
            )
        }
    }
}

@Composable
private fun DoseStemRow(
    item: TodayDose,
    now: LocalTime,
    position: DayPosition,
    elapsed: Boolean,
    showTime: Boolean,
    onToggle: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val accents = ThymeTheme.accents
    val overdue = isOverdue(item.scheduled.time, now, position, item.resolved)
    val medColor = accents.medicationColor(item.colorIndex)

    // Attention outranks identity: a due dose goes honey, everything else wears
    // the medication's own colour.
    val nodeTarget = if (overdue) accents.due else medColor
    val nodeColor by animateColorAsState(nodeTarget, motionColorSpec(), label = "node")
    val fill by animateFloatAsState(if (item.taken) 1f else 0f, motionFloatSpec(), label = "fill")

    val background = scheme.background
    val lineColor = if (elapsed) accents.stemSpent else accents.stem

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(GutterWidth)
                .fillMaxHeight()
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(GutterWidth)
            ) {
                val x = StemX.toPx()
                // Centre the node on the card, not the row, which is taller by
                // the gap that separates one card from the next.
                val cy = (size.height - CardGap.toPx()) / 2f
                val r = NodeRadius.toPx()

                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx(),
                )
                // Punch the line out from behind the node so hollow nodes read as
                // beads on a thread rather than a line crossing a circle.
                drawCircle(color = background, radius = r + 2.dp.toPx(), center = Offset(x, cy))
                if (fill > 0f) {
                    drawCircle(color = nodeColor, radius = r * fill, center = Offset(x, cy))
                }
                drawCircle(
                    color = nodeColor,
                    radius = r,
                    center = Offset(x, cy),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }

            // Three doses at 08:00 printed "8:00 AM" three times down the
            // gutter, which made one moment look like three. The node still
            // draws for every dose; only the repeated stamp is dropped.
            if (showTime) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = CardGap)
                ) {
                    Text(
                        text = item.scheduled.time.format(timeFormatter),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
                        color = if (overdue) accents.due else scheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(TimeWidth),
                    )
                }
            }
        }

        DoseCard(
            item = item,
            overdue = overdue,
            accentColor = medColor,
            onToggle = onToggle,
            modifier = Modifier
                .weight(1f)
                .padding(end = 20.dp, bottom = CardGap),
        )
    }
}

@Composable
private fun DoseCard(
    item: TodayDose,
    overdue: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val containerTarget =
        if (item.resolved) scheme.surfaceContainerLow else scheme.surfaceContainer
    val container by animateColorAsState(containerTarget, motionColorSpec(), label = "card")
    val shape = MaterialTheme.shapes.medium

    Surface(
        // Clip first so the ripple stays inside the card: the tap target and the
        // thing that looks tappable have to be the same shape.
        modifier = modifier
            .clip(shape)
            .toggleable(
                // Resolved, not taken: tapping a skipped row clears it back to
                // pending, so the control has to read as "on" for both outcomes
                // or the checkbox state lies to screen readers.
                value = item.resolved,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
            ),
        shape = shape,
        color = container,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val medColorRight = ThymeTheme.accents.medicationColor(item.colorIndexRight)
            MedicationAvatar(
                formIndex = item.form,
                colorLeft = if (item.resolved) scheme.onSurfaceVariant else accentColor,
                colorRight = if (item.resolved) scheme.onSurfaceVariant else medColorRight,
                containerColor = if (item.resolved) scheme.surfaceContainer
                else scheme.surfaceContainerHighest,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.medicationName,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (item.resolved) scheme.onSurfaceVariant else scheme.onSurface,
                )
                val detail = doseDetail(item)
                val reason = item.skipReason?.takeIf { it.isNotBlank() }
                if (overdue || item.skipped || detail.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = buildAnnotatedString {
                            if (item.skipped) {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                                    append("Skipped")
                                }
                                if (reason != null) append(" · $reason")
                                if (detail.isNotEmpty()) append("   ")
                            } else if (overdue) {
                                withStyle(
                                    SpanStyle(
                                        color = ThymeTheme.accents.due,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                ) { append("Due") }
                                if (detail.isNotEmpty()) append("   ")
                            }
                            append(detail)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            CheckDot(taken = item.taken, skipped = item.skipped, accentColor = accentColor)
        }
    }
}

@Composable
private fun CheckDot(taken: Boolean, skipped: Boolean, accentColor: Color) {
    val scheme = MaterialTheme.colorScheme
    // Skipped fills too - it is a decision, not an absence - but in the muted
    // role rather than the medication's colour, and it wears a dash, not a tick.
    val container by animateColorAsState(
        when {
            taken -> accentColor
            skipped -> scheme.surfaceContainerHighest
            else -> Color.Transparent
        },
        motionColorSpec(),
        label = "dot",
    )
    val border by animateColorAsState(
        when {
            taken -> accentColor
            skipped -> scheme.outlineVariant
            else -> scheme.outline
        },
        motionColorSpec(),
        label = "dotBorder",
    )
    val scale by animateFloatAsState(
        if (taken || skipped) 1f else 0f,
        motionFloatSpec(),
        label = "tick",
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(container)
            .border(2.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (scale > 0.01f) {
            Icon(
                imageVector = if (skipped) Icons.Filled.Remove else Icons.Filled.Check,
                contentDescription = null, // the card already announces checked state
                // Medication colours are light on dark and dark on light, so the
                // page surface is always the correct contrast for the tick. The
                // skipped dash sits on a surface tone instead, so it needs ink.
                tint = if (skipped) scheme.onSurfaceVariant else scheme.surface,
                modifier = Modifier.size((20f * scale).dp),
            )
        }
    }
}

@Composable
private fun EmptyState(
    onAddMedication: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Gutter comes from the caller only. This used to add another 40dp on top of
    // the caller's 32dp, squeezing the copy into a 72dp-inset ribbon.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Nothing scheduled",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Add a medication and its doses will show up here, laid out across your day.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddMedication, shape = MaterialTheme.shapes.large) {
            Text("Add medication")
        }
    }
}

/** Reads the way a label on a box does: "2 × 500mg", "500mg", or "×2". */
private fun doseDetail(item: TodayDose): String {
    val single = item.scheduled.quantity == 1.0
    val qty = formatQuantity(item.scheduled.quantity)
    val strength = item.strength?.takeIf { it.isNotBlank() }
    return when {
        strength != null && !single -> "$qty × $strength"
        strength != null -> strength
        !single -> "×$qty"
        else -> ""
    }
}

private fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

@Composable
private fun motionFloatSpec() = if (rememberReducedMotion()) {
    snap()
} else {
    spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)
}

@Composable
private fun motionColorSpec() = if (rememberReducedMotion()) {
    snap()
} else {
    spring<Color>(stiffness = Spring.StiffnessMediumLow)
}
