# Tactical Plan: S0185 — startup-splash-screen-investigation

**Strategic spec:** [`../S0185_startup-splash-screen-investigation.md`](../S0185_startup-splash-screen-investigation.md)
**Feature:** Startup splash / starting window investigation
**Tier:** —
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 1 / 3 done
**Last updated:** 2026-05-16

> **Scope:** tactical, English, developer handoff. Pure research spec — no production code changes. Phases collect evidence, run measurements, and produce a Tier recommendation. Implementation, if warranted, lives in child specs created at the end of Phase 03.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | evidence | — | ✅ Done | 4/4 | [PHASE_01__evidence.md](PHASE_01__evidence.md) |
| 02 | measurement | 01 | ⛔ Blocked (awaiting device run; instrumentation ready) | 0/3 | [PHASE_02__measurement.md](PHASE_02__measurement.md) |
| 03 | decision | 02 | ⬜ Not started | 0/2 | [PHASE_03__decision.md](PHASE_03__decision.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

Phases 02 and 03 are placeholders — written when Phase 01 closes, so the measurement plan can reference the exact evidence catalogue produced in Phase 01.

---

## Pre-Implementation Blockers

All open research items from strategic §6 must be resolved in Phases 01–02 before Phase 03 (decision) can begin.

- [ ] **§6.1** — Current baseline TTID / TTFD on a typical device — Phase 02 (blocked, needs device).
- [x] **§6.2 (partial)** — Synchronous Application-level calls blocking first draw — evidence catalogued in Phase 01 Step 01.3. Key candidates: `GmsAvailabilityChecker.check` and `CastContext.getSharedInstance`. Quantification deferred to Phase 02.
- [x] **§6.3 (partial)** — Baseline profile gap confirmed in Phase 01 Step 01.4: runtime installer present, generation pipeline absent. Cost/benefit evaluation deferred to Phase 02.
- [ ] **§6.4** — Owner Tier decision — Phase 03 (gated on Phase 02 numbers).

---

## Completion Gate

- [ ] All phases show ✅ Done (or ⏭️ Skipped with explicit rationale).
- [ ] `docs/FEATURES.md` — no update required (strategic §8: "Без изменений").
- [ ] `dev/CHANGELOG.md` has an entry for every research artifact written.
- [ ] Strategic spec §6 items are all updated to `Resolved` (or `Resolved (Skipped)` with rationale).
- [ ] Phase 03 decision is committed and, if child specs are created, their ids are listed in strategic §10.
- [ ] `/spec-check S0185` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to the appropriate `Block*` state.
5. All done: flip `Status:` to `Done`, run `/spec-check S0185`.

---

## Blockers Log

- **Phase 02** blocked on device access: TTID / TTFD measurement requires a release build on a physical Android device (API 31+ and API 26..30). Cannot be automated. Owner must perform or delegate the measurement run and record results in `PHASE_02__measurement.md`.
- 2026-05-16 — Phase 02 promoted from generic external hold to `BlockNeedUserTest`: canonical `S0185_TRACE` milestone lines and one `S0185_SUMMARY` line now cover `app_onCreate`, GMS, Cast, first frame, and fully drawn. Run-capture scaffolds live in `temp/S0185/02_measurement_journal.md` + `temp/S0185/02_trace_inventory.md`. Next operator action: install `standardDebug`, export one cold-start debug log, and record the summary values plus raw log path.

---

## Change Log

- 2026-05-16 — Initial tactical plan authored by `/spec-update` (P-2(a) restructure). Phase 01 (evidence) drafted; Phase 02 and Phase 03 are placeholders.
- 2026-05-16 — Phase 01 completed by `/spec-all` (claude-sonnet-4-6). Phase 02 and 03 files created. Spec status → `BlockNeedUserTest` (measurement required on device).
- 2026-05-16 — Phase 02 measurement handoff prepared manually: startup timing tags added for `GmsAvailabilityChecker.check` and `CastContext.getSharedInstance`; phase row clarified as "awaiting device run; instrumentation ready"; temp capture scaffolds added under `temp/S0185/`.
- 2026-05-16 — S0185 switched from ad-hoc timing tags to canonical debug-log output: `S0185_TRACE` logs milestone timestamps and `S0185_SUMMARY` prints first-frame / fully-drawn / GMS / Cast numbers for later post-hoc review.
