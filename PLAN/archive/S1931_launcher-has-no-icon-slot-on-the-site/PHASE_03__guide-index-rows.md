# Phase 03 - Guide index rows and the guide icon

**Strategic spec:** [`../S1931_launcher-has-no-icon-slot-on-the-site.md`](../S1931_launcher-has-no-icon-slot-on-the-site.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-22
**Completed:** 2026-08-22

---

## Objective

List the launcher guide in both lists of all three `docs/howto/index*.md` files and give it the `ic_launcher_mode` icon through the `howto` array of the doc-icon map.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] `docs/icons/doc/ic_launcher_mode.png` exists - produced by the PNG half of `export-doc-icon-pngs.ps1`, which this phase's step 03.3 consumes.
- [x] The three guide pages exist (Phase 01).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/icons/doc-icon-map.json` | Modified | ≤ 5 |
| `docs/howto/index.md` | Modified | ≤ 5 |
| `docs/howto/index-ru.md` | Modified | ≤ 5 |
| `docs/howto/index-uk.md` | Modified | ≤ 5 |

---

## Steps

### Step 03.1 - Add the `howto` map entry

**Files:** `docs/icons/doc-icon-map.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Append one object to the `howto` array pairing `ic_launcher_mode` with an emoji that appears nowhere else in `docs/howto/index.md`, `index-ru.md`, `index-uk.md` or in the existing 13 `howto` entries, taking the array to 14. Use that same emoji in the rows written in step 03.2.

**Why:**

Strategic §4 records that `apply-doc-icons.ps1` rewrites the guide indexes by a plain global text replacement of each mapped emoji, so an emoji already used elsewhere on those pages would be rewritten into the launcher icon in places that are not the launcher.

**Verification:**

- Value equality - `.howto.Count` equals `14`.
- Value equality - `.howto[13].drawable` equals `ic_launcher_mode`.
- `Grep` - the chosen emoji matches zero times across the three `docs/howto/index*.md` files before step 03.2 is applied, and it matches zero times in the other 13 `howto` entries.

**Status:** `[x]` done

**Step Log:**

- 2026-08-22 - howto array 13 -> 14: U+1FA9F paired with ic_launcher_mode. Emoji verified absent from the other 13 howto entries, from docsMap, and from all three docs/howto/index*.md before the rows were written, so the generator's global text replace cannot hit anything else.

---

### Step 03.2 - Add the guide to both lists in all three indexes

**Files:** `docs/howto/index.md`, `docs/howto/index-ru.md`, `docs/howto/index-uk.md`
**Depends on:** Step 03.1

**Prompt for developer:**

> In each of the three index files add one bullet to "Pick Your Guide in 10 Seconds" (and its localized heading) phrased as the reader's own wish, and one row to the "All Guides" table carrying the guide title, what the reader gets, an estimated time and the flavor column reading `Standard / noLegal`. Both entries start with the step 03.1 emoji and link to the locale's own guide page - `scenario-launcher-mode.md`, `scenario-launcher-mode-ru.md`, `scenario-launcher-mode-uk.md`. Leave the existing `Home-Screen Smart Widgets` rows untouched. Check the copy against `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist.

**Why:**

Strategic §11 criterion 4 requires all three index files to reach the new page from both lists, and §3.1 keeps the launcher distinct from the Android home-screen widgets entry that already sits in those lists.

**Verification:**

- `Grep` - `scenario-launcher-mode.md` matches exactly twice in `docs/howto/index.md`; the `-ru` and `-uk` link names match exactly twice in their own index files.
- `Grep` - `Standard / noLegal` is present in the new table row of each file.
- `Grep` - `Home-Screen Smart Widgets` still matches twice in `docs/howto/index.md` (both original rows intact).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-22 - One bullet added to the pick-your-guide list and one row to the All Guides table in all three index files, each linking the locale's own guide page and flagged 'Standard / noLegal'. Both Home-Screen Smart Widgets rows left intact.

---

### Step 03.3 - Apply the icons to the guide indexes

**Files:** `docs/howto/index.md`, `docs/howto/index-ru.md`, `docs/howto/index-uk.md`
**Depends on:** Step 03.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/docs/apply-doc-icons.ps1` and confirm it exits 0 and that the step 03.1 emoji has been replaced by an `<img>` pointing at `../icons/doc/ic_launcher_mode.png` in all six new entries.

**Why:**

Strategic §11 criterion 5 requires the icon to be visible both on the card and in the guide index, and §3.2 forbids hand-editing generated output - the `<img>` markup is the generator's, not the author's.

**Verification:**

- Exit code - `apply-doc-icons.ps1` returned 0.
- `Grep` - `icons/doc/ic_launcher_mode.png` matches exactly twice in each of the three index files.
- `Grep` - the step 03.1 emoji matches zero times in the three index files.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 03.*` above is `[x] done`.
- [x] Project compiles - not applicable, no source, resource or build file touched.
- [x] `Grep` for `TODO(phase-03)` returns zero hits.
- [x] Dev log entry added for the phase's file set via `scripts/post-change.ps1`.
- [x] `dev/CATALOG/<module>.jsonl` regeneration not required - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (documentation only, no audit trigger fired).

---

## Handoff Notes to Next Phase

Every user-facing surface named by strategic §11 now exists. Phase 04 revalidates the document registry, regenerates its derived files, and runs the icon and documentation gates.

---

## Rollback Plan

Revert the index rows and the `howto` map entry, then re-run `apply-doc-icons.ps1`. No data migration and no app surface is involved.
