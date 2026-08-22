# Phase 01 - Split the Wear docs registry group by audience

**Strategic spec:** [`../S1801_wear-documentation-site-pages.md`](../S1801_wear-documentation-site-pages.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 06
**Steps done:** 4 / 4
**Started:** 2026-08-18
**Completed:** 2026-08-18

---

## Objective

Replace the single `wear-docs` registry record with records that state each Wear document's real audience, so the internal progress trackers and the module build instructions stop being a publicly indexable surface, and regenerate the two artifacts the registry drives.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done - none.
- [x] Strategic §6 research items blocking this phase are Resolved - §6.4 is Resolved, artifact `research/04__existing-wear-docs-classification.md`.
- [x] Working tree is clean or on a feature branch.
- [x] `docs/DOCUMENT_REGISTRY.jsonl` currently holds exactly one record with `"id": "wear-docs"`.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/DOCUMENT_REGISTRY.jsonl` | Modified | ≤ 6 record lines |
| `docs/DOCS_MAP.md` | Modified (generated - never hand-edited) | n/a |
| `sitemap.xml` | Modified (generated - never hand-edited) | n/a |

> No Kotlin, no resources, no flavor-specific sources. Nothing in this phase reaches `app_v2/` or `wear/`.

---

## Steps

### Step 01.1 - Capture the baseline of published and declared files

**Files:** `evidence/wear-docs-baseline.txt`
**Depends on:** - start of phase

**Prompt for developer:**

> Write the current state to `evidence/wear-docs-baseline.txt`: the full `wear-docs` record from `docs/DOCUMENT_REGISTRY.jsonl`, the list of files matching `docs/WEAR_OS_*.md`, and every `<loc>` line in `sitemap.xml` that contains `WEAR`. Capture it before any edit, not after.

**Why:**

Strategic §10 records that five of the eight Wear documents were not meant for users and three were user-facing, but all eight shared one registry record; before splitting the group we need the exact set of published URLs to prove in Phase 06 that no user-facing page was lost and no internal document remained public.

**Verification:**

- `Glob` - `evidence/wear-docs-baseline.txt` exists.
- `Grep` - `wear-docs` matches in that file.
- `Grep` - `WEAR_OS_QUICK_START` matches in that file.

**Status:** `[x]` done

---

### Step 01.2 - Split the record into a user-facing group and a developer group

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace the single `wear-docs` record with two records. The user-facing one keeps `id` `wear-docs`, narrows `paths` to the two documents written for a watch owner - `docs/WEAR_OS_SMB_SETUP.md` and `docs/WEAR_OS_SMB_QUICK_REF.md` - sets `audience` to `user`, `published` true, `indexable` true, and moves `url` to `/docs/WEAR_OS_SMB_SETUP.html`, because the address it declares today belongs to a document that leaves this group. The developer one takes a new `id` `wear-dev-docs`, holds the remaining six `docs/WEAR_OS_*.md` files by explicit path list, and sets `audience` to `developer`, `published` false, `indexable` false. Keep `product_areas` `["wear"]` on both and leave every `update_triggers` value as it is today. Do not edit the Markdown files themselves.

**Why:**

Strategic ADR-2 decides that the group is split by real audience without touching the texts, because `audience: "mixed"` on a group holding both "how to build the module in Android Studio" and a closed-phase checklist records the absence of a decision rather than an audience, and strategic §2.4 requires that the publicly indexable Wear surface stop containing progress trackers and module build instructions.

**Verification:**

- `Grep` - `"id": "wear-dev-docs"` matches exactly once in `docs/DOCUMENT_REGISTRY.jsonl`.
- `Grep` - `"id": "wear-docs"` matches exactly once in `docs/DOCUMENT_REGISTRY.jsonl`.
- `Grep` - `WEAR_OS_STATUS` matches on the `wear-dev-docs` line and not on the `wear-docs` line.
- `Grep` - `docs/WEAR_OS_*.md` (the old glob) returns zero hits in `docs/DOCUMENT_REGISTRY.jsonl`.

**Status:** `[x]` done

---

### Step 01.3 - Claim Wear as a product area of the three-locale guide group

**Files:** `docs/DOCUMENT_REGISTRY.jsonl`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `wear` to the `product_areas` array of the `user-guides` record, because the Wear scenario pages Phases 02 and 03 write land under its `docs/howto/*.md` glob and that record already declares `languages` `["en", "ru", "uk"]` with a `localized_urls` object. Leave `wear-docs` at `languages: ["en"]` and give it no `localized_urls`.

**Why:**

Strategic §2.3 requires every page this ticket produces to read on three languages, and ADR-3 fixes three locales from day one - but that binds the new scenario pages, which live in the three-locale guide group, not the two pre-existing Wear reference documents that have no Russian or Ukrainian sibling on disk. Declaring locale addresses for pages that do not exist would put dead links in the sitemap, and translating those two documents is a non-goal of this ticket per strategic §2.

**Verification:**

- `Grep` - `"wear"` matches on the `user-guides` record line.
- `Grep` - `"localized_urls"` returns zero hits on the `wear-docs` record line.
- `Grep` - `"languages": ["en"]` matches on the `wear-docs` record line.
- `pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea "wear"` lists both `wear-docs` and `user-guides`.

**Status:** `[x]` done

---

### Step 01.4 - Validate the registry and regenerate what it drives

**Files:** `docs/DOCS_MAP.md`, `sitemap.xml`
**Depends on:** Step 01.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/document_registry/validate.ps1`, then `generate.ps1`, then `generate.ps1 -Check`. Diff the regenerated `sitemap.xml` against the baseline from Step 01.1 and confirm that every address that disappeared belongs to a document the split moved to `wear-dev-docs`, and that no address outside the Wear group changed. Never hand-edit either generated file.

**Why:**

Strategic §11.8 requires the sitemap and the document map to be regenerated from the registry and to match the actual set of published pages, and the canon forbids hand-editing a render target; the baseline diff is what turns "the generator ran" into evidence that only the intended addresses moved.

**Verification:**

- `scripts/document_registry/validate.ps1` exits 0.
- `scripts/document_registry/generate.ps1 -Check` exits 0.
- `Grep` - `WEAR_OS_STATUS` returns zero hits in `sitemap.xml`.
- `Grep` - `WEAR_OS_SMB_SETUP` matches in `sitemap.xml`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] Project compiles - not applicable: no source file is touched in this phase.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] If public API changed: not applicable - no Kotlin change.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The registry now states that the Wear user documentation group holds only watch-owner pages and that the developer documents are neither published nor indexed. Phases 02 and 03 write their pages under the `user-guides` glob, which this phase taught to claim `wear` as a product area, so a page added there is reachable by the registry query without another registry edit.

**Residual gap on strategic §2.4, deliberately not closed here.** The six developer documents still carry Jekyll front matter, so the static generator still builds them and they remain reachable by direct address - the registry stopped declaring them, which removes them from the sitemap and the document map, but does not remove them from the build. Closing that fully means either excluding them in the site configuration or stripping their front matter, and strategic §3.3 puts owner sign-off on exactly this act ("снятие публикации с внутренних трекеров"). Left for the owner rather than decided by the pipeline; strategic §11.6 - the indexable surface - is satisfied as written.

---

## Rollback Plan

Revert the phase commit. The two generated files are rebuilt from the registry by `generate.ps1`, and no Markdown document, no source file and no user-facing surface changed, so reverting restores the previous published set exactly.
