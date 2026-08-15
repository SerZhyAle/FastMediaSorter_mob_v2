# Phase 02 - Menu manager and clipboard dialog

**Strategic spec:** [`../S0542_download-link-from-clipboard.md`](../S0542_download-link-from-clipboard.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Introduce two new helper classes: a menu-item builder/dispatcher for the main-window dropdown, and a manager that shows the clipboard-prefilled single-line dialog and hands the entered string to the existing external-link receiver. No `MainActivity` change yet - both classes compile standalone.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done (string keys exist).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLinkDownloadMenuManager.kt` | New | ≤ 70 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLinkDownloadManager.kt` | New | ≤ 140 |

---

## Steps

### Step 02.1 - Create `MainLinkDownloadMenuManager`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLinkDownloadMenuManager.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `MainLinkDownloadMenuManager`, mirroring the structure of `MainQuickCaptureMenuManager` in the same package. Constructor takes a single `onLinkDownload: () -> Unit` callback. Expose `itemCount(enabled: Boolean): Int` (1 when enabled, else 0), `populate(popup: PopupMenu, enabled: Boolean, startOrder: Int): Int` (adds one item with `R.string.download_by_link_menu_label` and an existing link/download drawable when `enabled`, returns count added), and `handleMenuItem(itemId: Int): Boolean` (dispatches to `onLinkDownload`). Use a private menu-item id constant `13` - clear of the existing ids (calculator 1, mini-game 2, camera-OCR 9, quick 10/11/12). For the icon, reuse an existing drawable that reads as a link/download; verify your chosen drawable exists before referencing it. No business logic beyond menu build + dispatch.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLinkDownloadMenuManager.kt` exists.
- `Grep` - `class MainLinkDownloadMenuManager` matches exactly once.
- `Grep` - `fun populate(` and `fun handleMenuItem(` and `fun itemCount(` each present.
- `Grep` - `R.string.download_by_link_menu_label` present.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

---

### Step 02.2 - Create `MainLinkDownloadManager` (clipboard dialog + receiver dispatch)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLinkDownloadManager.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Create `MainLinkDownloadManager` holding an `Activity` (or `AppCompatActivity`) reference. Expose `show()` that:
> 1. Reads the current text from the system clipboard (`ClipboardManager`, primary clip's first text item, coerced to text); empty string when none.
> 2. Builds a `MaterialAlertDialogBuilder` dialog titled `R.string.download_by_link_dialog_title` with a single-line `EditText` (or `TextInputEditText`) prefilled with the clipboard text, hint `R.string.download_by_link_dialog_hint`, `inputType` text/URI single-line.
> 3. Positive button `android.R.string.ok`: if the entered text is non-blank, synthesize an internal share intent - `Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, enteredText).setClass(activity, ReceiveShareActivity::class.java)` - and `activity.startActivity(intent)`. This routes the string into the existing external-link path ("received from outside" per strategic §5). Do not call the download engine, auth, or progress UI directly.
> 4. Negative button `android.R.string.cancel`: dismiss, no action.
>
> Comment discipline: explain only the non-obvious "synthesize a share intent to reuse the external receiver" decision (WHY), not the obvious lines. No empty/broad catch, no hardcoded hex, no `Log.d`.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainLinkDownloadManager.kt` exists.
- `Grep` - `class MainLinkDownloadManager` matches exactly once.
- `Grep` - `ClipboardManager` present.
- `Grep` - `Intent.ACTION_SEND` and `ReceiveShareActivity` both present.
- `Grep` - `R.string.download_by_link_dialog_title` and `R.string.download_by_link_dialog_hint` present.
- `Grep -n "Log\.d\("` returns zero hits in the file.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for both new files via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

`MainLinkDownloadMenuManager(onLinkDownload)` and `MainLinkDownloadManager(activity)` exist and compile. Phase 03 instantiates both in `MainActivity` and wires the menu + visibility.

---

## Rollback Plan

Delete the two new files - no other code references them until Phase 03.
