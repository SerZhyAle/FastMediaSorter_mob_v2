# Tactical Plan: S0429 - home-screen-google-content

**Strategic spec:** [`../S0429_home-screen-google-content.md`](../S0429_home-screen-google-content.md)
**Research inputs:** none in this folder - strategic §5 item 4 and §8 carry the 2026-08-06 findings inline.
**Feature:** Now Playing gadget reads any app's media session
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 40
**Status:** Done - awaiting the device test (`BlockNeedUserTest`)
**Phases:** 4 / 4 done
**Last updated:** 2026-08-06

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | now-playing-source-seam | - | ✅ Done | 3/3 | [PHASE_01__now-playing-source-seam.md](PHASE_01__now-playing-source-seam.md) |
| 02 | media-session-source | 01 | ✅ Done | 4/4 | [PHASE_02__media-session-source.md](PHASE_02__media-session-source.md) |
| 03 | opt-in-and-disclosure | 02 | ✅ Done | 5/5 | [PHASE_03__opt-in-and-disclosure.md](PHASE_03__opt-in-and-disclosure.md) |
| 04 | docs-catalog-cleanup | all | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. Strategic §5 items 1-6 are all Resolved; the last one - where the user turns notification access on - was answered by the owner on 2026-08-06 (a button on the gadget in its degraded state, plus an entry in the permission registry).

---

## What this ticket is, after the 2026-08-06 research

One thing: teach the **existing** `AudioNowPlayingGadget` to read the active media session of **any** app, behind opt-in notification access, and fall back to the app's own session when that access is absent.

Out of scope, and no phase touches them:

- A new gadget. `AudioNowPlayingGadget` / `gadget_launcher_now_playing.xml` already exist (S1170) and their presentation - spans, three transport buttons, body tap - is not changed.
- Level 0 deep-link tiles: already covered by S0427's app cells and shortcuts.
- Level 2 (YouTube glance, OAuth) and level 3 (Gmail, CASA Tier 3): deferred and excluded by strategic §3.3.

## Placement facts this plan is built on (verified 2026-08-06)

- The gadget lives only in `src/launcherEnabled`, mounted by the `standard` and `noLegal` flavor blocks (`app_v2/build.gradle.kts:606`, `:634`) with its manifest injected for exactly those two (`:1063`). Nothing this ticket adds needs to sit in `src/main` except the permission-registry row, so Rule 14 is satisfied by placement rather than by a guard.
- `src/launcherEnabled/AndroidManifest.xml` currently declares two activities and no `<service>`.
- Special access is already modelled: `CheckPermissionStatusUseCase` branches per `manifestName` before falling back to `checkSelfPermission`, which is how `MANAGE_EXTERNAL_STORAGE` and battery optimisation are answered. A notification-listener row is one more branch there, not a new mechanism.
- The registry already carries a launcher-gated optional row - `read_contacts`, `flavorGates = setOf("SUPPORT_LAUNCHER")` - so the new row copies a live pattern.
- `gadget_launcher_now_playing.xml` has no `layout-land` variant and needs none: the only landscape file in that source set is `activity_launcher_home.xml`.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - not updated here; `/skill-release` owns them.
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - Phases 01-03 add classes.
- [ ] `docs/settings/settings-manifest.json` + `docs/SETTINGS_REFERENCE*.md` regenerated - Phase 03 adds a row to the permissions screen (Rule 22).
- [ ] `/spec-check S0429` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0429`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-06 - Initial tactical plan authored by `/spec-tech`.
