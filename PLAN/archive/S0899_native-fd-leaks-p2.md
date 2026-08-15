# S0899 - Native extractor / fd leaks on error paths (P2 cluster)

**Ticket:** S0899
**Status:** Archived
**Priority:** 35
**Date:** 2026-07-03
**Tier:** 2 - Easy (ad-hoc)

<!-- promoted by /spec-all S0878 triage - 2026-07-03 -->
<!-- auto-approved by /spec-all (compact) - 2026-07-03 -->

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03, из P2-appendix массового аудита 2026-07-02 (wf_34a4d99d-fbf). Static-review, не верифицировано скептиком. Тема кластера: MediaExtractor/ParcelFileDescriptor не освобождаются на error-путях.

- app_v2/src/main/java/com/sza/fastmediasorter/core/util/SafUriExtractor.kt:146 - MediaExtractor released only inside the use-block happy path - leaks native extractor when openFileDescriptor returns null or setDataSource/getTrackFormat throws
- app_v2/src/main/java/com/sza/fastmediasorter/core/util/SafUriExtractor.kt:214 - extractPdfInfo leaks the ParcelFileDescriptor when the PdfRenderer constructor throws (corrupt or password-protected PDF)
- app_v2/src/streamingEnabled/java/com/sza/fastmediasorter/data/link/streaming/MediaMuxerRemuxer.kt:58 - remux() leaks a native MediaExtractor when setDataSource fails on a segment file

## 1. Goal (RU)

Три native-ресурса (MediaExtractor, ParcelFileDescriptor) не освобождаются на error-путях. Утечка нативного дескриптора накапливается при повторных сбоях (битые/парольные PDF, недоступные сегменты) и приближает `Too many open files`. Освободить каждый ресурс через `try/finally` или `.use {}` на всех путях, включая исключения и early-return.

`standard` включает `streamingEnabled` source set, поэтому `a.ps1 fk` (standard) компилирует все три файла.

## 2. Constraints

- Behavior on the happy path unchanged (same returned metadata / RemuxResult).
- `ParcelFileDescriptor`, `PdfRenderer`, `MediaExtractor` cleanup order preserved (renderer before its fd).
- No new dependencies; pure structural cleanup.

## 3. Phases

### Phase 1 - `SafUriExtractor.extractVideoAudioInfo` MediaExtractor try/finally

- Step 1.1: Hoist `val extractor = MediaExtractor()` above the `try`; remove the inner `extractor.release()` (inside the `?.use { pfd -> .. }` block); add `finally { extractor.release() }` to the surrounding `try/catch`.
  - Verification: grep - `extractor.release()` sits in a `finally`; no `release()` inside the `use` block. Release now runs when `openFileDescriptor` returns null and when `setDataSource`/`getTrackFormat` throw.

### Phase 2 - `SafUriExtractor.extractPdfInfo` fd + renderer via `.use`

- Step 2.1: Replace the manual `openFileDescriptor` + `PdfRenderer` + `close()` pair with `context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> android.graphics.pdf.PdfRenderer(pfd).use { renderer -> renderer.pageCount } }`.
  - Verification: grep - no bare `pfd.close()` / `pdfRenderer.close()`; both wrapped in `.use`. If the `PdfRenderer` constructor throws (corrupt/password PDF), the outer `pfd.use` still closes the descriptor; the outer catch still returns `null`.

### Phase 3 - `MediaMuxerRemuxer.remux` release on setDataSource failure

- Step 3.1: In the per-segment `setDataSource` `catch (t: Throwable)`, add `runCatching { extractor.release() }` before `return RemuxResult.MuxFailed(..)`.
  - Verification: grep - the setDataSource catch releases the extractor before returning; the processing block's own `finally { runCatching { extractor.release() } }` covers all other paths. No path returns/throws with a live extractor.

### Phase 4 - Build gate

- Step 4.1: `standard debug` compiles (`a.ps1 fk`) - covers `src/main` + `src/streamingEnabled`. Detekt-clean on both touched files.
  - Verification: BUILD SUCCESSFUL; no new detekt findings on `SafUriExtractor.kt` / `MediaMuxerRemuxer.kt`.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0878 (audit tail container - triage source), S0116 (streaming remux pipeline - owns MediaMuxerRemuxer).

## Related

- S0878 (audit tail container - triage source).
- S0116 (streaming remux pipeline).

## Last Audit

**Date:** 2026-07-03 (spec-all, static). **Status:** Verified.

All three native-resource leaks closed; `standard debug` Kotlin compile PASS (covers `src/main` + `src/streamingEnabled`).

- **`SafUriExtractor.extractVideoAudioInfo`** - `MediaExtractor` hoisted above the `try`; released in a new `finally`; inner `use`-block `release()` removed. Native extractor is now freed when `openFileDescriptor` returns null (use skipped) and when `setDataSource`/`getTrackFormat` throw (previously leaked to the outer catch).
- **`SafUriExtractor.extractPdfInfo`** - descriptor + renderer now `openFileDescriptor(..)?.use { pfd -> PdfRenderer(pfd).use { it.pageCount } }`. If the `PdfRenderer` constructor throws (corrupt/password PDF), the outer `pfd.use` still closes the `ParcelFileDescriptor`. `PdfRenderer` is `AutoCloseable` (API 21+); minSdk 23/26 OK.
- **`MediaMuxerRemuxer.remux`** - the per-segment `setDataSource` catch now `runCatching { extractor.release() }` before its early `return`; the processing block's own `finally` covers all other paths. No return/throw leaves a live `MediaExtractor`.

**Evidence rung:** static + compile + detekt (P2). Leaks only manifest on error paths (null fd, corrupt/password PDF, failed segment `setDataSource`) not reachable by a normal device gesture; happy-path output unchanged. No device gate.
