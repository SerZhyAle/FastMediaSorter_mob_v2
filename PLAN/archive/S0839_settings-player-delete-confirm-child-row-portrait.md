# S0839 - Settings Player: confirm-delete child row on its own line (portrait)

**Ticket:** S0839
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 1 - Quick Win
**Source:** User request 2026-07-01 (`/spec-draft`)

<!-- auto-approved by /spec-all - 2026-07-01 -->

## Goal

В Settings -> Player, группа «Удаление и переименование в программе»: в портрете toggle «Подтверждать удаления» (`rowConfirmDelete`) сейчас стоит в один горизонтальный ряд с «Разрешить удаления» (`rowAllowDelete`) 50/50. Владелец хочет вынести его на собственную строку с отступом, сохранив визуальную и логическую подчинённость родителю. Только портрет; ландшафт (3-up ряд) не трогаем. Зависимость enable/disable и подписи не меняются.

## 1. Confirmed scope (research 2026-07-01)

Both toggles live in `containerDeleteConfirm` inside `app_v2/src/main/res/layout/fragment_settings_playback.xml` (portrait) and `layout-land/` (landscape). Layouts already diverge by design:

- Portrait: `containerDeleteConfirm` = horizontal, `rowAllowDelete` + `rowConfirmDelete` side-by-side (each `0dp`/weight 1); `rowAllowRename` is a separate row above.
- Landscape: `containerDeleteConfirm` = horizontal 3-up (`rowAllowRename` + `rowAllowDelete` + `rowConfirmDelete`, S0618 R2) - owner wants this kept.

Dependency wiring is in `PlaybackSettingsFragment` (`rowAllowDelete`/`rowConfirmDelete` listeners + `setCheckedSilently`), keyed off the row ids, not the container - restructuring the portrait container does not touch it. Precedent for "child indented in portrait, paired in landscape": S0651 (`rowMicRecordingAskFilename`) uses a nested vertical `LinearLayout` with `paddingStart="@dimen/settings_nested_margin_start"` (24dp).

## 2. Phase 1 - Portrait: stack confirm-delete as an indented child

In `layout/fragment_settings_playback.xml` only:

1. Change `containerDeleteConfirm` orientation `horizontal` -> `vertical`.
2. `rowAllowDelete`: `width` `0dp` -> `match_parent`; drop `layout_weight` and `layout_marginEnd`.
3. Wrap `rowConfirmDelete` in a nested vertical `LinearLayout` with `paddingStart="@dimen/settings_nested_margin_start"` (mirrors S0651); row `width` `0dp` -> `match_parent`, drop `layout_weight`, replace the old `marginStart` with `layout_marginTop="@dimen/margin_small"` for row spacing.

Landscape: add a divergence comment only; keep the 3-up inline row (Rule 11 - conscious orientation-specific design, not an oversight).

**Verification:** `.\a.ps1 fr` passes; both row ids (`rowAllowDelete`, `rowConfirmDelete`) still resolve for view binding; no label/dependency/landscape change.

## 3. Open points

Resolved during research:

1. Landscape stays the current inline 3-up row - yes (owner-scoped portrait-only); documented with an S0839 divergence comment.
2. Separate portrait/landscape XML variants - yes; Rule 11 satisfied by an intentional-divergence comment in `layout-land/` rather than a mirrored structural edit.
3. Child row icon/help affordance - the pair carries no leading icon; only the child now gets an indent + top margin, subtitles unchanged.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0651 (mic-recording child-row indent precedent), S0840 (sibling playback-settings layout tweak).

## Related

- S0651 - same "child indented in portrait, paired in landscape" pattern.
- S0840 - sibling Settings -> Player header/layout quick win.

## Last Audit

**Date:** 2026-07-01 (via /spec-next -> /spec-all)
**Verdict:** Verified

- Portrait `layout/fragment_settings_playback.xml`: `containerDeleteConfirm` orientation `horizontal` -> `vertical`; `rowAllowDelete` now `match_parent` (weight/marginEnd dropped); `rowConfirmDelete` wrapped in a nested vertical `LinearLayout` with `paddingStart="@dimen/settings_nested_margin_start"` (24dp indent, mirrors S0651), row `match_parent` + `layout_marginTop="@dimen/margin_small"`.
- Landscape `layout-land/fragment_settings_playback.xml`: unchanged 3-up inline row (S0618 R2); added an S0839 divergence comment so the portrait-only design is a documented decision (Rule 11), not an oversight.
- Both bound ids (`rowAllowDelete`, `rowConfirmDelete`) preserved; nesting depth does not affect view binding. Dependency wiring in `PlaybackSettingsFragment` (listeners + `setCheckedSilently`) untouched - enable/disable behavior identical.
- `a.ps1 fr` (mergeStandardDebugResources + processStandardDebugResources executed) -> BUILD SUCCESSFUL; `a.ps1 fk` (compileStandardDebugKotlin) -> BUILD SUCCESSFUL (view binding resolves both rows).
- No settings-manifest / Rule 22 regen: pure layout reflow of existing toggles - no setting added/removed/renamed, no behavior change.
- No ALL_FEATURES record: cosmetic portrait reflow of existing settings rows, not a new capability.
