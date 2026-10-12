package com.hackqing.scale.body

import com.hackqing.scale.model.UserProfile
import com.yunmai.haoqing.logic.config.FormulaJNI
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 体成分计算，等价于好轻的 `zf.i0.i(...)`：
 *  - BMI / 肌肉率 / 骨量 / 水分率 / 基础代谢 / 体年龄 来自 libconfig.so 的 FormulaJNI.formValue
 *  - 体脂率：优先取秤上报值（5~75），否则用 FormulaJNI.formFat 反算
 *  - 蛋白质率：由 BMI 推导（好轻 zf.i0.a）
 *  - 内脏脂肪等级：由体脂率与标准库阈值推导（好轻 zf.i0.o + xmstandarddata.db）
 *  - 去脂体重 / 脂肪重量：由体重与体脂率推导
 */
data class BodyComposition(
    val bmi: Float,
    val fatPercent: Float,
    val musclePercent: Float,
    val waterPercent: Float,
    val proteinPercent: Float,
    val boneKg: Float,
    val visceralFat: Int,
    val bmr: Float,
    val bodyAge: Int,
    val leanBodyMassKg: Float,
    val fatMassKg: Float,
)

object BodyCompositionCalculator {

    private val nativeAvailable: Boolean by lazy {
        try {
            // 触发 libconfig.so 加载（参数：身高,年龄,性别,体重,阻抗,体脂,版本,类型）
            FormulaJNI.formValue(170, 30, 1, 60f, 500f, 20f, 33, "devices")
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun isAvailable(): Boolean = nativeAvailable

    private fun round2(v: Float): Float = Math.round(v * 100f) / 100f

    /** 好轻 zf.i0.a：蛋白质率 ≈ 18 + f(BMI) */
    private fun proteinFormula(bmi: Float): Float {
        var d = if (bmi > 22f) -Math.pow((bmi - 22f).toDouble(), 0.25)
        else Math.pow((22f - bmi).toDouble(), 1.0 / 3.0)
        if (d <= -2.5) d = -2.5
        if (d >= 2.5) d = 2.5
        return round2((d + 18.0).toFloat())
    }

    /** 好轻 zf.i0.o：内脏脂肪等级 */
    private fun visceralIndex(fat: Float, maxVisceral: Float, sex: Int): Int {
        val diff = fat - maxVisceral
        val coef = when {
            diff > 0f -> 1.1f
            sex == 2 -> 1.8f
            else -> 1.4f
        }
        var idx = (9.0 + diff / coef + 0.5).toInt()
        if (idx < 1) idx = 1
        if (idx > 30) idx = 30
        return idx
    }

    /** 标准库 xmstandarddata.db：Type=2(体脂) Index=2 行的 EndData（即内脏脂肪上限阈值） */
    private fun maxVisceral(sex: Int, age: Int): Float {
        val a = if (age < 18) 18 else age
        return if (sex == 2) {
            when {
                a < 40 -> 34f
                a < 60 -> 35f
                else -> 36f
            }
        } else {
            when {
                a < 40 -> 21f
                a < 60 -> 22f
                else -> 24f
            }
        }
    }

    /**
     * @param fatFromFrame 秤上报的体脂率（版本>=30 时有效），可为空
     * @param resistance   阻抗
     * @param version      协议版本（好轻传 fVar.j()）
     */
    fun compute(
        profile: UserProfile,
        weightKg: Float,
        resistance: Int,
        fatFromFrame: Float?,
        version: Int,
    ): BodyComposition? {
        if (profile.heightCm <= 0 || weightKg <= 0f) return null
        if (!nativeAvailable) return null

        val height = profile.heightCm
        val age = if (profile.age < 18) 18 else profile.age
        val sex = profile.sex
        val type = "devices"

        var fat = fatFromFrame ?: 0f
        if (fat < 5f || fat > 75f) {
            fat = try {
                FormulaJNI.formFat(height, age, sex, weightKg, resistance.toFloat(), version, 0, "zh", type)
            } catch (t: Throwable) {
                0f
            }
        }
        if (fat < 5f || fat > 75f) return null

        val arr = try {
            FormulaJNI.formValue(height, age, sex, weightKg, resistance.toFloat(), fat, version, type)
        } catch (t: Throwable) {
            null
        } ?: return null
        if (arr.size < 7) return null

        val bmi = arr[1]
        val muscle = arr[2]
        val bone = arr[3]
        val water = arr[4]
        val bmr = arr[5]
        val bodyAge = arr[6].roundToInt()

        val fatMass = round2(weightKg * fat / 100f)
        val leanMass = round2(weightKg - fatMass)

        return BodyComposition(
            bmi = round2(bmi),
            fatPercent = round2(fat),
            musclePercent = round2(muscle),
            waterPercent = round2(water),
            proteinPercent = proteinFormula(bmi),
            boneKg = round2(bone),
            visceralFat = visceralIndex(fat, maxVisceral(sex, age), sex),
            bmr = round2(bmr),
            bodyAge = bodyAge,
            leanBodyMassKg = leanMass,
            fatMassKg = fatMass,
        )
    }
}
