# S0832 - Settings Media: image/GIF support toggles on separate portrait rows

**Ticket:** S0832
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 1 - Quick Win
**Source:** User request 2026-07-01 (`/spec-draft`)

<!-- auto-approved by /spec-all - 2026-07-01 -->

## Goal

В Settings -> Media, группа «Изображения, GIF..», два тогглера «Поддержка статичных изображений» и «Поддержка GIF» в портрете сейчас стоят в одну строку (2-up). Развести их на две отдельные строки - только портрет; ландшафт остаётся парным. Поведение, подписи - без изменений.

## 1. Confirmed scope (research 2026-07-01)

Both toggles live in `containerImagesGif` inside `app_v2/src/main/res/layout/fragment_settings_images.xml` (portrait) and a separate `layout-land/` variant. Portrait container was `orientation="horizontal"` with `rowSupportImages` + `rowSupportGifs` each `0dp`/weight 1 (side-by-side). Landscape has its own `containerImagesGif` (also horizontal) - owner wants it kept (Open point 1: portrait-only). Both row ids bind in the images settings fragment; ids preserved, so no Kotlin/binding impact (Open point 2 / Rule 11 handled via layout-land divergence comment).

## 2. Phase 1 - Portrait: stack the two toggles vertically

In `layout/fragment_settings_images.xml` only:

1. `containerImagesGif` orientation `horizontal` -> `vertical` (drop the now-irrelevant `gravity="center_vertical"`).
2. `rowSupportImages`: `width` `0dp` -> `match_parent`; drop `layout_weight` and `layout_marginEnd`.
3. `rowSupportGifs`: `width` `0dp` -> `match_parent`; drop `layout_weight`; replace `layout_marginStart` with `layout_marginTop="@dimen/margin_small"` for row spacing.

Landscape: add a divergence comment only; keep the paired horizontal row (Rule 11 - conscious portrait-only design).

**Verification:** `.\a.ps1 fr` passes; both row ids (`rowSupportImages`, `rowSupportGifs`) preserved; no label / behavior / landscape change.

## 3. Open points

Resolved (see §1): portrait-only; landscape kept paired; separate XML variants; ids preserved.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0834 / S0833 (sibling Settings -> Media row tweaks), S0839 (same "un-pair in portrait" pattern).

## Related

- S0839 - same portrait un-pairing pattern (playback delete/confirm).
- S0834, S0833 - sibling Settings -> Media quick wins.

## Last Audit

**Date:** 2026-07-01 (via /spec-next -> /spec-all)
**Verdict:** Verified

- Portrait `layout/fragment_settings_images.xml`: `containerImagesGif` `horizontal` -> `vertical`; `rowSupportImages` now `match_parent` (weight/marginEnd dropped, icon + help + checked preserved); `rowSupportGifs` `match_parent` + `layout_marginTop="@dimen/margin_small"` (weight/marginStart dropped).
- Landscape `layout-land/fragment_settings_images.xml`: unchanged paired horizontal row; added an S0832 divergence comment so the portrait-only design is documented (Rule 11), not an oversight.
- Both bound ids (`rowSupportImages`, `rowSupportGifs`) preserved - no Kotlin/binding impact; labels/subtitles/help/checked-default unchanged.
- `a.ps1 fr` (mergeStandardDebugResources + processStandardDebugResources executed) -> BUILD SUCCESSFUL.
- No settings-manifest / Rule 22 regen: layout reflow of existing toggles - no setting added/removed/renamed, no behavior change.
- No ALL_FEATURES record: cosmetic portrait reflow of existing settings rows, not a new capability.
