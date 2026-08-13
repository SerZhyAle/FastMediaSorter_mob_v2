# Phase 04 — Docs / catalog cleanup

**Strategic spec:** [`../S0234_google-account-card-error-ui.md`](../S0234_google-account-card-error-ui.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-17
**Completed:** 2026-05-17

---

## Objective

Synchronise the project's bookkeeping with the code change: regenerate the catalogue, run the locale audit one more time as a safety net, and verify the dev log carries an entry per touched file. No FEATURES.md edit (strategic §8 is bug-adjacent UX improvement, not a new feature; functionality log is owned by `/spec-dev`).

---

## Prerequisites

- [ ] Phase 03 is ✅ Done.
- [ ] Working tree contains the changes from Phases 01–03.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | n/a — regen |
| `dev/CATALOG/app_v2.md` | Modified (auto) | n/a — regen |

> Both files are gitignored per `project_build_gotchas` memory but still must be regenerated for local consistency. No commit / no dev-log entry needed for these two paths.

---

## Steps

### Step 04.1 — Catalogue regeneration

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run, in order:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Both scripts must exit 0.

**Verification:**

- `Bash` — `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` exits 0 (`expected: 0 | actual: 0`).
- `Bash` — `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` exits 0 (`expected: 0 | actual: 0`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — scan: 1129 files / 1374 records OK. render: 1374 records → app_v2.md OK. Both exit 0.

---

### Step 04.2 — Strings locale audit (safety net)

**Files:** none modified
**Depends on:** Step 04.1

**Prompt for developer:**

> Re-run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0234_"`. Confirms no key was accidentally dropped from a locale during later phases. Must exit 0.

**Verification:**

- `Bash` — `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0234_"` exits 0 (`expected: 0 | actual: 0`).

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — 12 keys × 3 locales = OK. Exit 0.

---

### Step 04.3 — Dev log spot-check

**Files:** `dev/CHANGELOG.md` (read-only check; entries themselves were appended via the script in earlier phases)
**Depends on:** Step 04.2

**Prompt for developer:**

> Grep `dev/CHANGELOG.md` for the four file paths touched by the implementation phases:
>
> - `strings_s0200.xml` (any of values/, values-ru/, values-uk/)
> - `GoogleAccountSettingsViewModel.kt`
> - `GoogleAccountSettingsHelper.kt`
>
> Each must appear at least once with a recent (today's) date. If any is missing, append it via `.\scripts\add_to_dev_log.ps1 "<path>" "spec-dev" "<terse description>"` — do NOT edit `dev/CHANGELOG.md` directly.
>
> FEATURES.md trilingual update: SKIP — strategic §8 is bug-adjacent UX improvement, not a new feature.

**Verification:**

- `Grep` — `dev/CHANGELOG.md` matches `GoogleAccountSettingsViewModel.kt` at least once after the dev log entries.
- `Grep` — `dev/CHANGELOG.md` matches `GoogleAccountSettingsHelper.kt` at least once after the dev log entries.
- `Grep` — `dev/CHANGELOG.md` matches `values/strings_s0200.xml` OR `values-ru/strings_s0200.xml` OR `values-uk/strings_s0200.xml` at least once after the dev log entries.

**Status:** `[x]` done

**Step Log:**

- 2026-05-17 — dev/CHANGELOG.md has 2026-05-17 entries for: values/strings_s0200.xml, values-ru/strings_s0200.xml, values-uk/strings_s0200.xml, GoogleAccountSettingsViewModel.kt, GoogleAccountSettingsHelper.kt. FEATURES.md trilingual: SKIPPED (bug-adjacent UX improvement).

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] No `TODO(phase-04)` markers remain.
- [x] `dev/CHANGELOG.md` has an entry for every implementation-phase file.
- [x] FEATURES.md trilingual: SKIPPED (bug-adjacent UX improvement; strategic §8 does not mandate an update).

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. `/spec-check S0234` is invoked next by `/spec-all` to flip the strategic spec to `Verified`.

---

## Rollback Plan

No code-affecting steps in this phase. Catalogue can be re-scanned at any time.
