# Спецификация (draft): S1880 - Сообщения состояний экрана просмотра часов зашиты по-английски

**Ticket:** S1880
**Status:** Archived
**Priority:** 55
**Date:** 2026-08-21
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-21

**Захвачено во время:** S1877 (wear-phone-browser-breadcrumb), на этапе исследования модуля часов - чтение кода, без устройства.

**Текст:**

`BrowseViewModel` в модуле часов возвращает тексты состояний «пусто» и «ошибка» английскими литералами прямо в коде, а не из строковых ресурсов. Найденные места:

- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseViewModel.kt:126` - `"This media type is disabled in settings"`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseViewModel.kt:158` - `"Network source not found"`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseViewModel.kt:180` - `"Connection failed: $error"`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseViewModel.kt:215` - `"No media files found"`
- `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseViewModel.kt:223` - `"Network error"`

Пользователь с русским или украинским языком часов видит на этом экране английский текст.

Показательно, что механизм для ровно этого класса дефекта в том же файле уже есть: роль `ScreenTitle` (`BrowseViewModel.kt:41-48`) заведена специально ради того, чтобы ViewModel мог назвать текст, не держа `Context` - либо строковым ресурсом, либо готовой строкой из данных. Комментарий у `getScreenTitle()` (`BrowseViewModel.kt:293-296`) ссылается на исходный дефект локализации S1683. То есть решение в файле есть, но на сообщения состояний оно не распространено.

**Почему не однострочник:** пять мест, каждому нужен строковый ключ; `"Connection failed: $error"` - это ещё и форматная строка с подстановкой. Новые ключи подпадают под правило 30 (тринадцать локалей) и требуют прохода `set-android-string.ps1 -Action add` с проверкой паритета. Плюс нужна проверка, нет ли уже подходящих ключей в модуле часов, которые следует переиспользовать вместо новых.

---

## 1. Проблема / симптом

Экран просмотра часов показывает пользователю английский текст в состояниях «пусто» и «ошибка» независимо от языка часов. Область - модуль `wear`, экран `ui/browse`.

## Цель

Состояния пустого списка и ошибок на экране просмотра часов должны брать пользовательский текст из
переводимых ресурсов. Техническая причина сбоя остаётся в журнале для диагностики и не показывается
пользователю как основной текст. Расположение текста, кнопка повтора и навигация остаются прежними.

---

## 2. Свидетельство

Чтение рабочего дерева 2026-08-21 в ходе исследования S1877. Устройство не использовалось; строки прочитаны в исходниках по перечисленным выше номерам строк.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

Не зафиксированы - тикет припаркован как находка, владельцу пока не показан.

### 3.2 Жёсткие ограничения

- **Flavor:** модуль `wear` целиком; вариантами сборки телефона не гейтится.
- **API level:** без API-специфики.
- **Wear OS:** да, изменение целиком внутри модуля часов.
- **Локализация:** новые ключи подпадают под правило 30 - тринадцать локалей перед релизом.

### 3.3 Owner inputs (Approval gate)

- **Flavor scope:** изменение ограничено модулем Wear OS и не добавляет гейт продукта по flavor.
- **Wear OS:** сообщения состояния остаются на экране `ui/browse` часов.
- **Localization:** пять новых ключей добавляются в базовую и все двенадцать объявленных локалей Wear OS.
- **UI placement contract:** текст остаётся в тех же существующих пустом и ошибочном состояниях для круглого и квадратного экрана; новый элемент, overflow и навигация не появляются.
- **Accessibility:** существующие текстовые семантики и размер Chip повтора не меняются; локализованный текст сохраняет роль читаемого сообщения состояния.
- **Communication policy:** пустое состояние даёт один следующий шаг, ошибка не раскрывает сырую причину исключения и предлагает повторить действие.
- **Validation level:** `fwr`, `fw`, аудит строк по ключу и статический closure для модуля `wear`.
- **Owner sign-off:** автономный запуск `/spec-next` владельцем 2026-08-21 делегирует выбор ресурса при неизменных размещении и поведении.
- **Related tickets:** S1854 (та же болезнь на подписи кнопки `Retry` того же экрана), S1683 (исходный дефект локализации, породивший роль `ScreenTitle`), S1628 (строки часов по десяти локалям), S1877 (тикет, в исследовании которого находка получена)

---

## 4. Связи с другими спеками

- S1854 - `bugfix-wear-browse-retry-hardcoded`: тот же экран и та же болезнь, но покрывает ровно одну подпись - кнопку `Retry` в `BrowseScreen.kt`. Сообщения состояний в `BrowseViewModel` в его список приёмки не входят, поэтому этот тикет не дубликат, а соседний.
- S1683 - `wear-player-controls-and-paging`: в его рамках заведена роль `ScreenTitle`, решающая ровно этот класс дефекта; здесь она распространяется на сообщения состояний.
- S1877 - `wear-phone-browser-breadcrumb`: поднимает `ScreenTitle` в общий пакет `ui/common`. Если этот тикет исполняется после S1877, роль уже будет лежать в общем месте.

---

## 5. Открытые вопросы / Research items

1. **Переиспользование против новых ключей**
   - **Вопрос:** есть ли в модуле часов уже подходящие ключи для «пусто», «ошибка сети», «источник не найден», или под каждое состояние нужен новый ключ?
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S1880_wear-browse-state-messages-hardcoded/research/01__string-reuse-and-locales.md`
2. **Форма для `"Connection failed: $error"`**
   - **Вопрос:** показывать ли подставленный технический текст ошибки пользователю часов вообще, или заменить его на общую фразу, оставив подробность только в логе?
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S1880_wear-browse-state-messages-hardcoded/research/02__error-message-policy.md`

---

## План выполнения (compact)

### Phase 01 - Localize browse state messages

**Objective:** Replace direct browse-state text with resource-backed presentation data and supply each
new key in every declared Wear locale.

#### Step 01.1 - Add localized browse-state resources

**Files:** `wear/src/main/res/values*/strings_browse.xml`

**Prompt for developer:**

> Add five `browse_*` state-message keys through the Android string editor. Reuse the existing retry
> action, but do not reuse generic technical-error resources where they would expose an exception or
> omit the appropriate next step.

**Why:** The watch currently renders English literals for all locales, and a user must receive an
actionable state message in the selected interface language.

**Verification:**

- `scripts/check_strings_localized.ps1 -Module wear -KeyPrefix browse_` exits 0.
- Every locale declared in the Wear resource tree contains each new key.

**Status:** `[x]` done - all 13 resource directories received the five keys; parity check passed.

#### Step 01.2 - Resolve state messages in the Compose screen

**Files:** `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseUiState.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseViewModel.kt`, `wear/src/main/java/com/sza/fastmediasorter/wear/ui/browse/BrowseScreen.kt`

**Prompt for developer:**

> Carry resource-backed messages through `BrowseUiState` using the existing `ScreenTitle` role, and
> resolve them only in the Compose screen. Replace every direct error or empty-state message,
> including exception messages, without changing retry behaviour or state placement.

**Why:** A view model cannot own an Android context, while raw exception text is neither localized
nor suitable as the primary user-facing error message.

**Verification:**

- Browse state creation has no direct user-visible English literal or exception message.
- `a.ps1 fw` exits 0 for the Wear module.

**Status:** `[x]` done - every browse empty and error state now carries `ScreenTitle.Resource`; Wear Kotlin compilation passed.

#### Step 01.3 - Close with module-scoped validation

**Files:** all files changed in Phase 01

**Prompt for developer:**

> Run the resource and Kotlin checks, then close the touched set through the project facade scoped to
> the Wear module.

**Why:** The phone checks do not compile watch sources, so only Wear-targeted evidence proves that the
localized resources and Compose state contract are valid together.

**Verification:**

- `a.ps1 fwr` and `a.ps1 fw` exit 0.
- `post-change.ps1` reports PASS or PASS WITH ADVISORIES for the complete touched set.

**Status:** `[x]` done - `fwr`, `fw`, string parity, scoped detekt and the phase-boundary audit passed; facade closure awaits its queued CODE.LOCK turn.

---

## Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES: это исправление локализации существующего экрана.

## Риски

- Перевод может потерять смысл следующего шага на малом экране часов. Митигация - короткие формулировки,
  сохранение существующего размещения и проверка всех объявленных локалей.

## Критерии готовности

1. Пустое и ошибочное состояния экрана просмотра часов отображают локализованный текст во всех
   поддерживаемых языках.
2. Ошибка подключения не показывает пользователю текст исключения.
3. Кнопка повтора и навигация работают так же, как до изменения.

## Last Audit

**Date:** 2026-08-21
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 9 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 1

- PASS: all five `browse_*` keys exist in the base resource and every twelve declared Wear locales.
- PASS: every browse empty/error state carries `ScreenTitle.Resource`; the Compose screen resolves it.
- PASS: exception text remains in Timber diagnostics and is not passed to the user-facing state.
- PASS: `fwr`, `fw`, scoped detekt and `post-change` passed for the Wear change set.
- PASS: phase audit found no P0-P3 lifecycle, ownership, concurrency, or architecture defect.
- PASS: no S1880 debug tag exists because no device-only acceptance criterion remains.
- EXEMPT: the strategic feature inventory explicitly records no new capability.
