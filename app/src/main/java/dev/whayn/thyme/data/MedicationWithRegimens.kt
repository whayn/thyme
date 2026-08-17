package dev.whayn.thyme.data

import androidx.room.Embedded
import androidx.room.Relation
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

/** The whole tree for one medication — what the management screen lists. */
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
}

/**
 * A time plus how much to take, as the editor hands it back.
 *
 * [id] carries the originating `scheduled_doses` row so a save can reconcile by
 * *identity* rather than by clock time. Keying on time would make moving 08:00
 * to 09:00 a delete-plus-insert, which silently strands the dose_logs attached
 * to the old row — the dose would appear to lose its whole history.
 * Zero means "new row".
 */
data class DoseTime(
    val id: Long = 0,
    val time: LocalTime,
    val quantity: Double = 1.0,
)
