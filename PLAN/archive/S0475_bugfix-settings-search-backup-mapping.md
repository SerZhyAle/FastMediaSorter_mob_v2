# Draft: S0475 - Неверный маппинг раздела в индексе поиска по настройкам (backup-restore → destinations)

**Ticket:** S0475
**Status:** Archived
**Priority:** 90
**Date:** 2026-06-17
**Tier:** 2 - Easy (ad-hoc bugfix)
**Origin:** auto-captured during S0474 research (out-of-scope finding)

> **Scope:** DRAFT idea inbox. Raw capture, no research/approval/spec-tech chaining.

---

## 0. Raw capture / Evidence

Обнаружено при исследовании S0474 (артефакт-ресёрч архитектуры настроек).

**Симптом:** глобальный поиск по настройкам для backup/restore-настроек навигирует пользователя на секцию «destinations» раздела операций - не туда, где настройка реально находится.

**Evidence:** маппинг в индексе поиска по настройкам связывает `fragment_settings_backup_restore` с `sectionId = "destinations"` (`ui/settings/.../SettingsSearchTabMapping.kt`, ~стр. 46-49). Backup-Restore не является частью Destinations.

**Нужно (предварительно):**
- Определить корректный `sectionId` для backup-restore.
- Поправить запись в маппинге индекса поиска и согласовать с доступностью раздела в поиске.
- Регресс: поиск по backup-настройке открывает правильную секцию.

**Дедуп:** проверено `search.ps1 "search"`/`"settings"` - открытого тикета на этот симптом нет (2026-06-17).

---

## 1. Resolution

**Корневая причина (уточнена при реализации):** реальные элементы backup/restore (`btnBackup`, `btnRestore`, экспорт/импорт настроек) физически лежат в `fragment_settings_general` (вкладка General). Индекс поиска дополнительно сканировал orphan-разметку `fragment_settings_backup_restore`, которая используется только классом `BackupRestoreFragment` - а он нигде не инстанцируется (мёртвый код). Эта запись маппинга давала три дефекта:
- неверный таб: `OPERATIONS/"destinations"` вместо General;
- дубли записей `btnBackup`/`btnRestore` (они уже индексируются из `fragment_settings_general`);
- фантомные записи `btnExportFavorites`/`btnImportFavorites`/`btnExportResources`/`btnImportResources`, чьи view id не существуют ни в одном живом фрагменте - навигация по ним всегда проваливается.

**Исправление:** `R.layout.fragment_settings_backup_restore` исключён из `SettingsSearchLayoutCatalog.layoutResIds` и из `SettingsSearchTabMapping.byLayout`. Настоящие строки backup/restore/export-import остаются в поиске через `fragment_settings_general` и корректно ведут на вкладку General.

**Изменённые файлы:**
- `ui/settings/search/SettingsSearchLayoutCatalog.kt`
- `ui/settings/search/SettingsSearchTabMapping.kt`
- `ui/settings/SettingsActivity.kt` (debug-тег маршрутизации поиска)

**Параллельная находка (parked):** orphan `BackupRestoreFragment` + `fragment_settings_backup_restore.xml` - мёртвый код, кандидат на удаление отдельным тикетом.

---

## Last Audit

**Date:** 2026-06-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 6 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

### Manual / on-device

Device test: 2026-06-18, emulator-5554 (Android 13, SDK 33), standard debug. All PASS:

- [x] Search `backup` -> 2 results both "Section: General" ("Backups, restore and settings export", "Backup settings to Google Drive"); no Operations/destinations result, no phantom rows.
- [x] Tap `backup` result -> lands on General tab, scrolls to backup/restore section.
- [x] Logcat probe -> `S0475: settings search routes key=headerAppData -> tab=GENERAL section=general`.
- [x] Search `export favorites` -> single live result "Section: General"; no broken/dead-view entry.

Static: `fragment_settings_backup_restore` excluded from `SettingsSearchLayoutCatalog` + `SettingsSearchTabMapping` (0 refs in search pkg); debug tag removed from `SettingsActivity` on Verified flip. (§FEATURES EXEMPT - search-routing bugfix, no new capability.)

**Evidence:** temp/adb_log_20260618_121206.log.
