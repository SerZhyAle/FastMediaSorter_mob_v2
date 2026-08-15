# Phase 02 — Session Context Binding

**Strategic spec:** [../S0176_nolegal-session-context-etld-fix.md](../S0176_nolegal-session-context-etld-fix.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04
**Steps done:** 2 / 2
**Started:** 2026-05-12
**Completed:** 2026-05-12

---

## Objective

Resolve the persisted host for each run, apply cookies from that host, and reuse the same host for post-success session bookkeeping.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 Q1 and Q3 resolutions are reflected in scope: dynamic WebView remains a first-class consumer and the session context holder stays unchanged.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt` | Modified | ≤ 450 |

> File projected >500 lines after change → backup step required (timestamped copy in `temp/`). File >1500 lines → split before continuing.

---

## Steps

### Step 02.1 — Add the stored-host resolution helper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add `private fun resolveSessionHost(host: String, accountId: String?): String?` to `LinkAutoDownloadCoordinator`. Resolve exact host first; if it has no cookies, compute the registrable domain and scan `cookieStore.listAllAccounts()` for the first active entry whose registrable domain matches and whose `cookieCount` is non-zero. Keep account-specific selection deterministic when `accountId` is supplied, because the dynamic WebView flow cannot rely on the shared HTTP cookie bridge to mask misses.

**Verification:**

- `Grep` — `private fun resolveSessionHost(host: String, accountId: String?): String?` is present in `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`.
- `Grep` — `cookieStore.listAllAccounts()` is present in `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`.
- `Grep` — `cookieCount > 0` is present in `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. Files: domain/usecase/link/LinkAutoDownloadCoordinator.kt (+27 LOC, resolveSessionHost added). Dev log recorded.

---

### Step 02.2 — Bind the resolved host into the session context and recency updates

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Refactor `applySessionContext` and the success path in `handle()` so the coordinator loads cookies from the resolved stored host, writes `sessionContext.set(resolvedHost, cookies)`, and calls `authSessionRepository.markLastUsed(appliedSessionHost ?: host, accountId)` after successful save or fallback results. This keeps the current session-context matching rules intact while aligning both the shared HTTP path and the dynamic WebView path to the same persisted host.

**Verification:**

- `Grep` — `val appliedSessionHost =` is present in `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`.
- `Grep` — `sessionContext.set(resolvedHost, cookies)` is present in `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`.
- `Grep` — `authSessionRepository.markLastUsed(appliedSessionHost ?: host, accountId)` is present in `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/link/LinkAutoDownloadCoordinator.kt`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-12 — Verification 3/3 PASS. Files: domain/usecase/link/LinkAutoDownloadCoordinator.kt (applySessionContext returns String?, handle() captures appliedSessionHost). Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles — BUILD SUCCESSFUL 2026-05-12.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.
- [x] Phase 02 leaves `LinkDownloadSessionContext` unchanged; only the coordinator binding path changes.

---

## Handoff Notes to Next Phase

Phase 03 should assert exact-match precedence, registrable-domain fallback, and matched-host `markLastUsed` behavior.

---

## Rollback Plan

Revert Phase 02 commit(s) — no schema, DI, or migration surface changes.