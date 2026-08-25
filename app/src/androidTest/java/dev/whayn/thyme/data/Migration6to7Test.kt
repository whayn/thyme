package dev.whayn.thyme.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The `dose_logs` rebuild in [Migration6to7] is the one edit in the alert feature
 * with no undo: get it wrong and every historical take is gone. SQLite has no
 * `RENAME COLUMN` before API 30 and minSdk is 26, so `takenAt` becomes `loggedAt`
 * by create/copy/drop/rename - four statements, any of which can quietly lose rows.
 *
 * This stands up a real v6 database, migrates it, and checks the data is still there.
 */
@RunWith(AndroidJUnit4::class)
class Migration6to7Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ThymeDatabase::class.java,
    )

    @Test
    fun migrate6To7_keepsHistoryAndAddsAlertColumns() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                "INSERT INTO medications (id, name, strength, colorIndex, colorIndexRight, form, active) " +
                    "VALUES (1, 'Metformin', '500mg', 3, 3, 0, 1)"
            )
            db.execSQL(
                "INSERT INTO regimens (id, medicationId, startDate, endDate, daysOfWeek, intervalDays, " +
                    "cycleOnDays, cycleOffDays, active) VALUES (1, 1, 20000, NULL, 127, 1, NULL, NULL, 1)"
            )
            db.execSQL(
                "INSERT INTO scheduled_doses (id, regimenId, time, quantity, active) " +
                    "VALUES (1, 1, 480, 1.0, 1)"
            )
            // Two takes, so a rebuild that drops all but one row is caught too.
            db.execSQL(
                "INSERT INTO dose_logs (id, scheduledDoseId, forDate, takenAt) " +
                    "VALUES (1, 1, 20000, $FIRST_TAKEN_AT)"
            )
            db.execSQL(
                "INSERT INTO dose_logs (id, scheduledDoseId, forDate, takenAt) " +
                    "VALUES (2, 1, 20001, $SECOND_TAKEN_AT)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, Migration6to7)

        db.query("SELECT COUNT(*) FROM dose_logs").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("both log rows must survive the table rebuild", 2, c.getInt(0))
        }

        db.query(
            "SELECT id, scheduledDoseId, forDate, loggedAt, outcome, skipReason " +
                "FROM dose_logs ORDER BY id"
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getLong(0))
            assertEquals(1, c.getLong(1))
            assertEquals(20000, c.getLong(2))
            assertEquals("takenAt must land in loggedAt intact", FIRST_TAKEN_AT, c.getLong(3))
            assertEquals("a pre-existing row could only ever mean taken", "TAKEN", c.getString(4))
            assertNull(c.getString(5))

            assertTrue(c.moveToNext())
            assertEquals(SECOND_TAKEN_AT, c.getLong(3))
        }

        db.query("SELECT alertTier, critical FROM medications WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("LIGHT", c.getString(0))
            assertEquals(0, c.getInt(1))
        }

        // The unique index is what makes resolveDose an upsert instead of a
        // duplicate factory, so losing it in the rebuild would be silent.
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' " +
            "AND name = 'index_dose_logs_scheduledDoseId_forDate'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("unique index must be recreated after the rebuild", 1, c.getInt(0))
        }

        db.query("SELECT COUNT(*) FROM dose_alerts").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        // The dose_logs rebuild drops and recreates the table that every log row
        // points through. A botched foreign key survives every other assertion
        // here and only surfaces later as a cascade that deletes nothing, or
        // one that deletes too much.
        db.query("PRAGMA foreign_key_check").use { c ->
            assertEquals("migration left dangling foreign keys", 0, c.count)
        }
    }

    @Test
    fun migrate6To7_uniqueIndexStillRejectsDuplicates() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.execSQL(
                "INSERT INTO medications (id, name, strength, colorIndex, colorIndexRight, form, active) " +
                    "VALUES (1, 'Metformin', NULL, 0, 0, 0, 1)"
            )
            db.execSQL(
                "INSERT INTO regimens (id, medicationId, startDate, endDate, daysOfWeek, intervalDays, " +
                    "cycleOnDays, cycleOffDays, active) VALUES (1, 1, 20000, NULL, 127, 1, NULL, NULL, 1)"
            )
            db.execSQL(
                "INSERT INTO scheduled_doses (id, regimenId, time, quantity, active) " +
                    "VALUES (1, 1, 480, 1.0, 1)"
            )
            db.execSQL(
                "INSERT INTO dose_logs (id, scheduledDoseId, forDate, takenAt) " +
                    "VALUES (1, 1, 20000, $FIRST_TAKEN_AT)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 7, true, Migration6to7)

        val duplicated = runCatching {
            db.execSQL(
                "INSERT INTO dose_logs (scheduledDoseId, forDate, loggedAt, outcome, skipReason) " +
                    "VALUES (1, 20000, $SECOND_TAKEN_AT, 'SKIPPED', 'Out of stock')"
            )
        }.isSuccess
        assertFalse("one resolution per dose per day is the whole invariant", duplicated)
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
        const val FIRST_TAKEN_AT = 1_700_000_000_000L
        const val SECOND_TAKEN_AT = 1_700_086_400_000L
    }
}
