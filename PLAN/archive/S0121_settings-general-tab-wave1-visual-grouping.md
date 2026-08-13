# Strategic Spec: S0121 — Settings General Tab Wave 1: Visual Grouping

**Ticket:** S0121
**Status:** Verified
**Priority:** 60
**Date:** 2026-05-08
**Tier:** 2 — Medium / single-module
**Parent spec:** S0119 — Settings Information Architecture Revision
**Roadmap entry:** S0119 Migration Wave 1

<!-- auto-approved by /spec-all — 2026-05-08 -->

> **Scope:** STRATEGIC. Goals, constraints, open questions. No class names, file paths, LOC limits, Room migrations, or Hilt modules.

---

## 1. Problem

General tab mixes service-action buttons (one-shot maintenance operations) with persistent preference rows throughout the Network, Cache, and App Data sections. There is no visual separation between the two entity types, increasing cognitive load and accidental-action risk.

Separately, five informational links (user guide, how-to, privacy policy, open-source licenses, welcome tutorial) live at the bottom of the General tab scroll body outside any labeled grouping, competing visually with preference controls.

This is M4 + M5 from the S0119 migration map, Wave 1 (zero-risk visual reorganization).

---

## 2. Goals

1. Wrap each cluster of service-action buttons inside a clearly-labeled collapsible sub-section header, distinguishing them from adjacent preference rows.
2. Group all five informational links under a new collapsible "About" section at the bottom of the General tab.
3. Preserve all button IDs, behavior, BuildConfig gates, and preference bindings exactly as they are.
4. Apply the same structural change in both portrait and landscape counterpart layouts.

**Non-goals:**

- No Kotlin logic changes, no ViewModel bindings, no UseCase changes.
- No search registry changes (none of these buttons are search-indexed).
- No cross-section or cross-tab moves.
- No changes to any other settings tab.

---

## 3. Wishes and Constraints

### 3.1 Owner requirements

- Within-section grouping only — service-action buttons stay in their current section (Network, Cache, App Data); they are just visually separated.
- `btnBackup` / `btnRestore` must remain visible only in standard flavor — existing BuildConfig gate must be preserved unchanged.
- Keyboard focus order must be updated to traverse all buttons in the new sub-section order; D-pad navigation must not skip any button.
- Light and dark theme must both render new sub-section headers with sufficient contrast (use `?attr/colorSurfaceVariant` background consistent with existing section headers).
- Landscape counterpart `res/layout-land/fragment_settings_general.xml` must be updated in the same commit.

### 3.2 Hard constraints

- **IDs unchanged:** all button IDs (`btnSyncNow`, `btnAutoCalculateCache`, `btnClearCache`, `btnClearStreamingCache`, `btnExportSettings`, `btnImportSettings`, `btnResetSettings`, `btnResetGeneralSection`, `btnResetSmbConnections`, `btnBackup`, `btnRestore`, `btnUserGuide`, `btnHowToGuides`, `btnOpenWelcome`, `btnPrivacyPolicy`, `btnOpenSourceLicenses`) must not change.
- **BuildConfig gate:** the container wrapping `btnBackup` / `btnRestore` must remain tagged for standard-flavor visibility as in the current layout.
- **String keys:** all `android:text` string references must remain unchanged; new sub-section header labels use new string keys added to all three locales (EN/RU/UK).
- **No logic files touched:** no `.kt` files should require changes; this is a layout-only operation.

---

## 4. Context

### 4.1 Affected sections in General tab

**Network section** (`containerSync` area):
- `btnSyncNow` — currently inline in `layoutSyncControls` among sync preference controls.

**Cache section** (`containerCache` area):
- `btnAutoCalculateCache`, `btnClearCache` — currently in `containerCache` ConstraintLayout alongside the cache size limit dropdown.
- `btnClearStreamingCache` — immediately above `containerSync`.
- `btnResetSmbConnections` — currently in `containerCache` ConstraintLayout.

**App Data section** (`containerAppData`):
- `btnExportSettings`, `btnImportSettings` — currently in an unnamed ConstraintLayout.
- `btnResetGeneralSection`, `btnResetSettings` — currently in `containerGeneralActions` ConstraintLayout.
- `btnBackup`, `btnRestore` — currently after a divider in App Data section (standard flavor only).

**Doc Links area** (below DEBUG section, outside any card):
- `btnUserGuide`, `btnHowToGuides`, `btnOpenWelcome`, `btnPrivacyPolicy`, `btnOpenSourceLicenses` — currently in `containerDocLinks` ConstraintLayout/Flow or LinearLayout outside any card.

### 4.2 Existing collapsible pattern

Section headers in this layout use `TextView` with `android:background="?attr/colorSurfaceVariant"`, `android:clickable="true"`, `android:focusable="true"`. Containers below are `LinearLayout` with `android:id="@+id/container*"`. Sub-section headers must follow the same visual pattern with slightly reduced text size to visually distinguish from top-level section headers.

---

## 5. Proposed Approach

### M4 — Service-action sub-section headers

- **Network section:** add a sub-section header labeled "Network Actions" above `btnSyncNow`; wrap `btnSyncNow` in a dedicated sub-container.
- **Cache section:** add a sub-section header labeled "Cache Management" above the group of `btnAutoCalculateCache`, `btnClearCache`, `btnClearStreamingCache`, `btnResetSmbConnections`; ensure all four are inside a dedicated sub-container.
- **App Data section:**
  - Add sub-section header "Settings Data" above `btnExportSettings`, `btnImportSettings`, `btnResetGeneralSection`, `btnResetSettings`.
  - Add sub-section header "Cloud Backup" (standard only) above `btnBackup` / `btnRestore` cluster.

### M5 — About section

- Wrap `containerDocLinks` (all five buttons) inside a new `MaterialCardView` matching the pattern of other sections.
- Add a top-level section header "About" (`headerAbout` id) inside the card.
- Container below the header: `containerAbout` — wraps the five buttons.
- Place the card between the DEBUG section card and the Version Info row.

### New string keys required

- `settings_section_network_actions` — "Network Actions"
- `settings_section_cache_management` — "Cache Management"
- `settings_section_settings_data` — "Settings Data"
- `settings_section_cloud_backup` — "Cloud Backup"
- `settings_category_about` — "About"

All five keys must be added to `values/strings.xml`, `values-ru/strings.xml`, `values-uk/strings.xml`.

---

## 6. Open Items

None. All decisions derivable from codebase and S0119 migration map.

---

## 7. Non-regression Checklist

- All service-action buttons retain their IDs and remain wired to their existing click listeners.
- `btnBackup` / `btnRestore` and their enclosing cloud backup sub-section are invisible in non-standard flavors (gate preserved).
- Focus order traverses all buttons in the new grouping order on both portrait and landscape.
- Section headers use `?attr/colorSurfaceVariant` background — sufficient contrast in light and dark theme.
- No preference keys change.
- No ViewModel bindings change.
- No search registry entries change.

---

## 8. Files Expected to Change

- `app_v2/src/main/res/layout/fragment_settings_general.xml`
- `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`

No `.kt` files expected to change.

---

## Last Audit

**Date:** 2026-05-08
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 16 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- [ ] Visual inspection on device: sub-section headers render with `?attr/colorSurfaceVariant` background in light and dark theme.
- [ ] D-pad navigation traverses all 16 buttons in portrait and landscape without skipping.
- [ ] `btnBackup`/`btnRestore` invisible in `lite` flavor (no static BuildConfig gate in layout — gate is Kotlin-side via `GeneralSettingsBackupHelper`; verify at runtime).

### Superseded note (2026-05-09)

S0121's M4 sub-headers were rolled back by S0124 because the existing top-level IA already addressed M4. The five wrapper LinearLayouts and their `TextView` headers — `containerSettingsData`/`headerSettingsData`, `containerCloudBackup`/`headerCloudBackup`, `containerNetworkActions`/`headerNetworkActions`, `containerCacheManagement`/`headerCacheManagement`, `containerSettingsResetGroup`/`headerSettingsReset` — together with the corresponding `settings_section_*` strings, were removed from `fragment_settings_general.xml` (portrait + land) and EN/RU/UK `strings.xml`.

S0121's M5 work (the About category — `containerAbout`/`headerAbout` + `settings_category_about`) remains valid and stays in production. Catalog status of S0121 is unchanged (`Verified`).
