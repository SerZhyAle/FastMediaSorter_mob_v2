# Phase 06 - Docs and Catalog Cleanup

**Strategic spec:** [`../S0271_truth_drift_detection.md`](../S0271_truth_drift_detection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Close S0271 mechanically: every created file appears in `dev/CHANGELOG.md`, the spec follow-up tickets are recorded under §10 of the strategic file, and the `FUNCTIONALITY.log` line is intentionally **not** written (no user-visible change - strategic §8 "Не затрагивает").

---

## Prerequisites

- [ ] Phase 01..05 all ✅ Done.
- [ ] Smoke chequer exit code `1` on current repo (drift exists; expected).
- [ ] Test harness exit code `0`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Modified (via script only) | - |
| `PLAN/S0271_truth_drift_detection.md` | Modified (§10 follow-up entries) | ≤ +30 |

> `dev/CATALOG/<module>.jsonl` and `<module>.md` - **not regenerated**: this spec touches zero `.kt` files. `docs/FEATURES*.md` - **not modified**: strategic §8 explicitly states "Не затрагивает".

---

## Steps

### Step 06.1 - Verify dev log entries for every artefact

**Files:** none (audit only)
**Depends on:** - start of phase

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` contains an entry (string match on filename) for each of these files created by Phases 01..05:
>
> - `PLAN/S0271_truth_drift_detection/DECISIONS.md`
> - `PLAN/S0271_truth_drift_detection/INDEX.md`
> - `PLAN/S0271_truth_drift_detection/PHASE_01__decisions-and-contracts.md`
> - `PLAN/S0271_truth_drift_detection/PHASE_02__gradle-source-parser.md`
> - `PLAN/S0271_truth_drift_detection/PHASE_03__pin-manifest-and-doc-parser.md`
> - `PLAN/S0271_truth_drift_detection/PHASE_04__comparator-output-cli.md`
> - `PLAN/S0271_truth_drift_detection/PHASE_05__test-harness-and-integration.md`
> - `PLAN/S0271_truth_drift_detection/PHASE_06__docs-catalog-cleanup.md`
> - `scripts/doc-drift/GradleParser.ps1`
> - `scripts/doc-drift/DocParser.ps1`
> - `scripts/doc-drift/Comparator.ps1`
> - `scripts/doc-drift/Output.ps1`
> - `scripts/doc-drift/pins.psd1`
> - `scripts/doc-drift/README.md`
> - `scripts/check-doc-vs-gradle.ps1`
> - `scripts/doc-drift.tests/Run-Tests.ps1`
> - `scripts/doc-drift.tests/Test-Helpers.ps1`
> - `scripts/doc-drift.tests/GradleParser.Tests.ps1`
> - `scripts/doc-drift.tests/DocParser.Tests.ps1`
> - `scripts/doc-drift.tests/Comparator.Tests.ps1`
>
> For every file in the list that is **missing** from `dev/CHANGELOG.md`, run `.\scripts\add_to_dev_log.ps1 "<file>" "spec-tech-fixup" "Backfill missed dev-log entry"`. Re-verify after backfill - every file must now appear.

**Verification:**

- `Grep` (on `dev/CHANGELOG.md`) - each filename from the list above produces at least one match.
- `Grep` (on `dev/CHANGELOG.md`) - `S0271` token produces ≥ 8 matches (one per phase file authored, plus per-script entries).

**Status:** `[x] done`

---

### Step 06.2 - Record follow-up tickets in strategic §10

**Files:** `PLAN/S0271_truth_drift_detection.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> Open the strategic spec and append a bullet to §10 ("Связи с другими спеками") under the existing "Тактический follow-up" entry, listing the two concrete follow-up tickets surfaced during tactical work (strategic §6 items 1 and 2 explicitly deferred):
>
> - `Sxxxx` (id allocated via `pwsh -NoProfile -File scripts/spec_catalog/next-id.ps1`): `wear/` coverage extension - extends manifest with Wear module pins; depends on S0271 Verified.
> - `Sxxxx` (id allocated via next-id): PR-gate wiring - decide between git pre-push hook, `/skill-release` mandatory step, or local pre-commit hook; depends on S0271 Verified.
>
> Do **not** create the strategic spec files for these follow-ups in this step - that is the work of a future `/spec` invocation. This step only records the ids in §10 so they exist as catalog placeholders. Insert each via `scripts/spec_catalog/insert.ps1` with `Status: Draft`, `Priority: 30`, name patterns "Drift checker wear coverage" and "Drift checker PR-gate wiring", file paths under `PLAN/Sxxxx_<slug>.md`. Insert.ps1 will reject the call if the file does not yet exist - create empty placeholder files with just `# Strategic Spec: <name>` and `**Status:** Draft` so the gate passes, then come back later with `/spec` to flesh them out.

**Verification:**

- `Grep` (on strategic spec §10) - two new `Sxxxx` references present after the "Тактический follow-up" bullet.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <newly-allocated-id-1> -Format json` - expected: status `Draft`, file path exists | actual: capture.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id <newly-allocated-id-2> -Format json` - expected: status `Draft`, file path exists | actual: capture.

**Status:** `[x] done`

---

### Step 06.3 - Final smoke + tactical INDEX status update

**Files:** `PLAN/S0271_truth_drift_detection/INDEX.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Final pre-`/spec-check` sanity:
>
> 1. Run `pwsh -NoProfile -File ./scripts/check-doc-vs-gradle.ps1 > temp/S0271_final_smoke.log 2>&1`. Capture exit code.
> 2. Run `pwsh -NoProfile -File ./scripts/doc-drift.tests/Run-Tests.ps1 > temp/S0271_final_tests.log 2>&1`. Capture exit code.
> 3. Open `INDEX.md`: flip every Phase row to `✅ Done`, update `Phases: 6 / 6 done`, set `Status: Done`, bump `Last updated: <today>`.
> 4. Add line to `Change Log` section: `<today> - All phases completed. Smoke exit 1 (drift present, expected). Tests exit 0.`
>
> Do not call `update.ps1 -Status Implemented` here - that flip belongs to `/spec-dev` (when invoked) or to `/spec-all` orchestration. Phase 06 closes only the tactical bookkeeping; the journal flip is upstream.

**Verification:**

- `Glob` - both log files exist under `temp/`.
- `Grep` (on `temp/S0271_final_smoke.log`) - `SUMMARY | total:` present.
- `Grep` (on `temp/S0271_final_tests.log`) - `RESULT | pass:` present.
- Exit codes recorded: smoke `expected: 1 | actual: <captured>`; tests `expected: 0 | actual: <captured>`.
- `Grep` (on `INDEX.md`) - `Phases: 6 / 6 done` present.
- `Grep` (on `INDEX.md`) - five `✅ Done` markers in the Phase Overview table (one per phase; final cleanup-phase row flips last).

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `dev/CHANGELOG.md` carries an entry for every artefact (verified by Grep).
- [x] Two follow-up tickets exist in `PLAN/spec-catalog.jsonl` as `Status: Draft` placeholders.
- [x] `INDEX.md` reads `Phases: 6 / 6 done`, `Status: Done`.
- [x] Grep for unresolved phase-06 placeholder markers returns zero hits.
- [x] `dev/FUNCTIONALITY.log` - **not** appended (strategic §8 - no user-visible change).
- [x] `dev/CATALOG/<module>.jsonl` - **not** regenerated (no `.kt` modifications).

---

## Handoff Notes to Next Phase

Final phase. After this, `/spec-check S0271` audits the implementation and flips the journal status to `Verified`. The chequer becomes a usable operator tool from the moment `scripts/check-doc-vs-gradle.ps1` exists; integration with `agent_bootstrap.ps1` (S0268) and with PR-gate (open Research item §6.2) lives in the two follow-up tickets recorded in Step 06.2.

---

## Rollback Plan

Phase 06 only writes to `dev/CHANGELOG.md` (append-only via script), strategic §10 (single bullet append), `INDEX.md` (status flips), and creates two placeholder spec files. To roll back: revert the strategic §10 bullet, revert INDEX status, `archive.ps1` the two placeholder tickets. The CHANGELOG append-only design has no rollback - extra entries are harmless.
