# Phase 03 - Cast pipeline wiring

**Strategic spec:** [`../S1558_cast-single-eye-crop.md`](../S1558_cast-single-eye-crop.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 4 / 4
**Started:** -
**Completed:** -

---

## Objective

Run the transcoder inside `resolveAndSend` between file resolution and `proxyServer.serveFile`, so a cropped copy is what the receiver fetches; delete that copy with the session and retire the deferral comment.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.
- [ ] `temp/CODE.LOCK` acquired via `scripts/utils/enter-code-lock.ps1 -Reason "S1558 phase 03"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastMediaManagerImpl.kt` | Modified | ≤ 470 |

> Backup / split thresholds: see Constraints (>500 LOC → backup step, >1500 LOC → split via Manager pattern). `CastMediaManagerImpl.kt` is 410 LOC and this phase keeps it under 470, still clear of the 500-LOC backup line and far from the 1500-LOC split line.
>
> **Flavor placement.** All edits are inside `src/castEnabled/`; `src/main` gains nothing and no `BuildConfig.IS_*` guard appears anywhere.

---

## Steps

### Step 03.1 - Insert the crop between file resolution and `serveFile`

**Files:** `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastMediaManagerImpl.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> In `resolveAndSend`, after the `localFile == null || !localFile.exists()` guard and before `proxyServer.serveFile(localFile)`, call the transcoder when `stereoCrop` is non-null and `file.type == MediaType.VIDEO`. Hold the result in a `castFile` local that falls back to `localFile` when the transcoder returns `null`, and pass `castFile` to both `serveFile` and `LocalCastProxyServer.mimeType`. Instantiate `CastStereoCropTranscoder` as a field alongside `proxyServer` and pass `context.cacheDir` as `outputDir`. Leave the `CastStreamDecision.Direct` early return untouched.

**Why:**

Strategic §5 places the substitution immediately before `serveFile` so the receiver fetches the cropped copy, and §3.2 excludes live streams from the feature because they never reach the proxy, which the existing early return already enforces.

**Verification:**

- `Grep` - `CastStereoCropTranscoder` matches in `CastMediaManagerImpl.kt`.
- `Grep` - `proxyServer.serveFile(castFile)` present.
- `Grep` - `LocalCastProxyServer.mimeType(castFile)` present.
- `Grep` - `serveFile(localFile)` returns zero hits (the old call is gone, not duplicated).
- `Grep` - `CastStreamDecision.Direct` still present and still returning before the crop block.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Cast pipeline wiring compiles: scoped detekt passed and a.ps1 fk standard debug passed.

---

### Step 03.2 - Track the cropped copy and delete it when the session ends

**Files:** `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastMediaManagerImpl.kt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add a `@Volatile private var croppedCastFile: File?` field, set it when the transcoder returns a file, and delete plus null it in a private `discardCroppedFile()` helper. Call that helper at the top of the crop block (so casting a second file does not leak the first copy), from `handleSessionEnd`, and from `release`. Cancel any in-flight transcode from `release` the same way `downloadJob` is cancelled.

**Why:**

Strategic §5 requires the temporary copy to be removed with the rest of the Cast cache, because otherwise the roughly doubled cache the strategic spec accepts for the duration of a session becomes permanent growth instead.

**Verification:**

- `Grep` - `croppedCastFile` matches in `CastMediaManagerImpl.kt`.
- `Grep` - `discardCroppedFile` matches at least four times (declaration plus three call sites).
- `Grep` - `discardCroppedFile` present inside the `handleSessionEnd` body.
- `Grep` - `discardCroppedFile` present inside the `release` body.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Cast pipeline wiring compiles: scoped detekt passed and a.ps1 fk standard debug passed.

---

### Step 03.3 - Guard against casting a crop the panel is not showing

**Files:** `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastMediaManagerImpl.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Log the resolved decision once per cast with `Timber.d`, naming the crop value, whether a cropped copy was produced, and the file actually served. Use the existing `CastMediaManager:` message prefix. Do not add an `Sxxxx:` prefix to this line - it is a permanent diagnostic, not a probe.

**Why:**

Strategic §11 criterion 1 turns on the receiver showing the same eye as the panel, and a single line naming the crop and the served file is what makes a wrong-eye report diagnosable from a log instead of reproducible only on the owner's device.

**Verification:**

- `Grep` - `CastMediaManager: casting` present with the crop value interpolated.
- `Grep` - `S1558:` returns zero hits in `app_v2/src/` (CLAUDE.md - `Sxxxx:` is reserved for temporary probes; the ticket is not in `BlockNeedUserTest` at this point).
- `Grep` - `Log\.d\(` returns zero hits in the modified file.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Cast pipeline wiring compiles: scoped detekt passed and a.ps1 fk standard debug passed.

---

### Step 03.4 - Replace the S1499 deferral comment

**Files:** `app_v2/src/castEnabled/java/com/sza/fastmediasorter/core/cast/CastMediaManagerImpl.kt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Delete the seven-line `S1499:` comment above the cast log line and write a short replacement describing what the code now does: the panel's single-eye crop is reproduced for Cast by a Media3 `Transformer` pass because no `MediaInfo` field carries a crop hint the default receiver honours, the crop is skipped above the duration ceiling, and live streams bypass this path entirely. Reference S1558. Keep it under six lines and state the constraint, not the history.

**Why:**

Strategic §11 criterion 5 requires the comment to stop describing a deferral and point at the implemented path, and the current text additionally misdescribes the panel crop as a GL effect when `PanelStereoCropApplier` replaced that with a `TextureView` matrix under S0264.

**Verification:**

- `Grep` - `Deferred until` returns zero hits in `CastMediaManagerImpl.kt`.
- `Grep` - `FFmpeg` returns zero hits in `CastMediaManagerImpl.kt`.
- `Grep` - `S1558` present in the replacement comment.
- `.\a.ps1 fk` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Cast pipeline wiring compiles: scoped detekt passed and a.ps1 fk standard debug passed.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] If public API changed: `dev/CATALOG/<module>.jsonl` regenerated via `pwsh -NoProfile -File dev/CATALOG/scripts/scan.ps1 -Module app_v2`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

The feature is functionally complete and silent: a stereo video cast with the panel flag on now reaches the receiver cropped, and every failure path already falls back to the original file. What is missing is that the user is told nothing while the encode runs, which is Phase 04's whole subject.

---

## Rollback Plan

Revert phase commit(s) - no data migration and no persisted state. The cropped copy lives in `cacheDir`, so a revert mid-session leaves at most one stale cache file the OS reclaims.
