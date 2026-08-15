# Phase 07 - Documentation, inventory and catalog cleanup

**Strategic spec:** [`../S1401_launcher-all-apps-screen.md`](../S1401_launcher-all-apps-screen.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** all phases
**Blocks:** none - final phase
**Steps done:** 4 / 4
**Started:** 2026-08-07
**Completed:** 2026-08-07

---

## Objective

Record the delivered capability, refresh the generated indexes and close every documentation obligation the strategic §3.2 documentation constraint names.

---

## Prerequisites

- [ ] Phases 01-06 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified via `scripts/all_features/add.ps1` | - |
| `docs/ICON_LEGEND.md` (and its translations, if the icon is newly user-visible) | Modified | - |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated - gitignored, never committed | - |
| `dev/CHANGELOG.md` | Appended via `scripts/add_to_dev_log.ps1` | - |

> `docs/FEATURES.md` and its translations are NOT touched here. They are populated only by `/skill-release` from the `ALL_FEATURES` diff (CLAUDE.md section 11).

---

## Steps

### Step 07.1 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one record through `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the full-screen all-apps list with search, five sort orders and the long-press action menu. Write it in English. Take the flavor list from the build gate rather than from memory - this capability ships wherever `SUPPORT_LAUNCHER` is on. Then run `scripts/all_features/validate.ps1`.

**Why:**

Strategic §8 declares this a new user-facing capability and §3.2 requires it to be registered in the inventory; the inventory is what `/skill-release` later diffs to write the public showcase, so a capability missing here is a capability that never reaches the release notes.

**Verification:**

- `Grep` - the new record present in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 07.2 - Update the icon legend if the button icon is newly user-visible

**Files:** `docs/ICON_LEGEND.md` and its translations
**Depends on:** Step 07.1

**Prompt for developer:**

> Check whether the grid icon now sitting on the taskbar is already described in the icon legend from its earlier use in the Start menu. If it is, update the description to name its new location; if the legend has no entry, add one in every locale the legend ships in. If the legend documents no launcher icons at all, record that and change nothing.

**Why:**

The document registry lists the icon legend under the `user-feature` trigger, and strategic §3.2 names it as a surface this change touches - an icon-only control with no legend entry is exactly the case the legend exists to cover.

**Verification:**

- `Grep` - the icon's legend entry names the taskbar, or a note recording "legend documents no launcher icons" exists under `temp/S1401/`.
- `pwsh -NoProfile -File scripts/document_registry/validate.ps1` exits 0 if any registered document changed.

**Status:** `[x]` done

---

### Step 07.3 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CATALOG/app_v2.md`
**Depends on:** Step 07.2

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once, then fill `role` and `status` for every class this ticket introduced through `dev/CATALOG/scripts/set.ps1`. Classes living only under `src/launcherEnabled/` declare their absence from the other flavors with `-NoFlavors "lite,photos,legacy,vr"`. These index files are gitignored - regenerate them, never commit them.

**Why:**

The catalog is the project's first lookup for any Kotlin class, so a class it does not know is a class the next ticket will not find; the flavor hint is what makes launcher-only placement searchable instead of only physically enforced by the source set.

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*AllApps*"` returns the new classes.
- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "InstalledApp*"` returns the cache classes with a filled role.

**Status:** `[x]` done

---

### Step 07.4 - Run mechanical closure

**Files:** all files this ticket changed
**Depends on:** Step 07.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<every changed file>" -Target "launcher" -Description "S1401 full-screen all-apps list with cache, search, sort and action menu" -ChangeType Mixed -ScopeToFile -Module app_v2`. Name the whole changed set, not one file - a verdict covers exactly what was passed. Read the closing line: only a bare `post-change: PASS` is clean; `PASS WITH ADVISORIES (n)` names each advisory and each one must be read. Exit code 2 means the gates could not run, which is not the same answer as a failure.

**Why:**

CLAUDE.md section 12 makes this facade the mechanical closure for the whole ticket, and this spec touches strings, layouts, Kotlin and a manifest at once - which is exactly the case where the narrow per-file gates miss something. Scoping to the changed set is what stops another ticket's in-flight work from blocking this close.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS` or `PASS WITH ADVISORIES` with every advisory read and answered.
- `Grep -rn "S1401:" app_v2/src --include=*.kt` matches only `Timber.d` probe lines, and only while the ticket sits in `BlockNeedUserTest`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 07.*` above is `[x] done`.
- [ ] `post-change.ps1` returned a clean verdict for the whole changed set.
- [ ] `docs/ALL_FEATURES.jsonl` carries the capability and validates.
- [ ] `dev/CHANGELOG.md` has an entry for every logical change of this ticket.
- [ ] `dev/CATALOG/app_v2.jsonl` regenerated and not committed.

---

## Step Log

- 2026-08-07 - Step 07.1 done. Record `launcher.all-apps-screen` added. Flavors taken from the build gate, not from memory: `SUPPORT_LAUNCHER` is `true` in exactly two `productFlavors` blocks, `standard` (line 338) and `noLegal` (line 413), and `false` in the default. Verification: record present; `validate.ps1` PASS, 659 records.
- 2026-08-07 - Step 07.2 done, third branch - recorded, changed nothing. The legend documents no launcher icons at all: the inventory has five surfaces (`player-command`, `program-nav`, `send-to`, `settings-header`, `settings-row`) and zero launcher entries, so the removed Start-menu row was never in it either. `ic_view_grid` does appear, but as the app-launch panel's icon ("Quick-access panel"); rewriting that row to mention the taskbar would make the panel's own entry wrong. Adding the launcher would mean a new surface covering the whole bar, which is its own piece of work. Reasoning written to `temp/S1401/07_2-icon-legend-decision.md`.
- 2026-08-07 - Step 07.3 done. `catalog_sync.ps1 -Module app_v2` scanned 2073 files into 2538 records; role and status filled for the six classes this ticket added, with `-NoFlavors "lite,photos,legacy,vr"` on the four that live only under `src/launcherEnabled/`. Verification: `query.ps1 -ClassMatches "*AllApps*"` returns all four new all-apps classes.
- 2026-08-07 - Step 07.4 done. Probe tags were inserted before the final build, so one build validated implementation and tags together (`.\a.ps1 dq` exit 0, `.\a.ps1 fkn` exit 0). The first closure run FAILED the ticket-log gate, correctly: the four `Timber.d("S1401: ..")` probes are only allowed while the ticket sits in `BlockNeedUserTest`, and it was still `In Progress`. Flipped the status first, re-ran the gate (`expected: 0 | actual: 0`), then re-ran the closure.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate. The ticket moves to `BlockNeedUserTest` with the `Timber.d("S1401: ..")` probes in place, and those probes are removed when `/spec-check` flips it to `Verified`.

---

## Rollback Plan

Nothing to roll back in source. A wrong inventory record is corrected in place; the catalog indexes are regenerated from the tree at any time.
