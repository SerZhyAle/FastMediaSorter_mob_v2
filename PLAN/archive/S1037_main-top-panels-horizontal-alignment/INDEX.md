# Tactical Plan: S1037 - main-top-panels-horizontal-alignment

**Strategic spec:** [`../S1037_main-top-panels-horizontal-alignment.md`](../S1037_main-top-panels-horizontal-alignment.md)
**Reference:** [`attachments/01__reference-portrait.jpg`](attachments/01__reference-portrait.jpg)
**Feature:** The three top panels of the main screen share one leading horizontal anchor in portrait.
**Tier:** 2 - Easy (ad-hoc)
**Priority:** 50
**Status:** Done (awaiting device verification - BlockNeedUserTest)
**Phases:** 2 / 2 done
**Last updated:** 2026-07-14

> Owner decision (§6): the shared anchor = the START of the first WORKING/content button of each panel, SKIPPING any leading service/entry control. No inter-element rhythm normalization.

Panels: `res/layout/view_main_programs_panel.xml`, `res/layout/view_main_streams_panel.xml`, resource-tabs strip in `res/layout/activity_main.xml` (+ land counterparts).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | portrait-leading-anchor | - | ✅ Done | 3 | [PHASE_01__portrait-leading-anchor.md](PHASE_01__portrait-leading-anchor.md) |
| 02 | landscape-pair-and-build | 01 | ✅ Done | 2 | [PHASE_02__landscape-pair-and-build.md](PHASE_02__landscape-pair-and-build.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Completion Gate

- [ ] All 3 top panels start their first working button at one shared leading anchor in portrait.
- [ ] Overflow/collapse/tab-selection behavior unchanged.
- [ ] Land counterpart edited if the portrait file has a paired land layout (Rule 11).
- [ ] standard debug build PASS.
- [ ] Device verification (narrow + wide portrait, no stepped offset) - deferred to `BlockNeedUserTest`.

---

## Change Log

- 2026-07-14 - Tactical plan authored by `/spec-tech` (F2).
