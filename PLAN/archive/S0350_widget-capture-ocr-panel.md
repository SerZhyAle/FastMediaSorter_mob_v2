# Стратегическая спецификация: S0350 - Виджет «Панель Захвата и OCR»

**Ticket:** S0350
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-04
**Tier:** 3 - Moderate (ad-hoc)
**Parent ticket:** S0348 (home-widget-icon-refresh) - выделено как суб-спецификация по решению владельца 2026-06-04.

**Tactical plan:** `PLAN/S0350_widget-capture-ocr-panel/INDEX.md` (будет создан через `/spec-tech`)

> **Scope:** STRATEGIC. Capture action panel for existing widget flows. Audio action is deferred until S0349 provides a standalone recording entry point.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - implementation via `/spec-all S0350`.
- **Goal / expected outcome:** Provided by user - реализовать виджет-панель Capture & OCR для быстрых capture actions.
- **Local anchor:** Provided by user - `PLAN/S0350_widget-capture-ocr-panel.md`.
- **Scope boundaries / forbidden areas:** Delegated by user - `/spec-all` auto-approval; не трогать read-only зоны, не реализовывать S0349 внутри S0350, не добавлять мёртвую audio-кнопку.
- **Done / success signal:** Delegated by user - `/spec-all` auto-approval; widget provider, layout, provider info, manifest entry and EN/RU/UK strings exist; available buttons launch Camera Photos and Camera OCR flows; audio action remains hidden until S0349 is implemented.
- **Autonomy rule:** Delegated by user - `/spec-all` auto-approval; agent may decide reversible widget layout details and record assumptions.
- **UI decisions / delegation:** Delegated by user - `/spec-all` auto-approval; unavailable actions are hidden, not disabled; `2x1` and `4x1` use the same horizontal action row and resize without showing dead controls.

Auto-approved by `/spec-all` on 2026-06-04 because remaining UI decisions are reversible and the only missing flow is already isolated in S0349.

---

## 1. Проблема

Действия захвата (фото, аудиозапись, Camera OCR) - частые, но разнесены по разным точкам входа. Один компактный ряд иконок ускорил бы запуск любого из них.

---

## 2. Цели

1. Добавить виджет-панель `2x1` / `4x1` с горизонтальным рядом action-иконок.
2. Иконки первой реализации: Camera Photos, Camera OCR.
3. Каждая иконка запускает соответствующий существующий flow в одно касание.
4. Стиль иконок согласован с icon-style языком первой волны S0348.
5. Quick Audio Recorder подключается после S0349; до этого audio action не отображается.

**Non-goals:**

- Не показывать состояние/результаты внутри панели (stateless action group, S0348 §6.4).
- Не превращать панель в OCR-историю.
- Не реализовывать standalone audio recording service внутри S0350.

---

## 3. Ограничения

- **Flavor:** каждая иконка видна только там, где доступен её flow (images / audio / translation). Недоступные action'ы скрываются, панель не показывает мёртвые кнопки.
- **Размеры:** `2x1` и `4x1` через resizable provider; на узком размере не обрезать иконки.
- **Локализация:** EN/RU/UK для label, description, accessibility каждого action'а.
- **Flavor isolation:** Rule 15 - capability-зависимая видимость без `BuildConfig.*` в `src/main`.
- **S0349 dependency:** audio button is not present until S0349 provides an approved, standalone Quick Audio Recorder entry point.

### 3.3 Owner inputs (Approval gate)

- **Approval signal:** owner invoked `/spec-all S0350` on 2026-06-04 after changing `/spec-all` rules to allow auto-approval when no blocking ambiguity remains.
- **Autonomy:** agent may decide reversible implementation details with explicit assumptions and must stop only for unsafe or contradictory decisions.
- **Unavailable actions:** hidden, not disabled.
- **Panel sizing:** `2x1` and `4x1` use the same horizontal action row; the row shows available actions without dead placeholders.
- **Audio action:** deferred to S0349 until standalone Quick Audio Recorder flow exists.
- **Related tickets:** S0348, S0349, S0320.

---

## 4. Research decisions

1. **Unavailable actions**
   - **Вопрос:** скрыть кнопку, disable или ужать ряд.
   - **Решение:** скрывать недоступные actions; ряд перераспределяет доступные кнопки.
   - **Статус:** Resolved.

2. **Action set for `2x1` and `4x1`**
   - **Вопрос:** различать набор кнопок по размеру или держать один contract.
   - **Решение:** один горизонтальный action row для обоих размеров. `2x1` показывает доступные приоритетные actions без подписи; `4x1` не добавляет недоступные actions только ради заполнения.
   - **Статус:** Resolved.

3. **Quick Audio Recorder dependency**
   - **Вопрос:** можно ли подключить аудиозапись сейчас.
   - **Решение:** текущая запись привязана к Browse/current resource, а standalone widget entry point определяется S0349. S0350 не создаёт мёртвую кнопку и не реализует S0349.
   - **Статус:** Resolved as deferred dependency.

---

## 5. Критерии готовности

1. Виджет «Панель Захвата и OCR» добавляется в размерах `2x1` / `4x1`.
2. Кнопки Camera Photos и Camera OCR работают и ведут в существующие flow.
3. Недоступные action'ы не показываются мёртвыми кнопками.
4. Quick Audio Recorder остаётся явной зависимостью S0349 и не отображается до появления standalone flow.

---

## 6. Связи

- **S0348** - parent; icon-style, picker registry, pinning.
- **S0349** - Quick Audio Recorder action.
- **S0320** - Camera OCR translate flow.

## Last Audit

**Date:** 2026-06-04
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 52 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 1

### Manual / on-device

- [ ] Optional launcher smoke: add the panel widget on a device/emulator and tap Camera Photos / Camera OCR.
- [x] Static and build evidence passed: strings EN/RU/UK, provider/layout/provider-info/manifest checks, catalog/dev-log checks, standard/lite/photos debug builds.
