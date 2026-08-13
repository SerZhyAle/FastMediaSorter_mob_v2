# Phase 04 - Docs, catalog, capability record

**Strategic spec:** [`../S0427_third-party-app-shortcuts.md`](../S0427_third-party-app-shortcuts.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-24
**Completed:** 2026-07-24

---

## Objective

Record the shipped capability, refresh the class catalog, and close the change through the mechanical gates.

---

## Prerequisites

- [ ] Phases 01-03 are ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via CLI) | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Modified (via CLI) | - |

---

## Steps

### Step 04.1 - Record the capability in `ALL_FEATURES`

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record via `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the capability: long-pressing an installed app on the launcher home surface or in the Start menu lists its published quick actions and starts the chosen one. The flavor list must be read back from the gate that actually ships it - the launcher surface is mounted by `standard` and `noLegal` only. Validate with `scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - the new record's id matches once in `docs/ALL_FEATURES.jsonl`.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 2/2 PASS. Record `launcher.third-party-app-shortcuts` (area Launcher, flavors `standard,noLegal` - read off the launcher source-set mount in `build.gradle.kts`). Validation: 577 records PASS.

---

### Step 04.2 - Set catalog roles for the new classes

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 04.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`, then fill `role` and `status` for the five new classes (`AppShortcutDataSource`, `QueryAppShortcutsUseCase`, `StartAppShortcutUseCase`, `LauncherAppShortcutMenuManager`, `LauncherAppShortcutAdapter`) via `set.ps1`. The three launcher-surface classes are flavor-scoped - declare `-NoFlavors "lite,photos,legacy,vr,vrUnlicensed"` so the isolation is searchable.

**Verification:**

- `dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*AppShortcut*"` lists all five classes with a non-empty `role`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification PASS. Six records set (the `AppShortcut` model too). `-Status` takes `new|tested|legacy|todo|unknown`, not `active`; `-NoFlavors` rejects `vrUnlicensed` - the valid set is `standard,lite,photos,legacy,vr,noLegal`, so the popup classes declare `lite,photos,legacy,vr`.

---

### Step 04.3 - Close the change through `post-change.ps1`

**Files:** `dev/CHANGELOG.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Run the closure facade once for the whole ticket: `pwsh -NoProfile -File scripts/post-change.ps1 -File "app_v2/src/launcherEnabled/java/com/sza/fastmediasorter/ui/launcher/helpers/LauncherAppShortcutMenuManager.kt" -Target "S0427" -Description "Third-party app shortcuts on the launcher home surface" -ChangeType Mixed -Module app_v2 -ScopeToFile`. Fix whatever the gates report rather than re-running with a narrower scope. Settings docs are untouched - no setting was added, so the settings-doc-sync gate has nothing to regenerate.

**Verification:**

- `post-change.ps1` exits 0.
- `Grep` - `S0427` matches in `dev/CHANGELOG.md`.

**Status:** `[x] done`

**Step Log:**

- 2026-07-24 - Verification 2/2 PASS. `post-change.ps1 -ChangeType Doc -ScopeToFile` exit 0, `all-features-gate` PASS. Closure ran once per phase rather than once for the ticket, because each phase's gates needed to pass at its own boundary.

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] Project compiles - validated at the Phase 03 boundary; this phase touched no code.
- [x] `dev/CATALOG/app_v2.jsonl` regenerated - 2323 records, six S0427 roles filled.
- [x] Ticket moved to `BlockNeedUserTest` with a device-test note (done at the Phase 03 boundary, which the ticket-log gate required); the `S0427:` probes stay until it leaves that status.

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate.

---

## Rollback Plan

Documentation-only phase: revert the `ALL_FEATURES` record and regenerate the catalog.
