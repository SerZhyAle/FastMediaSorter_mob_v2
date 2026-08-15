---
ticket: S0301
status: Verified
priority: 50
date: 2026-05-29
tier: 3
---

# Стратегическая спецификация: S0301 - noLegal Office embedded renderer

**Ticket:** S0301
**Status:** Archived
**Priority:** 50
**Date:** 2026-05-29
**Tier:** 3 - Strategic, noLegal-only follow-up
**Roadmap entry:** Ad-hoc - запрос 2026-05-29: добавить отдельный ticket для noLegal-части после verified-реализации S0299.
**Tactical spec:** `PLAN/S0301_nolegal-office-document-embedded-renderer/`
**Tactical plan:** `PLAN/S0301_nolegal-office-document-embedded-renderer/INDEX.md`

> **Scope:** STRATEGIC. noLegal-only embedded preview/rendering for Office documents after S0299 delivered the standard-safe external handoff. Без implementation-плана и без изменения текущего `standard` маршрута.

---

## 0. Approval Gate (owner input)

- **Requested mode:** Provided by user - spec.
- **Goal / expected outcome:** Provided by user - завести отдельный ticket для noLegal-части Office document support, вынесенной из S0299.
- **Local anchor:** Provided by user - S0299 verified implementation + отсутствие отдельного owner-tracked noLegal follow-up.
- **Scope boundaries / forbidden areas:** Provided by user - не расширять и не переоткрывать `standard` route из S0299; новый ticket покрывает только noLegal-only embedded preview/rendering.
- **Done / success signal:** Provided by user - новый strategic ticket создан в `PLAN/` и зарегистрирован в spec catalog.
- **Autonomy rule:** Delegated by user - agent may choose the slug and initial strategic structure with explicit assumptions.
- **UI decisions / delegation:** Provided by user - user expects the noLegal Office route to mirror the current PDF/EPUB viewer surface: direct internal viewing, no editing, with view/translate/OCR/print actions available in scope.

Owner follow-up clarified the remaining gate decisions on 2026-05-29: phase 1 may ship with any internal viewing quality that preserves the current PDF/EPUB-style UX, scope expands to the full Office-family, failed internal open shows a dialog (`external app` / `share` / `cancel`), medium runtime weight is acceptable, and ordinary external links may open without confirmation while active content remains blocked.

`Approval Gate` is complete. Explicit owner proceed signal was received on 2026-05-29 via `/spec-tech S0301`.

---

## 1. Проблема

- S0299 intentionally closed only the `standard`-safe route: Office documents are classified correctly and open through external apps, but no spec now owns the deeper noLegal-only renderer branch.
- Without a separate ticket, the noLegal capability has no product contract for engine choice, file-family scope, UI behavior, security rules, docs routing, or build/runtime constraints.
- The missing ticket creates a tracking gap: the feature is mentioned as a possible noLegal follow-up in S0299, but there is no dedicated artefact that can move through `Draft -> Approved -> Tactical -> In Progress`.
- This gap is not a regression in the shipped S0299 implementation. It is an unowned future surface that now needs its own lifecycle.

---

## 2. Цели

1. Выделить noLegal-only embedded Office preview/rendering в отдельный strategic ticket, не смешивая его со `standard` external handoff из S0299.
2. Зафиксировать допустимые product routes для noLegal: any internal viewing path is acceptable for phase 1 as long as it preserves the current PDF/EPUB-style UX contract.
3. Задать compile-time isolation contract: risky/heavy/unclear engine lives only in `src/noLegal/**` and does not leak into market builds.
4. Зафиксировать owner decisions, которые нужны до тактической декомпозиции: phase-1 fidelity tolerance, full Office-family scope, PDF/EPUB-style viewer parity, and the fallback dialog chain.
5. Подготовить отдельный audit surface для future docs, build validation, and `docs/FEATURES_noLegal*.md` updates when the feature is actually implemented.
6. Ограничить phase-1 document actions до view/translate/OCR/print without any editing surface.

**Non-goals:**

- Переоткрывать или расширять S0299 `standard` implementation.
- Менять public `docs/FEATURES*.md` до реализации noLegal-only capability.
- Добавлять spreadsheets or presentations в первую итерацию без owner approval.
- Выполнять macros, scripts, OLE, active content, or remote conversion uploads.
- Реализовывать engine selection or viewer UI in этом тикете.

---

## 3. Пожелания и ограничения

### 3.1 Пожелания владельца

1. noLegal part must live in a separate ticket instead of hiding inside S0299 notes.
2. Current `standard` path stays unchanged and remains the safe market surface.
3. The follow-up should stay explicit about noLegal-only routing, not as a vague future note.
4. Phase 1 may use any internal rendering approach if the UX matches the current PDF/EPUB document viewer surface.
5. First implementation should target the full Office-family rather than Word-only scope.
6. Failed internal open must fall back to an explicit dialog: external app / share / cancel.

### 3.2 Жёсткие ограничения

- **Flavor:** `noLegal` only. Any embedded renderer/converter must be isolated to flavor-specific source sets and dependencies.
- **API level:** baseline `noLegal` is API 26+.
- **Wear OS:** not affected.
- **Производительность:** parsing, conversion, and rendering preparation run off the UI thread; very large documents need explicit fallback or size caps.
- **Совместимость данных:** no Room schema change or persistent settings contract is assumed at the strategic stage.
- **Локализация:** EN/RU/UK required for any future user-visible strings.
- **Доступность:** if a dedicated viewer screen or actions appear, they must support TalkBack, keyboard, D-pad, and mouse.
- **Flavor isolation:** no new `BuildConfig.IS_*` checks in `src/main/java`; compile-time source-set isolation is mandatory.
- **Packaging:** any native engine must satisfy the repo's ABI and 16 KB page-size requirements for the shipped noLegal slice.
- **Runtime budget:** medium runtime / package overhead is acceptable for noLegal phase 1, but not an unbounded heavyweight experiment.
- **Security:** Office documents remain passive, untrusted inputs. No macros, embedded scripts, remote active content, or auto-followed external links.
- **Link policy:** ordinary hyperlinks may open normally from the internal viewer; active content remains blocked.
- **Docs policy:** noLegal-only capability is documented only in `docs/FEATURES_noLegal*.md` after implementation, never in public feature inventory.
- **UI copy:** future strings must pass `docs/COMMUNICATION_POLICY.md` tone checklist.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** `S0299` (verified standard external handoff parent), `S0156` (noLegal capability policy parent), `S0288` (precedent for heavy noLegal-only runtime packaging), `S0297` (research-first noLegal follow-up precedent).
- **Proceed signal:** owner explicitly selected both `1` and `2` on 2026-05-29, which means "approve now" and "run /spec-tech S0301" in the current thread.
- **Delegated scope:** tactical decomposition, phase graph, and compile-time isolation shape are delegated to the agent as long as the plan preserves PDF/EPUB-style UX parity, read-only scope, explicit fallback dialog, and noLegal-only runtime isolation.

---

## 4. Контекст текущей архитектуры

S0299 already established the safe product split: `standard` recognizes Word-family Office documents and opens them externally through the existing local-materialization path. That route is implemented and verified.

The repository also already has a formal flavor policy: noLegal-only capabilities must be isolated through source sets, interfaces, and flavor-specific dependency wiring instead of runtime gates in `src/main`.

Today there is no spec-owned noLegal Office renderer path. The codebase has document classification and external handoff, but no strategic contract for an embedded Office preview/rendering capability.

---

## 5. Предлагаемый подход

### 5.1 Capability split

- Keep S0299 as the owner of `standard` external handoff.
- Let S0301 own every noLegal-only embedded Office route beyond that baseline.
- Treat the first noLegal milestone as one of these explicitly named surfaces:
  - `text preview`
  - `structured preview`
  - `converted preview`
  - `embedded renderer`

### 5.1.1 Recommended phase-1 engine family

- **Recommended default:** a JVM/WebView hybrid Office stack isolated to `noLegal`: parser/converter libraries for Office families normalize content into HTML, table, and slide models; the existing player host then renders those models inside the shared Office viewer surface.
- **Why this fits S0301:** the owner explicitly accepted any internal viewing quality that preserves the current PDF/EPUB-style UX, and the repo already has a mature document shell built around WebView, text extraction, OCR/translation actions, and print adapters.
- **Why not native-first:** a LibreOffice/Collabora-style native core would overshoot the accepted medium runtime budget, add 16 KB / ABI / NDK complexity similar to or above the current PaddleOCR branch, and create a much heavier integration surface before phase-1 UX parity is proven.
- **Why not AGPL/commercial web-office first:** embedded web-office bundles are possible, but they add license ambiguity and usually want a server-style conversion/backend model that does not fit the current offline-first, no-upload constraints.
- **Practical family split for phase 1:**
   - Word-family (`.doc`, `.docx`, `.rtf`, `.odt`) → structured HTML/text-first preview.
   - Spreadsheet-family (`.xls`, `.xlsx`, `.ods`) → table/grid preview.
   - Presentation-family (`.ppt`, `.pptx`, `.odp`) → slide-by-slide preview.

### 5.2 Product routing contract

- noLegal direct tap follows the current PDF/EPUB pattern: the file opens straight into the internal document viewer rather than a chooser-first route.
- The phase-1 internal viewer surface matches the current document stack as closely as practical: viewing first, no editing, with translate/OCR/print actions available when the Office engine can support them safely.
- If internal opening fails, phase 1 shows an explicit fallback dialog with `external app`, `share`, and `cancel` actions instead of an automatic external handoff.
- The chosen embedded route must define the same fallback chain for local, remote, and cloud documents.
- Future copy must distinguish `preview` from `full rendering` so the product never promises Word-perfect fidelity unless the engine really delivers it.

### 5.2.1 UI clarification outcome

`/ui-clarify` status: READY.

- Office uses the same host and command surface family as the existing PDF/EPUB route.
- The Office container replaces the active document-view area only for noLegal Office files; other media/viewer surfaces remain hidden while the Office viewer is active.
- Portrait and landscape use the same behavior and equivalent layout slots.
- Office actions live in the existing overflow/action surface next to PDF/EPUB document actions.
- Unavailable Office actions are hidden rather than shown disabled.
- The UI layer owns the fallback dialog after the provider returns a fallback result.
- `Cancel` dismisses the fallback dialog and keeps the user on the current screen without calling `finish()`.
- Loading, empty, unsupported, and error states follow the current document-viewer pattern; unsupported Office files route to the fallback dialog.
- Keyboard, D-pad, mouse, TalkBack labels, and focus order must match the existing document action surface.

### 5.3 Flavor boundary

- Shared code sees only a capability boundary and a safe fallback contract.
- noLegal contributes the real implementation and risky dependencies.
- Market builds keep the current S0299 behavior with no bytecode or resource leakage from the noLegal engine.

### 5.4 File-family scope

- Phase 1 scope is the full Office-family.
- Tactical planning must still split families by confidence and verification cost if one engine path does not cover documents, spreadsheets, and presentations equally well.

### 5.5 Security and lifecycle

- Remote and cloud files reuse the existing materialize-to-local flow.
- Office viewer sessions receive only readable local files prepared through the same materialize-to-local contract that S0299 uses for external Office handoff.
- Session-owned temp files are deleted when the Office viewer closes, when the Activity finishes, or when internal open fails before rendering starts.
- App-cache materialized copies remain owned by the existing cache layer; the Office viewer never writes persistent copies outside app-private storage.
- A failed internal preparation does not retry indefinitely: phase 1 may perform one controlled re-materialization attempt, then shows the explicit fallback dialog.
- Embedded rendering never upgrades Office documents from passive content to executable content.
- Ordinary document hyperlinks may open from the viewer without an extra confirmation step, but active content remains blocked.

---

## 6. Открытые вопросы / Research items

1. **Fidelity target**
   - **Вопрос:** phase 1 accepts any internal rendering path if it preserves the PDF/EPUB-style UX; exact under-the-hood path still needs engineering selection.
   - **Статус:** Resolved - the owner accepted preview-quality tolerance for phase 1, and the technical default is the documented JVM/WebView hybrid path.

2. **Engine family**
   - **Вопрос:** какой technology path realistic for Android noLegal packaging?
   - **Статус:** Resolved - phase-1 default is a JVM/WebView hybrid stack. Recommended path: POI-class Office parsers plus permissive ODF/RTF parsing where needed, normalized into HTML/table/slide models for the shared internal viewer shell. Deferred alternatives: embedded web-office bundle in WebView; native LibreOffice/Collabora-style core.

3. **License / owner acceptance**
   - **Вопрос:** какие license/commercial/runtime trade-offs владелец готов принять для sideload-only noLegal build?
   - **Статус:** Resolved for phase 1 - default path stays on permissive JVM parser/converter licenses only. AGPL/commercial and native-heavy alternatives remain deferred, not the baseline implementation path.

4. **UI route**
   - **Вопрос:** нужен ли какой-то secondary action besides the direct internal open, or is the PDF/EPUB-style viewer-first route sufficient for phase 1?
   - **Статус:** Resolved - direct internal open; fallback dialog = `external app` / `share` / `cancel`.

5. **Document family scope**
   - **Вопрос:** остаёмся на `.doc/.docx/.rtf/.odt` или future capability covers spreadsheets/presentations too?
   - **Статус:** Resolved - phase 1 targets the full Office-family.

6. **Packaging budget**
   - **Вопрос:** acceptable APK/runtime overhead for the noLegal engine, ABI coverage, and startup budget.
   - **Статус:** Resolved - medium overhead maps to a JVM/WebView phase-1 stack. Native office-core packaging is explicitly out of the default phase-1 budget.

7. **Remote-file lifecycle**
   - **Вопрос:** how embedded preview interacts with cache eviction, temp copies, retries, and offline fallback.
   - **Статус:** Resolved - reuse the S0299 materialize-to-local contract for local/content/remote/cloud sources; viewer-created temp files are session-owned and cleaned on close/failure; cache-layer copies stay owned by the existing cache lifecycle; after one controlled preparation retry, show the fallback dialog.

8. **Docs surface**
   - **Вопрос:** what exact noLegal feature wording should land in `docs/FEATURES_noLegal*.md` after implementation.
   - **Статус:** Resolved at policy level - Phase 06 owns the exact shipped EN/RU/UK wording after implementation scope is known. The required wording must call the feature a read-only Office preview unless implementation proves full-fidelity rendering.

9. **Ordinary hyperlink behavior**
   - **Вопрос:** should standard non-active hyperlinks inside documents require confirmation?
   - **Статус:** Resolved - no confirmation needed for ordinary links; active content remains blocked.

---

## 7. Риски

- **Engine bloat:** native or JVM-heavy renderer can inflate noLegal APK size, startup time, and memory footprint beyond acceptable sideload targets.
- **License ambiguity:** even for noLegal, unclear redistribution terms create long-term maintenance and publication risk.
- **Expectation mismatch:** users may read `viewer` as Word-perfect fidelity when the implementation is only a preview/conversion layer.
- **Remote-cache leakage:** materialized files may linger if the embedded route does not reuse the existing cleanup contract.
- **UI fragmentation:** if noLegal diverges too far from S0299 behavior without an explicit UX contract, user expectations become inconsistent across flavors.
- **Security regression:** any engine that interprets scripts, macros, or embedded links too loosely breaks the passive-document contract.

---

## 8. Влияние на пользователя (docs/FEATURES)

If S0301 leads to a shipped noLegal-only capability:

- `docs/FEATURES_noLegal.md` gets a dedicated bullet describing the embedded Office preview/rendering scope.
- `docs/FEATURES_noLegal_RU.md` and `docs/FEATURES_noLegal_UK.md` receive mirrored wording.
- Baseline wording policy: call the shipped capability `read-only Office preview`, list the Office families that actually work, mention view/translate/OCR/print only if callbacks are verified, and avoid promising Word-perfect layout.
- Public `docs/FEATURES.md` + RU + UK stay unchanged because the capability is not part of market builds.

---

## 9. Архитектурные решения (ADR)

**ADR-1: S0299 remains closed; noLegal work moves to its own ticket**

- **Решение:** treat the missing noLegal renderer as a new tracked surface, not as a reason to reopen the verified `standard` handoff ticket.
- **Альтернативы:** append the work informally to S0299 or reopen S0299 with mixed-flavor scope.
- **Почему:** `standard` external handoff and noLegal embedded rendering have different compliance, dependency, packaging, and UX constraints.

**ADR-2: Compile-time isolation over runtime flavor branching**

- **Решение:** the future renderer must live behind flavor-specific boundaries and noLegal-only dependencies.
- **Альтернативы:** runtime `BuildConfig` branching inside `src/main`.
- **Почему:** the repo's flavor rules explicitly prohibit new noLegal leakage into market builds.

**ADR-3: Product wording follows real fidelity**

- **Решение:** future UX copy must say `preview` unless the selected engine genuinely provides full-fidelity rendering.
- **Альтернативы:** call every route a `viewer` regardless of output quality.
- **Почему:** honest naming prevents support noise and keeps expectations aligned with the chosen engine.

**ADR-4: Office documents stay passive inputs**

- **Решение:** no macros, no scripts, no OLE execution, no server-side conversion upload as a hidden implementation detail.
- **Альтернативы:** delegate risky active content to third-party engines or remote services.
- **Почему:** security and privacy costs are out of proportion to the value for FMS.

**ADR-5: Phase 1 uses a JVM/WebView hybrid stack, not a native office core**

- **Решение:** the default noLegal implementation path is a permissive-license JVM parser/converter stack that renders into the existing shared viewer shell.
- **Альтернативы:** embedded web-office bundle in WebView; native LibreOffice/Collabora-style core.
- **Почему:** it best matches the accepted medium runtime budget, reuses the current PDF/EPUB-style viewer shell, and minimizes ABI / NDK / page-alignment risk for the first iteration.

---

## 10. Связи с другими спеками

- **S0299** - parent split: `standard` Office external handoff delivered and verified; S0301 owns the noLegal-only embedded follow-up.
- **S0156** - policy parent for noLegal capability isolation and noLegal-only feature documentation.
- **S0288** - precedent for heavy sideload-only runtime packaged exclusively in `noLegal`.
- **S0297** - precedent for research-first noLegal follow-ups that become separate implementation tickets only after owner selection.

---

## 11. Критерии готовности (strategic-level)

1. The owner-confirmed viewer surface stays aligned with the current PDF/EPUB route: direct internal open, no editing, and view/translate/OCR/print actions in scope.
2. The owner-confirmed fallback is an explicit dialog: `external app` / `share` / `cancel`.
3. The supported file-family scope is explicit for phase 1: full Office-family.
4. The chosen engine family has a documented packaging, license, and security posture consistent with the owner-accepted medium runtime budget.
5. Link behavior is explicit: ordinary links may open; active content remains blocked.
6. Flavor isolation requirements are explicit before any tactical decomposition.
7. The ticket is ready to move to `/spec-tech` without reopening S0299.

---

## 12. Ссылка на тактическую спецификацию

Тактический план: `PLAN/S0301_nolegal-office-document-embedded-renderer/INDEX.md`.

---

## Revision History

- **2026-05-29** - created by Codex via `/spec`
  - Added a dedicated strategic draft for the noLegal Office embedded-renderer follow-up so S0299 can remain verified as the standard external-handoff ticket.
- **2026-05-29** - refined by Codex via `/spec-update` (focus: completeness, consistency)
   - Applied owner input: noLegal Office UX should mirror the current PDF/EPUB viewer surface, stay read-only, and keep view/translate/OCR/print actions in scope.
- **2026-05-29** - refined by Codex via `/spec-update` (focus: completeness, approval gate)
   - Applied owner answers for fidelity tolerance, full Office-family scope, explicit fallback dialog, medium runtime budget, and ordinary-link policy.
- **2026-05-29** - promoted and decomposed by Codex via `/spec-tech`
   - Approval gate was complete, so the explicit `/spec-tech S0301` proceed signal promoted the draft and authored the tactical phase plan. Strategic status advanced to `Tactical`.
- **2026-05-29** - refined by Codex via `/spec-update` (focus: engine-family blocker)
   - Closed the phase-1 engine-family research blocker with a recommended JVM/WebView hybrid stack, permissive-license default, and medium-budget packaging decision. Native and AGPL/commercial office cores remain deferred alternatives.
- **2026-05-29** - refined by Codex via `/spec-update` (focus: spec-dev readiness)
   - Closed remote-file lifecycle and docs-surface blockers at policy level so tactical execution can start without inventing a second cache contract or premature shipped copy.
- **2026-05-29** - advanced by Codex via `/spec-dev` (Phase 01 complete, blocked before Phase 02)
   - Implemented and verified Phase 01 family catalog. Stopped before Phase 02 because shared Office viewer host placement and visibility rules require explicit UI decisions before editing portrait/landscape player layouts.
- **2026-05-30** - refined by Codex via `/spec-update` (focus: UI clarification)
   - Applied owner-approved recommended UI answers: Office reuses the PDF/EPUB-style host/action surface, hides unavailable actions, keeps fallback-dialog ownership in the UI layer, and unblocks Phase 02.

## Last Audit

**Date:** 2026-05-30
**Mode:** full
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 47 · WARN 0 · FAIL 0 · MANUAL 2 · EXEMPT 2

### Manual / on-device

- [x] Static / compile validation: `:app_v2:assembleNoLegalDebug`, `:app_v2:assembleStandardDebug`, and `CommandPanelLayoutPlannerTest` passed after fixes.
- [ ] Optional device smoke: open representative DOCX/XLSX/PPTX/ODT/ODS/ODP/RTF files and one unsupported legacy binary to verify fallback dialog behavior on hardware.