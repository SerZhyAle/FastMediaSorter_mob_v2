# Tactical Plan: S0611 - bugfix-custom-theme-contrast

**Strategic spec:** [`../S0611_bugfix-custom-theme-contrast.md`](../S0611_bugfix-custom-theme-contrast.md)
**Research inputs:** [`research/01__m3-tonal-palettes-wcag.md`](research/01__m3-tonal-palettes-wcag.md)
**Feature:** WCAG AA contrast across all 9 themes (6 custom + Auto/Light/Dark)
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 90
**Status:** Done
**Phases:** 4 / 4 done
**Last updated:** 2026-06-22

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.
> **Build mode:** this plan runs under `no build` - compile/device verification deferred. Numeric WCAG (`temp/wcag_s0611.ps1`) is the standing correctness gate. Exact token values: research artifact 01.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | confirm-button-decouple | - | ✅ Done | 2/2 | [PHASE_01__confirm-button-decouple.md](PHASE_01__confirm-button-decouple.md) |
| 02 | dark-themes-tonal-palette | - | ✅ Done | 4/4 | [PHASE_02__dark-themes-tonal-palette.md](PHASE_02__dark-themes-tonal-palette.md) |
| 03 | light-themes-tonal-palette | - | ✅ Done | 3/3 | [PHASE_03__light-themes-tonal-palette.md](PHASE_03__light-themes-tonal-palette.md) |
| 04 | wcag-verification-closeout | 01, 02, 03 | ✅ Done | 3/3 | [PHASE_04__wcag-verification-closeout.md](PHASE_04__wcag-verification-closeout.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All strategic §6 research items are Resolved (see research artifact 01 - locked palettes + WCAG table). No blockers - Phase 01 may start.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES*.md` - strategic §8 declares no showcase change (contrast bugfix). No edit.
- [ ] `dev/CHANGELOG.md` has an entry for the change (batched).
- [ ] `temp/wcag_s0611.ps1` returns `ALL CHECKS PASS`.
- [ ] `/spec-check S0611` returns `Verified` (numeric basis; device visual confirm = manual item under no-build).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0611`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-06-22 - Initial tactical plan authored by `/spec-tech` (via `/spec-all`).
