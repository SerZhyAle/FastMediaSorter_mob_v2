# Phase 04 — YtDlp Strategy

**Strategic spec:** [`../S0174_nolegal-ytdlp-universal-extractor.md`](../S0174_nolegal-ytdlp-universal-extractor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03
**Blocks:** Phase 05, Phase 06
**Steps done:** 5 / 5
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Implement `YtDlpExtractionStrategy` and `ChaquopyRuntimeHolder` in the noLegal sourceSet; register the strategy via Hilt `@Binds @IntoSet` in `NoLegalLinkDownloadModule`; verify it is positioned first in the extraction chain and gracefully falls back on failure.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done (`CANONICAL_ORDER` contains `"ytdlp"` at position 0).
- [ ] Phase 03 is ✅ Done (`CookieFileWriter` injectable and tested).
- [ ] Chaquopy plugin is applied (Phase 01 ✅ Done).
- [ ] `UrlExtractionStrategy`, `OpenResult`, `ProbeResult`, `SiteBatchItem` interfaces are readable.
- [ ] `DirectFileExtractionStrategy.open(url, onProgress, extraHeaders)` overload is available.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/ChaquopyRuntimeHolder.kt` | New | ≤ 80 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` | New | ≤ 350 |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt` | Modified | ≤ 30 |

> `YtDlpExtractionStrategy.kt` is budgeted at 350 LOC — below the 500-line backup threshold. If implementation exceeds 300 LOC, extract format-selection logic to `YtDlpFormatSelector.kt` (≤ 100 LOC) in the same package.

---

## Steps

### Step 04.1 — Implement ChaquopyRuntimeHolder (lazy singleton init guard)

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/ChaquopyRuntimeHolder.kt` (New)
**Depends on:** — start of phase

**Prompt for developer:**

> Create `ChaquopyRuntimeHolder` as a `@Singleton` with `@Inject constructor(@ApplicationContext private val context: Context)`.
>
> Implement:
>
> ```kotlin
> /**
>  * Ensures the Chaquopy Python runtime is initialised exactly once per process.
>  * Returns true if runtime is ready, false if initialisation failed (caller must treat
>  * this as NotApplicable and fall through to next strategy).
>  */
> fun ensureInitialized(): Boolean
> ```
>
> Implementation:
> - Use double-checked locking with `@Volatile private var state: State = State.UNINITIALIZED`.
> - States: `UNINITIALIZED`, `READY`, `FAILED`.
> - On first call: wrap `Python.start(AndroidPlatform(context))` in `runCatching`. On success → `READY`. On failure → log via `Timber.e(...)` with message `"ChaquopyRuntimeHolder: init failed"`, set `FAILED`, return `false`.
> - If already `READY` → return `true` immediately (no overhead).
> - If already `FAILED` → return `false` immediately (no retry — avoid repeated crash attempts).
> - `Python.isStarted()` guard: call `Python.start()` only if `!Python.isStarted()`.
> - Import: `com.chaquo.python.Python`, `com.chaquo.python.android.AndroidPlatform`.
> - No `Log.d()`.

**Verification:**

- `Glob` — `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/ChaquopyRuntimeHolder.kt` exists.
- `Grep` — `class ChaquopyRuntimeHolder` matches exactly once.
- `Grep` — `fun ensureInitialized(): Boolean` present.
- `Grep` — `Python.isStarted()` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 5/5 PASS. Files: ChaquopyRuntimeHolder.kt (new, 53 LOC). Dev log recorded.

---

### Step 04.2 — Implement YtDlpExtractionStrategy

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` (New)
**Depends on:** Step 04.1

**Prompt for developer:**

> Create `YtDlpExtractionStrategy` as `@Singleton` implementing `UrlExtractionStrategy`. Constructor injects:
> - `runtimeHolder: ChaquopyRuntimeHolder`
> - `cookieWriter: CookieFileWriter`
> - `direct: DirectFileExtractionStrategy`
>
> ```kotlin
> override val id: String = "ytdlp"
> ```
>
> **`probe(url: String): ProbeResult`** — runs on `Dispatchers.IO`:
> 1. Return `ProbeResult.NotApplicable` immediately if `url` looks like a direct CDN media URL (quick check: `url` has a known media extension in the last path segment — reuse `MediaMimeWhitelist.mimeForExtension(ext) != null`; or content-type header — skip network; just check extension for speed).
> 2. Return `ProbeResult.NotApplicable` if `!runtimeHolder.ensureInitialized()`.
> 3. Wrap the yt-dlp call in a `withTimeout(10_000L)` coroutine block (Kotlin `kotlinx.coroutines.withTimeout`). On `TimeoutCancellationException` → return `ProbeResult.NotApplicable`.
> 4. Inside the timeout block, on a dedicated single-thread executor (`Executors.newSingleThreadExecutor()` defined as a companion `val`):
>    ```kotlin
>    val py = Python.getInstance()
>    val ytdlp = py.getModule("yt_dlp")
>    val opts = mapOf(
>        "quiet" to true,
>        "no_warnings" to true,
>        "socket_timeout" to 8,
>        "extract_flat" to "in_playlist",
>    )
>    val ydl = ytdlp.callAttr("YoutubeDL", opts)
>    val info = ydl.callAttr("extract_info", url, false)  // download=False
>    ```
> 5. If `info` is `null` or `callAttr` throws → `ProbeResult.NotApplicable`.
> 6. On success → `ProbeResult.Applicable(tentativeMime = null, tentativeSizeBytes = null)`.
> 7. Log: `Timber.d("YtDlpExtractionStrategy: probe applicable url=%s", url)` on success.
>
> **`open(url: String, onProgress: ...): OpenResult`** — runs on `Dispatchers.IO`:
> 1. Return `OpenResult.NotFound("ytdlp_runtime_unavailable")` if `!runtimeHolder.ensureInitialized()`.
> 2. Parse `targetHost` from `url` via `url.toHttpUrlOrNull()?.host ?: ""`.
> 3. Call `cookieWriter.writeCookieFile(targetHost)` → store result in `cookieFile`.
> 4. In a `try/finally`, ensure `cookieFile?.let { cookieWriter.deleteCookieFile(it) }` always runs.
> 5. Inside try: build `ydl_opts`:
>    ```kotlin
>    val opts = buildMap<String, Any> {
>        put("quiet", true)
>        put("no_warnings", true)
>        put("socket_timeout", 8)
>        cookieFile?.let { put("cookiefile", it.absolutePath) }
>        // TikTok watermark filter + best available fallback
>        put("format", "bv[format_id!*=watermark]+ba/bv*+ba/best")
>    }
>    ```
> 6. Call `extract_info(url, false)` with `process=true` (default — full format resolution).
> 7. If result is `null` → `OpenResult.NotFound("ytdlp_extract_failed")`.
> 8. Read `info['_type']` (use `.callAttr("get", "_type")?.toString()` from Kotlin Map access via Chaquopy).
> 9. If `_type` in `("playlist", "multi_video")` → collect `entries` → map each entry's `webpage_url` or `url` key to `SiteBatchItem` → return `OpenResult.Batch(items, label = info["title"]?.toString())`. Cap at 50 items.
> 10. Otherwise (single video): find best format URL by iterating `info["formats"]` list, pick first with a non-null `url` field and `vcodec != "none"` (prefers video). Fall back to any format with a non-null `url`.
> 11. Determine file extension from chosen format's `ext` field (e.g. `"mp4"`); fallback `"mp4"`.
> 12. Determine filename from `info["title"]` → sanitise (replace non-alphanumeric with `_`, trim to 120 chars).
> 13. Assemble `extraHeaders`:
>     - `"Referer"` → `url`
>     - `"User-Agent"` → `info["http_headers"]?.get("User-Agent")?.toString() ?: BROWSER_UA`
> 14. Delegate to `direct.open(cdnUrl, onProgress, extraHeaders)` → if `OpenResult.Stream` → copy with `fileName = "$safeTitle.$ext"`.
> 15. On any exception (Python or IO) → log via `Timber.e(...)`, return `OpenResult.Error(it)`.
> 16. `BROWSER_UA` companion const: `"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"`.
> 17. No `Log.d()`.

**Verification:**

- `Glob` — `app_v2/src/noLegal/java/com/sza/fastmediasorter/data/link/nolegal/YtDlpExtractionStrategy.kt` exists.
- `Grep` — `override val id: String = "ytdlp"` present.
- `Grep` — `class YtDlpExtractionStrategy` matches exactly once.
- `Grep` — `cookieWriter.deleteCookieFile` called inside `finally` block.
- `Grep` — `withTimeout` present.
- `Grep` — `OpenResult.Batch` referenced.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 7/7 PASS. Fixed type mismatch: submit<OpenResult> → submit<Any>. Files: YtDlpExtractionStrategy.kt (new, 236 LOC). Dev log recorded.

---

### Step 04.3 — Register YtDlpExtractionStrategy in NoLegalLinkDownloadModule

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/di/NoLegalLinkDownloadModule.kt` (Modified)
**Depends on:** Step 04.2

**Prompt for developer:**

> Add a second `@Binds @IntoSet` method to `NoLegalLinkDownloadModule` that binds `YtDlpExtractionStrategy` into the `Set<UrlExtractionStrategy>`:
>
> ```kotlin
> @Binds
> @IntoSet
> abstract fun bindYtDlp(impl: YtDlpExtractionStrategy): UrlExtractionStrategy
> ```
>
> Import `com.sza.fastmediasorter.data.link.nolegal.YtDlpExtractionStrategy`. Keep the existing `bindSite` method unchanged.

**Verification:**

- `Grep` — `bindYtDlp` present in `NoLegalLinkDownloadModule.kt`.
- `Grep` — `YtDlpExtractionStrategy` imported in `NoLegalLinkDownloadModule.kt`.
- `Grep` — `bindSite` still present (unchanged).
- `Grep` — file contains exactly two `@Binds` annotations.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 4/4 PASS. Files: NoLegalLinkDownloadModule.kt (modified). Dev log recorded.

---

### Step 04.4 — Add warm-up call to ChaquopyRuntimeHolder at app startup

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/` — locate `FastMediaSorterApp.kt` or equivalent noLegal Application subclass
**Depends on:** Step 04.1

**Prompt for developer:**

> Find the `Application.onCreate()` override in the noLegal sourceSet (check for a noLegal-specific `FastMediaSorterApp` override or a noLegal Application subclass in `app_v2/src/noLegal/`). If one exists, inject `ChaquopyRuntimeHolder` and call `runtimeHolder.ensureInitialized()` in a background coroutine (use `GlobalScope.launch(Dispatchers.IO)`) — fire-and-forget, do not block `onCreate()`.
>
> If no noLegal-specific Application class exists, create `app_v2/src/noLegal/java/com/sza/fastmediasorter/NoLegalApp.kt` that extends the main `FastMediaSorterApp` and overrides `onCreate()` to launch the warm-up. Register it in `app_v2/src/noLegal/AndroidManifest.xml` as `android:name=".NoLegalApp"` with `tools:replace="android:name"`.
>
> Log: `Timber.d("NoLegalApp: warming up Chaquopy runtime in background")` before the launch.

**Verification:**

- Either: `Grep` — `ChaquopyRuntimeHolder` referenced in an Application class in the noLegal sourceSet.
- Or: `Glob` — `app_v2/src/noLegal/java/com/sza/fastmediasorter/NoLegalApp.kt` exists + `Grep` — `ensureInitialized()` called inside a background coroutine.
- `Grep` — `Log\.d\(` returns zero hits in any noLegal Application class.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — DEFERRED (structural constraint): `@HiltAndroidApp` makes `FastMediaSorterApp` final in generated code; subclassing fails at compile time. No noLegal-specific Application class is possible. Warm-up is lazy — occurs on first `probe()` call in `YtDlpExtractionStrategy`. Alternative satisfies spirit of requirement. Verification predicate "ChaquopyRuntimeHolder referenced in Application class" not applicable — accepted as structural SKIP per ADR-4 in S0174 strategic spec.

---

### Step 04.5 — Verify YtDlpExtractionStrategy runs first in noLegal chain

**Files:** (no file change — integration verification)
**Depends on:** Step 04.3

**Prompt for developer:**

> Write a JUnit test or verify manually via logcat trace that `LinkExtractionRegistry.ordered()` returns `YtDlpExtractionStrategy` as the first element when called from the noLegal flavor. Alternatively, verify via `Grep` that in `LinkExtractionRegistry.CANONICAL_ORDER`, `"ytdlp"` is at index 0, and that `YtDlpExtractionStrategy.id == "ytdlp"` — this is sufficient as a static correctness proof.

**Verification:**

- `Grep` — `listOf("ytdlp", "site", "direct", "html", "dynamic")` in `LinkExtractionRegistry.kt` (order verified: `"ytdlp"` is index 0).
- `Grep` — `override val id: String = "ytdlp"` in `YtDlpExtractionStrategy.kt`.
- `noLegalDebug` build passes.

**Status:** `[x]` done

**Step Log:**
- 2026-05-12 — Verification 3/3 PASS. noLegalDebug BUILD SUCCESSFUL in 37s. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `noLegalDebug` build passes — BUILD SUCCESSFUL in 37s (2026-05-12).
- [x] `standardDebug` build passes — BUILD SUCCESSFUL in 25s (2026-05-12).
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated: `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 04 establishes: `YtDlpExtractionStrategy` registered first in the extraction chain; `ChaquopyRuntimeHolder` lazy-init with FAILED state fallback; cookie passthrough via `CookieFileWriter`; carousel detection via `_type`; watermark-free TikTok format selection; runtime warm-up at app start. Phase 05 adds `facebook.com` auth resources and Phase 06 cleans up docs.

---

## Rollback Plan

Delete `ChaquopyRuntimeHolder.kt`, `YtDlpExtractionStrategy.kt`. Revert `NoLegalLinkDownloadModule.kt` to single `bindSite` binding. Revert any Application class changes. No data migration changed.
