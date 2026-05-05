# Tactical Plan: S0081 — tv-remote-key-coverage

**Strategic spec:** [`../S0081_tv-remote-key-coverage.md`](../S0081_tv-remote-key-coverage.md)
**Feature:** TV remote key coverage and focus correctness for Android set-top boxes
**Tier:** 2 — Easy
**Priority:** 50
**Status:** Done
**Phases:** 5 / 5 done
**Last updated:** 2026-05-04

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | manifest-leanback | — | ✅ Done | 1/1 | [PHASE_01__manifest-leanback.md](PHASE_01__manifest-leanback.md) |
| 02 | tv-key-routing | 01 (+ §6.1 resolved) | ✅ Done | 3/3 | [PHASE_02__tv-key-routing.md](PHASE_02__tv-key-routing.md) |
| 03 | focus-traversal | 01 | ✅ Done | 2/2 | [PHASE_03__focus-traversal.md](PHASE_03__focus-traversal.md) |
| 04 | dpad-acceleration | 03 | ✅ Done | 2/2 | [PHASE_04__dpad-acceleration.md](PHASE_04__dpad-acceleration.md) |
| 05 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_05__docs-catalog-cleanup.md](PHASE_05__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Research §6.1:** Resolved 2026-05-04. Mainstream boxes (Fire TV, Shield, Chromecast, Mi Box) have NO color buttons; CHANNEL_UP/DOWN present on Fire TV. Color buttons exist on Smart TV remotes and IPTV boxes. Planned JSON additions confirmed correct; add `key:165:0` (KEYCODE_INFO → system.toggle_info) as extra entry. See strategic §6.1 for full breakdown.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` — no regen needed (no `.kt` public API changes; only private method additions).
- [ ] `/spec-check S0081` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0081`.

---

## Blockers Log

- 2026-05-04 — INDEX created. Phase 02 blocked pending §6.1 research (TV KEYCODE survey).

---

## Change Log

- 2026-05-04 — Initial tactical plan authored by `/spec-tech`.
