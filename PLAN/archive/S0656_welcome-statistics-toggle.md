# Стратегическая спецификация: S0656 - Галочка «Вести учёт статистики операций» на Welcome экране

**Ticket:** S0656
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-24
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-24
**Tactical spec:** `PLAN/S0656_welcome-statistics-toggle/` (создаётся через `/spec-tech`)

<!-- auto-approved by /spec-all - 2026-06-24 -->

> **Scope:** STRATEGIC. Цели, ограничения, решения. Без имён классов, путей, лимитов строк.

---

## 1. Проблема

Учёт статистики операций (`enableStatistics`) - локальная и обезличенная функция: данные собираются только на устройстве, ничего не отправляется без явного действия пользователя. Сейчас она спрятана в Настройках и по умолчанию выключена, поэтому большинство пользователей о ней не знают и не пользуются дашбордом статистики. Владелец хочет поднять её видимость на онбординг-экране Welcome и включить по умолчанию.

Область: онбординг (Welcome) + общая настройка статистики.

---

## 2. Цели

1. На странице «Функциональность» экрана Welcome появляется переключатель «Сбор статистики», привязанный к существующей настройке `enableStatistics`.
2. Переключатель и сама настройка по умолчанию включены.
3. Переключение на Welcome сразу применяет настройку (тот же механизм, что и остальные toggle этой страницы).
4. Описание под переключателем больше не утверждает «по умолчанию выключено» (это перестало быть правдой), но сохраняет заверение «ничего не отправляется автоматически».

**Non-goals:**

- Не вводим новый флаг/настройку - переиспользуем `enableStatistics` (он уже есть и гейтит StatsSink).
- Не меняем содержимое/формат самих собираемых метрик.
- Не трогаем экран дашборда статистики.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Минимальная описательность подписи - переиспользовать уже существующую трилингвальную подпись настройки статистики, а не плодить новую.

### 3.2 Жёсткие ограничения

- **Flavor:** все (настройка в `src/main`, доступна на standard/lite/photos/legacy; welcome-страница общая).
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** нейтрально (одна строка-переключатель + чтение существующего флага).
- **Совместимость данных:** меняется только default нового чтения `enableStatistics`. Существующие пользователи с уже сохранённым значением сохраняют свой выбор; новый default применяется к чистым установкам / непрочитанному значению. Без изменения схемы Room/DataStore.
- **Локализация:** EN/RU/UK обязательно.
- **Доступность:** переключатель наследует доступность `SettingsToggleRow` (touch target, не-цветовое отличие, focus).

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0473 (ввёл `enableStatistics` с default OFF - S0656 осознанно меняет этот default на ON), S0400 (онбординг Welcome / functionality page), S0499 (дашборд статистики), S0646 (соседний батч настроечного UI того же дня).
- **UI scope:** добавляется один переключатель на функциональную страницу Welcome рядом с существующими toggle; редактируется подпись настройки статистики. Портрет и ландшафт - оба.
- **Data scope:** разворот default `enableStatistics` OFF → ON. Privacy: владелец подтвердил (2026-06-24), что данные локальные и обезличенные, ничего не отправляется автоматически, поэтому privacy-возражение S0473 снято.

---

## 4. Контекст текущей архитектуры

Онбординг Welcome - постраничный (`WelcomeActivity` + pager). Функциональная страница рендерится тонким view-holder'ом, а вся логика её переключателей живёт в одном контроллере, который читает/пишет `AppSettings` через репозиторий настроек по единому паттерну `setCheckedSilently(..)` + `setOnCheckedChangeListener { persist { copy(..) } }`. Настройка `enableStatistics` уже существует, гейтит сбор статистики и имеет собственный переключатель на экране Настроек и трилингвальную подпись. Не хватает только её представления на онбординге и разворота default.

---

## 5. Предлагаемый подход

Переиспользование, не изобретение: добавить ещё один `SettingsToggleRow` на функциональную страницу Welcome по тому же паттерну, что и соседние переключатели, привязав его к `enableStatistics`. Сменить кодовый default `enableStatistics` на ON. Обновить трилингвальную подпись настройки, убрав теперь-неверное «по умолчанию выключено».

### 5.1 Основные столпы / модули

- Модель настроек: разворот default `enableStatistics`.
- Welcome / функциональная страница: новый переключатель (портрет + ландшафт).
- Контроллер функциональной страницы: привязка нового переключателя к существующему flow persist.
- Строковые ресурсы: правка summary статистики (EN/RU/UK).

### 5.2 Потоки данных и событий

Welcome toggle → контроллер → репозиторий настроек → `enableStatistics` → StatsSink (как и сегодня). Чтение: страница отражает текущее значение `enableStatistics` (по умолчанию ON).

### 5.3 Точки расширяемости

- Паттерн «переключатель функциональной страницы» остаётся однородным - будущие onboarding-toggle добавляются так же.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет. Privacy-трактовка и размещение разрешены owner-решением 2026-06-24 (default ON, локальные обезличенные данные; страница - функциональная, рядом с прочими toggle).

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Подпись «по умолчанию выключено» останется после разворота default | Средняя | Дезинформация о поведении | Обновить summary EN/RU/UK в этом же тикете |
| Разворот default воспримут как регрессию приватности S0473 | Низкая | Вопросы по политике | Зафиксировать owner-решение в §3.3/§9; данные локальные/обезличенные, ничего не шлётся автоматически |
| Правка только портретного layout | Низкая | Рассинхрон ориентаций | Править portrait и landscape `page_welcome_functionality` |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая видимая возможность: на онбординге Welcome добавлен переключатель сбора локальной статистики операций (по умолчанию включён). FEATURES-строка пишется `/skill-release` из diff `ALL_FEATURES`; per-spec FEATURES не редактируем.

---

## 9. Архитектурные решения (ADR)

- **ADR:** Default `enableStatistics` развёрнут OFF → ON. Это сознательно переопределяет default из S0473. Основание (owner, 2026-06-24): статистика локальная и обезличенная, ничего не отправляется автоматически, поэтому прежнее privacy-обоснование «выключено по умолчанию» снято. Заверение «ничего не отправляется автоматически» сохраняется в подписи.
- Прочее - по устоявшимся паттернам проекта (переиспользование `SettingsToggleRow` + контроллер).

---

## 10. Связи с другими спеками

- **S0473** - ввёл `enableStatistics` (default OFF). S0656 переопределяет default на ON.
- **S0400** - онбординг Welcome / функциональная страница (хост нового переключателя).
- **S0499** - дашборд статистики (виден при включённой настройке).
- **S0646** - соседний батч настроечного UI того же дня (не пересекается по файлам).

---

## 11. Критерии готовности (strategic-level)

1. На функциональной странице Welcome (портрет и ландшафт) виден переключатель статистики, по умолчанию включён.
2. Переключение на Welcome немедленно меняет `enableStatistics` и отражается на экране Настроек.
3. На чистой установке статистика включена по умолчанию; дашборд доступен без захода в Настройки.
4. Подпись настройки статистики не утверждает «по умолчанию выключено» ни в одной локали и сохраняет «ничего не отправляется автоматически».
5. Сборка standard debug проходит; гейты качества зелёные.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0656` - создаст `PLAN/S0656_welcome-statistics-toggle/` с фазами.

---

## Last Audit

### Manual / on-device

- Device: emulator-5554 (standard debug, sdk_gphone16k), build v2.60.6261.106-DEBUG. App data wiped (`pm clear`) for a true fresh-install onboarding.
- Date: 2026-06-26.
- Outcome: PASS
- [x] Welcome functionality page ("What should the app do?") shows the `rowStatistics` "Statistics collection" toggle, ON by default. Confirmed by switch visual (blue/right) and logcat `WelcomeFunctionalityController: S0656: .. current=true`.
- [x] Toggle responds: switched OFF (grey/left) then back ON.
- [x] Landscape: same functionality page renders the statistics row in the two-column `layout-land` (left column with the other toggles); row present and labelled identically.
- [x] After finishing onboarding, Settings -> General -> "Background sync, network and cache" mirrors the state: `rowEnableStatistics` ON, and the `rowOpenStatistics` Statistics dashboard entry (history icon + chevron) is visible (it is GONE while statistics is off).
- [x] Summary "Collected on your device. Nothing is sent automatically." in EN/RU/UK - no "off by default" claim in any locale (string audit + on-screen).
- Note: `WelcomeActivity` handles `configChanges`, so the landscape two-column page was exercised by setting the device landscape on the open page (both `layout/` and `layout-land/page_welcome_functionality.xml` carry `rowStatistics`).
- Evidence: temp/devtest_S0651_S0620_S0656/S0656_welcome_portrait.png, S0656_toggled_off.png, S0656_welcome_landscape.png, S0656_settings_mirror.png, S0656_logcat.txt
