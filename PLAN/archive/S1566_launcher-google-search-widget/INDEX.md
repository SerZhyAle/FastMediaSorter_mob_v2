# Tactical Plan: S1566 - launcher-google-search-widget

**Strategic spec:** [`../S1566_launcher-google-search-widget.md`](../S1566_launcher-google-search-widget.md)
**Research inputs:** [`research/01__gadget-contract-and-search-plumbing.md`](research/01__gadget-contract-and-search-plumbing.md)
**Feature:** Google search bar gadget on the launcher desktop
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-08-11

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | search-query-launch | - | ✅ Done | 2/2 | [PHASE_01__search-query-launch.md](PHASE_01__search-query-launch.md) |
| 02 | search-gadget | 01 | ✅ Done | 4/4 | [PHASE_02__search-gadget.md](PHASE_02__search-gadget.md) |
| 03 | starter-seed | 02 | ✅ Done | 2/2 | [PHASE_03__starter-seed.md](PHASE_03__starter-seed.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. All five strategic §6 items are Resolved by the owner quiz of 2026-08-11 and recorded in §6 and in the
`Quiz decisions` block.

---

## Planning facts carried from research

Established before phase design; a step contradicting one of these is a planning bug, not an implementation
detail.

- A gadget is one class in `src/launcherEnabled/.../ui/launcher/gadget/`, one `KEY_*` in
  `LauncherGadgetRegistry`, one registry wiring point, one layout under `src/launcherEnabled/res/layout/`,
  strings in `src/main/res/values*/strings.xml`.
- Gadget layouts have no `-land` counterpart: the desktop grid measures the cell, so every existing gadget
  layout is orientation-neutral. Rule 11 is satisfied by that absence, not waived.
- The cell card is deliberately non-focusable so the gadget's inner controls are the D-pad stops. An editable
  field inside a gadget is therefore sanctioned by the existing contract.
- Edit mode overlays a clickable scrim as the cell's last child, which swallows every touch. The field is
  inert during desktop rearrangement with no extra mechanism - this is what the device test must confirm.
- `LauncherGadgetRegistry` currently takes 8 constructor parameters against a detekt threshold of 10. This
  ticket takes the 9th. The next gadget must join a qualified list module instead.
- `LauncherStarterSets` deliberately duplicates gadget key literals and is held to the registry by
  `app_v2/src/testLauncherEnabled/.../LauncherStarterSetsParityTest.kt`. That test is the seed phase's gate.
- `ic_search.xml` already exists in `src/main/res/drawable`; no new drawable is introduced.
- No unit test exists for any gadget class. Phase 01 adds the first, covering the only pure logic this ticket
  produces.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; strategic §8 defers the wording to
      `/skill-release` from the `ALL_FEATURES` diff.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - this ticket adds public classes.
- [ ] `/spec-check S1566` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1566`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-11 - Initial tactical plan authored by `/spec-tech`.
