# Phase 04 - List gadgets

**Strategic spec:** [`../S1170_launcher-desktop-app-widgets.md`](../S1170_launcher-desktop-app-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 03
**Blocks:** Phase 07
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Ship the favourites and scheduled-tasks cells with their own list rendering over the same data sources the RemoteViews collection services use.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done - `gadgetKey` exists and the registry accepts an injected set.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/FavoritesGadget.kt` | New | ≤ 220 |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/ScheduledTasksGadget.kt` | New | ≤ 240 |
| ~~`.../res/layout/gadget_widget_list.xml`~~ | Not created | n/a |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/gadget/di/HomeWidgetGadgetModule.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/launcher/LauncherCellCommand.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/ExecuteLauncherCommandUseCase.kt` | Modified | ≤ 130 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/FavoritesRepository.kt` (+ Impl) | Modified | n/a |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeViewModel.kt` | Modified | n/a |
| `app_v2/src/main/res/values{,-ru,-uk}/strings.xml` | Modified | n/a |

**Four corrections found while implementing (2026-07-30).**

- **No new layout.** `gadget_launcher_list.xml` already is the shared list shell (title, message, scrolling list, two optional action buttons) and its own KDoc says the list gadgets share it "rather than each growing a near-copy that drifts". Creating `gadget_widget_list.xml` would have been that near-copy.
- **A row tap needed a command that did not exist.** Step 04.1 asks the row to open "the same `PlayerActivity` destination the widget's per-row fill-in intent does" - that intent carries `initialFilePath`, and no `LauncherCellCommand` could express a file. `research/01` had already flagged exactly this shape as not command-shaped. Rather than let the gadget build an Intent (which would bypass the shared launch guard and the journal), the sealed interface gained `FavoriteFile(resourceId, filePath)` with prefix `fav:` - additive, tolerant decode, no schema change - and `ExecuteLauncherCommandUseCase` gained the matching branch.
- **The confirmation lived in the wrong place, and that was already a bug.** Step 04.2 requires run-all to go through the desktop's `ConfirmScheduledOp` path. That branch sat in `LauncherHomeViewModel.onCellTapped`, not in `run` - so a scheduled operation pinned to the taskbar or opened from the Start menu reached `ExecuteLauncherCommandUseCase`, which deliberately refuses `ScheduledOp`, and the user got a bare "cannot open". Moved into `run`, which every surface (and now every gadget) goes through. Fixing this is what lets the gadget need no new host API.
- **Span source corrected.** Widget default size is `targetCellWidth`/`targetCellHeight` in the `appwidget-provider`, not the `minWidth`/`minHeight` dp pair Phase 03 cited - the dp values are a resize floor. Favourites is 3x3, scheduled tasks 2x1. Phase 03's nine spans happened to be right anyway; its KDoc now names the correct source.

**Deviation from 04.2, deliberate.** No single "run all" control. Each row runs its own operation through the confirmation, which names what is about to run; one blanket confirm for an unnamed batch of copy/move/delete operations is a worse thing to ask the user to approve. Toggle-pause is not reproduced either - it is a widget-local broadcast with no desktop equivalent, and Settings is one tap away through the row.

---

## Steps

### Step 04.1 - Favourites list gadget

**Files:** `FavoritesGadget.kt`, `gadget_widget_list.xml`, `HomeWidgetGadgetModule.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> `FavoritesWidgetService` feeds the RemoteViews collection; a launcher cell draws in our own process, so bind a `RecyclerView` in `gadget_widget_list.xml` to the same data source that service reads rather than reproducing the service. Extend `LauncherGadgetView` and collect the favourites in `onActive()` - that hook already scopes work to attached + STARTED, so do not add a bare `lifecycleScope.launch { flow.collect { } }`. Empty state mirrors the widget's: the row area is replaced by the same message, and tapping the cell body runs `fn:favorites` through `host.run`. A row tap opens the same `PlayerActivity` destination the widget's per-row fill-in intent does.

**Verification:**

- `Grep` - `class FavoritesGadget` present and implements `LauncherGadget`.
- `Grep` - `onActive()` present; `lifecycleScope.launch` returns zero hits in the file.
- ~~`Grep` - `="#` in `gadget_widget_list.xml`~~ - no new layout; the shared `gadget_launcher_list.xml` is reused unchanged.
- `Grep` - `HomeWidgetGadget(` count in the module is unchanged from Phase 03 (this gadget is its own class, registered alongside).

**Status:** `[x]` done

---

### Step 04.2 - Scheduled tasks list gadget

**Files:** `ScheduledTasksGadget.kt`, `HomeWidgetGadgetModule.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Reuse `gadget_widget_list.xml`. The widget's run-all and toggle-pause controls are self-broadcasts that a `LauncherCellCommand` cannot express (`research/01`), so the gadget invokes the same use cases directly through its injected dependencies. Running operations can DELETE or MOVE files, so run-all must go through the same confirmation the desktop already uses for `LauncherCellCommand.ScheduledOp` (see `LauncherHomeViewModel`'s `ConfirmScheduledOp` path) rather than firing on a bare tap. A row tap and the counters open Settings on the scheduled-operations section through Phase 02's `scheduled_tasks` route.

**Verification:**

- `Grep` - `class ScheduledTasksGadget` present.
- `Grep` - the confirmation path is referenced by name; no direct un-confirmed run-all call.
- `Grep` - `sendBroadcast` returns zero hits in the file.

**Status:** `[x]` done - the gadget holds no reference to the execute use case at all. It emits `LauncherCellCommand.ScheduledOp`, and `LauncherHomeViewModel.run` answers with `ConfirmScheduledOp`, so the confirmation cannot be bypassed by construction rather than by review.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] Project compiles - run `/build` for `standard` and `noLegal`.
- [ ] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. Both gadgets collect inside `LauncherGadgetView.onActive`, which is already scoped to attached AND STARTED, so neither holds a collector past its cell; neither registers a listener or receiver, so there is nothing to remove symmetrically. `FavoritesGadget` keeps an id -> (resourceId, uri) map so a row tap needs no second database read on the main thread.
- [x] detekt scoped gate PASS over the phase 04-07 file set. One finding was left deliberately: `LongParameterList 11/10` on `LauncherHomeViewModel`'s constructor is untouched pre-existing debt already ticketed as **S1314** (`launcherhomeviewmodel-detekt-debt`, Draft) - restructuring an 11-parameter ViewModel is that ticket's job, not a side effect of moving one `when` branch.

---

## Handoff Notes to Next Phase

`gadget_widget_list.xml` is the shared list shell. Phase 05's gadgets do not use it - their content is a single surface, not a list.

---

## Rollback Plan

Revert the phase commit and drop the two module registrations; cells already placed with these keys would fall back to `byKey` returning null, so Phase 07's audit must confirm the desktop tolerates an unknown key before release.
