# Tactical Plan: S0136 — bugfix-glide-disk-cache-not-persisting

**Strategic spec:** [`../S0136_bugfix-glide-disk-cache-not-persisting.md`](../S0136_bugfix-glide-disk-cache-not-persisting.md)
**Feature:** Glide disk cache must persist across app sessions
**Tier:** 3 — Moderate
**Priority:** 85
**Status:** Tactical
**Phases:** 3 / 3 done
**Last updated:** 2026-05-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | instrumentation | — | ✅ Done | 6/6 | [PHASE_01__instrumentation.md](PHASE_01__instrumentation.md) |
| 02 | fix | 01 + device test | ✅ Done | 1/1 | [PHASE_02__fix.md](PHASE_02__fix.md) |
| 03 | docs-catalog-cleanup | 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

### Blockers gating Phase 02 (fix)

- [x] **Research:** Phase 01 instrumentation shipped.
- [x] **Research:** Device session captured — `fastmediasorter_20260510_201249.log` + `fastmediasorter_20260510_203412.log`, Samsung SM-S731B, Android 16.
- [x] **Research:** D-branch chosen: **D1** (cache key drift). Evidence: files written in session 1 (`fileCount=5` at session 2 start); zero disk hits in session 2 (`disk=0, network=10, memory=41`). D2/D3/D4 ruled out.
- [x] **Research:** Phase 02 step body authored with D1 investigation + fix steps.
- [x] Cache key already stabilised (`path+size` only) — fix confirmed in code; Phase 01 diagnostic residue removed; GlideCacheStats: summary line added.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated if public API changed.
- [ ] `/spec-check S0136` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.
- [ ] All `Timber.d("S0136:` tags removed from `.kt` files (verified by Grep).
- [ ] Field-test confirms: open SMB folder → force-stop → reopen → `GlideCacheStats.logStats` shows `disk ≥ 30 % of total`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0136`.

---

## Blockers Log

- 2026-05-10 — Phase 02 blocked: device test data required before fix branch can be selected. Next: ship Phase 01 to device, capture logs, run `/spec-update S0136`.
- 2026-05-10 — Phase 01 ✅ shipped (debug build). Journal status flipped to `BlockNeedUserTest` until device session captured.
- 2026-05-10 — **D-branch resolved from field logs** `fastmediasorter_20260510_201249.log` + `fastmediasorter_20260510_203412.log`. Decision: **D1 (cache key drift)**. `Timber.d("S0136:")` tags removed, spec re-opened `Tactical`. Phase 02 step body re-authored. Remaining: one more device run (Step 02.1A) to identify the drifting field in `NetworkFileData`.

---

## Change Log

- 2026-05-10 — Initial tactical plan authored by `/spec-tech`.
