# Phase 01 - DataSource URI Contract

**Strategic spec:** [`../S0357_smb-video-playback-robustness.md`](../S0357_smb-video-playback-robustness.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Introduce a shared contract guaranteeing a non-null `getUri()` after a successful `open()` for all network playback DataSources (SMB/SFTP/FTP), and apply it so a zero-read open never surfaces as a media3 stats-wrapper NullPointerException (errorCode 2000).

---

## Prerequisites

- [ ] Pre-Implementation Blocker R1 (strategic §6.1) is Resolved - the zero-read / empty-URI root cause and the "reject zero-read open vs. guarantee stable URI" decision are known.
- [ ] Scope-Overlap Note in INDEX.md is confirmed by the owner - Phase 01 proceeds as the shared-contract delta over S0343, not a repeat of the per-class patch.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/NetworkPlaybackDataSource.kt` | New | ≤ 80 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SmbDataSource.kt` | Modified | ≤ 640 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt` | Modified | ≤ 340 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/FtpDataSource.kt` | Modified | ≤ 270 |

> `SmbDataSource.kt` is 629 lines - projected >500 after edit. Backup step required (Step 01.2 includes a timestamped copy in `temp/`). None of the three exceed 1500 lines, so no Manager split is needed.
> All three classes already extend `androidx.media3.datasource.BaseDataSource`; the new contract is a separate interface they additionally implement, not a base-class swap.

---

## Steps

### Step 01.1 - Define the shared URI contract interface

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/NetworkPlaybackDataSource.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create interface `NetworkPlaybackDataSource` in the `data.network.datasource` package. It declares the invariant from strategic ADR-1: after `open()` returns successfully, `getUri()` is non-null and stays non-null until the next `open()`; `close()` must not clear the URI identity. Document the invariant in KDoc referencing the media3 `StatsDataSource` non-null `getUri()` assertion. Add a single method `fun requireBoundUri(): Uri` (or a documented contract on `getUri()`) that throws a controlled `IOException` if the URI is unexpectedly null after open, so a contract violation is a managed open failure rather than a stats-wrapper NPE. Do not reference any flavor `BuildConfig` field.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/NetworkPlaybackDataSource.kt` exists.
- `Grep` - `interface NetworkPlaybackDataSource` matches exactly once (declaration line, not comment).
- `Grep` - `requireBoundUri` present.
- `Grep -n "Log\.d\("` on the new file returns zero hits.

**Status:** `[ ]` not done

---

### Step 01.2 - Apply the contract to SmbDataSource

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SmbDataSource.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> Before editing, create a timestamped backup of `SmbDataSource.kt` in `temp/` (file is >500 lines). Make `SmbDataSource` additionally implement `NetworkPlaybackDataSource`. Keep the existing early URI binding in `open()` and the existing close()-preserves-URI behaviour (do not regress S0343). Enforce the contract at the end of a successful `open()`: if `uri` is null after open completes, throw the controlled `IOException` from the contract instead of returning, so the media3 stats wrapper never reads a null URI. Honour the R1 decision on zero-read opens. Do not introduce any flavor `BuildConfig` guard.

**Verification:**

- `Grep` - `class SmbDataSource` line includes `NetworkPlaybackDataSource` (e.g. `Grep` for `SmbDataSource(` block followed by `NetworkPlaybackDataSource`), matches once.
- `Grep` - `requireBoundUri` or the contract enforcement call is present in `SmbDataSource.kt`.
- `Grep` - `override fun getUri(): Uri? = uri` still present (S0343 behaviour preserved).
- `Glob` - a `temp/SmbDataSource*.kt*` backup file exists.
- `Grep -n "Log\.d\("` on `SmbDataSource.kt` returns zero hits.

**Status:** `[ ]` not done

---

### Step 01.3 - Apply the contract to SftpDataSource and FtpDataSource

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SftpDataSource.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/FtpDataSource.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> Make both `SftpDataSource` and `FtpDataSource` additionally implement `NetworkPlaybackDataSource` and enforce the same end-of-open URI contract. `FtpDataSource.close()` already preserves URI identity (S0343); add the equivalent guarantee to `SftpDataSource.close()` if it is not already present. Apply the identical contract-enforcement check at the end of each `open()` path. Do not introduce any flavor `BuildConfig` guard.

**Verification:**

- `Grep` - `NetworkPlaybackDataSource` present in `SftpDataSource.kt` and in `FtpDataSource.kt`.
- `Grep` - `requireBoundUri` or the contract enforcement call present in both files.
- `Grep` - `override fun getUri(): Uri? = uri` still present in both files.
- `Grep -n "Log\.d\("` on both files returns zero hits.

**Status:** `[ ]` not done

---

### Step 01.4 - Insert BlockNeedUserTest verification tag at the open contract entry

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/datasource/SmbDataSource.kt`
**Depends on:** Step 01.2

**Prompt for developer:**

> At the contract-enforcement point at the end of `SmbDataSource.open()` (the changed flow entry for this phase), insert exactly one `Timber.d("S0357: SMB open URI contract enforced")`. This is the BlockNeedUserTest probe for the SMB open path; one tag per changed flow entry, not per line. Do not add `S0357:` to any `Timber.i/w/e` line or any log meant to remain after verification.

**Verification:**

- `Grep` - `Timber.d("S0357: SMB open URI contract enforced")` matches exactly once in `SmbDataSource.kt`.
- `Grep` - count of `Timber.d("S0357:` across all `.kt` files equals the number of changed-flow entry points declared for this phase (1 for SMB open; SFTP/FTP open entries get their own tag only if their flow changed materially - keep one per changed entry).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-01)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Public API changed (new interface) - `dev/CATALOG/app_v2.jsonl` regenerated via `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`.

---

## Handoff Notes to Next Phase

- `NetworkPlaybackDataSource` exists and is implemented by all three network DataSources; a successful `open()` now guarantees a bound URI or fails with a controlled `IOException`.
- Phase 02 builds session-level reopen/retry recovery on top of this contract - a reopen path must re-satisfy the URI contract.

---

## Rollback Plan

Revert the phase commit(s). The new interface is additive and no data migration or user-facing surface changed; reverting restores the prior per-class behaviour (S0343 patches remain in their own commits).
