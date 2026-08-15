# Phase 03 - Signal sources

**Strategic spec:** [`../S1421_launcher-own-notification-area.md`](../S1421_launcher-own-notification-area.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Fill the registry with the three signal producers strategic §4.6 found observable today - playback, file
transfer, background jobs - each returning its own signals and its own tap target.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §4.6 read - it fixes which producers are in scope and why the notification-posting services are not.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/source/PlaybackLauncherSignalSource.kt` | New | ≤ 110 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/source/FileTransferLauncherSignalSource.kt` | New | ≤ 130 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/source/BackgroundWorkLauncherSignalSource.kt` | New | ≤ 170 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/di/LauncherSignalModule.kt` | Modified | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/worker/WorkManagerScheduler.kt` | Modified | ≤ 420 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |

> The only `src/main` edit is widening one existing constant's visibility (step 03.3). It adds no launcher
> branch and no `BuildConfig` guard, so CLAUDE.md Rule 14 is untouched.

---

## Steps

### Step 03.1 - Add the playback source

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/source/PlaybackLauncherSignalSource.kt`, `app_v2/src/main/res/values/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `class PlaybackLauncherSignalSource @Inject constructor(@ApplicationContext private val context: Context) : LauncherSignalSource`.
> `read()` calls `AudioNowPlayingSnapshotStore.read(context)` - the same store `OwnSessionNowPlayingSource`
> reads - and returns a single `LauncherSignal(kind = PLAYBACK)` when `snapshot.active` is true, empty list
> otherwise. Use the snapshot's title as `label` and its artist as `detail`. `open()` returns the intent
> that opens the player, built the same way the playback notification's content intent is - reuse that
> builder rather than assembling a second one, so the strip and the notification cannot diverge.
> Add the string key `launcher_signal_playback` for the fallback label when the snapshot carries no title,
> via `set-android-string.ps1 -Action add -Key launcher_signal_playback -En .. -Ru .. -Uk ..`; check the
> wording against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §4.4 rules that a playback signal opens the player, and ADR-1 restricts sources to state the app
already produces - the snapshot store is that state, already written for the Now Playing gadget.

**Verification:**

- `Grep` - `class PlaybackLauncherSignalSource` matches exactly once and `: LauncherSignalSource` is on the same declaration.
- `Grep` - `AudioNowPlayingSnapshotStore` present.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_signal"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 03.2 - Add the file-transfer source

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/source/FileTransferLauncherSignalSource.kt`, `app_v2/src/main/res/values/strings.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `class FileTransferLauncherSignalSource @Inject constructor(@ApplicationContext private val context: Context, private val coordinator: BrowseFileTransferCoordinator) : LauncherSignalSource`.
> `BrowseFileTransferCoordinator` is already `@Singleton` and injectable outside Browse, so `observe()` maps
> `coordinator.activeTransferFlow()` straight through - no collector of its own and no application scope.
> Emit one `LauncherSignal(kind = FILE_TRANSFER)` while `state.isActive` with the file counter in `detail`,
> an empty list otherwise. `open()` returns `BrowseActivity.createIntent(..)` on the transfer's own resource
> and folder plus `EXTRA_REATTACH_TRANSFER`, which is the hand-off
> `BrowseFileOperationsManager.observeBackgroundTransfers()` already performs. Add `launcher_signal_transfer`
> via `set-android-string.ps1 -Action add`.

**Why:**

Strategic ADR-1 names running copy and transfer operations as one of the three signal categories, and §4.4
rules that tapping one opens the screen the operation belongs to.

**Verification:**

- `Grep` - `class FileTransferLauncherSignalSource` matches exactly once.
- `Grep` - `activeTransferFlow()` present.
- `Grep -n "GlobalScope"` returns zero hits in the file.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_signal"` exits 0.

**Status:** `[x]` done

---

### Step 03.3 - Add the background-work source

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/source/BackgroundWorkLauncherSignalSource.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/worker/WorkManagerScheduler.kt`, `app_v2/src/main/res/values/strings.xml`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `class BackgroundWorkLauncherSignalSource @Inject constructor(@ApplicationContext private val context: Context) : LauncherSignalSource`
> observing the three long-lived jobs that post their own notification: `NetworkFilesSyncWorker.WORK_NAME`
> and `DuplicateDetectionWorker.UNIQUE_WORK_NAME` through `getWorkInfosForUniqueWorkFlow`, and the
> scheduled-operations family through `getWorkInfosByTagFlow(WorkManagerScheduler.TAG_SCHEDULED_OP)`.
> Promote `TAG_SCHEDULED_OP` from `private companion object` to an `internal`/`const` member of a non-private
> companion in `WorkManagerScheduler` - its KDoc already calls it the only handle that reaches the whole
> family - and change nothing else in that file. Emit one signal per job whose `WorkInfo.State` is not
> finished, labelled from a string key per job kind. `open()` returns the screen each job belongs to:
> scheduled operations to the scheduled-operations screen, duplicate detection to the duplicates screen,
> network sync to the resource list; return null for a job with no screen rather than inventing one.
> Add `launcher_signal_scheduled_ops`, `launcher_signal_duplicate_scan`, `launcher_signal_network_sync`
> in one `set-android-string.ps1 -Action add` call each.

**Why:**

Strategic §4.6 fixes the first version's third source to the WorkManager-backed jobs, because they are the
only own-notification posters with observable state and building a live-notification registry across all
nine posters is out of this ticket's scope.

**Verification:**

- `Grep` - `class BackgroundWorkLauncherSignalSource` matches exactly once.
- `Grep` - `getWorkInfosByTagFlow` and `getWorkInfosForUniqueWorkFlow` both present.
- `Grep -n "private const val TAG_SCHEDULED_OP"` returns zero hits in `WorkManagerScheduler.kt`.
- `Grep -rn "\"sched_op\""` matches only the constant declaration - no second literal introduced.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_signal"` exits 0.

**Status:** `[x]` done

---

### Step 03.4 - Bind the three sources into the set

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/di/LauncherSignalModule.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Add three `@Binds @IntoSet abstract fun` methods to `LauncherSignalModule`, one per source, keeping the
> `@Multibinds` declaration from phase 01. Declaration order in this module does not set the row order and
> must not be relied on: a Dagger `Set` multibinding guarantees no iteration order, so
> `LauncherSignalRegistry` sorts by `LauncherSignalKind` declaration order instead (step 01.3).

**Why:**

Strategic ADR-4 requires a new source to arrive as one binding, so the concrete sources must reach the
registry through the multibound set rather than through its constructor.

**Verification:**

- `Grep -c "^    @IntoSet$"` returns 3 in the file - the annotation lines, not the KDoc's mention of them.
- `.\a.ps1 dq` exits 0. Not `fk`: Dagger validates the graph in `hiltJavaCompile`, which `fk` never reaches.
- `.\a.ps1 fkn` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, BUILD SUCCESSFUL in 1m 7s (run after the audit fix below).
- [x] `Grep` for `TODO(phase-03)` returns zero hits (expected: 0 | actual: 0).
- [x] `Grep -n "Log\.d\("` returns zero hits in every file this phase touched - `post-change`'s
      `nontimber-log` dimension reported 0 new occurrences on each.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_signal"` exits 0 -
      all 5 keys present in en/ru/uk.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13). See audit note below.

---

## Phase-boundary audit (2026-08-07)

Layers 1-3 of `docs/CODE_AUDIT_PROTOCOL.md`; Layer 4 has no Room surface of its own - the WorkManager
database is observed through its own public flows, not queried here.

- Layer 1 - PASS. Three sources at 58/74/89 lines, each named for its producer, each reaching the app only
  through state that state's owner already publishes. The single `src/main` edit widens one constant's
  visibility and changes no behaviour.
- Layer 2 - PASS. Every source returns a cold flow, so nothing runs while the strip is not collected. The
  one producer that polls is the one with no change notification, and it does so on `Dispatchers.IO`
  because its first `SharedPreferences` read touches disk.
- **P1 - FIXED in this phase.** `FileTransferLauncherSignalSource` kept the active transfer's resource id and
  folder path in two plain fields, written inside `map` on the collector's coroutine and read from `open()`
  on the main thread. Two independent writes can be observed half-applied, and a bare `Long` is not
  guaranteed to be written atomically, so a tap could have opened the wrong folder or a torn resource id.
  Replaced with one `@Volatile` reference to the whole `BrowseFileTransferRequest`; rebuilt, `.\a.ps1 dq`
  exit 0.
- Layer 3 - PASS. No listener is registered and no receiver bound; the WorkManager flows are cold and torn
  down with their collector.

---

## Step Log

- 2026-08-07 - Step 03.1 PASS. `source/PlaybackLauncherSignalSource.kt` created (58 lines, longest 107) and `launcher_signal_playback` added across EN/RU/UK in one `set-android-string.ps1 -Action add` call. Predicates: `class PlaybackLauncherSignalSource` x1 with `: LauncherSignalSource` on the declaration; `AudioNowPlayingSnapshotStore` present; `check_strings_localized.ps1 -KeyPrefix "launcher_signal"` exit 0 ("all 1 key(s) present in en/ru/uk"). Deviation from the prompt: it said to reuse the playback notification's content-intent builder, but no such builder exists - `AudioPlaybackService` constructs the intent inline for a `PendingIntent` (`setSessionActivity`, around line 564). The bare intent is rebuilt here against the same contract, `MainActivity.ACTION_RESUME_PLAYER`, with a comment naming it; extracting a shared builder would mean refactoring the service inside a step that does not own it.
- 2026-08-07 - Step 03.2 PASS. `source/FileTransferLauncherSignalSource.kt` created (74 lines, longest 109) and `launcher_signal_transfer` added across EN/RU/UK. Predicates: `class FileTransferLauncherSignalSource` x1; `activeTransferFlow()` present; `GlobalScope` 0 hits; `check_strings_localized.ps1 -KeyPrefix "launcher_signal"` exit 0 ("all 2 key(s) present in en/ru/uk"). This step is what forced the phase 01 contract revision recorded in that phase's Step Log: the prompt's "keep the latest state in a field updated by an application-scope collector" would have held a WorkManager Room observer open for the app's whole life to answer a synchronous `read()`. With `observe()` returning a flow, the coordinator's flow is mapped straight through and nothing runs while the strip is not collected. `PlaybackLauncherSignalSource` was rewritten to the same contract in this step; `.\a.ps1 fk` exit 0, BUILD SUCCESSFUL in 52s, covers all three files.
- 2026-08-07 - Step 03.3 PASS. `source/BackgroundWorkLauncherSignalSource.kt` created (89 lines, longest 108); `TAG_SCHEDULED_OP` promoted out of `WorkManagerScheduler`'s private companion; `launcher_signal_scheduled_ops`, `launcher_signal_duplicate_scan` and `launcher_signal_network_sync` added across EN/RU/UK. Predicates: class x1; `getWorkInfosByTagFlow` x1 and `getWorkInfosForUniqueWorkFlow` x2; `private const val TAG_SCHEDULED_OP` 0 hits; `"sched_op"` literal appears once, at the declaration; `check_strings_localized.ps1` exit 0 ("all 5 key(s) present in en/ru/uk"); `.\a.ps1 fk` exit 0, BUILD SUCCESSFUL in 45s.
  - Tap targets: scheduled operations open `SettingsActivity` with the existing `EXTRA_OPEN_SCHEDULED` deep-link extra; network sync opens `MainActivity`, the resource list; duplicate detection opens `DuplicatesActivity` with no resource extra. That last one is a deliberate limit, not an omission - a scan is enqueued with a `LongArray` of resource ids in its input data, and `WorkInfo` does not expose input data, so the resource cannot be recovered from the observed state. The activity already defaults `EXTRA_RESOURCE_ID` to -1, which is its own no-resource path.
- 2026-08-07 - Step 03.4 PASS. Three `@Binds @IntoSet` methods added to `LauncherSignalModule` beside the `@Multibinds` declaration. Predicates: `^    @IntoSet$` x3; `.\a.ps1 dq` exit 0, BUILD SUCCESSFUL in 1m 28s - `hiltJavaCompileStandardDebug` resolves the three-element set; `.\a.ps1 fkn` exit 0, BUILD SUCCESSFUL in 51s. The step's original predicate said `fk`; corrected to `dq` for the same reason as step 01.4, and the raw `@IntoSet` count was tightened to the annotation lines because the class KDoc names `@Binds @IntoSet` in prose and inflated the count to 4.

---

## Handoff Notes to Next Phase

`LauncherSignalRegistry.observe()` now emits real signals in a fixed order, and `open()` resolves a tap to
an intent or to null. Phase 04 renders that list and must treat a null intent as "no tap target", not as an
error.

---

## Rollback Plan

Revert phase commit(s). The one `src/main` edit is a visibility widening with no behaviour change; the
three sources are new files bound only through the launcher-only module.
