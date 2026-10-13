package com.hackqing.scale.model

/** 一条测量结果（来自体脂秤的最终体重帧或实时帧）。 */
data class ScaleMeasurement(
    val id: Long = System.currentTimeMillis(),
    val timeMillis: Long,
    /** 体重，单位 kg */
    val weightKg: Float,
    /** 原始阻抗/电阻值（体脂计算用），若无可为空 */
    val resistance: Int? = null,
    /** 体脂率 %（秤端上报，版本>=30），若无可为空 */
    val bodyFatPercent: Float? = null,
    /** 秤端用户编号 */
    val userId: Int? = null,
    /** 心率 */
    val heartRate: Int? = null,
    /** 是否最终结果（false 表示实时跳动值） */
    val isFinal: Boolean = true,
    /** 原始帧 hex，便于排查 */
    val rawHex: String = "",
    /** 称重协议版本号（帧第 2 字节） */
    val protoVersion: Int = 0,
    /** 体成分（由 hackqing 本地算法计算，等价好轻） */
    val composition: com.hackqing.scale.body.BodyComposition? = null,
    /** 是否已同步到 Health Connect（用于避免重复同步 & 删除时联动） */
    val hcSynced: Boolean = false,
)

/** 供 hackqing 绑定 / 写入秤的用户档案。秤用这些信息计算体脂并归属测量结果。 */
data class UserProfile(
    val userId: Int = 1,
    val heightCm: Int = 170,
    /** 1=男 2=女 */
    val sex: Int = 1,
    val age: Int = 30,
    val waistCm: Int = 85,
    val bustCm: Int = 90,
    val basisWeightKg: Float = 60f,
    /** 1=KG 2=LB 3=斤 */
    val unit: Int = 1,
    /** 体型，默认 -1 */
    val bodyType: Int = -1,
            /** 心率测量开关 */
    val heartRateSwitch: Int = 0,
)
