# Phase 02 - Scenario page: music on the watch

**Strategic spec:** [`../S1801_wear-documentation-site-pages.md`](../S1801_wear-documentation-site-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none in this plan - gated by `Blocker: S1781` (see INDEX Pre-Implementation Blockers)
**Blocks:** Phase 03, Phase 04, Phase 05
**Steps done:** 5 / 5
**Started:** 2026-08-19
**Completed:** 2026-08-19

---

## Objective

Publish a step-by-step guide that takes a watch owner from an empty watch to music playing on it, in English, Russian and Ukrainian, listed in both entrances of the step-by-step guide index.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] S1781 is `Verified` - the Wear main screen this guide opens on is final.
- [x] Strategic §6 research items blocking this phase are Resolved - §6.1 and §6.2, artifacts `research/01__site-genre-and-wear-gap.md` and `research/02__locales-and-s1211.md`.
- [x] A Wear OS surface is reachable by `adb devices` for walking the flow while writing it.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/howto/scenario-watch-music.md` | New | ≤ 200 |
| `docs/howto/scenario-watch-music-ru.md` | New | ≤ 200 |
| `docs/howto/scenario-watch-music-uk.md` | New | ≤ 200 |
| `docs/howto/index.md` | Modified | ≤ 4 added lines |
| `docs/howto/index-ru.md` | Modified | ≤ 4 added lines |
| `docs/howto/index-uk.md` | Modified | ≤ 4 added lines |

> No Kotlin, no resources, no layout. Nothing in this phase reaches `app_v2/` or `wear/`.

---

## Steps

### Step 02.1 - Trace the music flow on the Wear surface

**Files:** `evidence/watch-music-walkthrough.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Drive the Wear surface through the whole flow - pair the watch, reach the paired-phone resource, open a music folder, start playback, use shuffle, the bezel volume and the draggable position bar, then let the screen go off while audio continues. Record each screen, each control name as the interface spells it, and every place the flow differs from what you expected, into `evidence/watch-music-walkthrough.md`.

**Why:**

Strategic §3.1 requires scenarios to be written from the perspective of what the user wants to achieve, and §7 names "сценарий описан по плану, а не по работающему приложению" as the central risk; tracing the real screens first anchors the step text in what the user actually sees, not in what the Kotlin implementation thought it was rendering.

**Verification:**

- `Glob` - `evidence/watch-music-walkthrough.md` exists.
- `Grep` - the file names at least five distinct watch screens.

**Status:** `[x]` done

---

### Step 02.2 - Write the English scenario page

**Files:** `docs/howto/scenario-watch-music.md`
**Depends on:** Step 02.1

**Prompt for developer:**

> Write the page in the genre the nine existing `docs/howto/scenario-*.md` pages establish: front matter with `layout: default`, a `title`, and `permalink: /docs/howto/scenario-watch-music.html`; an H1 carrying an inline icon from `../icons/doc/`; a level and flavor line; a language-switch line linking the `-ru` and `-uk` siblings; a plain-English explanation of what the watch is doing; a "What You Will Need" section; numbered steps from the Step 02.1 walkthrough; a payoff section; a troubleshooting table. Leave an HTML comment placeholder `<!-- TODO screenshot: .. -->` at every step where the wording alone is ambiguous - Phase 04 fills them. Check the copy against `docs/COMMUNICATION_POLICY.md` §2 for the message formula and §6 for the tone checklist, and take every interface term from its §7 glossary. Write any navigation route as one route per line using ASCII `>`, never the arrow character.

**Why:**

Strategic ADR-1 decides that Wear guides join the existing step-by-step genre rather than getting their own section, because that genre already gives each page its own published address, which is what the owner asked for; strategic §3.2 additionally binds the copy to the communication policy glossary so the page does not drift from the terms the watch interface uses.

**Verification:**

- `Glob` - `docs/howto/scenario-watch-music.md` exists.
- `Grep` - `permalink: /docs/howto/scenario-watch-music.html` matches exactly once.
- `Grep` - `scenario-watch-music-ru.md` and `scenario-watch-music-uk.md` both match in the language-switch line.
- `Grep` - the arrow character U+2192 returns zero hits in the file.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

---

### Step 02.3 - Write the Russian and Ukrainian siblings

**Files:** `docs/howto/scenario-watch-music-ru.md`, `docs/howto/scenario-watch-music-uk.md`
**Depends on:** Step 02.2

**Prompt for developer:**

> Write both siblings in the same edit as each other, matching the English page step for step and section for section - same count of numbered steps, same screenshot placeholders in the same positions, same troubleshooting rows. Adapt the wording naturally per `docs/COMMUNICATION_POLICY.md` §5 rather than translating word for word, and set each file's own `permalink` and its own language-switch line pointing at the other two.

**Why:**

Strategic §2.3 requires the language switcher on every page to reach an existing page rather than nothing, and §7 lists locale divergence in step composition as a risk whose consequence is that switching language changes the content of the guide; writing the three locales in one change is the mitigation the strategic spec names.

**Verification:**

- `Glob` - both files exist.
- `Grep` - `permalink: /docs/howto/scenario-watch-music-ru.html` matches exactly once in the `-ru` file, and the `-uk` equivalent exactly once in the `-uk` file.
- The count of `## Step` headings is equal across the three locale files.
- The count of `<!-- TODO screenshot:` placeholders is equal across the three locale files.

**Status:** `[x]` done

---

### Step 02.4 - List the guide in both entrances of the index, on three locales

**Files:** `docs/howto/index.md`, `docs/howto/index-ru.md`, `docs/howto/index-uk.md`
**Depends on:** Step 02.3

**Prompt for developer:**

> Add the guide to each index file twice: once as a first-person question line in the "Pick Your Guide in 10 Seconds" list, and once as a row in the "All Guides" table with its Guide, What you get, Time and Flavor columns filled. Do all three locale files in this step, keeping the new entries in the same position in each.

**Why:**

Strategic §5.1 states that without entrances the pages exist but are unreachable, and §11.2 requires the Wear guides to be present in both index lists; the index is the only navigation the genre has, since cross-links between guide pages are written by hand and nothing generates them.

**Verification:**

- `Grep` - `scenario-watch-music.md` matches exactly twice in `docs/howto/index.md`.
- `Grep` - `scenario-watch-music-ru.md` matches exactly twice in `docs/howto/index-ru.md`.
- `Grep` - `scenario-watch-music-uk.md` matches exactly twice in `docs/howto/index-uk.md`.

**Status:** `[x]` done

---

### Step 02.5 - Run the navigation-recipe gate

**Files:** `docs/howto/scenario-watch-music.md`, `docs/howto/scenario-watch-music-ru.md`, `docs/howto/scenario-watch-music-uk.md`
**Depends on:** Step 02.4

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/assert-howto-settings-paths.ps1`. If it reports an unresolvable segment, fix the page to match the settings manifest title rather than extending the vocabulary, unless the segment is a genuine tab, media sub-section or non-settings screen - only then extend `docs/settings/howto-path-vocab.json` for all three locales.

**Why:**

Strategic §3.2 binds navigation routes to the gate that checks them against the settings manifest and enforces positional parity across the three locales, and §7 names term divergence between the pages and the interface as a risk whose consequence is that the user cannot find on the watch what the text names.

**Verification:**

- `scripts/quality/assert-howto-settings-paths.ps1` exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] Project compiles - not applicable: no source file is touched in this phase.
- [x] `Grep` for `TODO(phase-02)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The genre now holds a Wear guide, and the index files carry Wear rows in both of their lists. Phase 03 copies this page structure for the network scenario and adds its rows beside these ones; Phase 04 replaces every `<!-- TODO screenshot: .. -->` placeholder this phase left behind, and the placeholder count is identical across the three locale files, which is what lets Phase 04 verify that no locale was missed.

---

## Rollback Plan

Delete the three new pages and revert the six-line index change. No generated artifact, no source file and no data migration is involved, so the site returns to its previous page set on the next build.
