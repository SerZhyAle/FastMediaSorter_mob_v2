# Спецификация (compact bugfix): S1363 - CancellationException проглатывается широким catch и превращается в ошибку

**Ticket:** S1363
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-02
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-02

**Текст:**

Автозахват при анализе удалённого лог-бандла (`/newlog`), сессии `logs/fastmediasorter_20260731_004038.log` и `logs/fastmediasorter_20260801_183450.log`, устройство SM-S731B, Android 16 / API 36, сборка `2.60.7302.058-NoLegal-DEBUG`.

Обычный уход с экрана печатает в лог ошибку уровня E и возвращает наверх Failure-результат, хотя ничего не сломалось.

Случай 1 - `GoogleDriveRestClient.listFiles` (`.kt:208-210`, `catch (e: Exception)`):

```
2026-08-01 10:49:48.221 D/App: onDestroy: BrowseActivity
2026-08-01 10:49:49.150 E/App: Failed to list files
kotlinx.coroutines.JobCancellationException: Job was cancelled; job=SupervisorJobImpl{Cancelled}@cad2857
```

Случай 2 - `FileOperationUseCase.executeInternal` (`.kt:420-422`, `catch (e: Exception)`):

```
[30587] 20:32:01  E  [1202b180|file-operation] EXCEPTION in executeInternal
kotlinx.coroutines.JobCancellationException: Job was cancelled; job=JobImpl{Cancelling}@7b674dd
[30845] 20:32:31  E  [820aecc9|file-operation] EXCEPTION in executeInternal
kotlinx.coroutines.JobCancellationException: Job was cancelled; job=JobImpl{Cancelling}@8fa5b7a
```

---

## 1. Проблема / симптом

`catch (e: Exception)` в suspend-коде ловит и `CancellationException`. Отмена корутины при этом:

- логируется как ошибка (`Timber.e` / `StructuredLogger.e`), из-за чего диагностика забивается ложными E - в разборе удалённого лога это первое, что приходится отсеивать вручную;
- конвертируется в доменный результат ошибки (`CloudResult.Error`, `FileOperationResult.Failure`), то есть вызывающий видит "операция провалилась" вместо "операция отменена";
- не пробрасывается дальше, что ломает структурную конкурентность: родительский job считает дочерний завершившимся штатно.

Подтверждено два места, но конструкция типовая, поэтому тикет про класс проблемы, а не про две строки. Рядом лежит уже закрытый прецедент S1212 (bugfix-apk-install-swallows-cancellation, Verified) - тот же дефект, точечно исправленный в одном месте.

Кандидат на механический гейт (`scripts/quality/assert-*.ps1`) по правилу CLAUDE.md 13 "recurring finding -> mechanical gate": широкий `catch (e: Exception)` в suspend-функции без предшествующего `catch (e: CancellationException) { throw e }`.

---

## 2. Корневая причина

`CancellationException` - обычный наследник `Exception`, поэтому `catch (e: Exception)` в suspend-коде ловит её наравне с реальными сбоями.

- Отмена корутины неотличима от сбоя на уровне типа, а различает их только явная ветка `catch (e: CancellationException)` перед широкой либо вызов `Throwable.rethrowIfCancellation()` первой строкой блока.
- Хелпер `rethrowIfCancellation()` в `core/util/CoroutineExt.kt` уже существовал и решал ровно эту задачу, но применён был лишь в 5 файлах из всего дерева - лечение шло точечно, по мере того как дефект всплывал (S1212 - последний такой случай).
- Ничто не мешало дефекту расти: ни одного механического гейта на эту конструкцию не было, а `detekt`-правило `TooGenericExceptionCaught` в проекте не различает "поймал и обработал" и "поймал и проглотил отмену".
- Реальный размер класса на момент тикета - 804 места в `app_v2/src/main`, то есть проблема системная, а не в двух строках из лога.

---

## 3. Исправление

Двухчастное: сначала механический стоп роста, затем лечение подтверждённого логом участка.

### 3.1 Механический гейт

- Правило `swallowed-cancellation` добавлено в общую библиотеку лексических правил `scripts/quality/lib/source-matchers.ps1`, поэтому попадает в `assert-source-gates.ps1`, в `.\a.ps1 fg` и в `post-change.ps1` без отдельной проводки.
- Нарушение: широкий `catch (e: Exception)` / `catch (e: Throwable)`, достижимый из корутины, в цепочке которого нет более ранней ветки `catch (e: CancellationException)` и блок которого не начинается с `rethrowIfCancellation()`.
- Достижимость из корутины определяется ближайшей объемлющей конструкцией: сначала лямбда билдера (`withContext`, `launch`, `async`, `flow`, `coroutineScope` и прочие), иначе само объявление функции с модификатором `suspend`. Блокирующий помощник, который отменить нельзя, правилом не судится.
- Обе формы лечения приняты как равноправные: первая редакция правила знала только явную ветку и дала 24 ложных срабатывания на файлах, вылеченных хелпером.
- Тонкая обёртка `scripts/quality/assert-swallowed-cancellation.ps1` повторяет форму остальных членов семейства, baseline - `scripts/quality/swallowed-cancellation-baseline.txt`, храповик работает только вниз.
- Регрессионный набор `scripts/quality/assert-swallowed-cancellation.tests/Run-Tests.ps1` покрывает все три шага эвристики отдельно, потому что гейт, который видели только зелёным, ничего не доказывает.

### 3.2 Лечение подтверждённого участка

- Вылечен весь слой `data/cloud` (11 файлов) и `FileOperationUseCase` - ровно те два пути, что дали E-записи в разобранных сессиях, плюс однотипное окружение первого из них. Итого 72 места.
- Идиома - `rethrowIfCancellation()` первой строкой блока, а не отдельная ветка `catch (e: CancellationException)`. Выбор не стилистический: вариант с веткой был написан первым и уронил detekt на трёх файлах сразу - `LargeClass` в `OneDriveRestClient`, `ThrowsCount` в `CloudFileOperationHandler.uploadToCloudFromPath`, `CyclomaticComplexMethod` в `DropboxClientUtils.withRetry`. Хелпер стоит одну строку вместо двух и не добавляет ни `throw`, ни ветвления, поэтому ни один порог не сдвигается.
- Блок импортов в каждом изменённом файле пересортирован по раскладке ktlint. Вставка импорта в уже несортированный блок меняет его сигнатуру в detekt-baseline и воскрешает подавленную находку `ImportOrdering`, поэтому блок должен стать действительно сортированным, а не «не хуже, чем был».
- Остаток класса (732 места) не трогается в этом тикете и вынесен отдельным долговым тикетом - см. §5.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1212 (bugfix-apk-install-swallows-cancellation, Verified) - тот же дефект, исправлен точечно; S1362 (массовая операция умирает с экраном) - там эта же дыра портит диагностику.

---

## 4. Проверка

- Гейт видит дефект и лечение: `assert-swallowed-cancellation.ps1` дал 804 до правки и 732 после, delta -72, baseline проратчен вниз автоматически.
- Эвристика проверена не только на зелёном дереве: `assert-swallowed-cancellation.tests/Run-Tests.ps1` - 13 из 13, exit 0; среди случаев есть и заведомо сломанные, и обе формы лечения, и вызов хелпера не первой строкой.
- Гейт доказан не только на зелёном дереве: откат 12 файлов к исходному состоянию поднял счётчик обратно на 804 против baseline 732, то есть регресс он ловит.
- Компиляция: `.\a.ps1 fk` - BUILD SUCCESSFUL, exit 0.
- Статический анализ: `assert-detekt.ps1 -Gate` - PASS [app_v2 + wear], exit 0.
- Импорт `com.sza.fastmediasorter.core.util.rethrowIfCancellation` присутствует ровно один раз в каждом из 12 изменённых файлов - дублей вставка не создала.
- Регресс по таймаутам исключён проверкой, а не рассуждением: `TimeoutCancellationException` наследует `CancellationException`, поэтому проброс изменил бы поведение `withTimeout`; ни `withTimeout`, ни `NonCancellable` в вылеченном наборе не встречаются.
- Поведенческая проверка на устройстве для закрытия не требуется - по прецеденту S1212: дефект и его лечение доказываются статически, а сценарий из лога воспроизводится только с привязанным облачным аккаунтом.

---

## 5. Остаток

- В `app_v2/src/main` остаётся 732 места того же класса; крупнейшие узлы - `domain/usecase`, `ui/player/helpers`, `data/transfer/strategy`, `data/network`, `data/remote/ftp`.
- Рост остановлен храповиком, поэтому остаток - управляемый долг, а не открытая рана.
- Лечить его пакетно нельзя вслепую: там, где рядом стоит `withTimeout`, ветка `catch (e: TimeoutCancellationException)` должна идти перед пробросом, иначе таймаут перестанет деградировать в доменную ошибку и улетит наверх.

---

## Last Audit

**Date:** 2026-08-03
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 11 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

Проверено статически:

- Правило `swallowed-cancellation` зарегистрировано в `scripts/quality/lib/source-matchers.ps1` ровно один раз, обёртка и baseline на месте, baseline = 732.
- `assert-swallowed-cancellation.ps1 -Gate` - exit 0; `assert-swallowed-cancellation.tests/Run-Tests.ps1` - 13/13, exit 0.
- Гейт доказан на регрессе, а не только на зелёном дереве: откат вылеченных файлов поднял счётчик до 804 против baseline 732.
- 72 вызова `rethrowIfCancellation()` в 12 файлах - ровно столько, сколько мест было помечено гейтом; импорт хелпера в каждом файле ровно один.
- `.\a.ps1 fk` - BUILD SUCCESSFUL; `assert-detekt.ps1 -Gate` - PASS [app_v2 + wear]; `assert-script-cheatsheet-sync.ps1` - OK после регенерации.
- Отладочных тегов `Timber.d("S1363:` в дереве нет - соответствует статусу.

### Manual / on-device

- [ ] Наблюдение в удалённом логе: уход с экрана облачного ресурса во время сканирования не должен давать E-запись об отмене. Для закрытия не требуется (прецедент S1212), сценарий воспроизводится только с привязанным облачным аккаунтом.
