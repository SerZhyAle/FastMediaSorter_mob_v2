# Phase 05 — Docs, Catalog & Cleanup

**Strategic spec:** [`../S0108_welcome-language-picker.md`](../S0108_welcome-language-picker.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04 (all prior phases)
**Blocks:** —
**Steps done:** 0 / 3
**Started:** —
**Completed:** —

---

## Objective

Update user-facing feature docs (EN/RU/UK), regenerate the class catalog, and record dev log entries for all files modified across all phases. Final phase before `/spec-check S0108`.

---

## Prerequisites

- [ ] All prior phases (01–04) are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 1500 |
| `docs/FEATURES_RU.md` | Modified | ≤ 1500 |
| `docs/FEATURES_UK.md` | Modified | ≤ 1500 |
| `dev/CATALOG/app_v2.jsonl` | Modified (generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (generated) | — |

---

## Steps

### Step 05.1 — Update FEATURES trilingual docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, locate section **19. Settings**. Add the following bullet inside that section (after the existing language-change bullet if one exists, otherwise append to the section):
>
> ```markdown
> - **Welcome language picker**: On the first launch screen the user can switch the app language (English / Russian / Ukrainian) before completing the setup. The choice takes effect immediately for all subsequent Welcome pages and the entire app.
> ```
>
> In `docs/FEATURES_RU.md`, add the equivalent bullet in Russian in the same section:
>
> ```markdown
> - **Выбор языка на экране приветствия**: На первом экране запуска пользователь может выбрать язык приложения (English / Русский / Українська) до завершения настройки. Выбор мгновенно применяется ко всем последующим страницам приветствия и ко всему приложению.
> ```
>
> In `docs/FEATURES_UK.md`, add the equivalent bullet in Ukrainian in the same section:
>
> ```markdown
> - **Вибір мови на екрані привітання**: На першому екрані запуску користувач може обрати мову застосунку (English / Русский / Українська) до завершення налаштування. Вибір одразу застосовується до всіх подальших сторінок привітання та до всього застосунку.
> ```

**Verification:**

- `Grep` — `Welcome language picker` appears in `docs/FEATURES.md`.
- `Grep` — `Выбор языка на экране приветствия` appears in `docs/FEATURES_RU.md`.
- `Grep` — `Вибір мови на екрані привітання` appears in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 05.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run the catalog scan and render for the `app_v2` module:
>
> ```powershell
> "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> "/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Commit the updated `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md` together with the code changes.

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.jsonl` exists and has a modification timestamp newer than Phase 03 changes.
- `Grep` — `WelcomePagerAdapter` appears in `dev/CATALOG/app_v2.md`.

**Status:** `[ ]` not done

---

### Step 05.3 — Dev log for all modified files

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.2

**Prompt for developer:**

> Run the following commands to record the full change set in the dev log:
>
> ```powershell
> $pwsh = "/c/Program Files/PowerShell/7/pwsh.exe"
> & $pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/res/layout/page_welcome_enhanced.xml"          "S0108" "Add language picker strip"
> & $pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/res/layout-land/page_welcome_enhanced.xml"     "S0108" "Add language picker strip (landscape)"
> & $pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml"                        "S0108" "Add welcome_language_picker_hint (EN)"
> & $pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml"                     "S0108" "Add welcome_language_picker_hint (RU)"
> & $pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml"                     "S0108" "Add welcome_language_picker_hint (UK)"
> & $pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt" "S0108" "WelcomePage: showLanguagePicker + onLanguageSelected; EnhancedViewHolder wiring"
> & $pwsh -File scripts/add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt"    "S0108" "Page-0 language picker + onWelcomeLanguageSelected handler"
> & $pwsh -File scripts/add_to_dev_log.ps1 "docs/FEATURES.md"    "S0108" "Add Welcome language picker bullet"
> & $pwsh -File scripts/add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0108" "Add Welcome language picker bullet (RU)"
> & $pwsh -File scripts/add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0108" "Add Welcome language picker bullet (UK)"
> ```

**Verification:**

- `Grep` — `S0108` appears at least 8 times in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every Step above is `[x] done`.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` each contain the new bullet.
- [ ] `dev/CATALOG/app_v2.md` reflects Phase 03 changes.
- [ ] `dev/CHANGELOG.md` has entries for all 10 modified files.
- [ ] Run `/spec-check S0108` to transition strategic spec to `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert feature doc and catalog edits. No code or data changes in this phase.
