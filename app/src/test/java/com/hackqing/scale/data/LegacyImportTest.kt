package com.hackqing.scale.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyImportTest {

    @Test
    fun `parses legacy measurements with composition and hcSynced`() {
        val json = """
            [
              {"id":5,"time":1000,"weight":67.4,"resistance":361,"fat":17.3,"userId":118171509,"heartRate":70,"final":true,"raw":"AB","ver":33,"hcSynced":true,
               "comp":{"bmi":23.6,"fat":17.3,"muscle":55.41,"water":60.04,"protein":18.0,"bone":2.8,"visceral":5,"bmr":1574.9,"bodyAge":17,"lean":55.2,"fatMass":12.2}}
            ]
        """.trimIndent()
        val list = LegacyImport.parse(json)
        assertEquals(1, list.size)
        val m = list[0]
        assertEquals(5L, m.id)
        assertEquals(1000L, m.timeMillis)
        assertEquals(67.4f, m.weightKg, 0.001f)
        assertEquals(361, m.resistance)
        assertEquals(17.3f, m.bodyFatPercent!!, 0.001f)
        assertEquals(118171509, m.userId)
        assertEquals(70, m.heartRate)
        assertEquals(33, m.protoVersion)
        assertEquals(true, m.hcSynced)
        assertEquals(23.6f, m.composition!!.bmi, 0.001f)
        assertEquals(55.2f, m.composition!!.leanBodyMassKg, 0.001f)
    }

    @Test
    fun `empty or invalid json yields empty list`() {
        assertEquals(0, LegacyImport.parse("not json").size)
        assertEquals(0, LegacyImport.parse("[]").size)
    }
}
