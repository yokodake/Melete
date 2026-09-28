package com.yokodake.melete.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yokodake.melete.data.backup.BackupService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * A version-1 backup, frozen when the format became stable, restores into today's database and
 * exports back as the same record. See `BackupFixtureTest` for why the fixture is never edited.
 */
@RunWith(AndroidJUnit4::class)
class BackupCompatibilityTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var database: MeleteDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(instrumentation.targetContext, MeleteDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theVersionOneFixtureRestoresAndExportsBackUnchanged() = runBlocking<Unit> {
        val text = instrumentation.context.assets.open("backup-v1.json").use { it.readBytes().decodeToString() }
        val service = BackupService(database)
        val fixture = service.decode(text)

        service.restore(fixture)
        val again = service.export()

        // When it was written and from which schema are informational; everything else is the record.
        assertEquals(fixture.copy(exportedAt = "", schemaVersion = 0), again.copy(exportedAt = "", schemaVersion = 0))

        // And it reads as a record, not just as rows: the assisted sets come back negative.
        val repository = TrainingRepository(database)
        val band = repository.observeWeek(LocalDate.of(2026, 9, 28)).first().single { it.name == "Band pull-up" }
        val loads = repository.observeOccurrence(band.id).first()!!.sets.map { it.payload.measurement?.value }
        assertEquals(listOf(-15.0, -12.5), loads)
        assertTrue(DiaryRepository(database).observeDays(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 22)).first().isNotEmpty())
    }
}
