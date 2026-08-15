# Стратегическая спецификация: S1058 - Фиктивный MIME в "Открыть с помощью" для бинарных файлов

**Ticket:** S1058
**Status:** Archived
**Priority:** 30
**Date:** 2026-07-15
**Tier:** 4 - Minor (ad-hoc)
**Roadmap entry:** Ad-hoc - parked by S1056 research 2026-07-15

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-15 (spec-draft из ресёрча S1056)

**Симптом:** "Открыть с помощью" для бинарных файлов (BINARY_DISK/BINARY_ARCHIVE - .iso/.dmg/.vhd/.7z/..) строит несуществующий MIME `application/$extension` (напр. `application/iso`), поэтому системный chooser у большинства устройств не находит обработчик ("нет приложения").

**Доказательства:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseBinaryFileHandler.kt:157` - `"application/$extension"`.
- `openWithDefaultApp()` -> `OpenInShareTargetHandler` chooser с этим MIME.

**Действие:** маппить известные бинарные расширения на зарегистрированные MIME (напр. `.iso` -> `application/x-iso9660-image`, `.7z` -> `application/x-7z-compressed`, ..) с фолбэком `application/octet-stream` для неизвестных, вместо синтетического `application/<ext>`.

---

## 0a. Уточнение симптома (ресёрч 2026-07-16)

Формулировка §0 "chooser не находит обработчик" верна лишь наполовину - уточняю, чтобы приёмка не проверяла не тот факт.

- Синтетический MIME не «невидим» для системы: intent-фильтр с `<data android:mimeType="*/*"/>` **сматчится** и на `application/iso`. Пустой chooser бывает, но не гарантирован.
- Реальный проигрыш точечный: приложение, объявившее конкретный тип (`application/x-7z-compressed`), на `application/7z` **не сматчится никогда**. То есть теряются именно профильные обработчики (архиваторы), а остаются только `*/*`-универсалы.
- Второй выигрыш от фолбэка: `application/octet-stream` объявляют многие файловые менеджеры, а `application/vhd` - никто. Даже деградация до octet-stream строго расширяет множество кандидатов.

Итого чинится не «пусто/не пусто», а **корректность типа**: перестаём выдумывать media type, которого нет ни в одном реестре.

**Масштаб (замер по `BinaryFileTypeDetector`):** из 15 расширений `ARCHIVES` синтетический MIME даёт 14 - реальный только `zip` -> `application/zip`. Из 8 расширений `DISK_IMAGES` синтетические все 8. `BINARY_EXECUTABLE` и `BINARY_OTHER` уже отдают `application/octet-stream` - не затронуты, не чиню.

---

## 1. Проблема и мотивация

- `application/<ext>` - фабрикация: такого media type нет ни в IANA, ни в freedesktop shared-mime-info, ни в Debian `mime.types`.
- Профильные обработчики (архиваторы) объявляют конкретные типы и на фабрикацию не матчатся - пользователь видит либо пустой chooser, либо только `*/*`-универсалы.
- Ошибка тиражируется: список расширений в `BinaryFileTypeDetector` растёт, а MIME для каждого нового выводится по шаблону - то есть каждый новый архив приезжает уже сломанным.

## 2. Решение

Заменить фабрикацию на цепочку из трёх ступеней, ни одна из которых не выдумывает тип:

1. **Таблица реестровых типов** в `BinaryFileTypeDetector` (там же, где живут наборы расширений). Только те записи, для которых тип определён реестром - IANA, freedesktop shared-mime-info или Debian `mime.types`. Возвращает `null` на промах.
2. **Системная карта** - существующий `core/util/MimeTypeResolver.resolve(ext)`: `MimeTypeMap` -> `application/octet-stream`.
3. Фолбэк `application/octet-stream` уже внутри `MimeTypeResolver` - отдельной ветки не требуется.

### 2.1 Почему таблица, а не только `MimeTypeResolver`

Ветка «одна строка `MimeTypeResolver.resolve(ext)`» рассматривалась и отклонена по трём причинам:

- **Недетерминизм по API.** `MimeTypeMap` до API 29 - маленькая захардкоженная таблица `libcore.net.MimeUtils`, с API 29 - карта, собираемая из `mime.types`-файлов. Флейвор `legacy` держит minSdk 23, то есть одно и то же расширение дало бы разный MIME на разных устройствах.
- **Непроверяемость без устройства.** Что именно вернёт `MimeTypeMap` на конкретной прошивке - статически не установить. Robolectric 4.11.1 в проекте есть, но его `ShadowMimeTypeMap` по умолчанию **пуст** и наполняется тестом вручную - оракулом для «что знает реальное устройство» он быть не может. С таблицей приёмка проверяется юнит-тестом здесь и сейчас.
- **Ступени не конфликтуют.** Таблица идёт первой не «вместо» системной карты, а чтобы зафиксировать детерминизм там, где ответ известен; всё остальное по-прежнему уходит в системную карту.

### 2.2 Что в таблицу НЕ попадает

Расширения, для которых реестрового типа нет, в таблицу не вносятся: `tgz`, `tbz2`, `txz`, `arj`, `lzh`, `ace`, `zipx`, `img`, `vhd`, `vdi`, `qcow2`, `vmdk`, `toast`. Придумать им `application/x-<ext>` - значит воспроизвести ровно тот баг, который чинит тикет, только с приличным префиксом. Они проваливаются на ступень 2.

Отдельно `tar.gz`: лежит в наборе `ARCHIVES`, но недостижим - вызывающий код везде передаёт результат `substringAfterLast('.')`, то есть максимум `gz`. Из набора не убираю (не моя область), в таблицу не вношу.

### 2.3 Источники значений таблицы

- `zip` -> `application/zip` - IANA.
- `rar` -> `application/vnd.rar` - IANA (зарегистрирован 2016-07-14; `application/x-rar-compressed` - устаревший алиас).
- `gz` -> `application/gzip` - IANA.
- `7z` -> `application/x-7z-compressed`, `tar` -> `application/x-tar`, `bz2` -> `application/x-bzip2` - freedesktop / MDN Common types.
- `xz` -> `application/x-xz`, `cab` -> `application/vnd.ms-cab-compressed` - freedesktop shared-mime-info.
- `iso` -> `application/x-iso9660-image` - Debian `mime.types` (значение из §0).
- `dmg` -> `application/x-apple-diskimage` - де-факто Apple.

## 3. Область работ

### 3.1 Фаза A - таблица в детекторе и инвариант, не дающий ей рассохнуться

- Добавить в `BinaryFileTypeDetector` приватную карту `MIME_BY_EXTENSION` (§2.3) и `fun mimeTypeForExtension(extension: String): String?`.
- Держать объект **чистым** - без `android.*` импортов, иначе юнит-тесты потребуют Robolectric. Композиция со ступенью 2 - в вызывающем коде.
- Добавить набор `NO_REGISTRY_MIME` (§2.2) - явный список «решено не вносить», а не молчаливый пропуск.
- Юнит-тест на равенство множеств: `ARCHIVES + DISK_IMAGES == MIME_BY_EXTENSION.keys + NO_REGISTRY_MIME`.
- Это и есть профилактика: новое расширение в `ARCHIVES` без решения по MIME роняет тест, а не уезжает в релиз с фабрикацией.
- Для доступа теста наборы `ARCHIVES`/`DISK_IMAGES` перевести `private` -> `internal` (прецедент: `internal fun resolveContentSlot` в `MainStreamsPanelManager`).

### 3.2 Фаза B - вызывающий код

- В `BrowseBinaryFileHandler.getMimeTypeForFile` заменить `"application/$extension"` в ветках `BINARY_ARCHIVE` и `BINARY_DISK` на `BinaryFileTypeDetector.mimeTypeForExtension(extension) ?: MimeTypeResolver.resolve(extension)`.
- Ветки `BINARY_EXECUTABLE`, `OFFICE_DOCUMENT`, `else` **не трогать**: фабрикации в них нет, они уже отдают `application/octet-stream` или реестровый тип.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1056 (родитель, запарковал находку)

## 4. Non-goals

- Не трогать `BINARY_EXECUTABLE`/`OFFICE_DOCUMENT`/`else` - там фабрикации нет.
- Не выносить `getMimeTypeForFile` из UI-слоя в `MediaTypeUtils` - архитектурно верно, но это отдельный рефакторинг, не этот тикет.
- Не чинить «пустой chooser вместо тоста» - `Intent.createChooser` почти никогда не бросает `ActivityNotFoundException`, поэтому `R.string.no_app_to_open` мёртв. Отдельная находка, паркуется отдельным тикетом.
- Не удалять мёртвый `tar.gz` из `ARCHIVES`.

## 5. Критерии приёмки

- `getMimeTypeForFile` не возвращает `application/<ext>` ни для одного расширения из `ARCHIVES`/`DISK_IMAGES`.
- Для каждой записи §2.3 юнит-тест проверяет точную строку.
- Инвариант §3.1 (равенство множеств) зелёный.
- `.\a.ps1 fk` зелёный.
- Юнит-тесты `BinaryFileTypeDetectorTest` зелёные.

## Last Audit

**Дата:** 2026-07-16 | **Вердикт:** Verified

**Что сделано:**
- `util/BinaryFileTypeDetector.kt` - таблица `MIME_BY_EXTENSION` (10 записей, §2.3), явный `NO_REGISTRY_MIME` (14 записей, §2.2), `mimeTypeForExtension()`. Наборы `ARCHIVES`/`DISK_IMAGES` `private` -> `internal` под инвариант.
- `ui/browse/managers/BrowseBinaryFileHandler.kt` - ветки `BINARY_ARCHIVE`/`BINARY_DISK` слиты и переведены на `mimeTypeForExtension(ext) ?: MimeTypeResolver.resolve(ext)`. Остальные ветки не тронуты.
- `app_v2/src/test/.../BinaryFileTypeDetectorTest.kt` - новый, 6 кейсов.

**Проверки:**
- `.\a.ps1 fk` - expected: BUILD SUCCESSFUL | actual: BUILD SUCCESSFUL.
- `BinaryFileTypeDetectorTest` - expected: 0 failures | actual: `tests="6" failures="0" errors="0"`.
- Инвариант §3.1 проверен на способность краснеть: `temp/S1058/probe-invariant.ps1` вносит `zst` в `ARCHIVES` без решения по MIME - expected: suite RED | actual: `failures=1`, сообщение «extension classified as archive/disk but with no MIME decision .. was:<[zst]>»; файл восстановлен, `grep -c zst` -> 0, повторный прогон 6/6 зелёный.

**Правка в тестах по ходу:** кейс «запись не совпадает с `application/<ext>`» удалён - `zip` -> `application/zip` совпадает с шаблоном, будучи настоящим IANA-типом, то есть проверка не отличала «выдумано» от «шаблон случайно прав». Выдуманность решается реестром, что и делает тест точного пиннинга.

**Почему Verified без устройства:**
- Приёмка §5 целиком статическая и закрыта тестами; §0a заранее увела её от «пусто/не пусто», потому что населённость chooser'а - свойство установленных приложений, а не нашего кода.
- Регрессия по построению невозможна: фильтр `*/*` матчится на любой конкретный тип, поэтому универсалы, ловившие `application/iso`, ловят и `application/x-iso9660-image`; профильные обработчики могут только добавиться.
- Единственная теоретическая потеря - приложение, объявившее выдуманный тип буквально (`application/iso`). Считаю пренебрежимой: выдуманные типы не регистрирует никто.

**Побочный охват (осознанный):** `mime` из `buildBinaryContent` идёт не только в "Открыть с помощью", но и в `shareFile` -> `SendToMenuManager`. Корректный тип попадает и туда - тем же рассуждением про `*/*` это не хуже, а точнее.

**Residual / вне области:**
- `tgz`, `tbz2`, `txz`, `arj`, `lzh`, `ace`, `zipx`, `img`, `vhd`, `vdi`, `qcow2`, `vmdk`, `toast` идут на системную карту и, вероятнее всего, в `application/octet-stream`. Это осознанный выбор §2.2, а не пробел.
- Мёртвый тост `no_app_to_open` (§4) запаркован как **S1076**.

## 6. Риски

- **Неверное значение в таблице** - главный риск: `application/vnd.rar` против устаревшего `application/x-rar-compressed`. Смягчение: каждая запись §2.3 имеет ссылку на реестр; при промахе система всё равно не хуже сегодняшней фабрикации.
- **Профильный обработчик так и не найдётся** на конкретном устройстве - вне контроля приложения; тикет чинит корректность типа, а не наличие стороннего приложения.
- Изменение `private` -> `internal` расширяет видимость наборов в пределах модуля - приемлемо, прецедент есть.

<!-- auto-approved by /spec-all - 2026-07-16 -->
