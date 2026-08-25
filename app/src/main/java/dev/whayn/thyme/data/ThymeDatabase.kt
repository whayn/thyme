package dev.whayn.thyme.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Medication::class,
        Regimen::class,
        ScheduledDose::class,
        DoseLog::class,
        DoseAlert::class,
    ],
    version = 7,
    exportSchema = true
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
                    // Real migrations from v7 on: there is medication history in
                    // here now, and a destructive fallback would take it out on
                    // the next schema change. Downgrades still wipe - that is the
                    // one case where starting over is the right answer.
                    .addMigrations(Migration6to7)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}