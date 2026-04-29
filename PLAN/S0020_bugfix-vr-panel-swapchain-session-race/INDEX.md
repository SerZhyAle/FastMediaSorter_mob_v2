# Tactical Plan: bugfix-vr-panel-swapchain-session-race

**Strategic spec:** [`../S0020_bugfix-vr-panel-swapchain-session-race.md`](../S0020_bugfix-vr-panel-swapchain-session-race.md)
**Feature:** Restore reliable creation of the interactive panel swapchain on the first cold-start attempt; eliminate the silent "stored — session not yet up" defer path; emit observable `panel ready` / `panel never came up` diagnostic markers.
**Tier:** 3 — Moderate
**Status:** Done
**Phases:** 3 / 3 done
**Last updated:** 2026-04-28

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | native-jni-contract | — | ✅ Done | 3/3 | [PHASE_01__native-jni-contract.md](PHASE_01__native-jni-contract.md) |
| 02 | kotlin-diagnostics | 01 | ✅ Done | 3/3 | [PHASE_02__kotlin-diagnostics.md](PHASE_02__kotlin-diagnostics.md) |
| 03 | docs-catalog-cleanup | 01, 02 | ✅ Done | 3/3 | [PHASE_03__docs-catalog-cleanup.md](PHASE_03__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

Strategic §6 has 4 Open research items, all resolved inline by /spec-all on 2026-04-28:

- [x] §6.1 **Readiness condition** — `g_ctx.session != XR_NULL_HANDLE` is the correct precondition for `xrCreateSwapchain`. Native HUD JNI already uses this check; the bug is that native panel JNI uses the stricter `g_ctx.sessionRunning` instead. Aligning the check is a one-line fix.
- [x] §6.2 **Stored deferred requests** — for the panel path, the "stored" dimensions in `g_ctx.panelSwapchainWidth/Height` are dead code: nothing in `createSessionAndSwapchains` ever picks them up (unlike HUD which auto-creates from stored dims). Remove the dead path. HUD's "stored" path is preserved (it has a real consumer).
- [x] §6.3 **Cold restart panel reuse** — already implemented at `OpenXrNative.cpp:1254-1267`: existing impl checks for matching dimensions and reuses the swapchain. No change required.
- [x] §6.4 **Timeout for "panel never came up"** — with the native fix the call becomes synchronous: it either succeeds immediately or returns false. The "never came up" alert collapses to "the synchronous call returned false". No timer required.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` and locale mirrors NOT modified — strategic §8: no user-facing change.
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `/spec-check S0020` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-04-28 — Initial tactical plan authored by `/spec-tech` (via `/spec-all`).
