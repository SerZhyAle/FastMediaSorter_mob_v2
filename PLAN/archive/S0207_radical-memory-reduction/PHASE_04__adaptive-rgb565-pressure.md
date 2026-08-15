# Phase 04 — Adaptive RGB565 Under Pressure

**Strategic spec:** [`../S0207_radical-memory-reduction.md`](../S0207_radical-memory-reduction.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01, Phase 03
**Blocks:** —
**Steps done:** 5 / 6
**Started:** 2026-05-15
**Completed:** —

---

## Objective

Add `NativePressureMonitor` — a runtime sampler of `Debug.getNativeHeapFreeSize()`. When free native heap falls below a configurable threshold, downstream code forces `Bitmap.Config.RGB_565` even when the active profile does not require it. The static profile from Phase 03 is unchanged; the monitor provides a transient override.

**Coverage (research 2026-05-15).** Today the decode-format decision is duplicated across 9 sites in 4 files plus 3 bypass decoders. Phase 04 must consolidate this into a single resolver and cover the bypass paths too. Otherwise the runtime override is invisible to thumbnail loading, SMB/SFTP/FTP image decoding, and PDF page rendering — the exact paths that exercise the most native heap.

- 9 duplicated `if (memoryTier == MemoryTier.LOW) .format(PREFER_RGB_565)` sites:
  - `ui/player/ImageLoadingManager.kt:755,862,980`
  - `ui/player/ImagePreloadHelper.kt:172,207,230`
  - `ui/player/render/DualSurfaceStaticImageRenderer.kt:286,402`
  - `ui/player/AudioCoverArtLoader.kt:274,375`
- 3 sites that bypass Glide's `DecodeFormat` entirely with hardcoded `Bitmap.Config`:
  - `data/network/glide/SafeByteBufferBitmapDecoder.kt:69` — hardcoded `ARGB_8888` for all SMB/SFTP/FTP image decodes (high impact).
  - `data/glide/PdfPageDecoder.kt:69`, `data/glide/NetworkPdfThumbnailLoader.kt:444` — hardcoded `ARGB_8888` PDF pages.
- 1 site without any per-request format override: `ui/browse/AdapterThumbnailLoader.kt:256,281,333,367,435,456,502,545,584,636` — receives only Glide global default.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Baseline + Phase 03 measurements recorded for canonical scenario.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/NativePressureMonitor.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryPressureDecodeFormatResolver.kt` | New | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/NativePressureMonitorModule.kt` | New | ≤ 50 |
| `app_v2/src/main/java/com/sza/fastmediasorter/di/GlideAppModule.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt` | Modified | ≤ 1500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImagePreloadHelper.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/DualSurfaceStaticImageRenderer.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt` | Modified | ≤ 500 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/SafeByteBufferBitmapDecoder.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/PdfPageDecoder.kt` | Modified | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkPdfThumbnailLoader.kt` | Modified | ≤ 500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/core/memory/MemoryPressureDecodeFormatResolverTest.kt` | New | ≤ 200 |

---

## Steps

### Step 04.1 — Add `NativePressureMonitor` class

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/NativePressureMonitor.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `@Singleton class NativePressureMonitor @Inject constructor()`. Constants:
> - `private const val THRESHOLD_LOW_MB = 30L`
> - `private const val SAMPLE_VALIDITY_MS = 200L`
>
> State: `@Volatile private var lastSampleAtMs: Long = 0L`, `@Volatile private var lastFreeMb: Long = Long.MAX_VALUE`.
>
> Methods:
> - `fun isUnderPressure(): Boolean` — if `now - lastSampleAtMs > SAMPLE_VALIDITY_MS`, refresh `lastFreeMb = Debug.getNativeHeapFreeSize() / 1024 / 1024` and `lastSampleAtMs = now`. Return `lastFreeMb < THRESHOLD_LOW_MB`.
> - `fun lastFreeNativeMb(): Long` — returns `lastFreeMb` without forcing a refresh.
>
> KDoc explains: "Samples native heap free size with short TTL to avoid hot-path syscall storms. Threshold 30 MB chosen as Research item 4 starting hypothesis — calibrate after first run."

**Verification:**

- `Glob` — `NativePressureMonitor.kt` exists.
- `Grep` — `class NativePressureMonitor @Inject constructor()` present.
- `Grep` — `THRESHOLD_LOW_MB = 30L` AND `SAMPLE_VALIDITY_MS = 200L` both present.
- `Grep` — `Debug.getNativeHeapFreeSize` called.
- `Grep` for `Log.d\(` returns zero hits.

**Status:** `[x]` done — implemented as a singleton sampler with 30 MB threshold and 200 ms TTL.

---

### Step 04.2 — Add Hilt binding (provider only)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/di/NativePressureMonitorModule.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> Since `NativePressureMonitor` is a concrete `@Singleton class @Inject constructor()` with no interface, Hilt resolves it directly — but expose it via `@EntryPoint` so `GlideAppModule` (not a Hilt class itself) can access it:
> ```kotlin
> @Module @InstallIn(SingletonComponent::class)
> object NativePressureMonitorModule
>
> @EntryPoint @InstallIn(SingletonComponent::class)
> interface NativePressureEntryPoint {
>     fun nativePressureMonitor(): NativePressureMonitor
> }
> ```
> Package `com.sza.fastmediasorter.di`.

**Verification:**

- `Glob` — `NativePressureMonitorModule.kt` exists.
- `Grep` — `@EntryPoint @InstallIn(SingletonComponent::class)` present.
- `Grep` — `fun nativePressureMonitor(): NativePressureMonitor` present.

**Status:** `[x]` done — entry points for non-Hilt callers landed in `NativePressureMonitorModule.kt`.

---

### Step 04.3 — Add `MemoryPressureDecodeFormatResolver` (single decode-format helper)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/memory/MemoryPressureDecodeFormatResolver.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Create `@Singleton class MemoryPressureDecodeFormatResolver @Inject constructor(private val coordinator: MemoryProfileCoordinator, private val pressureMonitor: NativePressureMonitor)`. Public API:
> - `fun decodeFormat(): DecodeFormat` — returns `PREFER_RGB_565` if `coordinator.current().useRgb565 == true` OR `pressureMonitor.isUnderPressure() == true`, else `PREFER_ARGB_8888`.
> - `fun bitmapConfig(): Bitmap.Config` — same rule, returns `Bitmap.Config.RGB_565` vs `Bitmap.Config.ARGB_8888`. Provided for non-Glide call sites (bypass decoders in Step 04.5).
>
> KDoc: "Single source of truth for image decode format. Replaces 9 duplicated `if (memoryTier == LOW)` branches and feeds 3 bypass decoders (SafeByteBufferBitmapDecoder / PdfPageDecoder / NetworkPdfThumbnailLoader)."
>
> Both methods MUST be cheap — `coordinator.current()` is a `@Volatile` read, `pressureMonitor.isUnderPressure()` is TTL-cached. Safe to call inside `Glide.with(...).load(...)` chains and inside `BitmapFactory.Options` setup blocks.

**Verification:**

- `Glob` — `MemoryPressureDecodeFormatResolver.kt` exists.
- `Grep` — `class MemoryPressureDecodeFormatResolver` matches exactly once.
- `Grep` — both `fun decodeFormat()` and `fun bitmapConfig()` present.
- `Grep` — references to `coordinator.current().useRgb565` AND `pressureMonitor.isUnderPressure()`.

**Status:** `[x]` done — resolver landed with `decodeFormat()` and `bitmapConfig()` plus one-shot pressure override logging.

---

### Step 04.4 — Replace 9 duplicated tier-branches with the resolver + cover thumbnail loader

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImageLoadingManager.kt` (3 sites: L755, L862, L980)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/ImagePreloadHelper.kt` (3 sites: L172, L207, L230)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/render/DualSurfaceStaticImageRenderer.kt` (2 sites: L286, L402)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/AudioCoverArtLoader.kt` (2 sites: L274, L375)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/AdapterThumbnailLoader.kt` (10 `.load(...)` sites — currently NO per-request format override)

**Depends on:** Step 04.3

**Prompt for developer:**

> In each of the four player-side files, replace the existing `if (memoryTier == MemoryTier.LOW) requestOptions.format(DecodeFormat.PREFER_RGB_565).dontAnimate()` block with `requestOptions.format(resolver.decodeFormat())`. Inject `MemoryPressureDecodeFormatResolver` via constructor. Keep `.dontAnimate()` only where it was present today — that is animation control, not format selection.
>
> In `AdapterThumbnailLoader.kt` — add `.format(resolver.decodeFormat())` to every `Glide.with(...).load(...)` chain (currently 10 sites have no per-request format override). Inject the resolver.
>
> Add a one-line `Timber.i` ONLY when pressure-mode flips the decision (avoid log spam): `MemoryPressureDecodeFormatResolver: pressure override → RGB_565 (free=<N>MB, scenario=<name>)`. Use the monitor's `lastFreeNativeMb()` for the log payload to avoid a second syscall. The log can live inside the resolver itself, gated on `pressureMonitor.isUnderPressure() && !coordinator.current().useRgb565` so it only fires for the override case.
>
> Files >500 lines (`ImageLoadingManager.kt`) require a timestamped backup in `temp/`.

**Verification:**

- `Grep` — `resolver.decodeFormat()` present in each of the 5 listed files.
- `Grep` — count of `memoryTier == MemoryTier.LOW` branches in the 4 player files dropped to 0.
- `Grep` — `.format(` count in `AdapterThumbnailLoader.kt` now ≥ 10 (was 0).
- `Grep` — `pressure override` literal present.
- `Grep` for `Log.d\(` returns zero hits.

**Status:** `[x]` done — browse/player request builders now share the resolver-backed decode decision.

---

### Step 04.5 — Plumb resolver into bypass decoders (`SafeByteBufferBitmapDecoder`, PDF decoders)

**Files:**
- `app_v2/src/main/java/com/sza/fastmediasorter/data/network/glide/SafeByteBufferBitmapDecoder.kt:69`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/PdfPageDecoder.kt:69`
- `app_v2/src/main/java/com/sza/fastmediasorter/data/glide/NetworkPdfThumbnailLoader.kt:444`

**Depends on:** Step 04.4

**Prompt for developer:**

> These three decoders hardcode `Bitmap.Config.ARGB_8888` (and one `inPreferredConfig = ARGB_8888`), entirely bypassing Glide's `DecodeFormat`. They sit on the hottest paths for our scenario (SMB/SFTP/FTP byte-buffer image decode + PDF page render) — leaving them ARGB_8888-only voids the runtime override.
>
> Inject `MemoryPressureDecodeFormatResolver` into each decoder. Replace the hardcoded `Bitmap.Config.ARGB_8888` / `inPreferredConfig = ARGB_8888` with `resolver.bitmapConfig()`.
>
> For Glide-managed decoders (`SafeByteBufferBitmapDecoder` is a `ResourceDecoder<ByteBuffer, Bitmap>`), use `EntryPointAccessors.fromApplication(context, MemoryPressureEntryPoint::class.java).resolver()` if direct injection is not possible — the same entry-point pattern as Step 03.6. Add a single `@EntryPoint` interface in the new `MemoryPressureDecodeFormatResolver.kt` (or co-located file).
>
> Backup any file >500 lines.

**Verification:**

- `Grep` — `resolver.bitmapConfig()` present in each of the 3 files.
- `Grep` — count of `Bitmap.Config.ARGB_8888` in `SafeByteBufferBitmapDecoder.kt`, `PdfPageDecoder.kt`, `NetworkPdfThumbnailLoader.kt` is 0 (removed). Legitimate ARGB_8888 in `domain/usecase/` edit-pipeline files unaffected.
- `Grep` — `inPreferredConfig` no longer appears with a hardcoded `ARGB_8888` value in `SafeByteBufferBitmapDecoder.kt`.
- `Grep` for `Log.d\(` returns zero hits.

**Status:** `[x]` done — bypass decoders now use `resolver.bitmapConfig()` instead of hardcoded `ARGB_8888`.

---

### Step 04.6 — Calibration measurement

**Files:** —
**Depends on:** Step 04.5 + project compiles

**Prompt for developer:**

> Run the canonical scenario. Inspect `logs/current.log`:
> - If `MEM_PROBE | checkpoint=PRE_PLAY` shows native free < 30 MB AND `pressure override → RGB_565` appears at least once during the run, the monitor is active. Pass.
> - If never triggered (free ≥ 30 MB at all probe points), the monitor is dormant. Pass — Phases 02/03 already reduced pressure enough that runtime fallback is not needed in the canonical scenario.
>
> Either outcome is acceptable; record which one occurred in the phase notes. If neither pressure-mode trigger nor `MEM_PROBE` improvement is observed → escalate as a regression (file ad-hoc bugfix spec).

**Verification:**

- `Grep` in `logs/current.log` — at least one `MEM_PROBE | checkpoint=PRE_PLAY` line in the most recent session.
- Result recorded in Blockers Log of INDEX.md (which branch was observed: triggered or dormant).

**Status:** `[manual — deferred to human]` — requires device/emulator run; deferred to BlockNeedUserTest operator test.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles — `./gradlew.bat :app_v2:compileStandardDebugKotlin :app_v2:testStandardDebugUnitTest --tests "com.sza.fastmediasorter.core.memory.MemoryPressureDecodeFormatResolverTest"` PASS.
- [ ] Calibration result recorded.
- [x] Dev log entry added.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Phase 05 (small allocations) is independent — fuses bitmap-icon dedup with audio-buffer profile. Does not depend on Phase 04 outcome.

---

## Rollback Plan

Revert phase commits. Glide returns to using only profile-level `useRgb565` (no runtime override). No data, persistence, or user-facing surface changed.

---

## Revision History

- **2026-05-15** — manual implementation sync after Phase 04 code landed
  - Applied: marked Steps 04.1..04.5 complete; added targeted resolver test to Files Touched; recorded focused compile/test validation and left Step 04.6 open pending canonical logcat calibration.
- **2026-05-15** — by `/spec-update` (Claude Opus 4.7, focus: completeness)
  - Applied: Objective extended with full inventory of 9 duplicated tier-branches + 3 bypass decoders + 1 thumbnail loader gap; "Files Touched" extended with 9 new entries; new Step 04.3 (`MemoryPressureDecodeFormatResolver`); new Step 04.4 (replace 9 duplicated tier-branches + cover `AdapterThumbnailLoader`); new Step 04.5 (plumb resolver into bypass decoders `SafeByteBufferBitmapDecoder` / `PdfPageDecoder` / `NetworkPdfThumbnailLoader`); calibration renumbered 04.4 → 04.6; phase counter 4 → 6. Proposed (DISCUSS): 0.
  - Evidence: `temp/S0207_research/04_rgb565_threshold_map.md` (full call-site map) + `00_SUMMARY.md` F12.
