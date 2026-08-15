# Phase 03 - Workflow Glossary Doc

**Strategic spec:** [`../S0752_build-vs-release-workflow.md`](../S0752_build-vs-release-workflow.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 1 / 1
**Started:** 2026-06-27
**Completed:** 2026-06-27

---

## Objective

Create the single source-of-truth dev document defining "build" (сборка) and "release" (релиз), the GitHub Actions cost map per branch/trigger, the command/skill mapping, and the main-push guard with its escape hatch.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (CI triggers cleaned - cost map can state `main`-only).
- [ ] Phase 02 ✅ Done (guard exists - doc describes activation + escape hatch).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/BUILD_VS_RELEASE.md` | New | ≤ 140 |

---

## Steps

### Step 03.1 - Author the glossary document

**Files:** `docs/BUILD_VS_RELEASE.md` (New)

**Prompt for developer:**

> Write an English dev document (project convention: docs in EN) that defines the two terms and maps them to existing tooling. Required sections:
>
> 1. **Two terms** - "Build (сборка)": local, free, frequent - compile an APK, verify locally, commit+push to a `DEBUG-v0NN` branch; artifact is a debug APK in `DOWNLOADS/`, never published. "Release (релиз)": the `main` branch, paid CI, rare - docs/site update, release artifacts, store publication.
> 2. **CI cost map** - a table of the three workflows (`android-ci.yml`, `maestro-tests.yml`, `jekyll-gh-pages.yml`) with their triggers, stating explicitly that a push to `DEBUG-v0NN` triggers zero workflows, and paid CI fires only at the `main` boundary (push to `main` + PR to `main`). Cite `research/01` facts; do not re-derive.
> 3. **Command / skill mapping** - Build flow: `.\a.ps1 dq` (fast build), `.\a.ps1 fc` (local check), `.\a.ps1 c` (commit+push to DEBUG); reference skill `/build`. Release flow: single entry `/skill-release` (13-step checklist + 5 distribution channels) - do not restate the checklist, link to the skill.
> 4. **Main-push guard** - explain the `pre-push` hook (Phase 02), the one-time activation `pwsh -NoProfile -File scripts/githooks/activate-hooks.ps1`, that the release worktree is exempt by basename, and the `FMS_ALLOW_MAIN_PUSH=1` escape hatch for an intentional manual push.
> 5. **Maintenance note** - the CI cost map must be updated whenever a workflow trigger changes.
>
> Author style: `..` not `...`, plain hyphen, lists over tables except the multi-column cost map. No user-visible strings involved.

**Verification:**

- `Glob` - `docs/BUILD_VS_RELEASE.md` exists.
- `Grep` - `DEBUG-v0NN` present (build flow defined).
- `Grep` - `/skill-release` present (release flow mapped).
- `Grep` - `FMS_ALLOW_MAIN_PUSH` present (escape hatch documented).
- `Grep` - `\.\.\.` returns zero hits (style: no triple-dot ellipsis).

**Status:** `[x] done`

**Step Log:**

- 2026-06-27 - Verification 5/5 PASS. Wrote `docs/BUILD_VS_RELEASE.md` with all five sections (terms, CI cost map, command/skill mapping, main-push guard, maintenance). Tokens: DEBUG-v0NN(4), /skill-release(7), FMS_ALLOW_MAIN_PUSH(1), `...`(0).

---

## Phase Done Criteria

- [ ] Step 03.1 is `[x] done`.
- [ ] `Glob` confirms `docs/BUILD_VS_RELEASE.md` exists with all five sections.
- [ ] Dev log entry added for `docs/BUILD_VS_RELEASE.md`.

---

## Handoff Notes to Next Phase

The glossary is the terminology source of truth. Phase 04 links `/build` and `/skill-release` to it.

---

## Rollback Plan

Delete `docs/BUILD_VS_RELEASE.md` - no other file depends on it until Phase 04.
