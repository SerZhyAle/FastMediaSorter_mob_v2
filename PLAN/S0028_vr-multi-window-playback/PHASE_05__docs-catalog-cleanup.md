# Phase 05 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0028_vr-multi-window-playback.md`](../S0028_vr-multi-window-playback.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04 (all)
**Blocks:** none — final phase
**Steps done:** 0 / 4
**Started:** —
**Completed:** —

---

## Objective

Update trilingual feature documentation, regenerate the class catalog, and add dev-log entries for all files modified across all phases. Gate on `/spec-check S0028`.

---

## Prerequisites

- [ ] All phases 01–04 are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto) | — |

---

## Steps

### Step 05.1 — Update `docs/FEATURES.md` (English)

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Open `docs/FEATURES.md`. Locate the VR / Quest section (search for "VR" or "Quest"). Add a new bullet or subsection:
>
> ```
> **VR Multi-Window Mode (Quest 3+):** Open audio or photo slideshow in a separate window
> while Browse stays in the main window. Use "Open in new window" from the resource popup.
> Each window maintains its own playback state independently. Closing a window does not affect
> other open windows.
> ```
>
> Do not duplicate any existing bullet. Confirm the addition does not conflict with S0019 or S0026 bullets.

**Verification:**

- `Grep` — `Open in new window` matches in `docs/FEATURES.md`.
- `Grep` — `Multi-Window` matches in `docs/FEATURES.md`.

**Status:** `[ ]` not done

---

### Step 05.2 — Update `docs/FEATURES_RU.md` and `docs/FEATURES_UK.md`

**Files:** `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Mirror the addition from Step 05.1 into the Russian and Ukrainian feature docs. Use `..` for ellipsis if needed. Always use `ё`/`Ё` in Russian.
>
> Russian bullet:
> ```
> **Многооконный режим VR (Quest 3+):** Открывайте аудио или слайдшоу фото в отдельном окне,
> не закрывая основной Browse. Команда «Открыть в новом окне» в popup-меню ресурса.
> Каждое окно имеет независимое состояние воспроизведения. Закрытие одного окна не влияет на другие.
> ```
>
> Ukrainian bullet:
> ```
> **Багатовіконний режим VR (Quest 3+):** Відкривайте аудіо або слайдшоу фото в окремому вікні,
> не закриваючи основний Browse. Команда «Відкрити в новому вікні» у popup-меню ресурсу.
> Кожне вікно має незалежний стан відтворення. Закриття одного вікна не впливає на інші.
> ```

**Verification:**

- `Grep` — `Многооконный режим` matches in `docs/FEATURES_RU.md`.
- `Grep` — `Багатовіконний режим` matches in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 05.3 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 05.1, 05.2

**Prompt for developer:**

> Run the catalog scan and render scripts:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Confirm the updated classes appear in `dev/CATALOG/app_v2.md`:
> - `ResumeStateRepositoryImpl`
> - `ResumeStateRepository`
> - `SaveResumeStateUseCase`, `GetResumeStateUseCase`, `ClearResumeStateUseCase`
> - `BrowseEventHandler`
> - `ResourceOpsMenuManager`

**Verification:**

- `Grep` — `ResumeStateRepositoryImpl` matches in `dev/CATALOG/app_v2.md`.
- `Grep` — `openPlayerInNewWindow` does **not** need to appear (private impl detail), but `BrowseEventHandler` entry must exist.

**Status:** `[ ]` not done

---

### Step 05.4 — Final dev-log and spec-check

**Files:** *(no new files)*
**Depends on:** Step 05.3

**Prompt for developer:**

> 1. Add dev-log entries for every file modified across all phases that was not already logged within its phase. Use:
>    ```powershell
>    .\scripts\add_to_dev_log.ps1 "<path>" "S0028" "<description>"
>    ```
> 2. Run `/spec-check S0028` to confirm all strategic criteria from §11 are met. `/spec-check` will flip the spec status to `Verified` (or `Partial`/`Broken`) and write findings into the `## Last Audit` block of the strategic spec.
> 3. If `/spec-check` returns `Partial` or `Broken`, address the findings before marking this phase Done.

**Verification:**

- `Grep` — `S0028` matches at least 10 times in `dev/CHANGELOG.md` (one entry per touched file).
- `Grep` — `Verified` in `PLAN/spec-catalog.jsonl` on the S0028 line (after `/spec-check` passes).

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` all have the multi-window bullet.
- [ ] `dev/CATALOG/app_v2.jsonl` and `app_v2.md` committed alongside code changes.
- [ ] `/spec-check S0028` returned `Verified`.
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s). No code changes — docs and catalog are regeneratable.
