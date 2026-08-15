# Phase 01 - Compact serialization

**Strategic spec:** [`../S1039_share-resource-fmscfg.md`](../S1039_share-resource-fmscfg.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 04
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Add a compressed `FMSCFG1:` write path to `CompanionConfigSerializer` (mirror of the parser's existing inflate), so a dense `.fmscfg` payload stays inside QR capacity. No behavior change to the existing plain-JSON `serialize`.

---

## Prerequisites

- [ ] Working tree builds.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/companion/CompanionConfigSerializer.kt` | Modified | ≤ 60 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/companion/CompanionConfigSerializerTest.kt` | Modified | ≤ 130 |

---

## Steps

### Step 01.1 - Add `serializeCompressed`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/companion/CompanionConfigSerializer.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `fun serializeCompressed(dto: CompanionConfigDto): String` next to `serialize`. Build the plain JSON with the existing `gson.toJson(dto)`, gzip its UTF-8 bytes, base64-encode the gzip output (`java.util.Base64.getEncoder()`), and prefix with `CompanionConfigParser.COMPRESSED_PREFIX`. This must be the exact inverse of `CompanionConfigParser.inflate` so a round-trip through `CompanionConfigParser.parse` yields the identical DTO. Keep the plain `serialize` untouched. Use `java.util.zip.GZIPOutputStream` on a `ByteArrayOutputStream`, closing the gzip stream before reading the bytes. Update the class KDoc to note the compressed writer now exists (drop the "import-only" wording).

**Verification:**

- `Grep` - `fun serializeCompressed` matches exactly once in `CompanionConfigSerializer.kt`.
- `Grep` - `COMPRESSED_PREFIX` referenced in `CompanionConfigSerializer.kt`.
- `Grep` - `GZIPOutputStream` present in `CompanionConfigSerializer.kt`.

**Status:** `[ ]` not done

---

### Step 01.2 - Round-trip test for the compressed transport

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/companion/CompanionConfigSerializerTest.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add a test `round-trips a config through the compressed transport`: build a v2 `CompanionConfigDto` (reuse the shape from the existing v2 test), assert `serializer.serializeCompressed(dto).startsWith(CompanionConfigParser.COMPRESSED_PREFIX)`, then assert `parser.parse(serializer.serializeCompressed(dto)) == dto`. Add a second assertion that the compressed payload is strictly shorter than the plain `serializer.serialize(dto)` for this multi-field DTO (density is the point of the transport).

**Verification:**

- `Grep` - `serializeCompressed` present in `CompanionConfigSerializerTest.kt`.
- `Grep` - `COMPRESSED_PREFIX` present in `CompanionConfigSerializerTest.kt`.
- Run `.\a.ps1 fu` (or `.\gradlew.bat testStandardDebugUnitTest --tests "*CompanionConfigSerializerTest"`) - new test passes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `CompanionConfigSerializerTest` passes.
- [ ] Dev log entry added for both files.

---

## Handoff Notes to Next Phase

`CompanionConfigSerializer.serializeCompressed` produces the QR-ready payload string consumed by Phase 04 (`ExportCompanionConfigUseCase.exportQrPayload`). The plain-JSON `serialize` remains the file-share transport.

---

## Rollback Plan

Revert the phase commit - additive method + test, no data migration or user-facing surface.
