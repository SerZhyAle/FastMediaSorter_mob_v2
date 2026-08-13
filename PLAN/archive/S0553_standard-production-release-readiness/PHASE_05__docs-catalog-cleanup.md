# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S0553_standard-production-release-readiness.md`](../S0553_standard-production-release-readiness.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Steps done:** 2 / 2
**Started:** 2026-06-20
**Completed:** 2026-06-20

## Step Log

- 2026-06-20 - 05.1 PASS: gate cross-linked from `HOW_TO_DEVELOP_AND_RELEASE_RU.md`, `dev/PROJECT_OPERATIONS_INDEX.md`, `store_assets/PLAY_CONSOLE_CHECKLIST.md` (link-only, no copied sections).
- 2026-06-20 - 05.2 PASS: S0553 added zero `.kt` (the working-tree `.kt` are unrelated pre-existing WIP) - catalog regen N/A; dev log batched at finalization; no FEATURES/ALL_FEATURES change (internal release tooling).

---

## Objective

Wire the gate document into the existing release docs/routing and run post-change closure (dev log; catalog regen only if a `.kt` was added).

---

## Prerequisites

- [ ] Phases 01-04 Done; `docs/RELEASE_READINESS_STANDARD.md` fully filled.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/HOW_TO_DEVELOP_AND_RELEASE_RU.md` | Modified | ≤ +20 |
| `dev/PROJECT_OPERATIONS_INDEX.md` | Modified | ≤ +20 |
| `store_assets/PLAY_CONSOLE_CHECKLIST.md` | Modified | ≤ +10 |

---

## Steps

### Step 05.1 - Cross-link the gate into release docs and routing

**Files:** `docs/HOW_TO_DEVELOP_AND_RELEASE_RU.md`, `dev/PROJECT_OPERATIONS_INDEX.md`, `store_assets/PLAY_CONSOLE_CHECKLIST.md`

**Prompt for developer:**

> Add a one-line pointer to `docs/RELEASE_READINESS_STANDARD.md` from each: the release how-to (`HOW_TO_DEVELOP_AND_RELEASE_RU.md`) as the standard-production gate; `dev/PROJECT_OPERATIONS_INDEX.md` under release routing; and a header note in `store_assets/PLAY_CONSOLE_CHECKLIST.md` stating it is the operator slice of the full gate. Do not duplicate gate content - link only.

**Verification:**

- `Grep` - `RELEASE_READINESS_STANDARD` present in all three files.
- `Grep` - no copied gate sections (the link line only; the canonical content stays in the gate doc).

**Status:** `[x]` done

---

### Step 05.2 - Post-change closure

**Files:** (closure only - dev log, catalog)

**Prompt for developer:**

> Run the post-change facade / dev log for every file created or modified by S0553 (one batched logical entry set). Catalog regen is N/A (no `.kt` added) - confirm via `git status` that no `app_v2/**/*.kt` is in the S0553 diff; if any `.kt` slipped in, run `scripts/catalog_sync.ps1 -Module app_v2`. No `docs/FEATURES*.md` change (developer release tooling, strategic mandates none). No `docs/ALL_FEATURES.jsonl` record (no user-visible capability).

**Verification:**

- `dev/CHANGELOG.md` contains entries for `docs/RELEASE_READINESS_STANDARD.md`, the three `scripts/release/*.ps1`, the coverage manifest, and the waiver pack.
- `git status` shows no untracked `app_v2/**/*.kt` from this ticket (catalog regen genuinely N/A).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `/spec-check S0553` ready to run.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - doc cross-links only, no runtime change.
