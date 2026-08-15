# Phase 05 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0255_settings-authorization-group.md`](../S0255_settings-authorization-group.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 04
**Blocks:** none — final phase
**Steps done:** 2 / 3
**Started:** 2026-05-19
**Completed:** -

---

## Objective

Finalize the spec: regenerate the catalog for the touched module, verify the dev changelog covers every modified file, and confirm the strategic and tactical statuses are ready for `/spec-check`. No `docs/FEATURES.md` update — strategic §8 = "Без изменений".

---

## Prerequisites

- [ ] Phase 04 is ✅ Done (all behavior in place).
- [ ] Working tree clean or on the feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | n/a |
| `dev/CHANGELOG.md` | Modified (appended via `add_to_dev_log.ps1`) | n/a |

No source changes in this phase.

---

## Steps

### Step 05.1 — Regenerate catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`. This chains `scan.ps1 -Module app_v2` and `render.ps1 -Module app_v2` in a single PowerShell process. The catalog will pick up the modified `GeneralSettingsSectionsHelper` (new constant + new toggle binding), the modified `GeneralSettingsFragment` (new handlers), and the modified `PlaybackSettingsFragment` (removed handlers).

**Verification:**

- `Bash` — exit code 0 from `scripts/catalog_sync.ps1 -Module app_v2`.
- `Grep` — `GeneralSettingsSectionsHelper` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `GeneralSettingsFragment` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `PlaybackSettingsFragment` present in `dev/CATALOG/app_v2.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Verification PASS. `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exit 0; `GeneralSettingsSectionsHelper`, `GeneralSettingsFragment`, and `PlaybackSettingsFragment` remain present in `dev/CATALOG/app_v2.md`.

---

### Step 05.2 — Verify dev changelog parity

**Files:** `dev/CHANGELOG.md` (read-only check)
**Depends on:** Step 05.1

**Prompt for developer:**

> Confirm `dev/CHANGELOG.md` carries one entry per file that was modified across Phases 01–04. Expected entries (target column starts with `S0255 ...`):
>
> - `app_v2/src/main/res/values/strings.xml` (Phase 01)
> - `app_v2/src/main/res/values-ru/strings.xml` (Phase 01)
> - `app_v2/src/main/res/values-uk/strings.xml` (Phase 01)
> - `app_v2/src/main/res/layout/fragment_settings_general.xml` (Phase 02 + Phase 03 + Phase 04)
> - `app_v2/src/main/res/layout-land/fragment_settings_general.xml` (Phase 02 + Phase 03 + Phase 04)
> - `app_v2/src/main/res/layout/fragment_settings_playback.xml` (Phase 04)
> - `app_v2/src/main/res/layout-land/fragment_settings_playback.xml` (Phase 04)
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsSectionsHelper.kt` (Phase 02)
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` (Phase 04)
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PlaybackSettingsFragment.kt` (Phase 04)
>
> If any of these files lack a `S0255` entry in `dev/CHANGELOG.md`, append one now via `.\scripts\add_to_dev_log.ps1 "<path>" "S0255 PhaseNN" "<one-line summary>"`.

**Verification:**

- `Grep` — `S0255` returns ≥ 10 hits in `dev/CHANGELOG.md` (one per modified file at minimum, more if phases were committed separately).
- Each path listed above is referenced at least once in `dev/CHANGELOG.md` with `S0255` in the target column.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 — Verification PASS. `dev/CHANGELOG.md` contains `S0255` entries for every expected modified path from Phases 01..04 plus catalog/spec artifacts added during the static audit.

---

### Step 05.3 — Final build + locale audit + functionality log

**Files:** none modified by this step (logs only)
**Depends on:** Steps 05.1, 05.2

**Prompt for developer:**

> Run the closing validation chain:
>
> 1. `/build` (standard debug variant) — assembleStandardDebug must PASS.
> 2. `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "settings_category_authorization"` — exit 0 (EN/RU/UK parity).
> 3. `.\scripts\add_to_functionality_log.ps1 -Id S0255 -Op CHANGE -Description "Group three authorization-related elements (GSM banner, Google account card, saved authorizations row) into a new collapsible 'Authorization' section in General settings"` — strategic §2 lists a user-visible reorganisation, classifying as `CHANGE` per CLAUDE.md "Post-Change Steps" §3.

**Verification:**

- `/build` standard debug returns PASS.
- `check_strings_localized.ps1` exits 0.
- `Grep` — `S0255` matches at least once in `dev/FUNCTIONALITY.log`.

**Status:** `[x]` done

**Step Log:**

- 2026-05-19 23:33 — Closing chain PASS: `.\a.ps1 bd` returned `BUILD SUCCESSFUL` (standardDebug); `check_strings_localized.ps1 -KeyPrefix "settings_category_authorization"` exit 0 (EN/RU/UK all present); functionality log entry appended via `add_to_functionality_log.ps1 -Id S0255 -Op CHANGE`. Spec transitions to `BlockNeedUserTest` for on-device confirmation (see strategic Manual / on-device checklist).

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `/build` standard debug PASS.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for the catalog regen and for any missing-file backfill.
- [ ] `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` up to date.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. After this phase, the spec is ready for `/spec-check S0255` to verify completion and flip status to `Verified`.

---

## Rollback Plan

Step 05.1 outputs are generated artefacts — `scan.ps1`/`render.ps1` re-create them deterministically. Step 05.2 is read-only verification + optional backfill. Step 05.3 is verification only. No rollback needed for this phase in isolation.
