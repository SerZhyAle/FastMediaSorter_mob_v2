# Tactical Plan: S0220 — google-tv-availability-research

**Strategic spec:** [`../S0220_google-tv-availability-research.md`](../S0220_google-tv-availability-research.md)
**Feature:** Research and fix why the app is not visible in Play Store on Panasonic MX700 (Google TV)
**Tier:** 3 — Moderate
**Priority:** 50
**Status:** In Progress
**Phases:** 1 / 6 done (Phase 01 ✅; Phase 02–03 blocked on manual; Phase 04 partial)
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | manifest-audit | — | ✅ Done | 6/6 | [PHASE_01__manifest-audit.md](PHASE_01__manifest-audit.md) |
| 02 | play-console-audit | — | ⛔ Blocked (manual) | 0/3 | [PHASE_02__play-console-audit.md](PHASE_02__play-console-audit.md) |
| 03 | device-sideload-test | — | ⛔ Blocked (manual) | 0/4 | [PHASE_03__device-sideload-test.md](PHASE_03__device-sideload-test.md) |
| 04 | apply-manifest-fixes | 01 ✅; 02,03 pending | 🚧 In Progress | 4/5 | [PHASE_04__apply-manifest-fixes.md](PHASE_04__apply-manifest-fixes.md) |
| 05 | verify-tv-visibility | 04 | ⬜ Not started | 0/3 | [PHASE_05__verify-tv-visibility.md](PHASE_05__verify-tv-visibility.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 0/2 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Phases 01–03 are research phases and carry no pre-requisites. Phase 04 (apply-manifest-fixes) must not start until all items below are checked.

- [x] **Research §6.1** — `MANAGE_EXTERNAL_STORAGE` TV filter — Resolved: NOT a blocker. Phase 01 Step 1.2.
- [x] **Research §6.2** — `screenOrientation="sensor"` Play Store filter — Resolved: NOT a blocker (attribute absent). Phase 01 Step 1.3.
- [x] **Research §6.3** — `<layout>` VR tag TV impact — Resolved: NOT a blocker. Phase 01 Step 1.4.
- [ ] **Research §6.4** — Play Console Device Catalog status for Panasonic MX700 — PENDING Phase 02 (manual).
- [x] **Research §6.5** — `RECORD_AUDIO` implicit hardware filter — Resolved: NOT a blocker (overridden by uses-feature). Phase 01 Step 1.2.
- [ ] **Research §6.6** — Google Play Services version on MX700 — PENDING Phase 02 (manual).
- [x] **Research §6.7** — Google TV vs Android TV store rules — Resolved: baseline requirements satisfied. Phase 01 Step 1.1.
- [x] **Research §6.8** *(new)* — TV banner format — Resolved: BLOCKER A confirmed and fixed (Step 4.5 done). XML placeholder replaced with PNG.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` — no update required (strategic §8: "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `/spec-check S0220` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0220`.

---

## Blockers Log

- **Phase 02** (2026-05-16) — Requires interactive Play Console access. Manual only. Owner must log in to Play Console, check Device Catalog for Panasonic MX700, and record findings in Phase 02.
- **Phase 03** (2026-05-16) — Requires physical Panasonic MX700 TV. Manual only. Owner must sideload APK and record results in Phase 03 and `temp/panasonic-mx700-sideload-findings.txt`.

---

## Change Log

- 2026-05-16 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-16 — Phase 01 complete: manifest audit done (6/6 steps). §6.1, §6.2, §6.3, §6.5, §6.7 Resolved. BLOCKER A (XML banner) identified.
- 2026-05-16 — Phase 04 Steps 4.5 + 4.4 done: XML tv_banner.xml replaced with proper PNG at 5 densities. Build passes.
- 2026-05-16 — Phase 02 and 03 blocked pending manual owner action.
