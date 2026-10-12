# hackqing

接管 **好轻 / 云麦（`com.yunmai.scale`）体脂秤** 蓝牙数据的独立 Android App。

用 Kotlin + Jetpack Compose 编写，直接以 BLE GATT 方式连接体脂秤、复刻好轻的握手协议，
接收并解析秤上报的全部测量数据（体重 / 体脂率 / 阻抗 / 用户 ID / 心率 / 电量 / 序列号 / 固件等），
无需好轻 App、无需账号、无需联网。

> 本文档同时记录**逆向过程**与**可行性结论**。

---

## 一、可行性结论

**可行，且已完成验证。** 体脂秤是标准 BLE GATT 外设，数据在本地通过 GATT 通知（notify）上报，
与好轻账号/云端无关。只要按同一套 UUID 与帧协议连接，即可接管。

实测结果（hackqing 独立接收）：

| 项目 | 好轻 | hackqing |
|---|---|---|
| 体重 | 67.45 kg | 67.40 kg |
| 体脂率 | 17.3 % | 17.3 % |
| 阻抗 | 343 | 361 |
| 用户 ID | 118171509 | 118171509 |

（两次为不同时间点的测量，体重/阻抗存在正常波动；体脂率、用户 ID 完全一致。）

## 二、目标信息

| 项 | 值 |
|---|---|
| 目标 App | `com.yunmai.scale`（好轻）v5.6.4，targetSdk 34 |
| 体脂秤 | 设备名 `YUNMAI-Scale-3`，MAC `80:EA:CA:00:70:DF`，厂商 ID 210 |
| 通信方式 | BLE GATT（连接式），非经典蓝牙 |
| 通知/读特征 | UUID 含 `0000ffe4`（秤 → 手机） |
| 写特征 | UUID 含 `0000ffe9`（手机 → 秤） |
| 辅助特征 | `0000ffe6`（WiFi 秤） |
| MTU | 75 |

## 三、逆向方法

1. `adb` + root 拉取 `base.apk`，`jadx` 全量反编译（9 个 dex）。
2. 在混淆代码中定位协议实现：`bz.a`/`bz.b`（帧构造）、`zf.o1`/`zf.y`/`cz.g`（帧解析）、
   `zy.a0`/`zy.f`/`dz.l`（连接与握手）、`ok.g`（机型识别）。
3. 动态验证：好轻自身会把原始帧写进日志文件
   `/sdcard/Android/data/com.yunmai.scale/files/yunmai/devlog/ScaleCheck-0.log`
   （含 `Sender send ok!!hex:` 与 `最终体重 hexStr ：`），用 root 读取即可拿到**逐字节权威数据**，
   与静态分析互相印证。
4. 用抓包工具链（`reverse/tools/`：OnePlus `.cfa` → 标准 btsnoop → ATT 解析）辅助定位连接句柄。

## 四、协议摘要

帧结构（收发同一套）：

```
[0]    0x0D           固定头
[1]    长度           整帧字节数（含本字节与校验字节）
[2]    命令
[3]    版本/类型
[4..]  载荷
[last] 校验 = 第 1..(n-2) 字节逐字节异或
```

**连接后握手（复刻好轻实测）**：

```
0D05130016                                      0x13
0D1210 03 <uid4> <身高> <性别> <年龄> <腰围> <胸围> <体重×100低2字节> <单位> <体型> <ck>   0x10 用户信息
0D041713                                        0x17 查询时间
0D0D11 <时间戳4> 14 <时区符号> <时区3> <ck>      0x11 时间同步 (UTC+8 = 0x00007080)
0D044246                                        0x42 序列号查询
0D05F000F5                                      0xF0 固件查询
0D04181C                                        0x18
```

收到最终结果后回执：`0D041410`（0x14）。

**最终结果帧**（命令 `0x02`，实测样例 `0D211402006ACCE526070B27751A59015706C2DD`）：

| 字节 | 含义 | 样例 |
|---|---|---|
| [1] | 协议版本 | `0x21` = 33 |
| [2] | 长度 | `0x14` = 20 |
| [3] | 命令 | `0x02` |
| [5..8] | Unix 时间戳（大端） | `6ACCE526` |
| [9..12] | 用户 ID | `070B2775` = 118171509 |
| [13..14] | 体重 ×100（大端） | `1A59` = 6745 → 67.45 kg |
| [15..16] | 阻抗 | `0157` = 343 |
| [17..18] | 体脂率 ×100（版本 ≥30） | `06C2` = 1730 → 17.30 % |

**实时体重帧**（命令 `0x01`）：体重位于字节 [8..9]（×0.01）。

其余命令：`0x03` 用户列表、`0x04` 用户已满、`0x05` 鉴权、`0x06` 用户信息、`0x07` 设备时间、
`0x08` 电量、`0x40` 抱婴、`0x41` 小物、`0x42` 序列号、`0xF0` 固件、`0xA1` 全局配置。

详见 [`reverse/PROTOCOL.md`](reverse/PROTOCOL.md)。

## 五、使用

1. 用 Android Studio 打开本工程，或命令行 `./gradlew :app:assembleDebug` 构建。
2. 安装 `app/build/outputs/apk/debug/app-debug.apk`（需 Android 8.0+）。
3. 手机开启蓝牙；**先把好轻 App 强行停止**（避免它抢占秤的唯一连接）。
4. 打开 hackqing → 点「扫描设备」→ 发现 `YUNMAI-Scale-3` 会自动连接。
5. 光脚上秤，hackqing 会实时显示体重并保存最终结果（体重 / 体脂 / 阻抗 / 用户）。
   - 「历史」查看记录；「调试」查看原始帧、手动发帧、填写用户档案。
6. 首次使用时在「用户档案」填好身高/性别/年龄/腰围/胸围/单位，会随握手写入秤，
   秤据此计算并归属体脂数据。

## 六、体成分算法（全部指标）

秤只上报 **体重 + 阻抗 + 体脂率**；其余指标（肌肉率/水分/蛋白质/骨量/内脏脂肪/BMR/体年龄等）
由好轻本地算法计算。hackqing 已完整复刻：

- **核心指标**：复用云麦自带的原生算法库 `libconfig.so` 的 `FormulaJNI.formValue(...)`
  → 返回 `[?, BMI, 肌肉率, 骨量, 水分率, 基础代谢, 体年龄]`。
  实测样本（169cm/18岁/男/67.45kg/阻抗343/版本33）：
  `BMI 23.6 · 肌肉 55.41% · 骨量 2.8kg · 水分 60.04% · BMR 1574.9 · 体年龄 17`，
  与好轻显示的 `BMI 23.6 · 肌肉率 55.4%` **完全一致**。
- **蛋白质率**：`18 + f(BMI)`（好轻 `zf.i0.a`）。
- **内脏脂肪等级**：由体脂率与标准库阈值推导（好轻 `zf.i0.o`），阈值取自
  `assets/xmstandarddata.db`（`Standard` 表 Type=2/Index=2 的 EndData）。
- **去脂体重 / 脂肪重量**：由体重与体脂率推导。

### 关于 libconfig.so 的签名校验

`ClientConfigJNI.init()` 会校验 App 签名（内嵌期望 SHA1），不匹配则 `System.exit`。
本项目把 `.so` 内嵌的期望签名串替换为本工程的调试签名 SHA1
（脚本 `reverse/tools/patch_libconfig.py`），使校验通过、算法配置正常加载。

> 注意：`getValue` 的真实参数顺序为
> `(身高, 年龄, 性别, 体重, 阻抗, 体脂, 版本, 类型)`（jadx 反编译的参数命名有误，已按实测校正）。
> 若更换签名密钥，需用新签名 SHA1 重新执行一次补丁脚本。

## 七、功能

- **测量**：扫描 / 自动连接云麦秤、实时体重、用户档案写入、**后台自动接收**（前台服务常驻，
  上秤即自动连接同步，开机自启）。
- **历史**：记录列表、**点击查看详情**（含全部体成分）、**删除（二次确认）**、
  **导出 JSON**、**单项同步到 Health Connect**（体重 + 体脂）。
- **调试**：原始帧日志、手动发帧。

## 八、工程结构

```
app/src/main/java/
  com/hackqing/scale/
    MainActivity.kt              入口 + 权限
    ble/YunmaiProtocol.kt        协议：帧构造 / 解析 / UUID / 命令表
    ble/ScaleGattClient.kt       BLE：扫描 / 连接 / 订阅 / 握手 / 自动重连
    body/BodyComposition.kt      体成分计算（全部指标）
    health/HealthConnectManager.kt  Health Connect 写入
    model/Models.kt              数据模型
    data/ScaleRepository.kt      全局共享状态仓库（App 与后台服务共用）
    data/MeasurementStore.kt     历史持久化（JSON）
    service/ScaleService.kt      后台前台服务（自动重连/接收）
    service/BootReceiver.kt      开机自启
    ui/ScaleViewModel.kt         状态管理
    ui/ScaleScreen.kt            Compose 界面
  com/yunmai/haoqing/logic/config/
    FormulaJNI.java              体成分原生算法封装（类名/签名须与好轻一致）
    ClientConfigJNI.java         原生库初始化
app/src/main/jniLibs/            libconfig.so（已打签名补丁）
reverse/                         逆向工作区（APK、反编译产物、抓包、工具脚本、证据）
```

## 九、备注

- 接管无需 root；root 仅用于抓包/读好轻日志/强停好轻，方便逆向。
- 秤同一时刻只接受一个中心连接，务必先断掉/加强停好轻（后台自动接收会自动重连）。
- 体成分数值依赖「用户档案」中的身高/性别/年龄，请填写真实值以与好轻一致。
- **Health Connect 授权页要求**：Android 14+ 必须声明
  `<activity-alias android:name="ViewPermissionUsageActivity" ...>`
  并处理 **`android.intent.action.VIEW_PERMISSION_USAGE` + category `android.intent.category.HEALTH_PERMISSIONS`**
  （Android 13 及以下则用 `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` 的普通 Activity）。
  缺了它，Health Connect 会以 `App should support rationale intent, finishing!` 秒退授权页。
- 本工程仅用于在自有设备上实现数据互通。
