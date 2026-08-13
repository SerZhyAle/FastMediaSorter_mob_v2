# Спецификация (compact bugfix): S1468 - Стрим-диагностика не попадает в лог, который присылает пользователь

**Ticket:** S1468
**Status:** Archived
**Priority:** 85
**Date:** 2026-08-07
**Tier:** 2 - Easy (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-07

**Текст:**

```
"P:\ANDROID\FastMediaSorter_mob_v2\logs\FastMediaSorter Debug Logs (1)"
"P:\ANDROID\FastMediaSorter_mob_v2\logs\FastMediaSorter Debug Logs (2)"
"P:\ANDROID\FastMediaSorter_mob_v2\logs\fastmediasorter_logs (1).zip"
"P:\ANDROID\FastMediaSorter_mob_v2\logs\fastmediasorter_logs.zip"
"P:\ANDROID\FastMediaSorter_mob_v2\logs\FastMediaSorter Debug Logs"

изучи логи - вдруг найдешь что то полезное или сможешь закрытьобновить /создать какие то задачи?
Что можно сделать с приёмом телесигнала, он не полностью стабильный (последний лог)
```

**Вложения:**
- Лог-эвиденс общий с S1467 - `PLAN/S1467_bugfix-stream-stall-watchdog-reanchor-loop/attachments/01__device-log-2026-08-07-car-head-unit.log`

---

## 1. Проблема / симптом

Файловый лог, который пользователь выгружает и присылает («Debug Logs»), в release-сборке пишет только WARN и выше: `LoggingHelper` сажает `FileLoggingTree(context, minPriority = android.util.Log.WARN)`, а сам tree отбрасывает всё ниже порога.

Вся диагностика интернет-потоков при этом эмитится на INFO и DEBUG, то есть физически отсутствует в единственном логе, который до нас доходит:

- `Stream session: ttff=.. stalls=.. totalStallMs=.. dropped=.. decoder=..` - `Timber.i`, один раз за сессию, при teardown. Это ровно та сводка, ради которой она и писалась.
- `Stream diag: first frame ttff=..` - `Timber.i`
- `Stream diag: decoder=<имя> (hw|sw)` - `Timber.i`
- `Stream diag: dropped=.. total=..` - `Timber.d`
- `Stream quality: renditions=N single=<bool>` - `Timber.i`
- `Stream quality: stepped down to <=WxH @Nbps` - `Timber.i`
- `Stream state=<label> reconnecting=<bool>` - `Timber.d` (введена S0937 именно для тихих зависаний)

Практическое следствие видно в логе 2026-08-07: 34 строки `Stream stall - watchdog re-anchor` (WARN, поэтому дошли) и ноль контекста вокруг них. По этому логу нельзя ответить ни на один из вопросов, от которых зависит выбор решения в S1467: сколько рендиций у потока и не single-quality ли он; сработал ли step-down S1128 и до какой ступени; аппаратный или программный декодер выбран; сколько кадров дропнуто; каков был ttff. Диагностика написана, работает - и невидима.

Заметно, что события низкочастотные: сессионная сводка - раз на канал, `renditions` - раз на сессию, `stepped down` - только на застое. Спама в файл они не добавят.

---

## 2. Корневая причина

Две настройки, выбранные независимо и никогда не сверенные друг с другом.

- Уровень эмиссии выбран осознанно: диагностика потока - операционная, а не тревожная, поэтому `Timber.i` / `Timber.d`.
- Порог файлового tree в release - WARN: `FileLoggingTree(context, minPriority = android.util.Log.WARN)`, `LoggingHelper.kt:205`. В debug тот же класс создаётся без аргумента и берёт умолчание VERBOSE (`LoggingHelper.kt:189`, поле - `LoggingHelper.kt:221`), поэтому на столе дефект невидим и воспроизводится только на release-устройстве.

Отсев выполняется единственным сравнением уровня - `if (priority < minPriority) return`, `LoggingHelper.kt:338`. Оно стоит до `writeEntry`, то есть текст сообщения не осматривается вообще: сегодня в этом классе физически нет точки, где содержимое строки могло бы дать исключение из порога.

Два факта, которые сужают выбор механизма и проверены в коде, а не предположены.

- Исключений по содержимому в логовом пути нет ни одного: поиск по `allowList` / `allowlist` / `exempt` / `alwaysLog` / `forceLog` в `core/logging/` пуст. Единственная существующая реклассификация по содержимому - понижение ERROR до WARN для известных шумных ошибок (`isUnimportantError`, `LoggingHelper.kt:461-470`), и она осматривает throwable, а не текст сообщения.
- Тег как признак не годится: ни одна из семи строк не вызывает `Timber.tag(..)`, а `FileLoggingTree` не наследует `Timber.DebugTree` и потому не выводит тег из класса-вызывателя. Все семь приходят в `log()` с `tag == null` и рендерятся как `App`. Различить их можно только по тексту сообщения.

Проверка соседних подсистем, затребованная в §3, выполнена: у аудио-пути тот же корень. В `AudioPlaybackService.kt` восемь из одиннадцати строк `Audio diag:` эмитятся на INFO (строки 149, 158, 181, 194, 202, 206, 211, 222) и в release невидимы; видимы только три WARN (167, 171, 233) - ровно те, по которым §1 и счёл аудио-путь «частично видимым». Передача файлов в этот тикет не входит.

---

## 3. Исправление

Принят второй кандидат: allow-list префиксов сообщения в `FileLoggingTree`, общий порог WARN сохраняется.

Почему отклонены остальные два - оба отклонены по критериям, уже записанным в этом же спеке, а не по вкусу.

- Подъём строк до `Timber.w` отклонён: §1 сам фиксирует, что уровень INFO выбран осознанно как «операционный, не тревожный», и подъём стирает это различие в logcat и в crash-отчётах, а не только в файле.
- Переключатель в настройках отклонён: критерий приёмки в §4 - что нужные строки содержит **следующий** лог с той же магнитолы, без дополнительных действий владельца. Переключатель, который надо найти и включить до наступления дефекта, этому критерию не удовлетворяет; он же добавляет настроечную поверхность и обязательную пересборку документации настроек ради диагностики.

Что делается.

- Предикат `isAlwaysPersistedDiagnostic(message)` - top-level `internal fun` рядом с `stripTimberAppendedTrace`, по образцу этой же функции: она вынесена наружу именно ради юнит-тестируемости без создания пишущего в файл tree (`LoggingHelper.kt:667`).
- Гейт `LoggingHelper.kt:338` становится `if (priority < minPriority && !isAlwaysPersistedDiagnostic(message)) return`. Порядок операндов существенен: сравнение уровня остаётся первым, поэтому для строк WARN и выше сканирование префиксов не выполняется вовсе.
- Список префиксов: `Stream diag:`, `Stream session:`, `Stream quality:`, `Stream state=`, `Audio diag:`.
- Уровень строки сохраняется при записи: `writeEntry` рендерит `I/App:` или `D/App:`, то есть семантика WARN не размывается - это и есть преимущество перед первым кандидатом.
- Debug-ветка не трогается: там порог VERBOSE и все строки пишутся уже сейчас.

Объём записи ограничен по построению: все затронутые события - разовые на сессию, на канал или на действие пользователя, а существующие предохранители tree (`MAX_PENDING_WRITES`, ротация 5 файлов по 5 МБ) остаются последним рубежом.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1467 (потребитель - без этого тикета его §2 нечем закрыть), S0937 (автор строки `Stream state=`), S1128 (автор строк `Stream quality:`), S1127 (автор строк `Stream diag:` и `Stream session:` - четыре строки из семи, в исходном списке отсутствовал)

---

## 4. Проверка

- Юнит-тест предиката: каждый из пяти префиксов принимается, посторонняя INFO-строка отвергается, пустая строка отвергается, совпадение проверяется по началу строки, а не по вхождению.
- `.\a.ps1 fk` - компиляция standard проходит.
- Финальный критерий, снимается владельцем на устройстве: следующий лог с той же магнитолы содержит `Stream session:` и `Stream quality: renditions=` для каждого проигранного канала.

---

## 5. Фазы

### Phase 01 - Allow-list диагностических префиксов в файловом логе

**Objective:** файловый tree в release пишет перечисленные диагностические строки, не меняя порог WARN для всего остального.

#### Step 01.1 - Вынести предикат префиксов top-level

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt`
**Depends on:** - начало фазы

**Prompt for developer:**

> Add a top-level `internal fun isAlwaysPersistedDiagnostic(message: String): Boolean` next to `stripTimberAppendedTrace` at the end of the file. It returns true when the message starts with any entry of a private top-level list holding `Stream diag:`, `Stream session:`, `Stream quality:`, `Stream state=`, `Audio diag:`. Match on the start of the string, not on containment. Add a KDoc stating why the rule lives at top level and why the level itself is left untouched.

**Why:**

Тег у всех семи затронутых вызовов приходит как `null`, поэтому текст сообщения - единственный доступный признак, а размещение top-level повторяет уже принятое в этом файле решение по `stripTimberAppendedTrace`: правило проверяется юнит-тестом без создания пишущего в файл tree.

**Verification:**

- `Grep` - `internal fun isAlwaysPersistedDiagnostic` встречается в файле ровно один раз.
- `Grep` - каждый из пяти литералов префикса присутствует в файле.

**Status:** `[x]` done

#### Step 01.2 - Пропустить диагностику через порог

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `FileLoggingTree.log()` change the threshold guard to `if (priority < minPriority && !isAlwaysPersistedDiagnostic(message)) return`. Keep the priority comparison as the first operand so a WARN-or-higher line never scans the prefix list. Update the trailing comment to say that listed diagnostic prefixes are exempt.

**Why:**

Это единственная точка отсева: сравнение стоит до `writeEntry`, поэтому сегодня строка ниже порога не доходит до места, где её содержимое можно было бы осмотреть.

**Verification:**

- `Grep` - `!isAlwaysPersistedDiagnostic(message)` присутствует в теле `log()`.
- `Grep` - `if (priority < minPriority) return` больше не встречается.

**Status:** `[x]` done

#### Step 01.3 - Юнит-тест предиката

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/logging/LoggingHelperDiagnosticPrefixTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a plain JUnit test class - no Robolectric runner, because the predicate touches no Android API and the project tests pure-Kotlin logic without one (`StreamPlaybackDiagnosticsTest`). Assert that each of the five prefixes is accepted, that an unrelated INFO message is rejected, that an empty message is rejected, and that a message merely containing a prefix in the middle is rejected.
>
> <!-- step corrected during /spec-all impl - originally specified the Robolectric runner of LoggingHelperStripTraceTest, which that test needs only because it calls android.util.Log -->


**Why:**

`FileLoggingTree` - приватный вложенный класс без единого прямого теста, поэтому проверяемым делается именно вынесенный предикат, как это уже сделано для `stripTimberAppendedTrace`.

**Verification:**

- `Glob` - файл теста существует.
- `.\a.ps1 fu` или адресный `--tests` - класс проходит.

**Status:** `[x]` done

#### Phase Done Criteria

- [x] Все шаги `[x] done`.
- [x] `.\a.ps1 fk` - компиляция standard проходит.
- [x] `post-change.ps1` закрывает изменение без падения гейтов.

---

## Last Audit

**Дата:** 2026-08-07. **Проведён:** `/spec-all`, Simple path, S4.

Изменённый код:

- `app_v2/src/main/java/com/sza/fastmediasorter/core/logging/LoggingHelper.kt` - гейт `FileLoggingTree.log()` и top-level предикат `isAlwaysPersistedDiagnostic` с приватным списком префиксов.
- `app_v2/src/test/java/com/sza/fastmediasorter/core/logging/LoggingHelperDiagnosticPrefixTest.kt` - новый, 8 тестов.

Эвиденс:

- `.\a.ps1 fk` - exit 0, `BUILD SUCCESSFUL`.
- `.\a.ps1 fu -Tests "*LoggingHelperDiagnosticPrefixTest*"` - exit 0; `TEST-..LoggingHelperDiagnosticPrefixTest.xml` от 12:46:27 показывает `tests=8 failures=0 errors=0 skipped=0`.
- `post-change.ps1 -ScopeToFile -ChangeType Kotlin` - `post-change: PASS`, exit 0, без advisories.

Проверенное сверх шагов фазы:

- Приватность не пострадала: exempt-строки не обходят запись, а идут через тот же `writeEntry` -> `SecretMasker.sanitize` (`LoggingHelper.kt:377`), который маскирует и `scheme://user:pass@host`, и `token=` / `password=`. Это существенно, потому что затронутые строки несут `path=<url>` потока, а файл владелец пересылает целиком. Нового класса утечки не появилось и по другой причине: WARN-строки watchdog уже содержали тот же URL и в лог попадали.
- Стоимость горячего пути ограничена: сравнение уровня осталось первым операндом, поэтому строка WARN и выше список префиксов не сканирует вовсе; ниже порога это до пяти `startsWith` на коротких литералах.
- Предупреждение компилятора `LoggingHelper.kt:618 'val id: Long' is deprecated` осталось: `javap` по `android-36` показывает у `java.lang.Thread` только `getId()`, метода-замены `threadId()` на этой платформе нет. Чинить нечем, отдельный тикет не заводится.

Остаточный разрыв, осознанный:

- Третий критерий §4 снимается только владельцем: нужный лог даёт **release**-сборка на той же магнитоле. Отладочная проверка на эмуляторе здесь ничего не доказывает - в debug у файлового tree порог VERBOSE, то есть исключение из порога в debug вообще не исполняется. По этой же причине тикет не переводится в `BlockNeedUserTest`: отладочные пробы `Timber.d("S1468: ..")` были бы либо невидимы в release, либо видимы только через это же исключение, то есть проверяли бы сами себя.

**Вердикт:** `Implemented`. Механизм доказан компиляцией, восемью юнит-тестами и чтением единственной точки отсева; полевое подтверждение приходит вместе со следующим логом с магнитолы, который и является входом S1467.
