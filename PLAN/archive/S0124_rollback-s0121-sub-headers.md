# S0124 — Rollback S0121 sub-headers

Status: Verified
Priority: 60
Created: 2026-05-09
Updated: 2026-05-09

<!-- auto-approved by /spec-all — 2026-05-09 -->

## Goal

Откатить пять «sub-headers» wrapper-ов, которые S0121 ошибочно добавил внутрь уже существующих top-level контейнеров `containerAppData` и `containerSystem` категорий в `fragment_settings_general.xml`. Существующая 5-категорийная IA (Interface / Permissions / App Data / System / Debug) уже закрывала пункт M4 миграционной карты S0119, и под-заголовки только засоряли её. Удаляются обёрточные `LinearLayout` + их `TextView`-заголовки; все дочерние кнопки сохраняются как прямые потомки соответствующего top-level контейнера. Шестая top-level категория `containerAbout` (M5 из S0121) остаётся нетронутой.

## Affected files

- `app_v2/src/main/res/layout/fragment_settings_general.xml`
- `app_v2/src/main/res/layout-land/fragment_settings_general.xml`
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`
- `PLAN/S0121_settings-general-tab-wave1-visual-grouping.md` — append Superseded note in `## Last Audit`
- `docs/migration-map.md` — append M4 retraction paragraph

## Phase 1 — Remove sub-header wrappers from layout XML

Steps (apply identically to portrait `res/layout/` and landscape `res/layout-land/` copies of `fragment_settings_general.xml`):

- Remove the wrapper `LinearLayout android:id="@+id/containerSettingsData"` (parent `containerAppData`). Delete:
  - opening `<LinearLayout android:id="@+id/containerSettingsData" ...>`
  - inner `<TextView android:id="@+id/headerSettingsData" ... android:text="@string/settings_section_settings_data" ... />`
  - the wrapper's matching closing `</LinearLayout>`
  - the `<!-- S0121: Settings Data sub-section ... -->` comment line above the wrapper
- Remove wrapper `containerCloudBackup` (parent `containerAppData`) the same way: opening tag, `headerCloudBackup` TextView, matching `</LinearLayout>`, `<!-- S0121: Cloud Backup ... -->` comment.
- Remove wrapper `containerNetworkActions` (in portrait nested inside `containerSync`; in land it is a direct sibling under `containerSystem`). Delete opening tag, `headerNetworkActions` TextView, matching `</LinearLayout>`, `<!-- S0121: Network Actions ... -->` comment.
- Remove wrapper `containerCacheManagement` (parent `containerSystem`): opening tag, `headerCacheManagement` TextView, matching `</LinearLayout>`, `<!-- S0121: Cache Management ... -->` comment.
- Remove wrapper `containerSettingsResetGroup` (parent `containerSystem`): opening tag, `headerSettingsReset` TextView, matching `</LinearLayout>`, `<!-- S0121: Settings Reset ... -->` comment.

Constraints:

- Keep every child View intact: `btnExportSettings`, `btnImportSettings`, `btnHelpExportImport`, `btnBackup`, `btnRestore`, `iconHelpBackupInfo`, `layoutBackupInfo`, `progressBackup`, `progressRestore`, `btnSyncNow`, `tvSyncLastStatus`, `btnClearStreamingCache`, `containerCache` (with `tilCacheSizeLimit`, `actvCacheSizeLimit`, `btnAutoCalculateCache`, `btnClearCache`, `btnResetSmbConnections`, `tvCacheSize`), `containerGeneralActions` (with `btnResetGeneralSection`, `btnResetSettings`).
- Inner padding `LinearLayout` of `containerCloudBackup`, `containerCacheManagement`, `containerSettingsResetGroup` (the unnamed wrapper that holds `paddingStart/paddingEnd`) — collapse it: its children become direct children of the parent top-level container so visual padding is preserved by the parent's own padding. If the inner padding wrapper carries unique padding values used by S0121 to inset child content, drop the wrapper but DO NOT add new attributes to children — accept the parent container's standard insets.
- Do NOT touch `containerAbout`, `headerAbout`, or the string `settings_category_about`.

Verification predicates:

- Grep `headerSettingsData|headerCloudBackup|headerNetworkActions|headerCacheManagement|headerSettingsReset` across `app_v2/src/main/res/layout*` returns 0 hits.
- Grep `containerSettingsData|containerCloudBackup|containerNetworkActions|containerCacheManagement|containerSettingsResetGroup` across `app_v2/src/main/res/layout*` returns 0 hits.
- Grep for each preserved child id (`btnExportSettings`, `btnImportSettings`, `btnResetSettings`, `btnResetGeneralSection`, `btnResetSmbConnections`, `btnBackup`, `btnRestore`, `btnSyncNow`, `btnAutoCalculateCache`, `btnClearCache`, `btnClearStreamingCache`) — each still present in both portrait and land layout.
- Grep `headerAbout|containerAbout|settings_category_about` — still present in layout(s) and string xml.

## Phase 2 — Remove orphan string keys

Steps:

- In `app_v2/src/main/res/values/strings.xml` remove the 5 `<string>` lines for keys: `settings_section_network_actions`, `settings_section_cache_management`, `settings_section_settings_data`, `settings_section_cloud_backup`, `settings_section_settings_reset`.
- Apply identical removal in `values-ru/strings.xml` and `values-uk/strings.xml`.
- Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_section_"`.

Verification predicates:

- Grep `settings_section_network_actions|settings_section_cache_management|settings_section_settings_data|settings_section_cloud_backup|settings_section_settings_reset` across the whole repository returns 0 hits.
- `check_strings_localized.ps1 -KeyPrefix "settings_section_"` exits 0 (no missing locale entries for any remaining `settings_section_*` keys, if any).

## Phase 3 — Build gate

Steps:

- Run `standard debug` build via `/build` skill.
- Build must succeed without R-class or layout compilation errors.

Verification predicates:

- `standardDebug` build PASS.

## Phase 4 — Side-task documentation

Steps:

- In `PLAN/S0121_settings-general-tab-wave1-visual-grouping.md`, inside the existing `## Last Audit` block, append a "Superseded note" section: state that S0121's M4 sub-headers (`containerSettingsData`, `containerCloudBackup`, `containerNetworkActions`, `containerCacheManagement`, `containerSettingsResetGroup`) were rolled back by S0124 because the existing top-level IA already addressed M4. Note that S0121's M5 (About category — `containerAbout` + `settings_category_about`) remains valid and stays in production. Do NOT change the spec's catalog status; it stays `Verified`.
- In `docs/migration-map.md`, locate the M4 entry. At the bottom of that entry, add a paragraph: "M4 retracted: codebase already had top-level category IA (Interface / Permissions / App Data / System / Debug) that satisfied M4. S0121 sub-headers rolled back via S0124."

Verification predicates:

- `PLAN/S0121_settings-general-tab-wave1-visual-grouping.md` contains the substring `Superseded` and references both `S0124` and the rolled-back container ids.
- `docs/migration-map.md` contains the substring `M4 retracted` and references `S0124`.

## Phase 5 — Catalogue + changelog hygiene

Steps:

- Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `render.ps1 -Module app_v2` (XML-only edits do not change Kotlin catalogue but run for completeness; commit only if there are changes).
- Run `.\scripts\add_to_dev_log.ps1 "PLAN/S0124_rollback-s0121-sub-headers.md" "spec-all" "Pipeline complete: S0124 rollback of S0121 sub-headers"`.
- Update spec catalog status `Implemented` → `Verified` after audit passes.

Verification predicates:

- `dev/CHANGELOG.md` HEAD entry references `S0124`.
- `scripts/spec_catalog/select.ps1 -Id S0124 -Format json` reports `Status: Verified`.

---

## Last Audit

**Date:** 2026-05-09
**Mode:** compact (spec-all Simple path)
**Outcome:** Verified
**Counts:** PASS 7 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Predicates

- Grep `header(SettingsData|CloudBackup|NetworkActions|CacheManagement|SettingsReset)` and `container(SettingsData|CloudBackup|NetworkActions|CacheManagement|SettingsResetGroup)` across `app_v2/src/main/res/layout*` → 0 hits. PASS.
- Grep `settings_section_(network_actions|cache_management|settings_data|cloud_backup|settings_reset)` across the whole repo → 0 hits. PASS.
- `headerAbout` / `containerAbout` / `settings_category_about` still present in 2 layout files + 3 strings.xml. PASS.
- All 11 preserved button ids (`btnExportSettings`, `btnImportSettings`, `btnResetSettings`, `btnResetGeneralSection`, `btnResetSmbConnections`, `btnBackup`, `btnRestore`, `btnSyncNow`, `btnAutoCalculateCache`, `btnClearCache`, `btnClearStreamingCache`) present in both portrait and landscape layouts. PASS.
- `scripts/check_strings_localized.ps1 -KeyPrefix "settings_section_"` reports no remaining keys with the prefix. PASS.
- `gradlew.bat assembleStandardDebug` BUILD SUCCESSFUL in 37s. PASS.
- LinearLayout open/close balance: portrait 50/50, landscape 49/49. PASS.

### Side-task verification

- `PLAN/S0121_settings-general-tab-wave1-visual-grouping.md` contains `Superseded note (2026-05-09)` referencing S0124 and the rolled-back container ids. Catalog status of S0121 unchanged (`Verified`).
- `PLAN/S0119_settings-information-architecture-revision/docs/migration-map.md` M4 entry contains `M4 retracted (2026-05-09)` paragraph referencing S0124.

### Manual / on-device

- (none — no UI ambiguity, no on-device gates required for a layout/string rollback.)

