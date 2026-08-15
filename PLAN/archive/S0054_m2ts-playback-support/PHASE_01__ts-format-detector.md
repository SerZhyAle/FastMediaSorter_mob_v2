# Phase 01 — TS Format Detector

**Strategic spec:** [`../S0054_m2ts-playback-support.md`](../S0054_m2ts-playback-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-05-03
**Completed:** 2026-05-03

---

## Objective

Introduce `TsPacketFormat` and `TsPacketFormatDetector` — a pure-Kotlin utility that identifies the packet layout of a TS byte stream from a small probe buffer, with no dependency on Android or ExoPlayer APIs.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved. _(all closed)_
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormat.kt` | New | ≤ 15 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormatDetector.kt` | New | ≤ 55 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormatDetectorTest.kt` | New | ≤ 90 |

---

## Steps

### Step 01.1 — Create `TsPacketFormat` enum

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormat.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `TsPacketFormat.kt` in package `com.sza.fastmediasorter.data.network.datasource`. Declare an `enum class TsPacketFormat` with three values: `BD_192`, `STANDARD_188`, `UNKNOWN`. No additional members needed.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormat.kt` exists.
- `Grep` — `enum class TsPacketFormat` matches exactly once in that file.
- `Grep` — `BD_192` present in that file.
- `Grep` — `STANDARD_188` present in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 4/4 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormat.kt (+7 LOC). Dev log recorded.

---

### Step 01.2 — Create `TsPacketFormatDetector`

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormatDetector.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create `TsPacketFormatDetector.kt` in the same package. Declare a Kotlin `object TsPacketFormatDetector` with:
>
> - `const val PROBE_BYTES = 576` (192 × 3 — enough to check 3 consecutive BD-TS or 3 standard-TS packets)
> - `fun detect(probe: ByteArray): TsPacketFormat`
>
> Detection logic in `detect`:
> 1. If `probe.size < PROBE_BYTES` return `UNKNOWN`.
> 2. Check BD-TS (192-byte packets): `probe[4] == 0x47.toByte()` AND `probe[196] == 0x47.toByte()` AND `probe[388] == 0x47.toByte()` AND `probe[0] != 0x47.toByte()` → return `BD_192`.
> 3. Check standard MPEG-TS (188-byte packets): `probe[0] == 0x47.toByte()` AND `probe[188] == 0x47.toByte()` AND `probe[376] == 0x47.toByte()` → return `STANDARD_188`.
> 4. Otherwise return `UNKNOWN`.
>
> No imports beyond the enum. No Timber calls (pure utility, testable without Android context).

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormatDetector.kt` exists.
- `Grep` — `object TsPacketFormatDetector` matches exactly once.
- `Grep` — `fun detect(probe: ByteArray): TsPacketFormat` present.
- `Grep` — `PROBE_BYTES = 576` present.
- `Grep` — `Log\.d(` returns zero hits in this file (Timber-only rule).

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 5/5 PASS. Files: app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormatDetector.kt (+17 LOC). Dev log recorded.

---

### Step 01.3 — Write unit tests for `TsPacketFormatDetector`

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormatDetectorTest.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Create `TsPacketFormatDetectorTest.kt` in the test source set mirror package. Write JUnit 4 tests (no Android dependencies):
>
> - `detect returns BD_192 for synthetic BD-TS probe`: build a 576-byte array where byte 4, 196, 388 = `0x47` and bytes 0, 192, 384 = `0x00`; assert `BD_192`.
> - `detect returns STANDARD_188 for synthetic standard-TS probe`: build 576-byte array where bytes 0, 188, 376 = `0x47`; assert `STANDARD_188`.
> - `detect returns UNKNOWN for short probe (< 576 bytes)`: pass a 100-byte zeroed array; assert `UNKNOWN`.
> - `detect returns UNKNOWN for all-zero probe`: pass a 576-byte zeroed array; assert `UNKNOWN`.
> - `detect returns UNKNOWN when sync bytes match BD but fewer than 3 packets`: build 395-byte array (< 576) with 0x47 at positions 4 and 196 only; assert `UNKNOWN`.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormatDetectorTest.kt` exists.
- `Grep` — `class TsPacketFormatDetectorTest` matches exactly once.
- `Grep` — `BD_192` appears in test assertions (value check in at least one test).
- `Grep` — `STANDARD_188` appears in test assertions.
- `Grep` — `UNKNOWN` appears in test assertions.

**Status:** `[x]` done

**Step Log:**

- 2026-05-03 — Verification 5/5 PASS. Files: app_v2/src/test/java/com/sza/fastmediasorter/data/network/datasource/TsPacketFormatDetectorTest.kt (+47 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every Step 01.* above is `[x] done`.
- [x] Project compiles — `testStandardDebugUnitTest` BUILD SUCCESSFUL, 5/5 tests PASS (2026-05-03).
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `TsPacketFormat` enum is in `com.sza.fastmediasorter.data.network.datasource`.
- `TsPacketFormatDetector.PROBE_BYTES = 576` is the canonical probe size used by all detection helpers in Phase 02–04.
- Phase 02 adds `detectTsFormatSuspend` (DataSource-based) and updates `BdTsPlaybackHelper.wrapForBdTs`.
- Phase 03 adds `FileInputStream`-based detection for local files.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
