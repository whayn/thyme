package dev.whayn.thyme.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = ScheduledDose::class,
            parentColumns = ["id"],
            childColumns = ["scheduledDoseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["scheduledDoseId", "forDate"], unique = true)
    ]
)
data class DoseLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val scheduledDoseId: Long,
    val forDate: LocalDate,
    val takenAt: Instant,
)