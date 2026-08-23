# Стратегическая спецификация: S1813 - Переопределение в CloudFileOperationHandler теряет удержание диспетчера

**Ticket:** S1813
**Status:** Archived
**Priority:** 45
**Date:** 2026-08-19
**Tier:** 2 - Small (ad-hoc)
**Roadmap entry:** Ad-hoc - находка трассировки вызывающих при S1812, 2026-08-19

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-19

**Симптом.** `BaseFileOperationHandler.executeCopy` объявлена так:

```kotlin
open suspend fun executeCopy(
    operation: FileOperation.Copy,
    progressCallback: ByteProgressCallback? = null
): FileOperationResult = withContext(Dispatchers.IO) {
```

То есть базовый класс удерживает `Dispatchers.IO` за всех наследников. `CloudFileOperationHandler`
переопределяет её обычным телом:

```kotlin
override suspend fun executeCopy(
    operation: FileOperation.Copy,
    progressCallback: ByteProgressCallback?
): FileOperationResult {
    val destinationPath = operation.destination.path
```

Переопределение заменяет тело целиком, а значит и `withContext` вместе с ним. Наследник выполняет
копирование - включая сетевые пути SMB, FTP, SFTP и облако - на диспетчере вызывающего.

**Почему сегодня не стреляет.** Единственный вызывающий, `FileOperationUseCase`, оборачивает вход сам
(`launch(Dispatchers.IO)` на строке 215 и `withContext(Dispatchers.IO + ..)` на строке 436). То есть
безопасность держится на одном внешнем вызывающем, а не на контракте, который базовый класс декларирует.

**Почему это ловушка, а не мелочь.** Читатель, добавляющий второго вызывающего, видит в базовом классе
`= withContext(Dispatchers.IO)` и делает верный вывод, что удержание есть. Для облачного наследника этот
вывод неверен, и ничто в коде на это не указывает.

**Доказательства (2026-08-19):**

- `app_v2/src/main/java/com/sza/fastmediasorter/data/transfer/BaseFileOperationHandler.kt:33-36` -
  `open suspend fun executeCopy(..) = withContext(Dispatchers.IO) { .. }`.
- `app_v2/src/main/java/com/sza/fastmediasorter/data/cloud/CloudFileOperationHandler.kt:129` -
  `override suspend fun executeCopy(..): FileOperationResult {` без `withContext`.
- `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/FileOperationUseCase.kt:215,436` -
  единственный вызывающий, удерживающий контекст снаружи.

---

## 1. Проблема

Контракт, объявленный базовым классом, не выполняется одним из наследников, и разрыв не виден на месте
переопределения.

---

## 2. Цели

1. `CloudFileOperationHandler.executeCopy` либо удерживает диспетчер сама, либо явно документирует, что
   удержание - обязанность вызывающего, и почему так.
2. То же проверено для остальных переопределений в этом семействе - `executeMove` и `executeDelete`.

**Non-goals:**

- Правило lint на этот класс дефектов - это S1810.

---

## 3. Решение

**Замер 2026-08-19 показал, что случай не один.** У `BaseFileOperationHandler` четыре наследника, и
переопределений трёх удерживающих методов - восемь. Из них:

- **Восстанавливают удержание сами:** `CloudFileOperationHandler.executeDelete` (собственный `withContext`).
- **Делегируют в `super`, то есть удержаны базой:** `SftpFileOperationHandler.executeCopy` и
  `SftpFileOperationHandler.executeDelete`.
- **Теряют удержание - пять:** `CloudFileOperationHandler.executeCopy` (129) и `.executeMove` (219),
  `FtpFileOperationHandler.executeMove` (73), `SftpFileOperationHandler.executeMove` (88),
  `SmbFileOperationHandler.executeMove` (300).

`FtpFileOperationHandler.deleteFile` в список не входит: базовый `deleteFile` сам ничего не удерживает,
так что терять там нечего.

**Как чинится.** Каждое из пяти переопределений оборачивается на месте: заголовок
`): FileOperationResult {` становится `): FileOperationResult = withContext(Dispatchers.IO) {`, а все
собственные `return` функции получают метку `return@withContext`. Отступы тела не меняются вовсе - блок
функции и блок лямбды это одни и те же скобки.

**Почему не структурная переделка.** Напрашивается сделать `executeCopy` невиртуальной обёрткой, а
наследникам дать `protected open` метод под другим именем. Это чинит класс дефектов, а не пять случаев -
но требует переименовать пять переопределений, а переименование метода меняет его сигнатуру в базовой линии
detekt и поднимает всё, что на ней держалось. Цена измерена сегодня же, на S1812: там переименование двух
функций подняло шесть ранее подавленных находок и завалило гейт. Пять переименований обойдутся дороже, и
это отдельное решение.

Вместо этого инвариант записан комментарием у самих удерживающих методов базового класса - в том месте,
где его прочитает автор следующего переопределения.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1812 (нашёл случай и измерил цену переименования), S1810 (правило lint для этого класса дефектов)

---

## 6. Открытые вопросы / Research items

1. **Обернуть или делегировать**
   - **Вопрос:** обернуть тело переопределения, или перестроить базовый класс так, чтобы удержание было в
     невиртуальной обёртке, а наследники переопределяли защищённый метод?
   - **Статус:** Resolved (2026-08-19)
   - **Ответ:** обернуть на месте. Структурная переделка правильнее по существу, но дороже, чем стоит
     сегодня.
   - **Измерение:** наследников у `BaseFileOperationHandler` четыре, переопределений трёх удерживающих
     методов восемь, теряют удержание пять. Структурная форма потребовала бы переименовать все пять.
   - **Почему цена известна точно:** ровно это было проделано сегодня в S1812 и откачено. Переименование
     двух функций сдвинуло их сигнатуры в базовой линии detekt и подняло шесть ранее подавленных находок -
     `LongMethod`, `CyclomaticComplexMethod`, `NestedBlockDepth`, `ReturnCount`, `TooManyFunctions` - ни одна
     из которых не была новым долгом. Пять переименований дадут то же самое, только больше.
   - **Что оставлено:** структурная форма остаётся верным ответом на класс дефектов; её цена - разбор
     поднятой базовой линии, и это отдельный тикет, если пятый случай появится снова.

---

## 7. Фазы

### Phase 01 - Every override restates the confinement

**Objective:** no override of a confining base method runs its body on the caller's dispatcher, and the next author is told so at the declaration.

**Files Touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `data/cloud/CloudFileOperationHandler.kt` | Modified | 804 -> <= 810 |
| `data/network/FtpFileOperationHandler.kt` | Modified | 560 -> <= 566 |
| `data/network/SftpFileOperationHandler.kt` | Modified | 469 -> <= 475 |
| `data/network/SmbFileOperationHandler.kt` | Modified | 776 -> <= 782 |
| `data/transfer/BaseFileOperationHandler.kt` | Modified | 441 -> <= 447 |

---

#### Step 01.1 - Wrap the five overrides in place

**Files:** the four handler subclasses
**Depends on:** - start of phase

**Prompt for developer:**

> Turn each of the five losing overrides into `): FileOperationResult = withContext(Dispatchers.IO) {` and label every one of its own `return` statements `return@withContext`, including the four `?.let { return it }` forms in the cloud handler. Change no indentation.

**Why:**

Section 3 records that these five replace a base body whose whole content is `withContext(Dispatchers.IO) { .. }`, so each one silently hands its blocking work to whatever dispatcher the caller was on - the same defect class S1812 measured across the SMB family.

**Verification:**

- `Grep` - each of the five headers reads `= withContext(Dispatchers.IO) {`.
- `Grep` - zero unlabelled `return ` remain inside those five bodies.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

#### Step 01.2 - State the invariant where an override is written

**Files:** `data/transfer/BaseFileOperationHandler.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a comment above `executeCopy` in the base class saying that these three methods confine in their own body, that an override replaces that body, and that an override must therefore restate `withContext(Dispatchers.IO)` or delegate to `super`.

**Why:**

Section 1 records that the gap is invisible at the point of overriding - a reader sees `= withContext(Dispatchers.IO)` on the declaration and concludes the confinement is inherited, which is exactly the wrong conclusion and is what produced all five cases.

**Verification:**

- `Grep` - the comment is present immediately above `open suspend fun executeCopy`.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

---

**Phase Done Criteria**

- [x] Every `Step 01.*` above is `[x]` done.
- [x] `.\a.ps1 fk` exits 0 and the file-operation unit tests pass. - done
- [x] `post-change.ps1` closes with `post-change: PASS`.

---

---

## 8. Implementation State

**2026-08-19 - Phase 01 done.**

- Five overrides now read `): FileOperationResult = withContext(Dispatchers.IO) {`: `CloudFileOperationHandler.executeCopy` (132) and `.executeMove` (224), `FtpFileOperationHandler.executeMove` (76), `SftpFileOperationHandler.executeMove` (91), `SmbFileOperationHandler.executeMove` (303). Fifteen own `return` statements became `return@withContext`, plus four `?.let { return it }` in the cloud handler that the first pass missed and the compiler caught with "'return' is prohibited here".
- Not touched, because they already held the contract: `CloudFileOperationHandler.executeDelete` and both `executeRename` methods confine on their own; `SftpFileOperationHandler.executeCopy` and `.executeDelete` delegate to `super`.
- `FtpFileOperationHandler.deleteFile` was on the first list and came off it: the base `deleteFile` confines nothing, so that override loses nothing.
- `BaseFileOperationHandler` carries the invariant as a comment directly above `executeCopy` - the place an author reads before writing an override.
- Indentation is unchanged in all five bodies: a function's block and a lambda's block are the same braces, so wrapping costs two lines of comment and one changed header per site. File growth: 804 -> 808, 560 -> 562, 469 -> 471, 776 -> 778, 441 -> 445.

**Not user-visible.** Every one of the five was reached only through `FileOperationUseCase`, which confines at its own entry (lines 215 and 436), so no user could observe the difference. What changes is that the guarantee now lives where the declaration claims it does. No `ALL_FEATURES` record.

**Measured 2026-08-19:** `.\a.ps1 fk` exit 0; `detekt-scoped: PASS [app_v2] - 5 file(s), no new finding under the full configured rule set`; the file-operation unit tests green - 59 tests across 10 result files, `failures="0" errors="0"` in each, counts read from the XML; `post-change: PASS (Kotlin, 51848 ms)`.

---

## Last Audit

**Date:** 2026-08-19
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 11 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Concurrency audit (CLAUDE.md section 13 - a dispatcher change):

- **Semantics of the relabelled returns are identical.** A bare `return` inside the old function body left the function; `return@withContext` leaves the lambda that now *is* the function body. The four `?.let { return it }` cases are the same argument one level in - `let` is inline, so the original non-local return and the new labelled one both mean "this is the result".
- **No double confinement.** None of the five wrapped bodies contained a `withContext` of its own, so nothing is now nested twice; the three overrides that already confined were left alone precisely to avoid that.
- **No caller relies on the old behaviour.** The five were reachable only through `FileOperationUseCase`, which was already confining, so the change removes a dependency rather than adding one.
- **The compiler was the safety net for the transform, not an assumption.** The first pass converted only line-leading returns and failed to build with four "'return' is prohibited here" errors, which is exactly the shape a silent mistake would have taken.

No P0/P1 finding.

Evidence 2026-08-19: five wrapped headers at the lines named above; zero unlabelled `return ` left inside those bodies (the build proves it - an unlabelled one would not compile inside the lambda); the base-class comment present above `open suspend fun executeCopy`; `.\a.ps1 fk` exit 0; scoped detekt PASS on 5 files; 59 unit tests green; `post-change: PASS`; zero `Timber.d("S1813:` hits.

### Manual / on-device

- none - the change is invisible at runtime by construction, and the unit suite plus the compiler cover the transform.

## 10. Связи с другими спеками

- S1812 - трассировка вызывающих SMB, при которой находка и всплыла.
- S1810 - правило lint, которое такой разрыв ловило бы механически.
