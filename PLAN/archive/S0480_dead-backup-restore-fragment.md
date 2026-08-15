# Draft: S0480 - Мёртвый orphan-фрагмент BackupRestoreFragment + разметка

**Ticket:** S0480
**Status:** Archived
**Priority:** 30
**Date:** 2026-06-17
**Tier:** 2 - Easy (dead-code hygiene)
**Origin:** auto-captured during S0475 implementation (out-of-scope finding)

> **Scope:** DRAFT idea inbox. Raw capture, no research/approval/spec-tech chaining.

---

## 0. Raw capture / Evidence

**Симптом:** `BackupRestoreFragment` (`ui/settings/fragments/BackupRestoreFragment.kt`, ~478 LOC) и его разметка `res/layout/fragment_settings_backup_restore.xml` нигде не инстанцируются и не подключены - ни ViewPager `SettingsActivity`, ни nav-graph, ни манифест, ни рефлексия. Реальные backup/restore/export-import строки живут в `fragment_settings_general` + `GeneralSettingsBackupHelper`.

**Evidence:**
- `grep BackupRestoreFragment` по `app_v2/src/main` даёт только сам файл (плюс `dev/CHANGELOG.md`).
- Кнопки `btnExportFavorites`/`btnImportFavorites`/`btnExportResources`/`btnImportResources` существуют ТОЛЬКО в этой orphan-разметке и orphan-фрагменте.
- S0475 уже исключил `fragment_settings_backup_restore` из индекса поиска по настройкам.

**Нужно (предварительно):**
- Подтвердить, что фрагмент не является scaffolding активного тикета (git blame, PLAN).
- Если мёртв: удалить `BackupRestoreFragment.kt` + `fragment_settings_backup_restore.xml` (+ layout-land/иные варианты, если есть) + осиротевшие string-ключи, относящиеся только к нему.
- Проверить, действительно ли экспорт/импорт favorites/resources - забытая фича: если да, она недостижима из UI (отдельный продуктовый вопрос).
- Сборка на target-варианте (Rule 20: проверять на release/target).

**Дедуп:** `search.ps1 "BackupRestoreFragment"`/`"backup_restore"` - открытого тикета нет (2026-06-17).

---

## 1. Resolution (2026-06-17)

Подтверждено мёртвым и удалено (static verification, без сборки по запросу):

- `BackupRestoreFragment.kt` - orphan, единственный референс был в самом файле + пояснительных комментариях search-индекса.
- `fragment_settings_backup_restore.xml` - orphan-разметка фрагмента (layout-land варианта нет).
- `dialog_import_favorites_preview.xml` - инфлейтился только удалённым фрагментом, стал orphan.
- 17 orphan string-ключей (EN/RU/UK) удалены через `set-android-string.ps1`, все с нулём референсов: favorites export/import (`backup_export_favorites`, `backup_import_favorites`, `export_fav_*`, `import_fav_action`, `import_fav_preview_*`, `import_fav_result_*`, `import_fav_success_title`, `import_fav_conflict_*`) + `resource_share_export_success`.
- Stale-комментарии вычищены: `SettingsSearchLayoutCatalog.kt`, `SettingsSearchTabMapping.kt`, `card_google_account.xml`.

Намеренно НЕ тронуто (вне scope, отложено в S0491):

- `BackupRestoreViewModel` остаётся: его backup/restore (Google Drive) поток жив - используется `GeneralSettingsBackupHelper`.
- Мёртвый favorites/resource-share export-import путь в этом ViewModel (методы + flows + строки `import_fav_invalid_file`/`import_fav_failed` + backing UseCases) запаркован как S0491 - удаление сейчас сломало бы компиляцию ViewModel.

Финальная проверка: ноль висячих ссылок на удалённые артефакты в `app_v2/src`. Сборка не запускалась (по запросу) - компиляция отложена.
