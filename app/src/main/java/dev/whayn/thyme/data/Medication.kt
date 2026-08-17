package dev.whayn.thyme.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val strength: String? = null, // Strength is the strength of one pill, ie 1000mg of paracetamol
    // Index into the theme's medication palette, not an ARGB value — so one
    // saved choice renders correctly in both light and dark.
    val colorIndex: Int = 0,
    val active: Boolean = true,
)