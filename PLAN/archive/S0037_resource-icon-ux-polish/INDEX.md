# S0037 — Полировка иконок и бейджей ресурсов — Тактический план

**Статус:** Implemented (2026-04-30)  
**Стратегический spec:** `PLAN/S0037_resource-icon-ux-polish.md`  
**Создан:** 2026-04-30  
**Тир:** 2 (Easy, ~2–4 ч, низкий риск)

---

## Принятые решения по открытым вопросам

| Вопрос | Принятое решение |
|--------|-----------------|
| П3 — какую иконку для SMB | Вариант A: папка + три соединённых сетевых узла (LAN-топология) |
| П4 — менять composite size? | Нет. composite=48dp, только badge 18dp → 27dp |
| П5 — стиль кнопки | Новый drawable `bg_icon_button_dark.xml` (тёмный полупрозрачный круг) |

---

## Фазы

| # | Файл | Проблема | Риск |
|---|------|----------|------|
| 01 | `PHASE_01__badge-size-increase.md` | П4 — увеличить badge 18dp → 27dp | Минимальный (dimens только) |
| 02 | `PHASE_02__drawable-assets.md` | П1 + П3 — перерисовать 2 drawable | Низкий (UI только) |
| 03 | `PHASE_03__random-other-icon-logic.md` | П2 — случайная иконка для Other | Низкий (логика, без breaking API) |
| 04 | `PHASE_04__toolbar-button-ux.md` | П5 — видимость кнопки иконки в тулбаре | Низкий (layout + drawable) |
| 05 | `PHASE_05__docs-catalog-cleanup.md` | Финальная: dev log, CATALOG | Нет |

---

## Прогресс

- [x] PHASE_01 — badge size increase
- [x] PHASE_02 — drawable assets (camera + SMB)
- [x] PHASE_03 — random Other icon logic
- [x] PHASE_04 — toolbar icon button UX
- [x] PHASE_05 — docs & catalog cleanup

---

## Затронутые файлы (сводка)

```
app_v2/src/main/res/values/dimens.xml                           (П4)
app_v2/src/main/res/drawable/ico_02_001.xml                     (П1)
app_v2/src/main/res/drawable/ic_resource_smb.xml                (П3)
app_v2/src/main/java/.../ui/icon/ResourceIconComposer.kt        (П4 — verify insets)
app_v2/src/main/java/.../domain/usecase/ResolveResourceIconUseCase.kt  (П2)
app_v2/src/main/java/.../ui/addresource/AddResourceActivity.kt  (П2)
app_v2/src/main/res/drawable/bg_icon_button_dark.xml            (П5 — NEW)
app_v2/src/main/res/layout/toolbar_icon_action.xml              (П5)
```
