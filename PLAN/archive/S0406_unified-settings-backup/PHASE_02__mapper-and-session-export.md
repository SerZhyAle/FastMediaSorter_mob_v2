# Phase 02 - Mapper and session export

**Strategic spec:** [`../S0406_unified-settings-backup.md`](../S0406_unified-settings-backup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Map the new payload sections to/from domain models and expose web-auth session export/import on the repository.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt` | Modified | ≤ 640 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt` | Modified | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt` | Modified | ≤ 340 |

---

## Steps

### Step 02.1 - Map new payload sections in BackupMapper

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupMapper.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `toBackupNetworkCredential(entity)` reading the decrypted `entity.password` and `entity.decryptedSshPrivateKey` into plaintext fields, and `toNetworkCredentialsEntity(backup)` rebuilding via `NetworkCredentialsEntity.create(..)` so the password re-encrypts. Add `toBackupWebAuthSession(raw)` / `toRawAuthSession(backup)` converting between `BackupWebAuthSession`/`BackupCookie` and `java.net.HttpCookie` (map expiry to/from `maxAge` using a captured `savedAt`). Extend `toBackupSettings` to write `defaultUser`/`defaultPassword`, and `toAppSettings` to read them back.

**Verification:**

- `Grep` - `fun toBackupNetworkCredential` present.
- `Grep` - `fun toNetworkCredentialsEntity` present.
- `Grep` - `fun toBackupWebAuthSession` present.
- `Grep` - `defaultUser = settings.defaultUser` present in `BackupMapper.kt`.

**Status:** `[ ]` not done

---

### Step 02.2 - Add export/import session contract to repository interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add `suspend fun exportSessions(): List<RawAuthSession>` and `suspend fun importSessions(sessions: List<RawAuthSession>)`. Declare `data class RawAuthSession(host, accountId, displayName, userAgent: String?, savedAtEpochMillis: Long, lastUsedAtEpochMillis: Long, cookies: List<HttpCookie>)` in this file. Comment WHY only at the interface: export feeds the unified backup payload.

**Verification:**

- `Grep` - `suspend fun exportSessions(): List<RawAuthSession>` present.
- `Grep` - `suspend fun importSessions(` present.
- `Grep` - `data class RawAuthSession` present.

**Status:** `[ ]` not done

---

### Step 02.3 - Implement export/import over the cookie store

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> Implement `exportSessions()`: from `store.listAllAccounts()` keep `type == TYPE_ACTIVE` with `cookieCount > 0`, and for each build `RawAuthSession` using `store.loadForAccount(host, accountId)` and `store.loadUserAgentForAccount(host, accountId)`. Implement `importSessions()`: for each non-empty session call `store.saveForAccount(host, accountId, displayName, cookies, userAgent)`, then `refreshFlows()`. Run both on `Dispatchers.IO`.

**Verification:**

- `Grep` - `override suspend fun exportSessions` present.
- `Grep` - `override suspend fun importSessions` present.
- `Grep` - `store.loadForAccount` present in `AuthSessionRepositoryImpl.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 02.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in modified files.
- [ ] Dev log entry added for every file in "Files Touched".
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed).

---

## Handoff Notes to Next Phase

Mapper can now translate every section incl. secrets; repository can dump/restore web sessions. Phase 03 composes these into single build/apply use cases.

---

## Rollback Plan

Revert phase commit - additive mapper methods and repository methods; no caller depends on them yet.
