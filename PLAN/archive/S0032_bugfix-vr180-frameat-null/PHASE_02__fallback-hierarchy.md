# Phase 02 — Fallback Hierarchy & Placeholder

**Strategic spec:** [`../S0032_bugfix-vr180-frameat-null.md`](../S0032_bugfix-vr180-frameat-null.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 5 / 5
**Started:** —
**Completed:** —

---

## Objective

Extend `VideoPosterExtractor` with a three-tier fallback chain (Glide memory-cache hit → last ExoPlayer-rendered bitmap → static placeholder with localized content description) so that `onFirstFrameReady` always delivers a bitmap to `ImageLoadingManager.triggerVideoBackground`, never silently dropping the dynamic-background poster.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `VideoPosterExtractor.extract` returns `Result(null, …)` for all skip / failure paths and is wired into `VideoPlayerManager`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt` | Modified | ≤ 320 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt` | Modified | ≤ 920 |
| `app_v2/src/main/res/values/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | — |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | — |

---

## Steps

### Step 02.1 — Add Glide memory-cache lookup to extractor

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt`
**Depends on:** start of phase

**Prompt for developer:**

> Add a private function `private fun tryGlideMemoryCache(context: Context, path: String): Bitmap?` that performs a bounded synchronous lookup into Glide's cache — call it only from a non-main dispatcher (the caller is already on `Dispatchers.IO`). Use the same signature as `AdapterThumbnailLoader` for cache compatibility:
>
> ```kotlin
> val file = java.io.File(path)
> if (!file.canRead()) return null
> return try {
>     com.bumptech.glide.Glide.with(context)
>         .asBitmap()
>         .load(file)
>         .signature(com.bumptech.glide.signature.ObjectKey("${path}_${file.length()}"))
>         .override(AdapterThumbnailLoader.CACHED_THUMBNAIL_SIZE, AdapterThumbnailLoader.CACHED_THUMBNAIL_SIZE)
>         .centerCrop()
>         .onlyRetrieveFromCache(true)
>         .submit()
>         .get(50, java.util.concurrent.TimeUnit.MILLISECONDS)
> } catch (_: Exception) { null }
> ```
>
> If `AdapterThumbnailLoader.CACHED_THUMBNAIL_SIZE` is `private`, expose it as `internal const val` on the companion (a single-line widening edit, no behaviour change). Wire `tryGlideMemoryCache` into the `extract` flow so it runs **after** the preventive checks and before/in place of `getFrameAtTime` failure: any non-null bitmap from this path returns `Result(bitmap, Source.GLIDE_MEMORY, originalReasonOrNull)`. The `reason` field on a Glide-fallback result is the reason that *would have been* recorded for `getFrameAtTime` (`OOM`/`DECODER_BUSY`/etc.) — preserve it for diagnostics.
>
> Order in `extract`: **(a)** preventive skip path → if skipped, attempt `tryGlideMemoryCache` first, return its result if non-null with `Source.GLIDE_MEMORY`; **(b)** if not skipped → call `getFrameAtTime`, on success return `Source.FRAME_AT_TIME`; on null/exception → attempt `tryGlideMemoryCache`, return `Source.GLIDE_MEMORY` if non-null. Steps 02.2 / 02.3 add the next two fallback tiers.

**Verification:**

- `Grep` — `fun tryGlideMemoryCache` matches once in `VideoPosterExtractor.kt`.
- `Grep` — `onlyRetrieveFromCache\(true\)` referenced once in `VideoPosterExtractor.kt`.
- `Grep` — `Source\.GLIDE_MEMORY` returned in at least two distinct branches of `extract` (skipped path, frameAt-null path).
- `Grep` — `AdapterThumbnailLoader\.CACHED_THUMBNAIL_SIZE` referenced once in `VideoPosterExtractor.kt`.
- `Grep` in `AdapterThumbnailLoader.kt` — `CACHED_THUMBNAIL_SIZE` declaration is `internal` or `public` (no `private`).

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 5/5 PASS. Files: ui/player/VideoPosterExtractor.kt (+30 LOC). `CACHED_THUMBNAIL_SIZE` already public — no widening edit needed in `AdapterThumbnailLoader`. Dev log recorded.

---

### Step 02.2 — Track and reuse last ExoPlayer-delivered bitmap

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a private `@Volatile var lastDeliveredBitmap: Bitmap? = null` field to `VideoPosterExtractor` and a public `fun rememberDelivered(bitmap: Bitmap) { lastDeliveredBitmap = bitmap }`. The caller (Step 02.5) invokes `rememberDelivered` whenever a non-null bitmap is forwarded to `onFirstFrameReady`. In `extract`, after Glide-memory tier returns null, return `Result(lastDeliveredBitmap, Source.EXOPLAYER_LAST, originalReason)` if `lastDeliveredBitmap != null`. Do not retain the bitmap forever: add a public `fun reset() { lastDeliveredBitmap = null }` and call it from `VideoPlayerManager` whenever `currentFilePath` changes (Step 02.5).

**Verification:**

- `Grep` — `@Volatile` annotation and `lastDeliveredBitmap` declaration present in `VideoPosterExtractor.kt`.
- `Grep` — `fun rememberDelivered(` matches once.
- `Grep` — `fun reset(` matches once.
- `Grep` — `Source\.EXOPLAYER_LAST` returned at least once in `extract`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 4/4 PASS. Files: ui/player/VideoPosterExtractor.kt (+12 LOC). Dev log recorded.

---

### Step 02.3 — Generate static placeholder bitmap with localized content description

**Files:**
- `app_v2/src/main/res/values/strings.xml`
- `app_v2/src/main/res/values-ru/strings.xml`
- `app_v2/src/main/res/values-uk/strings.xml`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt`

**Depends on:** Step 02.2

**Prompt for developer:**

> Add the same string key in all three `strings.xml` files (no plurals, no params):
>
> | Locale | Key | Value |
> |--------|-----|-------|
> | EN (`values/strings.xml`) | `poster_thumbnail_unavailable` | `Thumbnail unavailable` |
> | RU (`values-ru/strings.xml`) | `poster_thumbnail_unavailable` | `Превью недоступно` |
> | UK (`values-uk/strings.xml`) | `poster_thumbnail_unavailable` | `Прев'ю недоступне` |
>
> Russian must keep the standard project author style (no need for `ё` here — none in this string). Ukrainian keeps the apostrophe `'` (the standard ASCII apostrophe used elsewhere in `values-uk/strings.xml`).
>
> In `VideoPosterExtractor`, add a private function `private fun buildPlaceholder(context: Context): Bitmap` that returns a 256×256 ARGB_8888 bitmap painted solid `0xFF222222` (dark gray) with the `R.drawable.ic_video` vector icon centered, tinted `0xFF888888`. Use `androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_video)` and standard `Canvas` drawing — no Glide, no IO. Cache the resulting bitmap in a private companion `var cachedPlaceholder: Bitmap? = null` so it is built at most once per process.
>
> Final fallback in `extract`: when no other tier produced a bitmap, return `Result(buildPlaceholder(context), Source.PLACEHOLDER, originalReason)`. The bitmap returned by this tier must be tagged so the caller can apply the localized `contentDescription` — see Step 02.5.

**Verification:**

- `Grep` — `poster_thumbnail_unavailable` matches in **all three** `strings.xml` files (one match each).
- `Grep` — `Thumbnail unavailable` in EN file; `Превью недоступно` in RU file; `Прев'ю недоступне` in UK file.
- `Grep` — `fun buildPlaceholder` matches once in `VideoPosterExtractor.kt`.
- `Grep` — `R\.drawable\.ic_video` referenced once in `VideoPosterExtractor.kt`.
- `Grep` — `Source\.PLACEHOLDER` returned at least once in `extract`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 5/5 PASS. Files: 3× strings.xml (+1 line each); ui/player/VideoPosterExtractor.kt (+25 LOC, including process-cached placeholder bitmap). Dev log recorded.

---

### Step 02.4 — Update enriched log to include `fallback=` field

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPosterExtractor.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Modify the `Timber.w("VideoPlayerManager: getFrameAtTime returned null …")` line introduced in Phase 01 Step 01.3 so it now also reports which fallback tier supplied the bitmap (or `placeholder`/`none`). The exact format:
>
> ```
> Timber.w("VideoPlayerManager: getFrameAtTime returned null path=$path reason=$reason fallback=$fallbackName")
> ```
>
> where `fallbackName` is one of `glide-memory`, `exoplayer-last`, `placeholder`. Emit one such line per `extract` call that took a fallback path; do not log on the `getFrameAtTime succeeded` path. For the preventive-skip path, log instead: `Timber.d("VideoPlayerManager: getFrameAtTime skipped path=$path reason=$reason fallback=$fallbackName")` (level `d`, not `w` — skip is expected behaviour, not a failure).

**Verification:**

- `Grep` — exactly one `Timber.w\(.*getFrameAtTime returned null` line in the file, and it must contain both `reason=` and `fallback=`.
- `Grep` — exactly one `Timber.d\(.*getFrameAtTime skipped` line in the file, with `reason=` and `fallback=`.
- `Grep` — fallback name literals `"glide-memory"`, `"exoplayer-last"`, `"placeholder"` appear in the file (passed as the `fallbackName` argument; the format string is `fallback=$fallbackName`).

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 3/3 PASS. Files: ui/player/VideoPosterExtractor.kt (refactored to single `runFallback` + `logFallback` helpers; net +14 LOC). Dev log recorded.

---

### Step 02.5 — Wire fallback chain through `VideoPlayerManager` and apply content description

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/VideoPlayerManager.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> In `onRenderedFirstFrame`, the `extract` call now always returns a non-null bitmap (Phase 02 guarantees it via the placeholder tier). Update the wiring so that `callback(result.bitmap!!)` is invoked unconditionally when the network/cloud short-circuit does not fire. After delivery: when `result.source != Source.PLACEHOLDER`, invoke `posterExtractor.rememberDelivered(result.bitmap!!)` so the bitmap can serve as the `EXOPLAYER_LAST` fallback for future calls on the same file. When `result.source == Source.PLACEHOLDER`, skip `rememberDelivered`.
>
> When `currentFilePath` changes (use the existing assignment site at line ~640: `currentFilePath = path`) — call `posterExtractor.reset()` immediately after the assignment so a stale bitmap from a previous file does not bleed across loads.
>
> Content description: `triggerVideoBackground` consumes the bitmap and passes it to a non-content view (the dynamic background plane behind the player), so no `contentDescription` is set there. The localized string `R.string.poster_thumbnail_unavailable` is reserved for the placeholder bitmap's accessibility node — wire it on the `binding.dynamicBackgroundView` (or whichever view `dynamicBackgroundProcessor` ultimately renders into) **only when the source is `PLACEHOLDER`**. Implementation: pass `result.source` through `triggerVideoBackground` as a second parameter (new signature: `fun triggerVideoBackground(bitmap: Bitmap, isPlaceholder: Boolean)`) and update the single call site in `PlayerMediaLoaderManager` accordingly. In `ImageLoadingManager.triggerVideoBackground`, when `isPlaceholder == true`, set `dynamicBackgroundProcessor` target view's `contentDescription = context.getString(R.string.poster_thumbnail_unavailable)`; otherwise clear it (`= null`).
>
> If `dynamicBackgroundProcessor` does not expose a target view that accepts `contentDescription`, route the description to the most reasonable accessible view — e.g. the player surface root — and document the choice in a single inline comment ≤ 80 chars. Do not add a fallback toast / dialog.

**Verification:**

- `Grep` in `VideoPlayerManager.kt` — `posterExtractor\.rememberDelivered` matches at least once.
- `Grep` in `VideoPlayerManager.kt` — `posterExtractor\.reset\(\)` matches at least once.
- `Grep` in `VideoPlayerManager.kt` — `result\.source` referenced (folded into `isPlaceholder` flag — single read fans out to `rememberDelivered` skip and the `callback(bitmap, isPlaceholder)` invocation).
- `Grep` in `ImageLoadingManager.kt` — `triggerVideoBackground` signature has two parameters (`Grep -n "fun triggerVideoBackground"` shows `bitmap.*isPlaceholder` or equivalent).
- `Grep` in `ImageLoadingManager.kt` — `R\.string\.poster_thumbnail_unavailable` referenced once.
- `Grep` in `PlayerMediaLoaderManager.kt` — the `triggerVideoBackground` call passes a second argument.
- `Grep` in `VideoPlayerManager.kt` — `getFrameAtTime` still returns zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 6/6 PASS. Files: ui/player/VideoPlayerManager.kt (callback signature widened to `(Bitmap, Boolean)`; reset on path swap; rememberDelivered for non-placeholder); ui/player/ImageLoadingManager.kt (triggerVideoBackground takes `isPlaceholder`, sets contentDescription on `ivDynamicBackground`); ui/player/helpers/PlayerMediaLoaderManager.kt (lambda updated). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — `assembleStandardDebug` BUILD SUCCESSFUL (2026-04-29).
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] All three `strings.xml` files contain `poster_thumbnail_unavailable`.
- [ ] Manual probe on `small.mp4` (≤ 1080p, ordinary local file): log shows `getFrameAtTime succeeded`, no `fallback=` line emitted, dynamic-background flow renders the real first frame (zero regression). MANUAL-REQUIRED.
- [ ] Manual probe on the 7K VR180 file (Quest 3): log shows either `getFrameAtTime skipped` or `getFrameAtTime returned null` with `reason=…` and a non-`placeholder` fallback if Glide memory cache has the thumbnail, otherwise `fallback=placeholder` and the dynamic-background plane displays the static gray placeholder, never a black/empty frame. MANUAL-REQUIRED — recorded as journal status `BlockNeedUserTest` after the spec is `Implemented`.

---

## Handoff Notes to Next Phase

- All behaviour changes land in this phase; Phase 03 only synchronises documentation, the catalog, and the dev log.
- Public API changed: `ImageLoadingManager.triggerVideoBackground` now takes two parameters. The catalog regen in Phase 03 will reflect this.

---

## Rollback Plan

Revert the phase commit. The placeholder string keys can stay (they are inert if unreferenced). The Phase 01 backup at `temp/VideoPlayerManager_<timestamp>.kt.backup` plus reverting Phase 01 commit fully restores prior behaviour. No data, schema, or migration changes.
