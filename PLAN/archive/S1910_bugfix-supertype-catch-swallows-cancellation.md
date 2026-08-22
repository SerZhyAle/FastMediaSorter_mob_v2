# Спецификация (compact bugfix): S1910 - Неохраняемый catch по надтипу CancellationException глотает отмену

**Ticket:** S1910
**Status:** Archived
**Priority:** 70
**Date:** 2026-08-21
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-21

**Захвачено во время:** S1889

**Текст:**

Найдено при разборе корневой причины S1889.

`kotlinx.coroutines.CancellationException` - это typealias на `java.util.concurrent.CancellationException`, который наследуется от `IllegalStateException`. Поэтому `catch (e: IllegalStateException)` и `catch (e: RuntimeException)` в корутинном коде ловят штатную отмену наравне с настоящими сбоями: пишут её в лог как ошибку и возвращают доменный «отказ» или пустой результат вместо того, чтобы пропустить отмену наверх.

S1889 расширил ратчет-гейт `swallowed-cancellation`, чтобы правило вообще видело эту форму, и закрыл всё, что попало в его периметр. Гейт отказывается поднимать baseline по конструкции, поэтому долг не был в него записан - он был вычищен: 2 блока в `CloudMediaScanner` плюс 5 в остальном `app_v2/src/main`. `actual 467 | delta 0`.

**Остаток - это ровно то, куда правило не смотрит.** `Roots` гейта - `app_v2/src/main`, поэтому модуль часов не судится вовсе и baseline его не удерживает. Там форма живёт дальше, найдено сканом на 2026-08-21:

```
wear/src/main/java/com/sza/fastmediasorter/wear/
    data/network/ftp/FtpConnectionTest.kt:42        catch (e: IllegalStateException)
    data/network/ftp/FtpDataSource.kt:69            catch (e: IllegalStateException)
    data/network/sftp/SftpDataSource.kt:76          catch (e: IllegalStateException)
    ui/streams/WearFaviconAtlasSlicer.kt:48         catch (e: IllegalStateException)
    FastMediaSorterWearApp.kt:40                    catch (e: IllegalStateException)
```

Наблюдение, а не решение: три сетевых источника выглядят как настоящие кандидаты - отмена туда достижима и превращается в доменный отказ соединения. `WearFaviconAtlasSlicer` и `FastMediaSorterWearApp` могут оказаться безопасными, каждый требует своего суждения.

Главный вопрос тикета, который надо решить до правок: **расширять ли `Roots` гейта на `wear/src`.** Без этого пять правок не удерживаются ничем и класс отрастёт заново - ровно так он и дожил до S1889 в `app_v2`. С этим - надо сперва измерить, сколько широких `catch` в `wear/src` правило найдёт заодно, и засеять для модуля отдельный baseline.

Скан-скрипт, которым получен список: `temp/S1889/scan-ise.ps1`.

---

## 1. Проблема / симптом

`kotlinx.coroutines.CancellationException` - typealias на `java.util.concurrent.CancellationException`, наследника `IllegalStateException`. Поэтому `catch (e: IllegalStateException)` и `catch (e: RuntimeException)` в корутинном коде ловят штатную отмену наравне со сбоями: пишут её в лог как ошибку и возвращают доменный отказ вместо того, чтобы пропустить отмену наверх. S1889 вычистил `app_v2/src/main`, но `Roots` гейта - только этот каталог, поэтому модуль часов не судился вовсе.

---

## 2. Корневая причина

Периметр правила, а не сам код: `swallowed-cancellation` объявлен с `Roots = @('app_v2/src/main')`, и `wear/src` не читался никогда. Класс жил в модуле часов беспрепятственно.

**Измерение (обязательный первый шаг тикета), 2026-08-21.** Прогон собственной функции правила `Find-SwallowedCancellationLines` по `wear/src` дал **34 попадания в 17 файлах** - существенно больше пяти из захвата. Пять из захвата были получены грепом по `catch (e: IllegalStateException)`, а правило считает ещё и широкие арматуры `catch (e: Exception)`/`Throwable`. То есть без измерения решение принималось бы по заниженной в семь раз цифре.

---

## 3. Исправление

### 3.1 Решение по главному вопросу: расширять ли `Roots`

Не расширять существующее правило, а **зарегистрировать второе, для модуля часов, с отдельным baseline**. Один общий счётчик на два корня позволил бы регрессии в одном модуле спрятаться за уборкой в другом и всё равно читаться как «не выше baseline» - ровно то, что ратчет обязан ловить. Добавлено правило `swallowed-cancellation-wear` (`Roots = @('wear/src')`, `swallowed-cancellation-wear-baseline.txt`).

### 3.2 Пять сайтов из захвата - все настоящие

Каждый разобран отдельно, как требовал захват; безопасных среди них не оказалось:

- `FtpConnectionTest:42`, `FtpDataSource:69`, `SftpDataSource:76` - отмена достижима и превращается в доменный отказ соединения (`Result.failure` / `failStream`).
- `WearFaviconAtlasSlicer:48` - внутри `withContext`; отменённая нарезка молча возвращала бы `null`, то есть «плитки нет».
- `FastMediaSorterWearApp:40` - внутри `launch` вокруг suspend-чтения настроек; отмена логировалась бы как сбой применения локали.

Во все пять добавлена первая арматура `catch (e: CancellationException) { throw e }` и импорт в отсортированное место блока.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** модуль `wear`, у которого нет вариантов сборки, плюс два файла инструментария в `scripts/quality`.
- **API level constraints:** не применимо.
- **Localization:** не применимо - пользовательских строк нет.
- **UI placement contract:** не применимо.
- **Accessibility:** не применимо.
- **Validation level:** измерение правила до и после, `a.ps1 fw`, `a.ps1 fwu`, и прогон самого гейта.
- **Owner sign-off:** не требуется - правка восстанавливает штатное распространение отмены и расширяет периметр уже принятого правила.
- **Related tickets:** S1363 (ввёл правило), S1889 (расширил его на форму по надтипу и вычистил `app_v2`), S1911 (соседняя форма - `runCatching`).

### 3.4 Baseline

После правок повторное измерение той же функцией: **29** (было 34). Этим числом и засеян baseline модуля - как всякий ратчет здесь, он может только опускаться.

### 3.5 Обёртка гейта

`assert-swallowed-cancellation.ps1` форвардил `Only = 'swallowed-cancellation'` и после появления второго правила рапортовал бы только про телефон, молча не упоминая часы - при том, что назван по правилу. Теперь форвардит оба имени.

---

## 4. Проверка

- Измерение правила по `wear/src`: **34** до правок, **29** после - той же функцией `Find-SwallowedCancellationLines`, которой судит гейт.
- `assert-swallowed-cancellation.ps1` печатает обе строки: `swallowed-cancellation` (app_v2) и `swallowed-cancellation-wear` **baseline 29 | actual 29 | delta 0**.
- `a.ps1 fw` - exit 0.
- `a.ps1 fwu` - exit 0, `assert-test-suite-complete: 38 report(s) for 38 *Test.kt` (ratio 1).
- Замечание о состоянии дерева: в этом прогоне `swallowed-cancellation` по `app_v2` показывал `actual 469 | delta 2`. Проверено пофайлово - ни один файл, изменённый этим тикетом или этой сессией, правилом не помечен; дельта принадлежит незавершённой работе параллельной сессии в `app_v2` и закрывается её собственным scoped-закрытием.
