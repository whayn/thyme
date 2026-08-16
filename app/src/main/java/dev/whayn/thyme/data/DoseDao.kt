package dev.whayn.thyme.data

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@Dao
interface DoseDao {
    @Query(
        """
        SELECT s.*, 
               m.name AS medicationName, 
               m.strength AS strength, 
               l.id AS logId, 
               l.takenAt AS takenAt
        FROM scheduled_doses AS s
        JOIN medications AS m
            ON m.id = s.medicationId
        LEFT JOIN dose_logs AS l
            ON l.scheduledDoseId = s.id AND l.forDate = :date
        WHERE s.active = 1 AND m.active = 1
        ORDER BY s.time
    """
    )
    fun observeDosesFor(date: LocalDate): Flow<List<TodayDose>>

    @Insert
    suspend fun insertLog(log: DoseLog)

    @Query("DELETE FROM dose_logs WHERE scheduledDoseId = :scheduledDoseId AND forDate = :date")
    suspend fun deleteLog(scheduledDoseId: Long, date: LocalDate)

    @Query("SELECT * FROM medications WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findMedicationByName(name: String): Medication?

    @Insert
    suspend fun insertMedication(medication: Medication): Long

    @Insert
    suspend fun insertScheduledDose(dose: ScheduledDose)

    @Transaction
    suspend fun addDose(
        name: String,
        strength: String?,
        time: LocalTime,
        quantity: Double = 1.0,
    ) {
        val medicationId = findMedicationByName(name)?.id
            ?: insertMedication(Medication(name = name, strength = strength))
        insertScheduledDose(
            ScheduledDose(medicationId = medicationId, time = time, quantity = quantity)
        )
    }
}