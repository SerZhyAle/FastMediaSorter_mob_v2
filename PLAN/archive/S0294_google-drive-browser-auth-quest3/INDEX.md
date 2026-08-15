# Tactical Plan: S0294 - google-drive-browser-auth-quest3

**Strategic spec:** [../S0294_google-drive-browser-auth-quest3.md](../S0294_google-drive-browser-auth-quest3.md)
**Feature:** Google Drive browser auth on Quest / XR without GMS
**Tier:** 4 - Strategic (ad-hoc)
**Priority:** 80
**Status:** Verified
**Phases:** 4 / 4 done
**Last updated:** 2026-05-24

> **Scope:** tactical, English, developer handoff. Every step has a verification predicate. Rationale lives in the strategic spec.

---

## Phase Overview

| # | Phase | Depends on | Status | Steps | File |
|---|-------|-----------|--------|------:|------|
| 01 | browser-oauth-foundation | - | ✅ Done | 2/2 | [PHASE_01__browser-oauth-foundation.md](PHASE_01__browser-oauth-foundation.md) |
| 02 | drive-auth-routing | 01 | ✅ Done | 3/3 | [PHASE_02__drive-auth-routing.md](PHASE_02__drive-auth-routing.md) |
| 03 | reauth-surfaces | 02 | ✅ Done | 3/3 | [PHASE_03__reauth-surfaces.md](PHASE_03__reauth-surfaces.md) |
| 04 | docs-catalog-cleanup | 01,02,03 | ✅ Done | 3/3 | [PHASE_04__docs-catalog-cleanup.md](PHASE_04__docs-catalog-cleanup.md) |

Status legend: `⬜ Not started` · `🚧 In Progress` · `✅ Done` · `⛔ Blocked` · `⏭️ Skipped`

---

## Pre-Implementation Blockers

- [x] **Redirect contract resolved:** reuse `com.sza.fastmediasorter:/oauth2redirect` from S0240 Quest OAuth research.
- [x] **Scope locked:** implementation covers Google Drive resource add + Google Drive re-auth surfaces only; Settings Google Account card, backup/restore, and other Google domains stay on the existing identity path.
- [x] **Browser policy fixed:** Google sign-in uses AppAuth + external browser/CCT only; WebView is forbidden.

---

## Completion Gate

- [x] All phases show ✅ Done.
- [x] Quest / XR without GMS opens Google browser auth from Add Resource instead of failing at `PlayServicesUnavailable`.
- [x] Successful consent returns to the app and opens the Google Drive folder picker.
- [x] Existing GMS-capable Android devices still use Credential Manager for Google Drive sign-in.
- [x] Google Drive account registration populates the common cloud account picker model.
- [x] No-browser fallback shows a concrete next step instead of `UnknownError`.
- [x] `dev/CHANGELOG.md` has an entry for every modified file.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated after Kotlin changes.
- [x] `/spec-check S0294` returns `Verified`.
- [x] Strategic spec `Status:` advanced to `Verified` by `/spec-check`.

---

## How to Track Progress

1. Before starting a phase: flip the row to `🚧 In Progress`. Update `Phases: X/N done`.
2. During a phase: flip each step to `[~] in progress` when work starts and `[x] done` only after its verification passes.
3. On phase completion: confirm every step is `[x] done`, confirm Phase Done Criteria, flip the row to `✅ Done`, and bump the counter.
4. If blocked: flip the phase row to `⛔ Blocked`, append a note to the Blockers Log, and set the strategic status to the matching `Block*` state when needed.
5. After Phase 04: run `/spec-check S0294` and keep fixing until the strategic spec reaches `Verified`.

---

## Blockers Log

- 2026-05-24 - Initial tactical plan authored. No blockers at plan creation time.

---

## Change Log

- 2026-05-24 - Initial tactical plan authored by `/spec-tech` equivalent flow.
- 2026-05-24 - All four phases completed; static audit reached `Verified`.