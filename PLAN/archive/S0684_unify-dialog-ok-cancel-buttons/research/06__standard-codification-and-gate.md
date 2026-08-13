# Research 06 - Standard codification + mechanical gate (strategic §6.6)

**Status:** Resolved (workflow research 2026-06-25)

## Where the standard is fixed

1. **`docs/ARCHITECTURE.md` "Button Taxonomy (MANDATORY)"** (lines 159-189): update the dialog Cancel slot row (line 176) and the intro paragraph (line 171) to describe the new look. Update the general "Low-emphasis / cancel" row (line 168) to point dialog pairs at the `DialogCancel` slot.
2. **`values/themes.xml`** comment (lines 307-308): mirror the new wording.
3. **`CLAUDE.md` + `AGENTS.md`**: one developer rule - any dialog/bottom-sheet confirm-cancel pair uses the S0538/S0684 styles; never a one-off cancel button.
4. **Mechanical gate**: `scripts/quality/assert-dialog-cancel-style.ps1`, wired into `scripts/post-change.ps1`.

## Proposed doc wording (line 176 replacement)

> `| Cancel | Widget.FastMediaSorter.Button.DialogCancel | Soft-pink tonal fill (cancel_button_bg / cancel_button_on), deliberately SMALLER than the green confirm - shorter height (dialog_cancel_button_min_height) and narrower than the wide confirm - so confirm reads as the dominant action and cancel as the lighter escape. Saturated red is reserved for DialogDestructive only. |`

## Gate design (Pattern A ratchet)

Template: `scripts/quality/assert-deprecated-pm-flags.ps1` (single regex over a file tree, single-int baseline, modes `-Gate` / `-UpdateBaseline` / `-List`).

- **Scope:** roots `app_v2/src/main/res/layout` + `layout-land`; filename filter `^(dialog_|bottom_sheet_).*\.xml$`.
- **Detection:** per-`MaterialButton` element (read `-Raw`, split on `<com.google.android.material.button.MaterialButton`, slice to tag close; attribute order varies). A violation = element where:
  - cancel signal TRUE: `android:id` matches `(?i)cancel|_skip$|btnskip|btnDismiss|btnNo\b|btnNotNow`, OR `android:text` references `@android:string/cancel` / a cancel/skip/dismiss string; AND
  - confirm guard FALSE: id NOT matching `(?i)(ok|apply|confirm|save|grant|yes|continue|done|delete|remove|clear)`, style NOT `DialogConfirm`/`DialogDestructive`; AND
  - `style` attribute absent OR `!= @style/Widget.FastMediaSorter.Button.DialogCancel`.
- **Baseline:** seed `0` (all current dialogs already comply). Fail-closed on any new one-off cancel button.
- **Builder dialogs are out of scope by construction** (no XML buttons; inherit via `materialAlertDialogTheme`).

## post-change.ps1 wiring (3 edits)

1. Flag near line 141: `$runsDialogCancelGate = $resolvedChangeType -in @('Xml','Mixed')` (optionally narrowed to dialog layout paths).
2. `if ($runsDialogCancelGate) { Invoke-Step "dialog-cancel-style-gate" { & $pwsh -NoProfile -File (Join-Path $root "scripts/quality/assert-dialog-cancel-style.ps1") -Gate } } else { Skip-Step ... }` alongside the focus-highlight block (~line 262).
3. Optional: append to `assert-neuroslop.ps1` `$children` array.
