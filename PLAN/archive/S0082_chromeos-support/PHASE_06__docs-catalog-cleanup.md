# Phase 06 — Docs & Catalog Cleanup

**Strategic spec:** [`../S0082_chromeos-support.md`](../S0082_chromeos-support.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, 02, 03, 04, 05 (all)
**Blocks:** —
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Update the trilingual feature docs to reflect Chrome OS platform support, regenerate the app_v2 catalog, and ensure the dev changelog covers all modified files from Phases 01–05.

---

## Prerequisites

- [ ] All preceding phases ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | +2 lines |
| `docs/FEATURES_RU.md` | Modified | +2 lines |
| `docs/FEATURES_UK.md` | Modified | +2 lines |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-regen) | — |

---

## Steps

### Step 6.1 — Update FEATURES.md platform requirements

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, find the **Platform requirements** sentence at the top (currently: "Android 8.0+ (API 26) for Standard, Lite, and Photos flavors …"). Append to that paragraph:
>
> `Runs on Chrome OS via Google Play (ARC++): folder access uses the system document picker, Cast may be limited by container networking.`
>
> Do not change any other line in the file.

**Verification:**

- `Grep` — `Chrome OS` present in `docs/FEATURES.md`.
- `Grep` — `ARC++` present in `docs/FEATURES.md`.

**Status:** `[ ]` not done

---

### Step 6.2 — Mirror update to FEATURES_RU.md and FEATURES_UK.md

**Files:** `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 6.1

**Prompt for developer:**

> Apply an equivalent addition to the platform requirements paragraph in both mirror files.
>
> Russian (`FEATURES_RU.md`): `Поддерживается Chrome OS через Google Play (ARC++): выбор папок — через системный диалог, Cast может быть ограничен контейнерной сетью.`
>
> Ukrainian (`FEATURES_UK.md`): `Підтримується Chrome OS через Google Play (ARC++): вибір тек — через системний діалог, Cast може бути обмежений мережею контейнера.`
>
> Use `ё`/`Ё` in Russian text where grammatically correct.

**Verification:**

- `Grep` — `Chrome OS` present in `docs/FEATURES_RU.md`.
- `Grep` — `Chrome OS` present in `docs/FEATURES_UK.md`.
- `Grep` — `ARC++` present in `docs/FEATURES_RU.md`.
- `Grep` — `ARC++` present in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 6.3 — Regenerate app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 6.1, 6.2

**Prompt for developer:**

> Run the catalog scan and render scripts:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> For each new file added in Phases 01–05 (`ChromeOsCompat.kt`, `MainChromeOsBannerManager.kt`), set `role` and `status` via `set.ps1` (see `dev/CATALOG/README.md`). Commit the updated `.jsonl` and `.md` together.
>
> Add dev log entries for all files modified across this entire spec that do not yet have one:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0082" "Chrome OS platform support note"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0082" "Chrome OS platform support note (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0082" "Chrome OS platform support note (UK)"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "S0082" "Catalog regenerated after Chrome OS support"
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and is non-empty.
- `Grep` — `ChromeOsCompat` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `MainChromeOsBannerManager` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-06)` returns zero hits.
- [ ] All files in Phases 01–05 have dev log entries.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated and committed.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. Run `/spec-check S0082` to advance status to `Verified`.

---

## Rollback Plan

Revert phase commit(s). FEATURES docs revert to pre-Chrome OS wording. Catalog reverts to pre-Phase 01 state. No code changes.
