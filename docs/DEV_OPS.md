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
All of those bypass the Horizon OS VR shell, so `com.oculus.vrshell.launch_id` is
missing from the Intent. The Meta XR runtime then registers with an empty
`clientLaunchId`, and the XR session stops at `VISIBLE` instead of reaching
`FOCUSED` — the headset stays in panel/overlay mode, no true immersive VR.

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

Menu → Library → *Unknown Sources* → `FastMediaSorter (VR debug)` → tap. HorizonOS now puts `com.oculus.vrshell.launch_id` into the launch Intent.

#### 3. Attach debugger (optional)

Android Studio → `Run → Attach Debugger to Android Process` → select `com.sza.fastmediasorter.vr.debug`. Breakpoints, variable inspection, evaluate expression — all work against the shell-launched process.

#### 4. Live logcat (optional, run before the tap on headset)

```powershell
adb logcat -s VrRuntimeClient OpenXR OpenXrNative VrPlayerActivity OpenXrSessionManager
```

### Verifying FOCUSED is reached

After step 2, look for this line in logcat:

```text
OpenXR  PostSessionStateChange: XR_SESSION_STATE_VISIBLE -> XR_SESSION_STATE_FOCUSED
```

If you only see `... -> XR_SESSION_STATE_VISIBLE` and a later `VrRuntimeClient: Client has lost focus.`, the launch token was not delivered — you launched via ADB/Studio/MQDH instead of from Library.

### Why launch_id matters

`VrPlayerActivity.getLaunchId(activity)` reads `com.oculus.vrshell.launch_id` from the Intent and is called by the Meta XR runtime via JNI reflection during `xrCreateSession`. `PlayerActivity.createIntent()` forwards the token from the source Activity's Intent (typically `MainActivity`, which the shell launched with the token) into the `VrPlayerActivity` Intent.
