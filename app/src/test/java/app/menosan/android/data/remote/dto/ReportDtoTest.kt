package app.menosan.android.data.remote.dto

import app.menosan.android.core.model.WasteCategory
import app.menosan.android.core.network.MenosanJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The report payload of contract §3 / §5.7 decodes, and survives additive server changes. */
class ReportDtoTest {

    @Test
    fun `contract example report decodes`() {
        val report = MenosanJson.decodeFromString(ReportDto.serializer(), CONTRACT_REPORT)

        assertEquals(LocalDate.of(2026, 9, 27), report.weekStart)
        assertEquals(118, report.stats.analyzedTotals.quantity)
        assertEquals(WasteCategory.RESIDUAL, report.stats.categories.single().category)
        assertEquals(59.3, report.stats.categories.single().sharePct, 0.0)
        assertEquals(2, report.stats.special.quantity)

        val hotspot = report.hotspots.single()
        assertEquals(
            listOf(HotspotCriterion.MOST_FREQUENT, HotspotCriterion.HIGHEST_QUANTITY, HotspotCriterion.AVOIDABLE),
            hotspot.criteria,
        )
        val rec = hotspot.recommendations.single()
        assertEquals(InterventionType.REDUCE, rec.type)
        assertEquals(CostLevel.SAVES_MONEY, rec.costLevel)
        assertEquals(Effort.LOW, rec.effort)
        assertEquals(true, rec.adopted)

        val comparison = report.comparison!!
        assertEquals(LocalDate.of(2026, 9, 20), comparison.previousWeekStart)
        assertEquals(Trend.DECREASED, comparison.total.trend)
        assertEquals(-9.2, comparison.total.deltaPct!!, 0.0)
        assertNull(comparison.subcategories.single().deltaPct)

        assertEquals(Trend.DECREASED, report.impacts.single().result)
    }

    @Test
    fun `null comparison and unknown fields and values are tolerated`() {
        val json = """
            {"weekStart":"2026-09-27","weekEnd":"2026-10-03","revision":2,"isLatest":false,"someNewField":{"x":1},
             "stats":{"analyzedTotals":{"frequency":0,"quantity":0},"categories":[],"subcategories":[],"special":{"frequency":1,"quantity":2}},
             "hotspots":[{"rank":1,"subcategory":"RES_SACHETS","criteria":["MOST_FREQUENT","SOMETHING_NEW"],"frequency":1,"quantity":1,"score":1.0,"recommendations":[]}],
             "comparison":null,"impacts":[]}
        """.trimIndent()

        val report = MenosanJson.decodeFromString(ReportDto.serializer(), json)

        assertNull(report.comparison)
        assertEquals(listOf(HotspotCriterion.MOST_FREQUENT, HotspotCriterion.UNKNOWN), report.hotspots.single().criteria)
    }

    @Test
    fun `report list is a bare array`() {
        val json = """[{"weekStart":"2026-09-20","weekEnd":"2026-09-26","analyzedQuantity":118,"hotspotCount":3,"adoptedCount":1,"isLatest":true}]"""

        val list = MenosanJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(ReportSummaryDto.serializer()),
            json,
        )

        assertEquals(true, list.single().isLatest)
    }

    private companion object {
        val CONTRACT_REPORT = """
            {
              "weekStart": "2026-09-27", "weekEnd": "2026-10-03", "revision": 1, "isLatest": true,
              "stats": {
                "analyzedTotals": {"frequency": 42, "quantity": 118},
                "categories": [{"category": "RESIDUAL", "frequency": 20, "quantity": 70, "sharePct": 59.3}],
                "subcategories": [{"code": "RES_SACHETS", "category": "RESIDUAL", "frequency": 12, "quantity": 40}],
                "special": {"frequency": 1, "quantity": 2}
              },
              "hotspots": [{
                "rank": 1, "subcategory": "RES_SACHETS", "criteria": ["MOST_FREQUENT","HIGHEST_QUANTITY","AVOIDABLE"],
                "frequency": 12, "quantity": 40, "score": 1.0,
                "recommendations": [{
                  "interventionId": "7f1c", "code": "RES_SACHETS_REFILL_STATION", "type": "REDUCE",
                  "title": "Refill", "description": "Bring a bottle.", "howTo": ["Find a station"], "costLevel": "SAVES_MONEY", "effort": "LOW",
                  "note": "You logged 12 sachets.", "continued": false, "adopted": true
                }]
              }],
              "comparison": {
                "previousWeekStart": "2026-09-20",
                "total": {"previous": 130, "current": 118, "delta": -12, "deltaPct": -9.2, "trend": "DECREASED"},
                "categories": [{"category": "RESIDUAL", "previous": 80, "current": 70, "delta": -10, "deltaPct": -12.5, "trend": "DECREASED"}],
                "subcategories": [{"code": "RES_PLASTIC_BAGS", "category": "RESIDUAL", "previous": 0, "current": 9, "delta": 9, "deltaPct": null, "trend": "INCREASED"}]
              },
              "impacts": [{
                "interventionId": "9a2b", "title": "Bring a bayong", "targetSubcategory": "RES_PLASTIC_BAGS",
                "baselineWeekStart": "2026-09-20", "baselineQuantity": 15, "followupQuantity": 9, "result": "DECREASED"
              }]
            }
        """.trimIndent()
    }
}
