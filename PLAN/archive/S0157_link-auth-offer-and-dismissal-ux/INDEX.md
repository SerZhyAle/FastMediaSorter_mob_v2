# Tactical Plan: S0157 — link-auth-offer-and-dismissal-ux

**Strategic spec:** [`../S0157_link-auth-offer-and-dismissal-ux.md`](../S0157_link-auth-offer-and-dismissal-ux.md)
**Feature:** Auth offer dismissal UX redesign + universal host offer
**Tier:** 3 — Moderate
**Priority:** 65
**Status:** Done
**Phases:** 6 / 6 done
**Last updated:** 2026-05-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | store-type-field-migration | — | ✅ Done | 8/8 | [PHASE_01__store-type-field-migration.md](PHASE_01__store-type-field-migration.md) |
| 02 | repository-dismissed-api | 01 | ✅ Done | 5/5 | [PHASE_02__repository-dismissed-api.md](PHASE_02__repository-dismissed-api.md) |
| 03 | universal-offer-dismiss-rewire | 02 | ✅ Done | 6/6 | [PHASE_03__universal-offer-dismiss-rewire.md](PHASE_03__universal-offer-dismiss-rewire.md) |
| 04 | mandatory-naming-default | 03 | ✅ Done | 3/3 | [PHASE_04__mandatory-naming-default.md](PHASE_04__mandatory-naming-default.md) |
| 05 | dismissed-records-in-settings | 02 | ✅ Done | 4/4 | [PHASE_05__dismissed-records-in-settings.md](PHASE_05__dismissed-records-in-settings.md) |
| 06 | docs-catalog-cleanup | 03,04,05 | ✅ Done | 4/4 | [PHASE_06__docs-catalog-cleanup.md](PHASE_06__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

No blockers — §6 research items in the strategic spec are all Resolved.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase 06).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] `/spec-check S0157` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## Key Architecture Notes

- **One-time wipe migration**: Phase 01 adds a `"s0157_wiped"` flag to a new plain SharedPreferences file `"link_download_cookies_meta"`. On first run, all `acct:` and `domain:` keys in `EncryptedCookieStore` are cleared. This is safe because the feature was never released to users.
- **`type` field**: `EncryptedCookieStore.AccountEntry` gains `type: String` ("active" | "dismissed"). Dismissed entries use `accountId = "__dismissed__"`. `refreshFlows()` must NOT prune `type=dismissed` entries even though their `cookieCount == 0`.
- **`AuthOfferDismissalStore` deleted in Phase 03**: both `ReceiveShareActivity` and `LinkAutoDownloadResultPresenter` are rewired to `AuthSessionRepository.isDismissedForHost()` and `markDismissed()`.
- **Universal offer**: `ReceiveShareActivity.maybeOfferAuthThenDownload()` no longer guards on `KnownAuthResources.matchHost()`. Any http(s) URL whose host has no active session and no dismissed record triggers the offer dialog.
- **`observeAccounts()` stays pure**: returns only `type=active` accounts. New `observeAccountsAll()` includes dismissed records. Settings screen uses the latter.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes. Never flip `[x]` on intent.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0157`.

---

## Blockers Log

_(none)_

---

## Change Log

- 2026-05-11 — Initial tactical plan authored by `/spec-tech`.
