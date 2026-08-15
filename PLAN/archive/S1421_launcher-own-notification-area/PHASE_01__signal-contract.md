# Phase 01 - Signal contract

**Strategic spec:** [`../S1421_launcher-own-notification-area.md`](../S1421_launcher-own-notification-area.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 4 / 4
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Introduce the signal model, the source contract every signal producer implements, and the registry that
merges sources into one observable list. No UI and no concrete source yet.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/nowplaying/NowPlayingSource.kt` read - this phase mirrors its shape.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignal.kt` | New | ≤ 90 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalSource.kt` | New | ≤ 60 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRegistry.kt` | New | ≤ 140 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/di/LauncherSignalModule.kt` | New | ≤ 40 |

> Flavor placement: all four files live in `src/launcherEnabled/`, the source set mounted only for
> `standard` and `noLegal`. Nothing lands in `src/main/java/`.

---

## Steps

### Step 01.1 - Declare the signal model

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignal.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `LauncherSignal` as a data class with `id: String` (stable across refreshes so the row can diff),
> `kind: LauncherSignalKind`, `@DrawableRes iconRes: Int`, `label: String` and `detail: String?`. Declare
> `LauncherSignalKind` in the same file as an enum with `PLAYBACK`, `FILE_TRANSFER`, `BACKGROUND_WORK`.
> The model carries no `Intent`, no `Context` and no Android view type - navigation is the source's job
> (step 01.2).

**Why:**

Strategic ADR-4 requires the signal source to be an extension point rather than a list enumerated inside
the strip, so the type the strip renders must be producible by a source S1465 adds later without touching
the strip.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `data class LauncherSignal(` matches exactly once.
- `Grep` - `enum class LauncherSignalKind` matches exactly once.
- `Grep` - `Intent` returns zero hits in this file.

**Status:** `[x]` done

---

### Step 01.2 - Declare the source contract

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `interface LauncherSignalSource` with `fun observe(): Flow<List<LauncherSignal>>` and
> `fun open(signal: LauncherSignal): Intent?`. Document on the interface that `observe()` emits an empty
> list when the source has nothing active, and that `open()` returns null when the signal has no screen to
> open, so the strip can drop the tap target instead of starting a no-op activity. Model the KDoc on
> `NowPlayingSource`, which states the same "consumer must not know which source it is talking to" rule.
>
> Revised 2026-08-07 during step 03.2 - originally `fun read(): List<LauncherSignal>`. See this phase's Step
> Log for why a snapshot getter could not work.

**Why:**

Strategic §4.4 rules that tapping a signal opens the screen the signal belongs to, and only the producer
of a signal knows that screen - putting `open()` on the source is what keeps the strip from carrying a
`when` over signal kinds that ADR-4 would then have to extend for every new source.

**Verification:**

- `Grep` - `interface LauncherSignalSource` matches exactly once.
- `Grep` - `fun observe(): Flow<List<LauncherSignal>>` present.
- `Grep` - `fun open(signal: LauncherSignal): Intent?` present.

**Status:** `[x]` done

---

### Step 01.3 - Add the registry that merges sources

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRegistry.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `@Singleton class LauncherSignalRegistry @Inject constructor(private val sources: Set<@JvmSuppressWildcards LauncherSignalSource>)`.
> Expose `fun observe(): Flow<List<LauncherSignal>>` that `combine`s every source's own `observe()`, flattens
> the parts and sorts them by `LauncherSignalKind` declaration order, then `.distinctUntilChanged()`. Expose
> `fun open(signal: LauncherSignal): Intent?` that asks each source and returns the first non-null. A source
> that throws must not take the row down: wrap each input in `.onStart { emit(emptyList()) }` (so one silent
> producer cannot stall `combine`, which waits for every input's first emission) and `.catch { }` that logs
> with `Timber.w` naming the source class and emits an empty list. An empty source set returns
> `flowOf(emptyList())` rather than an empty `combine`, which would never emit.
>
> Revised 2026-08-07 during step 03.2 alongside 01.2. The polling loop and its `REFRESH_INTERVAL_MS` moved
> into the one source that actually needs to poll.

**Why:**

Strategic ADR-2 requires one node to decide what the strip shows, and a registry that merges every source
into a single list is what lets phase 02's host ask one question - are there signals - instead of polling
each producer and re-deciding precedence itself.

**Verification:**

- `Grep` - `class LauncherSignalRegistry` matches exactly once.
- `Grep` - `fun observe(): Flow<List<LauncherSignal>>` present.
- `Grep` - `combine(`, `onStart {` and `.catch {` all present.
- `Grep` - `Log\.d\(` returns zero hits in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 01.4 - Wire the multibinding

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/di/LauncherSignalModule.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Create `@Module @InstallIn(SingletonComponent::class) abstract class LauncherSignalModule` carrying
> `@Multibinds abstract fun launcherSignalSources(): Set<LauncherSignalSource>`. Phase 03 adds the
> `@Binds @IntoSet` methods for the concrete sources; the empty-set declaration is what makes the graph
> resolve before any source exists. Model the file on
> `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/SensorGadgetModule.kt`.

**Why:**

Strategic ADR-4 states the source list is open for addition, and a `@Multibinds` set is what lets S1465
add a source by declaring one binding instead of editing the registry's constructor.

**Verification:**

- `Grep` - `@Multibinds` present in the file.
- `Grep` - `abstract class LauncherSignalModule` matches exactly once.
- `.\a.ps1 dq` exits 0. Not `fk`: `fk` stops after `compileStandardDebugKotlin`, and Dagger validates the
  graph in `hiltJavaCompile`, so a `MissingBinding` sits behind a green `fk` (S1170).

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, BUILD SUCCESSFUL in 1m 25s.
- [x] `.\a.ps1 fkn` exits 0 - BUILD SUCCESSFUL in 1m 29s.
- [x] `Grep` for `TODO(phase-01)` returns zero hits (expected: 0 | actual: 0).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13). See audit note below.

---

## Phase-boundary audit (2026-08-07)

Layers 1 and 2 of `docs/CODE_AUDIT_PROTOCOL.md`; Layer 3 has no listener to own and Layer 4 no Room surface.

- Layer 1 - PASS. Four files, 28/25/63/22 lines. Naming follows the `LauncherGadgetRegistry` idiom already
  in this package tree; no UI or Activity logic; every file in `src/launcherEnabled`, no `BuildConfig` guard.
- Layer 2 - PASS. `observe()` is a cold flow whose loop is cancellable at the `delay`; `flowOn` sits after
  `distinctUntilChanged`, so both the polling and the diffing run off the main thread.
- P3 - `observe()` is cold, so two collectors would run two independent polling loops. Phase 02 exposes one
  `StateFlow` from the manager and is the only collector, so this stays a note rather than a change.
- P3 - `runCatching` in `guarded()` catches `Throwable`, which would swallow a `CancellationException`. Both
  wrapped calls are non-suspend by the `LauncherSignalSource` contract and the repo's own
  `swallowed-cancellation` gate reported 0 new occurrences on this file, so no defensive rethrow was added.

### Contract revision, 2026-08-07 (raised in step 03.2, applied back here)

`LauncherSignalSource` changed from `fun read(): List<LauncherSignal>` to `fun observe(): Flow<List<LauncherSignal>>`,
and `LauncherSignalRegistry` from a polling loop to a `combine` over the sources. Both step texts and their
predicates above were rewritten to the delivered shape; the two files were re-verified by `.\a.ps1 fk`
(BUILD SUCCESSFUL in 52s).

The synchronous getter was unimplementable for two of the three sources. Transfers and background work are
change-notified through WorkManager flows, so answering a synchronous `read()` would have required each
source to hold a collector open for the app's entire life - a live Room observer on the WorkManager database
whether or not the launcher is on screen - or to block on IO inside `read()`, which the interface's own KDoc
forbade. Only playback genuinely polls, because its `SharedPreferences` snapshot has no change notification,
and that loop now lives in that one source instead of being imposed on all three.

This phase's own boundary audit did not catch it: with no concrete source written yet, the contract looked
serviceable. Recorded here rather than quietly fixed, because the audit missing it is the finding.

The `P3` note above about two collectors running two polling loops is void with it - `combine` has one
upstream per source and the registry no longer polls at all.

---

## Step Log

- 2026-08-07 - Step 01.1 PASS. `LauncherSignal.kt` created (28 lines). Predicates: file exists; `data class LauncherSignal(` x1; `enum class LauncherSignalKind` x1; `Intent` 0 hits. The KDoc first said "carries no `Intent`", which tripped the zero-hit predicate on its own wording - reworded to "carries no navigation target" rather than weakening the predicate. Longest line 112 chars, under the detekt 120 ceiling.
- 2026-08-07 - Step 01.2 PASS. `LauncherSignalSource.kt` created (25 lines). Predicates: `interface LauncherSignalSource` x1; `fun read(): List<LauncherSignal>` present; `fun open(signal: LauncherSignal): Intent?` present. Longest line 106. Note: `post-change.ps1` releases CODE.LOCK on completion, so the lock taken for 01.1 was gone by 01.2 - re-acquired before 01.3.
- 2026-08-07 - Step 01.3 PASS. `LauncherSignalRegistry.kt` created (63 lines). Predicates: `class LauncherSignalRegistry` x1; `fun observe(): Flow<List<LauncherSignal>>` present; `REFRESH_INTERVAL_MS` present and `delay(2_000` 0 hits; `Log.d(` 0 hits; longest line 111; `.\a.ps1 fk` exit 0 (BUILD SUCCESSFUL in 59s). Deviation from the prompt, deliberate: the prompt said to concatenate sources "in a stable source order", but a Dagger `Set` multibinding guarantees no iteration order, so binding order would not have been stable. Row order now comes from `LauncherSignalKind` declaration order (`sortedWith(compareBy({ it.kind.ordinal }, { it.id }))`), which is a real contract. Step 03.4's prompt corrected to match.
- 2026-08-07 - Step 01.4 PASS. `di/LauncherSignalModule.kt` created (22 lines). Predicates: `@Multibinds` present; `abstract class LauncherSignalModule` x1; `.\a.ps1 dq` exit 0 - `hiltJavaCompileStandardDebug` ran and BUILD SUCCESSFUL in 1m 25s, which is the graph proof `fk` cannot give. The step's original predicate named `fk`; corrected to `dq` before executing, because `fk` stops at `compileStandardDebugKotlin` and a `MissingBinding` would have hidden behind it (S1170).

---

## Handoff Notes to Next Phase

`LauncherSignalRegistry.observe()` is the single question phase 02's host asks. No source is bound yet, so
the flow emits an empty list - phase 02 must render its constant-height empty state on exactly that.

---

## Rollback Plan

Revert phase commit(s) - four new files, no existing file modified, no user-facing surface changed.
