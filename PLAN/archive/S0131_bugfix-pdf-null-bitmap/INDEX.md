# Tactical Plan: S0131 — bugfix-pdf-null-bitmap

**Strategic spec:** [`../S0131_bugfix-pdf-null-bitmap.md`](../S0131_bugfix-pdf-null-bitmap.md)
**Feature:** Fix null BitmapDrawable on PlayerActivity teardown + adaptive preload heap threshold
**Tier:** 2 — Easy
**Priority:** 90
**Status:** In Progress — awaiting on-device confirmation
**Phases:** 2 / 3 done
**Last updated:** 2026-05-09

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | teardown-guard | — | ✅ Done | 2/2 | [PHASE_01__teardown-guard.md](PHASE_01__teardown-guard.md) |
| 02 | adaptive-heap-threshold | 01 | ✅ Done | 1/1 | [PHASE_02__adaptive-heap-threshold.md](PHASE_02__adaptive-heap-threshold.md) |
| 03 | docs-catalog-cleanup | 01, 02 | 🚧 In Progress | 1/2 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Research items from strategic §6 resolved during tactical authoring:

- [x] **Research §6.1:** Full list of teardown lazy-init accesses — identified at `PlayerLifecycleManager.kt` lines 121, 235, 242. All three use `try/catch UninitializedPropertyAccessException` which has no effect on lazy computed properties; only `lateinit var` throws that. Fix: use backing-field null checks (`_xViewerManager != null`).
- [x] **Research §6.2:** Optimal relative threshold for preload — resolved as 15% relative with 15 MB absolute floor (`max(relative, 15 MB)`). Matches current emulator ratio (20 MB on 148 MB ≈ 13.5%) while scaling correctly for larger devices.

No open blockers. Phase 01 may start immediately.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` — no update needed (internal bugfix, see strategic §8).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated after `.kt` changes.
- [ ] `/spec-check S0131` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0131`.

---

## Blockers Log

- 2026-05-09 — Awaiting on-device confirmation for S0131 logcat paths before removing temporary `Timber.d("S0131:` tags and closing Phase 03 Step 3.2.

---

## Change Log

- 2026-05-09 — Initial tactical plan authored by `/spec-tech`.
