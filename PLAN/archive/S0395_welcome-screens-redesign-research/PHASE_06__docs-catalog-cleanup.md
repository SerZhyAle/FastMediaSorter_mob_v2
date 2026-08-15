# Phase 06 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0395_welcome-screens-redesign-research.md`](../S0395_welcome-screens-redesign-research.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 05
**Blocks:** none - final phase
**Steps done:** 2 / 2
**Started:** 2026-06-10
**Completed:** 2026-06-10

---

## Objective

Link every research artifact into the strategic spec §6 (Open → Resolved), close the dev-log trail, and advance the ticket to Implemented pending owner review of SYNTHESIS.md.

---

## Prerequisites

- [ ] Phase 05 is ✅ Done (SYNTHESIS.md and all 12 artifacts exist).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `PLAN/S0395_welcome-screens-redesign-research.md` | Modified | header + §6 lines only |

---

## Steps

### Step 06.1 - Resolve strategic §6 items with artifact links

**Files:** `PLAN/S0395_welcome-screens-redesign-research.md`
**Depends on:** - start of phase

**Prompt for developer:**

> For each strategic §6 item 1-12: flip `**Статус:** Open` to `**Статус:** Resolved` and add the `**Артефакт:**` line pointing to its file per the INDEX Artifact Map (e.g. item 1 → `PLAN/S0395_welcome-screens-redesign-research/research/01__current-flow-inventory.md`). Where an artifact's Conclusion overturned the owner draft (page order, granularity), append a one-line `**Ответ:**` summary to the item so the spec reads standalone. Do not rewrite any other section.

**Verification:**

- `Grep` - `Статус:\*\* Open` returns zero hits in `PLAN/S0395_welcome-screens-redesign-research.md`.
- `Grep` - `**Артефакт:**` appears 12 times in that file.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 2/2 PASS (0 Open / 12 Артефакт). All 12 items Resolved with one-line Ответ summaries; deviations (page swap, grouped network toggles, hidden VR/translation, deferred theme) readable in-spec.

---

### Step 06.2 - Close bookkeeping and advance status

**Files:** none beyond dev log / journal (script-driven)

**Prompt for developer:**

> Run `.\scripts\add_to_dev_log.ps1` for the strategic spec edit (target "spec", description "Resolve S0395 §6 items with research artifact links"). Confirm every artifact already has its dev-log line from phases 01-05 (grep `dev/CHANGELOG.md` for `S0395` paths); add any missing line. Catalog regen and FEATURES update are explicitly skipped (no Kotlin touched, strategic §8 = "Без изменений"). Verify zero `Timber.d("S0395` hits across `*.kt` (research ticket - no debug tags may exist). Then advance the journal: `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S0395 -Status Implemented`, update INDEX.md per "How to Track Progress" step 5, and post the owner-review request for `SYNTHESIS.md` in chat (strategic §11.4).

**Verification:**

- `Grep` - `dev/CHANGELOG.md` contains `01__current-flow-inventory.md` and `SYNTHESIS.md` entries.
- `Grep` - `Timber.d("S0395` returns zero hits across `app_v2/**/*.kt` and `wear/**/*.kt`.
- `Bash` - `select.ps1 -Id S0395 -Format json` reports `"status":"Implemented"`.

**Status:** `[x]` done

**Step Log:**

- 2026-06-10 - Verification 3/3 PASS: CHANGELOG covers all artifacts (3 hits incl. SYNTHESIS), zero S0395 Timber tags in .kt (research ticket - none allowed), journal at Implemented (20:53). Catalog regen + FEATURES skipped per plan (no Kotlin, §8 = no changes). Owner-review request posted in chat.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] `git status` - S0395 changes confined to `PLAN/S0395_welcome-screens-redesign-research*`, `dev/CHANGELOG.md`, `temp/sessions/` (pre-existing unrelated DEBUG-v013 changes noted in Phase 01).
- [x] INDEX.md Completion Gate checkboxes all addressed (owner review pending - see INDEX).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Dev tickets from `research/12__dev-ticket-split.md` are created only after owner sign-off on SYNTHESIS.md.

---

## Rollback Plan

Revert the §6 status flips - artifacts remain on disk; no code or data surface changed.
