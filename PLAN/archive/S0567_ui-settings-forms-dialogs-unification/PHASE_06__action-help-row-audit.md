# Phase 06 - ActionHelpRow + special-case audit

**Strategic spec:** [`../S0567_ui-settings-forms-dialogs-unification.md`](../S0567_ui-settings-forms-dialogs-unification.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, 02, 03, 04, 05
**Blocks:** Phase 07
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Introduce `ActionHelpRow` (`button + help icon`, project button taxonomy), migrate the repeated GIF-editor action/help strips, and record explicit long-term exceptions for the surviving specialized surfaces.

---

## Prerequisites

- [ ] Phases 01-05 ✅ Done.

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

### Step 06.1 - Declare `ahr_*` styleable + author layout + implement `ActionHelpRow.kt`

**Files:** `attrs.xml`, `view_action_help_row.xml`, `ActionHelpRow.kt`
**Depends on:** - start of phase
**Landscape:** widget layout - orientation-agnostic.

**Prompt for developer:**

> Add `declare-styleable name="ActionHelpRow"`: `ahr_buttonText` (string|reference), `ahr_buttonStyle` (reference, default a project `Widget.FastMediaSorter.Button.*` style), `ahr_showHelp` (boolean), `ahr_helpTitle` (string|reference), `ahr_helpMessage` (string|reference). Layout (`<merge>`): a project-taxonomy button (`@+id/ahr_button`, NOT raw `Widget.Material3.Button.*`) + help icon (`@+id/ahr_iconHelp`, touch target ≥ 48dp). Implement `class ActionHelpRow : LinearLayout`: `setButtonText`, `setOnButtonClickListener((View)->Unit)`, `setHelp(..)`; supports multi-line button labels on narrow screens. Row owns `TooltipDialog`. No HEX, Timber only.

**Verification:**

- `Grep` - `declare-styleable name="ActionHelpRow"` once; `class ActionHelpRow` once; `@+id/ahr_button` present.
- `Grep -i "#[0-9a-f]\{6\}"` zero hits in `view_action_help_row.xml`.
- `Grep -n "Widget\.Material3\.Button"` zero hits in `view_action_help_row.xml`.

**Status:** `[ ]` not done

---

### Step 06.2 - Migrate GIF-editor action/help strips

**Files:** `dialog_gif_editor.xml` (+`layout-land`), GIF-editor controller
**Depends on:** Step 06.1
**Landscape:** `dialog_gif_editor.xml` has a `layout-land/` counterpart - migrate symmetrically.

**Prompt for developer:**

> Replace the three repeated `button + help icon` strips in `dialog_gif_editor.xml` (strategic §1.1 item 7) with `<com.sza.fastmediasorter.ui.common.widget.ActionHelpRow>`. Move the manual help `TooltipDialog.show(..)` glue in the GIF-editor controller onto the row API. Mirror into `layout-land/`.

**Verification:**

- `Grep` - `ActionHelpRow` present in both orientations of `dialog_gif_editor.xml`.
- `/build` standard debug passes.

**Status:** `[ ]` not done

---

### Step 06.3 - Audit surviving special cases and record exceptions

**Files:** `../S0567_ui-settings-forms-dialogs-unification.md` (strategic - exception note only)
**Depends on:** Step 06.2

**Prompt for developer:**

> Run the strategic §7 anti-pattern shrink greps over `app_v2/src/main` (`<Spinner`, `TooltipDialog.show` in settings/resourceeditor/addresource, `Color.WHITE|Color.LTGRAY|setBackgroundColor|setTextColor` in `ui/dialog`). For each surviving hit that is an intentional long-term exception (notably `FileOperationDestinationDialog` color-chip grid per strategic §2.2), append a one-line entry to a new `## 8. Long-term exceptions` list in the strategic spec stating the surface and why it is excluded. This is the sole `PLAN/**`-editing step permitted outside Phase 07, because it records audit outcomes that gate completion.

**Verification:**

- `Grep` - `## 8. Long-term exceptions` present in the strategic spec.
- The three §7 greps run and their residual hits are each either migrated or listed under §8 (manual reconciliation, zero unexplained hits).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` is `[x] done`.
- [ ] Project compiles - `/build`.
- [ ] Strategic §7 anti-pattern greps: every residual hit is migrated or documented under §8.
- [ ] Dev log entry added.

---

## Handoff Notes to Next Phase

All seven primitives shipped; remaining anti-pattern hits are documented exceptions. Phase 07 finalizes catalog + docs + settings-sync.

---

## Rollback Plan

Revert phase commit(s) - widget is additive; GIF-editor strip reverts to prior markup.
