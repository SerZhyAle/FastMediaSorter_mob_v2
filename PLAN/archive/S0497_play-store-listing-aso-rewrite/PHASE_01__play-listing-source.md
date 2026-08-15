# Phase 01 - Play listing source

**Strategic spec:** [`../S0497_play-store-listing-aso-rewrite.md`](../S0497_play-store-listing-aso-rewrite.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03
**Steps done:** 2 / 2
**Started:** -
**Completed:** -

---

## Objective

Establish `play/listing/` as the Play-only listing source (texts per locale), decoupled from the
IzzyOnDroid fastlane metadata, with the ASO-rewritten EN/RU/UK title/short/full descriptions.

---

## Prerequisites

- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `play/listing/en-US/{title,short_description,full_description}.txt` | New | - |
| `play/listing/ru-RU/{title,short_description,full_description}.txt` | New | - |
| `play/listing/uk-UA/{title,short_description,full_description}.txt` | New | - |
| `play/listing/README.md` | New | ≤ 60 |

---

## Steps

### Step 01.1 - Author Play-optimized listing texts (EN/RU/UK)

**Files:** `play/listing/<locale>/{title,short_description,full_description}.txt`
**Depends on:** - start of phase

**Prompt for developer:**

> Write the Play-optimized title (<=30), short_description (<=80) and full_description (<=4000)
> for en-US, ru-RU, uk-UA under `play/listing/<locale>/`. Brand "Fast Media Sorter" in every locale.
> Lead the full_description with file-manager + media-player keywords in the first ~250 chars; group
> capabilities by benefit; drop the IzzyOnDroid Anti-Features / license block; keep one closing line
> on Apache-2.0 + offline. RU/UK use Ё/ё and `..`, native phrasing. Every claim must match shipped
> features (cross-check `docs/ALL_FEATURES.jsonl` / `docs/FEATURES*.md`). Authored as UTF-8 files via
> the Write tool (never pass Cyrillic through bash->pwsh args). Strings pass COMMUNICATION_POLICY §6 checklist.

**Verification:**

- `Glob` - all nine `play/listing/<locale>/*.txt` exist.
- Char counts within Play limits (title<=30, short<=80, full<=4000) for every file.
- `Grep` - `Fast Media Sorter` present in every `full_description.txt`.
- `Grep` - `Anti-Features` returns zero hits across `play/listing/**`.

**Status:** `[x]` done

---

### Step 01.2 - Document the Play-vs-fastlane decoupling

**Files:** `play/listing/README.md`
**Depends on:** Step 01.1

**Prompt for developer:**

> Add `play/listing/README.md` stating: this tree is the Google Play listing source consumed by
> `scripts/release/publish-play-listing.py`; `fastlane/metadata/android/` remains the IzzyOnDroid /
> GitHub-store source and must keep its Anti-Features block; changelogs stay shared under fastlane.
> List the expected directory layout (texts + `images/phoneScreenshots/`).

**Verification:**

- `Glob` - `play/listing/README.md` exists.
- `Grep` - `publish-play-listing` present in README.
- `Grep` - `fastlane` present in README (decoupling note).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` above is `[x] done`.
- [ ] Char-limit check passes for all nine text files.
- [ ] Dev log entry added for every file in "Files Touched".

---

## Handoff Notes to Next Phase

`play/listing/<locale>/` holds the authoritative Play texts. Phase 02 uploader reads title/short/full
from these paths; Phase 03 writes screenshots into `play/listing/<locale>/images/phoneScreenshots/`.

---

## Rollback Plan

Delete `play/listing/` - no build or runtime surface depends on it until Phase 02 ships.
