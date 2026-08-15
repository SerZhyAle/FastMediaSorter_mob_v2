# Phase 02 - ActionHelpRow + special-case audit

**Strategic spec:** [`../S0595_forms-dialogs-unification-remainder.md`](../S0595_forms-dialogs-unification-remainder.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-06-21
**Completed:** 2026-06-21

---

## Objective

Introduce `ActionHelpRow` (`button + help icon`, project button taxonomy), migrate the repeated GIF-editor action/help strips, and record explicit long-term exceptions for the surviving specialized surfaces.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/attrs.xml` | Modified | +10 |
| `app_v2/src/main/res/layout/view_action_help_row.xml` | New | ≤ 60 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/widget/ActionHelpRow.kt` | New | ≤ 180 |
| `app_v2/src/main/res/layout/dialog_gif_editor.xml` (+`layout-land`) | Modified | - |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/dialog/...GifEditor*.kt` | Modified | ≤ 500 |

---

## Steps

### Step 02.1 - Declare `ahr_*` styleable + author layout + implement `ActionHelpRow.kt`

**Files:** `attrs.xml`, `view_action_help_row.xml`, `ActionHelpRow.kt`
**Depends on:** - start of phase
**Landscape:** widget layout - orientation-agnostic.

**Prompt for developer:**

> Add `declare-styleable name="ActionHelpRow"`: `ahr_buttonText` (string|reference), `ahr_buttonStyle` (reference, default a project `Widget.FastMediaSorter.Button.*` style), `ahr_showHelp` (boolean), `ahr_helpTitle` (string|reference), `ahr_helpMessage` (string|reference). Layout (`<merge>`): a project-taxonomy button (`@+id/ahr_button`, NOT raw `Widget.Material3.Button.*`) + help icon (`@+id/ahr_iconHelp`, touch target ≥ 48dp). Implement `class ActionHelpRow : LinearLayout`: `setButtonText`, `setOnButtonClickListener((View)->Unit)`, `setHelp(..)`; supports multi-line button labels on narrow screens. Row owns `TooltipDialog`. No HEX, Timber only.

**Verification:**

- `Grep` - `declare-styleable name="ActionHelpRow"` once; `class ActionHelpRow` once; `@+id/ahr_button` present.
- `Grep -i "#[0-9a-f]\{6\}"` zero hits in `view_action_help_row.xml`.
- `Grep -n "Widget\.Material3\.Button"` zero hits in `view_action_help_row.xml`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Added `ActionHelpRow` styleable (`ahr_buttonText/buttonStyle/showHelp/helpTitle/helpMessage`). `view_action_help_row.xml` (`<merge>`: `ahr_button` MaterialButton default `Widget.FastMediaSorter.Button.Outlined` + `ahr_iconHelp` 48dp touch target; 0 HEX). `ActionHelpRow : LinearLayout`: `setButtonText`, `setOnButtonClickListener`, `setButtonEnabled`, `setHelp`; `ahr_buttonStyle` rebuilds the button via `ContextThemeWrapper`; row owns TooltipDialog.

---

### Step 02.2 - Migrate GIF-editor action/help strips

**Files:** `dialog_gif_editor.xml` (+`layout-land`), GIF-editor controller
**Depends on:** Step 02.1
**Landscape:** `dialog_gif_editor.xml` HAS a `layout-land/` counterpart - migrate symmetrically (verified in F2).

**Prompt for developer:**

> Replace the repeated `button + help icon` strips in `dialog_gif_editor.xml` (strategic §1 item 4 / S0567 §1.1 item 7) with `<com.sza.fastmediasorter.ui.common.widget.ActionHelpRow>`. Move the manual help `TooltipDialog.show(..)` glue in the GIF-editor controller onto the row API. Mirror into `layout-land/`.

**Verification:**

- `Grep` - `ActionHelpRow` present in both orientations of `dialog_gif_editor.xml`.
- `/build` standard debug passes.

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Replaced all 3 `button + help icon` strips (Extract Frames, Apply Speed, First Frame) with `ActionHelpRow` in `dialog_gif_editor.xml` + `layout-land/`. `GifEditorDialog.kt` rewired to `setOnButtonClickListener`/`setButtonEnabled`; manual `ivHelp*` + `showHelpDialog()` (AlertDialog) removed - help now via row-owned TooltipDialog. Also migrated `btnClose` raw `Widget.Material3.Button.TextButton` -> `Widget.FastMediaSorter.Button.Text` (taxonomy, both orientations). Verified `a.ps1 fc` PASS.

---

### Step 02.3 - Audit surviving special cases and record exceptions

**Files:** `../S0595_forms-dialogs-unification-remainder.md` (strategic - exception note only)
**Depends on:** Step 02.2

**Prompt for developer:**

> Run the strategic §7 anti-pattern shrink greps over `app_v2/src/main` (`Color.WHITE|Color.LTGRAY|setBackgroundColor|setTextColor` in `ui/dialog`; residual manual `layout_weight` form pairs in resource-entry layouts). For each surviving hit that is an intentional long-term exception (notably `FileOperationDestinationDialog` color-chip grid + `DestinationAdapter` per strategic §2.2, `ColorPickerDialog` where color is the payload), append a one-line entry to a new `## 8. Long-term exceptions` list in the strategic spec stating the surface and why it is excluded. This is the sole `PLAN/**`-editing step permitted outside Phase 03, because it records audit outcomes that gate completion.

**Verification:**

- `Grep` - `## 8. Long-term exceptions` present in the strategic spec.
- The §7 greps run and their residual hits are each either migrated or listed under §8 (manual reconciliation, zero unexplained hits).

**Status:** `[x] done`

**Step Log:**

- 2026-06-21 - Ran §7 greps. Color anti-patterns in `ui/dialog`: only `ColorPickerDialog`, `DestinationAdapter`, `FileOperationDestinationDialog` (all §2.2 color-is-data). Residual `layout_weight` in resource forms: only the `tilSftpPrivateKey`+`btnSftpLoadKey` field+button row (not a field pair). All recorded in strategic `## 8. Long-term exceptions`; zero unexplained hits.

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - `/build`.
- [ ] Strategic §7 anti-pattern greps: every residual hit is migrated or documented under §8.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

All three remaining primitives shipped; remaining anti-pattern hits are documented exceptions. Phase 03 finalizes catalog + docs + settings-sync.

---

## Rollback Plan

Revert phase commit(s) - widget is additive; GIF-editor strip reverts to prior markup.
