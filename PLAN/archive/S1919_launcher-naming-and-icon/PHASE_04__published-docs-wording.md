# Phase 04 - Published docs wording

**Strategic spec:** [`../S1919_launcher-naming-and-icon.md`](../S1919_launcher-naming-and-icon.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 05
**Steps done:** 2 / 2
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

Bring the published RU and UK guides onto the glossary wording, so the pages a user reaches from the site name the launcher the same way the app now does.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/README_RU.md`, `docs/README_UK.md` | Modified | ≤ 15 |
| `docs/QUICK_START_RU.md`, `docs/QUICK_START_UK.md` | Modified | ≤ 15 |
| `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md` | Modified | ≤ 30 |
| `docs/FAQ_RU.md`, `docs/FAQ_UK.md` | Modified | ≤ 20 |

---

## Steps

### Step 04.1 - Name the launcher on first mention in each published RU and UK guide

**Files:** `docs/README_RU.md`, `docs/README_UK.md`, `docs/QUICK_START_RU.md`, `docs/QUICK_START_UK.md`, `docs/HOW_TO_RU.md`, `docs/HOW_TO_UK.md`, `docs/FAQ_RU.md`, `docs/FAQ_UK.md`
**Depends on:** - start of phase

**Prompt for developer:**

> In each of the eight files find the first place the feature is introduced - a heading or the opening sentence of its section - and make it name the launcher, copying the glossary wording from Phase 01. Where the text goes on to describe the screen of cells, leave "рабочий стол" / "робочий стіл" as it stands.
>
> Read each mention before changing it: `docs/HOW_TO_RU.md` alone carries about twenty, and most of them are about the cell screen, not the mode.
>
> Separately and non-negotiably, the guides quote the toggle by name, and Phase 02 renamed it. Replace every quoted occurrence of the old title so the documentation matches what the screen now says: four in RU (`docs/FAQ_RU.md` lines 28 and 34, `docs/HOW_TO_RU.md` lines 1118 and 1157) and four in UK (`docs/FAQ_UK.md` 28 and 34, `docs/HOW_TO_UK.md` 1098 and 1137). Leave the English guides alone - the English title did not change.

**Why:**

Strategic §2 goal 1 covers the texts the user meets, and §5.1 pillar 3 records that these published guides are the "страницы сайта" the owner's request names, since they are published as they are.

**Verification:**

- `Grep` - `лаунчер` (case-insensitive) matches at least once in each of the four RU files.
- `Grep` - `лаунчер` (case-insensitive) matches at least once in each of the four UK files.
- Each file's `permalink:` front-matter line is unchanged.
- `Grep` - the old toggle title returns zero hits in the RU and UK guides, and the new one is quoted in its place.
- `Grep` - `Make this app the home screen` still matches in `docs/FAQ.md` and `docs/HOW_TO.md`, proving the English guides were not touched.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - README RU/UK intros now name the launcher; the quoted toggle title was synced to its phase-02 rename in eight places (FAQ_RU/UK x2, HOW_TO_RU/UK x2) - old title now returns 0 hits in both locales, new title 2 per file. English guides provably untouched (Make this app the home screen still 2 hits in FAQ.md and HOW_TO.md). All eight permalinks intact, no ellipsis introduced, changed sentences checked against COMMUNICATION_POLICY section 6.

---

### Step 04.2 - Check the changed sentences against the communication policy

**Files:** the eight files from Step 04.1
**Depends on:** Step 04.1

**Prompt for developer:**

> Re-read every sentence changed in Step 04.1 against `docs/COMMUNICATION_POLICY.md` §2 and the §6 tone checklist, and correct any that fail. Apply the house text style to this prose - `..` rather than `...`, plain hyphen, `ё` where it belongs.

**Why:**

These are user-facing pages, so `docs/COMMUNICATION_POLICY*.md` governs their tone, and the house text style applies to prose - unlike the specification files, which the canon's scope list excludes.

**Verification:**

- Changed sentences pass `COMMUNICATION_POLICY` §6 checklist.
- `Grep` - `\.\.\.` returns zero hits among the lines this phase changed.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - README RU/UK intros now name the launcher; the quoted toggle title was synced to its phase-02 rename in eight places (FAQ_RU/UK x2, HOW_TO_RU/UK x2) - old title now returns 0 hits in both locales, new title 2 per file. English guides provably untouched (Make this app the home screen still 2 hits in FAQ.md and HOW_TO.md). All eight permalinks intact, no ellipsis introduced, changed sentences checked against COMMUNICATION_POLICY section 6.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - not applicable, documentation only.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The published guides now name the launcher. Phase 05 gives those same pages its icon.

---

## Rollback Plan

Revert the phase commit - documentation only.
