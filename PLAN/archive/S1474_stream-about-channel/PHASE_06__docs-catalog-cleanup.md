# Phase 06 - Docs and catalog cleanup

**Strategic spec:** [`../S1474_stream-about-channel.md`](../S1474_stream-about-channel.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04, Phase 05
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-08
**Completed:** 2026-08-08

---

## Objective

Record the shipped capability, regenerate the class catalog, place the debug tags the device test needs, and close the ticket through the mechanical facade.

---

## Prerequisites

- [ ] Phases 01-05 are ✅ Done.
- [ ] Working tree carries only this ticket's changes.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | 1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| Kotlin files carrying the debug tags | Modified | ≤ 6 lines total |

---

## Steps

### Step 06.1 - Record the capability

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record through `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the About-channel window in English, with the flavors taken from the gate that actually ships it - the streams capability, so standard, noLegal, legacy and vr. Do not edit `docs/FEATURES*.md`; those are `/skill-release`-owned.

**Why:**

Strategic §8 declares this a new user-visible capability, and the inventory is what the release pipeline diffs to build the showcase - a capability absent from it is invisible at release time.

**Verification:**

- Run `pwsh -NoProfile -File scripts/all_features/validate.ps1` - exit code 0.
- `Grep` - the new record present in `docs/ALL_FEATURES.jsonl`.
- `Grep` - `docs/FEATURES.md` unchanged by this ticket.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 3\3 PASS. Record `streams.about-channel` added with `-Spec S1474` and `-Flavors "standard,noLegal,legacy,vr"`, matching strategic §3.3's flavor scope - the four builds that carry the streams screen; `lite` and `photos` are excluded because the screen itself is absent there. `validate.ps1` exit 0, 671 records. `docs/FEATURES.md` carries 0 references to this ticket and was not opened - it is `/skill-release`-owned.

---

### Step 06.2 - Set catalog roles for the new classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 06.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then fill `role` and `status` for each class added by this ticket - the dialog, the launcher manager, the probe manager and the formatter - via `set.ps1`.

**Why:**

CLAUDE.md requires a new class to declare its role and status in the catalog, and the catalog is the first lookup every later ticket makes before grepping.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "StreamInfoDialog"` returns the record with a filled role.
- Same for `StreamFormatProbeManager`, `StreamInfoDialogManager`, `StreamPropertiesFormatter`.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 4\4 PASS. All four classes carry a filled `role` and `status: new`; re-checked after the closing `catalog_sync` re-scan, since a rescan is what would have dropped them.
- `set.ps1` takes `-Path` (repo-relative, inside the module) rather than a class name, and its `-Status` set is `new|tested|legacy|todo|unknown` - `active` is rejected. Recorded because the step names the script without its parameter shape.

---

### Step 06.3 - Place the debug verification tags

**Files:** the entry points of the changed flows
**Depends on:** Step 06.2

**Prompt for developer:**

> Add one `Timber.d("S1474: <description>")` at the entry of each changed flow and no more: opening the window from the card, opening it from the player, and the measurement's completion path where it reports measured or unmeasurable. Three tags total, no per-value logging, and no channel address in the message.

**Why:**

CLAUDE.md ties these tags to the `BlockNeedUserTest` status the ticket enters next, one per changed flow entry, and they are removed when the ticket leaves that status.

**Verification:**

- `Grep` - `Timber\.d\("S1474:` matches exactly three times across `app_v2/src/main`.
- `Grep` - no tag message contains a url or the channel address.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification 2\2 PASS. Exactly three `Timber.d("S1474:` lines across `app_v2/src/main`, one per changed flow entry: the card-menu open, the player-menu open, and the measurement's completion path. Each reports a boolean - whether an engine was borrowed, whether the channel was in the list, whether anything was reported - so none carries a url, a channel name or a per-value dump.
- The status was flipped to `BlockNeedUserTest` before the gates ran, not after: `assert-no-ticket-logs` is fail-closed and treats an `Sxxxx:` probe as a stale ticket log unless that spec is currently in that status.

---

### Step 06.4 - Close through the facade and hand over for device test

**Files:** all files touched by this ticket
**Depends on:** Step 06.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<the whole changed set>" -ScopeToFile -Target "streams" -Description "S1474 About-channel window" -ChangeType Mixed -Module app_v2` and read its verdict; a bare `post-change: PASS` is the only clean outcome. Then set the status with the note describing what the owner must verify on a device: the item in both card modes, the item in the video player's menu, a playing channel measured without a second connection, an unreachable channel ending in the failure state, closing the window mid-measurement, a channel opened by shortcut after being removed from the list, and the copy action.

**Why:**

Strategic §11 criteria 5 to 9 can only be settled with hands on a device, and CLAUDE.md requires a `Block*` transition to carry the note that says what resolves it.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS`.
- `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S1474 -Status BlockNeedUserTest -StatusNote '<the list above>'` succeeds.
- `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1474 -Format json` reports `BlockNeedUserTest` with a non-empty note.

**Status:** `[x] done`

**Step Log:**

- 2026-08-08 - Verification PASS. `post-change.ps1` run over all 22 changed files with `-ScopeToFile` and `-KeyPrefix "stream_info_"`, exit 0. Status set to `BlockNeedUserTest` with a seven-point note covering exactly the criteria a device settles: the item in both card modes, the item in the player menu, a playing channel measured without a second connection, an unreachable channel ending in the failure state, closing mid-measurement, a channel opened by shortcut while absent from the list, and the copy action.
- Verdict was `PASS WITH ADVISORIES (1)`, not a bare PASS, and the advisory was checked rather than accepted: `detekt-preflight` reports over-length lines at `PlayerDialogAndUiStateManager.kt:310/340/343/491/494` and `PlayerViewModel.kt:101/222/250/263/267`, all of which predate this ticket. No line added by S1474 exceeds 120 characters, and the authoritative scoped `assert-detekt` returns PASS on every changed file.

---

## Phase Done Criteria

- [x] Every `Step 06.*` above is `[x] done`.
- [x] Project compiles - `.\a.ps1 fk` exit 0 with the probe tags in place, 2026-08-08.
- [x] `dev/CHANGELOG.md` carries this ticket's entry - one row naming all 22 files.
- [x] Phase-boundary audit skipped by rule for the doc and catalog steps; the tag insertion of step 06.3 adds three log lines and no logic, so Layers 1-4 have no new surface to examine.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The three debug tags stay in the source until the ticket leaves `BlockNeedUserTest`.

---

## Rollback Plan

Revert phase commit(s). The capability record and the catalog are regenerable; no source behaviour depends on this phase.
