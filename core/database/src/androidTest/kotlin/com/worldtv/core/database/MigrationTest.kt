package com.worldtv.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the database schema.
 *
 * Room exports each version's schema to `schemas/`, and this opens the current one to
 * prove the exported JSON matches the entities. The catalog is re-downloadable, but
 * favourites, recents and every stream's accumulated health history are not — those
 * are the reason this app is worth using twice, and a botched migration destroys them.
 *
 * When the schema changes: bump `WorldTvDatabase.version`, commit the new schema JSON,
 * and add a `helper.runMigrationsAndValidate(NAME, newVersion, true, Migration_N_M)`
 * case below.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorldTvDatabase::class.java,
    )

    @Test
    fun currentSchemaOpensCleanly() {
        helper.createDatabase(TEST_DB, CURRENT_VERSION).close()
        // Reopening through Room validates the entities against the exported schema;
        // a mismatch throws here instead of at runtime on a device.
        val database = helper.runMigrationsAndValidate(TEST_DB, CURRENT_VERSION, true)
        assertTrue(database.isOpen)
        database.close()
    }

    private companion object {
        const val TEST_DB = "migration-test.db"

        /** Must track [WorldTvDatabase]'s `version`. */
        const val CURRENT_VERSION = 1
    }
}
