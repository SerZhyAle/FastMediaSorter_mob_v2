# Tactical Plan: S1091 - launcher-default-profile-shortcuts

**Strategic spec:** [`../S1091_launcher-default-profile-shortcuts.md`](../S1091_launcher-default-profile-shortcuts.md)
**Research inputs:** none
**Feature:** Launcher: seed default desktop with ~12-15 useful shortcuts + "Android Settings" rename
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** Not started
**Phases:** 4 / 4 done
**Last updated:** 2026-07-21

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | starter-set-content | - | ✅ Done | 4/4 | [PHASE_01__starter-set-content.md](PHASE_01__starter-set-content.md) |
| 02 | seed-resolution | 01 | ✅ Done | 3/3 | [PHASE_02__seed-resolution.md](PHASE_02__seed-resolution.md) |
| 03 | android-settings-label | - | ✅ Done | 1/1 | [PHASE_03__android-settings-label.md](PHASE_03__android-settings-label.md) |
| 04 | docs-catalog-cleanup | 01,02,03 | ✅ Done | 2/2 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 lists no open research items.

---

## Design decision (owner-aligned, non-blocking)

- The unified starter set (clock + existing virtual-resource shortcuts + padding feature shortcuts + tail) is added for **every** profile, per owner decision (§3.3). The pre-existing profile-specific gadgets (`profileItems`: photo-frame folder-preview, audio playlist) are **kept additive** - they do not reduce any profile's resource set, and removing them would regress existing S0404 tailoring. Mainstream profiles (PERSONAL_SMARTPHONE / HOME_TABLET / VR_HEADSET / OTHER) return no profile extras, so they land at ~12-15 exactly.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - skip (strategic §8 = "Без изменений").
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated (public API of LauncherStarterSets + SeedLauncherDesktopUseCase changed).
- [ ] `/spec-check S1091` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S1091`.

---

## Blockers Log

- (none)

---

## Change Log

- 2026-07-21 - Initial tactical plan authored by `/spec-tech`.
