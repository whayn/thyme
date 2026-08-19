package dev.whayn.thyme.data

import androidx.room.Embedded
import androidx.room.Relation
import java.time.LocalDate
import java.time.LocalTime

/** A regimen together with the times it contains. */
data class RegimenWithDoses(
    @Embedded val regimen: Regimen,
    @Relation(parentColumn = "id", entityColumn = "regimenId")
    val doses: List<ScheduledDose>,
) {
    val activeDoses: List<ScheduledDose>
        get() = doses.filter { it.active }.sortedBy { it.time }
}

/** The whole tree for one medication what the management screen lists. */
data class MedicationWithRegimens(
    @Embedded val medication: Medication,
    @Relation(
        entity = Regimen::class,
        parentColumn = "id",
        entityColumn = "medicationId",
    )
    val regimens: List<RegimenWithDoses>,
) {
    val activeRegimens: List<RegimenWithDoses>
        get() = regimens.filter { it.regimen.active }

    /**
     * Courses still running on [today]: no end date, or one that has not passed.
     *
     * Distinct from [activeRegimens], which only filters the soft-delete flag. A
     * stopped course is still `active`; it simply has an `endDate` behind it, and
     * mixing the two in one list is how "what am I taking" gets lost among "what
     * I used to take".
     */
    fun currentRegimens(today: LocalDate): List<RegimenWithDoses> =
        activeRegimens.filter { it.regimen.endDate?.isBefore(today) != true }

    /** Courses whose end date has passed. Kept because their history is real. */
    fun stoppedRegimens(today: LocalDate): List<RegimenWithDoses> =
        activeRegimens.filter { it.regimen.endDate?.isBefore(today) == true }

    /**
     * Courses that "stop taking" would actually change, using the same predicate as
     * `DoseDao.stopMedication`.
     *
     * Stopping sets `endDate = today`, and a course ending today still applies
     * today, so it stays in [currentRegimens] until tomorrow. Without this the
     * action would remain on offer after it had already been taken, looking like
     * a button that does nothing.
     */
    fun stoppableRegimens(today: LocalDate): List<RegimenWithDoses> =
        activeRegimens.filter { it.regimen.endDate == null || it.regimen.endDate.isAfter(today) }
}

/**
 * A time plus how much to take, as the editor hands it back.
 *
 * [id] carries the originating `scheduled_doses` row so a save can reconcile by
 * *identity* rather than by clock time. Keying on time would make moving 08:00
 * to 09:00 a delete-plus-insert, which silently strands the dose_logs attached
 * to the old row the dose would appear to lose its whole history.
 * Zero means "new row".
 */
data class DoseTime(
    val id: Long = 0,
    val time: LocalTime,
    val quantity: Double = 1.0,
)
