# Phase 05 - Docs and catalog cleanup

**Strategic spec:** [`../S1473_streams-list-grid-media-filter.md`](../S1473_streams-list-grid-media-filter.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Bring the registered documents, the class catalog and the capability inventory in line with the shipped change.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] Strategic §6 research items blocking this phase are Resolved.
- [x] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | - |
| `docs/ICON_LEGEND*.md` | Modified | - |
| `docs/HOW_TO*.md` or the streams guide the audit names | Modified | - |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |

> Regenerated indexes are gitignored and rebuilt, never hand-edited.

---

## Steps

### Step 05.1 - Update the icon legend for the two new controls

**Files:** `docs/ICON_LEGEND*.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Grep the icon legend for the streams screen section. Add the two media-kind trigger icons with their three states, and correct any entry that describes the removed single import icon. Leave every other row untouched. Skip with a recorded reason if the legend does not cover the streams command row at all.

**Why:**

The document registry lists the icon legend under the `ui` and `user-feature` triggers, and Phase 04 adds two user-facing icons whose meaning is not guessable from the glyph alone.

**Verification:**

- `Grep` - the streams section of the legend names both new icons, or the step records the skip reason.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 05.2 - Correct the documented import path

**Files:** the streams guide the grep in this step names
**Depends on:** Step 05.1

**Prompt for developer:**

> Grep `docs/` for prose describing the two-step import chooser on the streams screen. Rewrite each hit to the direct path - the overflow button, then the catalog or URL entry. Update the EN, RU and UK variants of every file touched, in the same edit.

**Why:**

Strategic §7 records that removing the intermediate chooser leaves the documented path pointing at a dialog that no longer exists, and the ship-together surfaces rule requires every locale of a changed surface to move in one edit.

**Verification:**

- `Grep` - no remaining `docs/` prose describes choosing between catalog and URL from a dialog.
- Each touched file's `_RU` and `_UK` counterparts carry the matching edit.

**Status:** `[x]` done

---

### Step 05.3 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add one record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the inline audio/video filter and the consolidated command menu on the streams screen, in English, with `spec` set to `S1473`. Take the flavor list from the gate rather than from memory.

**Why:**

Strategic §8 names a user-visible capability, and the feature inventory is the per-spec home for it - the public showcase is written from this inventory's diff by the release pipeline, never per spec.

**Verification:**

- `Grep` - `S1473` present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 05.4 - Regenerate the class catalog and close

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then set `role` and `status` on the two new manager classes via `dev/CATALOG/scripts/set.ps1`. Close the ticket through `scripts/post-change.ps1` with the whole changed set and `-ScopeToFile`.

**Why:**

Two new classes were added in Phases 02 and 04, and an unfilled role leaves them invisible to the catalog queries every later ticket starts from.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "StreamsCommandLabelManager"` returns one record with a filled role.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "StreamsMediaKindTriggerManager"` returns one record with a filled role.
- `post-change.ps1` prints `post-change: PASS` and exits 0.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `dev/CHANGELOG.md` has an entry covering the ticket's changed set.
- [x] `pwsh -NoProfile -File scripts/document_registry/generate.ps1 -Check` exits 0.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits").

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate.

---

## Rollback Plan

Documentation-only. Revert the phase commit; regenerated indexes rebuild from source on the next sync.

---

## Step Log

- 2026-08-08 - Step 05.1 done as a recorded no-op. docs/ICON_LEGEND*.md carries a GENERATED banner and is rendered by scripts/docs/render-icon-legend.ps1 from docs/icons/icon-inventory.json, so it must not be hand-edited. Both trigger glyphs reuse ic_video and ic_audio, which are already in the inventory, so no new icon enters it; assert-icon-inventory-sync reported PASS with the change in place - 84 vector svg(s), no orphans, legend fresh, locales in parity.
- 2026-08-08 - Step 05.2 done. docs/HOW_TO.md, _RU.md and _UK.md updated in the same change: the plus button and the Import-then-choose path are replaced by the three-dot menu with its named entries, and each locale's streams tips gained one line describing the inline audio/video trigger.
- 2026-08-08 - Step 05.3 done. Record streams.inline-media-kind-filter-and-command-menu added to docs/ALL_FEATURES.jsonl with spec S1473 and flavors standard, legacy, noLegal, vr - the set the existing Streams records carry, since the Streams screen is absent in lite and photos. validate.ps1 PASS, 670 records.
- 2026-08-08 - Step 05.4 done. catalog_sync ran inside post-change; role and status set on both new managers via set.ps1; query.ps1 returns one record each with a filled role.
- 2026-08-08 - docs/FEATURES*.md deliberately untouched - it is the curated public showcase and is written only by /skill-release from the ALL_FEATURES diff.
