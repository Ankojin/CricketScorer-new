package `in`.nrkmart.cricscore

import `in`.nrkmart.cricscore.db.CricketDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalProfileIsolationTest {
    @Test
    fun guestProfileKeepsLegacyDatabaseName() {
        assertEquals("cricket_database", CricketDatabase.databaseNameForProfile(null))
        assertEquals("cricket_database", CricketDatabase.databaseNameForProfile(""))
    }

    @Test
    fun cloudAccountsUseStableDistinctOpaqueDatabaseNames() {
        val accountA = CricketDatabase.databaseNameForProfile("user_alpha")
        val accountB = CricketDatabase.databaseNameForProfile("user_beta")

        assertEquals(accountA, CricketDatabase.databaseNameForProfile("user_alpha"))
        assertNotEquals(accountA, accountB)
        assertFalse(accountA.contains("user_alpha"))
        assertFalse(accountB.contains("user_beta"))
    }
}