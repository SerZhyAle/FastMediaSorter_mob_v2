# Phase 00 - Ratchet gate seeded at the current count

**Strategic spec:** [`../S1329_activity-logic-debt-78-baselined-violations.md`](../S1329_activity-logic-debt-78-baselined-violations.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** -
**Blocks:** nothing - but every later phase ratchets the baseline this phase creates
**Steps done:** 2 / 2
**Started:** 2026-08-05
**Completed:** 2026-08-13

---

## Objective

Stop the debt growing before a single violation is fixed. The gate is created first and seeded at the
count that exists today, then ratcheted down by each code phase. Owner decision (strategic §6.2,
2026-08-02): seed now at 78, not at the end of the sweep at 32.

Without this phase, Phases 01-05 run with nothing preventing a sixteenth Activity from gaining a
domain injection - the exact mechanism that let the count reach 78 unnoticed.

---

## Prerequisites

- [x] Working tree is clean or on a feature branch.
- [x] Source count re-derived and equal to 78 - verify, do not assume the strategic §0 number
      (re-verified 2026-08-02: `app_v2/lint-baseline.xml` holds 78 `ActivityLogicViolation` entries).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-activity-logic-not-growing.ps1` | New | ≤ 180 |
| `scripts/quality/activity-logic-baseline.txt` | New | 1 |
| `scripts/quality/assert-fast-gates.ps1` | Modified | ≤ +3 |

---

## Steps

### Step 00.1 - Create the ratchet gate, seeded at 78

**Files:** `scripts/quality/assert-activity-logic-not-growing.ps1` (New),
`scripts/quality/activity-logic-baseline.txt` (New)
**Depends on:** - start of phase

**Prompt for developer:**

> Mirror `scripts/quality/assert-flavor-flags-not-growing.ps1` - it is the established ratchet shape in this
> repo. Note that the sibling is now a thin wrapper: the rule itself lives in
> `scripts/quality/lib/source-matchers.ps1` and is executed by `assert-source-gates.ps1` over a single walk
> of the tree. Prefer adding an `activity-logic` matcher there over authoring a sixth independent tree walk;
> fall back to a standalone script only if the matcher cannot express the rule.
>
> The gate counts violations and compares them against a committed single-integer baseline in
> `scripts/quality/activity-logic-baseline.txt`; the count may only go down.
>
> Count from **source**, not from `app_v2/lint-baseline.xml`, applying the detector's own rule from
> `lint-rules/src/main/java/com/sza/fastmediasorter/lint/ActivityLogicDetector.kt`: an `@Inject` field in a
> `*Activity` class whose declared type contains `Repository`, `UseCase`, `DataSource`, `Dao` or `Database`,
> **case-sensitively**. Counting from source keeps the gate honest even when the XML baseline is stale, and
> matches how the sibling gate works. Two traps that will produce a wrong count if missed:
>
> - Declarations wrap. `PlayerActivity.kt` has three where `@Inject lateinit var name:` and the type sit on
>   different lines. A line-oriented scan silently undercounts by three.
> - The match is case-sensitive. `com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore` in
>   `BrowseActivity` has a lowercase `repository` package segment and is **not** a violation; a
>   case-insensitive scan over-counts by one.
>
> Support the same three modes as the sibling gate: default report, `-Gate` (exit 1 on growth), and
> `-UpdateBaseline` (ratchet down only, refuse to raise). Follow CLAUDE.md Rule 7 / S1070 for exit codes -
> use `Write-Error $msg -ErrorAction Continue` before any `exit N` where N is not 1, and list the codes the
> script actually returns in its header. Scan all source sets, not just `src/main`:
> `ScreenCaptureConsentActivity` lives in `app_v2/src/screenCapture/`.
>
> Seed the baseline at **78** - the count as it stands before Phase 01. Do not seed at 32; that number is
> reached only after Phases 01-05 and is applied by Phase 06.

**Verification:**

- `Glob` - `scripts/quality/assert-activity-logic-not-growing.ps1` exists.
- `Glob` - `scripts/quality/activity-logic-baseline.txt` exists and its only content is `78`.
- `Grep` - `-UpdateBaseline` and `-Gate` both appear as parameters in the new script.
- `Grep` - `Write-Error` is followed by `-ErrorAction Continue` at every site preceding a non-1 `exit` in the new script.
- Run `pwsh -NoProfile -File scripts/quality/assert-activity-logic-not-growing.ps1` - exit code 0 and reported count is `78`.
- Run `pwsh -NoProfile -File scripts/quality/assert-activity-logic-not-growing.ps1 -Gate` - exit code 0.
- Run `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` - exit code 0 (new script honours the contract).

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - 00.1: activity-logic matcher already lived in lib/source-matchers.ps1, so the step needed only the wrapper and the seed. Created scripts/quality/assert-activity-logic-not-growing.ps1 (thin forwarder, mirrors assert-flavor-flags-not-growing.ps1) and seeded scripts/quality/activity-logic-baseline.txt at 78 via -UpdateBaseline. Source count re-derived, not assumed: 78. Predicates: default run exit 0 reporting 78; -Gate exit 0; assert-exit-contract exit 0. 00.2: step prompt was stale - S1338 replaced the five per-rule gate-table entries (assert-flavor-flags-not-growing.ps1 among them) with one assert-source-gates.ps1 entry running every lexical rule over a single tree walk, and activity-logic was already in that rule set. A sixth table entry would have added a second full walk and a gate label the S1598 hint registry does not know, so the gate would fail mute. Patched the step to the half that still applies and synced the documented set to the executed set in the .DESCRIPTION header of assert-fast-gates.ps1, replacing the three superseded wrapper names. Bite test run and reverted: throwaway @Inject SettingsRepository in CalculatorActivity.kt took the count to 79 and -Gate returned expected 1 | actual 1. Full batch: assert-source-gates PASS, activity-logic baseline 78 actual 78 delta 0. Unrelated red gate assert-unreferenced-strings (orphaned key network_monitor_local_ip_label, touches no S1329 file) parked as S1624.

---

### Step 00.2 - Wire the gate into the fast-gates batch

**Files:** `scripts/quality/assert-fast-gates.ps1`
**Depends on:** Step 00.1

**Prompt for developer:**

> **Corrected 2026-08-13 during execution.** The step was written against a gate table that no longer
> exists: S1338 replaced the five per-rule entries - `assert-flavor-flags-not-growing.ps1` among them -
> with one `assert-source-gates.ps1` entry that runs every lexical rule over a single walk of the tree.
> Because step 00.1 took the matcher route the plan itself preferred, `activity-logic` is already in that
> runner's rule set and therefore already executed by the batch. Adding a sixth table entry would spawn a
> second full tree walk for a rule already counted, and would introduce a gate label that the S1598 hint
> registry does not know, so the gate would fail mute.
>
> The remaining work is the half of the original prompt that still applies: make the documented set match
> the executed set. In the `.DESCRIPTION` header block of `assert-fast-gates.ps1`, replace the three stale
> wrapper names that S1338 superseded (`assert-flavor-flags-not-growing`, `assert-neuroslop`,
> `assert-deprecated-pm-flags`) with the `assert-source-gates` entry that actually runs, naming
> `activity-logic` among its rules. Change no gate-table entry and no argument.

**Verification:**

- `Grep` - `activity-logic` matches at least once in the `.DESCRIPTION` header of `assert-fast-gates.ps1`.
- `Grep` - `assert-flavor-flags-not-growing` returns zero hits in `assert-fast-gates.ps1` (the stale name is gone).
- `Grep` - the gate table still holds exactly one `assert-source-gates.ps1` entry and no `assert-activity-logic-not-growing.ps1` entry.
- Run `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` - the `assert-source-gates.ps1` row reports
  PASS with `activity-logic` at `baseline 78 | actual 78 | delta 0`. Judge that row, not the batch's aggregate
  exit code: the tree is shared and always dirty, so an unrelated ticket's in-flight WIP reddening a different
  gate is the condition CLAUDE.md section 12 "Dirty-tree closure" exists for. Any such unrelated FAIL is named
  in the Step Log with the ticket parked for it.
- Add a throwaway `@Inject lateinit var probe: SettingsRepository` to any in-scope Activity, run
  `assert-activity-logic-not-growing.ps1 -Gate`, confirm exit 1, then revert. A ratchet that never refuses
  anything is not a gate - prove it bites.

**Status:** `[x]` done

**Step Log:**

- 2026-08-13 - 00.1: activity-logic matcher already lived in lib/source-matchers.ps1, so the step needed only the wrapper and the seed. Created scripts/quality/assert-activity-logic-not-growing.ps1 (thin forwarder, mirrors assert-flavor-flags-not-growing.ps1) and seeded scripts/quality/activity-logic-baseline.txt at 78 via -UpdateBaseline. Source count re-derived, not assumed: 78. Predicates: default run exit 0 reporting 78; -Gate exit 0; assert-exit-contract exit 0. 00.2: step prompt was stale - S1338 replaced the five per-rule gate-table entries (assert-flavor-flags-not-growing.ps1 among them) with one assert-source-gates.ps1 entry running every lexical rule over a single tree walk, and activity-logic was already in that rule set. A sixth table entry would have added a second full walk and a gate label the S1598 hint registry does not know, so the gate would fail mute. Patched the step to the half that still applies and synced the documented set to the executed set in the .DESCRIPTION header of assert-fast-gates.ps1, replacing the three superseded wrapper names. Bite test run and reverted: throwaway @Inject SettingsRepository in CalculatorActivity.kt took the count to 79 and -Gate returned expected 1 | actual 1. Full batch: assert-source-gates PASS, activity-logic baseline 78 actual 78 delta 0. Unrelated red gate assert-unreferenced-strings (orphaned key network_monitor_local_ip_label, touches no S1329 file) parked as S1624.

---

## Phase Done Criteria

- [x] Every `Step 00.*` above is `[x] done`.
- [x] Script cheatsheet regenerated for the new script, and
      `pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1` exits 0. This phase adds a
      `scripts/` entry, and the cheatsheet is a registered document in `docs/DOCUMENT_REGISTRY.jsonl`
      (`script-cheatsheet`), so the sync gate fails until it is regenerated.
- [x] `pwsh -NoProfile -File scripts/quality/assert-fast-gates.ps1` reports PASS on every gate this phase can
      affect - `assert-source-gates.ps1` (which now carries `activity-logic`), `assert-exit-contract.ps1` and
      `assert-gate-hints-sync.ps1`. An unrelated gate red from another ticket's in-flight WIP is recorded in the
      Step Log with the ticket parked for it, per CLAUDE.md section 12 "Dirty-tree closure", not fixed here.
- [x] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

The gate is live at 78. Every later code phase must ratchet it down with `-UpdateBaseline` as part of its
own Done Criteria, so a phase that clears seven violations leaves the baseline at seven fewer. Phase 06
applies the final ratchet to 32 and regenerates the lint baseline.

---

## Rollback Plan

Revert the phase commit(s). The gate is additive - removing it restores the previous check set.
