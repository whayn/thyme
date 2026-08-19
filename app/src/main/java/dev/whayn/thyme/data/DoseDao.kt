package dev.whayn.thyme.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DoseDao {

    // ── Today ────────────────────────────────────────────────────────────────

    /**
     * Every dose that actually applies on [date].
     *
     * `:date` binds as an epochDay Long (see `Converters.fromLocalDate`), which
     * is what makes the interval and cycle arithmetic possible in SQL at all.
     * `startDate <= :date` also guarantees both modulo operands are
     * non-negative, so there is no negative-remainder edge case to handle.
     */
    @Query(
        """
        SELECT s.*,
               m.id AS medicationId,
               m.name AS medicationName,
               m.strength AS strength,
               m.colorIndex AS colorIndex,
               m.colorIndexRight AS colorIndexRight,
               m.form AS form,
               l.id AS logId,
               l.takenAt AS takenAt
        FROM scheduled_doses AS s
        JOIN regimens AS r
            ON r.id = s.regimenId
        JOIN medications AS m
            ON m.id = r.medicationId
        LEFT JOIN dose_logs AS l
            ON l.scheduledDoseId = s.id AND l.forDate = :date
        WHERE m.active = 1 AND r.active = 1 AND s.active = 1
          AND r.startDate <= :date
          AND (r.endDate IS NULL OR r.endDate >= :date)
          AND (r.daysOfWeek & :dayBit) != 0
          AND ((:date - r.startDate) % r.intervalDays) = 0
          AND (r.cycleOnDays IS NULL OR r.cycleOffDays IS NULL
               OR ((:date - r.startDate) % (r.cycleOnDays + r.cycleOffDays)) < r.cycleOnDays)
        ORDER BY s.time
    """
    )
    fun observeDosesForDate(date: LocalDate, dayBit: Int): Flow<List<TodayDose>>

    /** Callers never need to know about the bitmask. */
    fun observeDosesFor(date: LocalDate): Flow<List<TodayDose>> =
        observeDosesForDate(date, date.dayOfWeekBit())

    @Insert
    suspend fun insertLog(log: DoseLog)

    @Insert
    suspend fun insertLogs(logs: List<DoseLog>)

    @Query("DELETE FROM dose_logs WHERE scheduledDoseId = :scheduledDoseId AND forDate = :date")
    suspend fun deleteLog(scheduledDoseId: Long, date: LocalDate)

    // ── Medication management ────────────────────────────────────────────────

    @Transaction
    @Query("SELECT * FROM medications WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun observeMedications(): Flow<List<MedicationWithRegimens>>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    fun observeMedication(id: Long): Flow<MedicationWithRegimens?>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedication(id: Long): MedicationWithRegimens?

    /**
     * Stop taking something. Sets the end date rather than hiding the rows, so
     * every past day still renders exactly what was taken.
     */
    @Query(
        """
        UPDATE regimens SET endDate = :today
        WHERE medicationId = :medicationId AND (endDate IS NULL OR endDate > :today)
    """
    )
    suspend fun stopMedication(medicationId: Long, today: LocalDate)

    /** Stops one course the same way [stopMedication] does: end date, history intact. */
    @Query("UPDATE regimens SET endDate = :today WHERE id = :id AND (endDate IS NULL OR endDate > :today)")
    suspend fun stopRegimen(id: Long, today: LocalDate)

    /**
     * Soft delete for "I typed this wrong", where losing the history is the
     * point. Never a hard delete: dose_logs cascade off scheduled_doses.
     */
    @Query("UPDATE medications SET active = 0 WHERE id = :id")
    suspend fun deleteMedication(id: Long)

    /** Removes one course entirely, history included, by deactivating its regimen. */
    @Query("UPDATE regimens SET active = 0 WHERE id = :id")
    suspend fun deleteRegimen(id: Long)

    // ── Writes used by the editor ────────────────────────────────────────────

    @Insert
    suspend fun insertMedication(medication: Medication): Long

    @Update
    suspend fun updateMedication(medication: Medication)

    @Insert
    suspend fun insertRegimen(regimen: Regimen): Long

    @Update
    suspend fun updateRegimen(regimen: Regimen)

    @Insert
    suspend fun insertScheduledDoses(doses: List<ScheduledDose>)

    @Insert
    suspend fun insertScheduledDose(dose: ScheduledDose): Long

    @Update
    suspend fun updateScheduledDose(dose: ScheduledDose)

    @Query("SELECT * FROM scheduled_doses WHERE regimenId = :regimenId")
    suspend fun dosesForRegimen(regimenId: Long): List<ScheduledDose>

    @Query("DELETE FROM dose_logs")
    suspend fun clearDoseLogs()

    @Query("DELETE FROM scheduled_doses")
    suspend fun clearScheduledDoses()

    @Query("DELETE FROM regimens")
    suspend fun clearRegimens()

    @Query("DELETE FROM medications")
    suspend fun clearMedications()

    @Transaction
    suspend fun clearAllData() {
        clearDoseLogs()
        clearScheduledDoses()
        clearRegimens()
        clearMedications()
    }

    /**
     * Creates or updates a single regimen and its times in one transaction, so a
     * crash can never leave a course with no schedule.
     *
     * Times are *reconciled by row id*, not replaced: an edited row keeps its
     * identity and therefore the dose_logs attached to it, so changing 08:00 to
     * 09:00 moves the dose rather than resetting its history. Only rows the
     * editor dropped get deactivated, and only genuinely new ones are inserted.
     *
     * Editing times is intentionally live rather than bitemporal: past days can
     * change when a regimen is edited. Stopping a course uses endDate, so the
     * normal history-preserving operation remains correct.
     */
    @Transaction
    suspend fun saveRegimen(regimen: Regimen, times: List<DoseTime>): Long {
        val regimenId =
            if (regimen.id == 0L) insertRegimen(regimen)
            else regimen.id.also { updateRegimen(regimen) }

        val existing = dosesForRegimen(regimenId)
        val wanted = times.filter { it.id != 0L }.associateBy { it.id }

        existing.forEach { row ->
            val want = wanted[row.id]
            when {
                want == null ->
                    if (row.active) updateScheduledDose(row.copy(active = false))

                !row.active || row.time != want.time || row.quantity != want.quantity ->
                    updateScheduledDose(
                        row.copy(active = true, time = want.time, quantity = want.quantity)
                    )
            }
        }

        val toInsert = times
            .filter { it.id == 0L }
            .map { ScheduledDose(regimenId = regimenId, time = it.time, quantity = it.quantity) }
        if (toInsert.isNotEmpty()) insertScheduledDoses(toInsert)

        return regimenId
    }
}
