package dev.whayn.thyme.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Medication::class, Regimen::class, ScheduledDose::class, DoseLog::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ThymeDatabase : RoomDatabase() {

    abstract fun doseDao(): DoseDao

    companion object {
        @Volatile
        private var instance: ThymeDatabase? = null

        fun get(context: Context): ThymeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ThymeDatabase::class.java,
                    "thyme.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true) // to drop before prod
                    .build()
                    .also { instance = it }
            }
    }
}