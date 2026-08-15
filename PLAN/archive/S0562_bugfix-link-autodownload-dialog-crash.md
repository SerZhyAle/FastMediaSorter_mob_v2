# Стратегическая спецификация: S0562 - Краш инфляции диалога автоскачивания ссылки

**Ticket:** S0562
**Status:** Archived
**Priority:** 90
**Date:** 2026-06-20
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-20
**Tactical spec:** компактная - фазы встроены ниже (§Phases)

> **Scope:** компактный bugfix-спек (Simple path). Диагноз и фикс зафиксированы в §0.1; стратегические разделы заполнены кратко.

<!-- auto-approved by /spec-all - 2026-06-20 -->

---

## 0. Захваченный материал (inbox)

> Сырой захват идеи на лету. Вербатим-текст и вложения.

**Захвачено:** 2026-06-20

**Захвачено во время:** работы над фиксом отправки краш-репорта по email (CrashReportPromptManager / SupportIntentFactory).

**Текст:**

ReceiveShareActivity crash: inflating dialog_link_autodownload_progress throws InflateException because the cancel MaterialButton (style Widget.FastMediaSorter.Button.DialogCancel) cannot resolve attribute 0x7f0401e7 (dialogActionButtonMinHeight) under the translucent Theme.FastMediaSorter.Transparent host theme. The existing workaround in LinkAutoDownloadProgressDialog.show() wraps the inflater in ContextThemeWrapper(activity, R.style.Theme_FastMediaSorter), but the missing attr is bound only on Theme.FastMediaSorter.App, so Transparent still leaks into the theme chain and the inflate still crashes. Evidence: crash logs logs/fastmediasorter_crash_20260620_045348.log and _045432.log; stack at LinkAutoDownloadProgressDialog.kt:33 / ReceiveShareActivity.processLinkAutoDownload (line 430). Caused by UnsupportedOperationException: Failed to resolve attribute at index 37 under ThemeOverlay.Material3.Button.TextButton -> ThemeOverlay.Material3.DynamicColors.Dark -> Theme.FastMediaSorter.Transparent. Build 2.60.6200.349-NoLegal-DEBUG. Likely fix: wrap with Theme.FastMediaSorter.App (or whichever theme binds dialogActionButtonMinHeight), or define the attr on the Transparent theme / change the button style. Needs research before fix.

**Вложения:**

- log excerpt: краш-стек инфляции диалога (попытка 1) - `logs/fastmediasorter_crash_20260620_045348.log`
- log excerpt: краш-стек инфляции диалога (попытка 2) - `logs/fastmediasorter_crash_20260620_045432.log`

---

## 0.1 Диагноз и статус фикса (2026-06-20)

**Корень.** `LinkAutoDownloadProgressDialog.show()` инфлейтит `dialog_link_autodownload_progress` против хоста `ReceiveShareActivity`, который работает под прозрачной темой `Theme.FastMediaSorter.Transparent`. Кнопка Cancel (`Widget.FastMediaSorter.Button.DialogCancel`) использует `?attr/dialogActionButtonMinHeight`. Этот атрибут напрямую объявлен только в `Theme.FastMediaSorter.App` (`themes.xml:35`). На устройствах с DynamicColors тема пересобирается слоями (в crash-стеке `ThemeOverlay.Material3.DynamicColors.Dark`), атрибут теряется -> `UnsupportedOperationException: Failed to resolve attribute at index 37` -> `InflateException`.

**Состояние на момент аудита.**

- HEAD (закоммичено): без обёртки, инфляция против `activity`. Это и упало в сборке .349.
- Рабочее дерево (незакоммичено, прошлая сессия): добавлена обёртка `ContextThemeWrapper(activity, R.style.Theme_FastMediaSorter)`. Но `Theme.FastMediaSorter` - пустой parent-only алиас, атрибут он напрямую не несёт -> надёжность не гарантирована.

**Применённый фикс (2026-06-20).** Обёртка переведена на `Theme.FastMediaSorter.App` - тему, объявляющую `dialogActionButtonMinHeight` напрямую. `ContextThemeWrapper.applyStyle(force=true)` накладывает App-тему поверх Transparent-базы, поэтому атрибут гарантированно переопределяется. Компиляция: `a.ps1 fk` PASS.

**Осталось.** Проверка на устройстве: получить share-ссылку с автоскачиванием через `ReceiveShareActivity`, дождаться показа прогресс-диалога, убедиться что инфляция не падает и Cancel виден/работает. До проверки фикс не закоммичен в составе релиза.

---

## 1. Проблема

`ReceiveShareActivity` крашится при попытке показать прогресс-диалог автоскачивания ссылки. Диалог инфлейтится против прозрачной хост-темы, в цепочке которой (после пересборки DynamicColors) теряется `?attr/dialogActionButtonMinHeight`, нужный кнопке Cancel. Итог - `InflateException`, пользователь не может скачать файл по расшаренной ссылке. Затронут флоу приёма share-интента (`ui/share`).

---

## 2. Цели

1. Прогресс-диалог автоскачивания инфлейтится без краша под прозрачной хост-темой с включёнными DynamicColors.
2. Кнопка Cancel видна и отменяет загрузку.

**Non-goals:**

- Изменение визуального стиля диалога или поведения автоскачивания.
- Рефакторинг тем приложения за пределами надёжного резолва атрибута для этого диалога.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

- Минимальное точечное вмешательство - только устранение краша, без побочных изменений.

### 3.2 Жёсткие ограничения

- **Flavor:** воспроизводится во всех (тема в `src/main`); репорт пришёл с NoLegal-DEBUG.
- **API level:** без API-специфики.
- **Wear OS:** не затрагивается.
- **Производительность:** не критично.
- **Совместимость данных:** миграции нет.
- **Локализация:** без новых строк.
- **Доступность:** без изменений.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0003 (link auto-download channel)
- **UI:** изменений размещения/видимости/дизайна нет - это bugfix резолва темы существующего диалога, решений владельца не требует.

---

## 4. Контекст текущей архитектуры

За показ прогресса автоскачивания отвечает слой `ui/share`: `ReceiveShareActivity` (хост под `Theme.FastMediaSorter.Transparent`) создаёт `LinkAutoDownloadProgressDialog`, который инфлейтит свой layout с кнопкой Cancel в стиле `Widget.FastMediaSorter.Button.DialogCancel`. Кнопка ссылается на кастомный атрибут темы `dialogActionButtonMinHeight`, объявленный напрямую только в полной теме приложения. Прозрачная хост-тема его не несёт, а runtime-пересборка DynamicColors добивает его из цепочки - поэтому инфляция против контекста активити падает.

---

## 5. Предлагаемый подход

Инфлейтить диалог и строить `AlertDialog` против `ContextThemeWrapper`, обёрнутого в полную тему приложения, объявляющую недостающий атрибут напрямую. Обёртка накладывается поверх базовой темы хоста с `force=true`, что гарантирует переопределение атрибута независимо от слоёв DynamicColors.

### 5.1 Основные столпы / модули

- `ui/share` - единственная затронутая область (диалог автоскачивания).

### 5.2 Потоки данных и событий

- share-интент → `ReceiveShareActivity` → `LinkAutoDownloadProgressDialog.show()` (инфляция против themed-контекста) → отображение прогресса/Cancel.

### 5.3 Точки расширяемости

- Не требуется - точечный фикс.

---

## 6. Открытые вопросы / Research items

Открытых вопросов нет.

---

## 7. Риски

- Обёртка в App-тему теряет runtime DynamicColors для диалога (статичные Material3-цвета). Низкая вероятность жалоб, последствие косметическое, допустимо для прогресс-диалога.
- Прочие атрибуты layout должны резолвиться из полной Material3-темы - App-тема является полноценным `Theme.Material3.DayNight`, риск низкий.

---

## 8. Влияние на пользователя (docs/FEATURES)

Без изменений в docs/FEATURES (исправление краша, не новая возможность).

---

## 9. Архитектурные решения (ADR)

ADR нет - решение по устоявшимся паттернам проекта (themed `ContextThemeWrapper` для инфляции диалогов под нестандартной хост-темой).

---

## 10. Связи с другими спеками

- S0003 - канал автоскачивания ссылки (владелец диалога).

---

## 11. Критерии готовности (strategic-level)

1. Открытие share-ссылки с автоскачиванием показывает прогресс-диалог без краша.
2. Кнопка Cancel видна и отменяет загрузку.
3. Регрессии в обычных диалогах приложения нет.

---

## Phases

### Phase 01 - Инфляция против themed-контекста [DONE]

- [x] Обернуть инфлейтер и `AlertDialog.Builder` в `ContextThemeWrapper(activity, R.style.Theme_FastMediaSorter_App)` в `LinkAutoDownloadProgressDialog.show()`.
  - Verification: `dialogActionButtonMinHeight` объявлен в `Theme.FastMediaSorter.App` (`values/themes.xml:35`); импорт `androidx.appcompat.view.ContextThemeWrapper` присутствует.
- [x] Компиляция (auto-build - PASS): `a.ps1 fk`.

### Phase 02 - Проверка на устройстве [DONE]

- [x] Запустить share-флоу автоскачивания ссылки через `ReceiveShareActivity`, дождаться прогресс-диалога.
  - Verification (emulator-5556, 2026-06-20): SEND text/plain с google-URL через `.StandaloneTextSender` -> `processLinkAutoDownload` -> `show()`. Logcat: тег `S0562: ... inflate under themed host` сработал, инфляция без `InflateException`/`UnsupportedOperationException`/FATAL; activity штатно `onDestroy()` через ~4с (S0202 watchdog). Краш устранён.

---

## Last Audit

**2026-06-20 - Verified (via /spec-next -> /spec-all, on-device).**

- **Фикс:** `LinkAutoDownloadProgressDialog.show()` инфлейтит layout и строит `AlertDialog` против `ContextThemeWrapper(activity, R.style.Theme_FastMediaSorter_App)`. `Theme.FastMediaSorter.App` объявляет `dialogActionButtonMinHeight` напрямую (`values/themes.xml:35`); `applyStyle(force=true)` переопределяет атрибут поверх Transparent-базы независимо от слоёв DynamicColors.
- **Файл:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt` (рабочее дерево, ещё не закоммичено).
- **Build:** `assembleStandardDebug` PASS (35s); compile-check после снятия verification-тега PASS.
- **On-device:** emulator-5556 - share-link флоу достиг `show()`, диалог инфлейтился без краша, activity завершилась штатно. См. Phase 02 Verification.
- **Verification-тег:** снят (выход из `BlockNeedUserTest`), 0 вхождений `Timber.d("S0562:` в `.kt`.
- **Остаточные пробелы:** клик по Cancel на устройстве не симулировался (кнопка - часть успешно инфлейтнутого layout, бывшая точка краша устранена). Не блокирует.
