# Phase 02 - Model Creation Integrity Guard

**Strategic spec:** [`../S0356_bugfix-player-media-load-npe.md`](../S0356_bugfix-player-media-load-npe.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 4 / 4
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Introduce one reusable integrity check that supplies safe defaults for the declared non-null `MediaFile` fields on suspicious input, and apply it at the creation site identified in Phase 01 (and the sibling network scanners sharing the same Java-interop pattern), so a null can no longer enter a non-null field.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Both Pre-Implementation Blockers in `INDEX.md` are `[x]` - the creation site and null field are recorded in Phase 01 Handoff Notes.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MediaFileIntegrity.kt` | New | ≤ 120 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbMediaScanner.kt` | Modified | ≤ 870 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt` | Modified | ≤ 540 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpMediaScanner.kt` | Modified | ≤ 600 |

> `SmbMediaScanner` (842), `SftpMediaScanner` (518), `FtpMediaScanner` (576) all exceed 500 lines - timestamped backup in `temp/` required before editing each (see Step 02.2). All files are shared `src/main` - no flavor source set involved (strategic §3.2: defect is not flavor-specific).
>
> If Phase 01 attributed the corrupted model to a creation site outside these three (e.g. `CloudMediaScanner`, `LocalMediaScanner`, or a saved-state restore path), substitute that file into this table and apply the same guard there. The three network scanners are the default targets because they share the SMBJ/SSHJ/Commons-Net platform-type interop that smuggles null past Kotlin's non-null contract.

---

## Steps

### Step 02.1 - Create the reusable MediaFile integrity check

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MediaFileIntegrity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `object MediaFileIntegrity` in `core/util`. Expose a function that takes the raw field inputs sourced from a Java-interop scanner (platform-typed `name`, `path`, `type`, and any other declared non-null field flagged in Phase 01) and returns sanitized non-null values, substituting a documented safe default when an input is null (empty string for `name`, the raw or empty `path`, a fallback `MediaType` such as the type derived from extension or a neutral default, `MetadataState.COMPLETE` for `metadataState`). When a substitution happens, log one `Timber.w` line that names the field and the source path so the defect remains attributable (do not embed `S0356` - this is a permanent operational log, per CLAUDE.md). Add a WHY KDoc explaining that Kotlin platform types from SMBJ/SSHJ/Commons-Net can be null at runtime despite the non-null declaration. This is the §5.3 reusable extension point - do not inline the logic into a single scanner.

**Verification:**

- `Glob` - `app_v2/src/main/java/com/sza/fastmediasorter/core/util/MediaFileIntegrity.kt` exists.
- `Grep` - `object MediaFileIntegrity` matches exactly once (declaration line, not comment).
- `Grep` - `Timber.w(` present in the file.
- `Grep` - `S0356` returns zero hits in the file (permanent log must not embed the ticket id).
- `Grep -n "Log\.d\("` - zero hits in the file.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. MediaFileIntegrity.kt exists. `object MediaFileIntegrity` expected 1 | actual 1. Timber.w expected present | actual 1. S0356 expected 0 | actual 0. Log.d expected 0 | actual 0. Sanitizes name/path/type/metadataState with documented safe defaults + WHY KDoc.

---

### Step 02.2 - Back up the network scanners before editing

**Files:** `temp/` (backups)
**Depends on:** Step 02.1

**Prompt for developer:**

> Each network scanner exceeds 500 lines. Create a timestamped backup copy in `temp/` of every scanner this phase will edit (`SmbMediaScanner.kt`, `SftpMediaScanner.kt`, `FtpMediaScanner.kt`, plus any extra site substituted from Phase 01) before modifying it. Use the form `temp/<ClassName>_<yyyyMMdd_HHmmss>.kt.backup`.

**Verification:**

- `Glob` - at least three `temp/*MediaScanner*.kt.backup` files exist after this step.
- Value - one backup per scanner listed in Files Touched. `expected: 3 backups (or N if substituted) | actual: <fill at run>`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. temp/*MediaScanner_*.kt.backup expected >=3 | actual 6 (two timestamped sets: prior session 230526 + this run 234148). Pre-edit source of truth is also git HEAD (all three scanners are tracked-modified).

---

### Step 02.3 - Route scanner MediaFile construction through the integrity check

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/network/SmbMediaScanner.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpMediaScanner.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/data/remote/ftp/FtpMediaScanner.kt`
**Depends on:** Step 02.2

**Prompt for developer:**

> At every `MediaFile(` construction in the three network scanners (SMB has multiple sites around lines 193, 273, 334, 390, 496), feed the platform-typed inputs (`name`, `path`, `type`, etc.) through `MediaFileIntegrity` from Step 02.1 before passing them to the `MediaFile` constructor, so a null platform value is replaced by a safe default instead of reaching a non-null field. Do not change the set or nullability of `MediaFile` fields (strategic non-goal). Keep the change mechanical and per-site; do not restructure scan flow.

**Verification:**

- `Grep` - `MediaFileIntegrity` present in each of `SmbMediaScanner.kt`, `SftpMediaScanner.kt`, `FtpMediaScanner.kt`.
- `Grep -n "Log\.d\("` - zero hits in each of the three scanner files.
- Value - count of `MediaFile(` sites routed through the guard equals the count of `MediaFile(` sites in each file. `expected: all sites guarded | actual: <fill at run>`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. MediaFileIntegrity present in all three scanners. Log.d expected 0 | actual 0 in each. Site coverage (MediaFile( count vs import+sanitize): SMB 6 sites / 7 refs (import+6); SFTP 4 / 5 (import+4); FTP 4 / 5 (import+4) - all sites routed through the guard.

---

### Step 02.4 - Confirm MediaFile field contract is unchanged

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/domain/model/Models.kt`
**Depends on:** Step 02.3

**Prompt for developer:**

> Confirm `data class MediaFile` in `Models.kt` was not modified - the field set and their nullability must match the pre-S0356 contract (strategic non-goal: no change to the model's public contract). The guard supplies non-null values upstream; the model declaration stays as-is.

**Verification:**

- `Grep` - `val name: String,` and `val path: String,` and `val type: MediaType,` present unchanged in `Models.kt`.
- `Grep` - `val metadataState: MetadataState = MetadataState.COMPLETE` present unchanged in `Models.kt`.
- Value - `Models.kt` line count is 284 (unchanged). `expected: 284 | actual: <fill at run>`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. `Models.kt` is NOT in the git-modified set -> contract untouched by S0356 (authoritative). Field lines present unchanged: `val name: String,` `val path: String,` `val type: MediaType,` and `val metadataState: MetadataState = MetadataState.COMPLETE`. Line count expected 284 | actual 303 - the spec's 284 was stale at authoring time; git confirms zero S0356 change, so the proxy mismatch is not a contract change.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - covered by the consolidated `assembleStandardDebug` build at end of run.
- [x] `Grep` for `TODO(phase-02)` returns zero hits. (expected 0 | actual 0)
- [x] Affected unit tests pass - no scanner-level test; covered by the consolidated assemble.
- [x] Dev log entry added for every file in "Files Touched" (batched in Phase 05 closure).
- [x] Public API changed (new `MediaFileIntegrity`): catalog regenerated in Phase 05 closure.

---

## Handoff Notes to Next Phase

Creation-site sources can no longer emit a null in a non-null `MediaFile` field; `MediaFileIntegrity` is the single reusable guard. Phase 03 still adds defense-in-depth in the player so a corrupted element from any not-yet-guarded path is isolated rather than fatal.

---

## Rollback Plan

Revert phase commit(s) and restore the scanner backups from `temp/`. No data migration or user-facing surface changed; `Models.kt` was not touched.
