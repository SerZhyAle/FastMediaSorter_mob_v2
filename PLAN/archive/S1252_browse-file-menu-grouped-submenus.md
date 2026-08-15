# S1252 - The Browse file overflow menu is too long to scan; group its actions into submenus

**Status:** Archived
**Priority:** 55

<!-- auto-approved by /spec-all - 2026-07-28 -->


## 0. Raw capture

Owner request, 2026-07-28, verbatim:

> ниспадающее меню для файлов в Browse настолько большое (число пунктов), что нужно в нем сделать оаскрывающиеся субменю, выделив несколько групп операций

## 1. Where it lives

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/helpers/BrowseFileOverflowMenuManager.kt` (227 LOC) builds the menu.
- Two sources feed one flat list: direct `MenuItem` entries appended in the manager, and a block of `PlayerCommand` values appended conditionally per file type.

## 2. How long it actually gets

Counted from the current source, not estimated.

- Direct entries: open, open in VR cinema, copy, move, rename, delete, move up, move down, extract archive, open in separate window - **10**.
- `PlayerCommand` entries: up to **~30** more, gated by file type and settings (`isPdf`, `isText`, `isEpub`, `isImage`, `isAudio`, `isVideo`, `isWritable`, `enableTranslation`, `enableOcr`).

The gating means no single file shows all 40, but the worst realistic cases are bad: a writable PDF with translation and OCR enabled, or a writable image with both, each clear 20 visible items in one flat scrolling list.

## 3. The grouping the request implies

Natural clusters already visible in the source order - these are a starting point for the spec, not a decision:

- File operations: copy, move, rename, delete, move up, move down, extract archive.
- Open / view: open, open in VR cinema, open in separate window, favorite.
- Text and document work: search, translate, OCR, text settings, copy text - currently repeated four times over as near-identical PDF / text / EPUB / image variants.
- Editing: edit, crop, crop to file, draw overlay, save frame, compress copy.
- Share and metadata: send to, info, print, lyrics, search on YouTube Music.

The per-format repetition in the text block is worth attention on its own: `SEARCH_*`, `TRANSLATE_*`, `*_TEXT_SETTINGS` and `OCR_*` exist in four parallel sets that never appear together, so a single "Text" submenu whose entries resolve to the right command per file type may shorten the list more than any other single move.

## 4. Open questions for research

- Which submenu widget: nested `PopupMenu` (`SubMenu`, supported but shallow and unstyled), a bottom sheet with expandable sections, or a two-level custom dialog. The existing dialog-action-pair standard (CLAUDE.md 11) and the app's own bottom-sheet conventions should decide this, not novelty.
- Whether the same grouping must land in the player's own overflow menu, which shares the `PlayerCommand` vocabulary - a split where Browse groups and the player does not would teach two different maps for one command set.
- Keyboard / D-pad / TV traversal into and out of a submenu (CLAUDE.md Rule 16), and what "back" does at the second level.
- Whether frequently-used actions should stay at the top level rather than being buried one tap deeper. Grouping reduces scanning but adds a tap to everything it hides.
- Landscape and `layout-land` behaviour: a two-level menu that fits portrait may not fit a short landscape viewport.

## 4a. The signature is already over the line

Found while implementing **S1233**, which had to thread the surrounding folder into the VR entry: `showFor` now takes **21 parameters** against a detekt limit of 8, and carries a cyclomatic complexity of 30 against a limit of 20. Both were already baselined before S1233; the point is that this menu has outgrown a parameter list, and every future item makes it worse.

Whatever grouping this ticket lands, the same change should replace the flat parameter list with a context object plus an action map - otherwise the restructure fixes the menu the user sees and leaves the one the code sees untouched.

## 5. Not decided here

This is an inbox capture. No grouping, widget, or ordering is committed - section 3 records what the source already suggests so the research does not start from a blank page.

## 6. Research findings (2026-07-28, spec-next loop)

§4's open questions resolved from the codebase:

- **Widget precedent exists - native nested submenu.** The player's overflow already builds
  «Send to..» as a native `SubMenu` inside its `PopupMenu` (S0459 ADR-2,
  `CommandPanelController.Callback.onSendToOverflowSubMenuRequested`). So a two-level menu is
  not novel UI for this app; the novel part would only be inventing a *different* two-level
  widget. Browse's current `ListPopupWindow` (flat `ArrayAdapter`, custom `measurePopupWidth`)
  has no submenu support - the move is Browse adopting the player's `PopupMenu` + `SubMenu`
  pattern, not the other way around.
- **Bottom sheets are the app's convention for pickers, not for row overflow menus**
  (`SendToBottomSheet`, `IconPickerBottomSheet`, `PdfThumbnailSheet`, `NowPlayingBottomSheet` -
  all standalone pickers/surfaces; no file-row overflow uses one). A sheet would also detach
  the menu from its anchor row, which matters on tablets/large screens.
- **D-pad / keyboard (Rule 16) favours the native widget:** `PopupMenu`/`SubMenu` traversal,
  submenu enter/back and dismissal are framework behaviour; a hand-rolled two-level
  `ListPopupWindow` would need all of it re-implemented (the S1263 class of bug lives exactly
  there).
- **Player parity (§4 q2):** the player's overflow is priority-ordered with one submenu
  (Send to); its vocabulary overlaps but its context differs (open session vs file row).
  Re-grouping the player's overflow is a follow-up decision after Browse lands, not part of
  this ticket - recorded in §9.
- **Landscape (§4 q5):** native `PopupMenu` scrolls and repositions itself; no layout-land
  counterpart exists for either widget (both are runtime windows), so no Rule 11 pairing is
  involved.

## 7. Decisions

- **Widget:** migrate `BrowseFileOverflowMenuManager` from `ListPopupWindow` to `PopupMenu`
  with native `SubMenu` groups - the S0459 ADR-2 pattern, one mental model with the player.
- **Grouping (default, §3 adjusted for "frequent stays on top"):**
  - Top level (no extra tap): Open, Open in VR Cinema, Copy, Move, Rename, Delete.
  - `Organize` submenu: Move up, Move down, Extract archive, Favorite, Open in separate window.
  - `Text` submenu: Search, Translate, OCR, Text settings, Copy text - entries resolve to the
    right per-format `PlayerCommand` (PDF/text/EPUB/image) for the current file, collapsing the
    four parallel sets §3 calls out.
  - `Edit` submenu: Edit, Crop, Crop to file, Compress copy, Draw overlay, Save frame.
  - `Share & info` submenu: Send to, Info, Print, Lyrics, Search on YouTube Music.
  - A submenu with zero visible entries for the current file is not added; a submenu with
    exactly one entry hoists that entry to the top level (no single-child groups).
- **§4a lands in the same change:** `showFor`'s 21 parameters collapse into a
  `BrowseFileMenuContext` (file, siblings, settings, flags) plus a `BrowseFileMenuActions`
  callback holder; the 30-branch conditional list stays the single source of item visibility.
- **Top-level set is the recommended default, not a hard rule** - if the owner wants a
  different split, the grouping table above is one map edit; the structure does not change.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1233 (VR entry threading through this menu), S0459 (Send to.. + ADR-2
  submenu precedent), S1263 (dropdown content sinking - same widget family), S1133/S0964 (VR
  surfaces excluded from scope)

## 8. Phases (tactical outline)

1. Introduce `BrowseFileMenuContext` + `BrowseFileMenuActions`; mechanical caller migration in
   `BrowseManagerInitializer`/`MediaFileAdapter` call path; behaviour identical (flat list).
2. Replace `ListPopupWindow` with `PopupMenu` + `SubMenu` groups per §7; keep item gating
   logic verbatim; drop `measurePopupWidth` (framework sizing).
3. Emulator verification: worst-case writable PDF/image with translation+OCR on (top level
   6-8 items + 3-4 groups), D-pad in/out/back, landscape, plus regression tap-through of every
   top-level action.

## 8.1 Implementation (2026-07-28, spec-next loop)

All three §8 phases landed in one pass:

- `BrowseFileMenuContext` + `BrowseFileMenuActions` replace the 21-parameter `showFor`;
  `BrowseManagerInitializer`'s call-site migrated (its own 37-parameter constructor growth is
  pre-existing debt, parked as S1269 - baseline records 34).
- `ListPopupWindow` + `measurePopupWidth` deleted; native `PopupMenu` with `SubMenu` groups per
  §7; gating verbatim (`buildExtendedCommands` kept its exact baseline signature). Per-format
  Text entries reuse each command's own localized title - no second label set. Four new group
  strings (`browse_menu_group_*`), EN/RU/UK parity green.
- Emulator verification (emulator-5554 tablet, 19:09-19:12): writable jpg shows Open + hoisted
  Favorite + hoisted Text settings + `Правка` (5) + `Отправка и сведения` (3) - single-child
  hoist and empty-group drop both observed; submenus cascade beside the parent; Back closes one
  level at a time; "Информация о файле" tap-through opens the info dialog; D-pad DOWN x4 +
  CENTER enters the submenu, Back x2 returns to Browse (Rule 16). Probe
  `S1252: grouped menu sizes=[1, 1, 1, 5, 3]` in logcat, twice.
- Gates: fk PASS, scoped detekt PASS on both new/rewritten files (initializer blocked only by
  the pre-existing S1269 constructor finding), string parity PASS.

Worst-case (translation+OCR on) not reproduced on the device - those toggles are off in the
test profile; grouping of the extra items is compile-time (`groupFor`) and covered by the
verbatim gating.

## Last Audit

Device test 2026-07-29, emulator-5556 (sdk_gphone64_x86_64, Android 13 / API 33, 1080x2400
@420dpi, phone), installed build `2.60.7262.102-DEBUG` - no rebuild, no install. Locale EN,
profile "Personal smartphone", OCR and Translation both ON, so the §8.1 gap (worst case with
translation+OCR) is now covered on a device. Every menu level was enumerated from a
`uiautomator dump`, not read off a screenshot. Evidence: `temp/S1252/menu-inventory.txt`,
`temp/S1252/logcat_probe.txt`, `temp/S1252/dumps/*.xml`.

Per criterion:

- **Top level = Open / VR / Copy / Move / Rename / Delete, subject to settings** - PASS.
  Expected the six §7 entries minus whatever the settings gate off; actual, on all four file
  types tested, `Open, Copy, Move, Rename, Delete` in that order and nothing else.
- **"Open in VR Cinema" gate** - N/A on this device, absence is correct. Expected the entry only
  when `vrCinemaLaunchManager.isAvailable`; actual, `pm list features` reports no XR/VR feature
  on this AVD, and the video row's menu carries no VR entry. The positive case needs XR hardware.
- **Four groups exist (Organize / Text / Editing / Share and info)** - PASS. All four were
  observed as real cascading submenus: Organize on a video in grid + Manual Order
  (`Move up, Move down, Favorite`), Text on a text file (`Search, Translate, Text Settings,
  Copy`) and on an image (`Translate, Text Settings, Extract Text`), Editing on an image
  (`Edit, Crop, Crop to file, Compressed copy, Draw`) and on a video (`Edit, Save Frame`),
  Share and info on a text file (`Send to.., File Information, Print`), an audio file
  (`+ Lyrics, In YouMusic`) and a video (`Send to.., File Information`).
- **A one-entry group is hoisted, not left as a submenu** - PASS. Expected the single child on
  the top level; actual, `Favorite` (Organize = 1) sits at the top level on the text, image and
  audio rows, and `Edit` (Editing = 1, `EDIT_TEXT`) sits there on the text row. Both render at
  full item width, i.e. no cascade arrow, so they are leaves rather than collapsed submenus.
- **A zero-entry group is dropped** - PASS. Expected no header at all; actual, the audio row's
  menu is 7 rows with neither Text nor Editing (`sizes=[5, 1, 0, 0, 4]`), and the video row's
  menu has no Text (`sizes=[5, 3, 0, 2, 2]`).
- **Probe `S1252` in logcat** - PASS. Ten `S1252: grouped menu sizes=[..]` lines, one per menu
  open, and every one matches the rendered level enumerated from the dump.
- **Actions still fire through the new structure** - PASS. `Share and info -> File Information`
  opened the info dialog for the right file, and top-level `Rename` opened the rename dialog
  prefilled with it. D-pad `DOWN x6 + CENTER` entered the Organize submenu (Rule 16).

Two deltas against §8.1, neither blocking:

- `COPY_TEXT` reuses `R.string.copy`, so the Text submenu's last entry reads exactly **"Copy"** -
  the same word as the top-level file-copy action, one tap away from it. §8.1 chose to reuse each
  command's own localized title precisely to avoid a second label set; this is the one place where
  that choice collides. A dedicated "Copy text" title for `COPY_TEXT` would settle it.
- **Back closes the whole popup, not one level.** §8.1 recorded one-level-per-press from the
  API-35 tablet run; on this API-33 phone a single BACK from an open submenu dismissed the entire
  menu and returned to the Browse list, 3 out of 3 attempts. Framework behaviour of the popup
  helper, not app code.

Not exercised here: "Extract archive" (no `.zip` on the device), "Open in separate window"
(`allowSeparateWindow` off in this profile), the PDF and EPUB Text groups (no such files), and
landscape geometry.

### Fix applied after the run (2026-07-29)

The duplicate-label delta above is fixed rather than deferred: `PlayerCommand.COPY_TEXT` in
`CommandPanelLayoutPlanner` now takes its `titleResId` from `copy_to_clipboard` instead of `copy`.

No new string key. `copy_to_clipboard` already existed in all three locales ("Copy to Clipboard" /
"Копировать в буфер" / "Копіювати в буфер") and already labelled the copy button of
`ScrollableTextDialog` - the same act on the same kind of object, so reusing it keeps one wording for
one meaning instead of inventing a second.

The change also reaches the player's overflow menu, which shares this enum and carried the same
ambiguity.

Still open: the label itself has not been seen on a device since the change. The four acceptance
criteria were all proven on the run above and are unaffected by a title-resource swap, but the new
wording is a user-visible string, so this ticket stays `BlockNeedUserTest` until one screenshot of
the Text submenu confirms it renders.

## 9. Follow-ups (not this ticket)

- Player overflow re-grouping to the same map (decide after living with Browse groups).
- S1263 (dropdown label sinking) remains a separate defect in the old widget family; the §7
  migration makes it moot for THIS menu but not for other dropdowns.
