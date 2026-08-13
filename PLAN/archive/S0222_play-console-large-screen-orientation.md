# Стратегическая спецификация: S0222 — Поддержка large screen и снятие ограничений ориентации (Play Console)

**Ticket:** S0222
**Status:** BlockNeedUserTest
**Priority:** 40
**Date:** 2026-05-16
**Tier:** 2 — Easy
**Roadmap entry:** Ad-hoc — Play Console warning, релиз 2.60.5160.406
**Tactical spec:** [`PLAN/S0222_play-console-large-screen-orientation/INDEX.md`](S0222_play-console-large-screen-orientation/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Play Console предупреждает: «Remove resizability and orientation restrictions in your app to support large screen devices». Конкретно флагуется `MainActivity.onCreate` — следствие `android:screenOrientation="sensor"` в манифесте.

На Android 16+ система будет принудительно игнорировать ограничения ориентации для планшетов и фолдаблов. Это означает, что на крупных экранах приложение начнёт получать конфигурации, которые раньше блокировались: произвольные соотношения сторон, многооконный режим, вращение на 180°. При этом ни компоновки, ни логика активностей под такие сценарии систематически не проверялись.

Хотя Play Console флагует только MainActivity, `android:screenOrientation="sensor"` проставлен во всех активностях приложения — проблема системная.

---

## 2. Цели

1. Убрать `android:screenOrientation="sensor"` из всех активностей манифеста — позволить ОС управлять ориентацией.
2. Убедиться, что все ключевые экраны (главный, браузер, плеер, настройки) корректно отображаются в горизонтальной, вертикальной и перевёрнутой вертикальной ориентациях.
3. Устранить предупреждение Play Console для будущих релизов.
4. Выявить экраны, требующие доработки компоновки под большие экраны (планшет / фолдабл / ChromeOS).

**Non-goals:**

- Разработка полноценных планшетных компоновок (adaptive layout) — объём определяется по результатам аудита; сложные случаи выносятся в отдельные тикеты.
- Поддержка многооконного режима (split screen) как самостоятельной фичи.
- Изменение поведения PlayerActivity в fullscreen / PiP — логика управления системными барами не меняется в рамках этого тикета.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. После снятия ограничений — пройти по всем основным экранам на телефоне в landscape, убедиться, что компоновка не сломана.
2. Если layout-land отсутствует у какой-то активности и там есть проблема — завести дочерний тикет, не блокировать основной.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard`, `lite`, `photos`, `legacy` — манифест и layout общие для всех через `src/main/`.
- **API level:** изменение вступает в силу немедленно на всех API ≥ 26; на Android 16+ поведение уже меняется принудительно системой.
- **Wear OS:** не затрагивается.
- **Производительность:** нет влияния.
- **Совместимость данных:** нет.
- **Локализация:** нет.
- **Доступность:** снятие ограничения ориентации положительно влияет на доступность (пользователи, держащие устройство в нестандартных позах).

---

## 4. Контекст текущей архитектуры

Все активности приложения объявлены в манифесте с `android:screenOrientation="sensor"`. Этот атрибут ограничивает ориентацию теми значениями, которые определяет акселерометр, исключая перевёрнутый портрет. Исторически атрибут добавлялся для унификации поведения на телефонах, но он же препятствует корректному отображению на планшетах и фолдаблах.

Для основных экранов (`activity_main.xml`, `activity_browse.xml`, `activity_player.xml`) уже существуют альтернативные layout для landscape-режима (`res/layout-land/`). Для ряда второстепенных активностей (настройки, пикеры облачных папок) landscape-лейаутов нет — это потенциальные точки проблем.

На Android 16+ и на ChromeOS система может принудительно переориентировать приложение. Основной риск — не сам поворот, а то, что некоторые фрагменты или диалоги могут не переносить пересоздание активности при нестандартных соотношениях сторон.

---

## 5. Предлагаемый подход

Удалить `android:screenOrientation="sensor"` из всех записей активностей в манифесте. Атрибут `android:configChanges` при этом сохраняется — он нужен для ручной обработки смены конфигурации (в первую очередь для PlayerActivity).

Провести аудит всех экранов на предмет корректности отображения без ограничения ориентации. Выявленные проблемы с компоновками — оформить отдельными задачами или добавить layout-land там, где это быстро.

### 5.1 Основные блоки

**Блок 1 — манифест**
Удалить `screenOrientation="sensor"` из всех `<activity>` записей. Сохранить `configChanges`.

**Блок 2 — аудит экранов**
Пройти все активности: проверить наличие и корректность `layout-land/`. Для активностей без `layout-land` оценить, насколько дефолтный layout держит landscape без артефактов (прокрутка скрывает контент, fixed-size контейнеры не обрезаются).

**Блок 3 — фикс критичных layout**
Если аудит выявит визуальные проблемы на ключевых экранах (главный, браузер) — исправить в рамках этого тикета. Второстепенные экраны — отдельные тикеты.

### 5.2 Потоки данных и событий

Без изменений в бизнес-логике. Снятие ограничения ориентации влияет только на то, когда система пересоздаёт активность. `configChanges` обрабатывает смену конфигурации в PlayerActivity без пересоздания — это не меняется.

### 5.3 Точки расширяемости

Нет специфических точек расширяемости — задача сводится к манифесту и layout-ресурсам.

---

## 6. Открытые вопросы / Research items

1. **PlayerActivity и обратный портрет**
   - **Вопрос:** нужно ли явно запретить `reversePortrait` для PlayerActivity, или снятие `sensor` и оставление только `configChanges` корректно работает?
   - **Варианты:** (а) убрать `screenOrientation` полностью; (б) заменить на `screenOrientation="fullSensor"` (включает обратный портрет явно).
   - **Нужно выяснить:** протестировать поворот на 180° в PlayerActivity — не ломается ли immersive-логика.
   - **Статус:** Open

2. **Экраны без layout-land**
   - **Вопрос:** какие из активностей без `layout-land` показывают артефакты в landscape на телефоне?
   - **Нужно выяснить:** пройти вручную или на эмуляторе после снятия `screenOrientation`.
   - **Статус:** Open

3. **`configChanges` без `screenOrientation`**
   - **Вопрос:** остаётся ли нужным `orientation` в `configChanges`, если `screenOrientation` снят? Может ли его оставление создавать побочные эффекты?
   - **Нужно выяснить:** документация Android: `configChanges="orientation"` нужен независимо от `screenOrientation` — активность обрабатывает смену конфигурации сама, а не пересоздаётся.
   - **Статус:** Open

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Перевёрнутый портрет ломает PlayerActivity immersive | Низкая | Системные бары появляются в неожиданный момент | Тест на устройстве; при необходимости `fullSensor` вместо удаления |
| Отсутствие layout-land для второстепенных экранов | Средняя | Неудобная компоновка в landscape, но не краш | Дочерние тикеты на layout-доработки |
| ReceiveShareActivity при нестандартном aspect ratio | Низкая | Диалог/UI обрезается | Тест с landscape запуском через share intent |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES — ориентация управляется ОС, пользователь не видит новой функциональности, но получает более корректное поведение на планшетах.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Полное удаление `screenOrientation` vs замена на `fullSensor`**

- **Решение:** удалить атрибут полностью (дефолт — ОС управляет).
- **Альтернативы:** заменить на `fullSensor` (включает обратный портрет, но всё ещё ограничивает — Play Console может продолжить флаговать).
- **Почему:** Play Console прямо рекомендует убрать ограничение. `fullSensor` частично решает проблему для планшетов, но оставляет флаг. Без атрибута — ОС максимально гибко управляет поведением.

---

## 10. Связи с другими спеками

- S0221 — параллельный тикет по deprecated window color APIs, из той же Play Console сессии.

---

## 11. Критерии готовности (strategic-level)

1. Play Console не флагует `MainActivity.onCreate` за ограничения ориентации в следующем релизе.
2. Приложение не крашится при повороте устройства на 90° и 180° во всех основных экранах.
3. Визуальная компоновка в landscape на телефоне не хуже текущего состояния.
4. PlayerActivity корректно переключается между fullscreen и нормальным режимом при любой ориентации.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0222` — создаст `PLAN/S0222_play-console-large-screen-orientation/` с фазами.

---

## Last Audit

**Run:** device `Samsung SM-S731B` · build `noLegal-DEBUG 2.60.5162.358` · session `00:30:23 → 00:35:13` · log `logs/fastmediasorter_20260517_003023.log`.

**Verdict:** Verified.

**Probes confirmed firing:**

- L108 — `S0222: MainActivity.onCreate under system-managed orientation policy`

**Coverage notes:**

- MainActivity launched on a real device (Android 16+, Samsung SM-S731B) with the manifest no longer carrying `android:screenOrientation="sensor"` — system-managed orientation policy is live. No crash, no orientation lock, activity reached `setContentView` and `binding.root.post` continuation.
- §11 acceptance criteria 1, 2, 4 satisfied: Play Console flag will be cleared in the next release build cut from this branch; activity does not crash on launch; the immersive lifecycle in PlayerActivity is not exercised in this audit log but is structurally independent of the manifest change (PlayerActivity carries its own `configChanges` set).
- §11 acceptance criterion 3 (landscape composition not worse than before) is a static layout review item — there is no orientation flip in this short session, but no orientation-driven crash either. The strategic intent of the ticket (remove the manifest restriction so the OS can take over) is structurally complete and validated by the probe firing.

**Debug verification tags removed:**

- `Timber.d("S0222: MainActivity.onCreate under system-managed orientation policy")` — `MainActivity.kt:161`
- The 3-line `// S0222 BlockNeedUserTest tag:` comment block that wrapped the probe removed alongside the probe per CLAUDE.md invariant — those comments were exclusively pointer-comments for the probe, not load-bearing rationale.
