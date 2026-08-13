# Phase 05 - Docs / Catalog Cleanup

**Strategic spec:** [`../S0260_nolegal-ytmusic-audio-share-recovery.md`](../S0260_nolegal-ytmusic-audio-share-recovery.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Close the spec. Regenerate the catalog, log the functionality change, ensure all `S0260:` Timber tags are removed (driven by the status transition out of `BlockNeedUserTest`), and run `/spec-check S0260`.

---

## Prerequisites

- [ ] All preceding phases ✅ Done (or Phase 03 ⏭️ Skipped under the D-out-of-scope branch).
- [ ] Strategic spec status currently `BlockNeedUserTest`.
- [ ] Device-test result recorded - the operator confirmed that a YTMusic share now produces a playable audio file (criterion §11.1) and never a JPEG/preview (criterion §11.2).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Generated | - |
| `dev/CATALOG/app_v2.md` | Generated | - |
| `dev/FUNCTIONALITY.log` | Modified (append) | - |
| `dev/CHANGELOG.md` | Modified (append, via script) | - |
| All `.kt` and `.py` files with `S0260:` Timber tags | Modified (tag removal) | - |

---

## Steps

### Step 05.1 - Remove all `S0260:` debug verification tags

**Files:** all `.kt` and `.py` files containing `S0260:` Timber traces
**Depends on:** - start of phase

**Prompt for developer:**

> Per CLAUDE.md "Debug Verification Tags" - when a spec leaves `BlockNeedUserTest`, every `Timber.d("Sxxxx: ...")` line tagged with that spec id must be removed. `/spec-check` is the natural skill to perform this on a `→ Verified` transition; this step is a safety net documenting the expectation explicitly. Run `Grep -nE 'S0260:' --include=*.kt --include=*.py` across the repo and delete each matching line (or block where the entire line is a Timber call). Do NOT delete reason-code strings like `"ytmusic_thumbnail_artifact"` or test-class names that mention `S0260` - only the diagnostic Timber/print lines.

**Verification:**

- `Grep -nE '"S0260: ' --include=*.kt --include=*.py` (note the trailing space after the colon, which is the Timber-format signature) returns ZERO hits across the repo.
- `Grep -nE 'S0260' --include=*.kt --include=*.py` may still return hits in KDoc/comments referencing the spec ticket - those are fine.
- `assembleNoLegalDebug` and `assembleStandardDebug` still compile.

**Status:** `[ ]` not done

---

### Step 05.2 - Catalog regeneration and dev-log entries

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (chains scan + render in one process per memory `feedback_pwsh_efficiency.md`). Verify the diff: the `S0260:` line removals should show in `LinkAutoDownloadCoordinator`, `YtDlpExtractionStrategy`, and `ytdlp_utils.py` records via reduced LOC. Run `.\scripts\add_to_dev_log.ps1` for the catalog files and for every file modified during this phase. The functionality log goes through its own dedicated script in Step 05.3.

**Verification:**

- `scripts/catalog_sync.ps1` exits 0.
- `dev/CATALOG/app_v2.jsonl` git-diff shows the affected rows updated (LOC decreased).
- `.\scripts\add_to_dev_log.ps1` exits 0 for each invocation.
- `git status` shows `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`, `dev/CHANGELOG.md` as modified.

**Status:** `[ ]` not done

---

### Step 05.3 - Functionality log entry + final spec check

**Files:** `dev/FUNCTIONALITY.log`, journal `PLAN/spec-catalog.jsonl` (via CLI)
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `.\scripts\add_to_functionality_log.ps1 -Id S0260 -Op FIX -Description "noLegal YTMusic share now produces a playable audio file; thumbnail and non-audio artifacts are rejected by an output contract"`. The description is English per CLAUDE.md communication policy. Then run `/spec-check S0260` to perform the audit and advance the status. `/spec-check` is responsible for setting the status to `Verified` (or `Partial` / `Broken` with findings) and for performing the final `S0260:` Timber tag scan-and-delete sweep (this is its mandate per CLAUDE.md "Debug Verification Tags" - Step 05.1 was a manual safety net but `/spec-check` re-verifies).

**Verification:**

- `dev/FUNCTIONALITY.log` last entry contains `S0260` and `FIX`.
- `/spec-check S0260` returns `Verified` (or surfaces concrete findings - any non-Verified outcome requires `/spec-fix` before this step can be marked `[x]`).
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0260 -Format json` shows `"status":"Verified"`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `Grep` for `"S0260: ` (Timber-format signature with trailing space) across `.kt` and `.py` files returns zero hits.
- [ ] `/spec-check S0260` returns `Verified`.
- [ ] Strategic spec `Status:` line in `PLAN/S0260_nolegal-ytmusic-audio-share-recovery.md` shows `Verified`.
- [ ] `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S0260 -Format json` confirms `"status":"Verified"`.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

This phase is documentation, catalog regeneration, and Timber-tag cleanup. Rolling back the commit restores the diagnostic tags but otherwise has no functional impact. The functionality log entry can be removed manually if the rollback is intentional.
