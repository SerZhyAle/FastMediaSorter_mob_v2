# Phase 02 - Inline-audio parameter objects

**Strategic spec:** [`../S1328_streaminlineaudiomanager-detekt-debt.md`](../S1328_streaminlineaudiomanager-detekt-debt.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-03
**Completed:** 2026-08-03

---

## Objective

Group `StreamInlineAudioManager`'s ten constructor parameters into two holder data classes, taking
the constructor to four parameters and clearing the `LongParameterList - 10/10` finding without a
`@Suppress`.

**Amended 2026-08-02.** The finding is no longer live - the 2026-08-02 baseline rewrite added
`LongParameterList:StreamInlineAudioManager.kt$StreamInlineAudioManager$( lifecycleOwner: .. )` at
`baseline-app_v2.xml:3484`. The refactor still goes ahead (owner decision), and it now carries one
extra obligation: **delete that entry**, because after the rewrite its signature names parameters
that no longer exist. A dead baseline entry is the exact debt S1334 built
`scripts/quality/audit-detekt-baseline-drift.ps1` to detect, and S1350 / S1351 both pruned theirs in
the same situation. Every other baseline entry stays untouched.

---

## Prerequisites

- [ ] Phase 01 Step 01.1 is ✅ Done - the import order must be correct before this phase adds imports
      to the same block, or the re-keyed `ImportOrdering` entry surfaces a real finding here. Step
      01.2 is optional and does not gate this phase.
- [x] Holder shape confirmed in strategic §3.3 (both holders -> 4 parameters) - 2026-08-02.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1328 phase 02"`.
- [ ] `StreamsActivity.kt` (1205 lines) backed up to `temp/S1328/` if Phase 01's backup was not kept.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioDependencies.kt` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt` | Modified | ≤ 420 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 1215 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStreamsInlineAudioManager.kt` | Modified | ≤ 100 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern).

---

## Steps

### Step 02.1 - Add the two holder data classes

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioDependencies.kt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Create `StreamInlineAudioDependencies.kt` holding two `data class` declarations and nothing else,
> mirroring the file-of-holders shape already used by
> `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerDependencies.kt`.
> `StreamInlineAudioViews` carries `miniControl: View`, `titleView: TextView`,
> `playStopButton: ImageButton` - no defaults. `StreamInlineAudioCallbacks` carries
> `onPlayingChanged: (String?) -> Unit`, `onPlaybackStateChanged: (StreamSourceEntity?, Boolean) -> Unit`,
> `onError: (StreamSourceEntity) -> Unit`, `onSuccess: (StreamSourceEntity) -> Unit` and
> `onNowPlayingChanged: (playingId: String?, track: String?) -> Unit`, and **every** callback field
> gets a no-op default so a host that needs a subset omits the rest. Move the existing WHY comments
> for `onError`, `onSuccess` and `onNowPlayingChanged` verbatim from the manager's constructor onto
> the matching holder fields - they record non-obvious history (the
> `UninitializedPropertyAccessException` note in particular) and must not be lost or reworded. Add no
> comment that merely restates a field name. Both holders must stay `data class`: detekt's
> `LongParameterList` ignores data classes by default, which is why the 10-field
> `VideoPlayerHostDependencies` sits at the same threshold with no finding and no baseline entry.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioDependencies.kt` exists.
- `Grep` - `data class StreamInlineAudioViews` matches exactly once in that file.
- `Grep` - `data class StreamInlineAudioCallbacks` matches exactly once in that file.
- `Grep` - `class ` matches exactly twice in that file (the two data classes, no third type).
- `Grep` - `UninitializedPropertyAccess` matches at least once in that file (the migrated comment).
- `Grep` - `@Suppress` returns zero hits in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 6/6 PASS. File created at 38 lines against a budget of 60; two `data class` declarations and nothing else (`class ` count 2); the `onError`, `onSuccess` and `onNowPlayingChanged` comments moved verbatim, `UninitializedPropertyAccess` included; no `@Suppress`. Every callback field defaults to a no-op, `onPlayingChanged` included - it had no default on the old constructor.

---

### Step 02.2 - Swap the constructor and both call sites

**Files:** `.../helpers/StreamInlineAudioManager.kt`, `.../ui/streams/StreamsActivity.kt`, `.../ui/main/helpers/MainStreamsInlineAudioManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> One atomic edit across three files - the signature change and both call sites must land together or
> the module does not compile.
>
> In `StreamInlineAudioManager`, replace the ten constructor parameters with four:
> `lifecycleOwner: LifecycleOwner` (still not a `val`), `private val views: StreamInlineAudioViews`,
> `private val audioController: AudioServiceController`, and
> `private val callbacks: StreamInlineAudioCallbacks = StreamInlineAudioCallbacks()`. Keep that
> parameter order. Rewrite every body reference: `miniControl` -> `views.miniControl` (10 sites,
> including the `.context` reads that build the local ExoPlayer and the `R.string.streams_no_signal`
> lookup), `titleView` -> `views.titleView` (1 site), `playStopButton` -> `views.playStopButton`
> (1 site), and each of the five callbacks -> `callbacks.<name>` (3 / 3 / 2 / 1 / 1 sites in
> declaration order). Keep every resulting line under 120 characters and do not re-wrap lines that
> already fit.
>
> **Name collision - read before renaming `onPlaybackStateChanged`.** That identifier means two
> different things in this file. The constructor callback takes `(StreamSourceEntity?, Boolean)`; the
> `Player.Listener` override inside the anonymous `playerListener` object takes `(Int)`. Only the
> three two-argument call sites become `callbacks.onPlaybackStateChanged` - the `override fun
> onPlaybackStateChanged(playbackState: Int)` declaration and its `when` body stay exactly as they
> are. A blind find-and-replace breaks the listener contract, and the qualified form is what makes
> the two readable apart afterwards.
>
> In `StreamsActivity.setupViews()`, pass `views = StreamInlineAudioViews(..)` built from
> `binding.streamMiniControl` / `binding.tvMiniTitle` / `binding.btnMiniPlayStop`, and
> `callbacks = StreamInlineAudioCallbacks(..)` carrying all five existing lambdas unchanged. Keep the
> lambdas inline - do not promote any of them to a named private function. The original reason (the
> class reports `TooManyFunctions 42/40`, and a declaration pushes it to 43) no longer bites, because
> that finding is baselined on a class-level signature that a function count does not re-key. The
> instruction stands anyway: the class is already over the threshold, and S1198 owns bringing it back
> under - do not make its job larger from here.
>
> The two new imports this file needs (`StreamInlineAudioViews`, `StreamInlineAudioCallbacks`) go in
> the same `com.sza.fastmediasorter.ui.streams.helpers.*` run as `StreamInlineAudioManager`, in
> case-sensitive ordinal order. Adding them re-keys the baselined `ImportOrdering` entry, which is
> exactly why Phase 01 Step 01.1 runs first - verify the ordering with a gated detekt run, never with
> a PowerShell sort.
>
> In `MainStreamsInlineAudioManager`, pass the same two holders, but supply only `onPlayingChanged`
> and `onError` inside `StreamInlineAudioCallbacks` - the other three fields keep their defaults,
> exactly as the three omitted arguments do today.
>
> Do not add `@Suppress` anywhere: a suppression on a member that already carries a baselined finding
> re-keys the baseline signature and surfaces fresh findings (CLAUDE.md Rule 19).
>
> Touch `config/detekt/baseline-app_v2.xml` for exactly one thing - delete the single line
> `<ID>LongParameterList:StreamInlineAudioManager.kt$StreamInlineAudioManager$( lifecycleOwner: .. )</ID>`
> (line 3484 as of 2026-08-02), which is dead once the constructor has four parameters. Delete that
> one line by hand; do not run a baseline regeneration to achieve it. A regeneration rewrites the
> whole file and absorbs every other ticket's in-flight debt - that is precisely what happened on
> 2026-08-02 and what S1356 exists to deal with.

**Verification:**

- `Grep` - `private val miniControl`, `private val titleView`, `private val playStopButton` each return zero hits in `StreamInlineAudioManager.kt`.
- `Grep` - `private val views: StreamInlineAudioViews` and `private val callbacks: StreamInlineAudioCallbacks` each match exactly once in `StreamInlineAudioManager.kt`.
- `Grep` - `StreamInlineAudioViews(` matches exactly three times across `app_v2/src/main`: one per call site plus the `data class` declaration line itself, which the predicate as first written did not count.
- `Grep` - `StreamInlineAudioCallbacks(` matches exactly four times across `app_v2/src/main`: the default in the manager signature, one per call site, and the `data class` declaration line - same off-by-one as above.
- `Grep` - in `MainStreamsInlineAudioManager.kt`, `onPlaybackStateChanged`, `onSuccess` and `onNowPlayingChanged` each return zero hits (still defaulted).
- `Grep` - `override fun onPlaybackStateChanged(playbackState: Int)` still matches exactly once in `StreamInlineAudioManager.kt` (the `Player.Listener` override survived the rename).
- `Grep` - `callbacks.onPlaybackStateChanged` matches exactly three times in `StreamInlineAudioManager.kt`.
- `Grep` - `@Suppress` returns zero hits across all four files of this phase.
- `Grep` - `Timber.d("S1142:` matches exactly twice, `Timber.d("S1219:` exactly once, and `Timber.d("S0896:` exactly once in `StreamInlineAudioManager.kt`. All three tickets are `BlockNeedUserTest`; the S1219 probe reads `views.miniControl.width` after the rename.
- Value equality - `config/detekt/baseline-app_v2.xml` line count is **12285** (one less than the 12286 it stands at before this step), and `StreamInlineAudioManager.kt$` matches exactly **3** times in it - the three `SpacingBetweenDeclarationsWithComments` entries, with the `LongParameterList` entry gone. The pre-edit values are 12286 and 4.
- `Grep` - `Log\.d\(` returns zero hits in all four files.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 10/10 PASS. Constructor now `lifecycleOwner`, `views`, `audioController`, `callbacks` - the three old `private val` view properties return zero hits. Body rewritten: 10 `views.miniControl`, 1 `views.titleView`, 1 `views.playStopButton`, and 3/3/2/1/1 qualified callback sites. The `Player.Listener` `override fun onPlaybackStateChanged(playbackState: Int)` survived untouched while the three two-argument sites became `callbacks.onPlaybackStateChanged`. All four inherited probes intact (`S1142` x2, `S1219`, `S0896`), the `S1219` one now reading `views.miniControl.width`. Zero `@Suppress`, zero `Log.d(`. Baseline: exactly one line deleted by hand, 12286 -> 12285, `StreamInlineAudioManager.kt$` entries 4 -> 3; no regeneration was run. `a.ps1 fk` exit 0.
- 2026-08-03 - Two plan corrections, both mechanical. **(1)** The `StreamInlineAudioViews(` / `StreamInlineAudioCallbacks(` count predicates were off by one each: they did not count the `data class X(` declaration line in the new holder file. Predicates updated, intent unchanged - two call sites for the views, one default plus two call sites for the callbacks. **(2)** `android.view.View`, `android.widget.TextView` and `android.widget.ImageButton` became unused in `StreamInlineAudioManager.kt` once the view handles moved into the holder, so they were removed with the change (CLAUDE.md Rule 20); the step did not name them because it did not anticipate the imports going dead.

---

### Step 02.3 - Add the S1328 device-verification probe

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/helpers/StreamInlineAudioManager.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> The ticket enters `BlockNeedUserTest`, so add exactly one `Timber.d("S1328: ..")` probe at the entry
> point of the reworked flow - inside `play(..)`, reporting which holder-supplied views and callback
> set are in use. One tag for the changed flow, not one per renamed reference. Keep the line under
> 120 characters. Do not reuse the `S1328:` prefix in any `Timber.i/w/e` or other persisted message.

**Verification:**

- `Grep` - `Timber.d("S1328:` matches exactly once in `StreamInlineAudioManager.kt`.
- `Grep` - `S1328` returns zero hits at `Timber.i(`, `Timber.w(` or `Timber.e(` call sites anywhere in `app_v2/src`.
- `Grep` - the matched probe line is at most 120 characters.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 3/3 PASS. One `Timber.d("S1328:` at the head of `play(..)`, 99 characters, reporting the holder-supplied view id and the background-service branch. Zero `S1328` occurrences at `Timber.i/w/e` call sites anywhere in `app_v2/src`.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `pwsh -NoProfile -File ./a.ps1 fk` exit 0, re-run after the probe insertion.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] `assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles` over all four files exits 0, `PASS (no new findings; baselines hold)`. The report file on disk was not read.
- [x] `config/detekt/baseline-app_v2.xml` differs by exactly one deleted line - 12286 -> 12285, `StreamInlineAudioManager.kt$` entries 4 -> 3, no regeneration.
- [x] `scripts/quality/audit-detekt-baseline-drift.ps1` exit 0 and zero lines mentioning `StreamInlineAudio` - no dead entry for this file. (It does report 1882 dead entries repo-wide, the residue of the 2026-08-02 regeneration; that is S1356's ground, not this ticket's.)
- [x] One dev-log entry for the logical change, via `post-change.ps1` (`post-change: PASS (Mixed)`).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated by the same closure run.
- [x] Phase-boundary audit run - see below, no P0/P1. Listener symmetry gate: `new imbalance 0`.

## Phase-boundary audit - 2026-08-03

Layers 1-3 over the four files.

- **Layer 3, listener ownership - clean, and checked by hand as the criterion demands.** Neither
  `playerListener` nor `underrunListener` is touched by the rename: both are still `object`
  declarations held in `private val` fields, `addListener`/`removeListener` still pair inside
  `play(..)` / `stopPlaybackKeepingController()` / `releaseKeepingBackgroundService()`, and
  `addAnalyticsListener`/`removeAnalyticsListener` still pair around the local player's lifetime. The
  one listener registration that did move - `playStopButton.setOnClickListener` -> 
  `views.playStopButton.setOnClickListener` - is a view-scoped click listener with no removal
  counterpart before or after. The mechanical gate agrees: `new imbalance 0`.
- **Layer 2, lifecycle and concurrency - clean.** `lifecycleOwner` stays a non-`val` parameter used
  only inside `init` for `collectOnLifecycle`, so the manager still holds no reference to it. No
  coroutine, dispatcher or handler usage changed; `recoveryHandler` and its two runnables are
  untouched.
- **Layer 1, architecture - clean.** The holders live beside the manager in `ui/streams/helpers`,
  matching `ui/player/VideoPlayerDependencies.kt`. Both are data types, which is what keeps
  `LongParameterList` off them at five fields. The manager is 393 lines, `StreamsActivity` 1212, both
  inside CLAUDE.md Rule 2. No flavor guard, no new dependency, no behaviour change: every callback
  fires from exactly the site it fired from before, only qualified.
- **One risk considered and dismissed.** `onPlayingChanged` gained a no-op default it never had, so a
  future caller could silently omit it and lose the playing-id propagation. Both current call sites
  pass it explicitly, and the alternative - one holder field without a default - would break the
  second host, which deliberately omits three of five. Recorded here rather than guarded.

---

## Handoff Notes to Next Phase

The constructor is public API consumed from two packages (`ui.streams`, `ui.main.helpers`), so the
class catalog needs a regen and the new file needs its `role` / `status` filled in. Five probe lines
now live in `StreamInlineAudioManager.kt` - four inherited from three `BlockNeedUserTest` tickets
(S1142 twice, S1219, S0896) plus the new S1328 one.

---

## Rollback Plan

Revert the phase commit - no data migration and no user-facing surface changed. The holder file is
new, so a revert removes it wholesale.
