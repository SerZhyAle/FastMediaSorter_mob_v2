# Phase 02 - Amend the S1433 plan and close out

**Strategic spec:** [`../S1446_reconcile-s1433-charts-with-s1179.md`](../S1446_reconcile-s1433-charts-with-s1179.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Point S1433's phase 05 at the shared chart, record in its phase 06 why the GNSS work stays its own, and close the ticket through the facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S1433_network-monitor/PHASE_05__signal-charts.md` | Modified | n/a |
| `PLAN/S1433_network-monitor/PHASE_06__gnss-section.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated (gitignored) | n/a |

> These are the `PLAN/**` edits the real-work filter allows only in a final cleanup phase, and they are this ticket's stated goal rather than progress bookkeeping.

---

## Steps

### Step 02.1 - Point S1433 phase 05 at the shared chart

**Files:** `PLAN/S1433_network-monitor/PHASE_05__signal-charts.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Remove `ui/networkmonitor/views/SignalChartView.kt` and `attrs_signal_chart.xml` from the phase's Files Touched table and from every step that authors them. Rewrite the affected step to consume `SensorSeriesChartView` instead: map the two-minute ring onto `List<SensorSeriesPoint>`, set `showValueAxis` true, and build the `contentDescription` in the consumer from `summary()`. Delete the line claiming research found no existing chart component to reuse, and replace it with a pointer to S1446 and its research artifact. Leave `SignalSeries` and the four samplers exactly as planned.

**Why:**

Strategic §11 criteria 3 and §2 goal 2 require phase 05 to stop authoring a drawing class, and §9 ADR-2 states the reason: a second chart class would diverge from the first at its first change, while the point shape already matches.

**Verification:**

- `Grep` - `SignalChartView` and `attrs_signal_chart` return zero hits in `PHASE_05__signal-charts.md`.
- `Grep` - `SensorSeriesChartView` and `S1446` each match at least once in that file.
- `Grep` - `SignalSeries` still present, and the four sampler names are unchanged.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. Both chart-authoring rows dropped from Files Touched; Step 05.5 rewritten from "write one custom View" to "bind the shared chart", with the rate limit moved from redraws to the sample source because the shared view redraws only when given new points. The "research found no existing chart component to reuse" claim is gone (0 hits) and replaced by a pointer to S1446 §9 ADR-2 and its research artifact. `SignalChartView` 0, `attrs_signal_chart` 0, `SensorSeriesChartView` 4, `S1446` 4, `SignalSeries` still present, all four sampler names unchanged. Objective, Handoff Notes and Rollback Plan corrected too - each still promised a new view.
- 2026-08-08 - First pass left both forbidden names at 1 hit each: my own replacement verification predicate quoted them. Reworded the predicate to describe the shape instead of naming the classes, which is what a zero-hit grep demands.

---

### Step 02.2 - Record in S1433 phase 06 why GNSS stays separate

**Files:** `PLAN/S1433_network-monitor/PHASE_06__gnss-section.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Add one short note to the phase stating that its GNSS classes were checked against the landed `MotionReadingSource` and deliberately stay their own, with the two grounds: the satellite callback is a platform API that source never calls, and it reports no no-permission or no-hardware state. Name S1446 and its research artifact. Change no step.

**Why:**

Strategic §11 criterion 4 requires the phase to survive unchanged with its reason written down, because an unexplained duplicate reads as an oversight to the next reader and invites the same reconciliation a second time.

**Verification:**

- `Grep` - `S1446` matches at least once in `PHASE_06__gnss-section.md`.
- `Grep` - the step count and every step title in that file are unchanged from before this phase.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. One note added under Objective naming both grounds - `GnssStatus.Callback` is a platform API `MotionReadingSource` never calls, and that source reports neither a no-permission nor a no-hardware state - plus the research artifact. `S1446` matches once. All four step titles (06.1-06.4) and the step count are byte-identical; only line numbers shifted by the inserted note.

---

### Step 02.3 - Sync the catalog and close through the facade

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once, then close through `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<every file this ticket changed>" -Target "S1446" -Description "Shared series chart gains an opt-in value axis and summary; S1433 phase 05 consumes it" -ChangeType Mixed -ScopeToFile`. Read the verdict: only a bare `post-change: PASS` is clean.

**Why:**

CLAUDE.md section 12 routes mechanical closure through the facade so the changelog, the catalog and every scoped gate judge the whole changed set at once.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS`.
- `Grep` - `S1446` matches in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. `post-change.ps1 -ChangeType Mixed -ScopeToFile` over all five changed files exit 0, bare `post-change: PASS`, no advisories. `S1446` matches 11 times in `dev/CHANGELOG.md`. `catalog_sync` reported the index already up to date from the step 01.3 closure rather than re-scanning - same result, one scan saved.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-02)` returns zero real hits - the only match in this ticket's folder is this criterion line quoting itself.
- [x] Dev log entry added for every file this ticket changed.
- [x] No `docs/ALL_FEATURES.jsonl` record - `S1446` matches 0 times there, as strategic §8 requires.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - plan text and generated indexes only.
