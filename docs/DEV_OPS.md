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