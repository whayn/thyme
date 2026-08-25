package dev.whayn.thyme.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the alert system's columns and its state table.
 *
 * `dose_logs` has to be rebuilt rather than altered: renaming `takenAt` to
 * `loggedAt` needs `ALTER TABLE ... RENAME COLUMN`, which SQLite only gained in
 * 3.25 (API 30), and minSdk here is 26. The create/copy/drop/rename dance is the
 * portable equivalent, and it must recreate the unique index afterwards - that
 * index is what makes `resolveDose` an upsert rather than a duplicate factory.
 *
 * Every existing row is TAKEN, because before this migration a row could not
 * mean anything else.
 */
val Migration6to7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE medications ADD COLUMN alertTier TEXT NOT NULL DEFAULT 'LIGHT'"
        )
        db.execSQL(
            "ALTER TABLE medications ADD COLUMN critical INTEGER NOT NULL DEFAULT 0"
        )

        db.execSQL(
            """
            CREATE TABLE dose_logs_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scheduledDoseId INTEGER NOT NULL,
                forDate INTEGER NOT NULL,
                loggedAt INTEGER NOT NULL,
                outcome TEXT NOT NULL DEFAULT 'TAKEN',
                skipReason TEXT,
                FOREIGN KEY(scheduledDoseId) REFERENCES scheduled_doses(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO dose_logs_new (id, scheduledDoseId, forDate, loggedAt, outcome, skipReason)
            SELECT id, scheduledDoseId, forDate, takenAt, 'TAKEN', NULL FROM dose_logs
            """.trimIndent()
        )
        db.execSQL("DROP TABLE dose_logs")
        db.execSQL("ALTER TABLE dose_logs_new RENAME TO dose_logs")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_dose_logs_scheduledDoseId_forDate " +
                "ON dose_logs (scheduledDoseId, forDate)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS dose_alerts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scheduledDoseId INTEGER NOT NULL,
                forDate INTEGER NOT NULL,
                state TEXT NOT NULL,
                nextFireAt INTEGER NOT NULL,
                dueAt INTEGER NOT NULL,
                snoozeCount INTEGER NOT NULL,
                escalationStep INTEGER NOT NULL,
                firstFiredAt INTEGER,
                FOREIGN KEY(scheduledDoseId) REFERENCES scheduled_doses(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_dose_alerts_scheduledDoseId_forDate " +
                "ON dose_alerts (scheduledDoseId, forDate)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_dose_alerts_nextFireAt ON dose_alerts (nextFireAt)"
        )
    }
}
