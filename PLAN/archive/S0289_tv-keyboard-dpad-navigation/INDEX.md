# Tactical Plan: S0289 - tv-keyboard-dpad-navigation

**Strategic spec:** [`../S0289_tv-keyboard-dpad-navigation.md`](../S0289_tv-keyboard-dpad-navigation.md)
**Feature:** Full multimodal input parity across all in-house user-facing Activities
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 10 / 10 done
**Last updated:** 2026-05-22

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations | - | ✅ Done | 5/5 | [PHASE_01__foundations.md](PHASE_01__foundations.md) |
| 02 | main-activity-focus | 01 | ✅ Done | 5/5 | [PHASE_02__main-activity-focus.md](PHASE_02__main-activity-focus.md) |
| 03 | player-focus | 01 | ✅ Done | 6/6 | [PHASE_03__player-focus.md](PHASE_03__player-focus.md) |
| 04 | browse-focus | 01 | ✅ Done | 3/3 | [PHASE_04__browse-focus.md](PHASE_04__browse-focus.md) |
| 05 | forms-group | 01 | ✅ Done | 6/6 | [PHASE_05__forms-group.md](PHASE_05__forms-group.md) |
| 06 | lists-group | 01 | ✅ Done | 5/5 | [PHASE_06__lists-group.md](PHASE_06__lists-group.md) |
| 07 | multimodal-foundation | 01 | ✅ Done | 4/4 | [PHASE_07__multimodal-foundation.md](PHASE_07__multimodal-foundation.md) |
| 08 | complex-surfaces-multimodal | 02, 03, 04, 07 | ✅ Done | 4/4 | [PHASE_08__complex-surfaces-multimodal.md](PHASE_08__complex-surfaces-multimodal.md) |
| 09 | remaining-activities-multimodal | 05, 06, 07 | ✅ Done | 4/4 | [PHASE_09__remaining-activities-multimodal.md](PHASE_09__remaining-activities-multimodal.md) |
| 10 | docs-catalog-cleanup | 05, 06, 08, 09 | ✅ Done | 4/4 | [PHASE_10__docs-catalog-cleanup.md](PHASE_10__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No open blockers. Strategic §3.3 and §6 were expanded on 2026-05-22 with delegated assumptions for:

- scope of "any activity";
- mouse contract;
- gamepad / joystick contract;
- hard-buttons / Bluetooth buttons contract;
- activity-only scope for dialogs.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - **skip** (strategic §8 says "Без изменений в `docs/FEATURES.md`.").
- [x] `dev/CHANGELOG.md` has an entry for every modified file (≥ 98 S0289 lines on 2026-05-22).
- [x] `dev/CATALOG/app_v2.jsonl` regenerated (1422 records on the last sync).
- [ ] `/spec-check S0289` returns `Verified` (awaiting on-device verification).
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check` (currently `BlockNeedUserTest`).

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0289`.

---

## Blockers Log

- 2026-05-21 - Phase 05 Step 05.4 (AuthSessionsActivity) deferred: class extends AppCompatActivity not BaseActivity; getInitialFocusView() hook unavailable without a small refactor (add ViewBinding + setupViews/observeData stubs). Not blocking user device-test - revisit alongside BaseActivity migration of small Activities.
  - RESOLVED 2026-06-03: AuthSessionsActivity already extends `BaseActivity<ActivityAuthSessionsBinding>`; it has `getInitialFocusView()` + `getMouseScrollTargetView()` + the shared dispatch chain. The deferral note is obsolete.
- 2026-05-22 - Multimodal scope expansion approved. New phases 07-10 added; no open blocker remains after delegated assumptions were recorded in the strategic spec.

---

## Change Log

- 2026-05-21 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-22 - Expanded tactical plan from focus-only execution to multimodal input execution. Old cleanup phase replaced by multimodal foundation + parity phases and a new final cleanup phase.
- 2026-05-22 - Phase 07 completed: shared activity mouse dispatch, legacy secondary-button fallback, BaseActivity wiring, build, and catalog sync validated.

## Remediation Round - 2026-06-03 (post device-test)

Device-testing on a TV emulator revealed real reachability gaps the static audit had missed
(4 BLOCKER + ~11 MAJOR) plus a corrupted probe ledger. This round fixed them under the same ticket.

- **A1** `ActivityMouseDispatchHelper.resolveScrollTarget` walks up to the nearest scrollable
  ancestor → mouse wheel now scrolls long forms (ResourceEditor / AddResource) from any focus.
- **A2** Settings / AddResource surfaces no longer convert bare D-pad arrows to manual `MoveFocus`
  (`KeyboardShortcutHandler` `arrowFocus=false`) → native focus traversal; Settings language /
  theme Spinners and dropdowns are now D-pad operable. Tab switching stays on PageUp/PageDown.
- **B** Welcome `onTvNavigation` is edge-aware (`FocusFinder`): ←/→ move focus to an in-page
  neighbour and only flip the page at the horizontal edge; language buttons made focusable.
- **C** StandalonePlayer initial focus retargeted from the permanently-GONE `btnPlayPause` to the
  always-visible `btnBack`. (Shared player layout left untouched - topCommandPanel buttons are
  already focusable with `selectableItemBackgroundBorderless` focus feedback.)
- **D** AddResource initial focus → first visible type card; KeybindingRemap explicit focus chain.
- **E** Main empty-state CTA focusable; Duplicates list `nextFocusDown` → delete-FAB; cloud-picker
  row ENTER navigates into the folder (Select button still confirms destination).
- **F** Removed dead `PlayerActivity.isHudFocused()`; added `TvKeyRouterTest` (unit guard for the
  key→action table that was previously manual-only); corrected the stale AuthSessions blocker note.
- **G** Probe ledger restored to 16 probes across 15 in-scope surfaces - including the two
  previously-missing PlayerActivity probes (HUD focus + gamepad analog) and a Main probe.

Intentionally deferred (assessed, non-blocking): Welcome bottom-bar `nextFocusUp` (ViewPager2 already
delegates focus into the page); shared player-layout focus rings (already provided by the selectable
background); ResourceEditor default section expansion (headers already focusable / Enter-expandable).

## Revision History

- **2026-05-22** - by `/spec-update` (`GPT-5.4`, focus: completeness, consistency, verifiability)
  - Applied: added multimodal foundation and parity phases; strategic/tactical scope kept under the same S0289 ticket.
