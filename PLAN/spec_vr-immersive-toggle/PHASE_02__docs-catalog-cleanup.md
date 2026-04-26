# Phase 02 — Docs & Catalog Cleanup

**Strategic spec:** [`../spec_vr-immersive-toggle.md`](../spec_vr-immersive-toggle.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none — final phase
**Steps done:** 3 / 3
**Started:** 2026-04-25
**Completed:** 2026-04-25

---

## Objective

Update FEATURES trilingual docs to reflect the new button name and 2D support; clean up stale "3DVR" inline comments in touched files; run dev log for all changes.

---

## Prerequisites

- [ ] Phase 01 is `✅ Done`.
- [ ] Working tree is clean or on the same feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ~580 lines (no growth) |
| `docs/FEATURES_RU.md` | Modified | ~580 lines (no growth) |
| `docs/FEATURES_UK.md` | Modified | ~580 lines (no growth) |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt` | Modified | ≤ 1000 lines |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrToggleButtonManager.kt` | Modified | ≤ 55 lines |
| `app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt` | Modified | ≤ 1000 lines |

> No file exceeds 500 lines of change. No backup required (only inline comment edits for Kotlin files; docs changes are additive).

---

## Steps

### Step 2.1 — Update inline comments from "3DVR" to "Immersive" in Kotlin files

**Files:** `CommandPanelController.kt`, `VrToggleButtonManager.kt`, `VrPlayerActivity.kt`
**Depends on:** — start of phase (Phase 01 done)

**Prompt for developer:**

> Update inline comments only — no logic changes. Three locations:
>
> 1. **`CommandPanelController.kt` line ~361** — change comment:
>    ```kotlin
>    // 3DVR toggle: VR flavor only, visible for video files.
>    ```
>    to:
>    ```kotlin
>    // Immersive toggle: VR flavor only, visible for all video files including flat 2D.
>    ```
>
> 2. **`VrToggleButtonManager.kt` class-level KDoc** — change:
>    ```
>    * The button shows either "Watch in 3D VR" (panel mode → launch immersive)
>    * or "Exit 3D VR" (immersive mode → return to panel), switching icon and
>    ```
>    to:
>    ```
>    * The button shows either "Immersive view" (panel mode → launch immersive)
>    * or "Exit immersive" (immersive mode → return to panel), switching icon and
>    ```
>
> 3. **`VrPlayerActivity.kt` line ~230** — change comment:
>    ```kotlin
>    // Wire the 3DVR toggle button (VR flavor only — button is always in the layout but hidden in other flavors).
>    ```
>    to:
>    ```kotlin
>    // Wire the immersive toggle button (VR flavor only — button is always in the layout but hidden in other flavors).
>    ```
>    Also on line ~237:
>    ```kotlin
>    // Initial button state: "Enter 3D VR". Only flip to "Exit" once the VR pipeline
>    ```
>    to:
>    ```kotlin
>    // Initial button state: panel mode. Only flip to immersive once the VR pipeline
>    ```

**Verification:**

- `Grep` — pattern `Watch in 3D VR` in `VrToggleButtonManager.kt` returns zero matches.
- `Grep` — pattern `Exit 3D VR` in `VrToggleButtonManager.kt` returns zero matches.
- `Grep` — pattern `3DVR toggle` in `CommandPanelController.kt` returns zero matches.
- `Grep` — pattern `3DVR toggle button` in `VrPlayerActivity.kt` returns zero matches.
- `Grep` — pattern `Enter 3D VR` in `VrPlayerActivity.kt` returns zero matches.
- `Grep` — `Log\.d\(` in `CommandPanelController.kt` returns zero matches (Timber rule sanity check).

**Status:** `[x] done`

**Step Log:**

- 2026-04-25 — applied comment edits to 3 Kotlin files; Verification 6/6 PASS. Files: CommandPanelController.kt, VrToggleButtonManager.kt, VrPlayerActivity.kt. Dev log entries recorded.

---

### Step 2.2 — Update FEATURES.md + RU + UK

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 2.1

**Prompt for developer:**

> In each features doc, find the **3DVR toggle button** bullet under the **VR Edition** section and replace it with the updated text below. Use `/doc-update` skill if available to apply the change across all three files consistently.
>
> **EN (`docs/FEATURES.md`)** — replace the existing `**3DVR toggle button**` bullet:
> ```
> - **Immersive mode toggle**: A dedicated button on the video player command bar (VR edition only) switches between immersive OpenXR mode and the flat panel player for any video — including ordinary 2D content. Tapping it re-opens the current file in the opposite mode while preserving playback position. In immersive mode the same button returns to the panel player; the left thumbstick click performs the same action.
> ```
>
> **RU (`docs/FEATURES_RU.md`)** — replace the existing `**Кнопка 3DVR**` (or equivalent) bullet:
> ```
> - **Переключатель иммерсивного режима**: отдельная кнопка на командной панели видеоплеера (только VR-версия) переключает между иммерсивным режимом OpenXR и плоским панельным плеером для любого видео, включая обычное 2D. Нажатие повторно открывает текущий файл в противоположном режиме с сохранением позиции воспроизведения. В иммерсивном режиме та же кнопка возвращает в панельный плеер; клик левого стика контроллера выполняет то же действие.
> ```
>
> **UK (`docs/FEATURES_UK.md`)** — replace the existing bullet:
> ```
> - **Перемикач іммерсивного режиму**: окрема кнопка на командній панелі відеоплеєра (тільки VR-версія) перемикає між іммерсивним режимом OpenXR та плоским панельним плеєром для будь-якого відео, включно зі звичайним 2D. Натискання повторно відкриває поточний файл у протилежному режимі зі збереженням позиції відтворення. В іммерсивному режимі та сама кнопка повертає в панельний плеєр; клік лівого стіка контролера виконує ту саму дію.
> ```

**Verification:**

- `Grep` — pattern `3DVR toggle button` in `docs/FEATURES.md` returns zero matches.
- `Grep` — pattern `Immersive mode toggle` in `docs/FEATURES.md` returns one match.
- `Grep` — pattern `Переключатель иммерсивного` in `docs/FEATURES_RU.md` returns one match.
- `Grep` — pattern `Перемикач іммерсивного` in `docs/FEATURES_UK.md` returns one match.

**Status:** `[x] done`

**Step Log:**

- 2026-04-25 — updated EN/RU/UK FEATURES docs; Verification 4/4 PASS. Dev log entries recorded.

---

### Step 2.3 — Run dev log for all modified files

**Files:** all files touched in this phase
**Depends on:** Step 2.2

**Prompt for developer:**

> Run `add_to_dev_log.ps1` for every file modified in this phase:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/player/CommandPanelController.kt" "vr-immersive-toggle" "Update comment: 3DVR toggle -> immersive toggle"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/helpers/VrToggleButtonManager.kt" "vr-immersive-toggle" "Update KDoc: Watch in 3D VR -> Immersive view"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/vr/java/com/sza/fastmediasorter/vr/VrPlayerActivity.kt" "vr-immersive-toggle" "Update comments: 3DVR toggle button -> immersive toggle button"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "vr-immersive-toggle" "Update VR Edition: 3DVR toggle button -> Immersive mode toggle, note 2D support"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "vr-immersive-toggle" "RU: Update VR Edition immersive toggle description"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "vr-immersive-toggle" "UK: Update VR Edition immersive toggle description"
> ```
>
> Also run the dev log for the strategic spec status change:
> ```powershell
> .\scripts\add_to_dev_log.ps1 "PLAN/spec_vr-immersive-toggle.md" "vr-immersive-toggle" "Move strategic status to Tactical; link tactical plan"
> ```

**Verification:**

- `Grep` — pattern `vr-immersive-toggle` in `dev/CHANGELOG.md` returns at least 10 matches (one per file touched across both phases).

**Status:** `[x] done`

**Step Log:**

- 2026-04-25 — ran add_to_dev_log.ps1 for all 6 files; CHANGELOG has 16 vr-immersive-toggle entries (≥10 required); Verification PASS.

---

## Phase Done Criteria

- [x] Every `Step 2.*` above is `[x] done`.
- [x] `Grep` for `3DVR toggle button` or `Watch in 3D VR` or `Exit 3D VR` across `app_v2/src/` and `docs/` returns zero matches.
- [x] `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md` each contain exactly one bullet for the immersive toggle (no duplicate or leftover 3DVR bullet).
- [x] Dev log entries present for all files from Phase 01 and Phase 02.
- [x] Catalog regeneration: public API of no Kotlin file changed (string/comment-only edits) — catalog scan **not** required. Confirmed: `class VrToggleButtonManager` signature unchanged at VrToggleButtonManager.kt:18.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md](INDEX.md) Completion Gate. After this phase, run `/spec-check vr-immersive-toggle` to advance strategic spec status to `Verified`.

---

## Rollback Plan

Revert comment and docs changes. No data migration or behavior change was made in this phase.
