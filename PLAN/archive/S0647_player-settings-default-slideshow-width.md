# Стратегическая спецификация: S0647 - Поле "Default slideshow" не должно растягиваться на всю форму

**Ticket:** S0647
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-23
**Roadmap entry:** Ad-hoc - запрос 2026-06-23
**Tactical spec:** compact (этот файл, фазы ниже)

<!-- auto-approved by /spec-all - 2026-06-23 -->

> **Scope:** STRATEGIC + compact tactical.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-23
**Захвачено во время:** owner request `/spec-draft`

**Текст запроса (verbatim):**

Настройки - Плеер - Слайдшоу по-умолчанию. Поле на 4 цифры максимум сейчас растягивается на всю форму. Его нужно сделать ограниченной ширины короткой и поставить в одну строку к "Режим сортировки по умолчанию. Ландшафт и портрет. Бз сборки

**Ключевые требования из запроса:**

- Экран: Settings -> Player.
- Элемент: "Default slideshow" / "Слайдшоу по-умолчанию".
- Текущее поле ввода допускает максимум 4 цифры, но визуально растягивается на всю ширину формы.
- Поле нужно сделать коротким, ограниченной ширины.
- Нужно поставить его в одну строку с "Default sort mode" / "Режим сортировки по умолчанию".
- Изменение требуется и для portrait, и для landscape.
- Сборка не нужна.

**Что ожидается позже при доработке:**

- Проверить текущие portrait/landscape layout'ы экрана Player settings.
- Определить безопасную компактную ширину для 4-digit numeric input.
- Свести "Default slideshow" и "Default sort mode" в один горизонтальный ряд без регрессии выравнивания и доступности.

**Вложения:** нет.

---

## 1. Цель

В группе "Sorting & Slideshow" вкладки Player (Playback) поставить `spinnerSortMode` (Default sort mode) и `etSlideshowInterval` (Slideshow interval) в один горизонтальный ряд; numeric-поле слайдшоу ограничить по ширине, чтобы не растягивалось на всю форму. Portrait + landscape.

Это разворот S0567 ADR-1 (он намеренно сложил эти поля в полноширинную стопку); owner явно просит вернуть горизонтальный компактный вид, что совпадает с правилом против full-width элементов (S0605) и с уже применённым паттерном numeric-cap (параллелизм сети, `settings_input_numeric_max_width`).

---

## 2. Контекст (код)

- Layout: `res/layout/fragment_settings_playback.xml` + `res/layout-land/fragment_settings_playback.xml`, группа `containerSortingSlideshow`.
- `spinnerSortMode` - `SettingsDropdownRow`, `sdr_fieldWidth=settings_dropdown_compact_width` (240dp); landscape также `sdr_inline=true`.
- `etSlideshowInterval` - `SettingsInputRow`, `sir_inputType=2` (number); сейчас `match_parent`, без width-cap.
- `SettingsInputRow` поддерживает `sir_fieldMaxWidth` (-> `inputLayout.maxWidth`).
- Готовый dimen `settings_input_numeric_max_width` (140dp) - cap для numeric settings inputs.
- Id строк не меняются -> wiring в `PlaybackSettingsFragment` остаётся валидной (pure layout change).

---

## 3. Фазы

### Phase 1 - Horizontal compact row (portrait + landscape)

**Files:** `res/layout/fragment_settings_playback.xml`, `res/layout-land/fragment_settings_playback.xml`

**Steps:**

- Обернуть `spinnerSortMode` + `etSlideshowInterval` в горизонтальный `LinearLayout` (`baselineAligned=false`).
- `spinnerSortMode`: `layout_width=0dp`, `layout_weight=1` (заполняет левую часть; поле капается `sdr_fieldWidth`). В landscape сохранить `sdr_inline=true`.
- `etSlideshowInterval`: `layout_width=wrap_content`, `layout_marginStart=margin_small`, добавить `sir_fieldMaxWidth=settings_input_numeric_max_width`.
- Обновить устаревший S0567-комментарий на S0647-обоснование.

**Verification:**

- `Grep` - в обоих layout присутствует горизонтальный контейнер с обоими id и `sir_fieldMaxWidth` у `etSlideshowInterval`.
- `.\a.ps1 fr` (resources/manifest) - PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-06-23 - Both layouts edited: `spinnerSortMode` (weight=1) + `etSlideshowInterval` (`wrap_content`, `sir_fieldMaxWidth=settings_input_numeric_max_width`) wrapped in a horizontal `LinearLayout`; stale S0567 ADR-1 comment replaced. `.\a.ps1 fr` PASS; `post-change.ps1` (Xml) PASS - neuroslop baselines unchanged, settings-doc-sync OK (no manifest change). Visual fit on narrow screens pending owner glance (no device this run).

---

## 4. Не-цели

- Не менять сами настройки (sort mode, slideshow interval) - только визуальную компоновку.
- Не трогать прочие строки группы.

---

## 10. Связанные тикеты

- **S0567** - ADR-1 сделал поля полноширинной стопкой; S0647 разворачивает для этих двух полей по запросу owner.
- **S0605** - правило против full-width элементов (поддерживает изменение).
- **S0644 / S0646** - settings-UI батч (другие паттерны).

---

## Last Audit

**Date:** 2026-06-23
**Verdict:** Verified
**Mode:** code-vs-spec (no device this run; pure layout reflow, mechanically verifiable from XML).

- `res/layout/fragment_settings_playback.xml` - `spinnerSortMode` + `etSlideshowInterval` обёрнуты в горизонтальный `LinearLayout` (`baselineAligned=false`); dropdown `0dp`+`weight=1`+`sdr_fieldWidth=settings_dropdown_compact_width`; numeric `wrap_content`+`sir_fieldMaxWidth=settings_input_numeric_max_width`. Stale S0567 ADR-1 comment заменён на S0647. PASS.
- `res/layout-land/fragment_settings_playback.xml` - идентично + `sdr_inline="true"` на dropdown. PASS.
- §3 verification predicates (Grep двух layout + `.\a.ps1 fr`) - PASS.
- Timber `Sxxxx:` tags - none (чистый layout, статус никогда не был `BlockNeedUserTest`). OK.
- Visual narrow-screen fit - мягкая owner-прерогатива, не блокирующий критерий. Цель спеки (компактный горизонтальный ряд, numeric не растягивается) полностью присутствует в коде обеих ориентаций.
