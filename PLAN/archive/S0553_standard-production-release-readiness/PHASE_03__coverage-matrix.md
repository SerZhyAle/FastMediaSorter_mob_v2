# Phase 03 - Coverage matrix

**Strategic spec:** [`../S0553_standard-production-release-readiness.md`](../S0553_standard-production-release-readiness.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 2 / 2
**Started:** 2026-06-20
**Completed:** 2026-06-20

## Step Log

- 2026-06-20 - 03.1 PASS: `docs/release/standard-coverage-matrix.json` created; parses; intentionally-excluded + coverageGaps + best-effort-waiver (Cast/Wear) present.
- 2026-06-20 - 03.2 PASS: Coverage matrix table + Evidence ladder (all six levels) + risk-bucket mapping + single-device gap rendered; placeholder removed.

---

## Objective

Produce a machine-readable coverage manifest and the rendered coverage matrix + evidence ladder, recording for each capability group its coverage status, required evidence level, and the explicit single-device coverage gap.

---

## Prerequisites

- [ ] Phase 01 (surface snapshot) and Phase 02 (release-risk) Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/release/standard-coverage-matrix.json` | New | ≤ 250 |
| `docs/RELEASE_READINESS_STANDARD.md` | Modified | ≤ 400 |

---

## Steps

### Step 03.1 - Author the machine-readable coverage manifest

**Files:** `docs/release/standard-coverage-matrix.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `docs/release/standard-coverage-matrix.json`: an array of capability-group rows. Each row has `group` (e.g. local/SMB/SFTP/FTP, cloud+auth, video, audio+persistent, image, PDF/EPUB/text, OCR, translation, Cast, Wear companion, widgets+default-player, statistics/backup/settings-search), `coverage` (one of `covered`/`partial`/`not`/`intentionally-excluded`), `evidenceLevel` (one of `static`/`fast-build`/`release-build`/`emulator-spine`/`manual-device`/`play-console`), and `note`. Record the owner single-device decision (§3.3) as an explicit `partial` row or a top-level `coverageGaps` entry stating API/OEM diversity is unproven by design. Cast and Wear rows carry `note: "best-effort waiver (§3.3)"`.

**Verification:**

- `Glob` - `docs/release/standard-coverage-matrix.json` exists and parses: `pwsh -NoProfile -Command "Get-Content -Raw docs/release/standard-coverage-matrix.json | ConvertFrom-Json | Out-Null"` exits 0.
- `Grep` - `intentionally-excluded` and `coverageGaps` (or a `partial` single-device note) present.
- `Grep` - `best-effort waiver` present on Cast/Wear rows.

**Status:** `[x]` done

---

### Step 03.2 - Render the coverage matrix + evidence ladder in the gate document

**Files:** `docs/RELEASE_READINESS_STANDARD.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Replace the `Coverage matrix` placeholder with a table rendered from `standard-coverage-matrix.json` (group × coverage × evidence level × note) and an `Evidence ladder` subsection defining what each evidence level proves (static / fast-build / release-build / emulator-spine / manual-device / play-console) and mapping each strategic §6 risk bucket to its required minimum evidence level. State the single-device coverage gap explicitly as an accepted, recorded gap (not a silent PASS), per ADR-2.

**Verification:**

- `Grep` - `## Coverage matrix` no longer contains a `Filled in Phase` placeholder.
- `Grep` - `Evidence ladder` subsection present with all six levels named.
- `Grep` - `standard-coverage-matrix.json` referenced.
- `Grep` - single-device gap stated (e.g. `single device` / `API.*diversity`).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] JSON manifest parses (run the ConvertFrom-Json check) - record exit code.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added (or batched in Phase 05).

---

## Handoff Notes to Next Phase

The coverage manifest + matrix exist. Phase 04 folds the manifest into the verdict aggregator and the operator evidence pack.

---

## Rollback Plan

Revert phase commit(s) - new JSON + doc section, no runtime change.
