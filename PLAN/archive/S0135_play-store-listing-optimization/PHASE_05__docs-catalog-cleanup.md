# Phase 05 — Docs, Catalog & Play Console Checklist

**Strategic spec:** [`../S0135_play-store-listing-optimization.md`](../S0135_play-store-listing-optimization.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phases 01, 02, 03, 04 all ✅ Done
**Blocks:** none — final phase
**Steps done:** 4 / 4
**Started:** 2026-05-14
**Completed:** 2026-05-14

---

## Objective

Regenerate class catalog, finalize dev changelog entries, create the Play Console operator checklist, and close open questions in the strategic spec. No code changes in this phase.

---

## Prerequisites

- [ ] Phases 01, 02, 03, 04 all ✅ Done.
- [ ] Design assets (icon, feature graphic, 18 screenshots) are ready in `store_assets/` per `design_brief.md` — or Phase 05 proceeds for code/catalog portions only, and the Play Console checklist actions wait.
- [ ] Owner has decided on Play Console category (§6.2 of strategic spec).
- [ ] Owner has decided on developer name (§6.3) — or decided to defer.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `store_assets/PLAY_CONSOLE_CHECKLIST.md` | New | ≤ 80 |
| `dev/CATALOG/app_v2.jsonl` | Modified (auto) | n/a |
| `dev/CATALOG/app_v2.md` | Modified (auto) | n/a |
| `PLAN/S0135_play-store-listing-optimization.md` | Modified (open questions) | ≤ +20 |

---

## Steps

### Step 05.1 — Regenerate class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Phase 04 complete

**Prompt for developer:**

> Run:
> ```powershell
> pwsh -File dev/CATALOG/scripts/scan.ps1 -Module app_v2
> pwsh -File dev/CATALOG/scripts/render.ps1 -Module app_v2
> ```
> Then set `role` and `status` for the two new classes via `set.ps1`:
> ```powershell
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class ReviewEligibilityDataStore -Role "data-store" -Status "active"
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class RecordSortSuccessUseCase -Role "use-case" -Status "active"
> pwsh -File dev/CATALOG/scripts/set.ps1 -Module app_v2 -Class ReviewRequestManager -Role "ui-manager" -Status "active"
> ```
> Run `scan.ps1` + `render.ps1` again after setting roles.

**Verification:**

- `Grep` — `ReviewEligibilityDataStore` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `RecordSortSuccessUseCase` present in `dev/CATALOG/app_v2.md`.
- `Grep` — `ReviewRequestManager` present in `dev/CATALOG/app_v2.md`.

**Status:** `[x] done`

---

### Step 05.2 — Create Play Console operator checklist

**Files:** `store_assets/PLAY_CONSOLE_CHECKLIST.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Create `store_assets/PLAY_CONSOLE_CHECKLIST.md` with the content below.
> The operator completes this checklist when publishing the listing update to production.

```markdown
# Play Console Operator Checklist — S0135

Complete this checklist in Play Console → App content / Store listing before publishing to production.
Check each item only after the change is confirmed in the Console UI.

## A. Listing Texts (all three locales: EN / RU / UK)

- [ ] Title updated — source: `store_assets/play_store_description_{locale}.txt` line 2
- [ ] Short description updated — source: same file, line 5
- [ ] Full description updated — source: same file, lines 8+
- [ ] Verify EN title reads "Fast Media Sorter & Organizer" (no `Sorter&` typo)

## B. What's New

- [ ] EN What's New updated — source: `store_assets/whats_new.txt`
- [ ] RU What's New updated — source: `store_assets/whats_new_ru.txt`
- [ ] UK What's New updated — source: `store_assets/whats_new_uk.txt`
- [ ] No placeholder text `fix and improve` remains

## C. Graphics

- [ ] App icon uploaded: `store_assets/icon_512.png` (512 × 512 px)
- [ ] Feature graphic uploaded: `store_assets/feature_graphic_1024x500.png` (1024 × 500 px)
- [ ] 6 screenshots uploaded for EN locale (slots 1–6 per `design_brief.md` sequence)
- [ ] 6 screenshots uploaded for RU locale
- [ ] 6 screenshots uploaded for UK locale
- [ ] Screenshot slot 1 shows sorting-in-action (NOT the Settings screen)

## D. App Category

- [ ] Category reviewed — current: Photography. Decision: _____________
  - Suggested: switch to **Productivity** (broader "file organizer" query coverage, lower competition)
  - Research: check competitor categories for Slidebox, Photo Cleaner, Magic Cleaner before deciding
  - Record final decision in strategic spec §6.2

## E. Developer Account (optional)

- [ ] Developer name decision recorded in strategic spec §6.3 (keep personal / register studio)
- [ ] If changing: confirm Google Play name-change requirements do not reset install/review count

## F. Post-Publish Monitoring (first 14 days)

- [ ] Monitor Play Console Acquisition → Store Listing Conversion Rate
- [ ] Monitor organic installs vs. pre-publish baseline
- [ ] If conversion drops >20% from baseline: prepare rollback copy set

## Checklist completed by: ___________________  Date: ___________
```

**Verification:**

- `Glob` — `store_assets/PLAY_CONSOLE_CHECKLIST.md` exists.
- `Grep` — `Fast Media Sorter & Organizer` present in the file.
- `Grep` — `Acquisition` present (monitoring section).

**Status:** `[x] done`

---

### Step 05.3 — Resolve open questions in strategic spec

**Files:** `PLAN/S0135_play-store-listing-optimization.md`
**Depends on:** owner decisions on §6.2 and §6.3

**Prompt for developer:**

> In the strategic spec `PLAN/S0135_play-store-listing-optimization.md`, update the **§6 Open Questions** section:
>
> - §6.1 (Threshold N): change `Status: Open` to `Status: Resolved — 20 ops / 3 sessions / 90-day cooldown (RecordSortSuccessUseCase.Companion)`.
> - §6.5 (store assets location): change `Status: Open` to `Status: Resolved — store_assets/ (pre-existing)`.
> - §6.2 (category): fill in the owner's chosen category. If still undecided, mark `Status: Deferred — see PLAY_CONSOLE_CHECKLIST.md §D`.
> - §6.3 (developer name): fill in the owner's decision. If still undecided, mark `Status: Deferred — owner decision`.
> - §6.4 (design source): fill in the chosen execution path. If still undecided, mark `Status: Deferred — brief in store_assets/design_brief.md`.

**Verification:**

- `Grep` — `§6.1` section in strategic spec no longer contains `Status: Open`.
- `Grep` — `§6.5` section in strategic spec no longer contains `Status: Open`.

**Status:** `[x] done`

---

### Step 05.4 — Final dev changelog sweep

**Files:** `dev/CHANGELOG.md` (via script only)
**Depends on:** Steps 05.1–05.3

**Prompt for developer:**

> Run `add_to_dev_log.ps1` for every file modified in all phases that does not yet have a log entry.
> Minimum required entries for this phase:
>
> ```powershell
> .\scripts\add_to_dev_log.ps1 "store_assets/PLAY_CONSOLE_CHECKLIST.md" "spec-dev" "Add Play Console operator checklist for S0135"
> .\scripts\add_to_dev_log.ps1 "PLAN/S0135_play-store-listing-optimization.md" "spec-dev" "Resolve open questions §6.1, §6.5 — S0135 Tactical"
> .\scripts\add_to_dev_log.ps1 "dev/CATALOG/app_v2.md" "spec-dev" "Catalog: add ReviewEligibilityDataStore, RecordSortSuccessUseCase, ReviewRequestManager"
> ```

**Verification:**

- `Grep` — `S0135` present in `dev/CHANGELOG.md` (at least one entry).
- `Grep` — `ReviewRequestManager` present in `dev/CHANGELOG.md`.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` unchanged — per strategic §8, no new user-facing feature entry.
- [ ] All `store_assets/play_store_description_*.txt` files contain the new ASO copy.
- [ ] `store_assets/PLAY_CONSOLE_CHECKLIST.md` exists and is complete.
- [ ] Play Console checklist §A–§C items completed by operator (or noted as pending design delivery).
- [ ] `/spec-check S0135` run — status advances to `Verified`.

---

## Handoff Notes to Next Phase

Final phase — see `INDEX.md` Completion Gate. After this phase, run `/spec-check S0135` to close the ticket.

---

## Rollback Plan

No code changes in this phase — catalog and doc-only. Revert commits if needed. Play Console changes are reverted via the Console "Discard changes" UI before submission, or by re-uploading prior assets after publication.
