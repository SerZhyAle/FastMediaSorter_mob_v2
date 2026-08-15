# Phase 03 — «..» Button UI

**Strategic spec:** [`../S0073_player-copy-move-custom-path-button.md`](../S0073_player-copy-move-custom-path-button.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 6 / 6
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Render the «..» button at the end of both Copy and Move destination grids in `DestinationButtonsManager`. Update panel-visibility logic so panels are always shown («..» is always present). Add accessibility strings in EN, RU, and UK locales.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`onCustomPathPickerRequested` is live in callback + wired in `PlayerActivity`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt` | Modified | ≤ 460 |
| `app_v2/src/main/res/values/strings.xml` | Modified | existing + 1 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | existing + 1 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | existing + 1 |

---

## Steps

### Step 03.1 — Add `createCustomPathButton` helper to `DestinationButtonsManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt`
**Depends on:** — start of phase (Phase 02 complete)

**Prompt for developer:**

> Add a private helper method `createCustomPathButton(isCopy: Boolean): MaterialButton` to `DestinationButtonsManager` (after `createButtonRow()`). The button must:
> - Text: `".."`
> - `contentDescription`: `context.getString(R.string.btn_select_folder_description)`
> - Style: use `MaterialButton` with no background tint (grey/neutral), corner radius 12 dp, text size smaller than standard destination buttons — aim for visually subordinate appearance. Suggested: `textSize = 12f`, `cornerRadius = 12`, background color `Color.parseColor("#888888")` with white text.
> - `layoutParams`: `LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)` with `weight = 0f` and horizontal margin 4 dp — it should not stretch to fill the row.
> - `minimumWidth` / `minimumHeight`: at least 48 dp converted to px (accessibility touch target requirement).
> - `setOnClickListener`: call `callback.onCustomPathPickerRequested(if (isCopy) FileOperationType.COPY else FileOperationType.MOVE)`.

**Verification:**

- `Grep` — `fun createCustomPathButton` found in `DestinationButtonsManager.kt`.
- `Grep` — `onCustomPathPickerRequested` found in `DestinationButtonsManager.kt`.
- `Grep` — `btn_select_folder_description` found in `DestinationButtonsManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 3/3 PASS. Files: DestinationButtonsManager.kt (+28 LOC). Dev log recorded.

---

### Step 03.2 — Append «..» button to Copy and Move grids in `populateDestinationButtons`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `populateDestinationButtons()`, after the existing loops that build the Copy-panel rows (after the last `safeViews.copyToButtonsGrid.addView(rowLayout)`) and the Move-panel rows (after the last `safeViews.moveToButtonsGrid.addView(rowLayout)`), add a dedicated single-button row for the «..» button:
>
> ```kotlin
> // Append «..» row to Copy grid
> val dotDotRowCopy = createButtonRow()
> dotDotRowCopy.addView(createCustomPathButton(isCopy = true))
> safeViews.copyToButtonsGrid.addView(dotDotRowCopy)
>
> // Append «..» row to Move grid
> val dotDotRowMove = createButtonRow()
> dotDotRowMove.addView(createCustomPathButton(isCopy = false))
> safeViews.moveToButtonsGrid.addView(dotDotRowMove)
> ```
>
> Place these additions immediately after the respective destination-row loops, before the `val hasDestinations = ...` line.

**Verification:**

- `Grep` — `dotDotRowCopy` found in `DestinationButtonsManager.kt`.
- `Grep` — `dotDotRowMove` found in `DestinationButtonsManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: DestinationButtonsManager.kt (-4 LOC). Dev log recorded.

---

### Step 03.3 — Update panel-visibility logic to always show panels

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/DestinationButtonsManager.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Currently `populateDestinationButtons()` hides both panels when `hasDestinations == false` (the branch at line ~169):
> ```kotlin
> if (!hasDestinations) {
>     safeViews.copyToPanel.isVisible = false
>     safeViews.moveToPanel.isVisible = false
> }
> ```
> Because the «..» button is now always present, this guard must be removed so panels are shown even when `destinationsList.isEmpty()`. Delete (or comment out with an explanatory note) the entire `if (!hasDestinations)` branch. The subsequent `else if (!shouldShowPanels)` / `else` branches already handle the correct show/hide logic for fullscreen mode — keep them intact.
>
> The variable `hasDestinations` itself is no longer needed; remove it or leave it (either is fine as long as the guard branch is gone).

**Verification:**

- `Grep` — `if (!hasDestinations)` returns zero hits in `DestinationButtonsManager.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. if(!hasDestinations) guard removed; hasDestinations variable also removed. Dev log recorded.

---

### Step 03.4 — Add EN accessibility string

**Files:** `app_v2/src/main/res/values/strings.xml`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add to `app_v2/src/main/res/values/strings.xml`:
> ```xml
> <string name="btn_select_folder_description">Select folder</string>
> ```

**Verification:**

- `Grep` — `btn_select_folder_description` found in `app_v2/src/main/res/values/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. String already present at line 1560 ("Select destination folder"). PRE-RESOLVED.

---

### Step 03.5 — Add RU accessibility string

**Files:** `app_v2/src/main/res/values-ru/strings.xml`
**Depends on:** Step 03.4

**Prompt for developer:**

> Add to `app_v2/src/main/res/values-ru/strings.xml`:
> ```xml
> <string name="btn_select_folder_description">Выбрать папку</string>
> ```

**Verification:**

- `Grep` — `btn_select_folder_description` found in `app_v2/src/main/res/values-ru/strings.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. String already present at line 1594 ("Выбрать папку назначения"). PRE-RESOLVED.

---

### Step 03.6 — Add UK accessibility string and build

**Files:** `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** Step 03.5

**Prompt for developer:**

> Add to `app_v2/src/main/res/values-uk/strings.xml`:
> ```xml
> <string name="btn_select_folder_description">Вибрати папку</string>
> ```
> Then run a build to confirm all string references resolve and no compilation errors remain.

**Verification:**

- `Grep` — `btn_select_folder_description` found in `app_v2/src/main/res/values-uk/strings.xml`.
- Project compiles — run `/build`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. String already present at line 1573 ("Вибрати теку призначення"). PRE-RESOLVED. Build running.

---

## Phase Done Criteria

- [x] Every Step 03.* above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL in 11s.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- Both Copy and Move grids now always end with a «..» button.
- Panels are shown even when there are zero configured destination resources.
- `btn_select_folder_description` is present in EN/RU/UK string tables.
- Phase 04 updates feature docs and regenerates the human-readable catalog.

---

## Rollback Plan

Revert phase commit(s) — UI changes are additive; no data migration involved.
