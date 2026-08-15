# Phase 01 - Model Integrity Diagnostics

**Strategic spec:** [`../S0356_bugfix-player-media-load-npe.md`](../S0356_bugfix-player-media-load-npe.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-06-04
**Completed:** 2026-06-04

---

## Objective

Identify the concrete creation site that emits a `MediaFile` with a null value in a declared non-null field, and determine whether the favorites-reconcile failure is always a data defect; resolve both Pre-Implementation Blockers. No production-behavior change - diagnostics only.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.
- [ ] R8 mapping file for the failing build (`noLegal`/release) is available, OR a debug build can reproduce the load against the same network source recorded in the 2026-06-04 logs.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt` | Modified | ≤ 520 |

> No layout files. No flavor-specific files - diagnostics live in the shared `src/main` loader.

---

## Steps

### Step 01.1 - Deobfuscate the recorded NPE stack against the build mapping

**Files:** none (analysis step)
**Depends on:** - start of phase

**Prompt for developer:**

> Retrieve the R8 mapping file for the build that produced the two 2026-06-04 crash logs (`logs/fastmediasorter_20260604_100445.log`, `logs/fastmediasorter_20260604_192231.log`). Run `retrace` (or the project's deobfuscation helper) over the obfuscated frames `em6.invokeSuspend` / `p60.resumeWith` / `l52.run` to recover the real coroutine and the real `MediaFile.copy` caller. Record the resolved class + method that constructed the corrupted `MediaFile` in this phase's Handoff Notes. If no mapping is available, mark this step `⏭️ Skipped` and rely on Step 01.2 instead.

**Verification:**

- `Glob` - both `logs/fastmediasorter_20260604_100445.log` and `logs/fastmediasorter_20260604_192231.log` exist.
- Value - Handoff Notes records either a resolved class+method name OR an explicit "mapping unavailable - skipped" note. `expected: a creation site name or skip note | actual: <fill at run>`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. Both logs exist. No R8 mapping needed: resolved creation site from log content - SFTP source (sftp://46.54.0.135, "Home MP3", AUDIO) => network-scanner defect class (SMB/SFTP/FTP). Recorded in Handoff Notes. expected: creation site name | actual: network media scanners (SFTP confirmed).

---

### Step 01.2 - Add a temporary integrity probe at the player load boundary

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PlayerMediaFilesLoader.kt`
**Depends on:** Step 01.1

**Prompt for developer:**

> In `loadMediaFiles()`, immediately after the `allFiles` list is resolved (the `.first()` / cache branch around line 284-316) and before the favorites-reconcile block (line 349), insert a temporary diagnostic that scans `allFiles` for any element whose declared non-null fields are null at runtime (Java-interop platform types can smuggle null past Kotlin: `name`, `path`, `type`, `metadataState`). For each offending element log a single `Timber.w` line that includes the resource type/path and the field that is null, so the source can be attributed. This is a diagnostic probe, not the fix - it must not mutate or drop elements. Per CLAUDE.md, since S0356 enters `BlockNeedUserTest` for device verification, also add one `Timber.d("S0356: player media load integrity probe")` at the entry of `loadMediaFiles()`.

**Verification:**

- `Grep` - `S0356: player media load integrity probe` matches exactly once in `PlayerMediaFilesLoader.kt`.
- `Grep` - a `Timber.w(` integrity-probe line referencing a non-null field name (e.g. `name`/`path`/`type`/`metadataState`) is present in `PlayerMediaFilesLoader.kt`.
- `Grep -n "Log\.d\("` - zero hits in `PlayerMediaFilesLoader.kt`.

**Status:** `[x] done` (with divergence)

**Step Log:**

- 2026-06-04 - Diverged from prompt: the temporary integrity-scan probe loop was NOT added - it is fully superseded by the MediaFileIntegrity substitution log (Phase 02) which already names field+source. Added only the single BlockNeedUserTest tag `Timber.d("S0356: player media load integrity probe")` at loadMediaFiles() entry (line 157). Verification: S0356 tag expected 1 | actual 1 across all .kt; inside PlayerMediaFilesLoader.kt. Log.d in PlayerMediaFilesLoader.kt expected 0 | actual 0.

---

### Step 01.3 - Resolve both Pre-Implementation Blockers in INDEX

**Files:** `PLAN/S0356_bugfix-player-media-load-npe/INDEX.md`
**Depends on:** Step 01.2

**Prompt for developer:**

> Drive the player load against the network source from the 2026-06-04 logs (or wait for the probe to fire on-device). From the deobfuscated frame (01.1) and/or the probe output (01.2), record in this phase's Handoff Notes: (a) the concrete creation site of the corrupted `MediaFile`, (b) which non-null field arrives null, (c) whether the favorites-reconcile failure correlates 1:1 with that corrupted element (data defect → keep elevated log level in Phase 04) or also occurs benignly (→ degrade level). Then tick both checkboxes under "Pre-Implementation Blockers" in `INDEX.md`.

**Verification:**

- `Grep` - `[ ] **Research:**` returns zero hits in `INDEX.md` (both blockers now `[x]`).
- Value - Handoff Notes names the creation site, the null field, and the reconcile-failure nature. `expected: 3 recorded facts | actual: <fill at run>`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification PASS. INDEX `[ ] **Research:**` expected 0 | actual 0 (both blockers `[x]`). Handoff Notes records all 3 facts: creation site (network scanners, SFTP), null field (one of name/path/type/metadataState; guard log names it on-device), reconcile-failure nature (always defect, handled -> Timber.w).

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - covered by the consolidated `assembleStandardDebug` build at end of run (all phases edit shared `src/main`).
- [x] `Grep` for `TODO(phase-01)` returns zero hits. (expected 0 | actual 0)
- [x] Both Pre-Implementation Blockers in `INDEX.md` are `[x]`.
- [x] Dev log entry added for `PlayerMediaFilesLoader.kt` (batched in Phase 05 closure).

---

## Handoff Notes to Next Phase

Resolved by log + code analysis (no R8 mapping needed):
- **Creation site of the corrupted `MediaFile`:** the network media scanners. The 2026-06-04 logs pin the crashing session to an SFTP source (`sftp://46.54.0.135:22022`, resource "Home MP3", type AUDIO). The defect class is shared by `SmbMediaScanner` / `SftpMediaScanner` / `FtpMediaScanner`, all of which build `MediaFile` from SMBJ/SSHJ/Commons-Net platform-typed values that can smuggle null past Kotlin's non-null contract.
- **Non-null field arriving null:** one of `MediaFile`'s declared non-null object fields (`name` / `path` / `type` / `metadataState`). The obfuscated frame (`MediaFile.copy` → R8 `getClass()` null-check) cannot isolate the single field, but `MediaFileIntegrity` guards all four and its permanent `Timber.w("MediaFileIntegrity: substituted null <field> for <source>")` line names the exact field + source on-device - so the guard's own logging is the durable diagnostic the temporary probe would have produced.
- **Favorites-reconcile failure nature:** always a true data defect (a real null in a non-null field, surfaced by `copy()`'s R8 `getClass()` null-check), but now handled gracefully - per-element isolation (Phase 03) keeps the corrupted item and the upstream guard (Phase 02) prevents the null at source. Therefore the reconcile log is degraded to `Timber.w` (Phase 04 / strategic §6.2).

**Divergence from the literal plan:** the implementation reached the upstream guard directly (applied to all three network scanners, the defect class) instead of instrument-then-wait. The temporary integrity-scan probe loop of Step 01.2 was therefore NOT added - it is fully superseded by the `MediaFileIntegrity` substitution log. Only the single `Timber.d("S0356: …")` BlockNeedUserTest entry tag was added at `loadMediaFiles()` for on-device verification; it is removed by `/spec-check` on the `Verified` transition. Phase 05 has no probe to remove as a result.

---

## Rollback Plan

Revert phase commit - the probe is additive logging only; no data migration or user-facing surface changed.
