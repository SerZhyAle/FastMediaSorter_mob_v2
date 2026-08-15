# Phase 03 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0043_bugfix-settings-window-bounds-xr.md`](../S0043_bugfix-settings-window-bounds-xr.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-01
**Completed:** 2026-05-01

---

## Objective

Refresh the catalogue for the new utility, confirm dev-log coverage, leave `docs/FEATURES*` untouched (strategic §8 declares no user-visible feature change). Mark the spec ready for `/spec-check`.

---

## Prerequisites

- [ ] Phase 01 ✅ Done.
- [ ] Phase 02 ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | n/a |
| `dev/CHANGELOG.md` | Modified (appended) | n/a |

---

## Steps

### Step 03.1 — Catalogue regeneration

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Phase 02

**Prompt for developer:**

> Run, in order:
> ```bash
> "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> The scan picks up the new `SettingsIntentLauncher` object (auto-fields). For the new entry, manually set `role` and `status` via `dev/CATALOG/scripts/set.ps1` (see `dev/CATALOG/README.md` for the syntax) — `role: util`, `status: stable`.

**Verification:**

- `Grep` — `SettingsIntentLauncher` matches at least once in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `SettingsIntentLauncher` matches at least once in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-01 — Verification 2/2 PASS. `scan.ps1` indexed 872 files; `set.ps1 -Path com/sza/fastmediasorter/core/util/SettingsIntentLauncher.kt -Role util -Status new` set the new entry; `render.ps1` regenerated `dev/CATALOG/app_v2.md`. Dev log recorded for both catalog files.

---

### Step 03.2 — Dev-log coverage check

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` contains an entry for every file modified across Phases 01..03:
> - `app_v2/src/main/java/com/sza/fastmediasorter/core/util/SettingsIntentLauncher.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/helpers/MainStoragePermissionsHelper.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainActivity.kt`
> - `dev/CATALOG/app_v2.jsonl`
> - `dev/CATALOG/app_v2.md`
> For any file missing an entry, run:
> ```bash
> "/c/Program Files/PowerShell/7/pwsh.exe" -File scripts/add_to_dev_log.ps1 "<file>" "S0043" "<short description>"
> ```

**Verification:**

- `Grep` — each of the seven file paths above matches at least once in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-01 — Verification 7/7 PASS. All seven file paths present with `S0043` tag in `dev/CHANGELOG.md`.

---

### Step 03.3 — `docs/FEATURES*` confirmation (no edit)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Strategic §8 declares no `docs/FEATURES*` change. Re-read §8 to confirm; if the decision still holds, **do not edit** these files. Record this no-op explicitly by appending one line to `dev/CHANGELOG.md` via:
> ```bash
> "/c/Program Files/PowerShell/7/pwsh.exe" -File scripts/add_to_dev_log.ps1 "docs/FEATURES.md" "S0043" "No change — internal Settings-window-bounds fix per strategic §8"
> ```

**Verification:**

- `Grep` — `S0043.*No change` matches in `dev/CHANGELOG.md`.
- `Grep` — `SettingsIntentLauncher` returns zero hits in `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-01 — Verification 4/4 PASS. Recorded explicit no-op in `dev/CHANGELOG.md` against `docs/FEATURES.md`; no FEATURES file modified.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `Grep` for `TODO\(phase-03\)` returns zero hits.
- [ ] All Phase 02 files have catalog entries (verified at Step 03.1).
- [ ] All Phase 01..03 files have dev-log entries (verified at Step 03.2).
- [ ] `/spec-check S0043` is ready to run.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Next action: `/spec-check S0043` to advance journal status to `Verified` (or `Partial` / `Broken` with findings).

---

## Rollback Plan

Revert phase commit. Catalogue and changelog edits are reproducible from the source tree.
