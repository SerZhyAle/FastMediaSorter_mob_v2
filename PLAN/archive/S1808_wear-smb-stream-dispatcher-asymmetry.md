# Стратегическая спецификация: S1808 - SmbDataSource.getFileStream не переключает диспетчер

**Ticket:** S1808
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-19
**Tier:** 2 - Small (ad-hoc)
**Roadmap entry:** Ad-hoc - находка аудита фазы 01 тикета S1730, 2026-08-19

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-19

**Источник:** обязательный аудит на границе фазы, S1730 фаза 01.

**Симптом:**

Из трёх сетевых источников часов два - `FtpDataSource.getFileStream` и `SftpDataSource.getFileStream` -
оборачивают работу в `withContext(Dispatchers.IO)` сами. Третий, `SmbDataSource.getFileStream`, этого
не делает: `ensureConnected()` внутри него переключается, а сам вызов `share.openFile(..)` выполняется
на том диспетчере, который передал вызывающий.

**Доказательство:**

- `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/ftp/FtpDataSource.kt:53` - `withContext(Dispatchers.IO)`.
- `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/sftp/SftpDataSource.kt:59` - `withContext(Dispatchers.IO)`.
- `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/smb/SmbDataSource.kt:166` - обёртки нет;
  `withContext` в этом файле встречается на строках 45, 86 и 115, то есть в `connect`, `ensureConnected`
  и `disconnect`, но не в `getFileStream`.

**Почему это опасно:**

Сегодня это не проявляется только потому, что `BrowseViewModel.loadNetworkFiles` сам оборачивает весь
свой блок в `withContext(Dispatchers.IO)`. То есть безопасность держится на дисциплине каждого
вызывающего, а не на самом источнике данных. Любой новый вызывающий, стартующий из `viewModelScope`,
получает блокирующий сетевой вызов на главном потоке - то есть ANR. Именно это и произошло при
написании S1730: код был написан по образцу FTP и SFTP и оказался бы неверен только для SMB.

**Что проверить при исполнении:**

- Все вызывающие `SmbDataSource.getFileStream` - не полагается ли кто-то на то, что переключения нет.
- Симметрия трёх источников как отдельное свойство: три реализации одного контракта не должны
  расходиться в том, кто отвечает за диспетчер.
- Стоит ли это механической проверки, раз расхождение уже один раз стоило дефекта.

**Обход, применённый в S1730:**

`WearThumbnailRepositoryImpl.networkThumbnail` claims `Dispatchers.IO` сам и не доверяет источнику.
Обход снимать только вместе с исправлением здесь.

---

## 1. Проблема

Три реализации одного контракта расходятся в том, кто отвечает за диспетчер. FTP и SFTP переключаются сами в каждой публичной suspend-функции; SMB переключается только в `connect`, `ensureConnected` и `disconnect`, а четыре функции работы с файлами - нет. Безопасность держится на дисциплине каждого вызывающего, а не на самом источнике данных.

**Расхождение шире, чем описано в §0.** Обёртки нет не у одной функции, а у всех четырёх файловых: `listFiles` (строка 136), `getFileStream` (166), `getFileSize` (222), `getFileInfo` (264). У FTP и SFTP обёрнута каждая публичная suspend-функция.

---

## 2. Цели

1. Каждая публичная suspend-функция `SmbDataSource` сама переключается на `Dispatchers.IO`, как это уже делают `FtpDataSource` и `SftpDataSource`.
2. Комментарий, объясняющий защитную обёртку в `WearThumbnailRepositoryImpl`, перестаёт ссылаться на недоверие к источнику, потому что причины больше нет.

**Non-goals:**

- Снятие самой обёртки `withContext(Dispatchers.IO)` в `WearThumbnailRepositoryImpl.networkThumbnail`: она охватывает ещё и `previewReader.read(..)` - чтение потока и разбор EXIF, которым IO нужен независимо от источника. Снять надо не обёртку, а её обоснование.
- Механическая проверка симметрии - см. §6.

---

## 3. Решение

Обернуть тело каждой из четырёх функций в `withContext(Dispatchers.IO)`, превратив ранние `return` в `return@withContext`. Вложенный вызов `ensureConnected()`, который переключается сам, при этом становится no-op переключением.

Одновременно добавить `catch (e: CancellationException) { throw e }` первым плечом в каждый широкий `catch (e: Exception)`, попавший внутрь корутины: gate `swallowed-cancellation` считает такой catch новым по факту попадания в корутинный код, и без этого плеча отмена подавляется.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1730 (нашёл дефект, несёт обёртку), S1687 (другой дефект того же SMB-пути), S1304 (владеет закрытием SMB-хендла в `getFileStream`)

---

## 6. Открытые вопросы / Research items

1. **Стоит ли расхождение механической проверки**
   - **Вопрос:** нужна ли проверка, что публичная suspend-функция сетевого источника сама переключает диспетчер?
   - **Статус:** Resolved (2026-08-19)
   - **Ответ:** да, и её место - модуль `lint-rules`, а не новый grep-гейт в `scripts/quality`.
   - **Измерение (2026-08-19):** модуль `lint-rules` уже подключён к обоим модулям (`wear/build.gradle.kts:163`, `app_v2/build.gradle.kts:1455`) и уже содержит `MainThreadIoDetector`. Этот детектор не покрывает случай: он срабатывает на вызовах `kotlin.io`/`java.io.File` внутри классов, чьё имя оканчивается на `ViewModel`/`Activity`/`Fragment`/`View`, а здесь блокирующий вызов - сетевой (`smbj`), и класс - `DataSource`.
   - **Почему это не решается grep-гейтом:** признак «функция сама переключает диспетчер» требует разбора тела функции и учёта делегирования в приватный помощник, который переключается за неё - ровно та ошибка, которую `MainThreadIoDetector` уже один раз допустил и чинил в S1195. Текстовый гейт повторит её.
   - **Carrier: S1810** - расширение детектора на сетевые источники данных вынесено в отдельный тикет: это новый детектор со своим тестом в `CustomLintRulesTest`, а не правка внутри этого.

---

## 7. Фазы

### Phase 01 - SMB dispatcher symmetry

**Objective:** all four file-facing suspend functions of `SmbDataSource` confine themselves to `Dispatchers.IO`, and the thumbnail repository's comment stops citing a distrust that no longer applies.

**Files Touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/smb/SmbDataSource.kt` | Modified | <= 340 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/thumbnail/WearThumbnailRepositoryImpl.kt` | Modified | <= 160 |

---

#### Step 01.1 - Confine the four SMB file functions to `Dispatchers.IO`

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/smb/SmbDataSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Wrap the bodies of `listFiles`, `getFileStream`, `getFileSize` and `getFileInfo` in `withContext(Dispatchers.IO)`, turning each early `return` into `return@withContext`. Leave `connect`, `ensureConnected` and `disconnect` alone - they already switch.

**Why:**

Section 0 records that the safety of these four calls rests on every caller wrapping them, so a new caller started from `viewModelScope` gets a blocking network call on the main thread; goal 1 makes the source responsible for its own confinement, as `FtpDataSource` and `SftpDataSource` already are.

**Verification:**

- `Grep` - `withContext(Dispatchers.IO)` appears on the body line of all four functions.
- `Grep` - the file contains zero `return Result.failure` at function top level outside a `withContext` lambda.
- `pwsh -NoProfile -File ./a.ps1 fw` exits 0.

**Status:** `[x]` done

---

#### Step 01.2 - Rethrow cancellation from every catch now inside the coroutine

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/smb/SmbDataSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `catch (e: CancellationException) { throw e }` as the first arm of every broad `catch (e: Exception)` that step 01.1 moved inside a coroutine, including the inner one in `getFileInfo` that defaults the size to `0L`. Import `kotlinx.coroutines.CancellationException`.

**Why:**

A broad catch that has just entered coroutine code swallows cancellation, and the `swallowed-cancellation` dimension of `assert-neuroslop.ps1` scores exactly that shape as a new occurrence - so this arm is both correct on its merits and required for the change to close.

**Verification:**

- `Grep` - `import kotlinx.coroutines.CancellationException` present once.
- `Grep` - `catch (e: CancellationException)` appears five times.
- `pwsh -NoProfile -File ./a.ps1 fw` exits 0.

**Status:** `[x]` done

---

#### Step 01.3 - Restate the thumbnail repository's IO comment

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/data/thumbnail/WearThumbnailRepositoryImpl.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Rewrite the KDoc above `networkThumbnail` so it names the real reason the IO context is claimed there - `previewReader.read(..)` reads the stream and decodes EXIF on this thread - and drops the claim that the SMB source does not switch. Keep the `withContext` itself.

**Why:**

Goal 2 requires the comment to stop citing a distrust that step 01.1 removes; per CLAUDE.md Rule 8 an existing comment is a requirement, and a comment left asserting a fixed defect misdirects the next reader into re-adding a guard.

**Verification:**

- `Grep` - the KDoc no longer contains `runs on whatever dispatcher it is handed`.
- `Grep` - `withContext(Dispatchers.IO)` still present in `networkThumbnail`.
- `pwsh -NoProfile -File ./a.ps1 fw` exits 0.

**Status:** `[x]` done

---

**Phase Done Criteria**

- [x] Every `Step 01.*` above is `[x]` done.
- [x] `pwsh -NoProfile -File ./a.ps1 fw` exits 0 and `pwsh -NoProfile -File ./a.ps1 fwu` exits 0.
- [x] `post-change.ps1` closes with `post-change: PASS`.

---

---

## 8. Implementation State

**2026-08-19 - Phase 01 done.**

- `SmbDataSource.kt`: `listFiles`, `getFileStream`, `getFileSize` and `getFileInfo` are now expression bodies over `withContext(Dispatchers.IO)`; each early `return` became `return@withContext`. `connect`, `ensureConnected` and `disconnect` were already confined and are untouched.
- Five broad catches ended up inside coroutine code and each gained `catch (e: CancellationException) { throw e }` as its first arm - the four outer ones plus the inner size lookup in `getFileInfo`, which also gained the `Timber.w` its silent `0L` default was missing.
- `WearThumbnailRepositoryImpl.networkThumbnail`: the `withContext(Dispatchers.IO)` stays, because it also covers `previewReader.read(..)`; its KDoc now names that as the reason instead of citing distrust of the SMB source.

**No user-visible impact.** Nothing about the app's behaviour changes: the one live caller path already ran on `Dispatchers.IO` from the caller's side. The change moves the responsibility, so the next caller cannot inherit the defect. There is no `ALL_FEATURES` record to write.

**Measured 2026-08-19:** `.\a.ps1 fw` exit 0, `.\a.ps1 fwu` exit 0 with `assert-test-suite-complete: PASS` (20 reports / 20 `*Test.kt`), `post-change: PASS (Kotlin, 20055 ms)` with `assert-detekt: PASS [scoped]` and the neuroslop ratchet green - the cancellation arms are what kept `swallowed-cancellation` at zero delta.

**Parked during this ticket:** S1811 - `getFileSize` and `getFileInfo` have zero callers in `wear/src` and no active plan names them; the deprecation warning at `SmbDataSource.kt:303` lives inside one of them, so fixing it here would have entrenched code that should be removed.

## 10. Связи с другими спеками

- S1730 (`wear-resource-grid-view`) - тикет, чей аудит нашёл это; несёт защитную обёртку до исправления.
- S1687 (`bugfix-wear-network-playback-always-smb`) - другой дефект того же SMB-пути, не путать.

---

## Last Audit

**Date:** 2026-08-19
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

Concurrency audit (CLAUDE.md section 13 trigger - a dispatcher change): all three live call sites already ran inside `withContext(Dispatchers.IO)` of their own (`BrowseViewModel.loadNetworkFiles`, `WearThumbnailRepositoryImpl.networkThumbnail`, `DownloadNetworkFileUseCase.invoke`), so no caller loses a dispatcher it relied on and the nested switch inside `ensureConnected` is a no-op rather than a hazard. The mutable `share` field is read from the same context it was already read from in practice, so no new race is introduced. Cancellation now propagates out of the four functions instead of being folded into `Result.failure`, which is the correct behaviour for a cancelled coroutine and is what every caller's `getOrNull` / `getOrThrow` expects. No P0/P1 finding.

Evidence 2026-08-19: four `= withContext(Dispatchers.IO)` bodies at lines 137/171/231/277; zero top-level `return Result.failure` outside a `withContext` lambda; `import kotlinx.coroutines.CancellationException` once and `catch (e: CancellationException)` five times; the stale KDoc phrase gone from `WearThumbnailRepositoryImpl` while its two `withContext(Dispatchers.IO)` remain; `SmbDataSource.kt` 328 lines against a 340 budget and `WearThumbnailRepositoryImpl.kt` 125 against 160; `.\a.ps1 fw` exit 0; `.\a.ps1 fwu` exit 0; `post-change: PASS (Kotlin)` with scoped detekt PASS; zero `Timber.d("S1808:` hits; `check-open-items-carried.ps1 -Id S1808` exit 0.

### Manual / on-device

- none - the change is a dispatcher confinement with no user-visible surface, and the wear unit suite plus the compile are the whole contract.

**Parked during this ticket:** S1810 (carrier - lint detector for network data source dispatchers), S1811 (`getFileSize` / `getFileInfo` have no callers).
