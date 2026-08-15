# Phase 01 — store-multi-account

**Strategic spec:** [`../S0155_link-auth-multi-account.md`](../S0155_link-auth-multi-account.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none — foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04, Phase 05, Phase 06
**Steps done:** 0 / 5
**Started:** —
**Completed:** —

---

## Objective

Extend `EncryptedCookieStore` to a `(host, accountId)` keying model with transparent migration of legacy `domain:<host>` records; introduce `AuthAccountDomain` in the domain layer; update `AuthSessionRepository` and `AuthSessionRepositoryImpl` to expose the new multi-account API while keeping `observeDomains()` / `AuthSessionDomain` for backward compat.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done. *(foundation — none)*
- [ ] Working tree is clean or on a feature branch.
- [ ] `EncryptedCookieStore.kt` (143 LOC) read before editing — confirm existing comment invariants.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt` | Modified | ≤ 300 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt` | Modified | ≤ 90 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt` | Modified | ≤ 130 |

> `EncryptedCookieStore.kt` starts at 143 LOC; after this phase it will exceed 250 — create a timestamped backup in `temp/` before editing.

---

## Steps

### Step 01.1 — Backup EncryptedCookieStore before edit

**Files:** `temp/`
**Depends on:** — start of phase

**Prompt for developer:**

> Create a timestamped backup of `EncryptedCookieStore.kt` in `temp/` before any edit. File is 143 LOC but the post-edit file will exceed 250.

```powershell
$ts = Get-Date -Format "yyyyMMdd_HHmmss"
Copy-Item `
  "app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt" `
  "temp/EncryptedCookieStore_${ts}.backup.kt"
```

**Verification:**

- `Glob` — `temp/EncryptedCookieStore_*.backup.kt` matches at least one file.

**Status:** `[ ]` not done

---

### Step 01.2 — Extend EncryptedCookieStore with multi-account API

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Rewrite `EncryptedCookieStore.kt` to support `(host, accountId)` keying. Keep all existing public API methods as deprecated stubs that delegate to the new API, so callers in other phases compile until they are updated.
>
> **New key format:** `acct:<host>:<accountId>` (was `domain:<host>`).
>
> **Extended JSON payload** (add fields alongside existing `savedAtEpochMillis` + `cookies`):
> ```json
> {
>   "accountId": "<uuid>",
>   "displayName": "<user-visible label>",
>   "savedAtEpochMillis": 0,
>   "lastUsedAtEpochMillis": 0,
>   "cookies": [...]
> }
> ```
>
> **Migration:** Add private `migrateIfNeeded(legacyDisplayName: String)`. On first call, scan `prefs.all` for keys with prefix `LEGACY_PREFIX` (`"domain:"`). For each: read the old JSON, re-save it under `acct:<host>:__legacy__` with `accountId = "__legacy__"`, `displayName = legacyDisplayName`, the existing `savedAtEpochMillis`, and `lastUsedAtEpochMillis = 0`. Then remove the old key via `prefs.edit().remove(oldKey).apply()`. Log with `Timber.i("EncryptedCookieStore: migrated %d legacy session(s)", count)`. Migration is guarded by a `@Volatile migrated` flag — idempotent.
>
> **New public methods to add:**
> - `fun listAccounts(host: String): List<AccountEntry>` — returns all accounts for a host. `AccountEntry` is an inner data class: `data class AccountEntry(val accountId: String, val displayName: String, val savedAt: Instant?, val lastUsedAt: Instant?, val cookieCount: Int)`.
> - `fun loadForAccount(host: String, accountId: String): List<HttpCookie>` — loads cookies for a specific account.
> - `fun saveForAccount(host: String, accountId: String, displayName: String, cookies: List<HttpCookie>)` — saves with full account context.
> - `fun deleteForAccount(host: String, accountId: String)` — deletes a specific account.
> - `fun updateDisplayName(host: String, accountId: String, newName: String)` — rewrites displayName in-place (load full JSON, update `displayName` field, re-save).
> - `fun markLastUsed(host: String, accountId: String)` — updates `lastUsedAtEpochMillis = System.currentTimeMillis()`.
> - `fun listAllAccounts(): List<Pair<String, AccountEntry>>` — returns all `(host, entry)` pairs across all hosts. Used by `AuthSessionRepositoryImpl.snapshot()`.
>
> **Deprecated stubs (keep compiling until Phase 02 updates callers):**
> - `fun loadFor(domain: String)` — delegates to the account with the highest `lastUsedAtEpochMillis` for that host; if none, to `__legacy__`; if none, returns `emptyList()`.
> - `fun saveFor(domain: String, cookies: List<HttpCookie>)` — saves as `__legacy__` account with empty `displayName`.
> - `fun deleteFor(domain: String)` — deletes ALL accounts for the host.
> - `fun listDomains()` — returns distinct hosts across all `acct:` keys.
> - `fun savedAt(domain: String)` — returns `savedAt` of the most-recently-used account for that host.
>
> Annotate deprecated stubs with `@Deprecated("Use account-scoped methods", level = DeprecationLevel.WARNING)`.
>
> Add `Timber.d` logging at `Timber.v` level (via `LinkDownloadTrace.verbose`) only for the same paths where it currently exists. Do not add new `Log.*` calls.

**Verification:**

- `Grep` — `fun listAccounts(host: String)` present in `EncryptedCookieStore.kt`.
- `Grep` — `fun loadForAccount(host: String, accountId: String)` present.
- `Grep` — `fun saveForAccount(host: String, accountId: String, displayName: String` present.
- `Grep` — `fun deleteForAccount(host: String, accountId: String)` present.
- `Grep` — `fun updateDisplayName(host: String, accountId: String, newName: String)` present.
- `Grep` — `fun markLastUsed(host: String, accountId: String)` present.
- `Grep` — `fun listAllAccounts()` present.
- `Grep` — `migrateIfNeeded` present.
- `Grep` — `LEGACY_PREFIX` constant present.
- `Grep` — `data class AccountEntry` present inside `EncryptedCookieStore`.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 01.3 — Add AuthAccountDomain and update AuthSessionRepository interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `AuthAccountDomain` data class and extend the `AuthSessionRepository` interface with multi-account operations. Keep existing `saveSession(domain, cookies)` / `deleteSession(domain)` / `hasSession(domain)` / `observeDomains()` signatures — mark them deprecated with `DeprecationLevel.WARNING`. Existing callers compile until updated in later phases.
>
> **New data class** (add alongside `AuthSessionDomain`):
> ```kotlin
> data class AuthAccountDomain(
>     val host: String,
>     val accountId: String,
>     val displayName: String,
>     val cookieCount: Int,
>     val savedAt: Instant,
>     val lastUsedAt: Instant?,
> )
> ```
>
> **New interface methods:**
> - `fun observeAccounts(): Flow<List<AuthAccountDomain>>` — all accounts across all hosts, sorted by host then `displayName`.
> - `suspend fun saveSession(host: String, accountId: String, displayName: String, cookies: List<HttpCookie>)` — note: overloads the old method; Kotlin will dispatch by arity.
> - `suspend fun deleteAccount(host: String, accountId: String)`
> - `suspend fun listAccountsForHost(host: String): List<AuthAccountDomain>`
> - `suspend fun markLastUsed(host: String, accountId: String)`
> - `suspend fun updateDisplayName(host: String, accountId: String, newName: String)`
> - `suspend fun hasAnySession(host: String): Boolean` — replaces `hasSession(domain)`.

**Verification:**

- `Grep` — `data class AuthAccountDomain` present in `AuthSessionRepository.kt`.
- `Grep` — `fun observeAccounts()` present.
- `Grep` — `suspend fun hasAnySession(host: String)` present.
- `Grep` — `suspend fun deleteAccount(host: String, accountId: String)` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 01.4 — Update AuthSessionRepositoryImpl

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt`
**Depends on:** Step 01.3

**Prompt for developer:**

> Implement all new `AuthSessionRepository` methods in `AuthSessionRepositoryImpl`.
>
> - Change `private val flow: MutableStateFlow<List<AuthSessionDomain>>` → `private val flow: MutableStateFlow<List<AuthAccountDomain>>` for `observeAccounts()`. Add a separate `private val legacyFlow: MutableStateFlow<List<AuthSessionDomain>>` for the deprecated `observeDomains()`.
> - `snapshot()` private helper → call `store.migrateIfNeeded(legacyDisplayName)` first (pass localized string `"Account 1"` — but the impl has no Context; use a hardcoded English fallback `"Account 1"` since the store migration only runs once and the user can rename later). Then call `store.listAllAccounts()` to build `List<AuthAccountDomain>`.
> - `observeAccounts()` → returns `flow.asStateFlow()`.
> - `observeDomains()` (deprecated) → returns `legacyFlow.asStateFlow()` built by collapsing multi-account list to one `AuthSessionDomain` per host (the account with the highest `savedAt`).
> - `saveSession(host, accountId, displayName, cookies)` → calls `store.saveForAccount(...)`, then refreshes both flows.
> - `saveSession(domain, cookies)` (deprecated) → calls `store.saveFor(domain, cookies)`, then refreshes both flows.
> - `deleteAccount(host, accountId)` → calls `store.deleteForAccount(...)`, refreshes.
> - `deleteSession(domain)` (deprecated) → calls `store.deleteFor(domain)`, refreshes.
> - `listAccountsForHost(host)` → calls `store.listAccounts(host)`, maps to `AuthAccountDomain`.
> - `markLastUsed(host, accountId)` → calls `store.markLastUsed(...)` on IO, refreshes.
> - `updateDisplayName(host, accountId, newName)` → calls `store.updateDisplayName(...)` on IO, refreshes.
> - `hasAnySession(host)` → `store.listAccounts(host).isNotEmpty()`.
> - `hasSession(domain)` (deprecated) → delegates to `hasAnySession(domain)`.
>
> Flow refresh: one private `fun refreshFlows()` that rebuilds both `flow` and `legacyFlow` from `store.listAllAccounts()`.

**Verification:**

- `Grep` — `override fun observeAccounts()` present in `AuthSessionRepositoryImpl.kt`.
- `Grep` — `override suspend fun hasAnySession(host: String)` present.
- `Grep` — `override suspend fun deleteAccount(host: String, accountId: String)` present.
- `Grep` — `fun refreshFlows()` present.
- `Grep` — `Log\.d\(` returns zero hits in this file.

**Status:** `[ ]` not done

---

### Step 01.5 — Dev log for Phase 01 files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 01.4

**Prompt for developer:**

> Run `add_to_dev_log.ps1` for the three modified files.

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt" "S0155 Phase 01" "Add multi-account (host, accountId) storage with legacy migration"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt" "S0155 Phase 01" "Add AuthAccountDomain and multi-account repository interface"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt" "S0155 Phase 01" "Implement multi-account repository methods"
```

**Verification:**

- `Grep` — `S0155 Phase 01` matches at least 3 lines in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entries added for all 3 files in "Files Touched".
- [ ] `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` run; `.jsonl` updated.

---

## Handoff Notes to Next Phase

- `EncryptedCookieStore` exposes full `(host, accountId)` API; legacy `domain:` keys are migrated on first access.
- `AuthSessionRepository` has `observeAccounts()`, `hasAnySession()`, `listAccountsForHost()`, `saveSession(host, accountId, displayName, cookies)`, `deleteAccount()`, `markLastUsed()`, `updateDisplayName()`.
- Deprecated `saveSession(domain, cookies)` / `deleteSession(domain)` / `hasSession(domain)` / `observeDomains()` still compile — updated in later phases.
- Phase 02 adds `LinkDownloadSessionContext` so `LinkDownloadCookieJar` and `InvisibleWebViewExtractionStrategy` inject the right account's cookies.

---

## Rollback Plan

Revert phase commit(s). No data migration runs until first app launch after the update — reverting before that leaves existing data untouched.
