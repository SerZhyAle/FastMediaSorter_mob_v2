# Tactical Plan: S1179 - launcher-gps-sensor-widgets

**Strategic spec:** [`../S1179_launcher-gps-sensor-widgets.md`](../S1179_launcher-gps-sensor-widgets.md)
**Research inputs:** none
**Feature:** GPS and sensor gadgets on the launcher desktop
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** In Progress
**Phases:** 6 / 6 done
**Last updated:** 2026-08-07

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | sensor-foundation | - | ✅ Done | 6/6 | [PHASE_01__sensor-foundation.md](PHASE_01__sensor-foundation.md) |
| 02 | series-persistence | 01 | ✅ Done | 5/5 | [PHASE_02__series-persistence.md](PHASE_02__series-persistence.md) |
| 03 | compass-and-speed-gadgets | 01 | ✅ Done | 7/7 | [PHASE_03__compass-and-speed-gadgets.md](PHASE_03__compass-and-speed-gadgets.md) |
| 04 | series-chart-gadgets | 02, 03 | ✅ Done | 5/5 | [PHASE_04__series-chart-gadgets.md](PHASE_04__series-chart-gadgets.md) |
| 05 | steps-gadget | 01, 03 | ✅ Done | 5/5 | [PHASE_05__steps-gadget.md](PHASE_05__steps-gadget.md) |
| 06 | docs-catalog-cleanup | all | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Source-set placement contract

- Every sensor model, availability provider, platform source, repository and use case lands in `app_v2/src/main/java/**` and carries no flavor guard. Strategic §3.2 targets the launcher surface, and the launcher surface is a source set, not a `BuildConfig` field.
- Every gadget class, gadget layout and gadget registration lands in `app_v2/src/launcherEnabled/**`, which `app_v2/build.gradle.kts` mounts for `standard` and `noLegal` only. That placement is what satisfies strategic §3.2 "Flavor" - no `BuildConfig.IS_*` branch appears anywhere in this ticket.
- `ACTIVITY_RECOGNITION` is declared in `app_v2/src/launcherEnabled/AndroidManifest.xml`, never in `src/main`. `READ_PHONE_STATE` (S1415) is the precedent: a permission only the launcher surface needs stays out of the flavors that have no launcher.
- User-visible strings live in `app_v2/src/main/res/values*/strings.xml`, as every existing `launcher_gadget_*` key does.

---

## Findings that changed the plan (2026-08-06)

Strategic §4 was written 2026-07-25 and calls the area "полностью greenfield". Three of its premises no longer hold, and the phases below are planned against the tree as it is today. §4 carries a correction block recording the same three findings.

- **The gadget host is no longer greenfield.** `LauncherGadget` / `LauncherGadgetView` / `LauncherGadgetRegistry` exist in `src/launcherEnabled`, and `LauncherGadgetView.onActive()` already runs work only while the view is attached AND the host is STARTED. Strategic §3.2 "подписка на видимом гаджете, парная отписка" is therefore satisfied by using that base class, not by inventing a lifecycle rule.
- **`ACCESS_FINE_LOCATION` is already declared and already documented.** `src/main/AndroidManifest.xml` declares it plus `ACCESS_COARSE_LOCATION` and `uses-feature android.hardware.location(.gps) required="false"` for the S0766 camera geotag, and `PermissionRegistryRepositoryImpl` already carries its row. Only `ACTIVITY_RECOGNITION` is a new declaration, which narrows strategic §7's Play Data-safety risk to the steps gadget alone.
- **A working platform location source already exists to copy.** `CameraLocationProvider` (S0766) is a symmetric `LocationManager` start/stop source on plain platform APIs, no GMS. Phase 01 mirrors its shape rather than inventing one, and does not reuse the class itself - it is a `ui/cameracapture/helpers` class holding a mutable last-fix field, which is the wrong layer and the wrong contract for a Flow-based source.

---

## Authoring constraints found while implementing (2026-08-06)

Both were paid for once in Phase 01 and are written down so the remaining phases do not pay again.

- **detekt caps a function at 2 `return` statements** (`ReturnCount`). Every source in this ticket opens with guard clauses - "no sensor manager", "no sensor", "no permission" - and the natural shape is one early return per guard plus the real return, which is three. Collapse the guards into a single expression instead. This failed closure on `OrientationReadingSource` even though every one of the step's own predicates had passed.
- **`post-change.ps1` releases `CODE.LOCK` as part of closure**, and the release is owner-checked. So the lock is per *step*, not per phase: acquire immediately before the edit, let closure release it, acquire again for the next step. A build warning naming a *different* reason than your own is the signal that someone else now holds it.
- **A step's grep predicate constrains the code's formatting, so write the code to the predicate.** Two were hit in Phase 02: a wrapped `@Entity(` annotation does not match a predicate greping the one-line literal, and a `Migration(46, 47)` written as literals is what detekt's `MagicNumber` rejects. Where the two genuinely conflict, correct the predicate against the real tree and say so in the step - do not quietly satisfy one and drop the other.
- **A new DAO is not reachable until `DatabaseModule` provides it.** `AppDatabase` is the only `@Provides` in that graph; an abstract accessor on it is not a Hilt binding. Neither `fk` nor `fc` reports the resulting `MissingBinding` - only a build that runs the Hilt/annotation-processing tasks does.

---

## Downstream reuse note

Phase 04 puts `SensorSeriesChartView` in `src/main/java/.../ui/common/chart/`, not in `src/launcherEnabled`. S1433 (network-monitor, `Tactical`, same release package, queue position 32) plans its own `ui/networkmonitor/views/SignalChartView.kt` for the same job - a time series drawn on a Canvas with no colour-only encoding. S1179 ships first, so the shared class is placed where S1433 can consume it instead of authoring a twin. Reconciling S1433's plan against it is not this ticket's work and is parked separately.

---

## Pre-Implementation Blockers

None. Strategic §6 is closed - four items answered by the owner in the 2026-07-27 quiz, two by research. §3.2 explicitly delegates the series point cap to this plan, which Phase 02 fixes.

---

## Completion Gate

- [x] All phases show ✅ Done. Phase 05 carries one open criterion, named in its own "Why this phase is Done with one criterion open" section.
- [ ] `docs/ALL_FEATURES.jsonl` carries the capability record added via `scripts/all_features/add.ps1`, with flavors read from the generated `docs/FLAVOR_MATRIX.md` `SUPPORT_LAUNCHER` row. `docs/FEATURES*.md` is not touched here - it is `/skill-release`-owned (CLAUDE.md §11).
- [ ] `docs/PRIVACY_POLICY.md`, `.ru.md` and `.uk.md` describe `ACTIVITY_RECOGNITION` exactly as the manifest declares it.
- [ ] `PermissionRegistryManifestParityTest` passes on the variants where the permission composition differs - it is a release blocker per `docs/RELEASE_READINESS_STANDARD.md`, and this ticket changes both the manifest and the registry.
- [ ] **Owner / console action, not code:** the Play Data-safety form gains `ACTIVITY_RECOGNITION`. Strategic §11.8 names it, but the form lives in Play Console with no repo mirror, so it is carried out at release time through the `play-console` checklist in `docs/RELEASE_READINESS_STANDARD.md` §6.3. This ticket cannot close it and must not claim it.
- [ ] `dev/CHANGELOG.md` has an entry for the ticket, written by `scripts/post-change.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated - this ticket adds public classes.
- [ ] `standard debug` and `noLegal debug` both build (strategic §11.9).
- [ ] `/spec-check S1179` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1179`.

---

## Blockers Log

- none yet.

---

## Change Log

- 2026-08-06 - Initial tactical plan authored by `/spec-tech`.
