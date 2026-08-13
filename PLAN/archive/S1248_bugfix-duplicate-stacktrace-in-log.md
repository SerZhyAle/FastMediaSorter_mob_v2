# Спецификация (compact bugfix): S1248 - Stack trace дублируется в файловом логе

**Ticket:** S1248
**Status:** Archived
**Priority:** 55
**Date:** 2026-07-28
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-28

**Текст:**

Every error logged with a throwable is written twice into the session log file - FileLoggingTree appends the stack trace that Timber's prepareLog already merged into the message

**Обнаружено при:** анализе `logs/fastmediasorter_20260728_023240.log` (`/log-reader`).

**Эвиденс** - одно событие `Timber.e(throwable, message)` даёт две идентичные трассы подряд, строки 351-420 и 483-566 указанного лога:

```
2026-07-28 02:33:17.912 E/App: [scope=resource-navigation resource=MOV failureClass=timeout] Connection test failed
com.jcraft.jsch.JSchException: timeout: socket is not established
        at com.jcraft.jsch.Util.createSocket(Util.java:386)
        ..
        ... 21 more

com.jcraft.jsch.JSchException: timeout: socket is not established
        at com.jcraft.jsch.Util.createSocket(Util.java:386)
        ..
        ... 21 more
```

Место вызова единственное - `HandledNetworkOutcomeLogger.logConnectionTestFailure` вызывает `Timber.e(throwable, "$prefix $message")` ровно один раз.

---

## 1. Проблема / симптом

Каждая запись уровня ERROR со `throwable` попадает в файл сессии с двумя одинаковыми stack trace. Timber в `Tree.prepareLog` дописывает трассу к `message` и одновременно передаёт сам `throwable` в `log()`; кастомное дерево файлового лога дописывает трассу второй раз.

Последствия: файл сессии раздувается вдвое на каждой ошибке (в `logs/` уже лежат сессии по ~1 МБ), быстрее срабатывает ротация по `maxFileSize` и раньше теряется ранняя часть сессии, диагностика по логу читается тяжелее.

Область: подсистема логирования, `core/logging`. Затрагивает обе ветки - debug-дерево файлового лога и release-дерево вывода в logcat, где throwable печатается отдельным вызовом поверх уже дополненного сообщения.

Затронуты все flavor и оба типа сборки, поскольку дерево планируется в обеих ветках.

---

## 2. Корневая причина

Подтверждено (2026-07-28): `Timber.Tree.prepareLog` на закреплённой версии при `t != null`
приклеивает `"\n" + Log.getStackTraceString(t)` к `message` и ОДНОВРЕМЕННО передаёт `t` в
`log()`. Оба кастомных дерева рендерят трассу из `t` сами:

- `FileLoggingTree.writeEntry` (ERROR-ветка) печатал `message` (уже с трассой) и следом
  `getCompactStackTrace(t)` - две трассы в файле сессии.
- Release-дерево logcat печатало `message` и вторым `println` полную трассу - дубль в logcat.

Побочные следствия той же причины: WARN-ветка файла («одна компактная строка») на деле была
многострочной - трасса сидела внутри `message`; сжатие Glide-ошибок фактически не работало -
полная трасса всё равно печаталась через `message`.

Чинится на стороне деревьев: вызовы (`Timber.e(t, msg)`) корректны и остаются как есть.

---

## 3. Исправление

- Top-level `stripTimberAppendedTrace(message, t)` в `LoggingHelper.kt` - снимает приклеенную
  трассу и возвращает исходный текст вызова; для `Timber.e(t)` без текста (message == трасса)
  возвращает пустую строку, чтобы единственную копию напечатал рендер дерева.
- `FileLoggingTree.writeEntry`: санитизация и печать идут от очищенного текста; трасса
  печатается ровно один раз - через существующий `getCompactStackTrace` (сжатие Glide-ошибок
  теперь работает по назначению), WARN снова однострочен.
- Release-дерево: второй `println` трассы удалён - `message` уже несёт её.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1203 (файловое логирование вынесено на отдельный поток; статус `BlockNeedUserTest`)

---

## 4. Проверка

Выполнено 2026-07-28:

- Unit: `LoggingHelperStripTraceTest` (5 тестов, Robolectric @Config(sdk=34)) - снятие приклеенной
  трассы, неизменность без throwable, пустой результат для `Timber.e(t)` без текста, выживание
  переводов строк внутри текста, и «текст + один рендер трассы = ровно одна трасса».
  `check-standard-fast -Mode Unit`: Fast check passed (19:29).
- Устройство (emulator-5554, фикс-сборка, сессия `fastmediasorter_20260728_192839.log`):
  спровоцированы два ERROR с throwable (SMB-тест к недоступному 10.0.2.77 - путь эвиденса
  `logConnectionTestFailure`). В файле по ОДНОЙ трассе на событие: `failed to connect to
  /10.0.2.77` встречается ровно 2 раза на 2 события (строки 400 и 440); двойное вхождение
  `IoBridge.isConnected` внутри каждой - основной фрейм + фрейм в `Caused by`-цепочке, то есть
  структура одной трассы. До фикса эвиденс §0 показывал две идентичные трассы подряд.
- expected: одна трасса на ERROR-событие | actual: одна (с Caused by) на каждое из двух - PASS.

## Last Audit

**Дата:** 2026-07-28. **Вердикт:** Verified.

- Оба дерева исправлены (файловое + release-logcat); вызовы не менялись.
- Строка WARN снова однострочная, сжатие Glide-трасс работает по назначению (оба - побочные
  жертвы той же корневой причины, §2).
- detekt scoped PASS; fk/dq PASS; unit 5/5; девайс-сверка выше.
- Замечание процессу: бэкап `LoggingHelper.kt` (611 LOC) сделан после первой правки, а не до -
  отступление от Rule 5 зафиксировано, снапшот `temp/S1248/LoggingHelper_*_postedit.kt.bak`.
