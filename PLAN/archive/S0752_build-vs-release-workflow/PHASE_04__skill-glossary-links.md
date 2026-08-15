# Phase 04 - Skill Glossary Links

**Strategic spec:** [`../S0752_build-vs-release-workflow.md`](../S0752_build-vs-release-workflow.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-06-27
**Completed:** 2026-06-27

---

## Objective

Point the `/build` and `/skill-release` skills at the glossary as the terminology source of truth, satisfying strategic criterion 5.

---

## Prerequisites

- [ ] Phase 03 ✅ Done (`docs/BUILD_VS_RELEASE.md` exists).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `.claude/commands/build.md` | Modified | ≤ 4 |
| `.claude/commands/skill-release.md` | Modified | ≤ 4 |

---

## Steps

### Step 04.1 - Reference glossary from `/build`

**Files:** `.claude/commands/build.md`

**Prompt for developer:**

> Near the top of the Build Reference section, add one line linking the terminology source of truth: the distinction between a local "build" (free, DEBUG branch) and a "release" (paid CI, `main`) is defined in `docs/BUILD_VS_RELEASE.md`. Do not restate the cost map - just the pointer.

**Verification:**

- `Grep` - `docs/BUILD_VS_RELEASE.md` present in `.claude/commands/build.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 1/1 PASS. Added terminology pointer at top of Build Reference in `.claude/commands/build.md`.

---

### Step 04.2 - Reference glossary from `/skill-release`

**Files:** `.claude/commands/skill-release.md`

**Prompt for developer:**

> In the intro (near the Distribution channels line), add one line noting that `/skill-release` is the single "release" entry point as defined in `docs/BUILD_VS_RELEASE.md`, and that it is the only flow that spends paid GitHub Actions minutes. Do not restate the pipeline.

**Verification:**

- `Grep` - `docs/BUILD_VS_RELEASE.md` present in `.claude/commands/skill-release.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 1/1 PASS. Added terminology pointer near the Distribution channels intro in `.claude/commands/skill-release.md`.

---

## Phase Done Criteria

- [ ] Steps 04.1-04.2 are `[x] done`.
- [ ] `Grep` confirms both skill files reference `docs/BUILD_VS_RELEASE.md`.
- [ ] Dev log entry added for both files.

---

## Handoff Notes to Next Phase

All strategic criteria implemented. Phase 05 closes out dev-log/catalog hygiene.

---

## Rollback Plan

Revert the two one-line additions - no behavioral dependency.
