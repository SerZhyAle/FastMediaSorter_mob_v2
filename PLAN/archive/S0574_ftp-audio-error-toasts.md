# Compact spec: S0574 - FTP audio playback loud network-error logging

**Ticket:** S0574
**Status:** Archived
**Priority:** 90
**Date:** 2026-06-21
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - находка /spec-prerelease 2026-06-21
**Complexity:** Simple (compact spec, phases inline)

> **Scope:** COMPACT. Цель + фазы в одном файле. Два продуктовых дефекта в FTP-пути сетевого аудио.

---

## Goal

При воспроизведении FTP-аудио фоновые пути (полное скачивание для прекэша + чтение ID3-метаданных) шумно падают с красными `E/`-строками логката, хотя сам стрим играет нормально (ExoPlayer достигает `STATE_READY`) и плеер тихо откатывается на прямой стриминг. Две первопричины: незащищённый NPE в active-mode fallback `FtpConnectedOperations` (после passive-таймаута retrieve идёт на null data-socket, т.к. NAT блокирует active-mode), и логирование восстановимых сетевых сбоев на уровне ERROR вместо WARN. Цель - убрать NPE и понизить уровень логов для восстановимых сетевых сбоев, чтобы воспроизведение оставалось тихим, а prerelease-log-audit не помечал эти кластеры.

**Non-goals:**

- Не чинить сам NAT/active-mode data-connection (инфраструктурное ограничение эмулятора/сети - вне контроля приложения).
- Не трогать `listFiles`/`uploadFile` пути FTP (вне находки).
- Не менять SMB/SFTP/cloud поведение, кроме согласования уровня логов восстановимого прекэш-сбоя.
- Не добавлять/не менять пользовательские строки или toast (реального UI-toast в этих путях нет).

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-21
**Захвачено во время:** /spec-prerelease sweep (emulator-5556)

**Текст:**

FTP network-audio playback surfaces red error toasts. During /spec-prerelease sweep, playing an mp3 from an FTP resource (ftp://193.178.50.43:21) streamed fine (ExoPlayer reached STATE_READY), but the background full-download + metadata read paths failed loudly and surfaced red toasts.

Evidence from temp/s0484_run_20260621_041936.log:
1. FtpConnectedOperations.downloadFile active-mode fallback throws unguarded `java.lang.NullPointerException: Attempt to invoke virtual method 'java.net.InetAddress java.net.Socket.getInetAddress()' on a null object reference` at FtpConnectedOperations.kt:284 - after a passive-mode SocketTimeoutException the code does `enterLocalActiveMode()` + `retrieveFile()` and retries on a null data socket.
2. AudioMetadataLoader.readFtpPartial -> FtpClient.readFileBytes -> FtpConnectedOperations.readFileBytes E/ errors (same NPE cause + `java.net.SocketException: Socket closed`) while reading ID3 metadata over FTP.

Caller chain: PlayerMediaLoaderManager.downloadFtpFull (PlayerMediaLoaderManager.kt:620) and AudioMetadataLoader.kt:415.

Partly emulator-NAT (the FTP passive data port is unreachable from behind the emulator NAT), but two parts are product-side defects regardless of network:
- the unguarded NPE in the active-mode fallback (no null-socket guard);
- surfacing a raw error toast to the user during otherwise-working streaming playback.

Proposed fix direction: guard the active-mode retry against a null data socket and fail cleanly; suppress/soften the user-facing toast when the stream itself keeps playing (degrade silently to no-metadata / no-prefetch).

Discovery note: found by the new prerelease-log-audit.ps1 actionable clusters; the verdict aggregator missed it because the sweep captured logcat in `-v time` while search-log.ps1 only parses `-v threadtime` (fixed in the same change set).

**Вложения:**

- Лог-выжимка FTP-ошибок (NPE + Socket closed) - `PLAN/S0574_ftp-audio-error-toasts/attachments/02__ftp_error_excerpt.log`
- Скрин сетевого аудио-воспроизведения - `PLAN/S0574_ftp-audio-error-toasts/attachments/01__network_audio_playback.png`

**Анализ кода (2026-06-21):**

- Реального UI-toast в затронутых путях нет. `AudioMetadataLoader.readFtpPartial` уже глотает исключение (returns null, Timber.w). `preCacheNetworkAudio` тихо откатывается на стриминг (returns null). `prefetchNextAudio` логирует RECOVERABLE на Timber.w. "Red error toasts" = красные `E/`-строки логката от `Timber.e`, помеченные prerelease-log-audit.
- NPE-паттерн идентичен в трёх методах `FtpConnectedOperations`: `readFileBytes` (active-fallback ~155-185), `readFileBytesRange` (~224-253), `downloadFile` (~279-298). После passive `SocketTimeoutException` -> `enterLocalActiveMode()` -> retrieve на null data-socket -> NPE без локального catch -> всплывает во внешний `catch (e: Exception)` -> `Timber.e`.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0046 (sftp-key-auth-hardening), S0496 (ftp-download-shared-connection-and-progress), S0529 (network-audio-always-continue), S0346 (audio-readiness-feedback)

---

## Phase 01 - Guard FTP active-mode fallback against null data socket

File: `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpConnectedOperations.kt`

Step 1.1 - In `readFileBytes`, add a local `catch (active: Exception)` to the active-mode fallback block (the inner `try { retrieveFileStream(active) } finally { restore passive }` opened after the passive `SocketTimeoutException`). The catch logs at WARN ("FTP active-mode data connection failed (likely NAT-blocked): <remotePath>") and returns `Result.failure(IOException("FTP active-mode data connection failed: <remotePath>", active))`. Keep the existing `finally` that restores passive mode.

- Verification: a raw `NullPointerException` from the active retrieve can no longer propagate out of `readFileBytes`; the method returns `Result.failure` with a clean `IOException`.

Step 1.2 - Apply the same local `catch (active: Exception)` guard to the active-mode fallback in `readFileBytesRange`.

- Verification: `readFileBytesRange` active fallback returns clean `Result.failure`, no raw NPE.

Step 1.3 - Apply the same local `catch (active: Exception)` guard to the active-mode fallback in `downloadFile`.

- Verification: `downloadFile` active fallback returns clean `Result.failure`, no raw NPE.

Step 1.4 - Build gate.

- Verification: `.\a.ps1 dq` (standard debug) - BUILD SUCCESSFUL.

---

## Phase 02 - Downgrade recoverable network-failure logging to WARN

Files:
- `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpConnectedOperations.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaLoaderManager.kt`

Step 2.1 - In `FtpConnectedOperations`, downgrade the outer `catch (e: IOException)` / `catch (e: Exception)` logging from `Timber.e` to `Timber.w` in the three in-scope methods: `readFileBytes`, `readFileBytesRange`, `downloadFile`. Rationale: the method returns `Result.failure`; severity is the caller's decision. A failed FTP read/download is a recoverable network condition at the data layer, not a programming error. Leave `listFiles`/`uploadFile` untouched (out of scope).

- Verification: grep the three methods - their outer catch blocks use `Timber.w`, not `Timber.e`.

Step 2.2 - In `PlayerMediaLoaderManager`, downgrade the recoverable precache/download-fallback logs from `Timber.e` to `Timber.w`: the `preCacheNetworkAudio` catch-Exception line (the `download failed for <path>` message), and the SMB/SFTP/FTP `download failed for <path>` lines inside `downloadSmbFull`/`downloadSftpFull`/`downloadFtpFull`. All of these return null and fall back to direct streaming (recoverable). Leave the `no <protocol> credentials` lines as-is (genuine config error).

- Verification: grep `preCacheNetworkAudio.*download failed` and the per-protocol `download failed` lines - all use `Timber.w`; the `no <protocol> credentials` lines remain `Timber.e`.

Step 2.3 - Build gate.

- Verification: `.\a.ps1 dq` (standard debug) - BUILD SUCCESSFUL.

---

## Acceptance criteria

1. No raw `NullPointerException` escapes the FTP active-mode fallback in any of the three read/download methods - each returns a clean `Result.failure` instead.
2. Recoverable FTP read/download failures and recoverable network-audio precache/download fallbacks log at WARN, not ERROR - so prerelease-log-audit no longer surfaces these as red `E/` actionable clusters.
3. FTP audio streaming continues to play uninterrupted; metadata/prefetch degrade silently as before.
4. `listFiles`/`uploadFile` FTP paths and SMB/SFTP/cloud success behaviour unchanged; "no credentials" config errors still log at ERROR.

---

## 10. Связи с другими спеками

- S0529 (network-audio-always-continue) - тот же принцип «стрим продолжает играть, фон деградирует тихо».
- S0496 (ftp-download-shared-connection-and-progress) - тот же FTP download-путь.
- S0346 (audio-readiness-feedback) - toast-feedback аудио (подтверждает: error-toast в этих путях не показывается).

---

## Last Audit

**Date:** 2026-06-21 (via /spec-all)
**Verdict:** Verified - all acceptance criteria confirmed by static inspection + build.

**Phase 01 - active-mode NPE guard:**

- `FtpConnectedOperations.kt` - `readFileBytes`, `readFileBytesRange`, `downloadFile` each gained a local `catch (active: Exception)` in the active-mode fallback that logs WARN and returns `Result.failure(IOException(..))`. Confirmed: 3 occurrences of "FTP active-mode data connection failed (likely NAT-blocked)". Raw `NullPointerException` from the null data socket can no longer escape these methods.

**Phase 02 - log-level downgrade:**

- `FtpConnectedOperations.kt` - outer catch of `readFileBytes`/`readFileBytesRange`/`downloadFile` now `Timber.w`. Remaining `Timber.e` are out-of-scope only (`listFiles*`, `uploadFile`, `delete*`, `rename`, `move`, `createDirectory`, and the distinct non-timeout "download error during retrieveFile" handler).
- `PlayerMediaLoaderManager.kt` - `preCacheNetworkAudio` catch-Exception and the SMB/SFTP/FTP `download failed for <path>` fallbacks now `Timber.w`. Remaining `Timber.e` are the 3 "no <protocol> credentials" config errors only.

**Build:** `.\a.ps1 dq` (standard debug) BUILD SUCCESSFUL in 41s. No `src/vr/` touched.
**Quality gate:** `assert-neuroslop.ps1` exit 0, no regressions.

**Residual / not covered:**

- On-device confirmation that an FTP-audio session behind NAT no longer emits red `E/` clusters is not strictly required (fix is purely log-level + crash-path guard; streaming already worked). Optional device sweep can confirm in a future /spec-prerelease run.
- "Red error toasts" in §0 were logcat `E/` lines, not real UI toasts (no toast exists in these paths) - so no string/UI changes were needed.
