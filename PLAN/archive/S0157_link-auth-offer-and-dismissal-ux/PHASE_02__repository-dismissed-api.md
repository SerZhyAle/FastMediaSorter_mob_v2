# Phase 02 — repository-dismissed-api

**Strategic spec:** [`../S0157_link-auth-offer-and-dismissal-ux.md`](../S0157_link-auth-offer-and-dismissal-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-05-11
**Completed:** 2026-05-11

---

## Objective

Extend `AuthSessionRepository` and `AuthSessionRepositoryImpl` with the dismissed-record API (`markDismissed`, `isDismissedForHost`, `observeAccountsAll`). Fix `refreshFlows()` so it does not prune `type=dismissed` entries. Add `isDismissed: Boolean` to `AuthAccountDomain`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt` | Modified | ≤ 100 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt` | Modified | ≤ 240 |

---

## Steps

### Step 02.1 — Add `isDismissed` to `AuthAccountDomain`

**Files:** `domain/repository/AuthSessionRepository.kt`
**Depends on:** — start of phase

**Prompt for developer:**

> Add field `val isDismissed: Boolean = false` to `AuthAccountDomain`. The default `false` keeps all existing construction sites backward-compatible without requiring changes outside this phase.

**Verification:**

- `Grep` — `val isDismissed: Boolean` present in `AuthSessionRepository.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 1/1 PASS. `val isDismissed: Boolean = false` added. Files: AuthSessionRepository.kt (+2 LOC).

---

### Step 02.2 — Add dismissed-record API to `AuthSessionRepository` interface

**Files:** `domain/repository/AuthSessionRepository.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add three methods to the `AuthSessionRepository` interface under a new `// ── Dismissed-record API (S0157) ──` comment:
>
> - `suspend fun markDismissed(host: String)` — stores a dismissed record for this host (no cookies; offer will not be shown again until the record is deleted).
> - `suspend fun isDismissedForHost(host: String): Boolean` — returns true if a dismissed record exists for this host.
> - `fun observeAccountsAll(): Flow<List<AuthAccountDomain>>` — hot flow including both active sessions and dismissed records; used by the settings screen.

**Verification:**

- `Grep` — `suspend fun markDismissed(host: String)` present in `AuthSessionRepository.kt`.
- `Grep` — `suspend fun isDismissedForHost(host: String): Boolean` present.
- `Grep` — `fun observeAccountsAll(): Flow<List<AuthAccountDomain>>` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 3/3 PASS. All three new interface methods declared. Files: AuthSessionRepository.kt (+9 LOC).

---

### Step 02.3 — Fix `refreshFlows()` to preserve dismissed entries

**Files:** `data/repository/AuthSessionRepositoryImpl.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> In `AuthSessionRepositoryImpl.refreshFlows()`:
>
> 1. Change the stale-prune condition from `entry.cookieCount == 0` to `entry.cookieCount == 0 && entry.type == EncryptedCookieStore.TYPE_ACTIVE`. Dismissed entries (`type=dismissed`) must NOT be pruned.
> 2. The existing `live` filter (`cookieCount > 0`) already excludes dismissed entries from `accountFlow`. Verify this is correct — dismissed entries have `cookieCount == 0`.
> 3. Add a second `MutableStateFlow` field: `private val accountFlowAll: MutableStateFlow<List<AuthAccountDomain>> = MutableStateFlow(emptyList())`.
> 4. In `refreshFlows()`, populate `accountFlowAll` with the union of live active accounts AND dismissed records. Dismissed records are: `all.filter { (_, entry) -> entry.type == EncryptedCookieStore.TYPE_DISMISSED }`. Map them to `AuthAccountDomain` using `toAuthAccountDomain(host, isDismissed = true)`.
> 5. Sort `accountFlowAll` by `host` then by `isDismissed` (active first within each host), then by `displayName`.

**Verification:**

- `Grep` — `entry.type == EncryptedCookieStore.TYPE_ACTIVE` present in stale-prune filter.
- `Grep` — `accountFlowAll` declared as `MutableStateFlow` field.
- `Grep` — `TYPE_DISMISSED` referenced in `refreshFlows`.
- `Grep` — `isDismissed = true` referenced in dismissed entries mapping.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 4/4 PASS. `TYPE_ACTIVE` in prune filter, `accountFlowAll` declared, `TYPE_DISMISSED` in dismissed map, `isDismissed = true` in dismissed mapping. Files: AuthSessionRepositoryImpl.kt (+12 LOC).

---

### Step 02.4 — Implement dismissed API methods in `AuthSessionRepositoryImpl`

**Files:** `data/repository/AuthSessionRepositoryImpl.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Implement the three new interface methods:
>
> ```kotlin
> override suspend fun markDismissed(host: String) {
>     if (host.isBlank()) return
>     withContext(Dispatchers.IO) {
>         store.saveAsDismissed(host)
>         refreshFlows()
>     }
> }
>
> override suspend fun isDismissedForHost(host: String): Boolean =
>     withContext(Dispatchers.IO) { store.hasDismissedRecord(host) }
>
> override fun observeAccountsAll(): Flow<List<AuthAccountDomain>> = accountFlowAll.asStateFlow()
> ```

**Verification:**

- `Grep` — `override suspend fun markDismissed` present in `AuthSessionRepositoryImpl.kt`.
- `Grep` — `override suspend fun isDismissedForHost` present.
- `Grep` — `override fun observeAccountsAll` present.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 3/3 PASS. All three overrides implemented. Files: AuthSessionRepositoryImpl.kt (+16 LOC).

---

### Step 02.5 — Update `toAuthAccountDomain()` to accept `isDismissed` parameter

**Files:** `data/repository/AuthSessionRepositoryImpl.kt`
**Depends on:** Step 02.4

**Prompt for developer:**

> Update the private extension function `EncryptedCookieStore.AccountEntry.toAuthAccountDomain(host: String, ...) ` to accept an additional `isDismissed: Boolean = false` parameter and pass it to `AuthAccountDomain(... isDismissed = isDismissed)`. All existing call sites use the default (`false`); only the dismissed-records mapping in `refreshFlows()` passes `true`.

**Verification:**

- `Grep` — `isDismissed: Boolean = false` in `toAuthAccountDomain` signature.
- `Grep` — `isDismissed = isDismissed` in `AuthAccountDomain(` construction in that function.

**Status:** `[x] done`

**Step Log:**

- 2026-05-11 — Verification 2/2 PASS. `isDismissed: Boolean = false` in signature, `isDismissed = isDismissed` in construction. Files: AuthSessionRepositoryImpl.kt (+3 LOC).

---

## Phase Done Criteria

- [ ] Every `Step 02.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] Dev log entries added for both modified files via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `AuthSessionRepository` now exposes `markDismissed(host)`, `isDismissedForHost(host)`, `observeAccountsAll()`.
- `AuthAccountDomain.isDismissed` is set `true` for dismissed records; `false` for active sessions.
- `refreshFlows()` no longer prunes dismissed entries; `accountFlow` still contains only active sessions.
- `observeAccountsAll()` exposes both active + dismissed; Phase 05 uses this.

---

## Rollback Plan

Revert phase commit(s). No schema change or user-visible surface affected — purely an internal API addition.
