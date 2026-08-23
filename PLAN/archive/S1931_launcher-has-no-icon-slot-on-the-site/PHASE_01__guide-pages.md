# Phase 01 - Guide pages for launcher mode

**Strategic spec:** [`../S1931_launcher-has-no-icon-slot-on-the-site.md`](../S1931_launcher-has-no-icon-slot-on-the-site.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-22
**Completed:** 2026-08-22

---

## Objective

Create `docs/howto/scenario-launcher-mode.md` and its `-ru` / `-uk` pairs, shaped like the eleven existing `scenario-*.md` guides, leading the reader through switching launcher mode on and the first steps on the new desktop.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none.
- [ ] `docs/HOW_TO.md` section "How to Use the App as Your Home Screen" is present - the guide links back to it for the full reference.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/howto/scenario-launcher-mode.md` | New | ≤ 170 |
| `docs/howto/scenario-launcher-mode-ru.md` | New | ≤ 170 |
| `docs/howto/scenario-launcher-mode-uk.md` | New | ≤ 170 |

---

## Steps

### Step 01.1 - Write the English guide page

**Files:** `docs/howto/scenario-launcher-mode.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the page following the shape of `docs/howto/scenario-photo-frame.md`: a Jekyll front matter block with `layout: default`, a `title:` ending in `- FastMediaSorter v2` and `permalink: /docs/howto/scenario-launcher-mode.html`; an `<h1>` carrying one emoji; a `> **Level:** .. **Flavor:** ..` line naming Standard and noLegal; a language switcher line linking the `-ru` and `-uk` pages; then numbered `## Step N - ..` sections covering turning launcher mode on from Settings → General, confirming Android's home-app prompt, what the first desktop already holds, adding and rearranging cells, the taskbar and Start menu, and leaving launcher mode again. Close with `## Tips` and a `## Troubleshooting` table that includes the device-firmware case where the home-app choice does not stick. Point the reader at `../HOW_TO.md#how-to-use-the-app-as-your-home-screen` for the full reference rather than copying that section wholesale. Check the copy against `docs/COMMUNICATION_POLICY.md` §2 message formula and §6 tone checklist.

**Why:**

Strategic ADR-2 rules that only a page of its own closes §1, because an anchor inside `HOW_TO.md` would leave launcher mode living inside someone else's page - which is exactly the condition this ticket names as the problem - and §5 requires the existing `HOW_TO*` prose to stay in place as the reference the guide points to.

**Verification:**

- `Glob` - `docs/howto/scenario-launcher-mode.md` exists.
- `Grep` - `permalink: /docs/howto/scenario-launcher-mode.html` matches exactly once.
- `Grep` - `scenario-launcher-mode-ru.md` and `scenario-launcher-mode-uk.md` both match (language switcher).
- `Grep` - `HOW_TO.md#how-to-use-the-app-as-your-home-screen` matches at least once.
- `Grep` - `## Troubleshooting` matches exactly once.
- `Grep` - `docs/HOW_TO.md` still contains `## How to Use the App as Your Home Screen` (prose not moved).
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-22 - Wrote docs/howto/scenario-launcher-mode.md (122 lines, 9 H2 sections): permalink declared once, both locale links present, links back to HOW_TO.md#how-to-use-the-app-as-your-home-screen instead of copying it, HOW_TO.md section left in place.

---

### Step 01.2 - Write the Russian guide page

**Files:** `docs/howto/scenario-launcher-mode-ru.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create the Russian counterpart with the same section order and the same steps, front matter `permalink: /docs/howto/scenario-launcher-mode-ru.html`, and a language switcher linking the English and Ukrainian pages. Follow the house text style for Russian prose - `..` rather than `...`, plain hyphens, and `ё` where it is grammatically correct. Point the reader at `../HOW_TO_RU.md` for the full reference.

**Why:**

Strategic §3.2 requires landing pages and guides to be edited in EN, RU and UK within one change, so a page shipped in one locale only would leave the constraint unmet.

**Verification:**

- `Glob` - `docs/howto/scenario-launcher-mode-ru.md` exists.
- `Grep` - `permalink: /docs/howto/scenario-launcher-mode-ru.html` matches exactly once.
- Value equality - the count of `^## ` headings equals the count in `scenario-launcher-mode.md`.
- `Grep` - `HOW_TO_RU.md` matches at least once.

**Status:** `[x]` done

**Step Log:**

- 2026-08-22 - Wrote scenario-launcher-mode-ru.md and -uk.md: each declares its own permalink once, carries 9 H2 sections matching the English page, and points at HOW_TO_RU.md / HOW_TO_UK.md for the full reference.

---

### Step 01.3 - Write the Ukrainian guide page

**Files:** `docs/howto/scenario-launcher-mode-uk.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Create the Ukrainian counterpart with the same section order and the same steps, front matter `permalink: /docs/howto/scenario-launcher-mode-uk.html`, and a language switcher linking the English and Russian pages. Point the reader at `../HOW_TO_UK.md` for the full reference.

**Why:**

Strategic §3.2 requires landing pages and guides to be edited in EN, RU and UK within one change, so a page shipped in one locale only would leave the constraint unmet.

**Verification:**

- `Glob` - `docs/howto/scenario-launcher-mode-uk.md` exists.
- `Grep` - `permalink: /docs/howto/scenario-launcher-mode-uk.html` matches exactly once.
- Value equality - the count of `^## ` headings equals the count in `scenario-launcher-mode.md`.
- `Grep` - `HOW_TO_UK.md` matches at least once.

**Status:** `[x]` done

**Step Log:**

- 2026-08-22 - Wrote scenario-launcher-mode-ru.md and -uk.md: each declares its own permalink once, carries 9 H2 sections matching the English page, and points at HOW_TO_RU.md / HOW_TO_UK.md for the full reference.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable, no source, resource or build file touched.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for the phase's file set via `scripts/post-change.ps1`.
- [x] `dev/CATALOG/<module>.jsonl` regeneration not required - no Kotlin touched.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Three guide pages exist with declared permalinks, which is what the `user-guides` registry record requires of any file under `docs/howto/`. Phase 02 links its landing card at them and Phase 03 lists them in the guide indexes.

---

## Rollback Plan

Delete the three new pages. Nothing references them until Phase 02's card footers and Phase 03's index rows land, so no other surface breaks.
