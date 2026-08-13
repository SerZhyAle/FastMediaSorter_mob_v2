# Tactical Plan: S1399 - notification-small-icon-unified-branding

**Strategic spec:** [`../S1399_notification-small-icon-unified-branding.md`](../S1399_notification-small-icon-unified-branding.md)
**Feature:** Notifications - status-bar branding
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 3 / 3 done
**Last updated:** 2026-08-08

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | icon-asset-and-owner | - | ✅ Done | 3/3 | [PHASE_01__icon-asset-and-owner.md](PHASE_01__icon-asset-and-owner.md) |
| 02 | adopt-at-every-call-site | 01 | ✅ Done | 4/4 | [PHASE_02__adopt-at-every-call-site.md](PHASE_02__adopt-at-every-call-site.md) |
| 03 | gate-and-inventory | 02 | ✅ Done | 3/3 | [PHASE_03__gate-and-inventory.md](PHASE_03__gate-and-inventory.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None. §3.3 is filled from the owner's verbatim §0 text, and the one item still Open in §6 (legibility of the
silhouette at 24dp) closes on a device during acceptance, not by a decision.

---

## Three facts this plan must not lose

- **The source asset is already almost exactly right.** `app_v2/src/main/res/drawable/ic_app_logo.xml` is a
  single `<path>` with `fillColor="@color/white"` at 24dp - a silhouette already. The only thing that makes
  it unusable as a status-bar icon is line 10, `android:tint="?attr/colorControlNormal"`. So phase 01 is a
  copy minus one attribute, not an illustration task, and nobody needs to draw anything.
- **The `?attr` tint is a crash, not a cosmetic slip.** `ic_notification_cloud_download.xml` and
  `ic_notification_screen_capture.xml` both exist as separate forks for exactly this reason, and both carry
  the reason as a comment: a theme attribute cannot resolve outside the app theme, and `startForeground`
  then throws `CannotPostForegroundServiceNotificationException`. Do not "simplify" the new drawable by
  pointing it back at `ic_app_logo`.
- **Three of the four current drawables are not the bug.** Only `ic_notification_audio` is used off-label
  (three non-audio workers). `ic_notification_cloud_download` and `ic_notification_screen_capture` are
  feature-appropriate and were never complained about - they are replaced anyway because the owner said
  "повсеместно", and that is a deliberate scope choice recorded in strategic ADR-3, not an oversight to
  correct during implementation.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/ALL_FEATURES.jsonl` carries a FIX record for this ticket (phase 03).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `/spec-check S1399` returns `Verified`.

---

## How to Track Progress

1. Before starting a phase: flip its row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip a step to `[~] in progress` when started, `[x] done` when its Verification passes.
3. On phase completion: confirm every step `[x]`, confirm the Phase Done Criteria, flip the row to `✅ Done`.
4. If blocked: flip to `⛔ Blocked`, add a bullet to the Blockers Log.

---

## Blockers Log

- 2026-08-08 - **Resolved.** The sibling session finished its stream work: all four test call sites now
  pass `streamPlayOutcomeDao()` / `observeStreamPlayOutcomes`, `compileStandardDebugUnitTestKotlin`
  executes, and step 01.3's verification passed on re-run (`tests="1" failures="0"`, case present).
  Phase 01 is ✅ Done. Lesson kept for the rest of this ticket: copy a result XML out of
  `app_v2/build/test-results/` before citing it - a sibling gradle run wiped the first one within
  seconds on this shared tree.
- 2026-08-08 - Step 01.3's verification cannot run, for a reason outside this ticket: the shared
  `src/test` source set does not compile. Four files in a sibling session's in-flight stream work fail on
  a constructor that gained `streamPlayOutcomeDao` / `observeStreamPlayOutcomes` without its test callers
  being updated (`StreamSourceCatalogMergeTest.kt:34`, `AddStreamSourceUseCaseTest.kt:34`,
  `UpdateStreamSourceUseCaseTest.kt:30`, `StreamsViewModelAutoGridTest.kt:62`). Not fixed here - that is
  another session's half-written work and editing it would collide. `compileStandardDebugKotlin` and
  `.\a.ps1 fk` are both green, so every main source of this ticket compiles; only the test run is
  blocked. Re-run `check-standard-fast.ps1 -Mode Unit -Tests "*NotificationIcons*"` when the tree clears.

---

## Change Log

- 2026-08-08 - Initial tactical plan authored by `/spec-all` Stage F2, from the call-site inventory in the
  S1399 research pass.
