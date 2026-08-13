# Phase 04 - Strip mode wiring

**Strategic spec:** [`../S1431_launcher-top-status-strip-mode.md`](../S1431_launcher-top-status-strip-mode.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** Phase 07
**Steps done:** 5 / 5
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Make the strip's owning manager put the clock in the start zone and the device indicators in the end
zone while the mode is on, and take them back out when it is off - driving both through the renderer
phase 02 produced.

---

## Prerequisites

- [x] Phases 01, 02 and 03 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt` | Modified | 298 (budget ≤ 330) |
| `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt` | Modified | 980 (budget ≤ 995) |

> `LauncherHomeActivity.kt` was backed up in step 02.1; if that backup predates this phase's edits, take
> a fresh one before starting (Rule 5).

---

## Steps

### Step 04.1 - Accept the mode flow in the strip manager

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt`, `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/LauncherHomeActivity.kt`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `topStatusStripMode: Flow<Boolean>` parameter to `bind()` after `replaceSystemStatusArea`, and
> pass `viewModel.topStatusStripMode` from the `statusStripManager.bind(..)` call in
> `LauncherHomeActivity` (around line 259-264). Collect it with `collectOnLifecycle`, matching the
> existing collectors.

**Why:**

Strategic ADR-2 keeps this manager the only node allowed to change what the band shows, so the mode has
to reach the band through it rather than through a second observer on the same views.

**Verification:**

- `Grep` - `topStatusStripMode` matches in both touched files.
- `Grep` - `collectOnLifecycle` matches at least four times in `LauncherStatusStripManager.kt`.
- `Grep` - `lifecycleScope.launch` returns zero hits in both files (Rule 19: lifecycle-safe collection).

**Status:** `[x] done`

---

### Step 04.2 - Inflate the clock and the indicator row into the strip

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt`
**Depends on:** Step 04.1

**Prompt for developer:**

> When the mode turns on, inflate `launcher_status_clock` and `launcher_status_indicators` against the
> strip's content slot and hand them to `setPinnedStart` and `setPinnedEnd` on `launcherSignalRow`. When
> it turns off, pass `null` to both setters and drop the inflated references. Hold the two bindings in
> fields and clear them in `unbind()` alongside the existing teardown.

**Why:**

Strategic §4.3 places the clock at the left edge and the indicators at the right edge of the same band,
and strategic ADR-2 forbids any other class adding a child to the strip's content slot.

**Verification:**

- `Grep` - `setPinnedStart` and `setPinnedEnd` each match in `LauncherStatusStripManager.kt`.
- `Grep` - both new binding fields are assigned `null` inside `unbind()`.
- `Grep` - `LauncherStatusClockBinding` and `LauncherStatusIndicatorsBinding` both referenced.

**Status:** `[x] done`

---

### Step 04.3 - Drive the indicators with the shared renderer

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt`
**Depends on:** Step 04.2

**Prompt for developer:**

> Construct a `LauncherTrayManager` over the strip's clock and indicator bindings and bind it to the
> same tray composition the taskbar uses, so the six toggles govern both placements. Release it when the
> mode turns off and in `unbind()`. Do not copy any rendering, colour or permission rule into this class.

**Why:**

Strategic §11 criterion 7 requires the set and order of indicators to be identical in the tray and on
the strip, and strategic ADR-1 achieves that by reusing the one renderer rather than writing a second.

**Verification:**

- `Grep` - `LauncherTrayManager` referenced in `LauncherStatusStripManager.kt`.
- `Grep` - no indicator-specific rendering symbols (`applyBluetooth`, `renderBattery`, `applySim`)
  appear in `LauncherStatusStripManager.kt`.

**Status:** `[x] done`

---

### Step 04.4 - Give the strip clock its seconds

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt`
**Depends on:** Step 04.3

**Prompt for developer:**

> Set `format12Hour` to `h:mm:ss` and `format24Hour` to `H:mm:ss` on the strip's `TextClock` when the
> mode turns on, matching `gadget_launcher_clock.xml`. Leave the taskbar clock's format untouched. Add no
> ticker, handler or broadcast receiver.

**Why:**

The owner's captured text (§0) asks for seconds explicitly, and research 01 §2 verified against
`android.jar` that `TextClock` declares `onVisibilityAggregated(boolean)` and therefore stops its own
per-second ticker when the launcher is not visible - which is what strategic §3.2 requires, and a custom
ticker would not do without extra lifecycle code.

**Verification:**

- `Grep` - `h:mm:ss` matches in `LauncherStatusStripManager.kt`.
- `Grep` - `H:mm:ss` matches in `LauncherStatusStripManager.kt`.
- `Grep` - `Handler(`, `postDelayed`, `ACTION_TIME_TICK` each return zero hits in that file.

**Status:** `[x] done`

---

### Step 04.5 - Reserve the mode collector as the probe site (probe itself deferred)

**Files:** `app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherStatusStripManager.kt`
**Depends on:** Step 04.4

**Prompt for developer:**

> Leave the mode collector as the single entry point of the flow the owner will verify, and add NO
> `Timber.d("S1431:` here. The probe is written once, with every other flow-entry probe, in the final
> tag pass that accompanies the flip to `BlockNeedUserTest`.

**Why:**

Plan defect corrected during execution. `assert-no-ticket-logs.ps1` runs inside `post-change.ps1` and
hard-fails any `Timber.*("Sxxxx:` line whose spec is not currently `BlockNeedUserTest`; every
intermediate phase leaves this spec `In Progress`, so a per-phase probe blocks its own phase closure.
CLAUDE.md "Debug Verification Tags" states the same rule from the other side - the tag exists if and
only if the status does.

**Verification:**

- `Grep` - `Timber.d("S1431:` returns zero hits in `LauncherStatusStripManager.kt` at this phase.
- `Grep -n "Log\.d\("` - zero hits in that file.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - `a.ps1 fk` exit 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The band shows clock, signals and indicators whenever `topStatusStripMode` emits `true`. The mode is
still unreachable from the UI - phase 06 adds the switch. The existing S1421 probes in this file belong
to that ticket and stay.

---

## Rollback Plan

Revert phase commit(s). No persisted data changes; the strip returns to signals only.

---

## Step Log

- 2026-08-09 - Step 04.1 done. `bind()` takes `topStatusStripMode`, `trayComposition` and `onRequestPhoneStatePermission` (7 parameters, under detekt's threshold of 8) and the activity call site passes all three by name. expected: `topStatusStripMode` in both files, `collectOnLifecycle` >= 4, `lifecycleScope.launch` 0 | actual: 5 / 1, 5, 0 / 0.
- 2026-08-09 - Step 04.2 done, with a deliberate deviation. The step said to inflate on mode-on and drop the references on mode-off. Inflating per toggle would mean a NEW renderer over NEW views on every switch, while the previous renderer kept its lifecycle-scoped collectors alive over a detached hierarchy - a leak per toggle. Instead both layouts are inflated once in `bind()`, the mode only pins and unpins them, and the renderer's own visibility gate (step 04.3) is what releases the receivers. `unbind()` unpins both and clears all three fields. expected: both setters present, both fields nulled in `unbind()`, both binding types referenced | actual: yes, lines 255-256, 7 references.
- 2026-08-09 - Step 04.3 done. The strip gets its own `LauncherTrayManager` over its own two bindings, bound to the same `trayComposition` the taskbar uses, with `topStatusStripMode` as its visibility gate - so turning the mode off unregisters the battery receiver, the network callback and the Bluetooth/SIM collectors rather than merely hiding views. No rendering, colour or permission rule was copied. expected: `LauncherTrayManager` referenced, no `applyBluetooth`/`renderBattery`/`applySim` | actual: 3 references, 0.
- 2026-08-09 - Step 04.4 done. `format12Hour` / `format24Hour` set on the strip clock only, from companion constants mirroring `gadget_launcher_clock.xml`. No ticker, handler or time-tick receiver added. expected: `h:mm:ss` 1, `H:mm:ss` 1, ticker symbols 0 | actual: 1, 1, 0.
- 2026-08-09 - Step 04.5 rewritten during execution, plan defect. The probe was written as the step asked and `post-change.ps1` failed: `assert-no-ticket-logs` reported `expected: 0 | actual: 1 - stale probe (ticket not BlockNeedUserTest)`, because every intermediate phase leaves this spec `In Progress` and the gate enforces CLAUDE.md's tag-iff-status rule repo-wide. The probe was removed, the step rewritten to reserve the mode collector as the probe SITE, and the probe itself moved to the final tag pass that accompanies the `BlockNeedUserTest` flip. expected after the fix: `Timber.d("S1431:` 0 hits | actual: 0, and the gate passed on the re-run.
- 2026-08-09 - Phase-boundary audit. One P1 found and deliberately carried into phase 05, where it is that phase's whole subject: with the mode on, the taskbar's tray manager is still gated on `replaceSystemStatusArea` alone, so both renderers would count as visible at once - two battery receivers, two network callbacks and two permission requests. It cannot bite yet (the mode has no switch until phase 06 and defaults off), and phase 05 fixes it by gating the taskbar tray on `replaceSystemStatusArea && !topStatusStripMode`. Recorded here so the fix is not mistaken for a phase-05 nicety.
- 2026-08-09 - Audit note, no action: the strip's renderer instance logs S1087's own probe (`S1087: tray status content visible=`) with the MODE as its subject. S1087 is in `BlockNeedUserTest`, so the line is legitimate, but that ticket's device test will now see the probe twice with two meanings. Left untouched - editing another ticket's probe mid-device-test is the larger risk.
- 2026-08-09 - Closure. `post-change.ps1 -Files <2> -ScopeToFile -ChangeType Kotlin`: every gate PASS except `assert-detekt`, which FAILs on the pre-existing un-baselined `LauncherHomeActivity` debt parked as S1541 - the same two findings, unchanged. Dev log run directly, since a failed facade writes no changelog row.
