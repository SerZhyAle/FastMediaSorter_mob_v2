# Стратегическая спецификация: S0396 - Availability-контракт для онбординга

**Ticket:** S0396
**Status:** Archived
**Implemented date:** 2026-06-10
**Priority:** 50
**Date:** 2026-06-10
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - split S0395 (sign-off владельца 2026-06-10)
**Tactical spec:** `PLAN/S0396_welcome-availability-contract/` (будет создан через `/spec-tech`)
**Tactical plan:** `PLAN/S0396_welcome-availability-contract/INDEX.md`

> **Scope:** STRATEGIC. Ресёрч-база: `PLAN/S0395_welcome-screens-redesign-research/research/06__page4-functionality-toggles.md`, `09__flavor-matrix.md`.

---

## 1. Проблема

Видимость возможностей (OCR, перевод, VR, экран загружаемых элементов) сейчас вычисляется чтением флага сборки прямо в общем коде настроек - это долг Rule 15 и непригодно для welcome-страниц, которым нужны те же ответы «доступно ли». Единого рантайм-контракта доступности возможностей для онбординга нет.

## 2. Цели

1. Единый рантайм-интерфейс «доступна ли возможность X в этой сборке на этом устройстве» для OCR, перевода, VR и экрана элементов.
2. Реализации по source set'ам (паттерн `DeviceProfileAvailability`), без чтения флагов сборки в общем коде.
3. Существующее чтение `ENABLE_TRANSLATION` в общем коде настроек переведено на контракт (долг закрыт).

**Non-goals:** сами welcome-страницы (S0398/S0400); изменение состава возможностей.

## 3. Пожелания и ограничения

### 3.2 Жёсткие ограничения

- **Flavor:** все варианты; интерфейс в общем коде + flavor source sets по `dev/FLAVOR_DEVELOPMENT_RULES.md`.
- **Локализация:** новых строк нет.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** все варианты сборки; реализации в source set'ах по `dev/FLAVOR_DEVELOPMENT_RULES.md`.
- **Related tickets:** S0395 (ресёрч-база), S0398/S0400 (потребители), S0386 (доступность элементов).

## 4-5. Контекст и подход

Детали - в артефактах S0395 (06, 09): правила доступности OCR (флейвор + API≥26 + RAM≥3ГБ), перевода (флейвор + Play-установка/bundled), VR (только сборки с `src/vr`), кнопки элементов (есть хотя бы один применимый элемент).

## 6. Открытые вопросы / Research items

Открытых вопросов нет - закрыты ресёрчем S0395 (артефакты 06, 09).

## 7. Риски

Низкие: чистый рефакторинг видимости; риск - расхождение со старым поведением настроек, митигация - сравнение видимости до/после на всех флейворах.

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES.

## 9. ADR

ADR нет - паттерн `DeviceProfileAvailability` устоявшийся.

## 10. Связи с другими спеками

- S0395 (Implemented) - источник требований.
- S0398, S0400 - потребители контракта (создание страниц).
- Блокировок нет; стартует немедленно.

## 11. Критерии готовности (strategic-level)

1. Видимость OCR/перевода в настройках не изменилась ни в одном флейворе, но вычисляется через контракт.
2. В коде НАСТРОЕК (видимость строк OCR/перевода) не осталось чтений `ENABLE_TRANSLATION` - вычисление идёт через контракт. Функциональный гейтинг в плеерах/виджетах вне объёма S0396 (мигрирует попутно в потребляющих тикетах).

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0396`.

## Last Audit

**Date:** 2026-06-12
**Mode:** full
**Outcome:** Verified
**Counts:** PASS 24 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

All 4 tactical phases statically confirmed: CapabilityAvailability + @CompiledCapabilities contract, flavor @IntoSet contributors (ocrEnabled/translationEnabled/vrOnly), settings fragments inject + use it, BuildConfig.ENABLE_TRANSLATION removed from ui/settings. FEATURES exempt (§8 "Без изменений").
