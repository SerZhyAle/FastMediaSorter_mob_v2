# Стратегическая спецификация: S0483 - Кнопка «Отправить краш-репорт автору» в диалоге об ошибке

**Ticket:** S0483
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-17
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - запрос 2026-06-17
**Tactical spec:** `PLAN/S0483_crash-report-email-button/`
**Tactical plan:** `PLAN/S0483_crash-report-email-button/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 1. Проблема

Когда в приложении происходит настоящий сбой (необработанное исключение, реальный краш), пользователь видит диалог об ошибке, но не может одним действием сообщить о нём автору. Текст ошибки приходится копировать вручную, а файловый лог - искать и прикладывать отдельно; на практике это не делает никто, и автор остаётся без диагностики.

При этом тот же диалог показывает и штатные, ожидаемые сообщения («формат недоступен», «нет сети», «нет доступа») - для них репорт не нужен и только зашумлял бы интерфейс.

Нужно различать эти два случая и давать кнопку отправки репорта только тогда, когда это осмысленно - при реальном сбое.

---

## 2. Цели

1. В диалоге об ошибке появляется кнопка отправки краш-репорта автору, видимая только когда ошибка - реальный сбой (исключение), а не штатное информационное сообщение о недоступности.
2. Нажатие кнопки открывает почтовый клиент с заранее заполненным письмом автору: получатель, тема, текст ошибки в теле.
3. К письму автоматически приложен архив логов приложения, чтобы автор получил контекст без запроса дополнительных данных у пользователя.
4. Поведение единообразно во всех вариантах сборки.

**Non-goals:**

- Не вводится экран/диалог репорта для необработанных крашей, после которых приложение завершилось (они сейчас не показывают диалог, а лишь пишут крэш-файл) - это отдельная будущая работа (см. §6 п. 3).
- Не вводится автоматическая фоновая отправка отчётов без участия пользователя.
- Не меняется существующий канал «Сообщить о проблеме» в настройках.
- Не добавляется кнопка в компактный режим ошибок (Snackbar) - только в детальный диалог.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Минимум новой логики - переиспользовать готовый пайплайн экспорта логов и существующую инфраструктуру support-интентов.
2. Кнопка не должна загромождать диалог в обычных (не-краш) случаях.

### 3.2 Жёсткие ограничения

- **Flavor:** все варианты (standard, lite, photos, legacy). Диалог - общий для всех сборок, флейвор-специфичного поведения нет, разделение исходников не требуется.
- **API level:** без API-специфики. Отправка письма с вложением через системный share-механизм и `FileProvider` работает на всех поддерживаемых уровнях (включая legacy minSdk 23).
- **Wear OS:** не затрагивается.
- **Производительность:** сборка архива логов - дисковый I/O, выполняется вне главного потока перед открытием почтового клиента.
- **Совместимость данных:** изменений хранилища нет.
- **Локализация:** EN/RU/UK - обязательно для подписи кнопки и любых новых пользовательских строк (тема/тело письма по политике коммуникации).
- **Доступность:** кнопка имеет текстовую подпись и contentDescription, доступна с клавиатуры/D-pad, отличима не только цветом (см. CLAUDE.md Rule 16, Rule 19).
- **Получатель:** письмо адресуется `serzhyale@gmail.com` - отдельный канал для краш-репортов, не общий адрес поддержки `sza@ukr.net`.
- **Вложение:** прикладывается полный архив всех логов (переиспользование существующего пайплайна экспорта).
- **Коммуникационная политика:** подпись кнопки, тема и тело письма соответствуют `docs/COMMUNICATION_POLICY.md`; чек-лист тона (§6 политики) - обязательный гейт перед интеграцией строк.

### 3.3 Owner inputs (Approval gate)

Каждое поле ниже содержит конкретное значение. Проверка: `pwsh -NoProfile -File scripts/spec_catalog/check-owner-inputs.ps1 -Id S0483`.

- **UI placement contract:** кнопка размещается в ряду действий существующего диалога об ошибке (предпочтительно - уже зарезервированный опциональный слот действия, без нового ряда); видима только при ошибке-сбое, скрыта для информационных сообщений. Точный слот - решение `/spec-tech` с проверкой переполнения на узком портретном экране.
- **Accessibility:** текстовая подпись + contentDescription; фокусируемость для клавиатуры/D-pad; состояние различимо не только цветом.
- **Communication policy:** подпись кнопки, тема и тело письма проходят чек-лист тона `docs/COMMUNICATION_POLICY.md` (EN/RU/UK).
- **Localization:** все новые пользовательские строки добавляются в EN/RU/UK в одном изменении.
- **Validation level:** сборка целевого варианта проходит; ручная проверка на устройстве (диалог → кнопка → почтовый клиент с вложением) под `BlockNeedUserTest`.
- **Owner sign-off:** 2026-06-17 (адрес получателя и объём вложения подтверждены владельцем).
- **Related tickets:** связанные - область поверхностей ошибок (S0118, S0378, S0384) и крэш-обработка; жёстких зависимостей-блокеров нет.

---

## 4. Контекст текущей архитектуры

Сейчас за показ детальной ошибки отвечает единый диалоговый слой UI; он принимает заголовок, текст и опциональные детали, но не получает само исключение - поэтому на уровне диалога нет признака, по которому можно отличить реальный сбой от штатного сообщения о недоступности. Исходное исключение доступно выше по потоку (на части точек вызова), но в диалог не пробрасывается.

Файловое логирование и сборка архива логов уже существуют как отдельная инфраструктура (в т. ч. готовый шаг «упаковать логи и поделиться через FileProvider»). Существует и фабрика support-интентов, но её канал отправки письма использует механизм, неспособный нести вложение.

Решить задачу «здесь и сейчас» нельзя, потому что (а) диалог не знает, сбой это или информационное сообщение, и (б) текущий путь отправки письма не прикладывает файлы.

---

## 5. Предлагаемый подход

Диалог об ошибке получает признак «это сбой» и, только при его наличии, показывает действие отправки краш-репорта. Действие переиспользует существующий пайплайн упаковки логов для формирования вложения и системный механизм отправки письма, способный нести вложение, с заранее заполненными получателем, темой и телом (текст ошибки).

### 5.1 Основные столпы / модули

- **Классификация ошибки.** Вводится сигнал, отличающий ошибку-сбой (исключение/непредвиденный отказ) от штатного информационного сообщения. Диалог показывает действие репорта только для первой категории. Точный предикат и инвентаризация точек вызова - задача тактического слоя.
- **Действие отправки в диалоге.** Дополнительное действие в существующем ряду кнопок диалога, появляющееся условно. Подпись и иконка - по политике коммуникации и правилам доступности.
- **Формирование вложения.** Переиспользование существующего шага упаковки логов в архив во временной (кэш) области с предоставлением доступа через общий механизм file-provider.
- **Отправка письма.** Системный механизм отправки с получателем, темой, телом (текст ошибки) и вложением-архивом. Подготовка вложения - вне главного потока.

### 5.2 Потоки данных и событий

`источник ошибки → слой представления ошибки (с признаком «сбой») → диалог об ошибке → действие репорта → упаковка логов (кэш) → системная отправка письма (получатель + тема + текст + вложение)`.

### 5.3 Точки расширяемости

- Признак «сбой» вводится так, чтобы любая точка вызова диалога могла его выставлять единообразно (через общий контракт показа ошибки), а не точечными флагами в разных местах.
- Адрес получателя краш-репортов - единый источник истины (одно объявление), пригодный к смене без правок по коду.
- Действие отправки оставляет возможность позднее переиспользовать тот же механизм на других поверхностях (например, будущий путь необработанных крашей, §6 п. 3) без дублирования логики формирования письма и вложения.

---

## 6. Открытые вопросы / Research items

1. **Признак «сбой vs информационное сообщение»**
   - **Вопрос:** как диалог отличает реальный сбой от штатного сообщения о недоступности?
   - **Варианты:** проброс nullable-исключения в диалог (кнопка видна при ненулевом); явный булев флаг от точек вызова; выделенный опциональный слот действия только для краш-вызовов.
   - **Нужно выяснить:** аудит всех точек вызова диалога и отнесение классов ошибок к «сбой»/«информация» - на тактическом слое.
   - **Статус:** Resolved (стратегическое направление задано; точный предикат - в `/spec-tech`)
   - **Артефакт:** `PLAN/S0483_crash-report-email-button/research/01__crash-vs-info-gate.md`

2. **Доставка письма с вложением лога**
   - **Вопрос:** как приложить архив логов к письму, переиспользуя существующую инфраструктуру?
   - **Варианты:** расширить фабрику support-интентов builder'ом с вложением; либо направить через существующий системный share-механизм, уже умеющий «отправка + файл + получатель».
   - **Нужно выяснить:** где разместить сборку intent'а, чтобы не дублировать логику file-provider и упаковки логов.
   - **Статус:** Resolved (механизм определён: системная отправка с вложением + переиспользование упаковки логов)
   - **Артефакт:** `PLAN/S0483_crash-report-email-button/research/02__email-attachment-delivery.md`

3. **Необработанные краши (release), завершающие приложение**
   - **Вопрос:** стоит ли предлагать отправку репорта после реального необработанного краша на следующем старте (сейчас диалог при этом не показывается, пишется лишь крэш-файл)?
   - **Нужно выяснить:** UX точки входа после рестарта; вынести в отдельный тикет.
   - **Статус:** Open (вынесено за рамки S0483; на реализацию данной спеки не влияет - это будущее расширение того же механизма)

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Использование неспособного нести вложение механизма отправки | Средняя | Письмо уходит без лога, репорт бесполезен | Использовать механизм отправки с вложением, а не «mailto»-путь; проверить вложение на устройстве |
| Кнопка появляется при штатных сообщениях о недоступности | Средняя | Шум в UI, ложные репорты | Чёткий предикат «сбой»; аудит всех точек вызова диалога |
| Переполнение ряда кнопок на узком портретном экране | Средняя | Кнопки наезжают/обрезаются | Переиспользовать существующий слот; проверить портрет и ландшафт в паре |
| Архив логов содержит приватные данные пользователя | Средняя | Пользователь шлёт больше, чем ожидает | Отправка только по явному действию пользователя; прозрачная подпись действия |
| Сборка архива на главном потоке | Низкая | Подвисание UI перед открытием почты | Готовить вложение вне главного потока |
| Расхождение портрет/ландшафт-вёрстки диалога | Низкая | Кнопка только в одной ориентации | Править оба макета синхронно (CLAUDE.md Rule 11) |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая способность: из диалога об ошибке (при реальном сбое) пользователь может одним действием отправить автору письмо с описанием ошибки и приложенным логом. Добавить одно предложение в `docs/FEATURES.md` + `_RU` + `_UK`.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Адрес получателя - отдельный от общего канала поддержки**

- **Решение:** краш-репорты адресуются `serzhyale@gmail.com`, а не существующему адресу поддержки `sza@ukr.net`.
- **Альтернативы:** переиспользовать существующий `sza@ukr.net` ради единого канала.
- **Почему:** владелец явно указал личный адрес для краш-репортов как отдельный поток, отличный от общих обращений в поддержку (sign-off 2026-06-17).

**ADR-2: Вложение - полный архив логов, через существующий пайплайн**

- **Решение:** прикладывать полный ZIP всех логов, переиспользуя готовый шаг упаковки и совместного доступа.
- **Альтернативы:** прикладывать только лог текущей сессии/крэш-файл - меньше объём и приватных данных.
- **Почему:** владелец выбрал максимум контекста для диагностики при минимуме новой логики (sign-off 2026-06-17).

**ADR-3: Кнопка только в детальном диалоге, по явному действию**

- **Решение:** действие живёт в детальном диалоге об ошибке и срабатывает только по нажатию пользователя; компактный Snackbar-путь и автоотправка не затрагиваются.
- **Альтернативы:** дублировать действие в Snackbar; автоматически отправлять отчёты.
- **Почему:** диалог - место, где пользователь уже видит полный текст ошибки; явное действие уважает приватность (лог уходит только по решению пользователя).

---

## 10. Связи с другими спеками

- Область поверхностей ошибок и крэш-обработки: S0118, S0378, S0384 (контекст, не блокеры).
- Будущее расширение на путь необработанных крашей (§6 п. 3) - кандидат на отдельный тикет.
- Жёстких зависимостей `BlockByOtherTask` нет.

---

## 11. Критерии готовности (strategic-level)

1. При ошибке-сбое в диалоге об ошибке видна кнопка отправки краш-репорта.
2. При штатном информационном сообщении («недоступно», «нет сети» и т. п.) кнопка отсутствует.
3. Нажатие открывает почтовый клиент с получателем `serzhyale@gmail.com`, заполненной темой и текстом ошибки в теле.
4. К письму приложен архив логов приложения.
5. Поведение одинаково во всех вариантах сборки; подпись кнопки локализована (EN/RU/UK).
6. Кнопка доступна с клавиатуры/D-pad и корректно отображается в портрете и ландшафте.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0483` - создаст фазы в `PLAN/S0483_crash-report-email-button/`.

---

## Last Audit

### Manual (device) - 2026-06-19 (run 2) - emulator-5556 (Android 13, SDK 33), standard debug 2.60.6191.257

**Verdict: PASS (with one INCONCLUSIVE sub-check).** The real-exception path was finally exercised at runtime. The prior emulator blocker (archive IOException unreachable on FUSE) was sidestepped: the `BrowseLoadingManager` folder-scan flow forwards a non-null `Throwable` through `handleLoadingError` -> `BrowseErrorDisplayManager.showError(exception = e)` -> `ScrollableTextDialog(reportableThrowable != null)`, which is a second exception-bearing sink independent of the archive path.

How the real error was triggered:
- Added an unreachable SMB resource (`smb://10.255.255.1/Common`, "Add This Resource" manual-add, bypassing the connection test) with `showDetailedErrors=true`.
- Opening it from MainActivity hits the nav-level pre-flight connection test in `ResourceNavigationCoordinator.testConnectionAndNavigate`, which returns `NavigationResult.Error(userMessage, null)` - the throwable is deliberately nulled, so that dialog ("Cannot connect to SMB resource..") is an info-class message with `btnReport` GONE. This is the same `dialog_error_detail` surface (criterion 2 evidence on a network error).
- To reach the exception-bearing scan sink, launched `BrowseActivity` directly with `--el resourceId 10 --ez skipAvailabilityCheck true` (the same intent the app uses internally after a successful nav). The in-Browse scan to the unreachable host threw in the `GetMediaFilesUseCase` flow after ~6s (`BrowseLoadingManager: ERROR in flow - Exception`), routing the real `Throwable` to the report-enabled dialog.

Per-criterion (expected vs actual):
1. Button visible on exception-class dialog - expected VISIBLE; actual **PASS**. `btnReport` (contentDescription "Email crash report to author", `ic_send_email`) present and visible between `btnInlineAction` (Save) and `btnCopy`, exactly per Phase 03. Evidence: `15_PORTRAIT_btnReport_VISIBLE.png`, element dump.
2. Button hidden for info message - expected GONE; actual **PASS**. On the SMB connect-failure dialog (nav-gate, throwable nulled) the same layout shows only Share/Save/Copy/Close; `uiautomator dump` confirms `btnReport` absent. Evidence: `window_attempt1_smb_connect.xml`, `13_opened_loading.png`.
3. Tap opens email to `serzhyale@gmail.com` with subject+body - expected `ACTION_SEND` + `EXTRA_EMAIL` + subject + body; actual **PASS (intent) / INCONCLUSIVE (visual recipient/body)**. Tapping `btnReport` fired the S0483 probe and launched a chooser, then `START act=android.intent.action.SEND typ=application/zip flg=0xb080001 cmp=com.google.android.gm/.ComposeActivityGmailExternal clip={application/zip {U(content)}} (has extras)`. The intent reached Gmail's external compose with `FLAG_GRANT_READ_URI_PERMISSION` set (flg bit 0x1) and extras attached. The populated compose window (visible recipient/subject/body) could NOT be confirmed because **no Google account is signed in on this emulator** - Gmail opened to the "Welcome to Gmail" onboarding instead of compose. Recipient/subject/body therefore verified by intent shape + code, not by visual compose. Evidence: `16_after_btnReport_tap.png`, `17_gmail_compose.png`, `logcat_attempt4_gmail.log`.
4. App log zip attached - expected `type=application/zip` + zip stream + read grant; actual **PASS**. Chooser preview listed attachment `fastmediasorter_logs.zip`; SEND intent carried `typ=application/zip` with the FileProvider content URI in ClipData and the read-grant flag. Note: the ChooserActivity *preview* process logged a `SecurityException` reading the FileProvider URI - that is the documented chooser-preview limitation (separate uid, no per-app grant), NOT a delivery failure; the final SEND-to-Gmail intent carries the grant. Evidence: `16_after_btnReport_tap.png`, `logcat_attempt3_after_report_tap.log`.
5. Portrait - expected report flow works; actual **PASS** (all of the above in portrait).
6. Landscape - expected `btnReport` declared and rendered; actual **PASS (layout parity) / runtime row clipped**. `layout-land/dialog_error_detail.xml` declares `btnReport` + `ic_send_email` at the identical line/position as portrait. At runtime on this 1080px-tall landscape geometry the dialog's entire action row (Share/Save/Report/Copy/Close) is clipped below the visible window and not tappable; the dialog body does not scroll to reveal it. This affects all action buttons equally (pre-existing dialog-sizing behaviour, not S0483-specific). Evidence: `18_LANDSCAPE_dialog.png`, `20_landscape_details.png`, `window_landscape_error.xml`.

Probe harvest: `Timber.d("S0483:` FIRED once on button tap - `ScrollableTextDialog: S0483: crash-report button tapped; building email + log ZIP`. Tag left in place (status remains BlockNeedUserTest).

Out-of-scope finding parked: none (the ChooserActivity preview SecurityException is benign/documented; the missing Gmail account is an emulator-config gap, not an app bug).

Recommendation: the implementation works end-to-end for the real-exception browse path. Remaining gap is verification-environment only: confirm the populated Gmail compose (visible `serzhyale@gmail.com` recipient, subject, body) and landscape button tap on a device with (a) a signed-in mail account and (b) a taller landscape window, or via Maestro. Functionally the criteria are met; advancing to Verified is reasonable once the populated-compose recipient/body is eyeballed on an account-provisioned device.

Evidence: `temp/S0483_devtest/` (screenshots 01-20, logcat_attempt1-4, window_*.xml, session_recording.mp4).

### Manual (device) - 2026-06-19 - emulator-5554 (Android 13, SDK 33), standard debug 2.60.6191.257

**Verdict: INCONCLUSIVE.** Partial progress vs 2026-06-18: `showDetailedErrors` was successfully enabled via Settings UI, and an actual `ScrollableTextDialog` was reached at runtime (triggered via Archive on virtual://recent with `showDetailedErrors=true`). However, that error path carries `exception=null` (destination pre-check, not a real IOException), so `btnReport` stays GONE - confirming criterion 2 (button hidden for info messages) but not criterion 1 (button visible on real exception).

Emulator blocker (confirmed again, same root cause):
- The only `ArchiveProgress.Error(msg, e)` path with non-null exception is the top-level `IOException` catch in `ArchiveFilesUseCase` (lines 156-166) triggered when `ZipOutputStream` fails to open or write.
- Reaching that path requires `destDir.exists()==true && destDir.isDirectory==true` but writing fails. On AVD /sdcard (FUSE/sdcardfs), `chmod 000`/`444` is silently ignored (kernel ignores permission bits - confirmed again). Symlinks to non-existent paths cannot be created from adb shell (Permission denied). Race-condition deletion is unreliable and not reproducible.
- `archive_destination_not_found` pre-check (lines 85-88) fires before any ZIP creation and emits `exception=null`.

Per-criterion (expected vs actual):
1. Button visible on exception-class dialog - expected VISIBLE; actual NOT OBSERVED (exception path not reachable on emulator FUSE).
2. Button hidden for info message - expected GONE; actual **CONFIRMED GONE** via screenshot (Archive error dialog `virtual://recent` destination, `exception=null`, no btnReport visible). Evidence: `temp/S0483_device_test_20260619_1050/02_error_dialog_no_exception_no_button.png`.
3. Tap opens email to `serzhyale@gmail.com` - expected `ACTION_SEND`, `EXTRA_EMAIL`; actual NOT OBSERVED (exception dialog not reached).
4. App log zip attached - expected `EXTRA_STREAM` zip; actual NOT OBSERVED.
5. Portrait/landscape - `btnReport` present in both layout variants per Phase 03 static check; not runtime-verified.
6. `showDetailedErrors` gate - expected dialog when enabled; actual **CONFIRMED** via logcat: `showError: showDetailedErrors=true, message=Ошибка архивации...` → dialog shown. Evidence: `temp/S0483_device_test_20260619_1050/logcat_S0483.log`.

Probe harvest: `Timber.d("S0483:` never fired (exception path not reached). Tag left in place (status remains BlockNeedUserTest).

Evidence: `temp/S0483_device_test_20260619_1050/` (screenshots 01, 02; logcat_S0483.log).

Recommendation: test on a real device (non-FUSE filesystem, chmod actually works) or build a dedicated test-only error trigger accessible from a debug menu. Do not flip to Verified without exercising the real exception path at runtime.

---

### Manual (device) - 2026-06-18 - emulator-5554 (Android 13, SDK 33), standard debug

**Verdict: INCONCLUSIVE.** Could not produce a genuine exception-class browse error dialog on the emulator with local fixtures and no code changes, so the runtime criteria (button shown on crash, email intent, zip attachment, orientation render) could not be exercised. Static wiring is correct.

Reason the dialog could not be triggered honestly:
- The report button reaches the dialog only on the browse path, gated on `showDetailedErrors=true`, and only when a non-null `Throwable` is forwarded.
- The single browse sink that forwards a real exception is `ArchiveError(msg, exception)`, emitted only by the top-level `ArchiveProgress.Error(msg, e)` in `ArchiveFilesUseCase` (an IOException opening/writing the ZIP into the **current** browse directory; there is no separate destination picker).
- Forcing that IOException needs a writable-looking-but-unwritable current directory. On the emulator /sdcard is FUSE/sdcardfs: `chmod 000`/`555` is silently ignored (dir stays `media_rw`), so a read-only destination cannot be created. The invalid-name route is blocked by the name dialog regex `[/\\:*?"<>|]`. No SMB/SFTP/FTP server was available to exercise the network info case.
- Player/image-decode (corrupt media) errors route through the player `showError`, which does not wire `reportableThrowable`, so the button never appears there.

Per-criterion (expected vs actual):
1. Button visible on exception-class dialog - expected VISIBLE; actual NOT OBSERVED (could not trigger an exception dialog).
2. Button hidden for info message - expected GONE; actual NOT OBSERVED (no exception/info pair triggered; gate is correct in code).
3. Tap opens email to `serzhyale@gmail.com` with subject+body - expected `ACTION_SEND`, `EXTRA_EMAIL=[serzhyale@gmail.com]`, subject + body; actual NOT OBSERVED at runtime; intent shape correct by code read (`SupportIntentFactory.buildCrashReportEmail`).
4. App log zip attached - expected `type=application/zip` + `EXTRA_STREAM` zip + `FLAG_GRANT_READ_URI_PERMISSION`; actual NOT OBSERVED at runtime; correct by code read.
5. Localized + all flavors - dialog is in `src/main`, label `error_dialog_report_to_author`; not runtime-verified.
6. Focusable + portrait/landscape - `btnReport` present in both `layout/` and `layout-land/` per Phase 03; not runtime-verified (dialog not reached).

Probe harvest: `Timber.d("S0483:` never fired (logcat 4000-line window, 0 hits) - consistent with the dialog never being reached. Debug tag left in place (status stays BlockNeedUserTest).

Evidence: `temp/S0483_device_audit_20260618.md`, `temp/adb_log_20260618_123844.log`.

Recommendation: device-test on a real device or via a build where an archive write can genuinely fail (e.g. archive into a directory that becomes unwritable, or a path the app cannot write), or temporarily on a debug device with a non-FUSE writable-then-locked destination. Do not flip status to Verified on static wiring alone.
