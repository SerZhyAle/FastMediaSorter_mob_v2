# Стратегическая спецификация: Ad-hoc — Player Lifecycle Cancellation Resilience

**Status:** Verified
<!-- auto-approved by /spec-all — 2026-04-26 -->
**Date:** 2026-04-26
**Tier:** 2 — Easy
**Roadmap entry:** Ad-hoc — запрос пользователя 2026-04-26 (дополнительный анализ Quest 3 лога: `VideoPlayerManager: Failed to play video` с `JobCancellationException` при lifecycle switch)
**Tactical spec:** `PLAN/spec_player-lifecycle-cancellation/` (будет создан через `/spec-tech`)

> **Scope of this document:** STRATEGIC. Цели, ограничения, риски и направление решения. Без детальной пошаговой реализации.

---

## 1. Проблема

В логе есть отдельный playback failure, где менеджер воспроизведения пишет `Failed to play video`, а рядом виден `JobCancellationException` в момент уничтожения VR-активности. Этот сигнал возникает раньше, чем поздний SMB watchdog failure из другой проблемы, и выглядит как некорректная трактовка ожидаемой coroutine cancellation как полноценной playback error.

Пользовательский риск здесь не только в одном шумном логе. Когда lifecycle cancellation и реальная playback failure смешаны в один error path, приложение может показывать ложные ошибки, сбрасывать состояние слишком агрессивно или затруднять диагностику настоящих причин сбоев.

---

## 2. Цели

1. Ожидаемая lifecycle/coroutine cancellation при activity destroy, file switch или player release не логируется как полноценная playback error.
2. Реальные playback failures остаются отчётливо видимыми и не маскируются cancellation noise.
3. Пользовательский error UI не показывается для штатной отмены работы при уничтожении activity или смене контекста воспроизведения.
4. Player lifecycle имеет явный contract: cancel, release, re-open paths различаются семантически.

Non-goals:

- Исправление SMB/network playback failures как таковых.
- Глобальный refactor всего player stack.
- Изменение VR routing или OpenXR session lifecycle beyond what is needed to classify cancellation correctly.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. В логах cancellation должна уходить на debug/info уровень, а не на error.
2. Если cancel произошёл как часть нормального lifecycle transition, пользователь не должен видеть toast/dialog «Failed to play video».
3. Диагностика должна отделять `expected cancellation`, `user-aborted transition` и `real playback failure`.

### 3.2 Жёсткие ограничения

- **Flavor:** общий player path; затрагивает `standard`, `lite`, `legacy`, `vr`.
- **API level:** без Android API fork'ов.
- **Wear OS:** не затрагивается.
- **Архитектура:** lifecycle/cancellation semantics должны жить в player manager/helper layer, а не размазываться по UI.
- **Логирование:** `JobCancellationException` сам по себе не должен поднимать error severity без дополнительного сигнала реального сбоя.

---

## 4. Контекст текущей архитектуры

Запуск playback выполняется в coroutine path, который после routing/network/local decision переходит к конкретному media-source setup. Внешний catch на этом пути ловит `Exception` и на любой такой случай пишет `Failed to play video`, выключает buffering и показывает пользовательскую ошибку.

Такой generic catch не различает `JobCancellationException` и другие ожидаемые cancellation cases от настоящих playback failures. Архитектурный дефект в том, что семантика lifecycle cancellation теряется раньше, чем доходит до error-reporting слоя.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

#### Столп A — Cancellation Classification

Playback path получает явное различение `expected cancellation` и `unexpected failure`. `JobCancellationException` и соседние lifecycle-driven cancel cases не идут в пользовательский error path по умолчанию.

#### Столп Б — Error Reporting Contract

Error-reporting слой получает только те ошибки, которые действительно означают failed playback attempt. Cancellation становится отдельным outcome, а не error.

#### Столп В — Lifecycle-aware Logging

Логирование разделяет как минимум три категории: normal cancel during release/destroy, retryable playback failure, fatal playback failure. Это делает последующую диагностику точной.

### 5.2 Потоки данных и событий

```text
playback request coroutine
    ↓
play source setup / lifecycle transition
    ├─ expected cancel → cancellation outcome → debug/info log only
    └─ real exception → playback failure outcome → error log + user-facing error if appropriate
```

### 5.3 Точки расширяемости

- Та же outcome-model может использоваться для других manager paths, где cancellation является штатной частью UX.
- В будущем можно агрегировать telemetry по cancellation vs real failures отдельно.

---

## 6. Открытые вопросы / Research items

1. **Какие cancellation cases считать ожидаемыми?**
   - **Решение:** любой `CancellationException` (базовый тип для `JobCancellationException`). Wrapped interruption/abort cases на protocol boundaries поднимаются внутри того же coroutine и попадают в тот же catch — они классифицируются как реальные failures, если не являются `CancellationException`.
   - **Статус:** Resolved

2. **Нужно ли пользователю видеть мягкое сообщение при отменённом переходе?**
   - **Решение:** полностью silent path. UI-state сбрасывается lifecycle owner при destroy; отдельный UX signal не нужен и создаёт confusion.
   - **Статус:** Resolved

3. **Где должен жить outcome contract?**
   - **Решение:** локально в `VideoPlayerManager.playVideo()` строки 616-620 — единая точка входа для всех протоколов. Protocol helpers (`playSmb*`, `playSftp*` и др.) выполняются внутри того же coroutine scope и пробрасывают исключения вверх. Общий helper layer не нужен.
   - **Статус:** Resolved

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
| ---- | :---------: | ----------- | --------- |
| Слишком широкий cancellation filter проглотит реальную ошибку | Средняя | Сложнее диагностировать настоящий playback defect | Классифицировать только известные lifecycle-driven cases |
| UI перестанет показывать важную ошибку в пограничных переходах | Низкая | Плохая диагностируемость для пользователя | Разделить silent cancellation и retryable failure явно |
| Разные playback entry points сохранят разное поведение | Средняя | Непоследовательная ошибка/логика | Ввести единый error/cancellation contract |

---

## 8. Влияние на пользователя (docs/FEATURES)

No FEATURES doc update required.

Это исправление внутренней устойчивости lifecycle/error handling.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Cancellation — это отдельный outcome, а не разновидность playback error.**

- **Решение:** lifecycle-driven cancel cases отделяются от error path.
- **Альтернативы:** оставить общий catch и просто понизить уровень логирования.
- **Почему так:** пользовательский error path и state cleanup должны зависеть от семантики outcome, а не только от типа log message.

---

## 10. Связи с другими спеками

- Связана с network/player reliability, но не зависит от уже идущих VR stereo/input/control specs.
- Может частично соприкасаться с `spec_network-smb-pooling` только в части точной диагностики причин playback failure.

---

## 11. Критерии готовности (strategic-level)

1. `JobCancellationException` при нормальном lifecycle transition не приводит к `Failed to play video` error path.
2. Пользователь не видит ложную playback error при штатном destroy/switch сценарии.
3. Реальные playback failures по-прежнему отчётливо логируются и доходят до пользователя, когда это действительно нужно.

---

## 12. Ссылка на тактическую спецификацию

После утверждения этой страницы — перейти к `/spec-tech player-lifecycle-cancellation`, чтобы создать `PLAN/spec_player-lifecycle-cancellation/` с фазами реализации.
