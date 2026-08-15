# S0534 - Collapsible groups in the folder selection dialog

**Ticket:** S0534
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-19
**Tier:** 1 - Quick Win (ad-hoc)

> **Scope:** PRIMITIVE. Direct implementation, no tactical pipeline.

## Problem

In the local folder selection dialog ("Выбрать папку") the two top groups - "Специальные папки" and "Быстрый выбор стандартных папок" - are always fully expanded. They occupy most of the dialog height and push the manual-path input and the system picker below the fold. The user wants both groups collapsible, matching the existing collapsible-section pattern already used in Settings.

## Approach

- `res/layout/dialog_folder_selection.xml`: replace the two bold `TextView` section headers with `CollapsibleSectionHeader` (titles `special_virtual_folders` / `quick_select_common_folders`, `csh_expanded="true"`); wrap each group's button rows in a container `LinearLayout` with an id (`containerSpecialFolders` / `containerQuickFolders`) toggled by its header.
- `res/layout-land/dialog_folder_selection.xml`: identical change in the landscape counterpart.
- `ui/addresource/AddResourceScanManager.kt`: in `showFolderSelectionDialog`, wire each header to its container; default expanded; persist per-section expanded state in the shared `settings_section_states` prefs (keys `folder_picker_special_expanded`, `folder_picker_quick_expanded`) so a collapse choice survives reopening the dialog.

Default expanded preserves current behaviour; collapse is opt-in and remembered.

## Done criteria

- Tapping the "Специальные папки" header collapses/expands its six virtual-folder buttons; portrait and landscape both work.
- Tapping the "Быстрый выбор стандартных папок" header collapses/expands its quick-folder buttons; portrait and landscape both work.
- A collapsed group stays collapsed after closing and reopening the dialog.
- Capability-based hiding of individual virtual buttons still applies inside the collapsible container.

## Last Audit

**Date:** 2026-06-19
**Mode:** full
**Outcome:** Verified
**Counts:** PASS 4 (device) + 3 (static: both layouts + persistence keys) · WARN 0 · FAIL 0

Static checks: `containerSpecialFolders`/`containerQuickFolders` present in both `res/layout/` and `res/layout-land/dialog_folder_selection.xml`; `folder_picker_special_expanded`/`folder_picker_quick_expanded` persisted in `AddResourceScanManager`. Debug tag removed on Verified flip.

### Manual device test - 2026-06-19

- Device: emulator-5556 (Pixel 6, Android 13), standard debug v2.60.6191.257.
- Flow: Add Resource -> Add Local Folder -> Add Manually -> "Select Folder" dialog.
- Probe `Timber.d("S0534: opening folder selection dialog with collapsible sections")` fired on each of the 3 dialog opens.

PASS - Special Folders header collapse/expand (portrait).
- Expected: tapping the header hides/shows its 6 virtual buttons; prefix toggles between collapsed/expanded.
- Actual: prefix toggled `▼` -> `▶` -> `▼`; `containerSpecialFolders` and all 6 buttons (Recent Media, All Music, All Videos, Camera Photos, All Images, All Documents) left/re-entered the view tree accordingly.

PASS - Quick Select Common Folders header collapse/expand (portrait).
- Expected: tapping the header hides/shows its quick-folder buttons, independent of the other section.
- Actual: prefix toggled `▼` -> `▶`; `containerQuickFolders` and all quick buttons (Root, DCIM, Pictures, Download, Documents, WhatsApp, Telegram, Instagram) collapsed while Special Folders stayed expanded.

PASS - Collapsed state persists across close/reopen.
- Expected: collapsing both groups, dismissing the dialog (CANCEL) and reopening shows both groups still collapsed.
- Actual: both headers `▶` and both containers absent on reopen; state also held across an orientation change.

PASS - Landscape (res/layout-land counterpart).
- Expected: both headers render and toggle independently in landscape.
- Actual: dialog opened in landscape with both `CollapsibleSectionHeader`s; Special Folders expanded to a 2-column grid of 6 buttons; Quick Folders expanded independently with Special Folders left collapsed.

Evidence: temp/S0534_devtest/ (01..08 *.png).
