# Phase 01 — Domain Resolution

**Strategic spec:** [../S0176_nolegal-session-context-etld-fix.md](../S0176_nolegal-session-context-etld-fix.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Introduce one shared registrable-domain resolver for link-download auth and move the existing cookie-jar fallback to it.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 Q2 resolution is reflected in the implementation: use the PSL-aware OkHttp-based resolver with null-guard.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkCookieDomainResolver.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadCookieJar.kt` | Modified | ≤ 220 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split before continuing.

---

## Steps

### Step 01.1 — Create the shared registrable-domain resolver

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkCookieDomainResolver.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create `LinkCookieDomainResolver.kt` in the link cookie package. Expose `internal fun registrableDomainOrNull(host: String): String?` and back it with the PSL-aware resolver already available in the current OkHttp pin, using an `HttpUrl` created from the host when needed. Return `null` for IPs, `localhost`, and bare public-suffix inputs.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkCookieDomainResolver.kt` exists.
- `Grep` — `fun registrableDomainOrNull(host: String): String?` matches exactly once in that file.
- `Grep` — `topPrivateDomain` is present in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. Files: data/link/cookie/LinkDownloadCookieJar.kt (naive helper removed, routed to shared resolver). Dev log recorded.

---

### Step 01.2 — Switch the cookie jar to the shared resolver

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadCookieJar.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Remove the private registrable-domain helper from `LinkDownloadCookieJar` and route the existing all-account fallback through `registrableDomainOrNull(host)`. Keep the current lookup order intact: session context first, exact store lookup second, registrable-domain fallback third.

**Verification:**

- `Grep` — `registrableDomainOrNull(host)` is present in `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadCookieJar.kt`.
- `Grep` — `private fun registrableDomain(` returns zero hits in `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadCookieJar.kt`.
- `Grep` — `store.listAllAccounts()` is present in `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/LinkDownloadCookieJar.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. Files: data/link/cookie/LinkCookieDomainResolver.kt (+18 LOC). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL 2026-05-12.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` and `render.ps1` completed if a new Kotlin file was added. (run in Phase 04)

---

## Handoff Notes to Next Phase

Phase 02 may assume one approved `registrableDomainOrNull` entry-point and must not reintroduce per-file host parsing.

---

## Rollback Plan

Revert Phase 01 commit(s) — no schema, DI, or user-facing surface changes.