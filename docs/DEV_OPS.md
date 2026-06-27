# FastMediaSorter v2: OPS & Guidelines

## BUILD COMMANDS (PowerShell)

```powershell
# PRIMARY DEBUG (standard flavor, auto-versions)
.\dev\build-with-version.ps1

# PRIMARY LOCAL DEBUG (reuses configuration cache, stable app version fields)
.\a.ps1 d
.\a.ps1 db
.\a.ps1 dq

# TIMESTAMPED DEBUG ARTIFACT (when you really need an auto-versioned APK)
.\a.ps1 dav

# PER-FLAVOR SCRIPTS
.\scripts\builders\build-standard-debug.ps1
.\scripts\builders\build-standard-release.ps1
.\scripts\builders\build-lite-debug.ps1
.\scripts\builders\build-lite-release.ps1
.\scripts\builders\build-photos-debug.ps1
.\scripts\builders\build-photos-release.ps1
.\scripts\builders\build-legacy-debug.ps1
.\scripts\builders\build-legacy-release.ps1

# VR
.\scripts\builders\build-vr-debug.ps1                   # alias: .\a.ps1 vrd
.\scripts\builders\build-vr-release.ps1                 # alias: .\a.ps1 vr
.\scripts\builders\build-vr-aab.ps1                     # AAB for Meta Horizon Store
.\scripts\builders\install-vr-debug-to-device.ps1       # install, NO launch | alias: .\a.ps1 ivrd
.\scripts\builders\install-vr-release-to-device.ps1     # install, NO launch | alias: .\a.ps1 ivr
.\scripts\builders\build-vr-device.ps1                  # build+install+launch - smoke only, bypasses HorizonOS shell

# RELEASE AAB (standard, for Google Play)
.\scripts\builders\build-aab-release.ps1                # alias: .\a.ps1 r

# WEAR OS
.\gradlew.bat :wear:assembleDebug

# DIRECT GRADLE (any flavor×buildType combination)
.\gradlew.bat assembleStandardDebug
.\gradlew.bat assembleStandardRelease
.\gradlew.bat assembleLiteDebug
.\gradlew.bat assemblePhotosDebug
.\gradlew.bat assembleLegacyDebug
.\gradlew.bat assembleVrDebug
.\gradlew.bat assembleVrRelease
.\gradlew.bat assembleVrUnlicensedRelease
.\gradlew.bat bundleVrRelease                            # AAB for Meta Horizon Store
.\gradlew.bat assembleStandardStaging                    # staging = minified but debuggable
```

## a.ps1 SHORTCUTS

| Alias | Action |
|:------|:-------|
| `.\a.ps1 r`    | Build standard AAB release |
| `.\a.ps1 vr`   | Build VR release APK |
| `.\a.ps1 vrd`  | Build VR debug APK |
| `.\a.ps1 ivr`  | Install VR release to device (no launch) |
| `.\a.ps1 ivrd` | Install VR debug to device (no launch) |
| `.\a.ps1 d`    | Fast reusable debug build (standard) |
| `.\a.ps1 db`   | Fast reusable debug build, skip zip |
| `.\a.ps1 dav`  | Debug build with timestamped app version |
| `.\a.ps1 fk`   | Fast Kotlin compile check |
| `.\a.ps1 fr`   | Fast resources/manifest check |
| `.\a.ps1 fc`   | Fast code + resources check |
| `.\a.ps1 fu`   | Fast full unit-test suite |
| `.\a.ps1 dc`   | Clean + debug build |
| `.\a.ps1 cls`  | Clean Gradle caches |
| `.\a.ps1 ss`   | Show unresolved specs (`sca-specs`) |
| `.\a.ps1 adb <verb>` | Ad-hoc adb swiss-army passthrough (see DEVICE OPS below) |
| `.\a.ps1 adb-devices` / `adb-shot` / `adb-log` / `adb-current` / `adb-launch` / `adb-clear` | Fixed-verb device shortcuts |

## DEVICE OPS (ad-hoc)

`scripts/devtest/adb.ps1` is the quick swiss-army for one-off work against a connected
emulator / device - runs natively (~0 LLM tokens), auto-discovers adb (not on PATH),
takes `-DeviceId` / `-Release` / `-Package` / `-Json`, and uses stable exit codes
(0 ok / 1 no-adb-or-bad-args / 2 no-device / 3 multi-device / 4 pkg-not-installed / 7 adb-failed).

```powershell
.\a.ps1 adb devices                          # online devices: model + Android version
.\a.ps1 adb props                             # selected device: model, release, sdk, density, size
.\a.ps1 adb launch                            # start app (debug: explicit MainActivity, dodges LeakCanary)
.\a.ps1 adb stop                              # force-stop
.\a.ps1 adb clear                             # pm clear (reset app data)
.\a.ps1 adb shot                              # screenshot -> temp/
.\a.ps1 adb log -Tail 400 -Grep "S0035|Net"  # logcat -d app tail, filtered; full dump -> temp/
.\a.ps1 adb current                           # focused activity / package
.\a.ps1 adb install -Flavor standard          # install -r -d newest debug APK (or -Apk <path>)
.\a.ps1 adb tap -X 540 -Y 1000                # input tap / text -Text / key -Key
.\a.ps1 adb shell -Cmd "getprop ro.product.cpu.abi"
```

Run `.\a.ps1 adb` (no verb) for the full verb list. Direct form:
`pwsh -NoProfile -File scripts/devtest/adb.ps1 <verb> [options]`. This is the manual-work
layer; `mobile-mcp` drives agent UI walks, Maestro runs repeatable flows
(`scripts/devtest/maestro/`), `device-ready.ps1` is the test-skill pre-flight.

## TEST & VERIFY

```powershell
# FASTEST PROOFS
.\a.ps1 fk                      # Kotlin/Java symbol changes
.\a.ps1 fr                      # XML/resources/manifest/navigation changes
.\a.ps1 fc                      # Small mixed code + resource changes

# UNIT TESTS
.\a.ps1 fu
.\gradlew.bat testStandardDebugUnitTest

# TARGETED UNIT TESTS
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.SomeClassTest"

# LINT
.\gradlew.bat lintStandardDebug
```

### Preferred local validation ladder

1. `.\a.ps1 fk` for Kotlin-only symbol edits.
2. `.\a.ps1 fr` for resource / manifest edits.
3. `.\a.ps1 fc` for small mixed edits.
4. `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "..."` for focused logic changes.
5. `.\a.ps1 d` only when you need APK packaging / installable artifact proof.

`.\a.ps1 dav` is the slow artifact path. It keeps timestamped in-app versioning, but each unique override creates a fresh configuration-cache entry by design.

### KAPT stall recovery (targeted validation only)

Symptom: `:app_v2:kaptGenerateStubsStandardDebugKotlin` or `:app_v2:kaptStandardDebugKotlin` hangs with no output for several minutes while running a targeted validation command such as `:app_v2:compileStandardDebugKotlin` or `:app_v2:testStandardDebugUnitTest`. The build does not fail, so `build-debug.PS1`'s failure-driven auto-retry does not engage.

Fallback path - abort the stalled invocation, then:

```powershell
# 1. Clean only volatile kapt/kotlin/executionHistory dirs and retry once with --no-daemon.
pwsh -File scripts/utils/recover-kapt-stall.ps1 -Task ":app_v2:testStandardDebugUnitTest"

# 2. Or recover and retry manually (omit -Task to skip the auto-retry).
pwsh -File scripts/utils/recover-kapt-stall.ps1
.\gradlew.bat :app_v2:testStandardDebugUnitTest --no-daemon

# 3. Last resort if the targeted retry stalls again - full wipe (forces a cold rebuild).
.\scripts\builders\clean-gradle-caches.ps1
```

`recover-kapt-stall.ps1` is the targeted scalpel: it stops daemons, removes `app_v2/build/tmp/kapt3`, `app_v2/build/generated/source/kapt*`, `app_v2/build/kotlin`, `app_v2/build/tmp/kotlin-classes`, and `.gradle/<ver>/executionHistory`. `clean-gradle-caches.ps1` nukes everything (`.gradle/`, `build/`, `app_v2/build/`) and is the cold-start option.

### Shared-state mutation audit (S0703)

On-demand quality tool, not a build gate. Finds places where one shared object is mutated from several layers (the "last-write-wins" / redundant / unsafe class).

```powershell
# Stage 1 - mechanical candidate harvest (UI view props + data carriers), ranked report + JSON.
pwsh -NoProfile -File scripts/quality/audit-shared-state-writers.ps1 -Surface all -Top 20 -Json temp/shared-state-audit.json
```

`-Surface ui|data|all`, `-Top N`, `-MinWriters N`. Stage 2 hands the JSON plus the agent prompt `scripts/quality/shared-state-audit-prompt.md` to a research agent that adjudicates indirect writers / concurrency and lists survivors as `/spec-draft` candidates.

### Static analysis (detekt + ktlint) - S0720

A standalone static gate over Kotlin sources - detekt's code-smell/complexity rules plus the ktlint formatting ruleset. It is deliberately NOT wired into `assemble*`, so it never changes the runtime artifact or slows a normal build. Runs lexically (no type resolution), so it is fast and needs no full compile.

```powershell
# Run the gate (both modules)
.\gradlew.bat :app_v2:detekt :wear:detekt

# Wrapper with a PASS/FAIL verdict (this is what post-change.ps1 calls on Kotlin/Mixed)
pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Gate

# Re-freeze the baseline after an intentional refactor (rewrites the per-module XML)
.\gradlew.bat :app_v2:detektBaseline :wear:detektBaseline
```

Ratchet model: each module has a committed baseline freezing every pre-existing finding, so `detekt` fails only on NEW findings. Regenerate the baseline only when you intentionally accept/remove findings.

- Config: `config/detekt/detekt.yml` (relies on `buildUponDefaultConfig` - only enables formatting + a few thresholds).
- Baselines: `config/detekt/baseline-app_v2.xml`, `config/detekt/baseline-wear.xml`.
- Plugin: applied per-subproject in the root `build.gradle.kts` (`subprojects { }`), detekt `1.23.8` + `detekt-formatting`.

## STRING RESOURCE TOOLING

```powershell
# SINGLE-LOCALE UPDATE
pwsh -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale en -Key "cloud_check_failed" -Value "Could not check the cloud connection. Try again."

# EN/RU/UK UPDATE IN ONE CALL
pwsh -File scripts/utils/set-android-strings.ps1 -Module app_v2 -Key "cloud_check_failed" -EnValue "Could not check the cloud connection. Try again." -RuValue "Не удалось проверить подключение к облаку. Попробуйте ещё раз." -UkValue "Не вдалося перевірити підключення до хмари. Спробуйте ще раз."

# OPTIONAL SAFETY GUARDS
pwsh -File scripts/utils/set-android-strings.ps1 -Module app_v2 -Key "cloud_check_failed" -EnValue "Could not check the cloud connection. Try again." -RuValue "Не удалось проверить подключение к облаку. Попробуйте ещё раз." -UkValue "Не вдалося перевірити підключення до хмари. Спробуйте ще раз." -ExpectedOldEnValue "Could not check the cloud connection." -ExpectedOldRuValue "Не удалось проверить подключение к облаку." -ExpectedOldUkValue "Не вдалося перевірити підключення до хмари."

# LOCALE PARITY CHECK
pwsh -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "cloud_check_failed"
```

Use the string updater scripts for targeted `<string>` edits. Manual XML editing is still appropriate for structural resource changes such as `plurals`, `string-array`, comments, regrouping, or bulk rewrites.

## BUILD TYPES

| Type | minify | shrink | debuggable | appId suffix | notes |
|:-----|:------:|:------:|:----------:|:------------:|:------|
| `debug`   | - | - | ✓ | `.debug` | Custom keystore via `debug.keystore.properties`; `LOG_NETWORK_THUMBNAILS=true`; dedicated Dropbox key |
| `staging` | - | - | ✓ | `.staging` | `initWith(release)` - release proguard, shrink disabled; `matchingFallbacks=["release"]` |
| `release` | ✓ | ✓ | - | - | `debugSymbolLevel=FULL`; keystore via `.secrets/keystore.properties` (root fallback supported) |

## FEATURE FLAGS (BuildConfig)

### Core feature matrix

| Flavor           | VIDEO | AUDIO | IMAGES | CLOUD | DOCS | ANIM | VR  |
|:-----------------|:-----:|:-----:|:------:|:-----:|:----:|:----:|:---:|
| **standard**     | [+]   | [+]   | [+]    | [+]   | [+]  | [+]  | [-] |
| **lite**         | [+]   | [+]   | [+]    | [-]   | [-]  | [-]  | [-] |
| **photos**       | [-]   | [-]   | [+]    | [+]   | [-]  | [+]  | [-] |
| **legacy**       | [+]   | [+]   | [+]    | [+]   | [+]  | [+]  | [-] |
| **vr**           | [+]   | [+]   | [+]    | [+]   | [+]  | [+]  | [+] |
| **noLegal**      | [+]   | [+]   | [+]    | [+]   | [+]  | [+]  | [+] |

### Extended per-flavor flags

| Flag | std | lite | photos | legacy | vr | noL |
|:-----|:---:|:----:|:------:|:------:|:--:|:---:|
| `SUPPORT_MIC_RECORDING`            | [+] | [-] | [-] | [+] | [+] | [+] |
| `ENABLE_EPUB`                      | [+] | [-] | [-] | [+] | [+] | [+] |
| `ENABLE_TRANSLATION`               | [+] | [-] | [-] | [+] | [+] | [+] |
| `ENABLE_PERSISTENT_AUDIO_PLAYBACK` | [+] | [-] | [-] | [+] | [+] | [+] |
| `SUPPORTS_DEFAULT_PLAYER`          | [+] | [-] | [+] | [+] | [+] | [+] |
| `SUPPORT_WEAR_COMPANION`           | [+] | [-] | [-] | [+] | [-] | [+] |
| `ENABLE_DTS_DECODER`               | [+] | [-] | [-] | [+] | [+] | [+] |
| `SUPPORT_CAST`                     | [+] | [+] | [+] | [+] | [-] | [+] |
| `SUPPORT_VR_PLAYER`                | -   | -   | -   | -   | [+] | [+] |
| `VR_UI_COMPOSITION_LAYER_ENABLED`  | -   | -   | -   | -   | [+] | [+] |
| `IS_NO_LEGAL_FLAVOR`               | -   | -   | -   | -   | -   | [+] |

`noL` = `noLegal`. Cast is disabled in `vr` (Horizon OS lacks the Google Play Services Cast module); `noLegal` keeps it because it also targets phones/tablets. `SUPPORT_WEAR_COMPANION = true` in `noLegal` is harmless on Quest (no paired watch exists) and meaningful on phones/tablets - runtime decides. VR feature surface in `noLegal` is gated at runtime by `XrDetectionFacade` - VR controls show disabled on devices without an OpenXR runtime. S0250 (2026-05-19) archived the former `vrUnlicensed` flavor; `noLegal` now covers both phone-sideload and Quest-sideload through one APK.

### Build-type flags (all flavors)

| Flag | debug | staging | release |
|:-----|:-----:|:-------:|:-------:|
| `LOG_SMB_IO`                  | [-] | [-] | [-] |
| `LOG_NETWORK_THUMBNAILS`      | [+] | [-] | [-] |
| `LOG_LINK_DOWNLOAD`           | [+] | [-] | [-] |
| `ENABLE_LEAKCANARY`           | [-] | -   | -   |
| `ENABLE_SCHEDULED_OPERATIONS` | [+] | [+] | [+] |
| `ENABLE_BACKGROUND_AUDIO`     | [+] | [+] | [+] |

`ENABLE_LEAKCANARY` is debug-only (`debugImplementation`); field absent in staging/release.

## DATABASE

Room schema version: 6.
Library: `room-runtime:2.7.0`.
Migrations: `AppDatabase.kt`.
**Rule**: Increment schema version on every schema change.

## NDK & ABI

NDK r27c (`27.2.12479018`) - first NDK release with 16 KB page-size aligned `libc++_shared.so` (Google Play requirement since 2025-11-01 for apps targeting Android 15+).

ABI strategy is flavor-local, not buildType-local (AGP merges buildType+flavor `abiFilters` as UNION, not intersection - a buildType-level list would leak non-VR ABIs into VR AABs):
- `standard`, `lite`, `photos`, `legacy`: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`
- `vr`: `arm64-v8a` only (Meta Quest 2/3/Pro)
- `noLegal`: `arm64-v8a` + `x86_64` (Chaquopy Python wheels are arm64/x86_64 only; covers Quest + modern phones + emulators)

## QUEST DEBUGGING (VR flavor)

**Do NOT launch the VR build via `adb shell am start`, Android Studio Run, or MQDH Launch App.**
These entry points bypass the HorizonOS VR shell, so the panel activity is stacked
inside the same Android task as `VrPlayerActivity`. Because the panel activity
carries `com.oculus.intent.category.2D`, the compositor keeps rendering the task
root as the foreground window and the XR session stops at `VISIBLE` instead of
reaching `FOCUSED` - no true immersive VR.

### Why FOCUSED requires the hybrid-app task split

HorizonOS follows Meta's [Hybrid App Model](https://developers.meta.com/horizon/documentation/spatial-sdk/hybrid-apps-overview/):
an app declares two distinct Activities - a panel Activity with
`com.oculus.intent.category.2D` (our `MainActivity`) and an immersive Activity
with `com.oculus.intent.category.VR` (our `VrPlayerActivity`) - and switches
between them via an explicit task swap.

Two co-requisites make the VR category safe:

1. **Separate tasks.** `VrPlayerActivity` declares `android:taskAffinity="${applicationId}.vr"`
   in `app_v2/src/vr/AndroidManifest.xml`. `MainActivity` and the rest of the panel
   Activities stay on the default affinity. The compositor never sees a 2D window
   inside the VR task.
2. **Runtime handoff via `VrTaskTransition`.**
   `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/entry/VrTaskTransition.kt`
   implements the swap:
   - `enterImmersive(source, vrIntent)`: `ACTION_MAIN` + `FLAG_ACTIVITY_NEW_TASK` on the intent, then `source.finishAndRemoveTask()` tears down the panel task.
   - `exitImmersiveToPanel(source)`: builds a `PendingIntent` targeting `MainActivity` with `FLAG_IMMUTABLE`, attaches it as `extra_launch_in_home_pending_intent` on a `CATEGORY_HOME` intent, and calls `finishAndRemoveTask()` on the VR activity. HorizonOS fires the PendingIntent and the user lands on a fresh panel.

All non-VR `PlayerActivity.createIntent(...)` call sites in the VR flavor are
wrapped with `VrTaskTransition.shouldEnterImmersiveTask(intent)` so that explicit
standard-player intents (`BrowseEventHandler.createStandardPlayerIntent` for
MONO/audio) stay on the panel-launch path and preserve their `ActivityResultLauncher`
contract.

### Correct workflow

#### 1. Build + install only (no launch)

```powershell
.\scripts\builders\build-vr-debug.ps1                    # build debug APK   | .\a.ps1 vrd
.\scripts\builders\build-vr-release.ps1                  # build release APK | .\a.ps1 vr
.\scripts\builders\install-vr-debug-to-device.ps1        # install debug, NO launch   | .\a.ps1 ivrd
.\scripts\builders\install-vr-release-to-device.ps1      # install release, NO launch | .\a.ps1 ivr
```

`build-vr-device.ps1` DOES auto-launch via ADB - use it only for fast smoke checks where you don't care about FOCUSED state.

#### 2. Launch from the headset

Menu → Library → *Unknown Sources* → `FastMediaSorter (VR debug)` → tap. HorizonOS launches `MainActivity` as a 2D panel; tapping a VR-target file inside the library triggers the task swap described above, and `VrPlayerActivity` starts in its dedicated VR task.

#### 3. Attach debugger (optional)

Android Studio → `Run → Attach Debugger to Android Process` → select `com.sza.fastmediasorter.vr.debug`. Breakpoints, variable inspection, evaluate expression - all work against the shell-launched process.

#### 4. Live logcat (optional, run before the tap on headset)

```powershell
adb logcat -s VrRuntimeClient OpenXR OpenXrNative VrPlayerActivity OpenXrSessionManager VrTaskTransition
```

### Verifying FOCUSED is reached

After step 2, look for this line in logcat:

```text
OpenXR  PostSessionStateChange: XR_SESSION_STATE_VISIBLE -> XR_SESSION_STATE_FOCUSED
```

Expected full sequence for a successful immersive entry:

```text
XR_SESSION_STATE_IDLE -> XR_SESSION_STATE_READY
XR_SESSION_STATE_READY -> XR_SESSION_STATE_SYNCHRONIZED
XR_SESSION_STATE_SYNCHRONIZED -> XR_SESSION_STATE_VISIBLE
XR_SESSION_STATE_VISIBLE -> XR_SESSION_STATE_FOCUSED
```

If you only see `... -> XR_SESSION_STATE_VISIBLE` and a later `VrRuntimeClient: Client has lost focus.`, the panel task was not destroyed - either you launched via ADB/Studio/MQDH, or a panel Activity was recreated inside the VR task. Dump activities with:

```powershell
adb shell dumpsys activity activities
```

The healthy state after immersive entry is exactly one task with affinity `...vr` containing `VrPlayerActivity`, and no panel task at all.

### Historical note

Earlier revisions of this app attempted to add `com.oculus.intent.category.VR` to
`VrPlayerActivity` without splitting the task affinity. That produced an immediate
black screen because HorizonOS disabled passthrough before the XR session was
ready. The task split is the decisive co-requisite that makes the category safe.
An even earlier theory - that FOCUSED requires forwarding a
`com.oculus.vrshell.launch_id` extra - was disproved by intent dumps (the key was
never present) and has been removed from the codebase; do not re-introduce it.

## Release Signing Fingerprint (GitHub Store)

Spec S0214 - github-store-publication. Once the project ships its first
release through GitHub Store, every subsequent release must be signed with
the same key. If the SHA-256 fingerprint of the new APK does not match the
fingerprint GitHub Store recorded on first install, every user with the
app installed loses auto-update silently: the store flags the new release
as untrusted and falls back to manual install. To prevent that:

### What the pin protects

The pinned fingerprint is the contract between this repo and every device
that installed FastMediaSorter via GitHub Store. Auto-update through the
store's Shizuku / Sui / Dhizuku silent-install paths depends on the
fingerprint staying constant. Any deviation breaks updates en masse.

### Where the pin lives

`scripts/release/expected-signing-fingerprint.txt` - single uppercase
colon-separated SHA-256 line (32 bytes). Comments above explain capture
time, source APK, and keystore alias.

### How the publisher uses it

`scripts/release/publish-github-release.ps1` extracts the SHA-256
fingerprint from each staged APK via `apksigner verify --print-certs`
between the staging and release-create steps. A mismatch is a hard abort
with `expected: …` / `actual: …` in the error message - the publisher
exits non-zero before any GitHub-side mutation. The check runs regardless
of `-DryRun`.

### Rotation procedure (only when legitimately required)

Legitimate rotation reasons: keystore lost, mandated key change, compromise.
Aesthetic re-keying is **not** legitimate - never rotate just to "freshen
up" the signing config.

User-facing consequence is non-negotiable: **every existing GitHub Store
user must reinstall the app from scratch**. Auto-update through the store
will stop working until they do. Plan a rotation around a release where
that cost is acceptable.

Steps:

1. Produce a new keystore (out-of-band; document the new alias in
   root `local.properties` and any signing config that lives outside the repo, preferably under `.secrets/`).
2. Build a release APK with the new keystore (`a.ps1 r` / `a.ps1 vr`).
3. Capture the new SHA-256 via `apksigner verify --print-certs <new-apk>`,
   format as uppercase colon-separated 32-byte form.
4. Update `scripts/release/expected-signing-fingerprint.txt` with the new
   fingerprint and refresh the comment header (capture date, source APK,
   keystore alias).
5. Add an explicit `## Note: signing-key rotation` subsection to
   `docs/WHATS_NEW.md` for the release that rotates the key, with a
   one-line "users must reinstall via direct download" instruction.
6. Run the publisher: `pwsh -File scripts/release/publish-github-release.ps1`
   from the release worktree on `main`. The Assert-ExpectedFingerprint gate
   will now pass against the new pin.
7. Append an ADR-style entry inside this section recording: rotation date,
   reason, old fingerprint, new fingerprint, release tag that contained
   the rotation.

### ADR log

_(no rotations have happened yet - first entry will land here.)_
