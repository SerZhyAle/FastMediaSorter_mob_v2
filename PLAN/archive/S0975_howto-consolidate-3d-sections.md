# Стратегическая спецификация: S0975 - Консолидация трёх пересекающихся 3D-секций в HOW_TO

**Ticket:** S0975
**Status:** Archived
**Priority:** 25
**Date:** 2026-07-07
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-07 (найдено при верификации S0968)
**Tactical spec:** `PLAN/S0975_howto-consolidate-3d-sections/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-07

**Захвачено во время:** S0968 (howto-sbs-3d-vr-mode-flavor-drift)

**Текст:**

HOW_TO.md has three overlapping 3D sections that now all describe the same corrected two-tier reality (single-eye crop universal / manual per-format picker vr-noLegal-only / full immersion noLegal-only): "Watch SBS 3D videos in VR mode" (~line 151), "OpenXR VR Immersive Cinema" (~line 177, written by S0965), and "How to Watch 3D Videos (VR)" (~line 700). After S0968 fixed the flavor scope in all three, they are accurate and non-contradictory but redundant - the same 3D topic is maintained in three places across EN/RU/UK (9 blocks total). Follow-up: consolidate into a single authoritative 3D section (likely the OpenXR one), redirect or remove the other two, update the TOC anchors and any howto/index cross-links, keep EN/RU/UK parity and the S0558 settings-path gate green. Discovered while verifying S0968 (its research item 6.2 raised the same question). Low priority - pure doc-structure cleanup, no user-facing behavior change. Related: S0965, S0968.

---

## 1. Проблема

HOW_TO.md держал три пересекающихся 3D-секции («Watch SBS 3D videos in VR mode», «OpenXR VR Immersive Cinema», «How to Watch 3D Videos (VR)») в каждом из EN/RU/UK - 9 блоков про одну и ту же (после S0968) двухуровневую реальность. Точно, но избыточно: одна тема сопровождалась в трёх местах, риск будущего рассинхрона. Чисто структурная чистка, поведение приложения не меняется.

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений.>

**Non-goals:**

- <что явно вне объёма>

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** все (доки)
- **API level:** без API-специфики
- **Wear OS:** не затрагивается
- **Производительность:** н/д (доки)
- **Совместимость данных:** н/д
- **Локализация:** EN/RU/UK - всегда обязательно (HOW_TO.md/_RU/_UK)
- **Доступность:** н/д
- **HOW_TO path-gate (S0558):** цепочки «Settings -> ..» со стрелкой U+2192 должны резолвиться в манифесте, иначе гейт `scripts/quality/assert-howto-settings-paths.ps1` упадёт.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0965 (docs-vr-drift-reconcile-quickpath), S0968 (howto-sbs-3d-vr-mode-flavor-drift)
- **Sensitive-scope:** только docs (HOW_TO EN/RU/UK); без изменений app-поведения, flavor, данных, API, docs/FEATURES.

---

## 4. Контекст текущей архитектуры

HOW_TO.md/_RU/_UK - курируемые руками гайды с верхним TOC на якоря секций. Внешние ссылки на 3D-тему идут из `docs/howto/index.md` (сценарий-карточки). S0558-гейт `assert-howto-settings-paths.ps1` требует, чтобы каждая цепочка «Settings > ..» резолвилась в манифесте настроек.

---

## 5. Предлагаемый подход

Схлопнуть три секции в одну авторитетную, без потери уникальных деталей и без битых якорей:

- Авторитетная = «OpenXR VR Immersive Cinema» (её якорь `#openxr-vr-immersive-cinema` - единственный, на который ссылается `docs/howto/index.md`, поэтому заголовок сохраняется байт-в-байт).
- Перед удалением двух других в её шаг 1 вложены уникальные детали: путь-тумблер `Settings > Playback > "Show 3D content from one eye"` (S0558-gated) и перечень четырёх ручных 3D-режимов (Auto-detect, SBS, Over-Under, Mono/Disabled).
- Две избыточные секции удалены во всех трёх локалях; верхний TOC перенумерован; осиротевшие `---` вычищены до одного разделителя между секциями.
- Redirect-заглушки не нужны: на якоря удаляемых секций внешних ссылок нет (инвентаризация подтвердила).

---

## 6. Открытые вопросы / Research items

Оба разрешены исследованием (из кода):

1. **Какую секцию оставить авторитетной + нужны ли redirect-заглушки?** -> «OpenXR VR Immersive Cinema»: это единственный якорь, на который ссылается `docs/howto/index.md`. Заглушки не нужны - на две удаляемые секции внешних ссылок нет.
2. **Есть ли внешние ссылки на якоря удаляемых секций?** -> нет. `docs/howto/index.md` ссылается только на `#openxr-vr-immersive-cinema` (сохранён). Внутри-файловые TOC-ссылки на удаляемые якоря убраны вместе с секциями.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Удаление секции ломает внешний якорь/ссылку (TOC, howto/index, landing) | Средняя | Битая навигация | Инвентаризовать ссылки перед удалением, оставить redirect при необходимости |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES - чисто структурная чистка HOW_TO.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта.

---

## 10. Связи с другими спеками

- S0965 (docs-vr-drift-reconcile-quickpath) - написал секцию «OpenXR VR Immersive Cinema».
- S0968 (howto-sbs-3d-vr-mode-flavor-drift) - выправил флейвор-скоуп во всех трёх секциях; §6.2 поднял вопрос консолидации.

---

## 11. Критерии готовности (strategic-level)

1. Ровно одна 3D-секция на локаль в HOW_TO (EN/RU/UK); уникальные детали двух удалённых сохранены в авторитетной.
2. Якорь `#openxr-vr-immersive-cinema` и внешние ссылки из `docs/howto/index.md` не сломаны; ноль висячих ссылок на удалённые якоря.
3. S0558-гейт зелёный; EN/RU/UK паритет.

---

## Last Audit

**Date:** 2026-07-10
**Outcome:** Verified
**Method:** static (правка HOW_TO EN/RU/UK) + гейт-прогон, verified centrally.

- Три 3D-секции схлопнуты в «OpenXR VR Immersive Cinema» во всех трёх локалях; удалено ~50-52 строки на файл (две секции + TOC-запись каждая).
- Fold: шаг 1 авторитетной секции теперь называет `Settings > Playback > "Show 3D content from one eye"` (default ON) и перечисляет четыре ручных 3D-режима; ранее их не было.
- `assert-howto-settings-paths.ps1` -> exit 0 (49 recipes, локали в паритете). expected PASS | actual PASS.
- Пост-проверка: ровно 1 3D-заголовок на локаль; 0 висячих ссылок на `#watch-sbs-3d-videos-in-vr-mode` / `#how-to-watch-3d-videos-vr` (и RU/UK-эквиваленты); `#openxr-vr-immersive-cinema` не изменён - внешние ссылки `docs/howto/index.md` резолвятся.
- Побочно вычищен предсуществовавший двойной `---` в HOW_TO.md (доксовая гигиена, тривиально).

No action items.
