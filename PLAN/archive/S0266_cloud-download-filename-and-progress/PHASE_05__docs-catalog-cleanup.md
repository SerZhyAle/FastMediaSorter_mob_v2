# Phase 05 — Docs, Catalog, Functionality Log

**Strategic spec:** [`../S0266_cloud-download-filename-and-progress.md`](../S0266_cloud-download-filename-and-progress.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** —
**Steps done:** 4 / 4
**Started:** —
**Completed:** —

---

## Objective

Close the loop: regenerate catalog, mark flavor-scoped classes correctly, add `noLegal` feature doc entry (not public FEATURES), append functionality log entry.

---

## Prerequisites

- [ ] Phases 01..04 ✅ Done.
- [ ] `./a.ps1 dq` and noLegal variant both compile.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |
| `docs/FEATURES_noLegal.md` | Modified | — |
| `docs/FEATURES_noLegal_RU.md` | Modified | — |
| `docs/FEATURES_noLegal_UK.md` | Modified | — |
| `dev/FUNCTIONALITY.log` | Modified (append) | — |

> Public `docs/FEATURES.md` + `_RU` + `_UK` are NOT touched — this spec is a bug fix for standard cloud copy, not a new public feature. The noLegal silent APK launch is a noLegal-only capability per Strict Rule on noLegal docs.

---

## Steps

### Step 05.1 — Regenerate catalog and set flavor scope

**Files:**
- `dev/CATALOG/app_v2.jsonl`
- `dev/CATALOG/app_v2.md`

**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (single PowerShell process, scan + render). For the new classes `CloudFileHandle` and `CloudProgressAdapter` set `role` + `status` via `set.ps1`. For `BrowseApkInstallHandlerImpl` mark `-NoFlavors "standard,lite,photos,legacy,vr"` since it lives in `src/noLegal/` only.

**Verification:**

- `Bash` — `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` exits 0.
- `Grep` — `CloudFileHandle` matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `CloudProgressAdapter` matches in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `"noFlavors":\\["standard","lite","photos","legacy","vr"\\]` (or equivalent JSON form) matches for `BrowseApkInstallHandlerImpl` row.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 4/4 PASS. Catalog regen: 1133 files / 1375 records. noFlavors + role + status set for 3 entries.

---

### Step 05.2 — Append noLegal FEATURES entry (trilingual)

**Files:**
- `docs/FEATURES_noLegal.md`
- `docs/FEATURES_noLegal_RU.md`
- `docs/FEATURES_noLegal_UK.md`

**Depends on:** — start of phase

**Prompt for developer:**

> Add a single bullet under the most relevant existing section (likely "APK install" or similar). EN: `Launch APK directly from Google Drive / Dropbox / OneDrive — download happens silently in cache, system installer opens immediately.` RU: `Запуск APK прямо из Google Drive / Dropbox / OneDrive — скачивание идёт тихо в кеш, сразу открывается системный установщик.` UK: `Запуск APK прямо з Google Drive / Dropbox / OneDrive — завантаження відбувається в кеш без діалогів, одразу відкривається системний інсталятор.`
>
> If the noLegal FEATURES files do not exist yet (gitignored — may not be present in the repo on a fresh clone), create them as plain markdown with a single H1 and the bullet.

**Verification:**

- `Grep` — `Google Drive` matches in `docs/FEATURES_noLegal.md`.
- `Grep` — `Google Drive` matches in `docs/FEATURES_noLegal_RU.md`.
- `Grep` — `Google Drive` matches in `docs/FEATURES_noLegal_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 3/3 PASS. Extended existing "APK Install from Browse" section in all three locale files.

---

### Step 05.3 — Append functionality log entry

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 05.1, Step 05.2

**Prompt for developer:**

> Run `.\scripts\add_to_functionality_log.ps1 -Id S0266 -Op FIX -Description "Cloud download saves files under their real names (was: bare cloud fileId without extension). Progress dialog shows real filename, counter, and speed during cloud downloads. noLegal: APK launch from cloud works silently."`. This is one entry covering all three user-visible behaviour changes (FIX, not ADD — main path was always meant to work).

**Verification:**

- `Grep` — `S0266` matches in `dev/FUNCTIONALITY.log`.
- `Grep` — `FIX` near the `S0266` line.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 2/2 PASS. Line 155 of `dev/FUNCTIONALITY.log`.

---

### Step 05.4 — Final compile gate + spec status

**Files:** —
**Depends on:** Step 05.1 .. Step 05.3

**Prompt for developer:**

> Run `./a.ps1 dq` one last time to confirm everything still compiles. Confirm `Timber.d("S0266:` tags are present in code (will remain until `BlockNeedUserTest` → `Verified`). Spec status will move to `BlockNeedUserTest` by `/spec-dev` automatically once it sees all phases done.

**Verification:**

- `Bash` — `./a.ps1 dq` exits 0.
- `Grep` — `Timber.d("S0266:` matches at least once across `app_v2/src/**/*.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-20 — Verification 2/2 PASS. BUILD SUCCESSFUL in 1m 20s, version 2.60.5201.238. Timber.d S0266 tag present in `BrowseApkInstallHandlerImpl.kt`.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Catalog reflects new + modified classes.
- [x] noLegal FEATURES files updated trilingual.
- [x] `dev/FUNCTIONALITY.log` carries one S0266 FIX entry.
- [x] `./a.ps1 dq` exits 0.

---

## Handoff Notes to Next Phase

Final phase. See INDEX.md Completion Gate.

---

## Rollback Plan

Catalog regen and doc edits are reversible (regen + restore). Functionality log entry is append-only; if rollback is required, add a corresponding compensating entry.
