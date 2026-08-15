# Phase 06 — docs-catalog-cleanup

**Strategic spec:** [`../S0220_google-tv-availability-research.md`](../S0220_google-tv-availability-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** all previous phases
**Blocks:** nothing — final phase
**Steps done:** 0 / 2
**Started:** —
**Completed:** —

---

## Objective

Finalize documentation and catalog state for S0220. No code changes.

---

## Prerequisites

- [ ] Phase 05 ✅ Done — TV visibility verified on Panasonic MX700.

---

## Files Touched

| File | New / Modified | Note |
|------|:--------------:|------|
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | Only if Phase 04 introduced `.kt` changes (none expected — manifest-only) |
| `dev/CATALOG/app_v2.md` | Modified (regen) | Same condition |

> If Phase 04 made manifest-only changes (no `.kt` files), catalog regen is not required — mark Step 6.1 skipped.

---

## Steps

### Step 6.1 — Regenerate app_v2 catalog (if .kt files changed in Phase 04)

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase
**Condition:** Skip if Phase 04 was manifest-only (no `.kt` changes).

**Prompt for developer:**

> If any `.kt` files were modified during Phase 04, run:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Otherwise mark this step `⏭️ Skipped`.

**Verification:**

- If run: `Glob` — `dev/CATALOG/app_v2.jsonl` modified timestamp is today.
- If skipped: document "Phase 04 was manifest-only — no catalog regen needed".

**Status:** `[ ]` not done

---

### Step 6.2 — Close spec and add final dev log entry

**Files:** _(dev log + spec catalog)_
**Depends on:** Step 6.1

**Prompt for developer:**

> Run:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "PLAN/S0220_google-tv-availability-research/PHASE_06__docs-catalog-cleanup.md" "spec-dev" "S0220 Phase 06: cleanup complete"
> ```
> Then run `/spec-check S0220` to advance status to Verified.

**Verification:**

- Dev log entry exists for Phase 06.
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0220 -Format json` — `status` field equals `Verified`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every applicable Step 6.* is `[x] done` (skipped steps documented).
- [ ] `/spec-check S0220` returns `Verified`.
- [ ] Strategic spec `Status:` shows `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Documentation-only phase — no rollback needed.
