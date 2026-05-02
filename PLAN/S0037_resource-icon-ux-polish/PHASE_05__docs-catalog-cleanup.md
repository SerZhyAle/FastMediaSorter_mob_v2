# S0037 PHASE 05 — Docs & Catalog Cleanup

**Статус:** ✅ Done  
**Completed:** 2026-04-30  
**Цель:** Финальная обязательная фаза — dev log, catalog sync, статус спека.  
**Риск:** Нет.

---

## Step Log

- 2026-04-30 — Step 5.1 PASS: dev log entries written across phases 01–04 in real time. Step 5.2 PASS: `scan.ps1 -Module app_v2` (870 files), `render.ps1 -Module app_v2` regenerated `dev/CATALOG/app_v2.{jsonl,md}`. Step 5.3: FEATURES docs untouched (internal UX polish). Step 5.4 PASS: spec catalog → `Implemented`; strategic file header updated. Step 5.5 PASS: INDEX checkboxes flipped.

---

## Шаги

### Step 5.1 — Dev Log (итоговые записи)

Выполнить после каждой предыдущей фазы. Если не выполнено в фазах 01–04 — выполнить здесь:

```powershell
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/dimens.xml" "resource_icon_badge_size" "S0037 P4: badge size 18dp -> 27dp (1.5x increase)"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/drawable/ico_02_001.xml" "ico_02_001" "S0037 P1: redesign as retro camcorder silhouette (evenOdd lens ring)"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/drawable/ic_resource_smb.xml" "ic_resource_smb" "S0037 P3: redesign as network folder (folder + 3 LAN nodes)"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/ResolveResourceIconUseCase.kt" "ResolveResourceIconUseCase" "S0037 P2: use randomIdFor for SET_OTHER instead of fixed ico-05-001"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/java/com/sza/fastmediasorter/ui/addresource/AddResourceActivity.kt" "AddResourceActivity" "S0037 P2: initial icon for Other resources randomised"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/drawable/bg_icon_button_dark.xml" "bg_icon_button_dark" "S0037 P5: new dark circular background for toolbar pick-icon button"
.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/layout/toolbar_icon_action.xml" "btnPickIcon" "S0037 P5: use dark bg + foreground ripple, remove tint"
```

### Step 5.2 — CATALOG sync

```powershell
# Scan обновляет auto-поля для затронутых .kt файлов
pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2

# Render обновляет human-readable .md
pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

Затронутые .kt файлы для каталога:
- `ResolveResourceIconUseCase.kt` — импорты изменились (new deps: ResourceIconRegistry, ResourceIconSet)
- `AddResourceActivity.kt` — вызов изменился

### Step 5.3 — Проверить FEATURES.md

Изменения в S0037 — внутренние UX-улучшения существующих функций, не новые пользовательские фичи. Обновление `docs/FEATURES.md` / `FEATURES_RU.md` / `FEATURES_UK.md` **не требуется**.

### Step 5.4 — Обновить статус спека на Implemented

```powershell
pwsh -File scripts/spec_catalog/update.ps1 -Id S0037 -Status Implemented
```

Также обновить `PLAN/S0037_resource-icon-ux-polish.md`: `**Статус:** Tactical` → `**Статус:** Implemented`

### Step 5.5 — Обновить INDEX.md (прогресс-чекбоксы)

В `PLAN/S0037_resource-icon-ux-polish/INDEX.md` отметить все фазы как выполненные:
```markdown
- [x] PHASE_01 — badge size increase
- [x] PHASE_02 — drawable assets (camera + SMB)
- [x] PHASE_03 — random Other icon logic
- [x] PHASE_04 — toolbar icon button UX
- [x] PHASE_05 — docs & catalog cleanup
```
