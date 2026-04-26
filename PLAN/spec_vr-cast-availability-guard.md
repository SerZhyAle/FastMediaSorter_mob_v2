# Стратегическая спецификация: Ad-hoc — VR Cast Availability Guard

**Status:** Verified
<!-- auto-approved by /spec-all — 2026-04-26 -->
**Date:** 2026-04-26
**Tier:** 2 — Easy
**Roadmap entry:** Ad-hoc — запрос пользователя 2026-04-26 (дополнительный анализ Quest 3 лога: повторяющийся Cast SDK spam на устройствах без нужных Google Play Services)
**Tactical spec:** `PLAN/spec_vr-cast-availability-guard/` (будет создан через `/spec-tech`)

> **Scope of this document:** STRATEGIC. Цели, ограничения, риски и направление решения. Без детальной пошаговой реализации.

---

## 1. Проблема

На Quest 3 в течение одной VR-сессии десятки раз повторяется предупреждение о недоступности Cast SDK. Приложение пытается инициализировать cast-инфраструктуру на старте приложения и затем при инициализации player-сервисов, хотя Horizon OS не предоставляет полный набор сервисов Google Play, необходимых для стандартного Chromecast flow.

Проблема не ломает основной playback path, но создаёт постоянный шум в логах, расходует время на бессмысленные попытки инициализации и скрывает более важные сигналы в debug output. На VR-устройстве без рабочего Cast path это должно быть одноразовым capability decision, а не повторяющимся runtime-exception loop.

---

## 2. Цели

1. На Quest 3 и других VR-устройствах без рабочего Cast runtime cast-подсистема определяется как unavailable ровно один раз за процесс.
2. Повторные попытки инициализации Cast SDK в player lifecycle не выполняются, если платформа уже признана неподдерживаемой.
3. Лог содержит один понятный capability verdict вместо десятков одинаковых warning-сообщений.
4. На Android-устройствах с рабочими Google Play Services существующий Cast flow не деградирует.

Non-goals:

- Реализация альтернативного casting path для Quest / DLNA / Miracast / WebRTC.
- Полный рефактор всего player bootstrap.
- Изменение пользовательского VR UI, кроме возможного скрытия cast entry point там, где функция недоступна.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. На VR flavor желательно вообще не трогать cast bootstrap, если поддержка точно отсутствует.
2. Если жёсткое отключение по flavor слишком грубое, допустим runtime capability guard с кэшированием результата.
3. Пользовательский UI не должен показывать cast-команду там, где она заведомо не сработает.

### 3.2 Жёсткие ограничения

- **Flavor:** первичный эффект нужен для `vr`, но guard должен быть совместим с `standard`, `lite`, `legacy`, если логика останется общей.
- **API level:** без Android API fork'ов; ключевая зависимость — наличие Google Play Services / Cast module runtime.
- **Wear OS:** не затрагивается.
- **Архитектура:** heavy logic остаётся в player helper / capability layer, не в Activity.
- **Логирование:** только `Timber`; одно capability-warning сообщение максимум один раз на процесс.

---

## 4. Контекст текущей архитектуры

Cast bootstrap происходит как минимум в двух местах: ранняя инициализация на старте приложения и отдельная инициализация player-side manager при поднятии player-сервисов. Оба пути используют try/catch и «graceful failure», но при этом не переводят платформу в устойчивое состояние `cast unavailable`.

В результате одно и то же устройство снова и снова пытается получить Cast runtime, получает одинаковую `ModuleUnavailableException` и пишет одинаковый warning. Архитектурный зазор в том, что исключение рассматривается как локальный сбой конкретного вызова, а не как capability verdict для текущей платформы.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

#### Столп A — Capability Guard

Вводится единая capability-проверка Cast availability. Она выполняется один раз, кэширует результат и предоставляет остальным компонентам бинарный verdict: `available` / `unavailable` / `unknown`.

#### Столп Б — Player Bootstrap Short-Circuit

Player bootstrap получает ранний выход: если cast capability уже определена как `unavailable`, player-side manager не инициализирует Cast SDK, не подписывается на сессии и не показывает useless warning.

#### Столп В — Platform-aware UX

Где пользовательский UI предлагает Cast, там логика опирается на тот же capability verdict. На unsupported-платформе либо команда скрыта, либо показывает краткое недоступно-сообщение без повторной инициализации SDK.

### 5.2 Потоки данных и событий

```text
App startup
    ↓
Cast capability probe
    ├─ available → normal Cast path
    └─ unavailable → cache verdict for process
                         ↓
                player bootstrap / menu actions
                         ↓
                short-circuit without Cast SDK init
```

### 5.3 Точки расширяемости

- Capability layer должна поддерживать не только VR flavor, но и любые будущие платформы без Play Services.
- В будущем тот же механизм может использоваться для других optional Google-services features.

---

## 6. Открытые вопросы / Research items

1. **Где хранить capability verdict?**
   - **Вопрос:** достаточно ли process-local singleton/cache, или нужен более явный capability service.
   - **Статус:** Resolved — `BuildConfig.SUPPORT_CAST` compile-time flag; runtime singleton не нужен, решение принимается на уровне сборки.

2. **Нужно ли скрывать UI-команду полностью?**
   - **Вопрос:** на VR flavor лучше убирать Cast entry point из UI или оставлять disabled state с пояснением.
   - **Статус:** Resolved — кнопка скрыта полностью через `BuildConfig.SUPPORT_CAST`; тот же паттерн что `SUPPORT_VR_PLAYER`.

3. **Жёсткий flavor guard vs runtime guard**
   - **Вопрос:** нужно ли просто отключить Cast в `vr` flavor, или всё же делать runtime capability detection, чтобы логика была платформенно корректной и переиспользуемой.
   - **Статус:** Resolved — выбран жёсткий flavor gate; vr/vrUnlicensed всегда без Google Play Services, runtime probe не даёт ценности.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
| ---- | :---------: | ----------- | --------- |
| Слишком грубый guard отключит рабочий Cast на части Android-устройств | Низкая | Регресс функциональности | Предпочесть capability probe над blind flavor cutoff |
| UI и bootstrap будут читать разные verdict sources | Средняя | Расхождение между видимостью кнопки и runtime поведением | Единый shared capability source |
| Warning исчезнет, но дорогостоящая инициализация останется | Средняя | Частичное решение | Критерии готовности должны проверять и отсутствие повторной init-attempt логики |

---

## 8. Влияние на пользователя (docs/FEATURES)

No FEATURES doc update required.

Это platform guard и уборка ложного функционального пути, а не новая пользовательская возможность.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Проблема решается как capability decision, а не как повторяющийся try/catch.**

- **Решение:** после первого отрицательного результата система кэширует `cast unavailable` и прекращает повторные SDK init attempts.
- **Альтернативы:** оставить текущий try/catch в каждом месте и просто понизить лог-уровень.
- **Почему так:** проблема не только в шуме лога, но и в повторном выполнении заведомо бесполезного runtime path.

---

## 10. Связи с другими спеками

- Связана с VR log cleanup и platform-specific hygiene, но не зависит от текущих VR rendering/input spec-фаз.
- Может быть выполнена независимо от `spec_vr-input-reliability` и других уже идущих VR-спек.

---

## 11. Критерии готовности (strategic-level)

1. На Quest 3 за процесс появляется не более одного сообщения о недоступности Cast runtime.
2. Player bootstrap не делает повторных попыток инициализировать Cast SDK после отрицательного capability verdict.
3. На supported Android-устройствах существующий Cast flow продолжает работать.

---

## 12. Ссылка на тактическую спецификацию

После утверждения этой страницы — перейти к `/spec-tech vr-cast-availability-guard`, чтобы создать `PLAN/spec_vr-cast-availability-guard/` с фазами реализации.
