package com.hackqing.scale.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.BodyWaterMassRecord
import androidx.health.connect.client.records.BoneMassRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import com.hackqing.scale.model.ScaleMeasurement
import java.time.Instant

/**
 * Health Connect 单项同步。
 *
 * 写入项目：体重（WeightRecord）；若测量含体脂率则一并写入 BodyFatRecord。
 * 每条测量使用**稳定的 clientRecordId**（由测量 id 推导），因此重复同步同一记录
 * 在 Health Connect 中只会更新、不会产生重复条目；删除时也据此精确删除。
 * 目前为手动触发（每条记录点“同步”）。
 */
class HealthConnectManager(private val context: Context) {

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    val isAvailable: Boolean
        get() = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    /** 需要申请的权限集合。 */
    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(LeanBodyMassRecord::class),
        HealthPermission.getWritePermission(BoneMassRecord::class),
        HealthPermission.getWritePermission(BasalMetabolicRateRecord::class),
        HealthPermission.getWritePermission(BodyWaterMassRecord::class),
    )

    suspend fun hasAllPermissions(): Boolean {
        if (!isAvailable) return false
        return try {
            val granted = HealthConnectClient.getOrCreate(context)
                .permissionController.getGrantedPermissions()
            granted.containsAll(permissions)
        } catch (_: Exception) {
            false
        }
    }

    private fun weightClientId(m: ScaleMeasurement) = "hackqing-weight-${m.id}"
    private fun bodyFatClientId(m: ScaleMeasurement) = "hackqing-bodyfat-${m.id}"
    private fun leanClientId(m: ScaleMeasurement) = "hackqing-lean-${m.id}"
    private fun boneClientId(m: ScaleMeasurement) = "hackqing-bone-${m.id}"
    private fun bmrClientId(m: ScaleMeasurement) = "hackqing-bmr-${m.id}"
    private fun waterClientId(m: ScaleMeasurement) = "hackqing-water-${m.id}"

    /** 单项同步：写入（或更新）一条测量，使用稳定 clientRecordId 避免重复。 */
    suspend fun sync(m: ScaleMeasurement) {
        val client = HealthConnectClient.getOrCreate(context)
        val time = Instant.ofEpochMilli(m.timeMillis)
        val offset = java.time.ZoneId.systemDefault().rules.getOffset(time)
        val version = System.currentTimeMillis()
        val records = mutableListOf<Record>()
        records += WeightRecord(
            time = time,
            zoneOffset = offset,
            weight = Mass.kilograms(m.weightKg.toDouble()),
            metadata = Metadata.manualEntry(weightClientId(m), version),
        )
        m.bodyFatPercent?.let {
            records += BodyFatRecord(
                time = time,
                zoneOffset = offset,
                percentage = Percentage(it.toDouble()),
                metadata = Metadata.manualEntry(bodyFatClientId(m), version),
            )
        }
        m.composition?.let { c ->
            records += LeanBodyMassRecord(
                time = time,
                zoneOffset = offset,
                mass = Mass.kilograms(c.leanBodyMassKg.toDouble()),
                metadata = Metadata.manualEntry(leanClientId(m), version),
            )
            records += BoneMassRecord(
                time = time,
                zoneOffset = offset,
                mass = Mass.kilograms(c.boneKg.toDouble()),
                metadata = Metadata.manualEntry(boneClientId(m), version),
            )
            records += BasalMetabolicRateRecord(
                time = time,
                zoneOffset = offset,
                basalMetabolicRate = Power.kilocaloriesPerDay(c.bmr.toDouble()),
                metadata = Metadata.manualEntry(bmrClientId(m), version),
            )
            records += BodyWaterMassRecord(
                time = time,
                zoneOffset = offset,
                mass = Mass.kilograms(m.weightKg * c.waterPercent / 100.0),
                metadata = Metadata.manualEntry(waterClientId(m), version),
            )
        }
        client.insertRecords(records)
    }

    /** 删除该测量在 Health Connect 中对应的记录（按稳定 clientRecordId）。 */
    suspend fun deleteFor(m: ScaleMeasurement) {
        val client = HealthConnectClient.getOrCreate(context)
        client.deleteRecords(WeightRecord::class, emptyList(), listOf(weightClientId(m)))
        if (m.bodyFatPercent != null) {
            client.deleteRecords(BodyFatRecord::class, emptyList(), listOf(bodyFatClientId(m)))
        }
        if (m.composition != null) {
            client.deleteRecords(LeanBodyMassRecord::class, emptyList(), listOf(leanClientId(m)))
            client.deleteRecords(BoneMassRecord::class, emptyList(), listOf(boneClientId(m)))
            client.deleteRecords(BasalMetabolicRateRecord::class, emptyList(), listOf(bmrClientId(m)))
            client.deleteRecords(BodyWaterMassRecord::class, emptyList(), listOf(waterClientId(m)))
        }
    }
}
