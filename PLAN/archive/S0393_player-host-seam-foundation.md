# Стратегическая спецификация: S0393 - Единый host-seam плееров + перенос функционала legacy-хоста (фундамент догона)

**Ticket:** S0393
**Status:** Archived
**Priority:** 55
**Date:** 2026-06-10
**Tier:** 4 - Strategic (ad-hoc)
**Roadmap entry:** S0392 ROADMAP R0 (фундамент) - запрос владельца 2026-06-10
**Tactical spec:** `PLAN/S0393_player-host-seam-foundation/`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Draft - допускает черновой стиль.

---

## 1. Проблема

Type-specific действия плеера (обрезка, рисование, OCR, Lens, перевод, печать, save-frame, sleep-timer, lyrics, playback-control диалог и т.д.) реализованы делегатами, жёстко типизированными на `PlayerActivity`/`ActivityPlayerUnifiedBinding`/in-app `PlayerViewModel`. Это **доминирующий блокер B1** карты S0392: движки (логика) переиспользуемы, а склейка - нет, поэтому каждое действие протягивается в standalone вручную и рискует тихо разойтись между хостами (компилятор забытый sibling не ловит).

Дополнительно семейство хостов рассинхронено: `DocumentStandaloneActivity` и `TextStandaloneActivity` не реализуют `PlayerHostCapabilities` вовсе; а legacy `StandalonePlayerActivity` остаётся единственным носителем части функций (PiP, playback-control диалог, клавиши прокрутки текста, WebView-меню выделения, полный клавиатурный слой, полный `StandaloneViewManager`) - наследование, начатое S0380, не доведено.

Без общего seam догон всех четырёх областей (картинки/видео/аудио/документы-текст) будет дорогим и расходящимся.

Область: feature-path «Player/playback/doc viewing» модуля `app_v2`.

---

## 2. Цели

1. Ввести единый **binding-agnostic host-seam** - интерфейс, который потребляют делегаты type-specific действий вместо `PlayerActivity`/binding.
2. In-app `PlayerActivity` реализует seam как адаптер **без изменения поведения** (защита эталона от регрессий).
3. Специализированные standalone-хосты реализуют seam; `DocumentStandaloneActivity` и `TextStandaloneActivity` дополнительно реализуют `PlayerHostCapabilities`.
4. **Перенести весь функционал** из legacy `StandalonePlayerActivity` в seam + специализированные хосты (PiP, playback-control диалог, клавиатурный слой, клавиши прокрутки текста, WebView-меню выделения, всё, что у него ещё уникально).
5. После переноса пометить legacy `StandalonePlayerActivity` `@Deprecated` + `TODO` на удаление; не удалять, пока на него что-то маршрутизируется.
6. Seam становится единой точкой, после которой волны догона (картинки/видео/аудио/документы-текст) подключаются **однократно**, а не на каждый хост.

**Non-goals:**

- Сама протяжка отдельных действий волн (картинки/видео/аудио/документы) - порождённые тикеты поверх seam, не данная спека (кроме того, что нужно для переноса legacy).
- Переписывание движков (`ImageCropManager`, `ImageDrawOverlayManager`, viewer-менеджеры) - они уже generic.
- Структурные in-app-only возможности (list-nav, copy/move, undo, separate-window, persistent audio) - остаются by design (S0392 B2).
- Wear OS.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Сначала фундамент, потом волны (подтверждено 2026-06-10).
2. Из legacy-хоста забрать функционал прежде, чем его прятать; deprecated + TODO-удалить.
3. Догон в итоге - все четыре области.

### 3.2 Жёсткие ограничения

- **Регресс эталона:** in-app плеер не меняет поведение; seam-адаптер - behaviour-preserving; проверять на target-вариантах + тестами.
- **Flavor:** только флейворы с внешними точками входа; типы гейтятся через `SUPPORT_*`/`ENABLE_*`, без `BuildConfig.IS_*` в `src/main` (Rule 14).
- **Архитектура:** ветвление - через `PlayerHostCapabilities`/seam, не `if standalone`.
- **Производительность:** не жертвовать холодным стартом standalone; тяжёлые иерархии - лениво (`ViewStub`/`dagger.Lazy`).
- **LOC:** хост-классы держать в пределах лимита; логику выносить в seam-адаптеры/менеджеры.
- **API level:** без новой platform-специфики (PiP уже за `@RequiresApi`).

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** перенос не меняет видимое размещение in-app; standalone получает функции на тех же местах панели.
- **Validation level:** device-test обязателен (in-app регресс + standalone-функции на устройстве).
- **Data compatibility:** без новых форм хранения.
- **Owner sign-off:** объём (фундамент + перенос legacy + deprecate) подтверждён 2026-06-10.
- **Related tickets:** S0392 (карта/роадмап, R0), S0389/S0390 (уже подключённые куски паритета), S0380 (раздробил плеер - источник долга).

---

## 4. Контекст текущей архитектуры

Движок in-app-панели (`CommandPanelController`/`CommandPanelAvailabilityUpdater`/`CommandPanelLayoutPlanner`) и делегаты действий (`PlayerCropDelegate`, `PlayerDrawingSaveHelper`, `PlayerImageTranslationManager`, OCR/Lens/print/lyrics/save-frame менеджеры) завязаны на `PlayerActivity`/`ActivityPlayerUnifiedBinding`. Контракт `PlayerHostCapabilities` уже существует как seam ветвления возможностей, но обработчики действий за него не убраны. `StandaloneImageEditController` (S0390) - первый пример standalone-side обвязки generic-движка; его обобщение и есть зерно seam. Legacy `StandalonePlayerActivity` использует общий унифицированный layout и несёт самый полный набор функций среди standalone - он одновременно «источник переноса» и «кандидат на deprecate».

---

## 5. Предлагаемый подход

### 5.1 Host-seam

Ввести узкий интерфейс (рабочее имя - host action seam), отдающий делегатам ровно то, что им нужно, без `PlayerActivity`:

- поток текущего файла (и его разрешённого локального пути для записи);
- корневой `View` и точки монтирования оверлеев (область медиа-контента, контейнер изображения);
- хук перерисовки текущего файла после правки;
- хост диалогов (`AppCompatActivity`);
- lifecycle-scope;
- доступ к контексту ресурса там, где он есть (in-app), и его отсутствие (standalone) - выражается опционально, без ветвления в делегате.

Делегаты type-specific действий рефакторятся на потребление seam. In-app `PlayerActivity` реализует seam адаптером поверх своего binding/VM - поведение не меняется.

### 5.2 Перенос legacy-хоста

Снять с `StandalonePlayerActivity` инвентарь уникальных функций (S0392 MATRIX §9) и перенести в seam/специализированные хосты: PiP-менеджер, playback-control диалог, клавиатурный слой (включая клавиши прокрутки текста), WebView-меню выделения. После переноса - `@Deprecated` + `TODO`-удалить; маршрутизация внешних интентов уже идёт на специализированные хосты (диспетчер), legacy остаётся только прямой/fallback точкой до удаления.

### 5.3 Адаптация Document/Text

`DocumentStandaloneActivity` и `TextStandaloneActivity` реализуют `PlayerHostCapabilities` (узкий саб-контракт, если полный избыточен), чтобы войти в общий pipeline и принимать seam-делегаты.

### 5.4 Точки расширяемости

- После seam волны догона (картинки/видео/аудио/документы) - отдельные тикеты, каждый подключает действие однократно через seam.
- Карта S0392 - живой артефакт: статус строки → «есть» по мере подключения.

---

## 6. Открытые вопросы / Research items

1. **Минимальный состав seam**
   - Какой именно набор (root/overlay-mounts/reload/dialog-host/file-flow/resource-opt) покрывает crop/draw/edit-dialog/OCR/Lens/translate/print/save-frame/lyrics/playback-dialog без переписывания их движков?
   - **Статус:** Open (вход - S0392 MATRIX coupling-флаги)

2. **Полный vs узкий `PlayerHostCapabilities` для Document/Text**
   - Реализовывать полный контракт или ввести узкий саб-интерфейс под их набор?
   - **Статус:** Open

3. **Границы переноса legacy**
   - Что именно ещё уникально в `StandalonePlayerActivity` сверх известного (PiP/playback-dialog/text-keys/WebView-ActionMode/keyboard) - полный диф перед переносом.
   - **Статус:** Open

4. **Порядок: seam-then-harvest или harvest-then-seam**
   - Сначала seam и адаптация in-app, потом перенос legacy на seam; либо перенос как драйвер дизайна seam.
   - **Статус:** Open

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Seam-рефактор регрессит in-app плеер | Высокая | Поломка эталона | Адаптер без смены поведения; тесты; сборка на target-вариантах; пошагово по одному делегату |
| Перенос legacy теряет функцию | Средняя | Регресс standalone | Полный диф legacy перед deprecate; перенос до пометки `@Deprecated` |
| Рост хост-классов за лимит LOC | Средняя | Усложнение | Логику в seam-адаптеры/менеджеры |
| Двойной источник правды на время миграции | Средняя | Путаница | Мигрировать делегат за делегатом, не оставлять half-state надолго |
| D-pad/TV focus-цепочки при переносе клавиатуры | Низкая | Поломка навигации | Перепроверять focus-chains; layout+layout-land |

---

## 8. Влияние на пользователя (docs/FEATURES)

Прямого пользовательского изменения фундамент не даёт (внутренний рефактор + перенос). Видимый эффект - в последующих волнах догона. После переноса legacy пользователь не теряет ни одной функции (перенос полный до deprecate).

---

## 9. Архитектурные решения (ADR)

**ADR-1: Единый binding-agnostic host-seam вместо делегатов на `PlayerActivity`**

- **Решение:** делегаты потребляют seam; хосты реализуют seam.
- **Альтернативы:** продолжать дублировать обвязку на хост.
- **Почему:** схлопывает «менять во всех хостах» в «менять в одном»; снимает класс B1 разом (S0392 §10).

**ADR-2: Перенести функционал legacy перед deprecate, не удалять сразу**

- **Решение:** harvest → `@Deprecated` + `TODO` → удаление после снятия маршрутизации.
- **Альтернативы:** удалить сразу (потеря функций) / оставить навсегда (вечный долг).
- **Почему:** ноль потерь функционала + явный путь к удалению (решение владельца 2026-06-10).

---

## 10. Связи с другими спеками

- S0392 - карта/роадмап; данная спека реализует его R0 (фундамент).
- S0389/S0390 - уже подключённые куски паритета; `StandaloneImageEditController` (S0390) - зерно seam.
- S0380 - раздробил плеер (источник долга); legacy `StandalonePlayerActivity` - его остаток.
- Будущие волны догона (картинки/видео/аудио/документы-текст) - потребители seam, отдельные тикеты.

---

## 11. Критерии готовности (strategic-level)

1. Существует host-seam, потребляемый делегатами type-specific действий; in-app реализует его без изменения поведения (регресс-проверка пройдена).
2. Document/Text-хосты входят в общий pipeline (реализуют `PlayerHostCapabilities`/узкий саб-контракт).
3. Весь уникальный функционал legacy `StandalonePlayerActivity` перенесён в seam/специализированные хосты; ни одна функция не потеряна.
4. Legacy `StandalonePlayerActivity` помечен `@Deprecated` + `TODO`-удалить; внешняя маршрутизация на него не идёт.
5. Подключение нового type-specific действия требует правки в одном месте (через seam), не в каждом хосте.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0393` - создаст `PLAN/S0393_player-host-seam-foundation/` с фазами.

---

## Revision History

- **2026-06-15** - by `/spec-test-device` (emulator-5554, sdk_gphone16k_x86_64, Android 17)
  - Scenario: temp/S0393_mobile_test_scenario_20260615_2210.md · PASS/FAIL/SKIPPED 5/0/1 · log errors 0 (emulator GPU/codec noise only)
  - On-device tag coverage 6/7: seam crop entry (core), rotation-decouple, keyboard layer, image-action gate, PiP wiring, playback-control dialog all fired in standalone image+video hosts; no crash, no regression.
  - Not exercised on emulator (owner-manual real-device gaps): WebView ActionMode (tag 327), PiP enter/resume windowing, non-local share gate (`editable=false`), D-pad/TV focus. Spec stays `BlockNeedUserTest` for owner sweep of these four.
