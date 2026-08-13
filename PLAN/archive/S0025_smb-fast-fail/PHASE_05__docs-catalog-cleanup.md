# Phase 05 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0025_smb-fast-fail.md`](../S0025_smb-fast-fail.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02, Phase 03, Phase 04
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

Final phase — synchronise the catalog with all code changes from prior phases, ensure dev changelog entries are in place, and confirm no FEATURES doc update is required (per strategic §8).

---

## Prerequisites

- [ ] Phases 02, 03, 04 ✅ Done.
- [ ] Project compiles cleanly via `/build`.
- [ ] Working tree contains all S0025 changes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-regenerated) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (auto-regenerated) | n/a |
| `dev/CHANGELOG.md` | Modified (append-only via script) | n/a |

---

## Steps

### Step 05.1 — Confirm no docs/FEATURES update needed

**Files:** (no edits)
**Depends on:** — start of phase

**Prompt for developer:**

> Per strategic spec §8, this change is not user-visible (no new feature, no UI surface). Confirm by inspecting `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` for any existing entry mentioning "SMB error" or "network error timing" — if such an entry exists and is now outdated, update it; otherwise no edits.

**Verification:**

- `Grep -i "smb.*error.*timing\|fast.fail" docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md` — returns zero hits, confirming nothing to update.

**Status:** `[x]` done

---

### Step 05.2 — Regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — independent of Step 05.1

**Prompt for developer:**

> Run the catalog scan and render scripts:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> For any newly added class (`NetworkReachabilityGate`), set its role and status:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class NetworkReachabilityGate -Role "Synchronous gate that throws NetworkConnectionLostException when network/Wi-Fi is absent" -Status new
> ```

**Verification:**

- `Grep` — `NetworkReachabilityGate` matches at least once in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `NetworkReachabilityGate` matches at least once in `dev/CATALOG/app_v2.md`.

**Status:** `[x]` done

---

### Step 05.3 — Verify dev changelog entries

**Files:** `dev/CHANGELOG.md`
**Depends on:** Steps 05.1, 05.2

**Prompt for developer:**

> Confirm that `dev/CHANGELOG.md` contains entries for every file modified across phases 01–04 (each phase already required dev-log entries in its Done Criteria). If any file is missing, add it now via:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "<path>" "<target>" "<description>"
> ```
>
> Required minimum coverage: `NetworkReachabilityGate.kt` (new), `NetworkContextAnalyzer.kt`, `NetworkErrorMessageMapper.kt`, `SmbConnectionManager.kt`, `SmbErrorClassifier.kt`, `FtpClient.kt`, `SftpClient.kt` or `SftpConnectionPool.kt`, `DropboxClient.kt`, `GoogleDriveRestClient.kt`, `OneDriveRestClient.kt`, `CloudFileOperationHandler.kt`, plus all test files.

**Verification:**

- `Grep -c "NetworkReachabilityGate.kt" dev/CHANGELOG.md` — at least 1.
- `Grep -c "SmbConnectionManager.kt" dev/CHANGELOG.md` — at least 1 entry post-2026-04-29.
- `Grep -c "DropboxClient\|GoogleDriveRestClient\|OneDriveRestClient" dev/CHANGELOG.md` — at least 3 hits post-2026-04-29.

**Status:** `[x]` done

---

### Step 05.4 — Update spec catalog status

**Files:** `PLAN/spec-catalog.jsonl` (via CLI only)
**Depends on:** Steps 05.1–05.3

**Prompt for developer:**

> Once all phases are ✅ Done and Completion Gate items in `INDEX.md` are checked, the next step is `/spec-check S0025` — that skill flips status to `Verified` (or `Partial` / `Broken`). This phase only ensures the journal is in sync up to `Implemented`:
>
> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0025 -Status Implemented
> ```
>
> Direct edits to `PLAN/spec-catalog.jsonl` are forbidden.

**Verification:**

- `Bash` — `pwsh -File scripts/spec_catalog/select.ps1 -Id S0025 -Format json` returns `"status":"Implemented"`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` reflect all new/modified classes.
- [ ] `dev/CHANGELOG.md` has entries for every modified file.
- [ ] Spec journal status = `Implemented`.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md Completion Gate](INDEX.md#completion-gate). Next action by user: `/spec-check S0025`.

---

## Rollback Plan

This phase only updates documentation and catalog metadata — revert means re-running `scan.ps1` / `render.ps1` against the prior code state and resetting the journal status via `update.ps1 -Status Approved` (or whichever prior state).
