# Phase 05 — Docs + Catalog Cleanup

**Strategic spec:** [`../S0115_unified-error-display.md`](../S0115_unified-error-display.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all previous phases
**Blocks:** _(none — final phase)_
**Steps done:** 4 / 4
**Started:** 2026-05-08
**Completed:** 2026-05-08

---

## Objective

Update feature docs (EN/RU/UK), regenerate class catalog, verify S0115 debug tags, and run `/spec-check`.

---

## Prerequisites

- [ ] Phases 01–04 are all ✅ Done.
- [ ] Working tree is clean.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | — |
| `docs/FEATURES_RU.md` | Modified | — |
| `docs/FEATURES_UK.md` | Modified | — |
| `dev/CATALOG/app_v2.jsonl` | Modified (regen) | — |
| `dev/CATALOG/app_v2.md` | Modified (regen) | — |

---

## Steps

### Step 05.1 — Update FEATURES trilingual docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Add the following bullet to section **19. Settings** in each features file. Place it near other display-related settings bullets.
>
> - **EN (`docs/FEATURES.md`):**
>   `- **Unified error display:** Critical errors appear in red with an extended on-screen duration; debug-only warnings appear in amber and are suppressed in release builds. The "Detailed errors" dialog (Settings → Developer) gains Save to file and Share actions for diagnostics export, with a collapsible stack-trace section.`
>
> - **RU (`docs/FEATURES_RU.md`):**
>   `- **Унификация отображения ошибок:** Критические ошибки отображаются красным цветом с увеличенным временем отображения; отладочные предупреждения показываются янтарным цветом и полностью скрыты в релизных сборках. Диалог «Детальные ошибки» (Настройки → Для разработчиков) дополнен действиями «Сохранить в файл» и «Отправить» для экспорта диагностики, а стек трассировки скрыт под раскрываемым блоком.`
>
> - **UK (`docs/FEATURES_UK.md`):**
>   `- **Уніфікація відображення помилок:** Критичні помилки відображаються червоним кольором зі збільшеним часом показу; налагоджувальні попередження показуються янтарним і повністю приховані у релізних збірках. Діалог «Детальні помилки» (Налаштування → Для розробників) доповнено діями «Зберегти у файл» та «Надіслати» для експорту діагностики, а трасування стека приховане під блоком, що розгортається.`

**Verification:**

- `Grep` — `Unified error display` present in `docs/FEATURES.md`.
- `Grep` — `Унификация отображения` present in `docs/FEATURES_RU.md`.
- `Grep` — `Уніфікація відображення` present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 3/3 PASS. FEATURES.md, FEATURES_RU.md, FEATURES_UK.md updated with unified error display bullet in section 19 Settings. Dev log recorded.

---

### Step 05.2 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase (parallel with Step 05.1)

**Prompt for developer:**

> Run catalog scan and render for `app_v2`:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> After scan completes, set role and status for new classes via `set.ps1`:
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class ErrorSeverity -Role "core-enum" -Status active
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class AppErrorNotifier -Role "ui-util" -Status active
> ```

**Verification:**

- `Grep` — `ErrorSeverity` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `AppErrorNotifier` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 2/2 PASS. scan.ps1 + render.ps1 run. set.ps1 applied: ErrorSeverity (core-enum/new), AppErrorNotifier (ui-util/new). Dev log recorded.

---

### Step 05.3 — Verify S0115 debug tags present in logcat path

**Files:** _(grep check only)_
**Depends on:** — start of phase

**Prompt for developer:**

> Confirm all S0115 `Timber.d` tags are present (they must exist until `/spec-check` transitions the spec to Verified, at which point they are removed):
> ```powershell
> Select-String -Path "app_v2/src/main/java/**/*.kt" -Pattern 'Timber\.d\("S0115:' -Recurse
> ```
> Expected files: `AppErrorNotifier.kt`, `ErrorDialog.kt`, `BrowseErrorDisplayManager.kt`, `PlayerEventHandler.kt`, `AddResourceConnectionManager.kt`, `MainActivity.kt`.

**Verification:**

- `Grep` — `Timber.d("S0115:` present in `AppErrorNotifier.kt`.
- `Grep` — `Timber.d("S0115:` present in `ErrorDialog.kt`.
- `Grep` — `Timber.d("S0115:` present in `BrowseErrorDisplayManager.kt`.
- `Grep` — `Timber.d("S0115:` present in `PlayerEventHandler.kt`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification 4/4 PASS. Timber.d("S0115:` confirmed in AppErrorNotifier.kt, ErrorDialog.kt, BrowseErrorDisplayManager.kt, PlayerEventHandler.kt (also present in AddResourceConnectionManager.kt and MainActivity.kt). Dev log recorded.

---

### Step 05.4 — Run spec-check and advance status

**Files:** _(spec catalog update only)_
**Depends on:** Steps 05.1–05.3

**Prompt for developer:**

> Run `/spec-check S0115`. If all criteria pass, the skill will advance the spec to `Verified` and trigger removal of all `Timber.d("S0115:` tags. Follow the skill's output to completion.

**Verification:**

- `pwsh -File scripts/spec_catalog/select.ps1 -Id S0115 -Format json` returns `"status": "Verified"` after spec-check completes.

**Status:** `[x] done`

**Step Log:**

- 2026-05-08 — Verification PASS. spec-check returned Verified (PASS 36/WARN 0/FAIL 0). Status set to Verified via close.ps1.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] FEATURES trilingual updated and accurate.
- [ ] Catalog regenerated, `ErrorSeverity` and `AppErrorNotifier` entries present.
- [ ] `/spec-check S0115` returned `Verified`.
- [ ] Dev log entries added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

No code changes in this phase. Revert docs edits if needed. Catalog is regenerated from source — no rollback needed.
