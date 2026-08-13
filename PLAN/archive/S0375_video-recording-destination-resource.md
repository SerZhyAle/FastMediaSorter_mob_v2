---
ticket: S0375
status: Verified
priority: 50
date: 2026-06-06
tier: 3
---

# Стратегическая спецификация: S0375 - Ресурс для записи видео в Playback settings

**Ticket:** S0375
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-06
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-06: по аналогии с `Ресурс для записи с микрофона` и `Ресурс для фото с камеры` добавить `Ресурс для записи видео`, покрыть portrait и landscape и описать полную реализацию.
**Tactical spec:** `PLAN/S0375_video-recording-destination-resource/` (будет создан через `/spec-tech`)
**Tactical plan:** `PLAN/S0375_video-recording-destination-resource/INDEX.md`
**Implemented date:** 2026-06-07

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room и деталей DI.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec.
- **Goal / expected outcome:** Provided by user - создать стратегическую спецификацию на отдельную настройку `Ресурс для записи видео` по аналогии с destination-настройками для микрофона и фото с камеры, с покрытием portrait и landscape и полной реализацией маршрута сохранения.
- **Local anchor:** Provided by user - существующие настройки `Ресурс для записи с микрофона` и `Ресурс для фото с камеры`, а также текущий блок `Запись видео` в Playback settings.
- **Scope boundaries / forbidden areas:** Provided by user - в объёме сама настройка для portrait и landscape и полная реализация поведения записи видео; отдельный redesign других capture-секций и новый video capture stack не запрошены.
- **Done / success signal:** Provided by user - спецификация фиксирует новый video destination selector, его применение в рабочем маршруте видеозаписи и одинаковую структуру поведения для обеих ориентаций.
- **Autonomy rule:** Delegated by user - agent may decide with explicit assumptions (explicit proceed signal: `implement S0375`, 2026-06-07).
- **UI decisions / delegation:** Delegated by user - selector размещается внутри существующего video block сразу под `Open recorded video in player`, виден только при включённой видеозаписи, использует тот же selector pattern, что и соседние capture selectors, а fallback-label формулируется как `По умолчанию: если текущий ресурс недоступен, папка Movies устройства`.

Owner gate закрыт: user delegated the remaining implementation choices on 2026-06-07 by explicitly requesting `implement S0375`.

---

## 1. Проблема

В Playback capture-настройках уже есть три соседних пользовательских сценария: фото с камеры, запись видео и запись с микрофона. Для фото и микрофона surface уже доведён до явного destination-контракта: пользователь видит отдельный selector ресурса-получателя и понимает fallback-модель при пустом значении.

У видеозаписи такого контракта пока нет. Пользователь видит только включение самой команды и опциональное открытие результата в плеере, но не может отдельно задать ресурс-получатель для записи видео. При этом сама запись уже существует как самостоятельный сценарий и визуально соседствует с двумя flow, у которых destination-настройка есть.

Из-за этого возникает асимметрия и скрытая связанность:

1. Video block выглядит незавершённым относительно соседних camera и microphone блоков.
2. Поведение видеозаписи в случаях, когда текущий контекст не даёт пригодной папки-получателя, остаётся неочевидным для пользователя.
3. Внутренняя маршрутизация видео рискует продолжать зависеть от photo-oriented destination-логики, хотя пользователь ожидает отдельную video-настройку.
4. Разрыв одинаково заметен и в portrait, и в landscape, потому что video block уже отображается в обеих ориентациях, но без третьего selector-а.

---

## 2. Цели

1. Добавить явную настройку `Ресурс для записи видео` в ту же Playback capture-family, где уже живут selectors для микрофона и фото с камеры.
2. Сохранить семантическое равенство portrait и landscape: одинаковый состав блоков, одинаковые visibility rules, одинаковый пользовательский смысл.
3. Выделить для видеозаписи собственный destination-contract, независимый от настроек фото и микрофона.
4. Не ломать уже понятный пользователю сценарий записи прямо в текущий ресурс, если текущий ресурс сам по себе пригоден для сохранения.
5. Определить детерминированный fallback для видеозаписи на случай пустого, устаревшего или неприменимого selector-а.
6. Описать полную реализацию, а не частичный layout patch: settings surface, persistence, routing, локализация, feature-copy и validation.

**Non-goals:**

- новый встроенный механизм видеозаписи вместо существующего системного сценария;
- отдельный editor-flow или post-capture editing для видео;
- переработка уже существующих destination-настроек фото и микрофона;
- изменение per-widget target contract там, где target уже явно выбирается пользователем на уровне конкретного widget entry point;
- изменение Wear OS surface.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Новая video destination-настройка должна быть добавлена именно по аналогии с существующими selectors для микрофона и камеры.
2. Изменение должно быть отражено в portrait и landscape, а не только в одном layout-варианте.
3. Ожидается полная реализация, а не только добавление строк и кнопки без рабочего маршрута сохранения.

### 3.2 Жёсткие ограничения

- **Orientation parity:** portrait и landscape обязаны остаться функционально эквивалентны.
- **No hidden coupling:** изменение destination для фото не должно молча перенаправлять видео, и наоборот.
- **Behavior continuity:** если видеозапись запускается из реально пригодного текущего ресурса, привычный сценарий записи в него не должен регрессировать без явного owner-решения.
- **Target eligibility:** selector должен предлагать только те ресурсы, которые можно валидно использовать как получатель видео.
- **Localization:** все новые или изменённые пользовательские строки обязательны в EN/RU/UK.
- **Accessibility:** touch, keyboard, D-pad, mouse и safe-area поведение не должны ухудшиться.
- **Widget precedence:** entry point, у которого уже есть явный target, не должен быть переопределён новой глобальной video destination-настройкой.
- **Feature inventory parity:** публичное описание функции должно быть обновлено так, чтобы shipped behaviour и текст больше не расходились.

### 3.3 Owner inputs

- **Confirmed by request:** нужен отдельный `Ресурс для записи видео` по аналогии с уже существующими camera/microphone selectors.
- **Confirmed by request:** settings part обязана покрывать обе ориентации.
- **Confirmed by request:** спецификация должна описывать полную реализацию, а не только UI-врезку.
- **Delegated by user:** selector размещается внутри `video options`, сразу под `Open recorded video in player`, и скрывается вместе с video options, когда видеозапись выключена.
- **Delegated by user:** fallback copy использует понятную device-level формулировку про папку `Movies` и не скрывает базовый сценарий записи в пригодный текущий ресурс.

---

## 4. Контекст текущей архитектуры

Текущее состояние выглядит так:

1. Playback settings уже содержат цельную capture-группу, где camera photos, video recording и microphone recording стоят рядом как родственные сценарии.
2. Для camera photos и microphone recordings уже существуют явные destination selectors с понятной fallback-моделью.
3. Для video recording уже существует master-toggle и отдельная child-настройка `open in player`, но selector ресурса-получателя отсутствует.
4. Видеозапись уже является shipped user-facing capability, поэтому отсутствие destination-настройки выглядит как незакрытая дыра в продуктовой модели, а не как заготовка под будущую фичу.
5. У части entry points target выбирается явно на самом entry point, а у части flow target определяется контекстом текущего ресурса или общими capture-настройками. Новая video destination-настройка не должна размывать эту границу.
6. Feature-copy уже обещает пользователю запись видео в ресурс, поэтому расширение destination-contract должно сохранить понятность базового сценария и одновременно убрать текущую недосказанность.

---

## 5. Предлагаемый подход

### 5.1 Добавить явный selector рядом с video block

Внутри существующего video recording блока должен появиться третий элемент семейства настроек - `Ресурс для записи видео`. По UX-модели он должен вести себя так же, как соседние selectors для микрофона и фото: показывать текущее выбранное значение или fallback-label и открывать single-choice выбор пригодного ресурса.

### 5.2 Дать видеозаписи отдельный destination-contract

У видеозаписи должна появиться собственная сохраняемая настройка ресурса-получателя. Изменение этого значения влияет только на video capture и не меняет поведение photo capture или microphone recording.

### 5.3 Сохранить non-breaking current-resource semantics

Чтобы не ломать уже выпущенную и документированную модель из S0371, нужно сохранить правило:

1. Если пользователь запускает запись видео из реального writable текущего ресурса, запись сохраняется туда же, как и сейчас.
2. Только если текущий контекст не даёт пригодного ресурса-получателя, применяется явно выбранный `video destination`, если он валиден.
3. Если selector пустой, устаревший или больше невалиден, поток детерминированно деградирует в публичный каталог `Movies` устройства.

Такой контракт даёт пользователю новую явную настройку, но не ломает основной сценарий `record video into the current resource`.

### 5.4 Отделить глобальный default от entry points с уже выбранным target

Если отдельный entry point уже заставляет пользователя выбрать target заранее и хранит его локально для себя, этот явный target должен оставаться сильнее новой глобальной video destination-настройки. Глобальный selector нужен для shared capture policy, а не для переопределения уже выбранного explicit target.

### 5.5 Синхронизировать UI между portrait и landscape

Обе ориентации должны показывать одинаковую структуру video-настроек: один и тот же заголовок блока, один и тот же selector, одинаковые visibility rules и одинаковые fallback-объяснения. Допускается только разная плотность компоновки, но не разный смысл.

### 5.6 Обновить пользовательский текст и feature inventory

После реализации пользователь должен понимать, что:

1. Запись видео по-прежнему может идти прямо в текущий ресурс, когда он пригоден.
2. У видеозаписи теперь есть собственная destination-настройка.
3. Пустое или невалидное значение selector-а имеет понятный fallback, а не ведёт к неочевидному поведению.

---

## 6. Research items

1. **Visibility rule для selector-а**
   - **Вопрос:** показывать ли `Ресурс для записи видео` только при включённой видеозаписи или держать его видимым всегда внутри video block.
   - **Почему важно:** camera и microphone selectors сейчас живут с немного разной gating-моделью, и для video нужен единый осознанный выбор.
   - **Статус:** Resolved - selector живёт внутри `video options` и скрывается вместе с ними, чтобы video block повторял gating camera options и не раздувал отключённое состояние.

2. **Fallback copy**
   - **Вопрос:** как именно объяснять fallback пользователю, если основная модель двуслойная: сначала текущий пригодный ресурс, иначе выбранный video destination, иначе `Movies`.
   - **Почему важно:** слишком короткий текст скроет важную часть контракта, а слишком длинный перегрузит settings surface.
   - **Статус:** Resolved - primary label сообщает device fallback (`Movies`), а полный non-breaking contract фиксируется в spec, feature docs и runtime routing без перегрузки settings copy.

3. **Invalid selected target UX**
   - **Вопрос:** при недоступном ранее выбранном target-е значение должно очищаться автоматически, показывать warning-state или просто молча уходить в fallback.
   - **Почему важно:** пользователь должен понимать, почему файл ушёл не туда, куда он помнил.
   - **Статус:** Resolved - для parity с camera/microphone selectors stale target не блокирует flow; UI показывает fallback label, routing игнорирует invalid target и не делает auto-clear записи настройки.

4. **Eligibility filter parity**
   - **Вопрос:** selector для видео должен использовать ровно тот же фильтр пригодных ресурсов, что и camera/microphone selectors, или для видео нужен отдельный более узкий фильтр.
   - **Почему важно:** поддержка удалённых writable resources полезна, но нельзя показывать target-ы, для которых video save-path фактически нестабилен.
   - **Статус:** Resolved - selector использует тот же eligibility filter, что и соседние capture selectors: writable, non-virtual resources.

5. **Feature-copy wording**
   - **Вопрос:** как сформулировать user-facing описание новой video destination-модели так, чтобы не противоречить уже известной пользователю идее `в текущий ресурс`.
   - **Почему важно:** shipped docs и settings copy должны описывать одно и то же поведение.
   - **Статус:** Resolved - docs копия явно говорит, что запись по-прежнему идёт в пригодный текущий ресурс, а global `Video recordings destination` используется как fallback/default when current target is not usable.

---

## 7. Риски

1. Если реализация просто переиспользует camera destination вместо отдельного video destination, пользователь получит скрытую cross-capture связанность и непредсказуемое место сохранения.
2. Если новый selector изменит уже выпущенную модель `save into current resource` для обычных writable ресурсов, это будет регрессией относительно S0371.
3. Если fallback-copy не объяснит двухслойную модель достаточно ясно, пользователь не поймёт, почему одна запись ушла в текущую папку, а другая - в `Movies`.
4. Если portrait и landscape получат разный placement или gating, Settings снова разойдутся по смыслу.
5. Если глобальная video destination-настройка начнёт переопределять entry points с уже выбранным target, сломается предсказуемость widget и shortcut surfaces.

---

## 8. Влияние на пользователя (feature inventory)

Это новое user-facing уточнение уже существующей shipped video feature. После реализации потребуется обновить feature inventory и related help-copy так, чтобы они явно отражали:

1. наличие отдельной настройки `Ресурс для записи видео`;
2. сохранение базового сценария записи в пригодный текущий ресурс;
3. fallback в `Movies`, если selector не задан или невалиден и текущий контекст непригоден.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Video recording получает собственную destination-настройку**

- **Решение:** у видеозаписи появляется отдельный selector ресурса-получателя.
- **Альтернативы:** продолжать неявно наследовать photo destination; не добавлять selector вовсе.
- **Почему:** video уже является самостоятельной shipped функцией и должен иметь собственный понятный destination-contract.

**ADR-2: Пригодный текущий ресурс остаётся первичным target-ом**

- **Решение:** когда запись запускается из реально пригодного writable ресурса, результат сохраняется в него же.
- **Альтернативы:** всегда приоритизировать новый global selector; всегда писать в системный media-каталог независимо от контекста.
- **Почему:** это сохраняет non-breaking поведение S0371 и не ломает базовую пользовательскую модель `записать видео прямо сюда`.

**ADR-3: Пустой или невалидный selector для видео деградирует в `Movies`**

- **Решение:** при отсутствии пригодного текущего ресурса и валидного user-selected target-а используется публичный каталог `Movies` устройства.
- **Альтернативы:** каталог камеры, `Downloads`, блокировка записи до ручного выбора ресурса.
- **Почему:** `Movies` лучше соответствует типу создаваемого media-result и не смешивает видео с photo-only или generic-download fallback-ами.

**ADR-4: Explicit target entry points сильнее global default**

- **Решение:** если конкретный entry point уже хранит свой явный target, именно он определяет место сохранения.
- **Альтернативы:** всегда навязывать global video destination поверх любого entry point.
- **Почему:** пользовательский explicit choice должен иметь приоритет над общей настройкой.

**ADR-5: Portrait и landscape остаются семантически идентичны**

- **Решение:** обе ориентации показывают одинаковый video settings contract.
- **Альтернативы:** orientation-specific layout logic с разным составом или gating.
- **Почему:** settings IA не должна меняться только из-за поворота экрана.

---

## 10. Связи с другими спеками

- `S0367` ввёл playback-side capture grouping и destination-contract для microphone recordings и camera photos.
- `S0371` оформил video recording как user-facing capability, но без отдельного destination-selector-а.
- `S0375` завершает недостающий video destination-contract и не должен заново открывать уже согласованные части `S0371`, кроме тех мест, где нужно развязать video routing от photo-oriented поведения.

---

## 11. Критерии готовности (strategic-level)

1. В Playback settings появляется отдельный `Video recordings destination` surface и в portrait, и в landscape.
2. Выбор ресурса для видеозаписи сохраняется, отображается человеку понятным label-ом и может быть очищен обратно в fallback.
3. Маршрутизация видеозаписи перестаёт зависеть от destination-настройки фото.
4. Запись из пригодного текущего ресурса по-прежнему сохраняется в него же.
5. Если текущий контекст непригоден, используется валидный video destination, а при его отсутствии или невалидности - `Movies`.
6. Existing open-in-player contract остаётся рабочим, а editor handoff по-прежнему не появляется.
7. Entry points с заранее выбранным explicit target-ом не ломаются и не переопределяются новой глобальной настройкой.
8. Все новые или изменённые пользовательские строки проходят EN/RU/UK parity.
9. Feature inventory и related help-copy синхронизированы с shipped behaviour.
10. Validation покрывает минимум один локальный сценарий, один сценарий с очищенным selector-ом и один сценарий с non-current target-ом.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: `PLAN/S0375_video-recording-destination-resource/INDEX.md`.

Следующий шаг: реализация по фазам tactical plan и затем `/spec-check S0375`.

## Last Audit

**Date:** 2026-06-07
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 20 · WARN 0 · FAIL 0 · MANUAL 11 · EXEMPT 1
**Exemptions:** Phase 01 line-budget check for `AppSettings.kt` is excluded from the verdict: current size is 262 lines, but the pre-S0375 baseline already exceeded the tactical `≤ 220` budget (248 lines before this diff).

### Manual / on-device

- [ ] Confirm portrait Playback settings shows `Video recordings destination` under video options and hides it when video recording is disabled.
- [ ] Confirm landscape Playback settings shows the same row, order, fallback copy, and safe-area behaviour.
- [ ] Confirm the picker exposes only writable, non-virtual resources and remains reachable via touch, keyboard, D-pad, and mouse.
- [ ] Confirm selecting a destination updates the label and survives reopening Settings.
- [ ] Confirm clearing the selector restores the documented `Movies` fallback label.
- [ ] Confirm recording from a usable current resource still saves into that current resource.
- [ ] Confirm recording from a non-usable current context redirects to the configured video destination.
- [ ] Confirm a stale or invalid configured destination silently degrades to the device `Movies` folder.
- [ ] Confirm `Open recorded video in player` still opens the saved clip in the player and never hands off to the editor.
- [ ] Confirm entry points with an explicit preselected target keep their target and ignore the global fallback selector.
- [ ] Confirm at least one local, one cleared-selector, and one non-current-target recording scenario on device.