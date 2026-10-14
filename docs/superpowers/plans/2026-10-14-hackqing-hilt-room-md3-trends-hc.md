# hackqing — Hilt + Room + MD3 + Trends + HC + Export Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate hackqing off JSON-file persistence and manual wiring onto Hilt + Room + DataStore, rebuild it on Material 3, add a trends page, complete Health Connect body-composition writes, and unify the export schema — without touching the BLE protocol or the native body-composition algorithm.

**Architecture:** A single injected `@Singleton` repository replaces the `object ScaleRepository`, backed by a Room `HackqingDatabase` (with a one-time import of the legacy `measurements.json`) and a Preferences DataStore for the user profile. Hilt modules wire DB/DAO/repository/client; a new `ui/theme` supplies M3; a new Trends tab renders a pure aggregation; `HealthConnectManager` gains the remaining body-composition record types; one JSON codec serves export.

**Tech Stack:** Kotlin 2.2.21 · AGP 8.13.2 · Compose BOM 2025.11.00 · Material3 1.5 · Room 2.8.3 · Hilt 2.57.2 · KSP 2.2.21-2.0.4 · `androidx.health.connect:connect-client` 1.1.0 · JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-13-hackqing-hilt-room-md3-trends-hc-design.md`

## Global Constraints

- Work on branch `feat/hilt-room-md3-trends-hc` (base `feat/health-connect-sync-delete`). Never commit to `master`.
- `minSdk 26`, `targetSdk 36`, `compileSdk 36`, JVM target 17.
- **Do not change** `ble/YunmaiProtocol.kt`, `ble/ScaleGattClient.kt`, `body/BodyComposition.kt`, `jniLibs/libconfig.so`, `com.yunmai.haoqing.logic.config.*`.
- Preserve the existing Health Connect dedupe/delete semantics (stable `clientRecordId` `hackqing-weight-<id>` / `hackqing-bodyfat-<id>`, `hcSynced` flag, delete-linkage) from the base branch.
- The JSON→Room import is idempotent and never deletes the legacy file.
- Remove the hardcoded local proxy from `gradle.properties` (`127.0.0.1:7890`).

## Review Focus

- **Legacy data**: existing `measurements.json` must be imported exactly once and never lost; re-launching must not duplicate.
- **Profile change**: editing the profile must still recompute all historical body composition.
- **Body-composition fidelity**: the native algorithm's outputs must be unchanged after the migration.
- **HC idempotency**: re-syncing a measurement (now 6 record types) must not create duplicates; deleting must remove all record types.
- **Background service**: the foreground service must still receive/save measurements and reflect them in the UI (shared repository), after DI.

---

## Task 1: Version catalog + stack alignment + Hilt/Room deps

**Files:**
- Create: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (root), `app/build.gradle.kts`, `gradle.properties`

- [ ] **Step 1: Create `gradle/libs.versions.toml`** with: `agp = "8.13.2"`, `kotlin = "2.2.21"`, `ksp = "2.2.21-2.0.4"`, `composeBom = "2025.11.00"`, `material3 = "1.5.0-alpha08"`, `room = "2.8.3"`, `hilt = "2.57.2"`, `healthConnect = "1.1.0"`, `coreKtx = "1.17.0"`, `lifecycle = "2.9.4"`, `activityCompose = "1.11.0"`, `coroutines = "1.10.2"`, `junit = "4.13.2"`; libraries for the above plus `androidx.compose.material:material-icons-extended`, `androidx.room:room-runtime/ktx/compiler`, `com.google.dagger:hilt-android/compiler`, `androidx.health.connect:connect-client`; plugins `android-application`, `kotlin-android`, `kotlin-compose`, `ksp`, `hilt`.

- [ ] **Step 2: Root `build.gradle.kts`** — declare all five plugins `apply false`; `app/build.gradle.kts` — apply them, `compileSdk = 36` / `targetSdk = 36`, `buildFeatures { compose = true; buildConfig = true }`, `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`, and the dependencies from the catalog. Remove the `kotlinOptions { }` block in favour of the `kotlin { compilerOptions { jvmTarget = JVM_17 } }` form if AGP complains.

- [ ] **Step 3: `gradle.properties`** — delete the `systemProp.http(s).proxyHost/Port` lines (`127.0.0.1:7890`).

- [ ] **Step 4: Bump the Gradle wrapper** to `gradle-8.13-bin.zip` if the current wrapper is older than AGP 8.13 requires.

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (native `libconfig.so` still packaged; algorithm self-check log line present).

- [ ] **Step 6: Commit** `chore: align tech stack and add hilt + room`.

---

## Task 2: Room + DataStore persistence with one-time import

**Files:**
- Create: `app/src/main/java/com/hackqing/scale/data/local/ScaleMeasurementEntity.kt`
- Create: `app/src/main/java/com/hackqing/scale/data/local/MeasurementDao.kt`
- Create: `app/src/main/java/com/hackqing/scale/data/local/HackqingDatabase.kt`
- Create: `app/src/main/java/com/hackqing/scale/data/local/ProfileStore.kt`
- Test: `app/src/test/java/com/hackqing/scale/data/local/LegacyImportTest.kt`

**Interfaces:**
- Produces: `ScaleMeasurementEntity`; `MeasurementDao` (`stream(): Flow<List<ScaleMeasurementEntity>>`, `upsert`, `replaceAll`, `delete(id)`, `count`); `HackqingDatabase` (v1); `ProfileStore` (`profile: Flow<UserProfile>`, `save(UserProfile)`); a pure `parseLegacyMeasurements(json: String): List<ScaleMeasurement>`.

- [ ] **Step 1: Write the failing import test** — feed a small legacy JSON string and assert the parsed `ScaleMeasurement`s (id/time/weight/fat/comp fields), including the `hcSynced` flag.

- [ ] **Step 2: Run it (RED)** — `./gradlew :app:testDebugUnitTest --tests "*LegacyImportTest"` → FAIL (unresolved).

- [ ] **Step 3: Implement** the entity (one-to-one with `ScaleMeasurement`, composition fields inlined as nullable columns), DAO, DB (`@Database(version = 1, exportSchema = true)`), `ProfileStore` (Preferences DataStore `"profile"`), and `LegacyImport.parse(json)` — move the parsing currently in `MeasurementStore.load()` into a pure function so it is testable and reusable for the one-time import.

- [ ] **Step 4: GREEN + build** — `./gradlew :app:testDebugUnitTest :app:assembleDebug`.

- [ ] **Step 5: Commit** `feat(data): room + datastore persistence with legacy import`.

---

## Task 3: Hilt wiring + repository refactor

**Files:**
- Create: `app/src/main/java/com/hackqing/scale/di/{AppModule,DatabaseModule,RepositoryModule}.kt`
- Modify: `app/src/main/java/com/hackqing/scale/App.kt` (new `@HiltAndroidApp` Application — hackqing currently has none; create `HackqingApp`)
- Modify: `MainActivity.kt`, `ui/ScaleViewModel.kt`, `service/ScaleService.kt`, `service/BootReceiver.kt`
- Modify: `data/ScaleRepository.kt` (object → `@Singleton class`)

**Interfaces:**
- Consumes: Task 2's DB/DAO/ProfileStore.
- Produces: injectable `ScaleRepository`, `ScaleGattClient`, `HealthConnectManager`, `HackqingDatabase`+DAO, `ProfileStore`.

- [ ] **Step 1: Modules** — `DatabaseModule` (DB + DAO), `AppModule` (`@IoDispatcher`, `@ApplicationScope`), `RepositoryModule` (`@Singleton ScaleRepository(db, dao, profileStore, client, healthConnectManager, scope)`; `ScaleGattClient(@ApplicationContext)`; `HealthConnectManager(@ApplicationContext)`).

- [ ] **Step 2: `ScaleRepository` → injected singleton** — remove the `object`/`init(context)`; take dependencies via constructor; keep the exact public API (`state`, `startScan`, `connect`, `updateProfile`, `deleteMeasurement`, `markHcSynced`, `setAutoReconnect`, `clearHistory`, `sendHex`, `resendUserInfo`) so `ScaleViewModel`/`ScaleService` change only in how they obtain it. Persist via the DAO (upsert on final measurement; `replaceAll` for recompute-all and clear); run the one-time legacy import in an `init`/`start` guarded by a `prefs` flag `legacy_imported` and DAO `count() == 0`.

- [ ] **Step 3: `HackqingApp : Application` `@HiltAndroidApp`** — move the `ClientConfigJNI.init` + algorithm self-check here (or into `ScaleRepository.init`); register in the manifest `android:name`.

- [ ] **Step 4: Consumers** — `MainActivity` `@AndroidEntryPoint` + `ScaleViewModel` `@HiltViewModel @Inject constructor(repository, healthConnectManager, profileStore, @ApplicationContext context)`; `ScaleService` `@AndroidEntryPoint` + `@Inject` the repository; `BootReceiver` unchanged except obtaining the repo via `EntryPointAccessors` if needed.

- [ ] **Step 5: Build + tests** — `./gradlew :app:assembleDebug :app:testDebugUnitTest` green.

- [ ] **Step 6: Commit** `refactor: migrate to hilt and room-backed repository`.

---

## Task 4: Material 3 theme

**Files:**
- Create: `app/src/main/java/com/hackqing/scale/ui/theme/{Theme.kt,Color.kt,Type.kt}`
- Modify: `MainActivity.kt` (use `HackqingTheme`), `ui/ScaleScreen.kt` (drop `Bg/CardBg/Accent/Dim`), `res/values/strings.xml`

- [ ] **Step 1:** `HackqingTheme(darkTheme, dynamicColor, content)` — dynamic color on Android 12+, else a Material Theme Builder seed palette (keep the current green as the seed's primary for continuity).
- [ ] **Step 2:** Replace the hardcoded color constants in `ScaleScreen.kt` with `MaterialTheme.colorScheme.*`.
- [ ] **Step 3:** Move the visible Chinese strings and the notification channel/text into `res/values/strings.xml`.
- [ ] **Step 4:** Build; manual visual check optional. Commit `style: material 3 theme`.

---

## Task 5: Trends page (TDD)

**Files:**
- Create: `app/src/main/java/com/hackqing/scale/ui/trends/TrendData.kt`
- Create: `app/src/main/java/com/hackqing/scale/ui/trends/TrendsTab.kt` + `ui/trends/Charts.kt`
- Test: `app/src/test/java/com/hackqing/scale/ui/trends/TrendDataTest.kt`
- Modify: `ui/ScaleScreen.kt` (add a `趋势` tab)

**Interfaces:**
- Produces: `enum class TrendMetric { WEIGHT, FAT, MUSCLE, WATER, BONE, BMR, VISCERAL, BODY_AGE, BMI }`; `fun trendPoints(history: List<ScaleMeasurement>, metric: TrendMetric, rangeDays: Int?): List<Pair<Long, Float>>`.

- [ ] **Step 1: Failing test** — a fixed list of measurements → assert `trendPoints` filters by range, sorts by time, and picks the right field per metric (e.g. FAT reads `composition.fatPercent`).
- [ ] **Step 2: RED** → run the test.
- [ ] **Step 3: Implement** `TrendData.kt` (pure) and `TrendsTab.kt` (metric chips, range selector 7/30/90/全部, a Canvas area-line chart copied/adapted from MihealthReader's `AreaLineChart` + axes, and a min/avg/max row).
- [ ] **Step 4: GREEN + build**; add the tab to `ScaleScreen` (`listOf("测量","趋势","历史","调试")`).
- [ ] **Step 5: Commit** `feat(ui): trends page`.

---

## Task 6: Health Connect body-composition completion

**Files:**
- Modify: `app/src/main/java/com/hackqing/scale/health/HealthConnectManager.kt`
- Modify: `AndroidManifest.xml` (add 4 write permissions)

- [ ] **Step 1:** Add `LeanBodyMassRecord`, `BoneMassRecord`, `BasalMetabolicRateRecord`, `BodyWaterMassRecord` to `permissions` and to `sync(m)` (from `m.composition`), each with its own stable `clientRecordId` (`hackqing-lean-<id>`, `hackqing-bone-<id>`, `hackqing-bmr-<id>`, `hackqing-water-<id>`); body water mass = `weightKg * waterPercent/100` kg. Extend `deleteFor(m)` to delete all six. `hcSynced` semantics unchanged.
- [ ] **Step 2:** Manifest — `WRITE_LEAN_BODY_MASS`, `WRITE_BONE_MASS`, `WRITE_BASAL_METABOLIC_RATE`, `WRITE_BODY_WATER_MASS` (rationale activity/alias already present).
- [ ] **Step 3:** Build. Commit `feat(health): write full body composition to health connect`.

---

## Task 7: Unify the export schema

**Files:**
- Create: `app/src/main/java/com/hackqing/scale/data/ExportCodec.kt`
- Modify: `ui/ScaleViewModel.kt` (use the codec)

- [ ] **Step 1:** Move the export JSON building out of `ScaleViewModel.exportJson` into `ExportCodec.toJson(history): String` (one canonical schema: `app`, `exportedAt`, `count`, `measurements[]` with inline `bodyComposition`). Keep `MeasurementStore`'s short-key schema only as the legacy import reader.
- [ ] **Step 2:** Build + commit `refactor: single export json codec`.

---

## Task 8: CI + verification

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1:** Workflow running `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug` on JDK 17.
- [ ] **Step 2:** Run the same locally; ensure green.
- [ ] **Step 3:** Manual (device): algorithm self-check log still matches `BMI 23.6 / 肌肉 55.4%`; legacy history appears once; trends render; HC writes/updates 6 record types without duplicates. Update `docs/` if present. Commit.

## Self-Review Notes

- Spec coverage: §3 Room/DataStore→Tasks 2-3; §4 Hilt→Task 3; §5 MD3→Task 4; §6 trends→Task 5; §7 HC→Task 6; §8 export→Task 7; §10 CI→Task 8.
- The `ScaleRepository` object→singleton conversion is the riskiest step (shared by UI and the foreground service); keep its public API identical to minimise caller churn.
- No AIDL in hackqing, so the Windows `\u` aidl issue does not apply here.
- Native algorithm must be regression-checked (self-check log) after the DI move.
