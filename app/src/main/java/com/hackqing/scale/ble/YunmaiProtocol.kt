package com.hackqing.scale.ble

import com.hackqing.scale.model.ScaleMeasurement
import com.hackqing.scale.model.UserProfile
import java.nio.ByteBuffer
import java.util.Calendar

/**
 * 好轻 / 云麦 (Yunmai) 体脂秤 BLE 协议实现。
 *
 * 数据来源：反编译 com.yunmai.scale v5.6.4（bz.a/bz.b 帧构造、zf.o1/cz.g 帧解析、
 * zy.a0 连接握手），并用真机日志（ScaleCheck.log 的 "Sender send ok!!hex:"）逐字节核对。
 *
 * 帧结构（手机 -> 秤，以及秤 -> 手机的通知）：**同一套**
 *   [0] 0x0D      固定头
 *   [1] 长度      整帧字节数（含本字节与校验字节）
 *   [2] 命令
 *   [3] 版本/类型
 *   [4..] 载荷
 *   [last] 校验 = 第 1..(n-2) 字节逐字节异或
 *
 * 说明：同一型号（YUNMAI-Scale-3）上报的最终结果帧版本号为 0x21(33)。
 */
object YunmaiProtocol {

    // 特征值 UUID 关键字（好轻按子串匹配，见 zy.a0）
    const val NOTIFY_CHAR_HINT = "0000ffe4"   // 秤 -> 手机
    const val WRITE_CHAR_HINT = "0000ffe9"    // 手机 -> 秤
    const val WIFI_CHAR_HINT = "0000ffe6"

    // 接收命令（秤 -> 手机）
    const val RX_LIVE_WEIGHT = 0x01
    const val RX_FINAL_WEIGHT = 0x02
    const val RX_USER_LIST = 0x03
    const val RX_USER_FULL = 0x04
    const val RX_AUTH = 0x05
    const val RX_DEVICE_INFO = 0x06   // 收到用户信息
    const val RX_DEVICE_TIME = 0x07   // 返回设备时间
    const val RX_BATTERY = 0x08       // 返回设备电量
    const val RX_BABY = 0x40
    const val RX_SMALL_ITEM = 0x41
    const val RX_SERIAL = 0x42        // 返回设备序列号
    const val RX_FIRMWARE = 0xF0      // 返回固件信息
    const val RX_GLOBAL_CONFIG = 0xA1

    // 发送命令（手机 -> 秤）
    const val TX_USER_INFO = 0x10
    const val TX_TIME_SYNC = 0x11
    const val TX_CMD_12 = 0x12
    const val TX_CMD_13 = 0x13
    const val TX_ACK_FINISH = 0x14    // 收到最终体重后的回执 (zf.y.d())
    const val TX_CMD_15 = 0x15
    const val TX_QUERY_TIME = 0x17
    const val TX_CMD_18 = 0x18
    const val TX_BABY = 0x40
    const val TX_SMALL_ITEM = 0x41
    const val TX_SERIAL = 0x42
    const val TX_FIRMWARE = 0xF0

    const val USER_ADD_OR_UPDATE: Byte = 1
    const val USER_DELETE: Byte = 2
    const val USER_ADD_OR_QUERY: Byte = 3

    // ---------- 工具 ----------

    fun bytesToHex(b: ByteArray?): String {
        if (b == null) return ""
        val sb = StringBuilder(b.size * 2)
        for (x in b) sb.append(String.format("%02X", x))
        return sb.toString()
    }

    fun hexToBytes(s: String): ByteArray {
        val clean = s.replace(" ", "").replace(":", "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            out[i] = ((Character.digit(clean[i * 2], 16) shl 4) or
                    Character.digit(clean[i * 2 + 1], 16)).toByte()
        }
        return out
    }

    private fun b(v: Int): Byte = v.toByte()

    private fun int4(v: Int): ByteArray =
        ByteBuffer.allocate(4).putInt(v).array()   // 大端

    private fun xor(arr: ByteArray, from: Int, toExclusive: Int): Byte {
        var x = arr[from]
        for (i in from + 1 until toExclusive) x = (x.toInt() xor arr[i].toInt()).toByte()
        return x
    }

    /** 组装整帧：[0x0D, 总长, cmd, payload..., 校验] */
    fun buildFrame(cmd: Int, payload: ByteArray = ByteArray(0)): ByteArray {
        val f = ByteArray(payload.size + 4)
        f[0] = 0x0D
        f[1] = b(f.size)
        f[2] = b(cmd)
        payload.copyInto(f, 3)
        f[f.size - 1] = xor(f, 1, f.size - 1)
        return f
    }

    // ---------- 具体命令帧 ----------

    fun cmd13(): ByteArray = buildFrame(TX_CMD_13, byteArrayOf(0))
    fun queryTime(): ByteArray = buildFrame(TX_QUERY_TIME)
    fun ackFinish(): ByteArray = buildFrame(TX_ACK_FINISH)   // 0D041410
    fun cmd12(): ByteArray = buildFrame(TX_CMD_12)
    fun cmd15(): ByteArray = buildFrame(TX_CMD_15)
    fun cmd18(): ByteArray = buildFrame(TX_CMD_18)
    fun firmwareQuery(): ByteArray = buildFrame(TX_FIRMWARE, byteArrayOf(0))  // 0D05F000F5
    fun serialQuery(): ByteArray = buildFrame(TX_SERIAL)                      // 0D044246
    fun smallItemMode(i: Int): ByteArray = buildFrame(TX_SMALL_ITEM, byteArrayOf(b(i)))
    fun babyMode(i: Int): ByteArray = buildFrame(TX_BABY, byteArrayOf(b(i)))

    /**
     * 时间同步帧 (bz.a.j)。真机样例：
     *   0D 0D 11 6ACCE5C4 14 00 007080 7F
     */
    fun timeSync(
        epochSec: Int = (System.currentTimeMillis() / 1000).toInt(),
        withHeartRate: Boolean = false,
        heartRateSwitch: Int = 0,
    ): ByteArray {
        val cal = Calendar.getInstance()
        val tzSec = (cal.get(Calendar.ZONE_OFFSET) + cal.get(Calendar.DST_OFFSET)) / 1000
        val total = if (withHeartRate) 14 else 13
        val f = ByteArray(total)
        val time = int4(epochSec)
        val tz = int4(Math.abs(tzSec))
        f[0] = 0x0D
        f[1] = b(total)
        f[2] = b(TX_TIME_SYNC)
        f[3] = time[0]; f[4] = time[1]; f[5] = time[2]; f[6] = time[3]
        f[7] = 20
        f[8] = if (tzSec < 0) (-128).toByte() else 0
        f[9] = tz[1]; f[10] = tz[2]; f[11] = tz[3]
        if (withHeartRate) f[12] = b(heartRateSwitch)
        f[total - 1] = xor(f, 1, total - 1)
        return f
    }

    /**
     * 用户信息帧 (bz.a.i)，18 字节。真机样例：
     *   0D 12 10 03 070B2775 A9 01 12 55 5A 1A58 03 03 A8
     */
    fun userInfo(type: Byte, p: UserProfile): ByteArray {
        val weight4 = int4((p.basisWeightKg * 100).toInt())
        val userId4 = int4(p.userId)
        val f = ByteArray(18)
        f[0] = 0x0D
        f[1] = b(18)
        f[2] = b(TX_USER_INFO)
        f[3] = type
        f[4] = userId4[0]; f[5] = userId4[1]; f[6] = userId4[2]; f[7] = userId4[3]
        f[8] = b(p.heightCm)
        f[9] = b(p.sex)
        f[10] = b(if (p.age < 18) 0 else p.age)
        f[11] = b(p.waistCm)
        f[12] = b(p.bustCm)
        f[13] = weight4[2]; f[14] = weight4[3]
        f[15] = unitCode(p.unit)
        f[16] = unitCode(p.bodyType)
        f[17] = xor(f, 1, 17)
        return f
    }

    private fun unitCode(v: Int): Byte = when (v) {
        1 -> 1
        2 -> 2
        else -> 3
    }

    // ---------- 接收解析 ----------

    sealed interface Event {
        data class LiveWeight(val weightKg: Float, val measuringHeartRate: Boolean, val raw: String) : Event
        data class Final(val measurement: ScaleMeasurement, val raw: String) : Event
        data class UserList(val userIds: List<Int>, val total: Int, val raw: String) : Event
        data class Auth(val raw: String) : Event
        data class DeviceTime(val deviceEpochSec: Long, val deltaSec: Int, val raw: String) : Event
        data class Battery(val percent: Int, val raw: String) : Event
        data class Serial(val sn: String, val raw: String) : Event
        data class Firmware(val versionName: String, val raw: String) : Event
        data class Other(val cmd: Int, val raw: String) : Event
        data object Invalid : Event
    }

    private fun hexByteAt(hex: String, byteIndex: Int): Int =
        Integer.parseInt(hex.substring(byteIndex * 2, byteIndex * 2 + 2), 16)

    private fun hexIntBE(hex: String, fromByte: Int, toByteExclusive: Int): Long =
        java.lang.Long.parseLong(hex.substring(fromByte * 2, toByteExclusive * 2), 16)

    fun parse(value: ByteArray): Event {
        if (value.size < 4) return Event.Invalid
        val hex = bytesToHex(value)
        return try {
            parseHex(hex)
        } catch (t: Throwable) {
            Event.Other(-1, "$hex  (解析异常: ${t.message})")
        }
    }

    fun parseHex(hex: String): Event {
        if (hex.length < 8) return Event.Invalid
        val version = hexByteAt(hex, 1)
        val cmd = hexByteAt(hex, 3)
        return when (cmd) {
            RX_LIVE_WEIGHT -> {
                // 实时体重：体重位于字节 8..9 (×0.01)
                val weight = if (hex.length >= 20) hexIntBE(hex, 8, 10) * 0.01f else 0f
                val heart = version == 35 && hex.length >= 24 && hexByteAt(hex, 10) == 1
                Event.LiveWeight(weight, heart, hex)
            }
            RX_FINAL_WEIGHT -> {
                // 真机样例 0D211402006ACCE526070B27751A59015706C2DD:
                //  [1]=版本  [4]=标志  [5..8]=时间戳  [9..12]=userId
                //  [13..14]=体重×100  [15..16]=阻抗  [17..18]=体脂%×100 (版本>=30)
                val timeSec = if (hex.length >= 18) hexIntBE(hex, 5, 9) else (System.currentTimeMillis() / 1000)
                val userId = if (hex.length >= 26) hexIntBE(hex, 9, 13).toInt() else null
                val weight = if (hex.length >= 30) hexIntBE(hex, 13, 15) * 0.01f else 0f
                val resistance = if (hex.length >= 34) hexIntBE(hex, 15, 17).toInt() else null
                val fat = if (version >= 30 && hex.length >= 38) hexIntBE(hex, 17, 19) * 0.01f else null
                var heart: Int? = null
                if (version == 35 && hex.length >= 50) heart = hexByteAt(hex, 19)
                val m = ScaleMeasurement(
                    timeMillis = timeSec * 1000,
                    weightKg = weight,
                    resistance = resistance,
                    bodyFatPercent = fat,
                    userId = userId,
                    heartRate = heart,
                    isFinal = true,
                    rawHex = hex,
                    protoVersion = version,
                )
                Event.Final(m, hex)
            }
            RX_USER_LIST -> {
                val total = if (hex.length >= 12) hexByteAt(hex, 5) else 0
                val uid = if (hex.length >= 20) hexIntBE(hex, 6, 10).toInt() else 0
                Event.UserList(listOf(uid), total, hex)
            }
            RX_AUTH -> Event.Auth(hex)
            RX_DEVICE_TIME -> {
                val devSec = if (hex.length >= 16) hexIntBE(hex, 4, 8) else 0L
                val now = System.currentTimeMillis() / 1000
                Event.DeviceTime(devSec, (now - devSec).toInt(), hex)
            }
            RX_BATTERY -> {
                val pct = if (hex.length >= 10) hexByteAt(hex, 4) else -1
                Event.Battery(pct, hex)
            }
            RX_SERIAL -> {
                val sn = if (hex.length >= 38) {
                    val raw = hexToBytes(hex.substring(6, 38))
                    String(raw, Charsets.US_ASCII).trim { it <= ' ' || it.code == 0 }
                } else ""
                Event.Serial(sn, hex)
            }
            RX_FIRMWARE -> {
                val major = if (hex.length >= 10) hexByteAt(hex, 4) else 0
                val minor = if (hex.length >= 12) hexByteAt(hex, 5) else 0
                Event.Firmware("$major.$minor", hex)
            }
            else -> Event.Other(cmd, hex)
        }
    }

    fun cmdName(cmd: Int): String = when (cmd) {
        RX_LIVE_WEIGHT -> "实时体重"
        RX_FINAL_WEIGHT -> "最终结果"
        RX_USER_LIST -> "秤用户列表"
        RX_USER_FULL -> "用户数已满"
        RX_AUTH -> "鉴权请求"
        RX_DEVICE_INFO -> "收到用户信息"
        RX_DEVICE_TIME -> "返回设备时间"
        RX_BATTERY -> "返回设备电量"
        RX_BABY -> "抱婴模式"
        RX_SMALL_ITEM -> "小物模式"
        RX_SERIAL -> "返回设备序列号"
        RX_FIRMWARE -> "返回固件信息"
        RX_GLOBAL_CONFIG -> "全局配置"
        else -> "未知(0x%02X)".format(cmd)
    }
}
