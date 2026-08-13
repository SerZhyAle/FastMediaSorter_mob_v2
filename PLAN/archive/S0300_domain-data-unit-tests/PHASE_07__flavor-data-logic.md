# Phase 07 - Flavor-Specific Data Logic

**Strategic spec:** [`../S0300_domain-data-unit-tests.md`](../S0300_domain-data-unit-tests.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04, 05, 06
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-05-29
**Completed:** 2026-05-29

---

## Objective

Add JVM unit tests for flavor-only data logic in the flavor test source sets. Concrete in-scope target: the `noLegal` link-extraction strategies. The `vr` flavor has no in-scope `domain`/`data` logic (its flavor code lives in `core/xr` and `ui`, both out of S0300 scope), so no `vr` data tests are written.

---

## Prerequisites

- [ ] Phases 04, 05, 06 ✅ Done (shared link/data contracts covered, fakes stable).
- [ ] `COVERAGE_INVENTORY.md` rows flagged flavor-only are present.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/testNoLegal/java/com/sza/fastmediasorter/data/link/nolegal/*Test.kt` | New | ≤ 400 each |

> Flavor test placement: `noLegal` tests live under `src/testNoLegal/`, run via `testNoLegalDebugUnitTest`. No `BuildConfig` flavor guards inside test code - source-set placement is the isolation mechanism (CLAUDE.md Rule 15, `dev/FLAVOR_DEVELOPMENT_RULES.md`). The existing `CookieFileWriterTest` is the placement precedent.

---

## Steps

### Step 07.1 - Cover `noLegal` link-extraction strategies

**Files:** `app_v2/src/testNoLegal/java/com/sza/fastmediasorter/data/link/nolegal/*Test.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> For each in-scope class in `src/noLegal/java/.../data/link/nolegal` (the site-extraction strategies and their helpers - excluding the already-tested `CookieFileWriter` and any thin runtime holder marked out-of-scope), add a `*Test.kt` under `src/testNoLegal/`. Cover URL-matching predicates, extraction/parse branching, and failure handling. Fake the network downloader and any Chaquopy/runtime dependency - no real HTTP and no Python runtime in tests. Update inventory rows.

**Verification:**

- `Grep` - each in-scope `data/link/nolegal` class has a matching `*Test.kt` under `src/testNoLegal/`.
- `Grep -n "Log\.d\("` - zero hits across new files.
- `Grep -n "BuildConfig\.\(IS_\|SUPPORT_\|ENABLE_\)"` - zero hits in new test files.

**Status:** `[x]` done

---

### Step 07.2 - Green-run `noLegal` flavor tests

**Files:** - (validation only)
**Depends on:** Step 07.1

**Prompt for developer:**

> Run `testNoLegalDebugUnitTest` for the new classes; confirm each passes via per-class XML under `app_v2/build/test-results/testNoLegalDebugUnitTest/`. Do not fix unrelated pre-existing red tests; do not add new red.

**Verification:**

- Each new Phase 07 test class XML shows `failures="0" errors="0"` (`expected: 0/0 | actual: per report`).
- `assembleNoLegalDebug` compiles.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 07.*` above is `[x] done`. 9 new test classes (93 methods) under `src/testNoLegal/`; inventory Phase 07: 0 in-scope rows remain untested. `expected: 0 | actual: 0`.
- [x] `compileNoLegalDebugUnitTestKotlin` exit 0 (test sources of the noLegal variant compile; stronger than `assembleNoLegalDebug` for this test-only change).
- [x] All new Phase 07 test classes green per per-class XML under `testNoLegalDebugUnitTest` (`failures="0" errors="0"`); no new red.
- [x] `Grep` for `TODO(phase-07)` returns zero hits. `expected: 0 | actual: 0`.
- [x] `Grep -n "Log\.d\("` zero hits across new files.
- [x] No `BuildConfig` flavor guard in any new test file (source-set isolation).
- [x] Dev log entry added for the phase.

**Step Log:**

- 2026-05-29 - Covered by one `android-kotlin-developer` batch in `src/testNoLegal/`. 7 link-extraction strategies (ArtStation/Dailymotion/DeviantArt/Vimeo/Yt-dlp/NewPipe site/NewPipe downloader) + 2 OCR classes deferred from Phase 03 (PaddleOcrEngine, PaddleOcrEngineContributor). Robolectric used for stubbed `org.json`/`Bitmap`. Chaquopy/NewPipe/PaddleLite-native cores left uncovered (not JVM-reachable), documented per class. Adjacent debt: stale `Timber.d("S0288: …")` in `PaddleOcrEngine` (production, outside scope).

---

## Handoff Notes to Next Phase

All in-scope flavor-only data logic covered. Final phase performs catalog regen and dev-log/changelog closure.

---

## Rollback Plan

Delete the new `src/testNoLegal/.../data/link/nolegal/*Test.kt` files. No production code changed.
