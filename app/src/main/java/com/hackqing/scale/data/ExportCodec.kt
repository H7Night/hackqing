package com.hackqing.scale.data

import com.hackqing.scale.model.ScaleMeasurement
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * The single canonical export format for hackqing: an envelope with the app name, export
 * time, count and a `measurements[]` array whose entries inline their `bodyComposition`.
 * (The legacy short-key format read by [LegacyImport] is import-only and never produced.)
 */
object ExportCodec {

    fun toJson(history: List<ScaleMeasurement>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        val arr = JSONArray()
        for (m in history) {
            arr.put(JSONObject().apply {
                put("time", fmt.format(Date(m.timeMillis)))
                put("timestamp", m.timeMillis)
                put("weightKg", m.weightKg.toDouble())
                m.bodyFatPercent?.let { put("bodyFatPercent", it.toDouble()) }
                m.resistance?.let { put("resistance", it) }
                m.userId?.let { put("userId", it) }
                m.heartRate?.let { put("heartRate", it) }
                put("protoVersion", m.protoVersion)
                put("rawHex", m.rawHex)
                m.composition?.let { c ->
                    put("bodyComposition", JSONObject().apply {
                        put("bmi", c.bmi.toDouble())
                        put("fatPercent", c.fatPercent.toDouble())
                        put("musclePercent", c.musclePercent.toDouble())
                        put("waterPercent", c.waterPercent.toDouble())
                        put("proteinPercent", c.proteinPercent.toDouble())
                        put("boneKg", c.boneKg.toDouble())
                        put("visceralFat", c.visceralFat)
                        put("bmr", c.bmr.toDouble())
                        put("bodyAge", c.bodyAge)
                        put("leanBodyMassKg", c.leanBodyMassKg.toDouble())
                        put("fatMassKg", c.fatMassKg.toDouble())
                    })
                }
            })
        }
        return JSONObject().apply {
            put("app", "hackqing")
            put("exportedAt", System.currentTimeMillis())
            put("count", history.size)
            put("measurements", arr)
        }.toString(2)
    }
}
