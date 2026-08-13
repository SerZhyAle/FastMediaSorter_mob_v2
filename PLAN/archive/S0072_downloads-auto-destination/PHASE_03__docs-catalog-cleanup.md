# Phase 03 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0072_downloads-auto-destination.md`](../S0072_downloads-auto-destination.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** —
**Steps done:** 4 / 4
**Started:** 2026-05-04
**Completed:** 2026-05-04

---

## Objective

Update all three FEATURES files with the new user-facing bullet, regenerate the class catalog, and record dev log entries for every touched file.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.
- [ ] Phase 02 is ✅ Done.
- [ ] Build passes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ 1500 |
| `docs/FEATURES_RU.md` | Modified | ≤ 1500 |
| `docs/FEATURES_UK.md` | Modified | ≤ 1500 |
| `dev/CATALOG/app_v2.jsonl` | Modified (by scan script) | — |
| `dev/CATALOG/app_v2.md` | Modified (by render script) | — |

---

## Steps

### Step 03.1 — Update `docs/FEATURES.md`

**Files:** `docs/FEATURES.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In `docs/FEATURES.md`, find section **4. Destination Management** and add the following bullet at the end of the section's bullet list (before the next `##` heading):
>
> ```markdown
> - **Downloads destination pre-configured on install**: On a fresh install the Downloads folder is automatically added as the first destination, so the destination panel in the player is never empty out of the box.
> ```

**Verification:**

- `Grep` — `Downloads destination pre-configured on install` present in `docs/FEATURES.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 1/1 PASS. Files: docs/FEATURES.md (+1 LOC). Dev log recorded.

---

### Step 03.2 — Update `docs/FEATURES_RU.md`

**Files:** `docs/FEATURES_RU.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `docs/FEATURES_RU.md`, find the section corresponding to **4. Destination Management** (управление получателями) and add the following bullet at the end of its list:
>
> ```markdown
> - **Папка «Загрузки» настроена как получатель по умолчанию**: При первой установке папка загрузок автоматически добавляется как первый получатель — панель получателей в плеере не пуста сразу после инсталляции.
> ```

**Verification:**

- `Grep` — `Загрузки` and `первый получатель` both present in `docs/FEATURES_RU.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: docs/FEATURES_RU.md (+1 LOC). Dev log deferred to Step 03.4.

---

### Step 03.3 — Update `docs/FEATURES_UK.md`

**Files:** `docs/FEATURES_UK.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In `docs/FEATURES_UK.md`, find the section corresponding to **4. Destination Management** and add the following bullet at the end of its list:
>
> ```markdown
> - **Теку «Завантаження» налаштовано як отримувача за замовчуванням**: При першій установці тека завантажень автоматично додається як перший отримувач — панель отримувачів у плеєрі не порожня одразу після інсталяції.
> ```

**Verification:**

- `Grep` — `Завантаження` and `перший отримувач` both present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Verification 2/2 PASS. Files: docs/FEATURES_UK.md (+1 LOC). Dev log deferred to Step 03.4.

---

### Step 03.4 — Regenerate catalog and record dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Steps 03.1–03.3

**Prompt for developer:**

> Run the following commands in order:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then record dev log entries for every file touched across all phases:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/strings.xml" "S0072" "Add resource_name_downloads string (EN)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-ru/strings.xml" "S0072" "Add resource_name_downloads string (RU)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values-uk/strings.xml" "S0072" "Add resource_name_downloads string (UK)"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ProvisionDownloadsDestinationUseCase.kt" "S0072" "New use case: auto-provision Downloads as first destination"
> .\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt" "S0072" "Wire ProvisionDownloadsDestinationUseCase into init block"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0072" "Add Downloads pre-configured destination bullet"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0072" "Add Downloads pre-configured destination bullet (RU)"
> .\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0072" "Add Downloads pre-configured destination bullet (UK)"
> ```

**Verification:**

- `Glob` — `dev/CATALOG/app_v2.md` modified timestamp is today.
- `Grep` — `ProvisionDownloadsDestinationUseCase` present in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-04 — Catalog regenerated (920 records). Dev logs recorded for 8 files. ProvisionDownloadsDestinationUseCase present in app_v2.md PASS.

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] INDEX.md `Phases` counter updated to `3 / 3 done` and all rows show `✅ Done`.
- [ ] Run `/spec-check S0072` to advance strategic spec to `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed in this phase alone.
