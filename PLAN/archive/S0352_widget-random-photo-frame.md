# Стратегическая спецификация: S0352 - Виджет «Случайный кадр / Цифровая фоторамка»

**Ticket:** S0352
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-04
**Tier:** 3 - Moderate (ad-hoc)
**Parent ticket:** S0348 (home-widget-icon-refresh) - выделено как суб-спецификация по решению владельца 2026-06-04.
**Tactical plan:** `PLAN/S0352_widget-random-photo-frame/INDEX.md`

> **Scope:** STRATEGIC. Draft - содержание важнее стиля; перед `Approved` пройти approval gate.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - implementation (`/spec-all S0352`).
- **Goal / expected outcome:** Delegated by user - `/spec-all` auto-approval - добавить widget `2x2` / `3x3`, который показывает случайное фото из выбранного ресурса, обновляет кадр battery-aware и по нажатию открывает файл на весь экран.
- **Local anchor:** Delegated by user - `/spec-all` auto-approval - S0352 выделен из S0348 §13 как отдельный content-widget поверх уже существующей home-screen widget foundation и in-app pin/config flow первой волны.
- **Scope boundaries / forbidden areas:** Delegated by user - `/spec-all` auto-approval - только `app_v2` image-capable flavors; без `Wear`, без read-only зон, без live network scan из widget provider, без превращения виджета в интерактивную мини-галерею, без schema change и без новых Hilt scope/qualifier решений.
- **Done / success signal:** Delegated by user - `/spec-all` auto-approval - свежедобавленный widget `2x2` / `3x3` можно сконфигурировать на выбранный ресурс, он показывает случайный кадр из cache/local snapshot, периодически обновляется battery-aware, а tap открывает fullscreen viewer или явный fallback.
- **Autonomy rule:** Delegated by user - `/spec-all` auto-approval - agent may decide tactical details with explicit assumptions and must ask only if implementation would otherwise become unsafe or contradictory.
- **UI decisions / delegation:** Delegated by user - `/spec-all` auto-approval - first version is photo-first and non-browsable: resource is chosen during configuration, the widget surface shows one random frame plus explicit empty/error fallback, no in-widget next/previous controls, and refresh cadence is fixed rather than user-configurable.

Approval gate completed on 2026-06-04 by `/spec-all` auto-approval after checking S0348 parent constraints, current widget foundation, and existing pin/config flows.

---

## 1. Проблема

Нет способа показывать на home screen случайное фото из выбранного локального или сетевого ресурса как цифровую фоторамку.

---

## 2. Цели

1. Добавить content-виджет `2x2` / `3x3`, показывающий случайное фото из выбранного при настройке ресурса.
2. Периодическое обновление по таймеру / WorkManager (battery-aware).
3. Клик открывает файл на весь экран.
4. Источник выбирается при конфигурации виджета.

**Non-goals:**

- Не выполнять live network scan из provider (S0348 §6.4 - reject live network gallery without cache).
- Не превращать в полноценную галерею-листалку в первой версии.

---

## 3. Ограничения

- **Flavor:** только flavors с поддержкой изображений (`SUPPORT_IMAGES`).
- **Cache-first:** рендеринг из cached file list + cached/local thumbnails; при отсутствии cache - icon/empty state и открытие ресурса.
- **Update contract:** периодичность через WorkManager с ограничениями по батарее/трафику, не чаще разумного; без тяжёлых bitmap-операций в provider.
- **Privacy:** случайное фото на home screen - учесть приватность выбранного источника.
- **Локализация:** EN/RU/UK; accessibility.
- **Flavor isolation:** Rule 15.

### 3.3 Owner inputs (Approval gate)

- **Approval signal:** owner invoked `/spec-all S0352` on 2026-06-04; the spec is auto-approved because the remaining decisions are reversible tactical assumptions, not owner-blocking product forks.
- **Autonomy:** agent may decide reversible implementation details with explicit assumptions and must stop only for unsafe or contradictory decisions.
- **Widget surface:** `2x2` / `3x3` stay photo-first content widgets with one random frame, not a control panel and not a browseable mini-gallery.
- **Resource selection:** widget instance is bound to one resource during configuration and keeps that resource identity for future refreshes.
- **Refresh policy:** first version uses fixed battery-aware refresh without a user-facing cadence setting.
- **Fallback policy:** missing cache / offline state is explicit and tappable; no silent blank widget.
- **Related tickets:** S0348 (parent), S0351, S0353.

---

## 4. Research closure

1. **Photo-specific thumbnail policy**
	- **Resolved:** widget рендерит только из cached file snapshot и cached/local thumbnail pipeline; provider не выполняет live scan ресурса и не строит тяжёлые bitmap-операции на лету.
	- **Tactical consequence:** если cached thumbnail недоступен, widget показывает placeholder/empty state и даёт tap-переход в выбранный ресурс / viewer вместо молчаливого сбоя.

2. **Refresh cadence and configurability**
	- **Resolved:** v1 использует фиксированное battery-aware обновление без пользовательской настройки частоты.
	- **Tactical consequence:** tactical phase выбирает конкретный cadence `>= 30 min`, добавляет immediate refresh на pin/config/update события и не обещает live-slideshow поведение.

3. **Offline / no-cache fallback**
	- **Resolved:** отсутствие cache считается ожидаемым состоянием, а не ошибкой конфигурации.
	- **Tactical consequence:** widget показывает явный empty/error fallback с понятной семантикой и по нажатию открывает выбранный ресурс или последний доступный fullscreen entry point.

---

## 5. Критерии готовности

1. Виджет «Случайный кадр» добавляется в размерах `2x2` / `3x3`.
2. Отображает случайное фото из выбранного при настройке источника.
3. Периодически обновляет изображение (WorkManager, battery-aware).
4. Клик открывает файл на весь экран; при отсутствии cache - явный fallback.

---

## 6. Связи

- **S0348** - parent; picker registry, pinning, content-widget boundary, configurable-shortcut config flow.

## Last Audit

**Date:** 2026-06-04
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 19 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 0

### Manual / on-device

- [ ] Pin the widget on a launcher and configure a resource.
- [ ] Verify that tapping a rendered photo opens fullscreen and that empty-cache fallback opens the resource browser.
