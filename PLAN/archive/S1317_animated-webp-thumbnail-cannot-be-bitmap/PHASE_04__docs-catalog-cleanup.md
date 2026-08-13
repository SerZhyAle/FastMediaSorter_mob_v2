# Phase 04 - Docs, catalog, capability record

**Strategic spec:** [`../S1317_animated-webp-thumbnail-cannot-be-bitmap.md`](../S1317_animated-webp-thumbnail-cannot-be-bitmap.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** 🚧 In Progress
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 2 / 3
**Started:** 2026-08-01
**Completed:** -

---

## Objective

Regenerate the class catalog, record the delivered capability, and close the dev log for every file
this ticket touched.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a - generated |
| `docs/ALL_FEATURES.jsonl` | Modified | +1 record |
| `dev/CHANGELOG.md` | Modified | via script only |

---

## Steps

### Step 04.1 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Phase 01 changed the constructor signature of both animated decoders and added a private resource
> type. Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the ticket. These
> indexes are gitignored - regenerate, never commit.

**Verification:**

- Command exit code is 0.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*AnimatedImage*"` returns at least one record.

**Status:** `[x]` done - `catalog_sync.ps1 -Module app_v2 -Force` exit 0, 7 `*AnimatedImage*` records returned.

---

### Step 04.2 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Add one EN-only record through `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the
> shipped capability: animated WebP and APNG files render a still first-frame thumbnail in the browse
> list, launcher folder previews, and the file-info cover, instead of falling back to a broken-file
> placeholder. Read the flavor reach off the real gate rather than copying a sibling record: the
> decoders live in `src/main` behind `AnimatedImageSupportUtils.isAnimatedImageDecodeSupported()`,
> which is an API-28 test and not a flavor test, so every flavor ships this. Never hand-edit
> `docs/FEATURES*.md` - that file is `/skill-release`-owned.

**Verification:**

- Command exit code is 0.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - the new record's id appears exactly once in `docs/ALL_FEATURES.jsonl`.

**Status:** `[x]` done - `browse.animated-webp-apng-still-thumbnail` added, `validate.ps1` PASS (627 records), `S1317` appears once.

---

### Step 04.3 - Close the dev log for the ticket

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Batch one dev-log entry per logical change through
> `pwsh -NoProfile -File scripts/close-and-log.ps1 -DevLogs` covering the six touched source files
> plus the tactical plan. One entry per logical change, not per file. Never hand-edit
> `dev/CHANGELOG.md`.

**Verification:**

- Command exit code is 0.
- `Grep` - `S1317` appears in `dev/CHANGELOG.md` at least once.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/FEATURES.md` / `_RU.md` / `_UK.md` untouched - this ticket is a defect fix with no strategic §8 FEATURES mandate.
- [ ] Settings docs untouched - no setting was added, moved, renamed, or changed in behaviour, so CLAUDE.md Rule 22 does not fire.
- [ ] Document registry unchanged - the `architecture`, `user-guides`, and `product-complexity`
      records were read and none describes decoder-level Glide behaviour or a user-visible workflow
      this ticket alters.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate.

Device acceptance is owned by the transition into `BlockNeedUserTest`, which `/spec-dev` performs
after this phase: insert the two probe lines listed in INDEX, build once, then flip status with a
`-StatusNote` describing what to check. What the user must verify: an animated `.webp` on an SMB/FTP
share shows a real still thumbnail in the browse list instead of the extension placeholder, opening
it full-screen still animates, and logcat contains no `Unable to convert`.

---

## Rollback Plan

Revert phase commit(s) - documentation and generated indexes only, no runtime behaviour.
