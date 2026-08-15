# Phase 07 — Docs + Catalog Cleanup

**Strategic spec:** [`../S0101_unified-permission-onboarding.md`](../S0101_unified-permission-onboarding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all previous phases
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-06
**Completed:** 2026-05-06

---

## Objective

Update feature documentation in all three locales, regenerate the class catalog, and add dev log entries for all files touched across the entire S0101 implementation.

---

## Prerequisites

- [x] Phases 01–06 are all ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (generated) | — |

---

## Steps

### Step 7.1 — Update FEATURES trilingual docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Add a bullet to section 19 (Settings) in each doc describing the new permissions screen:
> - EN: "**Permission Management:** On first launch a full list of requested permissions is shown with descriptions. Re-accessible any time via Settings → Manage Permissions. Context-triggered requests appear when enabling features that require specific permissions."
> - RU: "**Управление разрешениями:** при первом запуске показывается полный список запрашиваемых прав с описанием. Доступен повторно в любой момент через Настройки → Управление разрешениями. Контекстные запросы появляются при включении функций, требующих конкретных прав."
> - UK: "**Керування дозволами:** під час першого запуску відображається повний список запитуваних прав з описом. Доступно повторно в будь-який момент через Налаштування → Керування дозволами. Контекстні запити з'являються при увімкненні функцій, що потребують конкретних прав."

**Verification:**

- `Grep` — `Permission Management` present in `docs/FEATURES.md`.
- `Grep` — `Управление разрешениями` present in `docs/FEATURES_RU.md`.
- `Grep` — `Керування дозволами` present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: docs/FEATURES.md, docs/FEATURES_RU.md, docs/FEATURES_UK.md (Permission Management bullet added to section 19 in all three locales). Dev log recorded.

---

### Step 7.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 7.1

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`.
> For each new class added in Phases 01–06, set `role` and `status` via `dev/CATALOG/scripts/set.ps1` (see `dev/CATALOG/README.md`).

**Verification:**

- `Grep` — `PermissionEntry` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `PermissionRegistryRepositoryImpl` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `PermissionsManagementFragment` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 3/3 PASS. Files: dev/CATALOG/app_v2.jsonl, dev/CATALOG/app_v2.md (941 classes scanned; role+status set for PermissionEntry, PermissionGroup, PermissionRegistryRepository, PermissionRegistryRepositoryImpl, RequestContextualPermissionUseCase, CheckPermissionStatusUseCase, PermissionsManagementFragment, PermissionDenialHandler, PermissionRationaleBottomSheet). Dev log recorded.

---

### Step 7.3 — Add dev log entries

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** — start of phase (can run in parallel with 7.1)

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` for every file created or modified across Phases 01–07 that does not already have a dev log entry. At minimum, one entry per `.kt` and `.xml` file, one entry per `strings.xml` locale change, and one entry for the FEATURES docs.
> Remove the `Timber.d("S0101: ...")` debug tags from all `.kt` files: `WelcomeActivity.kt` (Phase 03 tag) and `PermissionsManagementFragment.kt` (Phase 05 tag). Commit the removal together with this phase.

**Verification:**

- `Grep` — `S0101:` returns zero hits across all `.kt` files in `app_v2/src/` (confirms tag cleanup).
- `dev/CHANGELOG.md` contains `S0101` entries covering the new domain model files, the registry impl, the Welcome upgrade, and the Settings screen.

**Status:** `[x] done`

**Step Log:**
- 2026-05-06 — Verification 2/2 PASS. Removed `Timber.d("S0101: ...")` from WelcomeActivity.kt:finishWelcome() and PermissionsManagementFragment.kt:onViewCreated(). Grep for `S0101:` returns zero hits. dev/CHANGELOG.md contains S0101 entries for all domain model files, registry impl, Welcome upgrade, and Settings screen. Dev log recorded.

---

## Phase Done Criteria

- [x] Every `Step 7.*` above is `[x] done`.
- [x] `Grep` for `S0101:` in all `.kt` files returns zero hits (debug tags removed).
- [x] `Grep` for `TODO(phase-07)` returns zero hits.
- [x] All three FEATURES docs updated.
- [x] Catalog regenerated.
- [x] Run `/spec-check S0101` and confirm `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s). No code changes; docs and catalog are regenerable.
