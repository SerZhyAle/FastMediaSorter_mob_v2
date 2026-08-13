# Research 03 - Cancel size: single-source lever (strategic §6.3, §6.5)

**Status:** Resolved (workflow layout audit 2026-06-25)

## Layout audit finding

All 16 dialog/bottom-sheet action layouts (+ 11 landscape twins) were read. Result:

- **No layout uses `0dp` + `layout_weight`.** Every confirm and cancel button is `android:layout_width="wrap_content"`, content-sized.
- Confirm/cancel labels are short and similar length, so a visible "cancel narrower than confirm" contrast does NOT exist today and must be created deliberately.

## Chosen lever: widen confirm via style, not narrow cancel per-file

Two options were considered:

1. **Per-file**: give each cancel button an explicit narrower `layout_width`. REJECTED - a fixed `dp` width truncates longer locale labels (UK "Скасувати"), and it duplicates the change across ~13 files + landscape twins, violating the strategic §5.1 single-source pillar.
2. **Style-level (CHOSEN)**: add `android:minWidth` to `DialogConfirm` + `DialogDestructive`, leaving `DialogCancel` content-sized. Confirm floors to a wide "under-finger" button; cancel floats to its (narrower) content width. Cancel height is lowered in the shared `DialogCancel` style. Both axes live in `values/themes.xml` - zero per-file layout edits, no localization truncation, propagates to builder dialogs and custom layouts identically.

## Concrete sizing

- `dialog_confirm_button_min_height` stays `56dp` (existing).
- `dialog_cancel_button_min_height` = **48dp** (shorter than confirm; equals the touch-target floor - the S0611/Rule-16 invariant lower bound, never below).
- `dialog_confirm_button_min_width` = **120dp** (clearly wider than the ~90dp content cancel; fits a single action row with the 16dp gap on >=360dp-width dialogs). Tunable.

Result: confirm 56dp tall x >=120dp wide green; cancel 48dp tall x content-width pink. Owner intent ("ОК большая под палец, отмена поменьше") satisfied via shared styles.

## Migration scope (§6.5)

Because every custom layout already references `@style/Widget.FastMediaSorter.Button.DialogCancel`/`DialogConfirm`/`DialogDestructive` (delivered by S0538), and builder dialogs inherit them via `materialAlertDialogTheme`, the style edits propagate to ALL dialogs at once. No per-file dialog migration is required; the "audit builder pass-through" is satisfied by the new mechanical gate (research 06) seeding at baseline 0 (all current dialogs already compliant).
