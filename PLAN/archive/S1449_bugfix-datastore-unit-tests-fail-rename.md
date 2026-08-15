# Спецификация (compact bugfix): S1449 - Тесты на DataStore падают на переименовании временного файла

**Ticket:** S1449
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-06
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-06

**Захвачено во время:** S1436 (шаг 06.5, полный прогон `.\a.ps1 fu`)

**Текст:**

Four DataStore-backed unit test classes fail in the full suite and keep failing when run alone, so this is not a concurrency artifact of one run: `BrowseStateDataStoreTest` (5 of 6), `ReviewEligibilityDataStoreTest` (4), `GameStateRepositoryImplTest` (4), `SettingsRepositoryImplTest` (2). Every failure is the same exception:

```text
java.io.IOException: Unable to rename P:\ANDROID\FastMediaSorter_mob_v2\temp\gradle-tmp\junit<random>\browse.preferences_pb.tmp.
This likely means that there are multiple instances of DataStore for this file. Ensure that you are only creating a single instance of datastore for this file.
	at androidx.datastore.core.SingleProcessDataStore.writeData$datastore_core(SingleProcessDataStore.kt:433)
```

The four test files are months old (2026-05-29, 2026-05-31, 2026-07-18) and none of them was touched by the ticket that found this. The junit temp root is a fresh directory per run, so a stale leftover is not the cause; the rename failing on Windows while a second DataStore instance holds the file is. Whether the fault is the test (one DataStore instance per file per test) or the environment (`temp/gradle-tmp` as `java.io.tmpdir` while a sibling agent session runs its own gradle) is exactly what needs investigating - it decides whether the fix is in the tests or in the runner script.

Why this matters beyond the tests themselves: `.\a.ps1 fu` is the closing predicate of many tactical plans, and a suite that is red for reasons unrelated to the change teaches every ticket to look past a red suite. That is the expensive part.

---

## 1. Проблема / симптом

15 отказов в четырёх классах, все с одним исключением `java.io.IOException: Unable to rename .. .preferences_pb.tmp` из `SingleProcessDataStore.writeData`. Распределение: `BrowseStateDataStoreTest` 5, `GameStateRepositoryImplTest` 4, `ReviewEligibilityDataStoreTest` 4, `SettingsRepositoryImplTest` 2.

Воспроизводится детерминированно и не зависит от флейвора: те же 15 отказов с точно тем же распределением наблюдались 2026-08-07 на `standard` (15 из 15 всех отказов прогона) и на `lite`. Оба прогона шли под `BUILD.LOCK`, то есть строго последовательно и без параллельной gradle-сессии.

Это снимает гипотезу из §0 о влиянии соседней агентской сессии: конкуренции не было, а отказ остался.

---

## 2. Корневая причина

Тесты сами создают файл, который DataStore обязан создать сам, а Windows не даёт переименовать поверх существующего файла.

Механизм по шагам:

- `produceFile` в этих тестах возвращал `tempFolder.newFile("<имя>.preferences_pb")`, а `TemporaryFolder.newFile` не просто называет файл - он его **создаёт**, пустым.
- `SingleProcessDataStore.writeData` пишет во временный `<file>.tmp`, затем делает `renameTo(file)`.
- `java.io.File.renameTo` на Windows возвращает `false`, если файл назначения уже существует - в отличие от POSIX, где rename перезаписывает цель атомарно.
- DataStore на `false` бросает исключение с текстом «multiple instances of DataStore for this file». Формулировка вводит в заблуждение: она описывает самую частую причину на Linux, а не то, что произошло здесь. Именно она увела захват §0 в сторону конкуренции.

Решающая проверка предсказанием: если причина в переименовании, падать должны **только** тесты, которые пишут, а читающие - проходить. Так и есть. В `BrowseStateDataStoreTest` из шести кейсов проходит ровно один - `filter is null when nothing persisted`, единственный, который ничего не пишет; остальные пять пишут и падают. Чтение никогда не доходит до `renameTo`.

Отсюда же ответ на вопрос §0 «тест или окружение»: тест. `temp/gradle-tmp` и соседние сессии ни при чём.

---

## 3. Исправление

`produceFile` должен **называть** файл, а не создавать его.

1. Во всех пяти местах заменить `tempFolder.newFile("<имя>")` на `tempFolder.root.resolve("<имя>")` - возвращает тот же путь внутри временной папки, но файла не создаёт. Расширение `resolve` из stdlib, новых импортов не нужно.
2. Затронуты четыре файла: `BrowseStateDataStoreTest`, `ReviewEligibilityDataStoreTest`, `GameStateRepositoryImplTest`, `SettingsRepositoryImplTest` (в последнем два места).
3. В каждом файле оставить комментарий о том, **почему** файл нельзя создавать заранее, и что сообщение DataStore про «multiple instances» здесь врёт - иначе следующий автор теста повторит `newFile`, потому что он выглядит естественнее.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1244 - прежний случай, когда полный прогон `fu` обрывался и результат приходилось читать поклассово; S1436 - тикет, в шаге 06.5 которого находка обнаружена; S1450 - его прогоны на `standard` и `lite` дали доказательство детерминированности и сняли гипотезу о соседней сессии.

---

## 3.1 Поправка к §2 и §3 по результатам прогона 2026-08-07 (`/spec-do`, раунд 5)

Правка из §3 применена полностью - все пять мест используют `tempFolder.root.resolve(..)`, комментарии на месте. Она работает, но закрывает только половину причины.

**Замер после правки** (`check-standard-fast.ps1 -Mode Unit -Flavor Standard`, фильтр по четырём классам, отчёты свежие):

- `SettingsRepositoryImplTest` - 6 из 6, **было 2 отказа, стало 0**.
- `BrowseStateDataStoreTest` - 4 отказа из 6.
- `ReviewEligibilityDataStoreTest` - 3 отказа из 5.
- `GameStateRepositoryImplTest` - 2 отказа из 5.

Итого 15 -> 9. Исключение то же самое, дословно `Unable to rename .. .preferences_pb.tmp`, и каждый отказ - в своей свежей папке `junit<random>`, то есть предсозданного файла действительно больше нет.

**Настоящая причина, полная формулировка.** `File.renameTo` на Windows не может заменить существующий файл - никогда, а не только когда его создал тест. `SingleProcessDataStore.writeData` пишет в `<file>.tmp` и делает `renameTo(file)` при **каждой** записи. Пока файла нет, первая запись проходит; как только он появился - своей же первой записью - любая следующая запись падает.

Отсюда наблюдаемое распределение, и оно предсказывается точно: падают ровно те кейсы, которые пишут **больше одного раза**. `SettingsRepositoryImplTest` позеленел целиком потому, что каждый его кейс пишет один раз. Прежний критерий из §2 - «падают те, кто пишет» - был верен лишь потому, что предсозданный файл делал первую же запись второй по счёту.

**Версия библиотеки - вот где живёт дефект.** `app_v2/build.gradle.kts:1359` закрепляет `androidx.datastore:datastore-preferences:1.0.0`. Именно эта ветка использует `File.renameTo`. В 1.1.x запись переведена на okio-хранилище с атомарным перемещением, которое на Windows заменяет существующий файл штатно.

**Три пути, и выбор между ними - не тестовый вопрос.**

1. **Поднять `datastore-preferences` до 1.1.x.** Лечит класс дефекта в корне, а не в тестах. Цена: это продакшен-зависимость, хранящая пользовательские настройки, поэтому нужен прогон сборки и подтверждение, что формат `preferences_pb` на диске читается прежним - иначе у установленных пользователей теряются настройки. Формат между 1.0 и 1.1 не менялся, но утверждать это без проверки нельзя.
2. **Оставить 1.0.0 и переписать тесты на одну запись за кейс.** Дёшево и без риска для продакшена, но ослабляет ровно те проверки, ради которых тесты написаны: «накапливает», «очищает ранее сохранённое», «сбрасывает» - это по определению две записи.
3. **Оставить 1.0.0 и подсунуть тестам своё хранилище с атомарным перемещением.** Продакшен не трогается, тесты остаются честными. Цена: в 1.0.0 `PreferenceDataStoreFactory.create(produceFile)` не даёт точки подмены, так что это заметная работа, а не правка на строку.

Вариант 1 выглядит правильным и самым дешёвым, но требует решения по продакшен-зависимости и проверки совместимости данных - за пределами того, что этот тикет заявлял.

### Quiz decisions (2026-08-07)

- Какой из трёх путей брать → **вариант 1: поднять `androidx.datastore:datastore-preferences` до 1.1.7** (лечит класс дефекта в корне, а не в тестах; 1.1.7 - последняя стабильная в ветке 1.1.x по `maven-metadata.xml` Google Maven).
- Охват бампа → **оба модуля, `app_v2` и `wear`** (`wear/build.gradle.kts:181` пинит тот же 1.0.0; разные версии одной библиотеки в двух модулях одного репозитория - будущая ловушка).
- Достаточное доказательство совместимости данных → **апгрейд поверх старой установки на устройстве**: поставить текущую релизную сборку, задать настройки, поставить сборку с 1.1.7 поверх и убедиться, что настройки на месте. Прогон сборки и `fu` обязательны, но сами по себе недостаточны - они не читают старый `preferences_pb` новым кодом.

### 3.2 План после решения

1. `app_v2/build.gradle.kts:1381` и `wear/build.gradle.kts:181`: `androidx.datastore:datastore-preferences:1.0.0` -> `1.1.7`.
2. Собрать `standard` debug и прогнать полный набор: девять оставшихся отказов `Unable to rename` должны исчезнуть без единой правки в тестах.
3. Доказательство совместимости данных на устройстве, как решено выше. Пока оно не получено, тикет не уходит выше `Implemented`.

Транзитивные риски проверены заранее и не блокируют: `okio` 3.x, на который переходит запись в 1.1.x, уже в графе через `okhttp` 4.12.0, а `kotlinx-coroutines` 1.7.3 удовлетворяет требованию 1.1.x. Обе версии `datastore` требуют minSdk ниже нашего минимума (23 на `legacy`).

---

## 3.4 Поправка к §3.1 и §3.2 по результатам прогона 2026-08-07 12:10-12:35 (`/spec-do`, раунд 1)

Бамп сделан ровно как решено в §3.2 - `app_v2/build.gradle.kts` и `wear/build.gradle.kts` на `1.1.7`, плюс пины в `docs/TECH_STACK.md`, `dev/TECH_REQUIREMENTS.md` и два сниппета в `docs/WEAR_OS_*.md`. Сборка `standard debug` зелёная (`BUILD SUCCESSFUL in 1m 53s`), транзитивные риски подтверждены фактом: POM `1.1.7` требует `kotlinx-coroutines-core:1.7.3` - ровно наш пин.

**Посылка §3.1 оказалась неверной, и это главный результат раунда.** После бампа отказов осталось столько же - девять, дословно то же `Unable to rename .. .preferences_pb.tmp`. Изменился только стек: `androidx.datastore.core.FileStorageConnection.writeScope(FileStorage.kt:121)` вместо `SingleProcessDataStore.writeData`, то есть 1.1.7 действительно подключился и всё равно падает.

Почему §3.1 ошиблась: в 1.1.x okio-хранилище появилось, но оно **не** стало путём по умолчанию. `PreferenceDataStoreFactory.create(produceFile)` по-прежнему строит `FileStorage`, а та пишет через `File.renameTo`. Ловушка глубже: `createWithPath(produceFile: () -> okio.Path)` выглядит как okio-вход, но в android-артефакте это обёртка - байткод показывает `createWithPath$1`, который зовёт `path.toFile()` и уходит в ту же `FileStorage`. Правка на `createWithPath` дала те же девять отказов.

**Настоящее лечение - шов `Storage`, который 1.1.x открыл, а 1.0.0 не имел.** В тестах хранилище собирается руками:

- `PreferenceDataStoreFactory.create(storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) { path }, scope = scope)`.
- `androidx.datastore.preferences.core.PreferencesSerializer` в 1.1.7 публичный и реализует `OkioSerializer<Preferences>`, `OkioStorage` приходит транзитивно как `api`-зависимость - новых строк в `build.gradle.kts` не нужно.
- Почему это работает, проверено по байткоду, а не по документации: `okio.NioSystemFileSystem.atomicMove` вызывает `Files.move(src, dst, ATOMIC_MOVE, REPLACE_EXISTING)`, а такой move на Windows заменяет существующий файл - в отличие от `File.renameTo`.

Отсюда переоценка вариантов из §3.1: вариант 1 в одиночку не лечит вовсе, а вариант 3 стоит не «заметной работы», а трёх строк на класс - но только поверх бампа. То есть бамп остаётся нужен, просто он не лекарство, а включатель шва.

**Замер после полной правки** (`check-standard-fast.ps1 -Mode Unit -Flavor Standard`, фильтр по четырём классам, отчёты от 12:33:40):

- `BrowseStateDataStoreTest` - 6 из 6, отказов 0 (было 4).
- `ReviewEligibilityDataStoreTest` - 5 из 5, отказов 0 (было 3).
- `GameStateRepositoryImplTest` - 5 из 5, отказов 0 (было 2).
- Итого 9 -> 0. Ни одного `Unable to rename` в прогоне.

**Что осталось недоказанным и почему это не про этот тикет.** `SettingsRepositoryImplTest` выполняет один тест, после чего воркер умирает с `Process 'Gradle Test Executor N' finished with non-zero exit value 10`. Это не следствие правки: в прогоне 12:29, ещё до перехода на okio, класс вёл себя точно так же - 17 выполненных тестов складывались как 16 из трёх DataStore-классов плюс один отсюда. Причина - `AppSettings`, чей синтетический конструктор со всеми умолчаниями занимает 256 аргументных слотов при потолке 255 (замер `javap -s`), из-за чего class-файл невалиден и приложение вообще не стартует. Заведено как S1470.

Туда же упирается доказательство совместимости данных из §3.2: апгрейд поверх старой установки провести нельзя, приложение падает на `Hilt`-инъекции до чтения настроек. Снимок «до» снят и сохранён (`temp/S1449/settings_before.pb`, 6759 байт с реальными значениями), так что доказательство займёт минуты, как только S1470 закрыт.

**Бамп не должен уезжать в релиз раньше этого доказательства** - решение владельца из Quiz-блока прямо требует апгрейда поверх старой установки, и прогон сборки его не заменяет.

---

## 4. Проверка

- В прогоне `check-standard-fast.ps1 -Mode Unit -Flavor Standard` не остаётся ни одного отказа с `Unable to rename` / `SingleProcessDataStore`: было 15, ожидается 0. **Выполнено** 2026-08-07: 0 отказов.
- Четыре затронутых класса зелёные целиком, а не только читающие кейсы. **Выполнено для трёх** (`BrowseStateDataStoreTest` 6/6, `ReviewEligibilityDataStoreTest` 5/5, `GameStateRepositoryImplTest` 5/5); `SettingsRepositoryImplTest` не может отработать до закрытия S1470.
- Гейт полноты набора остаётся `PASS` - правка не должна уменьшить число исполняемых классов. **Не проверено**: полный набор сейчас обрывается по S1463 и S1470.
- Совместимость данных: апгрейд поверх старой установки сохраняет настройки. **Выполнено** 2026-08-07 после закрытия S1470.

---

## Last Audit

**2026-08-07, прогон `/spec-do`.** Блокировка S1470 снята в том же прогоне, поэтому оба остававшихся предиката закрыты.

- `SettingsRepositoryImplTest` - 6 из 6, ноль отказов. Раньше класс выполнял один тест и убивал воркер; причина была не в DataStore, а в `AppSettings` (S1470).
- Четыре целевых класса зелёные целиком: 6/6, 5/5, 5/5, 6/6 - итого 22 теста, ни одного `Unable to rename`. Было 15 отказов на старте тикета и 9 после первой правки.
- Совместимость данных доказана так, как требовал владелец, - апгрейдом поверх установки от 2026-08-06, а не прогоном сборки. Настройки на месте (`language=en`, `color_theme=AUTO`, `translation_target_language=ru`), `stats_baseline_first_install_version` подтверждает тождество стора, а файл вырос 6759 -> 6811 байт: 1.1.7 не только прочитал файл, созданный 1.0.0, но и записал в него.
- Гейты: `.\a.ps1 fg` - 15 из 15 зелёных.

Главный вывод тикета не в версии, а в шве: бамп до 1.1.7 сам по себе не лечит ничего, он открывает `Storage`, а лечит уже подмена хранилища на okio. `createWithPath` - ложный след, на Android это обёртка обратно в `File`.
