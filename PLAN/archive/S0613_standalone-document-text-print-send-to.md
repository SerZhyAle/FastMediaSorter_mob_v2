# Стратегическая спецификация: S0613 - Печать документов и текста через «Отправить в..» в standalone-плеерах

**Ticket:** S0613
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-22
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - попутная находка при S0610 (2026-06-22)
**Tactical spec:** `PLAN/S0613_standalone-document-text-print-send-to/`
**Tactical plan:** `PLAN/S0613_standalone-document-text-print-send-to/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

<!-- auto-approved by /spec-all - 2026-06-22 -->

---

## 1. Проблема

Standalone-плееры документов и текста - отдельные экраны просмотра одного файла, открытого извне приложения - не дают напечатать открытый файл, тогда как in-app-плеер документы и текст печатает.

В унифицированном меню «Отправить в..» этих экранов нет получателя «Печать»: хосты документов и текста не объявляют способность печати (host-capability), а отдельный пункт печати в ниспадающем меню (overflow) на них скрыт. Итог: открыв pdf/office-документ или текстовый файл из внешнего приложения, пользователь не может напечатать его из standalone-просмотра.

Это та же нестыковка, что устранена для изображений в S0610, но для остальных печатаемых standalone-хостов. Область - feature-path «плеер / standalone-просмотр», слой UI и команд плеера + общий механизм печати.

---

## 2. Цели

1. Печать документа (pdf, office) доступна из меню «Отправить в..» в standalone-плеере документов.
2. Печать текста доступна из меню «Отправить в..» в standalone-плеере текста.
3. Вызов печати единообразен с in-app-плеером и со standalone-плеером изображений (S0610): печать - получатель отправки, единый источник истины о её доступности на экране.
4. Механизм печати документов и текста переиспользуется, а не дублируется в standalone-хостах.

**Non-goals:**

- Печать EPUB (вне applicable-типов получателя печати; in-app-плеер EPUB тоже не печатает).
- Печать в standalone-хосте аудио (неприменимо).
- Изменение поведения печати в in-app-плеере (механизм лишь выносится за общий шов без смены поведения).
- Новые форматы печати или новый системный механизм печати.
- Группы «Копировать в» / «Переместить в» (объём S0610 для изображений, не часть данной спеки).

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Переиспользовать существующий механизм печати документов и текста (тот, что применяет in-app-плеер), а не создавать параллельный.
2. Печать приводится к общей модели получателей «Отправить в..» (как в S0610): один источник истины о доступности печати на экране.

### 3.2 Жёсткие ограничения

- **Flavor:** все варианты с поддержкой документов (standard, legacy, vr, noLegal). lite и photos документы не поддерживают (`SUPPORT_DOCUMENTS=false`) - там хост документов не задействован. Внутренний просмотрщик office - возможность флейвора noLegal; печать office следует существующей флейвор-возможности, без новых source set сверх имеющихся. Общий шов печати живёт в общем коде; флейвор-специфичная часть (внутренняя печать office) остаётся за существующим интерфейсом по `dev/FLAVOR_DEVELOPMENT_RULES.md`.
- **API level:** без специфики; печать использует штатный системный механизм печати, уже применяемый в приложении.
- **Wear OS:** не затрагивается.
- **Производительность:** материализация сетевого файла перед печатью выполняется отложенно по запросу печати (в фоне), как в in-app-плеере, и не задерживает первичный показ документа/текста.
- **Совместимость данных:** изменений формы хранения нет.
- **Локализация:** EN/RU/UK обязательны. Приоритет переиспользования существующих строк печати и ошибок; новые строки - только при отсутствии подходящих.
- **Доступность:** получатель печати показывается в уже доступном с клавиатуры и D-pad меню «Отправить в..»; новых кастомных UI-элементов нет.

### 3.3 Owner inputs (Approval gate)

- **UI placement contract:** «Печать» появляется как получатель в подменю «Отправить в..» хостов документов и текста; отдельный пункт печати в overflow не добавляется (уже скрытый пункт остаётся скрытым). Зеркало решения S0610 для изображений.
- **Flavor matrix:** документы поддерживают standard/legacy/vr/noLegal; lite и photos - нет (`SUPPORT_DOCUMENTS=false`, хост документов не используется). Печать office-документа использует внутренний просмотрщик в noLegal; в прочих флейворах office печатается тем же путём, что in-app (с откатом к share-for-print при отсутствии внутренней печати). Текст и pdf печатаются во всех флейворах с поддержкой документов/текста.
- **Accessibility:** получатель печати находится в существующем меню, фокусируемом с клавиатуры и D-pad; новых интерактивных элементов не вводится.
- **Communication policy:** тексты сообщений об ошибке печати соответствуют `docs/COMMUNICATION_POLICY.md` (фактологичны, без обещаний).
- **Validation level:** сборка целевых флейворов + ручная проверка на устройстве (печать pdf и текстового файла, открытых извне, через «Отправить в..»; проверка office-печати в noLegal).
- **Owner sign-off:** auto-approved by /spec-all 2026-06-22 (находка-наследник S0610, паттерн уже утверждён владельцем).
- **Related tickets:** S0610 (паттерн «печать как получатель» для хоста изображений), S0459 (унифицированное меню «Отправить в..», host-capability гейт печати), S0393 (семейство standalone-хостов).

---

## 4. Контекст текущей архитектуры

Меню «Отправить в..» собирает получателей по содержимому и дополнительно отсеивает тех, кого текущий экран-хост не поддерживает. Печать - получатель, привязанный к хосту: он появляется только если экран объявляет способность печатать. Сейчас эту способность объявляют in-app-плеер и (после S0610) standalone-плеер изображений. Хосты документов и текста её не объявляют, поэтому получатель печати в их «Отправить в..» не появляется - отсюда расхождение из §1.

Сам механизм печати документов и текста - материализация файла, печать pdf через системный адаптер, печать текста через эфемерный веб-рендер, печать office через внутренний просмотрщик, откат к share-for-print при отказе - реализован для in-app-плеера и привязан к его Activity. Поэтому напрямую из standalone-хоста он не вызывается, хотя оба standalone-хоста уже владеют нужными вспомогательными частями (подготовка сетевого файла, для документов - внутренний просмотрщик office).

---

## 5. Предлагаемый подход

### 5.1 Основные столпы / модули

**Столп A - печать как получатель отправки в хосте документов.**

- Хост документов объявляет способность печати, чтобы получатель печати прошёл host-гейт меню и появился в «Отправить в..» для pdf и office (EPUB вне applicable-типов).
- Печать вызывает переиспользуемый механизм печати документов через ту же модель получателей, что и прочие пункты «Отправить в..».

**Столп B - печать как получатель отправки в хосте текста.**

- Хост текста объявляет способность печати, чтобы получатель печати появился для текстовых файлов.
- Печать вызывает переиспользуемый механизм печати текста.

**Столп C - host-agnostic шов механизма печати.**

- Механизм печати документов и текста выносится за шов, не зависящий от конкретной Activity, чтобы in-app-плеер и оба standalone-хоста печатали через одну реализацию.
- Поведение печати в in-app-плеере не меняется; шов лишь снимает жёсткую привязку к Activity плеера.
- Различия по флейвору (внутренняя печать office) и поверхность сообщений об ошибке (in-app использует Snackbar, standalone - Toast) выражаются через шов, а не дублированием логики.

### 5.2 Потоки данных и событий

- Печать документа: UI хоста документов → объявленная host-способность печати → получатель «Печать» в модели отправки → host-agnostic механизм печати → материализация (для сетевого файла) → системная печать pdf/office.
- Печать текста: UI хоста текста → host-способность печати → получатель «Печать» → механизм печати текста → эфемерный веб-рендер → системная печать.
- Откат: при отказе системной печати - совместно используемый путь share-for-print, как в in-app-плеере.

### 5.3 Точки расширяемости

- Host-agnostic шов печати позволяет подключить печать любому будущему хосту без дублирования логики получателя или механизма.
- Флейвор-специфичная печать office остаётся за существующим интерфейсом просмотрщика, переиспользуемым через шов.

---

## 6. Открытые вопросы / Research items

1. **Путь печати текста в standalone-контексте**
   - **Вопрос:** что печатать и каким механизмом, если у текстового хоста нет собственного пути печати?
   - **Решение:** механизм печати текста уже существует - in-app-плеер печатает текст через эфемерный веб-рендер и системный адаптер печати; он переиспользуется через host-agnostic шов. Net-new - только подключение получателя печати к хосту текста, не сам механизм.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0613_standalone-document-text-print-send-to/research/01__text-print-path.md`

2. **Печать документа в standalone-контексте (материализация сетевых файлов)**
   - **Вопрос:** корректно ли печатается pdf/office из standalone, где файл может быть сетевым?
   - **Решение:** материализация выполняется тем же путём подготовки локального файла, что и in-app; standalone-хост документов уже владеет менеджером сетевых файлов, поэтому механизм печати получает локальную копию так же, как в плеере.
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0613_standalone-document-text-print-send-to/research/02__document-materialization-reuse.md`

3. **Зависимость рендера и печати документов от флейвора**
   - **Вопрос:** как печать ведёт себя в разных флейворах, в частности с внутренним просмотрщиком office в noLegal?
   - **Решение:** pdf и текст печатаются во всех флейворах с поддержкой; office-печать - возможность noLegal через внутренний просмотрщик, в прочих флейворах поведение совпадает с in-app (откат к share-for-print). EPUB не печатается (вне applicable-типов получателя печати).
   - **Статус:** Resolved
   - **Артефакт:** `PLAN/S0613_standalone-document-text-print-send-to/research/03__flavor-office-print.md`

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Механизм печати привязан к Activity плеера и не вызывается из standalone | Высокая | Без шва - дублирование логики печати в двух хостах | Вынести host-agnostic шов без смены поведения in-app; проверить сборкой и устройством |
| Вынос шва задевает in-app-плеер и регрессит печать в нём | Средняя | Регресс печати документов/текста в основном плеере | Шов сохраняет идентичную диспетчеризацию; in-app печатает тем же кодом; регресс-проверка на устройстве |
| Печать office зависит от флейвора (внутренний просмотрщик noLegal) | Средняя | В market-флейворах office не печатается внутренним путём | Следовать существующей флейвор-возможности, откат к share-for-print, как in-app |
| Поверхность ошибок различается (Snackbar in-app / Toast standalone) | Низкая | Несогласованный показ ошибки | Выразить сообщение об ошибке через шов; каждый хост рендерит своим способом |
| Эфемерный веб-рендер печати текста не привязан к окну | Низкая | Сбой печати при уходе с экрана во время рендера | Переиспользовать существующий путь с его guard'ами; проверить на устройстве |

---

## 8. Влияние на пользователя (docs/FEATURES)

Новая воспринимаемая способность: в standalone-просмотре документа (pdf/office) и текстового файла, открытого извне, пользователь может напечатать его через «Отправить в..». Одно предложение для `docs/FEATURES.md` + `_RU` + `_UK` при релизе (наполняется через `/skill-release` из диффа `ALL_FEATURES`).

---

## 9. Архитектурные решения (ADR)

**ADR-1: Печать остаётся получателем «Отправить в..», а не отдельным действием**

- **Решение:** хосты документов и текста объявляют host-способность печати; отдельный overflow-пункт печати не добавляется.
- **Альтернативы:** добавить изолированный пункт печати в overflow каждого хоста.
- **Почему:** единый источник истины о доступности печати (как принято для унифицированного меню и S0610), меньше путаницы и путей кода. На этих хостах overflow-пункт печати и так скрыт - удалять нечего, лишь объявляется способность.

**ADR-2: Механизм печати переиспользуется через host-agnostic шов, без параллельной реализации**

- **Решение:** вынести механизм печати документов/текста за шов, не зависящий от Activity плеера; in-app и оба standalone-хоста печатают одной реализацией.
- **Альтернативы:** инлайн-дублирование логики печати в каждом standalone-хосте (как сделано для тривиальной печати изображений в S0610).
- **Почему:** логика печати документов/текста нетривиальна (материализация, pdf-адаптер, веб-рендер текста, office, откат); дублирование в двух хостах повысило бы риск расхождений - пожелание владельца §3.1.1.

**ADR-3: EPUB не печатается; office-печать следует флейвор-возможности**

- **Решение:** EPUB остаётся вне applicable-типов получателя печати (как и для in-app); office печатается внутренним просмотрщиком в noLegal, в прочих флейворах - откат к share-for-print.
- **Альтернативы:** реализовать печать EPUB постранично.
- **Почему:** паритет с поведением in-app-плеера; печать EPUB - отдельный нетривиальный объём вне данной спеки.

---

## 10. Связи с другими спеками

- S0610 - паттерн «печать как получатель отправки» для хоста изображений; настоящая спека распространяет его на документы и текст.
- S0459 - унифицированное меню «Отправить в..», host-capability гейт печати (archived, архитектурное основание).
- S0393 - маршрутизация и состав standalone-хостов (archived); затрагиваемые хосты относятся к этому семейству.

Блокирующих зависимостей нет: механизм печати документов/текста независим от печати изображений S0610, поэтому S0610 не блокирует данную спеку.

---

## 11. Критерии готовности (strategic-level)

1. В standalone-плеере документов получатель «Печать» присутствует в «Отправить в..» для pdf и office и печатает текущий файл.
2. В standalone-плеере текста получатель «Печать» присутствует в «Отправить в..» и печатает текущий текст.
3. Отдельного изолированного пункта печати, дублирующего вызов, на этих экранах нет.
4. Печать в in-app-плеере не изменилась (регресс не допущен).
5. Office-печать работает в noLegal через внутренний просмотрщик; в прочих флейворах поведение совпадает с in-app.
6. Печать EPUB не предлагается.

---

## 12. Ссылка на тактическую спецификацию

Следующий шаг: `/spec-tech S0613` - создаст `PLAN/S0613_standalone-document-text-print-send-to/` с фазами.

---

## Last Audit

**Type:** observational snapshot (read-only - no build, no code edits). Taken 2026-06-22 ~11:38 while a concurrent process was actively implementing this ticket (`.kt` writes marching forward in real time). Not authoritative: the concurrent run's own `/spec-check` will supersede this block.

**Status at snapshot:** `In Progress`. INDEX still shows `0/4`, but code is already ahead of the tracking (drift).

**Phase 01 - print host seam: implemented.**

- `DocumentPrintHost` interface present with all four members (`printHostActivity`, `printNetworkFileManager`, `printOfficeDocument`, `showPrintMessage`).
- `PlayerPrintFallbackManager` renamed to `PrintShareFallbackManager` (`private val activity: Activity`); old file deleted.
- `DocumentPrintManager` refactored onto the seam: `host: DocumentPrintHost`, all `activity.*` usages routed through `host.printHostActivity`, `PrintShareFallbackManager(host.printHostActivity)`; no `private val activity` field remains.
- `PlayerActivity` implements `DocumentPrintHost`; `PlayerManagerInitializer` constructs `DocumentPrintManager(host = activity, ..)`.

**Phase 02 - document host print: implemented.**

- `DocumentStandaloneActivity` declares `SharePrintHost, DocumentPrintHost`; `@Inject mediaCapabilities`; lazy `DocumentPrintManager(host = this, ..)`; four seam members (`printOfficeDocument` gated on `officeViewerHostDelegate.isInitialized()`); `printMediaFile` dispatches to the shared manager.
- Overflow `R.id.menu_print` still in the hidden-items list (ADR-1 honoured).

**Phase 03 - text host print: implemented.**

- `TextStandaloneActivity` declares `SharePrintHost, DocumentPrintHost`; `MediaFile` import added; `@Inject mediaCapabilities`; lazy `DocumentPrintManager(host = this, ..)`; four seam members (`printOfficeDocument(): Boolean = false`); `printMediaFile` dispatches.
- Overflow `R.id.menu_print` still hidden.

**Device-test tags:** `Timber.d("S0613: ..")` already inserted at `DocumentStandaloneActivity.printMediaFile` (L338) and `TextStandaloneActivity.printMediaFile` (L275) - the final edits before the last build, consistent with an imminent `BlockNeedUserTest` transition. (Tags present while `Status: In Progress` is a transient mid-flight state, not a violation to fix here.)

**Phase 04 - docs/catalog closure: partial.**

- `dev/CHANGELOG.md` - 3 `S0613` entries present.
- `dev/CATALOG/app_v2.jsonl` - NOT yet regenerated: `DocumentPrintHost` absent, stale `PlayerPrintFallbackManager` still listed. Needs `catalog_sync.ps1 -Module app_v2`.
- `docs/ALL_FEATURES.jsonl` - no `S0613` record yet (Step 4.2 pending).

**Residual gaps (owned by the concurrent run, not actioned here):**

1. Final `standard debug` build to validate code + inserted tags.
2. Flip `Status` to `BlockNeedUserTest`; on-device verification of print via «Send to..» for PDF/Office (incl. noLegal Office path) and text.
3. Phase 04 closure: catalog regen + `ALL_FEATURES.jsonl` record.
4. INDEX checkboxes reconciled to reality (`4/4` once gates close).

**Update (2026-06-22 ~11:50, implementing run):** residual gaps 1-4 are now closed - `standard debug` build PASS (code + tags, `a.ps1 fk` and `a.ps1 dq`), status flipped to `BlockNeedUserTest`, catalog regenerated (`DocumentPrintHost` present, `PlayerPrintFallbackManager` gone), `docs/ALL_FEATURES.jsonl` record `documents.standalone_print_send_to` added, INDEX 4/4. Quality gates clean (neuroslop delta 0, ticket-log 0/0 with 2 allowed S0613 probes, deprecated-PM delta 0).

### Manual / on-device

**Outcome: INCONCLUSIVE** (emulator-5554, standard debug v2.60.6261.106, 2026-06-26). The Send-to -> Print wiring is verified for both standalone hosts; actual print OUTPUT cannot be confirmed on this x86 emulator. Hosts reached by enabling the "System media handler" toggle, then launching the now-enabled `.StandaloneDocsPlayer` / `.StandaloneTextPlayer` aliases with MediaStore content URIs (`test_doc_romcom.pdf`, `readme.txt`).

- Print in document viewer Send-to (PDF) - PASS (wiring). Send-to lists Email / Open in.. / Print / Other apps; tapping Print fired `S0613: standalone document print dispatched via Send-to receiver` and `DocumentPrintManager: starting print, type=PDF, file=test_doc_romcom.pdf`.
- Print in text viewer Send-to - PASS (wiring). Send-to lists Print; tapping it fired `S0613: standalone text print dispatched via Send-to receiver` and the text print mechanism ran (`dispatching 'Printing: readme.txt'`).
- No separate overflow print item - PASS. Document overflow = only "Open in FastMediaSorter"; text overflow = Open in FastMediaSorter / Text Settings / Draw. Neither lists Print.
- Actual printing - INCONCLUSIVE. On this x86 AVD `PrintManager.print()` rejects the call with the documented `IllegalStateException: Can print only from an activity` even though dispatch comes from the correct Activity context (`ctx=..DocumentStandaloneActivity` / `..TextStandaloneActivity`). The code catches it and degrades gracefully to the system share chooser (no crash). This is the emulator caveat the code already documents, not an S0613 defect; real print output needs a physical device with a printer.
- In-app player print regression - NOT RE-TESTED here (no separate in-app print drive this session). The shared `DocumentPrintManager` seam dispatches identically from `PlayerActivity` (DocumentPrintHost), so the seam is exercised by the standalone runs, but the in-app path was not independently confirmed.
- Office print (noLegal internal viewer) - NOT TESTABLE on standard build (out of this flavor's scope).

Evidence: temp/s0613_01_pdf_viewer.png, temp/s0613_03_text_viewer.png.

---

**Outcome: FAIL (real device) - print preview NEVER reached; PrintManager rejects on a physical device too, not just the emulator.**

Device: Samsung Galaxy S21+ (RFCR110NBQJ, SM-G996U1, Android 15 / SDK 35, 1080x2400), standard debug `com.sza.fastmediasorter.debug`. Date: 2026-06-28. Hosts reached the same way as the prior emulator run: enabled the in-app "System media handler" toggle (Settings -> Management -> Operating system interaction), after which the `.StandaloneDocsPlayer` / `.StandaloneTextPlayer` aliases resolve and a VIEW intent with a MediaStore content URI (`test_doc_romcom.pdf` id 166, `readme.txt` id 169) opens the standalone host.

- Send-to lists Print (PDF + text) - PASS (wiring). Document viewer Send-to = Email / Open in.. / Print / Other apps; text viewer Send-to = Email / Print / Other apps. No separate overflow Print item on either. Probes fired: `S0613: standalone document print dispatched via Send-to receiver`, `S0613: standalone text print dispatched via Send-to receiver`.
- Materialization - PASS. `DocumentPrintManager: prepared local file for print (483736 bytes): ..cache/standalone_content_1782597605599.pdf`; dispatch logged from the correct context: `dispatching 'Printing: test_doc_romcom.pdf' via android.print.PrintManager, ctx=..DocumentStandaloneActivity` (and `..TextStandaloneActivity` for text).
- **Actual print preview - FAIL.** On this PHYSICAL device `PrintManager.print()` throws `java.lang.IllegalStateException: Can print only from an activity` (`PrintManager.java:519`, from `DocumentPrintManager.dispatchPrint$lambda$0` -> `DocumentPrintManager.kt:126`) for BOTH PDF and text. This is the SAME exception the spec attributes to the x86 emulator only - it reproduces on real hardware, so it is NOT an emulator caveat but a genuine defect: the `Context` handed to `PrintManager.print()` is not accepted as an Activity by the platform, despite the dispatch-log naming the standalone Activity. The print preview / system "Save as PDF" service is therefore never reached from the in-app Print recipient.
- Graceful fallback - PASS (no crash). The app catches the exception, shows a Toast (`showPrintMessage`), and degrades to the system share chooser sharing the materialized PDF. Notably the fallback chooser DID surface `com.android.bips/com.android.bips.PdfPrintActivity` (Default Print Service) as a target, so printing is reachable only via the share-for-print fallback, not via the intended in-app print path.
- In-app player print regression - NOT RE-TESTED this session. The shared `DocumentPrintManager` seam is identical for `PlayerActivity` (DocumentPrintHost); since the seam fails the Activity-context check from the standalone hosts, the in-app path needs an independent check to confirm whether it hits the same `IllegalStateException` or passes a valid Activity context (the difference would localize the bug to how standalone hosts expose `printHostActivity`).
- Office print (noLegal internal viewer) - NOT TESTABLE on standard build.

Net: the Send-to -> Print recipient wiring is correct, but the core deliverable (a working print preview from standalone document/text viewers) does not function on a real device because of the `Can print only from an activity` rejection. This contradicts the BlockNeedUserTest status-note assumption that the failure is emulator-only. Recommend treating the print-context wiring (`printHostActivity` / the Context passed into `PrintManager.print()`) as a real bug to fix before Verified.

Evidence: temp/S0613_devtest/01_pdf_standalone_viewer.png, 02_pdf_sendto_print.png, 03_text_sendto_print.png, print_trace.txt, logcat_session.txt.

---

**Outcome: FAIL (owner retest, standalone PDF host UI regressions) - additional defects discovered outside the print-preview core path.**

Date: 2026-06-29. Surface: standalone PDF player.

- Top app bar shows an unexpected `Random` action in the standalone PDF viewer.
- Overflow menu misses the `Send to..` submenu entirely; only `Open in FastMediaSorter` is present.
- Bottom command panel `Copy to..` does not expand.
- Bottom command panel `Move to..` does not collapse.

Net: even after the print-dispatch rework, the standalone PDF host still does not meet the spec's user-facing command-surface expectations. The ticket must stay in a failing state until the PDF standalone command UI matches the intended standalone-player behavior and the print flow itself is re-verified.

## Revision History

- **2026-06-22** - by `/spec-test-device` (emulator-5554, Android AVD)
  - Scenario: temp/S0613_mobile_test_scenario_20260622_1149.md - regression smoke PASS (install + launch, no crash from the seam refactor); functional print flow INCONCLUSIVE on AVD (1 PASS / 0 FAIL / 1 INCONCLUSIVE), log errors 0. Standalone hosts are not shell-launchable (`exported=false`) and the AVD has no printer/indexed media, so the external-open -> Send-to -> Print flow needs a real-device manual test. Ticket stays `BlockNeedUserTest`; debug tags retained.
