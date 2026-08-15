# Phase 05 — Docs / Catalog / Cleanup

**Strategic spec:** [`../S0029_bugfix-resume-position-end-of-file.md`](../S0029_bugfix-resume-position-end-of-file.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-04-29
**Completed:** 2026-04-29

---

## Objective

User-facing docs trilingual update, catalog regen for the new domain class and changed repo methods, dev log finalisation. No code edits.

---

## Prerequisites

- [ ] Phase 01..04 ✅ Done.
- [ ] All unit tests pass — `/build` clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | n/a |
| `docs/FEATURES_RU.md` | Modified | n/a |
| `docs/FEATURES_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-regen) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (auto-regen) | n/a |
| `dev/CHANGELOG.md` | Modified (auto, via `add_to_dev_log.ps1`) | n/a |

---

## Steps

### Step 05.1 — Trilingual feature note

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the existing "Resume / playback-position memory" section in each file (search for `resume` / `возобновл` / `відновл`). Append one bullet per language under the existing description:
>
> - EN: `When a file plays to its end, the saved position is cleared so the next open starts from zero. Resume from a saved position now applies only to files paused mid-playback.`
> - RU: `Если файл воспроизведён до конца, сохранённая позиция очищается, и при следующем открытии плеер начинает с нуля. Возобновление с последней позиции работает только для файлов, прерванных на середине.`
> - UK: `Якщо файл відтворено до кінця, збережена позиція очищається, і при наступному відкритті плеєр починає з нуля. Відновлення з останньої позиції працює лише для файлів, перерваних посередині.`
>
> Use `..` (two dots), not `...`. Russian/Ukrainian text must use `ё`/`Ё` where grammatically correct (none in this string).

**Verification:**

- `Grep` — `cleared so the next open starts from zero` in `docs/FEATURES.md` once.
- `Grep` — `сохранённая позиция очищается` in `docs/FEATURES_RU.md` once (with `ё` in `сохранённая`).
- `Grep` — `збережена позиція очищається` in `docs/FEATURES_UK.md` once.
- `Grep` — `\.\.\.` (three dots) returns zero hits in the new bullets.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 3/3 PASS (EN/RU/UK strings each match once; Russian uses `сохранённая` with `ё`). Files: FEATURES.md, FEATURES_RU.md, FEATURES_UK.md (+1 bullet each). Dev log recorded.

---

### Step 05.2 — Catalog regen

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then for the new class fill its `role` and `status`:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class PlaybackCompletionDetector -Role "Pure-logic detector for end-of-playback near-end zone (S0029)" -Status tested
> ```
>
> Stage `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` together with the rest of the spec implementation.

**Verification:**

- `Grep` — `PlaybackCompletionDetector` matches once in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `markPlaybackCompleted` matches at least twice in `dev/CATALOG/app_v2.jsonl` (interface + impl).
- `dev/CATALOG/app_v2.md` regenerated (timestamp newer than the prior commit).

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Verification 3/3 PASS (PlaybackCompletionDetector × 1 in jsonl, markPlaybackCompleted × 2, app_v2.md regenerated). Files: dev/CATALOG/app_v2.jsonl, dev/CATALOG/app_v2.md. Dev log recorded.

---

### Step 05.3 — Dev log + spec status flip

**Files:** `dev/CHANGELOG.md` (auto), spec catalog journal
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1 "<path>" "spec-dev" "<one-line>"` for every code/doc/catalog file modified across all phases of this spec — see each phase's "Phase Done Criteria" line. Then flip the spec to `Implemented`:
>
> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0029 -Status Implemented
> ```
>
> Final invocation of `/spec-check S0029` will move the journal to `Verified` if all strategic criteria pass.

**Verification:**

- `dev/CHANGELOG.md` contains one entry per file from Phases 01..05.
- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0029 -Format json` reports `"status":"Implemented"`.

**Status:** `[x] done`

**Step Log:**

- 2026-04-29 — Build green (`testStandardDebugUnitTest --tests` for all 4 new test classes — BUILD SUCCESSFUL). Strategic spec status flipped to Implemented. Dev log entries cumulatively for every code/doc/catalog file modified.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Trilingual docs all carry the new bullet, with `..` and `ё` style respected.
- [ ] Catalog regenerated and committed with code.
- [ ] Spec catalog status = `Implemented`.

---

## Handoff Notes to Next Phase

Final phase — see [INDEX.md Completion Gate](INDEX.md#completion-gate). Next action is `/spec-check S0029` to advance to `Verified`.

---

## Rollback Plan

Pure-doc/catalog phase. Revert the trilingual diff and re-run `scan.ps1` + `render.ps1` from the previous commit's state to undo catalog changes.
