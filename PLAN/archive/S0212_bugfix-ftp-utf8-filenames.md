# S0212 — FTP UTF-8 Filename Encoding Fix

- **Ticket:** S0212
- **Name:** bugfix-ftp-utf8-filenames
- **Type:** Bugfix
- **Status:** Verified
- **Priority:** 75
- **Roadmap:** ad-hoc
- **Created:** 2026-05-15

<!-- auto-approved by /spec-all — 2026-05-15 -->

## Goal

Копирование, перемещение и удаление файлов с не-ASCII именами по FTP (например, `08-Тёмная Ночь.mp3`) падает с ошибкой `550 The filename, directory name, or volume label syntax is incorrect`. Причина — `controlEncoding = "UTF-8"` устанавливается после `client.connect()`, тогда как Apache Commons Net инициализирует `_controlInput_`/`_controlOutput_` Reader/Writer внутри `_connectAction_()` в момент connect — последующая переустановка кодировки не пересоздаёт уже открытые stream-ы. Дополнительно RFC 2640 серверы (Microsoft IIS FTP, FileZilla Server на Windows) по умолчанию интерпретируют байты имени файла как ANSI/OEM до явного `OPTS UTF8 ON`.

Фикс — два независимых изменения в каждой точке создания `FTPClient`: установить `controlEncoding` **до** `connect()`, и после успешного `login()` отправить best-effort `OPTS UTF8 ON` (игнорируя отказ старых серверов). Изменения охватывают `FtpClient.kt`, `FtpStandaloneOperations.kt`, `FtpExoPlayerPool.kt` — все 6 мест с одинаковым паттерном.

## Scope

- `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpClient.kt` — `connect()`.
- `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpStandaloneOperations.kt` — `testConnection()`, `uploadFile()`, `openInputStream()`, `executeWithNewConnection()`.
- `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpExoPlayerPool.kt` — `getConnectionForExoPlayer()`.

Out of scope:
- SFTP (SSHJ already negotiates UTF-8 by default; SFTP path works in the log).
- WebDAV / SMB / cloud (use platform encoding).
- Auto-detection of server filename encoding (we always assume UTF-8 — RFC 2640 compliance, matches all modern Windows/Linux FTP servers post-2010).

## Phases

### Phase 1 — Move controlEncoding before connect + add OPTS UTF8 ON

#### Step 1.1 — Extract helper for UTF-8 setup

- Add `private fun FTPClient.applyUtf8Encoding()` to `FtpStandaloneOperations` companion (or a shared `FtpEncodingSupport` object if cleaner) that sets `controlEncoding = "UTF-8"` on the receiver. Caller is responsible for invoking this **before** `connect()`.
- Add `private fun FTPClient.enableUtf8Mode()` that sends `sendCommand("OPTS", "UTF8 ON")` and logs `Timber.d("FTP: OPTS UTF8 ON reply=${replyCode}")`. The command may fail on legacy servers; do not throw or return failure — non-RFC-2640 servers still work with bare UTF-8 bytes once the client encoding is correct.

**Verification:**
- Grep `fun .*applyUtf8Encoding\|fun .*enableUtf8Mode` in `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/` returns ≥ 2 hits.
- File compiles (per phase build).

#### Step 1.2 — FtpClient.connect() ordering

- In `FtpClient.kt`, inside `suspend fun connect(...)`:
  - Move `client.controlEncoding = "UTF-8"` (currently line ~97) to **before** `client.connect(host, port)`. Call site: right after the timeout setters, before line 83.
  - After `client.login(...)` succeeds and after `client.enterLocalPassiveMode()`, call `client.enableUtf8Mode()`.
- Keep `client.setFileType(FTP.BINARY_FILE_TYPE)` unchanged.

**Verification:**
- Read `FtpClient.kt` lines 73–110. Expected: `controlEncoding = "UTF-8"` appears once and on a line numerically less than the `client.connect(host, port)` line. Actual line numbers recorded in `## Last Audit`.
- Grep `client.enableUtf8Mode\(\)` in `FtpClient.kt` returns exactly 1 hit.

#### Step 1.3 — FtpStandaloneOperations: 4 connect sites

- Inside `testConnection(...)`, `uploadFile(...)`, `openInputStream(...)`, `executeWithNewConnection(...)`:
  - Move `tempClient.controlEncoding = "UTF-8"` (currently after `enterLocalPassiveMode()` / `setFileType()`) to **inside the `apply { applyTimeouts(); ... }`** block at construction time, or as a separate line immediately after `FTPClient().apply { applyTimeouts() }`, before `connect()`.
  - After `login()` succeeds, call `tempClient.enableUtf8Mode()`.
- Remove the now-duplicate post-connect `controlEncoding = "UTF-8"` lines.

**Verification:**
- Grep `controlEncoding = "UTF-8"` in `FtpStandaloneOperations.kt` returns 4 hits (one per connect site) and **none** of them appear after a `connect(host, port)` call on the same client variable within the same function — confirmed by reading each function block.
- Grep `enableUtf8Mode\(\)` in `FtpStandaloneOperations.kt` returns 4 hits.

#### Step 1.4 — FtpExoPlayerPool.getConnectionForExoPlayer ordering

- Move `client.controlEncoding = "UTF-8"` (currently line ~86) to **before** `client.connect(connectionInfo.host, connectionInfo.port)` (line ~71). Place it after the timeout setters block, before connect.
- After `client.login(...)` succeeds and after `client.enterLocalPassiveMode()`, call `client.enableUtf8Mode()`.

**Verification:**
- Read `FtpExoPlayerPool.kt` lines 57–100. Expected: `controlEncoding` line precedes the `connect(...)` line.
- Grep `enableUtf8Mode\(\)` in `FtpExoPlayerPool.kt` returns exactly 1 hit.

#### Step 1.5 — Module build gate

- Run `/build` → `standard debug`. Expected: BUILD SUCCESSFUL.
- If build fails: capture compiler error, attempt minimal fix (typically import or signature mismatch on the helper), retry. Max 3 retries.

**Verification:**
- Exit code 0 from gradle build.
- No new warnings in `FtpClient.kt`, `FtpStandaloneOperations.kt`, `FtpExoPlayerPool.kt`.

### Phase 2 — Catalogue sync + dev log

#### Step 2.1 — Catalogue sync for app_v2

- Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- Run `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`.

**Verification:**
- Exit code 0 from both scripts.

#### Step 2.2 — Dev changelog entry

- Run `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/" "FTP UTF-8 fix" "S0212: move controlEncoding before connect + OPTS UTF8 ON post-login (fixes 550 on non-ASCII filenames)"`.

**Verification:**
- Tail of `dev/CHANGELOG.md` shows the new entry with current branch and timestamp.

#### Step 2.3 — Functionality log entry (FIX)

- Run `.\scripts\add_to_functionality_log.ps1 -Id S0212 -Op FIX -Description "FTP copy/move/delete now works for filenames with non-ASCII characters (Cyrillic, accented letters) — clients/servers negotiate UTF-8 control encoding correctly"`.

**Verification:**
- Tail of `dev/FUNCTIONALITY.log` contains an entry with id `S0212` and op `FIX`.

### Phase 3 — On-device verification (manual gate)

#### Step 3.1 — Insert debug verification tags

- In `FtpStandaloneOperations.uploadFile()` immediately before the `storeFile()` call: `Timber.d("S0212: FTP upload remotePath=$remotePath encoding=${tempClient.controlEncoding}")`.
- In `FtpStandaloneOperations.deleteFile()` block (inside `executeWithNewConnection`) immediately before `client.deleteFile(remotePath)`: `Timber.d("S0212: FTP delete remotePath=$remotePath encoding=${client.controlEncoding}")`.
- In `FtpStandaloneOperations.renameFile()` block immediately before `client.rename(oldPath, newPath)`: `Timber.d("S0212: FTP rename $oldPath -> $newPath encoding=${client.controlEncoding}")`.

**Verification:**
- Grep `Timber.d\("S0212:` in `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/` returns exactly 3 hits.

#### Step 3.2 — Status transition to BlockNeedUserTest

- Run `pwsh -File scripts/spec_catalog/update.ps1 -Id S0212 -Status BlockNeedUserTest`.

**Verification:**
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0212 -Format json` returns `"status":"BlockNeedUserTest"`.

#### Step 3.3 — Operator test plan (handed to user)

User reproduces the original failing scenario:
- Source resource: SFTP `sftp://193.178.50.43:22/data/`
- Destination resource: FTP `ftp://193.178.50.43:21/`
- Action: copy `08-Тёмная Ночь.mp3` from SFTP to FTP.
- Expected outcome in `logs/current.log`:
  - `S0212: FTP upload remotePath=...` line appears.
  - No `FAILURE {error=Could not copy the selected files...550...}` block.
  - File visible on FTP server with correct filename.
- Repeat with delete (delete the just-copied file) and rename (rename `08-Тёмная Ночь.mp3` → `тест.mp3`).

**Verification:** all three operations succeed, user-visible toast/dialog reports success, no SLog `[file-operation-sync] FAILURE` for these files.

## Notes / risks

- **Legacy server fallback:** `OPTS UTF8 ON` is best-effort. Servers without RFC 2640 support (some embedded NAS firmware, very old vsftpd builds) respond with 500/502 — `sendCommand` returns the reply code without throwing, we log and continue. The pre-connect `controlEncoding = "UTF-8"` is sufficient for any server that decodes filename bytes as UTF-8 by default.
- **`apply { applyTimeouts() }` placement:** keeping the encoding setter outside the apply block (as a separate line) is acceptable; the spec does not mandate either form as long as the call happens before `connect()`.
- **Existing connections:** `FtpClient.ftpClient` field is held for the lifetime of the resource. Existing pooled connections established before this fix will retain the wrong encoding until next `disconnect()/connect()` cycle. Not in scope to invalidate — naturally cycles on next session.

## Last Audit

**Date:** 2026-05-15
**Mode:** strategic (compact spec — Simple path)
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Reproduce original failing scenario: copy `08-Тёмная Ночь.mp3` from SFTP to FTP — expect success, no `550 The filename, directory name…` in logs.
- [ ] Repeat with delete and rename operations on a Cyrillic-named file.
- [ ] Optional: verify against a legacy non-RFC-2640 FTP server — `OPTS UTF8 ON` should log a 500/502 reply but operations on Cyrillic names still succeed via the pre-connect encoding fix alone.
