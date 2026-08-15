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
| `.\a.ps1 fu`   | Fast full unit-test suite |
| `.\a.ps1 flr`  | Fast lint-rules detector test suite (`:lint-rules:test`); `-Tests <filter>` narrows it |
| `.\a.ps1 dc`   | Clean + debug build |
| `.\a.ps1 cls`  | Clean Gradle caches |
| `.\a.ps1 ss`   | Show unresolved specs (`sca-specs`) |
| `.\a.ps1 adb <verb>` | Ad-hoc adb swiss-army passthrough (see DEVICE OPS below) |
| `.\a.ps1 adb-devices` / `adb-shot` / `adb-log` / `adb-current` / `adb-launch` / `adb-logcat-clear` | Fixed-verb device shortcuts |

## DEVICE OPS (ad-hoc)

`scripts/devtest/adb.ps1` is the quick swiss-army for one-off work against a connected
emulator / device - runs natively (~0 LLM tokens), auto-discovers adb (not on PATH),
takes `-DeviceId` / `-Release` / `-Package` / `-Json`, and uses stable exit codes
(0 ok / 1 no-adb-or-bad-args / 2 no-device / 3 multi-device / 4 pkg-not-installed /
5 destructive verb refused / 7 adb-failed).

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

# PER-FLAVOR PROOF - all six flavors, no dedicated letter needed
.\a.ps1 fc -Flavor Lite         # also: Standard | NoLegal | Photos | Legacy | Vr
.\a.ps1 fc -Flavor Legacy       # covers minSdk 23
.\a.ps1 fc -Flavor Vr           # the only check that compiles src/vr

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

- **`temp/BUILD.LOCK`** - acquired by `Enter-BuildLockOrExit` before any direct `gradlew`/`gradlew.bat` invocation, released by `Exit-AgentLock` after (success or failure). Since S1432 a busy lock **queues** the caller instead of refusing: it takes a ticket, reports its position and starts when its turn comes. Pass `-NoWait` (or set `FMS_LOCK_NO_WAIT=1`) where an immediate answer matters more than a turn.
- **`temp/CODE.LOCK`** - acquired via `scripts/utils/enter-code-lock.ps1 -Reason "<ticket/skill>"` before a multi-file source edit (Kotlin/XML/build-file). Since S1432 a busy lock queues the caller and **exits 4** ("queued, not yet your turn") rather than waving the edit through. Auto-releases from `post-change.ps1`'s closure - and that release is owner-checked, so it never removes a lock belonging to another live session; a skill that skips the facade (`/skill-fix`) must call `scripts/utils/exit-code-lock.ps1` itself when the edit is done.

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

`wait-for-lock-turn.ps1` takes a ticket, blocks, and **exits** the moment the turn arrives - its exit is the "your turn" signal, which is the only channel through which an external event returns an agent to work. The ticket deliberately survives that exit: the caller inherits it, protected by the reservation window, and passes it to `Enter-AgentLock -Ticket`. Exit codes: **0** granted, **2** timed out, **3** ticket evicted while waiting, **4** could not enqueue. Do not read the verdict from the exit code a background task reports - that is the exit of the last command in the launch line, and it has already turned a refused build into an apparently green one. Read the marker instead: `temp/<NAME>.TURN-<sessionId>.json`, carrying `outcome` (`granted` / `timeout` / `evicted`), the ticket number and how long the wait took.

**Re-entrancy.** Several gates run a nested script while already holding `BUILD.LOCK`, and `& other.ps1` executes in the same process - so a nested acquire would queue behind a lock this very run owns. `Enter-BuildLockOrExit` recognises the holder as itself (same pid) or as the ancestor that launched it (inherited `FMS_BUILD_LOCK_HELD_BY`) and reuses the lock instead of waiting.

`Enter-BuildLockOrExit` runs one check before it even reaches the lock (S1425): it resolves the JVM Gradle will run on - `org.gradle.java.home` from the user-level `gradle.properties`, then the repository one, then `JAVA_HOME` - and verifies that `bin/java(.exe)` and `lib/jvm.cfg` both exist under it. If either is missing it prints the resolved path, the missing file and the config file that set it, then **exits 3**: the environment cannot build, which is a different fact from a build that failed (exit 1) and from a wait that timed out (exit 2). Nothing is built and the lock is never taken. The check is two `Test-Path` calls and never launches a JVM, so it costs nothing per build. It exists because a partial Android Studio uninstall deleted `jbr/lib/jvm.cfg` while leaving `jbr/bin/java.exe`: the daemon already running kept compiling from memory, every compile check stayed green, and only forked JVMs failed - the whole unit-test tier was down for hours before anything said so.

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

# THE SAME SET AS A RELEASE BLOCKER (0 clean, 1 blocked, 2 cannot verify)
pwsh -NoProfile -File scripts/quality/assert-new-lexemes-translated.ps1

# THE BULK ROUND TRIP THAT CLEARS IT
pwsh -NoProfile -File scripts/utils/locale-bulk-import.ps1 -TextPath <file returned by the translator>
```

The app declares thirteen interface locales in `app_v2/src/main/res/xml/locales_config.xml`. Three - `en`, `ru`, `uk` - are authored and must stay complete. The other ten are machine-translated in bulk and are allowed to lag, but only until the release. The loop, in order:

1. Writing a key with `set-android-string.ps1 -Action add` names the locales the call left empty and prints a ready-to-paste `-Translations` fragment. A hint, not a refusal.
2. Closing a ticket that touched a strings file prints the `new-lexeme-count` advisory. Also not a refusal.
3. The pre-release sweep runs step `0.8`, which **is** the refusal. `list-new-lexemes.ps1` writes `temp/S1627/new_lexemes_en.txt`; that file goes to the external translation service, each returned file comes back through `locale-bulk-import.ps1`, and the step is re-run until it is 0.

Three facts a reader cannot derive from the commands:

- **The refusal sits at the release, not at the ticket, by owner decision (strategic ADR-2).** Nothing ships between releases, so translating each key the day it is written buys the user nothing while costing ten translations per ticket; one batch per release costs one round trip for all of them.
- **A missing translation is an absent key, never an English copy (ADR-6, S1190).** Android falls back to English on its own, so a partial locale is a shippable state. This is why the producer asks each locale's resource file which keys it carries, rather than comparing values.
- **`scripts/quality/locale-untranslated-baseline.txt` holds identities, not a count.** It froze the 19 keys already untranslated on 2026-08-14 - all of them `S1626`'s placeholder-misread phrasings - so a pre-existing gap cannot be reported as new. A count would let a new key slip in behind an old one cleared in the same release. Entries leave the file as `S1626` clears them, and the producer reports a cleared entry as stale; do not expect that soon, since `S1626` is `BlockExternal` - the rule that looked obvious (placeholder at a string edge) was measured over all 307 placeholder-bearing strings and does not discriminate, so the set clears through a probe in a future bulk round rather than through an edit anyone can make today.

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
| `DECLARES_BATTERY_OPTIMIZATION` | [+] | [+] | [-] |

`ENABLE_LEAKCANARY` is debug-only (`debugImplementation`); field absent in staging/release.

`DECLARES_BATTERY_OPTIMIZATION` (S1436) is the one flag here that mirrors the manifest rather than a feature: the release build strips `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, so code that would offer to grant it must read this flag rather than assume the permission is there. `DECLARES_OVERLAY_PERMISSION` and `DECLARES_SCREEN_CAPTURE` are the flavor-axis members of the same family - see `docs/FLAVOR_MATRIX.md`, which is generated from the `productFlavors` block. The permission registry filters its rows on all three, and `PermissionRegistryManifestParityTest` fails the build if a flag and the merged manifest ever disagree.

## DATABASE

Room schema version: 50 (`@Database(version = ..)` in `AppDatabase.kt` is the source of truth - read it rather than this line).
Library: `room-runtime:2.7.0`.
Migrations: one `MigrationNNToNN.kt` file per step in `data/local/db/`, registered in `core/di/DatabaseModule.kt`.
Exported schemas: `app_v2/schemas/<db-class>/<version>.json`, generated by the build and committed.
**Rule**: Increment schema version on every schema change, and take a migration's target DDL from the generated `<version>.json` rather than hand-writing it.

## NDK & ABI

NDK r27c (`27.2.12479018`) - first NDK release with 16 KB page-size aligned `libc++_shared.so` (Google Play requirement since 2025-11-01 for apps targeting Android 15+).

ABI strategy is flavor-local, not buildType-local (AGP merges buildType+flavor `abiFilters` as UNION, not intersection - a buildType-level list would leak non-VR ABIs into VR AABs):
- `standard`, `lite`, `photos`, `legacy`: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`
- `vr`: `arm64-v8a` only (Meta Quest 2/3/Pro)
- `noLegal`: `arm64-v8a` + `x86_64` (Chaquopy Python wheels are arm64/x86_64 only; covers Quest + modern phones + emulators)

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
6. Run the publisher: `pwsh -File scripts/release/publish-github-release.ps1`
   from the release worktree on `main`. The Assert-ExpectedFingerprint gate
   will now pass against the new pin.
7. Append an ADR-style entry inside this section recording: rotation date,
   reason, old fingerprint, new fingerprint, release tag that contained
   the rotation.

### ADR log

_(no rotations have happened yet - first entry will land here.)_
