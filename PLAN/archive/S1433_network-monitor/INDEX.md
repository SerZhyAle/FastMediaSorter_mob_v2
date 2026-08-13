# Tactical Plan: S1433 - network-monitor

**Strategic spec:** [`../S1433_network-monitor.md`](../S1433_network-monitor.md)
**Research inputs:** [`research/01__android-capabilities-and-existing-network-foundations.md`](research/01__android-capabilities-and-existing-network-foundations.md), [`research/02__live-signal-and-gnss-charts.md`](research/02__live-signal-and-gnss-charts.md), [`research/03__permission-registry-and-welcome-integration.md`](research/03__permission-registry-and-welcome-integration.md)
**Feature:** Network Monitor - diagnostic screen-program
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 50
**Status:** BlockNeedUserTest
**Phases:** 9 / 10 done
**Last updated:** 2026-08-11

> **Scope:** tactical, English, developer handoff. Every step has verification predicate. Rationale lives in strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | foundations-and-permissions | - | ✅ Done | 6/6 | [PHASE_01__foundations-and-permissions.md](PHASE_01__foundations-and-permissions.md) |
| 02 | domain-snapshot-and-radio-control | 01 | ✅ Done | 7/7 | [PHASE_02__domain-snapshot-and-radio-control.md](PHASE_02__domain-snapshot-and-radio-control.md) |
| 03 | history-storage-and-export | 01 | ✅ Done | 6/6 | [PHASE_03__history-storage-and-export.md](PHASE_03__history-storage-and-export.md) |
| 04 | active-network-operations | 02, 03 | ✅ Done | 6/6 | [PHASE_04__active-network-operations.md](PHASE_04__active-network-operations.md) |
| 05 | signal-charts | 02 | ✅ Done | 5/5 | [PHASE_05__signal-charts.md](PHASE_05__signal-charts.md) |
| 06 | gnss-section | 01, 05 | ✅ Done | 4/4 | [PHASE_06__gnss-section.md](PHASE_06__gnss-section.md) |
| 07 | monitor-shell-and-navigation | 02 | ✅ Done | 5/5 | [PHASE_07__monitor-shell-and-navigation.md](PHASE_07__monitor-shell-and-navigation.md) |
| 08 | section-subscreens | 04, 05, 06, 07 | ✅ Done | 8/8 | [PHASE_08__section-subscreens.md](PHASE_08__section-subscreens.md) |
| 09 | program-registration | 07 | ✅ Done | 7/7 | [PHASE_09__program-registration.md](PHASE_09__program-registration.md) |
| 10 | docs-catalog-cleanup | all | ⛔ Blocked | 6/7 | [PHASE_10__docs-catalog-cleanup.md](PHASE_10__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **UI placement** - resolved 2026-08-06 by `/ui-clarify`, written into strategic §3.1.2: active-connection card above a two-column tile grid in portrait, the same grid with more tiles per row in landscape, the radio toggle as the first row of a subscreen, and a always-visible compact chart with its text summary.
- [x] **Research (strategic §6.5)** - resolved 2026-08-08, written into strategic §6.5. Echo order `checkip.amazonaws.com` -> `api.ipify.org` -> `icanhazip.com`, two more as spares, all five re-verified live that day and agreeing on the address; liveness must be probed with GET, never HEAD. Both NAT-PMP/PCP and UPnP IGD are queried, cheap one first, and neither answering means "no CGNAT hint" rather than a negative. UPnP adds no permission - `CHANGE_WIFI_MULTICAST_STATE` and `MulticastLock` are already in the tree. Phase 04 is unblocked.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` - owned by `/skill-release`, not touched here; strategic §8 requires the `docs/ALL_FEATURES.jsonl` record instead (Phase 10).
- [ ] `dev/CHANGELOG.md` has entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S1433` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If whole spec blocked, also set journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S1433`.

---

## Cross-cutting invariants

These bind every phase and are not repeated per step.

- No background work. Every observer registers on section visibility and unregisters on stop; no service, no periodic job, no work after the screen closes.
- No automatic traffic. Speed test, subnet scan, external IP and resource check start only on an explicit user action and are cancellable.
- Nothing reads `BuildConfig.SUPPORT_NETWORK_MONITOR` outside the capability record (CLAUDE.md Rule 14).
- No subscriber identity ever reaches UI, export or logs: no IMEI, phone number, ICCID, no credentials.
- There is exactly one radio-control implementation and S1433 never writes a second copy. The direction reversed against the plan: S1441 shipped the seam first (`domain/radio/RadioControlContract.kt`, `RadioKind.kt`, the real controller in `src/networkMonitor`, the no-op in `src/networkMonitorDisabled`), so Phase 02 consumes it instead of producing it. The system surface for a refused toggle is `OsShortcutCatalog.Target.fallbackIntent`, not a richer return type.

---

## Blockers Log

- 2026-08-11 - Phase 10 awaits an online device for the required entry-point visibility and launch
  check. `scripts/devtest/adb.ps1 current` reported no online device. The associated Play Console
  Data Safety response has no repository artifact and is reviewed in the same release-console pass.
- 2026-08-06 - Phase 04 blocked on the IP-echo research item. The UI-placement blocker was resolved the same day, so Phases 07 and 08 are clear.
- 2026-08-08 - That blocker is cleared; no phase of this ticket is blocked on research any more. Both pre-implementation blockers are now closed.

---

## Change Log

- 2026-08-11 - Phase 09 closed after Standard, noLegal and Lite compilation plus the full Standard
  unit suite. The phase audit caught and fixed two issues before closure: disabled Monitor cells were
  still present on the launcher desktop, and two Programs-menu entries shared an order. Phase 10 is
  awaiting the required on-device check; its temporary `S1433:` probes intentionally remain while the
  journal is `BlockNeedUserTest`. The Play Console Data Safety response has no repository artifact and
  is included in that release-console review.

- 2026-08-11 - Phase 09 started. Its original file list named `OperationsSettingsFragment.kt` but omitted
  the fragment's actual portrait and landscape XML bindings; both are now explicit in the phase scope.

- 2026-08-06 - Initial tactical plan authored by `/spec-tech`.
- 2026-08-08 - Phase 03 re-planned against the tree after it blocked on two stale premises. The schema hop moved from 46 -> 47 to 48 -> 49 (`AppDatabase.kt` already reads 48, and both intermediate hops exist), the migration moved into its own `Migration48To49.kt` with registration in `core/di/DatabaseModule.kt` per the shape every hop since 44 follows, the binding site corrected to the `NetworkMonitorDataModule` Phase 02 created, and step 03.6 added for the instrumented migration test the last five hops each carry.
- 2026-08-09 - Phases 06, 07 and 08 executed and closed in one `/spec-all` run. Three defects were caught by closure gates rather than by review, and all three are worth remembering because none of them was visible in the code as written: the Activity's field injection of `SettingsRepository` (`activity-logic`, moved into `NetworkMonitorViewModel`), `SpreadOperator` at two system-surface call sites (detekt, fixed at the helper so no future caller can reintroduce it), and two dialogs raised with a bare `.show()` (`untracked-dialog`, moved to `showBoundTo`). A fourth gate failure turned out to be the gate's own defect: `listener-symmetry` counted `onBackPressedDispatcher.addCallback`, which the lifecycle removes and which therefore can never have a `remove*Callback` to pair with - it rejected every new Activity handling Back while a dozen existing ones passed on baseline. Fixed in the gate (Rule 13); it dropped 18 false positives project-wide and the baseline ratcheted 133 -> 115. Two review findings were fixed on top: the Wi-Fi subscreen rendered the *active* link even when the active transport was cellular, and Phase 06's `registerGnssStatusCallback` return value was discarded, so a receiver that declined would have rendered an empty sky forever.
- 2026-08-09 - Phase 05 was executed and audited on 2026-08-09 but its row here and its own header still read `Not started`; both corrected. Phase 06 corrected against the tree before execution, one stale premise: it listed `di/NetworkMonitorModule.kt` as modified, a name that exists only in the two flavor source sets - `src/main` carries `NetworkMonitorDataModule`, and neither class this phase adds needs a module entry at all. The step 06.3 sink was under-specified as "on-device" and is now named: one file per session under `filesDir`. Phase 08 step 08.5 gained a share action for it, because a track written into `filesDir` with nothing offering it is written where its owner can never read it.
- 2026-08-08 - Phase 02 corrected against the tree before execution, three defects, no code written. The planned `core/network/radio/RadioControl*.kt` were dropped - S1441 already shipped that seam, so the plan's own "never a second copy" invariant now forbids writing them. The planned `di/NetworkMonitorModule.kt` in `src/main` was renamed `NetworkMonitorDataModule` - the fully-qualified name is already declared by both flavor source sets Phase 01 created, so a third would be a duplicate class in all six builds. Step 02.7's test moved from `src/test/java` to a new flavor-scoped `src/testNetworkMonitor/java`, because its subject exists in only two flavors and the shared test source set compiles against all six (the S1453 / S1455 shape).
