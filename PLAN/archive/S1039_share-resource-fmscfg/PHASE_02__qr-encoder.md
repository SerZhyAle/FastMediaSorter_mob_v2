# Phase 02 - QR encoder

**Strategic spec:** [`../S1039_share-resource-fmscfg.md`](../S1039_share-resource-fmscfg.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 03
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Introduce a stateless `QrCodeEncoder` (string -> `BitMatrix` -> `Bitmap`) on the already-present ZXing `core`, mirroring the existing decode-side `QrCodeAnalyzer`. No UI yet.

---

## Prerequisites

- [ ] `com.google.zxing:core` present (confirmed `app_v2/build.gradle.kts:1331`).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/companionimport/qr/QrCodeEncoder.kt` | New | ≤ 80 |
| `app_v2/src/test/java/com/sza/fastmediasorter/ui/companionimport/qr/QrCodeEncoderTest.kt` | New | ≤ 60 |

---

## Steps

### Step 02.1 - Create `QrCodeEncoder`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/companionimport/qr/QrCodeEncoder.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `object QrCodeEncoder` with two functions. (1) `fun encodeToMatrix(payload: String, sizePx: Int): BitMatrix` - use `com.google.zxing.qrcode.QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)` where `hints` is an `EnumMap<EncodeHintType, Any>` setting `CHARACTER_SET` = "UTF-8", `MARGIN` = 1, and `ERROR_CORRECTION` = `ErrorCorrectionLevel.L` (maximise data capacity for dense payloads). (2) `fun encode(payload: String, sizePx: Int): Bitmap` - call `encodeToMatrix`, allocate a `Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)`, and set each pixel to `Color.BLACK` when `matrix[x, y]` is true else `Color.WHITE` (build an `IntArray` row and `setPixels` per row, do not call `setPixel` per pixel). Keep it pure/stateless - no Hilt, no Android context. KDoc must reference S1039 and note it is the encode-side mirror of `QrCodeAnalyzer`.

**Verification:**

- `Glob` - `QrCodeEncoder.kt` exists.
- `Grep` - `object QrCodeEncoder` matches exactly once.
- `Grep` - `fun encodeToMatrix` and `fun encode` both present.
- `Grep` - `QRCodeWriter` and `ErrorCorrectionLevel` present.

**Status:** `[ ]` not done

---

### Step 02.2 - Matrix unit test

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/ui/companionimport/qr/QrCodeEncoderTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add a JVM unit test `encodeToMatrix produces a square non-empty matrix` that calls `QrCodeEncoder.encodeToMatrix("FMSCFG1:test-payload", 256)` and asserts the returned `BitMatrix` has `width > 0`, `height > 0`, `width == height`, and at least one set module (`matrix[x, y] == true` for some cell). Test only `encodeToMatrix` (pure JVM); do not test `encode` (Android `Bitmap` needs instrumentation).

**Verification:**

- `Glob` - `QrCodeEncoderTest.kt` exists.
- Run `.\a.ps1 fu` (or `--tests "*QrCodeEncoderTest"`) - test passes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `QrCodeEncoderTest` passes.
- [ ] Dev log entry added for both files.
- [ ] `dev/CATALOG/app_v2.jsonl` regen deferred to Phase 06 (batched).

---

## Handoff Notes to Next Phase

`QrCodeEncoder.encode(payload, sizePx)` is the Bitmap source for Phase 03's display screen. Error-correction level L is deliberate - it maximises capacity; do not raise it without re-checking dense-payload scannability.

---

## Rollback Plan

Delete both new files - no other code references them yet.
