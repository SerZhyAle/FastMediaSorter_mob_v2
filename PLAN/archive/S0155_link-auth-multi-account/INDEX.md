# Tactical Plan: S0155 — link-auth-multi-account

**Strategic spec:** [`../S0155_link-auth-multi-account.md`](../S0155_link-auth-multi-account.md)
**Feature:** Multiple accounts per host for link-download authentication
**Tier:** 3 — Moderate (ad-hoc)
**Priority:** 60
**Status:** Done
**Phases:** 7 / 7 done
**Last updated:** 2026-05-11

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Research Items — Resolutions

Strategic §6 items are resolved here; no pre-implementation blockers remain.

**§6.1 — Account identifier extraction from WebView session**
Resolution: option (в). After the user taps "Save authorization", show a "Name this account" dialog with a best-effort hint extracted from cookies (`username`, `ds_user_id`, or similar platform cookies; otherwise empty). The user confirms or edits the name. The stable internal `accountId` is a UUID; `displayName` is the user-editable label. No platform-specific scraping of undocumented endpoints.

**§6.2 — Default account selection at share time**
Resolution: last successfully used for this host (highest `lastUsedAtEpochMillis`). If no `lastUsedAt` recorded for any account (e.g., all newly added), default to the first account alphabetically by `displayName`. No "pin" checkbox in this iteration.

**§6.3 — Per-account vs per-host denial storage**
Resolution: new dismissals are stored per `(host, accountId)`. Old per-host dismissals (from S0144, stored in `link_download_auth_offer` prefs) remain and block re-auth prompts for all accounts on that host (read both stores in `isDismissed`). New `markDismissed(host, accountId)` only writes to the per-account store.

**§6.4 — Placeholder for migrated records**
Resolution: legacy sessions (migrated from old `domain:<host>` keys) get `displayName = context.getString(R.string.s0155_account_default_name)` ("Account 1" / "Аккаунт 1" / "Акаунт 1"). No forced rename prompt. The Settings screen shows a quiet edit icon for renaming.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | store-multi-account | — | ✅ Done | 0/5 | [PHASE_01__store-multi-account.md](PHASE_01__store-multi-account.md) |
| 02 | session-context | 01 | ✅ Done | 0/3 | [PHASE_02__session-context.md](PHASE_02__session-context.md) |
| 03 | account-naming | 02 | ✅ Done | 0/4 | [PHASE_03__account-naming.md](PHASE_03__account-naming.md) |
| 04 | share-flow-integration | 03 | ✅ Done | 0/5 | [PHASE_04__share-flow-integration.md](PHASE_04__share-flow-integration.md) |
| 05 | named-reauth | 04 | ✅ Done | 0/3 | [PHASE_05__named-reauth.md](PHASE_05__named-reauth.md) |
| 06 | settings-accounts-ui | 05 | ✅ Done | 0/7 | [PHASE_06__settings-accounts-ui.md](PHASE_06__settings-accounts-ui.md) |
| 07 | docs-catalog-cleanup | all | ✅ Done | 0/5 | [PHASE_07__docs-catalog-cleanup.md](PHASE_07__docs-catalog-cleanup.md) |

Status legend: `✅ Done` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

All §6 research items resolved above. No blockers.

---

## Completion Gate

- [ ] All phases show ✅ Done.
- [ ] `docs/FEATURES.md` + `_RU.md` + `_UK.md` updated (Phase 07).
- [ ] `dev/CHANGELOG.md` has an entry for every modified file.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated via `scan.ps1`.
- [ ] `/spec-check S0155` returns `Verified`.
- [ ] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## Key Architectural Notes

**New key format in `EncryptedCookieStore`**
- Old: `domain:<host>` → cookies JSON
- New: `acct:<host>:<accountId>` → extended JSON (includes `accountId`, `displayName`, `lastUsedAtEpochMillis`)
- Migration: at first `prefs.all` access, any key starting with `domain:` is re-saved as `acct:<host>:__legacy__` with `displayName = "Account 1"` (localized string injected at call site, not inside the store itself), then the old key is deleted.

**Session context for OkHttp / WebView injection**
- `LinkDownloadSessionContext` (data singleton) holds the pre-loaded `List<HttpCookie>` for the currently executing download.
- Set by `LinkAutoDownloadCoordinator.handle()` before the pipeline runs; cleared in `finally`.
- `LinkDownloadCookieJar` and `InvisibleWebViewExtractionStrategy` read from context when active, fall back to store otherwise.

**AuthAccountDomain (new) vs AuthSessionDomain (kept for compat)**
- `AuthAccountDomain` is the canonical per-account model (host, accountId, displayName, cookieCount, savedAt, lastUsedAt).
- `AuthSessionDomain` is kept as deprecated; `observeDomains()` still emits `List<AuthSessionDomain>` (one per host, using most recent account) until Phase 06 migrates Settings UI.
- Phase 06 updates the Settings adapter to `AuthAccountDomain` and removes `AuthSessionDomain` from the live codepath.

**`AuthOfferDismissalStore` dual-store reads**
- `isDismissed(host, accountId)` returns true if `host` is in the legacy host set OR `host::accountId` is in the new account set.
- `markDismissed(host, accountId)` only writes to the new account set.

---

## How to Track Progress

1. Before starting a phase: flip row to `🚧 In Progress`. Update `Phases: X/7 done`.
2. During a phase: flip step to `[~] in progress` when started, `[x] done` when Verification passes.
3. On phase completion: confirm every step `[x]`, confirm Phase Done Criteria, flip row to `✅ Done`, bump counter.
4. If blocked: flip to `⛔ Blocked`, add bullet to Blockers Log.
5. All done: flip `Status:` to `Done`, run `/spec-check S0155`.

---

## Blockers Log

*(none)*

---

## Change Log

- 2026-05-11 — Initial tactical plan authored by `/spec-tech`.
- 2026-05-11 — All phases complete. /spec-check S0155 to verify.
