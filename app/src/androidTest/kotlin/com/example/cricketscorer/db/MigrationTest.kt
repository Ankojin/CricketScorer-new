package com.example.cricketscorer.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val TEST_DB = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CricketDatabase::class.java
    )

    @Test
    fun migrate1To3() {
        // Create v1 DB
        var db = helper.createDatabase(TEST_DB, 1)

        // Insert minimal valid row into tournaments and teams
        db.execSQL(
            "INSERT INTO tournaments (id, name, overs, ballType, powerplayOvers) VALUES ('t1', 'Tournament 1', 20, 'LEATHER', 6)"
        )
        db.execSQL(
            "INSERT INTO teams (id, tournamentId, name, matchesPlayed, wins, losses, points, nrr) VALUES ('team1', 't1', 'Team 1', 2, 1, 1, 2, 0.5)"
        )
        db.close()

        // Run migrations 1->2 and 2->3 and validate
        db = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3
        )

        // Verify that the migrated DB contains the expected data and columns
        val cursor = db.query("SELECT id, tournamentId, name, matchesPlayed, wins, losses, points, nrr, colorHex FROM teams WHERE id = 'team1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("team1", cursor.getString(cursor.getColumnIndexOrThrow("id")))
        assertEquals("t1", cursor.getString(cursor.getColumnIndexOrThrow("tournamentId")))
        assertEquals("Team 1", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("matchesPlayed")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("wins")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("losses")))
        assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow("points")))
        assertEquals(0.5, cursor.getDouble(cursor.getColumnIndexOrThrow("nrr")), 0.001)

        val colorHexIndex = cursor.getColumnIndexOrThrow("colorHex")
        assertTrue(cursor.isNull(colorHexIndex))
        cursor.close()

        val tCursor = db.query("SELECT id, name FROM tournaments WHERE id = 't1'")
        assertTrue(tCursor.moveToFirst())
        assertEquals("t1", tCursor.getString(tCursor.getColumnIndexOrThrow("id")))
        assertEquals("Tournament 1", tCursor.getString(tCursor.getColumnIndexOrThrow("name")))
        tCursor.close()
    }
}
