# Phase 02 — Regression Tests

**Strategic spec:** [../S0182_sticky_session_user_agent.md](../S0182_sticky_session_user_agent.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-13
**Completed:** 2026-05-13

---

## Objective

Freeze the new sticky-UA contract in focused unit tests for host matching and session binding.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is still confined to the intended S0182 slices.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContextTest.kt` | Modified | ≤ 220 |
| `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt` | Modified | ≤ 360 |

---

## Steps

### Step 02.1 — Cover host-aware `userAgentFor()` matching

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContextTest.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Extend the session-context contract tests so they assert `userAgentFor()` returns the pinned UA for exact-host and sibling-subdomain matches and returns null for unrelated domains.

**Verification:**

- `Grep` — `userAgentFor(` appears in `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContextTest.kt` at least three times.
- `Grep` — `m.instagram.com` appears in `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContextTest.kt` inside a `userAgentFor` assertion.
- `Grep` — `assertNull(context.userAgentFor("tiktok.com"))` is present in `app_v2/src/test/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadSessionContextTest.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Files: data/link/cookie/LinkDownloadSessionContextTest.kt (+18 LOC, exact/sibling/unrelated `userAgentFor()` coverage). Dev log recorded.

---

### Step 02.2 — Verify coordinator forwards pinned User-Agent into session context

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Update the focused coordinator tests so the cookie store stubs the pinned `User-Agent` lookup and the verification asserts `sessionContext.set(resolvedHost, cookies, userAgent)`. Preserve the existing exact-match and registrable-fallback coverage while adapting it to the new sticky-UA contract.

**Verification:**

- `Grep` — `loadUserAgentForAccount` is present in `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt`.
- `Grep` — `sessionContext.set("instagram.com", cookies,` is present in `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt`.
- `Grep` — `markLastUsed("instagram.com", "acc1")` remains present in `app_v2/src/test/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-13 — Verification 3/3 PASS. Files: domain/usecase/link/LinkAutoDownloadCoordinatorTest.kt (+10 LOC, pinned UA stubs and `sessionContext.set(..., userAgent)` verification). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Regression tests cover both session-context host matching and coordinator UA forwarding.
- [x] No test relies on the deprecated two-argument `sessionContext.set(...)` call for the sticky-UA path.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Phase 03 should run the narrowest executable checks for these two test classes and a noLegal compile check, then sync catalog and ticket artefacts.