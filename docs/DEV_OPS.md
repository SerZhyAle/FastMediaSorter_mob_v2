# FastMediaSorter v2: OPS & Guidelines

## BUILD COMMANDS (PowerShell)

```powershell
# PRIMARY DEBUG (standard flavor, auto-versions)
.\dev\build-with-version.ps1

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
.\scripts\builders\build-vr-device.ps1                  # build+install+launch — smoke only, bypasses HorizonOS shell

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
| `.\a.ps1 d`    | Build debug (standard) |
| `.\a.ps1 db`   | Build debug, skip zip |
| `.\a.ps1 dc`   | Clean + debug build |
| `.\a.ps1 cls`  | Clean Gradle caches |
| `.\a.ps1 ss`   | Show unresolved specs (`sca-specs`) |

## TEST & VERIFY

```powershell
# UNIT TESTS
.\gradlew.bat testStandardDebugUnitTest

# LINT
.\gradlew.bat lintStandardDebug
```

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
| `debug`   | — | — | ✓ | `.debug` | Custom keystore via `debug.keystore.properties`; `LOG_NETWORK_THUMBNAILS=true`; dedicated Dropbox key |
| `staging` | — | — | ✓ | `.staging` | `initWith(release)` — release proguard, shrink disabled; `matchingFallbacks=["release"]` |
| `release` | ✓ | ✓ | — | — | `debugSymbolLevel=FULL`; keystore via `keystore.properties` |

## FEATURE FLAGS (BuildConfig)

### Core feature matrix

| Flavor           | VIDEO | AUDIO | IMAGES | CLOUD | DOCS | ANIM | VR  |
|:-----------------|:-----:|:-----:|:------:|:-----:|:----:|:----:|:---:|
| **standard**     | [+]   | [+]   | [+]    | [+]   | [+]  | [+]  | [-] |
| **lite**         | [+]   | [+]   | [+]    | [-]   | [-]  | [-]  | [-] |
| **photos**       | [-]   | [-]   | [+]    | [+]   | [-]  | [+]  | [-] |
| **legacy**       | [+]   | [+]   | [+]    | [+]   | [+]  | [+]  | [-] |
| **vr**           | [+]   | [+]   | [+]    | [+]   | [+]  | [+]  | [+] |
| **vrUnlicensed** | [+]   | [+]   | [+]    | [+]   | [+]  | [+]  | [+] |

### Extended per-flavor flags

| Flag | std | lite | photos | legacy | vr | vrU |
|:-----|:---:|:----:|:------:|:------:|:--:|:---:|
| `SUPPORT_MIC_RECORDING`            | [+] | [-] | [-] | [+] | [+] | [+] |
| `ENABLE_EPUB`                      | [+] | [-] | [-] | [+] | [+] | [+] |
| `ENABLE_TRANSLATION`               | [+] | [-] | [-] | [+] | [+] | [+] |
| `ENABLE_PERSISTENT_AUDIO_PLAYBACK` | [+] | [-] | [-] | [+] | [+] | [+] |
| `SUPPORTS_DEFAULT_PLAYER`          | [+] | [-] | [+] | [+] | [+] | [+] |
| `SUPPORT_WEAR_COMPANION`           | [+] | [-] | [-] | [+] | [-] | [-] |
| `ENABLE_DTS_DECODER`               | [+] | [-] | [-] | [+] | [+] | [+] |
| `SUPPORT_CAST`                     | [+] | [+] | [+] | [+] | [-] | [-] |
| `VR_UI_COMPOSITION_LAYER_ENABLED`  | —   | —   | —   | —   | [+] | [+] |

`vrU` = `vrUnlicensed`. Cast is disabled in VR flavors: Horizon OS lacks the Google Play Services Cast module. `vrUnlicensed` always ships DTS — no store-review restrictions.

### Build-type flags (all flavors)

| Flag | debug | staging | release |
|:-----|:-----:|:-------:|:-------:|
| `LOG_SMB_IO`                  | [-] | [-] | [-] |
| `LOG_NETWORK_THUMBNAILS`      | [+] | [-] | [-] |
| `LOG_LINK_DOWNLOAD`           | [+] | [-] | [-] |
| `ENABLE_LEAKCANARY`           | [-] | —   | —   |
| `ENABLE_SCHEDULED_OPERATIONS` | [+] | [+] | [+] |
| `ENABLE_BACKGROUND_AUDIO`     | [+] | [+] | [+] |

`ENABLE_LEAKCANARY` is debug-only (`debugImplementation`); field absent in staging/release.

## DATABASE

Room schema version: 6.
Library: `room-runtime:2.7.0`.
Migrations: `AppDatabase.kt`.
**Rule**: Increment schema version on every schema change.

## NDK & ABI

NDK r27c (`27.2.12479018`) — first NDK release with 16 KB page-size aligned `libc++_shared.so` (Google Play requirement since 2025-11-01 for apps targeting Android 15+).

ABI strategy is flavor-local, not buildType-local (AGP merges buildType+flavor `abiFilters` as UNION, not intersection — a buildType-level list would leak non-VR ABIs into VR AABs):
- Non-VR flavors: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`
- `vr` + `vrUnlicensed`: `arm64-v8a` only (Meta Quest 2/3/Pro)

## QUEST DEBUGGING (VR flavor)

**Do NOT launch the VR build via `adb shell am start`, Android Studio Run, or MQDH Launch App.**
These entry points bypass the HorizonOS VR shell, so the panel activity is stacked
inside the same Android task as `VrPlayerActivity`. Because the panel activity
carries `com.oculus.intent.category.2D`, the compositor keeps rendering the task
root as the foreground window and the XR session stops at `VISIBLE` instead of
reaching `FOCUSED` — no true immersive VR.

### Why FOCUSED requires the hybrid-app task split

HorizonOS follows Meta's [Hybrid App Model](https://developers.meta.com/horizon/documentation/spatial-sdk/hybrid-apps-overview/):
an app declares two distinct Activities — a panel Activity with
`com.oculus.intent.category.2D` (our `MainActivity`) and an immersive Activity
with `com.oculus.intent.category.VR` (our `VrPlayerActivity`) — and switches
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

`build-vr-device.ps1` DOES auto-launch via ADB — use it only for fast smoke checks where you don't care about FOCUSED state.

#### 2. Launch from the headset

Menu → Library → *Unknown Sources* → `FastMediaSorter (VR debug)` → tap. HorizonOS launches `MainActivity` as a 2D panel; tapping a VR-target file inside the library triggers the task swap described above, and `VrPlayerActivity` starts in its dedicated VR task.

#### 3. Attach debugger (optional)

Android Studio → `Run → Attach Debugger to Android Process` → select `com.sza.fastmediasorter.vr.debug`. Breakpoints, variable inspection, evaluate expression — all work against the shell-launched process.

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

If you only see `... -> XR_SESSION_STATE_VISIBLE` and a later `VrRuntimeClient: Client has lost focus.`, the panel task was not destroyed — either you launched via ADB/Studio/MQDH, or a panel Activity was recreated inside the VR task. Dump activities with:

```powershell
adb shell dumpsys activity activities
```

The healthy state after immersive entry is exactly one task with affinity `...vr` containing `VrPlayerActivity`, and no panel task at all.

### Historical note

Earlier revisions of this app attempted to add `com.oculus.intent.category.VR` to
`VrPlayerActivity` without splitting the task affinity. That produced an immediate
black screen because HorizonOS disabled passthrough before the XR session was
ready. The task split is the decisive co-requisite that makes the category safe.
An even earlier theory — that FOCUSED requires forwarding a
`com.oculus.vrshell.launch_id` extra — was disproved by intent dumps (the key was
never present) and has been removed from the codebase; do not re-introduce it.
