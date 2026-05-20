---
mode: agent
description: "Use when: asked how to build the project, which build command to use, what flavors exist, how versioning works, how to deploy to device, or asked to run /build command. Triggers on: build, compile, assemble, APK, flavor, version bump, deploy."
---

# Build Guide

> **GLOBAL EXECUTION DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. **STRICTLY TECHNICAL LANGUAGE:** No fluff, no conversational filler, dry technical prose only.
> 2. **AUTONOMY OVER BUREAUCRACY:** DO NOT prompt the user for minor or non-structural specification inaccuracies. Silently fix them, make reasonable technical decisions, and proceed. Only block for critical business-logic decisions.
> 3. **TERSE REPORTING:** NO verbose summaries or time tracking. After executing this skill, output ONLY a single dry, concise statement of what was done and why.

Answer questions about the build system, scripts, versioning, flavors, and deployment for FastMediaSorter v2.

## Usage

```
/build [optional: specific question or topic]
```

Examples:
- `/build` - show the full build reference
- `/build how do I build for device?`
- `/build what flavors exist?`
- `/build how does versioning work?`

---

## Process

When this command is invoked with `$ARGUMENTS`:

**Step 1 - Parse the question.**
If `$ARGUMENTS` is provided, focus the answer on that topic. Otherwise output the full build reference below.

**Step 2 - Answer from the reference below.**
Do not search the codebase unless the user's question requires it. The reference below is authoritative for build/script/versioning questions.

---

## Build Reference

### Quick Decision Table

| Goal | Command |
|------|---------|
| Standard debug + version bump + deploy to device | `.\dev\build-with-version.ps1` |
| Fast debug (no version bump) | `.\build-debug.PS1` |
| Specific flavor debug | `.\gradlew.bat assemble<Flavor>Debug` |
| Device build (build + adb install + launch) | `.\scripts\builders\build-standard-device.ps1` |
| Release APK | `.\scripts\builders\build-standard-release.ps1` |
| Release AAB (Play Store) | `.\scripts\builders\build-aab-release.ps1` |
| All flavors release | `.\scripts\builders\build-release.ps1` |
| Unit tests | `.\gradlew.bat testStandardDebugUnitTest` |
| Lint | `.\gradlew.bat lintStandardDebug` |
| Wear OS debug | `.\gradlew.bat :wear:assembleDebug` |

---

### Product Flavors

| Flavor | VIDEO | AUDIO | IMAGES | CLOUD | DOCS | ANIM | minSdk |
|--------|:-----:|:-----:|:------:|:-----:|:----:|:----:|:------:|
| `standard` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 26 |
| `lite` | ✓ | - | ✓ | - | - | - | 26 |
| `photos` | - | - | ✓ | - | - | ✓ | 26 |
| `legacy` | ✓ | ✓ | ✓ | - | - | ✓ | 23 |

Features are gated via `BuildConfig` fields in `app_v2/build.gradle.kts`.
Source of truth for flavor config: `app_v2/build.gradle.kts`.

**Gradle flavor targets:**
```powershell
.\gradlew.bat assembleStandardDebug
.\gradlew.bat assembleLiteDebug
.\gradlew.bat assemblePhotosDebug
.\gradlew.bat assembleLegacyDebug

.\gradlew.bat assembleStandardRelease
.\gradlew.bat assembleLiteRelease
.\gradlew.bat assemblePhotosRelease
.\gradlew.bat assembleLegacyRelease
```

**Per-flavor builder scripts** (under `scripts/builders/`):
```powershell
.\scripts\builders\build-standard-debug.ps1
.\scripts\builders\build-lite-debug.ps1
.\scripts\builders\build-photos-debug.ps1
.\scripts\builders\build-legacy-debug.ps1
.\scripts\builders\build-standard-release.ps1
# ..etc
```

---

### Versioning

**Format:** `Y.YM.MDDH.Hmm`

| Segment | Meaning | Example |
|---------|---------|---------|
| `Y` | First digit of year | `2` (2026) |
| `YM` | Last digit of year + first digit of month | `63` (2026/03) |
| `MDDH` | Second digit of month + day + first digit of hour | `2281` (03/28 1x:xx) |
| `Hmm` | Second digit of hour + minutes | `432` (1**4**:32) |

Example: `2.63.2281.432` = 2026/03/28 14:32

**versionCode format:** `YYMMDDHHm` (9 digits, first digit of minutes only - to stay within Int32.MaxValue)
- Example: `260328143` = 2026/03/28 14:3x

**How versioning is applied:**
- `.\dev\build-with-version.ps1` - generates version from current datetime, patches `app_v2/build.gradle.kts`, builds, restores on failure.
- `.\build-debug.PS1` - fast build, does NOT bump version.
- Manual bump: edit `versionCode` and `versionName` in `app_v2/build.gradle.kts` directly.

---

### Build Scripts Overview

#### Primary Scripts

| Script | What it does |
|--------|-------------|
| `.\dev\build-with-version.ps1` | Auto-bumps version from datetime → builds all debug flavors → copies APK to `c:\GD\i\APK\` → installs + launches on connected device (background) |
| `.\build-debug.PS1` | Fast debug build, no version change |
| `.\scripts\builders\build-standard-device.ps1` | Build standard debug + adb install + launch |
| `.\scripts\builders\build-debug-device.ps1` | Generic debug build + device deploy |
| `.\scripts\builders\build-lite-device.ps1` | Lite flavor + device |
| `.\scripts\builders\build-photos-device.ps1` | Photos flavor + device |
| `.\scripts\builders\build-legacy-device.ps1` | Legacy flavor + device |
| `.\scripts\builders\build-standard-release.ps1` | Signed standard release APK |
| `.\scripts\builders\build-aab-release.ps1` | AAB for Play Store upload |
| `.\scripts\builders\build-release.ps1` | All flavors release |
| `.\scripts\builders\build-and-push-all.ps1` | Build all + push to device |
| `.\scripts\builders\build-universal.ps1` | Universal APK |
| `.\scripts\builders\clean-gradle-caches.ps1` | Wipe Gradle caches |

#### APK Output Paths (after build)
```
app_v2/build/outputs/apk/standard/debug/FastMediaSorter_debug.apk
app_v2/build/outputs/apk/lite/debug/
app_v2/build/outputs/apk/photos/debug/
app_v2/build/outputs/apk/legacy/debug/
app_v2/build/outputs/apk/standard/release/
```

---

### Device & Log Scripts

```powershell
# Install release APK manually via ADB
.\scripts\utils\Install_release_on_adb_connected_device.ps1

# Pull logcat + shared prefs from device
.\scripts\utils\extract-device-logs.ps1

# Search extracted log for errors
.\scripts\utils\search-log.ps1 -Errors

# General log search
.\scripts\utils\search-log.ps1 -Pattern "keyword"
```

Extracted logs land in `temp/` (never in project root).

---

### Testing

```powershell
# Unit tests (standard flavor)
.\gradlew.bat testStandardDebugUnitTest

# Unit tests (other flavors)
.\gradlew.bat testLiteDebugUnitTest
.\gradlew.bat testPhotosDebugUnitTest
.\gradlew.bat testLegacyDebugUnitTest

# Lint
.\gradlew.bat lintStandardDebug

# Maestro E2E smoke tests (~2-3 min)
.\scripts\utils\run-maestro-smoke.ps1

# Maestro critical suite
.\scripts\utils\run-maestro-smoke.ps1 -Suite critical

# Stress tests
.\scripts\utils\run-maestro-stress.ps1
.\scripts\utils\run-stress.ps1
```

Maestro test flows live in `maestro/smoke/` and `maestro/critical/`.
Do NOT use `npm install -g maestro-cli`.

---

### Dev Log (Mandatory After Every Change)

After every code or config change, run:
```powershell
.\scripts\add_to_dev_log.ps1 "<relative_path>" "<ClassName or target>" "<short description>"
```

Example:
```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt" "PlayerActivity" "Add Chromecast button to overflow menu"
```

This appends a timestamped row to `dev/CHANGELOG.md`. Never edit `CHANGELOG.md` directly.

---

### Dependency Management

- Version catalog: `gradle/libs.versions.toml` - check here first before adding any dependency.
- If a library is absent from the catalog, add it there before referencing it in `build.gradle.kts`.
- Room schema version: increment on every entity/schema change in `app_v2/build.gradle.kts` AND add a migration in `AppDatabase.kt`.

---

### SDK & Platform Baseline

| Setting | Value |
|---------|-------|
| `compileSdk` | 35 |
| `minSdk` (standard/lite/photos) | 26 (Android 8.0) |
| `minSdk` (legacy flavor) | 23 (Android 6.0) |
| Java | 17 |
| Kotlin | 1.9+ |

---

### Quality Rules

- All temp artifacts, APK copies, and backups go to `temp/` - never to project root.
- File size limit: 1500 lines max. Files >500 lines need a timestamped backup in `temp/` before modification.
- Never use `Log.d()` - use `Timber` only.
- Activity/Fragment logic must be delegated to `helpers/*Manager.kt` classes.
