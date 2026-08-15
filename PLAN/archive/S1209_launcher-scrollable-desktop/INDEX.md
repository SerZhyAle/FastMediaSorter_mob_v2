# Tactical Plan: S1209 - launcher-scrollable-desktop

**Strategic spec:** [`../S1209_launcher-scrollable-desktop.md`](../S1209_launcher-scrollable-desktop.md)
**Research inputs:** none - the AS-IS survey was run inline by `/spec-all` and its findings are folded into strategic §4
**Feature:** A launcher desktop whose scrolling is visible, reachable and appendable
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done - awaiting device test
**Phases:** 4 / 4 done
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.
>
> **What is already shipped (read before planning any edit):** the desktop has no row cap, measures itself as `rows * cellSize`, sits inside a `NestedScrollView` and therefore already scrolls by gesture. That landed with S0404 (ADR-9, Verified 2026-07-18). None of the phases below re-implement it. Each phase closes one part of the owner's request that the shipped behaviour does not satisfy.
>
> **Flavor placement:** every file touched here already lives under `app_v2/src/launcherEnabled/`, the source set that only `standard` and `noLegal` mount. No `BuildConfig` guard is introduced and none is needed.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | scrollbar-affordance | - | ✅ Done | 2/2 | [PHASE_01__scrollbar-affordance.md](PHASE_01__scrollbar-affordance.md) |
| 02 | append-below-add-flow | - | ✅ Done | 3/3 | [PHASE_02__append-below-add-flow.md](PHASE_02__append-below-add-flow.md) |
| 03 | drag-edge-autoscroll | 01 | ✅ Done | 2/2 | [PHASE_03__drag-edge-autoscroll.md](PHASE_03__drag-edge-autoscroll.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §6 carries three open items, and none of them blocks a phase:

- §6.1 asks the owner to confirm the request is read as strategic §2 rather than as new architecture. The working assumption is recorded in the spec and the phases below implement it; a different answer would shrink the plan, not invalidate a landed phase.
- §6.2 asks whether the house scrollbar styling suffices. Phase 01 ships the house pattern, which is the reversible choice - a wider custom track would replace four attributes.
- §6.4 asked the owner to decide the shape of a **draggable** scroll thumb. Answered 2026-08-06: it leaves this ticket for S1430, so no fifth phase is planned here and strategic §2.1а is gone.
- §6.5 asked which surface hosts the slotless add. Answered 2026-08-06: a "+" button on the taskbar, last element before Done, visible only while the desktop is being edited. Steps 02.2 and 02.3 are unblocked.
- §6.3 asks who arbitrates the swipe-up gesture shared with S1401. That ticket has not reached its phase 06; nothing here can be decided for it in advance. The §6.5 answer keeps this plan clear of it - a button is not a gesture.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not touched here; strategic §8 carries a user-visible sentence, so the capability is recorded in `docs/ALL_FEATURES.jsonl` and the showcase stays release-owned.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S1209` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## Manual gates (cannot close without a device)

- Strategic §11.1 and §11.3 are visual and gestural: that the scrollbar is visible and does not fade, and that a drag to the bottom edge scrolls the desktop far enough to drop a cell in a row that was off-screen. Neither is provable by a static predicate.
- Strategic §11.2 is half static and half visual: that the "+" appears on the taskbar in edit mode and is absent otherwise can only be seen, even though the code path behind it is greppable.
- Strategic §11.5 asks for both orientations on both launcher flavors.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1209`.

---

## Blockers Log

- 2026-08-06 - Steps 02.2 and 02.3 deferred on a placement decision the spec does not carry: which surface hosts the slotless add. Raised as strategic §6.5, resolvable with `/ui-clarify`. Step 02.1 shipped and is independent of the answer, and Phase 03 does not touch that surface, so the plan keeps moving.
- 2026-08-06 - CLEARED. The owner answered §6.5 through `/ui-clarify`: a "+" button on the taskbar, last element before Done, shown only in edit mode. That is the same surface and the same visibility rule S1412 gave the Done button, so the edit-mode chrome stays in one owner. Steps 02.2 and 02.3 reopened with the surface named.

---

## Change Log

- 2026-08-05 - Plan self-review found that strategic §2.1 conflated two things: a bar that is *visible* and a bar that is *draggable*. Only the first is deliverable from attributes. The strategic spec was split into §2.1 and §2.1а, §6.4 was added, and Phase 01 was narrowed to the visible half. Fixing it here cost one planning pass; discovering it in implementation would have cost a phase.
- 2026-08-05 - Coverage note for the accessibility constraint in strategic §3.2: "the scrollbar must not be the only sign that content continues past the edge" is satisfied by construction rather than by a step - the desktop lays out partially visible rows at the bottom edge, so content itself signals continuation. The touch-target half of that constraint applies only to a draggable element, which §6.4 defers.
- 2026-08-05 - Initial tactical plan authored by `/spec-tech`. Phase 01 and Phase 02 are independent of each other; Phase 03 depends on Phase 01 only because both touch the same scroll container and landing them in one order keeps the diff readable.
