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

# VR - one builder only; debug, AAB and install go through Gradle and adb.ps1 (see below)
.\scripts\builders\build-vr-release.ps1                 # release APK | alias: .\a.ps1 vr

# RELEASE AAB (standard, for Google Play)
.\scripts\builders\build-aab-release.ps1                # alias: .\a.ps1 r

# WEAR OS
.\gradlew.bat :wear:assembleDebug

# DIRECT GRADLE (any flavor×buildType combination)
.\gradlew.bat :app_v2:assembleStandardDebug
.\gradlew.bat :app_v2:assembleStandardRelease
.\gradlew.bat :app_v2:assembleLiteDebug
.\gradlew.bat :app_v2:assemblePhotosDebug
.\gradlew.bat :app_v2:assembleLegacyDebug
.\gradlew.bat :app_v2:assembleVrDebug
.\gradlew.bat :app_v2:assembleVrRelease
.\gradlew.bat :app_v2:bundleVrRelease                            # AAB for Meta Horizon Store
.\gradlew.bat :app_v2:assembleStandardStaging                    # staging = minified but debuggable
```

## a.ps1 SHORTCUTS

| Alias | Action |
|:------|:-------|
| `.\a.ps1 r`    | Build standard AAB release |
| `.\a.ps1 vr`   | Build VR release APK (the only VR alias - debug and install go through Gradle and `adb.ps1`) |
| `.\a.ps1 d`    | Fast reusable debug build (standard) |
| `.\a.ps1 db`   | Fast reusable debug build, skip zip |
| `.\a.ps1 dav`  | Debug build with timestamped app version |
| `.\a.ps1 fk`   | Fast Kotlin compile check (standard; add `-Flavor <name>` for any other) |
| `.\a.ps1 fr`   | Fast resources/manifest check (`-Flavor` applies) |
| `.\a.ps1 fc`   | Fast code + resources check (`-Flavor` applies) |
| `.\a.ps1 fu`   | Fast full unit-test suite (**`app_v2` only**) |
| `.\a.ps1 fw`   | Fast Kotlin compile check, **`wear` module** |
| `.\a.ps1 fwr`  | Fast resources/manifest check, **`wear` module** |
| `.\a.ps1 fwu`  | Fast unit-test suite, **`wear` module** |
| `.\a.ps1 flr`  | Fast lint-rules detector test suite (`:lint-rules:test`); `-Tests <filter>` narrows it |
| `.\a.ps1 dc`   | Clean + debug build |
| `.\a.ps1 cls`  | Clean Gradle caches |
| `.\a.ps1 ss`   | Show unresolved specs (`sca-specs`) |
| `.\a.ps1 adb <verb>` | Ad-hoc adb swiss-army passthrough (see DEVICE OPS below) |
| `.\a.ps1 adb-devices` / `adb-shot` / `adb-log` / `adb-current` / `adb-launch` / `adb-logcat-clear` | Fixed-verb device shortcuts |

## DEVICE OPS (ad-hoc)

`scripts/devtest/adb.ps1` is the quick swiss-army for one-off work against a connected
emulator / device - runs natively (~0 LLM tokens), auto-discovers adb (not on PATH),
takes `-DeviceId` / `-Release` / `-Package` / `-OutDir` / `-Json`, and uses stable exit codes
(0 ok / 1 no-adb-or-bad-args / 2 no-device / 3 multi-device / 4 pkg-not-installed /
5 destructive verb refused / 6 pull: no such remote path / 7 adb-failed / 8 `tap-label` / `tap-id`:
the target is not on screen and nothing was tapped / 9 clip-check: content off-glass).

**Two verbs are one-way and both require `-Yes`: `wipe-data` and `uninstall`.** The verb that used to be
called `clear` is gone - it was twice read as "clear the log" and wiped app data instead (S1167, S1572), so
`clear` now refuses and names its two replacements. "Clear the log" is `logcat-clear`.

```powershell
.\a.ps1 adb devices                          # online devices: model + Android version
.\a.ps1 adb props                             # selected device: model, release, sdk, density, size
.\a.ps1 adb launch                            # start app (debug: explicit MainActivity, dodges LeakCanary)
.\a.ps1 adb stop                              # force-stop
.\a.ps1 adb logcat-clear                      # empty the logcat buffer (no app state touched)
.\a.ps1 adb wipe-data -Yes                    # DESTRUCTIVE pm clear: data, grants and onboarding gone
.\a.ps1 adb shot                              # screenshot -> temp/
.\a.ps1 adb log -Tail 400 -Grep "S0035|Net"  # app's own process lines + lines naming the package
.\a.ps1 adb current                           # focused activity / package
.\a.ps1 adb install -Flavor standard          # install -r -d newest debug APK (or -Apk <path>)
.\a.ps1 adb tap -X 540 -Y 1000                # input tap / text -Text / key -Key
.\a.ps1 adb swipe -X 900 -Y 1200 -X2 200 -Y2 1200   # scroll or page: -Duration ms (default 300)
.\a.ps1 adb uidump -Grep "Settings|Media"     # labels, ids, bounds and tap points from the node tree
.\a.ps1 adb uidump -Ids                       # also list the nodes that carry only a resource-id
.\a.ps1 adb tap-id -ResourceId rowExport      # tap by resource-id; -Exact, -Index N; exit 8 if absent
.\a.ps1 adb tap-label -Label "Media Types"    # tap by label; -Exact, -Index N; exit 8 if absent
.\a.ps1 adb clip-check                        # content leaving the display shape; exit 9 on a defect
.\a.ps1 adb shell -Cmd "getprop ro.product.cpu.abi"
```

### `install` refuses a module/device mismatch (S2043)

`install` reads the selected device's `ro.build.characteristics` and refuses (exit 1, nothing
installed) when `-Module` disagrees with what it finds - a phone-flavored `-Module app_v2`
install against a device reporting `watch`, or `-Module wear` against one that does not. Both
modules publish under one `applicationId` (S1681), so before this guard the wrong `-Module`
silently replaced whichever app was already on that device and `install` still reported success.
`-Module wear` also only ever auto-resolves the RELEASE apk directory - a debug watch build
needs an explicit `-Apk`.

### Tapping by label, and what clip-check calls a defect (S1847)

`tap -X -Y` needs a coordinate, and a coordinate goes stale the moment the list under it scrolls -
in one wear sweep that put two taps on the row next to the intended one. `tap-label` takes the dump
and the tap in the SAME call, so there is no window for the screen to move, and when the label is
not on screen it exits **8** without tapping anything rather than guessing.

**Prefer `tap-id` when the element has a resource-id (S1879).** A label is translated and a
`resource-id` is not, so a call written against the label works on the locale the dump was taken on
and returns 8 on every other one - the same script, the same element, a different phone. `tap-id
-ResourceId <name>` takes the short name straight from the layout (`-Exact` also accepts the full
`<package>:id/<name>`, and matching is a case-insensitive substring by default, so `rowExport` also
reaches `rowExportAll` - pass `-Exact` when one name is the beginning of another). `uidump` prints
the identifier beside every label, and `uidump -Ids` additionally lists the nodes that carry no
label at all - a switch or an icon with nothing but an id was invisible to the tool before S1879.
`tap-label` stays correct where there is no id to aim at, which is most of Compose on the watch.

`clip-check` reads the glass outline from the device (`mRoundedCorners` in `dumpsys window
displays`), so the round watch (radius 240 on 480x480 - a circle) and the phone (radius 105 on
1080x2340 - a rounded rectangle) are one rule with no hardcoded geometry. It classifies rather than
alarms, because uiautomator reports bounds already clipped to the screen and the naive "did the box
leave the circle" test fires on every list head and tail:

- `EDGE` - the viewport cut it; this frame says nothing about the element's real extent.
- `CLIPPED` - it has a scrollable ancestor and would fit at the vertical centre. Normal.
- `OFF-GLASS` - no scroll position saves it. The only class with an exit code (**9**).

Only leaf nodes are judged: a container's box is the extent of a group, not of anything visible, and
the launcher's home-screen container was the first thing the verb called a defect on a normal phone.

`log` picks lines by process id, so the app's own Timber output survives even though Timber tags
a line with the class name and never with the package (S1332); the package-text arm remains, and is
what keeps the system-side lines about the app. A `WARN` verdict instead of `OK` means the filter
suppressed lines your pattern did match - the full capture under `temp/scratch/` still holds them and
is the fallback. A plain `OK 0 line(s)` therefore now means what it says.

Run `.\a.ps1 adb` (no verb) for the full verb list. Direct form:
`pwsh -NoProfile -File scripts/devtest/adb.ps1 <verb> [options]`. This is the manual-work
layer; `mobile-mcp` drives agent UI walks, Maestro runs repeatable flows
(`scripts/devtest/maestro/`), `device-ready.ps1` is the test-skill pre-flight.

### Camera WYSIWYG sweep, and the lens-pin switch (S1988)

`scripts/devtest/camera-wysiwyg-sweep.ps1` drives the in-app camera and asks, per cell, whether the
saved photo shows what the viewfinder showed. It refuses to answer on a dark or featureless scene
rather than returning a confident number derived from noise, so shoot a lit textured one.

`-NoPhysicalLensPin` measures with `Camera2Interop.setPhysicalCameraId` skipped, leaving the sub-lens
to the logical camera. **A run with it means nothing on its own.** It exists to separate strategic
S1988 §2.4's two surviving causes, and both of them fit every measurement taken so far equally well:
either CameraX computes the crop against the logical camera's sensor rectangle while both streams come
off the sub-sensor, or the device's HAL simply previews one field and saves another. Only the same
scene shot twice - once with the switch, once without - tells them apart, so plan a paired run.

Two properties of the switch are worth knowing before reading a report:

- **Debug builds only.** The receiver lives in `src/debug` (`CameraTestHooks.ACTION_LENS_PINNING`), so
  a release build has no such class and `CameraTestHooksBridge` turns every call into a no-op. The
  sweep checks for the receiver's distinctive ack code and reports `SKIP` for a cell nobody answered,
  because an unacknowledged cell is an ordinary pinned shot and reading it as the experiment would
  answer §2.4 with the wrong run.
- **Sent per cell, not once per run.** The sweep force-stops the app between shots, and the receiver
  is registered by the resumed activity, so the flag dies with the process. Each row records
  `lens_pinned` and `photo_file` for exactly that reason - a saved report cannot be mistaken later for
  the other half of the pair, and the photo's pixel size is the only observable that says whether the
  high-resolution mode was in play (the app derives that flag from the selected photo size, so nothing
  can read it back out).

## TEST & VERIFY

```powershell
# FASTEST PROOFS
.\a.ps1 fk                      # Kotlin/Java symbol changes
.\a.ps1 fr                      # XML/resources/manifest/navigation changes
.\a.ps1 fc                      # Small mixed code + resource changes

# PER-FLAVOR PROOF - all six flavors, no dedicated letter needed
.\a.ps1 fc -Flavor Lite         # also: Standard | NoLegal | Photos | Legacy | Vr
.\a.ps1 fc -Flavor Legacy       # covers minSdk 23
.\a.ps1 fc -Flavor Vr           # the only check that compiles src/vr

# WEAR MODULE - fk/fr/fc/fu never look at it, they exit 0 having checked app_v2
.\a.ps1 fw                      # Kotlin changes under wear/
.\a.ps1 fwr                     # resources/manifest changes under wear/
.\a.ps1 fwu                     # unit tests under wear/src/test

# UNIT TESTS
.\a.ps1 fu
.\gradlew.bat testStandardDebugUnitTest

# TARGETED UNIT TESTS
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "com.sza.fastmediasorter.SomeClassTest"

# LINT
.\gradlew.bat :app_v2:lintStandardDebug
```

### Preferred local validation ladder

1. `.\a.ps1 fk` for Kotlin-only symbol edits.
2. `.\a.ps1 fr` for resource / manifest edits.
3. `.\a.ps1 fc` for small mixed edits.
4. `pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Unit -Tests "..."` for focused logic changes.
5. `.\a.ps1 fc -Flavor <name>` per affected flavor when a change touches flavor-visible resources or flavor source sets. This is what satisfies a spec demanding proof on "every affected variant" - all six flavors are reachable and each call takes `BUILD.LOCK`, so the requirement never needs a direct `gradlew` call or a deferral (S1589; S1568 deferred it only because the flag was undocumented).
6. `.\a.ps1 d` only when you need APK packaging / installable artifact proof.

**Pick the rung by module first, not by change type (S1807).** Every rung above checks `app_v2`. A change under `wear/` is proved by `.\a.ps1 fw` (Kotlin), `.\a.ps1 fwr` (resources/manifest) and `.\a.ps1 fwu` (unit tests); the phone target exits 0 without compiling a single watch file, so quoting it under a wear ticket records a verdict about the other module. A change touching both modules needs one rung from each column.

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

### Streams-catalog performance checkpoints (S1502)

Five checkpoints measure the streams screen against a full-size catalog. They are ad-hoc measurements, not a release gate.

```powershell
pwsh -NoProfile -File scripts/devtest/streams-perf-seed.ps1 -Json
pwsh -NoProfile -File scripts/devtest/prerelease-measure.ps1 -Checkpoint streams-open -Json
```

- **Seed first, always.** `streams-perf-seed.ps1` loads the shipped catalog (`delivery/stream-catalog/streams.csv`, ~19,855 rows) into the debug package. It pulls the database to the host, applies the rows with `sqlite3`, and pushes it back, so the app must have been launched once for the database to exist. Exit 11 means the table did not reach the expected size.
- `streams-open` - screen open time, read from the system's `Displayed .. StreamsActivity` marker. **Run `adb logcat -c` before opening the screen**, or a previous launch's marker is reported as this run's. `StreamsActivity` is `android:exported="false"`, so it cannot be started from the shell - reach it through the UI, and note the entry only appears once the `enable_streams` setting is on (it defaults to off).
- `streams-peak-memory` - peak RSS from `/proc` VmHWM.
- `streams-search`, `streams-list-scroll`, `streams-grid-scroll` - janky-frame percentage from `gfxinfo`. **Advisory on an emulator** (software render), and worse than advisory when the sample is thin: a burst that renders under 100 frames is reported as `insufficient: true` and is not a number - do not put it in a comparison. Repeats of an identical run have been measured spreading 46-60% on an emulator. A meaningful reading needs a quiet host, a long scroll, and properly floor-tier hardware.
- Compare only against a baseline taken on the **same device**; store both sides as JSON (`-Json`) so the pair is auditable rather than remembered.

### KAPT stall recovery (targeted validation only)

Symptom: `:app_v2:kaptGenerateStubsStandardDebugKotlin` or `:app_v2:kaptStandardDebugKotlin` hangs with no output for several minutes while running a targeted validation command such as `:app_v2:compileStandardDebugKotlin` or `:app_v2:testStandardDebugUnitTest`. The build does not fail, so `build-debug.PS1`'s failure-driven auto-retry does not engage.

Fallback path - abort the stalled invocation, then:

```powershell
# 1. Clean only volatile kapt/kotlin/executionHistory dirs and retry once with --no-daemon.
pwsh -NoProfile -File scripts/utils/recover-kapt-stall.ps1 -Task ":app_v2:testStandardDebugUnitTest"

# 2. Or recover and retry manually (omit -Task to skip the auto-retry).
pwsh -NoProfile -File scripts/utils/recover-kapt-stall.ps1
.\gradlew.bat :app_v2:testStandardDebugUnitTest --no-daemon

# 3. Last resort if the targeted retry stalls again - full wipe (forces a cold rebuild).
.\scripts\builders\clean-gradle-caches.ps1
```

`recover-kapt-stall.ps1` is the targeted scalpel: it stops daemons, removes `app_v2/build/tmp/kapt3`, `app_v2/build/generated/source/kapt*`, `app_v2/build/kotlin`, `app_v2/build/tmp/kotlin-classes`, and `.gradle/<ver>/executionHistory`. `clean-gradle-caches.ps1` nukes everything (`.gradle/`, `build/`, `app_v2/build/`) and is the cold-start option.

### A class the incremental state lost, not a class the sources lack - S2127

Symptom: a Kotlin compile fails on a file in `src/main` that nobody edited, naming a class it "cannot access".

```text
e: .../ui/browse/managers/BrowseManagerInitializer.kt:118:53 Cannot access class 'ReviewRequestManager'.
   Check your module classpath for missing or conflicting dependencies.
e: .../ui/browse/managers/BrowseManagerInitializer.kt:390:42 Unresolved reference 'onSortOperationSuccess'.
```

Every part of it points away from the real cause. The classpath named is correct. The file named is a consumer, not the declaration. Neighbouring files that reference the same type explicitly stay silent, because they were not in the dirty set. And the run flips between red and green depending on what dirtied that set beforehand - a changed `-Pfms.versionCode` regenerates `BuildConfig`, which half the module depends on - which reads as a configuration defect.

Cause: a class whose source file moved between source sets keeps its FQCN and changes its source root. The incremental output then holds no `.class` for it, while the already-compiled binaries of its consumers keep naming it in their signatures. This repo relocates classes into paired source sets as a routine seam technique, so it recurs - S0403 did it for `cast`, `wear` and `playServices` in one ticket.

Handled automatically. `check-standard-fast.ps1` (`fk`/`fkn`/`fc`/`fr`/`fu`) and `build-debug.PS1` (`d`/`db`/`dq`/`dav`) both detect the signature and repeat the run once with `-Pkotlin.incremental=false`; that rebuilds the lost class output and heals the state for later incremental runs too. A run that compiles twice and prints `not a source defect (S2127)` is doing this on purpose.

The repeat is bound to that one signature, so an ordinary compile error still costs a single attempt. A genuinely missing dependency pays one extra compile and then reports its own verdict - it is never hidden.

Reproducing it by hand, if a future case needs confirming rather than repairing:

```powershell
.\gradlew.bat :app_v2:compileStandardDebugKotlin -Pchaquopy.enabled=false -Pkotlin.incremental=false --no-configuration-cache
```

Passing there while the incremental run fails is the proof - same task, same flavor, same configuration, one flag apart.

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

### Concurrent-agent locks, split by domain - S1338, S2109

A coordination resource is a **pair: type plus domain**, not one global word. Both types are driven through `scripts/utils/agent-lock.ps1`, and every domain that exists is declared in one table, `scripts/utils/agent-lock-domains.ps1` - adding a module is a row there, not an edit in each entry point.

| Domain | Covers | Derived from |
| --- | --- | --- |
| `Build.Phone` | gradle work on `app_v2`, all six flavors | the module the entry point builds |
| `Build.Wear` | gradle work on `wear` | the module the entry point builds |
| `Code.Phone` | edits under `app_v2/` | the changed path set |
| `Code.Wear` | edits under `wear/` | the changed path set |
| `Code.Scripts` | edits to `scripts/`, `dev/`, `docs/`, `PLAN/`, `.claude/`, `.github/`, the root agent files and `a.ps1` | the changed path set |

Two sessions contend only where their domains overlap. A watch edit, a phone edit and a scripts edit therefore proceed at the same time, and so do `.\a.ps1 fw` and `.\a.ps1 fk` - measured 2026-08-27 at 12 s wall for both, with no queue wait and no cache-contention message in either log.

**The domain is derived, not declared** (ADR-1). `enter-code-lock.ps1 -Files "<changed paths>"` maps the set through `Resolve-CodeDomainsForPaths`; a gradle entry point derives its domain from the module it already builds (`check-standard-fast.ps1` from `-Module`, now via the registry row in `scripts/utils/gradle-modules.ps1`, so a module with no domain of its own widens to both rather than defaulting to the phone's; `assert-detekt.ps1` from `-Module`, or both domains when it runs without one). `-Domain` exists as an escape hatch and is second-class on purpose: a wrongly declared domain silently removes protection while still looking like working coordination, whereas a wrongly derived one is visible in the file set the call already prints.

**Anything that does not decompose takes the full set** (ADR-2), so the failure direction is over-protection rather than under-protection: a build file in either module or at the root, a path the table does not recognise, a module added later, or a call that names no file set at all. A module's own `build.gradle.kts` deliberately belongs to the full set rather than to its module - the configuration phase processes every subproject, so a broken build file in one module fails a check requested for the other. The shared static-analysis config (`gradle/`, `lint-rules/`, `config/detekt/detekt.yml` and its siblings) is judged the same way; the per-module detekt **baselines** are the one carve-out, because `baseline-app_v2*` and `baseline-wear*` are named for their module and read by that module's check alone. That carve-out is not cosmetic: regenerating a baseline is a by-product of most Kotlin closures, so failing closed on it bought no protection and silently cost the split on the majority of tickets - observed 2026-08-31, a one-file `app_v2` edit plus its baseline took all three code domains. Over-protection is the safe direction to be wrong, but only where it protects something.

**Multi-domain work is all-or-nothing, in canonical order.** A set is taken in the table's fixed rank, and a domain that cannot be taken releases every domain already taken in that call. Both halves matter: a hand-picked order lets two overlapping sets block each other with no timeout to break it, and a caller left holding half a set blocks every overlapping session for the whole length of its own wait. A multi-domain waiter is granted only when its ticket is head in **every** domain of its set - head in one and second in another is exactly the state that livelocks two overlapping waiters.

**State written before the split is honoured** (strategic 3.2). Coordination files outlive a session, so a sibling may hold a pre-split `temp/BUILD.LOCK` or `temp/CODE.LOCK` at the moment the split lands. Those files name no domain, so the only safe reading is the widest one: a pre-split lock holds **every** domain of its type until its owner releases it or today's rules judge it stale, and a ticket left in a pre-split queue is a place in every domain of its type, ordered by its original sequence number. The first time such a file is honoured in a process, it says so on one line. Releasing one is the other half of the same rule and just as necessary - adoption that blocks without releasing converts every in-flight holder into a stall that only the staleness window ends - so a **bare** name releases the pre-split file of its type, while a single domain never does, because that file covers domains the caller did not take.

The two types, and how each is taken:

- **Build domains** - acquired by `Enter-BuildLockOrExit -Domain <..>` before any direct `gradlew`/`gradlew.bat` invocation, released by `Exit-AgentLock -Name Build -Domains <..>` after (success or failure). A caller that names no domain still takes both, so a script nobody has taught its module keeps serialising exactly as it did before the split. Since S1432 a busy domain **queues** the caller instead of refusing: it takes a ticket, reports its position and starts when its turn comes. Pass `-NoWait` (or set `FMS_LOCK_NO_WAIT=1`) where an immediate answer matters more than a turn.
- **Code domains** - acquired via `scripts/utils/enter-code-lock.ps1 -Files "<changed paths>" -Reason "<ticket/skill>"` before a multi-file source edit (Kotlin/XML/build-file). Since S1432 a busy domain queues the caller and **exits 4** ("queued, not yet your turn") rather than waving the edit through. Auto-releases from `post-change.ps1`'s closure, which frees exactly the domains the run actually holds - the union of what its change set maps to and what this session owns - so a scripts-only closure by a session that took the full set does not leave two domains held for nobody. That release is owner-checked per domain, so it never removes a lock belonging to another live session; a skill that skips the facade (`/skill-fix`) must call `scripts/utils/exit-code-lock.ps1` itself when the edit is done.

**A gradle task name in a repository script carries its module segment** (S2172). Write `:app_v2:assembleStandardDebug`, never `assembleStandardDebug`. This is not a spelling preference: an unqualified name is expanded by Gradle across **every** project in the build that declares it, so its meaning is set by the composition of the build rather than by the script that passes it. When S2090 gave the watch its own `standard` / `noLegal` dimension, forty call sites silently began building the watch as well, and not one of them was edited - measured 2026-08-27, `gradlew assembleStandardDebug --dry-run` scheduled 48 `:wear:` tasks beside 53 `:app_v2:` ones, while `:app_v2:assembleStandardDebug` scheduled none. This is the one way a correctly derived `-Domain` still under-protects, because the domain follows the module the entry point *believes* it builds: the caller holds `Build.Phone` and writes into `wear/build/**`, so a sibling's watch build dies on a locked `R.jar` with an error that reads as broken code rather than as contention. A watch artifact built by a phone task also inherits the phone's `versionCode`. Gate: `scripts/quality/assert-qualified-gradle-tasks.ps1`, in the fast-gates batch and so in every closure. S2175 extended the same gate to `.github/workflows/*.yml` - the CI workflows called `gradlew` with the identical unqualified shape, and a `.ps1`-only scanner could not see it.

**Releasing a wedged lock:** `..ps1 ub` (build) and `..ps1 uc` (code) are the launcher shortcuts for `scripts/utils/clear-agent-lock.ps1`. Both are conservative - a lock whose holder is still live is refused, and the holder's pid, age, reason and session id are printed instead, because clearing it would hand the turn to the next agent mid-edit. `..ps1 uc -Force` overrides once the holder is confirmed gone (check the session's transcript mtime, not the pid - a code-domain pid can be recycled), and drops the whole queue with it, including any ticket your own background waiter is holding.

#### Device leases - S1926

The third contended resource, and the last one to get an arbiter. `adb devices` reports an emulator as online whether or not somebody is mid-run on it, so before this a session discovered the conflict by breaking something: installing its APK, or switching HOME, out from under a running scenario (observed 2026-08-21 in S1895).

```powershell
# Take / give back a specific device
pwsh -NoProfile -File scripts/devtest/device-lease.ps1 -Verb Claim   -Id emulator-5554 -Reason "/spec-test-device S1234"
pwsh -NoProfile -File scripts/devtest/device-lease.ps1 -Verb Release -Id emulator-5554

# Who holds what
pwsh -NoProfile -File scripts/devtest/device-lease.ps1 -Verb Status
```

Exit codes match the ticket lease exactly, because it is the ticket lease's shape rather than the build lock's: **0** done, **1** error, **3** claim lost (a live sibling got there first - take a different device, this is not a fault), **4** release refused (a live foreign session owns it). One file per lease under `temp/DEVICE.LEASES/<serial>.json`, and the claim is an atomic file creation, so two sessions racing for one device cannot both win.

**There is deliberately no queue.** A build finishes on its own in minutes, so waiting for `BUILD.LOCK` terminates; a sibling's device scenario can run arbitrarily long, so waiting for a device does not. A taken device is a reason to defer the device stage, not to block on it.

**Eviction is by session liveness, with no watchdog** - whoever reads next sweeps, matching S1432. The liveness rule itself is not restated in the lease script: it comes from `Get-AgentTicketLiveness`, and the timings from `$Script:AgentLockTimings.Device` (45-minute silence window, matching the ticket lease because a session building and installing an APK writes nothing for a long time; 120-minute absolute ceiling, far below the ticket lease's 480 because a device is held for a scenario rather than for a ticket's whole life).

**The readiness probe consults it only when asked.** `device-ready.ps1 -ClaimFree` walks the online devices and keeps the first it can claim, turning the old `multiple-devices` refusal into a selection; `all-devices-leased` (statusCode 7) is a distinct answer from `no-device`, because "nothing to test on" ends the device stage while "everyone else is on them" means retry later. Without the switch the probe answers exactly as it always has - existing sessions do not change behaviour underneath themselves.

Like every other lock here, this is **advisory**: it coordinates consenting callers and does not stop a raw `adb` command, exactly as `BUILD.LOCK` does not stop a raw `gradlew`.

**The queue (S1432).** Each DOMAIN has its own queue directory `temp/<DOMAIN>.QUEUE` holding one ticket file per waiter, numbered in order. The head of the queue owns the turn: a free lock is **not** enough to acquire, because a live head that has not yet spent its reservation window (5 min for Build, 3 for Code) still owns it - that window is what survives the gap between "your turn" and the moment gradle actually starts. Ownership of a ticket belongs to an agent **session**, not a process. A ticket whose owner has gone quiet, or which passed its ceiling (60 min Build, 20 min Code), is evicted by whoever reads the queue next. Every timing lives in one table, `$Script:AgentLockTimings`.

**Queue fairness and liveness (S1448).** Four rules make the queue actually hand out turns in order, each of them fixing an observed starvation where a session sat still for tens of minutes without a single error:

- **Taking a lock retires every ticket of the acquiring session**, not only the ticket handed to the acquire. Otherwise a session working step by step - take lock, close step, immediately queue for the next one - leaves the previous step's ticket parked on the head *while it holds the lock*, and nobody behind it can ever advance.
- **The turn is decided by ticket identity, never by session identity.** A caller holding no ticket is answered from the lock and the head's reservation; it can no longer inherit the turn just because the head happens to belong to its own session. `enter-code-lock.ps1` therefore takes its place in the queue **before** it asks for the lock, exactly as `Enter-BuildLockOrExit` already did - so a session that releases and immediately wants the lock back queues behind whoever was already waiting. A re-entrant call from a session that already holds the lock is recognised and returns 0 without queueing.
- **A superset request tops up rather than re-queuing, but only in one direction** (S2200). The re-entrancy check above only fired when the requested set was *identical* to what the session already held - a session holding `Code.Wear` alone that then also needs `Code.Phone` fell through to the ordinary acquire path, which has no self-ownership check at all: it saw its own `Code.Wear` lock as "busy" and queued behind it, a wait nothing can ever end from the outside. `Enter-AgentLockDomain` still has no such check; instead `enter-code-lock.ps1` now splits the request into `Held` (already this session's) and `Missing` before touching the queue. Safety of granting `Missing` without releasing `Held` depends on canonical rank, not on self-ownership alone: it is safe exactly when every held domain outranks every missing one (`Code.Phone` < `Code.Wear` < `Code.Scripts`) - continuing upward through the table is equivalent to a fresh multi-domain acquire that already completed its first steps, so it inherits that acquire's deadlock-freedom. The other direction - holding a higher-ranked domain while a lower-ranked one is still missing - is refused outright (exit 4, nothing enqueued) with a message naming the self-collision and the recourse (`exit-code-lock.ps1` then retake the full set), because granting it would let a symmetric session holding the low-ranked domain deadlock against this one. `scripts/utils/agent-lock.ps1`'s `Resolve-AgentLockTopUp` is the single place this split is decided.
- **A waiting ticket carries its own heartbeat.** Liveness reads `lastSeenAt` first (stamped by `wait-for-lock-turn.ps1` on every poll), the owning session's transcript second, the enqueue time last. The transcript alone punished exactly the behaviour the contract demands: a session that queues, backgrounds the waiter and goes off to do lock-free work writes nothing, looked dead at the 15-minute mark, and was evicted from a place it had earned. **An abandoned head does not age out** (S2098, correcting what this line claimed before): `TicketCeilingMinutes` is declared for `Build` and `Code` but read by no queue consumer - only `ticket-lease.ps1` and `device-lease.ps1` apply the field, and `Remove-StaleAgentLockTickets` judges the owner, never the ticket's age. That is deliberate. A legitimate wait behind one long build, or behind several queued builds, outlasts both numbers, so applying them would evict a session waiting exactly as the contract demands - `scripts/utils/test-agent-lock-queue.ps1` asserts that survival. The remedy for a dropped intent is therefore explicit withdrawal, below, not a timer.
- **One head does age out: the one that was told to go and never went** (S2194). `Remove-StaleAgentLockTickets` carries a second, narrow reason to drop a ticket - **forfeit** - and it applies only to a queue **head** whose `turnGrantedAt` is older than that domain's `ReservationMinutes`, which does not hold the lock, and which is not the sweeping session's own. It is not the ticket-age timer the bullet above rules out: it reads `ReservationMinutes`, never `TicketCeilingMinutes` or `SessionStaleMinutes`, and it judges an **already-granted turn** rather than a wait, so a ticket that was never granted one survives any amount of waiting - `test-agent-lock-queue.ps1` asserts both boundaries. Safe because it fires only after the reservation expired, at which point the head holds no privilege anyway: `Test-AgentLockTurn` is already answering "your turn" to whoever asks. Leaving it in place is what costs - every remaining waiter is told to go at once and they race for the lock file, so a later arrival can overtake an earlier one, and every inspector reports a waiter who does not exist.
- **The refusal names the blocker that exists.** A lock that is held reports its holder; a lock that is free while a foreign ticket owns the head says so and names the head's session, reason, wait and reservation window. `enter-code-lock.ps1` no longer prints a `Holder:` line built from an absent lock file - the observed `Holder: session  (age 0s, reason: '')` sent readers hunting for a holder that was not there.

`lock-status.ps1 -Queue` surfaces the pathology directly: each ticket carries `heldByLockHolder`, the JSON payload carries `headOwnedByHolder`, and a text row owned by the current holder is suffixed `<- holds the lock`.

```powershell
# Who holds it, who is waiting, in what order (this session's own ticket is marked '>')
pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build.Wear -Queue
# A bare Build or Code prints one section per domain of the set, each naming its own domain
pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code -Queue -Json

# Wait for your turn OUT OF BAND: run this as a background task and keep working
pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name Code.Phone -Reason "S0900 edit"
```

**All five domains at a glance:** `.\a.ps1 rm` (`scripts/utils/monitor-spec-queue.ps1`) prints one line per domain with its holder and the tickets behind it, collapsing the idle domains into a single `free` line. Two properties are worth knowing before reading it. It **writes nothing** - unlike `lock-status.ps1`, it never evicts a stale ticket, so a queue entry it shows may be one the next acquire would sweep away; that is why every ticket row carries both its wait and its last heartbeat, and a long wait with a cold heartbeat is an abandoned intent, not a working sibling. And it takes the domain names from `agent-lock-domains.ps1` rather than listing them, which is the fix for what S2170 found: the section had kept naming the two pre-split files that nothing writes any more, so it reported "free" while three domains were held.

`wait-for-lock-turn.ps1` takes a ticket, blocks, and **exits** the moment the turn arrives - its exit is the "your turn" signal, which is the only channel through which an external event returns an agent to work. The ticket deliberately survives that exit: the caller inherits it, protected by the reservation window, and passes it to `Enter-AgentLock -Ticket`. Exit codes: **0** granted, **2** timed out, **3** ticket evicted while waiting, **4** could not enqueue. Do not read the verdict from the exit code a background task reports - that is the exit of the last command in the launch line, and it has already turned a refused build into an apparently green one. Read the marker instead: `temp/<DOMAIN>.TURN-<sessionId>.json`, one per domain of the set, carrying `outcome` (`granted` / `timeout` / `evicted` / `enqueue-failed`), the ticket number and how long the wait took.

**Withdrawing a dropped intent (S2098).** The queue has an operation for cancelling your own request, and it is the only remedy for an abandoned ticket:

```powershell
pwsh -NoProfile -File scripts/utils/withdraw-lock-ticket.ps1 -Name Code.Phone   # or: .\a.ps1 uqc / uqb
```

Its three boundaries are what separate it from the two operations it sits next to. It removes **only the calling session's** tickets, so it can never take someone else's place in line. It **never reads or writes the lock file**, so it is safe to run at any moment during another session's edit. And it **refuses (exit 2) when no session id is in the environment** rather than reporting a quiet zero, because without an identity "my ticket" and anyone else's are indistinguishable. Compare: `clear-agent-lock.ps1 -Name <..>` evicts only tickets whose owner is judged gone - which an abandoned ticket's owner is not - and `clear-agent-lock.ps1 -Name <..> -Force` drops the entire queue **plus the lock**, which may belong to a third, actively working session. That distinction is not academic: on 2026-08-27 an abandoned head sat in front of two waiting sessions, the unforced clear declined it, `-Force` would have taken a working session's lock, and the queue was only freed by deleting the ticket file by hand. Withdrawal stays the remedy for a ticket that has **not** been granted a turn; a head that was granted one and never entered is now dropped by the forfeit rule above, so that half of the case no longer needs a hand.

**Re-entrancy.** Several gates run a nested script while already holding `BUILD.LOCK`, and `& other.ps1` executes in the same process - so a nested acquire would queue behind a lock this very run owns. `Enter-BuildLockOrExit` recognises the holder as itself (same pid) or as the ancestor that launched it (inherited `FMS_BUILD_LOCK_HELD_BY`) and reuses the lock instead of waiting.

`Enter-BuildLockOrExit` runs one check before it even reaches the lock (S1425): it resolves the JVM Gradle will run on - `org.gradle.java.home` from the user-level `gradle.properties`, then the repository one, then `JAVA_HOME` - and verifies that `bin/java(.exe)` and `lib/jvm.cfg` both exist under it. If either is missing it prints the resolved path, the missing file and the config file that set it, then **exits 3**: the environment cannot build, which is a different fact from a build that failed (exit 1) and from a wait that timed out (exit 2). Nothing is built and the lock is never taken. The check is two `Test-Path` calls and never launches a JVM, so it costs nothing per build. It exists because a partial Android Studio uninstall deleted `jbr/lib/jvm.cfg` while leaving `jbr/bin/java.exe`: the daemon already running kept compiling from memory, every compile check stayed green, and only forked JVMs failed - the whole unit-test tier was down for hours before anything said so.

**Stale-snapshot repair (S1928).** Before that refusal fires on the launcher JVM, the guard asks a second question: is the *machine* misconfigured, or has only this process's snapshot of `JAVA_HOME` gone stale? An environment variable inside a running process is a snapshot taken at launch, so a JDK point-update leaves a long-lived agent session pointing at a directory that no longer exists while the machine's persisted value is already correct - and because every shell the session spawns inherits that snapshot, every gradle target fails identically until the process is restarted. When the persisted `JAVA_HOME` (User scope, then Machine) exists, differs from the snapshot and passes the same two-file check, the guard updates `$env:JAVA_HOME` for the current process and carries on:

```
JAVA_HOME snapshot was stale - refreshed from the persisted User value.
  was: C:\Program Files\Java\jdk-21.0.10 (missing bin/java(.exe), lib/jvm.cfg)
  now: C:\Program Files\Java\latest\jdk-21
  Only this process was changed. Fix the environment your session inherits, or the next one starts stale too.
```

Three properties make this a refresh rather than a silent JVM swap, and all three are deliberate. It **reads the persisted variable rather than choosing a JDK** - it never scans the disk, never reaches for the Android Studio `jbr`, and can only return a value the operator persisted themselves, which is the very value the stale snapshot is a snapshot of. It is **loud**, printing both values and the scope. It **writes nothing outside the current process** - no `setx`, no registry. When there is nothing to refresh (no persisted value, one equal to the snapshot, or one that is itself unusable) the original refusal and its exit 3 are unchanged. The repair buys the session, not a cure: the environment the session inherits still wants fixing, or the next session starts stale too.

Staleness is judged by the holder's own liveness, never by a guessed timeout while the holder is still working. `BUILD.LOCK` has a real process, so it is judged by PID liveness (with a start-time check against PID reuse). `CODE.LOCK` has no process - an editing turn is not one continuous process - so since S1432 it is judged by its owning **session**: a live owner keeps the lock however long the edit takes, because expiring a working session by the clock would hand its turn to the next agent mid-edit. A lock written before S1432 carries no session id and still expires by wall clock, so old files read correctly. A build script that finds `CODE.LOCK` fresh still only warns - it never refuses - so a session that legitimately needs to build while someone else edits cannot be deadlocked.

A third shared file follows the same family but keys ownership differently (S1396): the round state of `/spec-next` and `/spec-do`. Its owner is an agent session, not an OS process, so PID liveness cannot apply - `scripts/spec_catalog/spec-next-session.ps1` stamps `owner.sessionId` from `CLAUDE_CODE_SESSION_ID` and reads liveness off that session's transcript write time (`-StaleMinutes`, default 45). Every verb warns and writes anyway, the `CODE.LOCK` model. No session id in the environment -> ownership is undefined and all of it is a no-op.

**Parallel picker sessions (S1437).** Two or three `/spec-next` / `/spec-do` sessions now run at once in one working tree. Three things make that safe, and each replaced a different blocker:

- **Round state is per session** - `temp/spec-next-session.<sessionId>.json`, one file each. The old single file's `-Verb Init` refusal (exit 4) is gone; that code is retired and not reused. A pre-S1437 `temp/spec-next-session.json` is adopted into the per-session path on the first `Resume`.
- **A ticket lease stops two sessions working the same ticket** - `scripts/spec_catalog/ticket-lease.ps1`, one file per lease under `temp/SPEC-TICKET.LEASES/`. A claim is an atomic `CreateNew`, so of two sessions racing for one ticket exactly one wins; the loser gets **exit 3**, which is a normal outcome - it re-ranks with that id excluded and takes the next ticket, it does not wait. Release is owner-checked (**exit 4** refuses to free a live sibling's lease). Expiry follows the owning session's liveness with an independent 480-minute ceiling, and a stale lease is swept by whoever reads next - no watchdog, same as the queue. **S1448 widened what counts as alive**, because a preflight once offered S1436 as unleased while the owning session was demonstrably working it: a lease now carries its own `lastSeenAt`, refreshed on every verb its owner runs, and a session holding any code or build domain with a reason naming the ticket id counts as live on that evidence alone - the evidence is scanned across **every** domain, plus the two pre-split names, because after S2109 a session holding `Code.Wear` writes no file under the bare name and a check looking only there would read a working session as abandoned and sweep it. The 480-minute ceiling still judges `claimedAt` and neither signal extends it. `spec-next-preflight.ps1` consumes the lease set as an extra exclusion source and leaves its five sort keys alone, so the owner's release-plan order still decides who gets what.
- **A killed flow leaves its leases behind, and the sweep will not take them for 45 minutes** - deliberately, because that window is sized for a working session that writes nothing while it thinks. `.\a.ps1 ul` (`ticket-lease.ps1 -Verb Clean`) judges on live evidence instead: a lease survives only while a running headless child names its ticket, its owner holds any code or build domain naming it, this session owns it, or its owner's transcript moved within `-QuietMinutes` (2). Everything else is litter and goes, with the reason printed per lease. `-Force` drops the lot. Use it after `.\a.ps1 rs -Kill`, never as a way to take a ticket a sibling is working.

- **Catalog journal writes are serialized** - `Enter-CatalogLock` / `Exit-CatalogLock` (and the `Invoke-CatalogTransaction` wrapper) in `scripts/spec_catalog/_lib.ps1` hold a named system mutex across **read -> mutate -> write** in every mutator, id allocation included. The write was already atomic by temp-file rename; the failure it fixes is the lost update, where two processes hold the same snapshot and the later write silently drops the earlier change. A mutex rather than a lock file because a journal rewrite is milliseconds, and it dies with its process so a crashed holder cannot wedge the catalog.

```powershell
# Who is working what, right now, and when each session was last seen
pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Status
pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Status -Json
# Release-order view with ephemeral ownership for the selected package; it never rewrites PLAN/RELEASE_QUEUE.md.
pwsh -NoProfile -File scripts/spec_catalog/release-queue.ps1 -List -Release 32 -WithLeases
```

**Resuming across a context reset.** A reset gives the resuming agent a *new* session id, so the round it is resuming is always filed under the old one - and to a liveness test that old session looks alive, because its transcript was written seconds ago. Liveness alone therefore cannot tell "just stopped, waiting to be picked up" from "a sibling working right now". `-Verb Handoff` (which the threshold stop already runs) stamps `handoffAt` on the state, and `-Verb Resume` adopts only a round that is either stamped or whose owner has genuinely gone stale. Without that marker resume would either lose the round or steal a sibling's - there is no third answer available.

### Shared-state mutation audit (S0703)

On-demand quality tool, not a build gate. Finds places where one shared object is mutated from several layers (the "last-write-wins" / redundant / unsafe class).

```powershell
# Stage 1 - mechanical candidate harvest (UI view props + data carriers), ranked report + JSON.
pwsh -NoProfile -File scripts/quality/audit-shared-state-writers.ps1 -Surface all -Top 20 -Json temp/shared-state-audit.json
```

`-Surface ui|data|all`, `-Top N`, `-MinWriters N`. Stage 2 hands the JSON plus the agent prompt `scripts/quality/shared-state-audit-prompt.md` to a research agent that adjudicates indirect writers / concurrency and lists survivors as `/spec-draft` candidates.

### Closure facade failure reporting - S1598

`scripts/post-change.ps1` **runs every applicable gate before it gives up**. It used to end the process at the first non-zero child, so a changed set breaking three gates cost three full runs of the facade to discover - 215 failed runs in the week of 2026-08-05, median 8 turns from a failed run to the next one. The tail of a failed run now reads:

```text
post-change: FAIL (2 gate(s), Kotlin)
  failed: ticket-log-audit (exit 1)
      repro: pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1
  failed: neuroslop-gate (exit 1)
      repro: pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1 -Gate -ChangedFiles "<your,files>"
  Nothing was written: no changelog row, no catalog sync. Fix the above and re-run.
```

What did **not** change: exit codes stay `0` passed / `1` a gate failed / `2` could not verify, and a failed run still writes nothing - the barrier sits before `catalog-sync` and `dev-log`, so "there is a changelog row" still means "the closure passed". `detekt-preflight` still suppresses the whole-module `detekt-gate` when it fails, since it already ran the real analyser over the same files; the gate then reports `SKIP` naming the preflight rather than pretending it judged.

Each failed gate prints two extra lines - `repro:`, the command that runs that gate **alone**, and `fix:`, one sentence on what to do with the finding. Both come from `scripts/quality/gate-recovery-hints.psd1`, keyed by the gate label exactly as the facade prints it. Registering a new gate means adding an entry there, never editing the facade's output logic; `scripts/quality/assert-gate-hints-sync.ps1` (in `.\a.ps1 fg`) fails when a label has no entry or an entry names no label, because a missing hint is otherwise invisible until the moment that gate fails.

For Kotlin and XML-resource changes, the unfiltered `neuroslop-gate` is the sole automatic lexical pass for every rule in `source-matchers.ps1`, including `flavor-flags`, `public-mutable-flow` and `deprecated-pm-flags`. Their narrow wrapper commands remain available for direct diagnosis, but the facade must not route them a second time.

`doc-icons-sync-gate` runs only when the changed set includes a document-icon input: `docs/icons/doc-icon-map.json`, generated `docs/icons/doc/` assets, an icon generator, `index*.html`, `docs/howto/index*.md`, `docs/DOCS_MAP.md` or `docs/SETTINGS_REFERENCE*.md`. It is skipped for unrelated documentation edits. Run `pwsh -NoProfile -File scripts/quality/assert-doc-icons-sync.ps1 -Gate` to reproduce a failure; regenerate the assets and checked surfaces named by the report before closing again.

Regenerating those assets needs one Python dependency, and it lives in the repo venv the exporter already looks for (`.venv/Scripts/python.exe`), not on the machine: `.venv\Scripts\python.exe -m pip install -r scripts/docs/lib/requirements.txt`. The rasterizer is `resvg-py`, whose pip wheels carry the renderer compiled in. It replaced `cairosvg` in S1964 for exactly that reason - `cairosvg` has no native code of its own and dlopens a system `libcairo`, which on Windows only exists if GTK or some unrelated application installed it. Nobody ever installed it deliberately, nothing recorded that it was needed, and the day the machine no longer had it the exporter stopped mid-run and blocked a ticket (S1931). Do not go back to a backend that resolves its native half outside `.venv`.

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

**Format vs signal split (S2105) - a read-only view, not a second baseline.** detekt's Gradle plugin
reads exactly one baseline per module - `build.gradle.kts`'s `DetektExtension.baseline` is a single
`RegularFileProperty`, so `config/detekt/baseline-<module>.xml` stays the one file detekt, the ratchet
model above and the S1356 absorption gate all read; nothing about them changed. On top of it,
`scripts/quality/split-detekt-baseline.ps1` derives two committed, read-only VIEW files per module,
classifying every `<ID>` by rule name through `config/detekt/rule-categories.txt` (one
`RuleName<TAB>format|signal` line per rule, the only place the boundary is decided):

```powershell
# Per-category counts, no manual grep through a 2 MB XML
pwsh -NoProfile -File scripts/quality/split-detekt-baseline.ps1

# Regenerate after the operational baseline or the category table changed
pwsh -NoProfile -File scripts/quality/split-detekt-baseline.ps1 -Update -Reason '<why>'
```

- Views: `config/detekt/baseline-<module>-format.xml`, `config/detekt/baseline-<module>-signal.xml`.
  Their combined ID set always equals the operational baseline's exactly - checked by `-Gate`.
- A baseline rule name absent from `rule-categories.txt` fails closed (exit 2), never guesses a category.
- `post-change.ps1`'s `detekt-baseline-split-sync` gate (fatal, mirrors `detekt-baseline-absorption`)
  fires whenever an operational baseline, a view file, or the category table is among the changed
  files - a re-freeze or a hand-edited table without a matching `-Update` FAILs the same closure that
  changed it.
- Shrinking the format debt via batched autocorrect was measured in S2112 and **does not work as a
  campaign** - see "Batched autocorrect: measured and not adopted" below.

**Scoped preflight (S1595) - the cheap step that now decides.** `post-change.ps1` runs
`scripts/quality/detekt-preflight.ps1` before it starts the gradle gate, and since S1595 that step
runs the **real** analyser over only the changed files (`scripts/quality/detekt-scoped.ps1`,
detekt's CLI with the same config, the same `--build-upon-default-config` and the module's own
baseline). Measured 2.1 s for one file, 3.1 s as the `[detekt-preflight]` step; it takes no
`BUILD.LOCK`.

```powershell
# Judge just these files with the real analyser - no gradle, no lock
pwsh -NoProfile -File scripts/quality/detekt-scoped.ps1 -ChangedFiles "a.kt,b.kt"
```

Three outcomes, and the third is the one that matters:

- **exit 0** - the analyser ran and found nothing new in those files.
- **exit 1** - it ran and found something; every finding prints with rule, line and message, and
  the step is FATAL, so the closure stops before the ~87 s gradle gate is even started.
- **exit 2 - could not verify.** The analyser is assembled from the gradle dependency cache, so a
  version bump can break it. The preflight then prints a `DEGRADED` banner, falls back to its old
  three-rule lexical scan, and **exits 0 whatever that scan finds** - a lexical guess must never
  abort a closure. The gradle gate still runs behind it and still decides.

Why it replaced the lexical emulation: measured over the transcript corpus, the three hand-written
rules fired on 35.7% of attributable gate failures and fully covered 13.9%, so 86% of failures paid
the round-trip anyway; nine hand-listed rules would reach only 48.1%; and the size rules cannot be
reproduced lexically at all. Evidence in `PLAN/S1595_detekt-preflight-coverage-gap/research/`.

**Detekt-clean-first authoring tips (S0826).** Write touched `.kt` to pass this gate on the first build, not the second. The preflight above now names any violation in seconds, so these are about not writing one in the first place:
- Keep log/probe lines `<=120` chars (wrap args or shorten) - detekt's line-length rule fires on long `Timber.d(...)` calls as readily as on any other statement. Note that a long line trips **two** rules, `style:MaxLineLength` and ktlint's `MaximumLineLength`, and neither can be auto-corrected: no rule in this stack reflows a line.
- Avoid bare numeric literals - reuse `TimeUnit`, a companion `const`, or an existing const; `ignoreNumbers` in the ruleset config only covers -1/0/1/2.
- Keep functions to at most two `return` statements. `ReturnCount` was the second-largest cause of gate failures in the S1595 corpus (22) and is invisible to the old lexical scan.
- Put each argument on its own line once a call does not fit one line - `ArgumentListWrapping` was the fourth-largest cause (15), and one wide call typically produces several findings at once.
- Never add `@Suppress` to a method that already has a baselined finding - it shifts that finding's baseline signature and can surface a second, unrelated one (e.g. `FunctionNaming`) as a false "new" hit.

**Baseline-drift diagnostic (S1334).** A baseline entry is keyed to the full, whitespace-collapsed text of the code element it froze - if that element's shape changes (a parameter added, an import reordered), the entry silently stops matching. The finding it used to suppress does not disappear: it lies dormant until an unrelated change to the same file trips the diff-scoped gate, which then blames that unrelated ticket. `scripts/quality/audit-detekt-baseline-drift.ps1` surfaces this class of staleness on demand:

```powershell
# Classify every stale entry in the app_v2 baseline against the current detekt report
pwsh -NoProfile -File scripts/quality/audit-detekt-baseline-drift.ps1

# Same, for the wear module
pwsh -NoProfile -File scripts/quality/audit-detekt-baseline-drift.ps1 -BaselineFile config/detekt/baseline-wear.xml -ReportFile wear/build/reports/detekt/detekt.xml
```

Each stale entry prints as `DRIFTED` (the same rule is still live elsewhere in the same file, under a shape this entry no longer covers - a debt that quietly thawed) or `DEAD (prune candidate)` / `DEAD (file removed)` (nothing under that rule is live in the file at all - most likely already fixed, safe to prune after a glance). Diagnostic-only: it never fails a build and never mutates the baseline file - the classification is advisory input for a human decision, not an automated cleanup.

**Removing a dead entry (S2112) - `prune-detekt-baseline.ps1`, and NOT a re-freeze.** The diagnostic
above names dead entries; this is the tool that deletes them. It exists because detekt's own answer -
`:<module>:detektBaseline` - re-freezes the whole module and cannot tell "this finding was fixed"
from "this finding is new", which is exactly how the 2026-08-02 absorption incident happened
(`assert-detekt-baseline-absorption.ps1`, S1356). **A whole-module re-freeze is the wrong tool for
removing a dead entry; reach for it only when you mean to accept new debt deliberately.**

```powershell
# Report what is dead for these files - writes nothing
pwsh -NoProfile -File scripts/quality/prune-detekt-baseline.ps1 -Module app_v2 -Files "a.kt,b.kt"

# Delete those entries
pwsh -NoProfile -File scripts/quality/prune-detekt-baseline.ps1 -Module app_v2 -Files "a.kt,b.kt" -Apply -Reason '<why>'
```

It runs detekt's CLI over the named files with `--create-baseline`, which emits IDs in the
operational baseline's exact vocabulary, and subtracts the two sets. The contract is one-directional:

- **exit 0** - reported, or the dead entries were deleted. Deletions only; every surviving line is
  copied verbatim, so the diff is `N deletions, 0 insertions`.
- **exit 1** - the named files carry a finding the baseline does not hold. Every one is printed and
  **nothing is written**. The script has no code path that adds an `<ID>` at all, so absorbing debt
  here is impossible rather than merely forbidden.
- **exit 2** - could not verify. Note that detekt writes no baseline file when it finds nothing, so
  the run also requests a Checkstyle report and reads *its* presence as "the analyser ran" - without
  that, a dead analyser and a clean input set look identical and the prune would delete everything.

The input set is silently widened to every `.kt` in the module sharing a name with a named file: a
baseline ID carries `Rule:FileName$signature` with no directory, and 329 of app_v2's format entries
sit on names that occur in more than one source set. After a prune, regenerate the derived artifacts
in the same closing wave - `split-detekt-baseline.ps1 -Update` and
`assert-detekt-baseline-absorption.ps1 -Update` - or the split-sync gate fails the closure.

**Batched autocorrect: measured and not adopted (S2112).** The obvious use of the tool above is a
campaign - autocorrect a package, prune what died, repeat over the module. That was measured on
`core/util` (36 files, 120 format entries) on 2026-08-27 and the package had to be reverted. Three
things came out of it, and all three generalise:

- **Autocorrect is not idempotent.** Three passes were needed; pass 1 itself manufactured 18
  `NoSemicolons` findings by splitting calls across lines. Anything written as "correct once, then
  compile" is wrong by construction.
- **Wrapping relocates line-length debt, it does not remove it.** `ArgumentListWrapping` lifts a long
  string literal out of a `Timber.x(..)` call onto its own line, where it is still over 120
  characters but under a new signature - so the frozen `MaxLineLength` entry stops matching and the
  same debt returns as a *new* finding. Eight of the nine irreducible survivors were this. Since
  `MaxLineLength` + `MaximumLineLength` are 33% of the format baseline and no rule in this stack
  reflows a line, that third is not reachable by autocorrect at all.
- **A format-only pass is not format-only.** The ninth survivor was `ComplexCondition`, a *signal*
  rule whose baseline signature the reformat invalidated.

Cost, for the record: +146 lines (+2.7%, worst file +14.1%), no `LargeClass` crossing in that
package, and zero baseline entries retired. Full measurement:
`PLAN/S2112_shrink_detekt_format_baseline/research/03__autocorrect-price-report.md`. Whether to
continue in some other shape is an open owner decision, not a settled plan.

**The format step may only touch a file it improves (S2116).** `post-change.ps1`'s `detekt-format`
step is `detekt-scoped.ps1 -Fix`, and until 2026-08-27 it ran ktlint auto-correct over every file
in the closure's set unconditionally and never judged what it left on disk. Combined with the two
properties above - a wrap breaks the baseline signature, and no rule reflows an over-long string
literal - that made a closure that cannot converge: measured on S2104, 74 findings over 54 files a
judge run had called clean seconds earlier, identical across three consecutive `post-change.ps1`
runs, and reproduced in isolation on one file (`PASS`, 0 findings, then `FAIL`, 4 findings, after
`-Fix`). Since S2116 the mode is three passes:

- **Judge the whole set first.** A file with no finding is never handed to the corrector, so a clean
  set costs exactly one analyser pass and every file stays byte-identical. This is the common case,
  and it is also the case that produced the defect.
- **Correct only the files that carry a finding**, after snapshotting each one byte for byte.
- **Re-judge those files and restore any whose finding count grew**, naming the file and the rules
  that made it worse. `-Fix` still always exits 0: the verdict belongs to the preflight behind it.

The overlay `config/detekt/format-autocorrect.yml` was deliberately *not* narrowed to a denylist of
wrapping rules - a hand-kept list would need extending on every ktlint bump, while judging the
result catches a rule that does not exist yet. Contract tests for all three passes:
`scripts/quality/detekt-scoped.tests/Run-Tests.ps1` (cases F, G, H).

### Resource-link gate - S1915

Prints as `resource-link-gate`. The only gate in the closure facade that runs aapt. It fires when the changed set carries a resource or a manifest (`$isResourceChange`, so a Kotlin-only or docs-only closure skips it and pays nothing) and links those resources for every variant the set touches.

```powershell
# What the gate runs, one call per selected flavor - also the fix loop when it goes red
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Resources -Module app_v2 -Flavor Standard

# The same thing by its launcher shortcut
pwsh -NoProfile -File ./a.ps1 fr
```

**Module selection - derived, never declared (S2121).** The gate ignores `post-change.ps1 -Module` and resolves the modules from the changed resource paths themselves, through the registry in `scripts/utils/gradle-modules.ps1`. `-Module` defaults to `app_v2` and nothing corrected it, so a ten-file change lying entirely under `watchface/` linked `:app_v2:processStandardDebugResources` and printed PASS - a verdict about a module the change never touched, which is worse than no gate because it looks like one that fired. A set spanning two modules links both, in the registry's own order.

**Adding a module means adding a registry row.** A resource path under a directory the registry does not know fails the gate by name and links nothing; the gate never guesses a task name, because a guess either fails with a worse message than that refusal or silently passes about a variant nobody chose. The registry records the three facts a task name needs: the module's flavors, its build types, and whether it has any resource-processing task at all. Only `lint-rules` has none - it is a pure `kotlin("jvm")` project with no Android plugin - so it alone is named and skipped rather than linked.

**Build types are per-module too (S2123).** A variant name is flavor plus build type, and until S2123 only the flavor half lived in the registry; the other half was a `ValidateSet("Debug", "Release")` on the builder's `-BuildType`, which is a claim about every module and false for one. `:benchmark` declares neither: the `androidx.baselineprofile` plugin gives it exactly `nonMinifiedRelease` and `benchmarkRelease`, and since it carries no flavor dimension the whole task-name segment is the build type. That is why S2121 measured `:benchmark:processDebugResources does not exist` and recorded `LinksResources = $false` - the task *name* was unbuildable, not the module unlinkable. Measured 2026-08-27, the real tasks run green and cheap: `:benchmark:processNonMinifiedReleaseResources` in 2.4 s and `:benchmark:processNonMinifiedReleaseManifest` in 1.4 s, neither needing `:app_v2` to build. The gate now reads each module's default build type - its first declared one, still `Debug` for `app_v2`, `wear` and `watchface` - from the registry and prints it beside the module before running.

**Variant selection.** `src/main` and every non-flavor source set ship inside the default variant, so the module's first declared flavor is always linked; a path under `src/<flavor>/` adds that flavor on top, deduplicated, and only paths inside that module's own directory may select one. A resource under `src/vr/res` linked only as `standard` would be judged by a variant that never sees the file - the same false green S1807 found when a phone target was quoted as proof under a wear change. A module with no flavor dimension answers with an empty set, which is what makes the builder omit the variant segment entirely and run `:watchface:processDebugResources`; passing it a `-Flavor` is refused with exit 2 before any lock is taken.

**Why it exists.** Every other gate in the facade is lexical. Before S1915 no path in it ran aapt, and `a.ps1 fk` compiles Kotlin without linking anything - so a layout that did not link closed green, and the ticket reached `BlockNeedUserTest`, which means "install this on a device and test it", without anything ever having built what gets installed (S1881). The gate runs the link rather than asking whether a build happened, which is why it needs no build journal, no `temp/` marker and no dev-log parsing, and why parallel sessions raise no question here.

**Reading its verdict.** Exit 1 is a resource that does not link - the aapt line above the verdict names the file and the reference it could not resolve. Exit 2 is a different answer: the target never started, most often a `JAVA_HOME` pointing at a JDK that no longer exists (S1928), so nothing was checked and the resource is still unproven. The gate prints the module and every flavor it linked before running, so a green verdict cannot be read as covering a module it never touched.

Cost, measured 2026-08-21 on a warm daemon: 1.9 s with nothing to relink, 10.6 s for a flavor whose configuration cache was cold, 15.9 s on the red path, 41.8 s for a full relink after a real resource change - all foreground, table in `docs/BUILD_TEST_FAST_PATH.md`.

### Layout dimension-literal ratchet - S1922

Prints as `layout-hardcoded-dimens`. A growth stop, not a migration order: it counts hardcoded `NNdp` / `NNsp` values in layout attributes across all five layout directories (`layout`, `layout-land`, `layout-sw480dp`, `layout-sw720dp`, `layout-w600dp`) and fails only when the total rises above the frozen baseline.

```powershell
# Current count vs baseline, with every offending file listed
pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only layout-hardcoded-dimens -List

# PASS/FAIL verdict (this is how post-change.ps1 reaches it, via the neuroslop umbrella)
pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only layout-hardcoded-dimens -Gate

# Ratchet the baseline DOWN after migrating some literals
pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only layout-hardcoded-dimens -UpdateBaseline
```

**`0dp` is not counted, deliberately.** Measured 2026-08-21, 1561 of the 3454 literals in those directories are `"0dp"` - 45% of them. In a `ConstraintLayout` that is the "match constraints" keyword, a structural token rather than a size: it has no value anyone could want to change in one place, and moving it into `@dimen/` destroys the idiom. The baseline therefore reads **1893**, the count of literals that genuinely could be migrated, not 3454.

**Migration model - the Rule 32 model, same as `findviewbyid`.** No campaign over the 331 layout files is scheduled, and the previous attempt at one reached 63% before being abandoned and deleted. A literal converts when another ticket reaches its file for its own reasons; the next green `-UpdateBaseline` run lowers the baseline; the baseline never rises without a boundary decision. The gate's job is that last clause - it is why the count cannot drift back up while nobody is looking.

The rule lives in the shared registry (`scripts/quality/lib/source-matchers.ps1`) and rides the single tree walk with every other lexical rule, so it adds no traversal of its own: 331 files in roughly 0.3 s.

### Layer import ratchet - S2103

Four rules, printed as `ui-imports-data`, `ui-imports-room`, `ui-imports-impl` and `viewmodel-imports-repository`. They are the mechanical half of the layering rule `UI -> ViewModel -> UseCase -> Repository -> DataSource` (CLAUDE.md Rule 8, `docs/ARCHITECTURE.md`), which until S2103 was the only architectural rule in the repository with no exit code behind it - and Rule 33's own measurement is that a rule in prose holds at 1-8% while a rule with an exit code holds at 99%.

Each counts import lines under `app_v2/src/main/java/com/sza/fastmediasorter/ui/` and fails only when its total rises above a frozen baseline:

| Rule | Counts | Baseline (measured 2026-08-27) |
| --- | --- | --- |
| `ui-imports-data` | any `import com.sza.fastmediasorter.data.*` in a UI file | 403 |
| `viewmodel-imports-repository` | `import com.sza.fastmediasorter.domain.repository.*` in a `*ViewModel.kt` - the UseCase layer skipped | 47 |
| `ui-imports-room` | a Room `*Dao` / `*Entity` imported straight into UI | 36 |
| `ui-imports-impl` | a `*Impl` from `data.*` imported instead of its interface | 2 |

```powershell
# Current counts vs baselines, with every offending file listed
pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -List `
    -Only ui-imports-data,ui-imports-room,ui-imports-impl,viewmodel-imports-repository

# PASS/FAIL verdict (this is how post-change.ps1 reaches them, via the neuroslop umbrella)
pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Gate `
    -Only ui-imports-data,ui-imports-room,ui-imports-impl,viewmodel-imports-repository

# Ratchet the baselines DOWN after moving some imports behind their layer
pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -UpdateBaseline `
    -Only ui-imports-data,ui-imports-room,ui-imports-impl,viewmodel-imports-repository
```

**Four baselines, not one, and the overlap is deliberate.** `ui-imports-room` and `ui-imports-impl` are both subsets of `ui-imports-data`, so a Room import is counted twice. That is the point: the four numbers span three orders of magnitude (403 / 47 / 36 / 2), and under a single aggregate counter a new `*Dao` in a fragment could be paid for by deleting one unused `data.cloud` import elsewhere in the same change. S1910 is the ticket where exactly that masking happened.

**`data.model` is counted, and no suppression list exists.** All 16 of its UI imports are the `DeviceProfile` family - pure device-description types with no Room and no Android dependency, which by meaning belong in `domain.model` and simply live in the wrong package. The fix is to move the type, and the move lowers the baseline on the next green run; an exemption would freeze the wrong placement permanently.

**Migration model - the Rule 32 model, same as `findviewbyid` and `layout-hardcoded-dimens`.** No campaign over the 164 files is scheduled. A file converts when another ticket reaches it for its own reasons, the next green `-UpdateBaseline` run lowers the baseline, and the runner refuses to raise one. `ui-imports-room` is the baseline worth driving to zero first - a DAO in a fragment is the sharpest of the four.

**Placement class: per-ticket** (Rule 33, named at birth). Release-scope needs all four of its conditions and the second fails here - the subject is the changed file itself, not the tree or a shipped artifact. Per-ticket is earned by the first condition instead: later work builds on the leak, because every further file importing through the same hole raises the cost of unwinding it. Rule 33's failure mode - a gate that cannot attribute its finding and so fails on a sibling session's WIP - does not arise, since `-ScopeToFile` puts the runner in delta mode, judging each changed file against its own HEAD version.

The rules live in the shared registry (`scripts/quality/lib/source-matchers.ps1`) and ride the single tree walk, so they add no traversal: `app_v2/src/main` is already scanned, and the narrowing is a `PathFilter` applied to text already in memory. Wear is deliberately not judged - that module has no `com.sza.fastmediasorter.ui` package, so the rules would only produce a dead baseline of zero.

### Ratchet reconciliation - the two runs and what each judges - S2110

Every ratchet baseline in this repository is enforced by the same runner in two different senses, and the difference is the whole point:

- **The per-ticket closure judges the named file set.** `post-change.ps1 -ScopeToFile` hands the runner `-ChangedFiles`, which puts it in delta mode: each file's working copy is counted against its own `HEAD` version, and only growth fails. This is what keeps a closure from going red on a sibling session's in-flight work (S1338).
- **The release-scope run judges the whole tree.** `assert-release-scope-gates.ps1` invokes the same runner with no `-ChangedFiles` at all, so it compares each rule's project-wide count against its committed baseline. `/spec-prerelease` step 0.4 is the only mandatory path that reaches it.

**Why the second run had to exist.** Delta mode is fail-closed for a brand-new file - absent from `HEAD`, so every hit in it counts as new - which makes the predicate look airtight. It is not, because a file the author never names is judged by neither mode. Measured 2026-08-27: `layout-hardcoded-dimens` stood at 1899 against a baseline of 1893 in **committed** `HEAD`, with all five layout directories clean in the working tree. The six literals sat in three layout files created after the baseline commit, and every closure that carried them was green. No per-file logic can close that hole - only a run that looks at files nobody named.

**Two entries for one script is not duplication** (S2110 ADR-2). In `assert-fast-gates.ps1` the runner takes `-ChangedFiles` from its caller and judges a changed set; in `assert-release-scope-gates.ps1` it never does and always judges the tree. Different subject, so both entries are load-bearing - deleting either one is what returns the baselines to being nominal.

**`-Explain` turns a red total into a list of files.** A full-scan failure prints `baseline 1893 | actual 1899 | delta 6` and no address, which is the shape that costs an hour of git archaeology; `-Explain` performs that archaeology mechanically. It resolves the reference point as the last commit that touched **that rule's own baseline file**, then prints every path under the rule's roots whose count differs between that commit and the working tree:

```powershell
# Which files moved a rule off its baseline, and by how much
pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Explain -Only layout-hardcoded-dimens
```

Each line reads `path  refCount -> workCount`, and the run closes with the reference commit, both totals and the delta. It reports only - a delta never fails the run. A rule whose baseline file has no commit at all has no reference point, so the run says that and exits 2 (cannot verify) rather than printing an empty list that reads like "nothing drifted".

### Swallowed cancellation - the three cure forms and which one a site takes - S2104

The gate's own FailMessage names `catch (e: CancellationException) { throw e }`, and the tree abandoned that shape: 436 helper call sites against zero remaining supertype arms. A developer who reads only the failure message writes three lines and reorders a catch chain for nothing. The cures actually in use live in `core/util/CoroutineExt.kt`, mirrored deliberately in `wear/util/CoroutineExt.kt` because no module is shared between `app_v2` and `wear`:

- `Throwable.rethrowIfCancellation()` - re-throws, logs nothing.
- `Throwable.warnUnlessCancellation(message, vararg args)` - re-throws, else `Timber.w`.
- `Throwable.errorUnlessCancellation(message, vararg args)` - re-throws, else `Timber.e`.

**The call must be the block's first statement.** Anything above that first statement has already run error-path work on what was only a cancellation, and the matcher counts that as uncured - a one-line block therefore carries its cure on the `catch` line itself, which the matcher reads.

**Which form a site takes is decided by what the block already does, not by preference:**

- First statement is `Timber.e(<v>, ..)` or `Timber.w(<v>, ..)` passing the caught variable first - swap the whole call for the matching `*UnlessCancellation` member.
- Anything else - a log that does not pass the throwable, a `Timber.tag/d/i`, a `withContext`, a return expression, an empty body - insert `<v>.rethrowIfCancellation()` above it and leave the existing line untouched.

**A swap never changes the level of the line it replaces.** That is why the family covers warn and error rather than one level: most of the debt logs at error, and curing it with the warn member alone would silently downgrade real failures. Where a swap cannot preserve both the level and the stack trace, the insert form wins.

A site that already re-throws by hand keeps its own log line instead: give it a real `catch (e: CancellationException)` arm ahead of the broad one. The matcher skips a chain whose head arm names cancellation, so the debug line survives and the finding clears.

The matcher recognises the family by name shape (`\w+UnlessCancellation`), so a new member needs no paired gate edit.

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

### Restricted AppCompat menu reflection - S1406

A lexical ratchet (baseline 0) banning reflection into AppCompat menu internals in `app_v2/src/main`: a `getDeclaredField`/`getDeclaredMethod` call naming `mPopup`, `mMenuItems`, `mMenuView` or `getListView`, and any reference to the `androidx.appcompat.view.menu.*` restricted package.

It exists because the player overflow menu used to read `PopupMenu`'s private `mPopup` field to hang a long-press on the popup's internal `ListView`, wrapped in a broad catch. That combination fails silently: an AppCompat update drops the affordance and the catch guarantees nobody finds out. The affordance belongs in the command model, where the menu builder can render it as a visible item.

The rule lives in `scripts/quality/lib/source-matchers.ps1` and runs inside the single-walk runner, so `assert-neuroslop.ps1` (hence `post-change.ps1`) and `.\a.ps1 fg` both enforce it with no extra traversal.

```powershell
# Report count vs baseline
pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only restricted-menu-reflection

# PASS/FAIL verdict
pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only restricted-menu-reflection -Gate
```

Scope is deliberately narrow: `DeliveredNativeLibraryLoader` (reflection into `BaseDexClassLoader` for on-demand `.so` delivery) and the `FastMediaSorterApp` settings dump reflect legitimately and stay unflagged.

### Shared unit-test flavor scope - S1453

Refuses a test in `app_v2/src/test` that references a type living only in a flavor-scoped source set. That set compiles for every flavor, so one misplaced test breaks unit-test **compilation** on every flavor mounting the disabled counterpart - and while `lite` unit tests did not compile, the release-blocking permission-parity test could not run there at all.

The same gate enforces the mirror half of `dev/FLAVOR_DEVELOPMENT_RULES.md` RULE 7: a capability test set must be mounted into exactly the flavors that mount its main counterpart. A test set with no main counterpart on disk (`testDocumentsEnabled` groups by capability flag) is exempt.

Both the mount map and the flavor list are derived from `app_v2/build.gradle.kts` on every run through `scripts/quality/lib/flavor-source-map.ps1`, so no gate carries a copy. A mount line the parser cannot attribute makes the gate exit **2** - "could not verify" - rather than narrow the scan and still print PASS.

```powershell
# Report violations without failing a caller
pwsh -NoProfile -File scripts/quality/assert-shared-test-flavor-scope.ps1

# PASS/FAIL verdict (wired into assert-fast-gates.ps1 / .\a.ps1 fg)
pwsh -NoProfile -File scripts/quality/assert-shared-test-flavor-scope.ps1 -Gate

# Inspect the declaration index behind a verdict
pwsh -NoProfile -File scripts/quality/assert-shared-test-flavor-scope.ps1 -DumpIndex

# Regression suite - 13 cases over a synthetic repository, no writes into app_v2
pwsh -NoProfile -File scripts/quality/assert-shared-test-flavor-scope.tests/Run-Tests.ps1
```

`scripts/quality/assert-test-suite-complete.ps1` consumes the same map: its denominator is the variant's effective source roots, not `src/test` alone, which had understated `standard` by 2.1 % and `noLegal` by 4.2 %.

### Custom Android Lint rules - S0721

An AST-based custom lint checker `:lint-rules` enforcing structural project rules:
- **ActivityLogicViolation**: No business logic / `@Inject` repositories inside Activities.
- **UiContextLeak**: No storage of UI Context (Activity, Fragment, View) in ViewModels or `@Singleton`s.
- **UnsafeFlowCollect**: No lifecycle-unsafe Flow `.collect` calls without `repeatOnLifecycle` or `flowWithLifecycle`.
- **PlayerNotReleased**: Classes holding media players must release them via `release()`.
- **MainThreadIo**: Blocking file I/O calls on the main thread in UI / ViewModel classes.
- **NetworkDataSourceDispatcher**: Blocking socket network I/O calls (smbj, commons-net, jsch) without explicit background dispatcher confinement.

Usage:
```powershell
# Run lint check on standard flavor debug variant
.\gradlew.bat :app_v2:lintStandardDebug

# Run tests of the lint rules module itself
pwsh -NoProfile -File ./a.ps1 flr
```

### Memory Leak Testing (LeakCanary) - S0721

Instrumented leak detection run on demand using LeakCanary inside instrumented tests:
- **LeakDetectionInstrumentationTest**: Automates UI traversal or lifecycle actions and fails the test run if any memory leaks (retaining Activities, Fragments, etc.) are detected.

Usage:
```powershell
# Run the leak detection instrumented test
.\gradlew.bat :app_v2:connectedStandardDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.sza.fastmediasorter.leak.LeakDetectionInstrumentationTest
```


### Wear pre-release sweep - S1984

The watch has its own sweep, because every device stage of the phone one is written against the phone package, the phone launcher activity and the phone variant set.

```powershell
pwsh -NoProfile -File scripts/devtest/wear-prerelease-prepare.ps1 -DeviceId <serial>
pwsh -NoProfile -File scripts/devtest/wear-prerelease-walk.ps1 -DeviceId <serial>
```

- The procedure that sequences these and branches on their exit codes is `.claude/commands/spec-prerelease-wear.md`; what a watch release must prove is `docs/RELEASE_READINESS_WEAR.md`.
- **A watch must be attached.** The prepare step reads `ro.build.characteristics` and refuses anything without `watch`, because both modules publish under one application id and a run that landed on the phone would report a confident verdict about the wrong build.
- Run artifacts land in `temp/scratch/wear-prerelease/`: `artifact.json` (what was built and judged), `walk.json` (per-screen outcome plus the log audit's code), `wear_session.log`, and a screenshot and UI dump per screen.
- The content gates common to both modules run from `scripts/quality/assert-prerelease-content-gates.ps1`, which the phone sweep calls as well - adding a gate there covers the watch without editing either command file.

## OCR OVERLAY ACCURACY CORPUS (S1716)

A corpus of annotated scenes and a harness that scores the translation overlay's plate against them. It
lives in the test source set (`app_v2/src/test/java/com/sza/fastmediasorter/ocrbench/`), so it ships with
nothing and is unreachable from the app.

```powershell
pwsh -NoProfile -File scripts/ocrbench/run-corpus.ps1        # run the corpus, print the report path
pwsh -NoProfile -File scripts/ocrbench/fetch-real-scenes.ps1 # bring registered real scenes into the cache
```

Reports land in `temp/ocrbench/<YYYY-MM-DD>/overlay-rectangle-report.md`, and the newest path is also left
in `temp/ocrbench/last-report.txt`. Every acceptance bound taken from a run is written into
`docs/OCR_OVERLAY_ACCURACY.md` naming the report's date and path - a bound with no dated report behind it
does not exist.

**A report that backs a bound gets copied into its ticket folder** (`PLAN/Sxxxx_<slug>/reports/`) and cited
from there, not from `temp/`. `temp/` is disposable by Rule 1, so a bound citing it loses its provenance the
first time the directory is cleaned - and `check-evidence-durable.ps1` refuses to close a spec that does it.

**It scores rectangles, and only rectangles.** Four axes: annotated text found, plate-to-text overlap, plate
area spilling outside the paintable areas, and duration. Nothing here reads a pixel, so nothing here can say
how much source ink a plate actually hides - that axis needs a rasterised composition and belongs to
**S1782**, together with the Robolectric upgrade it costs. An axis the run could not compute is reported
`Unmeasured` with its reason and counted per axis in the report; it never arrives as a zero.

**Adding a synthetic scene.** Add a builder to `SyntheticScene` and list it in `all()`. Everything must come
from constants declared in that file - no clock, no randomness, no device metrics - because a scene that
redraws differently makes every later regression unattributable. Declare its paintable areas explicitly
rather than deriving them from the text areas: "where the text stands" and "where a plate may paint" are
different questions, and only the scene's author knows the second one.

**Adding a real scene.** Media never enter the repository; they live in a local folder addressed by the
`FMS_OCRBENCH_SCENES` environment variable. Register one with
`fetch-real-scenes.ps1 -Register <path relative to that folder>`, which computes its SHA-256 into the
committed manifest at `app_v2/src/test/resources/ocrbench/real-scenes.json`. Then annotate it by hand at
`app_v2/src/test/resources/ocrbench/annotations/<sceneId>.json` - the annotation is committed, because it is
the most expensive manual work here and the only part that cannot be regenerated. A registered scene missing
from the cache fails the run rather than shrinking the corpus quietly.

**The one rule that must not be broken: a draft annotation never scores.** An annotation filled from a
recogniser's own output is marked `draft` in its provenance, and `SceneAnnotation.isScorable()` refuses it,
as it refuses an unreadable scene and an empty annotation. Scoring a recogniser against its own output
measures nothing while looking like a perfect result. A human corrects the draft first; only then does it
count.


## STRING RESOURCE TOOLING

```powershell
# SINGLE-LOCALE UPDATE
pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Module app_v2 -Locale en -Key "cloud_check_failed" -Value "Could not check the cloud connection. Try again."

# EN/RU/UK UPDATE IN ONE CALL
pwsh -NoProfile -File scripts/utils/set-android-strings.ps1 -Module app_v2 -Key "cloud_check_failed" -EnValue "Could not check the cloud connection. Try again." -RuValue "Не удалось проверить подключение к облаку. Попробуйте ещё раз." -UkValue "Не вдалося перевірити підключення до хмари. Спробуйте ще раз."

# OPTIONAL SAFETY GUARDS
pwsh -NoProfile -File scripts/utils/set-android-strings.ps1 -Module app_v2 -Key "cloud_check_failed" -EnValue "Could not check the cloud connection. Try again." -RuValue "Не удалось проверить подключение к облаку. Попробуйте ещё раз." -UkValue "Не вдалося перевірити підключення до хмари. Спробуйте ще раз." -ExpectedOldEnValue "Could not check the cloud connection." -ExpectedOldRuValue "Не удалось проверить подключение к облаку." -ExpectedOldUkValue "Не вдалося перевірити підключення до хмари."

# LOCALE PARITY CHECK
pwsh -NoProfile -File scripts/check_strings_localized.ps1 -Module app_v2 -KeyPrefix "cloud_check_failed"
```

Use the string updater scripts for targeted `<string>` edits. Manual XML editing is still appropriate for structural resource changes such as `plurals`, `string-array`, comments, regrouping, or bulk rewrites.

### Unreferenced string keys - S1568

```powershell
# WHICH KEYS DOES NOTHING REFERENCE (report; any count is a valid result)
pwsh -NoProfile -File scripts/utils/audit-unreferenced-strings.ps1 -Module app_v2 -File strings.xml

# THE SAME MEASUREMENT AS A GATE (fails on a name that is neither referenced nor baselined)
pwsh -NoProfile -File scripts/quality/assert-unreferenced-strings.ps1 -Gate

# DELETE MANY KEYS IN ONE PASS, FROM EVERY LOCALE, WITH ONE REFERENCE SCAN
pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action remove -KeyList temp/S1568/removal-candidates.txt -DryRun
```

Three facts a reader cannot derive from the commands:

- **Liveness is decided per module.** `app_v2` and `wear` are separate resource namespaces with no dependency between them, so a key of one is unreachable from the other. 15 names exist in both, and a scan spanning both trees reports each of them as alive on the strength of the wrong module.
- **Every source set under `<module>/src` is scanned, not `src/main`.** Restricting the walk to `src/main` raises app_v2's dead count from 397 to 619: **222 names are referenced only from a flavor, feature or test source set**, and a main-only scan calls every one of them safe to delete.
- **A key kept despite being unreferenced belongs in the baseline, with a reason.** `scripts/quality/assert-unreferenced-strings-baseline.txt` is an allowlist of names, not a count, so a new dead key cannot slip in behind a deleted one. The reason column is the record of why the key was kept - an unexplained entry is how the previous 397 accumulated.

The three actions share one definition of "a reference", in `scripts/quality/lib/android-string-liveness.ps1`. Change it there, never in a caller.

### Generated splash drawables - S1706

```powershell
# THE ONLY WRITER of ic_splash_app_brand.xml, in either module
pwsh -NoProfile -File scripts/utils/generate-splash-brand.ps1 -Module <app_v2|wear>

# THE SAME COMPARISON AS A GATE (fails on a hand-edited or stale variant; in .\a.ps1 fg)
pwsh -NoProfile -File scripts/quality/assert-splash-brand-sync.ps1
```

- **The drawable is generated, never authored.** The system splash window cannot render a string, so the wordmark and the slogan exist only as contours baked in from `splash_slogan` and one template. A hand edit therefore compiles, renders, and diverges silently from every other locale.
- **`splash_slogan` is consumed at authoring time, not at run time.** Nothing under `app_v2/src` references it and nothing can, which is why it sits in the unreferenced-strings baseline with that reason rather than being deleted as dead.
- **The two modules generate different compositions on purpose.** The phone carries arrows, wordmark and slogan with one variant per locale; the watch carries the arrows alone, because measured on a Galaxy Watch 7 the wordmark rendered 10 px tall and the slogan 12 px, roughly 5-7 dp against Wear OS's 12 sp floor. `-Module wear` therefore adds `--arrows-only`, and the watch has no per-locale variant at all.

### Gson persistence contract - S1639

```powershell
# FULL REPORT - every serialization point, its sink, and the pinning verdict of each durable model
pwsh -NoProfile -File scripts/quality/assert-gson-persistence-contract.ps1

# THE SAME MEASUREMENT AS A GATE (this is what the fast batch and post-change.ps1 call)
pwsh -NoProfile -File scripts/quality/assert-gson-persistence-contract.ps1 -Gate

# STRUCTURED OUTPUT for a caller: points, model verdicts, unresolved points, suppression counts
pwsh -NoProfile -File scripts/quality/assert-gson-persistence-contract.ps1 -Format json
```

The invariant: a model whose Gson JSON outlives the process must have its field names pinned. It reached users six times (S0719, S0737, S1630, S1631, S1632, S1638) because nothing tied "this goes to storage" to "its names are pinned" - the two facts live in different files and usually different modules, so review cannot hold them together.

Four facts a reader cannot derive from the commands:

- **Durability is decided by the sink, not by a marking on the model.** A file under private storage, plain or encrypted preferences, DataStore, the Wear data layer and a user-facing export all outlive the process; a worker payload and a network request do not. A sink the table does not recognise counts as durable, because an unnecessary entry costs one written justification and a missed model costs a user incident.
- **Two forms of pinning are accepted, and each module is judged against its own rules.** `@SerializedName` on every property, or a keep rule in that module's `proguard-rules.pro` that holds field names. The phone annotates its contract models; the watch keeps the whole `wear.domain.model` package. A rule carrying `allowobfuscation`, or one qualified by an annotation, is refused - the tree holds a Gson rule of each shape that would otherwise green every model in it. A flavor-scoped rules file is deliberately not read: it pins nothing in the flavor that ships to Play.
- **Partial annotation is its own violation kind, and so are enum constants.** A half-annotated model reads as protected at a glance and survives review while still being broken. An enum is separate again: Gson writes the constant's own name, so neither annotating the containing model nor keeping it covers the value that actually ships.
- **The only suppression path is `scripts/quality/gson-persistence-exemptions-baseline.txt`, and it demands a written justification.** An entry with a bare name refuses the whole run with exit 2. A justification opening with `Ticket: Sxxxx` records a live defect owned by that ticket rather than excusing it, and the verdict line counts those separately - so a green run states out loud how many known defects it is still carrying. The file is a ratchet: removing an entry is always accepted.

### Thirteen locales - S1627

```powershell
# WHAT DOES NOT YET REACH EVERY DECLARED LOCALE (0 clean, 3 non-empty, 1 unusable input)
pwsh -NoProfile -File scripts/utils/list-new-lexemes.ps1

# THE SAME SET AS A RELEASE BLOCKER (0 clean, 1 blocked, 2 cannot verify) - ONCE PER MODULE
pwsh -NoProfile -File scripts/quality/assert-new-lexemes-translated.ps1
pwsh -NoProfile -File scripts/quality/assert-new-lexemes-translated.ps1 -Module wear

# THE BULK ROUND TRIP THAT CLEARS IT
pwsh -NoProfile -File scripts/utils/locale-bulk-import.ps1 -TextPath <file returned by the translator>
```

The app declares thirteen interface locales in `app_v2/src/main/res/xml/locales_config.xml`. Three - `en`, `ru`, `uk` - are authored and must stay complete. The other ten are machine-translated in bulk and are allowed to lag, but only until the release. The loop, in order:

1. Writing a key with `set-android-string.ps1 -Action add` names the locales the call left empty and prints a ready-to-paste `-Translations` fragment. A hint, not a refusal.
2. Closing a ticket that touched a strings file prints the `new-lexeme-count` advisory. Also not a refusal.
3. The pre-release sweep runs step `0.8`, which **is** the refusal. `list-new-lexemes.ps1` writes `temp/S1627/new_lexemes_en.txt`; that file goes to the external translation service, each returned file comes back through `locale-bulk-import.ps1`, and the step is re-run until it is 0.

Four facts a reader cannot derive from the commands:

- **The refusal sits at the release, not at the ticket, by owner decision (strategic ADR-2).** Nothing ships between releases, so translating each key the day it is written buys the user nothing while costing ten translations per ticket; one batch per release costs one round trip for all of them.
- **A missing translation is an absent key, never an English copy (ADR-6, S1190).** Android falls back to English on its own, so a partial locale is a shippable state. This is why the producer asks each locale's resource file which keys it carries, rather than comparing values.
- **Provenance is tracked per module, and the gate runs once per module (S1858).** `scripts/quality/locale-source-fingerprints.json` addresses a unit as `module|set|file|key[|slot]`. It has to: `app_v2` and `wear` each ship `src/main/res/values/strings.xml` and share 14 key names, 6 of them with different English text, so an unqualified identity gave the two modules one slot with room for one hash. Whichever module imported last won it, and the gate then measured the other module's text against the wrong hash and called six translated keys untranslated - unfixable by re-importing, because re-importing only moved the red to the other module. A registry written before that split declares no schema version, reads as v1 and is refused with exit 2 until `scripts/quality/migrate-locale-fingerprints-module.ps1` rewrites it; a v1 store read as v2 would reproduce the same false report with nothing left to explain it.
- **`scripts/quality/locale-untranslated-baseline.txt` holds identities, not a count.** It froze the keys already untranslated on 2026-08-14 - all of them `S1626`'s placeholder-misread phrasings - so a pre-existing gap cannot be reported as new. A count would let a new key slip in behind an old one cleared in the same release. Its entries are module-qualified for the same reason the registry's are. Entries leave the file as `S1626` clears them, and the producer reports a cleared entry as stale; do not expect that soon, since `S1626` is `BlockExternal` - the rule that looked obvious (placeholder at a string edge) was measured over all 307 placeholder-bearing strings and does not discriminate, so the set clears through a probe in a future bulk round rather than through an edit anyone can make today.

### Maestro oracle convention - S1612

```powershell
# GATE (fails on any flow that can be green without proving anything)
pwsh -NoProfile -File scripts/quality/assert-maestro-oracle.ps1

# ALSO RUNS INSIDE THE FAST STATIC BATCH
pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1
```

Scans `maestro/` and `scripts/devtest/maestro/` for the three authoring mistakes that make a flow green while proving nothing. The authoritative rule text lives in `maestro/WRITING_TESTS.md` section "Oracle convention" - the gate encodes exactly those rules and must not drift from them.

Three facts a reader cannot derive from the commands:

- **`optional: true` is judged by what it is attached to, not by where it appears.** On a navigation `tapOn` whose target genuinely varies - a system permission dialog, a skippable onboarding page - it is correct and stays. On `assertVisible` / `assertNotVisible` it turns the proof into a no-op that passes either way, so the gate tracks the enclosing command opener rather than matching the line on its own.
- **A regex selector does not fail loudly, it fails silently.** Maestro does not reliably match `id: ".*settings.*"`, so the step never fires and the flow proceeds green. This is why the rule is mechanical: a reviewer reading the YAML sees an intention that the runtime never carries out.
- **Every exemption names its reason and its exit condition.** `$exemptRelativePaths` in the gate holds `_shared/permissions.yaml` permanently (a fragment of nothing but optional permission taps, which the convention sanctions) and the two `device_only/3d-video-*.yaml` flows temporarily, pending S1618 - they drive a "Playback Settings" dialog that is unreachable from the player UI, so their regex selectors cannot be replaced with real ids because those ids do not exist.

## DEBUG PROBE INVARIANT (both directions)

CLAUDE.md Rule 2 makes the probe an **if and only if**: `Timber.d("Sxxxx: ..")` exists in `.kt` exactly when ticket `Sxxxx` is in `BlockNeedUserTest`. `scripts/quality/assert-no-ticket-logs.ps1` now checks both halves in one catalogue read and one source walk:

- **A ticket id in a permanent log** - any id in `Timber.i/w/e`, any non-probe id in `Timber.d`, or a probe whose ticket has moved on (stale). This half is the original gate.
- **A `BlockNeedUserTest` ticket with no probe in source** - added by S1290. This is the half that let S1279 sit for weeks waiting on a device check with nothing to read in the log, while its `## Last Audit` quoted probe output that no longer existed in the tree.

```powershell
pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1          # audit, always exits 0
pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1 -Gate    # fail-closed, both halves
```

Two facts a reader cannot derive from the commands:

- **The exceptions are an allow-list with reasons, not a counter.** `scripts/quality/blockneedusertest-probe-baseline.txt` holds `Sxxxx  <reason>` rows. There is exactly one legitimate reason, and measurement is what found it: a ticket that changes tooling, scripts or documentation and touches **no Kotlin** has nowhere to put a probe, yet still needs a human to verify it. Measured 2026-08-14 - 10 tickets in `BlockNeedUserTest`, 8 carrying a probe, both gaps of that shape. A ratchet counter was rejected deliberately (S1290 ADR-1): it would have recorded those two as anonymous debt, when the whole point is that the number moves only with an explanation. A ticket that *did* change Kotlin belongs in the source with a probe, never in this file.
- **A stale allow-list row is inert, not harmful.** The row is only consulted for ids currently in `BlockNeedUserTest`, so it stops being read the moment its ticket moves on. Delete it when you notice it; nothing breaks if you do not.

## HOUSE TEXT STYLE (where it is applied)

The style - `..` for the ellipsis, a plain hyphen for the long dashes, Russian `ё` where required - is applied **on the paths that write text**, not by a gate over the result. There is no `assert-*` for it, deliberately.

The rules live in exactly one place, `scripts/quality/lib/house-text-style.ps1`, as data. Three consumers read them and none re-declares a pattern:

- `scripts/utils/locale-bulk-import.ps1` - normalizes every returned translation line before it reaches a resource. This is where the debt came from: the external service re-typographs what it is given, so a house-style-clean English source came back with `…` and `–`. Each corrected line is named in the run's output as `normalized: ..`, and normalization never changes the exit code - a lost format token is rejected, a stray dash is simply fixed.
- `scripts/utils/set-android-string.ps1` - normalizes every value it writes, in every locale. The `ё` rule is applied to `ru` alone.
- `scripts/utils/fix-house-style.ps1` - the manual pass, and the only one for documentation prose. Dry run by default; `-Apply` writes. Exit 3 means "changes pending", not failure.

```powershell
pwsh -NoProfile -File scripts/utils/fix-house-style.ps1                       # dry run, both areas
pwsh -NoProfile -File scripts/utils/fix-house-style.ps1 -Area ResourceValue -Apply
pwsh -NoProfile -File scripts/utils/fix-house-style.ps1 -Area Prose -Path docs -Apply
```

Two facts a reader cannot derive from the commands:

- **Documentation prose carries no gate on purpose.** Measured 2026-08-14 (S1544): 134 of 137 files under `docs/` were clean without one, and the three that were not are the gitignored `FEATURES_noLegal*` showcases, which are never published. A gate would cost every run and defend a surface where nothing accumulates. S1340 §5 forbids growing the `assert-*` inventory for cosmetics, and this ticket shrank the script count by four rather than adding to it.
- **The `ResourceValue` area skips values that are wholly machine-readable** - a URL, a path, a bare format placeholder - because a literal `...` inside an address is part of the address. That path test demands printable ASCII end to end: Chinese and Japanese set no spaces between words, so "no whitespace and contains a slash" on its own matched whole CJK sentences and left them unfixed.

## SCRIPT HYGIENE (S1872)

Three checks keep the repository's ~370 PowerShell scripts findable, described and alive. All three are ratcheted: their ceilings may fall, never rise, so existing debt never blocks an unrelated ticket while a new script must be correct on the day it is written.

**`scripts/quality/assert-script-references.ps1`** - a script nothing references is either deleted or declares itself a hand-run tool.

- Judges **live wiring only**. A mention in an archived spec, a `dev/CHANGELOG.md` row or a read-only zone remembers a script; it does not call one. The repository holds over 6000 such documents, enough to make every dead script look wired - with them in the corpus the check reported 0 orphans out of 340 and could not fail.
- Judges **a path, not a file name** (S2124). Until 2026-08-27 the key was the bare file name, so the 37 files called `Run-Tests.ps1` shared one entry and three comments naming that word vouched for all 37 - none of which is called from anywhere. Any group of files sharing a name went unjudged the moment one member was mentioned. Re-keying raised the verdict from 30 to 58; the 28 added files are Pester runners with no launcher, owned by S2122.
- A token is resolved into the file it names by the ladder in `scripts/quality/lib/script-reference-resolution.ps1`: a `$PSScriptRoot`-anchored path, a bare name matching a sibling, the longest resolving path suffix, a unique bare name - and then a bare name several scripts carry, which is **evidence about none of them**. The first four rules are the price of the path key: without them the re-keying reported three scripts that run every day as dead.
- The baseline is a **list of paths, not a count**: repairing one orphan cannot free a slot the next one occupies silently. A line matching nothing prints a prune hint rather than failing.
- `docs/SCRIPT_CHEATSHEET.md` and the two baseline files are excluded **by definition, not by setting**: each names scripts by construction. The main baseline joined that list the moment it stopped being a count - as a list of 58 paths inside `scripts/`, it vouched for every orphan it recorded and drove the verdict to zero.
- A Pester suite beside a `Run-Tests.ps1` is reached by discovery, not by name, and is excused automatically.
- Escape hatch for a script you run by hand: put a line in its comment-based help reading `Manual tool: <why it exists and who runs it>`. An empty reason does not count.
- `-Memory` mode checks the other direction: every `.ps1` path written in `.claude/agent-memory/**` must resolve, or carry a `Historical:` / `External:` marker on its line or the line above.
- `-Docs` mode asks that same reverse question of the live documents, and is the one of the three that runs on every closure (`post-change.ps1`, step `doc-script-references`): a document naming a `.ps1` that does not exist hands its reader a command that cannot run. S1978 found one such line by hand and a sweep found thirteen more in three registered documents (S1979). Corpus: `docs/`, `dev/` minus its archive and changelog, `.claude/` minus `agent-memory`, and `CLAUDE.md` / `AGENTS.md` / `GEMINI.md` / `README.md`. Resolution is tree-wide - `maestro/*.ps1` and `.claude/hooks/*.ps1` are real scripts even though the orphan check above never judges them.
- The `-Docs` baseline is a **list**, not a count: `scripts/quality/doc-script-reference-baseline.txt` holds one `path :: token` line per known-bad reference, so a new phantom cannot hide behind a fixed one. Never add a line there to go green - fix the reference, or say on its line that the script is `External:` (ships outside this repository, like the `sza` plugin's hooks) or `Historical:` (retired). Under `-ScopeToFile` the closure judges only the documents it changed; a `.ps1` in the changed set widens it back to the whole corpus, because renaming or deleting a script is what breaks the documents naming it.
- Baseline: `scripts/quality/script-reference-baseline.txt`. Exit 0 at or below it, 1 above, 2 when a root is missing.

**`scripts/quality/assert-script-described.ps1`** - a script says what it does and which codes it returns.

- Two counts, kept apart so neither hides behind the other: no `.SYNOPSIS`, and declares `exit N` while documenting no `Exit codes:` block. A library that never exits is not asked for a contract.
- Baseline file carries **two lines**: undescribed count, then undocumented-exit count.
- Exit 0 at or below both ceilings, 1 above either, 2 when a root or the baseline is missing.

**`scripts/utils/script-help-text.ps1`** - the one reader both the gate and the cheatsheet generator use.

- `GetHelpContent()` returns **nothing** when a `#requires` statement sits above the help block, and this repository puts `#requires -Version 7.0` on line 1 by convention. Every conforming script was therefore invisible to the generator: the cheatsheet carried a synopsis for **0 of 373** entries, which read as "nobody writes synopses" when in fact many do and none could be read. The helper tries the parser first, then reads the leading comment block literally. Repairing the reader beat moving `#requires` in 370 files.
- Because the gate and `help.ps1` share this reader, the inventory and the gate can never disagree about whether a script is described.

**`scripts/quality/assert-file-line-ceiling.ps1`** - Rule 2's 2000-line ceiling, measured for the first time (S1270).

- Counts physical lines of `.kt`, `.java`, `.cpp` and `.h` under `app_v2/src` and `wear/src` - the same number `wc -l` gives, so a disagreement with the gate is always resolvable by hand.
- Ratcheted on the **count** of files above the ceiling, not on a list of names: a list would pin offenders by name and then a rename would read as a new violation.
- Baseline `scripts/quality/file-line-ceiling-baseline.txt`. Exit 0 at or below it, 1 above, 2 when a source root is missing.
- Before this the ceiling was advice: no script measured file length, and detekt's config carries `LongMethod` but no `FileLength` - and detekt never sees a `.cpp` at all. `app_v2/src/vr/cpp/xr_session.cpp` grew from 2101 to 2154 lines while a ticket about its size sat open.

**`scripts/quality/assert-detekt-baseline-absorption.ps1`** - existed since S1356 and was never wired into anything until 2026-08-21.

- Refuses a detekt baseline that **absorbed** a finding absent from the committed ID snapshot. Its mirror, `audit-detekt-baseline-drift.ps1` (S1334), classifies entries that went dead.
- Re-freezing a baseline is the quietest way to make a file look clean while its debt grows. Five tickets - S1186, S1198, S1247, S1269, S1311 - were written about that one mechanism in five different files before anyone noticed the check was written and never run.
- Takes no `-Quiet`; the fast-gate batch calls it with no arguments.

**One root set.** `help.ps1`, `assert-exit-contract.ps1` and both gates above scan `scripts/`, `dev/CATALOG/scripts/` and `dev/ACTIVITY_CATALOG/scripts/`. A population visible to one tool and invisible to another is the population nobody watches.

**Retiring a script.** Delete it together with its references in the same change. Do not leave a forwarding wrapper: nine such wrappers accumulated in `scripts/quality/`, each header claiming it stayed on disk "so every existing caller keeps working unchanged" while having zero callers, and every one of their rules already ran through `assert-source-gates.ps1`.

### Where a regression suite runs - S2122

A suite named `<subject>.tests/Run-Tests.ps1` is the repository's unit of script regression coverage. Until 2026-08-27 there were 37 of them and **not one was invoked from anywhere** - not from `a.ps1`, not from `post-change.ps1`, not from the fast-gate batch, not from the release-scope runner. The first sweep of all 37 found two real failures nobody knew about, one of them red since the ticket that introduced it closed `Verified`. A suite nobody runs is indistinguishable from an absent one.

`scripts/quality/run-script-suites.ps1` is the single implementation behind all three call sites, so the modes cannot drift apart in what they consider a suite or its subject.

- **Placement is the whole registration.** Put the suite at `<dir>/<name>.tests/Run-Tests.ps1` and it is discovered. There is no list to update and no entry to forget - which is deliberate, because a forgotten registry entry is the exact defect that produced this ticket (S2105 added a gate to the facade and never added its recovery-hint entry).
- **Which change selects which suite.** The first four rules are path arithmetic: the sibling script `<dir>/<name>.ps1`, the sibling library `<dir>/lib/<name>.ps1`, the sibling directory `<dir>/<name>/`, and the nested form `<dir>/<name>/tests/` mapping onto `<dir>/<name>/`. `scripts/doc-drift/` is the one directory carrying both shapes - `scripts/doc-drift.tests/` and `scripts/doc-drift/tests/` - and both resolve to it. Editing anything inside a suite's own directory always runs that suite.
- **A suite the path cannot reach declares its own subject.** A `# Subject: <path>[, <path>]` line in the suite's header names what it guards. Three suites need it: `oss-notices.tests` guards `generate-oss-notices.ps1`, and the two adb matcher suites guard `scripts/devtest/lib/ui-tree.ps1`. This is not a registry - the declaration lives inside the file it describes, so it cannot fall out of sync with something it is not part of. `run-script-suites.ps1 -ListOnly` prints every suite with its resolved subject and says so out loud when a suite resolves to nothing, so the gap is visible instead of silent.
- **Two call sites, two readings of the same exit code.** `post-change.ps1` (gate `script-suite-regression`) passes the changed set and runs only the neighbouring suites; it calls the runner **without** `-Gate`, so a suite that could not run for want of an environment tool is advisory and a developer machine missing an optional tool can still close a ticket. `assert-release-scope-gates.ps1` passes no changed set, runs everything, and calls it **with** `-Gate`, which turns that same condition into a failure - before a release the environment must be complete.
- **The exit-2 path fires on the agent's shell, not on a missing tool - measured 2026-08-27.** `scripts/spec_catalog/drift-check.tests` exits 2 when `rg` is absent, and it did so on every sweep run through the agent's Bash tool. `rg` is installed and on PATH: `%LOCALAPPDATA%\Microsoft\WinGet\Links\rg.exe`. The Bash tool's MSYS environment does not carry that directory, and the child `pwsh` inherits the truncated PATH, so the suite was answering honestly about a shell rather than about the machine. The same sweep from the PowerShell tool is **39 of 39 green in 215.4 s**. Two consequences worth keeping: a red or yellow row naming a missing executable should be re-run from PowerShell before it is believed, and this is exactly the class the exit-2 separation exists for - collapsed into the failure code it would have read as five defects that were never there.
- **Exit codes.** 0 every selected suite passed or none was selected; 1 a suite failed; 2 nothing failed but something could not verify and `-Gate` was passed. "Found a defect" and "did not look" are different answers, and merging them is what gets a run site silenced.
- **By hand:** `.\a.ps1 fs` for the full sweep, `.\a.ps1 fs -ChangedFiles "<paths>"` for the neighbours of a change, `.\a.ps1 fs -ListOnly` to see the selection without running anything.
- **Re-entry is guarded.** The runner exports `FMS_SCRIPT_SUITE_RUNNER=1` around each child, and an inner run reports itself skipped. Without it a suite that drives the closure facade would re-enter the facade's own gate and recurse.


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
| `SUPPORT_WEAR_COMPANION`           | [+] | [-] | [-] | [-] | [-] | [+] |
| `SUPPORT_CAST`                     | [+] | [+] | [+] | [+] | [-] | [+] |
| `SUPPORT_VR_PLAYER`                | [-] | [-] | [-] | [-] | [-] | [+] |
| `VR_UI_COMPOSITION_LAYER_ENABLED`  | n/a | n/a | n/a | n/a | [-] | [+] |
| `IS_NO_LEGAL_FLAVOR`               | [-] | [-] | [-] | [-] | [-] | [+] |

`noL` = `noLegal`. `n/a` means the field is not declared for that flavor at all, so it is absent from its `BuildConfig` and only a flavor-specific source set can reference it - distinct from `[-]`, which is a declared `false`.

`SUPPORT_VR_PLAYER` is true in `noLegal` only. The `vr` flavor declares it `false`: it ships the `src/vr` source set and its OpenXR runtime hooks, but immersive rendering is not wired to the player there yet (epic S0773), so `vr` is the Store-clean shell and `noLegal` is the sideload build where immersive playback works today. Reading the flavor name as the capability is what made this row read as enabled for `vr` until S1392.

Cast is disabled in `vr` (Horizon OS lacks the Google Play Services Cast module); `noLegal` keeps it because it also targets phones/tablets. `SUPPORT_WEAR_COMPANION = true` in `noLegal` is harmless on Quest (no paired watch exists) and meaningful on phones/tablets - runtime decides. `legacy` declares it `false` since S1951: that flavor carries `applicationIdSuffix = ".legacy"`, so the phone installs under an identity the watch app can never match, and Play Services routes the Data Layer by exactly that identity - the companion was declared on a route that cannot exist. The suffix is the frozen store identity of a published flavor, so the claim was dropped rather than the identity. VR feature surface in `noLegal` is gated at runtime by `XrDetectionFacade` - VR controls show disabled on devices without an OpenXR runtime. S0250 (2026-05-19) archived the former `vrUnlicensed` flavor; `noLegal` now covers both phone-sideload and Quest-sideload through one APK.

### Build-type flags (all flavors)

| Flag | debug | staging | release |
|:-----|:-----:|:-------:|:-------:|
| `LOG_SMB_IO`                  | [-] | [-] | [-] |
| `LOG_NETWORK_THUMBNAILS`      | [+] | [-] | [-] |
| `LOG_LINK_DOWNLOAD`           | [+] | [-] | [-] |
| `ENABLE_LEAKCANARY`           | [-] | -   | -   |
| `ENABLE_SCHEDULED_OPERATIONS` | [+] | [+] | [+] |
| `ENABLE_BACKGROUND_AUDIO`     | [+] | [+] | [+] |
| `DECLARES_BATTERY_OPTIMIZATION` | [+] | [+] | [-] |

`ENABLE_LEAKCANARY` is debug-only (`debugImplementation`); field absent in staging/release.

`DECLARES_BATTERY_OPTIMIZATION` (S1436) is the one flag here that mirrors the manifest rather than a feature: the release build strips `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, so code that would offer to grant it must read this flag rather than assume the permission is there. `DECLARES_OVERLAY_PERMISSION` and `DECLARES_SCREEN_CAPTURE` are the flavor-axis members of the same family - see `docs/FLAVOR_MATRIX.md`, which is generated from the `productFlavors` block. The permission registry filters its rows on all three, and `PermissionRegistryManifestParityTest` fails the build if a flag and the merged manifest ever disagree.

## DATABASE

Room schema version: 54 (`@Database(version = ..)` in `AppDatabase.kt` is the source of truth - read it rather than this line).
Library: `room-runtime:2.7.0`.
Migrations: one `MigrationNNToNN.kt` file per step in `data/local/db/`, registered in `core/di/DatabaseModule.kt`.
Exported schemas: `app_v2/schemas/<db-class>/<version>.json`, generated by the build and committed.
**Rule**: Increment schema version on every schema change, and take a migration's target DDL from the generated `<version>.json` rather than hand-writing it.

## NDK & ABI

NDK r27c (`27.2.12479018`) - first NDK release with 16 KB page-size aligned `libc++_shared.so` (Google Play requirement since 2025-11-01 for apps targeting Android 15+).

ABI strategy is flavor-local, not buildType-local (AGP merges buildType+flavor `abiFilters` as UNION, not intersection - a buildType-level list would leak non-VR ABIs into VR AABs):
- `standard`, `lite`, `photos`, `legacy`: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`
- `vr`: `arm64-v8a` only (Meta Quest 2/3/Pro)
- `noLegal`: `arm64-v8a` only since 2026-08-23 - the `x86_64` slice existed solely to run noLegal on an emulator and cost 93.8 MB of a 256.7 MB APK once S1060 added libVLC. It comes back only in a split debug build, as its own file (see below).

### `-Pfms.abiSplits=true` - per-ABI debug APKs (S1972)

An unsliced debug APK carries architectures the target device never executes: standard debug measured 154.3 MB, of which `armeabi-v7a` (18.6) and `x86` (27.1) run on nothing anyone here owns. The phone is arm64-v8a, every emulator is x86_64.

- **Who passes it:** the debug builders that do not need Chaquopy - `build-standard-debug.ps1`, `build-debug.PS1` (behind `a.ps1 d/db/dav/dq`) and `build-debug-clean.PS1`. Nothing else does.
- **noLegal cannot be split, and this is not an oversight.** AGP refuses `ndk.abiFilters` alongside `splits.abi`; Chaquopy refuses their absence (`Variant 'noLegalDebug': Chaquopy requires ndk.abiFilters`). A flavor carrying the Python runtime can be filtered or split, never both, so noLegal stays one `arm64-v8a` APK - the shape ruled for on 2026-08-23. `build-nolegal-debug.ps1` passes no property, and `build-debug.PS1` withholds it whenever Chaquopy is on.
- **Who deliberately does not:** every release path. A release still emits one all-architecture APK per flavor, because the GitHub asset is what IzzyOnDroid globs (S0215) and a single-architecture one would shrink the device set the release reaches - canon hard invariant 2.
- **What it changes:** `splits.abi` turns on with `include("arm64-v8a", "x86_64")`, and every flavor's `ndk.abiFilters` is skipped. Both, not either: AGP refuses the two mechanisms together (`Conflicting configuration: '..' in ndk abiFilters cannot be present when splits abi filters are set`), and it checks **every** variant at configuration time, so one unconditional filter anywhere in `build.gradle.kts` breaks every split build.
- **vr is excluded by the builders, not by the DSL.** `build-debug.PS1` refuses the flag for a vr task, because an x86_64 vr APK would carry no OpenXR native - the loader AAR ships arm64 only.
- **Play is untouched.** `android.splits` is ignored when building a bundle, and `bundle.abi.enableSplit` already defaults to true, so the AAB was always per-ABI.
- **Finding the artifact afterwards:** `scripts/utils/find-build-artifact.ps1`. Every builder, installer and release consumer resolves through it - it selects by ABI from `output-metadata.json` and throws when the request is ambiguous, rather than taking `elements[0]` or the newest file, both of which pick an architecture at random once a build emits more than one output.
- **Choosing the slice:** the debug builders take `-Abi <name>`; omitted, they read `ro.product.cpu.abi` off the connected device.

### Prebuilt FFmpeg DTS AAR - the one dependency a clean checkout lacks (S1539)

`app_v2/build.gradle.kts` declares `files("libs/fms-ffmpeg-dts.aar")` for the standard, noLegal,
legacy and vr flavors, but `.gitignore` excludes `libs/`, so the 11.5 MB binary exists only on a
machine that built it. A local build works; a fresh clone and every GitHub Actions runner do not.

- Build it: `scripts/builders/build-ffmpeg-dts-wsl.ps1` (WSL2, NDK r27c).
- Publish it after any rebuild: `pwsh -NoProfile -File scripts/builders/publish-ffmpeg-dts-aar.ps1`
  (uploads to the permanent `delivery-so-v1` release with `--clobber`).
- CI fetches it: `scripts/ci/fetch-prebuilt-libs.sh`, run by every build job in `android-ci.yml` and
  `maestro-tests.yml` before Gradle starts.

Skipping the publish step after a rebuild does not break CI - it silently builds against the previous
binary, which is acceptable because CI is a compile/lint/test gate and this artifact is a prebuilt
`.so` + `classes.jar` that nothing in the suite exercises. Roles and rationale: `delivery/INVENTORY.md`.

## DEOBFUSCATION RETENTION (S1695)

Gradle overwrites `app_v2/build/outputs/mapping/<variant>/mapping.txt` on every release build, so
exactly one mapping survives locally - the newest. Once a release has shipped and another build has
run over it, nothing local can decode a stack trace from it. That is not hypothetical: S1156 sat in
`BlockExternal` for three weeks because three obfuscated symbols from a shipped release could not be
resolved. Retention removes the failure by copying the payload out of the release build, keyed by
`versionCode`.

**What is retained, and what is not.** The R8 mapping and the native debug symbols only, never the
bundle. Measured 2026-08-15: 21.02 MB per release (mapping 178.9 MB of text compressing to ~14 MB,
plus ~7.9 MB of symbols), stored in 1.7 s. There is no pruning window - at this size a hundred
releases cost about 2.1 GB, and deleting old ones would eventually delete exactly the release someone
needed.

**Layout.** `c:\GD\WORK\FastMediaSorter\deobfuscation\<versionCode>\`:

- `<variant>-deobfuscation.zip` - `mapping.txt` at the root, `symbols/<abi>/<lib>.so.dbg` beneath it.
- `manifest.json` - one record per variant with the source (`bundle` or `outputs`), `mappingSha256`,
  byte counts and the store timestamp. Variants of one release are written by separate invocations,
  so the manifest is merged, never replaced.

**It happens by itself.** `a.ps1 r` retains `standard` from the bundle it just built;
`build-release-spectrum.ps1` retains every other published flavor from `build/outputs`. Do not add a
manual step - a step that can be forgotten is indistinguishable from having no retention. A retention
warning never fails the release build, because the bundle is already good at that point; the gate
below is what refuses to let it slide.

**Decoding a crash from a shipped release:**

```powershell
# What is retained at all
pwsh -NoProfile -File scripts/release/fetch-deobfuscation.ps1 -List

# Pull one release by the version string the crash report carries
pwsh -NoProfile -File scripts/release/fetch-deobfuscation.ps1 -VersionName 2.60.8122.034
# .. or by code, or -Latest. The last line printed is the absolute path of mapping.txt,
# ready to hand to a retrace tool or to assert-enum-persistence-contract.ps1 -Mapping.
```

**Enforcement.** `scripts/quality/assert-deobfuscation-retained.ps1` judges the newest `release/v*`
tag and is gating step 0.6 of `/spec-prerelease`. It reads the stored mapping back through the archive
and recomputes its SHA-256; presence is not accepted as proof, because a cloud folder mid-sync
presents a correctly sized placeholder. Exit 2 blocks exactly like exit 1 - "cannot verify" is not
"verified".

**It is deliberately not in `assert-fast-gates.ps1` / `.\a.ps1 fg`.** The check depends on a cloud
folder that is not mounted on every machine, and a gate that fails for environmental reasons on a
routine fast check trains everyone to ignore it. It belongs where a release is actually about to
happen, which is the pre-release sweep.

**Releases older than versionCode 260815000** predate this scheme and were never retained locally.
Their only surviving mapping is Play Console's, and the console does not hand it back as a file: the
`ReTrace mapping file` row offers deletion, not download, so the real recovery is downloading the
whole 85 MB bundle from `Original file` and unzipping
`BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map` out of it.

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
# Build
.\gradlew.bat :app_v2:assembleVrDebug                            # debug APK
.\scripts\builders\build-vr-release.ps1                  # release APK | .\a.ps1 vr

# Install, NO launch. `adb.ps1 install -Flavor` has no `vr` value, so name the APK explicitly.
.\scripts\devtest\adb.ps1 install -Apk app_v2\build\outputs\apk\vr\debug\FastMediaSorter_vr_debug_v<version>.apk
.\scripts\devtest\adb.ps1 install -Apk app_v2\build\outputs\apk\vr\release\FastMediaSorter_vr_v<version>.apk
```

Install only - never `adb.ps1 launch` here. Launching from ADB starts the panel without the HorizonOS shell, so the Activity never reaches FOCUSED state and immersive entry cannot be judged. Launch from the headset instead, as below.

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
6. Run the publisher: `pwsh -NoProfile -File scripts/release/publish-github-release.ps1`
   from the release worktree on `main`. The Assert-ExpectedFingerprint gate
   will now pass against the new pin.
7. Append an ADR-style entry inside this section recording: rotation date,
   reason, old fingerprint, new fingerprint, release tag that contained
   the rotation.

### ADR log

_(no rotations have happened yet - first entry will land here.)_
