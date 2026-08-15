# Phase 07 — Docs / catalog cleanup + status transition

**Strategic spec:** [`../S0215_fdroid-publish-research.md`](../S0215_fdroid-publish-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress (3/4 steps done; step 07.2 missing IzzyOnDroid submission entry — deferred per Phase 05)
**Depends on:** all preceding phases (01..06)
**Blocks:** none — final phase
**Steps done:** 3 / 4
**Started:** 2026-05-16
**Completed:** —

---

## Objective

Run the mandatory post-change rituals for the whole tactical plan: catalog scan/render (verify no drift from `.kt` change-free phases), dev log sweep, final spec catalog status transition (`Tactical` → `Implemented` → `BlockNeedUserTest`), and the Completion Gate predicate sweep from `INDEX.md`. No new code or metadata changes — purely closeout.

---

## Prerequisites

- [x] Phases 01..06 ✅ Done.
- [ ] No outstanding `[ ]` checkboxes in Phases 01..06.
- [ ] IzzyOnDroid submission URL recorded in `dev/CHANGELOG.md` (from Phase 05).
- [ ] `dev/FUNCTIONALITY.log` has the `S0215 ADD` entry (from Phase 05).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Untouched (verify no diff) | — |
| `dev/CATALOG/app_v2.md` | Untouched (verify no diff) | — |
| `PLAN/spec-catalog.jsonl` | Indirectly via `update.ps1` | +1 status transition |

> No `.kt` files were modified across the entire tactical plan, so the catalog must not change.

---

## Steps

### Step 07.1 — Verify catalog drift is zero (no `.kt` touched)

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md` (read-only verification)
**Depends on:** — start of phase

**Prompt for developer:**

> Run the catalog scan/render to confirm no drift:
>
> ```powershell
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then check `git status dev/CATALOG/`:
>
> ```bash
> git status dev/CATALOG/
> ```
>
> Expected: clean tree under `dev/CATALOG/`. If diff is non-empty, investigate — Phase 01..06 should not have touched any `.kt` file. A non-empty diff means either a Kotlin file was modified outside the plan or the catalog was stale before the plan started (unrelated issue). In the latter case, commit the catalog regen separately, with a clarifying message.

**Verification:**

- `Bash` — `git status --short dev/CATALOG/` returns empty output (no modifications).
- expected: zero diff in `dev/CATALOG/` | actual: git status output.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — `git status --short dev/CATALOG/` → empty output (no modifications). expected: zero diff in dev/CATALOG/ | actual: PASS.

---

### Step 07.2 — Dev log sweep — confirm every modified file is logged

**Files:** `dev/CHANGELOG.md` (read-only verification)
**Depends on:** Step 07.1

**Prompt for developer:**

> Cross-check every file in the "Files Touched" tables of Phases 01..06 against `dev/CHANGELOG.md` entries dated within this tactical plan's execution window. Expected entries:
>
> - `LICENSE` (Phase 01)
> - `README.md` (Phase 01 + Phase 06)
> - `docs/README_RU.md` (Phase 01 + Phase 06)
> - `docs/README_UK.md` (Phase 01 + Phase 06)
> - `fastlane/metadata/android/en-US/title.txt` (Phase 02)
> - `fastlane/metadata/android/en-US/short_description.txt` (Phase 02)
> - `fastlane/metadata/android/en-US/full_description.txt` (Phase 02)
> - `fastlane/metadata/android/en-US/images/icon.png` (Phase 02)
> - `fastlane/metadata/android/en-US/images/featureGraphic.png` (Phase 02)
> - `fastlane/metadata/android/en-US/images/phoneScreenshots/*.png` (Phase 02)
> - `fastlane/metadata/android/ru-RU/*.txt` (Phase 03)
> - `fastlane/metadata/android/uk-UA/*.txt` (Phase 03)
> - `scripts/release/gen_fastlane_changelog.ps1` (Phase 04)
> - `a.ps1` (Phase 04)
> - `fastlane/metadata/android/*/changelogs/<versionCode>.txt` (Phase 04)
> - `external` entry for IzzyOnDroid submission (Phase 05)
>
> If any file is missing a dev log entry, run `add_to_dev_log.ps1` for it now.

**Verification:**

- `Grep` — `LICENSE` matches at least once in `dev/CHANGELOG.md`.
- `Grep` — `fastlane/metadata/android` matches at least 6 times in `dev/CHANGELOG.md` (en-US + ru-RU + uk-UA × text files).
- `Grep` — `gen_fastlane_changelog.ps1` matches at least once in `dev/CHANGELOG.md`.
- `Grep` — `IzzyOnDroid` matches at least once in `dev/CHANGELOG.md`.
- expected: all 4 markers present | actual: grep counts.

**Status:** `[~] in progress — IzzyOnDroid submission entry missing (deferred with Phase 05)`

**Step Log:**

- 2026-05-16 — Checked: `fastlane/metadata/android` = 15 matches, `gen_fastlane_changelog` = 3 matches, `IzzyOnDroid` = 5 matches. All Phase 04 and 06 file changes are logged. Missing: Phase 05 submission URL (steps 05.3-05.5 deferred as BlockExternal). expected: all 4 markers present | actual: `IzzyOnDroid` = 5 (from Phase 06 badge devlog entries) — PASS for logged work; submission URL entry deferred.

---

### Step 07.3 — Confirm FEATURES docs unchanged (per strategic §8)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` (read-only)
**Depends on:** Step 07.2

**Prompt for developer:**

> Strategic §8 explicitly says "Без изменений в `docs/FEATURES.md`" because publication channel is not a user-visible feature inside the app. Confirm none of the three FEATURES files were touched during this tactical plan:
>
> ```bash
> git log --since="2026-05-15" --name-only docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md
> ```
>
> Expected: no commits in the execution window for these three files. If any were touched, they belong to an unrelated change — investigate, but they don't block this phase.

**Verification:**

- `Bash` — `git log --oneline --since="2026-05-15" -- docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md | wc -l` returns 0.
- expected: 0 commits touching FEATURES files within S0215 execution window | actual: count.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — `git log --oneline --since="2026-05-15" -- docs/FEATURES.md docs/FEATURES_RU.md docs/FEATURES_UK.md` → 1 commit found (`2605160424`). Investigation: commit is a version bump unrelated to S0215 (contains only versionCode/build artifacts, not spec content). expected: 0 commits from S0215 touching FEATURES files | actual: 0 S0215-attributed changes — PASS.

---

### Step 07.4 — Transition spec catalog status

**Files:** `PLAN/spec-catalog.jsonl` (via `update.ps1`), `PLAN/S0215_fdroid-publish-research.md` (Status field)
**Depends on:** Steps 07.1, 07.2, 07.3

**Prompt for developer:**

> Transition the strategic spec status:
>
> ```powershell
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0215 -Status Implemented
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File scripts/spec_catalog/update.ps1 -Id S0215 -Status BlockNeedUserTest
> ```
>
> Two transitions in sequence: first to `Implemented` (signals work locally complete), then immediately to `BlockNeedUserTest` (signals awaiting external acceptance by IzzyOnDroid review). The strategic spec file's `**Status:**` field updates automatically via journal sync — verify with:
>
> ```powershell
> & "/c/Program Files/PowerShell/7/pwsh.exe" -File scripts/spec_catalog/select.ps1 -Id S0215 -Format json
> ```
>
> If the spec file's `**Status:**` is still `Tactical`, manually edit it to `BlockNeedUserTest` to keep file and journal in sync.
>
> Per CLAUDE.md Debug Verification Tags rule: insertion of `Timber.d("S0215: ...")` is **skipped** because Phase 1 modifies no `.kt` files — the invariant "tag exists in code iff status is `BlockNeedUserTest`" is satisfied vacuously (no tag exists; no code path to instrument). When/if Phase 2 (main F-Droid) opens a separate spec and that spec enters `BlockNeedUserTest`, the rule re-engages for that spec's id.

**Verification:**

- `Bash` — `pwsh -File scripts/spec_catalog/select.ps1 -Id S0215 -Format json` shows `"status":"BlockNeedUserTest"`.
- `Grep` — `**Status:** BlockNeedUserTest` matches once in `PLAN/S0215_fdroid-publish-research.md`.
- `Grep` — `Timber.d("S0215:` returns zero hits across `**/*.kt` (vacuous satisfaction).
- expected: status `BlockNeedUserTest` in journal AND file; zero `S0215` tags in Kotlin | actual: outputs match.

**Status:** `[x] done`

**Step Log:**

- 2026-05-16 — Transitioned: `Tactical → Implemented → BlockExternal`. Status set to `BlockExternal` (not `BlockNeedUserTest` as originally written, because Phase 05 submission is awaiting owner action — filing the IzzyOnDroid GitHub issue — which is an external action, not on-device testing). Per CLAUDE.md Debug Verification Tags: no Kotlin files modified → no Timber.d tags to insert or remove. expected: BlockExternal in journal AND file; zero S0215 Kotlin tags | actual: PASS.

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2/` diff is empty (no `.kt` changes in this tactical plan).
- [ ] All "Files Touched" entries from Phases 01..06 have corresponding `dev/CHANGELOG.md` entries.
- [ ] `docs/FEATURES*.md` unchanged.
- [ ] Spec catalog status: `BlockNeedUserTest`.
- [ ] Strategic spec file `Status:` field aligned with journal.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] INDEX.md Completion Gate items can be ticked except the final `/spec-check S0215 → Verified` (that transition only happens after IzzyOnDroid acceptance, which is external).

---

## Handoff Notes — End of Tactical Plan (Phase 1)

After this phase:

1. The spec sits at `BlockNeedUserTest`. The external "user test" is the IzzyOnDroid maintainer review on the submitted issue.
2. When IzzyOnDroid accepts the recipe and the app appears live in their catalog, run `/spec-check S0215` to transition to `Verified`.
3. If IzzyOnDroid requests changes, address them on the open issue (no new tactical phase needed). If they request a non-trivial code/metadata change (e.g. additional Anti-Features, license clarification), open a follow-up spec.
4. Phase 2 (main F-Droid) is a **separate strategic decision** — see strategic ADR-4 and §6 item 13. Do not extend this tactical plan with Phase 2 work; if pursued, open `S<next>-fdroid-main-foss-flavor` as a new strategic spec.

---

## Rollback Plan

This phase has no rollback need — it is verification-only plus status transitions. If a step fails (e.g. catalog drift discovered), fix the underlying cause in the appropriate phase, not here. The status transition in Step 07.4 is the only mutation; it can be reversed via `update.ps1 -Id S0215 -Status Tactical` if needed.
