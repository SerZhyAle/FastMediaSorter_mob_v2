# Phase 04 — Docs and Catalog Cleanup

**Strategic spec:** [`../S0204_bugfix-toolbar-hidden-behind-statusbar.md`](../S0204_bugfix-toolbar-hidden-behind-statusbar.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** *(none — final phase)*
**Steps done:** 3 / 3
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Regenerate the class catalog, add functionality log entry, and advance the spec to `BlockNeedUserTest` for on-device verification.

---

## Prerequisites

- [ ] Phase 01, 02, 03 are all ✅ Done.
- [ ] Project compiles cleanly.
- [ ] Working tree is clean (all changes committed).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Modified (auto-generated) | — |
| `dev/CATALOG/app_v2.md` | Modified (auto-generated) | — |

---

## Steps

### Step 04.1 — Regenerate app_v2 catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** — start of phase

**Prompt for developer:**

> Run catalog sync for the `app_v2` module:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
>
> Confirm no errors. The `getStatusBarHeightSafe` function in `ViewExtensions.kt` is a top-level extension — `scan.ps1` may or may not pick it up depending on catalog conventions; no manual `set.ps1` call is required for a private utility function.

**Verification:**

- `Bash` — `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` exits 0. expected: exit 0 | actual: (record result)
- `Glob` — `dev/CATALOG/app_v2.md` modification timestamp is today.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 2/2 PASS. `pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2` scanned 1051 files and `render.ps1` regenerated `dev/CATALOG/app_v2.md`; both exited successfully. Dev log recorded for `dev/CATALOG/app_v2.jsonl` and `dev/CATALOG/app_v2.md`.

---

### Step 04.2 — Add functionality log entry

**Files:** `dev/FUNCTIONALITY.log`
**Depends on:** Step 04.1

**Prompt for developer:**

> ```powershell
> .\scripts\add_to_functionality_log.ps1 -Id S0204 -Op FIX -Description "Toolbar no longer hidden behind system status bar on Android 8.x OEM car head units; added three-tier status bar height fallback"
> ```

**Verification:**

- `Grep` — `S0204` matches in `dev/FUNCTIONALITY.log`.

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS. Added `[S0204] [FIX] Toolbar no longer hidden behind system status bar on Android 8.x OEM car head units; added three-tier status bar height fallback` to `dev/FUNCTIONALITY.log`.

---

### Step 04.3 — Advance spec to BlockNeedUserTest

**Files:** `PLAN/S0204_bugfix-toolbar-hidden-behind-statusbar.md`, `PLAN/spec-catalog.jsonl` (via script)
**Depends on:** Step 04.2

**Prompt for developer:**

> ```powershell
> pwsh -File scripts/spec_catalog/update.ps1 -Id S0204 -Status BlockNeedUserTest
> ```
>
> Confirm the Timber.d tags inserted in Phase 02 Step 02.2, Phase 03 Step 03.1, 03.2, 03.3 are still present — they are the logcat probes for on-device verification. Do NOT remove them now.
>
> **On-device verification checklist (for the tester):**
> - Install debug build on the car head unit.
> - Open Settings — confirm toolbar is fully visible below the system status bar; confirm back button is tappable.
> - Open Add Resource — confirm toolbar title and navigation icon are visible and tappable.
> - Open Edit/Create Resource (SFTP or other type) — confirm toolbar is below system status bar.
> - Open Browse screen — confirm control bar is fully accessible.
> - In logcat, confirm all four `S0204:` tags fire; note the `statusBarSafe=` value to resolve strategic §6 open questions.
> - On a standard Android phone: confirm no visual regression (toolbar not double-padded, not shifted).

**Verification:**

- `Bash` — `pwsh -File scripts/spec_catalog/select.ps1 -Id S0204 -Format json | ConvertFrom-Json | Select-Object -ExpandProperty status` outputs `BlockNeedUserTest`. expected: `BlockNeedUserTest` | actual: (record result)

**Status:** `[x] done`

**Step Log:**

- 2026-05-14 — Verification 1/1 PASS. `pwsh -File scripts/spec_catalog/update.ps1 -Id S0204 -Status BlockNeedUserTest` advanced the journal from `In Progress` to `BlockNeedUserTest`, and `select.ps1` confirmed the new status.

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `dev/CATALOG/app_v2.md` and `.jsonl` are up to date.
- [ ] Spec status in journal is `BlockNeedUserTest`.
- [ ] Timber.d tags for S0204 are present in all four modified `.kt` files.

---

## Handoff Notes to Next Phase

Final phase — see INDEX.md Completion Gate. After on-device verification passes, run `/spec-check S0204` to advance to `Verified` and auto-remove all `Timber.d("S0204:` tags.

---

## Rollback Plan

Revert phase commit(s) — no data migration or user-facing surface changed.
