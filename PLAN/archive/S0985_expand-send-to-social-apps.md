# Стратегическая спецификация: S0985 - Расширение списка социальных приложений «Отправить в..»

**Ticket:** S0985
**Status:** Archived
**Priority:** 50
**Date:** 2026-07-10
**Tier:** 2 - Easy (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-07-10

> **Scope:** COMPACT. Стратегические решения и тактические шаги для локального расширения существующего share-flow.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - implementation through `/spec-all`.
- **Goal / expected outcome:** Provided by user - расширить список «Отправить в..» приложениями Viber, TikTok, WhatsApp, Facebook Messenger и другими социальными приложениями.
- **Local anchor:** Provided by user - S0985 и существующий список «Отправить в..».
- **Scope boundaries / forbidden areas:** Delegated by user - расширить существующий Android share-flow без новых экранов, схемы данных и изменений Wear OS; read-only зоны не затрагивать.
- **Done / success signal:** Delegated by user - установленные Viber, TikTok и Messenger появляются отдельными целями и запускаются с текущим share payload; WhatsApp продолжает работать; системный пункт «Другие приложения» сохраняется.
- **Autonomy rule:** Delegated by user - `/spec-all` разрешает агенту принимать обратимые решения с явными предположениями.
- **UI decisions / delegation:** Delegated by user - использовать текущие строки bottom sheet, overflow и settings без новой раскладки; показывать только установленные приложения, фиксированный порядок, системный fallback последним.

## 0.1 Исходный запрос

Расширить список "отправтиь в.." приложений длугими соцсетями (Viber, TikTok, Whatsapp, Facebook messenger и другими

**Attachments:** none.

## 1. Проблема

В приложении уже есть отдельные цели Telegram, WhatsApp и Instagram, но отсутствуют явно запрошенные Viber, TikTok и Messenger. Универсальный пункт «Другие приложения» открывает системный Android Sharesheet, однако дополнительные популярные приложения нельзя закрепить как отдельные управляемые цели в существующем меню и настройках.

## 2. Цели

1. Добавить отдельные цели Viber, TikTok и Messenger в существующий список «Отправить в..».
2. Показывать каждую новую цель только при наличии совместимого установленного приложения.
3. Отправлять текущий локальный share payload непосредственно в выбранное приложение с возвратом к системному выбору при ошибке запуска.
4. Сохранить WhatsApp без регрессий и оставить «Другие приложения» универсальным способом выбрать любые остальные социальные приложения.
5. Сохранить единое поведение bottom sheet, overflow и настроек во всех ориентациях.
6. Показывать уникальную узнаваемую fallback-иконку для каждой курируемой социальной цели, пока launcher icon приложения недоступен.

**Non-goals:**

- Создание собственной копии Android Sharesheet или автоматическое перечисление всех установленных приложений.
- Программный выбор адресата, чата, публикации или аккаунта внутри стороннего приложения.
- Поддержка Wear OS.
- Добавление Facebook как отдельной цели в первой итерации: слово «другими» покрывается системным пунктом «Другие приложения», чтобы не раздувать постоянный список произвольным набором брендов.

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Явно поддержать Viber, TikTok и Facebook Messenger.
2. Не потерять уже существующую поддержку WhatsApp.
3. Оставить возможность отправки в любые другие социальные приложения.

### 3.2 Жёсткие ограничения

- **Flavor:** все варианты `app_v2`, где доступен текущий unified Send to flow.
- **API level:** сохранить текущий minSdk и корректную package visibility на Android 11+.
- **Wear OS:** без изменений.
- **Производительность:** не сканировать полный список установленных приложений; проверять только известные package ids существующим механизмом.
- **Совместимость данных:** существующие ids и сохранённые настройки не менять; новые цели выключены по умолчанию по действующему паттерну социальных приложений.
- **Локализация:** новые брендовые строки не добавлять; отображать label установленного приложения, использовать существующий нейтральный fallback.
- **Доступность:** текущие focus, TalkBack и touch-target контракты строк меню сохраняются.

### 3.3 Owner inputs (Approval gate)

- **UI placement:** Reuse the existing dynamic Send to rows in bottom sheet, overflow and settings; no layout changes.
- **Visibility:** Show a target in Send to only when it is enabled, installed and compatible with the current media type.
- **Fallback:** Keep Other apps last and use the existing system chooser fallback when a targeted launch fails.
- **Scope:** Add the three explicitly missing targets; WhatsApp is already present and all uncurated apps remain available through Other apps.
- **Related tickets:** S0459, S0463, S0478.

## 4. Контекст текущей архитектуры

Unified Send to flow использует реестр целей, проверку доступности установленного package, фиксированный порядок, отдельный обработчик отправки и универсальный системный fallback. Одна и та же модель питает bottom sheet, overflow и группу переключателей в настройках, поэтому новые декларации автоматически появляются на всех существующих поверхностях без изменения layout.

WhatsApp уже реализован как отдельная цель. Следовательно, S0985 расширяет существующий пакетно-ориентированный паттерн, а не создаёт новый share-механизм.

## 5. Решение

- Зарегистрировать Viber, Messenger и TikTok как package-backed цели, выключенные по умолчанию и видимые только при установленном приложении.
- Разместить Viber и Messenger рядом с мессенджерами, TikTok рядом с визуальными социальными приложениями, сохранив системный fallback последним.
- Использовать общий обработчик package-targeted `ACTION_SEND` для новых целей.
- Ограничить TikTok изображениями, GIF и видео; для всех новых целей использовать single-item семантику до подтверждения batch-контракта сторонних приложений.
- Объявить точные package ids в package visibility manifest.
- Дополнить unit-тест фиксированного порядка и feature inventory.

## 6. Research items

1. **Текущее покрытие**
   - **Результат:** WhatsApp уже зарегистрирован; Telegram и Instagram также существуют; пункт «Другие приложения» открывает системный chooser.
   - **Статус:** Resolved.
2. **Package visibility**
   - **Результат:** точные package ids нужны для существующей проверки установки на Android 11+; Viber - `com.viber.voip`, Messenger - `com.facebook.orca`, TikTok - `com.zhiliaoapp.musically` и региональный `com.ss.android.ugc.trill`.
   - **Статус:** Resolved.
3. **UI placement**
   - **Результат:** layout менять не нужно; реестр автоматически обслуживает portrait, landscape, bottom sheet, overflow и settings. Неустановленные приложения скрыты, системный fallback остаётся последним.
   - **Статус:** Resolved.

## 7. Риски

- Стороннее приложение может не принять конкретный MIME - targeted launch вернётся к системному chooser по действующему fallback.
- Package id регионального клиента может отличаться - TikTok получает два известных варианта; остальные варианты остаются доступны через «Другие приложения».
- Длинный список может снизить удобство - новые цели выключены по умолчанию и появляются в меню только после включения и при установленном приложении.

## 8. Влияние на пользователя

Feature inventory получает отдельные записи для Viber, Messenger и TikTok. Публичные showcase-файлы `docs/FEATURES*` не редактируются до release-flow по текущему проектному правилу.

## 9. Архитектурные решения

**ADR-1: Курируемые цели плюс системный fallback**

- **Решение:** добавить только явно отсутствующие Viber, Messenger и TikTok, а «и другие» обслуживать через существующий системный пункт.
- **Альтернатива:** динамически дублировать все приложения из Android Sharesheet внутри приложения.
- **Почему:** Android рекомендует системный Sharesheet для полного списка, а текущий проектный UI предназначен для небольшого настраиваемого набора закреплённых целей.

**ADR-2: Без брендовых ресурсов**

- **Решение:** получать label и icon из установленного package, сохранив существующие нейтральные fallback-ресурсы.
- **Почему:** это соответствует текущей архитектуре и не требует новых локализаций или брендовых assets.

## 10. Связи

- S0459 - unified Send to menu foundation.
- S0463 - настройки целей отправки.
- S0478 - overflow submenu.

## 11. Критерии готовности

1. При установленном и включённом Viber пользователь видит Viber в «Отправить в..» и может передать один файл.
2. При установленном и включённом Messenger пользователь видит Messenger и может передать один файл.
3. При установленном и включённом TikTok пользователь видит TikTok только для изображения, GIF или видео и может передать первый файл.
4. Неустановленные новые приложения не появляются в меню.
5. WhatsApp остаётся в реестре без изменения поведения.
6. «Другие приложения» остаётся последним и открывает системный chooser для любых остальных совместимых приложений.
7. Compile, targeted unit tests, manifest/resource gates and spec audit pass.
8. Viber, Messenger и TikTok имеют разные узнаваемые fallback-иконки в настройках и меню.

## 12. Тактические фазы

### Phase 01 - Register and dispatch social targets

#### Step 01.1 - Add target declarations, package visibility and order

**Files:**

- `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/ShareTargetModule.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/core/share/ShareTargetRegistry.kt`
- `app_v2/src/main/AndroidManifest.xml`

**Prompt for developer:** Register Viber, Messenger and TikTok with the package ids, defaults, media restrictions and canonical order defined above. Preserve all existing target ids and keep `system_share` last.

**Verification:**

- `Grep` finds target ids `viber`, `messenger` and `tiktok` exactly in the declarations and display order.
- `Grep` finds all four new package visibility declarations.
- No existing WhatsApp package or target declaration is removed.

**Status:** `[x]` done

#### Step 01.2 - Add package-targeted handlers and Hilt map entries

**Files:**

- `app_v2/src/main/java/com/sza/fastmediasorter/core/share/handlers/ConfiguredPackageShareTargetHandler.kt` (New)
- `app_v2/src/main/java/com/sza/fastmediasorter/core/share/di/ShareTargetHandlerModule.kt`

**Depends on:** Step 01.1

**Prompt for developer:** Add a reusable handler for the three new target ids. Resolve the first installed package with the project PackageManager compat helper, dispatch through the existing file-share invoker, and preserve chooser fallback behavior. Bind one configured handler per target id through the existing Hilt map.

**Verification:**

- `Grep` finds one handler implementation and three map keys matching the new target ids.
- Every declared target id has a matching handler map entry.
- `Grep` for `android.util.Log` and `System.out` in touched Kotlin files returns zero hits.

**Status:** `[x]` done

### Phase 02 - Tests, inventory and closure

#### Step 02.1 - Cover canonical order

**Files:** `app_v2/src/test/java/com/sza/fastmediasorter/core/share/ShareTargetRegistryTest.kt`

**Prompt for developer:** Extend the registry unit test to prove the canonical ordering of the new messenger/social targets and that `system_share` remains last.

**Verification:**

- The targeted registry test passes.
- Expected order includes `telegram`, `whatsapp`, `viber`, `messenger`, `instagram`, `tiktok`, then `system_share`.

**Status:** `[x]` done

#### Step 02.2 - Update feature inventory and project catalog

**Files:**

- `docs/ALL_FEATURES.jsonl`
- generated `dev/CATALOG/` outputs

**Depends on:** Step 02.1

**Prompt for developer:** Add active feature records for the Viber, Messenger and TikTok receivers using the repository helper, validate the inventory, sync the app catalog once, and record one logical dev-log entry.

**Verification:**

- Feature inventory validation passes and contains three S0985 records.
- Catalog sync completes successfully.
- Fast Kotlin compile and targeted unit test pass.

**Status:** `[x]` done

## Phase Done Criteria

- [x] Every tactical step is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` passes.
- [x] `./a.ps1 fk` passes.
- [x] Targeted registry unit test passes (6 tests, 0 failures).
- [x] `./a.ps1 fg` passes for the changed scope.
- [x] `/spec-check S0985` returns Verified.

## Last Audit

**Date:** 2026-07-10
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 18 · WARN 0 · FAIL 0 · MANUAL 3 · EXEMPT 2

### Manual / on-device

- [ ] With Viber installed, enable its Send to toggle and confirm a file opens the app's recipient flow.
- [ ] With Messenger installed, enable its Send to toggle and confirm a file opens the app's recipient flow.
- [ ] With a compatible short-video app installed, enable its toggle and confirm image/video dispatch plus first-file behavior for multi-selection.
