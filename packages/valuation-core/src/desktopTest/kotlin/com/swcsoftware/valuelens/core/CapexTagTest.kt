package com.swcsoftware.valuelens.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * ISSUES #71: Eli Lilly and Verizon file capex only under the "Other" tags
 * (`PaymentsToAcquireOtherPropertyPlantAndEquipment`, `PaymentsToAcquireOtherProductiveAssets`), so
 * owner earnings and free cash flow disappeared. The tags sit last in the capex list: they fill a
 * year that has no main capex tag and never override one that does. Same fixture as the Python test.
 */
class CapexTagTest {
    private val fixtures = generateSequence(File(".").absoluteFile) { it.parentFile }.map { File(it, "services/valuation-engine/tests/fixtures") }.first { it.isDirectory }

    @Test fun otherVariantIsLastResortPerPeriod() {
        val fin = Statements.normalize(CompanyFactsParser.parse(File(fixtures, "companyfacts_CAPEX_OTHER.json").readText(), CompanyRefCore("CAPX", 1, "SYNTHETIC CAPEX CO")))
        val capex = fin.annual.associate { it.fiscalYear to assertNotNull(it.values["capex"]) }
        assertEquals(80e6, capex[2024]!!.value)
        assertEquals("PaymentsToAcquirePropertyPlantAndEquipment", capex[2024]!!.tag)
        assertEquals(90e6, capex[2025]!!.value)
        assertEquals("PaymentsToAcquireOtherPropertyPlantAndEquipment", capex[2025]!!.tag)
    }
}
