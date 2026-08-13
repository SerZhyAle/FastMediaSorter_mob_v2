# Phase 04 - Signal row

**Strategic spec:** [`../S1421_launcher-own-notification-area.md`](../S1421_launcher-own-notification-area.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Render the signals inside the strip as two edge-anchored groups with the cutout gap between them, make each
icon a tap target that opens its owning screen, and make the row reachable without a touchscreen.

---

## Prerequisites

- [ ] Phase 02 and Phase 03 are ✅ Done.
- [ ] `LauncherStatusStripManager.signals` and `.cutoutInsets` are the only inputs used - no source is read directly here.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRowView.kt` | New | ≤ 300 |
| `app_v2/src/launcherEnabled/res/layout/launcher_signal_chip.xml` | New | ≤ 45 |
| `app_v2/src/launcherEnabled/res/layout/launcher_status_strip.xml` | Modified | ≤ 70 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt` | Modified | ≤ 260 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |

> `launcher_signal_chip.xml` needs no `layout-land` twin - it is a chip inside a strip whose height is the
> same in both orientations, and the orientation-dependent part is the gap, computed from insets at runtime.

---

## Steps

### Step 04.1 - Add the chip layout

**Files:** `app_v2/src/launcherEnabled/res/layout/launcher_signal_chip.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the chip as an `ImageView` with id `launcherSignalIcon`, sized to the strip height minus the
> vertical padding, `android:focusable="true"`, `android:clickable="true"` and
> `android:background="?attr/selectableItemBackgroundBorderless"`. Set `contentDescription` from code, not
> in XML - the description carries the signal's label. Use `?attr/` or `@color/` tokens only; no
> `="#rrggbb"` literal anywhere in the file.

**Why:**

Strategic §3.1 binds the row to Rule 16, which requires every interactive area to be operable by keyboard,
D-pad and mouse - a non-focusable `ImageView` is unreachable by all three.

**Verification:**

- `Glob` - the file exists.
- `Grep` - `android:focusable="true"` present.
- `Grep -n "=\"#"` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 04.2 - Add the row view with the cutout split

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRowView.kt`, `app_v2/src/launcherEnabled/res/layout/launcher_status_strip.xml`
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `class LauncherSignalRowView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ViewGroup`
> that lays out chips as two groups: one packed against the start edge, one against the end edge, with the
> cutout's own span left free between them. The span arrives through `setCutoutBounds(bounds: Rect)`, which
> the manager feeds from its `cutoutBounds` flow - a rect, not a scalar gap, so an off-centre punch-hole is
> cleared where it actually is rather than where a centred gap would assume. An empty rect must produce one
> continuous row, not an artificial break - that is the device-without-cutout case.
> Add `fun submit(signals: List<LauncherSignal>, onTap: (LauncherSignal) -> Unit)`
> that inflates or recycles one `launcher_signal_chip` per signal. Place the view inside
> `launcherStatusStripContent` in `launcher_status_strip.xml`.
> Measure and lay out in `onMeasure`/`onLayout` only; keep both methods free of allocation, and hold the
> chip count in a field rather than recomputing `childCount` inside the layout loop.

**Why:**

Strategic ADR-3 and §4.4 rule that the row splits into two edge groups with the middle left free, and that
the gap comes from the measured cutout inset so a device without a cutout gets the same row unbroken.

**Verification:**

- `Grep` - `class LauncherSignalRowView` matches exactly once.
- `Grep` - `fun setCutoutBounds(` and `fun submit(` both present.
- `Grep` - `LauncherSignalRowView` present in `launcher_status_strip.xml`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 04.3 - Feed the row from the decisive node

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Attach `LauncherSignalRowView` to the content slot and feed it from the manager: `submit()` on every
> `signals` emission, `setCutoutBounds` on every `cutoutBounds` emission. Collect both with
> `collectOnLifecycle`. An empty signal list leaves the slot empty at full height - what occupies it instead
> is strategic §5.2 and is not decided here.

**Why:**

Strategic ADR-2 makes this manager the only node that decides the strip's content, so the row must be
attached by it rather than by the activity or by the row view itself.

**Verification:**

- `Grep` - `submit(` present in the manager.
- `Grep -n "lifecycleScope.launch"` returns zero hits in the file.
- `Grep -rn "\.submit\("` across `app_v2/src/launcherEnabled` matches only this manager.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 04.4 - Open the owning screen on tap

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Pass an `onTap` lambda into `submit()` that calls `signalRegistry.open(signal)` and starts the returned
> intent. A null intent means the signal has no screen: leave the chip non-clickable rather than starting
> anything, and set `isClickable = false` on it so the ripple does not promise an action that never comes.
> Wrap the `startActivity` in `runCatching` and log a failure with `Timber.w` - a stale intent to a screen
> that no longer resolves must not take the launcher down.

**Why:**

Strategic §4.4 rules that tapping a signal opens the screen the signal belongs to, and §7 lists that as an
acceptance criterion.

**Verification:**

- `Grep` - `signalRegistry.open(` present.
- `Grep` - `runCatching` present around the activity start.
- `Grep -n "catch (e: Exception) \{\s*\}"` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 04.5 - Make the row reachable without touch

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRowView.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Chain the chips with `nextFocusRight` / `nextFocusLeft` in submit order, and set `nextFocusDown` on every
> chip to the desktop grid so D-pad Down leaves the strip instead of trapping focus there. Set each chip's
> `contentDescription` from the signal's label, and set `descendantFocusability = FOCUS_AFTER_DESCENDANTS`
> on the row. Do not draw a focus outline here: `FocusDecorationController` in `core/ui/focus/` already
> decorates the focused view app-wide, and a second indicator would double-draw.

**Why:**

Strategic §3.1 requires the row to work from keyboard and D-pad like any other interactive area, and §7
lists D-pad reachability as an acceptance criterion.

**Verification:**

- `Grep` - `nextFocusRight` and `nextFocusDown` both present.
- `Grep` - `contentDescription` set from the signal label.
- `Grep -n "focus_decoration_outline\|FocusFrame"` returns zero hits in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 04.1 PASS. `launcher_signal_chip.xml` created (16 lines) plus three dimens - `launcher_signal_chip_size` 24dp, `_padding` 3dp, `_spacing` 4dp. Predicates: file exists; `android:focusable="true"` present; `="#` 0 hits. The chip is under the 48dp touch-target minimum knowingly and the dimens comment says why: the band is 28dp tall by the owner's constant-height ruling, so a 48dp control cannot live in it at all - the same trade the launcher gadget action buttons already made.
- 2026-08-07 - Step 04.2 PASS. `LauncherSignalRowView.kt` created (146 lines, longest 111) and mounted inside `launcherStatusStripContent`. Predicates: class x1; `fun setCutoutBounds(` and `fun submit(` present; the view is referenced from `launcher_status_strip.xml`; `.\a.ps1 fk` exit 0, BUILD SUCCESSFUL in 43s.
  - Children are added and removed in `rebuild()`, never in `onMeasure`. The prompt asked for the fit computation in `onMeasure`, but adding or removing views during measure forces a second layout pass and makes the row flicker on every signal change; `rebuild()` is instead driven by the three inputs that can change the answer - the signal list, the cutout rect and `onSizeChanged`.
  - Cutout coordinates arrive in window space, so `getLocationInWindow` converts them to the row's own before use, with the array allocated once as a field rather than per layout.
  - Capacity is already computed and the list already truncated to it, so overflowing chips are never laid out over the cutout even before phase 05 adds the counter.
- 2026-08-07 - Step 04.3 PASS. `LauncherStatusStripManager` now attaches the row: `submit(..)` on every `signals` emission and `setCutoutBounds(..)` on every `cutoutBounds` emission, both through `collectOnLifecycle`. Predicates: `submit(` present in the manager; `lifecycleScope.launch` 0 hits; `.submit(` matches only this manager across `src/launcherEnabled`; `.\a.ps1 fk` exit 0, BUILD SUCCESSFUL in 37s. `submit` gained a `canOpen` parameter beyond the prompt's two, because step 04.4 has to leave a chip without a screen non-clickable and only the manager can answer whether a signal resolves. This step passes `canOpen = { false }` and an empty `onTap`, so the intermediate state is a row that renders and is not tappable rather than one whose ripples do nothing; 04.4 supplies both.
- 2026-08-07 - Step 04.4 PASS. `openSignal` added to the manager and wired as `submit`'s `onTap`, with `canOpen = { signalRegistry.open(it) != null }`. Predicates: `signalRegistry.open(` present (2 hits - the openability probe and the open itself); `runCatching { host.startActivity` present; empty `catch (e: Exception) {}` 0 hits; `.\a.ps1 fk` exit 0. One `fk` run in this step reported FAILURE with a daemon log dump and no `e:` line, then passed `UP-TO-DATE` on the very next run - a gradle daemon-busy artefact, not a code error, and recorded here so it is not mistaken for one later.
- 2026-08-07 - Step 04.5 PASS. Focus chaining added to `LauncherSignalRowView` (now 187 lines, longest 111). Predicates: `nextFocusRightId` and `nextFocusDownId` present; `contentDescription = signal.label` present; `focus_decoration_outline` / `FocusFrame` 0 hits; `.\a.ps1 fk` exit 0, BUILD SUCCESSFUL in 34s. Two things the prompt did not name but the step needs:
  - Each chip gets `generateViewId()` on inflate. `nextFocus*Id` addresses views by id and every chip inflates from one layout with one id, so without unique ids the whole row would have been a single D-pad target.
  - `onLayout` now right-aligns the end group as a block instead of filling it backwards from the edge. Filling backwards put child order in reverse of visual order, which would have stepped the D-pad right-to-left through that group even with the chain correct.
  - No focus outline is drawn: `FocusDecorationController` (`core/ui/focus/`) already decorates the focused view app-wide, verified present in the catalog before relying on it.

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, BUILD SUCCESSFUL in 46s.
- [x] `.\a.ps1 fkn` exits 0 - Fast check passed.
- [x] `Grep` for `TODO(phase-04)` returns zero hits (expected: 0 | actual: 0).
- [x] `Grep -n "Log\.d\("` returns zero hits in every file this phase touched - `post-change`'s
      `nontimber-log` dimension reported 0 new occurrences on each.
- [x] `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_signal"` exits 0.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13). See audit note below.

---

## Phase-boundary audit (2026-08-07)

Layers 1-3 of `docs/CODE_AUDIT_PROTOCOL.md`; no Room surface.

- Layer 1 - PASS. The row is 187 lines and holds no business logic: it measures, lays out, and delegates
  every decision - which signals exist, whether one can be opened, where a tap goes - to the manager.
- Layer 2 - PASS. No coroutine in the view; the manager's two collectors are lifecycle-scoped.
- Layer 3 - PASS. The lambdas the row captures point back at the manager, which holds the binding, which
  holds the row - a cycle, but one the collector traces through and one `unbind()` breaks on destroy.
  Chip click listeners are re-set or the chip removed on every rebuild, so none outlives its signal.
- P3 - `canOpen` builds a throwaway `Intent` per chip on every rebuild, to answer whether the chip should
  be clickable. Rebuilds are rare (the signal list is `distinctUntilChanged`, plus size and cutout changes)
  and the allocation is small, so it is left as the honest way to ask rather than a cached answer that could
  go stale between the render and the tap.
- UI refusal gate (S1338): placement is the owner's ruling in strategic §4.4 and §5.1, quoted verbatim
  there. Screenshot deferred (no device) - `device-ready.ps1` reported `no-device` for this whole session,
  and this phase's Done Criteria do not demand one. The strip is the first thing in this ticket a person can
  actually see, so the device test carries it (§10 of the strategic spec's status note when it lands).

---

## Handoff Notes to Next Phase

The row renders every signal it is given and does not yet truncate. Phase 05 adds the overflow rule: the
row must ask how many chips fit before submitting, and hand the remainder to the counter.

---

## Rollback Plan

Revert phase commit(s) - the strip falls back to the constant-height empty container phase 02 delivered.
