# Build & Test Fast Path

This playbook defines the cheapest safe validation path for common change types in `app_v2/` and `wear/`.

Goal: reduce local feedback time without lowering confidence or skipping quality gates.

## Core rule

Pick the cheapest proof that matches the risk of the change.

- Do not default to full APK builds for every edit.
- Do not default to `clean`.
- Do not default to the full unit suite when one targeted test proves the change.
- Escalate only when the previous proof level is not sufficient for the changed surface.

## Default command set

Use these commands as the standard local toolbox:

```powershell
.\a.ps1 fk
.\a.ps1 fr
.\a.ps1 fc
.\a.ps1 fu
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.SomeClassTest"
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Assemble
.\a.ps1 d
.\a.ps1 db
.\a.ps1 dq
.\gradlew.bat :wear:assembleDebug
.\a.ps1 adb install -Flavor standard
.\a.ps1 adb launch
.\a.ps1 adb log -Tail 400 -Grep "FATAL|ANR|Sxxxx"
pwsh -NoProfile -File scripts/utils/recover-kapt-stall.ps1 -Task ":app_v2:testStandardDebugUnitTest"
```

## Fast-path routing

### 1. Docs, comments, or non-runtime text only

Use grep-only proof.

```powershell
rg -n "expected text" docs dev <path>
```

Do not run Gradle for doc-only edits.

### 2. Kotlin or Java symbol edit, no resources touched

Use:

```powershell
.\a.ps1 fk
```

Examples:
- helper extraction
- ViewModel logic change
- repository/use-case edit
- DI wiring that does not need packaging proof

### 3. XML, manifest, navigation, or resources only

Use:

```powershell
.\a.ps1 fr
```

Examples:
- layout edits
- manifest flags
- drawables
- string/resource reshaping

### 4. Small mixed code + resource change

Use:

```powershell
.\a.ps1 fc
```

This is the default proof for small UI changes that touch both Kotlin and XML.

### 5. Focused logic fix with known tests

Run the narrowest relevant unit test first.

```powershell
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.SomeClassTest"
```

Prefer one class, one package, or one failure-focused test pattern before the full suite.

### 6. Broad logic change or shared infrastructure change

Escalate to the full local unit suite.

```powershell
.\a.ps1 fu
```

Examples:
- shared utility used across features
- settings serialization model
- data-layer contract changes
- script changes that alter generated values consumed by tests

### 7. Need installable artifact or packaging proof

Use the fast reusable debug path.

```powershell
.\a.ps1 d
```

If ZIP output is not needed, prefer:

```powershell
.\a.ps1 db
```

If you want less console noise during repeated local loops, prefer:

```powershell
.\a.ps1 dq
```

### 8. Need assemble proof without the full wrapper path

Use:

```powershell
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Assemble
```

This is useful when compile/resources are not enough, but you still do not need the richer artifact handling of `.\a.ps1 d`.

### 9. Wear-only change

Use:

```powershell
.\gradlew.bat :wear:assembleDebug
```

Escalate only if the change also affects shared code used by `app_v2/`.

### 10. Device smoke after a local build

Use:

```powershell
.\a.ps1 adb install -Flavor standard
.\a.ps1 adb launch
.\a.ps1 adb log -Tail 400 -Grep "FATAL|ANR|Sxxxx"
```

Prefer this for:
- startup sanity
- crash confirmation
- quick verification of user-visible behavior on a connected device

### 11. noLegal / Chaquopy path

Use the dedicated noLegal wrappers, not the standard fast-check assumptions.

```powershell
.\a.ps1 nd
.\a.ps1 nl
```

Reason: the noLegal graph intentionally avoids configuration-cache reuse.

### 12. KAPT stall recovery

If a targeted Kotlin/unit task hangs around `kaptGenerateStubs...` or `kapt...Kotlin`, recover instead of wiping all caches.

```powershell
pwsh -NoProfile -File scripts/utils/recover-kapt-stall.ps1 -Task ":app_v2:testStandardDebugUnitTest"
```

Use full cache cleanup only as a last resort.

## Escalation ladder

Move upward only when needed:

1. grep-only
2. `.\a.ps1 fk`
3. `.\a.ps1 fr`
4. `.\a.ps1 fc`
5. targeted `-Mode Unit -Tests "..."`
6. `.\a.ps1 fu`
7. `check-standard-fast.ps1 -Mode Assemble`
8. `.\a.ps1 d`
9. device smoke

## Recommended defaults by change type

| Change type | Default proof | Escalate when |
| --- | --- | --- |
| Docs only | grep | never, unless a script/doc generator changed |
| Kotlin-only symbol edits | `.\a.ps1 fk` | packaging or behavior needs proof |
| Resource/layout/manifest only | `.\a.ps1 fr` | mixed code also changed |
| Small UI fix | `.\a.ps1 fc` | behavior depends on runtime flow or packaging |
| Focused logic bug fix | targeted unit test | shared area or many tests affected |
| Shared model / serializer / infra | `.\a.ps1 fu` | install/runtime behavior also changed |
| Packaging/install concern | `.\a.ps1 d` | device-specific behavior matters |
| Wear-only change | `:wear:assembleDebug` | shared app code also changed |

## Anti-patterns

Avoid these habits:

- running `.\a.ps1 d` for every Kotlin change
- running `clean` to "be safe" in normal loops
- using `dav` during normal development
- jumping to the full unit suite before a targeted test
- using standard fast checks for `noLegal` tasks
- wiping all Gradle caches before trying targeted KAPT recovery

## Quick examples

### Change a ViewModel function

```powershell
.\a.ps1 fk
```

### Change a layout and its fragment code

```powershell
.\a.ps1 fc
```

### Fix one repository bug with existing tests

```powershell
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.data.SomeRepositoryTest"
```

### Verify the app still installs and launches

```powershell
.\a.ps1 db
.\a.ps1 adb install -Flavor standard
.\a.ps1 adb launch
```

## Decision summary

Use targeted checks by default, full builds only when the changed surface demands packaging or runtime proof, and full suites only when the blast radius is broad.
