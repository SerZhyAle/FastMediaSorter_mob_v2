# Стратегическая спецификация: S1009 - Локальная папка как отправитель/получатель в операциях по расписанию

**Ticket:** S1009
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-12
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-12
**Tactical spec:** `PLAN/S1009_scheduled-ops-local-folder-picker/INDEX.md` (5 фаз). Research: `PLAN/S1009_scheduled-ops-local-folder-picker/research/01__hidden-resource-and-picker-audit.md`

> **Schema re-plan (2026-07-24, /spec-all):** live `AppDatabase` is `@Database(version = 43)` (migrations wired through `Migration42To43.kt`, schema exports through `43.json`); the resource entity still has no `is_hidden` column. Add it as migration **`43 -> 44`** (`Migration43To44.kt`), schema export `44.json`. Every `40 -> 41` reference below (§3.2, §5, §7, §11) is superseded by `43 -> 44`. Schema-export guard **S1050 is resolved** - `AppDatabaseSchemaExportTest` now parses `@Database(version)` straight from source, so no hardcoded `CURRENT_VERSION` bump is needed; the migration only has to commit its `44.json` export.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-12

**Текст:**

"Операции по расписанию". Отправитель может быть любой ресурс или локальная папка с подпапками или без, а получатель - любой ресурс который не виртуальный и не только для чтения или локальная папка. То есть в диалог выбора ресурсов для оправителя и получателя в самый верх нужно добавить опцию "Локальная папка", где её можно выбрать (браузер папок у нас уже где-то реализован). Проверять что папки дрступны для записи

---

## 1. Проблема

В «Операциях по расписанию» отправитель и получатель выбираются из выпадающих списков зарегистрированных ресурсов. Пользователь хочет назначать отправителем или получателем произвольную локальную папку ad-hoc, не заводя её видимой записью в общем списке ресурсов.

Сейчас такой возможности нет: пикеры отправителя/получателя - выпадающие списки по спискам ресурсов; произвольная локальная папка как ad-hoc источник/приёмник не поддерживается, а завести её обычным ресурсом означало бы засорить общий список ресурсов и destination-пикеры.

Область: диалог операций по расписанию (настройки).

---

## 2. Цели

1. В верх пикеров отправителя И получателя scheduled-операции добавить опцию «Локальная папка».
2. Выбор опции открывает существующий браузер папок; пользователь выбирает локальную папку (с подпапками или без).
3. Получатель проверяется на доступность для записи; отправитель может быть только для чтения (тогда операция форсится в COPY, как уже делает существующее ограничение read-only источника).
4. Выбранная папка сохраняется как СКРЫТЫЙ scoped-ресурс (новый флаг), на который ссылается FK операции, но который НЕ виден в общем списке ресурсов и destination-пикерах.
5. Механизм скрытого ресурса (флаг + фильтр запроса) строится один раз и переиспользуется в [[S1010]].

**Non-goals:**

- Отдельная реализация механизма скрытого ресурса для [[S1010]] - тот же механизм.
- Скрытие/дедуп уже видимых ресурсов.
- Изменение семантики каскада FK (resource -> scheduled_op).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Опция «Локальная папка» - первым пунктом в обоих пикерах.

### 3.2 Жёсткие ограничения

- **Flavor:** все.
- **API level:** без API-специфики; проверка записи учитывает scoped storage (SAF).
- **Wear OS:** watch-sync читает список ресурсов - фильтр видимости должен исключить скрытые и там (см. §6).
- **Производительность:** фильтр в существующих запросах, без новых тяжёлых операций.
- **Совместимость данных:** Room-миграция 43 -> 44 (добавление колонки `is_hidden INTEGER NOT NULL DEFAULT 0`); существующие строки видимы по умолчанию; экспорт схемы `44.json`. Schema-guard S1050 уже резолвлен (парсит версию из `@Database` в исходнике) - ручной bump константы не нужен.
- **Локализация:** EN/RU/UK обязательно (строка «Локальная папка», ошибка недоступности записи).
- **Доступность:** новый верхний пункт - клавиатура/D-pad/касание, корректный порядок фокуса.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1010 (переиспользует механизм скрытого ресурса; сейчас BlockByOtherTask на S1009), S1050 (устаревший schema-export guard - обновить при этой миграции), S0731 (schema-export guard), S0200 (needs_sign_in - precedent boolean-миграции).
- **UI:** «Локальная папка» в самом верху пикеров отправителя и получателя scheduled-операции; выбор -> существующий браузер папок.
- **Данные:** новый флаг «не показывать в списке» на модели ресурса + фильтр запроса; FK операции резолвится, строка скрыта из UI.
- **Orphan-lifecycle:** авто-удаление скрытого ресурса при отвязке/переназначении/очистке связанной операции; строгий 1:1 FK, без reference-count. Область удаления ограничена строками `is_hidden`; переиспользованный видимый ресурс не удаляется.
- **Scope фильтра:** дополнительно прятать скрытые local-folder ресурсы на Browse home и переключателе ресурсов (сверх основного списка ресурсов, destination-пикеров, Wear-sync). Поиск по ресурсам и счётчики/лимиты сознательно НЕ фильтруются - скрытая строка может там всплыть (осознанное решение владельца).
- **Дедуп:** совпадение пути ad-hoc-папки с уже видимым LOCAL-ресурсом -> переиспользовать видимый ресурс как FK-цель; скрытую копию не создавать.
- **Хостинг браузера:** `OperationsScheduledManager` хостит SAF `OpenDocumentTree()` напрямую (переиспользуя writability-check и шаблон построения LOCAL-ресурса), не in-app браузер из AddResource; UX - системный picker ОС.
- На рекомендации спеки (§9 ADR / §5), явный owner-fork не потребовался: creation timing (на Save), UX недоступного получателя (отклонять тостом, read-only источник -> force COPY), схема хранения (`is_hidden` колонкой).

---

## 4. Контекст текущей архитектуры

Диалог создания/правки scheduled-операции содержит выпадающий пикер отправителя (source, non-null FK) и получателя (target, nullable FK), заполняемые списками ресурсов; выбранное имя резолвится обратно в ресурс. Ни один из пикеров не является браузером папок. FK `source_resource_id`/`target_resource_id` ссылаются на строку ресурса с `onDelete=CASCADE`, но каскад идёт только в сторону resource -> scheduled_op: удаление или правка операции не трогает связанный ресурс, обратного каскада нет.

Переиспользуемый локальный браузер папок, SAF-пикер и конструкция LOCAL-ресурса уже существуют, но привязаны к экрану добавления ресурса; диалог scheduled-операции живёт в настройках и не имеет собственного ActivityResult-хоста. Флага «скрытый ресурс» пока нет; списковые surface'ы (use-case списка ресурсов, use-case destination'ов, DAO-запросы) не фильтруют скрытые - без фильтра папка-ресурс всплыла бы в общем списке, destination'ах и самих пикерах. Проверка доступности для записи локальной папки уже реализована в локальном сканере.

---

## 5. Предлагаемый подход

Добавить на модель ресурса булев флаг «скрытый» (Room-миграция 43 -> 44, по precedent'у существующего boolean-флага), протянуть его через маппинг сущность<->домен. Исключить скрытые ресурсы из видимых surface'ов: use-case списка ресурсов, use-case destination'ов, главный список, Browse home и переключатель ресурсов, Wear watch-sync (решение владельца 2026-07-18). Поиск по ресурсам и счётчики/лимиты сознательно НЕ фильтруются - скрытая строка может там всплыть. В пикеры отправителя и получателя scheduled-операции добавить верхним пунктом «Локальная папка»; поскольку диалог не имеет ActivityResult-хоста, лаунчер SAF `OpenDocumentTree()` хостит напрямую `OperationsScheduledManager` (не in-app браузер из AddResource), результат прокидывается в диалог; переиспользовать существующие writability-check, takePersistableUriPermission и конструкцию LOCAL-ресурса, не дублируя их. Проверять доступность для записи: получатель - отклонять недоступную папку (тост-ошибка), отправитель - разрешать read-only и форсить COPY. Создавать и персистить скрытый ресурс на Save диалога (не в момент выбора папки), чтобы не оставлять orphan при отмене; полученный id класть в FK операции. Построить явный orphan-cleanup: при удалении, смене отправителя/получателя и очистке всех операций авто-удалять ранее связанный ресурс строго 1:1, только если он скрытый (`is_hidden`); reference-count не ведём. При дедупе (совпадение пути с видимым LOCAL-ресурсом) FK ссылается на видимый ресурс, и он при отвязке НИКОГДА не удаляется.

### 5.1 Основные столпы / модули

- Модель ресурса - новый флаг «скрытый» + Room-миграция.
- Фильтр видимости - исключение скрытых из видимых surface'ов.
- Пикеры scheduled-op - верхний пункт «Локальная папка» + хостинг лаунчера браузера.
- Проверка доступности для записи локальной папки.
- Orphan-cleanup скрытых ресурсов.

### 5.2 Потоки данных и событий

- Диалог scheduled-op -> «Локальная папка» -> SAF `OpenDocumentTree()` (хост `OperationsScheduledManager`) -> проверка записи -> (на Save) дедуп по type=LOCAL + path: совпадение -> FK на видимый ресурс; иначе -> скрытый LOCAL-ресурс -> id -> FK операции.
- Список ресурсов / destination'ы -> фильтр по флагу «скрытый».

### 5.3 Точки расширяемости

- Механизм скрытого ресурса переиспользуется в [[S1010]].
- Верхний пункт пикера - точка для будущих специальных источников/приёмников.

---

## 6. Открытые вопросы / Research items

Требуют решения владельца перед реализацией (полный аудит: **Артефакт:** `PLAN/S1009_scheduled-ops-local-folder-picker/research/01__hidden-resource-and-picker-audit.md`):

1. Orphan-cleanup: при удалении/смене отправителя-получателя/очистке всех операций удалять связанный скрытый ресурс автоматически или оставлять? Скрытый ресурс 1:1 с операцией (удалять при отвязке) или разделяемый (нужен подсчёт ссылок)? **Status: Resolved (2026-07-18)** - авто-удаление; строгий 1:1 FK, без reference-count; область ограничена строками `is_hidden` (переиспользованный видимый ресурс не удаляется, см. §9 cross-interaction).
2. Scope фильтра видимости: какие именно surface'ы исключают скрытые - список ресурсов, destination'ы, browse home, Wear watch-sync, backup/export, поиск - чтобы скрытый ресурс нигде не протёк. **Status: Resolved (2026-07-18)** - дополнительно прятать на Browse home и переключателе ресурсов (сверх основного списка, destination-пикеров, Wear-sync). Поиск по ресурсам и счётчики/лимиты сознательно НЕ исключены - скрытая строка может там всплыть (осознанное решение владельца, не упущение).
3. Creation timing: создавать скрытый ресурс в момент выбора папки (проще, но orphan при отмене диалога) или только на Save (staging)?
4. Дедуп: если выбранная папка совпадает с уже видимым ресурсом (по type=LOCAL + path) - переиспользовать его (и не скрывать) или всегда создавать отдельную скрытую копию? **Status: Resolved (2026-07-18)** - переиспользовать видимый ресурс как FK-цель; скрытую копию не создавать.
5. Хостинг браузера: извлечь и переиспользовать браузер экрана добавления ресурса или хостить отдельный лаунчер во фрагменте/менеджере настроек? **Status: Resolved (2026-07-18)** - `OperationsScheduledManager` хостит SAF `OpenDocumentTree()` напрямую (writability-check + шаблон построения LOCAL-ресурса переиспользуются); не in-app браузер из AddResource; UX - системный picker ОС.
6. UX недоступного получателя: жёстко блокировать выбор с тостом или разрешать с предупреждением; для отправителя - подтвердить авто-форс COPY при read-only.
7. Схема хранения «скрытого»: колонка `is_hidden` на `resources` (рекомендуется) vs отдельная таблица связей.
8. Home name-filter vs «поиск по ресурсам» (выявлено research'ем 2026-07-24). На главном экране фильтр по имени идёт через тот же FTS-запрос (`searchResourcesFts`), что и «поиск по ресурсам», а решения владельца требуют: browse home - скрывать, поиск - показывать. **Status: Resolved (2026-07-24, по принципу владельца)** - name-search ветка (FTS) НЕ фильтруется (владелец сознательно допустил всплытие скрытого в поиске), а browse home по умолчанию и фильтры type/media/sort (standard-SQL ветка `getFilteredResources`) фильтруют скрытое. Отмечено в финальном отчёте для возможного override владельцем.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Скрытый ресурс протекает в необёрнутый surface (browse/Wear/backup) | Средняя | Мусор/утечка в UI или экспорте | Полный аудит и фильтр всех surface'ов (§6.2) |
| Накопление orphan-скрытых ресурсов | Средняя | Разрастание таблицы, «мёртвые» строки | Явный orphan-cleanup (§6.1) |
| Ложно-положительная проверка записи (scoped storage) | Средняя | Сбой записи позже | Реальная проверка/persistable permission |
| Несовпадение schema-hash при миграции | Низкая | Крэш при открытии БД | `@ColumnInfo(defaultValue="0")` + экспорт `44.json` (guard S1050 авто-деривит версию из исходника) |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая возможность: назначать локальную папку отправителем или получателем операции по расписанию прямо из диалога, без засорения списка ресурсов. Текст для docs/FEATURES (EN/RU/UK) - при реализации.

---

## 9. Архитектурные решения (ADR)

- Флаг `is_hidden` колонкой на `resources` (не отдельная таблица связей) - минимальная поверхность, precedent существующего boolean-флага.
- Фильтр видимости в use-case/запросе, не в UI - централизованно, исключает утечки.
- Создание скрытого ресурса на Save диалога, не в момент выбора - минимизация orphan.
- Дедуп ad-hoc-папки: совпадение type=LOCAL + path с видимым ресурсом -> переиспользовать видимый как FK-цель, скрытую копию не создавать (решение владельца 2026-07-18).
- Orphan-cleanup: строгое 1:1 авто-удаление скрытого ресурса при отвязке/переназначении/очистке, без reference-count; область удаления ограничена строками `is_hidden`.
- Хостинг пикера: `OperationsScheduledManager` хостит SAF `OpenDocumentTree()` напрямую (не извлекать in-app браузер из AddResource); UX - системный picker ОС.
- Cross-interaction (verbatim): «Dedup=переиспользовать-видимый + Orphan=авто-удаление означает, что авто-удаление ОБЯЗАНО быть ограничено строками is_hidden - переиспользованный видимый ресурс при отвязке НИКОГДА не удаляется. Причина, по которой Dedup=B безопасен: op не должна молча удалять ресурс, которым владеет пользователь».

---

## 10. Связи с другими спеками

- **S1010** - переиспользует механизм скрытого ресурса, вводимый здесь; сейчас BlockByOtherTask на S1009.
- **S1050** - schema-export guard: резолвлен (парсит `@Database(version)` из исходника, не хранит `CURRENT_VERSION`); эта миграция требует только коммита `44.json`, ручной bump не нужен.
- S0731 (schema-export guard), S0200 (needs_sign_in - precedent boolean-миграции).

---

## 11. Критерии готовности (strategic-level)

1. В самом верху пикеров отправителя и получателя scheduled-операции есть опция «Локальная папка».
2. Выбор открывает существующий браузер папок; получатель принимается только если доступен для записи, отправитель может быть read-only (операция форсится в COPY).
3. Выбранная папка сохраняется скрытым ресурсом: FK операции резолвится, но она не отображается ни в одном списке ресурсов/destination'ов.
4. Удаление/правка/очистка операций не оставляет orphan-скрытых ресурсов (по утверждённой политике).
5. Миграция 43 -> 44 + экспорт схемы `44.json`; строки EN/RU/UK; тесты (миграция, фильтр видимости, orphan-cleanup).

---

## Owner decisions (2026-07-14)

- Локальная папка, выбранная ad-hoc из диалога отправителя/получателя операции по расписанию, становится СКРЫТОЙ/scoped-ссылкой, а НЕ видимой записью в основном списке ресурсов. Следствие: нужен флаг «не показывать в списке» на модели ресурса (например, `isHidden`/`isSystem`) плюс фильтр запроса, чтобы FK у `ScheduledOperationEntity` резолвился, а строка не попадала на экран ресурсов; такого флага сейчас нет - его надо добавить. Механизм скрытого ресурса строится один раз и переиспользуется с [[S1010]].

---

### Quiz decisions (2026-07-18)

- Orphan-cleanup (auto-delete vs reference-count)? -> авто-удаление, строгий 1:1 FK без reference-count (только строки `is_hidden`; переиспользованный видимый ресурс не трогаем).
- Scope фильтра видимости? -> дополнительно прячем на Browse home и переключателе ресурсов; поиск по ресурсам и счётчики/лимиты сознательно НЕ фильтруем.
- Дедуп с видимым ресурсом? -> совпадение пути у LOCAL -> переиспользуем видимый ресурс как FK-цель, скрытую копию не создаём.
- Хостинг браузера? -> `OperationsScheduledManager` хостит SAF `OpenDocumentTree()` напрямую; UX - системный picker ОС.
- Cross-interaction -> авто-удаление ограничено строками `is_hidden`: переиспользованный видимый ресурс при отвязке никогда не удаляется (op не должна молча удалять ресурс пользователя).

---

## Last Audit

### Manual device test (2026-07-27, on-device verification of the `checkTargetReachability` hotfix)

Device `emulator-5554`, `sdk_gphone64_x86_64`, Android 15 (SDK 35), 1080x2424. Build under test
`com.sza.fastmediasorter.debug 2.60.7270.028-DEBUG`, installed from the supplied APK and confirmed via
`dumpsys package .. | grep versionName`; no rebuild. Evidence: `temp/S1009/s1009_run.log`,
`temp/S1009/saf_src.png`, `temp/S1009/saf_dst.png`, `temp/S1009/target_pick.png`.

Result: 4 PASS. This re-runs item 3 of the 2026-07-26 audit, which was the FAIL.

- [x] **1. Scheduled COPY created with a SAF-picked local folder as DESTINATION - PASS.** expected: both endpoints set through the OS picker | actual: Source `Local Folder` -> `com.google.android.documentsui/.picker.PickActivity` -> `/Download/FastMediaSorter_Test/Ops/S1009_src` (grant `Allow .. access files in S1009_src?` -> ALLOW); Destination likewise -> `S1009_dst`. Dialog then read `actvSource=S1009_src`, `actvOperation=Copy`, `actvTarget=S1009_dst`; saved as op id 3.
- [x] **2. "Run now" executes the op - PASS.** expected: worker starts and completes | actual: `WM-WorkerWrapper: Starting work for .. ScheduledOperationsWorker`; `ScheduledOperationsWorker: starting op=3`; `ExecuteScheduledOperationUseCase: ScheduledOp[3] fired: COPY`; `Worker result SUCCESS`.
- [x] **3. Files actually land in the destination - PASS (verified by directory listing, not by UI).** expected: 2 files in `S1009_dst` | actual: pre-run `ls` on the destination was empty (`total 0`); post-run `ls -la` shows `photo_001.jpg` 8193700 B and `photo_002.jpg` 8309274 B, both mtime 2026-07-27 00:46. Integrity confirmed byte-for-byte: `md5sum` matches source exactly - `3387145dd02abc6733193ec3917d864f` and `c4507a2adbede375f544dac3147b7420` on both sides.
- [x] **4. Worker log is not `0 files, errors=1`, and the pre-flight probe fires - PASS.** expected: non-zero files, `errors=0`, probe present | actual: `ScheduledOperationsWorker: op=3 done - 2 files, errors=0`, plus per-file `ScheduledOp[3] COPY OK photo_001.jpg` / `photo_002.jpg`. Probe present verbatim: `ExecuteScheduledOperationUseCase: S1009: SAF target pre-flight, writable=true` at 00:46:42.230 - i.e. the `content://` target now routes through `CheckLocalFolderWritableUseCase` instead of the old `File(target.path)` check, and `LocalCopyFileOperation` then targets the SAF tree directly.

Still open in this ticket, unchanged and re-observed this run: the list row renders both endpoints as
`(deleted) -> (deleted)` (`tvSourceName`/`tvTargetName`) because `OperationsScheduledManager` resolves
names off the hidden-filtered list (P2). The non-writable-receiver branch remains NOT EXERCISED for the
same mechanical reason as before - every tree the system picker grants is writable and `chmod 555` does
not stick on FUSE storage; all picks this run logged `writable=true`.

### Manual device test (2026-07-26, `/spec-test-device`)

Device `emulator-5554`, `sdk_gphone64_x86_64`, Android 15 (SDK 35), 1080x2424 @ 420dpi.
Build under test `com.sza.fastmediasorter.debug 2.60.7262.102-DEBUG` (`versionCode=260726210`, standard
flavor, installed 22:45); no rebuild, no reinstall. Full evidence:
`temp/S1009/device_run_20260726_2312.md`, log `temp/S1009/run.log`, DB snapshots under `temp/S1009/*/`.

Result: 4 PASS, 1 FAIL, 1 NOT EXERCISED.

- [x] **1. "Local folder" atop both pickers, opens the system picker - PASS.** expected: `Local Folder` is item 0 of Source and Destination and opens the OS folder picker | actual: Source listed `Local Folder` then `All Files`, Destination listed `Local Folder` first; both selections moved focus to `com.google.android.documentsui/.picker.PickActivity`. Probes `S1009: local folder picked, side=SOURCE, writable=true` (23:00:08) and `side=TARGET, writable=true` (23:01:30).
- [ ] **2. Non-writable receiver rejected with a toast; read-only source forces COPY - NOT EXERCISED.** Both branches are gated on `CheckLocalFolderWritableUseCase` returning `false`, which resolves to `DocumentFile.canWrite()` for a SAF tree (`LocalMediaScanner.isWritableSAF`). No non-writable tree is producible on this emulator: `chmod 555` on an external-storage folder did not stick (mode stayed `drwxrws---`, FUSE ignores it), and every tree the system picker allows is writable. All four picks in this run logged `writable=true`, so only the writable branch was entered. No weaker check was substituted.
- [!] **3. Folder saves and the op runs, but stays hidden - FAIL (run half).** Save: `resources` gained `S1009_src`/`S1009_dst` with `is_hidden=1` and `content://` paths, op row `source=2 target=3 COPY`. Hiding: absent from browse home (ALL + Local tabs, scrolled to end) and from a fresh dialog's Source (`Local Folder, All Files, Recent Media, All Music, All Videos, Camera Photos, All Images, All Documents, Downloads`) and Destination (`Local Folder, Downloads`) dropdowns. Run: expected 2 files copied | actual `ScheduledOperationsWorker: op=1 done - 0 files, errors=1`, destination folder still empty, because `ExecuteScheduledOperationUseCase` (lines 246-248) pre-flights a LOCAL target with `File(target.path).exists()` and has no `content://` branch - `Target directory 'content://...S1009_dst' does not exist`. The source side is SAF-aware, the target side is not, so an ad-hoc SAF folder can never work as receiver. **P1.**
- [x] **4. Editing shows and preserves the folder - PASS.** expected: Edit dialog displays both folders and re-saving keeps the FKs | actual: `actvSource=S1009_src`, `actvTarget=S1009_dst`; after re-save the op still pointed at 2/3 and `resources` still held exactly 3 rows (no duplicate hidden copy). Probe `S1009: saveOperation op=1`.
- [x] **5. Delete / clear-all removes the hidden resource - PASS (both paths).** Delete: op count 0 and hidden ids 2/3 gone; probe `S1009: op 1 deleted, cleaning hidden FKs`. Clear all: a second op produced hidden ids 11/12, and after `Clear all` -> `Delete` the op count was 0 and 11/12 were gone. No orphan `is_hidden=1` row survived either path.
- [x] **6. Migration 43 -> 44 applies cleanly on upgrade - PASS (deliberately forced).** A plain launch exercises nothing here: the device DB was created fresh at 44 after the earlier data wipe. The migration was therefore forced (`temp/S1009/exercise-migration.ps1`) by downgrading a consolidated copy of the live DB to schema 43 - `is_hidden` dropped, `identity_hash` set to the exported 43 value `10a106ca640d29823ce6dfd72f82bb60`, `user_version=43` - pushing it back into the sandbox and relaunching. expected: Room migrates the 8 pre-existing rows to 44 | actual: launched to `MainActivity` with no crash and no Room migration error; `user_version=44`, `identity_hash=7909f4bf5822c85ac9ea03f339da8634` matching `schemas/.../44.json`, all 8 rows preserved with `is_hidden=0`.

Secondary defect (P2, outside the six acceptance items): the scheduled-op list row renders both
endpoints as `(deleted)` when the FKs point at hidden resources. `OperationsScheduledManager` passes
`resourceNameProvider = { id -> viewModel.resources.value.find { it.id == id }?.name }`, i.e. the
hidden-filtered visible list; the dialog got `augmentWithHiddenFk` but the list adapter did not.
