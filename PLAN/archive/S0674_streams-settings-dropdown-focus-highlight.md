# S0674 - Streams settings dropdown rows lack a visible focus indication

**Status:** Archived

## 0. Raw capture (auto-parked, do not lose)

Parked from S0673 closure: the `focus-highlight` ratchet gate (Rule 16, `scripts/quality/assert-focus-highlight.ps1`) reports `baseline 0 | actual 6 | delta +6`, all in the streams settings fragment - unrelated to the S0673 streams-catalog empty-state change.

Gate `-List` output:
- `app_v2/src/main/res/layout/fragment_settings_streams.xml:29  <SettingsDropdownRow>`
- `app_v2/src/main/res/layout/fragment_settings_streams.xml:40  <SettingsDropdownRow>`
- `app_v2/src/main/res/layout/fragment_settings_streams.xml:51  <SettingsDropdownRow>`
- `app_v2/src/main/res/layout-land/fragment_settings_streams.xml:29  <SettingsDropdownRow>`
- `app_v2/src/main/res/layout-land/fragment_settings_streams.xml:40  <SettingsDropdownRow>`
- `app_v2/src/main/res/layout-land/fragment_settings_streams.xml:51  <SettingsDropdownRow>`

Evidence: each `SettingsDropdownRow` (rowDefaultSort, rowDefaultMediaFilter, rowCatalogRefresh) carries `android:focusable="true"` + `android:clickable="true"` in XML, so the gate treats the wrapper as an interactive control, but the wrapper renders no focus indication (no background highlight, not in the gate's intrinsic/whitelist sets). The real focus target is the inner `AutoCompleteTextView`, which renders its own focus.

## 1. Symptom

- A pre-existing Rule 16 gap: D-pad/TV/keyboard focus on these dropdown rows shows nothing visible on the wrapper.
- The ratchet gate is now above baseline, so it will fail on any future closure that runs the gate until resolved.

## 2. Suspected resolution paths (decide during research)

- Mirror the whitelisted sibling `SettingsToggleRow`: have `SettingsDropdownRow` apply `?attr/selectableItemBackground` (or a focus selector) on the row in its constructor, then add `SettingsDropdownRow` to `$customFocusViews` in `assert-focus-highlight.ps1` (the gate cannot see a constructor-set background). Affects every dropdown row app-wide - needs a quick visual review.
- Or drop the redundant `android:clickable="true"`/`android:focusable="true"` on the wrapper and let the inner field be the D-pad stop - check this does not break row-level D-pad navigation.
- Whichever is chosen, re-run `assert-focus-highlight.ps1 -UpdateBaseline` to ratchet back to 0.

## 3. Scope

- In: the 3 `SettingsDropdownRow` instances in `fragment_settings_streams.xml` (+ `layout-land`), and any identical pattern in other settings fragments using the same widget.
- Out: the S0673 streams-catalog empty state (already done and clean).

## 4. Notes

- Not caused by S0673; surfaced by its post-change gate run. Likely introduced by the S0659/S0618 streams-settings work without a gate run.
- Verify whether other fragments place `focusable+clickable` on `SettingsDropdownRow`; if so this is a wider sweep, not just streams.

## 5. Resolution

Chosen path: drop the redundant wrapper interactivity (resolution path 2). Scope confirmed streams-only.

Rationale:
- The 3 `SettingsDropdownRow` wrappers carried `android:focusable`/`android:clickable` plus a `nextFocus*` chain, but the wrapper has no click action - `StreamsSettingsFragment` binds behaviour through the inner `AutoCompleteTextView` (`bindDropdown` -> `setOnItemSelectedListener`). The wrapper was a dead focus stop with no visible focus, which is the Rule 16 gap the gate flagged.
- Canonical usage proves the fix: `SettingsDropdownRow` in `fragment_settings_playback`, `fragment_settings_general` and the dialog layouts ships with NO `focusable`/`clickable`/`nextFocus` on the wrapper; the inner `AutoCompleteTextView` is the D-pad stop, renders its own focus, and is whitelisted as intrinsic-focus by the gate. Streams was the lone deviation. The gate's 6 hits were all streams - the authoritative proof that no other fragment shares the pattern, so no wider sweep.
- Path 1 (constructor `selectableItemBackground` + gate whitelist) rejected: it would make the wrapper LOOK focusable while still having no activate action - a highlighted focus trap - and would change every dropdown row app-wide.

Edits (portrait `layout/` + `layout-land/` parity):
- Remove `android:focusable` + `android:clickable` from `rowDefaultSort`, `rowDefaultMediaFilter`, `rowCatalogRefresh`.
- Remove the wrappers' own `nextFocusUp`/`nextFocusDown`.
- Remove the now-dangling neighbour refs that target the wrappers: `rowEnableStreams` `nextFocusDown` and `btnClearPlayStatuses` `nextFocusUp`. Default top-to-bottom focus order routes through the inner fields, matching playback/general.

Validation:
- `assert-focus-highlight.ps1 -List` -> 0 streams hits; gate `baseline 0 | actual 0 | delta 0` (PASS).
- `.\a.ps1 fr` -> `processStandardDebugResources` BUILD SUCCESSFUL (PASS) - XML valid, no dangling `@id`.

## Last Audit

**2026-06-25** - by `/spec-all` F5 (audit). Verdict: **Verified**.

- `rowDefaultSort` (l.29), `rowDefaultMediaFilter` (l.36), `rowCatalogRefresh` (l.43) in `fragment_settings_streams.xml` + `layout-land` parity: no `android:focusable`/`android:clickable`/`nextFocus*` on the wrappers (grep confirmed - remaining `focusable`/`clickable` sit only on legitimate toggle/button rows, none at l.29/36/43).
- `assert-focus-highlight.ps1`: `baseline 0 | actual 0 | delta 0` (PASS) - the +6 streams hits are gone.
- Matches the canonical working pattern (playback/general dropdown rows let the inner `AutoCompleteTextView` be the D-pad stop). Resolution §5 fully reflected in code. No residual gaps.
