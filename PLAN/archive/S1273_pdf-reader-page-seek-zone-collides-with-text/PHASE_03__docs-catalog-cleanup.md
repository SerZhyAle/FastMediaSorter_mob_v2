# Phase 03 - Docs and catalog cleanup

**Strategic spec:** [`../S1273_pdf-reader-page-seek-zone-collides-with-text.md`](../S1273_pdf-reader-page-seek-zone-collides-with-text.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Register the new class in the catalog, record the delivered capability in the feature inventory, and mark the sibling ticket S1274 as delivered by the same code.

---

## Prerequisites

- [ ] Phase 01 and Phase 02 are ✅ Done.
- [ ] The Phase 02 build passed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `PLAN/S1274_pdf-reader-swipe-page-turn.md` | Modified | n/a |

No `strings.xml` change - strategic section 3.3 records that this ticket adds no user-visible string, so the string audit does not apply. No settings key is added, so the Rule 22 settings-manifest regeneration does not apply either.

---

## Steps

### Step 03.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Phase 01 added a public class, so refresh the catalog once for the ticket with `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then give `PdfPageSwipeDetector` its role and status with `dev/CATALOG/scripts/set.ps1` so it is not left as an unclassified entry.

**Verification:**

- `Grep` - `PdfPageSwipeDetector` present in `dev/CATALOG/app_v2.jsonl`.
- Command exit code 0 recorded for `catalog_sync.ps1`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. `catalog_sync.ps1 -Module app_v2` exit 0, 2394 records. `set.ps1` gave the class `role=ui`, `status=new`, `noFlavors=lite,photos`.

---

### Step 03.2 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 03.1

**Prompt for developer:**

> Add one record via `scripts/all_features/add.ps1` for the delivered capability: turning PDF pages by swiping, with two fingers while zoomed and one finger while not. Read the flavor list off the actual gate rather than a sibling record - strategic section 3.3 names `SUPPORT_DOCUMENTS = true`, so confirm in `app_v2/build.gradle.kts` which flavors set it and pass exactly those. English only.

**Verification:**

- `Grep` - `S1273` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. Recorded through `close-and-log.ps1 -FuncOp CHANGE`, area `Documents`. Flavors read off `app_v2/build.gradle.kts`: `SUPPORT_DOCUMENTS = true` for standard, noLegal, legacy, vr; false for lite and photos. `validate.ps1` PASS, 619 records.

---

### Step 03.3 - Point S1274 at the code that closed it

**Files:** `PLAN/S1274_pdf-reader-swipe-page-turn.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> S1274 and S1273 are one piece of work by their own statement, and this plan implemented both halves of the shared decision. Add an inline `// S1274:` marker beside the two-finger claim and the one-finger claim in `PdfPageSwipeDetector`, so a later `drift-check.ps1` on S1274 finds the implementation instead of re-planning it, and add a short section to the S1274 spec file naming the files that carry its behaviour. Do not change S1274's catalog status here - its own audit decides that.

**Verification:**

- `Grep` - `// S1274:` matches at least once in `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/helpers/PdfPageSwipeDetector.kt`.
- `Grep` - `PdfPageSwipeDetector` present in `PLAN/S1274_pdf-reader-swipe-page-turn.md`.

**Status:** `[x]` done

**Step Log:**

- 2026-07-31 - Verification 2/2 PASS. Two `// S1274:` markers sit on the two claims in `PdfPageSwipeDetector.kt`; S1274 gained section 3.4 naming the three files. Its catalog status left untouched for its own audit.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Dev log entry added for the ticket via `.\scripts\add_to_dev_log.ps1`.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated.
- [ ] Ticket set to `BlockNeedUserTest` with a status note naming what must be checked on device.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The device test must cover two-finger paging on a zoomed page, one-finger panning still working on a zoomed page, and a slow unzoomed drag that clears no velocity threshold. The zoom reset after a page turn belongs to S1327 and is not a finding against this ticket.

---

## Rollback Plan

Revert the phase commit. Catalog and inventory records regenerate from source, so nothing is lost.
