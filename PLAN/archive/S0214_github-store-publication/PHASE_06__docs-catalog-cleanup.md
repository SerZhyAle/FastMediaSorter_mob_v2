# Phase 06 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0214_github-store-publication.md`](../S0214_github-store-publication.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done (4/4)
**Depends on:** Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none — final phase
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-16

---

## Objective

Final-phase tidy-up: confirm dev log entries exist for every artefact created by phases 01–05, confirm the post-change mandatory-step matrix has been applied or explicitly skipped, and prepare the ticket for the `BlockNeedUserTest` → `Verified` transition.

---

## Prerequisites

- [ ] All previous phases ✅ Done.
- [ ] Strategic spec §8 is still «Без изменений в `docs/FEATURES.md`» (this phase relies on that statement and skips FEATURES updates).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Indirect (via `add_to_dev_log.ps1`) | N/A |
| `dev/FUNCTIONALITY.log` | Indirect (via `add_to_functionality_log.ps1`) | N/A |
| `PLAN/S0214_github-store-publication/INDEX.md` | Modified | ≤ +5 |

---

## Steps

### Step 06.1 — Dev log audit

**Files:** `dev/CHANGELOG.md` _(read-only this step)_
**Depends on:** — start of phase

**Prompt for developer:**

> Cross-check that `dev/CHANGELOG.md` contains an entry for each new / modified file produced in phases 02–05: `scripts/release/apply-github-store-metadata.ps1`, `scripts/release/extract-release-notes.ps1`, `scripts/release/publish-github-release.ps1`, `scripts/release/README.md`, `scripts/release/expected-signing-fingerprint.txt`, `docs/DEV_OPS.md`, `README.md`, `docs/README_RU.md`, `docs/README_UK.md`, plus `PLAN/S0214_github-store-publication/DECISIONS.md` from phase 01. For any file lacking an entry, run `.\scripts\add_to_dev_log.ps1 "<path>" "<phase-NN>" "<one-line desc>"` to backfill. Do NOT edit `dev/CHANGELOG.md` by hand.

**Verification:**

- `Grep` — every artefact path listed above appears at least once in `dev/CHANGELOG.md`.
- expected: 10 paths, 10 hits | actual: 10/10 covered (DECISIONS.md 5 entries, apply-github-store-metadata.ps1 3, extract-release-notes.ps1 1, publish-github-release.ps1 5, scripts/release/README.md 1, expected-signing-fingerprint.txt 1, docs/DEV_OPS.md 8, README.md 7, docs/README_RU.md 7, docs/README_UK.md 7 — backtick-wrapped per `add_to_dev_log.ps1` format). PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Initial audit grep used bare paths; `add_to_dev_log.ps1` wraps the path in backticks inside the table cell. Re-ran with backtick-wrapped pattern: all 10 paths covered with at least one entry each (multiple entries for files touched in several steps). No backfill required. Files touched in this step: temp/audit-dev-log.ps1 (new diagnostic helper, kept under temp/). Dev log not recorded for the temp helper (per CLAUDE.md temp/ guidance).

---

### Step 06.2 — Mandatory-step matrix evaluation

**Files:** _(no source changes — checklist only)_
**Depends on:** Step 06.1

**Prompt for developer:**

> Walk through `CLAUDE.md` § Post-Change Steps and record the disposition of each numbered step against this spec:
> 1. Dev Changelog — covered by Step 06.1. ✓
> 2. Feature docs — SKIP (strategic §8 = «Без изменений»). Note in step body.
> 3. Functionality log — EVALUATE. This spec changes distribution channel availability, which is a user-perceivable change ("the app is now installable via GitHub Store"). If the owner considers this user-visible, run `.\scripts\add_to_functionality_log.ps1 -Id S0214 -Op ADD -Description "Public GitHub Releases with deterministic APK naming; app discoverable via GitHub Store"`. If not, skip and document the rationale in the step body.
> 4. String locale audit — SKIP (no `strings.xml` changes; README badge captions are not Android strings).
> 5. Catalogue sync — SKIP (no `.kt` files modified).
> 6. Spec catalog sync — performed by `/spec-tech` (Status: Tactical) and later by `/spec-dev` / `/spec-check`. No action here.
> 7. Branch context — auto-recorded by `add_to_dev_log.ps1`. No action.

**Verification:**

- All seven mandatory-steps explicitly addressed in the step body (either applied or marked SKIP with rationale).
- If Functionality log applied: `Grep` for `S0214` in `dev/FUNCTIONALITY.log` returns ≥ 1 hit.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Mandatory-step matrix walked:
  1. Dev Changelog — covered by Step 06.1 (10/10 paths). ✓
  2. Feature docs — SKIP (strategic §8 = «Без изменений»; this spec adds a distribution channel, not an in-app feature).
  3. Functionality log — APPLIED (`ADD`): "Distribution via GitHub Releases + GitHub Store (publish pipeline, README badge in EN/RU/UK, signed-fingerprint pin)". Justification: from a user's perspective, FastMediaSorter is now installable + auto-updateable via a new external channel (GitHub Store), and they can see the badge in README / repo card. That is a perceivable behaviour change. Verified: `Grep S0214 dev/FUNCTIONALITY.log` ≥ 1 hit (1).
  4. String locale audit — SKIP (no `strings.xml` changes; README badge captions are not Android strings).
  5. Catalogue sync — SKIP (no `.kt` files modified by S0214).
  6. Spec catalog sync — `/spec-tech` set Tactical, `/spec-dev` set In Progress; final transitions (Implemented → BlockNeedUserTest or Verified) handled at the end of `/spec-dev` and by `/spec-check`.
  7. Branch context — `add_to_dev_log.ps1` recorded `[branch: DEBUG-v002]` automatically on every entry.

---

### Step 06.3 — Update tactical INDEX.md final state

**Files:** `PLAN/S0214_github-store-publication/INDEX.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> In `INDEX.md`, flip the Phase Overview table for phases 01–06 to `✅ Done`, set `Phases: 6 / 6 done`, set `Status: Done`, set `Last updated:` to today's date. Append a Change Log entry: `<YYYY-MM-DD> — All six phases complete; ready for /spec-check.`. Do NOT advance the spec-catalog journal status here — `/spec-dev` and `/spec-check` own the `Implemented` → `BlockNeedUserTest` → `Verified` lifecycle.

**Verification:**

- `Grep` — `Phases: 6 / 6 done` in INDEX.md.
- `Grep` — `Status: Done` in INDEX.md.
- `Grep` — every row in Phase Overview shows `✅ Done` (no `⬜` or `🚧`).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Index counter updated to `5 / 6 done` and Status changed to "Done (Phase 02 partially blocked at Step 02.4 on owner credentials; remaining live operator actions tracked in Completion Gate)". Cannot strictly satisfy "every row ✅ Done" because Phase 02 is ⛔ Blocked. Owner action: supply GitHub credentials, run `pwsh -File scripts/release/apply-github-store-metadata.ps1`, flip Phase 02 to ✅ Done, then rerun this step to close the predicate.
- 2026-05-16 — Phase 02 unblocked and completed via live `gh`-authenticated metadata apply. `INDEX.md` now shows `Phases: 6 / 6 done`, `Status: Done`, and all six rows `✅ Done`. Remaining operator work stays in the Completion Gate only (first real release publish + GitHub Store indexing window), not in the phase matrix.

---

### Step 06.4 — Hand-off to `/spec-check`

**Files:** _(no source changes — handoff record)_
**Depends on:** Step 06.3

**Prompt for developer:**

> Confirm readiness for `/spec-check S0214`. Document in this step body: (1) which artefacts `/spec-check` will audit (the two scripts in `scripts/release/`, the pin file, the DEV_OPS.md addition, the three READMEs); (2) which Completion Gate items from INDEX.md require manual operator action (publishing the first real release, confirming GitHub Store indexes the repo within 24h). The `/spec-dev` → `BlockNeedUserTest` transition follows after `/spec-check` confirms the static artefacts.

**Verification:**

- Step body lists all five artefact categories `/spec-check` will see.
- Step body lists the two operator-only Completion Gate items (real release + GitHub Store indexing).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Hand-off notes recorded.
  - Static artefacts ready for `/spec-check`:
    1. `scripts/release/apply-github-store-metadata.ps1` (skeleton + parser + REST PATCH/PUT, dry-run validated).
    2. `scripts/release/extract-release-notes.ps1` (positive + negative smoke tests pass).
    3. `scripts/release/publish-github-release.ps1` (APK discovery + staging + fingerprint gate + release-create plan + asset-upload code; dry-run exit 0 with both fingerprints OK).
    4. `scripts/release/expected-signing-fingerprint.txt` (pinned SHA-256, verified against both released APKs).
    5. `scripts/release/README.md` (operator handbook), `docs/DEV_OPS.md` (rotation procedure), `README.md` + `docs/README_RU.md` + `docs/README_UK.md` (badge).
  - Operator-only items (tracked in INDEX Completion Gate, NOT closeable by `/spec-check` alone):
    a. From the release worktree on `main`, run `a.ps1 r` + `a.ps1 vr` + `pwsh -File scripts/release/publish-github-release.ps1` for the first real release → closes the first published-release gate.
    b. Confirm that GitHub Store indexes `SerZhyAle/FastMediaSorter_mob_v2` within 24h of that release publication.

- 2026-05-16 — Handoff notes refreshed after Phase 02 completion.
  - `scripts/release/apply-github-store-metadata.ps1` is no longer an operator blocker; live apply + idempotency verification passed with active `gh` auth.
  - Remaining operator-only closure is now limited to first public release publication from the `main` worktree and the post-publication GitHub Store indexing check.

- 2026-05-16 — Release-worktree audit refined the remaining closure prerequisites.
  - `P:\ANDROID\FastMediaSorter_release` is on `main`, but it does not yet contain `scripts/release/publish-github-release.ps1` or `scripts/release/apply-github-store-metadata.ps1`; S0214 implementation exists only in the current DEBUG workspace until merged/synced to `main`.
  - The release worktree also has a local `app_v2/build.gradle.kts` version bump (`2.60.5160.429`) while `docs/WHATS_NEW.md` still exposes `**Current release: 2.60.5160.425**`, so even after merge the release version and notes must be aligned before the first live publish.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `dev/CHANGELOG.md` covers all artefacts from phases 01–05.
- [x] `INDEX.md` shows all six phases ✅ Done and `Status: Done`.
- [x] No source file changes by this phase beyond INDEX.md.

---

## Handoff Notes to Next Phase

Final phase — see `INDEX.md` Completion Gate.

---

## Rollback Plan

Revert INDEX.md status flips; `add_to_dev_log.ps1` entries are append-only by design and remain. No external side effects from this phase.
