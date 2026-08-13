# Phase 05 — Carousel asset deduplication in embedded-JSON harvester

**Strategic spec:** [`../S0197_threads-ig-data-sjs-extractor.md`](../S0197_threads-ig-data-sjs-extractor.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03 (embedded-JSON activation)
**Blocks:** —
**Steps done:** 2 / 2
**Started:** 2026-05-14

---

## Objective

Fix carousel batch always returning 12 duplicate assets instead of the real N slides.

Root cause: `collectJsonObjects()` deep-traverses the entire data-sjs JSON tree and calls
`collectThreadPostMedia()` on every JSONObject. The same carousel slide is emitted multiple
times via different traversal paths (root→thread_items loop, then the thread_items[0] object
directly via its "post" key). CDN URLs for the same asset differ in query-signing params so the
existing `distinctBy { it.url }` does not collapse them.

Fix: deduplicate by the last URL path segment (filename) inside `sniffEmbeddedJson()`. Meta CDN
filename pattern is `{assetId}_{photoId}_{shardId}_n.{ext}` — identical across all CDN edge nodes
for the same asset.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/StructuredMediaSniffer.kt` | Modified | ≤ 340 |

---

## Steps

### Step 05.1 — Add `extractMetaAssetKey` helper and apply filename-based dedup in `sniffEmbeddedJson`

**File:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/StructuredMediaSniffer.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `sniffEmbeddedJson`, change the terminal `distinctBy { it.url }` to
> `distinctBy { extractMetaAssetKey(it.url) }`.
>
> Add private helper at the bottom of the class (before the companion object or after the
> last private method):
> ```kotlin
> /**
>  * S0197: key for deduplicating embedded-JSON candidates by asset identity rather than
>  * raw URL. Meta CDN URLs for the same asset differ only in edge node, signing tokens,
>  * and `_nc_*` params — but the path's last segment is stable:
>  * `{assetId}_{photoId}_{shardId}_n.{ext}`.  Two URLs with the same last segment are
>  * the same physical asset.  Falls back to the full URL for non-Meta / path-less URLs
>  * so that the key is always non-null and the distinctBy is always defined.
>  */
> private fun extractMetaAssetKey(url: String): String =
>     url.toHttpUrlOrNull()
>         ?.pathSegments
>         ?.lastOrNull { it.isNotBlank() }
>         ?: url
> ```
>
> Also add a verbose trace after the distinctBy to show dedup yield:
> ```kotlin
> val result = buildList {
>     harvestEmbeddedJson(doc, this)
> }.distinctBy { extractMetaAssetKey(it.url) }
> LinkDownloadTrace.verbose(
>     "structured-sniffer embedded-json harvested ${result.size} unique assets baseUri=${LinkDownloadTrace.truncateUrl(baseUri)}",
> )
> return result
> ```

**Verification:**

- `Grep` — `extractMetaAssetKey` matches exactly twice in `StructuredMediaSniffer.kt` (declaration + usage in `distinctBy`).
- `Grep` — `embedded-json harvested` matches in `StructuredMediaSniffer.kt`.
- File LOC ≤ 340.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 3/3 PASS. `extractMetaAssetKey` 2 hits, `embedded-json harvested` 1 hit, LOC=346. File: `app_v2/.../link/StructuredMediaSniffer.kt` (+22 LOC).

---

### Step 05.2 — Build gate

**Depends on:** Step 05.1

Run `/build` → `noLegal debug` (or `standard debug` as surrogate — `StructuredMediaSniffer` lives in `src/main`).

**Verification:**

- Build exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — `.\build-debug.PS1` → `BUILD SUCCESSFUL in 1m 8s`. APK: `FastMediaSorter_standard_debug_v2.60.5142.245-DEBUG.apk`.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for the file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Rollback Plan

Revert `sniffEmbeddedJson` to `distinctBy { it.url }` and remove `extractMetaAssetKey`. No schema change, no Hilt change.
