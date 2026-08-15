# Tactical Plan: S0637 - stream-channel-shortcut

**Strategic spec:** [`../S0637_stream-channel-shortcut.md`](../S0637_stream-channel-shortcut.md)
**Research inputs:** [`research/02__pin-shortcut-api-availability.md`](research/02__pin-shortcut-api-availability.md), [`research/03__stream-launch-entry-point.md`](research/03__stream-launch-entry-point.md)
**Feature:** Ярлык конкретной трансляции на домашнем экране
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Done (awaiting device test)
**Phases:** 4 / 4 done
**Last updated:** 2026-06-23

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

> **Flavor note (no source-set split).** The whole feature lives in `src/main` on the Streams UI (`StreamsActivity` / `StreamSourceAdapter` / `StreamsViewModel`). Flavor availability is inherited: the create-shortcut action is reachable only from the Streams screen, which is itself entry-point-gated (`SUPPORT_STREAMS`) - absent in lite/photos. There is no per-flavor behavior difference, so no `src/<flavor>/java` impl and no `BuildConfig.SUPPORT_*` guard in `src/main` are introduced (CLAUDE.md Rule 15 not triggered - nothing to gate in code).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | deep-link-entry | - | ✅ Done | 4/4 | [PHASE_01__deep-link-entry.md](PHASE_01__deep-link-entry.md) |
| 02 | pin-shortcut-helper | 01 | ✅ Done | 2/2 | [PHASE_02__pin-shortcut-helper.md](PHASE_02__pin-shortcut-helper.md) |
| 03 | row-action-trigger | 02 | ✅ Done | 3/3 | [PHASE_03__row-action-trigger.md](PHASE_03__row-action-trigger.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Resolved 2026-06-23:** §6 #4 - row action placement. Owner chose an overflow `⋮` button in the stream row (menu: Add to home screen / Remove). Long-press-removes and the pin button stay unchanged. Phase 03 reflects this (layout edit + overflow `PopupMenu`). No open blockers remain.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` - capability recorded. `docs/FEATURES*.md` is NOT edited here; the showcase sentence is promoted by `/skill-release` from the inventory diff (CLAUDE.md §11).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (new public class `StreamShortcutPinManager`).
- [ ] `/spec-check S0637` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0637`.

---

## Blockers Log

- 2026-06-23 - Phase 03 gated by §6 #4 (row action placement). Resolved same day: owner chose the overflow `⋮` button. Blocker cleared; Phase 03 rewritten for an overflow button + `PopupMenu`.

---

## Change Log

- 2026-06-23 - Initial tactical plan authored by `/spec-tech`.
