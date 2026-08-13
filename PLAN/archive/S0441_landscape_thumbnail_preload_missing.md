# S0441 - Thumbnail-preload toggles missing in landscape Settings/General

**Ticket:** S0441
**Status:** Archived
**Priority:** 40
**Date:** 2026-06-15
**Tier:** 2 - Simple (ad-hoc UI parity fix)

## Goal

Восстановить паритет ориентаций на странице Settings/General. Тоглы «Предзагрузка миниатюр» и под-тогл «только по Wi-Fi» присутствовали только в портретном лейауте; в landscape их не было, поэтому в горизонтальной ориентации пользователь не мог управлять предзагрузкой. Цель - добавить недостающие тоглы в landscape (Rule 11) и снять ставшую лишней nullable-защиту в коде.

## 0. Raw capture

Discovered while moving the SMB-reset button in `fragment_settings_general.xml` (no-ticket UI tweak).

- Portrait had `layoutThumbnailPreload`/`rowEnableThumbnailPreload` + `layoutThumbnailPreloadWifiOnly`/`rowThumbnailPreloadWifiOnly`.
- `res/layout-land/fragment_settings_general.xml` had none of them (jumped from `containerSync` straight to `containerCache`).
- Consequence: user could not toggle thumbnail preload in landscape; ViewBinding fields were nullable (present in only one orientation), forcing fragile null-guards.

## Resolution

- Added the two thumbnail-preload toggle rows to [layout-land/fragment_settings_general.xml](app_v2/src/main/res/layout-land/fragment_settings_general.xml) between `containerSync` and `containerCache`, mirroring portrait.
- Did NOT duplicate `btnResetSmbConnections`: in landscape that button already lives inside `containerCache` (next to `btnClearCache`), so the landscape `rowEnableThumbnailPreload` uses full width instead of sharing a horizontal row with the button (portrait pins the button to the right of the toggle).
- Fields are now present in both (only) orientations of this layout (no `sw480dp`/`sw720dp` variant of this file exists), so ViewBinding promotes `rowEnableThumbnailPreload`, `rowThumbnailPreloadWifiOnly`, `layoutThumbnailPreloadWifiOnly` to non-null. Removed the now-unnecessary safe calls in [GeneralSettingsObserversHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsObserversHelper.kt) and [GeneralSettingsViewSetupHelper.kt](app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt), matching the existing non-null style of the sibling `rowEnableBackgroundSync`.

## Decision

- Fork resolved (capture §0): the omission is an oversight, not a spatial decision - the toggles are short rows in a vertical scroll, so adding them to landscape cannot overflow.
- Chose direct landscape rows over extracting a shared `<include>`: this fragment maintains separate hand-written portrait/landscape files with no shared includes; introducing one only for these rows would be a broader refactor out of this ticket's scope.

## Phases

### Phase 01 - Add landscape toggles and tidy binding usage

1. Add `layoutThumbnailPreload` + `rowEnableThumbnailPreload` and `layoutThumbnailPreloadWifiOnly` + `rowThumbnailPreloadWifiOnly` to the landscape layout, without duplicating `btnResetSmbConnections`.
   - Verification: grep landscape file shows the four ids present and exactly one `btnResetSmbConnections`. PASS.
2. Drop the now-unnecessary `?.` safe calls on the promoted non-null binding fields in the two helpers.
   - Verification: `:app_v2:compileStandardDebugKotlin` -> `BUILD SUCCESSFUL` (16s, 2026-06-15) with the direct (non-null) calls; proves the fields are non-null in the regenerated binding. PASS.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none. Discovered during an unrelated SMB-reset-button UI tweak.

## Last Audit

**Date:** 2026-06-15
**Mode:** compact (spec-all Simple path)
**Outcome:** Verified
**Counts:** PASS 2 · WARN 0 · FAIL 0 · MANUAL 0

> Landscape Settings/General now carries the thumbnail-preload toggle and its Wi-Fi-only sub-toggle, matching portrait. The ViewBinding fields are promoted to non-null and the formerly-required safe calls were removed in both helpers; `:app_v2:compileStandardDebugKotlin` returns `BUILD SUCCESSFUL` with the direct calls, which is the structural proof that the ids now exist in every configuration of this layout. The added rows are standard `SettingsToggleRow` widgets already proven in portrait, placed in a vertical scroll, so visual rendering carries no meaningful device risk; no on-device gate inserted.
