package dev.whayn.thyme.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalTime

@Entity(
    tableName = "scheduled_doses",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicationId")]
)
data class ScheduledDose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationId: Long,
    val time: LocalTime,
    val quantity: Double = 1.0, // Quantity is the amount of pills taken in one dose
    val active: Boolean = true,
)