# Стратегическая спецификация: S1811 - Две неиспользуемые функции SmbDataSource на часах

**Ticket:** S1811
**Status:** Archived
**Priority:** 35
**Date:** 2026-08-19
**Tier:** 2 - Small (ad-hoc)
**Roadmap entry:** Ad-hoc - находка при исполнении S1808, 2026-08-19

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-19

**Симптом.** `SmbDataSource` модуля часов держит две публичные suspend-функции, которых никто не вызывает:
`getFileSize(path)` и `getFileInfo(path)` вместе с вложенным `data class FileInfo`.

**Доказательство (2026-08-19).**

- Поиск по `wear/src` за пределами самого `SmbDataSource.kt`: `getFileSize` - ноль вызовов, `getFileInfo` -
  ноль вызовов. Единственные совпадения `listFiles` в других файлах - это `BrowseViewModel` (SMB) и
  чужие `client.listFiles` / `cacheDir.listFiles()`, к этим двум отношения не имеющие.
- Поиск по активным планам `PLAN/` (без `archive/`): ни один тикет не планирует их использовать. Совпадения
  есть только в S1808 и S1810, где они упомянуты как объект правки, а не как будущий потребитель.
- Это тот самый случай, который протокол аудита велит перепроверять по `PLAN/`, прежде чем считать
  нулевой охват мёртвым кодом; проверка выполнена и мёртвым кодом их считать можно.

**Сопутствующий признак низкого качества.** Обе функции выглядят написанными впрок, а не по требованию:

- `getFileInfo` определяет `isDirectory` эвристикой `fileName.startsWith("\\") && fileName.endsWith("\\")`
  с комментарием `// Heuristic`.
- Он же подставляет `modifiedTime = System.currentTimeMillis()`, то есть возвращает время вызова вместо
  времени файла.
- Он же несёт единственное предупреждение компилятора во всём файле: `'val fileName: String!' is deprecated`
  (`SmbDataSource.kt:303`). Правило 7 требует чинить предупреждения в тронутых файлах, а S1808 этот файл
  тронул - но чинить предупреждение внутри функции, которую следует удалить целиком, значит закрепить её.

**Почему это не сделано в S1808.** Тот тикет обёртывал все четыре файловые функции в `Dispatchers.IO`, и его
собственные предикаты проверки называют четыре функции поимённо. Удаление двух из них после написания
плана переписало бы предикаты под уже принятое решение - ровно тот дрейф «спека следует за кодом», который
аудит здесь и ловит.

---

---

## 0.2 Что нашлось при исследовании: у этих функций есть причина, и она - дефект

**Захвачено:** 2026-08-19

`WearMediaFile` несёт поля `size` и `dateModified`. Заполняют их все три сетевых источника - по-разному:

- FTP: `size = ftpFile.size`, `dateModified = ftpFile.timestamp?.timeInMillis ?: 0L` (`FtpDataSource.kt:65-66`).
- SFTP: `size = entry.attrs.size`, `dateModified = entry.attrs.mTime.toLong() * 1000L` (`SftpDataSource.kt:45-46`).
- SMB: **`size = 0, dateModified = 0`** - буквально константы (`BrowseViewModel.kt:181-183`).

Причина - в форме контракта: `SmbDataSource.listFiles` возвращает `Result<List<String>>`, то есть одни имена.
Внутри он делает `currentShare.list(cleanPath).map { it.fileName }` и **выбрасывает всё остальное**.

**Метаданные при этом уже пришли, в том же вызове.** `share.list(..)` из smbj отдаёт элементы, несущие
`endOfFile` и `lastWriteTime`; телефонный `SmbDirectoryScanner` читает ровно их - `fileInfo.endOfFile` и
`fileInfo.lastWriteTime.toEpochMillis()` (строки 117-118, 189-190, 288-289). Значит правильный размер стоит
ноль дополнительных обращений к сети - его надо просто не выбрасывать.

**Это видно пользователю.** `BrowseScreen.kt:189` показывает вторичной строкой под именем файла:
`file.mimeType?.startsWith("image/") == true -> formatFileSize(file.size)`, а `formatFileSize` (строка 304)
для нуля возвращает ветку `else -> "$bytes B"`. То есть на часах под каждым изображением с SMB-шары написано
**«0 B»**, тогда как тот же файл по FTP или SFTP показывает настоящий размер.

**Отсюда следует роль двух мёртвых функций.** `getFileSize` и `getFileInfo` - это ненаписанный до конца обход:
способ добрать по одному файлу то, что листинг уже приносил пачкой и терял. Их никто не вызвал, потому что
вызывать их пришлось бы по разу на файл - N обращений к сети там, где хватает нуля.

---

## 1. Проблема

Листинг SMB на часах теряет размер и время изменения, которые уже получил, и подставляет вместо них нули.
Пользователь видит «0 B» под каждым изображением с SMB-шары. Две публичные функции, существующие ради этих
же метаданных, не вызываются ниоткуда и содержат заведомо неверные значения.

---

## 2. Цели

1. Листинг SMB на часах несёт настоящие размер и время изменения - из того же вызова `share.list(..)`,
   без дополнительных обращений к сети.
2. `getFileSize`, `getFileInfo` и `FileInfo` удалены: листинг делает их ненужными.
3. В `SmbDataSource.kt` не остаётся предупреждений компилятора.

**Non-goals:**

- Приведение SMB к сигнатуре `listDirectory(source, path)` FTP и SFTP: у SMB состояние соединения живёт в
  самом источнике (`connect` отдельно от `listFiles`), и ломать эту модель ради симметрии подписи - отдельное
  решение с собственной ценой.
- `isDirectory` для записей листинга: сегодня каталоги отсеиваются тем, что у них нет mime-типа, и менять
  этот механизм тикет не просят.

---

## 3. Решение

`SmbDataSource.listFiles` начинает возвращать `Result<List<SmbEntry>>`, где `SmbEntry` - это `name`, `size`
и `modifiedTime`, прочитанные из `fileInfo.fileName`, `fileInfo.endOfFile` и
`fileInfo.lastWriteTime.toEpochMillis()`. Единственный вызывающий - `BrowseViewModel:167` - заполняет из них
`WearMediaFile`, оставляя построение `uri` там же, где оно сейчас.

`getFileSize`, `getFileInfo` и вложенный `FileInfo` удаляются: метаданные приходят с листингом, а
`getFileInfo` вдобавок возвращал время вызова вместо времени файла и определял каталог эвристикой по слэшам.
Вместе с `getFileInfo` уходит единственное предупреждение компилятора в файле.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1808 (тот же файл, симметрия диспетчеров), S1730 и S1781 (активные тикеты часов, вероятные потребители метаданных)

---

## 6. Открытые вопросы / Research items

1. **Удалять или чинить**
   - **Вопрос:** нужна ли часам метаданная файла по SMB в обозримых тикетах, или обе функции удаляются?
   - **Статус:** Resolved (2026-08-19)
   - **Ответ:** метаданные нужны - и уже нужны сегодня, а не в обозримых тикетах; но брать их надо из
     листинга, а не этими двумя функциями. Значит: чинить листинг, функции удалить.
   - **Доказательство:** `WearMediaFile` несёт `size` и `dateModified`; FTP и SFTP заполняют их настоящими
     значениями, SMB - константами `0`; `BrowseScreen.kt:189` печатает этот ноль как «0 B» под каждым
     изображением. Полный разбор - в §0.2.
   - **Почему не «оставить и вызвать»:** вызов на файл - это одно обращение к сети на каждый элемент
     листинга, тогда как `share.list(..)` приносит те же поля разом. Телефонный `SmbDirectoryScanner`
     именно так и читает их с 2026-06.

---

## 7. Фазы

### Phase 01 - SMB listing carries its metadata

**Objective:** the watch shows the real size of a file on an SMB share, taken from the listing call that already returns it, and the two callerless metadata functions are gone.

**Files Touched**

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `wear/src/main/java/com/sza/fastmediasorter/wear/data/network/smb/SmbDataSource.kt` | Modified | <= 300 |
| `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseViewModel.kt` | Modified | <= 420 |

---

#### Step 01.1 - Return the listing's metadata instead of names

**Files:** `SmbDataSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `data class SmbEntry(val name: String, val size: Long, val modifiedTime: Long)` and change `listFiles` to return `Result<List<SmbEntry>>`, building each entry from `fileInfo.fileName`, `fileInfo.endOfFile` and `fileInfo.lastWriteTime.toEpochMillis()`. Keep the `withContext(Dispatchers.IO)` confinement and the cancellation rethrow S1808 added.

**Why:**

Section 0.2 records that `share.list(..)` already delivers size and modified time in the same round trip and that `map { it.fileName }` throws them away, which is the sole reason the watch fabricates zeros; taking them from the listing costs no extra network call.

**Verification:**

- `Grep` - `data class SmbEntry` declared once.
- `Grep` - `endOfFile` and `lastWriteTime` each appear in `listFiles`.
- `Grep` - `map { it.fileName }` no longer appears in the file.

**Status:** `[x]` done

---

#### Step 01.2 - Fill the media file from the entry

**Files:** `BrowseViewModel.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the SMB branch of `loadNetworkFiles`, map each `SmbEntry` to `WearMediaFile` with `size = entry.size` and `dateModified = entry.modifiedTime`, keeping the existing `fullPath` and `uri` construction and using `entry.name` where `fileName` was used.

**Why:**

Section 0.2 records that `BrowseScreen` prints `formatFileSize(file.size)` under every image, so the constant `0` is what a watch owner reads as "0 B" on an SMB share while the same file over FTP or SFTP shows its real size.

**Verification:**

- `Grep` - `size = 0` and `dateModified = 0` no longer appear in the SMB branch.
- `Grep` - `entry.size` and `entry.modifiedTime` present.
- `pwsh -NoProfile -File ./a.ps1 fw` exits 0.

**Status:** `[x]` done

---

#### Step 01.3 - Delete the two callerless metadata functions

**Files:** `SmbDataSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Delete `getFileSize`, `getFileInfo` and the nested `data class FileInfo`. Nothing calls them, and step 01.1 makes the data they returned arrive with the listing.

**Why:**

Goal 2 and CLAUDE.md Rule 20 require orphaned code to go, and section 0.2 records that `getFileInfo` in particular returned the time of the call as the file's modified time and decided `isDirectory` by a slash heuristic - values a future caller would have trusted.

**Verification:**

- `Grep` - `getFileSize`, `getFileInfo` and `FileInfo` return zero hits across `wear/src`.
- `pwsh -NoProfile -File ./a.ps1 fw` exits 0 and prints no compiler warning for this file.

**Status:** `[x]` done

---

**Phase Done Criteria**

- [x] Every `Step 01.*` above is `[x]` done.
- [x] `pwsh -NoProfile -File ./a.ps1 fw` and `fwu` each exit 0.
- [x] `post-change.ps1` closes with `post-change: PASS`.

---

## 8. Implementation State

**2026-08-19 - Phase 01 done.**

- `SmbDataSource.listFiles` returns `Result<List<SmbEntry>>`; each entry is built from `fileInfo.fileName`, `fileInfo.endOfFile` and `fileInfo.lastWriteTime.toEpochMillis()` - the same three fields the phone's `SmbDirectoryScanner` has read from this call since June. The `withContext(Dispatchers.IO)` confinement and the cancellation rethrow that S1808 added earlier today are preserved.
- `BrowseViewModel` fills `size` and `dateModified` from the entry instead of the constants `0, 0`. The `fullPath` and `uri` construction is untouched.
- `getFileSize`, `getFileInfo` and the nested `data class FileInfo` are gone - 101 lines. With them went the file's only compiler warning (`'val fileName: String!' is deprecated`), `getFileInfo`'s `modifiedTime = System.currentTimeMillis()` (the time of the call, presented as the file's) and its `isDirectory` slash heuristic.
- `SmbDataSource.kt` 348 -> 247 lines.

**User-visible.** On a watch browsing an SMB share, every image used to read "0 B" under its name while the same file over FTP or SFTP showed its real size. This is a fix of a field that was already displayed, not a new capability, so it takes no `ALL_FEATURES` record - the dev-log row and the commit carry it.

**Measured 2026-08-19:** `.\a.ps1 fw` exit 0 with zero warnings, `.\a.ps1 fwu` exit 0 with `assert-test-suite-complete: PASS`, `post-change: PASS (Kotlin, 19490 ms)`. Zero residual references to `getFileSize` / `getFileInfo` across `wear/src`; zero `size = 0` in the SMB branch; zero `map { it.fileName }` in the data source.

## 10. Связи с другими спеками

- S1808 - тронул этот файл и обнаружил нулевой охват; сознательно оставил удаление сюда.
- S1730, S1781 - активные тикеты часов, единственные вероятные будущие потребители метаданных.

---

## Last Audit

**Date:** 2026-08-19
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

Dead-weight check (CLAUDE.md Rule 20): the deletion was cross-checked against `PLAN/` before it was made - no active ticket named `getFileSize` or `getFileInfo` as a future consumer, and the only mentions were this spec's own and S1810's. Nothing else in `wear/src` references them now.

Evidence 2026-08-19: `data class SmbEntry` declared once; `endOfFile` and `lastWriteTime` both read inside `listFiles`; zero `map { it.fileName }` left in the data source; zero `size = 0` in the SMB branch of `BrowseViewModel` and both `entry.size` and `entry.modifiedTime` present; zero references to `getFileSize` / `getFileInfo` / `FileInfo` across `wear/src`; `SmbDataSource.kt` 247 lines against a 300 budget and `BrowseViewModel.kt` 283 against 420; `.\a.ps1 fw` exit 0 with **zero compiler warnings** (the deprecated `fileName` warning left with `getFileInfo`); `.\a.ps1 fwu` exit 0; `post-change: PASS (Kotlin)` with scoped detekt PASS; `check-open-items-carried.ps1 -Id S1811` exit 0; zero `Timber.d("S1811:` hits.

### Manual / on-device

- [ ] Browse an SMB share from the watch and confirm an image now shows its real size instead of "0 B". Not run here: the attached device is the phone, and this path needs the watch on the same network as a real SMB server. The mechanism itself is proven statically - the phone's `SmbDirectoryScanner` has read `endOfFile` and `lastWriteTime.toEpochMillis()` from the same `DiskShare.list(..)` call in production since June - so this is a confirmation, not an open risk.
