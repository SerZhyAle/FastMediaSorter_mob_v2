# Phase 03 - Base-activity host and Browse opt-out

**Strategic spec:** [`../S1398_hairline-background-progress-bar.md`](../S1398_hairline-background-progress-bar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-10
**Completed:** 2026-08-10

---

## Objective

Attach the bar over the content root of every Activity through the shared base class, collect the observer into it, and have the Browse screen opt out explicitly.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] Timestamped backups of `BaseActivity.kt` and `BrowseActivity.kt` taken in `temp/S1398/` - both exceed 500 LOC (CLAUDE.md Rule 5).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationBarAttachManager.kt` | New | ≤ 110 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` | Modified (622 LOC - backup first) | ≤ 660 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` | Modified (848 LOC - backup first) | ≤ 860 |

> No `res/layout*` file is touched: the bar is added programmatically to `android.R.id.content`, so no landscape counterpart exists to mirror under CLAUDE.md Rule 11. Landscape is still verified on device in step 03.4.

---

## Steps

### Step 03.1 - Back up the two large files

**Files:** `temp/S1398/`
**Depends on:** - start of phase

**Prompt for developer:**

> Copy `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt` and `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt` into `temp/S1398/` with a timestamp in each filename, before any edit in this phase.

**Why:**

CLAUDE.md Rule 5 requires a timestamped backup before editing any file over 500 LOC, and both files are well past it at 622 and 848 lines.

**Verification:**

- `Glob` - `temp/S1398/BaseActivity*.kt` matches at least one file.
- `Glob` - `temp/S1398/BrowseActivity*.kt` matches at least one file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-10 - Verification 2/2 PASS. `temp/S1398/BaseActivity.20260810-140800.kt` (30049 bytes) and `temp/S1398/BrowseActivity.20260810-140800.kt` (44380 bytes) written before any edit in this phase.

---

### Step 03.2 - Add the attach manager

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationBarAttachManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add `BackgroundOperationBarAttachManager` with a constructor taking the host `AppCompatActivity` and a `BackgroundOperationTrackManager`. Give it one public method, `fun attach()`, which:
>
> - resolves the content root with `activity.findViewById<FrameLayout>(android.R.id.content)` and returns early when it is null;
> - creates a `BackgroundOperationBarView`, calls `render(BackgroundOperationBarState.Hidden)` on it, and adds it with `FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM)`;
> - collects `trackManager.barState()` with `activity.collectOnLifecycle { .. }` and passes each value to `view.render(it)`.
>
> Follow the content-root attachment already used by `ui/main/helpers/VersionOverlayManager.kt`. Do not apply any window-inset padding to the view and do not consume insets - the bar sits at the physical bottom of the window on purpose. Do not add a touch listener, a click listener or a focus change listener of any kind.

**Why:**

Strategic §6.3 puts the bar on every screen, and §4 established that the 40+ Activities share no root layout, so an overlay over the content root is the only attachment point that needs no per-screen layout edit - which is also what keeps goal §2.2, no content shift, true. Deliberately skipping inset handling implements §6.6 and ADR-3: the bar goes under the translucent system bar and never becomes a second owner of the bottom inset, which is the failure the strategic §7 risk table calls the highest-probability one.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/backgroundop/BackgroundOperationBarAttachManager.kt` exists.
- `Grep` - `class BackgroundOperationBarAttachManager` matches exactly once.
- `Grep` - `android.R.id.content` and `Gravity.BOTTOM` each present.
- `Grep` - `collectOnLifecycle` present; `lifecycleScope.launch` returns zero hits in the file.
- `Grep` - `WindowInsets`, `setOnClickListener`, `setOnTouchListener` each return zero hits in the file.
- `Grep` - `Log\.d\(` returns zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-08-10 - Verification 10/10 PASS. Files: ui/common/backgroundop/BackgroundOperationBarAttachManager.kt (New, 37 LOC). Collects through `LifecycleOwner.collectOnLifecycle` (STARTED), so the bar stops being fed the moment the Activity stops. No inset handling, no click or touch listener.

---

### Step 03.3 - Wire the host into BaseActivity with an opt-out, and opt Browse out

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/ui/BaseActivity.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/BrowseActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> In `BaseActivity`, field-inject the track manager next to the existing `tvKeyRouter` injection, and add `protected open fun showsBackgroundOperationBar(): Boolean = true`, mirroring the shape of the existing `shouldEnableEdgeToEdge()` override point. Inside the existing `binding.root.post { .. }` block, after `observeData()`, call `BackgroundOperationBarAttachManager(this, trackManager).attach()` when `showsBackgroundOperationBar()` returns true. Attaching there rather than straight after `setContentView` keeps the first frame free of the extra view, matching the deferral the block already exists for.
>
> In `BrowseActivity`, override `showsBackgroundOperationBar()` to return `false`, with a one-line comment naming S1398 §6.2 as the reason.

**Why:**

Strategic §6.3 and ADR-1 put the host in the shared base class so no screen can be forgotten, and §6.2 keeps the bar off the Browse screen because the S1227 text strip already fills that role there with text, a percent and a tap target. ADR-2 requires that exclusion to be a visible override in `BrowseActivity` rather than a condition hidden inside the host, so the rule reads from the place where it applies.

**Verification:**

- `Grep` - `showsBackgroundOperationBar` is declared once in `BaseActivity.kt` and called once there, and overridden once in `BrowseActivity.kt`. (Corrected during execution: the predicate originally demanded exactly one hit in `BaseActivity.kt`, which the hook cannot satisfy - a hook has both a declaration and a call site.)
- `Grep` - `BackgroundOperationBarAttachManager` present in `BaseActivity.kt`.
- `Grep` - `override fun showsBackgroundOperationBar(): Boolean = false` present in `BrowseActivity.kt`.
- `Grep` - `tvTransferIndicator` hit count in `BrowseActivity.kt` and in `ui/browse/managers/BrowseEdgeToEdgeHelper.kt` is unchanged from before this phase.
- `Grep` - `Log\.d\(` returns zero hits in both modified files.
- `.\a.ps1 fk` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-08-10 - Verification 6/6 PASS. Files: core/ui/BaseActivity.kt (+13 LOC: field injection, the `showsBackgroundOperationBar()` hook, the guarded attach inside the existing `binding.root.post` block, two imports), ui/browse/BrowseActivity.kt (+5 LOC: the override with its S1398 §6.2 reason). `tvTransferIndicator` untouched - 0 hits in `BrowseActivity.kt` (it is wired in `BrowseManagerInitializer`) and 3 in `BrowseEdgeToEdgeHelper.kt`, whose strip list was not edited. Seven lines over 120 chars exist across the two files; all seven are pre-existing (Timber calls, a section separator, two prior `@Inject` declarations, a Toast, `createIntent`) and none was added here.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fc` exit 0, then `.\a.ps1 dq` exit 0. The full debug build was run deliberately rather than resting on `fc`: `fc` compiles Kotlin only and does not exercise the Dagger processor, so the new `BaseActivity` field injection would have compiled while the graph stayed unproven. `hiltJavaCompileStandardDebug` ran and passed, and the APK packaged. This single build validated the implementation and the debug tags together, as intended.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added via `post-change.ps1`. First attempt exited 1 on `ticket-log-audit`: the three `S1398:` probe tags are legal only while the ticket sits in `BlockNeedUserTest`, and it was still `In Progress`. The status was flipped first, then the closure re-run - the order the gate requires, not a weakening of it.
- [x] Phase-boundary audit run - no P0/P1 findings. Layer 1: naming and delegation hold; `BaseActivity` gained 13 lines and stays far under the split threshold. Layer 2 (lifecycle/coroutine): collection goes through `collectOnLifecycle` at STARTED, so it stops with the Activity; the attach runs inside the existing deferred `binding.root.post` block, which already guards against a destroyed Activity. Layer 3 (listener/memory ownership): the bar view is added to the per-instance content root and dies with the Activity; no listener is registered, so there is none to unregister, and the manager holds no static reference. Layer 4 not applicable.
- [x] UI-phase screenshot gate (S1338): placement decision recorded - strategic §3.3 with `Owner sign-off: 2026-08-10`. Screenshot deferred (no device): `device-ready.ps1` reports `ready:false`, `state:no-device`. The shot is owed by the device test this ticket is now blocked on, and the matrix for it is in the `BlockNeedUserTest` status note.

---

## Handoff Notes to Next Phase

Every Activity except Browse now hosts the bar. Any screen added later inherits it with no work; a screen that must not show it overrides `showsBackgroundOperationBar()`. The bottom-inset arbiter `BrowseEdgeToEdgeHelper` was deliberately left untouched, and its strip list must stay that way.

**Device-test matrix, carried to the `BlockNeedUserTest` gate.** Criteria §11.1, §11.2, §11.4 and §11.7 are claims about rendering and touch on a real window, so they are not statically verifiable and are deliberately not plan steps. The gate must cover: a background copy of several large files started from Browse and sent to the background; the bar visible, advancing and then vanishing on the main screen, a player and settings; the Browse screen showing the S1227 text strip and no bar, with the strip's tap still restoring the dialog; both orientations; both system navigation schemes; and a control at the bottom edge of a screen still responding to a tap while the bar is visible. Set the animator duration scale to 1 before judging the indeterminate state - emulators commonly run at scale 0, where an indeterminate indicator renders static and reads as a defect that is not there.

---

## Rollback Plan

Revert phase commit(s) and restore `BaseActivity.kt` / `BrowseActivity.kt` from the `temp/S1398/` backups taken in step 03.1. No data migration, no persisted state, no string removal.
