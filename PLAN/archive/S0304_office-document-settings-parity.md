---
ticket: S0304
status: Verified
priority: 55
date: 2026-05-30
tier: 3
---

# Стратегическая спецификация: S0304 - Office document settings parity

**Ticket:** S0304
**Status:** Archived
**Priority:** 55
**Date:** 2026-05-30
**Tier:** 3 - Moderate, UI/resource parity follow-up
**Roadmap entry:** Ad-hoc - запрос 2026-05-30: после S0299/S0301 привести settings и resource UI для Office-документов к паритету с PDF/EPUB.
**Tactical spec:** `PLAN/S0304_office-document-settings-parity/INDEX.md`

> **Scope:** STRATEGIC. Цели, ограничения, открытые вопросы. Без имён классов, путей, лимитов строк, миграций Room, модулей Hilt.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - collect data and create a new strategic spec.
- **Goal / expected outcome:** Provided by user - проанализировать и спланировать изменения UI application/resource settings, чтобы работа с Office-документами была похожа на существующие PDF/EPUB настройки.
- **Local anchor:** Provided by user - S0299; фактический follow-up также связан с verified noLegal Office renderer.
- **Scope boundaries / forbidden areas:** Provided by user - standard и noLegal должны получить явный Office document support в тех местах, где пользователь уже видит PDF/EPUB settings; rendering/routing logic из S0299/S0301 не переизобретать.
- **Done / success signal:** Provided by user - собраны данные по текущему UI, создан новый specification task.
- **Autonomy rule:** Delegated by user - agent may decide with explicit assumptions for research and draft structure; implementation still requires owner review.
- **UI decisions / delegation:** Provided by user - add Office/DOC support everywhere PDF/EPUB settings are currently visible; preserve the existing PDF/EPUB placement pattern unless tactical research proves the compact resource grid needs a bounded layout adjustment.

`Approved` is blocked until the owner reviews this draft or explicitly runs `/spec-tech S0304`.

---

## 1. Проблема

S0299 и S0301 уже закрыли базовое открытие Office-документов: `standard` отдаёт их внешнему приложению, `noLegal` может показывать поддерживаемые семейства внутри встроенного viewer. Но пользовательские settings/resource экраны всё ещё выглядят как старый мир: Text, PDF и EPUB видны как отдельные document-типы, а Office остаётся неявным следствием document bucket.

Из-за этого пользователь может включать, отключать, копировать или создавать document resources и не понимать, будут ли DOC/DOCX/RTF/ODT, а в noLegal ещё XLS/XLSX/ODS/PPT/PPTX/ODP, участвовать в фильтрах ресурса. Риск не только UX: при ручной правке resource media types Office может выпадать из набора, хотя data layer уже поддерживает его как отдельный document media type.

---

## 2. Цели

1. Пользователь видит Office documents в Document settings рядом с Text, PDF и EPUB, а не только как скрытую часть общего document support.
2. Resource create/edit flows показывают Office/DOC как явный media-type choice в тех же секциях, где уже есть PDF и EPUB.
3. Document profile presets, virtual documents resources and document filters include Office documents without silent loss when the user edits media types manually.
4. Default document viewer setup is no longer PDF-only; it either covers representative Office MIME types or gives a clear type choice before opening the Android default-app flow.
5. `standard` copy honestly describes external Office handoff; `noLegal` copy can mention built-in Office viewing only for its flavor-supported families.
6. All new/updated user-visible strings are localized EN/RU/UK and follow the communication policy.

**Non-goals:**

- New Office renderer, converter or parser implementation.
- Changing S0299 external handoff behavior for `standard`.
- Changing S0301 embedded viewer behavior for `noLegal`.
- Adding spreadsheets or presentations to `standard` beyond its current flavor catalog.
- Wear OS support.
- Room schema migration.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. Treat Office document settings like the existing PDF/EPUB settings, not as a hidden side effect.
2. Cover all visible application settings and resource settings where PDF/EPUB currently appear.
3. Include both `standard` and `noLegal` in the scope.
4. Preserve existing S0299/S0301 routing decisions.

### 3.2 Жёсткие ограничения

- **Flavor:** `standard` exposes Word-family Office handoff only; `noLegal` may expose full Office-family embedded viewing where already supported. Unsupported document flavors must hide or no-op consistently with the existing document gates.
- **Flavor isolation:** shared UI logic must not branch on raw flavor names or add new noLegal checks in common code; capability must come from the existing flavor-safe document family surface.
- **API level:** `standard` and `noLegal` baseline API 26+.
- **Wear OS:** not touched.
- **Performance:** settings/resource UI changes must not trigger document scans or MIME probing on the UI thread.
- **Совместимость данных:** reuse the existing Office document media type and stored flags; no Room migration.
- **Локализация:** EN/RU/UK are mandatory for all labels, subtitles, tooltips, dialog text and fallback copy.
- **Доступность:** every new row/checkbox/button must preserve touch target, D-pad/keyboard focus, TalkBack labels and mouse activation parity with adjacent PDF/EPUB controls.
- **UI copy:** wording must distinguish `standard` external handoff from `noLegal` built-in viewing and must pass `docs/COMMUNICATION_POLICY.md` tone checklist.
- **Orientation:** current affected settings/resource layouts have no dedicated landscape counterparts; tactical implementation must explicitly verify default-layout behavior in portrait and landscape.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S0299, S0301, S0156.
- **Requested mode:** Explicit `/spec-all S0304` on 2026-05-30; proceed through tactical planning, implementation and verification.
- **Goal / expected outcome:** Make Office document settings and resource media-type choices visible wherever Text, PDF and EPUB are already exposed.
- **Local anchor:** S0299 standard-safe Office handoff and S0301 noLegal embedded Office viewer.
- **Scope boundaries / forbidden areas:** Do not change Office rendering, routing, conversion, Room schema or Wear behavior.
- **Autonomy rule:** Agent may make bounded tactical UI decisions that preserve the existing PDF/EPUB placement pattern.
- **UI decisions / delegation:** Add an Office documents control next to document settings and resource media-type controls; use flavor-safe capability surfaces for visibility and copy.

---

## 4. Контекст текущей архитектуры

Research found the document pipeline in a split state. Data and playback already know Office documents as a document media type, and the flavor model already separates `standard` Word-family handoff from `noLegal` full-family embedded viewing. The Browse/player route is therefore not the missing part of this task.

The missing part is the user-facing configuration layer. Document settings currently present Text, PDF and EPUB as first-class toggles, including a PDF-only default document viewer probe. Resource editor and add-resource media-type sections expose individual media-type checkboxes for video, audio, images, GIF, text, PDF and EPUB, but not Office. Document presets already intend to include Office, so the UI can drift from the underlying model when a user saves explicit media-type choices.

Existing resource screens use compact checkbox grids. Adding Office must preserve that density while avoiding truncation and inaccessible hit targets. There are no dedicated landscape layout variants for the affected screens, so the default layout must be validated in both orientations.

---

## 5. Предлагаемый подход

### 5.1 Office document setting as a first-class document control

Add one Office document support control to the Document settings area at the same hierarchy level as Text, PDF and EPUB. The control must be governed by the same all-files behavior as the other document toggles.

The label should use a stable product term such as `Office documents`. Compact resource grids may use `DOC` only where space already forces PDF/EPUB-style abbreviations.

### 5.2 Resource media-type parity

Every create/edit resource flow that currently exposes PDF and EPUB media-type choices must expose Office documents in the same media-type group. This includes document quick-setup presets, manual network resources, copied resources and edited resources.

When all-files mode is enabled, Office must follow the same checked/disabled state as adjacent document checkboxes. When a document profile preset is selected, Office must be included in the resulting explicit media-type set.

### 5.3 Flavor-aware family wording

The UI has one Office document bucket but different family coverage by flavor. `standard` wording must describe DOC/DOCX/RTF/ODT external open. `noLegal` wording may describe built-in Office viewing and the wider spreadsheet/presentation family only where that family is actually supported.

This should be solved through flavor-safe capability/copy surfaces, not common-code flavor conditionals.

### 5.4 Default document viewer parity

The document default-app helper must stop using PDF as the only representative document MIME. If Android forces a single MIME per default-app chooser, the UI should offer a small type choice or use a representative Office probe in addition to PDF. The wording must avoid promising a global default if Android only applies the selected MIME family.

### 5.5 Data and event flow

- Settings UI toggles the global document media-type capability.
- Resource UI intersects global settings, profile presets and explicit resource media-type choices.
- Browse/scanning consumes the resulting document media-type set.
- Player routing remains owned by existing Office document handoff/viewer decisions.
- Failure and unsupported states remain in the existing Office fallback path.

### 5.6 Extension points

- The visible Office family list must follow the flavor-supported family catalog.
- Resource media-type grids must tolerate additional document families later without another structural rewrite.
- Default-viewer setup must allow adding more representative document MIME families later.

---

## 6. Открытые вопросы / Research items

1. **Existing PDF/EPUB UI anchors**
   - **Вопрос:** где пользователь уже видит PDF/EPUB settings?
   - **Варианты:** Document settings, resource editor media-type grid, add-resource SMB/SFTP media grids, document profile presets, virtual documents resource, default document viewer setup, document filter labels.
   - **Нужно выяснить:** нет дополнительных secondary settings screens outside the current static audit.
   - **Статус:** Resolved for draft - current static research found the anchors above.

2. **Office model readiness**
   - **Вопрос:** можно ли добавить UI без новой persistence model?
   - **Варианты:** reuse existing Office document media type; add new flags/settings; fold into PDF/EPUB.
   - **Нужно выяснить:** whether implementation can reuse the existing stored media-type flag everywhere.
   - **Статус:** Resolved for draft - existing model already has an Office document bucket and resource flag.

3. **Default document viewer UX**
   - **Вопрос:** как честно запустить Android default-app flow для PDF плюс Office?
   - **Варианты:** one button with type chooser; separate PDF/Office buttons; representative Office probe after PDF; settings fallback only.
   - **Нужно выяснить:** Android chooser/default behavior for multiple document MIME families on target devices.
   - **Статус:** Open - tactical UI decision required before implementation.

4. **Compact grid capacity**
   - **Вопрос:** как добавить Office в compact resource media grids without truncation?
   - **Варианты:** add a fourth document slot; reflow to three rows; use a compact `DOC` label; introduce a document sub-row.
   - **Нужно выяснить:** portrait and landscape measurements on 360 dp width and tablet/wide screens.
   - **Статус:** Open - tactical layout decision required.

5. **Flavor-specific copy surface**
   - **Вопрос:** где держать different `standard` vs `noLegal` wording?
   - **Варианты:** flavor resource override; capability-derived subtitle; shared generic copy.
   - **Нужно выяснить:** lowest-risk implementation that avoids common-code flavor checks.
   - **Статус:** Open - tactical design required.

6. **Feature docs scope**
   - **Вопрос:** does this change require public feature inventory updates or only refine existing Office handoff wording?
   - **Варианты:** update existing public Office handoff bullet; add settings/resource bullet; noLegal-only doc update for full-family embedded UI.
   - **Нужно выяснить:** final implementation surface.
   - **Статус:** Open - decide after tactical scope is frozen.

---

## 7. Риски

| Риск | Вероятность | Последствия | Митигация |
|------|:-----------:|-------------|-----------|
| Office remains hidden behind Text/PDF/EPUB toggles | Средняя | user cannot predict whether DOC files appear in resources | first-class Office row/checkbox wherever PDF/EPUB appear |
| Resource editor drops Office during manual save | Средняя | existing document resources lose Office visibility | include Office in explicit media-type read/write path |
| `standard` copy implies built-in Office viewing | Средняя | user trust and compliance risk | flavor-aware wording, no fidelity promises |
| Compact resource grids become cramped | Высокая | truncated labels, poor D-pad/TalkBack behavior | tactical layout measurement and accessibility pass |
| Default-app setup overpromises Android behavior | Средняя | user expects all document defaults to change at once | honest chooser wording and type-specific flow |
| New common code branches by noLegal flavor | Низкая | flavor isolation regression | use existing capability/family surfaces and flavor resource overrides |

---

## 8. Влияние на пользователя (docs/FEATURES)

After implementation, refine the existing public Office handoff entry or Settings/Resource Management entries to say that Office documents can be enabled explicitly in document settings and resource media-type filters. If noLegal-specific full-family UI is exposed, update only `docs/FEATURES_noLegal*.md` for embedded spreadsheet/presentation viewing.

---

## 9. Архитектурные решения (ADR)

**ADR-1: Office is a first-class document settings item**

- **Решение:** expose Office documents as their own document UI control wherever PDF/EPUB are configurable.
- **Альтернативы:** keep Office implicit when Text/PDF/EPUB are enabled; add only to all-files mode.
- **Почему:** implicit behavior creates resource-save drift and hides an already shipped document capability.

**ADR-2: One UI bucket, flavor-specific family coverage**

- **Решение:** use one Office document bucket in common UI, with family coverage and wording resolved by flavor-safe capability surfaces.
- **Альтернативы:** create separate Word/Spreadsheet/Presentation toggles now; add noLegal checks in shared UI.
- **Почему:** the stored model already has one Office document bucket, and family-specific UI would exceed the current parity task.

**ADR-3: Match PDF/EPUB placement before adding a new settings surface**

- **Решение:** add Office beside existing PDF/EPUB controls instead of creating a separate Office settings page.
- **Альтернативы:** create an Office settings subsection; hide Office under advanced options.
- **Почему:** the owner requested similar behavior to PDF/EPUB, and the existing document settings surface is the discoverable place.

**ADR-4: Default document viewer wording must be MIME-specific**

- **Решение:** default-app setup must acknowledge that Android may bind defaults per MIME family.
- **Альтернативы:** keep a single PDF probe under a generic document label.
- **Почему:** current PDF-only probe conflicts with the broader document viewer wording after Office support.

---

## 10. Связи с другими спеками

- **S0299** - parent standard-safe Office external handoff; verified.
- **S0301** - noLegal embedded Office renderer and full-family Office catalog; verified.
- **S0156** - noLegal capability surface and public/noLegal docs split.

---

## 11. Критерии готовности (strategic-level)

1. Document settings shows Office documents at the same level as Text, PDF and EPUB.
2. All-files mode checks and disables Office document support consistently with other document media types.
3. Resource editor create/edit surfaces can display, load, save and preserve Office document media-type selection.
4. Add-resource SMB/SFTP media-type sections include Office documents wherever PDF and EPUB are available.
5. Document quick-setup presets include Office documents in the visible selected set.
6. Virtual All Documents resources include Office documents when document support is enabled.
7. Default document viewer setup no longer uses PDF as the only document probe without explanation.
8. `standard` UI text describes external opening for DOC/DOCX/RTF/ODT.
9. `noLegal` UI text may describe built-in Office viewing only for the supported full Office family.
10. EN/RU/UK string parity passes for all new or changed user-visible strings.
11. Portrait and landscape checks confirm no truncation or inaccessible controls in affected settings/resource screens.
12. Standard and noLegal debug builds pass after implementation.

---

## 12. Ссылка на тактическую спецификацию

Тактическая спецификация: `PLAN/S0304_office-document-settings-parity/INDEX.md`.

---

## Revision History

- **2026-05-30** - promoted by Codex via `/spec-all`
  - Owner gate materialized for catalog promotion and tactical phase plan created.
- **2026-05-30** - verified by Codex via `/spec-all`
  - Implemented Office document settings/resource parity and verified standard/noLegal debug builds.
- **2026-05-30** - created by Copilot via `/spec`
  - Added strategic draft for Office document settings/resource UI parity after verified S0299 and S0301.
