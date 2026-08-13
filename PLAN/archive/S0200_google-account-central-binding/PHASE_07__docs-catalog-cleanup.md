# Phase 07 — Docs, Catalog, Changelog, FEATURES Cleanup

**Strategic spec:** [`../S0200_google-account-central-binding.md`](../S0200_google-account-central-binding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all previous phases
**Blocks:** none — final phase
**Steps done:** 4 / 4
**Started:** 2026-05-16
**Completed:** 2026-05-16

---

## Objective

Run every post-change ritual mandated by `CLAUDE.md` so the spec can pass `/spec-check`. No functional code changes here — purely documentation, indices, and change-log entries.

---

## Prerequisites

- [ ] Phases 01..06 all ✅ Done.
- [ ] All builds pass on `standardDebug`, `noLegalDebug`, `liteDebug`.
- [ ] All unit tests pass.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | as-is |
| `docs/FEATURES_RU.md` | Modified | as-is |
| `docs/FEATURES_UK.md` | Modified | as-is |
| `dev/CATALOG/app_v2.jsonl` | Modified (via scan/render) | auto |
| `dev/CATALOG/app_v2.md` | Modified (via scan/render) | auto |
| `dev/CHANGELOG.md` | Modified (via add_to_dev_log) | auto |
| `dev/FUNCTIONALITY.log` | Modified (via add_to_functionality_log) | auto |

---

## Steps

### Step 07.1 — FEATURES trilingual update

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** —

**Prompt for developer:**

> Per strategic §8, add ONE bullet under the "Cloud Integration" section in each of the three files.
>
> EN:
> ```markdown
> - **Google account binding**: a single sign-in to your Google account that powers Drive and any future Google integrations. Adding new Drive folders does not require another sign-in.
> ```
>
> RU (apply `..` instead of `...`; use `ё`/`Ё`):
> ```markdown
> - **Привязка Google-аккаунта**: один вход в Google для Drive и будущих Google-интеграций. Добавление новых папок Drive больше не требует повторной авторизации.
> ```
>
> UK:
> ```markdown
> - **Прив'язка Google-акаунта**: один вхід у Google для Drive і майбутніх Google-інтеграцій. Додавання нових папок Drive не потребує повторної авторизації.
> ```
>
> Locate the existing "Cloud Integration" subsection in each file — the bullet's position must match across all three files.

**Verification:**

- `Grep -n "Google account binding" docs/FEATURES.md` matches exactly once.
- `Grep -n "Привязка Google-аккаунта" docs/FEATURES_RU.md` matches exactly once.
- `Grep -n "Прив'язка Google-акаунта" docs/FEATURES_UK.md` matches exactly once.

**Status:** `[ ]` not done

---

### Step 07.2 — Catalog sync

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** —

**Prompt for developer:**

> Run:
>
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Then for each new class added in Phases 01–06, fill role/status via `set.ps1`. Required entries:
>
> | Class | Role |
> |---|---|
> | `GoogleIdentityRepository` | domain.repository |
> | `PrimaryGoogleAccount` | domain.model |
> | `PrimaryGoogleAccountState` | domain.model |
> | `GoogleScope` | domain.model |
> | `GoogleAccessToken` | domain.model |
> | `IdentitySignInResult` | domain.model |
> | `CredentialManagerGoogleIdentityRepository` | data.repository |
> | `GoogleTokenIssuer` | data.helper |
> | `PrimaryGoogleAccountStore` | data.helper |
> | `IdentityModule` | di.module |
> | `NoOpGoogleIdentityRepository` | data.repository |
> | `NoOpIdentityModule` | di.module |
> | `GoogleDomainMatcher` | data.helper |
> | `CctAvailabilityChecker` | data.helper |
> | `GoogleDomainBrowserLauncher` | data.helper |
> | `CctUnavailableException` | data.exception |
> | `CctRefusalDialog` | ui.helper |
> | `S0200AuthStateWipe` | data.migration |
> | `Migration_S0200` | data.migration |
> | `GoogleAccountSettingsViewModel` | ui.viewmodel |
> | `GoogleAccountSettingsHelper` | ui.helper |
>
> Flavor-only classes from `cloudEnabled` and `cloudDisabled`: declare via `set.ps1 -NoFlavors "..."` only if a single class is mounted in some flavors but not others. The shared `cloudEnabled` set goes to all SUPPORT_CLOUD=true flavors — no `-NoFlavors` needed unless a class is in `cloudDisabled` only or vice versa. The two no-op classes from Step 01.7 are mounted ONLY into `lite`, so they declare `-NoFlavors "standard,noLegal,photos,legacy,vr,vrUnlicensed"`. The Credential Manager impl + IdentityModule are mounted into ALL EXCEPT `lite`, so they declare `-NoFlavors "lite"`.

**Verification:**

- `Grep -n "GoogleIdentityRepository" dev/CATALOG/app_v2.jsonl` matches at least once.
- `Grep -n "CredentialManagerGoogleIdentityRepository" dev/CATALOG/app_v2.jsonl` matches at least once.
- `Grep -n "S0200AuthStateWipe" dev/CATALOG/app_v2.jsonl` matches at least once.

**Status:** `[ ]` not done

---

### Step 07.3 — Dev changelog batch

**Files:** `dev/CHANGELOG.md`
**Depends on:** Steps 01..06 complete

**Prompt for developer:**

> Run `add_to_dev_log.ps1` once per file actually modified across Phases 01..06. For efficiency batch them into a script — but each call to `add_to_dev_log.ps1` is one entry. Group conceptually:
>
> - Build / deps: `app_v2/build.gradle.kts` (one entry).
> - Domain types: each new file under `domain/identity/` (six entries).
> - `cloudEnabled` impl files (four entries).
> - `cloudDisabled` no-op files (two entries).
> - Browser layer (four new + three modified entries).
> - Drive layer (ten modified entries — coordinator, plugin, rest client, thumbnail, add-resource, browse, backup-restore VM + fragment, general fragment, credentials manager).
> - Wipe layer (two new + one modified entry).
> - Settings UI (six new + two modified entries).
> - Tests (five new + two modified entries).
> - Manifest + strings (one + three + three entries).
> - Phase docs (eight tactical files — INDEX + 7 phases).
>
> Total ≈ 60 entries. Worth scripting; do NOT edit `dev/CHANGELOG.md` directly.

**Verification:**

- `Grep -c "S0200" dev/CHANGELOG.md` ≥ 50.
- Build closure: `/build` → `standardDebug` still PASS. Expected: PASS. Actual: paste.

**Status:** `[ ]` not done

---

### Step 07.4 — Functionality log entry

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 07.3

**Prompt for developer:**

> Run:
>
> ```powershell
> .\scripts\add_to_functionality_log.ps1 -Id S0200 -Op ADD -Description "Central Google account binding via Credential Manager. Single primary account powers Drive and future Google integrations. CCT used for Google-domain auth flows; WebView retained for non-Google sources. Drive resources show needs-sign-in indicator. Settings has new Google Account card."
> ```
>
> Single ADD entry covers the user-visible capability per CLAUDE.md "Functionality log" rule.

**Verification:**

- `Grep -n "S0200.*Central Google account binding" dev/FUNCTIONALITY.log` matches exactly once.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] `git status` shows no unstaged changes after `add_to_dev_log.ps1`/`scan.ps1`/`render.ps1` runs.
- [ ] `/spec-check S0200` runs and returns `Verified` (or `BlockNeedUserTest` if pipeline correctly inserts on-device verification tags).

---

## Handoff Notes to Next Phase

Final phase. After this, the spec is closed via `/spec-check`.

---

## Rollback Plan

This phase touches only documentation and indices. Revert the commit if a downstream issue surfaces — no user-facing impact.
