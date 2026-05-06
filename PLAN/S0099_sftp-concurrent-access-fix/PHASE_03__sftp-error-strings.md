# Phase 03 — sftp-error-strings

**Strategic spec:** [`../S0099_sftp-concurrent-access-fix.md`](../S0099_sftp-concurrent-access-fix.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Add three user-facing SFTP error string resources (EN/RU/UK) and wire `SftpDownloadExhaustedException` into `SftpFileOperationHandler.copyFile()` so that the bridge-copy error path produces a localized message instead of the raw internal exception text.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`SftpDownloadExhaustedException` available at `data/remote/sftp/`).
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt` | Modified | ≤ 420 |

---

## Steps

### Step 03.1 — Add SFTP error strings to all three locale files

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`
**Depends on:** — start of phase

**Prompt for developer:**

> In each of the three `strings.xml` files, append the following three keys in the section containing other `error_network_*` strings (near `error_network_generic`).
>
> **`values/strings.xml`** (English):
> ```xml
>     <string name="error_sftp_copy_failed_server">Could not copy file from %1$s. Check connection and try again.</string>
>     <string name="error_sftp_copy_access_denied">No access to file on SFTP server. Check permissions.</string>
>     <string name="error_sftp_connection_limit">Server %1$s limits concurrent connections. Stop playback and try again.</string>
> ```
>
> **`values-ru/strings.xml`** (Russian):
> ```xml
>     <string name="error_sftp_copy_failed_server">Не удалось скопировать файл с %1$s. Проверьте соединение и попробуйте снова.</string>
>     <string name="error_sftp_copy_access_denied">Нет доступа к файлу на SFTP-сервере. Проверьте права доступа.</string>
>     <string name="error_sftp_connection_limit">Сервер %1$s ограничивает количество соединений. Остановите воспроизведение и попробуйте снова.</string>
> ```
>
> **`values-uk/strings.xml`** (Ukrainian):
> ```xml
>     <string name="error_sftp_copy_failed_server">Не вдалося скопіювати файл з %1$s. Перевірте з'єднання та спробуйте ще раз.</string>
>     <string name="error_sftp_copy_access_denied">Немає доступу до файлу на SFTP-сервері. Перевірте права доступу.</string>
>     <string name="error_sftp_connection_limit">Сервер %1$s обмежує кількість з'єднань. Зупиніть відтворення та спробуйте ще раз.</string>
> ```

**Verification:**

- `Grep` — `error_sftp_copy_failed_server` present in `values/strings.xml`.
- `Grep` — `error_sftp_copy_failed_server` present in `values-ru/strings.xml`.
- `Grep` — `error_sftp_copy_failed_server` present in `values-uk/strings.xml`.
- `Grep` — `error_sftp_connection_limit` present in `values-uk/strings.xml`.

**Status:** `[ ]` not done

---

### Step 03.2 — Wire `SftpDownloadExhaustedException` into bridge-copy error path

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SftpFileOperationHandler.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `SftpFileOperationHandler.kt`, add the following import after the existing imports:
> ```kotlin
> import com.sza.fastmediasorter.data.remote.sftp.SftpDownloadExhaustedException
> import com.sza.fastmediasorter.R
> ```
>
> In `copyFile()`, find the bridge-copy download block (lines ~246–255). Replace:
> ```kotlin
>                      // 1. Download to temp
>                      val downloadSuccess = if (sourcePath.startsWith("sftp:", ignoreCase = true)) {
>                          sftpStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null).isSuccess
>                      } else if (sourcePath.startsWith("smb:", ignoreCase = true)) {
>                          smbStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null).isSuccess
>                      } else {
>                          ftpStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null).isSuccess
>                      }
>                      
>                      if (!downloadSuccess) return Result.failure(Exception("Failed to download source for bridge copy"))
> ```
> with:
> ```kotlin
>                      // 1. Download to temp
>                      val downloadResult: Result<String> = if (sourcePath.startsWith("sftp:", ignoreCase = true)) {
>                          sftpStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null)
>                      } else if (sourcePath.startsWith("smb:", ignoreCase = true)) {
>                          smbStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null)
>                      } else {
>                          ftpStrategy.copyFile(sourcePath, tempFile.absolutePath, true, null)
>                      }
>
>                      if (downloadResult.isFailure) {
>                          val cause = downloadResult.exceptionOrNull()
>                          val host = runCatching {
>                              java.net.URI(sourcePath).host ?: sourcePath
>                          }.getOrElse { sourcePath }
>                          val msg = when {
>                              cause is SftpDownloadExhaustedException ->
>                                  context.getString(R.string.error_sftp_copy_failed_server, host)
>                              cause is com.jcraft.jsch.JSchException ->
>                                  context.getString(R.string.error_sftp_connection_limit, host)
>                              cause?.message?.contains("permission denied", ignoreCase = true) == true ||
>                              cause?.message?.contains("access denied", ignoreCase = true) == true ->
>                                  context.getString(R.string.error_sftp_copy_access_denied)
>                              sourcePath.startsWith("sftp:", ignoreCase = true) ->
>                                  context.getString(R.string.error_sftp_copy_failed_server, host)
>                              else -> "Failed to download source for bridge copy"
>                          }
>                          return Result.failure(Exception(msg, cause))
>                      }
> ```

**Verification:**

- `Grep` — `import com.sza.fastmediasorter.data.remote.sftp.SftpDownloadExhaustedException` present in `SftpFileOperationHandler.kt`.
- `Grep` — `downloadResult.isFailure` present in `SftpFileOperationHandler.kt`.
- `Grep` — `error_sftp_copy_failed_server` present in `SftpFileOperationHandler.kt`.
- `Grep` — `Failed to download source for bridge copy` — 0 matches (old hardcoded message removed).

**Status:** `[ ]` not done

---

### Step 03.3 — Build

**Files:** —
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `/build` (debug, any flavor). Build must succeed with zero errors.

**Verification:**

- Build exits with code 0.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every step above is `[x] done`.
- [ ] Project compiles — `/build` exits 0.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] String locale audit passes: `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "error_sftp_"` exits 0.
- [ ] Dev log entries added for all modified files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

`SftpFileOperationHandler` now surfaces localized errors from `SftpDownloadExhaustedException`. Proceed to Phase 04 (docs/catalog cleanup).

---

## Rollback Plan

Revert phase commits. Remove the three string keys. No data migration — no schema change.
