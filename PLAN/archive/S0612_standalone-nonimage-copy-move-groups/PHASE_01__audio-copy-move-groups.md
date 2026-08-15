# Phase 01 - Copy/Move groups in the standalone audio host

**Strategic spec:** [`../S0612_standalone-nonimage-copy-move-groups.md`](../S0612_standalone-nonimage-copy-move-groups.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none
**Blocks:** Phase 04
**Steps done:** 5 / 5

---

## Objective

Add the Copy/Move destination groups to `AudioStandaloneActivity` by including the shared bottom-panels content in both
orientations and wiring `DestinationButtonsManager` + the already-injected file-ops handler, with audio-aware move behavior
(playback ends via the lifecycle before the screen closes).

---

## Prerequisites

- [ ] Working tree on a feature branch or clean enough to isolate this change.
- [ ] `AudioStandaloneActivity.kt` is 565 LOC (> 500) - make a timestamped backup in `temp/` before editing (CLAUDE.md §10.5).
- [ ] Reference host open for copy-paste: `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/activity_standalone_audio.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/activity_standalone_audio.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt` | Modified | ≤ 700 |

> Landscape variant exists - both layouts must be edited (CLAUDE.md Rule 11). Reuse the existing shared include
> `@layout/player_bottom_panels_container_content`; do not author new copy/move view ids.

---

## Steps

### Step 01.1 - Include the shared bottom panels in both orientations

**Files:** `res/layout/activity_standalone_audio.xml`, `res/layout-land/activity_standalone_audio.xml`
**Depends on:** - start of phase

**Prompt for developer:**

> In each layout add `<include android:id="@+id/bottomPanelsContainer" layout="@layout/player_bottom_panels_container_content" />`
> as the last direct child of the root vertical `LinearLayout`, immediately after the `mediaContentArea` element closes.
> Keep portrait and landscape view ids identical so the binding field is non-null in both orientations. Do not hardcode
> colours - the shared include already uses theme attributes. Do not change `mediaContentArea`'s weight/height.

**Verification:**

- `Grep` - `player_bottom_panels_container_content` present in BOTH `res/layout/activity_standalone_audio.xml` and `res/layout-land/activity_standalone_audio.xml`.
- `.\a.ps1 fr` - resources/manifest compile (ViewBinding regenerates with `bottomPanelsContainer`).

**Status:** `[x]` done

**Step Log:**

---

### Step 01.2 - Inject the destinations use-case and add the custom-path launcher

**Files:** `.../standalone/AudioStandaloneActivity.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `@Inject lateinit var getDestinationsUseCase: GetDestinationsUseCase` (do NOT re-add `fileOperationUseCase`; it is
> already injected). Copy the reference host's `OpenDocumentTree` ActivityResult launcher verbatim: a `pendingCustomPathOp`
> field holding the pending Copy/Move op, a `customPathPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree())`
> that on a non-null tree Uri takes a persistable permission and delegates to `fileOperations.copyCurrentFileToPath(..)` /
> `moveCurrentFileToPath(..)` based on `pendingCustomPathOp`. Reuse the host's existing `settingsRepository` (already injected
> for the file-ops handler); if absent, add `@Inject lateinit var settingsRepository`.

**Verification:**

- `Grep` - `getDestinationsUseCase` injected in `AudioStandaloneActivity.kt`.
- `Grep` - `OpenDocumentTree` and `pendingCustomPathOp` present.
- `Grep` - exactly one `fileOperationUseCase` declaration (no duplicate injection).

**Status:** `[x]` done

**Step Log:**

---

### Step 01.3 - Construct `DestinationButtonsManager` and its callback

**Files:** `.../standalone/AudioStandaloneActivity.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a lazy `destinationButtonsManager = DestinationButtonsManager(root = binding.root, settingsRepository,
> getDestinationsUseCase, lifecycleScope, callback = object : DestinationButtonsCallback { .. }, shouldNumberSlots = { false },
> slotKeyGlyph = { null })`. Implement the callback: `onCopyClicked(it)` -> `fileOperations.copyCurrentFileTo(it)`;
> `onMoveClicked(it)` -> `fileOperations.moveCurrentFileTo(it)`; `onCustomPathPickerRequested(op)` -> store `op` in
> `pendingCustomPathOp` and `customPathPickerLauncher.launch(null)`; `getCurrentResourceId` -> `-1L`;
> `onUpdateCommandAvailability` -> no-op; `isCommandPanelVisible` -> `viewModel.state.value.mediaFile != null`. Match the
> reference host's signatures exactly.

**Verification:**

- `Grep` - `DestinationButtonsManager(` with `root = binding.root` in `AudioStandaloneActivity.kt`.
- `Grep` - `onCopyClicked` delegates to `fileOperations.copyCurrentFileTo` and `onMoveClicked` to `moveCurrentFileTo`.
- `Grep` - `getCurrentResourceId` returns `-1L`.

**Status:** `[x]` done

**Step Log:**

---

### Step 01.4 - Populate per shown file and migrate the nav inset

**Files:** `.../standalone/AudioStandaloneActivity.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> In `observeData`, inside the `if (file.path != lastShownPath)` block (right after `viewManager.show(file, MediaType.AUDIO)`),
> call `destinationButtonsManager.populateDestinationButtons()`. Migrate the nav-bottom inset: remove the bottom-padding inset
> listener currently applied to `binding.mediaContentArea` and apply it instead to
> `binding.root.findViewById<View>(R.id.bottomPanelsContainer)` (the include has no binding field), mirroring the reference
> host. Make the panels visible per the callback's `isCommandPanelVisible` contract; population already runs in a coroutine
> inside the manager (lazy - does not delay first audio render).

**Verification:**

- `Grep` - `populateDestinationButtons()` called in `AudioStandaloneActivity.kt`.
- `Grep` - the nav-bottom inset listener targets `bottomPanelsContainer` (not `mediaContentArea`).
- `.\a.ps1 fk` - Kotlin compiles.

**Status:** `[x]` done

**Step Log:**

---

### Step 01.5 - Confirm strings + audio move behavior

**Files:** `.../standalone/AudioStandaloneActivity.kt`
**Depends on:** Step 01.4

**Prompt for developer:**

> Confirm no new user-facing strings are introduced (the file-ops handler already reuses
> `msg_copy_started`/`msg_copy_success`/`msg_move_started`/`msg_move_success`/`error_copy_failed`/`error_move_failed`/`select_folder`
> from S0610). Confirm the audio move path needs no explicit pre-stop: `transferCurrentFile()` completes the move on disk
> before `activity.finish()`, and `onPause()`/`onDestroy()` release the audio service via the existing lifecycle (research 01).
> Add an inline WHY comment only if a non-obvious lifecycle ordering needs documenting; otherwise none.

**Verification:**

- `Grep` - no hardcoded user-facing toast literals added (all via `R.string.*`).
- `Grep` - no new `Timber.e` without a message; no empty catch blocks added.
- `.\a.ps1 fc` - code + resources compile (standalone audio path).

**Status:** `[x]` done

**Step Log:**

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - `.\a.ps1 fc` (or `dq`).
- [x] Both portrait and landscape audio layouts include the shared panels (parity).
- [ ] `scripts/post-change.ps1` run for the touched files (dev-log + catalog + quality gates).

---

## Handoff Notes to Next Phase

Audio host now offers Copy/Move groups. Phases 02/03 repeat the identical recipe for document and text; Phase 04 records the
capability and regenerates catalog/dev-log.

---

## Rollback Plan

Revert the phase commit - remove the two includes and the Kotlin wiring; no schema migration, no persistent surface affected.
