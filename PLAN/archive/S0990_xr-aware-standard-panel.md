# Стратегическая спецификация: S0990 - XR-aware standard (панельный режим на Android XR)

**Ticket:** S0990
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-11
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-11 (найдено при обсуждении стратегии дистрибуции Android XR)
**Tactical spec:** `PLAN/S0990_xr-aware-standard-panel/` (будет создан через `/spec-tech`)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-07-11

**Текст:**

оформить /spec-draft на «XR-aware standard» (тот же бандл, uses-feature spatial required=false + polish под spatial-панель Galaxy XR) — чтобы дешёвый апгрейд охвата не потерялся.

**Контекст находки (2026-07-11, обсуждение дистрибуции Android XR; Meta отложена, объём — только Google Play):**

Владелец спросил, публиковать ли VR отдельной программой в Google Play или сложить несколько бандлов в один продукт. Вывод: один продукт, `standard` на mobile track. При этом всплыл дешёвый промежуточный апгрейд, который стоит запарковать отдельно.

Суть идеи: `standard` AAB уже сейчас автоматически виден на Android XR (Samsung Galaxy XR) как «compatible mobile app» в плавающей spatial-панели, потому что `app_v2/src/main/AndroidManifest.xml` не объявляет ни одной `required="true"` hardware/XR-фичи (все `required="false"`). Этот тикет - дешёвый инкремент поверх бесплатного baseline:

1. Объявить `<uses-feature android:name="android.software.xr.api.spatial" android:required="false"/>` в `standard`-бандле, чтобы Google Play явно распознавал XR-panel совместимость, не теряя охват телефонов/планшетов.
2. Polish того, как существующий плоский UI рендерится внутри плавающей панели Galaxy XR / Home Space (путь Option A «spatialized mobile app»).

Остаётся на mobile release track, один бандл, без нового flavor, без immersive-воспроизведения. Доступно сейчас, независимо от готовности immersive.

**Non-goals (из захвата):**

- immersive/Full-Space воспроизведение - это S0556 + S0773.
- dedicated Android XR release track, новый store-clean XR flavor, `openxr required`.
- любая регрессия device-reach (`standard` сохраняет все 4 ABI + minSdk 26).

**Дедуп (проверено `PLAN` grep + каталог, 2026-07-11):** НЕ дубликат S0556. S0556 (`publish-vr-android-xr-store`, BlockExternal) - это ДРУГОЙ путь: dedicated Android XR track, `android.software.xr.api.openxr required=true`, отдельный store-clean Android XR flavor (ещё не существует), полный immersive Full-Space, заблокирован на S0773. Это Option B (immersive, видимость только XR). Этот тикет - Option A (lightweight, mobile track, плавающая панель, виден всем включая XR). Разные deliverable, flavor (standard vs новый XR flavor), track (mobile vs dedicated), сроки (сейчас vs после immersive).

**Источники:** developer.android.com/develop/xr/package-and-distribute; Play Console «Manage different form factor releases on dedicated tracks».

---

## 1. Проблема

`standard`-бандл уже виден на Android XR как совместимое mobile-приложение, но не заявляет XR-panel совместимость явно и не отполирован под плавающую spatial-панель Galaxy XR. Дешёвый инкремент охвата теряется без явной декларации + polish.

**Research (2026-07-11, автономно):** премиса подтверждена - `app_v2/src/main/AndroidManifest.xml` объявляет ВСЕ `uses-feature` как `required="false"` (wifi, touchscreen, faketouch, leanback, microphone, camera, camera.any, location*, screen.portrait/landscape), ни одной `required="true"`; есть явные комментарии про eligibility XR-headset / camera-less устройств. Фича `android.software.xr.api.spatial` сейчас не объявлена нигде (vr-манифест использует `openxr`, не `spatial`).

---

## 2. Цели

<Нумерованный список наблюдаемых улучшений.>

**Non-goals:**

- <что явно вне объёма>

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

<Нумерованный список желаемого, но необязательного к первой итерации.>

### 3.2 Жёсткие ограничения

- **Flavor:** standard (по возможности тривиально - также legacy/photos; решить на spec-tech). Standard остаётся store-clean, без втягивания noLegal; flavor-логика вне `src/main` по `dev/FLAVOR_DEVELOPMENT_RULES.md`.
- **API level:** minSdk без изменений (26).
- **Wear OS:** не затрагивается.
- **Производительность:** н/д (декларация манифеста + UI-polish).
- **Совместимость данных:** н/д.
- **Локализация:** EN/RU/UK - если появятся пользовательские строки.
- **Доступность:** панельный UI управляется gaze-and-pinch на Android XR; зоны попадания >=48dp.
- **Device-reach:** без сокращения ABI (все 4) и без регрессии minSdk - release-gate.

### 3.3 Owner inputs (Approval gate)

<Заполняется при переходе Draft → Approved (через /spec или /spec-update). В скелете оставить пустым, кроме обязательного поля ниже.>

- **Related tickets:** S0556 (publish-vr-android-xr-store - dedicated immersive XR track, комплементарный Option B), S0773 (vr-cinema-program-separate-player - immersive-эпик; дети S0962/S0963/S0964 в BlockNeedUserTest на 2026-07-11, код написан, на device-test gate Quest 3), S0775 (publish-app-samsung-store), S0555 (publish-vr-app-meta-store).

---

## 4. Контекст текущей архитектуры

<1-2 абзаца. Какие слои/компоненты отвечают за затронутую область. Почему сейчас нельзя решить проблему из §1. Без перечисления классов.>

---

## 5. Предлагаемый подход

<Архитектурный уровень: какие роли появятся, откуда читают / куда пишут. Имена классов, файлов, методов - запрещены.>

### 5.1 Основные столпы / модули

<Крупные логические блоки.>

### 5.2 Потоки данных и событий

<Высокоуровневая схема. «UI → слой применения → кэш → ..». Без имён методов.>

### 5.3 Точки расширяемости

<Что должно остаться открытым к расширению.>

---

## 6. Открытые вопросы / Research items (owner + device gate - блокирует переход)

Срез 1 (декларация манифеста) - автономно готов, но осознанно отложен владельцем:
- Полностью определён: добавить `<uses-feature android:name="android.software.xr.api.spatial" android:required="false"/>` в `app_v2/src/main/AndroidManifest.xml`. Device-reach-нейтрально (required=false, как остальные фичи). Реализация - одна строка.
- Блокер: владелец при захвате явно написал «запарковать отдельно» = отложить, не «сделать сейчас». Это store-facing декларация в shipping-манифест. Нужно go/no-go: включать ли в ближайший релиз или держать до готовности XR-стратегии (S0556/S0773).

Срез 2 (polish панели) - XR-device-gated:
- Рендеринг плоского UI в плавающей панели Galaxy XR / Home Space надо итеративно проверять на устройстве Android XR (Galaxy XR) или XR-эмуляторе. В этой сессии подключён телефонный эмулятор (emulator-5556), не XR - визуально верифицировать нельзя.
- Также надо подтвердить на XR-таргете, что декларация `spatial` даёт ожидаемый эффект распознавания.

Пока нет owner go-ahead + XR-устройства - BlockQuestions.

**Разрешено (2026-07-11, owner):** срез 1 - **добавить в ближайший релиз**. Реализовано: `<uses-feature android:name="android.software.xr.api.spatial" android:required="false"/>` добавлен в `app_v2/src/main/AndroidManifest.xml` (после `screen.landscape`). Срез 2 (polish панели) остаётся XR-device-gated follow-up (нужен Galaxy XR); отдельно не блокирует - витрина/охват уже улучшены декларацией.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| <описание> | Низкая / Средняя / Высокая | <что сломается> | <как предотвратить> |

---

## 8. Влияние на пользователя (docs/FEATURES)

<По умолчанию: «Без изменений в docs/FEATURES.» Если фича новая - одно предложение для FEATURES + _RU + _UK.>

---

## 9. Архитектурные решения (ADR)

<Если нет - «ADR нет - решение по устоявшимся паттернам проекта.»>

---

## 10. Связи с другими спеками

<Список связей или «Связей нет.»>

---

## 11. Критерии готовности (strategic-level)

<Нумерованный список. Наблюдаемые результаты, не архитектурные утверждения.>
