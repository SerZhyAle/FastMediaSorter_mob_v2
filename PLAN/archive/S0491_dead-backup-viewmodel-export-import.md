# Стратегическая спецификация: S0491 - Dead favorites + resource-share export/import path in BackupRestoreViewModel

**Ticket:** S0491
**Status:** Archived
**Priority:** 30
**Date:** 2026-06-17
**Tier:** 2 - Easy (ad-hoc, dead-code hygiene)
**Roadmap entry:** Ad-hoc - auto-captured during S0480 (2026-06-17)
**Tactical spec:** `PLAN/S0491_dead-backup-viewmodel-export-import/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-17

**Захвачено во время:** S0480

**Текст:**

Dead favorites + resource-share export/import path in BackupRestoreViewModel (unreachable after S0480).

Symptom: S0480 deleted the orphan BackupRestoreFragment + its layout. That fragment was the ONLY UI consumer of the favorites export/import and resource-share export/import flows in BackupRestoreViewModel. The live GeneralSettingsBackupHelper wires only Google Drive backup/restore (observes uiState). So these ViewModel members are now fully dead.

Evidence (grep over app_v2/src after S0480 deletions):
- exportFavorites / previewFavoritesImport / confirmFavoritesImport / exportAllResources / previewResourceImport / confirmResourceImport
- exportFavState / importFavState / exportResState / importResState
- all of the above now appear ONLY in app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/BackupRestoreViewModel.kt (no remaining caller).
- The 14 favorites/resource success string keys these methods used were already orphan and removed by S0480.
- The live resource-share export/import feature (MainActivity/MainEventHandler) uses a separate path and string resource_share_export_success was NOT shared with it.

Open product question (the one S0480 deferred): either (a) resurrect favorites + resource-share export/import in a real reachable settings screen, or (b) remove the dead ViewModel methods/flows AND audit their backing UseCases (FavoritesExport/Import, ResourceShare export/import) + repositories for further dead weight (Rule 20). Needs its own research to map the UseCase graph before deletion.

Origin: auto-captured during S0480 implementation (out-of-scope finding).

**Вложения:**

Вложений нет.

---

## 1. Проблема

После S0480 удалён единственный UI-потребитель экспорта/импорта избранного и обмена ресурсами (orphan-фрагмент настроек). Логика в слое представления настроек осталась целой, но недостижима из приложения. Пользователь больше не может выгрузить/загрузить избранное файлом или поделиться набором ресурсов через файл.

---

## 2. Цели

1. Экспорт и импорт избранного снова доступны из реального экрана настроек.
2. Экспорт и импорт обмена ресурсами (.fmsr) снова доступны оттуда же.
3. Функции живут рядом с уже существующим блоком резервного копирования, без отдельного экрана-сироты.

**Non-goals:**

- Воскрешение удалённого автономного экрана-фрагмента как отдельной вкладки.
- Изменение форматов файлов, движков экспорта/импорта или доменной логики.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Не плодить дубль UI резервного копирования Google Drive - переиспользовать существующий блок настроек.

### 3.2 Жёсткие ограничения

- **Flavor:** <затронутые варианты сборки>
- **API level:** <минимальный уровень Android или «без API-специфики»>
- **Wear OS:** <затрагивается или нет>
- **Производительность:** <бюджет CPU/память/батарея, если критично>
- **Совместимость данных:** <форма миграции без номера версии Room>
- **Локализация:** EN/RU/UK - всегда обязательно, или уточнение.
- **Доступность:** <TalkBack, touch target, не-цветовое отличие - если фича визуальная>

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** S0480 (источник находки), S0475 (исключил orphan-разметку из поиска по настройкам)

---

## 4. Контекст текущей архитектуры

Блок резервного копирования общих настроек уже размещает резервное копирование/восстановление Google Drive и переиспользует ту же модель представления, что держит потоки экспорта/импорта избранного и обмена ресурсами. Удалённый автономный фрагмент дублировал UI Google Drive и был единственной точкой входа в файловые потоки - поэтому после его удаления потоки осели мёртвым кодом, хотя модель представления цела.

---

## 5. Предлагаемый подход

Вернуть четыре действия (экспорт/импорт избранного, экспорт/импорт обмена ресурсами) в существующий блок резервного копирования общих настроек, переиспользуя уже внедрённую туда модель представления. Отдельный экран не воссоздаётся.

### 5.1 Основные столпы / модули

- Карточка «Данные и резервные копии» в общих настройках - новый дом для четырёх действий.
- Существующий помощник блока резервного копирования - принимает SAF-лончеры от фрагмента и держит диалоги предпросмотра/результата.

### 5.2 Потоки данных и событий

- UI настроек → SAF-выбор файла → модель представления → доменный сценарий → состояние UI → диалог/снэкбар.

### 5.3 Точки расширяемости

- Помощник остаётся открытым для добавления новых файловых действий резервного копирования по тому же шаблону «лончер + наблюдатель состояния».

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет. Продуктовая развилка S0480 решена владельцем в пользу воскрешения фичи (2026-06-17).

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Загромождение карточки настроек ещё четырьмя кнопками | Низкая | Перегруженный экран | Кнопки сгруппированы парами под разделителем, в портретной и альбомной разметке |
| Расхождение строк по локалям | Низкая | Непереведённые подписи | Ключи восстановлены из истории с паритетом EN/RU/UK |

---

## 8. Влияние на пользователя (docs/FEATURES)

Возвращается ранее существовавшая возможность. Запись о возможности фиксируется в `docs/ALL_FEATURES.jsonl`; публичный showcase обновляется только через `/skill-release`.

---

## 9. Архитектурные решения (ADR)

- Интеграция в существующий блок резервного копирования вместо воссоздания автономного фрагмента - устраняет дублирование UI Google Drive и гарантирует достижимость.

---

## 10. Связи с другими спеками

- S0480 - удалил orphan-фрагмент и осиротил эти потоки.
- S0475 - исключил orphan-разметку из индекса поиска по настройкам.
- S0422 - источник функции обмена ресурсами.

---

## 11. Критерии готовности (strategic-level)

1. Из общих настроек можно выгрузить избранное в файл и поделиться им.
2. Из общих настроек можно загрузить избранное из файла с выбором стратегии конфликтов.
3. Из общих настроек можно выгрузить и загрузить обмен ресурсами с предпросмотром.
4. Действия видны и работают в портретной и альбомной ориентациях.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0491` - создаст `PLAN/S0491_dead-backup-viewmodel-export-import/` с фазами.

---


## Last Audit

**Date:** 2026-06-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 - WARN 0 - FAIL 0 - MANUAL 0 - EXEMPT 0

### Manual / on-device

Device test 2026-06-18, emulator-5554 (Android 13, SDK 33), standard debug. Ticket resolved the S0480 fork toward RESURRECTION (the formerly-dead backup ViewModel export/import members now have live callers), not deletion. All PASS:

- [x] Static - previously-dead ViewModel members now called by `GeneralSettingsBackupHelper`/`GeneralSettingsFragment`; 4 buttons present in portrait + landscape; 4/4 resource-share strings present.
- [x] No overlap with S0475/S0480 orphan (`BackupRestoreFragment`/`fragment_settings_backup_restore` not re-created - that orphan stays separately dead).
- [x] Settings export -> wrote `FastMediaSorter_backup.json` (5125 B, fresh).
- [x] Export Favorites -> "Export Complete - Favorites exported successfully".
- [x] No crash; S0491 probe fired.

Debug tag removed from `GeneralSettingsBackupHelper` on Verified flip. Capability recorded in `docs/ALL_FEATURES.jsonl`.

**Evidence:** temp/s0491/ + adb logs.
