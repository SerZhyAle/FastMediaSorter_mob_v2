# Phase 02 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0147_bugfix-sftp-stale-session-inputstream-closed.md`](../S0147_bugfix-sftp-stale-session-inputstream-closed.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01 ✅ Done
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Regenerate the class catalog after the Phase 01 code change, add dev log entries for all touched files, and confirm no user-facing docs require update (strategic §8: no FEATURES change needed).

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] Project compiles cleanly.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (regen) | n/a |

---

## Steps

### Step 02.1 — Regenerate class catalog for app_v2 module

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Phase 01 done

**Prompt for developer:**

> Run:
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File "dev/CATALOG/scripts/scan.ps1" -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File "dev/CATALOG/scripts/render.ps1" -Module app_v2
> ```
> Confirm `SftpConnectionPool` still appears in the catalog with no new public-API entries (the new `isDeadTransportException` is `private`, so it should not appear).

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and has non-zero size.
- `Grep` — `SftpConnectionPool` matches in `dev/CATALOG/app_v2.md`.
- `Grep` — `isDeadTransportException` does NOT match in `dev/CATALOG/app_v2.md` (private — not catalogued).

**Status:** `[x] done`

---

### Step 02.2 — Add dev log entries

**Files:** `dev/CHANGELOG.md` (via script — do not edit directly)
**Depends on:** Step 02.1

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/data/remote/sftp/SftpConnectionPool.kt" "S0147" "Detect dead JSch transport (inputstream is closed), force session invalidation + single retry"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "catalog" "Regen after S0147 SftpConnectionPool change"
> ```

**Verification:**

- `Grep` — `S0147` present in `dev/CHANGELOG.md`.
- `Grep` — `SftpConnectionPool` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

---

### Step 02.3 — Confirm no FEATURES docs update needed

**Files:** none
**Depends on:** Step 02.1

**Prompt for developer:**

> Confirm strategic §8 decision: this fix is transparent to the user (no new UI text, no new feature entry). Verify `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` require no change. No action needed unless the decision has changed.

**Verification:**

- `Grep` — `S0147` does NOT appear in `docs/FEATURES.md` (no entry added — confirmed out of scope).

**Status:** `[x] done` — confirmed, S0147 not in docs/FEATURES.md (non-user-facing fix)

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `dev/CHANGELOG.md` entry present for `SftpConnectionPool.kt`.
- [x] `dev/CATALOG/app_v2.jsonl` and `app_v2.md` are current.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. After this phase, run `/spec-check S0147` to advance the ticket to `Verified` and remove the `Timber.d("S0147:` debug tag.

---

## Rollback Plan

Revert the catalog regen commit. No code or data changed in this phase.
