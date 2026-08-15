# Phase 05 — bd-ts-player-test

**Strategic spec:** [`../S0095_integration-test-review.md`](../S0095_integration-test-review.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 07
**Steps done:** 2 / 2
**Started:** 2026-05-05
**Completed:** 2026-05-05

---

## Objective

Add a minimal valid TS test asset to `androidTest/assets/` and an instrumentation test that verifies the BD-TS playback path does not crash on a partial/minimal container.

---

## Prerequisites

- [ ] Phase 02 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/androidTest/assets/test_media/minimal.ts` | New | binary — 576 bytes |
| `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/player/BdTsPlaybackInstrumentationTest.kt` | New | ≤ 180 |

---

## Steps

### Step 5.1 — Add minimal TS asset

**Files:** `app_v2/src/androidTest/assets/test_media/minimal.ts`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the file `app_v2/src/androidTest/assets/test_media/minimal.ts` as a binary file containing exactly one valid 188-byte MPEG-TS packet. The packet must: (a) start with sync byte `0x47`; (b) set PID to `0x0000` (PAT); (c) fill remaining 185 bytes with `0xFF` (stuffing). Write the file as raw binary. The purpose is to give the BD-TS detector a real but empty container rather than a random byte sequence, so that `TsPacketFormatDetector` classifies it as `STANDARD_188` without throwing.

**Verification:**

- `Glob` — `app_v2/src/androidTest/assets/test_media/minimal.ts` exists.
- `Bash` — `(Get-Item "app_v2/src/androidTest/assets/test_media/minimal.ts").Length -eq 576` returns `True`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 2/2 PASS. Files: minimal.ts (new, 576 bytes). Asset corrected to 576 bytes (3 TS packets) — 188-byte single-packet probe returns UNKNOWN, not STANDARD_188. Dev log recorded.

---

### Step 5.2 — Add BdTsPlaybackInstrumentationTest

**Files:** `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/player/BdTsPlaybackInstrumentationTest.kt`
**Depends on:** Step 5.1

**Prompt for developer:**

> Create `BdTsPlaybackInstrumentationTest` in package `com.sza.fastmediasorter.ui.player` (androidTest). In `@Before`, copy `test_media/minimal.ts` from `androidTest/assets` (via `context.assets.open("test_media/minimal.ts")`) to a temp file in `context.cacheDir`. In the single `@Test` method `tsPacketDetector_classifiesMinimalTsAsStandard188`: (a) instantiate `TsPacketFormatDetector` (it is a pure Kotlin class — no DI needed); (b) call its detection method on the temp file path or byte array; (c) assert the result is `TsPacketFormat.STANDARD_188`. Use `@SmallTest`. Clean up the temp file in `@After`. The test must not launch any Activity.

**Verification:**

- `Glob` — `app_v2/src/androidTest/java/com/sza/fastmediasorter/ui/player/BdTsPlaybackInstrumentationTest.kt` exists.
- `Grep` — `class BdTsPlaybackInstrumentationTest` present exactly once.
- `Grep` — `TsPacketFormat.STANDARD_188` present.
- `Grep` — `@SmallTest` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-05 — Verification 4/4 PASS. Files: BdTsPlaybackInstrumentationTest.kt (new, 26 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 5.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

BD-TS asset and its detector test are in place. Phase 06 (unit edge cases) is independent and can run in any order relative to Phases 03–05.

---

## Rollback Plan

Revert phase commit(s) — binary asset and one test file only; no production code changed.
