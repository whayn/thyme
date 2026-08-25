package dev.whayn.thyme.alert

import dev.whayn.thyme.data.AlertState
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.DoseAlert
import dev.whayn.thyme.data.TodayDose
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * One group of doses that should make noise at the same instant.
 *
 * The group's [tier] is the loudest of its members - a Strong medication makes
 * the whole group ring like a Strong - but skippability stays per dose, so a
 * critical pill and a vitamin sharing 08:00 still get their own action sets.
 */
data class PlannedAlert(
    val fireAt: Instant,
    val forDate: LocalDate,
    val doseIds: List<Long>,
    val tier: AlertTier,
    val anyCritical: Boolean,
) {
    /**
     * Stable identifier for this group: the minute it fires at.
     *
     * Used as the notification id and as the seed for action PendingIntent
     * request codes, so it has to fit in an Int - epoch minutes are about 29.5
     * million now and stay inside Int for millennia.
     *
     * Keyed on the instant rather than on forDate plus minute-of-day, because a
     * dose snoozed across midnight fires on a different day from the one it
     * belongs to, and the two schemes would collide there.
     */
    val groupKey: Long get() = fireAt.epochSecond / 60
}

/**
 * Works out what should ring next. Deliberately pure: no Android, no Room, no
 * clock of its own - everything arrives as a parameter, so the whole thing is
 * testable on the JVM with no emulator and no new dependencies.
 */
object AlertPlan {

    /**
     * How far ahead to look for the next dose.
     *
     * A regimen can legitimately have nothing due for weeks: a 21-on/7-off cycle
     * has a week-long gap, and a course can start in the future. The scan stops
     * at the first day that yields a candidate, so in practice it reads today
     * and returns.
     */
    const val LOOKAHEAD_DAYS = 62

    /**
     * How far into the past a dose can be and still ring on sight.
     *
     * The phone was off, or the alarm was dropped, and we are only finding out
     * now. Inside this window it is still worth taking, so fire immediately.
     * Older than that it is simply missed, and the Today screen already says so
     * - waking someone at 14:00 about an 08:00 dose helps nobody.
     */
    val CATCH_UP: Duration = Duration.ofMinutes(60)

    /** A dose's own instant. Calendar time: date D at time T, no 05:00 rotation. */
    fun instantOf(date: LocalDate, time: LocalTime, zone: ZoneId): Instant =
        date.atTime(time).atZone(zone).toInstant()

    /**
     * The doses on [date] that could still ring: not already resolved, and not
     * set to never alert.
     */
    fun candidatesOn(
        date: LocalDate,
        doses: List<TodayDose>,
        zone: ZoneId,
        /**
         * Doses that already have an alert row for [date], in **any** state.
         *
         * Deliberately has no default: forgetting it is a ring-forever bug, not
         * a degraded one. A dose that fired and then EXPIRED at its snooze cap
         * still has no log row - absence means missed - so it stays "unresolved"
         * forever and sits inside the catch-up window for the next hour. Leave
         * it in the candidate set and the alarm re-arms into the past and fires
         * immediately, over and over. SNOOZED rows are excluded here too because
         * they belong to the snooze path, which fires them at `nextFireAt`
         * rather than at their scheduled time.
         */
        alreadyHandled: Set<Long>,
    ): List<Pair<Instant, TodayDose>> =
        doses.asSequence()
            .filter { !it.resolved && it.alertTier != AlertTier.NONE }
            .filter { it.scheduled.id !in alreadyHandled }
            .map { instantOf(date, it.scheduled.time, zone) to it }
            .sortedBy { it.first }
            .toList()

    /**
     * Folds one day's candidates into groups sharing an instant, and returns the
     * earliest group at or after [from].
     *
     * Grouping is by exact instant. Doses a minute apart therefore ring a minute
     * apart, which is rare - times come from a picker and people choose round
     * numbers - and coalescing them would mean marking the pulled-forward doses
     * as fired ahead of time, which is exactly the eager `dose_alerts` row the
     * design avoids. Worth revisiting only if it turns out to bite.
     */
    fun groupFrom(
        candidates: List<Pair<Instant, TodayDose>>,
        date: LocalDate,
        from: Instant,
        /**
         * Everything due on [date], including doses set to Off.
         *
         * A NONE dose never *causes* an alert - it is filtered out of
         * [candidatesOn] before it can. But once something else has already put
         * a screen in front of you at that exact instant, hiding the vitamin due
         * in the same breath just means handling it twice. So NONE doses join a
         * group they did not trigger, and never form one alone.
         */
        allDue: List<TodayDose> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): PlannedAlert? {
        val eligible = candidates.filter { !it.first.isBefore(from) }
        val first = eligible.firstOrNull() ?: return null
        val triggering = eligible.filter { it.first == first.first }.map { it.second }

        val silentPassengers = allDue.filter {
            it.alertTier == AlertTier.NONE &&
                !it.resolved &&
                it.scheduled.id !in triggering.map { t -> t.scheduled.id } &&
                instantOf(date, it.scheduled.time, zone) == first.first
        }

        return PlannedAlert(
            fireAt = first.first,
            forDate = date,
            doseIds = (triggering + silentPassengers).map { it.scheduled.id },
            // Tier and criticality come from the doses that actually triggered
            // this: a passenger set to Off must not make anything louder.
            tier = triggering.maxOf { it.alertTier },
            anyCritical = triggering.any { it.critical },
        )
    }

    /**
     * The earliest of a scheduled group and a pending snooze.
     *
     * Snoozes are not a separate alarm: they compete for the same single
     * `setAlarmClock` slot, which is what keeps "one alarm at a time" true no
     * matter how many doses have been put off.
     */
    fun soonest(scheduled: PlannedAlert?, snoozed: PlannedAlert?): PlannedAlert? = when {
        scheduled == null -> snoozed
        snoozed == null -> scheduled
        snoozed.fireAt.isBefore(scheduled.fireAt) -> snoozed
        else -> scheduled
    }

    /**
     * Turns snoozed [DoseAlert] rows into a group, if any are waiting.
     *
     * [tierOf] resolves each dose's medication tier; a snoozed dose whose
     * medication has since been set to Off simply drops out.
     */
    fun snoozedGroup(
        alerts: List<DoseAlert>,
        tierOf: (DoseAlert) -> Pair<AlertTier, Boolean>?,
    ): PlannedAlert? {
        val live = alerts.filter { it.state == AlertState.SNOOZED }
            .mapNotNull { alert -> tierOf(alert)?.let { alert to it } }
            .filter { (_, tier) -> tier.first != AlertTier.NONE }
        val earliest = live.minByOrNull { it.first.nextFireAt } ?: return null
        val together = live.filter { it.first.nextFireAt == earliest.first.nextFireAt }
        return PlannedAlert(
            fireAt = earliest.first.nextFireAt,
            // The dose's own day, never recomputed from the fire instant: a 23:50
            // dose snoozed ten minutes fires tomorrow but still belongs to today.
            forDate = earliest.first.forDate,
            doseIds = together.map { it.first.scheduledDoseId },
            tier = together.maxOf { it.second.first },
            anyCritical = together.any { it.second.second },
        )
    }
}
