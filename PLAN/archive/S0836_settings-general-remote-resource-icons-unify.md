# S0836 - Settings General: unify remote-resource row icons with canonical set

**Ticket:** S0836
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 2 - Easy
**Source:** User request 2026-07-01 (`/spec-draft`)

<!-- auto-approved by /spec-all - 2026-07-01 -->

## Goal

В Settings -> Основные, группа «Удалённые ресурсы» (SMB / (S)FTP / Cloud) показывает произвольные иконки (`ic_wifi` / `ic_storage` / `ic_cloud`), не совпадающие с каноническими иконками типов ресурсов, которые уже используются при создании ресурса и в главном окне. Привести иконки строк к каноническому набору `ic_resource_smb` / `ic_resource_sftp` / `ic_resource_cloud`, чтобы визуальный язык типов ресурсов был единым по всему приложению. Portrait + landscape. Поведение, подписи, порядок, тогглы - без изменений.

## 1. Confirmed scope (research 2026-07-01)

Canonical per-type resource icons are centralized in `ui/icon/ResourceIconComposer` + `ui/icon/ConnectionBadgeMapper` (`ResourceType.SMB -> ic_resource_smb`, `SFTP -> ic_resource_sftp`, `FTP -> ic_resource_ftp`, `CLOUD -> ic_resource_cloud`, `LOCAL -> ic_resource_local`) and mirrored by every canonical surface: the resource-creation flow `activity_add_resource.xml` (SMB card `ic_resource_smb`, S/FTP card `ic_resource_sftp`, Cloud card `ic_resource_cloud`), `ResourcePickerDialogFragment`, the launch widget, and main-window resource items.

The Settings «Remote sources» group (`fragment_settings_general.xml` + `layout-land/`, S0391) instead hard-codes `rowSourceSmb=ic_wifi`, `rowSourceFtp=ic_storage`, `rowSourceCloud=ic_cloud` - the mismatch the owner reported. The group has 3 rows: SMB, the combined «(S)FTP», and Cloud - matching the 3 remote cards in the add-resource flow one-to-one.

Resolved open points: (1) types in scope are SMB / (S)FTP / Cloud, each with a canonical icon; (2) rows use static per-row `str_icon` - aligned directly to the canonical drawables (same static-icon approach as `activity_add_resource`); (3) «filtering / main window» uses the same canonical set via `ResourceIconComposer`, which this change now matches. The group header `headerRemoteSources` (`ic_wifi`) is a network-category signifier, not a resource-type slot (no single canonical «remote-umbrella» icon exists) - left unchanged to avoid guessing.

## 2. Phase 1 - Align row icons to the canonical set (portrait + landscape)

In BOTH `layout/fragment_settings_general.xml` and `layout-land/fragment_settings_general.xml`:

1. `rowSourceSmb`: `app:str_icon` `ic_wifi` -> `ic_resource_smb`.
2. `rowSourceFtp`: `app:str_icon` `ic_storage` -> `ic_resource_sftp` (the combined «(S)FTP» row; add-resource labels the S/FTP card with `ic_resource_sftp`).
3. `rowSourceCloud`: `app:str_icon` `ic_cloud` -> `ic_resource_cloud`.

**Verification:** `.\a.ps1 fr` passes; all three `ic_resource_*` drawables resolve (already used across add-resource / widget / main window); no behavior / label / order / grouping change. Old icons stay referenced elsewhere (`ic_wifi` on the group header, `ic_storage` / `ic_cloud` on other surfaces) - not orphaned.

## 3. Open points

Resolved (see §1).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0840 / S0841 / S0838 (settings icon tuning/unification family).

## Related

- S0391 - the «Remote sources» settings group.
- S0840, S0841, S0838 - settings icon tuning/unification family.

## Last Audit

**Date:** 2026-07-01 (via /spec-next -> /spec-all)
**Verdict:** Verified

- Portrait `layout/fragment_settings_general.xml` + landscape `layout-land/fragment_settings_general.xml`, both edited (Rule 11): `rowSourceSmb` `ic_wifi` -> `ic_resource_smb`; `rowSourceFtp` `ic_storage` -> `ic_resource_sftp`; `rowSourceCloud` `ic_cloud` -> `ic_resource_cloud`.
- Icons now match the canonical resource-type set (`ui/icon/ResourceIconComposer` + `ConnectionBadgeMapper`, `activity_add_resource`, resource picker, launch widget, main-window items) - single visual language for SMB / (S)FTP / Cloud across the app.
- Group header `headerRemoteSources` (`ic_wifi`) left as the network-category signifier (not a per-type slot); no canonical remote-umbrella icon exists, so not guessed.
- No behavior / label / order / grouping / toggle change; `str_icon` only. Old drawables remain referenced elsewhere (header + other surfaces) - none orphaned.
- `a.ps1 fr` (mergeStandardDebugResources + processStandardDebugResources executed) -> BUILD SUCCESSFUL; all three `ic_resource_*` resolve.
- No settings-manifest / Rule 22 regen: decorative per-row icon change, no settings metadata affected.
- No ALL_FEATURES record: cosmetic icon alignment of existing settings rows, not a new capability.
