# S0583 - streams-catalog-import-timeout

**Status:** Archived
**Priority:** 40
**Date:** 2026-06-21
**Tier:** 2 - Easy

<!-- auto-approved by /spec-all - 2026-06-21 -->

## 0. Raw capture

Parked during S0580 research (out-of-scope finding).

`ImportStreamCatalogUseCase.downloadCsv()` (`app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ImportStreamCatalogUseCase.kt`) fetches the catalog zip from a GitHub release with the shared `OkHttpClient` and whatever the app-wide timeout is. A stalled connection blocks the `Dispatchers.IO` coroutine until the global timeout fires; the UI shows the importing spinner the whole time.

Add an explicit per-call timeout (connect/read/write) and/or coroutine `withTimeout` to the catalog download so a slow/dead host fails fast with the existing `CatalogImportResult.Failure` path.

Independent of S0580 (filter feature does not touch the import network path).

## Goal

Импорт каталога стримов должен падать быстро при мёртвом или медленно-капающем хосте, а не висеть со спиннером. Общий `OkHttpClient` задаёт connect/read/write по 10s, но **не** задаёт `callTimeout`, поэтому хост, отдающий данные по байту с интервалом < 10s, сбрасывает read-таймаут на каждом чтении и может держать соединение неограниченно долго. Добавляем дериватный клиент с жёстким `callTimeout` - общим дедлайном на весь вызов (DNS + connect + write + чтение тела zip). Таймаут конвертируется в `InterruptedIOException`, который уже ловит существующий `catch (e: Exception)` и возвращает `CatalogImportResult.Failure`, так что UI-путь не меняется.

## Phase 01 - Cap the catalog download with a hard call timeout

- [x] Derive a per-call client inside `ImportStreamCatalogUseCase.downloadCsv()` from the injected `okHttpClient` via `okHttpClient.newBuilder().callTimeout(CATALOG_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()`, and issue `newCall(request)` on it instead of on the shared client. Inheriting from `newBuilder()` keeps the shared connection pool, dispatcher and the existing 10s connect/read/write timeouts; the added `callTimeout` is the overall ceiling the shared client lacks.
  - Verification: `downloadCsv()` no longer calls `okHttpClient.newCall(..)` directly; it calls `newCall(..)` on the `newBuilder().callTimeout(..)`-derived client. (PASS)
- [x] Add `const val CATALOG_CALL_TIMEOUT_SECONDS = 30L` to the private companion object next to `CATALOG_URL` / `MAX_CSV_BYTES`. 30s mirrors the download-client ceiling in `di/LinkDownloadModule.kt` and is generous for a small catalog zip.
  - Verification: companion object declares `CATALOG_CALL_TIMEOUT_SECONDS`; value referenced exactly once in `downloadCsv()`. (PASS)
- [x] Add `import java.util.concurrent.TimeUnit` to the import block.
  - Verification: import present; no unresolved-symbol on `TimeUnit`. (PASS)
- [x] Confirm the timeout failure reaches the existing failure path: `callTimeout` throws `java.io.InterruptedIOException` ("timeout"), a subtype of `IOException`/`Exception`, caught by the `try { downloadCsv() } catch (e: Exception)` block in `invoke()`, returning `CatalogImportResult.Failure`. No new catch / result type needed.
  - Verification: no change to the `invoke()` try/catch or `CatalogImportResult` sealed interface; failure mapping already covers the timeout. (PASS)
  - Verification: `.\a.ps1 fk` (Kotlin compile) exits 0. (PASS - auto-build)

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0570 (introduced `ImportStreamCatalogUseCase`), S0580 (parked this finding)

## Last Audit

- Date: 2026-06-21 (via /spec-all)
- Verdict: Verified
- Scope: single file `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/streams/ImportStreamCatalogUseCase.kt`.
- `downloadCsv()` now issues the call on `okHttpClient.newBuilder().callTimeout(CATALOG_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS).build()` (line ~93); no direct `okHttpClient.newCall(..)` remains.
- `CATALOG_CALL_TIMEOUT_SECONDS = 30L` declared in the private companion (line ~144), referenced once in `downloadCsv()`.
- `import java.util.concurrent.TimeUnit` added (line 12).
- `invoke()` try/catch and `CatalogImportResult` sealed interface unchanged: the `InterruptedIOException` raised on call-timeout is already mapped to `CatalogImportResult.Failure` by the existing `catch (e: Exception)`.
- Build: `.\a.ps1 fk` (compileStandardDebugKotlin) exit 0.
- No device test required: the failure path is pre-existing and unchanged; exercising the timeout needs an artificially slow host, and the call-timeout-to-`Failure` mapping is deterministic and compile-verified.
- Residual gaps: none.
