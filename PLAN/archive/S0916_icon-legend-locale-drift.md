# Стратегическая спецификация: S0916 - Устаревшие локализованные страницы иконок-легенды

**Ticket:** S0916
**Status:** Archived
**Priority:** 40
**Date:** 2026-07-03
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - обнаружено 2026-07-03 при доработке assert-icon-inventory-sync.ps1
**Tactical spec:** `PLAN/S0916_icon-legend-locale-drift/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-03

**Текст:**

Stale localized icon legend docs: assert-icon-inventory-sync.ps1 (run without -IncludeExportTest, 2026-07-03) reports docs/ICON_LEGEND_RU.md and docs/ICON_LEGEND_UK.md as stale vs. a fresh render (docs/ICON_LEGEND.md / EN is fine). Exact gate output:

icon-inventory-sync: FAIL (2 issue(s))
  [legend-fresh] docs/ICON_LEGEND_RU.md is stale - run: pwsh -NoProfile -File scripts/docs/render-icon-legend.ps1
  [legend-fresh] docs/ICON_LEGEND_UK.md is stale - run: pwsh -NoProfile -File scripts/docs/render-icon-legend.ps1

Discovered incidentally while patching scripts/quality/assert-icon-inventory-sync.ps1 for an unrelated cross-agent build-lock task (only touched the -IncludeExportTest gradle-guard block and the dot-source line - did not touch checks 1-4, the legend-render/diff logic). Likely cause: docs/icons/icon-inventory.json (or a string used by the trilingual legend renderer) changed since the RU/UK legend pages were last regenerated, and EN was regenerated but RU/UK were not (or a locale string changed without re-running the renderer). Fix is mechanical: run scripts/docs/render-icon-legend.ps1 and commit the regenerated docs/ICON_LEGEND_RU.md / docs/ICON_LEGEND_UK.md, but should first confirm WHY only RU/UK drifted (which underlying source changed) so the same mistake isn't repeated at the next icon change.

---

## 1. Проблема

Гейт `assert-icon-inventory-sync.ps1` (без `-IncludeExportTest`) падает: `docs/ICON_LEGEND_RU.md` и `docs/ICON_LEGEND_UK.md` разошлись с живым рендером, при этом английская версия (`docs/ICON_LEGEND.md`) свежая. Затронутая область - документация иконок (`docs/icons/`, `docs/ICON_LEGEND*.md`), рендерер `scripts/docs/render-icon-legend.ps1`.

---

## 2. Цели

1. Определить, какой источник (инвентарь иконок или локализованная строка) изменился и почему обновление затронуло не все локали.
2. Перерендерить и зафиксировать `docs/ICON_LEGEND_RU.md` и `docs/ICON_LEGEND_UK.md` в актуальном виде.
3. Убедиться, что гейт `assert-icon-inventory-sync.ps1` проходит на всех трёх локалях.

**Non-goals:**

- Изменение состава/формата самой иконки-легенды.
- Правки `docs/icons/icon-inventory.json` без явной причины (только если расследование покажет, что дрейф вызван некорректным содержимым инвентаря).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<нет>

### 3.2 Жёсткие ограничения

- **Flavor:** не относится к конкретному flavor - документационный гейт.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** не критично.
- **Совместимость данных:** нет миграций.
- **Локализация:** EN/RU/UK - именно расхождение локалей и есть предмет тикета.
- **Доступность:** не применимо.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none

---

## 4. Контекст текущей архитектуры

`docs/icons/icon-inventory.json` - единый источник данных об иконках. `scripts/docs/render-icon-legend.ps1` рендерит из него три файла (`docs/ICON_LEGEND.md`, `_RU`, `_UK`). Гейт `assert-icon-inventory-sync.ps1` (Check 3, "Legend freshness") перерендеривает во временную папку и побайтово сравнивает с закоммиченными файлами - расхождение означает, что после последнего изменения инвентаря/строк рендерер не был перезапущен для всех локалей.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

- Расследование: сравнить текущий `docs/icons/icon-inventory.json` и локализованные строковые источники легенды с последним коммитом `docs/ICON_LEGEND_RU.md`/`_UK.md`, чтобы понять, что именно изменилось.
- Регенерация: `scripts/docs/render-icon-legend.ps1` перезаписывает все три файла консистентно.

### 5.2 Потоки данных и событий

`icon-inventory.json` (+ локализованные строки) -> `render-icon-legend.ps1` -> `docs/ICON_LEGEND*.md` (EN/RU/UK) -> гейт `assert-icon-inventory-sync.ps1` сверяет с рендером.

### 5.3 Точки расширяемости

<нет>

---

## 6. Открытые вопросы / Research items

- Какое именно изменение (в инвентаре или в локализованной строке) вызвало расхождение только у RU/UK, а не у EN?

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Слепой перезапуск рендерера без выяснения причины скроет реальную проблему (например, забытый перевод новой иконки) | Средняя | Тот же дрейф повторится при следующем изменении инвентаря | Сначала диагностировать причину, затем регенерировать |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта.

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. `pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1` проходит без FAIL по всем трём локалям.
2. Причина расхождения RU/UK задокументирована (что именно изменилось).

---

## Last Audit

**Date:** 2026-07-04
**Mode:** strategic (Simple path)
**Outcome:** Verified
**Counts:** PASS 3 · WARN 0 · FAIL 0

### Причина дрейфа (диагностика)

Два независимых источника:

1. **`ic_camera_capture` (все три локали).** S0882 разбил группу настроек «Other features» на 4 заголовка (`headerCameraPhotos`/VideoCapture/MicRecording/ScreenRecording) и удалил строку `settings_category_other_features`, но `docs/icons/icon-inventory.json` (генерируется из живых app-реестров тестом `IconInventoryExportTest`) не был регенерирован. Инвентарь продолжал указывать `feature=settings_category_other_features` (удалённой строки) -> рендерер `render-icon-legend.ps1` падал в humanized-drawable fallback («Camera Capture»). Гейт `icon-inventory-sync` в post-change S0882 был SKIP (тронуты не icon-ассеты, а layout/строки), а Check 5 (inventory-vs-source) - opt-in только в CI, поэтому дрейф прошёл тихо.

2. **`ic_camera_ocr_translate` (только RU/UK).** Локализованная строка была переформулирована («Фото-перевод OCR» -> «Фото-OCR-перевод»; UK аналогично), EN не менялся; легенда не была перерендерена. Это исходное расхождение, замеченное 2026-07-03.

### Исправление

- Регенерирован `docs/icons/icon-inventory.json` из приложения: `:app_v2:testStandardDebugUnitTest --tests *IconInventoryExportTest -Dicon.inventory.generate=true` (под BUILD.LOCK, BUILD SUCCESSFUL). После этого `ic_camera_capture` резолвится в `headerCameraPhotos`/`settings_group_photography` - реальную экранную строку, без fallback.
- Регенерация вскрыла более широкий накопленный дрейф: инвентарь вырос на 12 vector-строк до текущего состояния приложения; 2 публичные vector-иконки (`ic_cloud_download`, `ic_picture_in_picture`) не имели SVG - экспортированы `scripts/docs/export-icon-svgs.ps1`.
- Перерендерены `docs/ICON_LEGEND.md` / `_RU.md` / `_UK.md` (129 строк).
- **Валидация:** `assert-icon-inventory-sync.ps1` -> PASS (60 vector svg, no orphans, legend fresh, locales in parity; humanized-fallback: none). Свежесть inventory-vs-source гарантирована конструктивно (инвентарь записан generate-mode тестом из приложения).
