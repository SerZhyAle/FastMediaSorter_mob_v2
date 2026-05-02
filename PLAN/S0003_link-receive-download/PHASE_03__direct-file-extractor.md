# Phase 03 — Direct-File Extractor Strategy

**Strategic spec:** [`../S0003_link-receive-download.md`](../S0003_link-receive-download.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Land the strategy contract `UrlExtractionStrategy`, the registry that orders strategies, and the first concrete strategy `DirectFileExtractionStrategy` (HEAD or GET, MIME whitelist, redirect guard, filename derivation). The strategy returns a streaming download handle that Phase 05 will consume; no UI, no writer integration here.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (`@Named("linkDownload")` `OkHttpClient` available in DI).
- [ ] Phase 02 ✅ Done (`LinkDownloadModule` exists; coordinator skeleton present).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt` | New | ≤ 150 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/MediaMimeWhitelist.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/DirectFileExtractionStrategy.kt` | New | ≤ 250 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt` | Modified | ≤ 120 |

---

## Steps

### Step 03.1 — Define `UrlExtractionStrategy` contract

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/UrlExtractionStrategy.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Define a sealed result and the strategy interface:
>
> ```kotlin
> interface UrlExtractionStrategy {
>     val id: String                                       // unique, short — "direct", "html", ..
>     suspend fun probe(url: String): ProbeResult
>     suspend fun open(url: String, onProgress: (bytesRead: Long, total: Long?) -> Unit): OpenResult
> }
>
> sealed interface ProbeResult {
>     object NotApplicable : ProbeResult
>     data class Applicable(val tentativeMime: String?, val tentativeSizeBytes: Long?) : ProbeResult
>     data class TransientError(val cause: Throwable) : ProbeResult
> }
>
> sealed interface OpenResult {
>     data class Stream(val body: java.io.InputStream, val contentLength: Long?, val mime: String, val fileName: String, val close: () -> Unit) : OpenResult
>     data class NotFound(val reason: String) : OpenResult
>     data class Blocked(val reason: BlockedReason) : OpenResult
>     data class Error(val cause: Throwable) : OpenResult
> }
>
> enum class BlockedReason { MimeNotAllowed, NonHttpScheme, RedirectToNonHttp }
> ```
>
> Place the file in `domain/usecase/link/`. No Hilt wiring here.

**Verification:**

- `Glob` — file exists.
- `Grep -n "interface UrlExtractionStrategy"` matches exactly once.
- `Grep -n "sealed interface OpenResult"` matches exactly once.
- `Grep -n "enum class BlockedReason"` matches exactly once.

**Status:** `[ ]` not done

---

### Step 03.2 — Define `LinkExtractionRegistry`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkExtractionRegistry.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create a small Hilt-injectable singleton that takes `Set<@JvmSuppressWildcards UrlExtractionStrategy>` and exposes `fun ordered(): List<UrlExtractionStrategy>` — the canonical order is `direct` first, then `html`. Reject duplicate ids by `require`-ing unique ids on construction. The set is provided via Hilt multibindings in Step 03.5; for this step, the class only needs to compile against an empty set.

**Verification:**

- `Glob` — file exists.
- `Grep -n "class LinkExtractionRegistry"` matches exactly once.
- `Grep -n "fun ordered"` in the file matches exactly once.
- `Grep -n "require\(strategies\\.distinctBy"` (or equivalent uniqueness check) matches at least once.

**Status:** `[ ]` not done

---

### Step 03.3 — Define MIME whitelist

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/MediaMimeWhitelist.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Single-purpose object that exposes `fun isAllowed(mime: String?): Boolean` and `fun extensionFor(mime: String?): String?`. Allowed types align with strategic §3.2 + the existing `MediaType` enum:
>
> - `image/*` (jpeg, png, webp, heic/heif, gif, bmp, avif)
> - `video/*` (mp4, webm, ogg, x-matroska, quicktime, mpeg, x-msvideo, 3gpp)
> - `audio/*` (mpeg, mp4, ogg, x-flac, x-wav, x-aac, opus)
> - `application/pdf`, `application/epub+zip`
> - `text/plain`, `text/markdown`
>
> Reject everything else (executables, archives, `application/octet-stream`). Match is case-insensitive on the type/subtype; ignore parameters after `;`.

**Verification:**

- `Glob` — file exists.
- `Grep -n "object MediaMimeWhitelist"` matches exactly once.
- `Grep -n "fun isAllowed"` in the file matches exactly once.
- `Grep -n "application/octet-stream"` in the file matches at least once (explicit rejection comment or guard).

**Status:** `[ ]` not done

---

### Step 03.4 — Implement `DirectFileExtractionStrategy`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/DirectFileExtractionStrategy.kt`
**Depends on:** Steps 03.1, 03.3

**Prompt for developer:**

> Concrete strategy with `@Inject constructor(@Named("linkDownload") httpClient: OkHttpClient)`. `id = "direct"`.
>
> `probe(url)`: Issue a `HEAD` request via OkHttp on the IO dispatcher; if the response is non-2xx, retry once with `GET` using `Range: bytes=0-0`. Read `Content-Type` and `Content-Length`. Return:
>
> - `Applicable(mime, size)` when `MediaMimeWhitelist.isAllowed(contentType) == true` OR the URL path ends in a whitelisted extension.
> - `NotApplicable` otherwise.
> - `TransientError(cause)` on `IOException` / `SocketTimeoutException`.
>
> `open(url, onProgress)`:
>
> 1. Validate URL scheme is `http(s)`; on mismatch return `Blocked(NonHttpScheme)`.
> 2. Issue `GET`, follow redirects manually — abort if any hop is non-`http(s)`. Return `Blocked(RedirectToNonHttp)` if so.
> 3. Read response `Content-Type`. Re-validate via `MediaMimeWhitelist.isAllowed`; on miss return `Blocked(MimeNotAllowed)`.
> 4. Derive filename: prefer `Content-Disposition` `filename*=` / `filename=`, fall back to last URL path segment, fall back to `download_<epoch>.<ext>` (extension via whitelist or `application/octet-stream` → `.bin` is impossible because we already rejected). Sanitize via the same regex used in `ReceiveShareActivity.createTextFile` (alnum/`_`/`-`).
> 5. Return `OpenResult.Stream(body, contentLength, mime, fileName, close)` where `body` wraps the response body input stream, `close` releases the OkHttp `Response`. The stream itself does not call `onProgress`; Phase 05 will instrument the copy.
>
> Use `Timber` only — never `Log.d`.

**Verification:**

- `Glob` — file exists.
- `Grep -n "class DirectFileExtractionStrategy"` matches exactly once.
- `Grep -n "override val id: String = \"direct\""` matches exactly once.
- `Grep -n "Log\\.d\\("` in the file returns zero hits.
- `Grep -n "MediaMimeWhitelist"` in the file matches at least twice (probe + open paths).
- `Grep -n "BlockedReason.RedirectToNonHttp"` in the file matches at least once.

**Status:** `[ ]` not done

---

### Step 03.5 — Wire registry + direct strategy through Hilt multibindings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt`
**Depends on:** Steps 03.2, 03.4

**Prompt for developer:**

> Add a multibindings declaration so `Set<UrlExtractionStrategy>` injection works:
>
> ```kotlin
> @Module
> @InstallIn(SingletonComponent::class)
> abstract class LinkDownloadStrategiesModule {
>     @Binds @IntoSet abstract fun bindDirect(impl: DirectFileExtractionStrategy): UrlExtractionStrategy
> }
> ```
>
> Keep the existing `OkHttpClient` `@Provides` from Phase 02 in the original `LinkDownloadModule`. The strategies module is a sibling abstract class in the same file (or a new file in `di/` — caller's choice; respect the line budget). Do not yet bind the HTML strategy — Phase 04 adds it.

**Verification:**

- `Grep -n "@IntoSet"` in `app_v2/src/main/java/com/sza/fastmediasorter/di/LinkDownloadModule.kt` matches at least once.
- `Grep -n "DirectFileExtractionStrategy"` in the same file matches at least once.
- `Grep -n "abstract fun bindDirect"` (or equivalent) matches exactly once.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles — run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

`UrlExtractionStrategy`, `LinkExtractionRegistry`, and `DirectFileExtractionStrategy` are available. Phase 04 adds `HtmlPageExtractionStrategy` as a second `@IntoSet` binding. Phase 05 consumes the registry inside `LinkAutoDownloadCoordinator.handle`.

---

## Rollback Plan

Revert phase commit(s). No persisted state; no UI surface affected.
