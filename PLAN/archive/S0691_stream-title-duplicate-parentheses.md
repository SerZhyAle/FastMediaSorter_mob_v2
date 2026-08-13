# Strategic spec: S0691 - Hide duplicate parenthetical stream titles

**Ticket:** S0691
**Status:** Archived
**Priority:** 50
**Date:** 2026-06-25
**Tier:** 3 - Moderate (ad-hoc)
**Roadmap entry:** Ad-hoc - parked from user `/spec-draft` request on 2026-06-25

> **Scope:** STRATEGIC skeleton. Captured idea only - no research/approval/spec-tech chaining yet.

---

## 0. Captured material (inbox)

**Captured:** 2026-06-25 (direct user request)

**Raw request (verbatim):**

> часто наименование трансляций в списке или сетке выглядит как "1001 Noites (1001 Noites)" Я понимаю, что в некоторых случаях значение в скобках другое. Но если оно одинаковое с основным - не выводить повторно

**Captured intent:**

- In the Streams feature, list or grid cards sometimes render a title in the form `Name (Name)`.
- The parenthetical suffix should stay visible when it differs from the main title.
- If the parenthetical value is the same as the main visible title, the duplicate suffix should be suppressed.

**Open angles (for later research):**

- Which exact stream surfaces are affected: catalog list, grid, search results, favorites, or all of them.
- What equality rule should be used before suppression: exact match only, or trim/case/spacing normalization too.
- Which upstream fields currently feed the `Title (Subtitle)` composition and whether the duplicate comes from metadata, catalog import, or UI formatting.

**Attachments: none.**

---

## 1. Problem

Placeholder - to be expanded when this draft is promoted (see captured material in §0).

---

## 10. Related specs

- No related specs identified yet.
