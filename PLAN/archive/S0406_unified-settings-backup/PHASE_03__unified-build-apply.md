# Phase 03 - Unified build/apply use cases

**Strategic spec:** [`../S0406_unified-settings-backup.md`](../S0406_unified-settings-backup.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02
**Blocks:** Phase 04, 05, 06
**Steps done:** 0 / 2
**Started:** -
**Completed:** -

---

## Objective

Introduce one builder use case (state → payload, incl. secrets) and one applier use case (payload → state, incl. secrets and merge), centralizing logic duplicated across local and Drive paths.

---

## Prerequisites

- [ ] Phase 02 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BuildBackupPayloadUseCase.kt` | New | ≤ 200 |
| `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyBackupPayloadUseCase.kt` | New | ≤ 320 |

---

## Steps

### Step 03.1 - BuildBackupPayloadUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/BuildBackupPayloadUseCase.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `BuildBackupPayloadUseCase` (constructor-injected: `SettingsRepository`, `ResourceRepository`, `FavoritesDao`, `ScheduledOperationRepository`, `NetworkCredentialsRepository`, `AuthSessionRepository`). `suspend operator fun invoke(): BackupPayload` gathers settings, resources, favorites, scheduled ops via existing `BackupMapper` calls, plus `networkCredentials` from `credentialsRepository.getAllCredentials().first().map { BackupMapper.toBackupNetworkCredential(it) }` and `webAuthSessions` from `authSessionRepository.exportSessions().map { BackupMapper.toBackupWebAuthSession(it) }`. Use `BuildConfig.VERSION_CODE/NAME` for metadata. No I/O serialization here - returns the domain payload only.

**Verification:**

- `Glob` - `BuildBackupPayloadUseCase.kt` exists.
- `Grep` - `class BuildBackupPayloadUseCase` matches once.
- `Grep` - `authSessionRepository.exportSessions()` present.
- `Grep` - `credentialsRepository.getAllCredentials()` present.

**Status:** `[ ]` not done

---

### Step 03.2 - ApplyBackupPayloadUseCase

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ApplyBackupPayloadUseCase.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Create `ApplyBackupPayloadUseCase` (inject `SettingsRepository`, `ResourceRepository`, `FavoritesDao`, `ScheduledOperationRepository`, `NetworkCredentialsRepository`, `AuthSessionRepository`, `WorkManagerScheduler`). `suspend operator fun invoke(payload: BackupPayload): RestoreSummary`. Reject `payload.version > BackupPayload.CURRENT_VERSION`. Apply in order: settings (merge via `toAppSettings`), resources (merge by path+type / cloud key, keep id on update), network credentials (merge by `credentialId`, password from backup wins), favorites (dedup by uri, resolve resource by path), scheduled ops (resolve resources, reschedule enabled), web sessions (`authSessionRepository.importSessions`). Reuse the dedup helpers - do not invent new keys (see research/03). Return counts in `RestoreSummary`. Tolerate null sections.

**Verification:**

- `Glob` - `ApplyBackupPayloadUseCase.kt` exists.
- `Grep` - `class ApplyBackupPayloadUseCase` matches once.
- `Grep` - `data class RestoreSummary` present.
- `Grep` - `authSessionRepository.importSessions` present.
- `Grep` - `credentialsRepository` present.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` is `[x] done`.
- [ ] Project compiles - run `/build`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] `Grep -n "Log\.d\("` returns zero hits in new files.
- [ ] Dev log entry added for both new files.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.

---

## Handoff Notes to Next Phase

Single source of truth for build/apply exists. Phases 04 and 05 retarget the two storage paths onto these use cases.

---

## Rollback Plan

Revert phase commit - new use cases not yet referenced by any caller.
