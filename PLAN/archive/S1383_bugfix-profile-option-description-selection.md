# Спецификация (compact bugfix): S1383 - Описание и повторный выбор профиля

**Ticket:** S1383
**Status:** Archived
**Priority:** 90
**Date:** 2026-08-03
**Tier:** 3 - Moderate (ad-hoc)

---

## 0. Захваченный материал (inbox)

**Захвачено:** 2026-08-03

**Текст:**

The description for each option cannot be read fully .
1. Need to see the full box with full text for selected option
2. If I press again on the already selected option - this must be choosen and continue to the next page
portrait and landscape

**Вложения:**
- screenshot: welcome profile selector with truncated option descriptions - attached in the conversation; no stable local workspace path was provided

---

## 1. Проблема / симптом

Страница выбора профиля устройства в Welcome (`page_welcome_profiles`) рисует сетку плиток профилей. Описание каждой плитки жёстко обрезано двумя строками с многоточием, поэтому текст выбранного профиля нельзя дочитать - при типичной длине описания (около 130 символов, три-четыре строки на телефоне в одну колонку) теряется последняя треть.

Вторая часть проблемы - навигация: тап по плитке только меняет выделение, поэтому подтверждение выбора всегда требует отдельного тапа по кнопке Next внизу. Повторный тап по уже выбранной плитке сегодня не делает ничего.

Оба симптома воспроизводятся одинаково в портретной и ландшафтной ориентации: обе ориентации используют один и тот же item-layout и один и тот же holder.

Затронутые поверхности: страница профилей в Welcome и диалог выбора профиля из Настроек - оба рисуют плитку через общий `DeviceProfileTileAdapter`.

---

## 2. Корневая причина

1. Обрезка описания зашита в разметку плитки: `item_device_profile_tile.xml`, `tvProfileTileDescription` имеет `android:maxLines="2"` и `android:ellipsize="end"`. Адаптер задаёт только текст, поэтому у выбранной плитки нет способа показать описание целиком.
2. Клик по плитке в `DeviceProfileTileAdapter` всегда уходит в единственный колбэк `onClick(type)`. Адаптер знает текущее выделение (`selected`), но не различает первый выбор и повторный тап по уже выбранному, поэтому Welcome-страница не может отличить «выбрал» от «подтвердил».
3. В `WelcomePage` нет колбэка подтверждения - только `onProfileSelected`, который в `WelcomeActivity` сводится к `viewModel.onProfileSelected(type)`. Перелистывание страницы живёт в `WelcomeActivity.flipPage(forward)` и до страницы профилей не доведено.

---

## 3. Исправление

Правка на общем слое (`DeviceProfileTileAdapter`), чтобы диалог Настроек получил читаемое описание текущего профиля тем же изменением. Повторный тап как подтверждение подключается опциональным колбэком, поэтому поведение диалога (тап = выбор и закрытие) не меняется.

### Step 1 - Раскрыть описание выбранной плитки и развести первый и повторный тап

**Файлы:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/profile/DeviceProfileTileAdapter.kt`

**Prompt for developer:**

> В `TileViewHolder.bind` задавать `tvProfileTileDescription.maxLines` явно в обеих ветках: `Int.MAX_VALUE` для выбранной плитки, `2` для остальных - иначе переиспользование view в RecyclerView оставит раскрытое описание на чужой плитке. Добавить в конструктор адаптера опциональный параметр `onReselect: ((DeviceProfileType) -> Unit)? = null` со значением по умолчанию `null` и в обработчике клика вызывать его, когда нажата уже выбранная плитка, иначе - существующий `onClick`.

**Why:**

Обрезка описания зашита в разметку (§2.1), а адаптер - единственное место, где известно текущее выделение, поэтому только здесь можно раскрыть текст ровно у выбранной плитки. Значение по умолчанию `null` оставляет диалог Настроек (§1) на прежнем пути `onClick`, где тап и так закрывает диалог.

**Verification:**

- `Grep` - `onReselect` присутствует в `DeviceProfileTileAdapter.kt`.
- `Grep` - `maxLines` встречается в `DeviceProfileTileAdapter.kt` (обе ветки заданы в коде).
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

---

### Step 2 - Добавить колбэк подтверждения профиля в модель страницы и Activity

**Файлы:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt`, `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt`

**Prompt for developer:**

> Добавить в data-класс `WelcomePage` поле `onProfileConfirmed: ((DeviceProfileType) -> Unit)? = null` рядом с `onProfileSelected`. В `WelcomeActivity` при сборке страницы профилей передать в него лямбду, которая применяет выбор через `viewModel.onProfileSelected(type)` и перелистывает вперёд существующим `flipPage(forward = true)`.

**Why:**

Без колбэка подтверждения страница профилей не имеет доступа к перелистыванию (§2.3), а повторный тап некуда направить. Переиспользование `flipPage` вместо прямой записи в `viewPager.currentItem` сохраняет единственную проверку границы страниц, поэтому подтверждение на последней странице не уводит пейджер за край.

**Verification:**

- `Grep` - `onProfileConfirmed` присутствует в `WelcomePagerAdapter.kt` и в `WelcomeActivity.kt`.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

---

### Step 3 - Подключить повторный тап на странице профилей Welcome

**Файлы:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/holders/ProfilesPageViewHolder.kt`

**Prompt for developer:**

> Сохранять `page.onProfileConfirmed` в поле holder-а и передавать его в `DeviceProfileTileAdapter` как `onReselect`. После смены выделения в `updateSelection` подтягивать выбранную плитку в видимую область через `rvProfiles.smoothScrollToPosition(index)`, чтобы раскрывшееся описание не уходило под нижний край.

**Why:**

Первый симптом (§1) - описание нельзя дочитать; если раскрытая плитка окажется частично за нижним краем сетки, правка Step 1 не решает исходную жалобу. `smoothScrollToPosition` доскраливает минимально и ничего не делает, когда элемент уже виден целиком, поэтому существующий инвариант holder-а «не дёргать сетку после пользовательского выбора» не нарушается.

**Verification:**

- `Grep` - `onReselect` присутствует в `ProfilesPageViewHolder.kt`.
- `Grep` - `smoothScrollToPosition` присутствует в `ProfilesPageViewHolder.kt`.
- `.\a.ps1 fk` - exit 0.

**Status:** `[x]` done

---

### Step 4 - Обновить устаревший комментарий разметки плитки

**Файлы:** `app_v2/src/main/res/layout/item_device_profile_tile.xml`

**Prompt for developer:**

> Заголовочный комментарий описывает описание плитки как строго двухстрочное. Переписать его так, чтобы две строки были свёрнутым состоянием, а выбранная плитка раскрывалась полностью в рантайме.

**Why:**

Комментарий рядом с изменённым поведением - требование Rule 8/9 (комментарии в затронутой области читаются как требования); оставленный как есть, он утверждает инвариант, который Step 1 отменяет.

**Verification:**

- `Grep` - в `item_device_profile_tile.xml` нет строки, утверждающей «two-line description» как безусловный инвариант.
- `.\a.ps1 fr` - exit 0.

**Status:** `[x]` done

---

### 3.2 Implementation State

Все четыре шага выполнены 2026-08-04. Изменённые файлы:

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/profile/DeviceProfileTileAdapter.kt` - раскрытие описания выбранной плитки, опциональный `onReselect`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomePagerAdapter.kt` - поле `onProfileConfirmed` в `WelcomePage`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/WelcomeActivity.kt` - лямбда подтверждения: применить выбор и `flipPage(forward = true)`.
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/welcome/holders/ProfilesPageViewHolder.kt` - проброс `onReselect`, `renderedSelected`, `revealTile`.
- `app_v2/src/main/res/layout/item_device_profile_tile.xml` - комментарий разметки.

Сборка: `.\a.ps1 fc` - exit 0; `.\a.ps1 fk` после вставки probe-тегов - exit 0.

Probe-теги на время `BlockNeedUserTest` (три точки входа изменённых потоков): тап по плитке (`DeviceProfileTileAdapter`), подтверждение повторным тапом (`WelcomeActivity`), подтягивание раскрытой плитки в видимую область (`ProfilesPageViewHolder`).

---

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1235 (`welcome-device-profile-auto-next`, Draft, пакет 31) - тот же жест на той же странице. S1235 просит «один тап = выбор и далее»; захваченный текст S1383 новее и уточняет: первый тап выбирает и раскрывает описание, повторный подтверждает и листает дальше. Для предвыбранного рекомендованного профиля обе формулировки совпадают - первый же тап по нему листает дальше.

---

## 4. Проверка

- Сборка: `.\a.ps1 fk` - exit 0 после каждого шага с кодом; `.\a.ps1 fc` - exit 0 после Step 4.
- Закрытие: `scripts/post-change.ps1 -Files "<изменённый набор>" -ChangeType Mixed -ScopeToFile` - вердикт `post-change: PASS`.
- На устройстве (Welcome, портрет и ландшафт): описание выбранной плитки видно целиком; повторный тап по ней переводит на следующую страницу онбординга.
- Регресс диалога профиля в Настройках: тап по плитке по-прежнему выбирает профиль и закрывает диалог одним нажатием.

---

## Revision History

- **2026-08-04** - by `/spec-test-device` (`sdk_gphone64_x86_64`, device: emulator-5554, Android 15 / SDK 35, build 2.60.8041.407-DEBUG)
  - Scenario: `temp/S1383/mobile_test_scenario_20260804_1408.md` · PASS/FAIL/SKIPPED 9/0/0 · Errors in log: 0 from the app process
  - Оба критерия подтверждены в портрете и в ландшафте, плюс регресс диалога Настроек. Ландшафт проверен изменением геометрии (`wm size 2424x1080`), не поворотом.
  - Побочная находка тулинга запаркована: S1387 (`search-log.ps1 -AppOnly` даёт ложноотрицательный результат).

---

## Last Audit

**Date:** 2026-08-04
**Mode:** strategic
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 12 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

### Manual / on-device

- [x] Описание выбранной плитки читается целиком, остальные остаются свёрнутыми - verified on-device 2026-08-04 (портрет: 126px против 86px; ландшафт: то же соотношение)
- [x] Повторный тап по уже выбранной плитке применяет профиль и листает дальше - verified on-device 2026-08-04 (обе ориентации, probe `confirm=true lines=2147483647` + переход на следующую страницу)
- [x] Раскрывшаяся плитка не уходит под нижний край - verified on-device 2026-08-04 (в ландшафте обрезанная плитка 184px стала видна целиком, 255px)
- [x] Регресс диалога профиля в Настройках: выбор и закрытие одним тапом - verified on-device 2026-08-04 (`onReselect` там не задан, тап ушёл прежним путём)
- [x] Сборка и гейты: `.\a.ps1 fc` exit 0, `.\a.ps1 fk` exit 0 (до и после снятия проб), `post-change: PASS`

**Запарковано вне объёма:** S1387 - `search-log.ps1 -AppOnly` даёт ложноотрицательный результат на пути верификации.
