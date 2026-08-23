# Спецификация: S1797 - SettingsRepositoryImpl detekt LargeClass debt

**Ticket:** S1797
**Status:** Archived
**Priority:** 40
**Date:** 2026-08-18
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - parked from S1796 (CLAUDE.md 3.1)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-18 при работе над S1796 (фронтальный фонарик).

**Симптом.** `SettingsRepositoryImpl` перешагнул порог detekt `LargeClass`: отчёт
`app_v2/build/reports/detekt/detekt.txt` даёт **609/600** на
`SettingsRepositoryImpl.kt:47:7`, сигнатура
`SettingsRepositoryImpl.kt$SettingsRepositoryImpl : SettingsRepository`.

**Evidence.**

- `assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles ..` -> exit 1, единственная находка
  `LargeClass` в этом файле.
- Файл - 1003 строки, тело класса начинается на строке 47.
- Записи в `config/detekt/baseline-app_v2.xml` для этого правила не было: долг всплывает у любого,
  кто коснётся файла, а не у того, кто его создал.
- S1796 добавил в класс 7 строк (два ключа настроек, чтение, запись) - без них метрика ~602,
  то есть порог уже был превышен до этой правки.

**Почему это отдельный тикет.** Разделение репозитория настроек на хранилища - самостоятельная
работа со своей разведкой: в `data/repository/settings/` уже живёт семейство `*SettingsStore`
(`AudioSettingsStore`, `CaptureSettingsStore`, `LinkSettingsStore`, ..), и вопрос «какие поля
`AppSettings` переезжают в какое хранилище» решается не по ходу чужого тикета.

**Временная мера, принятая в S1796.** Находка заморожена в `config/detekt/baseline-app_v2.xml`
по конвенции репозитория - так же заморожены `LargeClass` у `StreamsActivity`,
`BrowseLoadingManager` и `VrTextureDecoder`, каждый со своим `*-detekt-debt` тикетом
(S1198, S1311, S1247). Этот тикет снимает заморозку, а не обходит её.

---

## 1. Что нужно сделать

1. Разложить чтение и запись плоских полей `AppSettings` по хранилищам `data/repository/settings/`
   тем же способом, что уже применён к аудио, съёмке и ссылкам.
2. Снять запись `LargeClass:SettingsRepositoryImpl.kt$SettingsRepositoryImpl : SettingsRepository`
   из `config/detekt/baseline-app_v2.xml` и убедиться, что гейт зелёный без неё.

## 2. Критерии готовности

1. `assert-detekt.ps1 -Module app_v2 -Gate` не сообщает `LargeClass` по этому файлу.
2. Записи правила для этого класса в baseline нет.
3. Поведение настроек не изменилось: экспорт/импорт и профили устройств читают те же ключи.

---

## 3. Implementation State

**Что сделано.** Выделено самое крупное связное семейство плоских полей - launcher-настройки
рабочего стола (21 ключ): плотность сетки, состав и размещение таскбара, видимость индикаторов
трея, обои, блокировка рабочего стола, сортировка списка «Все приложения» и таймаут гашения экрана.

- Новый `data/repository/settings/LauncherSettingsStore.kt` - тот же контракт, что у соседей по
  пакету: `object`, приватные ключи, `data class Values`, `read(preferences)`, `write(preferences, settings)`.
- `SettingsRepositoryImpl.kt`: 988 -> 930 строк. Объявления ключей заменены маркер-комментарием,
  блок чтения - присваиваниями из `launcher.*`, блок записи - одним вызовом `LauncherSettingsStore.write`.
- Импорт `InstalledAppSortOrder` стал мёртвым и удалён (Rule 20) - он использовался только в
  вынесенном блоке.
- Из `config/detekt/baseline-app_v2.xml` удалена ровно одна запись
  `LargeClass:SettingsRepositoryImpl.kt$SettingsRepositoryImpl : SettingsRepository` (12246 -> 12245).

**Побочная правка в `SettingsPrefExtensions.kt`.** Вынесенный `read` из 21 поля упирался в
`CyclomaticComplexMethod` (20 при пороге 20): каждый инлайновый `?: default` - отдельная ветвь.
Добавлен `getOrDefault(key, default)` - чтение-контрпара к уже существовавшему `setOrRemove`.
Ветвление схлопывается, порог больше не задевается. Заводить новую запись в baseline было бы
ровно тем, что этот тикет убирает, поэтому правка структурная, а не заморозка.

**Правка гейта `class-architecture-naming` (Rule 13).** Гейт считал нарушением вложенный
`data class Values` - тот самый держатель результата, который по контракту есть у каждого
`*SettingsStore`. Двенадцать таких классов уже лежали поглощёнными в baseline, поэтому очередное
хранилище, написанное строго по конвенции, валило дельту. В список разрешённых суффиксов добавлен
`Values` - той же формы исключение, что и соседнее для `*Test`. Правило при этом продолжает
срабатывать: в `scripts/quality/source-matchers.tests/Run-Tests.ps1` добавлены два случая -
вложенный `Values` даёт 0, а честно неправильно названный класс в `data/repository` по-прежнему
даёт 1 (7 -> 9 случаев, все зелёные).

**Поведение сохранено намеренно, включая дефект.** `all_apps_sort_descending` пишется, но не
читается - точки чтения не было и до выноса. Добавление чтения меняло бы поведение, поэтому оно
осталось за рамками тикета и заведено отдельно как **S1798**; в `write` стоит комментарий с этой
ссылкой, чтобы асимметрия не читалась как недосмотр.

**Доказательства.**

- `.\a.ps1 fk` -> `BUILD SUCCESSFUL`, exit 0.
- `assert-detekt.ps1 -Module app_v2 -Gate -ChangedFiles <3 файла>` ->
  `PASS [scoped] - 32 file(s) with new findings project-wide, none among changed files`, exit 0.
  До правки тот же вызов давал `LargeClass` по `SettingsRepositoryImpl` - критерий 1 закрыт,
  критерий 2 закрыт удалением записи.
- Критерий 3 проверен механически: множество строковых ключей до и после выноса совпадает
  побайтно (144 ключа в обоих случаях, `diff` пуст), то есть экспорт/импорт и профили устройств
  читают те же ключи. Дефолты вынесенных полей сверены попарно - расхождений нет.
- Целевые unit-тесты: `SettingsRepositoryImplTest`, `SettingsRepositoryMirrorTest`,
  `ScreenshotSettingsStoreTest`, `BackupMapperTest` - 30 тестов, 0 failures, 0 errors.
  `SettingsRepositoryMirrorTest` гоняет настоящий DataStore через `SettingsRepositoryImpl`,
  то есть round-trip чтения и записи проверен, а не только компиляция.

**Замечание по проверке.** Прогон с `--tests` пишет результаты не в
`app_v2/build/test-results/testStandardDebugUnitTest/`, а в соседнюю папку с суффиксом
`-filtered`. В основной папке XML остаются от прошлого полного прогона, и по ним фильтрованный
запуск выглядит как «не запускался». Читать надо `-filtered`.

---

## Last Audit

**Date:** 2026-08-18
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Проверено: файл и объявление `object LauncherSettingsStore`; отсутствие записи
`LargeClass:SettingsRepositoryImpl` в baseline; ноль отладочных тегов `S1797` при статусе
`Implemented`; запись в `dev/CHANGELOG.md`; класс в `dev/CATALOG/app_v2.jsonl`; удалённый мёртвый
импорт `InstalledAppSortOrder`; отсутствие несённых открытых вопросов.

Критерии 1 и 3 закрыты прогонами этой же сессии, а не повторно здесь (аудит статический):
`assert-detekt.ps1 -Gate -ChangedFiles` -> exit 0, `none among changed files`; совпадение множества
ключей до и после (144/144) и 30 целевых unit-тестов без падений.

EXEMPT - раздела §8 FEATURES в спецификации нет: вынос хранилища не меняет ничего видимого
пользователю, поэтому записи в `docs/ALL_FEATURES.jsonl` не заводится.

### Manual / on-device

- [ ] Отдельной проверки на устройстве не требуется: поведение доказано совпадением ключей,
      дефолтов и round-trip тестом через настоящий DataStore.
