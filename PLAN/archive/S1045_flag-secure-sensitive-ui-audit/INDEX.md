# Tactical Plan: S1045 - flag-secure-sensitive-ui-audit

**Strategic spec:** [`../S1045_flag-secure-sensitive-ui-audit.md`](../S1045_flag-secure-sensitive-ui-audit.md)
**Research inputs:** [`research/01__secret-surface-inventory.md`](research/01__secret-surface-inventory.md)
**Feature:** Targeted `FLAG_SECURE` on secret-bearing screens, gated by a new user setting (default ON).
**Tier:** 3 - Moderate (ad-hoc)
**Priority:** 55
**Status:** Done (awaiting device verification - BlockNeedUserTest)
**Phases:** 6 / 6 done
**Last updated:** 2026-07-14

> Scope: tactical, English, developer handoff. Every step has a verification predicate. Rationale in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | setting-foundation | - | ⬜ Not started | 3 | [PHASE_01__setting-foundation.md](PHASE_01__setting-foundation.md) |
| 02 | baseactivity-secure-hook | 01 | ⬜ Not started | 2 | [PHASE_02__baseactivity-secure-hook.md](PHASE_02__baseactivity-secure-hook.md) |
| 03 | optin-secret-activities | 02 | ⬜ Not started | 4 | [PHASE_03__optin-secret-activities.md](PHASE_03__optin-secret-activities.md) |
| 04 | webview-dialog-secure | 01 | ⬜ Not started | 1 | [PHASE_04__webview-dialog-secure.md](PHASE_04__webview-dialog-secure.md) |
| 05 | settings-ui-toggle | 01 | ⬜ Not started | 3 | [PHASE_05__settings-ui-toggle.md](PHASE_05__settings-ui-toggle.md) |
| 06 | docs-catalog-cleanup | all | ⬜ Not started | 3 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

None - strategic §6 has no open items (owner decision + research/01 resolved all forks).

---

## Completion Gate

- [ ] All phases ✅ Done.
- [ ] `AppSettings.secureSensitiveScreens` persists (DataStore round-trip unit test passes).
- [ ] Each targeted surface applies `FLAG_SECURE` iff setting ON; `CompanionQrShareActivity` no longer sets it unconditionally.
- [ ] Settings toggle added; Rule 22 docs regenerated; `assert-settings-doc-sync` green.
- [ ] standard debug build PASS.
- [ ] Device verification (blank Recents / blocked screenshot per surface) - deferred to `BlockNeedUserTest`.

---

## Change Log

- 2026-07-14 - Tactical plan authored by `/spec-tech` (F2).
