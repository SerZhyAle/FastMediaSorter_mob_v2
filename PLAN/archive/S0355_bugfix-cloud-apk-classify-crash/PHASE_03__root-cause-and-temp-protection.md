# Phase 03 - Root cause and temp-copy protection

**Strategic spec:** [`../S0355_bugfix-cloud-apk-classify-crash.md`](../S0355_bugfix-cloud-apk-classify-crash.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 3 done, 1 skipped
**Started:** 2026-06-04
**Completed:** 2026-06-04

> **GATED PHASE.** Both Pre-Implementation Blockers in [`INDEX.md`](INDEX.md) (strategic §6.1 root cause, §6.2 interception sufficiency) must be checked before this phase starts. Phases 01-02 already remove the crash and the cascade; this phase only addresses the residual "classification silently degrades to NOT_VR" risk if the temp copy keeps vanishing.

---

## Objective

From the resolved research, document the root cause of `cloud_download_*.tmp` disappearance and either (a) implement a minimal temp-copy protection that shrinks the deletion window for VR APK classification, or (b) record that interception alone is sufficient and skip the protection sub-step.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] INDEX Pre-Implementation Blocker §6.1 (root cause identified) is checked.
- [ ] INDEX Pre-Implementation Blocker §6.2 (interception sufficiency decided) is checked.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0355_bugfix-cloud-apk-classify-crash.md` | Modified (strategic §6 status flip) | n/a |
| `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt` | Modified (only if §6.2 ⇒ protect) | ≤ 150 |

> The protection candidate, if pursued, stays in the noLegal source set (VR classification owns its `vr_apk_classification` cache dir). `UnifiedFileCache` exposes no public pin/busy API and the cloud temp file is created in `context.cacheDir` directly, so a generic "mark busy" hook does not exist - the realistic minimal protection is to narrow the window inside `resolveCloudArchive` (e.g. validate the reusable cache file immediately after download, before any further await). Do not add a `BuildConfig` guard; do not move this into `src/main`.

---

## Steps

### Step 03.1 - Record the resolved root cause in strategic §6.1

**Files:** `PLAN/S0355_bugfix-cloud-apk-classify-crash.md`
**Depends on:** - start of phase (after blockers checked)

**Prompt for developer:**

> Update strategic §6 item 1: change `**Статус:** Open` to `**Статус:** Resolved` and append a one-line conclusion naming the confirmed deletion mechanism (background temp cleanup, cache-size eviction, OS storage pressure, or test-build cache clear). Candidate culprit to investigate first: `app_v2/src/main/java/com/sza/fastmediasorter/domain/transfer/TempFileManager.kt` (it owns temp-file lifecycle) and any periodic cache-trim worker. Russian text in the strategic file; apply `..` / `ё` author style.

**Verification:**

- `Grep` - in `PLAN/S0355_bugfix-cloud-apk-classify-crash.md`, the §6.1 block no longer contains `Статус:** Open` for item 1 (expected: `Статус:** Resolved`).
- `Grep` - a non-empty conclusion line was added under §6.1.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification 2/2 PASS. §6.1 Статус: expected Resolved | actual Resolved. Conclusion: OS storage-pressure eviction of getCacheDir(); ruled out app-side cleaners (CleanupOrphanedTempFilesUseCase = *.temp_copy only; TempFileManager.cleanupOldTempFiles excludes cloud_download_ prefix + 24h floor).

---

### Step 03.2 - Record the interception-sufficiency decision in strategic §6.2 and set the protection verdict

**Files:** `PLAN/S0355_bugfix-cloud-apk-classify-crash.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Update strategic §6 item 2: flip `**Статус:** Open` to `**Статус:** Resolved` and state the verdict explicitly - either "interception sufficient, no temp protection" or "temp protection warranted". This verdict decides whether Step 03.3 runs or is marked `⏭️ Skipped` in INDEX. Russian text, author style.

**Verification:**

- `Grep` - §6.2 block no longer contains `Статус:** Open` (expected: `Статус:** Resolved`).
- `Grep` - the verdict line contains either `interception` / `перехват` (sufficient) or `protection` / `защит` (warranted) - expected: exactly one of the two outcomes recorded.

**Status:** `[x] done`

**Step Log:**

- 2026-06-04 - Verification 2/2 PASS. §6.2 Статус: expected Resolved | actual Resolved. Verdict = "перехват достаточен" (interception sufficient); no temp protection. Rationale: OS cacheDir eviction is unfixable in-app without relocating temps out of cacheDir (out of scope); resolver already re-validates cache file post-download. => Step 03.3 Skipped.

---

### Step 03.3 - (Conditional) Implement minimal temp-copy protection in the resolver

**Files:** `app_v2/src/noLegal/java/com/sza/fastmediasorter/ui/browse/managers/VrApkArchiveResolver.kt`
**Depends on:** Step 03.2

**Prompt for developer:**

> Run ONLY if Step 03.2 recorded "temp protection warranted"; otherwise mark this step `⏭️ Skipped` in INDEX and skip. If pursued: in `resolveCloudArchive`, narrow the deletion window between download and use - validate `cacheFile.exists() && cacheFile.length() > 0L` immediately after `downloadFromCloudToPublic` returns and before yielding control further, and add a `Timber.w(..)` diagnostic (no ticket id) if the just-written cache file is already gone. Keep the existing reuse short-circuit (`cacheFile.exists() && isFile && length > 0`) at the top. Stay in the noLegal source set; no new public API, no `BuildConfig` guard, no `src/main` edit.

**Verification (only when executed; if skipped, mark step `⏭️ Skipped` and record the skip in INDEX Change Log):**

- `Grep` - `cacheFile.length() > 0L` matches at least twice in `VrApkArchiveResolver.kt` (existing reuse check + new post-download validation).
- `Grep -n "Log\.d\("` - zero hits in `VrApkArchiveResolver.kt`.
- `Grep` - file remains under `app_v2/src/noLegal/java/` (path check) - no copy created under `src/main`.

**Status:** `⏭️ Skipped`

**Step Log:**

- 2026-06-04 - SKIPPED per Step 03.2 verdict "interception sufficient". No code change. The post-download re-validation the prompt describes (`cacheFile.exists() && length > 0L`) already exists in resolveCloudArchive; the genuine preservation fix (relocate cloud temp out of getCacheDir) is out of scope and recorded as a follow-up in strategic §6.2.

---

## Phase Done Criteria

- [x] Every executed `Step 03.*` is `[x] done`; Step 03.3 is `⏭️ Skipped` with a note in INDEX Change Log.
- [x] Both strategic §6 items read `Статус:** Resolved`.
- [x] If Step 03.3 ran: project compiles. N/A - Step 03.3 skipped, no code change in this phase.
- [x] `Grep` for `TODO(phase-03)` returns zero hits. (expected 0 | actual 0)
- [x] Dev log entry added for the strategic spec via post-change.ps1.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - N/A, no `.kt` changed in this phase.

---

## Handoff Notes to Next Phase

Both research items are resolved and the protection verdict is recorded. The behavioural surface of S0355 is final after this phase; Phase 04 performs catalog/dev-log/changelog closure only.

---

## Rollback Plan

Step 03.3 (if executed) reverts cleanly - it only narrows an existing validation window in one noLegal file, no data migration. The §6 status edits are documentation-only.
