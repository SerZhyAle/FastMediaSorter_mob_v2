# Tactical Plan: S0301 - nolegal-office-document-embedded-renderer

**Strategic spec:** [`../S0301_nolegal-office-document-embedded-renderer.md`](../S0301_nolegal-office-document-embedded-renderer.md)
**Feature:** noLegal Office embedded renderer
**Tier:** 3 - Strategic, noLegal-only follow-up
**Priority:** 50
**Status:** Implemented
**Phases:** 6 / 6 done
**Last updated:** 2026-05-30

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|------------|--------|------:|------|
| 01 | family-catalog | - | ✅ Done | 3/3 | [PHASE_01__family-catalog.md](PHASE_01__family-catalog.md) |
| 02 | viewer-boundary | 01 | ✅ Done | 4/4 | [PHASE_02__viewer-boundary.md](PHASE_02__viewer-boundary.md) |
| 03 | word-family-viewer | 02 | ✅ Done | 4/4 | [PHASE_03__word-family-viewer.md](PHASE_03__word-family-viewer.md) |
| 04 | sheet-slide-viewer | 03 | ✅ Done | 4/4 | [PHASE_04__sheet-slide-viewer.md](PHASE_04__sheet-slide-viewer.md) |
| 05 | action-parity-fallbacks | 04 | ✅ Done | 4/4 | [PHASE_05__action-parity-fallbacks.md](PHASE_05__action-parity-fallbacks.md) |
| 06 | docs-catalog-cleanup | 05 | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## ✅ Resolved: embedded engine decision (default applied)

The external-engine block is lifted. Per the owner's directive to apply the strategic
default answers and continue, Phase 03 uses an **in-tree, dependency-free engine** that
satisfies §5.1.1 / §6.2 (JVM/WebView hybrid, parser-normalized-to-HTML, preview quality)
without pinning any heavy third-party Office library:

- OOXML (`.docx` / `.xlsx` / `.pptx`) and ODF (`.odt` / `.ods` / `.odp`) are ZIP+XML
  containers, parsed with the JDK `java.util.zip.ZipFile` + the already-bundled
  `org.jsoup:jsoup:1.17.2` (XML mode) → normalized HTML rendered in a locked-down WebView.
- RTF is handled by a control-word stripper.
- Legacy binary `.doc` / `.xls` / `.ppt` are out of engine scope → the viewer manager
  returns `false` and the player falls back to the existing external-open path (S0299).

This removes the app-size / license / ABI / 16 KB-page risk that motivated the block: no new
dependency is added (§6.3 permissive posture preserved, §6.6 budget unaffected). The seam is
live: `NoLegalOfficeDocumentViewerProvider.resolve()` reports `DISPLAY_INTERNALLY` for
catalog-supported families, and `officeDocumentViewerContainer` hosts the Phase 03 viewer.

---

## Pre-Implementation Blockers

- [x] **Research:** choose the internal Office engine and render strategy - resolved in strategic §5.1.1 and §6.2 with the JVM/WebView hybrid recommendation.
- [x] **Research:** confirm license / redistribution posture for the chosen engine - resolved in strategic §6.3 for the phase-1 permissive-license default path.
- [x] **Research:** pin the technical runtime budget (ABI slices, package size, startup cost) for the chosen engine - resolved in strategic §6.6 for the medium-budget phase-1 default.
- [x] **Research:** define the remote-file lifecycle (cache ownership, temp copies, retry, cleanup) - resolved in strategic §5.5 and §6.7 by reusing the S0299 materialize-to-local contract.
- [x] **Docs:** finalize the shipped wording policy for `docs/FEATURES_noLegal*.md` - resolved in strategic §6.8 and §8; exact translations remain Phase 06 output after shipped scope is known.
- [x] **UI:** resolve Phase 02 player layout and fallback-dialog ambiguity - resolved in strategic §5.2.1 and Phase 02 `UI Clarification Status`.

---

## UI Clarification Status

Status: READY

### Approved Decisions

- Portrait: Office viewer uses the same document-view area family as PDF/EPUB; the Office container replaces the active viewer surface only for noLegal Office files.
- Landscape: equivalent Office container and visibility rules must be applied to the landscape `activity_player_unified.xml` variant in the same implementation step.
- Overflow/action surface: Office actions live next to the existing PDF/EPUB document actions; unavailable Office actions are hidden.
- Visibility: show the Office container only for noLegal Office files; hide image/video/audio/PDF/EPUB-specific viewer surfaces while Office is active.
- Fallback/error behavior: provider returns the fallback result, Player/Standalone UI shows `external app` / `share` / `cancel`, and `cancel` keeps the current screen open.
- Accessibility: keyboard, D-pad, mouse, TalkBack labels, and focus order follow the existing document action surface.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] `docs/FEATURES_noLegal.md` + `_RU.md` + `_UK.md` updated.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [x] `/spec-check S0301` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log. If the whole spec is blocked, also set the journal status to one of `BlockByOtherTask` / `BlockNeedUserTest` / `BlockQuestions` / `BlockExternal`.
5. All done: flip `Status:` to `Done`, run `/spec-check S0301`.

---

## Blockers Log

- 2026-05-29 - `BlockQuestions`: Phase 02 touches portrait and landscape player layouts. Exact Office viewer host placement, visibility behavior, and fallback-dialog ownership must be decided before editing UI files.
- 2026-05-30 - Resolved: owner approved the recommended UI answers. Phase 02 is unblocked and may start from `viewer-boundary`.

---

## Change Log

- 2026-05-29 - Initial tactical plan authored by `/spec-tech`.
- 2026-05-29 - Strategic status moved from `Draft` to `Tactical` after the owner-complete approval gate and explicit proceed signal.
- 2026-05-29 - Engine-family research blocker closed: recommended phase-1 default is a permissive JVM/WebView hybrid stack; remaining blockers are remote-file lifecycle and noLegal docs wording.
- 2026-05-29 - Remaining pre-implementation blockers closed at policy level: remote-file lifecycle reuses S0299 materialization, and noLegal docs wording is constrained to read-only Office preview language.
- 2026-05-30 - UI ambiguity gate resolved with approved host, visibility, action-surface, fallback-dialog, and accessibility decisions. Phase 02 returned to `Not started`.