# Phase 02 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0412_standalone-viewer-fullscreen.md`](../S0412_standalone-viewer-fullscreen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** — (final phase)
**Steps done:** 4 / 4
**Started:** 2026-06-13
**Completed:** 2026-06-13

---

## Objective

Update trilingual FEATURES docs, regenerate class catalog, record dev log entries.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] `.\a.ps1 fk` exit 0 confirmed at end of Phase 01.

---

## Files Touched

| File | New / Modified |
|------|:--------------:|
| `docs/FEATURES.md` | Modified |
| `docs/FEATURES_RU.md` | Modified |
| `docs/FEATURES_UK.md` | Modified |
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) |

---

## Steps

### Step 02.1 — Update `docs/FEATURES.md` (EN)

**Files:** `docs/FEATURES.md`
**Depends on:** start of phase

**Prompt for developer:**

> In section **"2. Media Browsing"**, extend the bullet `**External file viewing**` by appending the following sentence to its body (after the existing last sentence of that bullet):
>
> `In fullscreen mode — tap the Fullscreen button in the command bar to hide the system bars and command panel for immersive viewing; tap the Fullscreen button again (or use the keyboard shortcut) to exit.`

**Verification:**

- `Grep` — `Fullscreen button in the command bar` present in `docs/FEATURES.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-06-13 — Verification 1/1 PASS. docs/FEATURES.md: appended fullscreen sentence to "External file viewing" bullet.

---

### Step 02.2 — Update `docs/FEATURES_RU.md` (RU)

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Locate the corresponding bullet for **«Просмотр внешних файлов»** (or equivalent) in `docs/FEATURES_RU.md`. Append to the end of that bullet:
>
> `В полноэкранном режиме — нажмите кнопку «Полный экран» в панели команд, чтобы скрыть системные бары и панель для погружающего просмотра; нажмите повторно (или воспользуйтесь клавиатурным сочетанием) для выхода.`

**Verification:**

- `Grep` — `кнопку «Полный экран» в панели команд` present in `docs/FEATURES_RU.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-06-13 — Verification 1/1 PASS. docs/FEATURES_RU.md: appended RU fullscreen sentence.

---

### Step 02.3 — Update `docs/FEATURES_UK.md` (UK)

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Locate the corresponding bullet for **«Перегляд зовнішніх файлів»** (or equivalent) in `docs/FEATURES_UK.md`. Append:
>
> `У повноекранному режимі — натисніть кнопку «Повний екран» на панелі команд, щоб сховати системні панелі та панель команд для занурювального перегляду; натисніть ще раз (або скористайтеся комбінацією клавіш) для виходу.`

**Verification:**

- `Grep` — `кнопку «Повний екран» на панелі команд` present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**
- 2026-06-13 — Verification 1/1 PASS. docs/FEATURES_UK.md: appended UK fullscreen sentence.

---

### Step 02.4 — Catalog sync and dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 02.3

**Prompt for developer:**

> Run catalog regeneration and add dev log entries for all changed files:
> ```powershell
> pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2
>
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneFullscreenManager.kt" "S0412" "Add panel-aware fullscreen methods (enterFullscreenWithPanel, exitFullscreenWithPanel, toggleFullscreenWithPanel)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt" "S0412" "Wire btnFullscreenCmd for image/video/pdf/epub/office; move fullscreen manager init to setupViews"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/StandaloneViewManager.kt" "S0412" "Expose setFullscreenCallbacks; connect PDF/EPUB/Office viewer onEnterFullscreenMode/onExitFullscreenMode"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0412" "Document fullscreen button in standalone viewer (EN/RU/UK)"
> ```

**Verification:**

- `Grep` — `StandaloneFullscreenManager` in `dev/CATALOG/app_v2.jsonl` shows updated entry (has `setFullscreenCallbacks` method or updated timestamp).
- `Grep` — `S0412` appears in `dev/CHANGELOG.md` at least 4 times.

**Status:** `[x] done`

**Step Log:**
- 2026-06-13 — Verification PASS. catalog_sync exit 0 (1783 records). S0412 in CHANGELOG.md: 14 occurrences (≥4). close-and-log exit 0.

---

## Phase Done Criteria

- [ ] Every step 02.1–02.4 is `[x] done`.
- [ ] `Grep` for `TODO(phase-02)` returns zero hits.
- [ ] `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0412 -Status Implemented` — status advanced.

---

## Handoff Notes to Next Phase

Final phase. See INDEX.md Completion Gate:
- Run `/spec-check S0412` on device to advance to `Verified`.
- Insert `Timber.d("S0412: ...")` debug tags at changed flow entries before `BlockNeedUserTest` transition (added by `/spec-dev` as part of the final build step).

---

## Rollback Plan

Revert phase commit(s). FEATURES docs are plain text — no side effects.
