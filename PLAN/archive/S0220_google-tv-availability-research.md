# Стратегическая спецификация: S0220 — Ресёрч недоступности приложения на Google TV Panasonic MX700

**Ticket:** S0220
**Status:** Tactical
**Priority:** 50
**Date:** 2026-05-16
**Tier:** 3 — Moderate (ad-hoc — запрос 2026-05-16)
**Roadmap entry:** Ad-hoc — запрос 2026-05-16
**Tactical spec:** `PLAN/S0220_google-tv-availability-research/` — [`INDEX.md`](S0220_google-tv-availability-research/INDEX.md)

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Приложение не отображается в списке доступных к установке на телевизоре Panasonic MX700 (Google TV). Пользователь не может найти приложение через Play Store на устройстве. При этом в манифесте уже объявлены базовые маркеры совместимости с TV: `LEANBACK_LAUNCHER`, `android.software.leanback required="false"`, `android.hardware.touchscreen required="false"`. Причина фильтрации Play Store остаётся неизвестной — это может быть ошибка на стороне манифеста, Play Console, Play Store device targeting, либо специфическое поведение Panasonic MX700.

---

## 2. Цели

1. Установить точную причину, по которой Play Store не предлагает приложение к установке на Panasonic MX700.
2. Составить исчерпывающий список фильтров, которые могут блокировать видимость (манифест, Play Console, device catalog, совместимость hardware features).
3. Устранить все подтверждённые блокеры и верифицировать появление приложения в Play Store на целевом устройстве.

**Non-goals:**

- Полноценная адаптация UI под Leanback/TenFoot — это отдельная задача.
- Поддержка других TV-платформ (Samsung Tizen, LG webOS) — не в объёме этого спека.
- Изменение функциональности приложения для TV-сценариев.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. По возможности исправить все найденные причины в одном релизе.
2. Проверить видимость не только на MX700, но и на типичных Android TV / Google TV устройствах через Play Console — Device Catalog.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard` (основной публикуемый флейвор).
- **API level:** minSdk 26 (Android 8); изменение minSdk не допускается без отдельного решения.
- **Wear OS:** не затрагивается.
- **Производительность:** без ограничений — ресёрч и манифест-правки не влияют на runtime.
- **Совместимость данных:** нет Room-изменений.
- **Локализация:** изменения в манифесте не требуют локализации; если потребуются строки для TV-специфичного UI — EN/RU/UK обязательно.
- **Доступность:** вне объёма ресёрча.

---

## 4. Контекст текущей архитектуры

В манифесте уже присутствует ряд TV-совместимых деклараций: категория `LEANBACK_LAUNCHER` прицеплена к `MainActivity`, объявлены `android.software.leanback required="false"`, `android.hardware.touchscreen required="false"`, `android.hardware.faketouch required="false"`, `android.hardware.microphone required="false"`, `android.hardware.wifi required="false"`. Тем не менее этого оказалось недостаточно для появления приложения на Panasonic MX700.

Play Store на Google TV использует более строгую матрицу совместимости, чем классический Android TV. Panasonic MX700 работает под управлением Google TV (на базе Android 11/12), и Play Store на нём применяет правила device targeting, которые могут отличаться от правил телефонного Play Store. Фильтрация может происходить на нескольких уровнях: манифест (аппаратные требования), Play Console (device targeting, excluded devices list), GL texture formats, доступность конкретных Google Play Services на платформе.

---

## 5. Предлагаемый подход

Ресёрч разбивается на три параллельных направления: анализ манифеста и build config, анализ Play Console настроек, и верификация на устройстве через альтернативные методы установки.

### 5.1 Основные направления ресёрча

**Направление A — Манифест и hardware features:**
- Проверить все `<uses-feature>` и `<uses-permission>` на наличие неявных требований к аппаратуре, которые Play Store транслирует в hardware filter.
- Исследовать `android:screenOrientation="sensor"` на всех Activity — на TV нет датчика ориентации; Play Store может интерпретировать это как несовместимость.
- Проверить, не требует ли `RECORD_AUDIO` явного `android.hardware.microphone` на уровне Play Store despite `required="false"`.
- Проверить `MANAGE_EXTERNAL_STORAGE` — Google Play ограничивает эту permission на TV-устройствах и может блокировать видимость.
- Проверить совместимость `supports-screens requiresSmallestWidthDp="320"` с TV.
- Исследовать, не блокирует ли `<layout defaultWidth/defaultHeight>` в `<activity>` видимость на TV (это VR-специфичный тег).

**Направление B — Play Console и device targeting:**
- Проверить раздел «Device catalog» в Play Console: статус Panasonic MX700 (excluded / incompatible / supported).
- Проверить раздел «Advanced settings → Device targeting» на наличие ручных исключений для TV-классов.
- Проверить, подключён ли `com.sza.fastmediasorter` к правильным form factor категориям (Phone/Tablet vs TV).
- Проверить, есть ли в Play Console раздел «Google TV» как отдельный таргет и включено ли туда приложение.

**Направление C — Верификация на устройстве:**
- Попробовать установить APK напрямую (sideload) на Panasonic MX700 — проверить, запускается ли приложение.
- Запросить `adb shell pm list packages` и `dumpsys package` после sideload — убедиться, что нет runtime-ошибок.
- Если Play Store фильтрует: проверить через `adb shell am start -a android.intent.action.VIEW ...` страницу приложения в Play Store на TV — увидеть ли конкретную причину «не совместимо».

### 5.2 Потоки данных и событий

Фильтрация Play Store — внешний процесс. Исправления вносятся в манифест и Play Console, проверяются публикацией обновлённой сборки.

### 5.3 Точки расширяемости

- При обнаружении необходимости отдельного TV-специфичного поведения (отдельный лаунчер Activity, отдельный UI поток) — выделить в отдельный спек; текущий ресёрч только фиксирует видимость в Play Store.

---

## 6. Открытые вопросы / Research items

1. **`MANAGE_EXTERNAL_STORAGE` на TV**
   - **Вопрос:** блокирует ли эта permission появление приложения в Google TV Play Store?
   - **Ответ:** **Нет.** `MANAGE_EXTERNAL_STORAGE` не входит в таблицу permission→hardware-feature имплицирования Android. Она не вызывает hardware filter на TV. Политика Google Play требует заполнения Permissions Declaration Form, но это policy review, не TV-фильтр. Приложение, одобренное с этой permission для phone Play Store, остаётся eligible для TV.
   - **Статус:** Resolved — Not a blocker.

2. **`android:screenOrientation="sensor"` на TV**
   - **Вопрос:** является ли `sensor` ориентация причиной фильтрации?
   - **Ответ:** **Неприменимо.** В нашем манифесте `android:screenOrientation` **не объявлен ни на одном Activity** — только `android:configChanges`. Незаданный атрибут не является Play Store фильтром. Запрещены только явные portrait-family значения: `portrait`, `reversePortrait`, `sensorPortrait`, `userPortrait`. Рекомендация (не блокер): добавить `android:screenOrientation="landscape"` на `MainActivity` для соответствия TV quality criterion TV-LO.
   - **Статус:** Resolved — Not a blocker.

3. **`<layout>` тег в `<activity>` (VR-специфичный)**
   - **Вопрос:** влияет ли `<layout defaultWidth="1920dp">` на совместимость с TV в Play Store?
   - **Ответ:** **Нет.** `<layout>` — только подсказка для freeform/split-screen режима (API 24+). TV устройства не поддерживают freeform, тег игнорируется. В Play Store compatibility filtering этот тег не участвует.
   - **Статус:** Resolved — Not a blocker.

4. **Play Console device catalog: статус Panasonic MX700**
   - **Вопрос:** какой статус у Panasonic MX700 в Device Catalog?
   - **Ответ:** Требует ручной проверки в Play Console → Release → Device Catalog (поиск по модели). Phase 02 Step 2.1.
   - **Статус:** Open — Requires manual Play Console access (Phase 02).

5. **`RECORD_AUDIO` implicit hardware filter**
   - **Вопрос:** транслирует ли Play Store `RECORD_AUDIO` в неявное hardware требование несмотря на `required="false"`?
   - **Ответ:** `RECORD_AUDIO` имплицирует `android.hardware.microphone`. Это перекрыто явным объявлением `<uses-feature android:name="android.hardware.microphone" android:required="false"/>` в манифесте. Explicit declaration с `required="false"` отменяет implicit filter.
   - **Статус:** Resolved — Not a blocker.

6. **Panasonic MX700 — версия Google Play Services**
   - **Вопрос:** совместима ли версия Google Play Services на MX700 с targetSdk 35?
   - **Ответ:** Требует проверки: `adb shell dumpsys package com.google.android.gms | grep versionName` на устройстве. Panasonic MX700 работает на Google TV (Android 11/12), Play Services обновляются автоматически — несовместимость маловероятна, но требует подтверждения. Phase 02 Step 2.3.
   - **Статус:** Open — Requires device or Play Console access (Phase 02).

7. **Google TV vs Android TV store rules**
   - **Вопрос:** применяет ли Google TV Play Store дополнительные фильтры?
   - **Ответ:** Базовые требования идентичны: `LEANBACK_LAUNCHER` + `android.hardware.touchscreen required="false"` + `android.software.leanback required="false"`. Все три условия выполнены в нашем манифесте. Google TV Play Store — это расширенный Android TV Play Store с тем же filtering pipeline. Дополнительных аппаратных фильтров специфичных для Google TV (по сравнению с Android TV) не задокументировано.
   - **Статус:** Resolved — Not a blocker (baseline requirements satisfied).

8. **TV banner — XML drawable вместо PNG** *(найдено в ходе аудита, не было в исходном списке)*
   - **Вопрос:** является ли `res/drawable/tv_banner.xml` (layer-list XML) приемлемым форматом TV баннера?
   - **Ответ:** **Вероятно блокер.** TV launcher и Google Play TV ревью ожидают растровый PNG размером 320×180 px в `res/drawable-xhdpi/`. XML layer-list может не отображаться в TV launcher и вызывать ошибку при Play Store публикации. Текущий баннер — placeholder, содержащий только ic_launcher на тёмном фоне. Требует замены на полноценный PNG.
   - **Статус:** Confirmed Blocker — Fix in Phase 04.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Причина фильтрации на стороне Play Console и не воспроизводима без доступа к конкретному устройству | Средняя | Ресёрч затянется — нужна физическая проверка на MX700 | sideload APK + adb проверка как параллельный маршрут |
| Исправление манифеста сломает совместимость с другой платформой (VR, телефон) | Средняя | Регрессия для телефонных пользователей | Каждое изменение тестировать на стандартном смартфоне |
| `MANAGE_EXTERNAL_STORAGE` policy изменится и потребует удаления функциональности | Низкая | Потеря части file-management возможностей | Оценивается отдельно, если подтвердится |
| Panasonic MX700 не поддерживается Google Play (OEM exclusion) | Низкая | Приложение никогда не появится через Play Store | Исследовать sideload как альтернативу |

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в `docs/FEATURES.md` — это ресёрч и bugfix; возможность установки на TV не является новой задекларированной фичей.

---

## 9. Архитектурные решения (ADR)

ADR нет — решение по устоявшимся паттернам проекта. Решения будут зафиксированы по результатам ресёрча в тактическом спеке.

---

## 10. Связи с другими спеками

Связей нет.

---

## 11. Критерии готовности (strategic-level)

1. Все пункты §6 переведены в статус Resolved с задокументированным ответом.
2. Приложение видно в Play Store на Panasonic MX700 (или подтверждено, что устройство принципиально не поддерживается).
3. Все манифест-изменения не ломают установку и запуск на стандартном Android-смартфоне.
4. Play Console Device Catalog показывает Panasonic MX700 как «supported» (или задокументирована причина «incompatible»).

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0220` — создаст `PLAN/S0220_google-tv-availability-research/` с фазами.
