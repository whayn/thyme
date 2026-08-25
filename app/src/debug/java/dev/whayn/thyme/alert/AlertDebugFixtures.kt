package dev.whayn.thyme.alert

import android.content.Context
import dev.whayn.thyme.data.AlertTier
import dev.whayn.thyme.data.DoseDao
import dev.whayn.thyme.data.Medication
import dev.whayn.thyme.data.Regimen
import dev.whayn.thyme.data.ScheduledDose
import dev.whayn.thyme.data.ThymeDatabase
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Creates a real, one-shot dose a short way in the future.
 *
 * This is the tool the other two were missing. `fireTestAlarm` builds its own
 * PendingIntent and the DEBUG_FIRE broadcast calls [AlertDispatcher] directly:
 * both prove delivery works, and both skip the half where the bugs have
 * actually been - [AlertPlan] choosing a group, [AlarmScheduler] arming it,
 * AlarmManager holding it, [AlertAlarmReceiver] catching it, `markFired`
 * recording it and the re-arm that follows.
 *
 * Seeding an ordinary dose and letting the normal machinery find it exercises
 * every one of those in about a minute.
 */
object AlertDebugFixtures {

    /**
     * Marks the rows this file owns, so a later run can retire them without
     * touching real fixture data. A prefix rather than a flag column: the
     * schema is exported and migrated, and a debug-only column would have to
     * ship in every release build's database forever.
     */
    const val NAME_PREFIX = "[dbg]"

    data class Armed(
        val doseId: Long,
        val fireAt: Instant,
        val requestedAt: Instant,
        val planned: PlannedAlert?,
    )

    /**
     * Seeds a dose that becomes due at the first minute boundary at least
     * [minSeconds] away, then re-arms.
     *
     * Rounded up to a whole minute because `Converters.fromLocalTime` stores
     * minute-of-day: a scheduled dose cannot express seconds, so a tool that
     * promised "fires in 20s" through this path would be quietly rounding and
     * then looking like a scheduling bug. The real instant is returned instead.
     */
    suspend fun armRealDose(
        context: Context,
        minSeconds: Long,
        tier: AlertTier,
        critical: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Armed {
        val dao = ThymeDatabase.get(context).doseDao()
        retire(dao)

        val now = Instant.now()
        val target = now.plusSeconds(minSeconds).plus(Duration.ofSeconds(59))
            .truncatedTo(ChronoUnit.MINUTES)
        val local = target.atZone(zone)
        val date: LocalDate = local.toLocalDate()

        val medicationId = dao.insertMedication(
            Medication(
                name = "$NAME_PREFIX ${tier.name}${if (critical) " critical" else ""}",
                strength = local.toLocalTime().withSecond(0).withNano(0).toString(),
                colorIndex = 10,
                colorIndexRight = 2,
                alertTier = tier,
                critical = critical,
            )
        )
        // startDate == endDate == the target day makes this due exactly once.
        // An open-ended regimen would ring again every day until someone
        // noticed, which is a poor thing for a test fixture to do.
        val regimenId = dao.insertRegimen(
            Regimen(medicationId = medicationId, startDate = date, endDate = date)
        )
        val doseId = dao.insertScheduledDose(
            ScheduledDose(regimenId = regimenId, time = local.toLocalTime())
        )

        val planned = AlarmScheduler.get(context).rearm("debug-arm")
        return Armed(doseId = doseId, fireAt = target, requestedAt = now, planned = planned)
    }

    /**
     * Seeds a group of doses due now, and hands back their ids.
     *
     * The notification's shape is decided almost entirely by the group rather
     * than by the tier: one dose says "Taken" and several say "Take all", a
     * critical dose loses the one-tap Skip action, and a spent snooze cap
     * removes Snooze. Firing whatever real dose happened to be next could not
     * reach most of those combinations, which is why this exists.
     *
     * Real rows, not a fake payload: the notification actions are
     * PendingIntents into [AlertResponder], so they only genuinely work - and
     * only genuinely prove anything - against doses that exist.
     */
    suspend fun seedGroup(
        context: Context,
        count: Int,
        tier: AlertTier,
        critical: Boolean,
        /** Only the first dose is critical, so the per-dose skip friction shows. */
        mixed: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Pair<List<Long>, LocalDate> {
        val dao = ThymeDatabase.get(context).doseDao()
        retire(dao)

        val now = Instant.now().atZone(zone)
        val date = now.toLocalDate()
        val time = now.toLocalTime()
        val ids = (0 until count.coerceAtLeast(1)).map { index ->
            val doseCritical = when {
                mixed -> index == 0
                else -> critical
            }
            val medicationId = dao.insertMedication(
                Medication(
                    name = "$NAME_PREFIX ${SAMPLES[index % SAMPLES.size].first}",
                    strength = SAMPLES[index % SAMPLES.size].second,
                    colorIndex = SAMPLES[index % SAMPLES.size].third,
                    colorIndexRight = SAMPLES[index % SAMPLES.size].third,
                    form = index % 4,
                    alertTier = tier,
                    critical = doseCritical,
                )
            )
            val regimenId = dao.insertRegimen(
                Regimen(medicationId = medicationId, startDate = date, endDate = date)
            )
            dao.insertScheduledDose(ScheduledDose(regimenId = regimenId, time = time))
        }
        return ids to date
    }

    /** Recognisable names, so a screenshot says which case it is. */
    private val SAMPLES = listOf(
        Triple("Sertraline", "100mg", 6),
        Triple("Vitamin D3", "1000IU", 2),
        Triple("Amoxicillin", "500mg", 0),
        Triple("Melatonin", "3mg", 9),
        Triple("Salbutamol", "100mcg", 8),
        Triple("Ibuprofen", "200mg", 11),
    )

    /**
     * Soft-deletes every fixture this file has created.
     *
     * Soft, not hard: `dose_logs` and `dose_alerts` cascade off these rows, so
     * a hard delete would take the evidence of the run that just happened with
     * it - and the evidence is the point. `active = 0` is enough to keep them
     * out of every query and every future alert.
     */
    suspend fun retire(dao: DoseDao): Int {
        val mine = dao.observeMedications().first()
            .filter { it.medication.name.startsWith(NAME_PREFIX) }
        mine.forEach { dao.deleteMedication(it.medication.id) }
        return mine.size
    }
}
