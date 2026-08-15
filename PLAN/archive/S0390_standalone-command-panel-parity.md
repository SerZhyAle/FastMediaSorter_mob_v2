# Стратегическая спецификация: S0390 - Паритет командной панели STANDALONE-плеера (type-specific действия)

**Ticket:** S0390
**Status:** Archived
**Priority:** 45
**Date:** 2026-06-09
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - отколото от S0389 (Phase 04) 2026-06-09
**Tactical spec:** `PLAN/S0390_standalone-command-panel-parity/` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Без имён классов, путей, лимитов строк.

---

## 1. Проблема

В standalone-режиме (файл открыт из другого приложения) верхняя командная панель компактная: есть базовые файловые действия (назад, удалить, поделиться, избранное, инфо, переименовать, overflow) и листание по папке (S0389), но нет type-specific действий, доступных во встроенном проигрывателе: обрезка, рисование-оверлей, сжатие, поворот, OCR, перевод, Google Lens, печать, save-frame, sleep-timer, тексты песен. Пользователь воспринимает standalone как урезанный плеер.

Корень: standalone-хосты не используют движок панели in-app плеера; каждый держит свою разметку. Большинство хелперов type-specific действий жёстко привязаны к binding/Activity in-app плеера и непереиспользуемы из standalone без рефактора.

---

## 2. Цели

1. Standalone-панель показывает применимые к одиночному файлу type-specific действия для каждого типа медиа.
2. Размещение действий как в in-app: по приоритету в командной строке, при нехватке места — в ниспадающем (overflow) меню.
3. Действия выполняются реальной логикой (не заглушки); неприменимое во флейворе/типе скрыто.

**Non-goals:**

- Cast в standalone (контракт `supportsCast=false`).
- Листание по папке и «Открыть в FMS» (сделаны в S0389).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Паритет должен ощущаться как «тот же плеер».
2. Группа A (обрезка/кроп/сжатие/рисование/поворот) — первой итерацией, остальное волнами.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard`/`legacy`/`vr`/`noLegal`, `photos` (image/video). Type-specific действия гейтятся через `SUPPORT_*`/`ENABLE_*`, без `BuildConfig.IS_*` в `src/main`.
- **API level:** без новой platform-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** действия не блокируют UI-поток.
- **Локализация:** EN/RU/UK для новых строк.
- **Доступность:** contentDescription, D-pad-фокус, ≥48dp, не-цветовое различие.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** паритет в флейворах с внешними точками входа; type-гейт через `SUPPORT_*`.
- **UI placement contract:** приоритетное размещение бар↔overflow, как in-app; неприменимые кнопки скрываются.
- **Accessibility:** contentDescription + D-pad + ≥48dp на каждое новое действие.
- **Communication policy:** новые строки проходят `docs/COMMUNICATION_POLICY.md` §6.
- **Localization:** EN/RU/UK lockstep.
- **Validation level:** device-test обязателен.
- **Owner sign-off:** 2026-06-09 - объём (группа A первой, остальное волнами) подтверждён.
- **Related tickets:** S0389 (предшественник: контракт `supportsTypeSpecificActions`, листание, маршрутизация).

---

## 4. Контекст текущей архитектуры

Standalone-хосты не делят движок панели in-app плеера. Хелперы готовых generic-действий (обрезка, рисование) принимают generic Activity/Context и переиспользуемы. Остальные (OCR, Lens, печать, перевод изображения, save-frame, sleep-timer, lyrics) привязаны к `ActivityPlayerUnifiedBinding`/in-app Activity и требуют выделения generic-seam. Флаг `supportsTypeSpecificActions` уже введён (S0389), но пока не читается как гейт.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**Столп A - размещение по приоритету.** Ввести для standalone модель команд с приоритетом и план бар↔overflow (обобщить существующий планировщик in-app панели либо лёгкий standalone-аналог). Видимость гейтится `supportsTypeSpecificActions` × тип × флейвор.

**Столп B - группа A действий.** Протянуть обрезку/кроп/сжатие/рисование/поворот через готовые generic-хелперы + метод поворота в standalone VM. Нужны overlay-вью (crop/draw) в standalone-разметках.

**Столп C - волны остальных действий.** Каждое из OCR/Lens/печать/перевод/save-frame/sleep-timer/lyrics - отдельной фазой/тикетом с рефактором своего хелпера в generic-seam.

### 5.2 Точки расширяемости

- Generic-seam для binding-зависимых хелперов (root/AppCompatActivity/интерфейс вместо `ActivityPlayerUnifiedBinding`).
- Узкий host-саб-интерфейс под type-specific гейт, чтобы Document/Text не тащили весь тяжёлый контракт.

---

## 6. Открытые вопросы / Research items

1. **Планировщик бар↔overflow для standalone**
   - **Вопрос:** обобщить существующий in-app планировщик (он связан с in-app VM) или сделать лёгкий standalone-планировщик?
   - **Находка (код-ресерч 2026-06-10):** `CommandPanelLayoutPlanner.planLayout()` — чистая геометрия бар↔overflow, без Android-вью и без VM. Связан с in-app VM только метод `buildActiveCommands(state: PlayerViewModel.PlayerState)`; `CommandPanelAvailabilityUpdater`/`CommandPanelController` жёстко завязаны на `ActivityPlayerUnifiedBinding` и непереиспользуемы.
   - **Решение:** переиспользовать `planLayout()` как есть; ввести лёгкий standalone-набор Group A команд (узкий enum CROP/CROP_TO_FILE/COMPRESS/DRAW/ROTATE) с гейтом тип × локально-записываемый путь × `SUPPORT_IMAGES`. `buildActiveCommands`/`CommandPanelAvailabilityUpdater` НЕ трогаем (защита in-app плеера от регрессий).
   - **Статус:** Resolved

2. **Generic-seam для binding-зависимых хелперов**
   - **Вопрос:** какой минимальный seam покрывает Group A без переписывания логики?
   - **Находка (код-ресерч 2026-06-10):** движки Group A уже generic: `ImageCropManager` (`Context`+`FileOperationUseCase`), `ImageDrawOverlayManager` (`Activity`+`ViewGroup`), `ScreenRotationManager`/`StandalonePlayerViewModel.toggleRotationSensor` для поворота экрана. Но player-side делегаты `PlayerCropDelegate`/`PlayerDrawingSaveHelper` привязаны к `PlayerActivity`/`activityBinding` и непереиспользуемы.
   - **Решение:** движки не редактируем; вводим standalone-side делегаты (`StandaloneCropController`, `StandaloneDrawSaveHelper`), которые подключают generic-движки к standalone-разметке/VM. `mediaContentArea`/`photoDualSurfaceContainer` уже присутствуют в standalone-layout — crop/draw оверлеи монтируются туда; для draw-тулбара добавляется `<ViewStub>` в обе ориентации.
   - **Статус:** Resolved

3. **Document/Text и контракт возможностей**
   - **Вопрос:** делать их реализующими полный `PlayerHostCapabilities` или ввести узкий саб-интерфейс?
   - **Решение:** Group A трогает только `PhotoVideoStandaloneActivity` (image). Узкий саб-интерфейс под type-specific преждевременен (YAGNI) — гейт по типу медиа в standalone-планировщике достаточен. Саб-интерфейс отложить до волн C, когда подключатся Document/Text/Audio действия.
   - **Статус:** Resolved

4. **Поворот в Group A — какой именно**
   - **Находка:** in-app команда `ROTATION_TOGGLE` = переключатель сенсорного авто-поворота экрана (`toggleRotationSensor()` → `ScreenRotationManager`), гейт `!followSystemRotation && hasAccelerometer`. Это и есть «поворот» Group A (S0389 §6.1 «rotation toggle, нужен метод в VM»), а не диалог поворота пикселей (`ImageEditDialog`/`RotateImageUseCase` — отдельная волна).
   - **Решение:** в standalone добавить метод-переключатель сенсорного поворота в `StandalonePlayerViewModel` + `ScreenRotationManager`, зеркало in-app.
   - **Статус:** Resolved

5. **Файл вне локальной ФС (content:// без записываемого пути)**
   - **Решение:** зеркалить in-app поведение `ImageCropManager.resolveDestinationPath` — fallback сохранения в Downloads (crop-to-file/compress), а не скрытие действия. In-place crop требует записываемого локального пути (резолв через `ResolveLocalPathFromUriUseCase`); иначе показываем save-as-вариант.
   - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Рефактор binding-зависимых хелперов задевает in-app плеер | Средняя | Регрессии в основном плеере | Выделять seam без смены поведения; покрывать тестами; собирать на target-варианте |
| Рост хост-классов панели за лимит сопровождаемости | Средняя | Усложнение поддержки | Выносить в helper-роли/менеджеры |
| Жёсткие `nextFocus*` в standalone-разметках | Низкая | Поломка D-pad/TV навигации | Перепроверять focus-chains при добавлении кнопок; править layout+layout-land |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новое: при внешнем просмотре доступны те же действия, что и во встроенном плеере (обрезка, поворот, рисование и др.). Обновление `docs/FEATURES.md` + `_RU` + `_UK` - при реализации.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Ветвление через `supportsTypeSpecificActions`, а не «if standalone»**

- **Решение:** видимость type-specific действий гейтится capability-флагом.
- **Почему:** сохраняет seam S0380/S0389; единая точка ветвления.

---

## 10. Связи с другими спеками

- S0389 - предшественник: ввёл флаг `supportsTypeSpecificActions`, листание по папке и маршрутизацию «Открыть в FMS». S0390 завершает паритет панели, отколотый от S0389 Phase 04.

---

## 11. Критерии готовности (strategic-level)

1. При внешнем просмотре изображения доступны обрезка/кроп/сжатие/рисование/поворот, работают реальной логикой.
2. Действия размещаются по приоритету: в строке, при нехватке места - в overflow.
3. Неприменимые во флейворе/типе действия скрыты.
4. Последующие волны (OCR/Lens/печать/перевод/save-frame/sleep-timer/lyrics) протянуты по мере рефактора их хелперов.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0390`.
