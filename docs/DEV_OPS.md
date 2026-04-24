# FastMediaSorter v2: OPS & Guidelines

## BUILD COMMANDS (PowerShell)

```powershell
# PRIMARY DEBUG
.\dev\build-with-version.ps1

# FAST DEBUG
.\build-debug.PS1

# FLAVORS
.\gradlew.bat assembleStandardDebug
.\gradlew.bat assembleLiteDebug
.\gradlew.bat assemblePhotosDebug
.\gradlew.bat assembleLegacyDebug
.\gradlew.bat assembleVrDebug

# WEAR OS
.\gradlew.bat :wear:assembleDebug

# RELEASE
.\gradlew.bat assembleStandardRelease
.\gradlew.bat assembleVrRelease
.\gradlew.bat bundleVrRelease          # AAB for Google Play / Android XR
```

## TEST & VERIFY

```powershell
# UNIT TESTS
.\gradlew.bat testStandardDebugUnitTest

# LINT
.\gradlew.bat lintStandardDebug
```

## FEATURE FLAGS (BuildConfig)

| FLAVOR       | VIDEO | AUDIO | IMAGES | CLOUD | DOCS | ANIM | VR  |
| :----------- | :---: | :---: | :----: | :---: | :--: | :--: | :-: |
| **standard** |  [+]  |  [+]  |  [+]   |  [+]  | [+]  | [+]  | [-] |
| **vr**       |  [+]  |  [+]  |  [+]   |  [+]  | [+]  | [+]  | [+] |
| **lite**     |  [+]  |  [-]  |  [+]   |  [-]  | [-]  | [-]  | [-] |
| **photos**   |  [-]  |  [-]  |  [+]   |  [-]  | [-]  | [+]  | [-] |
| **legacy**   |  [+]  |  [+]  |  [+]   |  [-]  | [-]  | [+]  | [-] |

## DATABASE

Room Config: Version 6.
Migrations: `AppDatabase.kt`.
**Rule**: Increment version on schema change.

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
