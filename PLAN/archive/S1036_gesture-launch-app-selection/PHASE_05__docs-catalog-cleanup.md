# Phase 05 - Docs, settings manifest and catalog cleanup

**Strategic spec:** [`../S1036_gesture-launch-app-selection.md`](../S1036_gesture-launch-app-selection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-08-10
**Completed:** 2026-08-10

---

## Objective

Regenerate every artifact the four preceding phases invalidated, and record the delivered capability in the developer inventory.

---

## Prerequisites

- [ ] Phases 01-04 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` | Regenerated | n/a |
| `docs/SETTINGS_REFERENCE.md` (+ `_RU`, `_UK`) | Regenerated | n/a |
| `docs/settings/settings-annotations.json` | Modified | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified | one record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | n/a |

> `docs/FEATURES*.md` is deliberately absent from this table. Strategic §8 defers the public wording to `/skill-release` from the `ALL_FEATURES` diff, and CLAUDE.md §11 makes those files `/skill-release`-owned.

---

## Steps

### Step 05.1 - Regenerate the settings manifest and reference

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE.md`, `docs/SETTINGS_REFERENCE_RU.md`, `docs/SETTINGS_REFERENCE_UK.md`, `docs/settings/settings-annotations.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/reindex-settings.ps1` to regenerate the settings manifest from the layout, re-render the three reference documents and assert the result. Add an annotation entry for each of the twelve new rows so the reference explains what the row does rather than only naming it. Never hand-edit the manifest or the rendered reference - they are generated targets.

**Why:**

The gesture dialog is a registered non-screen settings surface with scope id `gestures`, and CLAUDE.md Rule 22 requires the manifest, the reference and the annotations to be regenerated whenever a setting's presence changes - Phase 04 added twelve rows carrying `android:id`, which the scanner picks up.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- `Grep` - the twelve new row ids appear in `docs/settings/settings-manifest.json`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-10 - Verification 2\2 PASS. First `reindex-settings.ps1` run exited 3 and named the twelve unannotated keys, which is the gate doing its job; each got an EN/RU/UK annotation saying which zone and direction it belongs to, that it both chooses and clears, and that it is visible only while that direction launches an app. Second run exited 2 (drift regenerated, the expected outcome for a real settings change) and `assert-settings-doc-sync.ps1` then exits 0: catalog complete, manifest fresh, annotations covered (282 keys, 0 orphans), reference and HOW_TO in sync. The twelve ids resolve in `settings-manifest.json`. Note: writing the annotations through PowerShell's JSON round-trip re-sorted the whole file by key, so its diff is larger than twelve entries - no key was lost (268 -> 282, `comm` reports no missing key) and every pre-existing value is byte-identical in content.

---

### Step 05.2 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Add one record through `pwsh -NoProfile -File scripts/all_features/add.ps1` describing, in English, that an edge gesture can launch any installed app chosen per slot, with a reset and a fallback to opening FastMediaSorter. Set the record's flavors from what actually gates the capability, not from the strategic spec's prose: the gesture ships in `noLegal` unconditionally and in `standard` only when the edge-gesture overlay property is on. Validate with `scripts/all_features/validate.ps1`.

**Why:**

Strategic §8 marks this a new user-facing capability and names the `ALL_FEATURES` diff as the source `/skill-release` reads to write the public wording, so a missing record means the feature silently never reaches the release notes.

**Verification:**

- `Grep` - `S1036` appears in the `spec` field of exactly one `docs/ALL_FEATURES.jsonl` record.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-10 - Verification 2\2 PASS. One record added through `add.ps1`: `gestures.edge_gesture_launch_chosen_app`, area `Edge Gestures`, spec `S1036`, exactly one S1036 record in the file. `validate.ps1` exits 0 (686 records). Flavors recorded as `standard,noLegal`, matching every other `Edge Gestures` record: the capability's real gate is the injected overlay-controller set, which is non-empty on `noLegal` always and on `standard` when `fms.edgeGestureOverlay` is on - listing `noLegal` alone would have reported this one feature as narrower than the family it ships inside.

---

### Step 05.3 - Sync the class catalog and close the ticket mechanically

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 05.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket, then close through `pwsh -NoProfile -File scripts/post-change.ps1` naming the whole changed set with `-Files` and adding `-ScopeToFile`, with `-ChangeType Mixed`. Read the closure verdict: only a bare `post-change: PASS` counts as clean, and exit 2 means the gates did not run rather than that they passed.

**Why:**

CLAUDE.md §12 routes mechanical closure through the facade and requires one dev-log entry per logical change rather than per file, and the dirty-tree rule requires naming the whole changed set so the scoped gates judge exactly what this ticket touched.

**Verification:**

- `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<whole changed set>" -ScopeToFile -ChangeType Mixed -Target "spec-dev" -Description "S1036 gesture app launch"` exits 0 and prints `post-change: PASS`.
- `Grep` - `dev/CHANGELOG.md` carries an entry naming S1036.

**Status:** `[x]` done

**Step Log:**

- 2026-08-10 - Verification 2\2 PASS. `catalog_sync.ps1 -Module app_v2` ran once for the whole ticket (2763 records rendered). Closure over all eleven changed files with `-ScopeToFile -ChangeType Mixed`; `dev/CHANGELOG.md` carries the S1036 rows. Two things had to happen in order, and both are worth knowing next time: the ticket-log gate refused the run while the ticket was still `In Progress` - the three `Timber.d("S1036:` probes read as stale probes until the journal says `BlockNeedUserTest`, so the status flip comes first and the closure second. Then the first clean-status run ended `PASS WITH ADVISORIES (1)`: the changed set touches two registered documents, so `document-registry` withholds a bare PASS until they are acknowledged. Both were read - `settings-reference` (generated, published, en/ru/uk, re-rendered by `reindex-settings`, its `howto-path-vocab.json` sibling needs nothing because the new rows sit inside the already-registered `gestures` scope) and `feature-inventory` (one added record, `ALL_FEATURES.schema.json` unchanged, no new field) - and the re-run with `-RegistryAck "settings-reference,feature-inventory"` prints the bare `post-change: PASS`. Passing the ack on the first call would have saved a second dev-log row for the same set.

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- [x] `docs/ALL_FEATURES.jsonl` carries exactly one S1036 record.
- [x] Device verification target chosen per strategic §3.2: `noLegal debug` (or `standard` with `-Pfms.edgeGestureOverlay=on`). Written into the ticket's `BlockNeedUserTest` status note as the first instruction, because a tester who reaches for a plain `standard debug` build will find no edge gestures at all and read that as a regression.

### Phase-boundary audit (2026-08-10)

Not applicable beyond what already ran: `Files Touched` is generated documentation plus one inventory record - no code surface, so Layers 1-4 have nothing to inspect. The checks that do apply ran as gates: settings-doc-sync (catalog, manifest, annotations, reference, HOW_TO all green), `all_features/validate.ps1`, and the full closure.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. The ticket then needs on-device verification of the five observable criteria (choose, launch, reset, removed-app fallback, unavailability on builds without gestures), which is what moves it to `BlockNeedUserTest` rather than straight to `Verified`.

---

## Rollback Plan

Revert the phase commit and re-run `reindex-settings.ps1` - every file this phase touches is generated from sources the earlier phases own, so regeneration reproduces it exactly.
