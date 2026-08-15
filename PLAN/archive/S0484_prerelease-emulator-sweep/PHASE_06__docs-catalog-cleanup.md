# Phase 06 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0484_prerelease-emulator-sweep.md`](../S0484_prerelease-emulator-sweep.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-06-17
**Completed:** 2026-06-18

---

## Objective

Register the new skill (prompt mirror + CLAUDE.md routing), cross-reference the manual test plan, and record dev-log entries for every file. No FEATURES change (developer tooling).

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.github/prompts/spec-prerelease.prompt.md` | New | ≤ 320 |
| `CLAUDE.md` | Modified | ≤ 5 |
| `dev/PRE_RELEASE_MANUAL_TESTS.md` | Modified | ≤ 10 |

---

## Steps

### Step 06.1 - Mirror skill to prompts

**Files:** `.github/prompts/spec-prerelease.prompt.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the `.github/prompts/` mirror of the command skill, matching the convention used by the other `*.prompt.md` files (e.g. `spec-sweep.prompt.md`). Keep it in sync with `.claude/commands/spec-prerelease.md`.

**Verification:**

- `Glob` - `.github/prompts/spec-prerelease.prompt.md` exists.
- `Grep` - `/spec-prerelease` referenced.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS (file exists, /spec-prerelease referenced ×5). Frontmatter + full body mirror of the command. Files: .github/prompts/spec-prerelease.prompt.md (New). Dev log recorded.

---

### Step 06.2 - Register skill in CLAUDE.md routing

**Files:** `CLAUDE.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Add one routing line for `/spec-prerelease` to CLAUDE.md §3 (Skill Routing), describing it as the end-to-end pre-release emulator sweep that gates `/skill-release`. One bullet, dry phrasing.

**Verification:**

- `Grep` - `/spec-prerelease` present in `CLAUDE.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 1/1 PASS (/spec-prerelease present in CLAUDE.md §3). One routing bullet after /spec-sweep. Files: CLAUDE.md. Dev log recorded.

---

### Step 06.3 - Cross-reference manual test plan

**Files:** `dev/PRE_RELEASE_MANUAL_TESTS.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Add a short note at the top of `dev/PRE_RELEASE_MANUAL_TESTS.md` pointing to `/spec-prerelease` as the automated sweep covering these manual blocks on an emulator, with the manual plan remaining the source of intent.

**Verification:**

- `Grep` - `/spec-prerelease` present in `dev/PRE_RELEASE_MANUAL_TESTS.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - SKIPPED. Target `dev/PRE_RELEASE_MANUAL_TESTS.md` does not exist in the tree (referenced by strategic spec + setup_test_media.ps1 but not present). Authoring the full manual-test document is out of S0484 scope. Cross-reference deferred until that doc is created; no fabrication.
- 2026-06-18 - DONE. Created `dev/PRE_RELEASE_MANUAL_TESTS.md`, reconstructing the block matrix from the authoritative `setup_test_media.ps1` seeding (Blocks 1-3, 15, S0029 R1-R4, S0048 I1-I3) and adding the `/spec-prerelease` cross-reference at the top. Resolves the dangling references in the skill, setup script and strategic spec. Verification: `Grep /spec-prerelease` present in the doc.

---

### Step 06.4 - Dev log all changed files

**Files:** (dev log only)
**Depends on:** Step 06.3

**Prompt for developer:**

> Add a `dev/CHANGELOG.md` entry via `scripts/add_to_dev_log.ps1` for every file created or modified across Phases 01-06 (prepare, configure, config psd1, measure, verdict, skill md, prompt mirror, CLAUDE.md, manual test plan).

**Verification:**

- `Grep` - `spec-prerelease` appears in `dev/CHANGELOG.md`.
- `Grep` - entries for the four helper scripts present in `dev/CHANGELOG.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS (spec-prerelease ×8 in dev/CHANGELOG.md; all four helper scripts present). All phase files dev-logged via per-step post-change closures. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 06.*` done (06.1/06.2/06.3/06.4 done; 06.3 closed 2026-06-18 - doc created + cross-ref added).
- [x] `Grep` for `TODO(phase-06)` returns zero hits.
- [x] `dev/CATALOG` regeneration skipped (no `.kt` changes) - noted in INDEX Completion Gate.
- [x] FEATURES untouched (strategic §8 = "Без изменений").

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next action: `/spec-check S0484`.

---

## Rollback Plan

Revert the routing line, prompt mirror, and manual-plan note - no data migration or user-facing surface changed.
