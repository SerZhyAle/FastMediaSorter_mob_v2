# Phase 03 - Overflow Commands

**Strategic spec:** [`../S0660_stream-card-overflow-actions-menu.md`](../S0660_stream-card-overflow-actions-menu.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-24
**Completed:** 2026-06-24

---

## Objective

Extend the stream-card overflow (`три точки`) into the canonical secondary-action surface: add `Edit` (manual-only) and `Send link` items beside the existing `Add to home screen` and `Remove`, wired to an edit dialog and an Android sharesheet. The long-press remove and the separate pin button stay as transitional duplicates (strategic §6.2).

---

## Prerequisites

- [ ] Phase 01 ✅ Done - `UpdateStreamSourceUseCase` exists.
- [ ] Phase 02 ✅ Done - `streams_edit`, `streams_send_link`, `streams_share_chooser_title`, `streams_edit_dialog_title` exist in EN/RU/UK.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt` | Modified | ≤ 230 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt` | Modified | ≤ 470 |

> `item_stream_source.xml` is not edited - `btnOverflow` already exists and the menu is built programmatically. No landscape layout counterpart exists for the row item.

---

## Steps

### Step 03.1 - Add Edit + Send-link items to the overflow PopupMenu

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamSourceAdapter.kt`
**Depends on:** Phase 02

**Prompt for developer:**

> Add two constructor callbacks `onEdit: (StreamSourceEntity) -> Unit` and `onShareLink: (StreamSourceEntity) -> Unit`. In the `btnOverflow` `PopupMenu` block, build the items in this order: `Add to home screen` (0), `Edit` (1, `R.string.streams_edit`) added only when `source.sourceOrigin == "MANUAL"`, `Send link` (2, `R.string.streams_send_link`), `Remove` (3). Route the new ids in `setOnMenuItemClickListener` to `onEdit(source)` / `onShareLink(source)`. Add the matching `ID_EDIT`/`ID_SHARE_LINK` constants. Update the class KDoc to note the overflow is the canonical secondary-action surface. Keep the existing right-click-opens-overflow and long-press-removes behaviour unchanged (transitional duplicates).

**Verification:**

- `Grep` - `onEdit:` and `onShareLink:` present in the constructor of `StreamSourceAdapter.kt`.
- `Grep` - `R.string.streams_edit` and `R.string.streams_send_link` present.
- `Grep` - `sourceOrigin == "MANUAL"` present (Edit visibility guard).
- `Grep -n "Log\.d\("` on the file returns zero hits.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 4/4 PASS. Files: StreamSourceAdapter.kt (+~18 LOC). Dev log recorded.

---

### Step 03.2 - Add `onEdit` to the ViewModel

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsViewModel.kt`
**Depends on:** Phase 01, Step 03.1

**Prompt for developer:**

> Inject `UpdateStreamSourceUseCase`. Add `fun onEdit(source: StreamSourceEntity, url: String, title: String?)` that launches in `viewModelScope` and, on `UpdateStreamSourceUseCase.UpdateResult.InvalidUrl`, sends `StreamsEvent.Message(R.string.streams_error_invalid_url)` (reuse the existing add-path error); ignore `Success`/`NotEditable` (the UI already gates Edit to manual rows). Match the `onAdd` style.

**Verification:**

- `Grep` - `updateStreamSource` (the injected use-case property) present in `StreamsViewModel.kt`.
- `Grep` - `fun onEdit(` matches exactly once.
- `Grep` - `UpdateResult.InvalidUrl` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 3/3 PASS. Files: StreamsViewModel.kt (+~8 LOC). Dev log recorded.

---

### Step 03.3 - Wire the edit dialog and the share intent in the Activity

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/streams/StreamsActivity.kt`
**Depends on:** Step 03.1, Step 03.2

**Prompt for developer:**

> Pass `onEdit = ::showEditDialog` and `onShareLink = ::onShareLink` to the `StreamSourceAdapter`. Add `showEditDialog(source)`: inflate `DialogAddStreamBinding`, keep `tilTitle` visible, pre-fill `etUrl` with `source.url` and `etTitle` with `source.title`, set the dialog title to `R.string.streams_edit_dialog_title`, and on OK call `viewModel.onEdit(source, etUrl.text..trim(), etTitle.text..)`; apply `DialogKeyboardDelegate` like `showSourceDialog`. Add `onShareLink(source)`: build `Intent(ACTION_SEND)` with `type = "text/plain"` and `EXTRA_TEXT = source.url`, wrap with `Intent.createChooser(intent, getString(R.string.streams_share_chooser_title))`, and `startActivity`. Do not refactor `showSourceDialog`; the edit dialog may share its keyboard-delegate helper but must keep the add path intact.

**Verification:**

- `Grep` - `fun showEditDialog(` and `fun onShareLink(` each match exactly once in `StreamsActivity.kt`.
- `Grep` - `onEdit = ::showEditDialog` and `onShareLink = ::onShareLink` present in the adapter construction.
- `Grep` - `Intent.createChooser(` and `R.string.streams_share_chooser_title` present.
- `Grep` - `viewModel.onEdit(` present.

**Status:** `[x]` done

**Step Log:**

- 2026-06-24 - Verification 4/4 PASS. Files: StreamsActivity.kt (+~40 LOC, incl. 2 S0660 debug probes). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Manual smoke (reasoned, not on-device here): Edit item appears only on manual rows; Send link opens a chooser; long-press remove and the pin button still work.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The overflow now carries home-screen / edit (manual-only) / send-link / remove. Phase 04 records the capability in `docs/ALL_FEATURES.jsonl` and regenerates the catalog (new `UpdateStreamSourceUseCase`). The single-flow `Timber.d("S0660: ..")` probe required by `BlockNeedUserTest` is inserted by `/spec-dev` at the final transition, not in these phases.

---

## Rollback Plan

Revert the phase commit(s) - UI-only change over existing classes; no data migration. The Phase 01 data path remains dormant if reverted.
