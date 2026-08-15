# Phase 06 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0102_spec-catalog-ergonomics.md`](../S0102_spec-catalog-ergonomics.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05 (all previous phases)
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Confirm catalog integrity, verify dev log completeness, and confirm that no FEATURES docs update is required.

---

## Prerequisites

- [ ] All previous phases (01–05) are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Modified (via script) | — |

> `dev/CATALOG/app_v2.jsonl` — not regenerated; no Kotlin files changed in this spec.
> `docs/FEATURES*.md` — not updated; internal tooling per strategic §8.

---

## Steps

### Step 6.1 — Validate catalog integrity

**Files:** _(none — read-only check)_
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -File scripts/spec_catalog/validate.ps1`. All checks must pass with exit code 0. If any FAIL is reported, fix the underlying issue (do not suppress the check). WARN on staleness is acceptable.

**Verification:**

- Shell: `pwsh -File scripts/spec_catalog/validate.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 6.2 — Verify dev log completeness

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 6.1

**Prompt for developer:**

> Confirm that `dev/CHANGELOG.md` contains entries (via `add_to_dev_log.ps1`) for every file modified or created in Phases 01–05:
>
> - `scripts/spec_catalog/SCHEMA.md`
> - `scripts/spec_catalog/_lib.ps1`
> - `scripts/spec_catalog/update.ps1`
> - `scripts/spec_catalog/next-id.ps1`
> - `scripts/spec_catalog/search.ps1`
> - `scripts/spec_catalog/stats.ps1`
> - `scripts/spec_catalog/close.ps1`
> - `scripts/spec_catalog/bulk-update.ps1`
> - `CLAUDE.md`
> - Any skill prompt files edited in Phase 05.
>
> For any entry missing, run `.\scripts\add_to_dev_log.ps1 "<path>" "S0102" "<description>"` now.

**Verification:**

- `Grep` — `next-id.ps1` present in `dev/CHANGELOG.md`.
- `Grep` — `bulk-update.ps1` present in `dev/CHANGELOG.md`.
- `Grep` — `close.ps1` present in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

### Step 6.3 — Confirm FEATURES docs require no update

**Files:** _(none)_
**Depends on:** Step 6.1

**Prompt for developer:**

> Confirm that `docs/FEATURES.md`, `docs/FEATURES_RU.md`, and `docs/FEATURES_UK.md` do not need updating. Per strategic §8: this spec delivers internal developer tooling only — no user-facing feature surface changed.

**Verification:**

- `Grep` — `S0102` does NOT appear in `docs/FEATURES.md` (confirming no accidental doc change was made).

**Status:** `[ ]` not done

---

### Step 6.4 — Run `/spec-check S0102`

**Files:** _(none)_
**Depends on:** Step 6.2, Step 6.3

**Prompt for developer:**

> Run `/spec-check S0102`. It must return `Verified` and advance the strategic spec status accordingly. If it returns `Partial` or `Broken`, fix the reported gaps before marking this step done.

**Verification:**

- `Grep` — `**Status:** Verified` present in `PLAN/S0102_spec-catalog-ergonomics.md` after `/spec-check` completes.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every Step 6.* above is `[x] done`.
- [ ] `pwsh -File scripts/spec_catalog/validate.ps1` exits 0.
- [ ] Strategic spec `Status:` is `Verified`.
- [ ] All five new scripts (`next-id.ps1`, `search.ps1`, `stats.ps1`, `close.ps1`, `bulk-update.ps1`) appear in `dev/CHANGELOG.md`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

No code changes in this phase — only verification and log entries. Nothing to roll back.
