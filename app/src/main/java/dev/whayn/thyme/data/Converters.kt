package dev.whayn.thyme.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {
    @TypeConverter
    fun fromLocalTime(time: LocalTime): Int = time.toSecondOfDay() / 60

    @TypeConverter
    fun toLocalTime(minutesOfDay: Int): LocalTime =
        LocalTime.ofSecondOfDay(minutesOfDay * 60L)

    @TypeConverter
    fun fromLocalDate(date: LocalDate): Long = date.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    @TypeConverter
    fun fromInstant(instant: Instant): Long = instant.toEpochMilli()

    @TypeConverter
    fun toInstant(millis: Long): Instant = Instant.ofEpochMilli(millis)

    // Enums are stored by name, not ordinal, so the Database Inspector stays
    // readable and reordering an enum can never silently rewrite history.
    // Every read falls back rather than throwing: an unknown name from a
    // downgraded build must not take the whole query down with it.

    @TypeConverter
    fun fromAlertTier(tier: AlertTier): String = tier.name

    @TypeConverter
    fun toAlertTier(name: String): AlertTier =
        runCatching { AlertTier.valueOf(name) }.getOrDefault(AlertTier.LIGHT)

    @TypeConverter
    fun fromDoseOutcome(outcome: DoseOutcome): String = outcome.name

    @TypeConverter
    fun toDoseOutcome(name: String): DoseOutcome =
        runCatching { DoseOutcome.valueOf(name) }.getOrDefault(DoseOutcome.TAKEN)

    @TypeConverter
    fun fromAlertState(state: AlertState): String = state.name

    @TypeConverter
    fun toAlertState(name: String): AlertState =
        runCatching { AlertState.valueOf(name) }.getOrDefault(AlertState.EXPIRED)
}