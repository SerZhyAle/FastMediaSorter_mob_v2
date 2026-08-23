# Стратегическая спецификация: S1760 - «Добавить ресурс»: переименование и попадание ярлыка в раздел ресурсов

**Ticket:** S1760
**Status:** Archived
**Priority:** 50
**Date:** 2026-08-16
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - эпик S1615 (кластер C-24)

---

## Goal

1. Кнопка «создать ресурс» переименована в «добавить ресурс» (EN/RU/UK).
2. Ярлык добавленного ресурса при добавлении на рабочий стол автоматически помещается в раздел ресурсов (`SECTION_RESOURCES`).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1615 (родительский эпик, запись L-034), S1746.
- **Localization:** переименование кнопки - EN/RU/UK.
- **Validation level:** добавление ресурса со стола -> ярлык в разделе ресурсов.
- **Owner sign-off:** делегировано конвейеру /spec-all эпика S1615 - 2026-08-16.

<!-- auto-approved by /spec-all - 2026-08-18 -->

---

# Phase 01 - Resource Shortcut Addition UI and Section Placement

**Strategic spec:** `PLAN/S1760_launcher-add-resource-rename-and-section.md`
**Status:** ✅ Done

## Objective

Rename resource creation buttons to "Add resource" across EN/RU/UK string resources and update `LauncherDesktopRepositoryImpl` to place resource shortcuts into the Resources section (`SECTION_RESOURCES`).

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/res/values/strings.xml` | Modified | ≤ 3400 |
| `app_v2/src/main/res/values-ru/strings.xml` | Modified | ≤ 3400 |
| `app_v2/src/main/res/values-uk/strings.xml` | Modified | ≤ 3400 |
| `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt` | Modified | ≤ 500 |
| `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImplTest.kt` | Modified | ≤ 500 |

## Steps

### Step 01.1 - Rename launcher resource creation strings to Add resource in EN, RU, and UK

**Files:** `app_v2/src/main/res/values/strings.xml`, `app_v2/src/main/res/values-ru/strings.xml`, `app_v2/src/main/res/values-uk/strings.xml`

**Prompt for developer:**

> Update `launcher_create_resource_menu_row` and `launcher_create_resource_picker_item` in `values/strings.xml`, `values-ru/strings.xml`, and `values-uk/strings.xml` to use "Add resource" / "Add resource.." ("Добавить ресурс" / "Добавить ресурс..", "Додати ресурс" / "Додати ресурс..").

**Why:**

Button label was misleadingly named "Create resource" when it actually adds an existing resource shortcut.

**Verification:**

- `Grep` - `launcher_create_resource_menu_row` in `values/strings.xml` reads `Add resource`.
- `Grep` - `launcher_create_resource_menu_row` in `values-ru/strings.xml` reads `Добавить ресурс`.

**Status:** `[x]` done

---

### Step 01.2 - Target SECTION_RESOURCES in LauncherDesktopRepositoryImpl for resource shortcuts

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImpl.kt`, `app_v2/src/test/java/com/sza/fastmediasorter/data/repository/LauncherDesktopRepositoryImplTest.kt`

**Prompt for developer:**

> Update `findFreeAnchor` in `LauncherDesktopRepositoryImpl` so that resource shortcut candidates (`PREFIX_RESOURCE`) scan inside `SECTION_RESOURCES` first before falling back to full desktop scan. Add unit test in `LauncherDesktopRepositoryImplTest` verifying resource shortcut placement into Resources section.

**Why:**

Resource shortcuts added from the desktop must land in their own Resources section rather than at an arbitrary location.

**Verification:**

- `Grep` - `SECTION_RESOURCES` referenced in `LauncherDesktopRepositoryImpl.kt`.
- Unit test in `LauncherDesktopRepositoryImplTest` passes.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every Step 01.* above is `[x]` done.
- [x] Project compiles cleanly.
