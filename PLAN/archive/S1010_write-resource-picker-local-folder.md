# Стратегическая спецификация: S1010 - Локальная папка в диалоге выбора ресурса-получателя

**Ticket:** S1010
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-12
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-12
**Tactical spec:** `PLAN/S1010_write-resource-picker-local-folder/INDEX.md` (5 фаз)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-12

**Текст:**

В настройках у нас много мест, где можно выбрать ресурс для записи (для фото, видео, скриншотов, загрузок и так далее..
получатель - любой ресурс который не виртуальный и не только для чтения и новое: _или локальная папка_. То есть в диалог выбора ресурсов для в самый верх нужно добавить опцию "Локальная папка", где её можно выбрать (браузер папок у нас уже где-то реализован). Проверять что папки доступны для записи. Смотри S1009

---

## 1. Проблема

В настройках есть несколько мест, где для операций записи выбирается ресурс-получатель (фото, видео, скриншоты, загрузки и т.д.). Сейчас получателем может быть только уже зарегистрированный ресурс, который не виртуальный и не только для чтения. Пользователь хочет назначать получателем и произвольную локальную папку, не заводя её как отдельный видимый ресурс в общем списке.

Область: диалог выбора ресурса-получателя записи в настройках.

---

## 2. Цели

1. В верх диалога выбора ресурса-получателя записи добавить опцию «Локальная папка».
2. Выбор опции открывает существующий браузер папок; пользователь выбирает локальную папку.
3. Выбранная папка проверяется на доступность для записи перед принятием.
4. Выбранная папка становится получателем для данной настройки, но НЕ появляется видимой записью в общем списке ресурсов (скрытый scoped-ресурс).

**Non-goals:**

- Отдельная реализация механизма «скрытого ресурса» - переиспользуется механизм из [[S1009]].
- Изменение диалогов, не относящихся к выбору получателя записи.
- Новый браузер папок - используется существующий.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Опция «Локальная папка» - первым пунктом списка (в самом верху).

### 3.2 Жёсткие ограничения

- **Flavor:** все (настройки получателей записи общие).
- **API level:** без API-специфики; проверка записи учитывает scoped storage.
- **Wear OS:** не затрагивается.
- **Производительность:** без изменений.
- **Совместимость данных:** скрытый ресурс - через флаг/фильтр из [[S1009]]; без отдельной миграции этим тикетом.
- **Локализация:** EN/RU/UK обязательно (строка «Локальная папка»).
- **Доступность:** новый пункт списка - клавиатура/D-pad/касание, корректный порядок фокуса.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1009 (жёсткая зависимость - механизм скрытого ресурса), S1012 (сосед по батчу companion-ресурсов).
- **UI:** новая опция «Локальная папка» в самом верху селектора получателя записи; выбор -> существующий браузер папок.
- **Данные:** выбранная папка - скрытый scoped-ресурс, привязанный только к настройке, невидимый в общем списке (флаг + фильтр запроса из S1009).

---

## 4. Контекст текущей архитектуры

Выбор ресурса-получателя записи в настройках проходит через диалог-селектор получателя и менеджер настроек операций. Получатель фильтруется как не-виртуальный, доступный для записи ресурс. Браузер локальных папок в приложении уже есть (обработчик выбора папки/SAF). Механизма «скрытого» ресурса (флаг «не показывать в списке» + фильтр запроса) пока нет - его вводит [[S1009]]; без него локальная папка либо не может быть представлена как ресурс, либо засорит общий список.

---

## 5. Предлагаемый подход

Добавить первым пунктом селектора получателя записи опцию «Локальная папка». При выборе - открыть существующий браузер папок, получить локальный путь, проверить доступность для записи, и сохранить папку как скрытый scoped-ресурс (флаг из [[S1009]]), на который ссылается конкретная настройка получателя. В общий список ресурсов такой ресурс не попадает (фильтр запроса из [[S1009]]).

### 5.1 Основные столпы / модули

- Селектор получателя записи - новая верхняя опция + маршрут в браузер папок.
- Скрытый ресурс - переиспользование механизма [[S1009]] (флаг + фильтр).
- Проверка доступности для записи локальной папки.

### 5.2 Потоки данных и событий

- Настройка получателя -> селектор -> «Локальная папка» -> браузер папок -> проверка записи -> скрытый ресурс (флаг S1009) -> привязка к настройке.

### 5.3 Точки расширяемости

- Верхняя опция селектора - точка для будущих специальных получателей.

---

## 6. Открытые вопросы / Research items

1. **Форма механизма скрытого ресурса.** Определена в [[S1009]] (Verified): поле `MediaResource.isHidden` + колонка `is_hidden` (`ResourceEntity`, миграция `Migration43To44`, БД уже на `version=44` - для S1010 новой схемы не требуется). Use cases `CheckLocalFolderWritableUseCase`, `ResolveLocalFolderResourceUseCase` (дедуп по пути + persist скрытого ресурса), `CleanupHiddenResourceUseCase` (orphan-cleanup, no-op на null/невидимый ресурс) - все переиспользуются без изменений. **Статус: Resolved (2026-08-02, тактика S1010 на основе завершённого S1009).**
2. **Полный перечень настроек-получателей.** 7 полей `AppSettings`, сгруппированных в 3 точки вставки: `OperationsSettingsFragment.showDestinationPicker()` (linkAutoDownloadResourceId + через `OperationsCaptureManager` - cameraPhotosDestinationResourceId, videoRecordingDestinationResourceId, micRecordingDestinationResourceId, screenRecordingDestinationResourceId), `EdgeGestureConfigDialogFragment.showDestinationPicker()` (screenshotDestinationResourceId), `DestinationPickerDialog` (videoSnapshotResourceId, через `VideoSettingsFragment`). Эфемерное действие "сохранить лог как ресурс" (`GeneralSettingsLogHelper`, тоже через `DestinationPickerDialog`) - НЕ входит в объём: это не персистентная настройка получателя записи, а разовое действие; опция «Локальная папка» туда не добавляется (opt-in параметр `DestinationPickerDialog` остаётся `null`/по умолчанию для этого вызова). **Статус: Resolved (2026-08-02).**
3. **Метод проверки доступности записи.** Переиспользуется `CheckLocalFolderWritableUseCase` как есть (persistable SAF-permission + `DocumentFile.canWrite()` через `LocalMediaScanner.isWritableSAF`, timeout 5s -> false) - тот же механизм, что и в [[S1009]], новой реализации не требуется. **Статус: Resolved (2026-08-02).**

**Найдено при тактике, вне объёма S1010:** 4 из 7 настроек (camera/video/mic/screen-recording) в итоге пишут через `CaptureDestinationPolicy`, который резолвит только `java.io.File` без ветки `content://`/SAF - если пользователь укажет туда SAF-папку (через новую опцию этого тикета или уже сегодня вручную зарегистрированным LOCAL-ресурсом), фактическая запись при захвате может не сработать. Это предсуществующий пробел (не вносится этим тикетом), заведён отдельным тикетом [[S1354]] - S1010 не блокируется им и добавляет опцию единообразно во все 7 настроек, как и заявлено в §2.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Скрытый ресурс протекает в общий список | Средняя | Мусор в списке ресурсов | Полный фильтр запроса из [[S1009]] |
| Ложно-положительная проверка записи (scoped storage) | Средняя | Сбой записи позже | Реальная проверка записи/persistable permission |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая возможность: назначать получателем записи произвольную локальную папку прямо из селектора, без засорения списка ресурсов. Текст для docs/FEATURES (EN/RU/UK) - при реализации.

---

## 9. Архитектурные решения (ADR)

- Переиспользовать механизм скрытого ресурса из [[S1009]] вместо отдельной реализации - единый флаг/фильтр, меньше поверхности.

---

## 10. Связи с другими спеками

- **Блокируется S1009** - механизм скрытого ресурса (флаг «не показывать в списке» + фильтр запроса) должен быть введён первым; без него S1010 не реализуем. **S1009 Verified (2026-07-31)** - блокер снят.
- S1012 - сосед по батчу companion-ресурсов (общий контекст ресурсов-получателей).
- S1354 - предсуществующий пробел SAF-поддержки в `CaptureDestinationPolicy` (camera/video/mic/screen-recording), найден при тактике S1010; не блокирует, отслеживается отдельно.

---

## 11. Критерии готовности (strategic-level)

1. В самом верху селектора получателя записи есть опция «Локальная папка».
2. Выбор опции открывает существующий браузер папок.
3. Принимаются только папки, доступные для записи.
4. Выбранная папка работает как получатель, но не отображается в общем списке ресурсов.
5. Строки EN/RU/UK на месте.

---

## Revision History

- **2026-08-02** - by `/spec-test-device` (`sdk_gphone64_x86_64`, device: emulator-5554)
  - Scenario: `temp/S1010/mobile_test_scenario_20260802_1600.md` · PASS/FAIL/SKIPPED 7/0/1 · Errors in log: 0
  - §11 criteria 1, 2, 4 PASS; criterion 3 PASS on the positive path only (non-writable rejection SKIPPED as
    out-of-scope - not constructible on this emulator); criterion 5 is static and already proven.
  - Both `Timber.d("S1010: …")` probes fired at D level, confirming `wrapOnSelected`'s sentinel branch and
    `onFolderPicked` were exercised on-device. 0 FATAL exceptions, 0 app-level errors.

---

## Owner decisions (2026-07-14)

- Локальная папка, выбранная из селектора ресурса-получателя записи (фото/видео/скриншоты/загрузки), становится СКРЫТОЙ/scoped-ссылкой, привязанной только к этой настройке, а НЕ видимой записью в общем списке ресурсов. Следствие: переиспользовать тот же механизм скрытого ресурса, что вводится в [[S1009]] (один флаг «не показывать в списке» + фильтр запроса), без отдельной реализации.

---

## Last Audit

**Date:** 2026-08-02
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 20 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

All five §11 criteria are satisfied. The option is prepended in all three insertion families
(`OperationsSettingsFragment`, `EdgeGestureConfigDialogFragment`, `DestinationPickerDialog`) and every one of the
seven target settings reaches a wired picker: `linkAutoDownloadResourceId` via the fragment's direct call, the four
capture ids via `OperationsCaptureManager`'s `pickDestination` (bound to `::showDestinationPicker`),
`screenshotDestinationResourceId` via `EdgeGestureConfigManager`'s `pickDestination`, and `videoSnapshotResourceId`
via the opt-in `localFolderPicker` argument. The out-of-scope `GeneralSettingsLogHelper` call site passes no
`localFolderPicker` and is provably unchanged. No flavor gating, no Room schema change, no `wear` impact; strings
present in EN/RU/UK; catalog, dev log and phase/INDEX statuses all consistent (5/5 phases, 10/10 steps).

Device run 2026-08-02 on emulator-5554 exercised the flow end to end: both debug probes fired at D level, the SAF
browser opened, the picked folder became the destination, and it did not appear in the general resource list
(0 `content://` entries). 0 FATAL exceptions, 0 app-level errors. Debug tags removed on this transition.

EXEMPT: `docs/FEATURES*.md` trilingual text - owned by `/skill-release` and never written per-spec (CLAUDE.md
section 11); the capability is instead recorded in `docs/ALL_FEATURES.jsonl` as
`settings.local-folder-as-write-destination`.

### Manual / on-device

- [x] "Local Folder" is the first row of the write-receiver picker - verified on-device 2026-08-02
- [x] Selecting it opens the system folder browser - verified on-device 2026-08-02
- [x] The picked folder becomes the destination - verified on-device 2026-08-02
- [x] The picked folder does not appear in the general resource list - verified on-device 2026-08-02
- [ ] A non-writable folder is rejected with `error_folder_not_writable` - the guard is present and unconditional in
      `LocalFolderDestinationPickerManager.onFolderPicked`, but the negative path was not exercised: SAF only offers
      trees the user can grant, so a grantable-but-non-writable folder needs external fixture setup. The underlying
      `CheckLocalFolderWritableUseCase` is reused unchanged from [[S1009]] (Verified).
- [ ] Capture-time writing to a SAF destination for the four `CaptureDestinationPolicy`-backed settings - tracked by
      [[S1354]], a pre-existing gap explicitly outside this ticket's scope (§6).
