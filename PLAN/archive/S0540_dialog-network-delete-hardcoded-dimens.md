# S0540 - Replace hardcoded dimens in dialog_network_delete_confirmation

**Ticket:** S0540
**Status:** Archived
**Priority:** 35
**Date:** 2026-06-19
**Tier:** 1 - Quick Win (ad-hoc)

> Pure mechanical token swap. Implemented without build per request (resource-only change, no `.kt` touched).

## 0. Capture (verbatim evidence)

Discovered during S0538 (dialog button unification) codebase research.

- `app_v2/src/main/res/layout/dialog_network_delete_confirmation.xml` uses hardcoded raw `24dp` padding and raw `20sp`/`16sp`/`14sp` text sizes (around lines 7, 16, 26, 35) instead of `?dimen`/`@dimen` tokens.
- It also references hard Android platform attrs (`?android:attr/textColorPrimary` / `?android:attr/textColorSecondary`) that do not respond to Material3 theme tokens.
- Symptom: predates the dimen-token discipline; never migrated. Conflicts with the no-hardcoded-sizes / neuroslop ratchet direction.

## 1. Problem (rough)

A single dialog layout still hardcodes sizes and platform color attrs instead of using project dimen/Material3 tokens; bring it in line with the token discipline.

## 2. Resolution

Migrated both orientation variants (`res/layout/` + `res/layout-land/`) to existing tokens:

- `padding="24dp"` -> `@dimen/dialog_padding_large`
- title `textSize="20sp"` -> `@dimen/text_size_title_dialog`
- subtitle `textSize="16sp"` -> `@dimen/text_size_normal`
- body/list/info/checkbox `textSize="14sp"` -> `@dimen/text_size_small`
- margins `16dp/12dp/4dp/8dp` -> `@dimen/margin_large` / `@dimen/margin_medium` / `@dimen/margin_small` / `@dimen/margin_normal`
- icon `20dp` -> `@dimen/icon_size_button`, icon `24dp` -> `@dimen/icon_size_small`
- warning-block `padding="12dp"` -> `@dimen/padding_normal`
- `?android:attr/textColorPrimary` -> `?attr/colorOnSurface`
- `?android:attr/textColorSecondary` (text + `app:tint`) -> `?attr/colorOnSurfaceVariant`

Already-Material3 error attrs (`?attr/colorError`, `?attr/colorErrorContainer`, `?attr/colorOnErrorContainer`) left untouched. No `.kt`/string changes. Build deferred per request; resource-token-only swap, all referenced dimens pre-exist in `dimens.xml`.

## Last Audit

**Date:** 2026-06-19
**Mode:** full
**Outcome:** Verified
**Counts:** PASS 3 · WARN 0 · FAIL 0

Static checks (both `res/layout/` + `res/layout-land/dialog_network_delete_confirmation.xml`): 0 raw `dp`/`sp` text/padding/margin literals (the only remaining `0dp` is the ConstraintLayout MATCH_CONSTRAINT weight idiom, not a tokenizable size); 0 `?android:attr/textColor*` platform attrs. No `.kt` / no debug tags. Resource-token-only swap; no device acceptance.
