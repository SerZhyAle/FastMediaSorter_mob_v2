# Спецификация (draft): S1135 - Дублирующиеся VR-коды в дефолтных биндингах (vr:19/vr:20)

**Ticket:** S1135
**Status:** Archived
**Priority:** 45
**Date:** 2026-07-20
**Tier:** 1 - Trivial

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-20 (исследование S1134, аудит `default_bindings.json`)

**Захвачено во время:** read-only исследование VR-кейбиндингов для S1134 - out-of-scope находка.

**Текст:**

Два VR-кода в шипнутых дефолтах назначены дважды каждый - настоящий конфликт биндингов, запечённый в `app_v2/src/main/assets/input/default_bindings.json`:

- `vr:19` -> `navigation.seek_micro_backward` (:206) И `vr.swipe_left` (:962).
- `vr:20` -> `navigation.seek_micro_forward` (:194) И `vr.swipe_right` (:974).

**Контекст / evidence:**

- Один и тот же raw XR-код связан с двумя разными командами -> `DetectConflictsUseCase` увидит конфликт на стоковой установке (вероятно ложный/нежелательный конфликт-бейдж из коробки).
- Не связано с симптомом S1134 (метки-строки VR) - отдельное решение: какая команда должна владеть каждым кодом.
- Evidence: `app_v2/src/main/assets/input/default_bindings.json:194,206,962,974`.

## 1. Проблема

Дефолтные биндинги содержат две пары команд, делящих один VR-код (19 и 20), что создаёт конфликт в `DetectConflictsUseCase` на чистой установке. Нужно решить, какая команда владеет каждым из кодов, и развести дубли (или переназначить `vr.swipe_left/right` на свободные коды).

## 2. Открытые вопросы

- ~~Какая команда должна владеть `vr:19` и `vr:20` - навигация (`seek_micro_*`) или VR-жест (`swipe_left/right`)?~~ **Решено (см. Quiz decisions):** `navigation.seek_micro_backward/forward` владеют `vr:19/vr:20`.
- ~~Есть ли свободные VR-коды для переназначения (код 18 не используется; проверить диапазон при работе).~~ **Неактуально:** переназначение не требуется - конфликт снимается удалением вестигиальных сидов `vr.swipe_left/right`.
- ~~Связано с общим вопросом S1134 о том, вестигиален ли VR-раздел кейбиндингов вообще.~~ **S1134 = Archived.** Свайп-команды остаются вестигиальными на уровне диспетчера (хендлера нет), но полное удаление раздела - вне объёма S1135.

### Решение (реализация)

- `navigation.seek_micro_backward` сохраняет `vr:19`, `navigation.seek_micro_forward` сохраняет `vr:20` в `app_v2/src/main/assets/input/default_bindings.json`.
- Из `vr.swipe_left` (`:962`) и `vr.swipe_right` (`:974`) удаляются дефолтные VR-триггеры `vr:19`/`vr:20` - остаются с пустым набором триггеров.
- Обоснование: у `seek_micro_*` есть реальный рантайм-хендлер (`GamepadInputManager`), у `vr.swipe_*` диспетчера нет; коды 19/20 в XR-таксономии = swipe-left/right, а семантика «свайп = seek» сохраняется. `seek_micro_*` имеют `flavor_gate: vr_only` без иных триггеров, поэтому именно они должны удержать биндинг.

### Quiz decisions (2026-07-23)

- Кто владеет `vr:19`/`vr:20` в дефолтах? → **seek_micro сохраняет коды, дефолты vr.swipe_left/right удаляются** (seek_micro имеет рантайм-хендлер; vr.swipe_* без диспетчера - вестигиальны; семантика свайпа сохранена).

---

## 3. Owner inputs (Approval gate)

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** none (S1134 - Archived, источник out-of-scope находки).
- **Data scope:** правка шипнутого ассета `app_v2/src/main/assets/input/default_bindings.json` - удаление дефолтных VR-триггеров `vr:19`/`vr:20` у вестигиальных `vr.swipe_left/right`; `navigation.seek_micro_backward/forward` сохраняют коды. Без изменения схемы, без Kotlin.

---

## Last Audit

**Date:** 2026-07-23
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 5 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] На чистой установке VR-флейвора конфликт-бейдж для `vr:19`/`vr:20` не появляется (косвенное следствие дедупа; статически гарантировано - `DetectConflictsUseCase` не найдёт повторного назначения). Требует Quest/XR-устройства для визуального подтверждения.

<!-- Checks: default_bindings.json exists (PASS); vr:19/vr:20 assigned exactly once each = seek_micro (PASS); vr.swipe_left/right vr triggers empty (PASS); JSON parses (PASS); dev-log entry present (PASS). §8 FEATURES absent - internal bugfix, no user-visible capability (EXEMPT). Zero Timber.d("S1135: tags (Implemented invariant). -->

