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
.\scripts\builders\build-wear-debug.PS1                 # alias: .\a.ps1 wd
.\scripts\builders\build-wear-release.PS1

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
| `.\a.ps1 faw`  | Fast instrumented-test compile check, **`wear` module** (S2355) |
| `.\a.ps1 fwm`  | Connected Room migration test run on watch, **`wear` module** (S2355) |
| `.\a.ps1 flr`  | Fast lint-rules detector test suite (`:lint-rules:test`); `-Tests <filter>` narrows it |
| `.\a.ps1 fl`   | Android lint, **`app_v2`** (`:app_v2:lintStandardDebug`); runs long, background it (S3155) |
| `.\a.ps1 flw`  | Android lint, **`wear`** (`:wear:lintStandardDebug`); runs long, background it (S3155) |
| `.\a.ps1 dc`   | Clean + debug build |
| `.\a.ps1 cls`  | Clean Gradle caches |
| `.\a.ps1 ss`   | Show unresolved specs (`sca-specs`) |
| `.\a.ps1 adb <verb>` | Ad-hoc adb swiss-army passthrough (see DEVICE OPS below) |
| `.\a.ps1 adb-devices` / `adb-shot` / `adb-log` / `adb-current` / `adb-launch` / `adb-logcat-clear` | Fixed-verb device shortcuts |

## DEVICE OPS (ad-hoc)

**Which device answered decides what these verbs may do to it: `docs/DEVICE_FLEET.md` (CLAUDE.md Rule 35).** This section is the mechanics - discovery, verbs, exit codes - and never the permission; the roster there is the only home of a per-device grant, so read it and match the serial before the first call.

`scripts/devtest/adb.ps1` is the quick swiss-army for one-off work against a connected
emulator / device - runs natively (~0 LLM tokens), auto-discovers adb (not on PATH),
takes `-DeviceId` / `-Release` / `-Package` / `-OutDir` / `-Json`, and uses stable exit codes
(0 ok / 1 no-adb-or-bad-args / 2 no-device / 3 multi-device / 4 pkg-not-installed /
5 destructive verb refused / 6 pull: no such remote path / 7 adb-failed / 8 `tap-label` / `tap-id`:
the target is not on screen and nothing was tapped / 9 clip-check: content off-glass / 12 `rotary`:
the selected device is not a watch and nothing was sent).

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
.\a.ps1 adb rotary -Axis 1.0 -Repeat 3        # turn the watch bezel; watch only, exit 12 elsewhere
.\a.ps1 adb shell -Cmd "getprop ro.product.cpu.abi"
```

### `rotary` is the bezel, because the flow language has none (S2548)

Maestro has no rotary expression, so a watch scenario that must reach its target by rotation rather
than by touch calls `rotary` from the runner around the flow, never from inside the `.yaml`. It sends
`input rotaryencoder scroll <axis>`, repeated `-Repeat` times: a list scrolls by repeating a small
turn, and one large axis value flings instead. The verb refuses (exit 12, nothing sent) when the
selected device does not report `watch` in `ro.build.characteristics` - a rotary encoder exists on no
other form factor, and both modules publish under one `applicationId`, so nothing else would have
caught the wrong target. Reaching an off-screen item by touch is a different question and stays inside
the flow, where `scrollUntilVisible` answers it.

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

**A screen that never idles cannot be dumped at all, and no wrapper change fixes it (S3289).** All
three tree verbs run `uiautomator dump`, which waits for 500 ms with no accessibility event anywhere
on the device, budget 10 s, and offers no flag to shorten or skip that wait - `uiautomator help` on
the device lists only `--verbose` and `--compressed`. A window carrying a live readout emits a
content-change event about every 100 ms, which is `ViewRootImpl`'s own coalescing floor, so the gap
never arrives; the app's player was measured at 100-101 ms while playing on `RFCR110NBQJ`. The
refusal is exit **7** and it names this, because the old advice - let the screen settle and re-run -
is false for a screen that by construction will not settle. It costs one retry rather than three:
each refused attempt blocks for the full idle budget, measured 11.10 s, so the old budget spent
~35 s to learn nothing. A transient refusal while a window settles after a tap is the other regime
and the single retry is what clears it (measured: 2 refusals in 15 calls, the next call through).
What works on an undumpable screen is `shot`, and the same screen once the readout stops. Reading
the tree from `dumpsys activity top` instead was measured and refused - `dev/REFUTED_APPROACHES.md`;
the app-side cure is carried by S3293.

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

### Two shape checks, and why merging them would lose the one Play applies (S2757)

`clip-check` judges TOUCH TARGETS, not ink. Compose publishes every node to the accessibility tree
as `touchBoundsInRoot` - the layout rectangle inflated to the 48 dp minimum touch target around its
centre - and uiautomator, so clip-check, reads that inflated box. Measured 2026-09-08 on the watch
calculator: both children of a value row were already 48 dp, their published boxes therefore
overhung the row by 14.9 px right and 14.6 px up, clip-check called them `OFF-GLASS`, and a pixel
read of the same frame showed the ink sitting exactly where `calculatorShape()` put it - inside the
glass. The finding was true about tappability and false about the rejection it was being used to
investigate.

Play reads no tree. It photographs the frame and writes `cut off by the screen edges`. So
`scripts/devtest/wear-ink-clip.ps1` is the second, independent check: it captures the frame, masks
everything beyond the glass outline, takes the modal colour of that outside region as the background
(never an assumed black - a watch capture is a square bitmap and the app paints its window
background across all of it), and reports 8-connected clusters of pixels that differ from it. Both
tools read the outline through the same `clip-check -Json` shape block, so they cannot end up
judging two different circles on one device.

- `clip-check` - exit **9** `OFF-GLASS`, exit **10** with `-Strict` when any node is `CLIPPED` in
  this frame. A target leaving the glass is partly untappable: a usability defect, fixed as one.
- `wear-ink-clip.ps1` - exit **0** clean, **9** ink outside the glass, **2** could not verify. Only
  this one proves or refutes Play's claim.

Exit **2** is a verdict of its own and must not be read as a pass: a capture taken while the splash
still animates is a single colour end to end and would satisfy every pixel test for the wrong
reason. Contract suite: `scripts/devtest/wear-ink-clip.tests/Run-Tests.ps1`, which draws its frames
and needs no device.

`log` picks lines by process id, so the app's own Timber output survives even though Timber tags
a line with the class name and never with the package (S1332); the package-text arm remains, and is
what keeps the system-side lines about the app. A `WARN` verdict instead of `OK` means the filter
suppressed lines your pattern did match - the full capture under `temp/scratch/` still holds them and
is the fallback. A plain `OK 0 line(s)` therefore now means what it says.

Run `.\a.ps1 adb` (no verb) for the full verb list. Direct form:
`pwsh -NoProfile -File scripts/devtest/adb.ps1 <verb> [options]`. This is the manual-work
layer; the Maestro MCP server drives agent UI walks, Maestro flows run repeatable ones
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

# PER-FLAVOR PROOF - every flavor, no dedicated letter needed
.\a.ps1 fc -Flavor Lite         # also: Standard | NoLegal | Photos | Legacy | Vr | Foss
.\a.ps1 fc -Flavor Legacy       # covers minSdk 23
.\a.ps1 fc -Flavor Vr           # the only check that compiles src/vr

# WEAR MODULE - fk/fr/fc/fu never look at it, they exit 0 having checked app_v2
.\a.ps1 fw                      # Kotlin changes under wear/
.\a.ps1 fwr                     # resources/manifest changes under wear/
.\a.ps1 fwu                     # unit tests under wear/src/test
.\a.ps1 faw                     # instrumented tests compile under wear/src/androidTest (S2355)
.\a.ps1 fwm                     # Room migration tests run on connected watch (S2355)

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
5. `.\a.ps1 fc -Flavor <name>` per affected flavor when a change touches flavor-visible resources or flavor source sets. This is what satisfies a spec demanding proof on "every affected variant" - every flavor is reachable and each call takes `BUILD.LOCK`, so the requirement never needs a direct `gradlew` call or a deferral (S1589; S1568 deferred it only because the flag was undocumented).
6. `.\a.ps1 d` only when you need APK packaging / installable artifact proof.

**Pick the rung by module first, not by change type (S1807).** Every rung above checks `app_v2`. A change under `wear/` is proved by `.\a.ps1 fw` (Kotlin), `.\a.ps1 fwr` (resources/manifest), `.\a.ps1 fwu` (unit tests), `.\a.ps1 faw` (instrumented test compile) and `.\a.ps1 fwm` (connected Room migration tests); the phone target exits 0 without compiling a single watch file, so quoting it under a wear ticket records a verdict about the other module. A change touching both modules needs one rung from each column.

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
   C:\Users\<user>\.gradle\caches\<ver>\transforms\..\okhttp3-integration-5.0.7-api.jar!\..\GlideIndexer_..class
   and P:\ANDROID\FastMediaSorter_mob_v2\app_v2
```

Cause: KSP2's incremental bookkeeping relativizes every classpath entry against the module directory. On a Windows host whose Gradle cache and project sit on different drives, `Path.relativize` throws on the cross-root pair. Nothing about the touched source matters - the failure lands while walking a dependency jar.

`gradle.properties` therefore carries `ksp.incremental=false`. Do not remove it to "speed builds up":

- KSP1 is not a fallback. `ksp.useKSP2=false` fails at configuration time with `KSP1 is no longer available` - the plugin ships KSP2 only.
- The cost is small and measured: a no-change run stays `UP-TO-DATE` at ~2 s, a one-file edit costs ~24 s. Only the first build after flipping the property pays a full pass (~2 min).
- The line is inert wherever the cache and project share a root (Linux CI, or a same-drive Windows layout).

A same-root layout (`GRADLE_USER_HOME` on the project's drive) also avoids the crash, but that is a machine-specific absolute path - the same reason `org.gradle.java.home` is not committed, see the header of `gradle.properties`.

### The closure preflight - S2872

`pwsh -NoProfile -File scripts/utils/preflight-checks.ps1` asks once, at the end of a session, what coordination state you are about to leave behind: `FMS_AGENT_ID` set, a `kind=session` start line posted, no lock domain still held by you, no lock queue still carrying your ticket, no ticket lease still claimed by you. Each finding prints the remedy command. Exit 0 clean, 1 something is leaked, 2 the harness could not be loaded; `-Json` for a machine-readable summary, `-Quiet` for failures only. It mutates nothing - a preflight that silently repairs what it found hides the leak from the next run.

It is written for a runtime with no hooks (`docs/NON_CLAUDE_RUNTIME_RULES.md`), but four of its five checks read the lock and lease stores described below, so a Claude Code session that leaked the same lock is caught by the same call.

Two things it deliberately does not do. It judges **no working-tree state** - that is `scripts/post-change.ps1 -Files "a,b" -ScopeToFile` for a change and `.\a.ps1 fg` for the fast static gates, and a third copy of the gate set here would drift from both. And it parses **no command history**: nothing outside Claude Code writes one where this repository could read it, which is why the rule sheet's "query the catalogue before grepping `.kt`" has no backstop here - the marker-versus-dirty-`.kt` advisory that was planned fired on 38 standing modified files, this tree's normal state, and a check that always fires teaches the reader to skip the verdict.

Contract suite: `scripts/utils/preflight-checks.tests/Run-Tests.ps1`.

### Concurrent-agent locks, split by domain - S1338, S2109

A coordination resource is a **pair: type plus domain**, not one global word. Both types are driven through `scripts/utils/agent-lock.ps1`, and every domain that exists is declared in one table, `locks.domains` in `.sza-profile.json`, with the path mapping beside it in `locks.pathRules`. The canon harness validates types and ranks and every entry point - resolve, acquire, status, queue, release - reads that table (S2697), so adding a domain is a profile row, not an edit in each entry point. The rows below are the current profile, not a closed list.

| Domain | Covers | Derived from |
| --- | --- | --- |
| `Build.Phone` | gradle work on `app_v2`, every flavor | the module the entry point builds |
| `Build.Wear` | gradle work on `wear` | the module the entry point builds |
| `Code.Phone` | edits under `app_v2/` | the changed path set |
| `Code.Wear` | edits under `wear/` | the changed path set |
| `Code.Scripts` | edits to `scripts/`, `dev/`, `docs/`, `.claude/`, `.github/`, the root agent files and `a.ps1`; plus the content trees `play/`, `fastlane/`, `store_assets/`, `delivery/`, `maestro/` and the root site pages, documents and icons (S2342) | the changed path set |
| `Code.Fixture` | lock-test scratch under `temp/lock-fixture/` only (S2697) | the changed path set |
| *(no domain)* | edits under `PLAN/` (S2338) or the rest of `temp/` (S2710) - the two exemptions | the changed path set |

**The test is "is this path already serialised by something finer", not "is it source"** (S2338). The domain lock exists to order what nothing else orders, so a path some other mechanism already makes exclusive does not need it - and `PLAN/` is exclusive twice over. A spec file and its phase folder belong to exactly one ticket, and a ticket is held exclusively by `ticket-lease.ps1` (atomic claim, exit 3 to the loser), so two sessions cannot reach one spec file at all; the journals and both release files are written only through the catalog mutators, every one of which holds `Enter-CatalogLock`. `docs/` and `dev/` are deliberately **not** exempt by the same test: they are hand-edited prose with nothing finer over them, so a concurrent edit there is an ordinary lost update. A PLAN-only changed set therefore resolves to no domain at all, and `enter-code-lock.ps1` reports that and exits 0 with nothing to release. Measured 2026-09-02 over the last 397 dev-log rows: 217 (55%) touched `PLAN/` and nothing else, so before the exemption the majority of closures took a domain that protected nothing while serialising every other `scripts/` and `docs/` edit in the repository. Callers must handle the empty set, which was unreachable before this ticket.

**`temp/` is exempt too, by the opposite half of the same test** (S2710). `PLAN/` is exempt because something finer already orders it; `temp/` is exempt because there is nothing there to order - Rule 1 makes it the scratch root for artifacts, backups, logs, per-ticket work and throwaway sandboxes, none of which any second session hand-edits as shared text. Before the rule existed no pattern named `temp/`, so a path under it matched no anchored prefix and fell through to the full set: writing one disposable file took `Code.Phone`, `Code.Wear` and `Code.Scripts` at once. Measured 2026-09-07: `assert-always-loaded-budget`'s contract suite failed its ratchet case with exit 4 while the domain was free and only its queue held by a foreign session, making a gate's verdict depend on a sibling's queue. The lock files and queues at the `temp/` root are unaffected - the mechanism writes those with its own primitives, never through the code lock.

**A lock test takes `Code.Fixture`, never a production domain** (S2697). A contract suite for the lock mechanism has to acquire a real domain - the store cannot be sandboxed, see the header of `scripts/utils/code-lock-scope.tests/Run-Tests.ps1` - and until this ticket it acquired `Code.Scripts`, the hottest domain in the repository. Measured by S2693 over three days: 14 of 63 `Code.Scripts` queue handoffs (22%) were that suite, so a real session waited behind a test. The profile now maps `^temp/lock-fixture/` to `Code.Fixture` ahead of the `temp/` exemption, the suite's acquiring cases target that path, and its last case asserts it left `Code.Scripts` untouched. A new lock test targets the same directory. This needed a canon change first: until plugin `2026.913.1` the timings table still validated names against a hardcoded list, so a domain declared only in the profile resolved correctly and was then refused by acquire. The same release writes queue handoffs as schema 2 with a normalized `paths` array (schema-1 files stay readable), which is the input for grouping waits by source subtree before any further split of `Code.Scripts`.

**Content with no code in it takes `Code.Scripts`, not the full set** (S2342). Fail-closed exists for a path that *might* belong to a module - over-protecting an unknown one is the safe direction to be wrong. A store listing, a site page, a Fastlane metadata file or a root licence cannot belong to a module in principle: none of them compiles, links or packs into an APK. Until this ticket they all fell through to `return $full`, so a one-line edit to the Play listing serialised phone and watch work it could not conflict with - observed in S2340 phase 03, where a set of one repository script plus one listing file queued behind a wear session. Measured 2026-09-02 over the last 400 dev-log rows: the full code set was taken 11 times, 9 of those sets touched content and 8 were content **only**, so the expensive serialisation was spent almost entirely on paths with nothing to serialise. The branch was read off a full listing of the repository root rather than extended one directory per finding, and the remainder is asserted rather than assumed: `corex/` (unrecognised source) and the modules `benchmark/` and `watchface/` still take every code domain. Those two are real Gradle modules with no `Build.*` domain of their own, so giving them a code domain is a boundary decision - a table row plus its own build lock - and is deliberately not made here.

**A free `Build.*` domain does not mean a free build directory** (S2584). The lock answers "who holds the domain" by the wrapper's pid; occupancy of the build tree is a different question, because the process that holds `app_v2/build/` open is a **test worker the wrapper spawned** - not the wrapper, not the daemon, and not listed by `gradlew --status` at all. A worker that hangs outlives the run that created it, keeps `compile_and_runtime_r_class_jar/<variant>/**/R.jar` open, and every later entry point for that variant dies rewriting it: `java.io.IOException: Couldn't delete .. R.jar`. Measured twice on 2026-09-05 - one holder kept the jar 102 minutes while ten sessions queued behind a domain that correctly reported `absent (free)`, each getting its grant and failing on the first resource task. So `check-standard-fast.ps1` judges the directory the one way that worked in both incidents: it opens the file (`FileShare::None`) before the run and again when a run fails naming that jar, and on a held handle it prints the holder - pid, start, CPU - and exits **2**, this repository's "could not verify", never 1. The distinction is the whole point: exit 1 reads as "your change is broken" and sends the reader to edit working code, which is what both incidents cost. `-Mode Code` is deliberately exempt, since `compile*Kotlin` never rewrites the jar, which makes `fk`/`fkn`/`fw` the only checks that still return a verdict during such an incident. Rule 35 still forbids killing a process this session did not start - the reaper is `scripts/utils/agent-watchdog.ps1`, and the diagnosis says to check whether it is running before sweeping by hand. Helper and its contract suite: `scripts/builders/build-output-holder.ps1`.

**A run reaps the workers it spawned, before it frees the domain** (S2585). The paragraph above keeps a session from mistaking a foreign holder for its own broken code; this one keeps a session from becoming that holder. `& gradlew.bat` is not a process group on Windows - the launcher client and the forked test worker are independent processes - so a wrapper that exits leaves them running, which is why `agent-watchdog.ps1` carries an orphaned-client reaper at all. Every `-Mode Unit` run therefore ends by listing the workers under its own task's `org.gradle.internal.worker.tmpdir=<root>\<module>\build\tmp\<task>\work` and stopping each one **while `Build.*` is still held**. That order is the whole safety argument: under the held domain only one task can be running in that build directory, so a worker beneath that task's tmpdir belongs to this run rather than to somebody else, which is what keeps the reap clear of Rule 35. Gradle has already returned by then, so a survivor is an orphan and not work in progress. The exit a `finally` cannot see - the wrapper killed outright, the suspected shape of the 2026-09-05 incident where the client had been alive 83 minutes with its wrapper gone - is covered by a record at `temp/GRADLE-RUN/run-<pid>.json`, written before the run and deleted in that same `finally`: a record whose pid no longer exists is an orphaned run with no age threshold, no CPU sample and no guess about what counts as build machinery, which is exactly what the watchdog's reaper must fall back on and why it cannot act for 25 minutes. **The task timeout beside it is not a second reaper and kills no worker**: `fms.unitTestTimeoutMinutes` in `gradle.properties` (20, one value read by both modules so they cannot drift apart) bounds the *task*, so a hung run stops holding the domain and the wrapper reaches the `finally` that does the reaping - `dev/REFUTED_APPROACHES.md` records why no timeout can do more than that. Helper and its contract suite: `scripts/builders/gradle-worker-reaper.ps1`.

**`dev/CHANGELOG.md` carries its own mutex, not the domain lock** (S2338). `scripts/add_to_dev_log.ps1` appends by read-modify-write - it scans recent rows for a duplicate, decides, then appends - and until this ticket that critical section was covered only incidentally, by the `Code.Scripts` lock a closure happened to hold because `dev/` is in the prefix list. With PLAN-only closures no longer taking any domain, the cover would have vanished for most writers, so the script now takes a per-checkout `Global\FMS-DevLog-<hash>` mutex around the scan and the append. Same shape and same reason as the spec catalog (S1437) and the feature inventory (S1537), which measured eight concurrent unlocked writers landing four records; verified here at 8 of 8. A system mutex rather than a lock file, because an append is milliseconds while the BUILD/CODE family is sized for 3-60 minute edit windows with queue directories and reservations.

Two sessions contend only where their domains overlap. A watch edit, a phone edit and a scripts edit therefore proceed at the same time, and so do `.\a.ps1 fw` and `.\a.ps1 fk` - measured 2026-08-27 at 12 s wall for both, with no queue wait and no cache-contention message in either log.

**The queue can be measured after the fact, and an argument about it should be** (S2606). Nothing journals it - a ticket is deleted the moment its turn comes and `lock-status.ps1` answers only for the present second - but the history is recoverable from the logs already in `temp/`, because `check-standard-fast.ps1` stamps its log file name **before** `Enter-BuildLockOrExit` and writes the `Date:` header line right **after** the acquire, so the difference is that run's time in the queue. `pwsh -NoProfile -File scripts/utils/measure-build-lock-wait.ps1` reads that out: waits per mode, the short-versus-long hold-class split, which class held the domain while a short check waited, runs that took the domain and then wrote nothing but their header, and any two runs holding one domain at once - the last of these exits **1**, since it would mean mutual exclusion failed. `-Since` narrows the window, `-Json` gives the aggregates. Its two blind spots come from the method, not the queue: a holder that writes no fast-check log (`assert-detekt.ps1`, `build-debug.PS1`, a gradle assemble) can be waited for but never seen, so its share reads as unattributed, and a hold's end is the log's last write, which under-reports exactly the run that hangs after its last line - so a detected overlap is real while an absent one is only probably absent. Measured 2026-09-05 over 1281 runs: 0 overlapping holds, handovers of 1-2 s, and the 3600 s wait budget bound not once - which is why `dev/REFUTED_APPROACHES.md` carries a per-hold-class wait budget as refuted rather than pending.

- **A run that must outlive the session takes no lock of its own** (S2400). `scripts/utils/start-detached.ps1` starts a command as a hidden, parent-independent process with its log and exit marker under `temp/<ticket|scratch>/` - the route for a full Maestro sweep or any job longer than the session, since the harness's background task dies with the session. The launcher acquires nothing: whatever build or code domain the command needs, the caller takes and releases it, exactly as for a foreground run. Classification of what may go to the background at all: `docs/BUILD_TEST_FAST_PATH.md` "Verdict or work".

**The domain is derived, not declared** (ADR-1). `enter-code-lock.ps1 -Files "<changed paths>"` maps the set through `Resolve-CodeDomainsForPaths`; a gradle entry point derives its domain from the module it already builds (`check-standard-fast.ps1` from `-Module`, now via the registry row in `scripts/utils/gradle-modules.ps1`, so a module with no domain of its own widens to both rather than defaulting to the phone's; `assert-detekt.ps1` from `-Module`, or both domains when it runs without one). `-Domain` exists as an escape hatch and is second-class on purpose: a wrongly declared domain silently removes protection while still looking like working coordination, whereas a wrongly derived one is visible in the file set the call already prints.

**Anything that does not decompose takes the full set** (ADR-2), so the failure direction is over-protection rather than under-protection: a build file in either module or at the root, a path the table does not recognise, a module added later, or a call that names no file set at all. A module's own `build.gradle.kts` deliberately belongs to the full set rather than to its module - the configuration phase processes every subproject, so a broken build file in one module fails a check requested for the other. The shared static-analysis config (`gradle/`, `lint-rules/`, `config/detekt/detekt.yml` and its siblings) is judged the same way; the per-module detekt **baselines** are the one carve-out, because `baseline-app_v2*` and `baseline-wear*` are named for their module and read by that module's check alone. That carve-out is not cosmetic: regenerating a baseline is a by-product of most Kotlin closures, so failing closed on it bought no protection and silently cost the split on the majority of tickets - observed 2026-08-31, a one-file `app_v2` edit plus its baseline took all three code domains. Over-protection is the safe direction to be wrong, but only where it protects something.

**Multi-domain work is all-or-nothing, in canonical order.** A set is taken in the table's fixed rank, and a domain that cannot be taken releases every domain already taken in that call. Both halves matter: a hand-picked order lets two overlapping sets block each other with no timeout to break it, and a caller left holding half a set blocks every overlapping session for the whole length of its own wait. A multi-domain waiter is granted only when its ticket is head in **every** domain of its set - head in one and second in another is exactly the state that livelocks two overlapping waiters.

**State written before the split is honoured** (strategic 3.2). Coordination files outlive a session, so a sibling may hold a pre-split `temp/BUILD.LOCK` or `temp/CODE.LOCK` at the moment the split lands. Those files name no domain, so the only safe reading is the widest one: a pre-split lock holds **every** domain of its type until its owner releases it or today's rules judge it stale, and a ticket left in a pre-split queue is a place in every domain of its type, ordered by its original sequence number. The first time such a file is honoured in a process, it says so on one line. Releasing one is the other half of the same rule and just as necessary - adoption that blocks without releasing converts every in-flight holder into a stall that only the staleness window ends - so a **bare** name releases the pre-split file of its type, while a single domain never does, because that file covers domains the caller did not take.

The two types, and how each is taken:

- **Build domains** - acquired by `Enter-BuildLockOrExit -Domain <..>` before any direct `gradlew`/`gradlew.bat` invocation, released by `Exit-AgentLock -Name Build -Domains <..>` after (success or failure). A caller that names no domain still takes both, so a script nobody has taught its module keeps serialising exactly as it did before the split. Since S1432 a busy domain **queues** the caller instead of refusing: it takes a ticket, reports its position and starts when its turn comes. Pass `-NoWait` (or set `FMS_LOCK_NO_WAIT=1`) where an immediate answer matters more than a turn.
- **Code domains** - acquired via `scripts/utils/enter-code-lock.ps1 -Files "<changed paths>" -Reason "<ticket/skill>"` before a multi-file source edit (Kotlin/XML/build-file). Since S1432 a busy domain queues the caller and **exits 4** ("queued, not yet your turn") rather than waving the edit through. **The caller releases it, and `post-change.ps1` is only the backstop** (S2419): the window closes the moment the step's last file is written, released by the caller's own `scripts/utils/exit-code-lock.ps1` - the verification predicates, `plan-tick.ps1`, the phase's `Project compiles` build (already serialised by `Build.*`), the unit suite and the whole gate batch run outside it. Until 2026-09-03 every text promised the facade's trailing `finally` instead, and measured over `temp/AGENT-CHAT` for 2026-09-02 21:30 .. 2026-09-03 01:20 that cost a 95 s median hold on `Code.Scripts` with a 703 s maximum, all 51 queue waits in the window on that one domain, at a depth of ten - while the closure's own gate batch was 19.0-48.8 s of it. `post-change.ps1` still releases, now before its gates rather than after them, but only as the backstop for a run that ended early; it frees exactly the domains the run actually holds - the union of what its change set maps to and what this session owns - so a scripts-only closure by a session that took the full set does not leave two domains held for nobody. That release is owner-checked per domain, so it never removes a lock belonging to another live session; a skill that skips the facade (`/skill-fix`) must call `exit-code-lock.ps1` itself when the edit is done.

**A gradle task name in a repository script carries its module segment** (S2172). Write `:app_v2:assembleStandardDebug`, never `assembleStandardDebug`. This is not a spelling preference: an unqualified name is expanded by Gradle across **every** project in the build that declares it, so its meaning is set by the composition of the build rather than by the script that passes it. When S2090 gave the watch its own `standard` / `noLegal` dimension, forty call sites silently began building the watch as well, and not one of them was edited - measured 2026-08-27, `gradlew assembleStandardDebug --dry-run` scheduled 48 `:wear:` tasks beside 53 `:app_v2:` ones, while `:app_v2:assembleStandardDebug` scheduled none. This is the one way a correctly derived `-Domain` still under-protects, because the domain follows the module the entry point *believes* it builds: the caller holds `Build.Phone` and writes into `wear/build/**`, so a sibling's watch build dies on a locked `R.jar` with an error that reads as broken code rather than as contention. A watch artifact built by a phone task also inherits the phone's `versionCode`. Gate: `scripts/quality/assert-qualified-gradle-tasks.ps1`, in the fast-gates batch and so in every closure. S2175 extended the same gate to `.github/workflows/*.yml` - the CI workflows called `gradlew` with the identical unqualified shape, and a `.ps1`-only scanner could not see it.

**Releasing a wedged lock:** `.\a.ps1 ub` (build) and `.\a.ps1 uc` (code) are the launcher shortcuts for `scripts/utils/clear-agent-lock.ps1`. Both are conservative - a lock whose holder is still live is refused, and the holder's pid, age, reason and session id are printed instead, because clearing it would hand the turn to the next agent mid-edit. `.\a.ps1 uc -Force` overrides once the holder is confirmed gone (check the session's transcript mtime - the `subagents/` subtree included, S2408 - not the pid, since a code-domain pid can be recycled), and drops the whole queue with it, including any ticket your own background waiter is holding.

#### Device leases - S1926

The third contended resource, and the last one to get an arbiter. `adb devices` reports an emulator as online whether or not somebody is mid-run on it, so before this a session discovered the conflict by breaking something: installing its APK, or switching HOME, out from under a running scenario (observed 2026-08-21 in S1895).

```powershell
# Take / give back a specific device
pwsh -NoProfile -File scripts/devtest/device-lease.ps1 -Verb Claim   -Id emulator-5554 -Reason "/spec-test-device S1234"
pwsh -NoProfile -File scripts/devtest/device-lease.ps1 -Verb Release -Id emulator-5554

# Who holds what
pwsh -NoProfile -File scripts/devtest/device-lease.ps1 -Verb Status
```

Exit codes match the ticket lease exactly, because it is the ticket lease's shape rather than the build lock's: **0** done, **1** error, **3** claim lost (a live sibling got there first - take a different device, this is not a fault), **4** release refused (a live foreign session owns it). One file per lease under `temp/DEVICE.LEASES/<serial>.json`, and the claim is an atomic file creation, so two sessions racing for one device cannot both win. Both store paths and the `:` -> `_` serial encoding are declared once, in `scripts/devtest/lib/device-store-paths.ps1` (S3036), and read from there by every production caller - the two CLIs, the readiness probe `device-ready.ps1` and the monitor writer. Renaming either directory is therefore one edit in that file, and it reaches the archiver's protected set through `scripts/utils/temp-root-inventory.ps1`, which derives both names rather than listing them; `scripts/devtest/lib/device-store-paths.tests/` case E7 fails if a rename reaches the declaration but not that protection. The path is NOT a `.sza-profile.json` key, and that is a decision rather than an omission: the canon-shipped harness reads no device path at all, and `Get-SzaProfileValue` throws on an unknown key by design (S2705), so a project-only key would be unreadable until the canon declared one it never reads.

**There is deliberately no queue.** A build finishes on its own in minutes, so waiting for `BUILD.LOCK` terminates; a sibling's device scenario can run arbitrarily long, so waiting for a device does not. A taken device is a reason to defer the device stage, not to block on it.

**Eviction is by session liveness, with no watchdog** - whoever reads next sweeps, matching S1432. The liveness rule itself is not restated in the lease script: it comes from `Get-AgentTicketLiveness`, and the timings from `$Script:AgentLockTimings.Device` (45-minute silence window, matching the ticket lease because a session building and installing an APK writes nothing for a long time; 120-minute absolute ceiling, far below the ticket lease's 480 because a device is held for a scenario rather than for a ticket's whole life).

**The readiness probe consults it only when asked.** `device-ready.ps1 -ClaimFree` walks the online devices and keeps the first it can claim, turning the old `multiple-devices` refusal into a selection; `all-devices-leased` (statusCode 7) is a distinct answer from `no-device`, because "nothing to test on" ends the device stage while "everyone else is on them" means retry later. Without the switch the probe answers exactly as it always has - existing sessions do not change behaviour underneath themselves.

Like every other lock here, this is **advisory**: it coordinates consenting callers and does not stop a raw `adb` command, exactly as `BUILD.LOCK` does not stop a raw `gradlew`.

**The queue (S1432).** Each DOMAIN has its own queue directory `temp/<DOMAIN>.QUEUE` holding one ticket file per waiter, numbered in order. The head of the queue owns the turn: a free lock is **not** enough to acquire, because a live head that has not yet spent its reservation window (5 min for Build, 1 for Code) still owns it - that window is what survives the gap between "your turn" and the moment gradle actually starts. Ownership of a ticket belongs to an agent **session**, not a process - with one qualification since S2577, below: on a build domain the enqueueing process is also the waiter, so its death evicts the ticket even while the session lives. A ticket whose owner has gone quiet, or which passed its ceiling (60 min Build, 20 min Code), is evicted by whoever reads the queue next. Every timing lives in one table, `$Script:AgentLockTimings`.

**Queue fairness and liveness (S1448).** Four rules make the queue actually hand out turns in order, each of them fixing an observed starvation where a session sat still for tens of minutes without a single error:

- **Taking a lock retires every ticket of the acquiring session**, not only the ticket handed to the acquire. Otherwise a session working step by step - take lock, close step, immediately queue for the next one - leaves the previous step's ticket parked on the head *while it holds the lock*, and nobody behind it can ever advance.
- **The turn is decided by ticket identity, never by session identity.** A caller holding no ticket is answered from the lock and the head's reservation; it can no longer inherit the turn just because the head happens to belong to its own session. `enter-code-lock.ps1` therefore takes its place in the queue **before** it asks for the lock, exactly as `Enter-BuildLockOrExit` already did - so a session that releases and immediately wants the lock back queues behind whoever was already waiting. A re-entrant call from a session that already holds the lock is recognised and returns 0 without queueing.
- **A superset request tops up rather than re-queuing, but only in one direction** (S2200). The re-entrancy check above only fired when the requested set was *identical* to what the session already held - a session holding `Code.Wear` alone that then also needs `Code.Phone` fell through to the ordinary acquire path, which has no self-ownership check at all: it saw its own `Code.Wear` lock as "busy" and queued behind it, a wait nothing can ever end from the outside. `Enter-AgentLockDomain` still has no such check; instead `enter-code-lock.ps1` now splits the request into `Held` (already this session's) and `Missing` before touching the queue. Safety of granting `Missing` without releasing `Held` depends on canonical rank, not on self-ownership alone: it is safe exactly when every held domain outranks every missing one (`Code.Phone` < `Code.Wear` < `Code.Scripts`) - continuing upward through the table is equivalent to a fresh multi-domain acquire that already completed its first steps, so it inherits that acquire's deadlock-freedom. The other direction - holding a higher-ranked domain while a lower-ranked one is still missing - is refused outright (exit 4, nothing enqueued) with a message naming the self-collision and the recourse (`exit-code-lock.ps1` then retake the full set), because granting it would let a symmetric session holding the low-ranked domain deadlock against this one. `scripts/utils/agent-lock.ps1`'s `Resolve-AgentLockTopUp` is the single place this split is decided.
- **A waiting ticket carries its own heartbeat.** Liveness reads `lastSeenAt` first (stamped by `wait-for-lock-turn.ps1` on every poll), the owning session's transcript second, the enqueue time last. The transcript alone punished exactly the behaviour the contract demands: a session that queues, backgrounds the waiter and goes off to do lock-free work writes nothing, looked dead at the 15-minute mark, and was evicted from a place it had earned. **An abandoned head does not age out** (S2098, correcting what this line claimed before): `TicketCeilingMinutes` is declared for `Build` and `Code` but read by no queue consumer - only `ticket-lease.ps1` and `device-lease.ps1` apply the field, and `Remove-StaleAgentLockTickets` judges the owner, never the ticket's age. That is deliberate. A legitimate wait behind one long build, or behind several queued builds, outlasts both numbers, so applying them would evict a session waiting exactly as the contract demands - `scripts/utils/test-agent-lock-queue.ps1` asserts that survival. The remedy for a dropped intent is therefore explicit withdrawal, below, not a timer.
- **One head does age out: the one that was told to go and never went** (S2194). `Remove-StaleAgentLockTickets` carries a second, narrow reason to drop a ticket - **forfeit** - and it applies only to a queue **head** whose `turnGrantedAt` is older than that domain's `ReservationMinutes`, which does not hold the lock, and which is not the sweeping session's own. It is not the ticket-age timer the bullet above rules out: it reads `ReservationMinutes`, never `TicketCeilingMinutes` or `SessionStaleMinutes`, and it judges an **already-granted turn** rather than a wait, so a ticket that was never granted one survives any amount of waiting - `test-agent-lock-queue.ps1` asserts both boundaries. Safe because it fires only after the reservation expired, at which point the head holds no privilege anyway: `Test-AgentLockTurn` is already answering "your turn" to whoever asks. **That safety argument assumed a FREE lock and never said so, which is the hole S2421 closed.** Under a live lock held by a third session the head could not enter however hard it tried, so "granted and never taken" is simply false - measured 2026-09-03, a waiter that had polled every 5 s for 292 s lost its `Code.Scripts` place to a session that was never in the queue, and the ticket behind it went the same way on the next sweep. Two changes, both required: the forfeit is not considered at all while a live foreign lock exists, and `turnGrantedAt` is cleared on every observation of a held lock, because it records that a free lock was **observed** rather than that it stayed free - `Set-AgentTicketTurnGranted` is one-shot, so without the reset the stamp is already spent when the lock frees and the exemption would buy the head a zero-length window. Leaving it in place is what costs - every remaining waiter is told to go at once and they race for the lock file, so a later arrival can overtake an earlier one, and every inspector reports a waiter who does not exist.
- **A build ticket also ages out when its own process dies** (S2577). The third and narrowest reason in `Remove-StaleAgentLockTickets`, and the only one that reads the ticket's `pid` rather than its owning session. It exists because the two halves of the same mechanism disagreed: `Get-AgentLockStatus` judges a `Build.*` **lock** by pid liveness and reclaims it the moment the process dies, while the **ticket** of the same acquisition was judged only by its session - which kept writing, so the ticket rode to the head of the queue and held a reservation nobody was left to spend. Measured 2026-09-05 on `Build.Phone`: twelve sessions behind one hung holder, and after the holder was cleared three of the survivors had a dead pid (waited 51, 49 and 16 minutes) and had to be deleted with `Remove-Item` by hand, no path existing for it. The rule is true for build domains only, because there the enqueueing process **is** the waiter - `Enter-BuildLockOrExit` waits in-process and removes its own tickets when the wait fails - whereas `enter-code-lock.ps1` enqueues and exits 4 immediately, so a dead pid is the normal state of a perfectly live code ticket. Four conditions keep it off anyone still working, all of them mirroring what S2421 cost when this sweep last evicted a live waiter: the verdict must be `foreign-live` (never our own place, never `undetermined`), the process must be provably gone with every doubt reading as alive (`Test-AgentTicketProcessAlive`, and an unreadable `StartTime` is a doubt), no heartbeat inside `ReservationMinutes` - a live `wait-for-lock-turn.ps1` re-stamps its own pid on every poll, which is what protects a ticket inherited through session dedup or `-Handoff` - and no live turn reservation, the window in which a ticket deliberately outlives the waiter process that earned it. New tickets carry `procStart` beside `pid` so a recycled pid cannot revive a dead owner; a ticket written before this is judged by its enqueue time instead.
- **The refusal names the blocker that exists.** A lock that is held reports its holder; a lock that is free while a foreign ticket owns the head says so and names the head's session, reason, wait and reservation window. `enter-code-lock.ps1` no longer prints a `Holder:` line built from an absent lock file - the observed `Holder: session  (age 0s, reason: '')` sent readers hunting for a holder that was not there.

`lock-status.ps1 -Queue` surfaces the pathology directly: each ticket carries `heldByLockHolder`, the JSON payload carries `headOwnedByHolder`, and a text row owned by the current holder is suffixed `<- holds the lock`.

**The stalled holder - a signal, not a rule (S2413).** A holder that simply stops moving is invisible to everything above: it is not stale, so nothing evicts it, and every reader sees only half the picture. `Get-AgentLockStatus` measures the owner's silence but spends it on one held/stale answer, so the ten minutes between "still working" and "reclaimable" have no name; `agent-chat.ps1 -Verb Status` knows the silence but not the locks, and judges it against a window three times larger (45 min, from `SpecTicket.SessionStaleMinutes`), so it prints `live`; `monitor-spec-queue.ps1` prints both halves in two different sections and joins neither. Measured 2026-09-03: a session stopped at 00:19 while holding all three code domains taken at 00:17, two sessions queued behind it, and the owner spotted it by eye at 00:29. `Get-AgentLockStall -Name <domain>` is that missing join - the **`quiet-owner`** rule: **held, a queue behind it, and the owner quiet longer than that domain's `StallMinutes`** (10 for every code domain, equal to their `LockStaleMinutes`, which is why S2582 adding the field changed this rule's verdict nowhere) - and `Get-AgentLockStalls` runs it over the table. Since S2582 the predicate carries two rules and every verdict names the one that produced it in a `rule` field, because they rest on different evidence and a row that does not say which would repeat the `processAlive: True` mistake. Two exclusions keep it from being noise: an empty queue is never reported, because a quiet holder blocking nobody harms nobody; and the holder's own leftover ticket is not counted as a waiter, that being the `heldByLockHolder` shape above. A third one - a build domain is never reported, because a dead pid already makes the lock stale and the next claimant reclaims it unaided - stood until S2582 removed it; the paragraph below is why it was wrong. The threshold comes from `$Script:AgentLockTimings` (10 min for code) and is deliberately *below* the same domain's `SessionStaleMinutes` (15), so the warning arrives before the lock is even reclaimable - which is the whole point of a warning. A live holder **process** does not clear the verdict; it is printed beside it, because "hung" and "gone" cost the queue the same and differ only in what to do next. Quiet time is `Get-AgentOwnerQuietMinutes`, reading exactly the marks `Get-AgentTicketLiveness` judges by - transcript write including the subagent subtree, heartbeat, newest chat line - so the signal and the eviction can never disagree about one owner (S1621).

**The build rule - `no-cpu` (S2582).** The exclusion above rested on a premise true only of a DEAD holder: a build lock is judged by pid, so a dead process makes it stale and the next claimant takes it. A holder that is alive and hung passes that test, and then the only remaining limit is `LockStaleMinutes` - an hour. Measured 2026-09-05, `Build.Phone` was held 50 minutes by a process burning no CPU with 13 sessions queued behind it, the head of the queue unmoved for 51 minutes, and not one `STALLED` line was printed; `lock-status.ps1` said `processAlive: True`, which reads as "alive and working", and `clear-agent-lock.ps1` offered its override only "once the holder is confirmed gone" - a state a hung holder never reaches. So the predicate gained a second rule rather than a widened condition, because neither of the code rule's two ingredients transfers: **owner silence cannot judge a build**, since a session waiting on its own foreground build legitimately writes nothing for the build's whole length, and a signal that lights on every healthy build is switched off the first day. The build rule keeps the shared half - held, a foreign queue behind it - then requires, in this order, that the lock's age reach the domain's `StallMinutes`, that the holder's process be alive, and only then pays for one measurement: the holder's whole descendant tree burned CPU below an idle rate **and** no build-engine process on the machine burned CPU above a busy rate. Both halves are needed. The engine **detaches** - its parent is gone by design, so it is never a descendant of the wrapper holding the lock, and that wrapper legitimately sits at zero while the engine compiles at full speed elsewhere in the process table. The price of getting this wrong is measured, not hypothetical: the first edition of the external reaper judged the holder's tree alone and killed **24 live builds in 105 minutes**, always exactly at its threshold, while exactly one build in that window reached success. Four details carry the rest of the design. The threshold is a field of its own, `StallMinutes` in `$Script:AgentLockTimings` - 25 for build, the reaper's own value, raised there from 12 because engine startup and a cold configuration phase regularly exceed 12; 10 for every code domain, equal to their `LockStaleMinutes`, so S2413's behaviour is unchanged to the bit. The two floors are expressed as **rates** - CPU-seconds per second of window - not as constants against a fixed window, so the signal and the reaper cannot reach opposite verdicts about one holder merely by sampling for different lengths. Both sets are measured in **one** `Start-Sleep`, so a domain costs one window and both halves describe the same stretch of time, and that window is paid only after four free conditions have already held - a healthy build never reaches the threshold and never pays for it. And the engine's vocabulary - process names, the command-line pattern of a working engine, the launcher client to exclude - lives in `.sza-profile.json` under `locks.buildEngine`, never in the harness body, because the mechanism ships with the canon and the vocabulary belongs to the project; a project declaring none gets **no build verdict at all**, deliberately fail-closed, since half the predicate is unmeasurable without it and the half that remains is the one measured to be wrong on its own. Owner silence is still computed and printed for a build holder - 49 minutes in the incident, a useful second clue - but it judges nothing.

It is drawn wherever someone already looks, and only when non-empty: a red `STALLED` line under `lock-status.ps1 -Queue` naming its rule and, for `no-cpu`, the measured tree and engine CPU over the window beside the `processAlive` line rather than instead of it (plus a `stall` property in `-Json`), a `stalls` array in `Get-DevMonitorSnapshot` from which both `monitor-spec-queue.ps1` and the monitor page render one section above the locks - each row carrying its rule as a column of its own, so a build row cannot be read as a code row - the refusal in `clear-agent-lock.ps1`, which on a verdict prints it and says what actually resolves a hung holder, a line once per five minutes of waiting inside `Enter-AgentLock`, so the session queued behind the hang learns it first (there were 13 of them in the incident, each burning 5 to 51 minutes), and a line in `enter-code-lock.ps1`'s exit-4 refusal beside the holder's chat lines - the waiting session learns it is queued behind a dead holder before the operator does. **Nothing branches on it, and nothing evicts on it.** No lock, queue or lease reads the verdict, the refusal's exit code is unchanged, and waiting remains the correct response: the lock still goes stale on its own and the waiter still takes it. Eviction stays where it already lives, in the deliberately external reaper `scripts/utils/agent-watchdog.ps1`, and CLAUDE.md Rule 35 still forbids killing another session's process on the strength of a printed line. The signal only names what a human was previously left to spot.

```powershell
# Who holds it, who is waiting, in what order (this session's own ticket is marked '>')
pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build.Wear -Queue
# A bare Build or Code prints one section per domain of the set, each naming its own domain
pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code -Queue -Json

# Wait for your turn OUT OF BAND: run this as a background task and keep working.
# -Acquire takes the lock in the waiter itself, so its exit means the lock is already yours.
pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name Code.Phone -Reason "S0900 edit" -Acquire
```

**Every domain at a glance:** `.\a.ps1 rm` (`scripts/utils/monitor-spec-queue.ps1`) prints one line per domain with its holder and the tickets behind it, collapsing the idle domains into a single `free` line. Two properties are worth knowing before reading it. It **writes nothing** - unlike `lock-status.ps1`, it never evicts a stale ticket, so a queue entry it shows may be one the next acquire would sweep away; that is why every ticket row carries both its wait and its last heartbeat, and a long wait with a cold heartbeat is an abandoned intent, not a working sibling. And it takes the domain names from `agent-lock-domains.ps1` rather than listing them, which is the fix for what S2170 found: the section had kept naming the two pre-split files that nothing writes any more, so it reported "free" while three domains were held.

`wait-for-lock-turn.ps1` takes a ticket, blocks, and **exits** the moment the turn arrives - its exit is the "your turn" signal, which is the only channel through which an external event returns an agent to work. The ticket deliberately survives that exit: the caller inherits it, protected by the reservation window, and passes it to `Enter-AgentLock -Ticket`. Exit codes: **0** granted, **2** timed out, **3** ticket evicted while waiting, **4** could not enqueue. Do not read the verdict from the exit code a background task reports - that is the exit of the last command in the launch line, and it has already turned a refused build into an apparently green one. Read the marker instead: `temp/<DOMAIN>.TURN-<sessionId>.json`, one per domain of the set, carrying `outcome` (`granted` / `timeout` / `evicted` / `enqueue-failed`), the ticket number and how long the wait took.

**Let the waiter take the lock: `-Acquire`.** Without it the waiter exits on the grant and the lock is claimed by the caller's NEXT call - a model round trip, and that round trip is spent out of the head's reservation window while the lock sits free. Measured on `Code.Scripts` 2026-09-03 from the agent chat: the two handovers where the granted head had to come back through a chat turn left the free lock idle 3m03s and 3m07s, the full `ReservationMinutes`, after which S2194 forfeited the head and the queue moved on; the handovers where the winner was already polling took 9-16 s. `-Acquire` chains `Enter-AgentLock` onto the poll that observed the turn, so a freed domain is held again within one poll interval (5 s) at no token cost, and the caller's `enter-code-lock` re-run becomes a re-entrant no-op - the release is still owed exactly as before. The marker's `outcome` reads `acquired` rather than `granted`. It degrades to a plain wait, with a warning, in the two cases where the lock would outlive the process that took it: a **Build** domain, whose staleness is judged by the acquiring PID (the waiter exits at once, so the lock would read as dead on arrival), and a **`pid-<PID>` session identity**, which makes every later process of the same session a stranger to the lock it just took.

**Carry the ticket in the handoff (S2403).** `enter-code-lock`'s exit-4 message writes `temp/LOCK-HANDOFF/HANDOFF-<..>.json` and prints both follow-up commands with `-Handoff <path>` - the waiter command and the post-grant re-run of `enter-code-lock`. Pass the printed path verbatim. In a runtime with no session id the waiter and the re-run are different pwsh processes and strangers to the first ticket's pid identity, so without the handoff each takes a SECOND ticket for the same intent and the waiter then waits out the reservation window behind its own dead first ticket - observed live as `#1` (enter-code-lock) beside `#2` (waiter) and a grant 3 minutes after the lock freed. An absent, expired or consumed handoff degrades to a fresh ticket, which is the pre-S2403 behaviour.

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

Staleness is judged by the holder's own liveness, never by a guessed timeout while the holder is still working. `BUILD.LOCK` has a real process, so it is judged by PID liveness (with a start-time check against PID reuse). `CODE.LOCK` has no process - an editing turn is not one continuous process - so since S1432 it is judged by its owning **session**: a live owner keeps the lock however long the edit takes, because expiring a working session by the clock would hand its turn to the next agent mid-edit.

**"No process" is stronger than it sounds, and the file still records a pid (S2623).** A code lock's `pid` names the `enter-code-lock.ps1` process, which acquires and `exit 0`s in the same breath - a later `exit-code-lock.ps1` process releases - so it is dead milliseconds after a perfectly valid acquire. Measured 2026-09-06 in a redirected sandbox: the lock was one second old, `HELD` and correct, and its recorded pid resolved to nothing. **A dead holder pid is therefore the normal state of every code lock and evidence of nothing**, which is why `Get-AgentLockStatus` leaves `ProcessAlive` null on these domains rather than computing an answer that could only mislead - and why reclaiming on it would let any session take any code domain the instant after it was granted, which is not a mutex. The cost of leaving that unsaid was paid before it was written down: a session read the dead pid off a jammed `Code.Scripts`, reported a leaked lock, and the owner it accused was alive and closed its own ticket twenty minutes later. So `lock-status.ps1` now labels the pid as the acquiring process and prints the line that does decide - `owner: <sessionId> (<liveness verdict>)`, straight from `Get-AgentLockStatus`'s `OwnerLiveness` - because the previous shape put a meaningless number where the reader looks first and the deciding one nowhere at all. `dev/REFUTED_APPROACHES.md` carries the refutation for anyone who proposes the eviction again.

**What counts as a live session, in order (S2408 fixes the order and adds the first entry).** `Get-AgentTicketLiveness` asks, strongest first: (1) is the owner's own process running, when the id names one (`host-` or `pid-`) - checked against the record's own write time so a recycled pid cannot revive a dead owner; (2) the ticket's `lastSeenAt` heartbeat; (3) the session transcript's write time, which since S2408 means the newest of `<session>.jsonl` **and** `<session>/subagents/**/*.jsonl`; (4) the newest chat line (S2372 ADR-7); (5) the enqueue time. Every one of them can only answer "live" - none of them can make a holder stale that the clock would have kept - so adding a signal never adds an eviction. The subagent subtree is there because a session that delegates writes nothing to its own transcript for the whole run: measured 2026-09-03, a six-minute gap on a session editing continuously, and the day before, eleven such minutes cost a live session its `Code.Scripts` lock at the ten-minute window while a sibling took the domain on top of it. `Get-LeaseQuietMinutes` reads signals 1 and 3 through the same two helpers (`Test-AgentIdentityProcessAlive`, `Get-AgentSessionTranscriptLastWrite`), so the lease and the lock cannot disagree about one session (S1621). A lock written before S1432 carries no session id and still expires by wall clock, so old files read correctly. A build script that finds `CODE.LOCK` fresh still only warns - it never refuses - so a session that legitimately needs to build while someone else edits cannot be deadlocked.

**Unnamed holders carry a `pid-NNNN` owner (S2371).** A session whose environment has no session id is stamped on the lock through the same accessor the queue ticket uses, so the lock and the ticket of one acquisition name the same `pid-NNNN` identity instead of diverging into `null` versus `pid-NNNN` - the divergence made a session unable to recognise its own lock and queue behind itself. A pid identity lives exactly one pwsh process, while acquire and release are deliberately different processes, so the owner-checked release treats an unnamed holder's lock as releasable by an unnamed caller (the pre-S2371 advisory semantics - a null owner was releasable by anyone) and as untouchable by a NAMED session until staleness takes it. Liveness for a pid owner has no transcript and degrades to the heartbeat/wall-clock chain, which also bounds how long an abandoned unnamed hold can block a domain.

A third shared file follows the same family but keys ownership differently (S1396): the round state of `/spec-next` and `/spec-do`. Its owner is an agent session, not an OS process, so PID liveness cannot apply - `scripts/spec_catalog/spec-next-session.ps1` stamps `owner.sessionId` from `CLAUDE_CODE_SESSION_ID` and reads liveness off that session's transcript write time (`-StaleMinutes`, default 45). Every verb warns and writes anyway, the `CODE.LOCK` model. No session id in the environment -> ownership is undefined and all of it is a no-op.

**Parallel picker sessions (S1437).** Two or three `/spec-next` / `/spec-do` sessions now run at once in one working tree. Three things make that safe, and each replaced a different blocker:

- **Round state is per session** - `temp/spec-next-session.<sessionId>.json`, one file each. The old single file's `-Verb Init` refusal (exit 4) is gone; that code is retired and not reused. A pre-S1437 `temp/spec-next-session.json` is adopted into the per-session path on the first `Resume`.
- **A ticket lease stops two sessions working the same ticket** - `scripts/spec_catalog/ticket-lease.ps1`, one file per lease under `temp/SPEC-TICKET.LEASES/`. A claim is an atomic `CreateNew`, so of two sessions racing for one ticket exactly one wins; the loser gets **exit 3**, which is a normal outcome - it re-ranks with that id excluded and takes the next ticket, it does not wait. Release is owner-checked (**exit 4** refuses to free a live sibling's lease), and **S2608 made that refusal reach `-Force`**: the lease stays while its owner holds *or awaits* any code or build domain under a reason naming the ticket. `-Force` asserts that a supervisor watched the owning process exit, and a process that exited holds no lock and stands in no queue, so the direct observation outranks the assertion. It is the same evidence `Clean` and the liveness verdict already trusted through one shared `Test-LeaseOwnerWorksTicket`, which `Release` was the single exit never to consult - measured twice, on S2466 and then on S2583 two days later, where the owner was queued for `Code.Scripts` at the instant its lease was force-released and took the lock seconds afterwards. S2500's process signals stay and are unchanged; they simply cannot see an ordinary session, whose id is a guid naming no process and whose recorded pid is the `pwsh` that wrote the lease and exited. Expiry follows the owning session's liveness with an independent 480-minute ceiling, and a stale lease is swept by whoever reads next - no watchdog, same as the queue. **S1448 widened what counts as alive**, because a preflight once offered S1436 as unleased while the owning session was demonstrably working it: a lease now carries its own `lastSeenAt`, refreshed on every verb its owner runs, and a session holding any code or build domain with a reason naming the ticket id counts as live on that evidence alone - the evidence is scanned across **every** domain, plus the two pre-split names, because after S2109 a session holding `Code.Wear` writes no file under the bare name and a check looking only there would read a working session as abandoned and sweep it. The 480-minute ceiling still judges `claimedAt` and neither signal extends it. `spec-next-preflight.ps1` consumes the lease set as an extra exclusion source and leaves its five sort keys alone, so the owner's release-plan order still decides who gets what. **S2404 gave the claim a handoff for no-session-id runtimes** (ZCode, a plain shell, cron), where every pwsh invocation is its own "session" and the claiming pid is unreachable from the next one: every successful Claim writes `temp/LEASE-HANDOFF/LEASE-HANDOFF-<Sxxxx>-<stamp>-<pid>.json` and prints the path (a `lease handoff:` line through `spec-preamble.ps1`), and later invocations pass `-Handoff <path>` so a re-claim refreshes the heartbeat as `already-mine` and a release proves succession instead of hitting **exit 4** until the 45-minute window expires; an expired or foreign handoff is ignored and today's semantics apply. `spec-preamble.ps1` both prints that path and **accepts** one, because it is the single call `/spec-dev` claims through: with the CLI alone repaired, two preamble runs under different pid identities still measured exit 0 then exit 3, which is the ticket's own symptom surviving on the path most drivers use.
- **A killed flow leaves its leases behind, and `.\a.ps1 ul` (`ticket-lease.ps1 -Verb Clean`) is what clears them** - with two keep-signals the plain sweep does not have: a running headless child naming the ticket, and the owner holding - or, since S2608, awaiting - any code or build domain naming it. Everything else is judged by **the same verdict and the same window as Claim** (`SessionStaleMinutes`, 45), which is **S2407** and is the correction of a real incident: Clean used to carry a two-minute window of its own, so one script answered "held by a live session" (Claim, exit 3) and "litter" (Clean) about one lease at one moment, and a live interactive VS Code session - mid-generation, holding no lock because it had just released one for its siblings, with no headless child to vouch for it - lost S2406 to a sibling that had read its chat and seen it speak two minutes earlier. A chat line still only ever **extends** a life (S2372 ADR-7): a lease the shared verdict calls stale is kept anyway while any single signal - heartbeat, transcript or chat - is inside the window. Each drop prints the session it was taken from and that session's last chat lines, and posts `dropped Sxxxx held by <name>` so the victim reads it at its own next refusal. **`-QuietMinutes` below 45 is refused (exit 2)** unless `-Force` is also passed, because narrowing the window redefines "dead" for somebody else's lease; `-Force` alone is the right tool after `.\a.ps1 rs -Kill`, since its contract is a supervisor that watched the processes exit. Never a way to take a ticket a sibling is working.

- **Catalog journal writes are serialized** - `Enter-CatalogLock` / `Exit-CatalogLock` (and the `Invoke-CatalogTransaction` wrapper) in `scripts/spec_catalog/_lib.ps1` hold a named system mutex across **read -> mutate -> write** in every mutator, id allocation included. The write was already atomic by temp-file rename; the failure it fixes is the lost update, where two processes hold the same snapshot and the later write silently drops the earlier change. A mutex rather than a lock file because a journal rewrite is milliseconds, and it dies with its process so a crashed holder cannot wedge the catalog.

```powershell
# Who is working what, right now, and when each session was last seen
pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Status
pwsh -NoProfile -File scripts/spec_catalog/ticket-lease.ps1 -Verb Status -Json
# Release-order view with ephemeral ownership for the selected package; it never rewrites PLAN/RELEASE_QUEUE.md.
# Each taken row is marked inline ('[taken 5.4m, /spec-all, session 39ebfe7f]'), so occupancy reads in the
# same scan as the plan; the block underneath still carries the full session id needed to steal or clear one.
```

**The release files carry the same marker** (owner ruling 2026-09-01). `PLAN/RELEASE_QUEUE.md` and `PLAN/RELEASE_READY.md` are where the plan is actually read, so a ticket held by a live session is marked on its own row there - `[taken 15:42, /spec-all, be08adb0]`, a claim time rather than an age, because an age written into a file is wrong a minute later. The lease store under `temp/` stays the source of truth: the marker is re-rendered from it on every catalog write, so a session that died loses its marker on the next write and its ticket reads as free again. Anything parsing those files must strip the marker before reading the status column - `Remove-ReleaseQueueLeaseMarker` in `scripts/spec_catalog/_lib.ps1` is that one strip, and `run-spec-all-queue.ps1` tolerates the same tail in its own line pattern.

```powershell
# Re-render the markers on demand (any catalog write does it too)
pwsh -NoProfile -File scripts/spec_catalog/release-queue.ps1 -Reconcile
pwsh -NoProfile -File scripts/spec_catalog/release-queue.ps1 -List -Release 32 -WithLeases
```

**Resuming across a context reset.** A reset gives the resuming agent a *new* session id, so the round it is resuming is always filed under the old one - and to a liveness test that old session looks alive, because its transcript was written seconds ago. Liveness alone therefore cannot tell "just stopped, waiting to be picked up" from "a sibling working right now". `-Verb Handoff` (which the threshold stop already runs) stamps `handoffAt` on the state, and `-Verb Resume` adopts only a round that is either stamped or whose owner has genuinely gone stale. Without that marker resume would either lose the round or steal a sibling's - there is no third answer available.

#### Device registry - S2855

Where the device lease answers "who is driving this device right now" and is swept when that session goes quiet, the device registry answers what no layer could before: **what was last installed on each test device, when, and by whom** - durable, machine-local, never swept. One file per device under `temp/DEVICE.REGISTRY/<serial>.json` - path and serial encoding from the same declaration as the lease store, `scripts/devtest/lib/device-store-paths.ps1`: the lease store's serial discipline with the opposite lifecycle. Releasing or sweeping a lease never touches a mark, and a mark never creates or extends a lease.

```powershell
# The park: role, who is driving what, and the last install mark
pwsh -NoProfile -File scripts/devtest/device-registry.ps1 -Verb Status

# The verb the install hooks call; Refresh reconciles from a live device; Forget -Yes erases
pwsh -NoProfile -File scripts/devtest/device-registry.ps1 -Verb Record -Id emulator-5554 -Package com.sza.fastmediasorter.debug -VersionName 2.60.9100.101 -RecordedBy 'adb.ps1 install'
pwsh -NoProfile -File scripts/devtest/device-registry.ps1 -Verb Refresh -Id RFCR110NBQJ
```

The record carries the mark (package, module, flavor, build type, version pair, artifact, when, session and ticket, `recordedBy`) plus the last five installs. **Recording is a by-product of the sanctioned install entry points**: the `adb.ps1 install` verb and the noLegal debug installer write the mark themselves, each stamped with its own `recordedBy`, and a recording failure never fails the install. A freshness guard on `lastUpdateTime` keeps a pre-existing package from being recorded as if this install had touched it. Raw `adb install` stays unrecorded - the advisory principle covers accounting exactly as it covers locking - and `-Verb Refresh` is how out-of-band installs are learned from a live device.

**Marks are facts, never permissions.** `docs/DEVICE_FLEET.md` stays the only authority for what a device permits; the registry records what happened. Read-only verbs never create the store directory, so a probe or the monitor can look without leaving artifacts. The monitor page renders the park in a devices section fed by the page writer, which reads both device stores itself - the snapshot collector and the terminal renderer are canon-harness forwarders (S2402), so the section is page-only by design. `device-ready.ps1 -WithRegistry` attaches the mark to a ready answer behind the same opt-in discipline as `-ClaimFree`.

#### Device state journal - S3201

The registry records what was installed; the state journal records what a test **changed** and must put back. One file per device under `temp/DEVICE.STATE/<serial>.json` (same declaration, `scripts/devtest/lib/device-store-paths.ps1`), keyed by `ro.serialno` because the watch's wireless adb id changes every session. It holds the original `wm density` and `wm size` overrides, `font_scale`, any `settings` key a run wrote, and the app's `files/datastore/` files as hashes with a byte copy beside the journal.

```powershell
# Open a device run: restore leftovers of an unclosed run, then snapshot
pwsh -NoProfile -File scripts/devtest/adb.ps1 state-begin -DeviceId <id>
# Close it: put every drifted value back, one RESTORED line each; exit 13 = a value did not come back
pwsh -NoProfile -File scripts/devtest/adb.ps1 state-check -DeviceId <id>
```

`adb.ps1 font-scale -Scale` and `adb.ps1 shell -Cmd` with `wm density|wm size|settings put|delete` record the original before they run, so a change made outside an open run is still put back by the next `state-begin`. `/spec-test-device` and `wear-prerelease-walk.ps1` open and close the journal themselves. A DataStore file goes back after a force-stop, through `/data/local/tmp` and `run-as cp`; that route needs a debuggable build.

#### Agent chat - S2372

The fourth coordination layer, and the only one with no rights: it grants nothing, forbids nothing and owns nothing - it tells. The locks, queues and leases answer "busy or free"; the chat answers "busy with what, since when, and is the holder still talking".

Two streams, deliberately separate, under `temp/AGENT-CHAT/` (one file per message, atomic create, never a shared file with five appenders):

- `progress/` - what a session is doing now: `kind` from a closed list (`session`, `phase`, `lock`, `wait`, `ticket`, `status`, `verdict`, `check`, `build`, `device`, `abandon`, `heartbeat`, `note`), ticket, phase, domains, one line of prose. Kept 180 minutes - a successful ticket run fits in 90 (max over 326 journal rows, 2026-09-02) and the successor of a dead session arrives no sooner than the lease's 45-minute stale window, so the last trace outlives both. The last message of a session that died is where it stopped.
- `findings/` - a result: a measurement, an answer, an environment state. Carries a `topic`, evidence (command, exit code, artifact) and a **scope** - up to 16 repository paths whose change makes it wrong. A finding is dead when anything in its scope was written after it, when its TTL passed (default `SpecTicket.TicketCeilingMinutes`, 480), or when the device it names is not in `adb devices` (adb missing counts as gone). The clock is never the truth; the scope is. Measured 2026-09-02: enumerating `app_v2/src` (4,881 files) costs 241 ms, against the 14-47 s a fast check a finding lets a session skip costs (S2935 re-measured `fg` at 47 s on 2026-09-11; S2451 had it at 32 s).

Who writes, without costing a token: `Enter-AgentLock` / `Exit-AgentLock` (`lock`), `enter-code-lock.ps1` and `Enter-BuildLockOrExit` when queued (`wait`), `ticket-lease.ps1` (`ticket`), `update.ps1` (`status`), `post-change.ps1` (`verdict`), `assert-release-scope-gates.ps1` on green (finding `gates:release-scope`, scope `app_v2/src`, `wear/src`, `scripts`, `docs`, etc.), `device-ready.ps1` on READY (finding `device:<serial>`, TTL 60, carrying its canonical request string, dies with the serial), and the `post-agent-chat-session.ps1` hook at session start and end (`session`). The model owes three lines: a phase start (`/spec-dev`), a stage boundary (`/spec-all`), giving work up (`-Kind abandon`).

**The runner consoles read it too.** A `claude -p` child prints its one line when the ticket ENDS and `.claude/runner/silent-mode.md` forbids it any narration before that, so an `r0`..`r3` console used to sit blank for the whole 30-60 minutes of a pipeline, which reads exactly like a hang. `scripts/utils/watch-agent-progress.ps1` is started beside the runner by `a.ps1` (`-NoNewWindow`, so it writes into that same console, and `-ParentPid`, so it dies with it) and tails `progress/` for the kinds that mean progress - `status`, `verdict`, `phase`, `ticket`, `abandon`, `note` - skipping `lock` and `session`, which fire several times per step and say nothing about where the pipeline is. A pass prints at most `-MaxLinesPerPass` lines and counts the rest; ten quiet minutes print one still-working line. Scope comes from `FMS_QUEUE_INSTANCE`, which `a.ps1` now exports before launching a runner (`mono`, `a`, `b`, `c`): every descendant inherits it, `agent-identity.ps1` stamps it into each record as `agent.instance`, and so three parallel runners each print their own work and none prints a sibling's. Before this nothing set that variable and every record read `instance -`. Run it bare in a spare window for all instances at once. Read-only and best-effort by construction: it holds no lock, writes nothing, and a malformed record skips that record rather than ending the watch - nothing may depend on its output (Rule 34).

Who reads, and where: the refusal is the moment - `enter-code-lock.ps1` (exit 4) and `ticket-lease.ps1 -Verb Claim` (exit 3) print the holder's last three lines under their own verdict; `spec-next-preflight.ps1` adds `last_chat` to every `leased_ids` entry; `monitor-spec-queue.ps1` (`.\a.ps1 rm`) has an "agent chat" section. Nothing polls.

```powershell
# What is everyone doing (one row per agent in the window; SILENT past SpecTicket.SessionStaleMinutes)
pwsh -NoProfile -File ./a.ps1 chat -Verb Status

# The last lines of one holder (the id the refusal printed)
pwsh -NoProfile -File scripts/utils/agent-chat.ps1 -Verb Read -AgentId <id> -Last 5

# Check live device or release-scope findings (-Query searches by bare keyword)
pwsh -NoProfile -File scripts/utils/agent-chat.ps1 -Verb Find -Topic "device:*"
pwsh -NoProfile -File scripts/utils/agent-chat.ps1 -Verb Find -Topic "gates:release-scope"
pwsh -NoProfile -File scripts/utils/agent-chat.ps1 -Verb Find -Query "release-scope"

# Post the three lines only the model knows
pwsh -NoProfile -File scripts/utils/agent-chat.ps1 -Verb Post -Kind phase -Ticket S1234 -Phase 02 -Note "writers"
pwsh -NoProfile -File scripts/utils/agent-chat.ps1 -Verb Post -Kind abandon -Ticket S1234 -Note "drift needs the owner"

# A finding of your own: scope and/or TTL is mandatory - a record nothing can invalidate is refused
pwsh -NoProfile -File scripts/utils/agent-chat.ps1 -Verb Post -Finding -Kind check -Topic "catalog:app_v2" -Scope app_v2/src/main/java -Note "catalog_sync fresh" -EvidenceCommand "catalog_sync.ps1" -EvidenceExit 0
```

Exit codes of `agent-chat.ps1`: **0** done, **1** refused input (unknown kind, a finding with neither scope nor TTL, a scope over 16 paths), **2** usage or store unreachable.

**Opt-in reuse (S2409).** A consumer calls `Get-AgentChatCoveringFinding` (in `scripts/utils/agent-chat-store.ps1`) to check if an alive finding matching its canonical request string exists. `device-ready.ps1 -ReuseFinding` reuses any live READY finding for the requested device and options. `assert-release-scope-gates.ps1 -ReuseFinding` reuses only findings written by the caller's own session (`-OwnAgentOnly`).

**Trust rule (CLAUDE.md Rule 34).** The chat decides nothing. No lock, queue or lease consults it to grant anything - the correctness of the existing coordination rests on atomic acquisition, and a decision taken on the content of a file another process writes reopens exactly the race five tickets closed. No verdict that reaches a spec or a gate may rest on a finding someone else wrote: a ticket's closure runs its own checks. What a finding may do is spare an agent cheap idempotent **work** - a release-scope gate run whose scope is intact, a READY device probe - and the agent that skipped names the finding in its output. *A finding relieves you of work, never of the report.*

**Identity.** One chain in `scripts/utils/agent-identity.ps1`: `FMS_AGENT_ID`, then `CLAUDE_CODE_SESSION_ID`, then the ancestor host process `host-<name>-<pid>-<startTicks>` (S2408), then `pid-<PID>`; `runtime`, `model` and `instance` come from `FMS_AGENT_RUNTIME` / `FMS_AGENT_MODEL` / `FMS_QUEUE_INSTANCE` or read `unknown`, never empty. `Get-AgentSessionId` in the lock library reads the same chain, so a lock, a queue ticket, a lease and a chat line name one agent identically (the S2371 lesson) - and since S2408 so do `New-AgentLockTicket`, the post-acquire ticket sweep, `lock-status.ps1`, `wait-for-lock-turn.ps1`, `wait-for-ticket-work.ps1`, `withdraw-lock-ticket.ps1` and `session-bootstrap.ps1`, each of which used to read the raw variable and substitute its own `pid-<PID>`. A subagent inherits its parent's session id (measured 2026-09-02) and so is its parent unless it sets `FMS_AGENT_ID`.

**The host walk, and what it costs.** Step 3 exists because step 4 cannot identify a session: a runtime that starts a fresh shell per command gets a new pid each time, and one measured hour on 2026-09-02 produced 46 identities and 46 nicknames for a single session - whose lease looked dead within a minute, whose ticket a sibling then took, and whose every release was forced. The walk climbs `(Get-Process -Id $PID).Parent` past shells and interpreters (`pwsh`, `cmd`, `bash`, `node`, `python`, `git`, `conhost`..) to the first process that outlives a command. Reaching a machine-wide process (`explorer`, `svchost`, `services`..) **adopts the ancestor one step below it** (S2417), because that ancestor is the root of a single session's tree and not the machine's - the owner's three `a.ps1 r1/r2/r3` queue runners, all started from one `explorer`, give three different roots. The machine-wide process itself is still never the identity, so one identity for every session on the box remains impossible. Only a walk with nothing to adopt returns the pid fallback: a trail that ended because the parent had already exited, or the depth bound - adopting there would mint a `host-` id that lives one command. Every identity carries the outcome as `hostWalk` (`resolved`, `adopted-below-<name>`, `no-host-below-<name>`, `no-host-trail-lost`, `no-host-depth`, `disabled`, `not-reached`), and since the identity object is embedded whole in every chat message the outcome lands in files that outlive the process - which is exactly what the S2417 investigation lacked, both failing processes having exited before anyone looked. `startTicks` stops a recycled pid from naming the wrong process. Measured on the owner's machine: the whole warm walk 0.42 ms average, against 118 ms for a **single** step through `Get-CimInstance` - which is why it is the property, not CIM. Two chats inside one host process share one identity: accepted, and the reason a hookless runtime should still set `FMS_AGENT_ID` first. `FMS_AGENT_HOST_WALK=0` disables the walk.

```powershell
# Which of the four steps am I on right now?
pwsh -NoProfile -File scripts/utils/agent-chat.ps1 -Verb Whoami
```

**Nickname (owner ruling 2026-09-02).** An id is a uuid or a pid, and the owner reading the chat cannot tell two of those apart, so every agent speaks under a readable name: `<adjective>-<animal>-<MMdd>-<HHmm>` (32 x 32 words plus the minute it was taken, e.g. `brisk-otter-0902-2231`). It is taken once per session, at the first identity resolution - the session-start hook for Claude Code, the first script run for anyone else - and kept in `temp/AGENT-CHAT/names/<id>.json` (created with `FileMode.CreateNew`, so two processes of one session cannot take two names), which is why every later process of the session answers with the same name. `FMS_AGENT_NAME` overrides it; `.\a.ps1 chat -Verb Whoami` prints yours. Every chat line, `-Verb Status`, the monitor and the refusal context print the name with the id beside it in brackets; scripts keep comparing ids only, because two agents can draw the same name in the same minute and identity comparison is exactly where S2371 already diverged. Name files nobody has touched for seven days are swept with the rest.

**Liveness.** The owner's newest chat message is the fourth signal in `Get-AgentTicketLiveness` and in the lease's quiet-time - after the ticket heartbeat and the transcript, before the enqueue time. It can only extend a life, never shorten one: a Claude Code session in a half-hour of reading writes no chat line and is still judged by its transcript; a runtime without a transcript is judged by what it said rather than by the clock alone.

**Any runtime.** Files plus one script are the whole transport. Claude Code's hooks are an accelerator: they post session start and end for free. A runtime without hooks posts those two lines itself (`AGENTS.md` section 9.1) and gets everything else - every lock, lease, status and verdict line - from the scripts it already runs. The rule is numbered in `CLAUDE.md` so `assert-rule-digest-sync.ps1` refuses a digest that forgets it; no second gate exists for that.

**Sweeping.** Every write and every read starts with the sweep: progress past retention, findings past expiry, both streams past their caps (400 / 200, oldest first), stale `.tmp` files. The TURN-marker pile (S2405) is the precedent this exists to avoid. Contract suite: `scripts/utils/agent-chat.tests/Run-Tests.ps1`; hook suite: `.claude/hooks/tests/Run-PostAgentChatSession-Tests.ps1`.


#### Development monitor page - S2406

The second render of the queue monitor: what `.\a.ps1 rm` prints, in a browser tab that stays current on its own. It exists because the terminal snapshot is longer than a screen the moment three instances and a handful of agents are busy, and because a page can say "silent", "stale" and "queued" in a colour and a word where the terminal has only a number. English throughout, one monospace face, tables and rows, no animation - the owner's ruling of 2026-09-02 - and a lot of data: every section of the terminal plus the agents with their nickname, lease and phase, the current package of `PLAN/RELEASE_QUEUE.md` in file order with the `[taken ..]` marker, the newest chat lines and the alive findings.

One collector, two renders (S1621): `scripts/utils/dev-monitor-snapshot.ps1` is the only code that reads the sources - leases (judged by the same `Get-AgentTicketLiveness` the lease script uses, in-process instead of through a child pwsh), locks and queues, chat, journals, stop flags, headless children, the release queue, gate telemetry, context signals and the watchdog log - and returns one object (`schema` 1) with its own `durationMs` plus per-source `timings`. `monitor-spec-queue.ps1` prints it; `-Json` emits it verbatim; the page renders it. It writes nothing: every chat read passes `-NoSweep`, no lock, no chat post, no child process. Measured 2026-09-02 on the live tree: 476-518 ms cold, 209-230 ms warm (median 229), against a 1000 ms budget - one third of the 3 s interval - and the 1176 ms the old terminal snapshot took with its 434 ms lease child.

**One roster, not four identity spaces** (owner finding 2026-09-10). The page opened with a `running` table that admitted only two kinds of agent: the owner of a live lease, and one whose *newest* chat message was `kind=session`. An agent that was actually working - posting `phase`, `progress` or `lock` - matched neither and was absent from it, while the lock table below named holders and queue waiters that appeared in no other section; a fourth spelling came from the harness name fallback, which slices the first eight characters of an id that never posted a message, so several distinct `codex-takeover-<epoch>` sessions all printed as `codex-ta`. Measured on the 12:20 snapshot of that day: `Code.Scripts` was held by `jade-gecko-0910-1120`, listed nowhere else on the page. The reader was left to guess that four spellings were one agent, which is the one thing the page exists to answer.

`running` and `agents` are now one section, and it opens the page while `gate health` - reference rather than a live signal - closes it. The roster is keyed by session id: one row per agent carrying its ticket, its phase, the domains it **holds**, the domains it is **waiting for** with its queue position, and the freshest of its three clocks (lease heartbeat, chat message, session record - the oldest of them reads a working agent as quiet). Rows are ordered blockers first, then the blocked, then by freshness. The roster shows what is happening **now**: an agent quiet longer than ten minutes collapses into one line that still names six of them, rather than the chat window's three hours of history. Ten rather than the harness `SilentMinutes` of 45, and the cut can be that hard only because ownership overrides it - see the alarm below.

**The roster spends width and height only on what says something** (owner finding 2026-09-10, third pass - "ugly and uninformative"). Three rules, one cause. `min-width` on a table cell applies to its whole **column**, so `td.wrap`'s 18em floor was claimed by `holds` and `waiting for` even though both printed `-` on 17 of 18 rows - some 650 px held hostage, which is what squeezed the text columns into a strip; an empty cell now drops the class, and a column of short structured content (a domain and a duration) uses a 9em floor rather than the prose one. Half the note rows repeated their own phase cell word for word - `lastNote` and `phaseNote` are the same string whenever the newest message is the phase - so a note row is dropped when it echoes the phase or is `session started/ended` bookkeeping, which the state and `seen` columns already carry, and it is capped at 200 characters with the rest on the row's tooltip because one chat line can be a 600-character gate report. And a row now needs a **ticket** of any provenance to be listed at all, beside the existing hold-or-queue rule: half of what the page called active were sessions whose whole trace was a lock released minutes earlier, at two lines each, so the half that was working did not fit on one screen. Those collapse into the one line, whose first three entries name what they last did so it still separates "finished and went quiet" from "just released a lock". Measured on the 16:21 snapshot: 18 rows over 37 table lines became 7 rows over 15. `?/?` is gone from `runtime/model` - the runtime, the model and the instance print when known and the cell says nothing when they are not - and the first column's header now says `state`, which is what it has always shown.

**The last note is a row, not a column,** spanning the table directly under its agent, whose own row drops its bottom border so the pair reads as one entry. It is the only wrapping cell on the page, so as a column it set the height of every row it appeared on and squeezed the eight narrow columns into a strip.

**A quiet agent that still owns something is red** (`NO LIFE`), and its note row says what it is still holding and for how long. This is a deliberately wider net than the `stalls` array above it: that verdict needs a queue behind the holder before it fires, while an agent sitting on a ticket nobody is waiting for still blocks that ticket. A lease the harness itself judges `foreign-stale` is red however recent the agent's chat is - the lease verdict is made from the lease's own evidence and wins. What counts as **owned** - a lock domain, a queue position or a ticket lease - is one predicate shared by the alarm and by the rule that keeps a row out of the collapsed line; they were written separately at first and disagreed on exactly one case, a quiet agent holding only a ticket, which the cut therefore hid - the single row the alarm exists to show. The live tree had no such agent, so only a fixture (`temp/S2406/verify/`) found it. The join happens in the renderer, not the collector: `locks[].sessionId`, `locks[].queue[].sessionId`, `leases[].sessionId`, `agents[].id` and `sessions[].id` were all in `schema` 1 already. Two disciplines carry over from the child rows - a ticket that does not come from a lease is printed with a dim `?` naming where it came from, and a `name` that is a prefix of its own id is printed as `unnamed` beside a short id rather than as a nickname (a uuid shortens from the head, a `codex-takeover-<epoch>` id from the tail, because that is where each one is unique). A headless `claude -p` child stays process-shaped in its own rows below the sessions: it is joined to work by ticket and never by pid.

S2700 adds the parallel-work signals without adding a journal: gate health comes from a bounded tail of `temp/metrics/gate-executions.jsonl`; runner health is summarized from the existing run journals; agent context is the last context-signal marker for its session; and watchdog actions are a bounded tail of `temp/scratch/watchdog/watchdog.log`. A missing or empty source reads as `source silent`, not as a red failure. The page puts gate health and watchdog actions in their own tables, annotates a `set-named` gate as `named your file`, and colors an agent context marker only when it says `over threshold`. The terminal exposes the same fields. A source reader must stay in the snapshot function - neither renderer reads any of these files directly.

**`problems` opens the page** (owner ruling 2026-09-10, second pass the same day): a terse red/yellow at-a-glance panel above the roster, so a clean run needs no further reading and a red run is told which section below has the detail. Four categories, each read from a primitive array rather than from the roster join so a roster bug can never hide them: a **lock queue** (`locks[].queue` non-empty; red when the head-of-queue waiter is unseen for over 5 minutes, the same "cold" rule the locks table already uses, otherwise yellow); a **dead lease or stalled ticket** (a lease the harness judges `foreign-stale` or `unknown`, plus a `stalls` entry whose rule is `quiet-owner` - an agent gone silent mid-ticket on a `Code.*` domain); a **build crash** (a `stalls` entry whose rule is `no-cpu` - S2582's build-domain rule, `Build.*` only, meaning the holder process itself is gone while the lock is still held); and an **abandoned ticket** - a lease whose `lastSeenMinutes` exceeds the roster's ten-minute window even though the harness still calls it live. That fourth one is what the panel opened without, and the gap was visible on the live page the same day: lease S2859 had been quiet 37 minutes, the roster below painted its owner red as `NO LIFE`, and the panel printed `ALL CLEAR` directly above it - because a lease is judged against the harness `SessionStaleMinutes` of 45 while the roster cuts at ten, and the panel had no rule of its own. The stricter of the two is the one the reader sees, so the panel now applies it, from `leases[]` alone rather than from the roster join, and the constant is declared once for both. A quiet **lock** holder is deliberately still not a category: with nobody queued behind it, `stalls` excludes it by the same S2413 reasoning, and the ticket case is the one the panel must cover because a ticket nobody is waiting for produces no `stalls` entry at all. All read red except an uncold lock queue and an `unknown`-liveness lease, which read yellow. Nothing found renders one green `ALL CLEAR` line naming the five things it checked.

`s.gates` is deliberately excluded as a source, even though "a failed gate" reads as the obvious definition of "build crash". Measured live 2026-09-10: the canon collector (`Get-DevMonitorGates`) marks a whole run `FAIL` the moment any one gate inside it is `SKIP` - not applicable to that change set - and never carries a per-gate `PASS`/`SKIP`/`FAIL` split into `failures[]` (`gate`/`scope`/`count` only); that split exists only in the raw `temp/metrics/gate-executions.jsonl` lines, which no renderer may read directly (S2413). On the live tree this makes nearly every `post-change` run read `FAIL`: one sampled run carried 44 `SKIP` gates and zero real failures, reported as a single `FAIL` with all 44 names attached. Reusing that field here would put the single noisiest possible line on the page on every ordinary run - the exact cry-wolf this panel exists to prevent. The `gate health` table below is unaffected and still shows the raw verdict to whoever opens it; the collector-side SKIP/FAIL conflation is a canon defect, not a page defect, and stays open for a canon session to fix.

Two files under `temp/monitor/`, both written by `scripts/utils/dev-monitor-writer.ps1`:

- `index.html` - the shell: inline CSS, inline renderer, no external reference. Written once per writer start.
- `snapshot.js` - the data: `window.__devMonitor({..})`, replaced every 3 s through a temp name and `Move-Item`, so the browser never reads a half file.

**One element is not an object, and a silent renderer is worse than a red one** (2026-09-10). `ConvertTo-Json` writes a one-element collection as a bare object, and a PowerShell function's output is enumerated into the pipeline, so the `@()` inside `Get-DeviceParkRows` was undone at its call site: a park holding exactly one device serialised as `"devices":{..}`. The page reads that field with `.forEach`, which threw, and because `render()` paints the sections in order, everything from `devices` down - locks, watchdog actions, next up, chat, findings, finished, stop, gate health - stayed blank while the header went on reporting a fresh age from its own clock. Nothing on the page said so, which is why it survived: the failure looks exactly like a quiet machine. Three fixes, at three layers. The writer wraps the call, so the field is an array at the source. Every collection in the renderer is read through `arr()`, which accepts both shapes, because the collector is a canon forwarder this repository cannot gate and the same unwrap can reach the page from any of its arrays. And `render()` runs inside a try/catch that puts a red `PAGE ERROR` line in the `problems` panel with the exception's own message - the next one is read rather than guessed. The suite asserts the JSON shape off the raw text (`"devices":[`), since `ConvertFrom-Json` hides the difference, and the existing devices case had passed while the page was broken because the live park it seeds into usually holds a second serial.

**Every stamp is parsed by hand** (same pass). The snapshot carries two shapes and each defeats the engine's date parser in its own way: a .NET round-trip string with seven fractional digits, where the ECMAScript format allows three, so the raw 27-character stamp appeared in the header's `since` and the locks table's `since`; and `MM/DD/YYYY HH:MM:SS`, which is what `[string]` makes of a `DateTime` that `ConvertFrom-Json` built from a `*Utc` field - no marker survives the cast, so `new Date()` reads it as local and would have drawn the gate-health clock exactly one local offset behind every other section. `local()` matches both, builds the date through `Date.UTC`, and takes epoch milliseconds as well.

Why two files and no server: `fetch` and XHR from a `file://` page are refused by CORS in Chrome, Edge and Firefox, but a classic `<script src>` from the same directory is not. The shell appends `<script src="snapshot.js?t=<now>">` every interval and repaints the tables in place - no reload, no flicker, no lost scroll position, and no process whose death would blank the page; a dead writer leaves the last snapshot and an honest age. Two consecutive load failures fall back to `location.reload()`. The header recomputes the snapshot age from the page's own clock every second and says `fresh`, `writer silent` (three intervals without a new snapshot) or `writer stopped` (the writer's last snapshot said so) - a word beside the colour, so it reads without colour.

```powershell
pwsh -NoProfile -File ./a.ps1 rmw             # start the detached writer, open the page once
pwsh -NoProfile -File ./a.ps1 rmw -Status     # pid, page path, snapshot age
pwsh -NoProfile -File ./a.ps1 rmw -Stop       # STOP flag, then Stop-Process after three intervals
pwsh -NoProfile -File ./a.ps1 rm -Json        # the snapshot object, for any other viewer
pwsh -NoProfile -File scripts/utils/dev-monitor-writer.ps1 -Once -OutDir temp/scratch/monitor   # one shell + one snapshot, no loop
```

The writer runs through `start-detached.ps1 -OutDir temp/monitor` (S2400), so it outlives the shell and the session that started it; `writer.pid` refuses a second instance (a second `rmw` prints the running pid and opens the page again); `-Stop` creates the `STOP` flag the loop reads between ticks. It takes no lock and posts nothing to the chat - it is a viewer, not an agent - and writes nothing outside its directory. Exit codes of the writer: **0** done, **1** could not start (launcher failed, no first snapshot within 15 s, another writer alive), **2** `temp/` missing. Contract suites: `scripts/utils/dev-monitor-snapshot.tests/` (snapshot fields, read-only proof, terminal `-Json` parity) and `scripts/utils/dev-monitor-writer.tests/` (shell self-containment and no-animation, start / second start / ticks / stop lifecycle, no writer artifact at the top level of `temp/` - the case names the writer's own artifacts rather than diffing that shared directory, S3025).

#### The foreground refusal - why a short check queues and exits instead of blocking (S2612)

A `Build.*` domain is exclusive, so the wall clocks in `docs/BUILD_TEST_FAST_PATH.md` measure a target once it already holds the domain. The wait in front of it is in none of them, and Rule 6 pins the foreground boundary to the Bash tool's own 120 s timeout - so a short check could spend that entire window queueing and be killed before it ran a line. A killed foreground check reports no verdict at all, which is indistinguishable from a check nobody ran.

Measured 2026-09-06 from `temp/check_fast_*.log`, 1327 runs over 2026-09-03..09-06 (`scripts/utils/measure-build-lock-wait.ps1`): of 975 short-class runs, 531 waited at all, 94 (9.6%) waited past 60 s, 82 (8.4%) past 90 s and 66 (6.8%) past 120 s. Mutual exclusion itself was never violated - zero overlapping holds - so the defect was in the waiter's behaviour, not in the lock.

The refusal: a short-hold-class run on a busy domain takes its queue place, prints the holder's pid, age and reason plus the exact `wait-for-lock-turn.ps1` command and a handoff path, and exits **4**. Same code `enter-code-lock.ps1` returns for a busy code domain, which is the asymmetry this closed - the code side had sent the agent to a background waiter since S1432 while the build side blocked in-process, and nothing named the difference.

- **Budget 60 s**, a named constant in `scripts/builders/build-queue-refusal.ps1`. The foreground window is spent on wait *plus* run, and a short target's own clock is 14-32 s, so the survivable wait is 88-105 s, not 120. 60 s clears that with margin and sits above the 84th percentile of everything that waits at all. The trade it buys: about twelve runs per three days pay one background-wait turn, and roughly eighty-two stop returning nothing. A per-target budget (`120 - this target's runtime`) was rejected - it buys those twelve back at the price of a copy of the measured table inside the entry point, which would then drift from the document that owns it.
- **The place survives the refusal.** The ticket set is taken *before* the report and never removed; the rerun adopts it because `New-AgentLockTicket` dedups per session, and the handoff file carries it where there is no session id (S2403). A refusal that dropped its place would convert waiting into starvation.
- **The hold class is the foreground signal**, and there is no second detector: Rule 6 sends the short set to the foreground and the long set to the background by the same measured table, so "short class" and "bounded by 120 s" are one statement made at two layers. S2580 had already computed the class for the queue reason text; this reuses it. The unattended runner is not an exception - it launches `claude -p`, whose agent runs checks through the same tool.
- **Escape hatch:** `-BlockThrough` for one call, `FMS_LOCK_BLOCK=1` for a session, inherited by child processes. It only ever makes behaviour more blocking.
- **Re-entrancy exemption.** `Enter-BuildLockOrExit` reuses a domain the same run already holds, but that guard is inside the function while this check runs before it, and `post-change.ps1` spawns `check-standard-fast.ps1` as a child in two gates. Any inherited `FMS_BUILD_LOCK_HELD_BY` therefore suppresses the refusal - deliberately coarser than the harness's pid-plus-start-ticks test, because refusing a lock we own breaks a working closure while declining to refuse merely restores the old blocking path.
- **Callers must read exit 4 as "did not look", not "found a defect".** `post-change.ps1` converts it to its documented exit 2; `a.ps1` passes it through.

**The canon default is unchanged, and that is deliberate.** `Enter-BuildLockOrExit` ships with the `sza` plugin (S2402): a change to its body is overwritten by the next plugin update and never reaches the other consumers. Its `-WaitTimeoutSeconds` is not a substitute either - on expiry it deletes the caller's tickets, surrendering the place, and it calls `exit` itself, so no caller can append the continuation command to its refusal. The decision therefore sits in the repo-side entry point, which is the only layer that knows the hold class, can enqueue before refusing, and survives a plugin update. Blocking stays correct for every caller that is not bounded by a 120 s window.

Contract suite: `scripts/builders/build-queue-refusal.tests/`. History and thresholds: `measure-build-lock-wait.ps1`, which buckets short-class waits, marks the operative budget (read out of the helper, not restated) and counts refusals separately from waits.

#### A script that rewrites a render target takes that target's code domain (S2615)

A generated file has two writers: the agent who edits it by hand, refused or queued by `enter-code-lock.ps1`, and the generator that regenerates it, which until S2615 took no code domain at all. Measured 2026-09-06, eleven scripts rewrote a path the domain table assigns to a `Code.*` domain and none of them took it - so the path a hand edit was queued for was overwritten by a generator walking past the queue. It was not eleven oversights: `Enter-BuildLockOrExit` gives a gradle script its domain in one line and the code domains had no such entry point, only the two-child-process `enter-code-lock.ps1` / `exit-code-lock.ps1` pair plus a release obligation on every exit branch.

The entry point is `scripts/utils/code-lock-scope.ps1`, dot-sourced, never invoked:

```powershell
. (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')
$scope = $null
try {
    $scope = Enter-CodeLockOrExit -Path $targets -Reason 'export-icon-svgs.ps1 (docs/icons/svg)'
    # ..write, and prune, inside this window..
}
finally { Exit-CodeLockScope -Scope $scope }
```

Four rules, each of which fails silently when broken:

- **On the write path only.** Every generator in this family is also called in a `-Check` / verify mode that writes nothing, and `.\a.ps1 fg` runs its gate children concurrently (S2451), so a lock taken in verify mode serialises the whole battery and protects nothing. A renderer with an `-OutDir` override decides by where the output actually lands: into `docs/` it takes the domain, into a scratch directory it takes nothing.
- **After any build domain, never before it.** Domains carry a rank (`Build.Phone` 1 .. `Code.Fixture` 6, as `locks.domains` declares them) and `Resolve-AgentLockTopUp` refuses the descending direction outright, so in a gradle-backed generator the call goes after `Enter-BuildLockOrExit`.
- **Pass the concrete files, never the directory holding them.** Every path rule is an anchored prefix, so `docs` matches none of them and takes the fail-closed branch that returns EVERY code domain. That is safe and wrong, and it looks exactly like working coordination - the run still prints `acquired`. It happened during this ticket: two renderers passed their `$OutDir` and queued behind a sibling's `Code.Phone` with the docs tree free. `Enter-CodeLockOrExit` now prints one line naming the fail-closed answer whenever a path set resolves to all three domains.
- **A busy domain is exit 4, not a wait.** Same answer `enter-code-lock.ps1` gives for a busy code domain and `check-standard-fast.ps1` for a busy `Build.*` (S2612). A caller must read it as "did not look", not "found a defect": `scripts/docs/oss-notices.tests/Run-Tests.ps1` converts it to its own exit 2, because a sibling's ordinary lock is an environment state and not a defect in the subject. Block through with `-BlockThrough` or `FMS_LOCK_BLOCK=1`.

Adopted by the ten render-target generators listed in `PLAN/S2615_bugfix-icon-inventory-regenerator-skips-code-lock.md` section 1. Contract suite: `scripts/utils/code-lock-scope.tests/`.

#### The second entry point, and the registry that keeps both honest (S2635)

S2635 finished the sweep and found the premise it inherited was wrong. The ratchet baselines are **not** written only under `-UpdateBaseline`: `assert-source-gates.ps1` rewrites a baseline DOWN in ordinary gate mode with no switch at all (the S1338 auto-ratchet), and `.\a.ps1 fg` reaches that engine through eleven concurrent children. Measured 2026-09-06 - a baseline seeded at 5 against a live count of 0 was rewritten to 0 by a plain `-Gate -Only globalscope` run that exited 0. So the battery was already writing into `Code.Scripts` from parallel children, unlocked.

That produced two rules the earlier ticket did not need, both of which now decide how a new writer is wired:

- **Acquire lazily when the write is conditional.** Taking the domain at the top of a gate that writes only on a rare branch serialises the whole battery on every run for a write that almost never happens. Take it immediately around the write instead.
- **A busy domain is not always exit 4.** `Enter-CodeLockOrSkip` returns a scope with `Skipped = $true` and never exits, for a caller whose verdict is already computed and whose write is bookkeeping - exit 4 there would paint a passing gate red. It is deliberately ticketless: a caller that will not come back for the write must not leave a queue place nobody retires. Use `Enter-CodeLockOrExit` when NOT writing is work left undone; use `Enter-CodeLockOrSkip` when not writing costs only staleness a later run repairs.

One more placement rule, learned the hard way: **load the helper inside the write branch, not at the top of the file**, whenever the script has a contract suite that runs it from a temp sandbox. Two suites copy `scripts/quality/` without `scripts/utils/`, so a top-level dot-source killed all 26 of their cases before the first assertion.

The registry is `scripts/quality/code-domain-writers.manifest.txt` - every writer, its class (A lazy-and-skip, B lock-and-exit-4) and its domain - enforced by `scripts/quality/assert-code-domain-writers.ps1` in `.\a.ps1 fg` and `post-change.ps1`. Add the row and the lock in the same change; the gate checks both, and a second ratcheted dimension catches an obvious writer that was never registered at all.

#### Rule 23 history - the measurements behind the domain test, the content-tree decision and the edit-only window (moved off CLAUDE.md by S2521)

The rule itself is `CLAUDE.md` Rule 23; this is the text it used to carry inline, kept verbatim so a reader who wants the reason finds it where the mechanism is documented rather than paying for it on every request.

The domain set lives in `locks.domains` of `.sza-profile.json` and nowhere else (S2697): currently `Build.Phone` and `Build.Wear` for gradle, `Code.Phone`, `Code.Wear` and `Code.Scripts` for edits, `Code.Fixture` for lock-test scratch - so a watch edit and a phone edit proceed at once, and so does a scripts edit beside either. Pass the changed set and let it map: `enter-code-lock.ps1 -Files "<paths>" -Reason "<ticket/skill>"`; a gradle entry point derives its domain from the module it already builds, and every script invoking `gradlew`/`gradlew.bat` acquires via `Enter-BuildLockOrExit -Domain <..>` and releases the same set after success or failure. `enter-code-lock.ps1` exits **4** meaning "queued, not yet your turn - do not edit sources yet". **The waiting contract, which no script can enforce for you:** when queued, run `pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name <domain> -Reason "<why>"` as a **background** task - its exit is the "your turn" signal, a multi-domain wait is granted only when you are head in *every* domain of your set - and keep working on what needs no lock: reading, research, specs, catalog, log analysis. The exit-4 message prints both follow-up commands with `-Handoff <path>` (S2403): pass that path - in a runtime with no session id the waiter and the post-grant re-run are strangers to the first ticket, and without the handoff each takes a second ticket for the same intent and the waiter waits out the reservation window behind its own dead first ticket. **The test is not "is it source" but "is this path already serialised by something finer" (S2338)** - the domain lock exists to order what nothing else orders, so a path another mechanism already makes exclusive does not need it, and a path nothing else covers does. By that test: sources, resources, build files, repository scripts, `.claude/`, `.github/`, documentation under `docs/` and notes under `dev/` need the lock, because they are hand-edited with nothing finer over them - and so do the content trees `play/`, `fastlane/`, `store_assets/`, `delivery/` and `maestro/` plus the root site pages, documents and icons, which since S2342 take `Code.Scripts` instead of falling through to the full set: none of them compiles, links or packs into an APK, so serialising phone and watch work against a store-listing edit protected nothing, and measured 2026-09-02 that was 8 of the 11 recent full-set acquisitions. Fail-closed keeps the rest: `corex/` and the modules `benchmark/` and `watchface/` still take every code domain, the last two because giving a module a code domain without a `Build.*` domain is a boundary decision this did not make. `PLAN/` does **not**, and is one of the table's two exemptions: a spec file and its phase folder belong to exactly one ticket, held exclusively by `ticket-lease.ps1`, and the journals plus both release files are written only through the catalog mutators, which all hold the catalog's own mutex. A PLAN-only changed set therefore resolves to no domain at all and `enter-code-lock.ps1` exits 0 with nothing to release - measured 2026-09-02, that is 55% of recent closures, each of which used to take a domain that protected nothing. **`temp/` is the second exemption (S2710), and it is exempt for the opposite reason:** nothing there is serialised by anything finer because there is nothing there to serialise - by Rule 1 it holds artifacts, backups, logs, per-ticket scratch and throwaway sandboxes, none of which compiles, links or packs into an APK, and no two sessions hand-edit one file there as shared text. Until that rule existed no pattern named `temp/` at all, so a path under it matched no anchored prefix and took the fail-closed answer, EVERY code domain, to write a file its own run then deletes: measured 2026-09-07, `assert-always-loaded-budget`'s contract suite failed its ratchet case with exit 4 while `Code.Scripts` itself was free and only its queue was held by a foreign session - a suite whose verdict depended on a sibling's queue rather than on the gate it tests. The coordination files at the `temp/` root are unaffected: the lock mechanism writes them with its own primitives, never through `Enter-CodeLockOrExit`. **The window is the edit and nothing else (S2419):** it opens immediately before the first edit of a step and closes the moment that step's last file is written, released by your own `exit-code-lock.ps1` rather than by whatever runs next. Everything after that edge runs unlocked - the verification predicates, `plan-tick.ps1` (a `PLAN/` write takes no domain anyway), the phase's `Project compiles` build, which `Build.*` already serialises on its own, the unit suite, the `post-change.ps1` gate batch, the dev log, the catalog. "Release it right after" was the whole of this rule until 2026-09-03 and did not say after *what*, so three texts each answered differently and all three answered "after the closure": `/spec-dev` step 6a and its reference both promised `post-change.ps1` would release at step 10, and this script's own help repeated it. Measured over `temp/AGENT-CHAT` for 2026-09-02 21:30 .. 2026-09-03 01:20 - 112 lock events, 51 closed acquire/release pairs - that cost a 95 s median hold on `Code.Scripts` with a 703 s maximum, of which the closure's own gate batch was 19.0-48.8 s; all 51 queue waits in the window were on that one domain and it reached ten deep. `post-change.ps1` still releases, since S2419 before its gates instead of after them, but only as the backstop for a run that ended early. Withdraw a dropped intent with `pwsh -NoProfile -File scripts/utils/withdraw-lock-ticket.ps1 -Name <domain>`, or `.\a.ps1 uqb` / `uqc`; only the granted-but-unclaimed head self-heals (S2194). Inspect the order with `lock-status.ps1 -Name <domain> -Queue`, which prints one section per domain when given a bare `Build` or `Code`. A pre-split `temp/BUILD.LOCK` or `temp/CODE.LOCK` still holds **every** domain of its type until its owner releases it. Ticket ownership, eviction, the head-of-queue reservation, the outcome marker (never trust a background task's exit code) and re-entrancy: `docs/DEV_OPS.md` "Concurrent-agent locks".

The lock window text above superseded "release it right after", which had been the whole of the rule until 2026-09-03 and did not say after *what*.

### How much runs at once - the concurrency bound (S3308)

The locks above order work that already exists; nothing decided how much of it there should be. Until this ticket the level was set by two hands that could not see each other - lanes started deliberately with `a.ps1 r1`/`r2`/`r3`, and sessions opened beside them - so the total was never anybody's number. Measured over 2026-09-17..19: up to **11 simultaneous sessions**, `check_fast_app_v2_Code` running a median **~50 s** against the **14.1 s** `docs/BUILD_TEST_FAST_PATH.md` documents for `a.ps1 fk`, and **40** domain queue entries in the window. The idle half of the old argument had already been paid off - 64 runner runs, **0** of them moving no status - so the price of concurrency here is build time, not wasted runs.

The bound is one declared block, `concurrency` in `.sza-profile.json`, and nothing else configures it:

- `maxSessions` - the owner's ruling of 2026-09-19, currently 6.
- `activeAgentWindowMinutes` - how recently an agent must have acted to count. **Active is the owner's definition of 2026-09-19**: it talked in the chat, wrote a file or ran a script inside that window. A hung agent, an interrupted one, one stopped by usage limits, one that finished its task and sits idle, and one the owner has declared dead are all out of the count - the bound measures load, and an open window is not load. This is why the count is of recent activity and not of running processes: 12 agent processes were live on 2026-09-19 while the chat showed 7 that had acted at all.
- `idleRunSharePercentMax` - the share of runner runs that moved no status, above which more lanes buy nothing.
- `fastCheckLagSecondsMax` - how far past its documented duration the phone code fast check may run. 14.1 s of lag is the check taking twice the documented figure.
- `holdSecondsMax` - the age at which a Rule 23 domain hold is reported as having outlived its edit window.

`a.ps1 r1`/`r2`/`r3` read that block before starting and **exit 4** naming the bound crossed, with the measured value beside the declared one. `r0` is exempt: MONO is one agent alone, which is the opposite of adding load. The measured side comes from `scripts/utils/measure-process-throughput.ps1`, which sweeps the three journals that were already being written - the runner's run rows, `temp/metrics/gate-executions.jsonl` and the fast-check logs - and answers any window with the five values plus the model each policy actually produced, a warning for a declared policy that never ran, and the long-hold report below. Nothing swept them before, so every process audit re-mined the same corpus by hand and its numbers died with the conversation.

**A hold that outlived its edit window is now reported, not discovered afterwards.** Rule 23 releases a domain at the last file a step writes. On 2026-09-19 one `Code.Scripts` hold ran at least 41 minutes with no release, against 4 to 418 seconds for every other hold of that domain in the same stretch, and alone produced the window's longest wait - 747 s served by a neighbouring session. The summary pairs acquire and release events from the agent progress records and the queue handoffs, prints every hold past `holdSecondsMax` with its domain, holder and duration, and carries **an acquire with no release as `still held`** rather than dropping it for having no end stamp - that shape is precisely what a dropped hold looks like. The longest wait printed beside a hold is a lower bound: the grant itself is not journalled, so a waiter that took a ticket during the hold is credited only with the time up to the hold's end.

### The temp/ root inventory (S3030)

`temp/` root holds three kinds of content, and `scripts/utils/archive-temp.ps1` has always said so in
its own synopsis: **fixed infrastructure** that must never move, **per-ticket scratch** (`temp/Sxxxx/`,
kept while the ticket is live and judged by its catalog status, never by its name), and **loose
per-run artifacts** that are legal only while a retention window covers them. CLAUDE.md Rule 10 used
to describe the first kind and enumerate it inline, which is why the sentence "the root holds only
what Rule 10 lists" could not be checked: two of the three legitimate classes were outside the
sentence, so a literal reading condemned 2459 of 2470 entries and a charitable one condemned nothing.

**One declaration, two consumers.** `scripts/utils/temp-root-inventory.ps1` is the only place the
allowed set is written. It derives what has an authority - the `*.LOCK`, `*.QUEUE` and `*.TURN-*`
names from `agent-lock-domains.ps1`, the harness's own directories from `.sza-profile.json`, the two
device stores from `scripts/devtest/lib/device-store-paths.ps1` (S3036, which created the authority
those two rows were hand-listed for want of) - and enumerates by hand only the names nothing else
declares, each carrying the writer that creates it.
`archive-temp.ps1` reads it to decide what a sweep may never move; `scripts/quality/assert-temp-root-inventory.ps1`
reads it to decide what the root may hold. Adding a fixed name at the root is one row plus its
reason, and the gate is what tells its author the row is missing - which is how
`temp/catalog-touch.marker` was found on 2026-09-12 and declared in S3036: written by
`dev/CATALOG/scripts/query.ps1`, read by the Rule 29 hook `guard-catalog-before-kt-search.ps1` and
removed at every session start, it exists only between a catalog query and the next session, so the
one-shot census that produced the first inventory could not see it. An intermittent name is the
shape a census misses and a standing gate catches.

**The two consumers read one member differently, on purpose.** `FixedFilePatterns` is a live sink the
log tooling appends to, so a sweep must not move it. `RetainedFilePatterns` is legal *because* the
sweep carries it away: protecting it would stop the retention that was the only reason to allow it,
and the per-run transcripts would then accumulate without bound.

**Ask the root, do not read a list:**

```powershell
pwsh -NoProfile -File scripts/quality/assert-temp-root-inventory.ps1
```

Exit 0 means every top-level entry is a declared name, a declared pattern, or a ticket directory.
Exit 1 names each entry declared nowhere and the class it failed. The gate is **release scope** (Rule
33): the directory is shared by every concurrent session, so a per-closure run would fail whoever ran
it over a neighbour's lock file - which is how S2998's blacklist and then S3025's suite assertion
were each broken by a process that was not under test.

Two facts worth knowing before touching that root:

- **The per-run check and build transcripts are legal, and they are the bulk of it.** Measured
  2026-09-12 the root held 2470 entries, of which 2194 `check_fast_*.log` and 169 `build_debug_*.log`.
  That is not a backlog - it is the seven-day retention window's equilibrium at the repository's
  check rate, and the archiver's dry run on the same tree planned to move 87 of them.
- **That corpus is load-bearing, so do not "clean it up".** `scripts/utils/measure-build-lock-wait.ps1`
  reads it from the root by glob (and so do `scripts/metrics/measure-unit-fork-parallelism.ps1` and
  `scripts/builders/get-last-build-failure.ps1`), `dev/REFUTED_APPROACHES.md` instructs a future
  session to re-run the first of those before re-proposing a refuted build-lock change, and S2606
  reconstructed 1281 runs from it. Relocating the logs is a ticket with three readers to move in
  lock-step, not a tidy-up - and it is a declared non-goal of S3030.

**A ticket-shaped FILE is not scratch.** `temp/S3030/` is legal; `temp/S3030_notes.txt` never was.
The retired flat `temp/Sxxxx_*` scheme left exactly the second shape behind, and the gate's ticket
class matches directories only so those files surface instead of hiding behind the rule that protects
the directories.

### Release freeze - admission control for a sweep, not a sixth lock (S3010)

Rule 23 orders concurrent work at the granularity a **change** needs: five domains, each held for one
edit, released at the last file written. A release sweep needs the opposite promise - not "no one else
is editing this file right now" but "no one is ADDING anything to this tree until I have a verdict" -
because every gate at `/spec-prerelease` steps 0.4 to 0.9 measures the whole tree rather than a change
(Rule 33). The two are not the same lock taken for longer: a domain held for a whole sweep would block
every sibling from all work, which is unacceptable and also unenforceable, since the sweep itself takes
those domains to fix what it finds.

**What it cost before there was one.** Over the r37 sweep on 2026-09-12, between its first changelog
row (`prerelease-37`, 05:02:09) and its last (`S2687 prerelease r37`, 06:47:37): 33 rows landed and
exactly 2 were the sweep's own. The `app_v2` lexeme corpus read 135 strings, then 146, then 145 across
one sitting; the `wear` corpus was translated at 59 strings and held 18 entirely different ones by the
time that round was imported. The costliest single interference was `S3007`, a Wear Tourist
mini-program scaffolded at 05:58:08 and implemented at 06:17:15 - a ticket **opened** after the sweep
had already cleared the wear gate it invalidated. That is what sized the refusal: stopping work being
opened buys most of the convergence for the narrowest possible refusal.

**The queue runner was not the problem, and standing it down is not the fix.** `temp/STOP-SPEC-QUEUE`
carried `stop requested 2026-09-11T23:39:33` and the newest record across
`temp/spec-queue/runs-{a,b,c}.jsonl` finished 2026-09-11T23:49:21 - the runner had been down for five
hours before the sweep began and contributed zero of the 31 sibling rows. Every interfering write came
from an interactive session. `release-freeze.ps1 -Verb Take` still stops the runner, and `Release`
restores it only when the freeze was what stopped it, so a stop the owner requested for their own
reasons survives; but that is insurance against a hole that was simply not open during r37, never the
mechanism.

**Mechanism.** `scripts/utils/release-freeze.ps1` owns a marker at `temp/RELEASE-FREEZE.json` and
answers one question - is a freeze held, and by whom. `.claude/hooks/guard-release-freeze.ps1` is the
only thing that refuses: a `PreToolUse` hook on `Bash` and `PowerShell` that blocks a call claiming a
ticket - `ticket-lease.ps1 -Verb Claim`, `spec-preamble.ps1`, `spec-next-preflight.ps1` - while a live
foreign session holds the freeze. It never refuses the holder, a ticket already in flight, work under
`PLAN/` or `temp/`, reading, analysis, or any command at all while no marker exists; and it fails open
on a malformed payload, an unreadable marker or a missing script, because the cost of a wrong allow is
one uncoordinated ticket while the cost of a wrong refusal is every command the machine runs. The hot
path is one `Test-Path`: the child process that reads the marker is spawned only while a freeze
actually exists.

**Why a hook and not a domain.** Every ticket-opening script under `scripts/spec_catalog/` is a
generated canon forwarder - `spec-preamble.ps1`, `spec-next-preflight.ps1`, `ticket-lease.ps1` and
`release-queue.ps1` are the same 132-line file - so none of them may carry the check, while `.claude/`
is this repository's own (Rule 8). Adding a sixth domain would also have put the change on both sides
of the canon boundary and under S2998's lock contract suites, for semantics that are not a domain's.

**Three ends, any one sufficient**, so it cannot outlive its sweep: an explicit `Release`; the owner
session no longer being live, judged by `Get-AgentTicketLiveness` - the same function that decides
every lock and lease, at the `SpecTicket` timings; and an absolute expiry stamped into the marker at
Take, default four hours and refused above the `SpecTicket` ceiling. A reader that finds a dead or
expired marker reports it as absent and deletes it in the same call, so an abandoned freeze costs the
next session one line of output instead of a standoff (S2761 records that failure mode for the code
lock). Liveness is deliberately **not** a pid test: the process running Take exits the moment Take
returns - the same reason `lock-status.ps1` prints "acquiring process - exits at acquire, not the
holder" - and `Test-AgentIdentityProcessAlive` answers false for a session-guid owner by design, which
is what a Claude session has, so either shortcut would report every live freeze as dead.

**Convergence honesty, which needs no co-operation at all.**
`scripts/quality/release-scope-fingerprint.ps1` records what the judged tree looked like when the
gates cleared, in named input groups, and says which moved before the verdict. Step 4 re-runs **only**
the gates reading a moved group - `assert-release-scope-gates.ps1 -OnlyGroups <names>` - at most once,
and if the tree moves again the verdict is not PASS and the report says the sweep could not converge,
naming the groups and the interfering rows. Each gate still measures the whole tree when it runs; the
groups decide only whether to run it, never what it looks at, so Rule 33's placement is untouched. A
gate absent from the mapping is always re-run - the safe default is to re-measure, never to assume
unchanged. The hash covers path, length and mtime and never file contents: measured 2026-09-12, all
six groups together cost 1163 ms, and content hashing would put the check in the same cost class as
the gates it exists to save. Expect `specs-archive` to move on most sweeps - `PLAN/` is lock-exempt and
every sibling archives into it - which re-runs `assert-archive-artefacts` alone.

Contract suites: `scripts/utils/release-freeze.tests/Run-Tests.ps1`,
`scripts/quality/release-scope-fingerprint.tests/Run-Tests.ps1`,
`.claude/hooks/tests/Run-GuardReleaseFreeze-Tests.ps1`.

### Mono mode - one agent alone on the project (S3158)

Every coordination mechanism above - the domains, the ticket lease, device leases, the agent chat - orders an agent against its siblings. When the owner runs exactly one agent, each of them is a model turn spent ordering against nobody: a lease claim and release per ticket, an enter and exit per edit step, a chat line per stage or phase, a chat read at every refusal. MONO removes those turns by declaration.

**Entry points.** `/spec-all -m <Sxxxx>` and `/spec-code -m <Sxxxx>` for one ticket; `.\a.ps1 r0` for a chain, which is `scripts/utils/run-mono-queue.ps1` handing over to `run-spec-queue.ps1` as instance `mono` (`runner.instances.mono` in `.sza-profile.json`, silent like a/b/c) with the prompt `/spec-all -m {id}`. The skip list and the delegation context `mono=1` live in `.claude/reference/mono-mode.md` and nowhere else.

**What the run skips.** The ticket lease (the preamble runs `-NoLease`), `enter-code-lock.ps1` / `exit-code-lock.ps1` / `wait-for-lock-turn.ps1`, device leases and `device-ready.ps1 -ClaimFree`, and every model-posted or model-read chat line after the start. Nothing is checked or waited for: `r0` does not look for another runner, and a red build, a changed file or a busy domain is the run's own, never a sibling's.

**The one start call, and why it drops state unconditionally.** `scripts/utils/mono-mode.ps1 -Verb Start` prints the chat history once, drops every ticket lease (`ticket-lease.ps1 -Verb Clean -Force`) and every lock and queue (`clear-agent-lock.ps1 -Name Build|Code -Force`), and posts one journal note. The harness is consumed here (section "The process harness comes from the canon"), so a gradle wrapper still takes its build domain through `Enter-BuildLockOrExit` and a closure still releases through its backstop. Uncontended that costs milliseconds and no model turn; behind a lock left by a dead session it would be a wait, which the mode rules out. Judging the leftovers' liveness first is the check MONO removes, so `-Force` is not a shortcut but the definition. `-DryRun` lists and drops nothing; `-Stores Leases` is how the contract suite stays off the real lock files, which have no fixture root.

**No marker.** Nothing is written for other sessions to respect and no hook refuses them: the declaration is that there are none. Running a MONO run beside a leased one is a misuse the mode does not defend against.

Suite: `scripts/utils/mono-mode.tests/Run-Tests.ps1` (fixture roots `FMS_TICKET_LEASE_ROOT`, `FMS_AGENT_CHAT_ROOT`).

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

### The closure ledger - one gate batch per ticket per set of bytes - S3301

`scripts/post-change.ps1` judges file content but used to hold no memory of having judged it, so a ticket whose edits arrive in fragments paid for the whole batch once per fragment. Measured 2026-09-18 on the S3193 run: two closures 69 seconds apart over the same six files, `39577 ms` then `27532 ms`, four build-domain round-trips instead of two, and not one new judgement out of the second pass.

The ledger is `temp/metrics/post-change-closures.jsonl`, written by `scripts/quality/lib/post-change-closure-ledger.ps1`. After a **clean** `PASS` the facade appends a record: the ticket, the change type, the module, the `-ScopeToFile` mode, the `HEAD` revision, and per changed file its path, length and SHA-256. At the start of the next run it looks for a record satisfying all six conditions:

1. Same `-Target`, and it has the `Sxxxx` shape - a closure without a ticket has no boundary for reuse to live inside.
2. The recorded run ended in a clean `PASS`. `PASS WITH ADVISORIES` means a gate saw something it could not attribute, which is a reason to look again; `FAIL` writes nothing at all.
3. Same change type, module and `-ScopeToFile` mode.
4. Same `HEAD`.
5. This run's changed set is covered by the record file for file, with identical length and hash; its deleted set is a subset of the record's. A set carrying one member the record never saw does not qualify.
6. The record is inside the reuse window (45 minutes; `FMS_POSTCHANGE_REUSE_WINDOW_MIN` overrides it).

On a hit the gate batch does not run - `Invoke-Gate`, `Invoke-AdvisoryStep`, `Invoke-FixedInputGate` and `Start-PooledGate` all short-circuit, so nothing is started in a thread either - and the verdict says whose it is:

```text
post-change: PASS (REUSED from run a84027b32b91, 74s ago over 6 unchanged file(s); gate batch not re-run, 241 ms)
  Same ticket, same bytes, same HEAD, clean PASS - re-run with -NoReuse to judge them again.
```

The bare word `PASS` is never printed for a reused verdict: this run judged nothing, and the run it names is the one whose protocol file holds the evidence.

Everything unknown means "run the batch". A missing, unreadable or malformed ledger, an unreadable file in the set, a parse failure on any record - each of them is a miss, never an error, and a ledger write that fails is swallowed: the ledger can cost the next run its shortcut and nothing else. Turn it off with `-NoReuse` for one run or `FMS_POSTCHANGE_NO_REUSE=1` for a session.

This is not a cache of gate verdicts across tickets, and it never makes a closure cheaper the first time. It removes exactly one thing: paying twice for the same bytes inside one ticket.

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

### Unjoined test scope ratchet - S2748

One rule, `test-unjoined-scope`, counting `CoroutineScope(` constructions under `app_v2/src/test` and `wear/src/test` whose line does not also name a test dispatcher, a test scheduler or a `TestScope`. Such a scope is not a child of `runTest`: nothing joins it, nothing can cancel it, and its coroutine runs on a real dispatcher past the end of the test body. `kotlinx-coroutines-test` then hands the escaping exception to the **next** `runTest` on that worker process, and with `forkEvery = 100L` in `app_v2/build.gradle.kts` that can be any of a hundred classes - S2743 spent a ticket clearing the name of a test that was only the witness, and S2746 found the culprit two files away.

```powershell
# Every offending construction, file:line
pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only test-unjoined-scope -List

# PASS/FAIL verdict
pwsh -NoProfile -File scripts/quality/assert-source-gates.ps1 -Only test-unjoined-scope -Gate
```

Two cures, both accepted by the gate and both named in its refusal: build the scope on the test's own scheduler (`CoroutineScope(UnconfinedTestDispatcher(testScheduler))`, which also drops the construction out of the count), or join it in `@After` with `runBlocking { scope.coroutineContext.job.cancelAndJoin() }`.

**The ratchet stops growth; it does not measure the repair.** The seven DataStore tests S2748 fixed take the second cure - `PreferenceDataStoreFactory.create` runs in `@Before`, where no `testScheduler` exists yet - so their `CoroutineScope(Dispatchers.IO + SupervisorJob())` fields stay counted after the fix. The baseline was seeded at the measured 20 with the nine sites already repaired, and it falls when a file converts to the first cure, never rises. The link between a field and the `@After` that should join it is not expressible in a regex, which is why the count is of constructions rather than of defects.

**One entry for both modules**, unlike the phone/wear split used by `swallowed-cancellation` and `unpoliced-animation`. That split exists so a regression in shipped code cannot hide behind a cleanup in the other module; here the subject is a test-authoring habit that travels with whoever writes the test, and `wear/src/test` contributes two of the twenty sites, so a second baseline would carry more bookkeeping than signal.

**Placement class: per-ticket** (Rule 33, named at birth). The subject is the changed test file itself, not the tree or a shipped artifact, so release scope does not apply; and under `-ScopeToFile` the runner judges each changed file against its own HEAD version, so a sibling session's WIP cannot fail the close.

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

### The three shapes of a scoped gate - which gate is in which class

`CLAUDE.md` section 12 states the three shapes; the membership lives here, because it changes with every new gate and an always-loaded page pays for that churn on every request of every session (S2828).

- **Count ratchet, FATAL on a per-file delta against HEAD:** `neuroslop`, `listener-symmetry`, `flavor-flag`, `deprecated-pm`, `public-mutable-flow`, `focus-highlight`. A sibling's WIP raises the project-wide count and still cannot fail your close.
- **Repo-wide re-render, advisory under `-ScopeToFile`:** `icon-inventory`, `script-cheatsheet`, `device-profile-matrix`, plus three stages of `settings-doc-sync` - `catalog-complete`, `annotations` and `reference-fresh` - each advisory only when the set feeds none of that stage's own inputs, and fatal the moment it feeds one (S2604 for the render, S2831 for the other two). Each regenerates from or judges the whole tree, so its drift is not attributable to one changed file; the gate names the stage and the finding and returns exit 3 for the facade to downgrade.
- **Fixed input, FATAL only when a declared input is in the set:** every gate dot-sourcing `scripts/quality/lib/fixed-input-scope.ps1` and invoked through `Invoke-FixedInputGate` - today `doc-pin-drift`, `flavor-matrix-doc`, `launcher-reset-coverage`, `oss-notices`, `rule-digest-sync`, `wear-canonical-key-parity`, `wear-settings-parity`, `wear-wire-vocabulary-parity`. Outside the set the gate prints its findings and returns child exit 3, which the closure reports as an advisory naming the script to re-run project-wide. Read the membership off the dot-source, never off this list: S2824 gave three gates the fork, S2827 a fourth and S2828 four more inside two days, and the name lists in `CLAUDE.md` and `AGENTS.md` were stale both times.

### The gate placement review (S2537)

`assert-release-scope-gates.ps1` ends with `measure-gate-frequency.ps1 -Placement`, printed as a report and never as a gate. It reads `temp/metrics/gate-executions.jsonl` and gives every gate its executions, findings, **median** run, typical total (median x executions) and cost per finding, marking the rows worth re-judging by CLAUDE.md Rule 33. It is advisory on purpose: the runner collapses every non-zero code to FAIL, and a candidate is a row a human then judges by the four-part test, whose exceptions - later work builds on the defect, the evidence exists only at the moment of the change, agents read the artifact between releases - no arithmetic over this journal can see.

**Read it by the median.** Ranked by the mean, `detekt-gate` was the largest cost in the repository: 86 398 s over 751 runs, 54% of all closure gate time. Its median is 11 ms - the clean-verdict cache answers almost every call - and 88.9% of that sum came from ten runs, one journalled at 34 711 s (9.6 hours), which is a stall and not analysis (parked as S2538). The cost floor is therefore applied to median x executions and never to the observed sum, so one stall can neither nominate a gate nor hide one. The observed sum is still printed beside it, because the gap between the two IS the stall signal.

Measured over 2026-08-24..2026-09-04 (69 647 records), the honest per-closure ranking is `settings-doc-sync-gate` 42.5 s, `script-suite-regression` 13.7 s, `catalog-sync` 10.6 s, `fgs-notification-gate` 5.7 s over 1022 closures, `resource-link-gate` 5.8 s, `ctor-arg-slots-gate` 5.1 s. `settings-doc-sync-gate` takes the `Build.Phone` domain lock for its manifest-regeneration stage, which is why its p90 is 154 s and its maximum 564 s against that 42.5 s median: a rank-and-file closure queues behind a sibling session's build.

### The gate-placement registry (S2870)

`scripts/quality/gate-placement.jsonl` is the decision record for **where every gate runs and who decided that**. One JSON object per line, fields `gate`, `kind`, `scope`, `decided`, `ticket`, `basis`, `reason`, plus an optional `label` when the closure invokes the gate under a name that is not its file name (`assert-detekt.ps1` runs as `detekt-gate`). It is hand-maintained: no generator rewrites it, because a decision is not derivable from the tree it describes.

**Why it exists.** Placement decisions were being made and then written as prose in the `.DESCRIPTION` of whichever runner the gate left. S1939 moved three gates to release scope and recorded the reasoning that way; four more decisions to *not* move a gate lived in the same place. None could be queried and none could be checked, so nothing could tell that a retired gate had been quietly wired back into the closure - and the reverse was invisible too, because a gate missing from `post-change.ps1` looked exactly like a gate deliberately kept out of it.

**Scope classes, and the runner that satisfies each:**

- `per-ticket` - `scripts/post-change.ps1`. The closure.
- `release-scope` - `scripts/quality/assert-release-scope-gates.ps1`, run by `/spec-prerelease` step 0.4.
- `prerelease-content` - `assert-prerelease-content-gates.ps1`, or a direct step of the `/spec-prerelease` driver.
- `build` - `scripts/release/standard-release-gate.ps1` or a script under `scripts/builders/`.
- `fast-batch` - `assert-fast-gates.ps1` and nothing else: it runs when an operator types `.\a.ps1 fg`.
- `hand-run` - no runner references it.
- `stage` - another gate invokes it directly and it inherits that gate's placement.
- `runner` - an aggregator that runs other gates and has no placement of its own.

**Membership in `assert-fast-gates.ps1` does NOT satisfy `per-ticket`, and this is the registry's one substantive claim.** `fg` is a target a human types; `post-change.ps1` runs at every closure. A gate wired only into the fast batch runs when someone happens to think of it. That distinction is four recorded incidents, two of which reached the owner's phone: `assert-ctor-arg-slots.ps1` lived in the fast batch alone and the 236th `AppSettings` field crossed the JVM's 255-slot ceiling, killing the app in `Application.onCreate` (S2300); `assert-migration-test-pairing` lived in the fast batch alone and a Room migration with no instrumented test deleted the database on first launch (S2306). Both gates existed, both were correct, and both passed every by-hand run.

**A new gate names its scope class at birth.** CLAUDE.md Rule 33 has said so in words since it was written; `assert-gate-placement.ps1` is what makes it binding. A gate script with no registry row fails the closure, and the refusal names the class its wiring implies. Unnamed still means per-ticket.

**`basis` separates a judgement from an observation.** `judged` means a human applied the Rule 33 four-part test and this row records the verdict; `seeded` means the row only states where the gate was found when the registry was created. The distinction is load-bearing rather than decorative: `measure-gate-frequency.ps1 -Placement` suppresses a `judged` row so the advisory stops re-proposing a decision already made, and if the initial 100-row seed had been written as `judged` the report would have gone permanently quiet over decisions nobody made.

**`kind: closure-step`** covers the labelled steps of `post-change.ps1` that are not gates at all - `dev-log`, `catalog-sync`, `detekt-format`, `doc-pins-sync`, `strings-audit`. They write, render or sync, so they can never report a finding, and the placement report used to rank them forever as expensive gates with a zero catch rate. They carry no file on disk and the verifier exempts them from the one-record-per-script rule.

**Measured on adoption, 2026-09-10:** 109 gate scripts, 114 records, 8 of them `judged`. `per-ticket` 45, `fast-batch` 33, `release-scope` 14, `hand-run` 11, `prerelease-content` 4, `build` 3, `runner` 3, `stage` 1. The 33 in `fast-batch` are the population worth re-judging - each is either legitimately hand-run or an S2300 waiting to happen - and naming the class is what makes that list exist. Suppressing the five closure steps removed roughly 19 500 s of typical gate time from the candidate list.

**The candidate COUNT is a snapshot and will move; the suppressed set is what is stable.** The placement report reads `temp/metrics/gate-executions.jsonl`, which is gitignored, machine-local and grows with every closure, so a gate drifts across the 600 s threshold on its own: the count read 16 before this change and 11 immediately after, then 12 an hour later on 1082 more records, with the same five rows suppressed throughout. Quote the suppressed set and the reason, never the survivor count, when citing this report.

**What the gate does NOT guard.** It judges `scope` against real wiring in both directions, but nothing pins `basis` or `reason`: flipping a `judged` row back to `seeded`, or rewriting the reason it carries, passes. The registry is a hand-maintained, version-controlled file, so that edit is visible in review and in `git diff` - but it is not mechanically refused, and no gate can supply the verdict a human removed.

Gate: `scripts/quality/assert-gate-placement.ps1`, per-ticket under the fixed-input contract (S2824) - fatal when the changed set carries a gate script, a runner or the registry, advisory otherwise. Suite: `scripts/quality/assert-gate-placement.tests/Run-Tests.ps1`.

### The fast battery prints two verdicts (S2693)

`.\a.ps1 fg` given a changed set - `assert-fast-gates.ps1 -ChangedFiles <the comma-joined paths this change touched>` - splits its summary in two. **YOUR SET** holds the gates that actually received the set and judged it, and it alone decides the exit code. **THE TREE** holds every gate that judged the whole project regardless of what was passed; a red gate there prints its name, says the work belongs outside the caller's set, and leaves the exit code at 0. Run with no changed set - a release run, a CI run, `.\a.ps1 fg` typed bare - and the behaviour is identical to before: one block, any red gate exits 1.

Which block a gate lands in is a property of the invocation, not of the gate: it is `set` when the gate was handed `-ChangedFiles` and `tree` when it was not, computed beside the forwarding decision so the two cannot drift apart. One promotion crosses that line, and it is the only way a tree gate reaches the exit code: a tree gate whose FAIL output names a repo-relative path from the changed set moves into YOUR SET as `set-named` (the S2693 re-audit found that without it a gate that takes no `-ChangedFiles` - exit contract, script references, memory budget - reported the caller's own defect as advisory). The search is for the path in both slash forms, never the bare file name. All three classes are journalled - the gate rows and the batch row carry a `scope` field, absent on every row written before the split, so a reader must treat a missing field as unknown rather than as `tree`.

Why: measured over 2026-08-28..2026-09-07, the battery ran 62 times and was clean on none of them, median three red gates per run, the reds being project-wide invariants catching another session's unfinished work in a tree that carries up to six concurrent sessions. A verdict red on every run stops being read, and the operator who stops reading it also stops seeing the reds that are his. This weakens no invariant: `assert-release-scope-gates.ps1` keeps the tree fatal at the boundary that ships, which is where a tree-wide subject belongs under Rule 33.

Contract suite: `scripts/quality/assert-fast-gates.tests/Run-Tests.ps1`. It asserts the relation between the printed blocks and the exit code rather than an expected colour, because the tree it runs on carries other sessions and any absolute expectation would be flaky by construction.

### A closure step waits under a ceiling (S2538)

**No step of a closure waits without a limit, and an exhausted limit is exit 2 - never PASS and never FAIL.** The contract is S1338's, written after one `post-change.ps1` run hung for three hours and still reported PASS; `scripts/utils/process-timeout.ps1` bounds the process, and S2538 extended the same rule to the two places the closure waits for something other than a process.

**The ceiling sits on the join, because the child already carries its own.** `assert-detekt.ps1` caps its `Build.Phone` queue wait at 900 s and its gradle run at 600 s, so the detekt child cannot exceed roughly 1500 s - and yet `detekt-gate` was journalled twice at **34 711 s (9.6 hours)**. That time was not spent in detekt. It was spent in `Receive-Job -Wait`, which had no ceiling, so a gate obeying its own limit was awaited by a closure that had none. Both joins now wait at most 1800 s - `Wait-Job -Timeout`, because `Receive-Job` carries no `-Timeout` on pwsh 7 - in `scripts/quality/lib/gate-pool.ps1` for the sixteen pooled gates and at the `detekt-gate` call site for detekt. A `MISSING` row for `detekt-gate` therefore means the join timed out, not that a gate is absent.

**The journalled duration is the work, not the wait.** A pooled child times itself and hands the number back through `Get-PooledElapsedMs`; the detekt job now does the same. Without it the wrapper's stopwatch measured the join - 11 ms on a healthy closure, because the job had long finished, and hours on a stalled one. Both numbers describe waiting, and the column is read as the cost of the analysis.

**A row names the run that wrote it.** `runId` in each telemetry record answers what the two stalls could not: they were journalled as a `PASS` row and a `FAIL` row for the same gate 23 ms apart with identical durations, and nothing distinguished one run reporting twice from two runs released by one event. `measure-gate-frequency.ps1 -Placement` now names any gate a single run reported more than once, and stays silent for the history written before the field existed.

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

### Activity locale wrapper gate - S2930

`scripts/quality/assert-activity-locale-wrapper.ps1` refuses an Activity in `app_v2` that resolves its resources outside the app's locale wrapper - one that neither extends `BaseActivity` nor overrides `attachBaseContext` with `LocaleHelper.applyLocale`. Such a screen shows the framework configuration's language rather than the one the user chose, and it does so silently: every other screen in the same flow is correct, so the defect reads as a translation bug. Measured 2026-09-11, 36 Activity declarations existed across the module and 34 of them were unwrapped; `WearCompanionActivity` was observed in Russian on an `en-US` device one tap from a screen showing English.

Scope is every non-test source set, `app_v2/src/*/java`, not just `src/main`: nine of the offenders lived in flavor sets, including both VR activities and both launcher ones, and a gate rooted at `src/main` would have reported them clean.

**Exclusions are named, never counted.** `scripts/quality/activity-locale-wrapper-baseline.txt` carries one `<repo-relative path> | <reason>` per line, and a row with no reason fails the gate as a misconfiguration (exit 2) instead of passing quietly. A count ratchet was rejected for this rule: it admits a fresh violation the moment an old one is fixed, and the exemptions here are genuinely rare and genuinely need reading - the one that matters is `PrintDispatchActivity`, where wrapping the context makes Samsung/One UI reject `PrintManager.print()` outright (S0613).

Per-ticket by Rule 33, so it runs from `post-change.ps1` on any changed `app_v2/src/*/java/**.kt`, and it is also listed in `assert-fast-gates.ps1` so `.\a.ps1 fg` reports it.

```powershell
# Full tree
pwsh -NoProfile -File scripts/quality/assert-activity-locale-wrapper.ps1 -Gate

# Only what this change touched
pwsh -NoProfile -File scripts/quality/assert-activity-locale-wrapper.ps1 -Gate -ChangedFiles "a.kt,b.kt"

# Report, naming each excused Activity and its reason
pwsh -NoProfile -File scripts/quality/assert-activity-locale-wrapper.ps1 -List
```

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
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode ConnectedAndroidTest -Tests com.sza.fastmediasorter.leak.LeakDetectionInstrumentationTest
```

Routed through the builder rather than `gradlew.bat` because Rule 23 admits one gradle invocation per
build domain and a direct call bypasses `temp/BUILD.PHONE.LOCK`. In this mode `-Tests` is forwarded to
AndroidJUnitRunner, not to Gradle: name **either** fully qualified classes (`Pkg.Class`, or
`Pkg.Class#method`) **or** packages, comma-separated, and the builder picks `class=` or `package=` by the
final segment's case. Mixing the two is refused rather than guessed, and a Gradle glob would be accepted
silently and then run the whole instrumented suite on the device.

### Database upgrade proof - S2306

The Room migrations are the one thing that can destroy data a user already has, and Room only compares a
migration against the exported schema on the device, during the first launch after an update. `.\a.ps1 fa`
compiles the instrumented set; it does not run it. **`.\a.ps1 fam` runs it** - every test in
`com.sza.fastmediasorter.data.local.db`, per-hop plus the whole-chain test - and is the only place that
comparison happens before a user's phone performs it.

```powershell
.\a.ps1 fam -DeviceId emulator-5554   # needs a connected device or emulator; long, background it
```

**Name the device (S2363).** AGP's connected task carries no device argument and installs on every
device `adb devices` reports, so with the owner's phone plugged in beside the emulator it went to the
phone too - on 2026-09-02 it tried to install the app there and to uninstall the test APK, and the whole
run failed on the phone's refusal. `fam` and `fwm` now take `-DeviceId <serial>` (defaulting to
`ANDROID_SERIAL`), pin the run to it, and print `Device: <serial>` in the banner. With several devices
attached and none named, the target **refuses and lists them** rather than fanning out - the target
device is resolved through `scripts/devtest/device-ready.ps1`, which is also what knows about a device
a sibling session leased. One attached device is still used without being named.

Static half, in every closure that touches `data/local/db`, an exported schema or `DatabaseModule.kt`:
`assert-migration-schema-conformance.ps1` (the SQL against the schema JSON) and
`assert-migration-test-pairing.ps1` (a migration with no test). They judge text and do not replace the
run. Release half: `/spec-prerelease` step 1.4, gating.


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
- The declared screen list `scripts/devtest/wear-prerelease-screens.json` is gated by `scripts/quality/assert-wear-walk-contract.ps1` with the ratchet baseline `scripts/quality/wear-walk-contract-baseline.txt`. It binds every entry to the screen it opens and the string resource it expects, and refuses a `*Screen` that is neither walked nor excluded with a reason. It runs per ticket and not on the sweep, because the subject is a wear screen and a rename has to fail in the ticket that made it (S2547). S2621 made "per ticket" literal: `scripts/post-change.ps1` runs it whenever the changed set carries a `wear/**/*Screen.kt` or the list itself, scoped to that set with `-ChangedFiles` so a neighbour's unclassified screen cannot refuse your closure. The whole-tree run stays in `.\a.ps1 fg` and in the release scope, where nothing is scoped away.
- The walk refuses to report screens it could not have seen: it requires `mWakefulness=Awake`, manages ambient mode for the duration and restores it, and returns 2 rather than a list of failures when the watch will not wake.
- **Every scroll stops at the end of the list, and `-MaxScrolls` is a safety cap rather than the budget (S2767).** The walk reaches for a control where it stands, and only when that misses does it settle the list to the top and hunt downwards, reading the UI tree after each swipe and stopping the moment two consecutive reads agree. The blind fixed-count version was not merely imprecise: measured on `emulator-5556` 2026-09-09, four back-to-back overscroll swipes on an already-at-top list OPEN the row under the finger - on Home that is the last-used shortcut, so the walk left for the audio player, started playback, and judged every later entry against the player while the app-in-front guard saw the same package throughout. Four screens were reported unreachable for that reason and none of them was a product defect. Raising the count made it worse, which is why the cap is documented as a backstop: the measured depths are Home 6 swipes, Apps 5, Settings 4, and Home has no fixed length at all - it draws one row per last-used resource.
- **`unreachable` is its own outcome, apart from `failed` (S2767).** `failed` now means one thing only: the screen opened and its expected token was not on it - a product defect. A screen whose control was never found is `unreachable`, counted and printed separately by both the walk and `prerelease-verdict.ps1`, and it blocks the PASS exactly as `failed` does, because a screen nobody opened satisfies no Play requirement. Both used to print `failed (tap)`, which read as a regression on fifteen screens and cost two rebuilds before it turned out to be the walk's own scrolling.
- **The walk tracks where it is standing, and recovers when it stops knowing (S2779).** The position is a stack of the labels tapped to reach the current screen, pushed only by an entry that actually OPENED and popped by that entry's `backAfter` presses - the model is `scripts/devtest/lib/wear-walk-position.ps1`, pure and driven by the suite without a watch. On `-RehomeAfterUnreachable` consecutive `unreachable` entries (default 2) the walk relaunches the app and taps back down that stack, and the existing app-in-front guard now shares the same recovery instead of stopping at the relaunch: a bare relaunch lands on Home, from which every settings page and every Apps page is unreachable by construction, so it converted one lost position into a cascade of its own. Why it is keyed on a run of failures: on the 2026-09-09 watch run eleven consecutive entries after `apps-water-flashlight` were recorded unreachable and spent 23 of the run's 35 minutes hunting for controls that were never on screen, which is one swallowed BACK rather than eleven absent screens - and the app never left the foreground, so the guard could not see it. A re-home is reported (`counts.rehomes`, and per row `rehomed` / `rehomeReason` / `rehomeRestored`) and never scored: the sweep handling its own navigation is not the app failing. It does sharpen the report - an `unreachable` reached for from a position restored in full says the control is not where the screen list places it, which is a screen-list edit, while one from a position that could not be restored leaves both explanations open.
- **A re-home force-stops before it launches, and that is the part that makes it a re-home (S2779).** `adb.ps1 launch` is `am start -n <pkg>/<activity>`, which RESUMES a live task at whatever screen it was left on - so relaunching an app stuck inside the Calculator returns to the Calculator, and the recovery recovers nothing. Measured on `emulator-5554` 2026-09-09: with the launch alone the replay restored 0 of 2 levels twice in a row, and with the force-stop in front of it 2 of 2. The app-in-front guard had carried this since S1984, where it is least visible: an app that left the foreground usually still has its task, so the guard's relaunch was returning to the screen the walk had wandered off to rather than to Home. The replay also reaches each level through the same hunt an entry uses, because a bare tap sees only what is rendered and `Apps` is the fourth row of Home on a 384x384 round face.

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
# THE ONLY WRITER of ic_splash_app_brand.xml
pwsh -NoProfile -File scripts/utils/generate-splash-brand.ps1 -Module app_v2

# THE SAME COMPARISON AS A GATE (fails on a hand-edited or stale variant; in .\a.ps1 fg)
pwsh -NoProfile -File scripts/quality/assert-splash-brand-sync.ps1
```

- **The drawable is generated, never authored.** The system splash window cannot render a string, so the wordmark and the slogan exist only as contours baked in from `splash_slogan` and one template. A hand edit therefore compiles, renders, and diverges silently from every other locale.
- **`splash_slogan` is consumed at authoring time, not at run time.** Nothing under `app_v2/src` references it and nothing can, which is why it sits in the unreferenced-strings baseline with that reason rather than being deleted as dead.
- **The generator is phone-only since S2593, and `-Module wear` is rejected outright.** The watch used to take an arrows-only composition of the same drawable - measured on a Galaxy Watch 7 the phone's wordmark rendered 10 px tall and its slogan 12 px, roughly 5-7 dp against Wear OS's 12 sp floor, so both were dropped. What retired the branch was not legibility but S2274: removing `windowSplashScreenAnimatedIcon` from the wear theme lets the platform draw the launcher icon itself, which is what Play requirement WO-V15 asks for, and that left the generated watch glyph with no consumer at all. Restoring the branch means re-opening that rejection, so the module, its `--arrows-only` mode and the watch drawable were deleted together rather than kept in reserve.

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
3. The pre-release sweep runs step `0.8`, which **is** the refusal. Each module keeps its own translator-ready file: `temp/S1627/app_v2/new_lexemes_en.txt` for the phone and `temp/S1627/wear/new_lexemes_en.txt` for the watch. Send each non-empty file to the external translation service, import the phone result with `locale-bulk-import.ps1` and the watch result with the same command plus `-Module wear`, then re-run the step until it is 0.

Six facts a reader cannot derive from the commands:

- **The refusal sits at the release, not at the ticket, by owner decision (strategic ADR-2).** Nothing ships between releases, so translating each key the day it is written buys the user nothing while costing ten translations per ticket; one batch per release costs one round trip for all of them.
- **A missing translation is an absent key, never an English copy (ADR-6, S1190), and since S3304 that is enforced rather than stated.** Android falls back to English on its own, so a partial locale is a shippable state - and an English copy is strictly worse than the absent key, because it renders the same words while reading as translated to every counting tool. Presence and freshness are still answered from the key set and the registry; the fourth count compares the localized value against the English source and reports equality as untranslated. It had to exist: a copied value satisfies all three of the older counts, so the gate was blind to it by construction. Measured on `physical_flashlight_title` - `values-ar`, `values-fr`, `values-hi` and `values-b+zh+Hans` all shipped `Camera flashlight` under the same stamp `4937533679a78582` the two genuinely translated locales carried, and the gate named none of them. The same enforcement sits at the writing end: `seed-locale-tranche.ps1` omits a map value equal to its English source and stamps nothing for it, which is what its own header had promised since S1190.
- **The English-identical count runs on twelve locales, not ten, and is exempted by a checked-in list rather than by a heuristic (S3304).** Lagging is a best-effort policy, so presence and freshness are asked only of the ten machine-translated locales; "this value IS the English source" is a defect in `ru` and `uk` too, and only the default locale is excluded, since its values are the source. Legitimate identicals - brand names, acronyms, units, pure format tokens - live in `scripts/quality/locale-identical-allowlist.json`, and an allow-listed key is exempt from all four counts, absence included. The list is checked in because the obvious heuristic does not discriminate: "identical to English and carrying a Latin word of three letters" scored 383 rows in `de` and 90 in `ru`, a locale the owner authors and keeps complete. It was seeded once, mechanically, from the keys whose ONLY gap was an English value - 633 in `app_v2` and 136 in `wear`, 747 after the union - so turning the count on changed no verdict on the day it landed; re-derive it with `list-new-lexemes.ps1 -IdenticalKeysPath <file>` pointed at a non-existent `-AllowlistPath`. It shrinks as `S3305` lands real translations, and a key that is also absent or stale somewhere is deliberately left out, so seeding can never retire a gap the other three counts already report.
- **An allow-list entry is scoped to locales, because the property is per (key, locale) and not per key (S3309).** An entry is a bare key name, which entitles every locale exactly as before, or an object naming the entitled ones - `{"key": "camera_mode_photo", "locales": ["fr", "it"]}`. The distinction is not decorative: measured on the live tree 2026-09-19, 517 of the 646 allow-listed units the corpus matches are SPLIT, some locales having translated them and others carrying the English verbatim, so a key-level entry is wrong for one of the two groups it covers - `camera_mode_photo` is legitimately "Photo" in French and an untranslated leftover in Arabic. `Test-LocaleIdenticalAllowlisted` therefore takes a **mandatory** `-Locale`, which is what stops a call site from restoring the old key-level answer by omitting an argument; the one question that is genuinely key-level, "does the list mention this key at all", is `Test-LocaleIdenticalAllowlistHasKey` and is asked only by the reviewer's dump. Narrowing an existing entry is not hand work: `review-locale-identical-allowlist.ps1` writes `allowlist-keep-scoped.txt` beside its TSV, each key already scoped to the locales that carry the English verbatim today, and `-Keep` reads that grammar back (`key`, `key: fr it`, `key: fr,it`). Until this landed there was no lever in the project that could say "fr is entitled to this one, ar is not", so every untranslated leftover sharing a key with a legitimate identical was permanently invisible to the release gate.
- **Provenance is tracked per module, and the gate runs once per module (S1858).** `scripts/quality/locale-source-fingerprints.json` addresses a unit as `module|set|file|key[|slot]`. It has to: `app_v2` and `wear` each ship `src/main/res/values/strings.xml` and share 14 key names, 6 of them with different English text, so an unqualified identity gave the two modules one slot with room for one hash. Whichever module imported last won it, and the gate then measured the other module's text against the wrong hash and called six translated keys untranslated - unfixable by re-importing, because re-importing only moved the red to the other module. A registry written before that split declares no schema version, reads as v1 and is refused with exit 2 until `scripts/quality/migrate-locale-fingerprints-module.ps1` rewrites it; a v1 store read as v2 would reproduce the same false report with nothing left to explain it.
- **Provenance is written by whoever writes the text, so a direct seed is self-sufficient (S2327).** `scripts/utils/seed-locale-tranche.ps1` stamps the registry for every unit it translated from the supplied map, and `locale-bulk-import.ps1` no longer does it after the fact. A run that writes a locale file and no fingerprint produces a key the producer still reports as untranslated, however complete the file is - measured on S2320, where adding 20 registry entries by hand removed the key from the report without touching one byte of locale text. The importer could not get this right from where it stood: the accept-or-reject decision is per key and it saw one exit code per source file, so it stamped keys the seeder had rejected - and under `-Merge` a rejected replacement leaves the previously shipped translation in place, which turned the stamp into fresh provenance for stale text. Nothing is stamped for a `-Merge` passthrough, a rejected key or a `-DryRun`: none of them produced new text.
- **Every write to the registry goes through `Edit-LocaleSourceFingerprints`, and the lock covers the read (S3008).** The store is one JSON document and every writer rewrites it whole, so a writer that loaded the file before another writer's save and saved after it discards every identity the other added. Nothing reports it: both processes exit 0 and each prints its own stamp count. Measured on the r37 import round - a ten-locale import of 146 lexemes reported `accepted 146 | rejected 0`, wrote every value correctly, and 145 of the 146 stamps for `de` were gone when the gate re-ran a moment later; the single survivor was the one key stamped from a different source file by a separate process. Locking the save alone would not have helped, because the stale snapshot forms at the read - which is why the transaction in `scripts/quality/lib/locale-fingerprints.ps1` opens a mutex keyed on the store path, reads inside it, hands the caller that fresh map to mutate, saves and releases. A seventh writer added later inherits the ordering by calling it; a writer that calls `Get-` and `Save-` in a pair does not, and there is no longer a reason to. `Code.Scripts` was the wrong instrument for this and was rejected: Rule 23's domain governs edits to script files, not runtime use of a data file, and `set-android-string.ps1` runs many times per ticket from sessions already holding `Code.Phone`. Contract suite: `scripts/quality.tests/locale-fingerprints-concurrency.Tests.ps1`, which spawns two overlapping writers and fails against a lock-free library.
- **`scripts/quality/locale-untranslated-baseline.txt` holds identities, not a count.** It froze the keys already untranslated on 2026-08-14 - all of them `S1626`'s placeholder-misread phrasings - so a pre-existing gap cannot be reported as new. A count would let a new key slip in behind an old one cleared in the same release. Its entries are module-qualified for the same reason the registry's are. Entries leave the file as `S1626` clears them, and the producer reports a cleared entry as stale; do not expect that soon, since `S1626` is `BlockExternal` - the rule that looked obvious (placeholder at a string edge) was measured over all 307 placeholder-bearing strings and does not discriminate, so the set clears through a probe in a future bulk round rather than through an edit anyone can make today.

### Play listing locales - S2340

```powershell
# THE PARITY GATE (0 clean, 1 violation, 2 cannot verify)
pwsh -NoProfile -File scripts/quality/assert-play-listing-locales.ps1

# IT ALSO RUNS FROM THE RELEASE-SCOPE BATCH, WHICH /spec-prerelease STEP 0.4 REACHES
pwsh -NoProfile -File scripts/quality/assert-release-scope-gates.ps1
```

**Scope class: release, not per ticket.** Its subject is the whole listing tree against the whole locale declaration, so it must not be wired into `post-change.ps1` or `.\a.ps1 fg` - there it would redden whichever session closed next over debt that session neither created nor can repair (Rule 33; the class S1939 measured at 68 of 191 red lines).

The same thirteen-locale set as the section above, judged for a different surface: `assert-new-lexemes-translated.ps1` asks whether `strings.xml` reaches every declared locale, this one asks whether the Play listing does. Wear App Quality Guidelines WO-G2 requires the listing to be "localized in languages offered by the app", and non-compliance is grounds for rejecting a submission.

Four facts a reader cannot derive from the commands:

- **The language set is declared twice, and this gate is the only thing comparing the copies.** `app_v2/src/main/res/xml/locales_config.xml` is the authority (S1190: a language is added there and nowhere else); the second copy is the `LOCALES` dict in `scripts/release/publish-play-listing.py`. Nothing compared them, so the first grew to thirteen while the second sat at three, and the drift was found by reading Google's guideline rather than by any check here.
- **A missing dict row fails silently, which is why the gate is needed at all.** The publisher iterates its dict, never the directory listing, so a locale folder without a row is skipped without a message. The only previous observer was publication itself - and `publish-play-listing.ps1 -Mode commit` is owner-gated and rare, so the gap could widen indefinitely between two runs.
- **The app-locale-to-Play-code table inside the gate is data, not a derivation.** Play's listing languages are a fixed list, not free-form BCP-47: `uk` takes no region, `de-DE` and `hi-IN` require one, `ar` and `ur` forbid one, and Chinese has no script-only code, so the app's `zh-Hans` maps onto `zh-CN`. A derived mapping is wrong for five of the thirteen. Adding a language to the app therefore means adding a row to the gate as well, and it refuses until the listing follows.
- **Three failure kinds, because they call for different repairs.** `PARITY` - a declared locale no folder serves, or a published folder no locale maps to. `COMPLETE` - a folder in the dict missing one of the three text files; the publisher exits 1 for *every* locale on this, not just the incomplete one. `LIMIT` - a text over 30 / 80 / 4000, counted in code points on the trimmed string, which is what Python's `len()` reports on the value Play receives.

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

## THE LINT BASELINES (S3155)

Two files, one per Android module: `app_v2/lint-baseline.xml` and `wear/lint-baseline.xml`. Each records findings the project has **accepted**, so lint can keep failing the build on anything new. Both modules run `abortOnError = true`.

**Lint runs locally through `.\a.ps1 fl` (app_v2) and `.\a.ps1 flw` (wear)**, both wrapping `scripts/builders/check-lint.ps1`. Before S3155 no target invoked lint at all - `fk`, `fkn`, `fc`, `fr`, `fg` and `fu` every one exit 0 without a single lint task - so CI was the only place the check ran and 479 app_v2 errors plus 116 wear errors accumulated unseen. Both targets run long; background them.

**What is in a baseline and why:**

- `NetworkDataSourceDispatcher` - 419 rows in app_v2, 30 in wear. Recorded, not fixed: the detector resolves callers only inside one file and only through a private method, so it cannot see a dispatcher switch made by a caller elsewhere, and the count measures that blind spot. **Carrier: S3156.**
- `UiContextLeak` - six rows in app_v2, on three `@Singleton` classes holding `View` fields. A real architectural finding, not an artifact; the fields are cleared on teardown, and deciding between rescoping, `WeakReference` or the status quo is a DI and lifecycle change. **Carrier: S3157.**

Nothing else is in either baseline by choice. Every other error class S3155 met was fixed in code or carries an in-source `@SuppressLint` with a written reason, which is the rule: a suppression states what makes the call safe, at the call, where the next reader will find it. A baseline row says only "accepted", so it needs a carrier ticket to mean anything.

**Regenerating.** `check-lint.ps1 -Module <app_v2|wear> -Regenerate` locally, or the `regenerate-lint-baseline` dispatch of `.github/workflows/android-ci.yml` for both modules at once - that job is the only environment running the same lint version and dependency mode CI judges with. **Regeneration is always the last step, never the first:** it records whatever is currently failing as accepted, so running it before the real defects are fixed is exactly how a genuine bug becomes an invisible baseline row.

**A declared baseline that does not exist gets created.** Lint writes it and then fails the build with `Aborting build since new baseline file was created` - a blanket regeneration arrived at by configuration rather than by choice, and indistinguishable from a deliberate one afterwards. It happened once here, on the first wear run, writing all 199 findings. `android.experimental.lint.missingBaselineIsEmptyBaseline=true` in `gradle.properties` closes that door: a missing baseline now means an empty one, lint reports everything and writes nothing. The two regeneration paths above each override the flag for their own invocation, because they are the callers that do want the file.

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

## THE PROCESS HARNESS COMES FROM THE CANON (S2402)

The ticket journal and its closing gates, the domain locks, ticket leases, agent identity, the agent
chat, the change journal, the document registry, the batch queue runner, the capability ledger and
the command-alias generator are no longer authored here. They ship with the `sza` canon plugin as
`tools/harness/`, and this repository consumes them - the same move Rule 24 records for the guard
hooks, with a bigger subject.

**Nothing you type changes.** Every path those scripts had is still there and still works:
`scripts/spec_catalog/select.ps1`, `scripts/utils/enter-code-lock.ps1`, `scripts/add_to_dev_log.ps1`
and the other 74 are **generated forwarders** (77 in `scripts/utils/sza-forwarders.manifest.txt`),
each a block of resolution plus a call - 30 to 113 lines, most of them near the upper end since S2452
made the third candidate resolve lazily and in a child scope. The
forwarder is what resolves the shipped copy, because the plugin cache path carries both the home
directory and the plugin version - `~/.claude/plugins/cache/sza-unified-rules/sza/<version>/tools/harness`
- so no call site could name it and stay correct across an update. Resolution order, first hit wins:
`SZA_HARNESS_ROOT`, the plugin cache's newest version, then `SZA_CANON_ROOT`. A forwarder that
resolves none of the three exits **2** naming all three and printing
`claude plugin update sza@sza-unified-rules`.

**The resolution is portable, and a runner that has no harness gets the refusal rather than a
crash (S3075).** The home directory is read as `$env:USERPROFILE` or `$env:HOME`, whichever the OS
supplies, and every path segment in the generated block is written with forward slashes. Before that
the block said `Join-Path $env:USERPROFILE '.claude\plugins\..'`, which on a Linux GitHub runner was
handed `$null` and threw under the forwarder's own `ErrorActionPreference = 'Stop'` - so the file
died three lines in, and nine gates of the "Static Gates" job reported
`Cannot bind argument to parameter 'Path'` instead of "the harness is not installed". The backslash
was the same defect one level down: on Unix it is an ordinary filename character, so the probe could
never match. Case 5 of `scripts/utils/install-sza-forwarders.tests/` pins the runner's shape - no
`USERPROFILE`, no `HOME`, no harness - and requires exit 2 with the refusal printed. The CI job
installs the harness from the public canon repository into `SZA_HARNESS_ROOT`, so those gates run
there rather than merely refusing politely.

**The third candidate's machine default is written in exactly one place** - `Get-CanonRoot` in
`scripts/utils/project-paths.ps1`, currently `P:\WEB\sza-unified-rules`. Move the canon checkout and
that function is the only edit; a forwarder asks for the value rather than knowing it, and only when
candidates 1-2 both miss, which on this machine they never do. It used to be written in 76 places,
74 of them files marked `GENERATED - do not edit`, which is what S2452 measured and removed.

**What this repository configures lives in `.sza-profile.json` at the root, and nowhere else.** The
roots under `PLAN/` and `temp/`, the `Sxxxx` grammar, the thirteen statuses, the lock domains
with their path rules, the `Timber.d("Sxxxx: ..")` probe shape, the queue runner's model policy, the
status -> command map, the ledger's `flavors` dimension and the site's locales are all fields there.
Editing a harness script body is the wrong move twice over: it is overwritten by the next plugin
update, and it never reaches the other projects.

- **`hooks.postClose` is the repository's slot inside a canon script.** The profile declares a list of
  scripts plus arguments (`{Module}` stands for the module being closed) and `close-and-log.ps1` runs
  each one after the status flip, the dev-log rows and the capability record - so a check placed here
  judges a ticket's closure in the closing call itself, in every runtime, without touching the canon
  body. It carries the class-catalogue scan and render, and since S2927 also
  `scripts/quality/assert-allfeatures-sync.ps1 -Gate`: the ledger is written by a script, so the
  session that writes a record never names `docs/ALL_FEATURES.jsonl` in its changed set and
  `all-features-gate` in `post-change.ps1` never judged it - the FAIL surfaced one or more sessions
  later, against whoever next touched the file by hand. A hook exiting non-zero becomes a `FAILED`
  step in the close report, which is detection in the same call rather than a refusal before the
  write; refusing at the keystroke would mean editing `all_features/add.ps1`, whose body is the
  canon's. A hook here judges the whole artifact, not the row just written, so it may only carry a
  check the tree already passes.
- **Regenerate the forwarders** after a plugin update that adds or renames a harness script:
  `pwsh -NoProfile -File scripts/utils/install-sza-forwarders.ps1`. The set it writes is
  `scripts/utils/sza-forwarders.manifest.txt` (local path | harness path, one per line); `-Restore`
  puts the pre-forwarder copies back from `temp/sza-forwarders-backup/`, and `-WhatIf` lists without
  writing.
- **A forwarder serves two shapes**, because half this set is dot-sourced as a library and half is
  invoked as a CLI: it branches on `$MyInvocation.InvocationName -eq '.'`. It also maps a child that
  THROWS to exit 1, since such a child never reaches its own `exit` and would otherwise report
  success.
- **The `*.tests` suites stayed here** and are the consumer's proof: they exercise the shipped layer
  through this repository's paths, statuses and fixtures. `pwsh -NoProfile -File
  scripts/quality/run-script-suites.ps1 -ChangedFiles "<the changed set>"`. A suite that asserts on
  its subject's SOURCE TEXT must read the shipped file, not the forwarder - `agent-lock.tests` shows
  the shape.
- **The layer's own gate** is `assert-portable.ps1` (External: it ships with the canon plugin under
  `tools/harness/`, it is not a script of this repository): it refuses a harness script whose code
  lines name a product path, an environment prefix, a log call or a build-system marker.

## THE QUEUE RUNNER REMEMBERS IDLE RUNS (S2695)

`run-spec-queue.ps1` used to forget a fruitless ticket the moment its process ended. A ticket handed back with the status it started with is dropped for the rest of that run, but the list holding it lives in the loop's memory, so the next start and every parallel instance ranked it again and offered it again. Measured over 2026-08-28..09-07: 121 of 485 runs moved no status (18%, 34.9 h), 27 tickets were run twice or more in a row with no result, one of them eight times. The skip cache could not carry the memory either - the runner wipes it at each start by design, so it was empty in the same window.

**The counter is derived, never stored.** `tools/harness/batch/_idle-runs.ps1` (External: it ships with the canon plugin under `tools/harness/`, it is not a script of this repository) reads every `runs-*.jsonl` under `temp/spec-queue/`, orders a ticket's rows by `finishedAt` and walks back from the newest until the first row that moved the status. That is the idle series. Two properties come free from the shape rather than from code: it spans instances, because all journals are read, and it resets on the first status move, because a moving row ends the walk. Nothing has to be cleared, and no mutator has to learn about it.

**What the threshold does.** At `runner.idleRunThreshold` (2 here) the ticket is passed over by `spec-next-preflight.ps1` with `auto_skip: idle-hold`, so the runner takes the next line of its package, and `[idle N, <outcome>]` appears on the ticket's row in `PLAN/RELEASE_QUEUE.md` next to the `[taken ..]` marker - the same render-on-every-write contract, so it can never disagree with the journals. `runner.idleOutcomes` names which journal outcomes count; an outcome outside that list ends the series rather than being stepped over, since the count claims consecutive idle runs. Both keys live in `.sza-profile.json`, so retuning the policy needs no canon session - and S2871 did retune it. The list is now `ok` and `timeout` alone: those two mean a run reached the ticket and handed it back where it was, while `claim-lost`, `claim-lost-before-launch`, `no-progress-or-claim-lost`, `launch-failed` and `child-failed` all describe a failure of the runner. A foreign lease says the ticket is being worked right now, and a child that never launched says nothing about it at all, so neither is evidence of idleness; holding on them also saved nothing, since both branches are the cheap ones that spend no child. Measured 2026-09-08 before the change: 39 tickets were held, and one six-minute window of children exiting 1 in zero minutes accounted for nine of them, S2757 among them held purely because a sibling owned its lease. Held tickets are reviewed by `/spec-sweep` Phase C.

**`child-failed` is the last of those names, and it exists because the exit code used to be journalled and never read (S2873).** `$outcome` started at `'ok'` and only three branches rewrote it - `timeout`, `claim-lost`, `launch-failed` - so an ordinary child that started and failed matched none of them and fell through to the elapsed-time guess, arriving in the journal as an idle ticket. Measured over 985 rows on 2026-09-10, 68 of them (6.9%) named a failed child something else, and 10 of those kept `'ok'` outright - which IS an idle outcome here, so S2871's narrowing did not reach them and a run the runner had failed still pushed its ticket towards being passed over. S1565 was held as `[idle 2, ok]` that morning on the strength of one such row. `tools/harness/batch/run-spec-queue.ps1` now decides both the outcome and `moved` in one function, `Resolve-RunOutcome`: an established verdict is never overwritten, a non-zero exit is `child-failed`, and an empty `statusAfter` reports no move at all - two rows recorded `Draft -> "", moved: true` for children killed mid-run, and `moved` outranks the outcome in every consumer. It needs no profile change, because the new name is outside `idleOutcomes` by construction. Covered by `scripts/utils/run-spec-queue.tests/Run-Tests.ps1`. **A canon deploy is owed before it reaches the journals** - the fix lives in the checkout, and the plugin cache the forwarder resolves still carries the old classification, so the suite SKIPs those cases until then.

**A held ticket is never unreachable.** The hold applies to automatic ranking only. `/spec-all Sxxxx`, `run-spec-queue.ps1 -Ids Sxxxx` and the owner picking a row by hand all bypass the ranker and run it.

**The child timeout follows the plan, not the clock.** `-TimeoutMinutes` used to run from the child's start whatever it was doing, and killed 14 children between minute 60 and minute 90 in that same window. The wait is now sliced: each time the ticket's tactical folder carries one more `**Status:** `[x]`` step than the last reading, the deadline moves to a full `-TimeoutMinutes` from that moment. The step mark is the only signal, deliberately - the agent chat is written by the lock, lease and status scripts as a side effect, so a spinning child would renew itself on it for ever, and a dev-log row arrives once at the end when there is nothing left to extend. A ticket with no tactical folder gives no signal, waits in one slice and dies exactly when it did before; an extension can only add time, so the change has no worse case than the old behaviour. The kill line names whether the deadline was ever extended and how many steps the child produced.

Suite: `scripts/utils/run-spec-queue.tests/Run-Tests.ps1`, over synthetic journals in a throwaway project root. It SKIPs by name while the resolved harness predates the change - that is the undeployed state, not a defect.

## PORTABLE PATHS (S2326)

`scripts/utils/project-paths.ps1` is the one place a repository script learns where anything is. Dot-source it (`. "$PSScriptRoot\..\utils\project-paths.ps1"`) and ask by role; do not write a path literal. Moving the tree to another drive letter or another directory name must require editing no script - the working case is a RAM disk, where `P:\ANDROID\FastMediaSorter_mob_v2` becomes `M:\FastMediaSorter_mob_v2`.

**The root is found by a marker, not by counting `..`.** The walk goes upward until one directory carries `settings.gradle.kts`, `a.ps1` and `CLAUDE.md` **together**. All three are required: `settings.gradle.kts` alone also sits in the release worktree next door, and `a.ps1` alone would match a copied launcher. A `..` count is a property of where a file happens to sit, so it stops being true the moment the file moves between subdirectories - the marker is a property of the tree and survives both moves. The walk starts from the module's own directory, so every caller gets the same answer regardless of its depth.

**Four roles, five functions.**

- `Get-ProjectRoot` - the tree's own root.
- `Get-ProjectPath -Relative 'DOWNLOADS/x.apk'` - a location inside the tree, either separator style.
- `Get-SiblingPath -Name 'FastMediaSorter_release'` - a directory beside the root. Only the release worktree needs this; `FastMediaSorter_credentials` is not read by any script, because signing resolves on the Gradle side relative to the root.
- `Get-ToolPath -Tool Adb` - an external tool. Resolution order: the override variable, then `PATH`, then the known install locations, then a refusal naming the tool.
- `Get-ArtifactSink -Kind Drive` - a delivery destination, or `$null` when it is not reachable here.

**A missing tool fails by name; a missing sink warns and skips.** Without `adb` an install is impossible, so `Get-ToolPath` throws and names both the tool and the variable that would fix it. Without the Google Drive directory a build is still a build, so `Get-ArtifactSink` returns `$null`, writes a warning, and the caller skips its copy - delivery must never become a build blocker. `Get-ToolPath` also prints the path it picked, because a second SDK's `adb` is otherwise invisible until an install lands on the wrong device.

**Override variables.** Every one of them beats discovery.

| Variable | Overrides |
| --- | --- |
| `FMS_ADB` | `adb` |
| `FMS_SEVENZIP` | `7z` |
| `FMS_PWSH` | `pwsh` |
| `FMS_NODE` | `node` |
| `FMS_NPM` | `npm` |
| `FMS_MAESTRO` | `maestro` |
| `FMS_FFMPEG` | `ffmpeg` |
| `FMS_SINK_DRIVE` | artifact sink (Google Drive work directory) |
| `FMS_SINK_COMMANDER` | artifact sink (Total Commander drop) |
| `FMS_SINK_APK` | artifact sink (APK archive) |
| `FMS_SINK_DEOBFUSCATION` | artifact sink (mapping retention) |
| `FMS_SINK_REMOTE_LOGS` | artifact sink (remote-log intake) |
| `FMS_PROJECT_MOUNT` | the repository's Linux mount path, for `scripts/builders/build-ffmpeg-dts.sh` when it is run by hand instead of through its PowerShell launcher |

**Adding a tool or a sink is one row**, in `$script:FmsToolTable` or `$script:FmsSinkTable` - never an edit at a call site.

**The sink table is the only place in the repository allowed to name a machine path literally**, which is why the gate below excludes that file by name: a default that lives nowhere would silently stop delivering artifacts on the machine that has those directories.

**The gate: `hardcoded-drive-path`**, a rule in `scripts/quality/lib/source-matchers.ps1`, run by `assert-source-gates.ps1` from both `post-change.ps1` and `.\a.ps1 fg`, baseline `scripts/quality/hardcoded-drive-path-baseline.txt` seeded at **0**. It judges `.ps1`, `.psm1`, `.cmd`, `.bat` and `.sh` under `scripts/`, `maestro/`, `dev/` and `a.ps1`, and refuses a new literal drive path. It deliberately does not fire on a URL scheme, a `$env:`-derived path, a whole-line comment (a comment binds nothing, and `clean-user-temp.ps1` names `C:\Windows\Temp` precisely as a directory it refuses to touch), or a regex character class such as `[\s:\-|]`, which puts a letter, a colon and a separator side by side and read as a drive in the rule's first draft.

## DELIVERING A BUILT ARTIFACT (S1707, S2332)

**One script delivers, and a builder calls it: `scripts/utils/publish-artifact.ps1`.** It puts the artifact raw into the Google Drive share, adds a password ZIP for recipients whose mail or security policy refuses a bare `.apk`, and copies it into the Total Commander staging folder. It delegates the Drive half to `scripts/utils/copy-to-drive.ps1`, which stays scoped to that one sink because two callers already read it that way.

- `-Path` takes **several artifacts**, which land as several raw copies and go into **one** archive. That is what `build-aab-release.ps1` needs: the AAB and its APK travel as a unit.
- `-Name` renames a single artifact; with a set it names the **archive** only, because renaming one member would silently decide which of them is the real artifact.
- `-CommanderPath` picks which artifact reaches the Commander folder. It defaults to the first, and a release names the APK explicitly - that folder is a sideload staging area and nobody sideloads an AAB.
- `-NoZip` and `-NoCommander` are for a path that legitimately delivers less. Five builders mirror to Drive alone and pass `-NoCommander`; `build-with-version.ps1` has never produced the archive and passes `-NoZip`.
- **It never fails a build.** An unreachable sink or a missing 7-Zip is reported and skipped: the artifact is already built, so its courtesy copy cannot throw it away.
- Invoke it with the **call operator**, never `pwsh -File` - `-File` binds a comma-separated value as one string and never produces an array, so a two-artifact delivery would look like one missing file.
- Regression suite: `scripts/utils/publish-artifact.tests/Run-Tests.ps1`, hermetic through `-DriveDir` / `-CommanderDir`.

**The gate: `inline-delivery-block`**, a rule in the same `source-matchers.ps1`, run by `assert-source-gates.ps1` from both `post-change.ps1` and `.\a.ps1 fg`, baseline `scripts/quality/inline-delivery-block-baseline.txt` seeded at **0**. Under `scripts/builders/`, `scripts/release/` and `dev/` it refuses `Get-ArtifactSink -Kind Drive`, `-Kind Commander` and `Get-ToolPath -Tool SevenZip` - the three calls a build path stops needing once it delegates. Other sinks are untouched, so `build-with-version.ps1` keeps resolving `Kind Apk` for its distribution folder. `scripts/utils/` sits outside the filter rather than in an exclusion list, because that is where the implementation lives and naming the two files by hand would let a third hand-written copy appear beside them unjudged.

**Why `dev/` is in that list (S2337).** The builder the owner actually runs is `dev/build-with-version.ps1`, launched by `dev/build-with-version.bat` - not the same-named file under `scripts/builders/`, which is the orphan S2331 deletes. While the scope was the two `scripts/` directories alone, the **0** baseline meant "zero among the files walked", not "zero in the tree": the live builder kept its hand-written block through S2332's entire conversion and was never counted. The directory is named rather than the one file, for the same reason `scripts/utils/` is a filter and not an exclusion list. `dev/archive/` is excluded as a read-only zone, matching `hardcoded-drive-path`, which already walks `dev/`.

**Why the gate exists rather than a convention.** S1707 extracted this block into `copy-to-drive.ps1` for exactly this reason, and then nothing was converted: measured 2026-09-02, the block was still hand-written in 26 places across 25 builders while the shared script had two callers. The cost is in S1707's own record - the watch shipped in release 2.60.8232.251 while its Drive copy stayed at the 15 August build, looking current and being a month stale.

## SCRIPT HYGIENE (S1872)

Three checks keep the repository's ~370 PowerShell scripts findable, described and alive. All three are ratcheted: their ceilings may fall, never rise, so existing debt never blocks an unrelated ticket while a new script must be correct on the day it is written.

**`scripts/quality/assert-script-references.ps1`** - a script nothing references is either deleted or declares itself a hand-run tool.

- Judges **live wiring only**. A mention in an archived spec, a `dev/CHANGELOG.md` row or a read-only zone remembers a script; it does not call one. The repository holds over 6000 such documents, enough to make every dead script look wired - with them in the corpus the check reported 0 orphans out of 340 and could not fail.
- Judges **a path, not a file name** (S2124). Until 2026-08-27 the key was the bare file name, so the 37 files called `Run-Tests.ps1` shared one entry and three comments naming that word vouched for all 37 - none of which is called from anywhere. Any group of files sharing a name went unjudged the moment one member was mentioned. Re-keying raised the verdict from 30 to 58; the 28 added files are Pester runners with no launcher, owned by S2122.
- A token is resolved into the file it names by the ladder in `scripts/quality/lib/script-reference-resolution.ps1`: a `$PSScriptRoot`-anchored path, a bare name matching a sibling, the longest resolving path suffix, a unique bare name - and then a bare name several scripts carry, which is **evidence about none of them**. The first four rules are the price of the path key: without them the re-keying reported three scripts that run every day as dead.
- The ladder **matches against every script in the tree and answers only with the judged ones** (S2336). Until 2026-09-02 it matched against the judged roots alone, so a token naming a real file outside them - `maestro/`, `.claude/hooks/`, the version-stamping builder under `dev/` - matched nothing, shortened to its bare leaf, and credited whichever homonym was inside the index. Seven files addressed the builder in `dev/` and between them kept an unrelated copy under `scripts/builders/` alive; the ambiguity counter read 0 throughout, because it counted carriers in the index and the second carrier was outside it. A rule that now matches a real file **stops** instead of shortening past it, even when the answer narrows to nothing, and ambiguity is counted tree-wide. Surfaced exactly one script, which S2331 owns.
- The tree walk feeding that match **drops nested worktree copies, and the name checks below keep them**. A worktree is a second copy of the repository, so it carries a second file for every name: measured 2026-09-02, 911 `.ps1` files walked against 490 after the exclusion. Fed to the resolver, those 421 duplicates would make every name in the tree ambiguous and report the whole repository as unreferenced.
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

### A discovered suite must also be known to git - S2411

Placement being the whole registration cuts the other way too: a suite is registered on the machine that placed it and nowhere else. Nothing in the closure path stages anything - neither `post-change.ps1` nor `close-and-log.ps1` runs `git add` - and `/git` assembles a commit by naming files inside groups built from the changed set, where a new untracked directory appears as the single folded line `?? dir/` and is lost. Once missed it stays missed, because `git commit -a` stages edits to **tracked** files only. Measured 2026-09-03: eight of 65 runners existed on the owner's machine alone. The damage is not an absent test but a lying one - in a fresh clone or the release worktree the runner discovers a smaller set and prints the same green verdict.

- **`scripts/quality/assert-suite-tracked.ps1`** asks git about every runner the discovery selected and refuses the ones the index does not know, printing the exact `git add` that clears each.
- **It asks about the index, not about `HEAD`.** "Present in the last commit" is unsatisfiable at the moment the check runs: the suite is written by the very ticket now closing and the owner commits afterwards. `git add` is the minimal irreversible step - after it the next commit carries the file by itself.
- **The list comes from `run-script-suites.ps1 -ListOnly -Json`, never from its own walk.** A second walk would judge a set different from the one that executes, which is the divergence S1621 forbids. The measurement that opened this ticket made that mistake in miniature: it searched for the literal `Run-Tests.ps1`, Windows matched `run-tests.ps1` case-insensitively and git did not, and two tracked suites were reported as missing.
- **Two call sites, split by what each can see.** `post-change.ps1` (gate `suite-tracked`) runs it only when the changed set itself carries a runner, so the refusal names the session that wrote it and can never fire over a sibling's work in flight. `assert-release-scope-gates.ps1` runs it over the whole tree, where the accumulated debt is visible but attributable to nobody.
- **Exit codes.** 0 every discovered runner is in the index (or `-Gate` was absent); 1 at least one is not, with `-Gate`; 2 git is absent, the target is not a work tree, or the runner produced no list. The last is deliberate - collapsing "could not look" into "found a defect" is the failure this whole section is about.
- **Staging is the author's, not the gate's.** The refusal prints the command and stops there: closing a ticket touches the git index nowhere else, and this is not the place to make it start.

### A dot-sourced script must also be known to git - S2616

S2411 above covers one shape of the same defect, and it is the milder one. A suite runner that never reached the index makes a check quietly observe less; a **dot-source target** that never reached the index stops its consumer from starting at all, because `. "$PSScriptRoot\gradle-worker-reaper.ps1"` is resolved when the consumer is **parsed**, not when a function in it is first called. Measured 2026-09-05 during S2588: `scripts/builders/gradle-worker-reaper.ps1` was dot-sourced by `check-standard-fast.ps1` and by its own contract suite, matched no `.gitignore` rule, and had simply never been `git add`ed - so in a fresh clone `fk`, `fkn`, `fc`, `fr`, `fu` and every `a.ps1` target above them died on a parse error. Its ticket S2585 had closed `Verified` the day before, because every check it ran read the working tree, which holds the file either way.

- **`scripts/quality/assert-dotsource-tracked.ps1`** parses every `.ps1` outside the frozen and generated trees, resolves each dot-source target it can know statically, and refuses the ones the index does not carry - naming each consumer and the line it sits on, then printing the `git add` that clears it.
- **The selection comes from the parser, not from a regular expression.** `.` is an operator no regex can tell from a decimal point, a member access, or a sentence in a comment. The gate asks the AST one question - is this node a `CommandAst` whose `InvocationOperator` is `Dot` - so a script merely *named* in a comment produces no finding. This is deliberately **not** `lib/script-reference-resolution.ps1`, which is permissive on purpose: for orphan detection (S2124) a script named in a comment genuinely is referenced, and that same permissiveness here would invent findings.
- **What it cannot resolve, it counts out loud.** Measured 2026-09-06: 509 dot-source sites across 596 scripts, of which 229 resolve onto 52 distinct targets. The other 280 address `$szaFwdTarget` - the canon forwarders (S2402), whose target is a plugin-cache path outside the repository and not this repository's to track. A path built from a runtime variable cannot be known without running the script, so the gate prints how many it did not judge instead of leaving a reader to infer coverage from a green line. A target that resolves but is absent from disk is printed too and changes no exit code: that is either a broken dot-source or a resolution the gate got wrong, and calling it "untracked" would be a finding manufactured out of its own uncertainty.
- **Two call sites, split by what each can see**, exactly as S2411. `post-change.ps1` (gate `dotsource-tracked`) fires when the changed set carries any `.ps1` and addresses those files directly rather than walking the tree - 639 ms against 8.1 s for the full sweep. `assert-release-scope-gates.ps1` runs the whole tree, where a target orphaned by a session that has since ended is visible and attributable to nobody.
- **The index question is shared with S2411**, in `scripts/quality/lib/git-index-membership.ps1`. What is shared is only the comparison between git's repository-relative output and a path off the disk - the step this repository has already got wrong once (S2411's own header records Windows matching a runner name case-insensitively where git did not). One copy of that normalization can be fixed; two diverge, which is what the S1621 rule forbids.
- **The work tree is validated before anything is judged.** A selection that narrows to zero targets never reaches the index query, so leaving the check to that query let an unusable work tree report a clean tree it had never looked at. Caught by case G of the gate's own contract suite on the day it was written, and fixed in both gates.
- **Exit codes.** 0 every judged target is in the index (or `-Gate` was absent); 1 at least one is not, with `-Gate`; 2 git is absent, the target is not a work tree, or the discovery root is unreadable.


## ABANDONED SCRIPT PROCESSES (S2610)

A repository script can be alive for hours having executed **nothing**. The symptom is a `pwsh`
process aged in hours whose processor time is a fraction of a second, and the cause is not the
script.

- **The mechanism needs two halves, and both come from the caller.** PowerShell answers a call that
  omits a `[Parameter(Mandatory)]` argument by PROMPTING for it, and the prompt reads stdin. An
  agent runtime that records tool time on DISPATCH hands its child a stdin pipe and never closes it,
  so the prompt never returns. Binding happens before the script's first statement, so nothing runs,
  nothing is written, and no lock is taken.
- **What it looks like.** Measured 2026-09-05: 15 pairs of `pwsh` processes - an outer
  `pwsh -Command` and the inner `-File` it spawned - alive 14-17 hours on 0.3-0.7 s of CPU each,
  every one missing one argument (`-State` on `plan-tick.ps1` twelve times, `-Description` on
  `post-change.ps1`, `-Name` on `lock-status.ps1`, a positional on `add_to_dev_log.ps1`). A ticket's
  Phase 05 ticks, its dev-log row and a seven-file closure with all its gates therefore silently did
  not happen, while the runtime that fired them recorded success.
- **A read-only script hangs identically to a writing one**, which is the fastest way to recognise
  it: `lock-status.ps1` takes no lock and writes nothing, and it hung beside the rest. If the block
  were mid-write, that script could not be in the list.
- **It is not an undrained stdout pipe.** That mechanism is real but its threshold is far above
  these scripts - reproduced at 1000 and 4000 bytes exiting normally against 64000 blocking, where
  `lock-status.ps1` emits 96 bytes. Recorded in `dev/REFUTED_APPROACHES.md` because it is the
  natural first guess and it cost a reproduction.
- **Find them:** `pwsh -NoProfile -File scripts/utils/reap-abandoned-script-processes.ps1`, which
  reports by default and ends the process trees under `-Kill`. Age and idleness alone select the
  wrong population - live queue-runner windows measured younger and cheaper than abandoned ones - so
  it spares anything carrying `-NoExit`, anything long-running by design, and anything supervising a
  live child that is not itself abandoned.
- **What now refuses instead of hanging.** The generated forwarders point host input at an ended
  reader when input is already redirected, so a harness script named with a missing argument exits
  naming it; `post-change.ps1` validates `-Target` and `-Description` in its body and exits 2. A
  script declaring its own parameters cannot be fixed this way - the binding precedes its first
  line - so the caller completing its call is still the real cure, which is why the rule sits in
  `AGENTS.md` section 9.1 where non-Claude runtimes read it.


## THE DEVICE BUILD IS A FULL REBUILD, AND ITS SILENCE IS NORMAL (S3290)

`scripts/builders/build-standard-device.ps1` always passes `--no-build-cache --rerun-tasks
-Pkotlin.incremental=false` (S3094), because the APK it produces is installed on a phone seconds
later and must not carry a Hilt component from one build paired with consumers from another. The
price is that nothing is ever up to date: the run measured 2026-09-18 reported `BUILD SUCCESSFUL in
4m 51s`, **49 actionable tasks, 49 executed**.

Most of that wall clock is silence, and the silence is not a symptom:

- Gradle's plain console prints `> Task :x` when a task **starts** and nothing more until it ends.
- `mergeExtDexStandardDebug`, `kspStandardDebugKotlin`, `compileStandardDebugKotlin` (1m 22s) and
  `dexBuilderStandardDebug` each run for minutes without printing a character.
- The Kotlin daemon's CPU total is not a progress signal. It climbs only on the two Kotlin tasks -
  measured 3418 -> 3554 s - and sits at exactly 3554 s through `hiltJavaCompile`,
  `transformClassesWithAsm` and `dexBuilder`. Reading that flat total as proof of death is what
  ended two healthy runs before the APK existed.

So the wait now reports itself. `scripts/builders/gradle-progress-watch.ps1` streams Gradle's output
live and, after 60 seconds with no new line, prints one heartbeat naming the running task, how long
it has been silent, the elapsed build time and the CPU-second delta of every JVM over 500 MB. A run
that passes the 45-minute ceiling is stopped and the script exits **124**, the code `timeout(1)`
uses. Rule 35 bounds what the ceiling may kill: the Gradle **client** this call started, never the
daemon, which is shared with every other session and cancels the build itself once the client
disconnects. Contract suite: `scripts/builders/gradle-progress-watch.tests/Run-Tests.ps1`.

The same shape as `fms.unitTestTimeoutMinutes` in `gradle.properties` (S2585) and for the same
reason - a wrapper that waits forever holds its build domain forever.

## BUILD TYPES

| Type | minify | shrink | debuggable | appId suffix | notes |
|:-----|:------:|:------:|:----------:|:------------:|:------|
| `debug`   | - | - | ✓ | `.debug` | Custom keystore via `debug.keystore.properties`; `LOG_NETWORK_THUMBNAILS=true`; dedicated Dropbox key |
| `staging` | - | - | ✓ | `.staging` | `initWith(release)` - release proguard, shrink disabled; `matchingFallbacks=["release"]` |
| `release` | ✓ | ✓ | - | - | `debugSymbolLevel=FULL`; keystore via `.secrets/keystore.properties` (root fallback supported) |

## BUILD VERSIONING (S1873)

The version a user reads on the device is the time that build was produced. Not a constant somebody
remembered to update - the actual minute.

**The two fields.**

- `versionName` - `Y.YM.MDDH.Hmm`, byte-identical for `app_v2` and `wear`. `2.60.9030.953` is
  2026-09-03 09:53. This is the string on the About screen and in a support log.
- `versionCode` - nine digits for both modules, `yyMMddHH` plus one separator digit that says which
  module it is: `app_v2` takes `floor(minute / 10)` and owns `0..5`, `wear` takes
  `6 + floor(minute / 15)` and owns `6..9`. The two MUST differ: both modules publish under one
  `applicationId` (S1681) and Play refuses a release that repeats a code. The minute is truncated
  because a full `yyMMddHHmm` is ten digits and overflows the 2100000000 ceiling - so two artifacts
  of one module built inside the same block (ten minutes for the phone, fifteen for the watch) share
  a code while their names still differ.

  The separator is a **partition, not an offset** (S2721): neither module has to know the other's
  code to stay clear of it, which is what lets the watch release on its own cadence
  (`/skill-release-wear`) where no phone stamp exists. Before S2721 `wear` carried the bare 8-digit
  `yyMMddHH` - ten times smaller than the phone's code under one `applicationId`, so a fresh watch
  build read as older than a phone build from months earlier, and its hour resolution allowed only
  one watch release per hour.

**Three sources, one order.** Resolved in `app_v2/build.gradle.kts` and `wear/build.gradle.kts`:

1. `-Pfms.versionCode` / `-Pfms.versionName` passed on the command line. Always wins. This is how a
   multi-module release keeps one timestamp across two gradle invocations seconds apart - the
   orchestrator resolves the stamp once and passes it to both.
2. The in-build stamp in `gradle/build-version-stamp.gradle.kts`, applied when **this invocation
   packages an artifact** and no property was passed. It covers the paths no wrapper script reaches:
   a raw `gradlew`, a CI job, the IDE's Run button.
3. The checked-in `defaultAppVersionCode` / `defaultAppVersionName`. Nothing writes these any more,
   so they are a deliberately non-releasable **sentinel**: an artifact carrying one is an artifact
   nobody stamped.

**Which invocations get a stamp.** Source 2 fires when a requested task name contains `assemble`,
`bundle`, `install`, `package`, `connected` or `baselineprofile`, and not `uninstall`. `connected`
and `baselineprofile` are in that list because those tasks install what they build on a real device.
Task options and their values are excluded first - `--tests "*ApkInstallFailureTest*"` is a pattern,
not a task, and matching it made a unit-test run pay a packaging build's cache invalidation.

Compile-only work - `fk`, `fkn`, `fc`, `fr`, `fw`, `fwr`, `fwu`, lint, unit tests - matches nothing
in that list, keeps the sentinel, and keeps its configuration-cache entry. That is deliberate: a
stamp changes `BuildConfig`, and `BuildConfig` constants are inlined at every use site, so a moving
version recompiles everything that reads it. Measured: an unstamped repeat is 3.3 s, a stamped
packaging build 111 s. The fast checks cannot pay that, and they package nothing.

**Why a `ValueSource` and not a clock read.** The configuration cache serialises a configuration-time
value into its entry and replays it. Measured 2026-09-03: a `System.currentTimeMillis()` read in the
build script returned the identical value seventy seconds later, with `Reusing configuration cache`
and no warning - the original defect, minus the symptom that made it findable. A `ValueSource` is
re-obtained before an entry is reused, and Gradle invalidates the entry itself when the value moves:
`cannot be reused because a build logic input of type 'BuildClockValueSource' has changed`. The raw
stamp is memoised on the root project, so one invocation building both modules reads the clock once
and cannot straddle a minute boundary between them.

**Reading a produced artifact's version.** AGP writes `output-metadata.json` beside every artifact,
and it cannot disagree with the file it describes:

```powershell
Get-Content app_v2\build\outputs\apk\standard\debug\output-metadata.json | ConvertFrom-Json |
    Select-Object -ExpandProperty elements | Select-Object versionName, versionCode
```

That file - never the build script - is what `-ReuseVersion`, `publish-github-release.ps1` and
`publish-play-release.py` read.

**The two gates, and what each judges.**

- `scripts/quality/assert-artifact-version-fresh.ps1` judges a **produced artifact**: it rejects a
  `versionName` whose encoded time is far from the file's write time, and rejects a `versionCode`
  equal to the module's sentinel. Three exit codes, because "found a defect" and "could not look"
  are different answers: **0** fresh, **1** stale, **2** could not verify. It is the only check in
  the repository that judges a result rather than an intention, so it also catches a packaging path
  that does not exist yet.
- `scripts/quality/assert-module-version-parity.ps1` judges the **two checked-in constants**: the
  names must match byte for byte, and both codes must be what `Get-BuildVersionStamp` derives from
  the instant that shared name encodes. It decodes the name rather than relating one code to the
  other, because since S2721 neither code is a function of the other. Nothing writes those constants
  now, so a violation is a hand edit and the fix is a hand edit.

**Never write a version into a build file.** Fifteen scripts used to rewrite the constants in place
with a regex; that is retired (ADR-4). A rewritten constant cannot be told apart from historical
residue, the mutation left the working tree dirty, and the release flow needed a step to revert it.
The formula lives in exactly two places that are kept byte-compatible - `Get-BuildVersionStamp` in
`scripts/utils/build-version-stamp.ps1` for scripts, and `gradle/build-version-stamp.gradle.kts`
inside the build.

## FEATURE FLAGS (BuildConfig)

[`docs/FLAVOR_MATRIX.md`](FLAVOR_MATRIX.md) is the canonical, generated answer to "which capability is available in which flavor" - rendered from the `productFlavors` block by `scripts/docs/generate-flavor-matrix.ps1`, together with the machine-readable `docs/flavors/flavor-matrix.json`. The two tables below are a working summary of it and are checked against it cell by cell by `scripts/quality/assert-flavor-matrix-docs.ps1` (in `.\a.ps1 fg` and in `post-change.ps1`), so an inverted marker fails instead of drifting. Change `app_v2/build.gradle.kts`, then regenerate; never fix a disagreement by editing the generated table.

That gate reads glyph table CELLS and, by its own manifest, never looks at prose - which is how the seventh flavor `foss` left thirteen documents and `a.ps1`'s help text still saying six, twice over after S1392. The sentences are covered by `scripts/quality/assert-flavor-count-prose.ps1` (in `.\a.ps1 fg`), which resolves two lexical claims against the same generated JSON: a numeral standing next to a flavor noun under an all-quantifier, and a list presented as the complete set - a `-Flavor A|B|C` value list, or a parenthesised name list right after an all-quantifier. A deliberate subset is not a finding, so naming four flavors that carry Streams stays legal. When a sentence only means "every flavor", drop the number rather than correcting it: text with no count cannot go stale.

### Core feature matrix

| Flavor           | VIDEO | AUDIO | IMAGES | CLOUD | NETWORK | DOCS | ANIM | STREAMS | VR  |
|:-----------------|:-----:|:-----:|:------:|:-----:|:-------:|:----:|:----:|:-------:|:---:|
| **standard**     | [+]   | [+]   | [+]    | [+]   | [+]     | [+]  | [+]  | [+]     | [-] |
| **lite**         | [+]   | [+]   | [+]    | [-]   | [-]     | [-]  | [-]  | [-]     | [-] |
| **photos**       | [-]   | [-]   | [+]    | [+]   | [+]     | [-]  | [+]  | [-]     | [-] |
| **legacy**       | [+]   | [+]   | [+]    | [+]   | [+]     | [+]  | [+]  | [+]     | [-] |
| **vr**           | [+]   | [+]   | [+]    | [+]   | [+]     | [+]  | [+]  | [+]     | [-] |
| **noLegal**      | [+]   | [+]   | [+]    | [+]   | [+]     | [+]  | [+]  | [+]     | [+] |
| **foss**         | [+]   | [+]   | [+]    | [-]   | [+]     | [+]  | [+]  | [-]     | [-] |

`NETWORK` = `SUPPORT_LOCAL_NETWORK` (SMB/SFTP/FTP), `STREAMS` = `SUPPORT_STREAMS`, `VR` = `SUPPORT_VR_PLAYER`. Those two network/streams columns are the pair that defines `lite` and were missing here until S1392; `lite` is the only flavor with neither.

### Extended per-flavor flags

| Flag | std | lite | photos | legacy | vr | noL | foss |
|:-----|:---:|:----:|:------:|:------:|:--:|:---:|:----:|
| `SUPPORT_MIC_RECORDING`            | [+] | [-] | [-] | [+] | [+] | [+] | [-] |
| `ENABLE_EPUB`                      | [+] | [-] | [-] | [+] | [+] | [+] | [+] |
| `ENABLE_TRANSLATION`               | [+] | [-] | [-] | [+] | [+] | [+] | [-] |
| `ENABLE_PERSISTENT_AUDIO_PLAYBACK` | [+] | [-] | [-] | [+] | [+] | [+] | [+] |
| `SUPPORTS_DEFAULT_PLAYER`          | [+] | [-] | [+] | [+] | [+] | [+] | [+] |
| `SUPPORT_WEAR_COMPANION`           | [+] | [-] | [-] | [-] | [-] | [+] | [-] |
| `SUPPORT_CAST`                     | [+] | [+] | [+] | [+] | [-] | [+] | [-] |
| `SUPPORT_VR_PLAYER`                | [-] | [-] | [-] | [-] | [-] | [+] | [-] |
| `VR_UI_COMPOSITION_LAYER_ENABLED`  | n/a | n/a | n/a | n/a | [-] | [+] | n/a |
| `IS_NO_LEGAL_FLAVOR`               | [-] | [-] | [-] | [-] | [-] | [+] | [-] |

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

Room schema version: 58 (`@Database(version = ..)` in `AppDatabase.kt` is the source of truth - read it rather than this line).
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
- **Who deliberately does not:** every release path. A release emits one unsliced APK per flavor carrying that flavor's own ABI set (settled by S2067), because the GitHub asset is what IzzyOnDroid globs (S0215) and a single-architecture one would shrink the device set the release reaches - canon hard invariant 2.
- **What it changes:** `splits.abi` turns on with `include("arm64-v8a", "x86_64")`, and every flavor's `ndk.abiFilters` is skipped. Both, not either: AGP refuses the two mechanisms together (`Conflicting configuration: '..' in ndk abiFilters cannot be present when splits abi filters are set`), and it checks **every** variant at configuration time, so one unconditional filter anywhere in `build.gradle.kts` breaks every split build.
- **vr is excluded by the builders, not by the DSL.** `build-debug.PS1` refuses the flag for a vr task, because an x86_64 vr APK would carry no OpenXR native - the loader AAR ships arm64 only.
- **Play is untouched.** `android.splits` is ignored when building a bundle, and `bundle.abi.enableSplit` already defaults to true, so the AAB was always per-ABI.
- **Finding the artifact afterwards:** `scripts/utils/find-build-artifact.ps1`. Every builder, installer and release consumer resolves through it - it selects by ABI from `output-metadata.json` and throws when the request is ambiguous, rather than taking `elements[0]` or the newest file, both of which pick an architecture at random once a build emits more than one output.
- **Choosing the slice:** the debug builders take `-Abi <name>`; omitted, they read `ro.product.cpu.abi` off the connected device.

### Prebuilt native AARs - the dependencies a clean checkout lacks (S1539, S2879)

`app_v2/build.gradle.kts` declares `files("libs/fms-ffmpeg-dts.aar")` and `files("libs/fms-vpx.aar")`
for the standard, noLegal, legacy and vr flavors, but `.gitignore` excludes `libs/`, so both binaries
exist only on a machine that built them. A local build works; a fresh clone and every GitHub Actions
runner do not.

**The list of them is one file, `scripts/ci/prebuilt-native-aars.txt`**, and all three consumers read
it rather than keeping a copy - that is what S2879 changed, because the mechanism S1539 built was
written around one hardcoded file name and the second AAR was never added to it.

- Build them: `scripts/builders/build-ffmpeg-dts-wsl.ps1` (WSL2, NDK r27c) and
  `scripts/builders/build-libvpx-vp9.sh` + `compile-vp9-classes.ps1`.
- Publish after any rebuild:
  `pwsh -NoProfile -File scripts/builders/publish-prebuilt-native-aar.ps1 -Name <file.aar>` (or
  `-All`), which uploads to the permanent `delivery-so-v1` release with `--clobber`.
- CI fetches them: `scripts/ci/fetch-prebuilt-libs.sh`, run by every build job in `android-ci.yml`
  and `maestro-tests.yml` before Gradle starts.
- The build refuses to go without them: `verifyPrebuiltNativeAars<Variant>` hangs off
  `pre<Variant>Build` for those four flavors and fails with the missing path and the fetch command.

That last one exists because the failure mode is asymmetric. An absent FFmpeg AAR killed the build
loudly (69 red runs before S1539); an absent VP9 AAR killed nothing - Gradle resolves an absent
`files(..)` path to an EMPTY collection, so CI stayed green for months and shipped an artifact with
no software VP9 renderer, which also meant no CI run could prove anything about the libvpx path.

Skipping the publish step after a rebuild does not break CI - it silently builds against the previous
binary, which is acceptable because CI is a compile/lint/test gate and these artifacts are prebuilt
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

**One release can occupy two directories (S2722).** The key is each ARTIFACT's own `versionCode`, and
since S2721 the watch's code is not a function of the phone's, so a release that ships both modules
writes the phone's flavors under the app code and the watch mapping under the wear code, each with its
own manifest. It is not re-keyed to one directory per release because a watch-only release published
through `/skill-release-wear` has no phone code to file under at all. What ties the two together is
the `versionName`, which both modules stamp from the same build instant in a joint release - so that,
not a derived code, is what identifies a release when reading the archive. Fetching the watch payload
therefore takes the variant as well as the name:
`fetch-deobfuscation.ps1 -VersionName <version> -Variant wear`. A watch-only release ties to nothing:
since S2788 it stamps its own name from its own instant, so it occupies a directory of its own, named
after the build its crash reports came from.

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
"verified". Since S2722 it resolves the judged release by `versionName` across the whole archive
rather than by one derived code, so the watch mapping is judged too; before that it opened the phone
code's directory alone and its PASS naming `standard` read as "looked and found nothing" when it meant
"did not look". A judged release with no wear payload now says so in the verdict (`wearRetained` in
`-Json`) instead of passing over it - the archive cannot distinguish a release that published no watch
artifact from one whose watch retention was lost, so it is stated, not failed. `/skill-release-wear`
creates no `release/v*` tag, so a watch-only release is reached only by `-VersionName <version>`.

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
