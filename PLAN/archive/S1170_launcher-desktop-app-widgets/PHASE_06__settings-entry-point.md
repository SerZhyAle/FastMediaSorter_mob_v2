# Phase 06 - Settings entry point

**Strategic spec:** [`../S1170_launcher-desktop-app-widgets.md`](../S1170_launcher-desktop-app-widgets.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 03
**Blocks:** Phase 07
**Steps done:** 3 / 3
**Started:** 2026-07-30
**Completed:** 2026-07-30

---

## What this phase added beyond the plan (2026-07-30)

**Spans had to move into `src/main`, and that is a real design consequence, not a detail.** The placing code lives in Settings (`src/main`); `LauncherGadgetRegistry` and every gadget ship only in `src/launcherEnabled` and are invisible from there. So the cell's default footprint could not be read off the gadget. `HomeWidgetEntry` gained `gadgetSpanW` / `gadgetSpanH`, taken from each widget's own `targetCellWidth` / `targetCellHeight` - flavor-neutral data that already describes the widget. The alternative would have been a `BuildConfig` guard in `src/main` (banned, Rule 14) or a silent second copy of the numbers.

**New files not in the plan's list**

- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/launcher/PlaceHomeWidgetOnLauncherDesktopUseCase.kt` - resolving the orientation, its column count and the cell shape is domain work; the helper's job ends at "the user picked this row".
- `app_v2/src/main/java/com/sza/fastmediasorter/widget/registry/HomeWidgetCatalog.kt` gained `entries()`, an ungated accessor for code that needs the static table rather than the user-facing offer.

**A `columns == 0` edge the plan did not name.** The desktop's column count is written when the launcher first measures its grid, so it is 0 on a device where launcher mode was enabled but the home screen never rendered. That is genuinely "the cell did not land", so it shares the no-room answer and message rather than inventing a column count the next real layout would contradict.

---

## Objective

Add the second Settings button that puts the picked widget on the launcher desktop, visible only when the launcher surface exists, using the free-slot placement from Phase 01.

---

## Prerequisites

- [ ] Phase 01 and Phase 03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/layout/fragment_settings_destinations.xml` | Modified | n/a |
| `app_v2/src/main/res/layout-land/fragment_settings_destinations.xml` | Modified | n/a |
| `app_v2/src/main/res/values{,-ru,-uk}/strings.xml` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/HomeWidgetSettingsHelper.kt` | Modified | ≤ 220 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/OperationsSettingsFragment.kt` | Modified | ≤ 500 |

> Landscape parity is mandatory: the existing button lives at `fragment_settings_destinations.xml:968` (portrait) and `:1199` (landscape). Both get the new button.

---

## Steps

### Step 06.1 - Add the button to both orientations with trilingual copy

**Files:** the two `fragment_settings_destinations.xml` files, `strings.xml` x3
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `MaterialButton` `@+id/buttonAddLauncherWidget` directly after `@+id/buttonAddHomeWidget` in the portrait layout and at the matching position in `layout-land`, styled identically to its neighbour (same style, same `@drawable/ic_add`). Add its label across EN/RU/UK in one `set-android-string.ps1 -Action add` call. The copy must distinguish the two destinations at a glance - the existing button targets the Android home screen, this one the app's own launcher desktop - and pass `docs/COMMUNICATION_POLICY.md` §2 and §6. No hardcoded `="#hex"` colour.

**Verification:**

- `Grep` - `buttonAddLauncherWidget` present in both `layout/` and `layout-land/` variants.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"` exits 0.
- `Grep` - `="#` returns zero hits on the added lines.
- Strings pass the `COMMUNICATION_POLICY` §6 checklist.

**Status:** `[x]` done - `expected: strings present in en/ru/uk | actual: OK, all keys present, exit 0`. Three keys added: the button label plus the two outcome messages, since a placement that silently did nothing would read as success.

---

### Step 06.2 - Gate visibility on the launcher surface

**Files:** `HomeWidgetSettingsHelper.kt`, `OperationsSettingsFragment.kt`
**Depends on:** Step 06.1

**Prompt for developer:**

> Take the launcher-mode signal the settings layer already uses rather than inventing one: `LauncherModeContract.isAvailableInBuild` decides whether the surface exists in this build at all (it is false in lite/photos/legacy), and `LauncherRoleManager.isModeEnabled()` decides whether the user actually turned launcher mode on - `GeneralSettingsLauncherHelper` shows the precedent for both. Hide the button when the surface is absent; the strategic spec asks for "visible when launcher mode is on". Because this button lives in `src/main` and the launcher ships only in `standard`/`noLegal`, the gate must be the injected contract, never a `BuildConfig` flavor guard. Also register the row in the settings-search gate the same way `rowLauncherSettings` is registered in `SettingsSearchCapabilityGate`, so search cannot surface a control the build does not have.

**Verification:**

- `Grep` - `LauncherModeContract` referenced in the helper or fragment for this button.
- `Grep` - `BuildConfig.` returns zero new hits in either touched file.
- `Grep` - the new button id appears in `SettingsSearchCapabilityGate`.

**Status:** `[x]` done - gated on both axes: `LauncherModeContract.isAvailableInBuild` for "this build has the surface" and `LauncherRoleManager.isModeEnabled()` for "the user actually turned it on". The search gate mirrors only the build axis, deliberately - the runtime one is a live user toggle and this gate answers per build.

---

### Step 06.3 - Place the picked widget on the desktop

**Files:** `HomeWidgetSettingsHelper.kt`
**Depends on:** Step 06.2

**Prompt for developer:**

> Reuse `showPickerDialog` and its `ListSelectionDialog<HomeWidgetEntry>` unchanged - the owner asked for the same list, and duplicating it would let the two lists drift. Branch only on what happens after selection: the existing button pins to the Android home screen through `HomeWidgetPinner`; the new one builds a `LauncherCell` of kind `GADGET` whose `target` is the entry's Phase 03 `gadgetKey`, takes its spans from the registered gadget, and calls `addCellInFirstFreeSlot` from Phase 01. Place into the orientation the device is currently in. Report the outcome to the user: confirm placement, and say so plainly when the desktop had no room (`addCellInFirstFreeSlot` returned null) instead of failing silently. Both messages are new trilingual strings. This is a UseCase-level operation, so route it through the domain layer rather than calling the repository from the helper.

**Verification:**

- `Grep` - `addCellInFirstFreeSlot(` called exactly once in the settings path.
- `Grep` - `showPickerDialog` still has a single definition - the two buttons share it.
- `Grep` - the null return is handled with a user-visible message, not swallowed.
- `pwsh -NoProfile -File scripts/check_strings_localized.ps1` exits 0.

**Status:** `[x]` done - one `showPickerDialog`, one `ListSelectionDialog`, one loader; only `onSelected` branches on a private `Destination` enum. `addCellInFirstFreeSlot` is reached solely through the new UseCase, so the settings layer never touches the repository (Rule: UI carries no business logic).

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] Project compiles - run `/build` for `standard`, and for one flavor without the launcher (`lite`) to prove the gate compiles where the surface is absent.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] `check_strings_localized.ps1` exits 0.
- [x] Settings docs regenerated (CLAUDE.md Rule 22). It counted, and the gate had to be run three times before it went green - each stage caught a different missing artefact, which is exactly why the phase file says to act on its verdict rather than assume: `manifest-fresh` (regenerated in generate mode), then `annotations` (the new key needed an en/ru/uk entry in `settings-annotations.json`), then `reference-fresh` (`render-settings-reference.ps1`). Final verdict: `settings-doc-sync: OK - catalog complete, manifest fresh, annotations covered, reference up to date, HOW_TO recipes in sync`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings. The helper's two coroutines are `viewLifecycleOwner.lifecycleScope` and both re-check `fragment.isAdded` after suspending, so neither touches a detached view; no listener is registered without the button's own lifetime bounding it.

---

## Handoff Notes to Next Phase

This is the first code path that persists a home-widget `gadgetKey` into a desktop cell. From here the keys are a live storage format.

---

## Rollback Plan

Revert the phase commit and remove the new strings. Cells already placed by a test build would remain in the database with a key the registry still resolves, so no cleanup migration is needed.
