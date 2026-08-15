# Phase 06 — Docs, catalog, locale-audit cleanup

**Strategic spec:** [`../S0144_fix-link-download-auth-ux.md`](../S0144_fix-link-download-auth-ux.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-05-10
**Completed:** 2026-05-10

---

## Objective

Land the trilingual FEATURES note, regenerate the class catalog for `app_v2`, run the string-locale audit for all new keys, and record dev-log entries — leaving the spec ready for `/spec-check`.

---

## Prerequisites

- [ ] Phases 01–05 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/FEATURES.md` | Modified | ≤ +6 |
| `docs/FEATURES_RU.md` | Modified | ≤ +6 |
| `docs/FEATURES_UK.md` | Modified | ≤ +6 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | — |
| `dev/CATALOG/app_v2.md` | Regenerated | — |

---

## Steps

### Step 06.1 — Update FEATURES (trilingual)

**Files:** `docs/FEATURES.md`, `docs/FEATURES_RU.md`, `docs/FEATURES_UK.md`
**Depends on:** — start of phase

**Prompt for developer:**

> In the existing link-download / "download from URL" feature area of all three FEATURES files, add concise bullets (do not duplicate existing wording): (1) sharing a link from a known social resource offers to save an authorization for it; (2) the "Add authorization" screen now has a top toolbar with a `+` action; (3) adding an authorization lets you pick a resource from a built-in list of popular networks or enter the address manually; (4) signing in to Instagram and similar resources through the in-app browser no longer fails with an "unknown URL scheme" error. Use `..` not `...`; keep `ё`/`Ё` in the RU file. Run `/doc-update` if the area's structure needs re-mirroring.

**Verification:**

- `Grep` — a new bullet mentioning "authorization" (EN) added in `docs/FEATURES.md` under the link-download area.
- `Grep` — corresponding RU bullet present in `docs/FEATURES_RU.md`.
- `Grep` — corresponding UK bullet present in `docs/FEATURES_UK.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Added "Social-link authorizations (S0144)" bullet after the S0116 bullet in `docs/FEATURES.md` + `_RU.md` + `_UK.md`. Dev log recorded.

---

### Step 06.2 — Run the string-locale audit

**Files:** — (verification only)
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -File scripts/check_strings_localized.ps1 -KeyPrefix "auth_add_"`, then `-KeyPrefix "auth_offer_dialog_"`. Each must exit 0. Fix any EN/RU/UK gap before continuing. (No `auth_sessions_screen_title` key was created — Phase 02 reuses the existing trilingual `setting_saved_authorizations_title`.)

**Verification:**

- `scripts/check_strings_localized.ps1 -KeyPrefix "auth_add_"` exits 0.
- `scripts/check_strings_localized.ps1 -KeyPrefix "auth_offer_dialog_"` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — `auth_add_` (2 keys) and `auth_offer_dialog_` (4 keys) all OK in EN/RU/UK; both audits exit 0.

---

### Step 06.3 — Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` then `pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2`. For the new classes (`KnownAuthResources`, `AuthOfferDismissalStore`) set `role` + `status` via `pwsh -File dev/CATALOG/scripts/set.ps1` per `dev/CATALOG/README.md`. Commit the regenerated `.jsonl` + `.md` together with the code.

**Verification:**

- `Grep` — `KnownAuthResources` present in `dev/CATALOG/app_v2.jsonl`.
- `Grep` — `AuthOfferDismissalStore` present in `dev/CATALOG/app_v2.jsonl`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — `scan.ps1` + `render.ps1` for app_v2 (997 records); `set.ps1` filled role+status for `KnownAuthResources` and `AuthOfferDismissalStore` (status=new). Dev log recorded for `.jsonl` + `.md`.

---

### Step 06.4 — Dev-log entries + Timber-tag sweep note

**Files:** — (process step)
**Depends on:** Step 06.1, Step 06.3

**Prompt for developer:**

> Ensure `.\scripts\add_to_dev_log.ps1` has an entry for every file modified across Phases 01–06 (run it for any that were missed). Confirm that the only `Timber.d("S0144:` tags present are the flow-entry tags introduced by Phases 02/03/04/05 and that they are still needed; they are removed later when `/spec-check` advances the spec to `Verified` (CLAUDE.md "Debug Verification Tags"). Do not remove them now.

**Verification:**

- `Grep` — recent `S0144` lines present in `dev/CHANGELOG.md` for each modified file.
- `Grep -rn 'Timber.d("S0144:' app_v2/src` — returns only the documented flow-entry tags (one per changed flow).

**Status:** `[x] done`

**Step Log:**

- 2026-05-10 — Dev log entries verified for all modified files. Three S0144 flow-entry tags remain (AuthSessionsListFragment "auth-add picker shown", WebViewAuthDialogFragment "webview-auth redirect intercepted", ReceiveShareActivity "share-auth offer evaluated"); the redundant "auth-sessions add action" tag was dropped — kept one tag per changed flow. Tags to be removed by `/spec-check` on transition to `Verified`.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project still compiles — `build-debug.PS1` → BUILD SUCCESSFUL (2026-05-10).
- [x] `dev/CHANGELOG.md` has an entry for every file modified in this spec.
- [x] `/spec-check S0144` ready to run.

---

## Handoff Notes to Next Phase

Final phase — see [`INDEX.md`](INDEX.md) Completion Gate. Next: `/spec-check S0144`.

---

## Rollback Plan

Docs/catalog only — revert the phase commit; no code or data impact.
