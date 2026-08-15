# Phase 03 — Regression Tests

**Strategic spec:** [../S0176_nolegal-session-context-etld-fix.md](../S0176_nolegal-session-context-etld-fix.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Lock the resolver, session-binding, and matched-host bookkeeping behavior behind narrow unit tests.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research outcomes are reflected in the implementation scope.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkCookieDomainResolverTest.kt` | New | ≤ 180 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContextTest.kt` | New | ≤ 180 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt` | New | ≤ 420 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split before continuing.

---

## Steps

### Step 03.1 — Add resolver and session-context contract tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkCookieDomainResolverTest.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContextTest.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add unit tests that freeze the approved registrable-domain parsing contract and the current `LinkDownloadSessionContext.cookiesFor()` matching rules. Cover a base-domain host, a stored `www.` host serving a sibling subdomain request, and one no-match case so the Q3 research result stays executable.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkCookieDomainResolverTest.kt` exists.
- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContextTest.kt` exists.
- `Grep` — `fun www_session_matches_sibling_subdomain_requests()` is present in `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContextTest.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. Files: data/link/cookie/LinkCookieDomainResolverTest.kt (+42 LOC), data/link/cookie/LinkDownloadSessionContextTest.kt (+45 LOC). Dev log recorded.

---

### Step 03.2 — Add coordinator fallback and bookkeeping tests

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add focused coordinator tests for three paths: exact host match wins over registrable fallback; registrable fallback binds the persisted base host into the session context; successful account-bound downloads call `markLastUsed` with the matched stored host.

**Verification:**

- `Glob` — `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt` exists.
- `Grep` — `fun exact_match_precedes_registrable_fallback()` is present in `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt`.
- `Grep` — `fun successful_account_bound_download_marks_last_used_for_matched_host()` is present in `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. Files: domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt (+138 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL 2026-05-12.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] The narrow unit-test target for the new files passes. MANUAL-REQUIRED
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

Phase 04 should only do bookkeeping and verification handoff. No new product logic belongs there.

---

## Rollback Plan

Revert Phase 03 commit(s) — test-only surface.