# Research 01 - what the code actually does with sections

**Ticket:** S1742
**Date:** 2026-08-18
**Method:** read-only source survey (catalog query + targeted reads). Every claim carries file:line.

---

## 1. Section key identity

- Preset catalogue: `core/launcher/LauncherSectionCatalog.kt:17-31` - a fixed list of six `Section(key, labelRes)`, looked up by `byKey` (line 30).
- Unknown key: `ResolveLauncherCommandLabelUseCase.sectionVisual:381-384` returns `null`, by explicit KDoc contract (375-380) - "inventing a caption would leave a header naming nothing".
- **The label override does not rescue it.** `ResolveLauncherDesktopUseCase.toUi:72-89` applies `labelOverride` only inside `resolved?.let { .. }` (76-79), so a null visual stays null whatever the override says.
- Render: `LauncherCellViewBinder.bindSection:393-413` draws `R.string.launcher_home_cell_unavailable` when `visual` is null (400-401).
- Backup carries `target` and `labelOverride` verbatim (`BackupMapper.kt:650-677`, id reset to 0 at 666), so a user section's identity rides entirely on its `target` string.

**Consequence.** Strategic §4's premise - "пользовательская подпись ячейки уже перекрывает любую вычисленную" - is true for a *resolvable* key only. A user key is not resolvable, so §5.2's "rename writes the label override" is not sufficient on its own: either the resolver must produce a visual for an unknown section key, or the override must be allowed to stand alone. This is an implementation obstacle, not a research curiosity.

## 2. Block move semantics

- `LauncherDesktopRepositoryImpl.moveCell:357-399` is single-cell: header pinned to column 0 (365), refused across a header row (372-378), plain move when free (387-390), anchor swap when the blocker has an identical footprint (391-397, 411-414), refused otherwise.
- No operation anywhere moves a header together with the cells it owns.
- `LauncherCellDao.pushRowsDown:113-117` (S1772) shifts a contiguous tail down by a uniform delta in one statement; `findInRowBand:119-129` lists the cells reaching into a row band. Both are consumed only by single-cell seating (`LauncherDesktopRepositoryImpl.kt:60-129`). Neither relocates an isolated block, and there is no upward variant.
- Membership is positional and never stored: `LauncherSectionMembership.ownerOf:66-85` gives a cell to the last header preceding it; `sectionEndExclusive:169-179` ends a section at the next header row, or at the bottom for the last one.

## 3. Collapsed-state storage

- `ui/launcher/helpers/LauncherSectionCollapseManager.kt`, backed by `CollapsibleSectionStore` shared prefs (`ui/common/widget/CollapsibleSectionStore.kt:30-40`).
- Key format `keyFor(orientation, target)`:76-77 -> `launcher_desktop__<ORIENTATION>__<target>`, where `target` is the encoded command (`sec:widgets`).
- **No cleanup on delete.** `LauncherHomeViewModel.removeCell:429-433` calls only the repository's `removeCell`, which is `cellDao.deleteById` (`LauncherDesktopRepositoryImpl.kt:304-306`). The collapse entry is orphaned, and a later section reusing the same key string would silently inherit the old state.

## 4. Gesture ownership on the header

- Short press: `LauncherCellViewBinder.bindSection:410` -> collapse/expand.
- Long press: **no listener is attached at all** on the header path (`bindSection` KDoc 388-391 states the S1428 ban), and `nameLongPressForAccessibility` (314-315, 369-375) is never called for a section. The header consumes touch as a clickable view, so the gesture reaches neither the header nor the desktop's own long-press (`LauncherEditModeManager.kt:53-59`). It is a silent no-op by absence, not by message.
- Edit mode: `decorateForEdit:223-267` lays a transparent scrim over **every** cell, headers included, whose long-press starts a drag (236-245). The gesture is therefore already taken in edit mode - which is why the owner's ruling (§ decisions, item 8) confines section editing to normal unlocked mode.
- Accessibility: `announceSectionState:426-439` sets only the title plus collapsed/expanded state. There is no spoken claim about long-press to revise.

## 5. Pattern to follow for the actions sheet

`ui/launcher/signal/LauncherSignalListBottomSheet.kt` - a `BottomSheetDialogFragment` with a RecyclerView adapter, caller-supplied list and `onTap` set before `show()` (24-29), first-row focus for D-pad (46-50), dismiss on tap (83). Its KDoc (22) records that a picker carries no confirm/cancel pair, which is the same exemption a section-actions list needs.

## 6. Test coverage that exists today

`LauncherDesktopRepositoryImplTest.kt` and `LauncherSectionMembershipTest.kt` cover `moveCell` and membership. No test exists for `LauncherSectionCollapseManager` or for `bindSection`/`decorateForEdit`.
