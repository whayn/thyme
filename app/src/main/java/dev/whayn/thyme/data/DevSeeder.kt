package dev.whayn.thyme.data

import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

/**
 * Debug-only fixture data. Wired to a button behind [dev.whayn.thyme.BuildConfig.DEBUG]
 * (see SettingsScreen) so the app never ships a way to trigger this.
 *
 * Backdates dose_logs across [historyDays] with randomised adherence rather than
 * inserting only today's rows, because the thing worth exercising is Stats 
 * streaks, percentages, the calendar heatmap  none of which show anything
 * interesting from a single day of logs.
 */
object DevSeeder {

    private const val historyDays = 45

    private data class Fixture(
        val name: String,
        val strength: String?,
        val colorIndex: Int,
        val colorIndexRight: Int,
        val form: Int,
        val times: List<LocalTime>,
        val adherence: Double, // 0..1 chance any given due dose actually gets logged
        val daysOfWeek: Int = Recurrence.EVERY_DAY,
        val intervalDays: Int = 1,
        val cycleOnDays: Int? = null,
        val cycleOffDays: Int? = null,
        val startedDaysAgo: Int = historyDays,
    )

    private val fixtures = listOf(
        Fixture(
            name = "Sertraline",
            strength = "100mg",
            colorIndex = 6, // green
            colorIndexRight = 6,
            form = 1, // round tablet
            times = listOf(LocalTime.of(8, 0)),
            adherence = 0.95,
        ),
        Fixture(
            name = "Vitamin D3",
            strength = "1000IU",
            colorIndex = 2, // yellow
            colorIndexRight = 2,
            form = 8, // softgel
            times = listOf(LocalTime.of(8, 0)),
            adherence = 0.8,
        ),
        Fixture(
            name = "Amoxicillin",
            strength = "500mg",
            colorIndex = 0, // white
            colorIndexRight = 10, // red
            form = 0, // capsule
            times = listOf(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0)),
            adherence = 0.9,
            startedDaysAgo = 6,
        ),
        Fixture(
            name = "Ibuprofen",
            strength = "200mg",
            colorIndex = 11, // orange
            colorIndexRight = 11,
            form = 3, // caplet
            times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0)),
            adherence = 0.5,
            daysOfWeek = Recurrence.WEEKENDS,
        ),
        Fixture(
            name = "Salbutamol",
            strength = "100mcg",
            colorIndex = 8, // blue
            colorIndexRight = 8,
            form = 12, // inhaler
            times = listOf(LocalTime.of(7, 30)),
            adherence = 0.6,
            intervalDays = 2,
        ),
        Fixture(
            name = "Melatonin",
            strength = "3mg",
            colorIndex = 9, // purple
            colorIndexRight = 9,
            form = 1,
            times = listOf(LocalTime.of(22, 0)),
            adherence = 0.7,
            daysOfWeek = Recurrence.WEEKDAYS,
        ),
        Fixture(
            name = "Prednisone taper",
            strength = "10mg",
            colorIndex = 4, // pink
            colorIndexRight = 4,
            form = 1,
            times = listOf(LocalTime.of(8, 0)),
            adherence = 1.0,
            cycleOnDays = 5,
            cycleOffDays = 2,
            startedDaysAgo = 20,
        ),
    )

    suspend fun seed(
        dao: DoseDao,
        today: LocalDate = LocalDate.now(),
        random: Random = Random(seed = 20260818)
    ) {
        fixtures.forEach { fixture ->
            val medicationId = dao.insertMedication(
                Medication(
                    name = fixture.name,
                    strength = fixture.strength,
                    colorIndex = fixture.colorIndex,
                    colorIndexRight = fixture.colorIndexRight,
                    form = fixture.form,
                )
            )
            val startDate = today.minusDays(fixture.startedDaysAgo.toLong())
            val regimenId = dao.insertRegimen(
                Regimen(
                    medicationId = medicationId,
                    startDate = startDate,
                    daysOfWeek = fixture.daysOfWeek,
                    intervalDays = fixture.intervalDays,
                    cycleOnDays = fixture.cycleOnDays,
                    cycleOffDays = fixture.cycleOffDays,
                )
            )
            val doseIds = fixture.times.map { time ->
                dao.insertScheduledDose(ScheduledDose(regimenId = regimenId, time = time))
            }

            val logs = mutableListOf<DoseLog>()
            var date = startDate
            while (!date.isAfter(today)) {
                if (isDue(date, startDate, fixture) && random.nextDouble() < fixture.adherence) {
                    fixture.times.zip(doseIds).forEach { (time, doseId) ->
                        val loggedTime = time.plusMinutes(random.nextLong(-20, 21))
                        logs += DoseLog(
                            scheduledDoseId = doseId,
                            forDate = date,
                            loggedAt = date.atTime(loggedTime)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant(),
                        )
                    }
                }
                date = date.plusDays(1)
            }
            dao.insertLogs(logs)
        }
    }

    private fun isDue(date: LocalDate, startDate: LocalDate, fixture: Fixture): Boolean {
        val elapsed = java.time.temporal.ChronoUnit.DAYS.between(startDate, date)
        if (elapsed % fixture.intervalDays != 0L) return false
        if (Recurrence.bitOf(date) and fixture.daysOfWeek == 0) return false
        val on = fixture.cycleOnDays
        val off = fixture.cycleOffDays
        if (on != null && off != null && elapsed % (on + off) >= on) return false
        return true
    }
}
