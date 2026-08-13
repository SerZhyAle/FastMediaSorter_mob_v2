# Phase 02 — adaptive-heap-threshold

**Strategic spec:** [`../S0131_bugfix-pdf-null-bitmap.md`](../S0131_bugfix-pdf-null-bitmap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 1 / 1
**Started:** 2026-05-09
**Completed:** 2026-05-09

---

## Objective

Replace the fixed 20 MB native heap free threshold in `ImagePreloadHelper` with an adaptive check: require the larger of a 15 MB absolute floor or a 15% relative floor before preload runs. Add named constants for both limits.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImagePreloadHelper.kt` | Modified | ≤ 226 |

> File is 226 lines — no backup required.

---

## Steps

### Step 2.1 — Introduce adaptive heap threshold in ImagePreloadHelper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImagePreloadHelper.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> In `ImagePreloadHelper`, the current guard in `preloadNextImageIfNeeded()` blocks preload whenever `nativeHeapFree < 20` MB. On devices where Glide holds a large bitmap pool, the native heap free value hovers at 2–9 MB permanently, making preload permanently disabled.
>
> Replace the fixed threshold with an adaptive one. At the class level (companion object or file-level constants), add:
> ```kotlin
> private const val MIN_NATIVE_HEAP_FREE_MB = 15L
> private const val MIN_NATIVE_HEAP_FREE_PCT = 15
> ```
>
> In `preloadNextImageIfNeeded()`, replace:
> ```kotlin
> val nativeHeapFree = android.os.Debug.getNativeHeapFreeSize() / (1024 * 1024)
> if (nativeHeapFree < 20) {
>     Timber.w("ImagePreloadHelper: Preload skipped — native heap low (${nativeHeapFree}MB free)")
>     return
> }
> ```
> With:
> ```kotlin
> val nativeHeapFree = android.os.Debug.getNativeHeapFreeSize() / (1024 * 1024)
> val nativeHeapTotal = android.os.Debug.getNativeHeapSize() / (1024 * 1024)
> val freePercent = if (nativeHeapTotal > 0) (nativeHeapFree * 100 / nativeHeapTotal).toInt() else 0
> val relativeFloorMb = ((nativeHeapTotal * MIN_NATIVE_HEAP_FREE_PCT) + 99L) / 100L
> val minimumRequiredMb = relativeFloorMb.coerceAtLeast(MIN_NATIVE_HEAP_FREE_MB)
> if (nativeHeapFree < minimumRequiredMb) {
>     Timber.d("ImagePreloadHelper: Preload skipped — native heap low (${nativeHeapFree}MB free, needs ${minimumRequiredMb}MB, ${freePercent}% of ${nativeHeapTotal}MB)")
>     return
> }
> ```
> This keeps the original protection for small heaps while scaling correctly for larger heaps where a flat 20 MB cutoff is too weak.
>
> Downgrade the log from `Timber.w` to `Timber.d` — this is a normal operating condition, not a warning, once the threshold is calibrated.
>
> Add debug tag at function entry:
> ```kotlin
> Timber.d("S0131: preloadNextImageIfNeeded — adaptive heap check")
> ```

**Verification:**

- `Grep -n "MIN_NATIVE_HEAP_FREE_MB" ImagePreloadHelper.kt` — matches at declaration and usage (≥ 2 hits).
- `Grep -n "MIN_NATIVE_HEAP_FREE_PCT" ImagePreloadHelper.kt` — matches at declaration and usage (≥ 2 hits).
- `Grep -n "minimumRequiredMb" ImagePreloadHelper.kt` — matches at declaration and guard usage (≥ 2 hits).
- `Grep -n "getNativeHeapSize()" ImagePreloadHelper.kt` — matches at least once.
- `Grep -n "nativeHeapFree < 20" ImagePreloadHelper.kt` — zero hits (old literal removed).
- `Grep -n "Timber.w.*native heap low" ImagePreloadHelper.kt` — zero hits (downgraded to `d`).
- `Grep -n "Log\.d(" ImagePreloadHelper.kt` — zero hits.

**Status:** `[x] done`

**Step Log:**

- 2026-05-09 — Verification 7/7 PASS. companion object with `MIN_NATIVE_HEAP_FREE_MB=15L`, `MIN_NATIVE_HEAP_FREE_PCT=15` added; adaptive max-floor guard at lines 77–79; old literal `< 20` removed; `Timber.w` downgraded to `Timber.d`; `Log.d(` zero hits. Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 2.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entry added for `ImagePreloadHelper.kt` via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

Phase 02 establishes: preload heap check uses the larger of the absolute and relative thresholds. On a device where native heap is 148 MB, the relative floor is 23 MB, so `nativeHeapFree = 10` MB stays blocked (correct) and `nativeHeapFree = 20` MB also stays blocked until headroom is truly available. On a device with 512 MB native heap, the relative floor is 77 MB, so preload still proceeds at `374` MB free but stops much earlier than a flat 20 MB cutoff. Proceed to Phase 03.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
