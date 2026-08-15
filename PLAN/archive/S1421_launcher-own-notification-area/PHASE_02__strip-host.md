# Phase 02 - Strip host

**Strategic spec:** [`../S1421_launcher-own-notification-area.md`](../S1421_launcher-own-notification-area.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 5 / 5
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Create the top strip the launcher desktop currently lacks: one container of constant height, inside the
safe area in both orientations, shown only while the launcher replaces the system status area, and owned
by a single manager that decides what occupies it.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Strategic §4.5 read - there is no existing top-strip view or class to extend; this phase creates one.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/res/layout/launcher_status_strip.xml` | New | ≤ 60 |
| `app_v2/src/launcherEnabled/res/layout/activity_launcher_home.xml` | Modified | ≤ 90 |
| `app_v2/src/launcherEnabled/res/layout-land/activity_launcher_home.xml` | Modified | ≤ 90 |
| `app_v2/src/launcherEnabled/res/values/dimens.xml` | Modified | ≤ 40 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt` | New | ≤ 220 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | ≤ 940 |

> `launcher_status_strip.xml` has no `layout-land` counterpart on purpose: `launcher_taskbar.xml` already
> ships as one file included from both orientation trees, and the strip follows that precedent. Rule 11
> still binds `activity_launcher_home.xml`, whose land twin exists and is edited in the same step.
>
> `LauncherHomeActivity.kt` is 904 lines - over the 500-line backup threshold. Step 02.5 carries the
> backup sub-step (CLAUDE.md Rule 5) and must keep the file under the 1500-line ceiling.

---

## Steps

### Step 02.1 - Add the strip layout and its height dimen

**Files:** `app_v2/src/launcherEnabled/res/layout/launcher_status_strip.xml`, `app_v2/src/launcherEnabled/res/values/dimens.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `launcher_status_strip.xml` as a `FrameLayout` with `android:id="@+id/launcherStatusStrip"`,
> `layout_width="match_parent"`, `layout_height="@dimen/launcher_status_strip_height"` and
> `android:visibility="gone"`. Give it one child `FrameLayout` with id `launcherStatusStripContent`, which
> is the slot later phases fill - leave it empty here. Add `<dimen name="launcher_status_strip_height">28dp</dimen>`
> to `dimens.xml` next to `launcher_taskbar_height`. Use no hardcoded `="#hex"` colour anywhere in the file
> - the strip draws on the wallpaper and needs no background of its own; if one is ever needed it comes
> from `?attr/`.

**Why:**

Strategic §4.4 rules that the strip keeps a constant height and neither collapses nor sits empty, so the
height has to be a resource the content cannot influence rather than a `wrap_content` that shrinks the
moment a signal disappears.

**Verification:**

- `Glob` - `app_v2/src/launcherEnabled/res/layout/launcher_status_strip.xml` exists.
- `Grep` - `launcher_status_strip_height` present in `app_v2/src/launcherEnabled/res/values/dimens.xml`.
- `Grep -n "=\"#"` returns zero hits in `launcher_status_strip.xml`.

**Status:** `[x]` done

---

### Step 02.2 - Mount the strip in both orientation layouts

**Files:** `app_v2/src/launcherEnabled/res/layout/activity_launcher_home.xml`, `app_v2/src/launcherEnabled/res/layout-land/activity_launcher_home.xml`
**Depends on:** Step 02.1

**Prompt for developer:**

> In both files add, above the `launcherGridScroll` block, an `<include android:id="@+id/launcherStatusStrip"
> layout="@layout/launcher_status_strip" android:layout_width="0dp" android:layout_height="@dimen/launcher_status_strip_height" />`
> constrained to `parent` start, end and top. Then re-anchor `launcherGridScroll`:
> `app:layout_constraintTop_toBottomOf="@+id/launcherStatusStrip"` instead of `toTopOf="parent"`. Keep both
> files identical in ids and constraint names - one `ViewBinding` serves both, so an id present in one and
> absent in the other is a null field at runtime. Do not change the wallpaper layers or the taskbar
> include.

**Why:**

Strategic §1 states the freed band is what this ticket fills, and the desktop scroll currently anchors to
the parent top - without re-anchoring, any strip drawn there would sit under the grid rather than above it.

**Verification:**

- `Grep` - `launcherStatusStrip` present in both `res/layout/activity_launcher_home.xml` and `res/layout-land/activity_launcher_home.xml`.
- `Grep` - `layout_constraintTop_toBottomOf="@+id/launcherStatusStrip"` present in both files.
- `Grep -c "layout_constraintTop_toTopOf=\"parent\""` on `launcherGridScroll`'s block returns zero.
- `.\a.ps1 fr` exits 0.

**Status:** `[x]` done

---

### Step 02.3 - Add the decisive node

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt`
**Depends on:** Step 02.2, Phase 01

**Prompt for developer:**

> Create `class LauncherStatusStripManager @Inject constructor(private val signalRegistry: LauncherSignalRegistry)`
> in the `helpers` package, modelled on `LauncherTrayManager`'s shape: a `bind(binding, lifecycleOwner, replaceSystemStatusArea: Flow<Boolean>)`
> entry point, an `apply()` that sets visibility, and no direct Android framework work outside the view it
> owns. Its whole job is the ADR-2 decision, and it is the only class allowed to set
> `launcherStatusStrip.visibility` or to add a child to `launcherStatusStripContent`:
>
> - The strip is `VISIBLE` when and only when `replaceSystemStatusArea` is true; otherwise `GONE`.
> - While visible, collect `signalRegistry.observe()` with `collectOnLifecycle` (never a bare
>   `lifecycleScope.launch { collect { } }`) and expose the current list through
>   `val signals: StateFlow<List<LauncherSignal>>` for phase 04 to render.
> - Content selection is a single `when`: a non-empty list means the signal row occupies the slot; an empty
>   list means the empty slot stays as it is. Phase 04 supplies the row; leave the empty branch a no-op
>   with a comment naming strategic §5.2 as the open decision, and do not invent placeholder content.
>
> The manager keeps a nullable binding reference and clears it in an `unbind()` the activity calls from
> `onDestroy`, so the strip does not outlive the surface.

**Why:**

Strategic ADR-2 requires a single node to own the area, because two tickets writing into one strip
independently make the applied order accidental and push the conflict into a third ticket.

**Verification:**

- `Grep` - `class LauncherStatusStripManager` matches exactly once.
- `Grep` - `collectOnLifecycle` present; `Grep -n "lifecycleScope.launch"` returns zero hits in the file.
- `Grep` - `fun unbind` present.
- `Grep -rn "launcherStatusStrip"` across `app_v2/src/launcherEnabled/java` matches only this manager - the
  two layout files and this class are the whole surface, nothing else may reach the strip.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 02.4 - Keep the strip inside the safe area

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> In `bind()`, apply the safe-area padding to the strip with `applySystemBarInsetPadding` from
> `utils/ViewExtensions.kt`, passing `applyBottom = false` and `useStatusBarHeightFallback = false` - the
> launcher controls the bar's visibility itself, which is exactly the case that KDoc names. Call it once
> per bound view, per the same KDoc, and request a fresh dispatch with `ViewCompat.requestApplyInsets` when
> `replaceSystemStatusArea` changes rather than calling the helper again. Store the left and right cutout
> insets from the helper's `onApplied` callback into `val cutoutInsets: StateFlow<Rect>` for phase 04 to
> split the row on.

**Why:**

Strategic §3.1 binds this strip to Rule 17 in both orientations, and §6 records that the cutout is not the
only obstruction - landscape system bars narrow the same strip, so the gap has to come from measured insets
rather than a device-specific constant.

**Verification:**

- `Grep` - `applySystemBarInsetPadding` present exactly once in the file.
- `Grep` - `useStatusBarHeightFallback = false` present.
- `Grep` - `cutoutBounds` declared as a `StateFlow<Rect>`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 02.5 - Bind the manager from the activity

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Back the file up to `temp/S1421/` first - it is 904 lines, over the Rule 5 threshold. Inject
> `LauncherStatusStripManager`, call its `bind()` next to the existing `launcherTrayManager.bind(..)` call
> and pass the same `replaceSystemStatusArea` flow the activity already collects for
> `applyStatusBarPolicy`. Call `unbind()` from `onDestroy`. Add no logic beyond the two calls - the
> decision belongs to the manager (Rule 3), and the activity must stay under 1500 lines.

**Why:**

Strategic §3.1 places this surface on the launcher desktop, and the desktop's only host is
`LauncherHomeActivity` - without a bind call the manager built in the previous steps is never attached to
a view.

**Verification:**

- `Glob` - a timestamped backup of `LauncherHomeActivity.kt` exists under `temp/S1421/`.
- `Grep` - `statusStripManager.bind(` present exactly once.
- `Grep` - `statusStripManager.unbind()` present exactly once.
- `(Get-Content LauncherHomeActivity.kt | Measure-Object -Line).Lines` is below 1500.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, BUILD SUCCESSFUL.
- [x] `.\a.ps1 fkn` exits 0 - BUILD SUCCESSFUL in 49s.
- [x] `Grep` for `TODO(phase-02)` returns zero hits (expected: 0 | actual: 0).
- [x] `Grep -n "Log\.d\("` returns zero hits in every file this phase touched - `post-change`'s
      `nontimber-log` dimension reported 0 new occurrences on each.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13). See audit note below.

---

## Phase-boundary audit (2026-08-07)

Layers 1-3 of `docs/CODE_AUDIT_PROTOCOL.md`; Layer 4 has no Room surface.

- Layer 1 - PASS. The activity gained one injected field, one `bind` call and an `onDestroy` carrying only
  `unbind()`, so no logic moved into it (Rule 3); it is 944 lines, below the ceiling. Both orientation
  layouts were edited together (Rule 11). Everything sits in `src/launcherEnabled`, no `BuildConfig` guard.
- Layer 2 - PASS. Both collectors go through `collectOnLifecycle`, so the 2 s signal poll stops whenever the
  launcher is not the started surface rather than running behind another app.
- Layer 3 - PASS. The manager is unscoped, so a fresh instance is created per window and dies with it; the
  two inset listeners are owned by views that die with the same window, and the view-to-manager direction is
  the only strong reference. `unbind()` drops the manager-to-view direction on destroy. The
  `listener-symmetry` gate reported 0 new imbalance.
- P3 - a `GONE` `<include>` resolves to a zero-height point at its own anchor, so the desktop grid returns to
  the parent top when the strip is off. Correct by ConstraintLayout's rules and unchanged from the previous
  layout, but it is a layout behaviour rather than an asserted one - worth an eye on the device test.

---

## Step Log

- 2026-08-07 - Step 02.1 PASS. `launcher_status_strip.xml` created (19 lines) and `launcher_status_strip_height` = 28dp added to `launcherEnabled/res/values/dimens.xml`. Predicates: layout exists; `="#` 0 hits; dimen present. Height chosen as the 24dp status bar it replaces plus room for a chip's touch feedback, and commented as such.
- 2026-08-07 - Step 02.2 PASS. Strip included at the top of both `res/layout/activity_launcher_home.xml` and `res/layout-land/activity_launcher_home.xml`, with `launcherGridScroll` re-anchored to its bottom in each. Predicates: `launcherStatusStrip` present in both (2 hits each - the include id and the grid's anchor); `layout_constraintTop_toBottomOf="@+id/launcherStatusStrip"` present in both; the grid's old `layout_constraintTop_toTopOf="parent"` gone from both (expected: 0 | actual: 0); `.\a.ps1 fr` exit 0, BUILD SUCCESSFUL in 8s.
- 2026-08-07 - Step 02.3 PASS. `LauncherStatusStripManager.kt` created (61 lines). Predicates: `class LauncherStatusStripManager` x1; `collectOnLifecycle` present (3 hits) and `lifecycleScope.launch` 0 hits; `fun unbind` present; `launcherStatusStrip*` referenced from no other `.kt` under `src/launcherEnabled/java`; `.\a.ps1 fk` exit 0, BUILD SUCCESSFUL in 24s. Two deviations from the prompt, both deliberate: (1) the root of `launcher_status_strip.xml` lost the `launcherStatusStrip` id it was given in 02.1 - the `<include>` already supplies that id, and carrying it twice would name one view twice in a single ViewBinding; (2) the prompt's `apply()` with a no-op empty branch was dropped, because both of its branches would have done nothing at this phase and a function that does nothing in either branch is the scaffolding Rule 19 refuses. The §5.2 comment it was to carry now sits on the class KDoc, where it still records the open decision. The prompt's `launcherStatusStrip.visibility` predicate was likewise corrected: the manager sets `binding.root.isVisible`, so the invariant is written as "no other `.kt` reaches the strip".
- 2026-08-07 - Step 02.4 PASS. Safe-area padding and cutout observation added to `LauncherStatusStripManager` (now 127 lines, longest 110). Predicates: `applySystemBarInsetPadding(` x1; `useStatusBarHeightFallback = false` present; `cutoutBounds` declared `StateFlow<Rect>`; `.\a.ps1 fk` exit 0. Three deviations, each with a reason:
  - `applyTop = false` added to the helper call. Padding the top by the cutout height would push a 28dp band out of the very space it exists to occupy - ADR-3 wants the row **beside** the cutout, not below it.
  - The cutout span comes from `DisplayCutoutCompat.getBoundingRects()`, not from `displayCutout()` insets and not from `boundingRectTop`. First attempt used `boundingRectTop` and failed to compile; `javap` on `core-1.9.0.aar` shows `DisplayCutoutCompat` exposes `getSafeInset*`, `getBoundingRects` and `getWaterfallInsets` only - there is no `boundingRectTop` on the compat type. The insets route is wrong for a different reason: a camera island in the middle of the top edge yields a top inset and no horizontal one, so it cannot say where the hole is. Top-edge rects are filtered and unioned, which also covers dual punch-holes.
  - The flow is named `cutoutBounds` (a `Rect`), not `cutoutInsets`. Phase 04's step 04.2 updated to take the rect rather than a scalar gap - see its own note.
  - The cutout listener is installed on `launcherStatusStripContent`, not on the root: `applySystemBarInsetPadding` already owns the root's listener and `setOnApplyWindowInsetsListener` replaces rather than adds. The helper passes insets through unconsumed, so both views still receive the dispatch.
- 2026-08-07 - Step 02.5 PASS. `LauncherHomeActivity.kt` backed up to `temp/S1421/LauncherHomeActivity.kt.20260807_215347.bak` before editing (904 lines, over the Rule 5 threshold), then given an `@Inject lateinit var statusStripManager`, one `bind(..)` call beside the existing tray bind, and a new `onDestroy` calling `unbind()`. Predicates: backup exists; `statusStripManager.bind(` x1; `statusStripManager.unbind()` x1; file now 944 lines, below the 1500 ceiling; `.\a.ps1 dq` exit 0, BUILD SUCCESSFUL. The activity had no `onDestroy` override before this step, so one was added carrying nothing but the unbind (Rule 3). The first draft of its comment claimed the manager is app-scoped; it is unscoped, so the comment was corrected before the build rather than shipping a wrong reason.
- 2026-08-07 - UI refusal gate (S1338): placement decision is recorded - owner ruling quoted verbatim in strategic §4.4 (constant height, band at the top of the desktop) - so the gate's first condition holds. Screenshot deferred (no device): `device-ready.ps1` reported `no-device` at session start, and this phase's own Done Criteria do not demand a shot. The strip is invisible until a signal exists (phase 04), so the first meaningful screenshot belongs to that phase anyway.

---

## Handoff Notes to Next Phase

The strip exists, is constant height, is inside the safe area, and is visible only while the launcher
replaces the system status area. `LauncherStatusStripManager` owns `launcherStatusStripContent` and exposes
`signals` and `cutoutInsets`; phase 04 renders into that slot and reads those two flows. Nothing else may
set the strip's visibility.

---

## Rollback Plan

Revert phase commit(s). The layout edits are additive - reverting restores `launcherGridScroll`'s
`toTopOf="parent"` anchor. No data migration, no persisted setting introduced.
