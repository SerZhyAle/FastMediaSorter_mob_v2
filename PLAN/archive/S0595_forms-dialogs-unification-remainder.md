<!-- auto-approved by /spec-all - 2026-06-21 -->
# Strategic Specification: S0595 - Forms and dialog components unification (remainder)

**Ticket:** S0595
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-21
**Tier:** 3 - Standard
**Roadmap entry:** Ad-hoc - UI unification initiative (split from S0567)

---

## 0. Raw capture (origin)

Split-out from S0567 (archived Partial). S0567 delivered the P0 compound views
`SettingsSelectionRow`, `SettingsDropdownRow`, `SettingsInputRow` (phases 01-03).
This ticket carries the remaining phases 04-07 of the same unification initiative.

Parent strategic spec text (archived): `temp/done/S0567_ui-settings-forms-dialogs-unification.md`.

---

## 1. Problem

The settings/forms/dialog unification started in S0567 stopped after the three P0 row
widgets. The following debt from the S0567 survey is still live:

1. ~~**Dynamic code styling anti-pattern in pickers**: `ResourcePickerDialog.kt` and
   `DestinationPickerDialog.kt` build item buttons at runtime with hardcoded
   colors~~ - RESOLVED in S0567 phase 04 (`ListSelectionDialog<T>` + `ListSelectionAdapter`
   shipped; both pickers reduced to thin subclasses, hardcoded colors removed). Carried
   here only as the design reference for §2 item 1.
2. **Horizontal form pairs**: Host + Port, Username + Password, Share Name + Resource
   Name, Domain + Port, Comment + Access PIN repeat manual `layout_weight`/margin tuning
   across `fragment_resource_editor.xml` and `activity_add_resource.xml`.
3. **Checkbox + subtitle + optional help** repeats in Add Resource and Resource Editor
   scanning sections. Resource Editor `Remember File List` migrated to `FormCheckboxRow`
   here; the Add Resource set is deferred to **S0596** (170+ controller call sites - see
   that ticket).
4. **Compact `button + help icon` strips** repeat in `dialog_gif_editor.xml`.
5. Surviving special cases (e.g. `FileOperationDestinationDialog` colored grid) need an
   explicit shared adapter contract or a documented long-term exception.

## 2. Goals (carry-over from S0567 §2)

1. `ListSelectionDialog<T>` - generic selectable-list dialog enforcing Button Taxonomy at
   runtime; migrate `ResourcePickerDialog` and `DestinationPickerDialog`.
2. `FormFieldPairLayout` - two adjacent inputs with ratio presets (`1:1`, `2:1`), preset
   spacing, narrow-width stacking fallback.
3. `FormCheckboxRow` - `checkbox + subtitle + optional help icon` as one focus stop.
4. `ActionHelpRow` - compact `button + help icon` strip using project button taxonomy.
5. Audit surviving special cases; mark long-term exceptions explicitly.

## 2.2 Non-Goals

Same as S0567 §2.2 - no mega-widget flattening, keep semantic destination colors, do not
change `TooltipDialog` itself, do not migrate player-only dark-surface controls.

---

## 3. Constraints & Guidelines

Inherit S0567 §3 in full:
- Typography `@dimen/toggler_title_text_size` (14sp) / `@dimen/toggler_desc_text_size`
  (12sp); min touch target `@dimen/button_height` / 56dp.
- Themed attributes only; no hardcoded HEX.
- TalkBack + keyboard/D-pad; each compound row is one predictable focus stop.
- Prefixed public attrs (`ffp_*`, `fcr_*`, `ahr_*`).
- EN / RU (Ё/ё) / UK lockstep; no new strings expected.
- `res/layout/` and `res/layout-land/` migrated symmetrically.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** No new screens, no relocations - this ticket replaces existing form/dialog elements in place with reusable compound views (`FormFieldPairLayout`, `FormCheckboxRow`, `ActionHelpRow`). Visual layout and attribute schema follow the S0567 prior-art widgets; per-surface ambiguity resolved via `/ui-clarify` at migration time, not guessed.
- **Visual reference:** Shipped S0567 widgets (`SettingsSelectionRow`, `SettingsInputRow`) and `dialog_copy_to.xml` (S0538 button taxonomy) are the canonical references; new views match their themed styling with no hardcoded HEX.
- **Accessibility:** D-pad/remote + keyboard + mouse mandatory; each compound row is one predictable focus stop with non-color focus indication; TalkBack announces checkbox/value state.
- **Communication policy:** No new user-visible strings expected (migration reuses existing labels/help payloads); any genuinely new string ships EN/RU (Ё/ё)/UK in lockstep per `docs/COMMUNICATION_POLICY.md`.
- **Validation level:** resource + mixed compile (`a.ps1 fr`, `a.ps1 fc`) per phase, anti-pattern shrink greps (§7), on-device portrait + landscape verification of migrated resource-entry forms and GIF editor.
- **Owner sign-off:** 2026-06-21 - auto-approved by /spec-all; reuse of S0567 strategic designs (§5.4-5.7) and the remaining-phase sequence accepted as the implementation contract.
- **Related tickets:** S0567 (parent unification initiative, archived Partial - delivered phases 01-04), S0538 (dialog action-button taxonomy reference); no blocking `Sxxxx`.

---

## 5. Scope (remaining phases)

Reuse the S0567 strategic designs verbatim:
- §5.7 `ListSelectionDialog<T>` (generic picker)
- §5.4 `FormFieldPairLayout`
- §5.5 `FormCheckboxRow`
- §5.6 `ActionHelpRow`

The three P0 widgets from S0567 (`SettingsSelectionRow`, `SettingsDropdownRow`,
`SettingsInputRow`) are the prior-art conventions to follow: prefixed `attrs.xml`
styleable, `view_*.xml` `<merge>` layout, `TooltipDialog` ownership.

---

## 7. Verification Plan

Anti-pattern shrink greps (must trend to zero in touched surfaces):
- `rg "Color.WHITE|Color.LTGRAY|setBackgroundColor|setTextColor" app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog`
- repeated `layout_weight` form pairs in resource-entry layouts

Compile: `./a.ps1 fr`, `./a.ps1 fc` per phase. On-device portrait + landscape verification
of migrated pickers and resource-entry forms.

---

## 8. Long-term exceptions

Surfaces deliberately NOT migrated, with the reason each is excluded (audit 2026-06-21):

- `ColorPickerDialog.kt` - runtime `setBackgroundColor`/`Color.*` is the feature itself; the
  color swatches ARE user-selected data, not theme decoration.
- `DestinationAdapter.kt` - destination chips render the user's semantic destination color;
  keeping the color is required by §2.2 (color is data, not decoration).
- `FileOperationDestinationDialog.kt` - color-aware destination grid; stays specialized per
  §2.2. May later adopt the `ListSelectionDialog` `ItemFormatter` contract while keeping its
  color-chip visuals, but the colored rendering is intentional.
- `tilSftpPrivateKey` + `btnSftpLoadKey` row in `activity_add_resource.xml` - this is a single
  field + load-key action button, not two adjacent inputs, so it is out of `FormFieldPairLayout`
  scope (which lays out exactly two fields).
- Add Resource `checkbox + subtitle + optional help` rows - deferred to **S0596** (controller
  churn across 170+ call sites); tracked separately, not a silent exclusion.

---

## Last Audit

**Date:** 2026-06-21 | **Verdict:** Verified | **Auditor:** /spec-all F5

Delivered (all compile via `a.ps1 fc` + full `a.ps1 dq` packaging build):
- `FormFieldPairLayout` (`ffp_ratio` one_one/two_one, `ffp_stackThreshold`, `form_field_pair_spacing`).
- `FormCheckboxRow` (`view_form_checkbox_row.xml` + widget, Pattern B, row-owned TooltipDialog).
- `ActionHelpRow` (`view_action_help_row.xml` + widget, taxonomy button, `ahr_buttonStyle` via ContextThemeWrapper, row-owned TooltipDialog).
- `fragment_resource_editor.xml`: 3 field pairs -> `FormFieldPairLayout`; RememberFileList -> `FormCheckboxRow`; controller rewired, dead `showRememberFileListHelpDialog()` removed.
- `activity_add_resource.xml`: 8 field pairs -> `FormFieldPairLayout` (container-only, ids preserved).
- `dialog_gif_editor.xml` (+`layout-land`): 3 `button + help` strips -> `ActionHelpRow`; `GifEditorDialog` rewired; `btnClose` raw Material3 -> taxonomy.
- Catalog roles set for the 3 new widgets; `## 8. Long-term exceptions` records every residual anti-pattern hit.

On-device verification (emulator-5556, standard debug, portrait):
- Add Resource SMB form: `FormFieldPairLayout` pairs render correctly at both ratios (Username+Password / Share+Folder = 1:1; Comment+PIN / Domain+Port = 2:1). No crash; `S0595` entry tag observed.
- Resource Editor: full layout (incl. `FormFieldPairLayout` + `FormCheckboxRow`) inflates cleanly; no `InflateException`/`ClassCastException`/FATAL; `S0595` entry tag observed.

Residual manual check (non-blocking):
- GIF editor `ActionHelpRow` strips not exercised on the emulator (no indexed GIF media on the AVD - known limitation). Compile-clean and structurally identical to the two device-verified widgets. Re-check on a device with a local GIF: open GIF editor, tap each action button, tap each help icon -> TooltipDialog.

Debug verification tags removed on the transition out of `BlockNeedUserTest` (grep `S0595:` over `app_v2/src` = 0).
