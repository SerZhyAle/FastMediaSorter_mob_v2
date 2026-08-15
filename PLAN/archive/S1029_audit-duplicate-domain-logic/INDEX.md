# Tactical Plan: S1029 - audit-duplicate-domain-logic

**Strategic spec:** [`../S1029_audit-duplicate-domain-logic.md`](../S1029_audit-duplicate-domain-logic.md)
**Research inputs:** [`research/01__duplicate-logic-inventory.md`](research/01__duplicate-logic-inventory.md)
**Feature:** One-time duplicate-domain-logic audit; fix the single top low-risk cluster; defer the rest with a note.
**Tier:** 4 - Strategic (ad-hoc, bounded)
**Priority:** 50
**Status:** Verified
**Phases:** 1 / 1 done
**Last updated:** 2026-07-14

> Owner scope: audit + fix top cluster only, NOT a recurring gate. Deferred clusters documented in research/01 (follow-up candidates).

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | consolidate-path-normalization | - | ⬜ Not started | 3 | [PHASE_01__consolidate-path-normalization.md](PHASE_01__consolidate-path-normalization.md) |

---

## Completion Gate

- [ ] Audit inventory produced (research/01) - DONE.
- [ ] Top cluster (#1 resource-strategy path normalization) consolidated; two private copies deleted.
- [ ] Behavior preserved; existing SftpResourceStrategyTest + FtpResourceStrategyTest pass unmodified.
- [ ] Deferred clusters documented (research/01 "Deferred").
- [ ] standard debug build PASS; no device needed (pure refactor) -> eligible for Verified.

---

## Change Log

- 2026-07-14 - Tactical plan authored by `/spec-tech` (F2). Single-phase (top cluster only), per owner bounded scope.
