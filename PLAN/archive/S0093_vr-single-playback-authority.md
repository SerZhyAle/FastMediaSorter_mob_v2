# Стратегическая спецификация: S0093 — VR single playback authority

**Ticket:** S0093
**Status:** Verified
**Implemented date:** 2026-05-05
**Priority:** 80
**Date:** 2026-05-05
**Tier:** 3 — High-impact VR architecture
**Roadmap entry:** Ad-hoc — field logs 2026-05-05, системный VR playback failure
**Tactical spec:** [`PLAN/S0093_vr-single-playback-authority/INDEX.md`](S0093_vr-single-playback-authority/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

VR route selection часто принимает корректное решение (`IMMERSIVE_VIDEO`), но реальное playback/render state остаётся рассинхронизированным. Логи многократно фиксируют `stereo coherence MISMATCH coordinator=MONO ...`, после чего immersive session деградирует в выход обратно в flat player. Дополнительно обнаружено архитектурное расщепление playback authority: часть команд уходит в inherited standard player stack, часть — в отдельный VR playback backend.

---

## 2. Цели

1. В VR остаётся один источник истины по playback state, seek, audio tracks и stereo mode.
2. Route/state/render pipeline использует согласованную stereo-state модель.
3. Командный роутер больше не делит transport/control команды между разными backend.
4. Исправление закрывает системный класс VR playback failures, а не один частный формат.

**Non-goals:**

- Quick fix одного конкретного медиафайла.
- Локальная косметическая правка HUD/panel UX без исправления playback authority.
- Выпуск VR rewrite в обход core Standard blockers.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. VR нужно рассматривать как отдельный поток работ, не блокирующий выпуск Standard.
2. Новая архитектура должна быть объяснима и тестируема, без полумёртвых параллельных backend.

### 3.2 Жёсткие ограничения

- **Flavor:** только `vr` / `vrUnlicensed`.
- **API / device surface:** Quest-class XR runtime, arm64-v8a.
- **Производительность:** нельзя потерять плавность immersive rendering.
- **Совместимость:** пользовательские hotkeys / panel controls должны остаться доступны после migration.

---

## 4. Контекст текущей архитектуры

Текущий VR host наследует standard player host и тем самым тянет в VR весь legacy playback stack. Одновременно в VR DI присутствует отдельный playback abstraction, задекларированный как основной VR backend. Это создаёт конфликт модели владения transport state. Route decision helper при этом выглядит локально корректным и покрыт unit tests, что указывает не на ошибку выбора маршрута как такового, а на проблему downstream orchestration.

---

## 5. Предлагаемый подход

Выделить отдельную фазу архитектурной консолидации: определить единственный playback authority для VR, затем привести command routing, track selection, seek, stereo-state propagation и render layer binding к одной цепочке управления. Route selection остаётся отдельной ответственностью и не смешивается с transport execution.

Стартовый исполняемый срез закрепляет canonical transport authority за уже используемым shared ExoPlayer path (`VideoPlayerManager` / inherited player stack), потому что VR render/session helpers уже читают и настраивают именно этот player. Dedicated VR engine выносится в отдельную фазу interface rationalization вместо частичного параллельного использования.

### 5.1 Основные столпы

**Single playback authority.** Весь transport/control state в VR должен принадлежать одному backend.

**Stereo-state unification.** Coordinator, activity, renderer и layer descriptor читают одну и ту же модель состояния.

**Command parity.** Seek, speed, audio tracks, subtitle tracks и overlay controls обращаются к одному слою исполнения.

---

## 6. Открытые вопросы / Research items

1. **Resolved:** canonical backend для VR закреплён за shared `VideoPlayerManager` / ExoPlayer path.
2. **Resolved:** выбран прямой migration path без адаптера: command authority consolidation → activity playback facade → удаление obsolete engine path.
3. Какие минимальные on-device regression suites обязательны до перевода VR в `Verified`?

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Слишком большой объём для quick-fix цикла | Высокая | Затягивание release window | Держать `S0093` отдельно от `S0091/S0092` |
| Попытка частичного локального fix только усилит split-brain architecture | Высокая | Ещё более трудноотлавливаемые regressions | Делать только через явную стратегическую консолидацию |

---

## 8. Влияние на пользователя (docs/FEATURES)

Потенциально user-facing, но обновление `docs/FEATURES*.md` требуется только после реальной реализации, не на этапе стратегической спеки.

---

## 9. Архитектурные решения (ADR)

**ADR-1:** canonical VR playback authority — shared `VideoPlayerManager` / ExoPlayer path.

**ADR-2:** parallel `VrPlaybackEngine` transport path удалён как неиспользуемый и конкурирующий с реальным VR render/session pipeline.

---

## 10. Связи с другими спеками

- `S0018`, `S0026`, `S0033`, `S0038`, `S0078`, `S0080` — предыдущие VR фиксы и decomposition work.

---

## 11. Критерии готовности (strategic-level)

1. Определён canonical playback authority.
2. Сформирован тактический план миграции.
3. Стратегия не блокирует core Standard fixes.

## Last Audit

**Date:** 2026-05-06
**Mode:** full
**Flags:** —
**Outcome:** Verified
**Counts:** PASS 22 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 0

### Manual / on-device

- [ ] On-device VR regression: confirm immersive video playback, seek, audio track selection on Quest 3 after `VrPlaybackEngine` removal (§6.3 unresolved — on-device regression suite still undefined).