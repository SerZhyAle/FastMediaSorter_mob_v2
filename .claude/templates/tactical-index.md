<!-- Template consumed by: /spec-tech (Process step 4). -->
<!-- Substitute: <Sxxxx>, <short-name>, the phase rows and counts. Read this file before writing INDEX.md. -->

# Tactical Plan: <Sxxxx> - <short-name>

**Strategic spec:** [`../Sxxxx_<short-name>.md`](../Sxxxx_<short-name>.md)
**Research inputs:** [`research/<NN>__<topic-slug>.md`](research/<NN>__<topic-slug>.md) <one link per artifact, or "none">
**Feature:** <feature name>
**Tier:** <tier label>
**Priority:** <0..100>
**Status:** Not started
**Phases:** 0 / N done
**Last updated:** <YYYY-MM-DD>

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | <slug> | - | ⬜ Not started | 0/N | [PHASE_01__<slug>.md](PHASE_01__<slug>.md) |
| 02 | <slug> | 01 | ⬜ Not started | 0/N | [PHASE_02__<slug>.md](PHASE_02__<slug>.md) |
| NN | docs-catalog-cleanup | all | ⬜ Not started | 0/N | [PHASE_NN__docs-catalog-cleanup.md](PHASE_NN__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

<Every §6 research item with `Status: Open` becomes checkbox. Phase 01 must not start while any blocker unchecked.>

- [ ] **Research:** <title> - required before Phase <NN>. See strategic §6.X.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - update only if strategic §8 contains FEATURES sentence (not "Без изменений"); skip otherwise.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/<module>.jsonl` regenerated if public API changed.
- [ ] `/spec-check <Sxxxx>` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check <Sxxxx>`.

---

## Blockers Log

- <YYYY-MM-DD> - Phase NN blocked: <cause>. Next: <who/what/when>.

---

## Change Log

- <YYYY-MM-DD> - Initial tactical plan authored by `/spec-tech`.
