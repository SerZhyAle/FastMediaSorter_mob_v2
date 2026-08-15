# Стратегическая спецификация: S0671 - Захват экрана MediaProjection + пост-обработка в Play-сборке

**Ticket:** S0671
**Status:** Archived
**Priority:** 60
**Date:** 2026-06-24
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос владельца 2026-06-24 (перенос noLegal-функционала в standard, пункт 1)
**Tactical spec:** `PLAN/S0671_standard-mediaprojection-capture-suite/`
**Tactical plan:** `PLAN/S0671_standard-mediaprojection-capture-suite/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

В отгружаемой Google Play сборке (флейвор `standard`) полностью отсутствует захват экрана и вся пост-обработка снимков, хотя совместимый с политикой Play движок захвата (MediaProjection с системным диалогом согласия) в проекте уже написан. Он не доезжает до пользователя только потому, что отключён сборочным флагом захвата (для «быстрой» подачи в Play). В результате пять способностей, безопасных для Play, живут лишь в noLegal-сборке (sideload): снимок из меню «Операции», захват на Android 8-9 через разовое согласие, копирование снимка в буфер + открытие в редакторе рисования + OCR-перевод, отправка через курированный список получателей, настраиваемая папка сохранения с цепочкой запасных каталогов.

Эффект на пользователя: владельцы Play-версии вообще не могут снять и обработать скриншот средствами приложения, хотя технически и юридически это возможно.

---

## 2. Цели

1. Пользователь Play-сборки может сделать снимок экрана из меню «Операции» через штатный системный диалог согласия MediaProjection - без службы спец-возможностей и без sideload.
2. На Android 8-9 тот же снимок делается через MediaProjection с разовым системным согласием.
3. Снимок можно сразу скопировать в буфер обмена, открыть в редакторе рисования и распознать+перевести (OCR) средствами on-device.
4. Снимок можно отправить через курированный список получателей вместо полного системного диалога «Поделиться».
5. Снимок сохраняется в выбранное назначение с цепочкой запасных папок (Pictures/Screenshots -> DCIM/Screenshots -> Downloads).
6. Выполнены нетехнические обязательства Play, без которых способность нельзя отгрузить: декларация foreground-service для типа mediaProjection, заметное in-app раскрытие (prominent disclosure) перед согласием, актуальные privacy policy и раздел Data safety.

**Non-goals:**

- Краевые жесты и невидимая полоса-триггер - отдельная спека S0672 (высокий риск, зависит от этой).
- Тихий захват через службу спец-возможностей - остаётся исключительно в noLegal (запрещён политикой Play).
- Установка APK из «Обзора» / бейдж VR-APK - остаётся в noLegal.
- Любые изменения поведения noLegal-сборки.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Минимальная переделка: переиспользовать существующий движок захвата, а не писать новый.
2. Пользователь должен заранее понимать, что каждый сеанс захвата сопровождается системным диалогом согласия и постоянным уведомлением о трансляции (это плата за Play-совместимый путь).

### 3.2 Жёсткие ограничения

- **Flavor:** только `standard` (Play). noLegal остаётся без изменений и сохраняет свой тихий путь захвата. Включение поведения управляется сборочным флагом захвата экрана (сейчас выключен для standard); конкретное имя флага и его проводка - в `/spec-tech`. Дисциплина source-set по `dev/FLAVOR_DEVELOPMENT_RULES.md` сохраняется: общий движок остаётся в общем source-set, флейвор-специфика не протекает в `src/main`.
- **API level:** путь обязан работать на Android 8-9 (разовое согласие) и на Android 14/15 (тип foreground-service mediaProjection обязателен, токен согласия одноразовый, постоянное уведомление и чип в статусбаре на Android 15). minSdk standard - 26.
- **Производительность:** ресурсы захвата (виртуальный дисплей, foreground-service) освобождаются сразу по завершении сеанса и по системной остановке трансляции.
- **Совместимость данных:** сохранение через MediaStore (scoped storage), без устаревшего прямого доступа к файловой системе на Android 10+.
- **Локализация:** EN/RU/UK обязательна для всех новых пользовательских строк (раскрытие, тосты, подписи).
- **Доступность:** диалог раскрытия и любые новые элементы управления доступны через TalkBack и не полагаются только на цвет.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** `standard` (Play) - вводит способность; noLegal не затрагивается.
- **API level constraints:** Android 8-9 (разовое согласие) и Android 14/15 (обязательный FGS-тип mediaProjection, одноразовый токен, постоянное уведомление, чип Android 15); minSdk 26.
- **UI placement contract:** заметное in-app раскрытие показывается в нормальном потоке непосредственно перед системным запросом согласия (не закопано в настройках); снимок из меню «Операции».
- **Accessibility:** диалог раскрытия и подтверждения доступны через TalkBack, цель тапа >= 48dp, отличие не только цветом.
- **Communication policy:** все новые строки соответствуют `docs/COMMUNICATION_POLICY.md` (тон-чеклист §6 - обязательный гейт перед интеграцией строк).
- **Localization:** EN/RU/UK для всех новых строк - обязательно.
- **Data compatibility:** запись снимков через MediaStore; устаревший прямой доступ к хранилищу не используется на Android 10+.
- **Validation level:** сборка `standard` debug + ручная проверка на устройстве (снимок -> согласие -> сохранение); релизная сборка `standard` собирается с включённым захватом.
- **Owner sign-off:** 2026-06-24 - владелец подтвердил отгрузку в Play; UX-цена (системное согласие + постоянное уведомление каждый сеанс) принята.
- **Related tickets:** S0621 (реализованный standard-путь жеста, BlockNeedUserTest), S0418/S0559/S0630 (архив, предыдущие итерации движка и гейта), S0672 (зависит от этой - краевые жесты), S0183 (sideload-установка, остаётся в noLegal).

---

## 4. Контекст текущей архитектуры

За захват в проекте отвечает общий движок подтверждаемого захвата: активность системного согласия, foreground-service захвата типа mediaProjection и набор пусковых точек (пункт меню «Операции»). Пост-обработка (буфер, редактор рисования, OCR-перевод, отправка получателям, выбор папки сохранения) уже живёт во флейвор-нейтральном слое и переиспользуется обоими сборками. Весь этот движок монтируется в standard только при включённом сборочном флаге захвата, который для «быстрой» подачи в Play выключен - поэтому в отгружаемой сборке его нет. Решить проблему «из коробки» нельзя: помимо флага, для законной отгрузки требуются нетехнические артефакты Play (декларация FGS, раскрытие, политика), которых сейчас нет.

---

## 5. Предлагаемый подход

Включить уже существующий Play-совместимый движок захвата в отгружаемую `standard`-сборку и закрыть нетехнические обязательства Play. Никакого нового механизма захвата не вводится - переиспользуется путь «системный диалог согласия -> foreground-service -> сохранение -> пост-обработка».

### 5.1 Основные столпы / модули

1. **Включение движка в standard.** Снять сборочный гейт, скрывающий движок захвата от Play-сборки, так чтобы пункт меню «Операции», путь Android 8-9 и пост-обработка стали доступны. noLegal-поведение не меняется.
2. **Заметное раскрытие (prominent disclosure).** Перед первым системным запросом согласия пользователю показывается понятное объяснение: что захватывается, как используется и куда уходит, с трактовкой контента экрана как персональных/чувствительных данных. Раскрытие - в нормальном потоке, не в недрах настроек.
3. **Декларация Play и сопутствующие документы.** Заполнить в Play Console декларацию foreground-service для типа mediaProjection (use case, описание функции, демо-видео шагов), выложить privacy policy и заполнить раздел Data safety под сбор контента экрана. Это не код, но это часть критериев готовности.
4. **Подтверждение пост-обработки.** Убедиться, что после захвата доступны буфер обмена, открытие в редакторе рисования, OCR-перевод (on-device), отправка получателям и сохранение в выбранную папку с запасной цепочкой - все эти способности уже флейвор-нейтральны и должны «приехать» вместе с движком.

### 5.2 Потоки данных и событий

Пользователь -> пункт меню «Операции» -> заметное раскрытие -> системный диалог согласия (на каждый сеанс) -> foreground-service захвата -> снимок -> сохранение через MediaStore в выбранную папку (с запасной цепочкой) -> опционально буфер / редактор рисования / OCR-перевод / отправка получателям. По системной остановке трансляции ресурсы освобождаются.

### 5.3 Точки расширяемости

- Слой пусковых точек захвата должен оставаться открытым: краевой жест (S0672) подключается как ещё один триггер к тому же движку, без его дублирования.
- Назначение сохранения и цепочка запасных папок остаются конфигурируемыми.

---

## 6. Открытые вопросы / Research items

1. **Допустимость MediaProjection-захвата в Play и его условия**
   - **Вопрос:** разрешён ли в Play захват экрана общего назначения через MediaProjection и какие обязательства это накладывает?
   - **Нужно выяснить:** требования к согласию на каждый сеанс, тип FGS, декларация Play Console, prominent disclosure, Data safety.
   - **Статус:** Resolved (исследование политики Play 2025-2026 проведено).
   - **Артефакт:** `PLAN/S0671_standard-mediaprojection-capture-suite/research/01__mediaprojection-play-policy.md`
2. **Пост-обработка и хранилище под политикой Play**
   - **Вопрос:** не создают ли буфер/редактор/OCR/отправка получателям/сохранение проблем с политикой или scoped storage?
   - **Статус:** Resolved.
   - **Артефакт:** `PLAN/S0671_standard-mediaprojection-capture-suite/research/02__postprocess-storage-play-policy.md`

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Play отклоняет декларацию FGS mediaProjection | Средняя | Сборка не публикуется | Корректный use case + демо-видео реального сценария; ручная проверка декларации до подачи |
| Нет/слабое prominent disclosure | Средняя | Отказ по User Data policy | Раскрытие в нормальном потоке непосредственно перед согласием; ревью текста по COMMUNICATION_POLICY |
| Недовольство UX (диалог + постоянное уведомление каждый сеанс) | Средняя | Негативные отзывы | Объяснить в onboarding/раскрытии, что это требование платформы и плата за приватность |
| Регрессия noLegal при снятии гейта | Низкая | Поломка тихого пути | Изменение касается только standard-ветки гейта; noLegal-путь не трогается, проверяется отдельно |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая способность для Play-версии. Предлагаемая формулировка (EN/RU/UK заполнит `/skill-release` из диффа ALL_FEATURES): «Снимок экрана из приложения с системным согласием, с копированием в буфер, рисованием, OCR-переводом, отправкой получателям и выбором папки сохранения». Точную запись инвентаря добавить в `docs/ALL_FEATURES.jsonl` при реализации.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Только MediaProjection-путь в standard, без службы спец-возможностей**

- **Решение:** в Play-сборке захват идёт исключительно через MediaProjection с системным согласием на каждый сеанс.
- **Альтернативы:** тихий захват через службу спец-возможностей (как в noLegal).
- **Почему:** политика Play (Use of the AccessibilityService API) запрещает использовать спец-возможности для нелечебной цели; объявление isAccessibilityTool=true для скриншот-тула - отдельное нарушение (Deceptive Behavior). MediaProjection - штатный, одобряемый путь.

**ADR-2: Отгрузка управляется сборочным флагом, а не правкой `src/main`**

- **Решение:** включение/выключение всего движка захвата в standard - через существующий сборочный флаг.
- **Альтернативы:** условные проверки `BuildConfig.IS_*` в общем коде.
- **Почему:** Rule 15 запрещает флейвор-гварды в `src/main`; флаг + source-set дисциплина уже на месте.

---

## 10. Связи с другими спеками

- **S0672** (краевые жесты, Play-совместимый триггер) - зависит от этой спеки; подключается к тому же движку.
- **S0621** - реализованный standard-путь жеста (BlockNeedUserTest); пересекается в части движка, но эта спека про отгрузку всего набора + комплаенс, а не про сам жест.
- **S0418 / S0559 / S0630** (архив) - предыдущие итерации движка, разделения меню и сборочного гейта; контекст.
- **S0183** - sideload-установка APK; остаётся в noLegal, вне объёма.

---

## 11. Критерии готовности (strategic-level)

1. На свежей `standard`-сборке пользователь делает снимок экрана из меню «Операции» через системный диалог согласия, без участия службы спец-возможностей и без sideload.
2. Снимок можно скопировать в буфер, открыть в редакторе рисования, распознать+перевести (OCR), отправить через курированный список получателей.
3. Снимок сохраняется в выбранную папку; при недоступности срабатывает цепочка запасных (Pictures/Screenshots -> DCIM/Screenshots -> Downloads).
4. Путь работает на Android 8-9 (разовое согласие) и на Android 14/15 (FGS-тип mediaProjection, постоянное уведомление, корректная обработка системной остановки).
5. Перед первым согласием показывается заметное in-app раскрытие; в Play Console заполнена декларация FGS mediaProjection с демо-видео; выложены privacy policy и Data safety.
6. Релизная `standard`-сборка собирается с включённым захватом; noLegal-сборка не изменена и проходит проверку отдельно.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0671` - создаст `PLAN/S0671_standard-mediaprojection-capture-suite/` с фазами.

---

## Last Audit

- **2026-06-26** - `/spec-test-device` on emulator-5554 (standard debug `2.60.6261.051-DEBUG`, Android release 17 / API 37). Status NOT flipped (owned by `/spec-check`). Scenario + evidence: `temp/S0671_mobile_test_scenario_20260626_1054.md`, log `temp/S0671_run_20260626_1054.log`.

### Manual / on-device

- [!] Criterion 1 - menu-screenshot via system consent on standard - **FAIL on-device 2026-06-26**. The "Screenshot test" button (`btnTakeScreenshotNow`) is ABSENT in Settings -> Управление (Operations); settings-search for "screenshot" returns "no matches". There is no reachable UI entry point to start the capture flow on standard. Root cause: the button lives inside the edge-gesture card (`groupScreenGestures`), which `OperationsGesturesManager` hides when `screenGestureControllers` is empty; on standard the edge overlay is OFF (`fms.edgeGestureOverlay=off`) so the controller set is empty and the card (with the button) is GONE - even though the `MenuScreenshotLauncher` suite IS mounted. Phase 01's flag-split decoupled the suite from the overlay but left the button's visibility keyed to the overlay card. See scenario "Root cause" section.
- [ ] Criterion 2 - clipboard / drawing editor / OCR-translate / send-to-recipients - **INCONCLUSIVE (unreachable)**. Post-save actions (draw/OCR/send-to) are keyed by gesture DIRECTION in `ScreenCaptureService.runPostSaveAction`; the menu path passes `null` -> `SILENT_SCREENSHOT` only, so they are not exercisable via the menu path and ride the edge gesture (S0672, OFF in standard). Clipboard runs in-service before save (independent of direction) but its toggle lives in the same hidden gestures card. None reachable while the entry point is missing.
- [ ] Criterion 3 - save to chosen folder + fallback chain - **INCONCLUSIVE (unreachable)**. No way to trigger a capture, so save/fallback could not be observed on-device. Code path (`SaveScreenshotUseCase` + `screen_capture_saved_to` toast + fallback notifier) is present but unexercised.
- [ ] Criterion 4 - Android 8-9 + 14/15 FGS / persistent notification / system stop - **INCONCLUSIVE (unreachable)**. FGS service and `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission confirmed present in the standard APK (static), but no capture session could be started to observe the notification or stop handling.
- [ ] Criterion 5 - prominent disclosure before first consent - **INCONCLUSIVE (unreachable)**. Disclosure dialog code + strings (`screen_capture_disclosure_*`) + persisted flag (`screenCaptureDisclosureAccepted=false` at start) all present and mounted, but the disclosure never showed because the entry point is missing. Probe `Timber.d("S0671: ..")` produced 0 hits in logcat (flow never entered). Play Console FGS declaration / privacy policy / Data safety are external (non-code) and out of device-test scope.

> On-device summary: suite IS compiled into standard (manifest + permissions + probe verified statically), but the **menu-screenshot UI entry point is unreachable on standard**, blocking criterion 1 and leaving 2-5 unexercisable. No crashes (0 app errors in logcat). This is a UI-visibility regression from the Phase 01 capture/overlay flag-split, in-scope for S0671 -> `/spec-fix S0671`.

---

## Revision History

- **2026-06-26** - by `/spec-test-device` (`claude-opus-4-8[1m]`, device: emulator-5554 Android 17/API 37)
  - Scenario: `temp/S0671_mobile_test_scenario_20260626_1054.md` · PASS/FAIL/INCONCLUSIVE 0/1/4 · Errors in log: 0
  - Finding: standard ships the capture suite but with no reachable menu-screenshot UI entry (button trapped in the OFF edge-gesture card). Recommended `/spec-fix S0671`.
