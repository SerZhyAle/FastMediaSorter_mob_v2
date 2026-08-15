# Phase 07 — docs-catalog-cleanup

**Strategic spec:** [`../S0155_link-auth-multi-account.md`](../S0155_link-auth-multi-account.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all (Phase 06)
**Blocks:** none — final phase
**Steps done:** 5 / 5
**Started:** —
**Completed:** —

---

## Objective

Update `docs/FEATURES.md` + mirrors with the multi-account capability description; regenerate the class catalog; run the final string locale audit; remove `AuthSessionAdapter` (now dead code); update tactical INDEX status.

---

## Prerequisites

- [ ] All phases 01–06 are ✅ Done.
- [ ] Project compiles and passes `/build`.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | existing |
| `docs/FEATURES_RU.md` | Modified | existing |
| `docs/FEATURES_UK.md` | Modified | existing |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionAdapter.kt` | Deleted | — |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | — |
| `dev/CATALOG/app_v2.md` | Regenerated | — |

---

## Steps

### Step 07.1 — Update FEATURES trilingual docs

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Locate the existing link-download / authorization feature section in `docs/FEATURES.md` (search for "S0116" or "link download" or "authorization" to find the section). Add a bullet describing multi-account support. Trigger `/doc-update` skill to propagate the change to `_RU` and `_UK` mirrors in the same step.
>
> **English bullet to add** (under the link-download authorization sub-section):
> `- Multiple accounts per host: save separate sign-ins for the same site (e.g., a personal and a work Instagram account); the app asks which account to use before downloading; account sessions can be added, renamed, and removed individually in Settings → Authorizations.`
>
> The `_RU` and `_UK` mirrors get the equivalent localized bullet via `/doc-update`.

**Verification:**

- `Grep` — `Multiple accounts per host` present in `docs/FEATURES.md`.
- `Grep` — `Несколько аккаунтов` present in `docs/FEATURES_RU.md`.
- `Grep` — `Кілька акаунтів` present in `docs/FEATURES_UK.md`.

**Status:** `[ ]` not done

---

### Step 07.2 — Remove AuthSessionAdapter (dead code)

**Files:** `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionAdapter.kt`
**Depends on:** Step 07.1

**Prompt for developer:**

> `AuthSessionAdapter` is no longer instantiated by any live code path (replaced by `AuthAccountGroupAdapter` in Phase 06). Delete the file.
>
> Before deleting, `Grep` across the codebase to confirm no remaining references:
> ```powershell
> grep -rn "AuthSessionAdapter" app_v2/src/main/ | grep -v "\.backup\."
> ```
> If any non-backup reference exists → fix the reference first, then delete.

**Verification:**

- `Glob` — `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/auth/AuthSessionAdapter.kt` does NOT exist.
- `Grep` — `AuthSessionAdapter` in `app_v2/src/main/` returns zero hits.

**Status:** `[ ]` not done

---

### Step 07.3 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 07.2

**Prompt for developer:**

```powershell
"/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
"/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

Then verify new classes have correct `role` and `status` set via `set.ps1` where needed:
- `LinkDownloadSessionContext` → role: `data`, status: `active`
- `AccountNameHintExtractor` → role: `util`, status: `active`
- `AccountSelectionManager` → role: `ui-helper`, status: `active`
- `AuthAccountGroupAdapter` → role: `ui-adapter`, status: `active`

```powershell
"/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class "LinkDownloadSessionContext" -Role "data" -Status "active"
"/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class "AccountNameHintExtractor" -Role "util" -Status "active"
"/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class "AccountSelectionManager" -Role "ui-helper" -Status "active"
"/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class "AuthAccountGroupAdapter" -Role "ui-adapter" -Status "active"
"/c/Program Files/PowerShell/7/pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
```

**Verification:**

- `Grep` — `LinkDownloadSessionContext` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `AccountNameHintExtractor` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `AccountSelectionManager` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `AuthAccountGroupAdapter` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `AuthSessionAdapter` absent from `dev/CATALOG/app_v2.md` (deleted in Step 07.2).

**Status:** `[ ]` not done

---

### Step 07.4 — Final string locale audit

**Files:** all `values*/strings_s0155.xml`
**Depends on:** Step 07.3

**Prompt for developer:**

> Run the final locale parity check for all S0155 keys:
> ```powershell
> pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "s0155_"
> ```
> Exit code must be 0.

**Verification:**

- Script exits with code 0.

**Status:** `[ ]` not done

---

### Step 07.5 — Dev log for Phase 07 files and advance spec status

**Files:** `dev/CHANGELOG.md` (via script), `PLAN/S0155_link-auth-multi-account.md`
**Depends on:** Step 07.4

```powershell
.\scripts\add_to_dev_log.ps1 "docs/FEATURES.md" "S0155 Phase 07" "Add multi-account authorization feature description"
.\scripts\add_to_dev_log.ps1 "docs/FEATURES_RU.md" "S0155 Phase 07" "RU: multi-account authorization feature"
.\scripts\add_to_dev_log.ps1 "docs/FEATURES_UK.md" "S0155 Phase 07" "UK: multi-account authorization feature"
.\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.jsonl" "S0155 Phase 07" "Catalog regen: new classes from S0155"
.\scripts\add_to_dev_log.ps1 "PLAN/S0155_link-auth-multi-account/INDEX.md" "S0155 Phase 07" "Tactical plan complete — all 7 phases done"
```

Update `INDEX.md`:
- Set `**Status:** Done`.
- Set `**Phases:** 7 / 7 done`.
- Flip all phase rows to `✅ Done`.
- Add entry to Change Log: `- <YYYY-MM-DD> — All phases complete. /spec-check S0155 to verify.`

**Verification:**

- `Grep` — `S0155 Phase 07` matches at least 5 lines in `dev/CHANGELOG.md`.
- `Grep` — `Status:** Done` present in `PLAN/S0155_link-auth-multi-account/INDEX.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] Project compiles — run `/build`.
- [ ] `Grep` for `TODO(phase-07)` returns zero hits.
- [ ] `/spec-check S0155` run to finalize.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s). FEATURES docs and catalog changes are purely additive/regenerated — no data impact.
