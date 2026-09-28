package com.yokodake.melete.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The exported schemas are the history migrations are tested against, so they must never change
 * after the fact. Room re-exports `N.json` whenever the entities change under an unchanged version
 * number, silently replacing the schema installed apps actually have; pinning each exported
 * schema's identity here turns that into a failing test instead.
 */
class SchemaBaselineTest {

    private val schemas = File("schemas/com.yokodake.melete.data.MeleteDatabase")

    /** Every released schema and its Room identity hash. Add a line when a version is added. */
    private val released = mapOf(
        10 to "668aea72f58ec32158d5f564e4c1390c",
    )

    private fun identityOf(version: Int): String? =
        Regex("\"identityHash\"\\s*:\\s*\"([0-9a-f]+)\"")
            .find(File(schemas, "$version.json").readText())?.groupValues?.get(1)

    @Test
    fun `no exported schema has changed since it was released`() {
        released.forEach { (version, hash) ->
            assertEquals(
                "Schema $version changed without a version bump: bump MeleteMigrations.CURRENT and add a migration",
                hash,
                identityOf(version),
            )
        }
    }

    @Test
    fun `every schema from the baseline to the current one is exported and pinned`() {
        val expected = (MeleteMigrations.BASELINE..MeleteMigrations.CURRENT).toSet()
        val exported = schemas.listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.toSet()
        assertTrue("Missing exported schemas: ${expected - exported}", exported.containsAll(expected))
        assertEquals("Pin the identity of every released schema here", expected, released.keys)
    }
}
