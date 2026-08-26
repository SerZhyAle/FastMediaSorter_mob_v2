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
.\gradlew.bat lintStandardDebug
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

- **`temp/BUILD.LOCK`** - acquired by `Enter-BuildLockOrExit` before any direct `gradlew`/`gradlew.bat` invocation, released by `Exit-AgentLock` after (success or failure). Since S1432 a busy lock **queues** the caller instead of refusing: it takes a ticket, reports its position and starts when its turn comes. Pass `-NoWait` (or set `FMS_LOCK_NO_WAIT=1`) where an immediate answer matters more than a turn.
- **`temp/CODE.LOCK`** - acquired via `scripts/utils/enter-code-lock.ps1 -Reason "<ticket/skill>"` before a multi-file source edit (Kotlin/XML/build-file). Since S1432 a busy lock queues the caller and **exits 4** ("queued, not yet your turn") rather than waving the edit through. Auto-releases from `post-change.ps1`'s closure - and that release is owner-checked, so it never removes a lock belonging to another live session; a skill that skips the facade (`/skill-fix`) must call `scripts/utils/exit-code-lock.ps1` itself when the edit is done.

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

**The queue (S1432).** Each lock has a queue directory `temp/<NAME>.QUEUE` holding one ticket file per waiter, numbered in order. The head of the queue owns the turn: a free lock is **not** enough to acquire, because a live head that has not yet spent its reservation window (5 min for Build, 3 for Code) still owns it - that window is what survives the gap between "your turn" and the moment gradle actually starts. Ownership of a ticket belongs to an agent **session**, not a process. A ticket whose owner has gone quiet, or which passed its ceiling (60 min Build, 20 min Code), is evicted by whoever reads the queue next. Every timing lives in one table, `$Script:AgentLockTimings`.

**Queue fairness and liveness (S1448).** Four rules make the queue actually hand out turns in order, each of them fixing an observed starvation where a session sat still for tens of minutes without a single error:

- **Taking a lock retires every ticket of the acquiring session**, not only the ticket handed to the acquire. Otherwise a session working step by step - take lock, close step, immediately queue for the next one - leaves the previous step's ticket parked on the head *while it holds the lock*, and nobody behind it can ever advance.
- **The turn is decided by ticket identity, never by session identity.** A caller holding no ticket is answered from the lock and the head's reservation; it can no longer inherit the turn just because the head happens to belong to its own session. `enter-code-lock.ps1` therefore takes its place in the queue **before** it asks for the lock, exactly as `Enter-BuildLockOrExit` already did - so a session that releases and immediately wants the lock back queues behind whoever was already waiting. A re-entrant call from a session that already holds the lock is recognised and returns 0 without queueing.
- **A waiting ticket carries its own heartbeat.** Liveness reads `lastSeenAt` first (stamped by `wait-for-lock-turn.ps1` on every poll), the owning session's transcript second, the enqueue time last. The transcript alone punished exactly the behaviour the contract demands: a session that queues, backgrounds the waiter and goes off to do lock-free work writes nothing, looked dead at the 15-minute mark, and was evicted from a place it had earned. The absolute ticket ceiling still judges `enqueuedAt` and is **not** extended by the heartbeat, so a genuinely abandoned head still ages out.
- **The refusal names the blocker that exists.** A lock that is held reports its holder; a lock that is free while a foreign ticket owns the head says so and names the head's session, reason, wait and reservation window. `enter-code-lock.ps1` no longer prints a `Holder:` line built from an absent lock file - the observed `Holder: session  (age 0s, reason: '')` sent readers hunting for a holder that was not there.

`lock-status.ps1 -Queue` surfaces the pathology directly: each ticket carries `heldByLockHolder`, the JSON payload carries `headOwnedByHolder`, and a text row owned by the current holder is suffixed `<- holds the lock`.

```powershell
# Who holds it, who is waiting, in what order (this session's own ticket is marked '>')
pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Build -Queue
pwsh -NoProfile -File scripts/utils/lock-status.ps1 -Name Code -Queue -Json

# Wait for your turn OUT OF BAND: run this as a background task and keep working
pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Name Code -Reason "S0900 edit"
```

`wait-for-lock-turn.ps1` takes a ticket, blocks, and **exits** the moment the turn arrives - its exit is the "your turn" signal, which is the only channel through which an external event returns an agent to work. The ticket deliberately survives that exit: the caller inherits it, protected by the reservation window, and passes it to `Enter-AgentLock -Ticket`. Exit codes: **0** granted, **2** timed out, **3** ticket evicted while waiting, **4** could not enqueue. Do not read the verdict from the exit code a background task reports - that is the exit of the last command in the launch line, and it has already turned a refused build into an apparently green one. Read the marker instead: `temp/<NAME>.TURN-<sessionId>.json`, carrying `outcome` (`granted` / `timeout` / `evicted` / `enqueue-failed`), the ticket number and how long the wait took.

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
- **A ticket lease stops two sessions working the same ticket** - `scripts/spec_catalog/ticket-lease.ps1`, one file per lease under `temp/SPEC-TICKET.LEASES/`. A claim is an atomic `CreateNew`, so of two sessions racing for one ticket exactly one wins; the loser gets **exit 3**, which is a normal outcome - it re-ranks with that id excluded and takes the next ticket, it does not wait. Release is owner-checked (**exit 4** refuses to free a live sibling's lease). Expiry follows the owning session's liveness with an independent 480-minute ceiling, and a stale lease is swept by whoever reads next - no watchdog, same as the queue. **S1448 widened what counts as alive**, because a preflight once offered S1436 as unleased while the owning session was demonstrably working it: a lease now carries its own `lastSeenAt`, refreshed on every verb its owner runs, and a session holding `CODE.LOCK` or `BUILD.LOCK` with a reason naming the ticket id counts as live on that evidence alone. The 480-minute ceiling still judges `claimedAt` and neither signal extends it. `spec-next-preflight.ps1` consumes the lease set as an extra exclusion source and leaves its five sort keys alone, so the owner's release-plan order still decides who gets what.
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

### Resource-link gate - S1915

Prints as `resource-link-gate`. The only gate in the closure facade that runs aapt. It fires when the changed set carries a resource or a manifest (`$isResourceChange`, so a Kotlin-only or docs-only closure skips it and pays nothing) and links those resources for every variant the set touches.

```powershell
# What the gate runs, one call per selected flavor - also the fix loop when it goes red
pwsh -NoProfile -File scripts/builders/check-standard-fast.ps1 -Mode Resources -Module app_v2 -Flavor Standard

# The same thing by its launcher shortcut
pwsh -NoProfile -File ./a.ps1 fr
```

**Variant selection.** `src/main` and every non-flavor source set ship inside the default variant, so `Standard` is always linked; a path under `src/<flavor>/` adds that flavor on top, deduplicated. A resource under `src/vr/res` linked only as `standard` would be judged by a variant that never sees the file - the same false green S1807 found when a phone target was quoted as proof under a wear change. The `wear` module declares no product flavors and `check-standard-fast.ps1` exits 2 on any non-default `-Flavor`, so the watch is answered before source sets are read at all.

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
- `docs/SCRIPT_CHEATSHEET.md` is excluded **by definition, not by setting**: it names every script by construction.
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

Room schema version: 53 (`@Database(version = ..)` in `AppDatabase.kt` is the source of truth - read it rather than this line).
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
6. Run the publisher: `pwsh -NoProfile -File scripts/release/publish-github-release.ps1`
   from the release worktree on `main`. The Assert-ExpectedFingerprint gate
   will now pass against the new pin.
7. Append an ADR-style entry inside this section recording: rotation date,
   reason, old fingerprint, new fingerprint, release tag that contained
   the rotation.

### ADR log

_(no rotations have happened yet - first entry will land here.)_
