# Стратегическая спецификация: Ad-hoc — VR XR Cold Start Latency

**Status:** BlockNeedUserTest
<!-- auto-approved by /spec-all — 2026-04-27 -->
<!-- BlockNeedUserTest set by /spec-all — 2026-04-29: Phase 02 measurement requires Quest 3 + adb logcat; pipeline cannot proceed without measurement data -->
**Date:** 2026-04-26
**Tier:** 2 — Easy
**Roadmap entry:** Ad-hoc — запрос пользователя 2026-04-26 (из Quest 3 VR problem list: P3-5 — первый запуск XR-сессии ждёт 1093ms и до этого нигде не был зафиксирован).
**Tactical plan:** `PLAN/S0014_vr-xr-cold-start/INDEX.md`

> **Scope of this document:** STRATEGIC. Фиксирует пропущенную проблему cold-start latency как отдельный optimization/backlog item. Не означает автоматический немедленный implementation commitment.

---

## 1. Проблема

Первый вход в XR-сессию на Quest 3 заметно медленнее последующих: лог показывает `BaseActivity.setupViews[VrPlayerActivity]: START (waited 1093ms for first frame)`, тогда как повторные запуски укладываются примерно в 144-201ms. Пользователь видит медленный cold start и воспринимает это как «тормозной» вход в VR, хотя дальнейшая работа может быть нормальной.

Сейчас эта проблема присутствует только в [PLAN/spec-list-vr-problems.md](PLAN/spec-list-vr-problems.md) как observation P3-5 и никак не привязана к отдельному scope. Если её не зафиксировать отдельно, она выпадет между VR input, immersive toggle и прочими спеками, потому что не является их прямым подмодулем.

---

## 2. Цели

1. Зафиксировать cold-start XR latency как отдельную задачу с owner'ом и критериями решения.
2. Разделить измерение проблемы на этапы: Android first frame wait, XR thread init, EGL creation, native OpenXR init, GL resource setup.
3. Подготовить почву для оптимизации без смешивания этой задачи с input, HUD или stereo-routing спеками.
4. Если после исследования окажется, что задержка приемлема и не стоит сложности оптимизации, документ должен позволить формально перевести задачу в backlog / won't-fix-now вместо потери в воздухе.

Non-goals:

- Изменение пользовательских VR controls или маршрутизации media.
- Оптимизация steady-state rendering FPS.
- Fisheye, OU/TAB, hand tracking, HUD и другие функциональные VR-баги из текущей сессии.
- Любые Android UI/layout изменения вне startup path.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Проблема не должна оставаться незадокументированной: либо отдельная спека, либо явный backlog/won't-fix статус.
2. Не смешивать cold-start optimization с текущими рабочими спеками по VR input/HUD, чтобы не раздувать их scope.
3. Сначала измерения и декомпозиция задержки, потом решение о приоритете реализации.

### 3.2 Жёсткие ограничения

- **Flavor:** только `vr`.
- **API level:** практический таргет — Quest / Android 14, но без Android API fork'ов внутри приложения.
- **Wear OS:** не затрагивается.
- **Архитектура:** heavy init logic остаётся в `OpenXrSessionManager` и native OpenXR bridge; не возвращать её в Activity.
- **Приоритет:** optimization task, а не crash/data-loss/security blocker.

---

## 4. Контекст текущей архитектуры

`BaseActivity` откладывает `setupViews()` через `binding.root.post`, чтобы дать первому Android frame отрисоваться быстрее. Для `VrPlayerActivity` это означает, что логируемое ожидание первого frame включает не только XR init, но и задержку до выполнения `setupViews()` после `onCreate`.

Далее cold-start path продолжается в `VrPlayerActivity.onResume()`, где запускается `OpenXrSessionManager.initialize()` на IO-потоке. Сам `OpenXrSessionManager` создаёт render thread, поднимает EGL context, вызывает `OpenXrNative.nativeInitialize()`, затем только после успешного native init вызывает `onSessionReady`, где `VrPlayerActivity.initializeVrRenderPipeline()` последовательно создаёт/переинициализирует bridge, stereo renderer, photo renderer и HUD swapchain.

Иными словами, текущий cold-start — это не одна операция, а цепочка из нескольких последовательных фаз. Пока эта цепочка не измерена по частям, нельзя честно решить, стоит ли оптимизировать её сейчас или формально отложить.

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

#### Столп A — Разбивка latency по стадиям

Нужно получить отдельные метрики для:

1. `onCreate` → `BaseActivity.setupViews START`;
2. `startXrInitialization` → `OpenXrSessionManager.initialize begin`;
3. EGL create;
4. `nativeInitialize`;
5. `onSessionReady`;
6. `initializeVrRenderPipeline` и первая usable frame.

Без этого любая оптимизация будет угадыванием.

#### Столп Б — Выделение cold-start-only work из steady-state init

После замера нужно определить, какие шаги действительно обязаны происходить до первой usable XR frame, а какие можно:

- лениво инициализировать;
- прогреть заранее;
- переиспользовать между сессиями;
- переносить после появления первой usable картинки.

#### Столп В — Формальный выход: optimize now или backlog

Эта спека должна завершиться одним из двух валидных исходов:

1. **Optimize now:** есть дешёвая и безопасная оптимизация с измеримым выигрышем.
2. **Won't fix now / backlog:** задержка признана допустимой относительно сложности/риска, но задача остаётся зафиксированной и не теряется.

### 5.2 Потоки данных и событий

```text
VrPlayerActivity.onCreate
    ↓
BaseActivity binding.root.post
    ↓
setupViews()
    ↓
VrPlayerActivity.onResume
    ↓
startXrInitialization()
    ↓
OpenXrSessionManager.initialize()
    ↓
EGL create → nativeInitialize → onSessionReady
    ↓
initializeVrRenderPipeline()
    ↓
first usable immersive frame
```

### 5.3 Точки расширяемости

- Если будет решено добавлять startup telemetry для VR, результаты этой спеки можно использовать как основу для постоянных perf-маркеров.
- Подход может переиспользоваться для warm resume / session re-entry, но это не часть текущего scope.

---

## 6. Открытые вопросы / Research items

1. **Что именно составляет 1093ms?**
   - **Вопрос:** сколько из этого времени уходит на Android first-frame defer, сколько на EGL/native OpenXR init, сколько на GL resource setup.
   - **Статус:** BlockNeedUserTest

2. **Нужна ли вообще оптимизация?**
   - **Вопрос:** 1.1s cold start для первой XR-сессии — это неприемлемо для текущего UX или нормальная цена первого входа на Quest 3?
   - **Статус:** BlockNeedUserTest

3. **Можно ли безопасно прогревать XR/GL раньше?**
   - **Вопрос:** допустимо ли заранее создавать часть ресурсов до явного входа в иммерсив, не ломая lifecycle и не тратя батарею зря.
   - **Статус:** BlockNeedUserTest

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
| ---- | :---------: | ----------- | --------- |
| Оптимизация старта усложнит lifecycle и породит race conditions | Средняя | Нестабильный XR start/stop | Сначала измерения, потом только локальные изменения |
| Выигрыш окажется минимальным | Высокая | Потраченное время без заметного UX-эффекта | Допустить outcome `Won't fix now` после исследования |
| Перенос инициализации позже ухудшит first usable frame | Средняя | Пользователь увидит ранний, но пустой/неполный кадр | Разделять «first frame» и «first usable frame» в метриках |

---

## 8. Влияние на пользователя (docs/FEATURES)

No FEATURES doc update required.

Это optimization/backlog item без новой пользовательской функции.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Cold-start latency получает отдельную spec, а не растворяется внутри input/toggle specs.**

- **Решение:** создать самостоятельную стратегическую спецификацию.
- **Альтернативы:** дописать пару строк в `spec_vr-input-reliability.md`; оставить только в problem list.
- **Почему так:** проблема orthogonal к input/HUD/routing и требует собственных perf-метрик и trade-off decision.

**ADR-2: Первым deliverable является измерение, а не код.**

- **Решение:** tactical work начинается с instrumentation и stage breakdown.
- **Альтернативы:** сразу пытаться оптимизировать EGL/native init по ощущениям.
- **Почему так:** без stage breakdown невозможно понять root cause и оценить ROI.

---

## 10. Связи с другими спеками

- [PLAN/spec-list-vr-problems.md](PLAN/spec-list-vr-problems.md) — источник P3-5.
- [PLAN/spec_vr-input-reliability.md](PLAN/spec_vr-input-reliability.md) — смежная VR-спека, но cold-start не входит в её scope.
- [PLAN/spec_vr-immersive-toggle.md](PLAN/spec_vr-immersive-toggle.md) — не связана напрямую, кроме общего VR lifecycle контекста.

---

## 11. Критерии готовности (strategic-level)

1. Проблема P3-5 больше не является «неучтённой» — у неё есть отдельный spec owner и documented scope.
2. Cold-start latency разложена на измеримые стадии.
3. По итогам tactical research принято явное решение: optimization implementation или backlog / won't-fix-now.
4. Если optimization выполняется, успех измеряется не логами «кажется быстрее», а stage-by-stage метриками до/после.

---

## 12. Ссылка на тактическую спецификацию

После утверждения этой страницы — либо перейти к `/spec-tech vr-xr-cold-start` для research/optimization phases, либо формально пометить эту стратегическую спецификацию как backlog / won't-fix-now с датой и причиной.
