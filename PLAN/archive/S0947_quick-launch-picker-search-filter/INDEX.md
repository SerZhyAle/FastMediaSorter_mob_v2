# S0947 - Tactical plan: canonical single-choice picker with conditional quick search-filter

**Ticket:** S0947
**Status:** Tactical
**Strategic spec:** `PLAN/S0947_quick-launch-picker-search-filter.md`
**Owner contract:** strategic §3.3 + §4.1 (portrait+landscape, TV/D-pad, highlight+autoscroll to selected on open, search shown only when the list does not fit the screen, passive IME, case-insensitive `contains` on the primary label).

**Status (2026-07-09):** Phases 01-02 done (code) + Phase 03 scoped per owner decision; Phase 04 device-verification pending -> `BlockNeedUserTest`. Owner decision 2026-07-09: keep the `ListSelectionDialog<T>` family (ListSelectionDialog / DestinationPickerDialog / ResourcePickerDialog) bespoke - it is a typed generic settings-picker with ~8 typed call sites; force-migration would erase types (plan Phase 03 "leave non-simple usage untouched"). Migrated: the 4 quick-launch pickers.

## Approach

Extend the existing `ui/dialog/SearchableOptionPickerDialog.kt` (S0580 ADR-1) into the one canonical simple single-choice picker, then migrate the fragmented simple pickers onto it. Do NOT build a new component (strategic §8.1). Scope boundary is the cost driver (strategic §8.2): only simple `text ± leading image` single-choice lists migrate; pickers with per-option descriptions / custom option views and the remote cloud-folder Activities keep bespoke UI.

## Migration inventory (live tree, verified 2026-07-06)

In scope (simple single-choice, migrate to canonical):
- `ui/applaunchpanel/edit/AppPickerDialogFragment.kt`
- `ui/applaunchpanel/edit/InternalRoutePickerDialogFragment.kt`
- `ui/applaunchpanel/edit/OsShortcutPickerDialogFragment.kt`
- `ui/applaunchpanel/edit/ResourcePickerDialogFragment.kt`
- `ui/dialog/ListSelectionDialog.kt`
- `ui/dialog/DestinationPickerDialog.kt`
- `ui/dialog/ResourcePickerDialog.kt`

Out of scope (keep bespoke - strategic §4.1/§8.2):
- `ui/dialog/DeviceProfilePickerDialogFragment` (per-option descriptions / custom view)
- `IconPickerBottomSheet` (grid, not a single-column list)
- `ui/dialog/ColorPickerDialog` (not a text±image list)
- `DropboxFolderPickerActivity` / `GoogleDriveFolderPickerActivity` / `OneDriveFolderPickerActivity` (remote-listing Activities, different retrofit shape)

## Phases

### Phase 01 - Canonical component enhancement (foundation)

Enhance `ui/dialog/SearchableOptionPickerDialog.kt` + its layout (`res/layout/` and `res/layout-land/` counterpart):
1. Conditional search-field visibility: after the list is laid out, measure whether all options fit the current viewport; show the search field only when they do not. Re-measure on `onConfigurationChanged` / rotation so the field appears/disappears correctly per orientation. Keep the field passive (no auto-IME) - matches current behavior.
2. Selected-option highlight + autoscroll: highlight the currently selected `Option.id` in the adapter and `scrollToPosition` (centered) on open.
3. Input coverage (Rule 16/17): confirm D-pad/TV focus order (search field <-> list), `nextFocus*`, and that the list is reachable and scrollable via D-pad; keep content inside `systemBars` + `displayCutout` safe bounds in both orientations.

Verification: `.\a.ps1 fc` compiles; component opens with a short list (no search field) and a long list (search field present) on the emulator; rotation toggles the field per fit.

### Phase 02 - Quick-launch family migration

Migrate the four `ui/applaunchpanel/edit/*PickerDialogFragment.kt` to construct `SearchableOptionPickerDialog` with their data mapped to `Option(id, label, flag?)` and wire the existing result callbacks. Remove the bespoke list/adapter code fully replaced by the canonical component. Preserve each fragment's public entry API so callers in the quick-launch edit flow are unchanged.

Verification: `.\a.ps1 fc`; each of the four pickers opens from the quick-launch panel edit flow and returns the same selection result as before.

### Phase 03 - Generic list-dialog migration

Migrate `ui/dialog/ListSelectionDialog.kt`, `ui/dialog/DestinationPickerDialog.kt`, `ui/dialog/ResourcePickerDialog.kt` (the simple single-choice cases) onto the canonical component; audit every call site so behavior/labels are unchanged. Leave any non-simple usage untouched and note it in `## Last Audit`.

Verification: `.\a.ps1 fc`; grep call sites (`catalog query.ps1`) to confirm no orphaned adapters/layouts remain; each migrated dialog returns the same result.

### Phase 04 - On-device verification (BlockNeedUserTest)

The owner contract is visual/interaction behavior that only a device confirms. Insert `Timber.d("S0947: <entry-point>")` probes at each migrated picker's open path (final code edits before the last build), set `BlockNeedUserTest`, and hand to `/spec-test-device` (device online). Confirm on device:
- search-filter present only for lists that overflow the screen, absent for short lists, correct across rotation;
- selected option highlighted and autoscrolled into view on open;
- D-pad navigation reaches search + list; portrait and landscape both correct;
- filter matches the primary label case-insensitively.

## Out-of-scope / residual (for `## Last Audit`)

- Cloud-folder picker Activities and `DeviceProfilePickerDialogFragment` intentionally not migrated.
- If any in-scope picker turns out to carry hidden per-option descriptions, record it and keep it bespoke rather than forcing the simple contract.
