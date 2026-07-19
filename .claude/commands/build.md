---
description: "Use when building the app locally or answering build-system questions - flavor matrix, gradle flags, a.ps1 targets, build failures. The local build flow, not release. Triggers: 'build', 'compile', 'why did the build fail', 'which flavor'."
---

# Build Guide

> **GLOBAL DIRECTIVES (ANTI-BUREAUCRACY):**
> 1. Dry technical prose only - no filler.
> 2. Autonomy: silently fix minor/non-structural inaccuracies; block only for critical business-logic decisions.
> 3. Terse report: one dry statement of what was done and why.

A local **build** ("сборка"): compile, verify locally, commit to a `DEBUG-v0NN` branch. Free - zero GitHub Actions minutes (see `docs/BUILD_VS_RELEASE.md`). This skill is two things: a **work-order checklist** for doing one build without losing anything, and a **reference** for the build system, scripts, versioning, flavors, deployment. For publishing a version, that is a release - use `/release`.

## Usage

```
/build                          # run the build checklist: fast checks + build standard debug + build noLegal debug
/build <specific question>      # answer from the reference below
```

Examples:
- `/build` - execute the work-order checklist end to end, building both standard and noLegal debug
- `/build how do I build for device?`
- `/build what flavors exist?`
- `/build how does versioning work?`

---

## Process

On `$ARGUMENTS`:
- Step 1 - Parse. Empty -> **execute** the Build Checklist below in order; do not just print it. A specific topic -> skip execution and answer from the reference.
- Step 2 - For empty input: walk the checklist steps, running each command and recording `expected: X | actual: Y`. Build standard debug first, then noLegal debug - sequentially, never two gradle builds at once (daemon OOM) - this is hard-enforced by `temp/BUILD.LOCK` (see Cross-Agent Build Lock below), not just a convention. Report a terse one-line verdict per build (PASS/FAIL + APK path or failure digest).
- Step 3 - For a specific question: answer from the reference below (authoritative for build/script/versioning). Don't search the codebase unless the question requires it.

---

## Build Checklist (work order)

The order of work for a single local build, so nothing is dropped. None of this spends GitHub Actions minutes - a DEBUG-branch push triggers no CI (`docs/BUILD_VS_RELEASE.md`).

1. **Branch.** `git branch --show-current` - confirm a `DEBUG-v0NN` branch, never `main`. The pre-push guard blocks an accidental `main` push.
2. **Scope the change.** Know exactly which files this build covers; for a spec, the active `Sxxxx`.
3. **Fast checks first** (seconds, not a full build):
   - `.\a.ps1 fk` - Kotlin compile check (symbol/signature changes).
   - `.\a.ps1 fc` - code + resources (when XML/strings/layouts touched).
   - `.\a.ps1 fu` - unit suite, when logic changed (note pre-existing failures; verify own work per-class).
4. **Build standard debug.** `.\a.ps1 dq` (fast, quiet) or `.\a.ps1 d` (zipped reusable APK). `.\a.ps1 cd` only when a clean build is genuinely needed.
4b. **Build noLegal debug.** `.\a.ps1 nd` (`scripts/builders/build-nolegal-debug.ps1`). Run only after Step 4 finishes - never two gradle builds concurrently (daemon OOM); a second attempt while `temp/BUILD.LOCK` is live refuses on its own (see below). noLegal mounts `src/noLegal/` + VR/streaming source sets the standard flavor skips, so it catches flavor-isolation breakage standard cannot.
5. **Verify locally.** Install + drive on a device/emulator when the change is user-visible (`/run`, `/verify`, or `.\a.ps1 id`). A compile-only change can stop at Step 3.
6. **Clean up the touched files.** Resolve lint/neuroslop warnings in files you edited; Timber only (no `Log.d`); landscape layout parity if a `res/layout/` file changed.
7. **Record the change.** Dev log per logical change (`scripts/post-change.ps1` facade, or `add_to_dev_log.ps1`); catalog sync once per ticket after `.kt` edits.
8. **Commit + push to DEBUG.** `.\a.ps1 c "<message>"` - `git add` + commit + push to the current DEBUG branch. Quoted arg = commit subject. Still zero CI.

Build failed? `.\a.ps1 bf` (last failure block) or `.\a.ps1 bfd` (structured digest). kapt stall -> `scripts/utils/recover-kapt-stall.ps1`.

---

## Build Reference

> Terminology: a local **build** (free, on a `DEBUG-v0NN` branch) versus a **release** (paid CI, on `main`) is defined in `docs/BUILD_VS_RELEASE.md` - the source of truth for which actions spend GitHub Actions minutes.

### Quick Decision Table

| Goal | Command |
|------|---------|
| Standard debug + version bump + deploy to device | `.\dev\build-with-version.ps1` |
| Fast debug (no version bump) | `.\a.ps1 d` (zip) · `.\a.ps1 db` (no zip) |
| **Fast quiet debug (recommended in skill loops)** | `.\a.ps1 dq` (no zip, suppresses UP-TO-DATE / deprecated-DSL / known-acceptable warnings) |
| noLegal debug (sideload flavor) | `.\a.ps1 nd` (`scripts/builders/build-nolegal-debug.ps1`) |
| Specific flavor debug | `.\gradlew.bat assemble<Flavor>Debug` |
| Device build (build + adb install + launch) | `.\scripts\builders\build-standard-device.ps1` |
| Release APK | `.\scripts\builders\build-standard-release.ps1` |
| Release AAB (Play Store) | `.\scripts\builders\build-aab-release.ps1` |
| All flavors release | `.\scripts\builders\build-release.ps1` |
| Unit tests | `.\gradlew.bat testStandardDebugUnitTest` |
| Lint | `.\gradlew.bat lintStandardDebug` |
| Wear OS debug | `.\gradlew.bat :wear:assembleDebug` |

**Quiet mode (`dq` / `-Quiet`).** `scripts/builders/build-debug.PS1` accepts `-Quiet` (mapped via `a.ps1 dq`). Captures full Gradle output for retry-detection (Gradle cache pack, kapt incrementalData lock) but prints only lines NOT in known-noise list:

- `> Task :app_v2:X UP-TO-DATE / NO-SOURCE / SKIPPED / FROM-CACHE`
- Three AGP DSL deprecation warnings (`builtInKotlin`, `newDsl`, `applicationVariants`/`testVariants`/`unitTestVariants`)
- Known-acceptable Kotlin warnings (`PlayerActivity.kt` `'open' has no effect on a final class` lines 678/686/689/1122)
- `Note: [1] Wrote GeneratedAppGlideModule`, kapt processor-option notes, `Using the build cache is enabled`

Errors, FAIL/SUCCESS verdict lines, final task list, any unknown warning: always printed. Total suppression count reported at end - nothing hidden silently. Update pattern list in `scripts/builders/build-debug.PS1` when adding intentional warnings; never use it to mask real failures.

---

### Cross-Agent Build Lock (CLAUDE.md Rule 23)

Every builder script (and any gradle-backed quality gate: `assert-detekt.ps1`, `assert-settings-doc-sync.ps1`, `assert-icon-inventory-sync.ps1 -IncludeExportTest`) acquires `temp/BUILD.LOCK` before invoking `gradlew`/`gradlew.bat` and releases it after, success or failure - via `scripts/utils/agent-lock.ps1`. If a second agent session tries to build while one is live, the script refuses immediately:

```
BUILD.LOCK held - refusing to start a second gradle build.
  Holder PID: 12345  age: 47s  reason: 'build-debug.PS1'  host: MARK
```

This is not a guessed timeout - staleness is judged by whether the holder PID is still alive, so a genuinely long build (release AAB, full unit suite, an OOM-recovery cascade) is never force-unlocked out from under it. A dead holder (crashed/killed process) is reclaimed automatically on the next attempt.

Check status without trying to build: `pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build` (exit 0 = free, 1 = held; add `-Json` for machine-readable output). If refused, wait for the holder to finish (or work on something else) rather than retrying in a loop.

Before a multi-file source edit, skills also acquire `temp/CODE.LOCK` (`scripts/utils/enter-code-lock.ps1`) - advisory only, so a live build only warns about it, it never refuses.

---

### Product Flavors

| Flavor | VIDEO | AUDIO | IMAGES | CLOUD | DOCS | ANIM | VR | minSdk | Distribution |
|--------|:-----:|:-----:|:------:|:-----:|:----:|:----:|:--:|:------:|:-------------|
| `standard` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | - | 26 | Google Play |
| `lite` | ✓ | - | ✓ | - | - | - | - | 26 | Google Play |
| `photos` | - | - | ✓ | - | - | ✓ | - | 26 | Google Play |
| `legacy` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | - | 23 | Google Play (API 23-25) |
| `vr` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 26 | Meta Horizon Store |
| `vrUnlicensed` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 26 | ADB sideload only (same appId as `vr`) |
| `noLegal` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 26 | Sideload only (gitignored docs) |

Capability flags gated via `BuildConfig` fields in `app_v2/build.gradle.kts` (source of truth for flavor config). Use only inside flavor source sets - never inside `src/main/java/`.

### Flavor Source Set Discipline

Each flavor has own source set: `app_v2/src/<flavor>/java/`, `.../res/`, `AndroidManifest.xml`. Flavor-only code goes there, NOT into `src/main/java/`.

| Layout | Purpose | Where to place new code |
|--------|---------|-------------------------|
| `src/main/java/` | Shared across all flavors | Contract interfaces + No-Op fallbacks |
| `src/standard/java/` | Currently empty; reserved for standard-only logic | Standard-only impls (rare) |
| `src/vr/java/` | OpenXR rendering, VR HUD, immersive activity | All VR-only impls; mounted into `vr`, `vrUnlicensed`, `noLegal` |
| `src/noLegal/java/` | yt-dlp / NewPipe extractors, GPL components | noLegal-only impls (sideload only, never in Google Play) |
| `src/lite/`, `src/photos/`, `src/legacy/` | Manifest + res only at present | Add Java sources here if these flavors need divergent behavior |
| `src/streamingEnabled/java/` | Shared by flavors with HLS/DASH (`standard`, `noLegal`, `legacy`, `vr`, `vrUnlicensed`) | Streaming download pipeline |
| `src/streamingDisabled/java/` | Shared by flavors without streaming (`lite`, `photos`) | No-Op streaming pipeline |

Rules: `dev/FLAVOR_DEVELOPMENT_RULES.md` + CLAUDE.md Rule 15. New flavor-specific feature recipe:

1. `src/main/java/.../FeatureX.kt` - `interface FeatureX { ... }` + `class NoOpFeatureX : FeatureX` + `@Module class FeatureXMainModule { @Binds fun bind(noOp: NoOpFeatureX): FeatureX }`.
2. `src/<flavor>/java/.../RealFeatureX.kt` - real impl.
3. `src/<flavor>/java/.../di/FeatureXFlavorModule.kt` - `@Module class FeatureXFlavorModule { @Binds fun bind(real: RealFeatureX): FeatureX }` + `@TestInstallIn` or replace-strategy as flavor's main module.
4. Never write `if (BuildConfig.SUPPORT_VR_PLAYER) { startVr() } else { ... }` inside `src/main/java/`.

Troubleshooting:
- `unresolved reference: SomeClass` building flavor X not Y → `src/main/java/` references class living only in `src/<Y>/java/`. Move reference behind interface in main.
- "leaks GPL code to standard build" warning during compliance audit → check `src/main/java/` for imports from `com.sza.fastmediasorter.noLegal.*` (must be zero).
- VR build compiles for non-arm64 ABI → check `cmake.abiFilters = arm64-v8a` in flavor's `externalNativeBuild` block.

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

**versionCode format:** `YYMMDDHHm` (9 digits, first digit of minutes only - to stay within Int32.MaxValue). Example: `260328143` = 2026/03/28 14:3x

**How applied:**
- `.\dev\build-with-version.ps1` - generate version from datetime, patch `app_v2/build.gradle.kts`, build, restore on failure.
- `.\build-debug.PS1` - fast build, does NOT bump version.
- Manual: edit `versionCode` + `versionName` in `app_v2/build.gradle.kts` directly.

---

### Build Scripts Overview

#### Primary Scripts

| Script | What it does |
|--------|-------------|
| `.\dev\build-with-version.ps1` | Auto-bumps version from datetime → builds all debug flavors → copies APK to `c:\GD\i\APK\` → installs + launches on device (background) |
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

Extracted logs land under `temp/Sxxxx/` (ticket-bound) or `temp/scratch/` (no ticket), never project root. Script-owned digests keep their fixed paths (e.g. `temp/build-failure-digest.json`).


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

Maestro flows in `maestro/smoke/` and `maestro/critical/`. Do NOT use `npm install -g maestro-cli`.

---

### KAPT Stall Recovery

If `:app_v2:kaptGenerateStubsStandardDebugKotlin` or `:app_v2:kaptStandardDebugKotlin` hangs with no output for several minutes during targeted validation command (`:app_v2:compileStandardDebugKotlin`, `:app_v2:testStandardDebugUnitTest`, per-class test run), build is not failing - stalls silently, so `build-debug.PS1` cannot auto-retry. Abort stalled invocation, use targeted recovery:

```powershell
# Stop daemons, clear only kapt/kotlin/executionHistory volatile state, retry once with --no-daemon
pwsh -NoProfile -File scripts/utils/recover-kapt-stall.ps1 -Task ":app_v2:testStandardDebugUnitTest"

# Recover without auto-retry, then run any command manually
pwsh -NoProfile -File scripts/utils/recover-kapt-stall.ps1
.\gradlew.bat :app_v2:testStandardDebugUnitTest --no-daemon

# Cold-start fallback if even the targeted retry stalls again
.\scripts\builders\clean-gradle-caches.ps1
```

`recover-kapt-stall.ps1` removes `app_v2/build/tmp/kapt3`, `app_v2/build/generated/source/kapt*`, `app_v2/build/kotlin`, `app_v2/build/tmp/kotlin-classes`, `.gradle/<ver>/executionHistory` only - does NOT wipe `.gradle/` or `app_v2/build/`. Use `clean-gradle-caches.ps1` only when targeted path fails twice.

---

### Dev Log (Mandatory After Every Change)

After every code or config change:
```powershell
.\scripts\add_to_dev_log.ps1 "<relative_path>" "<ClassName or target>" "<short description>"
```

Example:
```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/PlayerActivity.kt" "PlayerActivity" "Add Chromecast button to overflow menu"
```

Appends timestamped row to `dev/CHANGELOG.md`. Never edit `CHANGELOG.md` directly.

---

### Dependency Management

- Version catalog: `gradle/libs.versions.toml` - check here first before adding any dependency.
- Library absent from catalog → add it there before referencing in `build.gradle.kts`.
- Room schema version: increment on every entity/schema change in `app_v2/build.gradle.kts` AND add migration in `AppDatabase.kt`.

---

### SDK & Platform Baseline

| Setting | Value |
|---------|-------|
| `compileSdk` | 35 |
| `minSdk` (standard/lite/photos) | 26 (Android 8.0) |
| `minSdk` (legacy flavor) | 23 (Android 6.0) |
| Java | 17 |
| Kotlin | 2.2.10 |

---

### Quality Rules

- Temp artifacts, APK copies, backups → `temp/Sxxxx/` (ticket-bound) or `temp/scratch/` (no ticket), never project root. Fixed infra (locks, `temp/done/`, caches, script-owned digests) stays at `temp/` root - see CLAUDE.md Rule 10.1.
- File size limit 1500 lines. Files >500 lines need timestamped backup under `temp/Sxxxx/` (or `temp/scratch/`) before modification.
- Never `Log.d()` - use `Timber` only.
- Activity/Fragment logic delegated to `helpers/*Manager.kt`.
