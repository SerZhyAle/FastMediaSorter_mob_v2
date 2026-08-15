# S0849 - Missing RU/UK localizations for resource-import strings

**Ticket:** S0849
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-01
**Tier:** 2 - Small (ad-hoc)
**Source:** Parked by /spec-next during S0799 audit (2026-07-01)

<!-- auto-approved by /spec-all - 2026-07-01 -->

## Goal

Закрыть трилингвальный parity-gap: несколько resource-строк присутствуют в EN, но отсутствуют в RU и/или UK. Добавить недостающие переводы, чтобы `check_strings_localized.ps1` проходил чисто по resource-префиксу. Scope узкий - только resource-ключи; полный проектный parity-sweep вне этого тикета.

## 0. Captured request

**Captured:** 2026-07-01 (parked during S0799 resource-vs-folder audit)

**Symptom:** `scripts/check_strings_localized.ps1` reports several resource-related string keys present in EN but MISSING in RU and/or UK - a trilingual-parity gap predating and unrelated to S0799.

Not attributable to S0799 (git history shows no S0799-era edits near these keys); out of that spec's scope.

## 1. Confirmed scope (audit 2026-07-01)

`check_strings_localized.ps1 -KeyPrefix "*resource*"` -> 6 keys failing, all in `app_v2/src/main/res/values*/strings.xml` (residual file):

- `error_importing_resources` - UK missing (EN "Error importing resources", RU "Ошибка импорта ресурсов")
- `import_resources_message` - UK missing (EN "Add SZA resources?", RU "Добавить ресурсы SZA?")
- `import_resources_title` - UK missing (EN "Import Resources", RU "Импорт ресурсов")
- `music_resource_unavailable` - UK missing (EN "Music resource unavailable", RU "Ресурс с музыкой недоступен")
- `remove_resource` - RU + UK missing (EN "Remove Resource")
- `sza_resources_imported` - UK missing (EN "SZA resources imported", RU "Ресурсы SZA импортированы")

No other prefix contributes resource keys; the `*resource*` audit is the authoritative complete set for this ticket.

## 2. Phase 1 - Add missing RU/UK translations

1. Author a byte-safe `.ps1` in `temp/` (Cyrillic values must not cross the bash->pwsh CLI boundary) that calls `scripts/utils/set-android-string.ps1 -Action set -CreateIfMissing` once per missing locale/key:
   - UK `error_importing_resources` = "Помилка імпорту ресурсів"
   - UK `import_resources_message` = "Додати ресурси SZA?"
   - UK `import_resources_title` = "Імпорт ресурсів"
   - UK `music_resource_unavailable` = "Ресурс з музикою недоступний"
   - UK `sza_resources_imported` = "Ресурси SZA імпортовано"
   - RU `remove_resource` = "Удалить ресурс"
   - UK `remove_resource` = "Видалити ресурс"
2. **Verification:** `check_strings_localized.ps1 -KeyPrefix "*resource*"` exits 0 (all keys present in EN/RU/UK).

Note: `remove_resource` is the neutral "remove from resource list" button in the cloud-auth dialog (`BrowseFeedbackDialogManager.showCloudAuthenticationDialog`). "Resource" = registered entity, so RU/UK use удалить/видалити, consistent with `delete_resource_title` (UK "Видалити ресурс").

## 3. Open points

Resolved during S1 audit:

1. Complete list of missing keys - confirmed as the 6 above via `*resource*` audit.
2. Scope narrow vs full-project sweep - narrow (this ticket is Tier 2, resource-scoped by name). A full-project trilingual-parity sweep, if desired, is a separate broader ticket.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0799

## Related

- S0799 (resource-vs-folder terminology audit - surfaced this gap, but excluded it as out of scope).

## Last Audit

**Date:** 2026-07-01 (via /spec-next -> /spec-all)
**Verdict:** Verified

- Added 7 missing translations, all in `app_v2/src/main/res/values*/strings.xml`:
  - UK `error_importing_resources` = "Помилка імпорту ресурсів"
  - UK `import_resources_message` = "Додати ресурси SZA?"
  - UK `import_resources_title` = "Імпорт ресурсів"
  - UK `music_resource_unavailable` = "Ресурс з музикою недоступний"
  - UK `sza_resources_imported` = "Ресурси SZA імпортовано"
  - RU `remove_resource` = "Удалить ресурс"
  - UK `remove_resource` = "Видалити ресурс"
- File bytes verified as correct UTF-8 Cyrillic (console echo garbled, files clean).
- `check_strings_localized.ps1 -KeyPrefix "*resource*"` -> exit 0, 178/178 keys present in EN/RU/UK.
- `a.ps1 fr` (processStandardDebugResources) -> BUILD SUCCESSFUL, resources compile with the new strings.
- Scope confirmed narrow: `*resource*` audit is the authoritative complete set; no other prefix contributes resource keys. A full-project trilingual-parity sweep, if wanted, is a separate broader ticket.
- No ALL_FEATURES record: i18n-parity defect fix of existing strings, not a new capability.
