# FastMediaSorter v2 - Technical Requirements & Stack Reference

**Last Updated**: July 19, 2026
**Purpose**: Single source of truth for the full technical stack, library inventory, platform constraints, minimum and recommended requirements.

---

## 1. Platform & SDK

| Parameter            | Value                          | Notes                                           |
|:---------------------|:-------------------------------|:------------------------------------------------|
| Platform             | Android Native                 | Kotlin + Java                                   |
| Kotlin version       | 2.2.10                         | Pinned in `build.gradle.kts`                    |
| Java target          | 17                             | `jvmTarget = "17"`                              |
| Android Gradle Plugin| 9.2.1                          | AGP, pinned in root `build.gradle.kts`          |
| Gradle               | 9.4.1                          | Wrapper in `gradle/wrapper/` (AGP 9.x)          |
| compileSdk           | 36                             | Android 16 (Baklava)                            |
| targetSdk            | 36                             | Required for Play Store compliance (S1149)      |
| minSdk (standard)    | 26                             | Android 8.0 (Oreo)                              |
| minSdk (legacy)      | 23                             | Android 6.0 (Marshmallow) - covers API 23-25    |
| minSdk (wear)        | 28                             | Wear OS 2.0+                                    |
| NDK version          | 27.2.12479018                  | For native libraries (Tesseract OCR, VR)        |
| KSP version          | 2.3.8                          | Aligned with Kotlin 2.2.10                      |

---

## 2. Module Structure

| Module   | Package Root                                        | UI Toolkit          | Purpose                    |
|:---------|:----------------------------------------------------|:--------------------|:---------------------------|
| `app_v2` | `com.sza.fastmediasorter`                           | View System + Compose | Main Android app           |
| `wear`   | `com.sza.fastmediasorter.wear`                      | Jetpack Compose      | Wear OS companion          |

### Product Flavors (app_v2 only)

| Flavor     | minSdk | Video | Audio | Images | Cloud | Documents | Animations | EPUB | Translation |
|:-----------|:------:|:-----:|:-----:|:------:|:-----:|:---------:|:----------:|:----:|:-----------:|
| `standard` | 26     | ✅    | ✅    | ✅     | ✅    | ✅        | ✅         | ✅   | ✅          |
| `lite`     | 26     | ✅    | ✅    | ✅     | ❌    | ❌        | ❌         | ❌   | ❌          |
| `photos`   | 26     | ❌    | ❌    | ✅     | ✅    | ❌        | ✅         | ❌   | ❌          |
| `legacy`   | 23     | ✅    | ✅    | ✅     | ✅    | ✅        | ✅         | ✅   | ✅          |

---

## 3. Architecture

- **Pattern**: Clean Architecture + MVVM + Hilt DI
- **Data flow**: `UI → ViewModel → UseCase → Repository → DataSource`
- **Dependency rule**: `UI → Domain → Data` (never reverse)
- **Key patterns**: Manager delegation, Strategy (file ops), Connection pooling (SMB)

---

## 4. Dependency Inventory

### 4.1 AndroidX Core

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `core-ktx`                      | 1.13.0     | Kotlin extensions for Android      |
| `appcompat`                     | 1.7.1      | Backward-compatible UI framework   |
| `constraintlayout`             | 2.1.4      | Complex layout engine              |
| `recyclerview`                  | 1.3.2      | Scrolling list/grid rendering      |
| `swiperefreshlayout`           | 1.1.0      | Pull-to-refresh gesture            |
| `viewpager2`                    | 1.0.0      | Swipe paging                       |
| `activity-ktx`                  | 1.8.2      | Activity KTX extensions            |
| `fragment-ktx`                  | 1.6.2      | Fragment KTX extensions            |
| `documentfile`                  | 1.0.1      | SAF (Storage Access Framework)     |
| `exifinterface`                 | 1.3.7      | Image metadata (EXIF)              |
| `profileinstaller`             | 1.3.1      | Baseline Profiles runtime          |
| `security-crypto`              | 1.1.0-alpha06 | EncryptedSharedPreferences      |

### 4.2 Jetpack Compose

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `compose-bom`                   | 2024.02.00 | BOM for Compose dependencies       |
| `compose-ui`                    | (BOM)      | Core Compose UI                    |
| `compose-material3`            | (BOM)      | Material 3 components              |
| `compose-material-icons`       | (BOM)      | Icon sets (core + extended)        |
| `activity-compose`             | 1.8.2      | Compose Activity integration       |
| `lifecycle-viewmodel-compose`  | 2.7.0      | ViewModel in Compose               |
| Compose Compiler Extension      | 1.5.14     | Matched to Kotlin 1.9.24           |

### 4.3 Wear OS (wear module)

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `wear-compose-material`        | 1.2.1      | Wear OS Material Compose           |
| `wear-compose-foundation`      | 1.2.1      | Wear OS Compose Foundation         |
| `wear-compose-navigation`      | 1.2.1      | Wear OS Compose Navigation         |
| `play-services-wearable`       | 18.1.0     | Phone↔Watch communication          |
| `wear`                          | 1.3.0      | Wear OS essentials                 |
| `accompanist-permissions`       | 0.34.0     | Runtime permission handling        |
| `coil-compose`                  | 2.5.0      | Image loading (Compose)            |
| `hilt-navigation-compose`      | 1.1.0      | Hilt ViewModel in Compose Nav      |

### 4.4 Lifecycle & Navigation

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `lifecycle-viewmodel-ktx`      | 2.7.0      | ViewModel with coroutines          |
| `lifecycle-livedata-ktx`       | 2.7.0      | LiveData with coroutines           |
| `lifecycle-runtime-ktx`        | 2.7.0      | Lifecycle-aware coroutines         |
| `lifecycle-process`            | 2.7.0      | ProcessLifecycleOwner              |
| `navigation-fragment-ktx`      | 2.7.6      | Fragment navigation                |
| `navigation-ui-ktx`            | 2.7.6      | Navigation UI integration          |

### 4.5 Dependency Injection

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `hilt-android`                  | 2.59       | DI framework                       |
| `hilt-android-compiler`        | 2.59       | Annotation processor (KSP)         |
| `hilt-work`                     | 1.2.0      | Hilt + WorkManager integration     |
| `hilt-compiler`                 | 1.2.0      | Hilt AndroidX compiler             |

### 4.6 Data Persistence

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `room-runtime`                  | 2.7.0      | SQLite ORM                         |
| `room-ktx`                     | 2.7.0      | Room coroutines support            |
| `room-compiler`                | 2.7.0      | Room annotation processor          |
| `datastore-preferences`        | 1.1.7      | Key-value preferences store        |
| `paging-runtime-ktx`           | 3.2.1      | Paging 3 library                   |
| Room DB version                  | 50         | Current schema version (see AppDatabase.kt) |

### 4.7 Media Playback

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `media3-exoplayer`             | 1.2.1      | Video/audio playback (no DASH/HLS) |
| `media3-ui`                    | 1.2.1      | Player UI components               |
| `media3-common`                | 1.2.1      | Media3 shared types                |
| `media3-decoder`               | 1.2.1      | Audio decoders (WAV etc.)          |
| `media3-session`               | 1.2.1      | MediaSession (background audio)    |

### 4.8 Image Loading & Display

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `glide`                         | 4.16.0     | Image loading (app_v2)             |
| `glide-ksp`                    | 4.16.0     | Glide annotation processor - the KSP artifact, not `glide-compiler` |
| `glide-okhttp3-integration`    | 4.16.0     | OkHttp transport for Glide         |
| `camera-core` / `camera-camera2` / `camera-lifecycle` / `camera-view` | 1.5.3 | In-app CameraX preview and JPEG capture |
| `PhotoView`                     | 2.3.0      | Pinch-to-zoom & rotation           |
| `fastscroll`                    | 1.3.0      | Interactive RecyclerView scrollbar  |
| `coil-compose`                  | 2.5.0      | Image loading (wear, Compose)      |

### 4.9 Network Protocols

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `smbj`                          | 0.12.1     | SMB 2/3 client                     |
| `jsch`                          | 0.2.26     | SFTP/SSH client (mwiede fork)      |
| `commons-net`                   | 3.10.0     | FTP client (Apache)                |
| `okhttp`                        | 4.12.0     | HTTP client                        |
| `retrofit`                      | 2.9.0      | REST API client                    |
| `converter-gson`               | 2.9.0      | JSON serialization for Retrofit    |

### 4.10 Cloud Storage Providers

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `play-services-auth`           | 21.0.0     | Google Sign-In (OAuth2)            |
| `dropbox-core-sdk`             | 5.4.5      | Dropbox API client                 |
| `msal`                          | 6.0.1      | Microsoft Identity (OneDrive)      |

### 4.11 OCR & AI

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `mlkit-translate`              | 17.0.3     | On-device translation              |
| `mlkit-text-recognition`       | 16.0.1     | OCR (Latin/Cyrillic)               |
| `mlkit-language-id`            | 17.0.6     | Language identification             |
| `tesseract4android`            | 4.8.0      | Offline OCR (better Cyrillic)      |

### 4.12 Document Processing

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `epub4j-core`                   | 4.2        | EPUB parsing                       |
| `jsoup`                         | 1.17.2     | HTML parsing (EPUB content)        |
| `markwon-core`                  | 4.6.2      | Markdown rendering                 |
| `zip4j`                         | 2.11.5     | Password-protected ZIP extraction  |
| PdfRenderer                      | built-in   | PDF rendering (API 21+)            |

### 4.13 Logging & Debugging

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `timber`                         | 5.0.1      | Structured logging                 |
| `leakcanary-android`           | 2.12       | Memory leak detection (debug)      |
| `chucker`                       | 4.0.0      | HTTP inspector (debug)             |
| `work-multiprocess`            | 2.9.0      | LeakCanary heap analysis (debug)   |

### 4.14 Background Processing

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `work-runtime-ktx`             | 2.9.0      | WorkManager (background jobs)      |
| `kotlinx-coroutines-android`   | 1.7.3      | Android coroutine dispatchers      |
| `kotlinx-coroutines-core`      | 1.7.3      | Core coroutine library             |
| `kotlinx-coroutines-play-services` | 1.7.3  | Play Services Task↔Coroutine       |

### 4.15 UI Extras

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `material`                       | 1.14.0     | Material Design 3 components       |

### 4.16 Build Compatibility

| Library                          | Version    | Purpose                            |
|:---------------------------------|:-----------|:-----------------------------------|
| `desugar_jdk_libs`              | 2.0.4      | java.time.* and Java 8+ APIs on API 23-25 (legacy flavor). API 26+ uses native support. |

### 4.17 Testing

| Library                          | Version    | Scope    | Purpose                     |
|:---------------------------------|:-----------|:---------|:----------------------------|
| `junit`                          | 4.13.2     | unit     | Test framework               |
| `kotlinx-coroutines-test`      | 1.7.3      | unit     | Coroutine test utilities     |
| `core-testing`                  | 2.2.0      | unit     | Architecture components test |
| `mockk`                         | 1.13.9     | unit     | Mocking framework            |
| `robolectric`                   | 4.11.1     | unit     | Android framework in JVM     |
| `espresso-core`                 | 3.5.1      | android  | UI testing                   |
| `test-ext-junit`               | 1.1.5      | android  | AndroidJUnit runner          |
| `navigation-testing`           | 2.7.6      | android  | Navigation test utilities    |
| `hilt-android-testing`         | 2.59       | android  | Hilt test injection          |
| `room-testing`                  | 2.7.0      | android  | Room migration tests         |

---

## 5. Build Configuration

### Gradle Properties

| Property                                   | Value           | Purpose                                 |
|:-------------------------------------------|:----------------|:----------------------------------------|
| `org.gradle.jvmargs`                      | `-Xmx16g`      | Max heap for Gradle daemon              |
| `org.gradle.configuration-cache`           | `false`         | Safe default for direct noLegal/IDE builds; helper scripts opt in explicitly |
| `org.gradle.parallel`                      | `true`          | Parallel task execution                 |
| `org.gradle.caching`                       | `true`          | Build cache enabled                     |
| `android.enableR8.fullMode`               | `false`         | R8 compat mode (faster builds)          |
| `android.bundle.enableNativeLibraryAlignment` | `true`      | 16 KB page alignment (Play Store req)   |
| `ksp.incremental`                          | `false`         | KSP incremental processing off: it fails with "this and base files have different roots" on this host |

### ProGuard / R8

- Release builds: `isMinifyEnabled = true`, `isShrinkResources = true`
- ProGuard rules: `proguard-android.txt` (non-optimizing) + `proguard-rules.pro`
- Debug symbols: `FULL` level for release crash reports

### Signing

- Release signing configured via `.secrets/keystore.properties` (excluded from VCS; root fallback still supported)
- Debug builds use auto-generated debug keystore

### Localization

- Supported locales: `en`, `ru`, `uk`
- Resource configurations restricted to these locales for APK size optimization

---

## 6. Technical Constraints & Limitations

### Hard Constraints

| Constraint                              | Details                                                    |
|:----------------------------------------|:-----------------------------------------------------------|
| JDK version                             | 17, 21, or 25. JDK 26+ is **incompatible** with the current Gradle 9.4.1 / AGP 9.2.1 toolchain |
| Kotlin ↔ Compose compiler match         | Kotlin 1.9.24 requires Compose Compiler 1.5.14 exactly     |
| 16 KB page alignment                    | Mandatory for Google Play since Nov 1, 2025 (Android 15+)  |
| `Log.d()` prohibited                    | Use `Timber` exclusively                                   |
| Activity business logic prohibited      | Delegate to Manager/Helper classes                         |
| File size limit                         | Max 1500ines per file; extract to `helpers/`             |
| Room DB migration                       | Must increment version on any schema change                |
| Native lib packaging                    | `useLegacyPackaging = false` for 16KB alignment            |

### Logical Constraints

| Constraint                              | Details                                                    |
|:----------------------------------------|:-----------------------------------------------------------|
| Cloud token storage                     | EncryptedSharedPreferences (per-account keys)              |
| Token refresh                           | Proactive at 50 min + reactive on 401 (max 3 retries, 2s delay) |
| Glide memory cache                      | `heap × 10%` with 64 MB cap                               |
| Memory tier                             | Heap-aware: downgrades tier on small heap devices          |
| FTP active mode                         | Fallback to active mode if passive fails                   |
| SFTP coroutine check                    | Must check `Job.isActive` in JSch callbacks                |
| SMB connection pooling                  | Managed by `SmbConnectionManager`                          |
| Cloud path prefix                       | `cloud://` for cloud resources, `smb://`/`sftp://`/`ftp://` for network |
| Secret masking                          | Tokens/passwords auto-replaced with `***` in logs          |

---

## 7. Minimum Requirements (End User)

### Main App (app_v2)

| Parameter                | Standard/Photos      | Lite flavor          | Legacy flavor       |
|:-------------------------|:---------------------|:---------------------|:--------------------|
| Android version          | 8.0 (Oreo, API 26)  | 8.0 (Oreo, API 26)  | 6.0 (Marshmallow, API 23) |
| RAM                      | ≥ 2 GB               | ≥ 2 GB               | ≥ 1.5 GB            |
| Heap (dalvik.vm.heapsize)| ≥ 256 MB             | ≥ 256 MB             | ≥ 128 MB            |
| Free storage             | ≥ 200 MB             | ≥ 200 MB             | ≥ 200 MB            |
| Google Play Services     | Required for Cloud    | Not used - no cloud providers | Required for Cloud   |
| Internet                 | Required for Cloud/OCR Translation | Not required - local files only | Required for Cloud/OCR Translation |
| Network protocols        | SMB 2/3, SFTP, FTP   | None - `SUPPORT_LOCAL_NETWORK` is false | SMB 2/3, SFTP, FTP  |

`lite` is split out because grouping it with Standard/Photos asserted cloud and network support it does not have (`SUPPORT_CLOUD` and `SUPPORT_LOCAL_NETWORK` both false since S0448). The generated grid is [`docs/FLAVOR_MATRIX.md`](../docs/FLAVOR_MATRIX.md); `vr` and `noLegal` match the Standard column on every row in this table.

### Wear OS (wear)

| Parameter                | Value                              |
|:-------------------------|:-----------------------------------|
| Wear OS version          | 2.0+ (API 28)                     |
| RAM                      | ≥ 1 GB                             |
| Free storage             | ≥ 100 MB                           |
| Companion app            | Required on paired phone            |
| Connectivity             | Bluetooth/Wi-Fi to phone or direct Wi-Fi for SMB |

---

## 8. Recommended Requirements (End User)

### Main App (app_v2)

| Parameter                | Recommended                       |
|:-------------------------|:----------------------------------|
| Android version          | 12+ (API 31+)                     |
| RAM                      | ≥ 4 GB                            |
| Heap                     | ≥ 512 MB                          |
| Free storage             | ≥ 500 MB (for OCR models + cache) |
| Processor                | ARMv8-A (arm64-v8a) or x86_64     |
| Screen                   | ≥ 5" with 1080p resolution        |
| Network                  | Wi-Fi 5 (802.11ac) for SMB/cloud  |
| Google Play Services     | Latest version                     |

### Wear OS (wear)

| Parameter                | Recommended                       |
|:-------------------------|:----------------------------------|
| Wear OS version          | 3.0+ (API 30+)                    |
| RAM                      | ≥ 2 GB                            |
| Display                  | AMOLED, 400×400+                  |
| Connectivity             | Direct Wi-Fi capable               |

---

## 9. Build Artifacts & Commands

### Quick Reference

```powershell
# Debug APK (standard)
.\gradlew.bat assembleStandardDebug

# Release APK (standard)
.\gradlew.bat assembleStandardRelease

# AAB (for Google Play)
.\gradlew.bat bundleStandardRelease

# All flavors debug
.\gradlew.bat assembleDebug

# Unit tests
.\gradlew.bat testStandardDebugUnitTest

# Lint check
.\gradlew.bat lintStandardDebug

# Wear OS debug
.\gradlew.bat :wear:assembleDebug

# Build with auto-version
.\dev\build-with-version.ps1
```

### Output Paths

| Artifact            | Path                                                              |
|:--------------------|:------------------------------------------------------------------|
| Debug APK           | `app_v2/build/outputs/apk/standard/debug/`                       |
| Release APK         | `app_v2/build/outputs/apk/standard/release/`                     |
| AAB                 | `app_v2/build/outputs/bundle/standardRelease/`                   |
| Lint report (HTML)  | `app_v2/build/reports/lint-results.html`                         |
| Test report (HTML)  | `app_v2/build/reports/tests/testStandardDebugUnitTest/index.html`|

---

## 10. Project Statistics

Detailed live complexity snapshot is maintained in `dev/PRODUCT_COMPLEXITY_ASSESSMENT.md`.

### Codebase Size

| Metric                  | Value                 | Notes                                           |
|:------------------------|:----------------------|:------------------------------------------------|
| Snapshot date           | July 17, 2026         | See dedicated complexity assessment for method  |
| Kotlin source files     | 2,360                 | All checked-in Kotlin under `app_v2/src` + `wear/src` |
| XML layout/config files | 934                   | Layouts, manifests, menus, drawables, XML config |
| Total lines of code     | 342,695               | Kotlin only, current repository snapshot        |
| Total source files      | 3,294                 | Kotlin + XML combined                           |

### Code Objects

| Object Type             | Count                 | Notes                                           |
|:------------------------|:----------------------|:------------------------------------------------|
| Classes (including data)| 634                   | Domain models, UI, repositories, managers       |
| Interfaces             | 79                    | Contracts for DI, repositories, use cases       |
| Enums                  | 41                    | MediaType, ResourceType, SortMode, etc.         |
| Functions/Methods      | 3,098                 | Top-level + nested (avg ~6 per class)           |

### Module Breakdown

| Module / Scope          | KT Files | LOC (approx) | Purpose                     |
|:------------------------|:---------|:-------------|:----------------------------|
| `app_v2` + `wear` (all source sets) | 2,360 | 342,695 | Full checked-in app code surface |
| Production subset (no tests) | 1,956 | 272,129 | Runtime product code only |

### Architectural Breakdown (app_v2)

| Layer                   | Packages | Purpose                                     |
|:------------------------|:---------|:--------------------------------------------|
| **UI** (`ui/`)          | ~80 files| Activities, Fragments, ViewModels, Compose  |
| **Domain** (`domain/`)  | ~40 files| Use Cases, Models, Repository interfaces    |
| **Data** (`data/`)      | ~150 files| Repositories, DataSources, DB, Network      |
| **Core** (`core/`, `util/`, `di/`) | ~80 files | DI, managers, utilities, logging             |
| **Background** (`worker/`, `widget/`) | ~20 files | WorkManager, widgets                       |

### Database

| Aspect                  | Value                 | Notes                                           |
|:------------------------|:----------------------|:------------------------------------------------|
| Room DB version        | 50                    | Current schema in `AppDatabase`                |
| Number of entities     | 27                    | Current `@Database(entities = ..)` set         |
| Migrations             | 49 migrations         | Registered in DatabaseModule; through v50      |

---

## 11. Version History (Relevant Pinning Decisions)

| Decision                           | Reason                                                      |
|:-----------------------------------|:------------------------------------------------------------|
| Kotlin 2.2.10                      | Aligned with KSP 2.3.8 + Compose plugin 2.2.10              |
| AGP 9.2.1                          | Requires Gradle 9.x; introduces `AndroidApiLevel`           |
| Gradle 9.4.1                       | Required by AGP 9.x; JDK 17/21                              |
| Hilt 2.59                          | Current stable; processed by KSP since S1338 phase 10       |
| Media3 1.2.1 (not 1.3+)           | 1.3+ requires API adjustments not yet validated             |
| Glide 4.16.0                       | Latest stable; 5.x requires migration                      |
| SMBJ 0.12.1                        | Last version before breaking BC changes in 0.13            |
| Compose BOM 2024.02.00             | Matched to Wear Compose 1.2.1 compatibility                |

---

*This document is the authoritative reference for technical stack and requirements. For architecture details see `docs/ARCHITECTURE.md`, for build/ops see `docs/DEV_OPS.md`.*
