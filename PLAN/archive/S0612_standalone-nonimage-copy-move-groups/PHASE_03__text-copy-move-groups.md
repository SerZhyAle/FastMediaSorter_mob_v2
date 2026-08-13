# Phase 03 - Copy/Move groups in the standalone text host

**Strategic spec:** [`../S0612_standalone-nonimage-copy-move-groups.md`](../S0612_standalone-nonimage-copy-move-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 04
**Steps done:** 5 / 5

---

## Objective

Add the Copy/Move destination groups to `TextStandaloneActivity` by including the shared bottom-panels content in both
orientations and wiring `DestinationButtonsManager` + the already-injected file-ops handler. The existing
`btnCopyTextCmd` (copy text to clipboard) is unrelated and stays as-is.

---

## Prerequisites

- [ ] `TextStandaloneActivity.kt` is 468 LOC (< 500) - no backup required.
- [ ] Reference host open for copy-paste: `PhotoVideoStandaloneActivity.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_standalone_text.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/activity_standalone_text.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/TextStandaloneActivity.kt` | Modified | ≤ 600 |

> Landscape variant exists - both layouts must be edited (CLAUDE.md Rule 11).

---

## Steps

### Step 03.1 - Include the shared bottom panels in both orientations

**Files:** `res/layout/activity_standalone_text.xml`, `res/layout-land/activity_standalone_text.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> Same as Step 01.1 for the text layouts: add `<include android:id="@+id/bottomPanelsContainer"
> layout="@layout/player_bottom_panels_container_content" />` as the last child of the root vertical `LinearLayout`, after
> `mediaContentArea`, in BOTH portrait and landscape. Keep ids identical across orientations; no hardcoded colours; do not
> move `btnCopyTextCmd` / `btnEditTextCmd`.

**Verification:**

- `Grep` - `player_bottom_panels_container_content` present in BOTH text layout files.
- `.\a.ps1 fr` - resources/manifest compile.

**Status:** `[x]` done

**Step Log:**

---

### Step 03.2 - Inject the destinations use-case and add the custom-path launcher

**Files:** `.../standalone/TextStandaloneActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Same as Step 01.2: add `@Inject lateinit var getDestinationsUseCase` (do NOT re-add `fileOperationUseCase`); copy the
> `OpenDocumentTree` launcher + `pendingCustomPathOp` verbatim; delegate to `fileOperations.copyCurrentFileToPath` /
> `moveCurrentFileToPath`. Reuse the host's existing `settingsRepository`.

**Verification:**

- `Grep` - `getDestinationsUseCase` injected; `OpenDocumentTree` + `pendingCustomPathOp` present.
- `Grep` - exactly one `fileOperationUseCase` declaration.

**Status:** `[x]` done

**Step Log:**

---

### Step 03.3 - Construct `DestinationButtonsManager` and its callback

**Files:** `.../standalone/TextStandaloneActivity.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Same as Step 01.3: lazy `DestinationButtonsManager(root = binding.root, ..)` with the inline callback. `getCurrentResourceId`
> -> `-1L`; `isCommandPanelVisible` -> `viewModel.state.value.mediaFile != null`.

**Verification:**

- `Grep` - `DestinationButtonsManager(` with `root = binding.root`.
- `Grep` - `onCopyClicked` / `onMoveClicked` delegate to `fileOperations`.
- `Grep` - `getCurrentResourceId` returns `-1L`.

**Status:** `[x]` done

**Step Log:**

---

### Step 03.4 - Populate per shown file and migrate the nav inset

**Files:** `.../standalone/TextStandaloneActivity.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> In `observeData`, inside `if (file.path != lastShownPath)` (right after `textViewerManager.displayText(file, ..)` and the
> `btnEditTextCmd.isVisible` line), call `destinationButtonsManager.populateDestinationButtons()`. Migrate the nav-bottom
> inset listener from `binding.mediaContentArea` to `binding.root.findViewById<View>(R.id.bottomPanelsContainer)`.

**Verification:**

- `Grep` - `populateDestinationButtons()` called in the file.
- `Grep` - nav-bottom inset listener targets `bottomPanelsContainer`.
- `.\a.ps1 fk` - Kotlin compiles.

**Status:** `[x]` done

**Step Log:**

---

### Step 03.5 - Confirm strings comply

**Files:** `.../standalone/TextStandaloneActivity.kt`
**Depends on:** Step 03.4

**Prompt for developer:**

> Confirm no new user-facing strings are introduced (reuse the S0610 copy/move keys). No hardcoded toast literals; no empty
> catch blocks; no new `Timber.e` without a message. Do not touch the clipboard-copy (`btnCopyTextCmd`) string.

**Verification:**

- `Grep` - no hardcoded user-facing toast literals added.
- `.\a.ps1 fc` - code + resources compile.

**Status:** `[x]` done

**Step Log:**

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` (or `dq`).
- [x] Both portrait and landscape text layouts include the shared panels (parity).
- [ ] `scripts/post-change.ps1` run for the touched files.

---

## Handoff Notes to Next Phase

All three non-image standalone hosts now offer Copy/Move groups. Phase 04 records the capability and regenerates
catalog/dev-log.

---

## Rollback Plan

Revert the phase commit - remove the two includes and the Kotlin wiring; no schema migration.
