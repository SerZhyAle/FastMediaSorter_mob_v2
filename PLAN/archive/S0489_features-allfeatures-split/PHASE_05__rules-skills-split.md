# Phase 05 - Rules & Skills Split

**Strategic spec:** [`../S0489_features-allfeatures-split.md`](../S0489_features-allfeatures-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, 02
**Blocks:** Phase 07
**Steps done:** 4 / 4
**Started:** 2026-06-17
**Completed:** 2026-06-17

---

## Objective

Rewire project rules and skills: specs record their result in ALL_FEATURES (not FUNCTIONALITY.log), FEATURES is touched only via the release pipeline, and `/skill-release` populates the showcase from the ALL_FEATURES diff since the previous release.

---

## Prerequisites

- [ ] Phase 01 (tooling) + Phase 02 (log retired) are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/all_features/diff.ps1` | New | ≤ 200 |
| `.claude/commands/skill-release.md` | Modified | n/a |
| `.claude/commands/spec-dev.md` | Modified | n/a |
| `.claude/commands/spec-check.md` | Modified | n/a |
| `.claude/commands/quick.md` | Modified | n/a |
| `.claude/commands/doc-update.md` | Modified | n/a |
| `CLAUDE.md` | Modified | n/a |

---

## Steps

### Step 05.1 - Release-diff tool

**Files:** `scripts/all_features/diff.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Author `diff.ps1 -From <gitRef> [-To <gitRef=HEAD>]` that diffs `docs/ALL_FEATURES.jsonl` between two refs and prints added/changed records (by `id`) as a readable list (id, area, name, description, spec). This is the input `/skill-release` reads to decide showcase additions. Exit 0 on success; emit nothing extra on no-change.

**Verification:**

- `Glob` - `scripts/all_features/diff.ps1` exists.
- Run: `pwsh -NoProfile -File scripts/all_features/diff.ps1 -From HEAD -To HEAD` exits 0 and prints no records.
- `Grep` - `ALL_FEATURES.jsonl` referenced in the script.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. diff.ps1 created; HEAD..HEAD exits 0 with no records; references the inventory path.

---

### Step 05.2 - Rewire spec skills to write ALL_FEATURES

**Files:** `.claude/commands/spec-dev.md`, `.claude/commands/spec-check.md`, `.claude/commands/quick.md`, `.claude/commands/doc-update.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> In each skill, replace the post-change instruction to append to `dev/FUNCTIONALITY.log` (via `add_to_functionality_log.ps1`) with: record the delivered user-visible capability in `docs/ALL_FEATURES.jsonl` via `scripts/all_features/add.ps1`. Clarify the split: ALL_FEATURES is the developer inventory (EN-only, every shipped capability); `docs/FEATURES*` is the curated public showcase touched ONLY by `/skill-release`, never per-spec. Leave each skill's other behavior intact.

**Verification:**

- `Grep` - `scripts/all_features/add.ps1` referenced in all four skill files.
- `Grep` - no remaining instruction to call `add_to_functionality_log.ps1` in these four files.
- `Grep` - each file states FEATURES is showcase-only / release-populated.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. spec-dev/spec-check/quick/doc-update rewired to record in ALL_FEATURES via add.ps1; zero residual add_to_functionality_log references; each states FEATURES is showcase populated by /skill-release. Also retargeted scripts/spec_catalog/close-and-log.ps1 step 3 from the retired writer to all_features/add.ps1 (new -FeatArea/-FeatName/-FeatFlavors/-FeatId params) so finalization keeps working.

---

### Step 05.3 - Teach /skill-release to populate FEATURES from the diff

**Files:** `.claude/commands/skill-release.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Update the release pipeline: where it currently reads `dev/FUNCTIONALITY.log` for "what's new", replace with `scripts/all_features/diff.ps1 -From <PREV_TAG>`. Add a step: from the diff, select important/standout capabilities and add or update them in `docs/FEATURES*` (EN/RU/UK in lockstep), since the showcase is published to the site. Keep noLegal items sourced from the gitignored `docs/ALL_FEATURES_noLegal.jsonl`, never the public file. Preserve the existing cross-check intent but retarget it from the log to ALL_FEATURES.

**Verification:**

- `Grep` - `scripts/all_features/diff.ps1` referenced in `skill-release.md`.
- `Grep` - instruction present to update `docs/FEATURES` (EN/RU/UK) from the diff.
- `Grep` - `ALL_FEATURES_noLegal.jsonl` referenced for noLegal sourcing.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 3/3 PASS. skill-release Step 12b rewritten: reads diff.ps1 -From $PREV_TAG, promotes standout items into FEATURES (EN/RU/UK lockstep), noLegal from ALL_FEATURES_noLegal.jsonl. Zero residual FUNCTIONALITY.log references.

---

### Step 05.4 - Update CLAUDE.md feature policy

**Files:** `CLAUDE.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Update §11 Feature & UI Policies: state that `docs/ALL_FEATURES.jsonl` is the EN-only developer inventory (every shipped capability, written via `scripts/all_features/add.ps1`), `docs/FEATURES*` is the curated public showcase (EN/RU/UK, populated only by `/skill-release`), and noLegal additions go to gitignored `docs/ALL_FEATURES_noLegal.jsonl` mirroring the FEATURES_noLegal policy. Remove/repoint any standing instruction implying per-spec FEATURES edits or FUNCTIONALITY.log usage.

**Verification:**

- `Grep` - `ALL_FEATURES.jsonl` referenced in `CLAUDE.md` §11.
- `Grep` - CLAUDE.md states FEATURES is populated by `/skill-release`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-17 - Verification 2/2 PASS. CLAUDE.md §11 rewritten: ALL_FEATURES.jsonl as EN-only developer inventory (add.ps1/validate.ps1, replaced FUNCTIONALITY.log); FEATURES as curated showcase populated only by /skill-release; noLegal routing noted.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `Grep` across `.claude/commands/` shows no active mandate to append to `dev/FUNCTIONALITY.log` (historical mentions in skill-release cross-check retargeted).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The full loop is wired: specs → ALL_FEATURES (add.ps1) → release diff → FEATURES showcase. Phase 06 adds the mechanical gate keeping ALL_FEATURES from drifting.

---

## Rollback Plan

Revert the skills/CLAUDE.md commit and delete `diff.ps1`. Skills fall back to prior FUNCTIONALITY.log wording (still retired by Phase 02 guard, so re-fix before relying on it).
