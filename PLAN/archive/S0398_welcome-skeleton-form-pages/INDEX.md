# Tactical Plan: S0398 - welcome-skeleton-form-pages

**Strategic spec:** [`../S0398_welcome-skeleton-form-pages.md`](../S0398_welcome-skeleton-form-pages.md)
**Research inputs:** [`../S0395_welcome-screens-redesign-research/SYNTHESIS.md`](../S0395_welcome-screens-redesign-research/SYNTHESIS.md), [`../S0395_welcome-screens-redesign-research/research/01__current-flow-inventory.md`](../S0395_welcome-screens-redesign-research/research/01__current-flow-inventory.md), [`research/02`](../S0395_welcome-screens-redesign-research/research/02__page0-language-theme.md), [`08`](../S0395_welcome-screens-redesign-research/research/08__reentry-upgrade.md), [`09`](../S0395_welcome-screens-redesign-research/research/09__flavor-matrix.md), [`10`](../S0395_welcome-screens-redesign-research/research/10__length-defaults.md), [`11`](../S0395_welcome-screens-redesign-research/research/11__accessibility-input.md)
**Feature:** Welcome onboarding skeleton (pages-as-data, Next-only, page 0, decorative networks page, cleanup)
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 60
**Status:** In Progress
**Phases:** 6 / 6 done
**Last updated:** 2026-06-10

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Integration decision (grounded)

- S0398 §2 scope excludes the dedicated profile / functionality / permissions pages (those are S0399/S0400/S0402). The skeleton therefore KEEPS the existing page-0 profile card and the existing permissions overlay working; it only data-drives the page list, removes the 4 decorative pages, adds page-0 theme + the decorative networks page, removes Skip, and fixes re-entry. D/E/G later extract profile/functionality/permissions into dedicated pages.
- Flavor visibility uses the existing `MediaCapabilities` surface (core/capability, bound per flavor) - NOT `BuildConfig` in src/main. The default-app page collapse needs a new `supportsDefaultPlayer` field (Phase 01); the networks Cloud tile uses existing `supportsCloud` (Phase 04). S0398 does NOT depend on S0396 (`CapabilityAvailability` is for OCR/translation/VR, a different surface).
- noLegal mounts `src/vr/java` (build.gradle.kts:535) → it reuses vr's `MediaCapabilitiesModule`. The 5 flavor module files (standard/legacy/vr/photos/lite) cover all 6 flavors.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | welcome-shell-framework | - | ✅ Done | 4/4 | [PHASE_01__welcome-shell-framework.md](PHASE_01__welcome-shell-framework.md) |
| 02 | decorative-removal | 01 | ✅ Done | 4/4 | [PHASE_02__decorative-removal.md](PHASE_02__decorative-removal.md) |
| 03 | page0-theme-row | 01 | ✅ Done | 3/3 | [PHASE_03__page0-theme-row.md](PHASE_03__page0-theme-row.md) |
| 04 | networks-decorative-page | 01 | ✅ Done | 3/3 | [PHASE_04__networks-decorative-page.md](PHASE_04__networks-decorative-page.md) |
| 05 | reentry-fixes | 01 | ✅ Done | 3/3 | [PHASE_05__reentry-fixes.md](PHASE_05__reentry-fixes.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 2/2 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

> **§2.5 touch-zones note:** the strategic "relocate touch-zones to a first-player-launch hint" goal is ALREADY satisfied by pre-existing player infrastructure (`TouchZoneHintType.FULLSCREEN_9ZONE` + `PlayerUiStateCoordinator.isTouchZoneHintShown` once-tracking + `PlayerTouchZoneSetupManager.showHintOverlay`, wired in `PlayerActivity`). Removing the welcome touch-zones page (Phase 02) loses nothing - no new hint phase needed. (Research artifact 01 missed this existing hint; corrected at plan self-review.)

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None (research). Phase 01 may start once the working tree is clean - this ticket is a wide WelcomeActivity refactor; do not run it on top of unrelated uncommitted work (see Blockers Log note about pre-existing DEBUG-v013 drift + temp_stash_s0397).

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - SKIP for S0398 (strategic §8 defers the FEATURES sentence to the final ticket of the line; intermediate state is not user-final).
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (public API changed).
- [~] All six flavors assemble - standard/lite/photos/legacy green WITH debug tags; noLegal/vr owner-waived (share validated src/main welcome sources, no flavor-specific welcome code).
- [ ] `/spec-check S0398` returns `Verified` - pending device test (status BlockNeedUserTest).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0398`.

---

## Blockers Log

- 2026-06-10 - Working-tree note (not a spec blocker): at plan time the tree carried +7 pre-existing flavor-flag reads in three standalone player activities and a `temp_stash_s0397` stash. Settle those before a wide WelcomeActivity refactor to avoid clobbering in-flight work.

---

## Change Log

- 2026-06-10 - Initial tactical plan authored by `/spec-tech`.
