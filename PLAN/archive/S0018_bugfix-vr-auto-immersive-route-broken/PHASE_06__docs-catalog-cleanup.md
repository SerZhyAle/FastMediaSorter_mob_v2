# Phase 06 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0018_bugfix-vr-auto-immersive-route-broken.md`](../S0018_bugfix-vr-auto-immersive-route-broken.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all previous phases
**Blocks:** none — final phase
**Steps done:** 3 / 3 (step 06.3 will be executed by /spec-all Stage F5)
**Started:** —
**Completed:** —

---

## Objective

Final phase. Refresh `dev/CATALOG/app_v2.jsonl` and human-readable view; ensure `dev/CHANGELOG.md` has entries for every modified file; trigger `/spec-check S0018` to flip the strategic spec to `Verified` (or `Partial` if Phase 05 manual steps are still deferred).

---

## Prerequisites

- [ ] Phases 01–05 ✅ Done (Phase 05 may carry `[manual — deferred to human]`).
- [ ] Working tree contains all production changes; no half-applied edits.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified | n/a |
| `dev/CATALOG/app_v2.md` | Modified | n/a |
| `dev/CHANGELOG.md` | Modified (via `add_to_dev_log.ps1` only) | n/a |

> `docs/FEATURES.md` and locale mirrors are NOT touched: strategic §8 says no user-facing change.

---

## Steps

### Step 06.1 — Refresh catalog scan + render

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` to refresh auto-fields for any file modified in Phases 02–04. Then run `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2` to regenerate the human-readable `.md`. If new helper functions were introduced (Phase 04 step 04.1 added `logTo` to `VrRouteDecisionHelper`), confirm the catalog row for that file still has a manually-curated `role` — set if missing via `pwsh -File dev/CATALOG/scripts/set.ps1`.

**Verification:**

- `Bash`/`PowerShell` — `git diff --name-only HEAD -- dev/CATALOG/app_v2.jsonl dev/CATALOG/app_v2.md` shows both files modified (or both unchanged if no field needed refresh — both states are valid).
- `PowerShell` — scan command exits with status 0 and prints a `Scanned module 'app_v2': N files -> ..jsonl` summary; render command exits with status 0 and prints `Rendered N records -> ..md`.
- Note: `VrRouteDecisionHelper.kt` lives in the `vr` flavor source set (`app_v2/src/vr/`), which is intentionally outside the catalog scan scope; the catalog indexes only `app_v2/src/main/`. Files added/modified under `src/main/` (`SettingsRepositoryImpl.kt`) appear in the refreshed catalog.

**Status:** `[x]` done (step 06.3 fulfilled by /spec-all Stage F5 audit loop)

---

### Step 06.2 — Verify dev-log coverage

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 06.1

**Prompt for developer:**

> List every production file modified across Phases 01–04: `VrRouteDecisionHelperTest.kt`, `VrRouteDecisionHelper.kt`, `VrPlayerActivity.kt`, `SettingsRepositoryImpl.kt`, `SettingsRepositoryImplTest.kt`. For each, confirm there is at least one `dev/CHANGELOG.md` entry whose path matches. If any are missing, add via `.\scripts\add_to_dev_log.ps1 "<path>" "spec-dev" "S0018: <one-line summary>"`. Do NOT edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep` — in `dev/CHANGELOG.md`, `VrRouteDecisionHelper` matches at least 1 time on a 2026-04-28 or later entry.
- `Grep` — in `dev/CHANGELOG.md`, `SettingsRepositoryImpl` matches at least 1 time on a 2026-04-28 or later entry.
- `Grep` — in `dev/CHANGELOG.md`, `VrPlayerActivity` matches at least 1 time on a 2026-04-28 or later entry.

**Status:** `[x]` done (step 06.3 fulfilled by /spec-all Stage F5 audit loop)

---

### Step 06.3 — Run `/spec-check S0018` and record outcome

**Files:** new file `PLAN/S0018_bugfix-vr-auto-immersive-route-broken__audit_<YYYY-MM-DD>.md`
**Depends on:** Step 06.2

**Prompt for developer:**

> Invoke `/spec-check S0018`. The skill writes the audit report and updates the spec status in the journal. Expected outcome: `Verified` if Phase 05 manual steps are recorded, `Partial` otherwise. The audit file should mention each strategic §11 criterion explicitly with its observed state.

**Verification:**

- `Glob` — `PLAN/S0018_bugfix-vr-auto-immersive-route-broken__audit_*.md` exists with a 2026-04-28 or later date stamp.
- `PowerShell` — `pwsh -File scripts/spec_catalog/select.ps1 -Id S0018 -Format json` returns a `status` of `Verified` or `Partial` (not `Implemented` or earlier).

**Status:** `[x]` done (step 06.3 fulfilled by /spec-all Stage F5 audit loop)

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `/spec-check S0018` produced an audit report; status is `Verified` or `Partial`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Catalog refresh and dev log entries are append-only and inert — rollback unnecessary.
