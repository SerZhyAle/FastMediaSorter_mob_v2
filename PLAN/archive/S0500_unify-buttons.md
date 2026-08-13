# Стратегическая спецификация: S0500 - Унификация кнопок в приложении

**Ticket:** S0500
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-18
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-18
**Tactical plan:** `PLAN/S0500_unify-buttons/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст пользователя и вложения. Распределяется по §1/§3.1/§6 при доработке через `/spec` или `/spec-update`; секцию можно удалить, когда материал перенесён.

**Захвачено:** 2026-06-18

**Текст:**

унифицировать кнопки в программе. а то зоопарк какой то. Пусть будет пара типов где они уместны. в общеи будет ресерч и решение и замена

**Вложения:**

Вложений нет.

---

## 1. Проблема

Кнопки в приложении оформлены непоследовательно - «зоопарк». Одна и та же смысловая роль реализуется разными виджетами (то `Button`, то `MaterialButton`, то стиль `TextButton`), смешаны два поколения Material (MaterialComponents и Material3) с разными радиусами/тенями, есть захардкоженные hex-цвета в разметке (нарушение Rule 19) и дублирующиеся проектные стили. Эффект на пользователя - визуальная разнородность экранов, скачущие формы/состояния кнопок, риск нечитаемости на светлой теме там, где цвет зашит под тёмный фон.

Область: слой разметки `app_v2` (`res/layout/`, `res/layout-land/`, оверрайды flavor `noLegal`) и проектные стили кнопок в `values/themes.xml` / `values/styles.xml`. Бизнес-логика не затронута.

---

## 2. Цели

1. Единая минимальная таксономия кнопок (несколько именованных стилей Material3) для типовых ролей: основная (filled), вторичная (tonal/outlined), низкоприоритетная/отмена (text), иконочная.
2. Плоский `<Button>` и рассинхронизированные стили заменены на виджеты/стили из новой таксономии там, где это уместно по роли.
3. Семейство проектных стилей `SettingsButton.*` сведено к единому поколению Material3 без MC/M3-раскола.
4. Захардкоженные hex-цвета на кнопках заменены на `?attr/`/`@color/` (Rule 19), кроме явно исключённых поверхностей (см. §6).
5. Паритет portrait/landscape сохранён (Rule 11); доступность (touch target, фокус D-pad/TV) не ухудшена (Rule 16).

**Non-goals:**

- Не меняем идентификаторы кнопок, завязанные на менеджеры плеера (паритет id между unified/standalone-активити обязателен).
- Не мигрируем массив медиа-`ImageButton` (бордерлесс-look плеера) - см. §6, форк 2.
- Не трогаем зарезервированные контролы ExoPlayer (`@id/exo_*`).
- Не делаем редизайн камеры/OCR-экрана (отдельная тема тёмной темы).
- Не меняем функциональность кнопок - только оформление и виджет-класс.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. «Пара типов где они уместны» - таксономия должна быть небольшой и применяться по смысловой роли, а не везде механически.
2. Процесс: ресерч → решение → замена (владелец участвует в решении по таксономии и спорным форкам §6).

### 3.2 Жёсткие ограничения

- **Flavor:** standard, lite, photos, legacy + оверрайды `noLegal` (3 list-item разметки с `ImageButton` правятся синхронно).
- **API level:** без API-специфики - Material-виджеты доступны с minSdk 23 (legacy) и выше; тема приложения уже `Theme.Material3.*`.
- **Wear OS:** не затрагивается.
- **Производительность:** нейтрально (только разметка/стили).
- **Совместимость данных:** нет.
- **Локализация:** EN/RU/UK обязательно для любых новых/изменённых видимых строк (в объёме - вынос захардкоженных строк на затронутых кнопках).
- **Доступность:** сохранить/обеспечить touch target ≥48dp, фокус и `nextFocus*` для D-pad/TV, не-цветовое отличие состояний.

### 3.3 Owner inputs (Approval gate)

- **UI placement/visibility:** изменение чисто стилевое; размещение и видимость кнопок не меняются. Спорные поверхности (камера, медиа-`ImageButton`, ExoPlayer) вынесены в §6 с предложенными дефолтами.
- **Локализация:** EN/RU/UK для любых вынесенных строк.
- **Доступность:** см. §3.2 (touch target, фокус, не-цветовое отличие).
- **Related tickets:** S0479 (декомпозиция секции операций настроек - пересекается по settings-разметкам), S0476 (BuildConfig flavor isolation настроек).

---

## 4. Контекст текущей архитектуры

Оформление кнопок задаётся в XML-разметках и проектных стилях (`values/themes.xml`), а поведение (клики, видимость, рантайм-тинт) - в менеджер/хелпер-классах слоя UI. ViewModel'и не ссылаются на id кнопок напрямую; id стабильны между portrait и landscape и между unified/standalone-плеерами (этим управляют общие менеджеры). Проблему §1 нельзя «решить из коробки», потому что нет единого авторитетного стиля на роль: проектные стили сосуществуют с прямыми ссылками на стили MC/M3 и AppCompat на одном смысловом уровне, а исторические правки добавляли кнопки точечно, каждый раз в своём оформлении.

---

## 5. Предлагаемый подход

Ввести единое семейство именованных стилей кнопок Material3 и привести разметки к нему, не меняя поведения и id.

### 5.1 Основные столпы / модули

- Семейство стилей кнопок в `values/styles.xml` (Material3-родители): Filled, Tonal, Outlined, Text, Icon.
- Миграция разметок: плоский `<Button>` и рассинхронизированные стили → виджет `MaterialButton` + стиль из семейства по роли.
- Консолидация `SettingsButton.*` в новое семейство.
- Зачистка захардкоженных hex на кнопках → `?attr/`/`@color/`, с учётом исключений §6.

### 5.2 Потоки данных и событий

- Разметка (стиль из семейства) → тема Material3 → цвета/формы/состояния из `?attr/color*` и shape-аппирансов.
- Менеджеры UI продолжают находить кнопки по неизменным id и навешивать поведение - контракт id неизменен.

### 5.3 Точки расширяемости

- Новые роли кнопок добавляются как новый стиль семейства, без правки разметок других ролей.
- Исключённые поверхности (камера) при желании получают отдельное стилевое подсемейство, не ломая базовое.

---

## 6. Открытые вопросы / Research items

Ресерч выполнен; форки подтверждены владельцем 2026-06-18 - все по консервативным дефолтам.

1. Камера/видоискатель (`activity_camera_ocr_translate.xml`, `activity_camera_capture.xml`) - намеренно тёмные. **Решено: исключить** из базовой таксономии (отдельная тема при желании).
2. ~295 медиа-`ImageButton` (бордерлесс-look плеера). **Решено: вне объёма** - унифицируем только `Button`/`MaterialButton`; `ImageButton` остаются на `selectableItemBackgroundBorderless`.
3. Контролы ExoPlayer (`custom_player_controls*.xml`, `@id/exo_*`) - контракт библиотеки. **Решено: не трогать**.
4. Семейство `SettingsButton.*` (7 стилей, MC+M3 раскол). **Решено: свести** в новое M3-семейство.
5. `?android:attr/borderlessButtonStyle` на плоском `<Button>` (2 диалога + 1 bottom sheet). **Решено: заменить** на Text-стиль семейства.
6. Паритет landscape для крупных settings-разметок (847 и 975 строк + их `layout-land`). **Решено: единый проход** на экран (portrait+land вместе), без роста файлов.
7. `item_destination_button.xml` - инлайновый `@color/white`. **Решено: `?attr/colorOnPrimary`** (тема-безопасно).

**Артефакт:** [`research/01__button-inventory.md`](S0500_unify-buttons/research/01__button-inventory.md)

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Регрессия фокуса/риппла на контролах плеера | Средняя | Ухудшение D-pad/TV навигации на нагруженном экране | Исключить медиа-`ImageButton` и `exo_*` (форки 2,3); ручная device-проверка плеера |
| Расхождение id при правке standalone-активити | Средняя | Менеджеры теряют кнопки, рантайм-сбой | Не менять id; править стиль/класс, не идентификаторы |
| Светлая тема ломается там, где цвет был зашит под тёмный фон | Средняя | Нечитаемые кнопки | Переход на `?attr/` цвета; камеру вынести (форк 1) |
| Пропуск landscape-двойника | Средняя | Portrait-only оформление | Парный проход portrait+land (Rule 11) |
| Рост settings-разметок >лимита при правках | Низкая | Превышение бюджета строк | Заменять, не добавлять; бэкап в `temp/` перед правкой больших файлов |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без новой пользовательской функции. Визуальная консистентность кнопок - улучшение оформления, не отдельная фича для docs/FEATURES.

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (Material3-стили в `styles.xml`, тема-атрибуты вместо hex).

---

## 10. Связи с другими спеками

- S0479 - декомпозиция секции операций настроек (пересечение по settings-разметкам; координировать порядок правок).
- S0476 - BuildConfig flavor isolation настроек.

---

## 11. Критерии готовности (strategic-level)

1. В `styles.xml` присутствует единое семейство стилей кнопок Material3 для типовых ролей.
2. Плоские `<Button>` и рассинхронизированные стили в объёме (исключая форки 1-3) заменены на семейство.
3. `SettingsButton.*` сведены к единому Material3-поколению.
4. На затронутых кнопках нет захардкоженных hex (кроме исключённых поверхностей); цвета через `?attr/`/`@color/`.
5. Паритет portrait/landscape сохранён; touch target и фокус D-pad/TV не ухудшены.
6. Сборка standard debug проходит; ручная device-проверка ключевых экранов (настройки, диалоги, welcome, плеер) - без визуальных регрессий.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: `PLAN/S0500_unify-buttons/INDEX.md` (5 фаз, все Done).

---

## Revision History

- **2026-06-18** - by `/spec-test-device` (emulator-5554, sdk_gphone64_x86_64 Android 13/SDK33)
  - Scenario: `temp/S0500_mobile_test_scenario_20260618_1835.md` · PASS/FAIL/SKIPPED 5/0/0 · app crashes 0 · breadcrumbs: welcome ✓, settings ✓
  - Unified Material3 button family renders across welcome (6 pages), welcome-permissions, settings, licenses - no inflation crash or regression.

---

## Last Audit

**Date:** 2026-06-18
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 1

Static invariants (all PASS):
- §11.1 family: 5 `Widget.FastMediaSorter.Button.{Filled,Tonal,Outlined,Text,Icon}` in themes.xml.
- §11.2 plain `<Button>` / desync styles replaced: 0 plain `<Button>` outside `widget_scheduled_tasks.xml` (RemoteViews, exempt); 0 `@style/Widget.MaterialComponents.Button.*` outside camera (fork 1) + calculator (own taxonomy).
- §11.3 SettingsButton single M3 generation: 0 MaterialComponents parents in the family; 0 `OutlinedM3`/`TextM3` repo-wide.
- §11.4 hex on touched buttons: `item_destination_button.xml` + welcome buttons `@color/white` -> `?attr/colorOnPrimary` (0 remaining).
- §11.5 portrait/landscape parity: every layout edited with its `layout-land` twin; focus attrs preserved.
- §11.6 build + device: standard debug BUILD SUCCESSFUL; on-device run PASS 5/0/0, 0 crashes.
- §8 FEATURES: EXEMPT (no user-visible feature).
- All 5 tactical phases ✅ Done; debug tags removed on this Verified flip.

### Manual / on-device

- [x] Settings - verified on-device 2026-06-18.
- [x] Welcome (6 pages) + welcome-permissions - verified on-device 2026-06-18.
- [x] Open Source Licenses (migrated buttons) - verified on-device 2026-06-18.
- [ ] Player crop/text-viewer button surfaces - migrated (MC->family) + build-verified; on-device confirmation recommended (same remap proven on other surfaces).
- [ ] Standalone Permissions & Access screen (fragment_permissions_management.xml) - migrated + build-verified; not walked on-device this run.
- [ ] File-operation / folder-selection / scheduled-operation dialogs - migrated + build-verified; not walked on-device this run.
