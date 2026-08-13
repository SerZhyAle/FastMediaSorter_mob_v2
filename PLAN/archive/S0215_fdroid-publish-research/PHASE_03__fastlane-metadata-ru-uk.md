# Phase 03 — Fastlane metadata (Russian ru-RU + Ukrainian uk-UA)

**Strategic spec:** [`../S0215_fdroid-publish-research.md`](../S0215_fdroid-publish-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 02
**Blocks:** Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Mirror the EN fastlane metadata into `ru-RU` and `uk-UA` locales with culturally appropriate translations passing `COMMUNICATION_POLICY_RU.md` / `_UK.md` §6 tone checklists. Image assets (icon, feature graphic, screenshots) are reused from `en-US/` by reference (no copy) since they are language-neutral.

---

## Prerequisites

- [x] Phase 02 ✅ Done (EN tree exists, lengths verified).
- [x] Strategic §6.6 resolved → BCP47 (`ru-RU`, `uk-UA`).
- [ ] `docs/COMMUNICATION_POLICY_RU.md` and `_UK.md` available for tone reference.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `fastlane/metadata/android/ru-RU/title.txt` | New | 1 line, ≤ 50 chars |
| `fastlane/metadata/android/ru-RU/short_description.txt` | New | 1 line, ≤ 80 chars |
| `fastlane/metadata/android/ru-RU/full_description.txt` | New | ≤ 4000 chars |
| `fastlane/metadata/android/uk-UA/title.txt` | New | 1 line, ≤ 50 chars |
| `fastlane/metadata/android/uk-UA/short_description.txt` | New | 1 line, ≤ 80 chars |
| `fastlane/metadata/android/uk-UA/full_description.txt` | New | ≤ 4000 chars |

> Images (`icon.png`, `featureGraphic.png`, `phoneScreenshots/*.png`) are **not** duplicated — IzzyOnDroid falls back from `ru-RU` / `uk-UA` to `en-US` images automatically. Do not create `ru-RU/images/` or `uk-UA/images/` directories.

---

## Steps

### Step 03.1 — Write `ru-RU/title.txt`

**Files:** `fastlane/metadata/android/ru-RU/title.txt`
**Depends on:** — start of phase

**Prompt for developer:**

> Create the file with exactly one line:
>
> ```
> FastMediaSorter
> ```
>
> Russian markets keep the Latin name (consistent with the Google Play listing). No transliteration. ≤ 50 chars.

**Verification:**

- `Glob` — file exists.
- `Bash` — `wc -c < fastlane/metadata/android/ru-RU/title.txt` returns ≤ 51.
- expected content: `FastMediaSorter\n` | actual: 16 bytes.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS. File created (16 bytes, latin name retained for RU market parity with Google Play). Dev log recorded.

---

### Step 03.2 — Write `ru-RU/short_description.txt` + `full_description.txt`

**Files:** `fastlane/metadata/android/ru-RU/short_description.txt`, `fastlane/metadata/android/ru-RU/full_description.txt`
**Depends on:** Step 03.1

**Prompt for developer:**

> Translate the EN texts from Phase 02 (`short_description.txt`, `full_description.txt`) into Russian following `docs/COMMUNICATION_POLICY_RU.md` §6 tone checklist. Use `..` (two dots) not `...`. Always use `ё`/`Ё` where grammatically correct. Keep length budgets: short ≤ 80 chars, full ≤ 4000 chars.
>
> Suggested short:
>
> ```
> Сортировка, просмотр и воспроизведение медиа: локально, по сети и в облаке.
> ```
>
> (75 chars — within budget.)
>
> Full description: translate the EN body, preserving bullet structure and the dedicated paragraph «Несвободные зависимости» — Cloud SDKs, ML Kit, Play Services, Cast, In-App Review. Keep the FOSS section («Свободные компоненты»). Keep the GitHub URL and Apache 2.0 license line unchanged.

**Verification:**

- `Glob` — both files exist.
- `Bash` — `wc -c < fastlane/metadata/android/ru-RU/short_description.txt` returns ≤ 81 bytes (note: UTF-8 multibyte chars — F-Droid counts characters, not bytes; use `pwsh -Command "(Get-Content path -Raw).Length"` for char count if byte count exceeds 80 but char count is ≤ 80).
- `Bash` — `wc -c < fastlane/metadata/android/ru-RU/full_description.txt` returns a number; UTF-8 char count via `pwsh -Command "(Get-Content path -Raw).Length"` must be ≤ 4000.
- `Grep` — `Несвободные зависимости` matches exactly once in `full_description.txt`.
- `Grep` — `https://github.com/SerZhyAle/FastMediaSorter_mob_v2` matches exactly once in `full_description.txt`.
- expected short char count: ≤ 80 | actual: PowerShell length.
- expected full char count: ≤ 4000 | actual: PowerShell length.
- Strings pass COMMUNICATION_POLICY_RU §6 checklist (manual review). Author-style: `..` / `ё`-`Ё` enforced.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 6/6 PASS. Files: `short_description.txt` (76 chars), `full_description.txt` (2660 chars). Grep markers `Несвободные зависимости`=1, GitHub URL=1. Tone checklist passed: honest disclosure, declarative copy, plain Russian. No `...` (project author style), no missing `ё`. Dev log recorded.

---

### Step 03.3 — Write `uk-UA/title.txt`

**Files:** `fastlane/metadata/android/uk-UA/title.txt`
**Depends on:** — start of phase (independent of step 03.1, but typically authored after RU)

**Prompt for developer:**

> Create the file with exactly one line:
>
> ```
> FastMediaSorter
> ```
>
> ≤ 50 chars. Latin name retained.

**Verification:**

- `Glob` — file exists.
- `Bash` — `wc -c < fastlane/metadata/android/uk-UA/title.txt` returns ≤ 51.
- expected: `FastMediaSorter\n` | actual: 16 bytes.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS. File created (16 bytes, latin name retained). Dev log recorded.

---

### Step 03.4 — Write `uk-UA/short_description.txt` + `full_description.txt`

**Files:** `fastlane/metadata/android/uk-UA/short_description.txt`, `fastlane/metadata/android/uk-UA/full_description.txt`
**Depends on:** Step 03.3

**Prompt for developer:**

> Translate the EN texts from Phase 02 into Ukrainian following `docs/COMMUNICATION_POLICY_UK.md` §6 tone checklist. Use `..` (two dots) not `...` consistently. Keep length budgets.
>
> Suggested short:
>
> ```
> Сортування, перегляд і відтворення медіа: локально, по мережі та у хмарі.
> ```
>
> (73 chars.)
>
> Full description: translate EN body, preserving structure, the dedicated paragraph «Невільні залежності», the FOSS section («Вільні компоненти»), GitHub URL and Apache 2.0 license line.

**Verification:**

- `Glob` — both files exist.
- `Bash` — UTF-8 char count for short via `pwsh -Command "(Get-Content path -Raw).Length"` must be ≤ 80.
- `Bash` — UTF-8 char count for full via `pwsh -Command "(Get-Content path -Raw).Length"` must be ≤ 4000.
- `Grep` — `Невільні залежності` matches exactly once in `full_description.txt`.
- `Grep` — `https://github.com/SerZhyAle/FastMediaSorter_mob_v2` matches exactly once.
- expected short char count: ≤ 80 | actual: PowerShell length.
- expected full char count: ≤ 4000 | actual: PowerShell length.
- Strings pass COMMUNICATION_POLICY_UK §6 checklist (manual review).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 6/6 PASS. Files: `short_description.txt` (74 chars), `full_description.txt` (2655 chars). Grep markers `Невільні залежності`=1, GitHub URL=1. Tone checklist passed: honest disclosure, declarative copy, plain Ukrainian. No `...` (project author style). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Both `ru-RU/` and `uk-UA/` trees contain `title.txt`, `short_description.txt`, `full_description.txt`.
- [ ] All char-count limits verified via PowerShell (`(Get-Content path -Raw).Length`).
- [ ] No `images/` subdirectories created under `ru-RU/` or `uk-UA/`.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [ ] Strings pass `COMMUNICATION_POLICY_RU` and `_UK` §6 checklists (manual review).

---

## Handoff Notes to Next Phase

Phase 04 (changelog pipeline) wires the `WHATS_NEW.md` / `_RU` / `_UK` sectioned releases into per-locale `changelogs/<versionCode>.txt` files. The pipeline must produce three files per release (en-US, ru-RU, uk-UA) matching the locales installed in Phases 02 / 03.

---

## Rollback Plan

Revert phase commit — RU / UK localized txt files disappear. IzzyOnDroid falls back to `en-US` for RU/UK users. No runtime code changed.
