# Phase 05 — README "Get it on GitHub Store" Badge

**Strategic spec:** [`../S0214_github-store-publication.md`](../S0214_github-store-publication.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-05-15
**Completed:** 2026-05-15

---

## Objective

Add a clickable "Get it on GitHub Store" badge to the three README locales (EN root, RU, UK), linking to `https://github-store.org/app?repo=SerZhyAle/FastMediaSorter_mob_v2`. Visual format and captions are taken from `DECISIONS.md` §`## README badge`.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done — `DECISIONS.md` §`## README badge` is final.
- [ ] All three target README files exist on disk (verified):
    - `README.md` (root, EN)
    - `docs/README_RU.md`
    - `docs/README_UK.md`

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `README.md` | Modified | ≤ +30 |
| `docs/README_RU.md` | Modified | ≤ +30 |
| `docs/README_UK.md` | Modified | ≤ +30 |

---

## Steps

### Step 05.1 — Locate the badge insertion anchor in each README

**Files:** `README.md`, `docs/README_RU.md`, `docs/README_UK.md` _(read-only this step)_
**Depends on:** — start of phase

**Prompt for developer:**

> For each of the three READMEs, identify the existing "Download" section (or its localized equivalent) where badges such as F-Droid / Google Play / direct APK currently live. Record the exact line range of that section for each file as inline notes in a scratch buffer (not committed). If a file has no Download section, the anchor is "immediately after the top description and before the first feature heading". Do NOT modify any file in this step.

**Verification:**

- Each of the three files has an identified insertion anchor recorded (manual confirmation in chat or step body).
- expected: three anchor line numbers / section names recorded | actual: README.md L84 `## Download 📥` (insert after Google Play badge ~L86); docs/README_RU.md L72 `## Скачать 📥` (same relative position); docs/README_UK.md L72 `## Завантажити 📥` (same). PASS.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Located insertion anchors in all three READMEs via heading grep. All three have an existing distribution-section heading where the GHS badge fits next to the Google Play badge. No file modified in this step.

---

### Step 05.2 — Insert badge into root `README.md` (EN)

**Files:** `README.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Insert the GitHub Store badge at the anchor from Step 05.1, formatted exactly per `DECISIONS.md` §`## README badge` (badge image URL, link target `https://github-store.org/app?repo=SerZhyAle/FastMediaSorter_mob_v2`, height matching neighbouring badges, alt text). Place it alongside existing distribution badges so the visual hierarchy reads left-to-right at the same height. Use the EN caption from `DECISIONS.md`. Comply with `docs/COMMUNICATION_POLICY.md` §2 (CTA formula) and §6 (tone checklist) for the caption.

**Verification:**

- `Grep` — `github-store.org/app?repo=SerZhyAle/FastMediaSorter_mob_v2` matches in `README.md`.
- `Grep` — `alt=` (or markdown alt-syntax) present for the badge image.
- `Grep` — string from `DECISIONS.md` EN caption present verbatim.
- Strings pass COMMUNICATION_POLICY §6 checklist (manual: tone is friendly, not bureaucratic; not "click here"; uses sentence case if other captions do).

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS (link `github-store.org/app?repo=SerZhyAle/FastMediaSorter_mob_v2` 1×; `alt="Get it on GitHub Store"` 1×; EN caption verbatim from DECISIONS.md 1×; COMMUNICATION_POLICY §6 spot-check passed at decision time, see DECISIONS.md). Files: README.md (+8 LOC; badge inserted between Google Play and "compiled APK files" lines). Dev log recorded.

---

### Step 05.3 — Insert badge into `docs/README_RU.md` (Russian)

**Files:** `docs/README_RU.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Mirror Step 05.2 in the Russian README. Same badge image URL, same link target, same height. Use the RU caption from `DECISIONS.md`. The caption must use `..` (two dots) and `ё`/`Ё` per project author style (`CLAUDE.md` § Author Style). Comply with `docs/COMMUNICATION_POLICY_RU.md` §2 and §6.

**Verification:**

- `Grep` — `github-store.org/app?repo=SerZhyAle/FastMediaSorter_mob_v2` matches in `docs/README_RU.md`.
- `Grep` — RU caption from DECISIONS.md present verbatim.
- `Grep -i` — sequence `\.\.\.` (three dots in a row) **absent** in the new lines added by this step (project style: `..`).
- Strings pass COMMUNICATION_POLICY_RU §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 4/4 PASS (link 1×, RU caption verbatim 1×, no `...` triple-dot anywhere in file, COMMUNICATION_POLICY_RU §6 pre-validated in Phase 01). Files: docs/README_RU.md (+8 LOC). Dev log recorded.

---

### Step 05.4 — Insert badge into `docs/README_UK.md` (Ukrainian)

**Files:** `docs/README_UK.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Mirror Steps 05.2 / 05.3 in the Ukrainian README. Same badge image URL, same link target, same height. Use the UK caption from `DECISIONS.md`. Comply with `docs/COMMUNICATION_POLICY_UK.md` §2 and §6.

**Verification:**

- `Grep` — `github-store.org/app?repo=SerZhyAle/FastMediaSorter_mob_v2` matches in `docs/README_UK.md`.
- `Grep` — UK caption from DECISIONS.md present verbatim.
- Strings pass COMMUNICATION_POLICY_UK §6 checklist.

**Status:** `[x] done`

**Step Log:**

- 2026-05-15 — Verification 3/3 PASS (link 1×, UK caption verbatim 1×, COMMUNICATION_POLICY_UK §6 pre-validated in Phase 01). Phase Done Criterion: badge link across all 3 READMEs returns 3 hits. Files: docs/README_UK.md (+8 LOC). Dev log recorded.

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] All three READMEs contain the same badge image URL and the same link target.
- [ ] Each README's caption is the locale-appropriate text from DECISIONS.md (no caption duplicated between locales).
- [ ] `Grep` for `github-store.org/app?repo=SerZhyAle/FastMediaSorter_mob_v2` returns ≥ 3 hits (one per README).
- [ ] Dev log entries added for all three README files.

---

## Handoff Notes to Next Phase

Final phase (06) records all touched files in the dev log and runs no further consumers of READMEs.

---

## Rollback Plan

Each README change is additive and small — revert the badge-block hunk per file. No data migration.
