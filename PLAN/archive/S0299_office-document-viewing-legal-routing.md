---
ticket: S0299
status: Verified
priority: 55
date: 2026-05-28
tier: 3
---

# Стратегическая спецификация: S0299 - Office document viewing and legal routing

**Ticket:** S0299
**Status:** Archived
**Implemented date:** 2026-05-28
**Priority:** 55
**Date:** 2026-05-28
**Tier:** 3 - Strategic, compliance-sensitive feature
**Roadmap entry:** Ad-hoc - запрос 2026-05-28: исследовать просмотр DOC, DOCX и других office-документов; если `standard` нельзя легализовать, вынести рискованную часть в `noLegal`.
**Tactical spec:** `PLAN/S0299_office-document-viewing-legal-routing/INDEX.md`

**Tactical plan:** `PLAN/S0299_office-document-viewing-legal-routing/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, research-выводы и flavor-routing. Без implementation-плана и без изменения кода.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - research + strategic spec.
- **Goal / expected outcome:** Provided by user - понять, можно ли открывать для просмотра DOC, DOCX и другие document-типы; рискованные для `standard` решения отправлять в `noLegal`.
- **Local anchor:** Delegated by user - Browse file tap and StandaloneDocsPlayer external intents; agent may choose exact internal routing.
- **Scope boundaries / forbidden areas:** Provided by user - `standard` не должен получать нелегализуемый document-engine; `noLegal` допустим как fallback-канал.
- **Done / success signal:** Provided by user - research выполнен, создана спека с выводом по `standard` / `noLegal`.
- **Autonomy rule:** Delegated by user - agent may decide with explicit assumptions for research, tactical split, UI behavior, and implementation details.
- **UI decisions / delegation:** Delegated by user - direct tap opens external viewer through Android handoff; unsupported/missing viewer shows a localized fallback; no embedded Office preview in this iteration.

Owner explicitly delegated the remaining blockers on 2026-05-28. `Approved` is allowed for the external-handoff first iteration.

---

## 1. Проблема

- Документный контур приложения уже покрывает PDF, EPUB и текст, но Office-документы вроде `.doc` и `.docx` остаются вне явного viewer-контракта.
- Пользователь, который хранит документы рядом с медиа на локальном диске, NAS или cloud-ресурсе, ожидает хотя бы безопасный open-for-view path без ручного выхода из FMS.
- Встроенный просмотр Office-файлов нельзя добавлять тем же способом, что PDF: в Android нет платформенного DOC/DOCX renderer, а полноценные Office-движки несут лицензионные, размерные и maintenance-риски.
- Нужно разделить `standard`-safe функциональность и `noLegal`-only варианты до начала реализации, чтобы не протащить проблемную зависимость в market-сборку.

---

## 2. Цели

1. Пользователь может увидеть `.doc`, `.docx`, `.rtf`, `.odt` и близкие office-документы как распознанные documents, а не как случайные binary-файлы.
2. `standard` получает безопасный способ открыть такой файл для просмотра через установленное внешнее приложение или через легализованный lightweight-preview.
3. Встроенный high-fidelity Office-renderer появляется только после отдельного compliance-аудита; если аудит не проходит для `standard`, реализация уходит в `noLegal`.
4. Remote/cloud Office-файлы открываются через существующий materialize-to-local flow с контролируемым cache/fallback поведением.
5. Документные macros, embedded scripts, OLE-объекты и remote-active content никогда не выполняются.
6. Пользовательские формулировки честно различают external open, lightweight preview и встроенный viewer.

**Non-goals:**

- Редактирование и сохранение Office-документов.
- Pixel-perfect совместимость с Microsoft Word, Excel или PowerPoint.
- Выполнение macros, embedded OLE или active content.
- Cloud-conversion через загрузку пользовательских документов на сторонний сервер.
- Замена полноценного Office-suite внутри FMS.
- Реализация в этом тикете.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Исследовать DOC, DOCX и другие document-типы для open/view сценария.
2. Сначала попытаться легализовать вариант для `standard`.
3. Если выбранный вариант нельзя безопасно отгрузить в `standard`, вынести его в `noLegal`.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard` допускает только platform intent handoff и зависимости с пройденным license/notice/audit; `noLegal` допускает отдельный flavor-isolated renderer/converter после явного owner approval.
- **API level:** baseline `standard`/`noLegal` - API 26+. `legacy` с API 23 не входит в первую фазу без отдельной проверки зависимостей.
- **Wear OS:** не затрагивается.
- **Производительность:** preview/parsing выполняется вне UI thread; большие документы получают size cap и external-open fallback.
- **Совместимость данных:** без Room-схемы и без изменения сохранённых user settings в первой фазе.
- **Локализация:** EN/RU/UK обязательны для новых strings.
- **Доступность:** chooser/fallback states должны быть доступны для TalkBack, D-pad, keyboard и mouse.
- **Compliance:** AGPL/GPL, коммерчески ограниченные SDK, тяжёлые native engines и dual-license ambiguity запрещены в `standard` без отдельного signed-off решения.
- **Security:** Office-файлы рассматриваются как untrusted input; macros, scripts, embedded remote content и автоматическое открытие внешних ссылок запрещены.
- **UI copy:** новые сообщения проходят `docs/COMMUNICATION_POLICY.md` tone checklist.

---

## 4. Контекст текущей архитектуры

Текущий документный контур построен вокруг трёх семейств: PDF, EPUB и text. Они участвуют в фильтрах, default-player association, document settings, print/share fallback и player-side viewer routing.

Office-документы сейчас не имеют отдельной продуктовой категории. В all-files mode они могут попасть в список, но не получают честный viewer contract: нет гарантированного MIME-routing, нет Office-specific fallback, нет ясного сообщения, когда внешнее приложение не установлено.

Flavor-модель уже содержит `standard` и `noLegal` как разные compliance-поверхности. Любая реализация, завязанная на неприемлемую для market-сборки зависимость, должна быть инжектирована через flavor-specific boundary, а не через runtime `BuildConfig` checks в общем коде.

---

## 5. Предлагаемый подход

### 5.1 Capability matrix

Ввести продуктовую матрицу document-opening routes:

- **Native FMS viewer:** уже существующие PDF, EPUB, text.
- **Standard external handoff:** Office-документы открываются через Android `ACTION_VIEW` с корректным MIME, `content://` доступом и read-permission grant.
- **Standard lightweight preview:** опциональный режим, который извлекает текст/простую HTML-структуру только через легализованные permissive dependencies.
- **noLegal embedded renderer:** high-fidelity rendering/conversion через тяжёлый или спорный engine, если он не проходит `standard` compliance.

### 5.2 Standard route

- Распознавать Office-документы по расширению и MIME, не обещая встроенный Word/Office viewer.
- Для локальных файлов передавать viewer-приложению безопасный URI.
- Для network/cloud/content источников сначала материализовать readable local copy через существующий cache/download path.
- Если внешнего приложения нет, показывать понятный fallback: установить Office/document viewer, открыть как text preview если доступно, либо оставить файл без просмотра.
- Lightweight preview допускается только после dependency audit; продуктовая формулировка - «preview» или «text preview», не «full viewer».

### 5.3 noLegal route

- Если нужен встроенный layout-faithful renderer, он живёт только как flavor-specific provider.
- `standard` получает no-op provider и не компилирует risky/native/AGPL engine.
- `noLegal` может использовать тяжёлую native-конвертацию или AGPL/commercial-aware engine только после отдельной owner/legal approval и documented notices.
- `docs/FEATURES_noLegal*.md` обновляются только после реализации noLegal-only capability; public `docs/FEATURES*.md` не получают noLegal-only строки.

### 5.4 Data and event flow

- Browse/player получает файл.
- Document route resolver определяет route: native viewer, external handoff, lightweight preview, noLegal embedded renderer, unsupported.
- Для non-local источников файл готовится как local readable copy.
- UI запускает выбранный route.
- Ошибка route превращается в fallback state без crash и без silent no-op.

### 5.5 Extension points

- Document-route provider должен быть расширяемым по MIME/extension.
- Встроенный Office-renderer должен быть заменяемым без изменения общих viewer-экранов.
- noLegal implementation не должна протекать в `standard` binary.

---

## 6. Открытые вопросы / Research items

1. **Android external open route**
   - **Вопрос:** можно ли безопасно открыть Office-файл через систему?
   - **Варианты:** `ACTION_VIEW` с URI grant; собственный renderer.
   - **Нужно выяснить:** package-visibility queries и fallback UX на Android 11+.
   - **Статус:** Resolved - Android documents `ACTION_VIEW` handoff and `FLAG_GRANT_READ_URI_PERMISSION`: https://developer.android.com/training/package-visibility/use-cases

2. **Native Android renderer scope**
   - **Вопрос:** есть ли платформенный DOC/DOCX renderer?
   - **Варианты:** PdfRenderer; WebView; external app.
   - **Нужно выяснить:** покрытие `PdfRenderer`.
   - **Статус:** Resolved - `PdfRenderer` covers PDF rendering, not Office documents: https://developer.android.com/reference/android/graphics/pdf/PdfRenderer

3. **DOCX format legality**
   - **Вопрос:** сам `.docx` формат является blocker-ом?
   - **Варианты:** формат нельзя использовать; формат можно парсить при корректной реализации.
   - **Нужно выяснить:** license posture Open XML.
   - **Статус:** Resolved - Microsoft describes Open XML formats as royalty-free to use/license: https://support.microsoft.com/en-gb/office/open-xml-formats-and-file-name-extensions-5200d93c-3449-4380-8e11-31ef14555b18

4. **Apache POI**
   - **Вопрос:** подходит ли POI для `standard`?
   - **Варианты:** text/HTML preview; full viewer; не использовать.
   - **Нужно выяснить:** license и quality.
   - **Статус:** Resolved - Apache-2.0, good for extraction, incomplete for full fidelity: https://poi.apache.org/legal.html and https://poi.apache.org/components/document/index.html

5. **docx4j**
   - **Вопрос:** подходит ли docx4j для DOCX preview/conversion?
   - **Варианты:** HTML export; PDF export; no-op.
   - **Нужно выяснить:** license и Android dependency weight.
   - **Статус:** Resolved for license, open for Android fit - Apache-2.0, supports OpenXML packages and export paths: https://github.com/plutext/docx4j

6. **Apache Tika**
   - **Вопрос:** покрывает ли Tika другие office-типы?
   - **Варианты:** text/metadata extraction for DOC/DOCX/RTF/ODF; rendering.
   - **Нужно выяснить:** supported formats and Android footprint.
   - **Статус:** Resolved for capability, open for Android footprint - extracts text/metadata via parsers, not full rendering: https://tika.apache.org/2.9.2/formats.html

7. **LibreOffice engine**
   - **Вопрос:** нужен ли high-fidelity embedded renderer?
   - **Варианты:** LibreOffice-derived engine; external app; lightweight preview.
   - **Нужно выяснить:** native size, 16 KB alignment, LGPL/MPL obligations, startup cost.
   - **Статус:** Open - license/source baseline known, product decision and Android packaging not approved: https://www.libreoffice.org/licenses/

8. **ONLYOFFICE engine**
   - **Вопрос:** можно ли встроить ONLYOFFICE в `standard`?
   - **Варианты:** AGPL source release, commercial license, noLegal-only experiment, reject.
   - **Нужно выяснить:** owner acceptance of AGPL/commercial terms.
   - **Статус:** Resolved for `standard` default - reject unless separate approval/commercial license: https://www.onlyoffice.com/cs/license-faq

9. **Owner scope for "other document types"**
   - **Вопрос:** входят ли spreadsheets/presentations (`.xls`, `.xlsx`, `.ppt`, `.pptx`) или только text documents (`.doc`, `.docx`, `.rtf`, `.odt`)?
   - **Варианты:** Word-family only; all Office-family; external-only for spreadsheets/presentations.
   - **Нужно выяснить:** владелец выбирает первую фазу.
   - **Статус:** Resolved - first implementation covers Word-family document handoff only: `.doc`, `.docx`, `.rtf`, `.odt`; spreadsheets/presentations remain future external-only scope.

10. **UI behavior**
   - **Вопрос:** где пользователь видит Office action: tap opens external viewer, overflow action, chooser, or preview-first?
   - **Варианты:** direct tap external; direct tap preview; overflow-only; settings toggle.
   - **Нужно выяснить:** `/ui-clarify` before implementation.
   - **Статус:** Resolved - first implementation uses direct tap / external intent handoff with localized missing-viewer fallback; no preview-first UX and no new settings toggle.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| `standard` accidentally ships risky engine | Средняя | Play/compliance issue, APK bloat | Flavor boundary, dependency audit, no risky implementation in shared source |
| User expects Word-perfect rendering from text preview | Высокая | Trust loss, support noise | Honest wording: external open vs preview vs full viewer |
| Legacy `.doc` parsing is fragile | Средняя | Broken preview, bad formatting | External handoff default, sample corpus before preview |
| Remote/cloud materialization leaks temp files | Средняя | Privacy/storage issue | Existing cache lifecycle, clear fallback states, no root writes |
| No installed external viewer | Высокая | Tap appears broken | Explicit fallback dialog/toast and optional share/open-with route |
| Parser dependency increases APK size | Средняя | Slower build, larger install | Size budget gate before `standard`; move heavy route to `noLegal` |
| AGPL/copyleft obligations misunderstood | Средняя | Legal/compliance exposure | Treat as blocked for `standard` until explicit approval |

---

## 8. Влияние на пользователя (docs/FEATURES)

После реализации `standard` external-open:

- `docs/FEATURES.md`: "Office document handoff: DOC, DOCX, RTF, and ODT files can be opened from FMS with an installed document viewer, including files prepared from local, network, or cloud sources."
- `docs/FEATURES_RU.md` и `docs/FEATURES_UK.md`: зеркальные строки.

Если реализуется только `noLegal` embedded renderer:

- Public `docs/FEATURES*.md` - без изменений.
- `docs/FEATURES_noLegal*.md` - отдельная строка про встроенный Office renderer/converter после implementation.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Compliance-first routing**

- **Решение:** `standard` получает только routes, которые прошли license/dependency audit; всё спорное уходит в `noLegal`.
- **Альтернативы:** сразу добавить high-fidelity engine в общий document viewer; не поддерживать Office-документы вообще.
- **Почему:** пользователь получает быстрый safe win через external open, а рискованные engines не протекают в market binary.

**ADR-2: External handoff is the first standard milestone**

- **Решение:** первая реализация для `standard` - распознавание Office документов и системное открытие через installed viewer.
- **Альтернативы:** встроенный parser first; noLegal-only first.
- **Почему:** это минимальный legal/technical footprint и лучший UX fallback, когда на устройстве уже установлен Office/document viewer.

**ADR-3: Lightweight preview must be named as preview**

- **Решение:** если добавляется parser-based view, продукт называет его preview/text preview, пока нет layout-faithful renderer.
- **Альтернативы:** назвать feature "DOCX viewer".
- **Почему:** POI/Tika/docx4j отлично подходят для extraction/conversion-сценариев, но не гарантируют Word-perfect rendering.

**ADR-4: no macros and no cloud conversion**

- **Решение:** Office documents are passive inputs; no macro execution, no active OLE, no automatic cloud upload/conversion.
- **Альтернативы:** использовать external cloud conversion API для fidelity.
- **Почему:** privacy/security risk выше пользовательской ценности для FMS.

---

## 10. Связи с другими спеками

- **S0156** - policy parent for noLegal-only documentation and flavor isolation.
- **S0297** - prior noLegal capability research pattern; this spec follows the same "standard-safe vs noLegal-only" separation idea.
- **S0016** and **S0086** - archived document-print/export context; not blockers.
- **S0296** - no direct dependency. Active editor tab is unrelated to Office document viewing.

---

## 11. Критерии готовности (strategic-level)

1. `.doc`, `.docx`, `.rtf` and `.odt` files have an explicit route instead of falling through as unsupported binary files.
2. `standard` opens Office documents through installed external viewers with correct MIME and URI permissions.
3. If no external viewer exists, the user receives a clear fallback state.
4. Optional `standard` preview ships only after permissive-license dependency audit, APK-size review, and sample-corpus validation.
5. Any high-fidelity embedded Office engine is isolated to `noLegal` unless a later signed-off decision clears it for `standard`.
6. Remote/cloud Office documents use the existing local materialization/cache lifecycle and do not leave uncontrolled temp copies.
7. Macros, scripts, embedded OLE and automatic remote content are never executed.
8. New strings are localized EN/RU/UK and comply with the communication policy.
9. `standard` and `noLegal` builds pass for the implemented route.
10. Feature docs are updated in public or noLegal-only inventories according to the chosen flavor surface.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: `PLAN/S0299_office-document-viewing-legal-routing/INDEX.md`.

---

## Revision History

- **2026-05-28** - verified by Codex via `/spec-check`
  - Static audit passed for standard external handoff: no embedded Office renderer dependency, no stale S0299 debug tags, build/catalog/docs/dev-log checks complete.
- **2026-05-28** - owner delegation applied by Codex via `/spec-all`
  - Resolved approval-gate blockers as delegated: Browse tap and standalone document intents are the anchors; UI behavior is direct external handoff with fallback. First implementation excludes embedded preview/rendering.
- **2026-05-28** - tactical plan created by Codex via `/spec-all`
  - Status advanced to Tactical for the standard external-handoff implementation.
- **2026-05-28** - created by Codex via `/spec` + `/research`
  - Added research-backed draft for Office document viewing routes. Recommended first milestone: `standard` external handoff. Lightweight preview remains optional after audit. High-fidelity embedded rendering is `noLegal`-only unless explicitly legalized for `standard`.

## Last Audit

**Date:** 2026-05-28
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 22 · WARN 0 · FAIL 0 · MANUAL 1 · EXEMPT 2

### Manual / on-device

- [ ] Optional device smoke: open DOC/DOCX/RTF/ODT from Browse and standalone intent on a device with an installed document viewer.

Static checks covered: standard debug build PASS, catalog sync PASS, feature docs EN/RU/UK present, Office MIME/extension mapping present, manifest package-visibility and standalone MIME entries present, external opener grants read URI permission and excludes this package, Player/Standalone routes use `prepareFileForRead`, no embedded Office renderer dependency added, no `Timber.d("S0299:` tags found.
