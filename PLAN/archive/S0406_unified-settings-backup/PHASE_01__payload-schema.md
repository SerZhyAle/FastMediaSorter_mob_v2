# Phase 01 - Payload schema

**Strategic spec:** [`../S0406_unified-settings-backup.md`](../S0406_unified-settings-backup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04, 05, 06
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Extend the backup payload data model to version 5 with secret-bearing sections (network credentials, web auth sessions) and global default network credentials; no behavior change.

---

## Prerequisites

- [ ] Working tree clean or on feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt` | Modified | ≤ 320 |

---

## Steps

### Step 01.1 - Bump payload version and add new section lists

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `BackupPayload`, bump `CURRENT_VERSION` to `5`. Add two nullable fields (default null, for Gson forward-compat): `networkCredentials: List<BackupNetworkCredential>? = null` and `webAuthSessions: List<BackupWebAuthSession>? = null`. Keep existing fields and ordering.

**Verification:**

- `Grep` - `const val CURRENT_VERSION = 5` matches once.
- `Grep` - `val networkCredentials: List<BackupNetworkCredential>?` present.
- `Grep` - `val webAuthSessions: List<BackupWebAuthSession>?` present.

**Status:** `[ ]` not done

---

### Step 01.2 - Add BackupNetworkCredential, BackupWebAuthSession, BackupCookie

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add three data classes with safe defaults. `BackupNetworkCredential(credentialId, type, server, port, username, domain, shareName, sshPrivateKey, accountId, password)` - `password` and `sshPrivateKey` are plaintext (per ADR-2). `BackupWebAuthSession(host, accountId, displayName, userAgent, savedAtEpochMillis, lastUsedAtEpochMillis, cookies: List<BackupCookie>)`. `BackupCookie(name, value, domain, path, secure, httpOnly, expiresAtEpochMillis: Long?)`. All fields default to empty/zero/null so Gson tolerates missing nodes.

**Verification:**

- `Grep` - `data class BackupNetworkCredential` matches once.
- `Grep` - `data class BackupWebAuthSession` matches once.
- `Grep` - `data class BackupCookie` matches once.

**Status:** `[ ]` not done

---

### Step 01.3 - Carry global default network credentials in BackupSettings

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BackupData.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `defaultUser: String = ""` and `defaultPassword: String = ""` to `BackupSettings` (previously excluded). These carry the global network default login per max-portability requirement.

**Verification:**

- `Grep` - `val defaultUser: String` present in `BackupData.kt`.
- `Grep` - `val defaultPassword: String` present in `BackupData.kt`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for `BackupData.kt`.

---

## Handoff Notes to Next Phase

New DTOs exist but no mapper reads/writes them yet. Phase 02 wires `BackupMapper` and session export/import.

---

## Rollback Plan

Revert phase commit - pure additive data-model change, no migration or user surface.
