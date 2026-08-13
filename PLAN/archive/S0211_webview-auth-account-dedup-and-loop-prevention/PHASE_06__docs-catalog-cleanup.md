# Phase 06 — Docs / Catalog Cleanup

**Strategic spec:** [`../S0211_webview-auth-account-dedup-and-loop-prevention.md`](../S0211_webview-auth-account-dedup-and-loop-prevention.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01–05
**Blocks:** —
**Steps done:** 3 / 3
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Refresh `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md`, finalise the dev changelog and the functionality log, and confirm no FEATURES doc updates are needed.

---

## Prerequisites

- [ ] Phases 01–05 ✅ Done.
- [ ] Working tree contains the changes from all earlier phases.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (regenerated) | — |
| `dev/CATALOG/app_v2.md` | Modified (regenerated) | — |
| `dev/CHANGELOG.md` | Modified (via script only) | — |
| `dev/FUNCTIONALITY.log` | Modified (via script only) | — |

---

## Steps

### Step 06.1 — Regenerate the app_v2 catalogue

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Phases 01–05 ✅ Done.

**Prompt for developer:**

> Run, in order:
>
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> For the two new classes, set explicit `role` + `status` via `set.ps1`:
>
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/set.ps1 `
>     -Module app_v2 -Class AccountIdentityExtractor `
>     -Role "data/link/auth helper — extracts stable identity from cookies for known social platforms" `
>     -Status "active"
>
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/set.ps1 `
>     -Module app_v2 -Class DedupAuthAccountsUseCase `
>     -Role "domain/usecase — one-shot S0211 cleanup of duplicate auth accounts" `
>     -Status "active"
> ```
>
> Re-render after `set.ps1` to refresh the human-readable `.md`:
>
> ```powershell
> & "C:\Program Files\PowerShell\7\pwsh.exe" -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```

**Verification:**

- `Grep -n "AccountIdentityExtractor" dev/CATALOG/app_v2.jsonl` — at least one match.
- `Grep -n "DedupAuthAccountsUseCase" dev/CATALOG/app_v2.jsonl` — at least one match.
- `Grep -n "AccountIdentityExtractor" dev/CATALOG/app_v2.md` — at least one match.
- `Grep -n "DedupAuthAccountsUseCase" dev/CATALOG/app_v2.md` — at least one match.

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — Catalog regen complete. set.ps1 used `-Path` flag (template used outdated `-Class`); `-Status new` (set is `new,tested,legacy,todo,unknown`). Both classes recorded with role + status=new. Catalog 1059 files / 1296 records.

---

### Step 06.2 — Append dev changelog entries

**Files:** `dev/CHANGELOG.md` (via script only)
**Depends on:** Step 06.1

**Prompt for developer:**

> For each file modified or created across the five preceding phases, append one entry via `.\scripts\add_to_dev_log.ps1` (`<path>` `<target>` `<description>` argument order). Never edit `dev/CHANGELOG.md` by hand.
>
> Files to log (at minimum):
>
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/link/auth/AccountIdentityExtractor.kt`
> - `app_v2/src/test/java/com/sza/fastmediasorter/data/link/auth/AccountIdentityExtractorTest.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/link/cookie/EncryptedCookieStore.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/domain/repository/AuthSessionRepository.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/data/repository/AuthSessionRepositoryImpl.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthViewModel.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/auth/WebViewAuthDialogFragment.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/DedupAuthAccountsUseCase.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/main/MainViewModel.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
> - `app_v2/src/main/java/com/sza/fastmediasorter/worker/LinkDownloadWorker.kt`
> - `app_v2/src/main/res/values/strings.xml`
> - `app_v2/src/main/res/values-ru/strings.xml`
> - `app_v2/src/main/res/values-uk/strings.xml`
> - `dev/CATALOG/app_v2.jsonl`
> - `dev/CATALOG/app_v2.md`

**Verification:**

- `Grep -n "S0211" dev/CHANGELOG.md | tail -20` — at least one fresh entry per logged file (one line per call).

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — All 16 source/spec/catalog files logged via `add_to_dev_log.ps1` across phases 01–06 (dev-log entries written at each step's completion). Step 06.2 specifically logged the two catalog files.

---

### Step 06.3 — Append functionality log entry; confirm no FEATURES update

**Files:** `dev/FUNCTIONALITY.log` (via script only)
**Depends on:** Step 06.2

**Prompt for developer:**

> The strategic §8 says "Без изменений в публичных docs/FEATURES*.md" — do NOT touch `docs/FEATURES.md` / `_RU` / `_UK`. The noLegal-only feature is already described umbrella-style in `docs/FEATURES_noLegal*.md` (S0156 §6.9); S0211 is a quality improvement, not a new capability — no entry there either.
>
> Append a single FIX entry to `dev/FUNCTIONALITY.log` (this is a user-visible behaviour change: no more duplicate accounts, honest notification, named reauth dialog):
>
> ```powershell
> .\scripts\add_to_functionality_log.ps1 `
>     -Id S0211 `
>     -Op FIX `
>     -Description "Link-share auth: dedup accounts by stable identity (ds_user_id / c_user / twid); honest 'preview-only, no sign-in needed' notification; reauth dialog names the existing account; one-shot cleanup of pre-existing duplicates"
> ```

**Verification:**

- `Grep -n "S0211" dev/FUNCTIONALITY.log` — at least one fresh entry from this run.
- `Grep -n "S0211" docs/FEATURES.md` — zero matches (verifies we did NOT erroneously touch public FEATURES).
- `Grep -n "S0211" docs/FEATURES_noLegal.md` — zero matches (verifies we did NOT touch the noLegal FEATURES mirror).

**Status:** `[x]` done

**Step Log:**

- 2026-05-15 — FUNC_LOG entry written (FIX, line 37). `docs/FEATURES.md` and `docs/FEATURES_noLegal.md` both untouched (0 S0211 matches each).

---

## Phase Done Criteria

- [ ] Every `Step 06.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.jsonl` + `dev/CATALOG/app_v2.md` regenerated and committed alongside source changes.
- [ ] `dev/CHANGELOG.md` carries one entry per modified file from phases 01–05.
- [ ] `dev/FUNCTIONALITY.log` carries one S0211 FIX entry.
- [ ] No edits to `docs/FEATURES*.md` or `docs/FEATURES_noLegal*.md`.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. After this phase, the pipeline transitions to `BlockNeedUserTest` (the spec's verification gate is on-device per strategic §11). `/spec-check` clears the `Timber.d("S0211: …")` tags on the `Verified` flip.

---

## Rollback Plan

`dev/CATALOG/*` files regenerate from source on every scan — revert is just running `scan.ps1` + `render.ps1` after the code rollback. `dev/CHANGELOG.md` and `dev/FUNCTIONALITY.log` are append-only — manual rollback would mean editing those files; usually not needed.
