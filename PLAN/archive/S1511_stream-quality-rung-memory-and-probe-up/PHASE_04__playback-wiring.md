# Phase 04 - Wire memory and probe into playback

**Strategic spec:** [`../S1511_stream-quality-rung-memory-and-probe-up.md`](../S1511_stream-quality-rung-memory-and-probe-up.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Connect the store and the policy to the live stream session: restore the learned ceiling on open, run the probe on the existing tick, judge it against what actually played, and persist the outcome.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/GetStreamQualityMemoryUseCase.kt` | New | ≤ 40 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/RecordStreamQualityMemoryUseCase.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/StreamSourceRepository.kt` | Modified | ≤ 30 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/di/DatabaseModule.kt` | Modified | ≤ 8 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 12 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/StreamQualityMemoryEntryPoint.kt` | New | ≤ 35 |

> Added 2026-08-14: the two rows below `StreamPlaybackHelper.kt` are the dependency route the plan assumed was already there. `VideoPlayerManager` is constructed by hand outside the Hilt graph, so a use case has to be routed to it deliberately.
>
> The bundle route S1144 ADR-6 used for the per-channel track preference was written first and then reverted: it is filled from `PlayerActivity`, so each dependency added to it becomes another domain-layer field injection in an Activity. `post-change` refused it - `assert-neuroslop` activity-logic, `+2 in PlayerActivity.kt`, CLAUDE.md Rule 3. The track preference predates that ratchet and is baselined; a new one is not. The shipped route is the `EntryPoint` one that `PlaybackControlDialogFragment` and `PlaybackRenderersFactory` already use, which keeps the host out of it entirely - so `VideoPlayerDependencies.kt`, `PlayerViewerFactory.kt`, `PlayerActivity.kt` and the manager test are untouched by this ticket after all.

> Confirm the repository interface's own file before editing the implementation; follow whatever split `recordPlayOutcome` and `playOutcome` already use rather than inventing a second shape.

---

## Steps

### Step 04.1 - Add the use cases and repository access

**Files:** `GetStreamQualityMemoryUseCase.kt`, `RecordStreamQualityMemoryUseCase.kt`, `StreamSourceRepository.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a read and a write path for the rung memory, mirroring `GetStreamPlayOutcomeUseCase` and `RecordStreamPlayOutcomeUseCase` in shape and placement. The write also prunes: drop records older than the retention period and keep the store within its address cap. Normalize the address with the Phase 01 normalizer at the boundary, so no caller passes a raw URL.

**Why:**

Strategic section 5.1 item 5 requires the memory to age out and stay bounded, and the layering rule puts persistence access behind use cases rather than letting a player helper reach a DAO.

**Verification:**

- `Glob` - both use-case files exist under `domain/usecase/streams/`.
- `Grep` - `StreamUrlNormalizer` referenced from the write path.
- `Grep` - `StreamQualityMemoryDao` returns zero hits outside `data/` and `core/di/`. Corrected 2026-08-14: Hilt cannot inject a Room DAO interface without a `@Provides`, and every DAO in the project is bound in `core/di/DatabaseModule.kt` - the shipped `StreamPlayOutcomeDao` this step mirrors fails the original predicate identically. The intent the predicate guards is that no consumer bypasses the repository, which a DI binding is not.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Rung-memory read and write paths added, mirroring GetStreamPlayOutcomeUseCase / RecordStreamPlayOutcomeUseCase. Both use cases normalize the address themselves so read and write cannot disagree on the key; the write prunes by age (7d) and by channel cap (200) in one transaction. Predicates: both files present, StreamUrlNormalizer referenced from the write path, StreamQualityMemoryDao reaches nothing outside data/ except the DI binding. Plan corrected: DatabaseModule.kt was missing from Files Touched (Hilt has no binding for a Room DAO without a @Provides - the shipped StreamPlayOutcomeDao it mirrors is bound the same way), and predicate 3 was unachievable as written.

---

### Step 04.2 - Restore the learned ceiling when the ladder arrives

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Read the channel's memory when the stream session starts, and pass it into `setRenditions(..)` at the existing inventory site so the ceiling is placed on the remembered rung. Do not move or duplicate the once-per-session inventory guard. Log which rung was adopted and, when the remembered rung is not on this ladder, log that it was ignored - the two cases must not read alike in an archive.

**Why:**

Strategic section 6 Q1 fixes the moment as "immediately after the ladder is inventoried", because the ladder is unknown before `onTracksChanged`, and section 7 names re-inventory as the way a probe could silently cancel itself.

**Verification:**

- `Grep` - the remembered rung is passed to `setRenditions` in `inventoryStreamRenditions`.
- `Grep` - the once-per-session guard `renditionCount == 0` is still present and unmodified.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Remembered ceiling restored at the inventory site. The memory is read in playStreamVideo, where suspending is free, and held on a session field because inventoryStreamRenditions runs inside a Media3 callback that cannot suspend; RTSP reads nothing. Three distinguishable log lines: adopted verbatim, not on this ladder, fell back to a lower rung. Predicates: setRenditions(renditions, remembered) present at L279, once-per-session guard renditionCount == 0 unmodified at L265. a.ps1 fk exit 0. Plan corrected: the dependency route was missing - VideoPlayerManager is built by hand outside Hilt, so both use cases and the application scope travel VideoPlayerStoreDependencies, per the S1144 ADR-6 precedent; five files added to Files Touched.

---

### Step 04.3 - Run the probe on the existing tick

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> On each stats-sampler tick, ask the policy whether a probe is due and, when it is, apply the returned `Cap` through the same `selector.setParameters(..)` the step-down path uses. Add no second timer. Log the probe start with the rung it is reaching for.

**Why:**

Strategic ADR-3 forbids a new timer because the tick already runs on the stream session's own lifetime, and ADR-4 reuses the live parameter application that makes the probe cheap.

**Verification:**

- `Grep` - the probe call is reached from the stats-sampler closure, not from a new coroutine loop.
- `Grep` - no new `while (isActive)` loop added to `StreamPlaybackHelper.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Probe scheduled on the existing stats tick. evaluateStreamQualityProbe is called from the StreamStatsSampler readSample closure (L219), asks the controller isProbeDue and, when due, startProbe; the returned Cap goes through applyStreamQualityCap, extracted from the step-down path so both directions share one setParameters call (ADR-4). The probed rung is logged with its true bitrate read off the controller, because a rung with no declared bandwidth yields the size-only sentinel in the Cap. Predicates: probe call reached from the sampler closure, while (isActive) 0 hits in the file. a.ps1 fk exit 0.

---

### Step 04.4 - Judge the probe against what actually played

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Close the probe with a verdict: a stall arriving while it is open fails it, and surviving the observation window succeeds only when `ExoPlayer.videoFormat` shows the picture actually reached the probed rung. Read that format the same way the step-down path already does. Log both numbers - the rung probed and the rung that played - so a probe that changed nothing cannot read like one that worked.

**Why:**

Strategic section 7 names the case where a probe "survived its window" while the engine never climbed, which would write a rung that rendered no frame; section 4 records that the signal for what actually played already exists and needs no new listener.

**Verification:**

- `Grep` - `videoFormat` read in the probe verdict path.
- `Grep` - the probe verdict log line prints both the probed and the playing rung.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Probe verdict wired. closeStreamQualityProbe runs on the tick while a probe is open and asks closeProbeIfSurvived, which succeeds only when the picture reached the probed rung; the probed rung is captured before the close because a successful close drops it. The failure direction stays where a stall already arrives - applyStreamQualityStepDown now captures isProbeOpen before registerStall and prints a distinct restored line, so a failed probe cannot read like a step down. Predicates: videoFormat is read at L374 through playingStreamRendition(), the single reader both verdict paths call (L338 step-down, L429 probe) - the plan asked for the same reader the step-down path uses, and sharing one function is that; verdict log prints both rungs at L432. a.ps1 fk exit 0.

---

### Step 04.5 - Persist the outcome

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StreamPlaybackHelper.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Write the policy's snapshot to the store on a step down and on each probe verdict, off the main thread through the use case. Make the write survive the session teardown that nulls the controller, so an outcome decided moments before the user leaves the channel is not lost.

**Why:**

Strategic section 2 goal 1 requires the learned rung to outlive the session, and section 4 records that the controller and its collaborators are nulled together at teardown, which is exactly when a naive write would be dropped.

**Verification:**

- `Grep` - the write is invoked from both the step-down and the probe-verdict paths.
- `Grep` - `GlobalScope` returns zero hits in `StreamPlaybackHelper.kt`.
- `/build` `standard debug` succeeds.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Outcome persisted on both verdict paths. persistStreamQualityMemory snapshots controller.memory on the calling thread and hands only values to the application scope (IoDispatcher + SupervisorJob), so a write decided moments before teardown still lands after the controller is nulled; the channel address is kept on its own session field for the same reason. Called from applyStreamQualityStepDown (L362 - covers both a step down and a failed probe) and from closeStreamQualityProbe (L464 - a survived probe). Predicates: both call sites present, GlobalScope 0 hits in the file, a.ps1 dq exit 0 - Build Successful, and hiltJavaCompileStandardDebug ran, which is the only check that proves the new DAO binding and the ApplicationScope injection resolve.
- 2026-08-14 - Route corrected after post-change refused it: the S1144 bundle ends in PlayerActivity, so both use cases landed there as domain-layer field injections - assert-neuroslop activity-logic reported '+2 in PlayerActivity.kt', CLAUDE.md Rule 3. Reworked onto the EntryPoint route PlaybackControlDialogFragment and PlaybackRenderersFactory already use (new di/StreamQualityMemoryEntryPoint.kt), which keeps the host out of it; VideoPlayerDependencies, PlayerViewerFactory, PlayerActivity and the manager test are untouched by this ticket after all. Re-run: post-change PASS, activity-logic new occurrences 0, a.ps1 dq exit 0.
- 2026-08-14 - Phase-boundary audit (Layers 1, 2, 3, 4). Layer 1 clean: UI -> UseCase -> Repository -> DAO respected, VerbNounUseCase naming, every touched file far under the 1500 LOC ceiling (StreamPlaybackHelper 728, VideoPlayerManager 841). Layer 2 answers the question phase 02 carried forward - probeFailuresByRung and the probe state are unsynchronised fields, and both mutators now reach them from one thread: managerScope is Dispatchers.Main, the stats sampler launches in it, and Media3 delivers listener callbacks on the app main thread. The persist path snapshots controller.memory on Main and hands only values to the IO application scope, so it touches no controller field off-thread. Layer 3: no listener added or removed - listener-symmetry new imbalance 0 - and both new session fields are nulled in releaseStreamDiagnostics beside the ones they travel with. Layer 4: every DAO call is suspend, and the two prunes share one withTransaction. P2 recorded, not fixed: a probe whose window elapses while the picture never climbs stays open indefinitely, because only a climb closes it as success and only a stall fails it. Benign on inspection - the raised ceiling is permissive rather than forced, a later climb still closes it as success, and a stall still restores it - and closing it on a timer would change phase 02 policy and its 26 shipped tests without the strategic spec ruling on what an inconclusive probe means. Screenshot deferred (no device): the ticket has no user-visible surface at all - strategic 3.3 records that explicitly, by the precedent of the silent step-down - so phase Done Criteria demand no shot.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings; the added write path is checked for main-thread safety and the tick path for listener symmetry.

---

## Handoff Notes to Next Phase

The feature is complete and silent. Phase 05 records it and regenerates the derived documents.

---

## Rollback Plan

Revert phase commit(s) - the store and policy remain but are unreached, so playback returns to the shipped step-down behaviour. The database version stays at 50; do not attempt to unwind it.
