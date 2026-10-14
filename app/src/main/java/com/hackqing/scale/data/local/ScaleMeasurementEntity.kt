package com.hackqing.scale.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hackqing.scale.body.BodyComposition
import com.hackqing.scale.model.ScaleMeasurement

@Entity(tableName = "measurement")
data class ScaleMeasurementEntity(
    @PrimaryKey val id: Long,
    val timeMillis: Long,
    val weightKg: Float,
    val resistance: Int?,
    val bodyFatPercent: Float?,
    val userId: Int?,
    val heartRate: Int?,
    val isFinal: Boolean,
    val rawHex: String,
    val protoVersion: Int,
    val hcSynced: Boolean,
    val bmi: Float?,
    val fatPercent: Float?,
    val musclePercent: Float?,
    val waterPercent: Float?,
    val proteinPercent: Float?,
    val boneKg: Float?,
    val visceralFat: Int?,
    val bmr: Float?,
    val bodyAge: Int?,
    val leanBodyMassKg: Float?,
    val fatMassKg: Float?,
)

fun ScaleMeasurement.toEntity(): ScaleMeasurementEntity = ScaleMeasurementEntity(
    id = id,
    timeMillis = timeMillis,
    weightKg = weightKg,
    resistance = resistance,
    bodyFatPercent = bodyFatPercent,
    userId = userId,
    heartRate = heartRate,
    isFinal = isFinal,
    rawHex = rawHex,
    protoVersion = protoVersion,
    hcSynced = hcSynced,
    bmi = composition?.bmi,
    fatPercent = composition?.fatPercent,
    musclePercent = composition?.musclePercent,
    waterPercent = composition?.waterPercent,
    proteinPercent = composition?.proteinPercent,
    boneKg = composition?.boneKg,
    visceralFat = composition?.visceralFat,
    bmr = composition?.bmr,
    bodyAge = composition?.bodyAge,
    leanBodyMassKg = composition?.leanBodyMassKg,
    fatMassKg = composition?.fatMassKg,
)

fun ScaleMeasurementEntity.toExternal(): ScaleMeasurement = ScaleMeasurement(
    id = id,
    timeMillis = timeMillis,
    weightKg = weightKg,
    resistance = resistance,
    bodyFatPercent = bodyFatPercent,
    userId = userId,
    heartRate = heartRate,
    isFinal = isFinal,
    rawHex = rawHex,
    protoVersion = protoVersion,
    hcSynced = hcSynced,
    composition = bmi?.let {
        BodyComposition(
            bmi = it,
            fatPercent = fatPercent ?: 0f,
            musclePercent = musclePercent ?: 0f,
            waterPercent = waterPercent ?: 0f,
            proteinPercent = proteinPercent ?: 0f,
            boneKg = boneKg ?: 0f,
            visceralFat = visceralFat ?: 0,
            bmr = bmr ?: 0f,
            bodyAge = bodyAge ?: 0,
            leanBodyMassKg = leanBodyMassKg ?: 0f,
            fatMassKg = fatMassKg ?: 0f,
        )
    },
)
