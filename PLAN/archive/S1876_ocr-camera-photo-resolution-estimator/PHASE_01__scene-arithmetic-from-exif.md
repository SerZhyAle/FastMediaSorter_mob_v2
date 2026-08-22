# Phase 01 - Scene arithmetic from EXIF

**Strategic spec:** [`../S1876_ocr-camera-photo-resolution-estimator.md`](../S1876_ocr-camera-photo-resolution-estimator.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 4 / 4
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

`EstimateOcrResolutionUseCase` computes the declared resolution for `OcrSourceKind.CAMERA_PHOTO` from the photo's subject distance and 35 mm-equivalent focal length, and keeps the floor whenever either input is absent or unusable.

---

## Prerequisites

- [ ] Strategic §6.1 is Open and confirmed **not** to gate this phase - it gates tier B only.
- [ ] `temp/CODE.LOCK` acquired before the first source edit.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/EstimateOcrResolutionUseCase.kt` | Modified | ≤ 140 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/ocr/EstimateOcrResolutionUseCaseTest.kt` | Modified | ≤ 220 |

---

## Steps

### Step 01.1 - Widen the signature with the two camera inputs

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/EstimateOcrResolutionUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add two optional parameters to `invoke`, `focal35Mm: Float? = null` and `subjectDistanceM: Float? = null`, placed after `screenDensityDpi` so every existing call site keeps compiling unchanged. Document each in the existing KDoc `@param` block, naming the EXIF tag it comes from and the source kind that consumes it.

**Why:**

Strategic §5.4 keeps `ExifInterface` out of the domain layer by having the caller extract the tags and pass primitives, matching how `pageWidthPoints` and `screenDensityDpi` already reach this class.

**Verification:**

- `Grep` - `focal35Mm: Float? = null` present exactly once in the file.
- `Grep` - `subjectDistanceM: Float? = null` present exactly once in the file.
- `Grep` - `@param focal35Mm` and `@param subjectDistanceM` both present.

**Status:** `[x]` done - both parameters added after `screenDensityDpi`, both documented with the EXIF tag they carry.

---

### Step 01.2 - Compute the camera resolution

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/EstimateOcrResolutionUseCase.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace `OcrSourceKind.CAMERA_PHOTO -> null` with a call to a new private `cameraPhotoDpi(pixelWidth, focal35Mm, subjectDistanceM)`. That function returns `null` when `pixelWidth`, `focal35Mm` or `subjectDistanceM` is null or not strictly positive, and otherwise returns `(pixelWidth * focal35Mm * MM_PER_INCH / (FULL_FRAME_WIDTH_MM * subjectDistanceM * MM_PER_M)).roundToInt()`. Add the three constants to the companion with a KDoc line each: `MM_PER_INCH = 25.4f`, `FULL_FRAME_WIDTH_MM = 36f`, `MM_PER_M = 1000f`.

**Why:**

Strategic §5.1 derives the resolution by similar triangles from data already in the file, which makes the camera branch arithmetic rather than the invented scene constant that `docs/OCR_OVERLAY_ACCURACY.md` rejected for our material.

**Verification:**

- `Grep` - `private fun cameraPhotoDpi` present exactly once.
- `Grep` - `OcrSourceKind.CAMERA_PHOTO -> cameraPhotoDpi` present, and `CAMERA_PHOTO -> null` absent.
- `Grep` - `FULL_FRAME_WIDTH_MM`, `MM_PER_INCH`, `MM_PER_M` each declared once in the companion.
- No magic number remains inline in `cameraPhotoDpi` (detekt `MagicNumber` clean).

**Status:** `[x]` done - `cameraPhotoDpi` added with `FULL_FRAME_WIDTH_MM`, `MM_PER_INCH` and `MM_PER_M` in the companion. Written with two returns rather than four guard returns, because detekt's `ReturnCount` caps a function at two.

---

### Step 01.3 - Rewrite the KDoc claim about the camera branch

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/ocr/EstimateOcrResolutionUseCase.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> The class KDoc currently states that `CAMERA_PHOTO` declares the floor deliberately and that its estimator is an open item. Replace that paragraph: the branch is arithmetic when EXIF carries subject distance and 35 mm-equivalent focal length, and falls back to the floor otherwise, with the residual open question narrowed to photos that carry neither. Update the same claim in `OcrSourceKind.CAMERA_PHOTO`'s KDoc, which calls it "the one kind that is genuinely estimated".

**Why:**

Rule 8 of CLAUDE.md treats existing KDoc as a requirement, so a KDoc that still asserts the floor is deliberate would contradict the code this phase ships and mislead the next reader.

**Verification:**

- `Grep` - `it is only the floor` or `declares the floor too, deliberately` absent from `EstimateOcrResolutionUseCase.kt`.
- `Grep` - `the one kind that is genuinely estimated` absent from `OcrSourceKind.kt`.
- Both files still carry an `S1715` reference and now also carry `S1876`.

**Status:** `[x]` done - the class KDoc now says every declaring branch computes its number, and `OcrSourceKind.CAMERA_PHOTO` no longer claims to be the one genuinely estimated kind.

---

### Step 01.4 - Cover both branches with unit tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/ocr/EstimateOcrResolutionUseCaseTest.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Replace the test named `camera photo declares the floor while its estimator is an open question` with tests covering: a computed value for `pixelWidth = 4000`, `focal35Mm = 26f`, `subjectDistanceM = 0.3f`; the floor when `subjectDistanceM` is `0f`, meaning "unknown" in EXIF; the floor when `focal35Mm` is null; and the floor when the computed value falls below it, using `subjectDistanceM = 2f`. Keep the existing exhaustive-kinds test passing.

**Why:**

Strategic §11 criteria 2-4 require both branches and the `subjectDistance == 0` case to be demonstrated, and criterion 4 requires proof that no input drops below `FLOOR_DPI`.

**Verification:**

- `pwsh -NoProfile -File ./a.ps1 fu` - `EstimateOcrResolutionUseCaseTest` passes; read the per-class XML, not the suite total.
- `Grep` - the old open-question test name is absent from the file.
- `Grep` - a test asserting `FLOOR_DPI` for `subjectDistanceM = 0f` is present.

**Status:** `[x]` done - four camera tests replace the single open-question one. Read from `TEST-com.sza.fastmediasorter.domain.ocr.EstimateOcrResolutionUseCaseTest.xml` written 15:30:39: `tests=13 failures=0 errors=0 skipped=0`, per class rather than from the suite total.

---

## Phase Done Criteria

- [ ] All 4 steps `[x]`.
- [ ] `pwsh -NoProfile -File ./a.ps1 fk` exits 0.
- [ ] `pwsh -NoProfile -File ./a.ps1 fu` - the estimator's test class passes.
- [ ] `post-change.ps1 -ScopeToFile` over the changed set exits 0.
