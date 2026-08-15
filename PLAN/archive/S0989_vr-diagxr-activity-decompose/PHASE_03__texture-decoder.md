# Phase 03 - Texture Decoder

**Strategic spec:** [`../S0989_vr-diagxr-activity-decompose.md`](../S0989_vr-diagxr-activity-decompose.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 04
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Move image decode (budget sampling, Glide-pool bounded decode of bundled asset + files, bitmap->RGBA copy through the reusable direct buffer, pool return) into `VrTextureDecoder`, returning a decode result by value on `Dispatchers.IO`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrTextureDecoder.kt` | New | ≤ 260 |
| `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt` | Modified | ≤ 1500 |

> Flavor placement: vr-only helper under `src/vr/java/...`.

---

## Steps

### Step 03.1 - Create VrTextureDecoder

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/helpers/VrTextureDecoder.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `VrTextureDecoder(private val context: Context)` in `...ui.xr.helpers` with a nested `data class Decoded(val bytes: ByteArray, val width: Int, val height: Int)`. Move verbatim (preserving every OOM guard, `inSampleSize` retry, `IllegalArgumentException` fallback, and `Timber` line): `getReusableDirectBuffer`, `copyBitmapToRgbaBytes`, `decodeBundledPooled`, `decodeFilePooled`, `pickSampleSizeForBudget`, `returnToPool`, and the `BUNDLED_WIDTH` / `BUNDLED_HEIGHT` / `RGBA_BYTES_PER_PIXEL` / `MAX_DECODE_BYTES` constants (companion). Replace Activity-context references (`Glide.get(this@DiagnosticXrActivity)`, `resources`, `Glide.get(this)`) with `context` / `context.resources` / `Glide.get(context)`. Expose two suspend functions returning `Decoded?`: `suspend fun decodeBundled(): Decoded?` and `suspend fun decodeFile(file: File): Decoded?`, each wrapping the pooled decode + `copyBitmapToRgbaBytes` + `returnToPool` sequence exactly as `decodeBundledAsset`/`decodeImageToActivityBytes` do today (minus the `runtime.setRenderConfig` / HUD / `currentFilename` orchestration, which stays in the Activity). Add `fun releaseBuffer()` nulling `reusableDirectBuffer`.

**Verification:**

- `Glob` - `VrTextureDecoder.kt` exists.
- `Grep` - `class VrTextureDecoder` matches exactly once.
- `Grep` - `suspend fun decodeBundled(): Decoded?` and `suspend fun decodeFile(file: File): Decoded?` present.
- `Grep` - `pickSampleSizeForBudget` returns zero hits in `DiagnosticXrActivity.kt`.

**Status:** `[x]` done

---

### Step 03.2 - Rewire Activity decode paths

**Files:** `app_v2/src/vr/java/com/sza/fastmediasorter/ui/xr/DiagnosticXrActivity.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Instantiate `private val textureDecoder = VrTextureDecoder(applicationContext)` (in `proceedWithInitialization`). Rewrite `decodeBundledAsset()` and `decodeImageToActivityBytes(file)` to keep their orchestration (`runtime.setRenderConfig`, `hudRenderer.currentFilename`, `isPanelHudMode()` branch, banner queue) but delegate the decode to `textureDecoder.decodeBundled()` / `textureDecoder.decodeFile(file)`, assigning `textureBytes/textureWidth/textureHeight` from the returned `Decoded` (return false when null - identical fallback). Rewrite the `loadCurrentMediaItem` image branch to call `textureDecoder.decodeFile(file)` and `runtime.queueFrame(decoded.bytes, decoded.width, decoded.height)`, preserving the S0960 "keep previous frame" null path. Remove the moved decode/buffer methods, fields, and constants from the Activity. In `onDestroy`, call `textureDecoder.releaseBuffer()` where `reusableDirectBuffer = null` was.

**Verification:**

- `Grep` - `decodeFilePooled` / `decodeBundledPooled` / `reusableDirectBuffer` return zero hits in the Activity.
- `Grep` - `textureDecoder.decodeFile(` and `textureDecoder.decodeBundled(` present.
- `/build` - `standard debug` + `vr debug` compile.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - `/build` `standard debug` + `vr debug`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for both files.
- [ ] Phase-boundary audit - no unresolved P0/P1; confirm decode stays on `Dispatchers.IO`, no main-thread decode introduced.

---

## Handoff Notes to Next Phase

Decode owned by `VrTextureDecoder`; `textureBytes/Width/Height` remain Activity fields consumed by the render thread. Phase 04 leaves them untouched.

---

## Rollback Plan

Revert phase commit(s) - pure code move; decode dispatcher and OOM guards unchanged.
