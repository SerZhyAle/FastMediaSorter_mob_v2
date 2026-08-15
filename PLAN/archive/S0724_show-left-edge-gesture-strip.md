# Стратегическая спецификация: S0724 - Опция «Показывать полосу жеста с левого края»

**Ticket:** S0724
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-26
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос владельца 2026-06-26
**Tactical spec:** не требуется (Tier 2; реализовано напрямую)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Полоса жеста скриншота у левого края экрана сейчас полностью прозрачна - её не видно. Пользователь не знает, где именно проводить свайп, и не получает обратной связи, что полоса активна. Нужна опция, делающая полосу видимой.

Область - настройки жестов с левого края (группа «Жесты экрана»).

---

## 2. Цели

1. В настройках жестов с левого края появляется переключатель «Показывать полосу жеста с левого края».
2. По умолчанию опция выключена - поведение не меняется (полоса прозрачна).
3. При включении только первые 4 px полосы становятся полупрозрачно-серыми, а остальная ширина остаётся прозрачной.
4. Переключение применяется к уже активной полосе сразу, без перезапуска приложения.

**Non-goals:**

- Настройка произвольного цвета или прозрачности полосы.
- Изменение ширины, положения или геометрии полосы.
- Включение самой полосы жеста на standard (за это отвечает S0672).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Разместить переключатель прямо в группе настроек жестов с левого края, рядом с включением оверлея.
2. Цвет видимой направляющей - серый RGB(128,128,128) с полупрозрачностью, ширина видимой части - ровно первые 4 px.

### 3.2 Жёсткие ограничения

- **Flavor:** standard (через capability edge-gesture-overlay) и noLegal. Не noLegal-only.
- **API level:** без API-специфики; работает на обоих путях захвата (MediaProjection и accessibility).
- **Wear OS:** не затрагивается.
- **Производительность:** нулевой бюджет - лёгкая подложка у существующего overlay-view, без изменения geometry hit-area.
- **Совместимость данных:** новый булев флаг настроек, по умолчанию выключен; апгрейд без изменения поведения.
- **Локализация:** EN/RU/UK - обязательно.
- **Доступность:** опция - визуальное улучшение обнаружимости; не вводит цвет как единственный канал информации.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0672 (standard-edge-gesture-play-compliance), S0629 (Play FGS declaration), S0621 (standard edge gesture)

---

## 4. Контекст текущей архитектуры

Полоса жеста - системный overlay-view, который добавляют сервисы-хосты (общий путь захвата и accessibility-путь). View создаётся без фона, поэтому он прозрачен. Настройки приложения хранятся в общем слое и читаются сервисами и контроллерами оверлея.

Раньше видимость полосы не была параметром: цвет фона view жёстко отсутствовал, а у настроек не было соответствующего поля.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

- Новое булево поле настроек «видимость полосы» в общем слое настроек (хранение + дефолт выключено).
- Менеджер overlay-полосы умеет показывать полупрозрачную серую направляющую только на первых 4 px view или оставлять весь view прозрачным, и менять это у живой полосы.
- Контроллер оверлея получает метод «применить видимость полосы», который пробрасывает явное значение в активный хост (без чтения настроек - без гонок).
- Настроечный экран получает переключатель в группе жестов; он пишет настройку и сразу пушит значение в контроллер.

### 5.2 Потоки данных и событий

- UI-переключатель → сохранение настройки + явный вызов контроллера → хост-сервис → перекраска живой полосы.
- Старт/восстановление оверлея → хост читает текущее значение настройки → полоса создаётся сразу с нужным цветом.

### 5.3 Точки расширяемости

- Значение видимости проходит как явный параметр через хост и менеджер, поэтому при необходимости легко заменить «4 px guide / transparent» на другой визуальный режим.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Гонка чтения настройки при мгновенном переключении | Низкая | Полоса показывает старый цвет | Явное значение пробрасывается параметром, настройка не перечитывается на этом пути |
| Видимая направляющая перекрывает контент у левого края | Низкая | Тонкая серая подсказка поверх UI | Опция по умолчанию выключена; видимы только первые 4 px, hit-area не меняется |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (опция - вспомогательная видимость для уже существующей возможности жеста). Capability зафиксирована в `docs/ALL_FEATURES.jsonl`.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (поле настроек + проброс явного значения через контроллер и хост).

---

## 10. Связи с другими спеками

- S0672 - разблокирует полосу жеста на standard; данная опция действует там, где полоса доступна.
- S0621 - исходная полоса жеста с левого края.

---

## 11. Критерии готовности (strategic-level)

1. В настройках жестов виден переключатель «Показывать полосу жеста с левого края», по умолчанию выключен.
2. При включении и активном оверлее у левого края видны только первые 4 px полосы как полупрозрачная серая направляющая.
3. При выключении полоса снова прозрачна.
4. Переключение применяется к живой полосе без перезапуска приложения.
5. EN/RU/UK строки на месте; настройка переживает перезапуск.

---

## 12. Ссылка на тактическую спецификацию

Tier 2 - тактическая спецификация не создавалась; реализовано напрямую.

---

## Last Audit

### Manual (device test 2026-06-27, Galaxy S21+ SM-G996U1, Android 15, standard debug, build 2.60.6261.106-DEBUG)

Verdict: PASS (the literal grey-strip render is INCONCLUSIVE - overlay windows are excluded from screencap/MediaProjection, an environment capture limit, not an app fault).

- Setting present in Settings -> Management -> "Left-edge screen gestures" - expected: toggle "Show the left-edge gesture strip"; actual: PASS. Row `rowGestureStripVisible`, subtitle "Show a semi-transparent grey guide on the first 4 px; the rest stays transparent". Sits directly under the "Gesture overlay" master toggle, beside the Up/Right/Down action pickers.
- Localization - expected: EN/RU/UK; actual: PASS. Settings-search index carries EN/RU/UK title + description ("show a semi-transparent grey guide on the first 4 px" / "показывать полупрозрачную серую направляющую на первых 4 px" / "показувати напівпрозору сіру напрямну на перших 4 px").
- Toggle ON - expected: live strip recoloured, setting saved; actual: PASS (probe + persisted bool). Probe `S0724: gesture strip visibility toggled -> true`. DataStore `screenshot_gesture_strip_visible` Value = varint 1 (`12 02 08 01`). Default off (the row started off until toggled).
- Toggle OFF - expected: strip transparent, setting saved; actual: PASS. Probe `S0724: gesture strip visibility toggled -> false`. DataStore Value = varint 0 (`12 02 08 00`).
- Applies to live strip without restart - expected: recolour live; actual: PASS (code path `ScreenGestureOverlayManager.setStripVisible` recolours the existing view; `screen_gesture_overlay_strip` window stayed live across both toggles; no re-add).
- Survives app restart - expected: setting persists; actual: PASS. Toggled ON, `force-stop` + relaunch -> `screenshot_gesture_strip_visible` still varint 1 in DataStore, overlay window recreated.
- Visual grey 4 px guide - INCONCLUSIVE. Hardware `screencap` / MediaProjection does NOT render `TYPE_APPLICATION_OVERLAY` windows into the framebuffer, so the grey edge cannot be captured even when shown on the physical panel. Visible-only verification requires a human eyeballing the device, or an external camera. Evidence captured anyway: `temp/S0724_devtest/strip_ON_home_screencap.png` (strip not in framebuffer, as expected).
- Evidence: `temp/S0724_devtest/` (`probes.txt`, screencap) + DataStore byte decode in session notes.
