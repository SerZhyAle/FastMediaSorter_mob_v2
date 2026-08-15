# Phase 04 - Gate and legal document

**Strategic spec:** [`../S1495_oss-notices-incomplete.md`](../S1495_oss-notices-incomplete.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-10
**Completed:** 2026-08-10

---

## Objective

Make the published pages defensible mechanically: a gate that fails on drift, wired into the closure facade, and a `THIRD_PARTY_LICENSES.md` sentence that names a mechanism that exists.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-oss-notices.ps1` | New | ≤ 220 |
| `scripts/post-change.ps1` | Modified | ≤ 60 added |
| `THIRD_PARTY_LICENSES.md` | Modified | ≤ 10 |

---

## Steps

### Step 04.1 - Write the gate

**Files:** `scripts/quality/assert-oss-notices.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/quality/assert-oss-notices.ps1` following `scripts/quality/assert-flavor-matrix-docs.ps1`: a `-Gate` switch separating fatal from read-only report mode, a `-Quiet` switch, and a header naming every exit code. It invokes the generator with `-Check` and additionally asserts that every shipping coordinate resolves in the manifest. Report each failure with its coordinate or its differing artifact. Per CLAUDE.md section 7 on reachable exit codes, write `Write-Error $msg -ErrorAction Continue` before any `exit N` where N is not 1.

**Why:**

Strategic §2.5 requires the list to survive dependency changes rather than decay, and §7 records that the manual list going stale again is the highest-probability risk on the ticket - a gate is the only thing that converts that risk into a failed close.

**Verification:**

- `Glob` - `scripts/quality/assert-oss-notices.ps1` exists.
- Run it with `-Gate` on the current tree - exit 0.
- Alter one character in `docs/OPEN_SOURCE.md`, run it with `-Gate` - exit 1, the artifact named. Restore the file.
- `Grep` - `Exit codes` present in the script header.

**Status:** `[x] done`

---

### Step 04.2 - Wire the gate into post-change

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> Register an `oss-notices-gate` step in `scripts/post-change.ps1` next to `flavor-matrix-doc-gate`, using the same applicability shape: fire when a changed file is either build file, the licence manifest, the parser, the generator, the gate itself, the snapshot, or any `docs/OPEN_SOURCE*.md`. Skip with an explicit reason otherwise. Invoke the gate with `-Gate -Quiet`.

**Why:**

Strategic §11.7 states that the gate must be called from `post-change.ps1`, because a check nobody runs on the path that closes a change is the same absent mechanism the ticket is fixing.

**Verification:**

- `Grep` - `oss-notices-gate` matches in `scripts/post-change.ps1`.
- `Grep` - `assert-oss-notices.ps1` matches in `scripts/post-change.ps1`.
- Run `post-change.ps1` against a changed `docs/OPEN_SOURCE.md` - the step runs rather than skipping.
- Run it against an unrelated changed file - the step reports skipped with its reason.

**Status:** `[x] done`

---

### Step 04.3 - Correct the false reference

**Files:** `THIRD_PARTY_LICENSES.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Rewrite the sentence at `THIRD_PARTY_LICENSES.md:5` so it names `scripts/docs/generate-oss-notices.ps1` and the pages it renders instead of the non-existent OSS licence aggregator in release-prep tooling. Keep the document's scope unchanged - it stays limited to bundled binary assets - and keep the existing CC0 entry untouched.

**Why:**

Strategic §2.3 requires that no legal document point at a mechanism that does not exist, and §0 records that this sentence created the appearance of coverage where there was none; strategic non-goals forbid widening this document any further than fixing that reference.

**Verification:**

- `Grep` - `OSS license aggregator` returns zero hits in `THIRD_PARTY_LICENSES.md`.
- `Grep` - `generate-oss-notices.ps1` matches in `THIRD_PARTY_LICENSES.md`.
- `Grep` - `CC0 1.0 Universal` still matches, the asset entry intact.

**Status:** `[x] done`

---

## Step Log

- 2026-08-10 - Step 04.1 done. Gate passes on the clean tree (97 shipping coordinates) and exits 1 when a single sentence of the RU page is altered.
- 2026-08-10 - Step 04.2 done. Both branches proven, not just the firing one: the gate runs when a notice artifact is in the changed set and reports an explicit skip reason when it is not. Verifying only the firing branch would leave an always-on or never-on gate undetected.
- 2026-08-10 - Step 04.3 done. The sentence now names `scripts/docs/generate-oss-notices.ps1`; the document's scope and its CC0 asset entry are untouched, per the strategic non-goal.
- 2026-08-10 - Side effect to declare: the skip-branch probe was run through `post-change.ps1`, which writes a dev-log row, so `dev/CHANGELOG.md` carries one meaningless entry ("S1495 phase 04 skip probe / probe"). Left in place - the changelog is script-owned and hand-editing it to hide the noise would be the worse fault.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - not applicable, no compiled source touched.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `dev/CATALOG` regeneration - not applicable, no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Drift between the build files and the published pages now fails a close. Phase 05 registers the pages so the registry knows they exist and are generated.

---

## Rollback Plan

Revert the `post-change.ps1` step registration and the `THIRD_PARTY_LICENSES.md` sentence, delete the gate - the generator keeps working, only the enforcement disappears.
