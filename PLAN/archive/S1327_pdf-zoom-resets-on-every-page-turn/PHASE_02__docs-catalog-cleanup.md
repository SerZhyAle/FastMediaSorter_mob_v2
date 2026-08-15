# Phase 02 - Docs and catalog cleanup

**Strategic spec:** [`../S1327_pdf-zoom-resets-on-every-page-turn.md`](../S1327_pdf-zoom-resets-on-every-page-turn.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-03
**Completed:** 2026-08-03

---

## Objective

Record the delivered capability in the feature inventory, correct the reader documentation that now describes the wrong behaviour, and close the ticket mechanically.

---

## Prerequisites

- [x] Phase 01 is ✅ Done.
- [x] The Phase 01 compile check passed.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | n/a |
| `docs/HOW_TO.md` | Modified | n/a |
| `docs/HOW_TO_RU.md` | Modified | n/a |
| `docs/HOW_TO_UK.md` | Modified | n/a |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

No `strings.xml` change, so the string audit does not apply. No settings key, so Rule 22 does not apply. `docs/FEATURES*.md` is not touched here - the showcase is written by `/skill-release` from the inventory diff.

---

## Steps

### Step 02.1 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record through `scripts/all_features/add.ps1` for the delivered capability: the PDF reader keeps the zoom the reader was using when the page turns, instead of dropping every page back to fit. Area `Documents`, spec `S1327`, English only. Read the flavor list off the gate in `app_v2/build.gradle.kts` rather than off a neighbouring record - `SUPPORT_DOCUMENTS` is true for standard, noLegal, legacy and vr, and false for lite and photos, so pass exactly those four.

**Verification:**

- `Grep` - `S1327` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 2/2 PASS. Record `documents.pdf-zoom-survives-the-page-turn` added with `"spec":"S1327"`; `validate.ps1` exit 0, `634 record(s)`. Flavors read off `app_v2/build.gradle.kts`: `SUPPORT_DOCUMENTS` is true for standard, noLegal, legacy, vr and false for lite, photos.

---

### Step 02.2 - Correct the reader documentation in all three languages

**Files:** `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> The PDF reader section of `HOW_TO` lists the zoom gestures and, at the time of planning, ends that list with the double-tap zoom reset - `HOW_TO.md:958`, `HOW_TO_RU.md:933`, `HOW_TO_UK.md:903`. Add one bullet in the same list saying the zoom now stays as you set it when the page turns, and that the double tap is how you go back to fit. Each locale gets its own edit in its own file; do not machine-translate one into three without reading the surrounding wording, since the three files phrase the gesture list differently. Check the sentence against `docs/COMMUNICATION_POLICY.md` section 6 before writing it.

**Verification:**

- `Grep` - the new bullet is present in each of `docs/HOW_TO.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`.
- The three bullets sit in the same list as the existing zoom-reset bullet in each file, not in a separate section.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 3/3 PASS. One bullet per locale, each written against that file's own wording rather than translated from the English: `HOW_TO.md:959`, `HOW_TO_RU.md:934`, `HOW_TO_UK.md:904`. Each sits immediately after the double-tap zoom-reset bullet in the same `**Gestures:**` list, and each names the double tap as the way back to the whole page. COMMUNICATION_POLICY §6: plain user language, no raw error text, no dead-end phrasing.

---

### Step 02.3 - Close the ticket mechanically

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Route the closure through `scripts/post-change.ps1` with `-ChangeType Mixed` and `-ScopeToFile`, which chains the dev log, the catalog sync and the gates in one call on a tree that is dirty with other tickets' work. One dev-log entry for the ticket, not one per touched file. Then move the ticket to `BlockNeedUserTest` through `update.ps1`, with a `-StatusNote` naming what the owner has to check on a device: on a long real PDF, zoom into a page, turn to the next one by any route, and confirm the new page arrives at the same zoom rather than at fit. The Step 01.3 probe stays in the tree until the ticket leaves that status.

**Verification:**

- `post-change.ps1` exit code 0 recorded.
- `Grep` - `S1327` present in `dev/CHANGELOG.md`.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1327 -Format json` reports `BlockNeedUserTest` with a non-empty `statusNote`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-03 - Verification 3/3 PASS. `post-change.ps1 -ChangeType Mixed -ScopeToFile` exit 0; `S1327` present in `dev/CHANGELOG.md`; `select.ps1` reports `BlockNeedUserTest` with a status note naming the four page-turn routes plus the second-document check.
- 2026-08-03 - Two deviations worth naming. **(1)** The `BlockNeedUserTest` flip happened at the end of Phase 01, not here: `assert-no-ticket-logs` refuses a `Timber.d("S1327:` probe while the ticket is still `In Progress`, so the status has to lead the gate, not follow it. **(2)** The first Phase 02 closure returned `PASS WITH ADVISORIES (1)` on the document-registry trigger. The two matched records were read and their siblings checked - `README*.md` describes the PDF viewer only as having "zoom, pan, and gesture navigation", which stays true and needs no edit, and no `QUICK_START*`, `FAQ*`, `TROUBLESHOOTING*`, `LIMITATIONS*` or `docs/howto/*` file documents the per-page zoom reset. Re-run with `-RegistryAck "user-guides,feature-inventory"` returned a clean `post-change: PASS`, at the cost of one duplicate changelog row.

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Dev log entry added for the ticket via `post-change.ps1`.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - `2408 records`, rendered by the closure run.
- [x] Ticket set to `BlockNeedUserTest` with a status note naming what must be checked on device.
- [x] Exactly one `Timber.d("S1327:` line in the tree, `PdfViewerManager.kt:840`, matching the `BlockNeedUserTest` status.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

The device test needs a document long enough that the owner is genuinely reading zoomed, and should cover more than the arrow buttons: a swipe turn, a thumbnail-sheet jump and a go-to-page jump each take a different route into `showPdfPage`, and owner decision B determines whether all three are expected to carry the zoom or only the first.

---

## Rollback Plan

Revert the phase commit. The catalog and the inventory regenerate from source, and the `HOW_TO` edit is three additive bullets.
