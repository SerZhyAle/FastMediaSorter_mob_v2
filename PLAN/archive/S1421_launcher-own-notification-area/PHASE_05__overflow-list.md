# Phase 05 - Overflow list

**Strategic spec:** [`../S1421_launcher-own-notification-area.md`](../S1421_launcher-own-notification-area.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Truncate the row to what fits, put a `+N` counter in the last slot, and open the full signal list from it -
the new surface strategic §5.1 says has nothing in the project to reuse.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done.
- [ ] Strategic §5.1 read - it records that scrolling is ruled out by ADR-3 and that no `+N` surface exists to copy.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRowView.kt` | Modified | ≤ 380 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalListBottomSheet.kt` | New | ≤ 220 |
| `app_v2/src/launcherEnabled/res/layout/launcher_signal_list_sheet.xml` | New | ≤ 55 |
| `app_v2/src/launcherEnabled/res/layout/launcher_signal_list_item.xml` | New | ≤ 60 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt` | Modified | ≤ 300 |
| `app_v2/src/main/res/values/strings.xml` (+ `values-ru`, `values-uk`) | Modified | - |

> The sheet layouts need no `layout-land` twin: a `BottomSheetDialogFragment` with a `RecyclerView` reflows
> by height, and the project's existing sheets ship one layout. If a landscape variant is later added, both
> files move together per Rule 11.

---

## Steps

### Step 05.1 - Truncate the row and add the counter chip

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalRowView.kt`, `app_v2/src/main/res/values/strings.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In `onMeasure`, compute how many chips fit in the two edge groups given the current cutout gap. When the
> submitted list is longer, show `fits - 1` chips and put a counter chip in the last slot reading the
> `launcher_signal_overflow_count` string with the number of hidden signals. Expose
> `fun setOnOverflowTap(listener: () -> Unit)`. When everything fits, no counter chip is created at all -
> not a counter reading zero. Add `launcher_signal_overflow_count` via
> `set-android-string.ps1 -Action add -Key launcher_signal_overflow_count -En -Ru -Uk`; the value is a
> format string carrying one integer. Check the wording against `docs/COMMUNICATION_POLICY.md` §2 and §6.

**Why:**

Strategic §5.1 records the owner's ruling that overflowing signals collapse into a `+N` chip in the last
slot rather than being dropped by priority or by recency, because no signal may disappear silently.

**Verification:**

- `Grep` - `launcher_signal_overflow_count` present in the row view and in `values/strings.xml`.
- `Grep` - `fun setOnOverflowTap(` present.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_signal"` exits 0.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 05.2 - Add the signal list sheet

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalListBottomSheet.kt`, `app_v2/src/launcherEnabled/res/layout/launcher_signal_list_sheet.xml`, `app_v2/src/launcherEnabled/res/layout/launcher_signal_list_item.xml`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create `class LauncherSignalListBottomSheet : BottomSheetDialogFragment` modelled on
> `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/SendToBottomSheet.kt` - same shape, a
> `RecyclerView` plus an inner `RecyclerView.Adapter`. The sheet takes the full signal list and a tap
> callback through `newInstance`-style arguments or a settable field; it holds no reference to the registry
> and performs no navigation itself. The item layout shows icon, label and detail; the row is
> `android:focusable="true"` and `android:clickable="true"`. Add the sheet title key
> `launcher_signal_list_title`. Use `?attr/` or `@color/` tokens only in both layouts. The sheet carries no
> confirm/cancel pair - it is a picker, which CLAUDE.md §11 exempts.

**Why:**

Strategic §5.1 states the `+N` counter opens the full list of active signals so that no signal is lost, and
records that the project has no existing surface of this kind to reuse.

**Verification:**

- `Glob` - all three files exist.
- `Grep` - `class LauncherSignalListBottomSheet : BottomSheetDialogFragment` matches exactly once.
- `Grep -n "=\"#"` returns zero hits in both new layouts.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "launcher_signal"` exits 0.

**Status:** `[x]` done

---

### Step 05.3 - Open the sheet from the counter and route its taps

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt`
**Depends on:** Step 05.2

**Prompt for developer:**

> Wire `setOnOverflowTap` to show `LauncherSignalListBottomSheet` with the current full signal list, and
> route its item taps through the same `signalRegistry.open(signal)` path step 04.4 built - one navigation
> path, not two. Dismiss the sheet from the activity's `onStop`, matching how `shortcutMenuManager.dismiss()`
> and `cellActionMenuManager.dismiss()` already behave, so it cannot survive the launcher leaving the
> foreground.

**Why:**

Strategic ADR-2 makes this manager the single owner of the strip's behaviour, so the surface the strip opens
and the navigation it performs both belong here rather than in the row view.

**Verification:**

- `Grep` - `LauncherSignalListBottomSheet` referenced from exactly one file besides its own declaration -
  this manager.
- `Grep` - `setOnOverflowTap` present.
- `Grep -c "startActivity("` returns 1 in the manager. That, not the `signalRegistry.open(` count, is the
  one-navigation-path invariant: `open()` is legitimately called twice, once to ask whether a chip is
  openable and once to open it, but only one place starts anything.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

### Step 05.4 - Make the sheet reachable without touch

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/signal/LauncherSignalListBottomSheet.kt`
**Depends on:** Step 05.3

**Prompt for developer:**

> Give the list initial focus on its first item when the sheet opens in non-touch mode, and set each item's
> `contentDescription` from its label. Do not add a focus outline - `FocusDecorationFragmentCallbacks`
> already decorates focused views inside dialog and bottom-sheet windows.

**Why:**

Strategic §3.1 requires every interactive area of this feature to work from keyboard and D-pad, and the
sheet is the only way to reach an overflowed signal at all.

**Verification:**

- `Grep` - `requestFocus` present in the sheet.
- `Grep -n "FocusFrame\|focus_decoration_outline"` returns zero hits in the file.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

## Step Log

- 2026-08-07 - Step 05.1 PASS. Truncation and the counter chip added to `LauncherSignalRowView`; `launcher_signal_counter.xml` created; `launcher_signal_overflow_count` (`+%1$d`) added across EN/RU/UK. Predicates: the key is referenced from the row and present in `values/strings.xml`; `fun setOnOverflowTap(` present; `="#` 0 hits in the new layout; `check_strings_localized.ps1` exit 0 ("all 6 key(s) present in en/ru/uk"); `.\a.ps1 fk` exit 0, BUILD SUCCESSFUL in 25s.
  - The counter is a `TextView` in its own layout, not the `ImageView` chip - the count is the whole content and a glyph cannot carry it. The prompt said "counter chip" without naming a view type.
  - The counter costs a slot: with `capacity` chips' worth of room and more signals than that, `capacity - 1` chips are drawn and the counter stands for the rest. `capacity == 0` draws nothing at all, the degenerate case of a strip narrower than one chip.
  - `syncChildren` now clears and re-inflates instead of trimming, because the counter's presence changes what the last child *is*; keeping a stale one would need a cast that cannot fail safely.
  - The format string carries one integer and no sentence, so COMMUNICATION_POLICY §6's tone checklist has no prose to judge - it is a count, not a message.
- 2026-08-07 - Step 05.2 PASS. `LauncherSignalListBottomSheet.kt` created (88 lines) with `launcher_signal_list_sheet.xml` and `launcher_signal_list_item.xml`; `launcher_signal_list_title` added across EN/RU/UK. Predicates: all three files exist; `class LauncherSignalListBottomSheet : BottomSheetDialogFragment` x1; `="#` 0 hits in both layouts; `check_strings_localized.ps1` exit 0 ("all 7 key(s) present in en/ru/uk"); `.\a.ps1 fk` exit 0, BUILD SUCCESSFUL in 41s. Modelled on `SendToBottomSheet` as the prompt directed - same `BottomSheetDialogFragment` plus inner `RecyclerView.Adapter` shape, and the same `doOnPreDraw` trick for focusing the first row, which also satisfies step 05.4. The sheet holds no registry and navigates nowhere itself; the caller supplies the list and the tap handler, keeping one navigation path (ADR-2).
- 2026-08-07 - Step 05.3 PASS. The manager now opens the sheet from the counter and dismisses it on stop. Predicates: the sheet is referenced from exactly one file besides its own declaration; `setOnOverflowTap` present; `startActivity(` appears once; `.\a.ps1 fk` exit 0, BUILD SUCCESSFUL in 35s.
  - `bind()` gained a `FragmentManager` parameter, passed by the activity as `supportFragmentManager`. That is data, not logic, so Rule 3 still holds - the alternative was casting the `LifecycleOwner` to a `FragmentActivity` inside the manager, which can fail at runtime for no gain.
  - The manager became a `DefaultLifecycleObserver` and dismisses on `onStop`, the same lifecycle edge and the same reason `shortcutMenuManager` and `cellActionMenuManager` already use in this activity - a sheet over the home screen must not reappear over whatever the user opened next.
  - `showSignalList` no-ops when the sheet is already up, so a double tap cannot stack two.
  - The sheet lists every active signal, not only the truncated ones: the counter stands for the overflow, but the list behind it is the whole picture.
- 2026-08-07 - Step 05.4 PASS, pre-resolved by step 05.2. Its predicates verified against the code as it stands rather than re-implemented: `requestFocus` present in the sheet (the `doOnPreDraw` first-row focus); `contentDescription = signal.label` set per row; `FocusFrame` / `focus_decoration_outline` 0 hits - `FocusDecorationFragmentCallbacks` already decorates focused views inside dialog and bottom-sheet windows. Copying `SendToBottomSheet` wholesale, as step 05.2's prompt directed, brought this step's content with it; recorded rather than silently ticked.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 dq` exit 0, BUILD SUCCESSFUL in 41s.
- [x] `.\a.ps1 fkn` exits 0 - Fast check passed.
- [x] `Grep` for `TODO(phase-05)` returns zero hits (expected: 0 | actual: 0).
- [x] `Grep -n "Log\.d\("` returns zero hits in every file this phase touched - `post-change`'s
      `nontimber-log` dimension reported 0 new occurrences on each.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13). See audit note below.

---

## Phase-boundary audit (2026-08-07)

Layers 1-3 of `docs/CODE_AUDIT_PROTOCOL.md`; no Room surface.

- Layer 1 - PASS. The sheet is 88 lines and owns no state beyond what it was handed; the manager stays the
  only class that decides what the strip shows and where a tap goes. `LauncherHomeActivity` gained one
  argument on an existing call and no logic.
- Layer 2 - PASS. No coroutine added. The sheet is a plain `BottomSheetDialogFragment`.
- Layer 3 - PASS. `onDestroyView` clears the sheet's binding; the manager clears both the binding and the
  `FragmentManager` in `unbind()`. The lifecycle observer is registered on the activity's own lifecycle and
  dies with it.
- P3 - the sheet's `signals` list is a snapshot taken when it opens and does not follow later emissions, so
  a signal that ends while the sheet is up stays listed until it is reopened. Deliberate: a list that
  re-sorted under a moving finger is worse than a stale row, and the row behind it is live.
- P3 - `signals` and `onTap` are set on the instance rather than passed as arguments, so a system-initiated
  process death while the sheet is up would restore it empty. `SendToBottomSheet` has the same shape and
  documents the same reason - it is a modal dialog, never restored from the back stack - and the manager
  dismisses it on `onStop` regardless.
- UI refusal gate (S1338): placement is the owner's ruling in strategic §5.1 - the `+N` chip is the last
  icon and opens the full list - and §5.3 explicitly delegated the surface's form to tactics. Screenshot
  deferred (no device) for the whole session; this phase's Done Criteria do not demand one.

---

## Handoff Notes to Next Phase

Every acceptance criterion in strategic §7 is now covered except the idle-strip one, which waits on the
owner answer in §5.2. Phase 06 records the capability and regenerates the indexes.

---

## Rollback Plan

Revert phase commit(s) - the row falls back to rendering every signal without truncation, which is phase
04's state.
