# Phase 03 - ErrorDialog wiring + accessibility

**Strategic spec:** [`../S0378_system-info-dialog-compact-actions.md`](../S0378_system-info-dialog-compact-actions.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Bind every action to the new icon row in `ErrorDialog.kt`, remove the AlertDialog negative/neutral/positive button panel, keep the same handlers and call contract, and attach accessibility metadata (contentDescription + tooltip) to each icon-only button.

---

## Prerequisites

- [ ] Phase 02 ✅ Done (layout exposes the new ids).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt` | Modified | ≤ 240 |

---

## Steps

### Step 03.1 - Remove the AlertDialog button panel, bind the icon row

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt`

**Prompt for developer:**

> Drop `setNegativeButton`, `setNeutralButton`, and `setPositiveButton` from the builder. Build the dialog with `val dialog = AlertDialog.Builder(context).setTitle(title).setView(dialogView).create()`. Resolve the four new buttons via `findViewById` (`btnPrimary`, `btnInlineAction`, `btnCopy`, `btnClose`); remove the `btnSaveToFile` reference. Wire handlers, preserving existing behaviour:
> - `btnClose` -> `dialog.dismiss()`.
> - `btnCopy` -> `copyToClipboard(context, fullText)` (dialog stays open).
> - `btnPrimary`: if `actionButtonText != null && onActionClick != null` -> set `text = actionButtonText`, `icon = null`, `onClick { dialog.dismiss(); onActionClick() }` (labeled custom CTA, §6 item 4); else -> keep `ic_share` icon-only, `onClick { share fullText via ACTION_SEND chooser }`.
> - `btnInlineAction`: if `inlineActionButtonText != null && onInlineActionClick != null` -> set `icon = ic_copy_full_report`, `onClick { onInlineActionClick() }`; else -> keep `ic_create_text_file` icon, `onClick { saveErrorToFile(context, fullText) }`.
> Replace the final `builder.show()` with `dialog.show()` inside the same `try/catch (BadTokenException)` and return the dialog. Keep the `details` collapsible toggle logic untouched.

**Verification:**

- `Grep` - `setNegativeButton`, `setNeutralButton`, `setPositiveButton` all absent in the file.
- `Grep` - `findViewById<.*>(R.id.btnPrimary)` and `R.id.btnClose`, `R.id.btnCopy`, `R.id.btnInlineAction` present.
- `Grep` - `btnSaveToFile` absent.
- `Grep -n "Log\.d\("` - zero hits.

**Status:** `[ ]` not done

---

### Step 03.2 - Accessibility metadata for icon-only buttons

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt`

**Prompt for developer:**

> For each icon-only button set `contentDescription` and `androidx.appcompat.widget.TooltipCompat.setTooltipText(button, text)` from an existing string resource: `btnClose` -> `R.string.close`; `btnCopy` -> `R.string.copy_to_clipboard`; `btnPrimary` default (share) -> `R.string.error_dialog_share` (or `R.string.share`); `btnInlineAction` default (save) -> `R.string.error_dialog_save_to_file`, sensitive case (copy full report) -> `R.string.system_info_copy_full_report`. For the labeled custom-CTA `btnPrimary`, set `contentDescription = actionButtonText`. No new string resources are introduced. Verify each referenced key exists before use.

**Verification:**

- `Grep` - `TooltipCompat.setTooltipText` present (>= 1).
- `Grep` - `contentDescription` assigned for the icon buttons.
- `Grep` (strings) - `R.string.close`, `R.string.copy_to_clipboard`, `R.string.error_dialog_save_to_file`, `R.string.system_info_copy_full_report` keys exist in `values/strings*.xml`.

**Status:** `[ ]` not done

---

### Step 03.3 - Insert BlockNeedUserTest debug tag + build

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/ErrorDialog.kt`

**Prompt for developer:**

> As the final code edit before the build (the ticket is about to enter `BlockNeedUserTest`), insert one `Timber.d("S0378: error dialog shown with compact icon action row")` at the entry of `show(..)` after the activity-state guard. This is the operator's logcat probe; the `S0378:` prefix is reserved for this temporary tag only. Then build `standard debug`.

**Verification:**

- `Grep` - exactly one `Timber.d("S0378:` in the file.
- Build: `.\a.ps1 dq` (standard debug) - expected: BUILD SUCCESSFUL.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `standard debug` build passes (validates layout + icons + wiring + tag in one pass).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Catalog regenerated (Phase 04) - `ErrorDialog.kt` API unchanged (same public `show` signatures), so jsonl diff should be empty or metadata-only.

---

## Handoff Notes to Next Phase

All `ErrorDialog.show(..)` callers now render the compact icon row with no source change on their side. Public `show(..)` overloads keep their signatures (contract preserved). Phase 04 runs the mechanical closure.

---

## Rollback Plan

Revert `ErrorDialog.kt` and the two layout files (Phase 02) together - no data migration, no contract change, no persisted state.
