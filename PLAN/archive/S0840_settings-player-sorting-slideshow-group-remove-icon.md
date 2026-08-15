# S0840 - Settings Player: remove sorting/slideshow group header icon

**Ticket:** S0840
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 1 - Quick Win
**Source:** User request 2026-07-01 (`/spec-draft`)

<!-- auto-approved by /spec-all - 2026-07-01 -->

## Goal

На странице настроек «Плеер» у группы «Сортировка, слайд-шоу и порядок запуска» текущая иконка заголовка (`ic_slideshow`) не отражает состав секции и в портрете раздувает header во вторую строку. Убрать иконку из заголовка группы (portrait + landscape) - остаётся текстовый header. Состав группы, порядок, подписи и поведение сворачивания не меняются.

## 1. Confirmed scope (research 2026-07-01)

Group header lives in `app_v2/src/main/res/layout/fragment_settings_playback.xml` (+ `layout-land/` counterpart), widget `CollapsibleSectionHeader` id `headerSortingSlideshow`, title `@string/settings_category_sorting_slideshow`. Icon set via `app:csh_icon="@drawable/ic_slideshow"`.

The widget hides its icon slot by design (S0776): `view_collapsible_section_header.xml` declares `csh_icon` ImageView with `android:visibility="gone"`, and `CollapsibleSectionHeader` only shows it when the `csh_icon` attribute resolves (`if (iconRes != 0) setIcon(iconRes)`). Removing the attribute therefore yields a clean text-only header with no empty leading slot - exactly the desired end state.

## 2. Phase 1 - Remove group header icon (portrait + landscape)

In BOTH `layout/fragment_settings_playback.xml` and `layout-land/fragment_settings_playback.xml`, delete the `app:csh_icon="@drawable/ic_slideshow"` line from the `headerSortingSlideshow` `CollapsibleSectionHeader`. Leave `csh_title` / `csh_showHelp` and the group contents untouched.

**Verification:** `.\a.ps1 fr` (resource/manifest compile) passes; both layouts drop the icon attribute; `ic_slideshow` stays defined (reused by 15 other surfaces); no behavior/label/order/collapse change.

## 3. Open points

Resolved during research:

1. End state is text-only header in both orientations - yes; widget renders header without a leading icon slot (gone by default).
2. Portrait/landscape use separate layout defs - yes, `layout/` + `layout-land/`; both edited (Rule 11).
3. Docs/settings-manifest mirror - no; a group-header icon is decorative and carries no settings metadata, so Rule 22 needs no regen (same as S0841).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0841 (settings-management canonical feature icons - sibling icon-tuning), S0836/S0838 (icon-unification family).

## Related

- S0841, S0836, S0838 - settings icon tuning/unification family.

## Last Audit

**Date:** 2026-07-01 (via /spec-next -> /spec-all)
**Verdict:** Verified

- Portrait `layout/fragment_settings_playback.xml` + landscape `layout-land/fragment_settings_playback.xml`, both edited (Rule 11): removed `app:csh_icon="@drawable/ic_slideshow"` from the `headerSortingSlideshow` `CollapsibleSectionHeader`; `csh_title` / `csh_showHelp` and group contents untouched.
- `CollapsibleSectionHeader` renders the header text-only when `csh_icon` is absent: `view_collapsible_section_header.xml` `csh_icon` ImageView is `visibility="gone"` by default and only shown when the attribute resolves (`if (iconRes != 0) setIcon(iconRes)`) - no empty leading slot remains, header collapses to one line in portrait.
- `ic_slideshow` kept: still referenced by 15 other surfaces (standalone players, shortcuts, statistics, welcome) - not dead-weight.
- `a.ps1 fr` (mergeStandardDebugResources + processStandardDebugResources executed) -> BUILD SUCCESSFUL.
- No settings-manifest / Rule 22 regen: decorative group-header icon carries no settings metadata (same rationale as S0841).
- No ALL_FEATURES record: cosmetic icon removal from an existing settings group, not a new capability.
