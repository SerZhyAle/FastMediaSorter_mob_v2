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
| `.\a.ps1 flr`  | Fast lint-rules detector test suite (`:lint-rules:test`); `-Tests <filter>` narrows it |
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
.\a.ps1 adb log -Tail 400 -Grep "S0035|Net"  # app's own process lines + lines naming the package
.\a.ps1 adb current                           # focused activity / package
.\a.ps1 adb install -Flavor standard          # install -r -d newest debug APK (or -Apk <path>)
.\a.ps1 adb tap -X 540 -Y 1000                # input tap / text -Text / key -Key
.\a.ps1 adb shell -Cmd "getprop ro.product.cpu.abi"
```

`log` picks lines by process id, so the app's own Timber output survives even though Timber tags
a line with the class name and never with the package (S1332); the package-text arm remains, and is
what keeps the system-side lines about the app. A `WARN` verdict instead of `OK` means the filter
suppressed lines your pattern did match - the full capture under `temp/scratch/` still holds them and
is the fallback. A plain `OK 0 line(s)` therefore now means what it says.

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

### Macrobenchmark and Baseline Profiles (S0722)

```powershell
.\a.ps1 mb
.\a.ps1 gbp
```

- `mb` runs the standard Macrobenchmark suite against the benchmark target.
- `gbp` collects the standard Baseline Profile through the `nonMinifiedRelease` generation flow.
- Wrapper scripts: `scripts/builders/run-standard-macrobenchmark.ps1` and `scripts/builders/generate-standard-baseline-profile.ps1`.
- Expect JSON results and Perfetto traces under `benchmark/build/outputs/connected_android_test_additional_output/<variant>/connected/<device_id>/`.
- See `docs/PERFETTO_PLAYBOOK.md` for thresholds, output interpretation, and Perfetto escalation rules.

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

### KSP incremental is off on purpose - S1375

Symptom, if the setting is ever removed: `:app_v2:kspStandardDebugKotlin` fails and `compileStandardDebugKotlin` never runs, so nothing in `app_v2` compiles.

```text
e: [ksp] java.lang.IllegalArgumentException: this and base files have different roots:
   C:\Users\<user>\.gradle\caches\<ver>\transforms\..\okhttp3-integration-4.16.0-api.jar!\..\GlideIndexer_..class
   and P:\ANDROID\FastMediaSorter_mob_v2\app_v2
```

Cause: KSP2's incremental bookkeeping relativizes every classpath entry against the module directory. On a Windows host whose Gradle cache and project sit on different drives, `Path.relativize` throws on the cross-root pair. Nothing about the touched source matters - the failure lands while walking a dependency jar.

`gradle.properties` therefore carries `ksp.incremental=false`. Do not remove it to "speed builds up":

- KSP1 is not a fallback. `ksp.useKSP2=false` fails at configuration time with `KSP1 is no longer available` - the plugin ships KSP2 only.
- The cost is small and measured: a no-change run stays `UP-TO-DATE` at ~2 s, a one-file edit costs ~24 s. Only the first build after flipping the property pays a full pass (~2 min).
- The line is inert wherever the cache and project share a root (Linux CI, or a same-drive Windows layout).

A same-root layout (`GRADLE_USER_HOME` on the project's drive) also avoids the crash, but that is a machine-specific absolute path - the same reason `org.gradle.java.home` is not committed, see the header of `gradle.properties`.

### Concurrent-agent locks (BUILD.LOCK / CODE.LOCK) - S1338

Two independent locks under `temp/`, both driven through `scripts/utils/agent-lock.ps1`, so two agent sessions in the same working tree do not race each other:

- **`temp/BUILD.LOCK`** - acquired by `Enter-BuildLockOrExit` before any direct `gradlew`/`gradlew.bat` invocation, released by `Exit-AgentLock` after (success or failure). A second gradle-backed invocation while the lock is held refuses outright, reporting the holder's PID, age, and reason instead of racing the daemon.
- **`temp/CODE.LOCK`** - acquired via `scripts/utils/enter-code-lock.ps1 -Reason "<ticket/skill>"` before a multi-file source edit (Kotlin/XML/build-file). Auto-releases from `post-change.ps1`'s closure; a skill that skips the facade (`/skill-fix`) must call `scripts/utils/exit-code-lock.ps1` itself when the edit is done.

Staleness for both locks is judged by PID liveness, never by a guessed timeout while the holder process is still alive - a generous safety-net ceiling exists only for the edge case where the holder process itself is gone without releasing. `CODE.LOCK` is advisory only (Tier 2, no hook enforcement - the agent owns the decision, same model as `dirty-tree-guard.ps1`): a build script that finds it fresh warns but does not refuse, so it cannot deadlock a session that legitimately needs to build while a lock is held for an unrelated edit.

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

**Detekt-clean-first authoring tips (S0826).** Write touched `.kt` to pass this gate on the first build, not the second:
- Keep log/probe lines `<=120` chars (wrap args or shorten) - detekt's line-length rule fires on long `Timber.d(...)` calls as readily as on any other statement.
- Avoid bare numeric literals - reuse `TimeUnit`, a companion `const`, or an existing const; `ignoreNumbers` in the ruleset config only covers -1/0/1/2.
- Never add `@Suppress` to a method that already has a baselined finding - it shifts that finding's baseline signature and can surface a second, unrelated one (e.g. `FunctionNaming`) as a false "new" hit.

**Baseline-drift diagnostic (S1334).** A baseline entry is keyed to the full, whitespace-collapsed text of the code element it froze - if that element's shape changes (a parameter added, an import reordered), the entry silently stops matching. The finding it used to suppress does not disappear: it lies dormant until an unrelated change to the same file trips the diff-scoped gate, which then blames that unrelated ticket. `scripts/quality/audit-detekt-baseline-drift.ps1` surfaces this class of staleness on demand:

```powershell
# Classify every stale entry in the app_v2 baseline against the current detekt report
pwsh -NoProfile -File scripts/quality/audit-detekt-baseline-drift.ps1

# Same, for the wear module
pwsh -NoProfile -File scripts/quality/audit-detekt-baseline-drift.ps1 -BaselineFile config/detekt/baseline-wear.xml -ReportFile wear/build/reports/detekt/detekt.xml
```

Each stale entry prints as `DRIFTED` (the same rule is still live elsewhere in the same file, under a shape this entry no longer covers - a debt that quietly thawed) or `DEAD (prune candidate)` / `DEAD (file removed)` (nothing under that rule is live in the file at all - most likely already fixed, safe to prune after a glance). Diagnostic-only: it never fails a build and never mutates the baseline file - the classification is advisory input for a human decision, not an automated cleanup.

### Listener symmetry ratchet gate - S0721

A lexical ratchet over Kotlin listener ownership: `register*`/`unregister*`, `registerReceiver`/`unregisterReceiver`, and `add*Listener|Callback|Observer` vs the matching `remove*` calls. The gate is deliberately cheap - it scans `app_v2/src/main` + `wear/src/main`, compares the aggregate balance per file, and fails only when the total imbalance grows above the frozen baseline.

```powershell
# Report current count vs baseline
pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.ps1

# PASS/FAIL verdict (wired into post-change.ps1 for Kotlin/Mixed changes)
pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.ps1 -Gate

# Print every unbalanced file with counts
pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.ps1 -List

# Ratchet the committed baseline DOWN after intentional cleanup
pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.ps1 -UpdateBaseline
```

Ratchet model: `scripts/quality/listener-symmetry-baseline.txt` freezes the current debt and blocks only NEW symmetry drift. The gate is a cheap guardrail, not a proof of lifecycle correctness - treat every hit as an audit lead, then confirm the symmetric lifecycle edge in code review or a targeted audit pass.

### Custom Android Lint rules - S0721

An AST-based custom lint checker `:lint-rules` enforcing structural project rules:
- **ActivityLogicViolation**: No business logic / `@Inject` repositories inside Activities.
- **UiContextLeak**: No storage of UI Context (Activity, Fragment, View) in ViewModels or `@Singleton`s.
- **UnsafeFlowCollect**: No lifecycle-unsafe Flow `.collect` calls without `repeatOnLifecycle` or `flowWithLifecycle`.
- **PlayerNotReleased**: Classes holding media players must release them via `release()`.
- **MainThreadIo**: Blocking file/network I/O calls on the main thread in UI / ViewModel classes.

Usage:
```powershell
# Run lint check on standard flavor debug variant
.\gradlew.bat :app_v2:lintStandardDebug

# Run tests of the lint rules module itself
.\gradlew.bat :lint-rules:test
```

### Memory Leak Testing (LeakCanary) - S0721

Instrumented leak detection run on demand using LeakCanary inside instrumented tests:
- **LeakDetectionInstrumentationTest**: Automates UI traversal or lifecycle actions and fails the test run if any memory leaks (retaining Activities, Fragments, etc.) are detected.

Usage:
```powershell
# Run the leak detection instrumented test
.\gradlew.bat :app_v2:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sza.fastmediasorter.leak.LeakDetectionInstrumentationTest
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
| `debug`   | - | - | ✓ | `.debug` | Custom keystore via `debug.keystore.properties`; `LOG_NETWORK_THUMBNAILS=true`; dedicated Dropbox key |
| `staging` | - | - | ✓ | `.staging` | `initWith(release)` - release proguard, shrink disabled; `matchingFallbacks=["release"]` |
| `release` | ✓ | ✓ | - | - | `debugSymbolLevel=FULL`; keystore via `.secrets/keystore.properties` (root fallback supported) |

## FEATURE FLAGS (BuildConfig)

[`docs/FLAVOR_MATRIX.md`](FLAVOR_MATRIX.md) is the canonical, generated answer to "which capability is available in which flavor" - rendered from the `productFlavors` block by `scripts/docs/generate-flavor-matrix.ps1`, together with the machine-readable `docs/flavors/flavor-matrix.json`. The two tables below are a working summary of it and are checked against it cell by cell by `scripts/quality/assert-flavor-matrix-docs.ps1` (in `.\a.ps1 fg` and in `post-change.ps1`), so an inverted marker fails instead of drifting. Change `app_v2/build.gradle.kts`, then regenerate; never fix a disagreement by editing the generated table.

### Core feature matrix

| Flavor           | VIDEO | AUDIO | IMAGES | CLOUD | NETWORK | DOCS | ANIM | STREAMS | VR  |
|:-----------------|:-----:|:-----:|:------:|:-----:|:-------:|:----:|:----:|:-------:|:---:|
| **standard**     | [+]   | [+]   | [+]    | [+]   | [+]     | [+]  | [+]  | [+]     | [-] |
| **lite**         | [+]   | [+]   | [+]    | [-]   | [-]     | [-]  | [-]  | [-]     | [-] |
| **photos**       | [-]   | [-]   | [+]    | [+]   | [+]     | [-]  | [+]  | [-]     | [-] |
| **legacy**       | [+]   | [+]   | [+]    | [+]   | [+]     | [+]  | [+]  | [+]     | [-] |
| **vr**           | [+]   | [+]   | [+]    | [+]   | [+]     | [+]  | [+]  | [+]     | [-] |
| **noLegal**      | [+]   | [+]   | [+]    | [+]   | [+]     | [+]  | [+]  | [+]     | [+] |

`NETWORK` = `SUPPORT_LOCAL_NETWORK` (SMB/SFTP/FTP), `STREAMS` = `SUPPORT_STREAMS`, `VR` = `SUPPORT_VR_PLAYER`. Those two network/streams columns are the pair that defines `lite` and were missing here until S1392; `lite` is the only flavor with neither.

### Extended per-flavor flags

| Flag | std | lite | photos | legacy | vr | noL |
|:-----|:---:|:----:|:------:|:------:|:--:|:---:|
| `SUPPORT_MIC_RECORDING`            | [+] | [-] | [-] | [+] | [+] | [+] |
| `ENABLE_EPUB`                      | [+] | [-] | [-] | [+] | [+] | [+] |
| `ENABLE_TRANSLATION`               | [+] | [-] | [-] | [+] | [+] | [+] |
| `ENABLE_PERSISTENT_AUDIO_PLAYBACK` | [+] | [-] | [-] | [+] | [+] | [+] |
| `SUPPORTS_DEFAULT_PLAYER`          | [+] | [-] | [+] | [+] | [+] | [+] |
| `SUPPORT_WEAR_COMPANION`           | [+] | [-] | [-] | [+] | [-] | [+] |
| `SUPPORT_CAST`                     | [+] | [+] | [+] | [+] | [-] | [+] |
| `SUPPORT_VR_PLAYER`                | [-] | [-] | [-] | [-] | [-] | [+] |
| `VR_UI_COMPOSITION_LAYER_ENABLED`  | n/a | n/a | n/a | n/a | [-] | [+] |
| `IS_NO_LEGAL_FLAVOR`               | [-] | [-] | [-] | [-] | [-] | [+] |

`noL` = `noLegal`. `n/a` means the field is not declared for that flavor at all, so it is absent from its `BuildConfig` and only a flavor-specific source set can reference it - distinct from `[-]`, which is a declared `false`.

`SUPPORT_VR_PLAYER` is true in `noLegal` only. The `vr` flavor declares it `false`: it ships the `src/vr` source set and its OpenXR runtime hooks, but immersive rendering is not wired to the player there yet (epic S0773), so `vr` is the Store-clean shell and `noLegal` is the sideload build where immersive playback works today. Reading the flavor name as the capability is what made this row read as enabled for `vr` until S1392.

Cast is disabled in `vr` (Horizon OS lacks the Google Play Services Cast module); `noLegal` keeps it because it also targets phones/tablets. `SUPPORT_WEAR_COMPANION = true` in `noLegal` is harmless on Quest (no paired watch exists) and meaningful on phones/tablets - runtime decides. VR feature surface in `noLegal` is gated at runtime by `XrDetectionFacade` - VR controls show disabled on devices without an OpenXR runtime. S0250 (2026-05-19) archived the former `vrUnlicensed` flavor; `noLegal` now covers both phone-sideload and Quest-sideload through one APK.

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
These entry points start the immersive Activity through the plain Android launch path,
bypassing the HorizonOS VR shell that recognizes `com.oculus.intent.category.VR`. Without
that shell handoff the Activity's window may never get the compositor focus the native
OpenXR session waits for, so the session can stall at `VISIBLE` instead of reaching
`FOCUSED` - no true immersive VR.

### The real immersive host: `DiagnosticXrActivity`

There is no panel/VR task-affinity split in the current architecture. `MainActivity` is
the ordinary 2D panel - it carries no VR-specific category and stays on the app's default
task. The dedicated immersive host is `DiagnosticXrActivity`
(`app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`, declared in
`app_v2/src/vr/AndroidManifest.xml`):

- `android:launchMode="singleTask"`, `android:exported="true"`, `android:screenOrientation="landscape"`.
- Intent-filter: `android.intent.action.MAIN` + `com.oculus.intent.category.VR` +
  `android.intent.category.DEFAULT`. The VR category is the HorizonOS hint to launch in
  headset mode - there is no `android:taskAffinity` override on this Activity.
- Entry is explicit: `XrEntryGatewayImpl` / `StartVrPlaybackUseCaseImpl` (`core/xr`,
  vr/noLegal source set) build an `Intent(appContext, DiagnosticXrActivity::class.java)`,
  add `FLAG_ACTIVITY_NEW_TASK` (required because the launch runs from the Application
  context, not an Activity), and call `startActivity`. Triggers: the player's VR entry
  badge, Browse's "Open in VR Cinema" (S0962), and the "Test Immersive" button in Settings.
- Exit is a `CATEGORY_HOME` + `PendingIntent` handoff back to the panel
  (`MainActivity`/`SettingsActivity`), built inline in
  `DiagnosticXrActivity.returnToSettingsTaskOrFinish`, followed by `finish()`.

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

Menu → Library → *Unknown Sources* → `FastMediaSorter (VR debug)` → tap. HorizonOS launches `MainActivity` as a 2D panel; tapping "Test Immersive" (or a VR-target file) fires the XR entry gateway, which starts `DiagnosticXrActivity` directly.

#### 3. Attach debugger (optional)

Android Studio → `Run → Attach Debugger to Android Process` → select `com.sza.fastmediasorter.debug` (the `vr` flavor has no `applicationIdSuffix` - it shares the debug package with `standard`, per the S0232 applicationId policy above). Breakpoints, variable inspection, evaluate expression - all work against the shell-launched process.

#### 4. Live logcat (optional, run before the tap on headset)

```powershell
adb logcat -s DiagnosticXrActivity DiagnosticXrRenderThread S0249.XrSession S0249.JniBridge OpenXR_SessionImpl VrRuntimeClient
```

`S0249.XrSession` / `S0249.JniBridge` are our own native tags; `OpenXR_SessionImpl` /
`VrRuntimeClient` come from the Meta/HorizonOS OpenXR runtime itself - both matter when a
session fails to reach FOCUSED. Android Studio's `package:mine` logcat export drops all of
these (immersive playback runs in native threads and the per-entry Activity is
`finish()`-ed, so the pid looks dead to the package filter) - capture with raw
`adb logcat -b all -v threadtime` instead.

### Verifying FOCUSED is reached

The native session logs state transitions under `S0249.XrSession` as
`session state -> <N>` - a raw `XrSessionState` integer, not its symbolic name. Per the
OpenXR 1.0 spec: `IDLE=1`, `READY=2`, `SYNCHRONIZED=3`, `VISIBLE=4`, `FOCUSED=5`. A healthy
immersive entry climbs `1 -> 2 -> 3 -> 4 -> 5`.

If the state sticks at `1` (`IDLE`, never reaching `2`), or logcat shows
`OpenXR_SessionImpl: xrCreateSession: Activity is not yet in the ready state` or
`VrRuntimeClient: Failed to get window type`, either the Activity did not go through the
VR shell path, or you are looking at the immersive re-entry bug fixed in S0607 (repeat
entries reusing an `XrInstance` bound to an already-`finish()`-ed Activity). Dump
activities with:

```powershell
adb shell dumpsys activity activities
```

### Historical note

The predecessor to `DiagnosticXrActivity` extended the same `PlayerActivity` as the 2D
panel, so it needed a `${applicationId}.vr` task-affinity split plus a dedicated
`VrTaskTransition` handoff helper to keep the compositor from seeing a 2D window inside the
VR task. Both are gone: `VrTaskTransition` was removed in S0251, and the old immersive host
was replaced by the standalone `DiagnosticXrActivity` in S0282. The new host never shares a
task or an Activity class with the panel, so the affinity split is no longer needed - do
not resurrect it.

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
