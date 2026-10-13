# hackqing — 技术栈对齐 · Hilt+Room 迁移 · MD3 重做 · 趋势页 · HC 补全 · 导出合一

Date: 2026-10-13
Status: Draft (待评审)
Scope: hackqing 单模块 `:app`。与 Kenko / MihealthReader 相互独立，不抽公共框架；复用的代码在本仓库内复制适配。
基底：本设计基于分支 `feat/health-connect-sync-delete`（已含 HC 同步去重、删除联动、后台服务自启）。§7 是在其之上**扩展**，而非从零新增。

## 1. 目标

在**不改变 BLE 协议解析与体成分算法**的前提下，把 hackqing 从"2024 年栈 + JSON 文件 + 无 DI"升级到与 Kenko 一致的技术栈与架构，并补齐四项产品能力：

1. 技术栈对齐（item 1）
2. 持久化 JSON → Room、引入 Hilt（item 1）
3. Material Design 3 重做（item 5）
4. 趋势页（item 4）
5. Health Connect 体成分补全（item 2）
6. 导出 schema 合一（item 6）

非目标：跨端互相导入；Kenko / MihealthReader；改变 `FormulaJNI`/`libconfig.so` 体成分算法；BLE 抓包/逆向工作区。

## 2. 目标技术栈（对齐 Kenko）

Kotlin 2.2.21 · AGP 8.13.2 · Compose BOM 2025.11.00 · Material3 1.5.0-alpha08 · Room 2.8.3 · Hilt 2.57.2 · KSP 2.2.21-2.0.4 · targetSdk 36 / compileSdk 36 / minSdk 26。

- 引入 `gradle/libs.versions.toml` 版本目录（当前为根 build.gradle 硬编码版本）。
- Kotlin/JVM target 17。
- **移除 `gradle.properties` 里写死的本地代理 `127.0.0.1:7890`**（换机器即构建失败）。
- 保留 `jniLibs/libconfig.so`（两个 ABI）与 `com.yunmai.haoqing.logic.config.FormulaJNI`。

## 3. 持久化：JSON → Room + DataStore

当前：`data/MeasurementStore.kt` 把整份历史写成 `filesDir/measurements.json`（全量重写、无版本、无原子写、异常静默吞掉），用户档案在 `SharedPreferences`。

- **Room**：新增 `Kenko` 风格分层
  - `local/model/ScaleMeasurementEntity`（`id` PK、`timeMillis`、`weightKg`、`resistance?`、`bodyFatPercent?`、`userId?`、`heartRate?`、`protoVersion`、`rawHex`、`hcSynced`，以及内联可空的体成分列：`bmi/fatPercent/musclePercent/waterPercent/proteinPercent/boneKg/visceralFat/bmr/bodyAge/leanBodyMassKg/fatMassKg`）。
  - `local/dao/MeasurementDao`（insert / deleteAll / stream / count）。
  - `local/HackqingDatabase`（`exportSchema = true`，schemas 目录）。
- **UserProfile**：迁移到 `DataStore<Preferences>`（`data/local/datastore/`），键与现有字段一一对应。
- **一次性导入迁移**：首次启动若 `measurements.json` 存在且 Room 表为空 → 导入全部，置迁移完成标记；此后以 Room 为准，**不再写 JSON、不删旧文件**（保留回退余地）。
- **仓储**：`data/ScaleRepository.kt` 由"内存 List + 全量写文件"改为走 DAO；启动不再全量重算历史（改为按需）。

## 4. 依赖注入：引入 Hilt

- `@HiltAndroidApp` Application 类；`@AndroidEntryPoint` MainActivity；`ScaleService`（前台服务）、`BootReceiver` 视情况。
- `di/DatabaseModule`（DB + DAO）、`di/RepositoryModule`、`di/AppModule`（`@IoDispatcher`、`@ApplicationScope`）。
- `ui/ScaleViewModel` 由 `by viewModels()` 改为 Hilt 注入；`HealthConnectManager` 改为 `@Singleton` + 构造注入。
- `ScaleRepository` 由 `object` 单例改为 `@Singleton` 注入（保留 App 与前台服务共享同一实例）。

## 5. Material Design 3 重做（item 5）

- 新增 `ui/theme/`：`Theme.kt`（`HackqingTheme(theme, dynamicColor, content)`）、`Color.kt`、`Type.kt`、`Shapes.kt`。
- **明/暗双色板**（当前只有硬编码深色）：高亮用 Material Theme Builder 生成的基线色板（种子色自定，如云麦蓝），Android 12+ 使用 `dynamicDark/LightColorScheme`。
- 删除 `MainActivity.kt` 里硬编码的 `darkColorScheme(...)` 与 `ScaleScreen.kt` 顶部的 `Bg/CardBg/Accent/Dim` 常量，改用 `MaterialTheme.colorScheme`。
- 字符串从 Kotlin 硬编码抽到 `res/values/strings.xml`（中文默认；通知渠道/文案同步）；`values-night/` 可省。
- 导航：保留三页 tab，改为 MD3 `NavigationBar` 组件（是否引入 Navigation-Compose 见 §7 决策）。

## 6. 趋势页（item 4）

- 底部导航改为 **测量 / 趋势 / 历史 / 调试** 四页（默认方案；见 §9 决策）。
- 指标可选：体重、体脂率、肌肉率、水分率、骨量、基础代谢、内脏脂肪、体龄、BMI。
- 范围：7 天 / 30 天 / 90 天 / 全部。
- 图表：面积折线 + 坐标轴 + 最小/平均/最大行；**复用 MihealthReader 的 Canvas 图表做法**（`ui/charts/` 复制适配，实现 `AreaLineChart` + `AxisChart`）。
- 数据源：Room 流，`Flow<List<ScaleMeasurement>>` → 按选择指标/范围聚合为曲线点（纯函数，便于单测）。

## 7. Health Connect 体成分补全（item 2）

**当前分支已有**（`feat/health-connect-sync-delete`）：`HealthConnectManager` 写 `WeightRecord` + `BodyFatRecord`，稳定 `clientRecordId`（`hackqing-weight-<id>` / `hackqing-bodyfat-<id>`）幂等去重，本地 `hcSynced` 标记，删除已同步记录时联动删 HC；`MainActivity` 按开关自动拉起后台服务。遗留 TODO：删除联动的端到端验证、清理旧版无 ID 的重复条目。

在此**基础上扩展**：

- **新增写入**：`LeanBodyMassRecord`、`BoneMassRecord`、`BasalMetabolicRateRecord`、`BodyWaterMassRecord`；沿用同一 `clientRecordId` 方案（`hackqing-leanmass-<id>` 等）。
  - 单位：骨量/去脂体重 kg → `Mass.kilograms`；水分率 % → 质量（体重 × 水分率）写 `BodyWaterMassRecord`；BMR kcal/天 → `Energy.kilocalories`。
- **权限**：在现有 `WRITE_WEIGHT/WRITE_BODY_FAT` 上加 `WRITE_LEAN_BODY_MASS/WRITE_BONE_MASS/WRITE_BASAL_METABOLIC_RATE/WRITE_BODY_WATER_MASS`；rationale `activity-alias` 已有，复用。
- **模式**：只写；新测量即写；沿用现有去重与联动删除；回填历史 + 「全量同步」入口；失败记日志、绝不阻断接收/UI。
- **本地模型**：`hcSynced` 与体成分字段需在 §3 的 Room 实体中持久化（新增列）。

## 8. 导出 schema 合一（item 6）

现状：内部 store schema（短键 `fat/ver/comp`）与导出 schema（长键 `bodyFatPercent/bodyComposition`）**两套并存**。

- 合并为**一套规范导出 JSON**（`schema_version`、`app`、`exportedAt`、`measurements[]`，每个测量内联体成分对象）。
- 单一序列化实现；文件导出（SAF `CreateDocument`）复用它。
- 保持"各自 schema、暂不跨端导入"。

## 9. 需评审拍板的决策

1. **趋势页位置**：独立底部 tab（默认）还是嵌入历史页顶部。
2. **导航**：tab 仍用 `mutableStateOf` 整数切换（最小改动）还是引入 Navigation-Compose（与 Kenko 一致，推荐）。
3. **字符串本地化**：仅中文（默认）还是同时补 `values-en`。
4. **MD3 种子色**：云麦蓝 / 其他。

## 10. 测试与 CI

- 单测（JUnit）：`YunmaiProtocol.parseHex` 黄金用例（用 README 的 raw hex，如 `0D211402006ACCE526070B27751A59015706C2DD`）；`BodyCompositionCalculator`（用 README 样本 169cm/18岁/男/67.45kg/阻抗343/版本33 → BMI 23.6/BMR 1574.9 等）；Room 迁移与 DAO；导出 JSON round-trip；HC DTO 映射（纯函数）。
- 新增 GitHub Actions：`assembleDebug` + `test` + `lint`（覆盖当前无 CI）。
- 设备验证用模拟器；**不在持有真实数据的设备上跑 instrumented 测试**（避免污染真实 DB）。

## 11. 风险

- **签名补丁**：`libconfig.so` 内嵌期望签名 SHA1，`FormulaJNI.init()` 会校验并在不匹配时 `System.exit`。升级/更换签名后必须重跑 `reverse/tools/patch_libconfig.py`。本迁移不改签名前置条件，但需回归验证算法仍返回一致数值。
- **依赖大跳变**（Kotlin 2.0.20 → 2.2.21、AGP 8.9.1 → 8.13.2）可能暴露编译告警/错误与新 API 差异。
- **数据迁移**：JSON → Room 的导入必须幂等、可回退（保留旧文件）。

## 12. 非目标

- 跨端导入；改变协议/算法；Kenko / MihealthReader。
