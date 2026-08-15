# Phase 01 - gate-foundation

**Strategic spec:** [../S0286_compliance-lint-gate-and-features-sanitization.md](../S0286_compliance-lint-gate-and-features-sanitization.md)
**Tactical index:** [INDEX.md](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-05-21
**Completed:** 2026-05-21

---

## Objective

Introduce the compliance deny-list source, the legacy baseline, the governance note, and a cacheable Gradle verification task wired into `preBuild`.

---

## Prerequisites

- [x] All INDEX blockers are resolved.
- [x] Working tree is on a development branch.
- [x] The task is limited to `app_v2` + public `docs/FEATURES*.md`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/compliance/platform-name-denylist.txt` | New | <= 120 |
| `app_v2/compliance/platform-name-baseline.txt` | New | <= 240 |
| `docs/COMPLIANCE_DENYLIST.md` | New | <= 220 |
| `app_v2/build.gradle.kts` | Modified | <= 950 |

> `app_v2/build.gradle.kts` is already > 500 lines - create a timestamped backup in `temp/` before editing it.

---

## Steps

### Step 01.1 - Add the deny-list and legacy baseline sources

**Files:** `app_v2/compliance/platform-name-denylist.txt`, `app_v2/compliance/platform-name-baseline.txt`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the plain-text deny-list file and the temporary baseline file. Keep the seed list aligned with strategic §3.2, but exclude `YouTube` / `youtube.com` / `youtu.be` in this first iteration. Store baseline suppressions as `relative/path<TAB>trimmed line`, one entry per current legacy match that must stay green for now.

**Verification:**

- `Glob` - `app_v2/compliance/platform-name-denylist.txt` exists.
- `Glob` - `app_v2/compliance/platform-name-baseline.txt` exists.
- `Grep` - `Instagram`, `TikTok`, and `threads.com` exist in the deny-list file.
- `Grep` - `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/KnownAuthResources.kt` exists in the baseline file.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification PASS. Files: `app_v2/compliance/platform-name-denylist.txt`, `app_v2/compliance/platform-name-baseline.txt`. Seed list written without the deferred YouTube edge-case; legacy baseline captured for the current market-source debt.

---

### Step 01.2 - Document the compliance workflow and allowed suppressions

**Files:** `docs/COMPLIANCE_DENYLIST.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Write a short governance doc for the deny-list workflow: where the source files live, what the task scans, how to add a new forbidden token, how to add a temporary baseline line, and how to use the inline `allow-platform-literal:` suppression marker when a market-file literal is truly unavoidable.

**Verification:**

- `Glob` - `docs/COMPLIANCE_DENYLIST.md` exists.
- `Grep` - `allow-platform-literal:` exists in the document.
- `Grep` - `FEATURES_noLegal` exists in the document.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification PASS. Files: `docs/COMPLIANCE_DENYLIST.md`. Governance doc records task scope, baseline format, `FEATURES_noLegal` exclusion, and the `allow-platform-literal:` suppression workflow.

---

### Step 01.3 - Wire the cacheable Gradle compliance task into preBuild

**Files:** `app_v2/build.gradle.kts`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add a cacheable `verifyNoPlatformNames` task that reads the deny-list and baseline files, scans the market source roots plus the three public `docs/FEATURES*.md` files, ignores `noLegal`, supports an inline `allow-platform-literal:` suppression marker, and fails with an actionable `path:line` message when a new forbidden literal appears. Wire the task into `preBuild` so `assembleStandardDebug` goes red on violations.

**Verification:**

- `Grep` - `verifyNoPlatformNames` exists in `app_v2/build.gradle.kts`.
- `Grep` - `allow-platform-literal:` exists in `app_v2/build.gradle.kts`.
- `Grep` - `preBuild` depends on `verifyNoPlatformNames`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-21 - Verification PASS. File: `app_v2/build.gradle.kts`. `verifyNoPlatformNames` is cacheable, wired into `preBuild`, and validated green via `:app_v2:verifyNoPlatformNames --no-daemon`.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `assembleStandardDebug` passes with the compliance task enabled.
- [x] Dev log entry added for every file in "Files Touched" via `./scripts/add_to_dev_log.ps1`.

---

## Handoff Notes to Next Phase

The deny-list task is live and still temporarily suppresses the existing public FEATURES bullets through the baseline file; Phase 02 must rewrite those bullets and delete the corresponding baseline entries.

---

## Rollback Plan

Revert the new compliance files and the `build.gradle.kts` task block. No runtime data or schema changes are involved.
