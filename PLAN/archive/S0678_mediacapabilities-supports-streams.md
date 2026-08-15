# Спецификация: S0678 - Гейт трансляций через контракт capability, без прямого BuildConfig в потребителе

**Ticket:** S0678
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-25
**Tier:** 2 - Minor (ad-hoc)

<!-- auto-approved by /spec-all - 2026-06-25 -->

> **Scope:** COMPACT (Simple path). Поведение-сохраняющий рефактор flavor-изоляции.

---

## Цель

`MainActivity` читал `BuildConfig.SUPPORT_STREAMS` напрямую для гейта пункта меню трансляций, минуя контракт capability, хотя такой контракт уже существует: `CapabilityAvailability.isStreamsAvailable()` (тот же паттерн, что `isOcrAvailable`/`isTranslationAvailable`/`isPersistentAudioPlaybackAvailable`). Направить потребителя через контракт.

**Корректировка захваченного материала:** §0 предлагал добавить `supportsStreams` в `MediaCapabilities` (data class). Это неверный дом - создало бы второй source of truth рядом с уже существующим `CapabilityAvailability.isStreamsAvailable()`. Доступность трансляций (опциональная фича) семантически живёт в `CapabilityAvailability`, не в `MediaCapabilities` (медиа/хранилище). Чтение флага внутри самого контракта остаётся - оно благословлено (как у siblings) и не нарушает Rule 14 (флаг не `IS_*`). Несогласованность была только в потребителе.

**Non-goals:**

- Добавление поля в `MediaCapabilities` - отвергнуто (дубль контракта).
- Изменение самого контракта `CapabilityAvailability.isStreamsAvailable()` - остаётся как есть.

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-06-25 (parked из research S0675)

**Текст:** Гейт трансляций сделан прямым чтением `BuildConfig.SUPPORT_STREAMS` в `src/main` (`CapabilityAvailability.kt:47`, `MainActivity.kt:667`), минуя интерфейс capability. Завершить flavor-изоляцию.

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** parked из research S0675; согласовано с S0565 (то решение разрешало чтение флага как capability-flag, не `IS_*`; здесь чтение лишь централизуется в контракте), S0575 (рантайм-тоггл master)
- **Flavor scope:** все flavor (потребитель в `src/main`); поведение по flavor неизменно (standard/legacy/noLegal/vr = streams on, lite/photos = off)
- **API level:** без API-специфики

---

## Фазы

### Phase 01 - Потребитель через контракт

- [x] `MainActivity`: инжектнуть `CapabilityAvailability`; заменить два прямых `BuildConfig.SUPPORT_STREAMS` (Timber-лог + гейт `streamsMenuManager.populate`) на `capabilityAvailability.isStreamsAvailable()`.
- [x] Удалить ставший мёртвым `import com.sza.fastmediasorter.BuildConfig` из `MainActivity` (Rule 20 + lint).
- [x] Обновить комментарии S0565/S0678 в `MainActivity` и KDoc `MainStreamsMenuManager` (caller теперь передаёт `isStreamsAvailable()`).
- **Verification:** grep `MainActivity` - нет `BuildConfig.SUPPORT_STREAMS` в коде; `.\a.ps1 d` собирается.

---

## Критерии готовности

1. В `src/main` нет прямого чтения `BuildConfig.SUPPORT_STREAMS` вне контракта `CapabilityAvailability` (потребители идут через `isStreamsAvailable()`).
2. Поведение гейта меню трансляций по flavor неизменно (photos/lite - отсутствует).

---

## Last Audit

**2026-06-25** - by `/spec-all` S4 (audit). Verdict: **Verified**.

- Criterion 1 (нет прямого чтения вне контракта): grep `src/main` для `BuildConfig.SUPPORT_STREAMS` -> остаётся только `CapabilityAvailability.kt:47` (сам контракт, как `isPersistentAudioPlaybackAvailable`); в `MainActivity` ссылка осталась лишь в комментарии, в коде нет (PASS).
- Criterion 2 (поведение по flavor неизменно): по построению - `isStreamsAvailable()` возвращает тот же `BuildConfig.SUPPORT_STREAMS`, гейт остался `isStreamsAvailable() && isStreamsEnabled`. standard/legacy/noLegal/vr = on, lite/photos = off.
- Сборка: `.\a.ps1 d` BUILD SUCCESSFUL (Hilt инжектит `CapabilityAvailability` в `MainActivity`).
- Гейты: neuroslop / deprecated-pm-flags PASS; мёртвый `import BuildConfig` удалён (Rule 20).
- Подход скорректирован vs §0: дом - `CapabilityAvailability` (где `isStreamsAvailable` уже жил), не `MediaCapabilities` (избежали второго source of truth).

---

## Связи

- Parked из research S0675.
- S0565 - ранее благословило чтение `SUPPORT_STREAMS` в src/main (не `IS_*`); S0678 централизует чтение в контракте, не противореча.
- S0575 - рантайм master-тоггл; гейт = `isStreamsAvailable() && isStreamsEnabled`.
