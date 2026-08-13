# Phase 06 — Docs + Catalog Cleanup

**Strategic spec:** [`../S0242_bugfix-browse-list-sync-after-player.md`](../S0242_bugfix-browse-list-sync-after-player.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01 – 05
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-18
**Completed:** 2026-05-18

---

## Objective

Final cleanup: regenerate catalog, ensure every modified file has a dev-log entry, append a FIX entry to the functionality log, refresh `Last updated` markers. No `docs/FEATURES*.md` changes — strategic §8 says "Без изменений" (bug fix, not a new capability).

---

## Prerequisites

- [ ] Phases 01–05 ✅ Done.
- [ ] Working tree contains all spec-driven changes (no unrelated edits).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified | (auto-generated) |
| `dev/CATALOG/app_v2.md` | Modified | (auto-generated) |
| `dev/CHANGELOG.md` | Modified | (via `add_to_dev_log.ps1`) |
| `dev/FUNCTIONALITY.log` | Modified | (via `add_to_functionality_log.ps1`) |

---

## Steps

### Step 06.1 — Catalog scan + render

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run catalog regeneration with full path:
>
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> For new classes (`Mutation`, `MutationJournal`, `InMemoryMutationJournal`, `PathNormalizer`, `CanonicalPathNormalizer`, `BrowseReconcilerManager`, `QuickVerifier`, `QuickVerifierDispatcher`, `LocalQuickVerifier`, `SmbQuickVerifier`, `SftpQuickVerifier`, `CloudQuickVerifier`), fill `role` and `status` via `set.ps1` per `dev/CATALOG/README.md`:
>
> - role: `domain-model` for `Mutation`; `domain-contract` for interfaces (`MutationJournal`, `PathNormalizer`, `QuickVerifier`); `data-impl` for impls (`InMemoryMutationJournal`, `CanonicalPathNormalizer`, `*QuickVerifier`, `QuickVerifierDispatcher`); `ui-manager` for `BrowseReconcilerManager`.
> - status: `stable` (initial release of these classes).

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` mtime newer than the start of this phase.
- `Grep -n "MutationJournal" dev/CATALOG/app_v2.md` — at least 2 matches (interface + impl).
- `Grep -n "BrowseReconcilerManager" dev/CATALOG/app_v2.md` — at least 1 match.

**Status:** `[x]` done

---

### Step 06.2 — Verify dev log coverage

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> For every file in Phases 01–05's "Files Touched" tables, confirm a `dev/CHANGELOG.md` entry exists. Run:
>
> ```powershell
> Select-String -Path dev/CHANGELOG.md -Pattern "S0242" | Select-Object -ExpandProperty LineNumber, Line
> ```
>
> If any modified file is missing an entry, add it via:
>
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/add_to_dev_log.ps1 "<file>" "spec-dev" "<short desc> (S0242)"
> ```

**Verification:**

- `Grep -n "S0242" dev/CHANGELOG.md` — at least one entry per phase (≥ 5 hits total — one per phase including foundation, journal wiring, reconciler, verifier, observer).

**Status:** `[x]` done

---

### Step 06.3 — Functionality log: FIX entry

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 06.2

**Prompt for developer:**

> Append one `FIX` entry (per CLAUDE.md "Post-Change Steps" #3, user-visible bug fix):
>
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File scripts/add_to_functionality_log.ps1 -Id S0242 -Op FIX -Description "Browse file list now reflects deletes / moves / renames performed in Player on return — no manual pull-to-refresh required, works for local, SMB, SFTP, FTP, and cloud (Drive, Dropbox, OneDrive)."
> ```
>
> Note: `/spec-all` constraints document that sub-skills own this call — `/spec-dev` invokes it on `Implemented`, `/spec-check` invokes it on `Verified` if missed. This explicit step is a fallback in case both bypass it; the script is idempotent w.r.t. duplicate id+op pairs, so re-invoking is safe.

**Verification:**

- `Grep -n "S0242.*FIX" dev/FUNCTIONALITY.log` — at least one entry.

**Status:** `[x]` done

---

### Step 06.4 — Refresh INDEX `Last updated` and phase counters

**Files:** `PLAN/S0242_bugfix-browse-list-sync-after-player/INDEX.md`
**Depends on:** Step 06.3

**Prompt for developer:**

> Update `Last updated:` to today's date, set `Phases: 6 / 6 done`, flip every Phase Overview row to `✅ Done`. Append a Change Log entry:
>
> ```markdown
> - <YYYY-MM-DD> — All 6 phases marked done. Spec ready for /spec-check.
> ```

**Verification:**

- `Grep -n "Phases: 6 / 6 done" PLAN/S0242_bugfix-browse-list-sync-after-player/INDEX.md` — exactly one hit.
- `Grep -c "✅ Done" PLAN/S0242_bugfix-browse-list-sync-after-player/INDEX.md` — at least 6 (one per phase row).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (mtime newer than phase start).
- [ ] `dev/CHANGELOG.md` has ≥ 5 entries tagged `S0242`.
- [ ] `dev/FUNCTIONALITY.log` has at least one `S0242 FIX` entry.
- [ ] `INDEX.md` shows `Phases: 6 / 6 done`.
- [ ] Project still compiles — `.\a.ps1 dq` exit 0 (no code change in this phase, but sanity check).

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Next step is `/spec-check S0242` (runs from `/spec-all` Stage F5).

---

## Rollback Plan

Catalog regeneration is idempotent — re-run `scan.ps1` + `render.ps1` to restore. Dev log entries stay; they are append-only history.
