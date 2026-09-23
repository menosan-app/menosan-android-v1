package app.menosan.android.core.analytics

import app.menosan.android.data.repo.TaxonomyRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Runs `aggregate`, `findHotspots`, `compare`, and `measureImpact` against the shared test vectors
 * (`src/test/resources/analytics-test-vectors.json`, a copy of `menosan-api/docs/analytics-test-vectors.json`),
 * exactly like the backend's `AnalyticsVectorsTest` (plan §5.7 parity). Never edit the vectors here: regenerate them
 * in menosan-api and copy the file over.
 */
class AnalyticsVectorsTest {
    private val taxonomy = TaxonomyRepository.parseTaxonomy(File("src/main/assets/taxonomy.json").readText())

    private val vectors: VectorFile = run {
        val text = requireNotNull(javaClass.classLoader?.getResource(VECTORS)) { "Missing test resource $VECTORS" }.readText()
        VectorJson.decodeFromString(VectorFile.serializer(), text)
    }

    @Test
    fun `vectors match this app's algorithm and taxonomy versions`() {
        assertEquals("Bump the vectors together with ALGORITHM_VERSION", ALGORITHM_VERSION, vectors.algorithmVersion)
        assertEquals(taxonomy.version, vectors.taxonomyVersion)
        assertTrue("Plan §9 BE-3 asks for at least 14 cases", vectors.cases.size >= 14)
        assertEquals("Case names must be unique", vectors.cases.size, vectors.cases.map { it.name }.toSet().size)
    }

    @Test
    fun `all four functions match every shared test vector`() {
        for (case in vectors.cases) {
            assertNotNull("${case.name}: expected is missing", case.expected)
            val expected = case.expected!!
            val actual = run(case)
            assertEquals("${case.name}: stats", expected.stats, actual.stats)
            assertEquals("${case.name}: hotspots", expected.hotspots, actual.hotspots)
            assertEquals("${case.name}: comparison", expected.comparison, actual.comparison)
            assertEquals("${case.name}: impacts", expected.impacts, actual.impacts)
        }
    }

    @Test
    fun `results serialize to the same JSON as the vectors`() {
        for (case in vectors.cases) {
            val expectedJson = VectorJson.encodeToString(Expected.serializer(), case.expected!!)
            assertEquals(case.name, expectedJson, VectorJson.encodeToString(Expected.serializer(), run(case)))
        }
    }

    private fun run(case: VectorCase): Expected {
        val tax = taxonomy.analyticsTaxonomy()
        val stats = aggregate(case.entries.map { EntryInput(it.subcategory, it.quantity) }, tax)
        val previous = aggregate(case.previousEntries.map { EntryInput(it.subcategory, it.quantity) }, tax)
        return Expected(
            stats = stats,
            hotspots = findHotspots(stats, tax),
            comparison = compare(stats, previous, case.previousWeekStart),
            impacts = measureImpact(case.adoptions, stats),
        )
    }

    @Serializable
    data class VectorFile(val algorithmVersion: Int, val taxonomyVersion: Int, val about: String, val cases: List<VectorCase>)

    @Serializable
    data class VectorCase(
        val name: String,
        val description: String,
        val previousWeekStart: String = "2026-09-20",
        val previousEntries: List<VectorEntry> = emptyList(),
        val adoptions: List<AdoptionInput> = emptyList(),
        val entries: List<VectorEntry>,
        val expected: Expected? = null,
    )

    @Serializable
    data class VectorEntry(val subcategory: String, val quantity: Int)

    @Serializable
    data class Expected(val stats: WeeklyStats, val hotspots: List<Hotspot>, val comparison: Comparison?, val impacts: List<Impact>)

    private companion object {
        const val VECTORS = "analytics-test-vectors.json"

        val VectorJson = Json {
            encodeDefaults = true
            explicitNulls = true
        }
    }
}
