# Phase 04 - Docs & Catalog Cleanup

**Strategic spec:** [`../S0781_main-resource-type-filter-panel-collapse.md`](../S0781_main-resource-type-filter-panel-collapse.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-01
**Completed:** 2026-07-01

---

## Objective

Record the delivered capability, regenerate the class catalog for the new manager, and journal every code change. No FEATURES*.md / settings-manifest edits (see notes).

---

## Prerequisites

- [ ] Phases 01-03 ✅ Done; build green.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified (via tool) | n/a |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored) | n/a |
| `dev/CHANGELOG.md` | Modified (via tool) | n/a |

> **Not touched:** `docs/FEATURES*.md` (owned by `/skill-release`, populated from the ALL_FEATURES diff). `docs/settings/settings-manifest.json` (Rule 22 not triggered - `resourceTypeTabCollapsed` is internal UI-state, not a Settings-screen toggle).

---

## Steps

### Step 04.1 - Record the capability in ALL_FEATURES

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only capability record via the tool (do NOT hand-edit the JSONL):
> `pwsh -NoProfile -File scripts/all_features/add.ps1` with `spec = S0781` and a one-line summary such as "Long-press the main-window resource-type filter strip to collapse it into a colored label strip (tap to expand); state persists across restarts." Use the script's actual parameter names (run it with `-?` / inspect an existing record first). Standard flavor - do NOT pass `-NoLegal`.
> Then validate: `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - `S0781` matches in `docs/ALL_FEATURES.jsonl`.
- `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 04.2 - Regenerate the class catalog + set role/status

**Files:** `dev/CATALOG/app_v2.jsonl` (gitignored, local index)
**Depends on:** Step 04.1

**Prompt for developer:**

> Regenerate the catalog so the new manager is indexed, then fill its metadata:
> 1. `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` (scan + render in one process).
> 2. `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1` to set `role` + `status` for `MainResourceTabsCollapseManager` (use the script's params; role = main-window UI-state helper, status = active).

**Verification:**

- `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -ClassMatches "*MainResourceTabsCollapseManager*"` returns the class (catalog files are gitignored - do not verify with bare bash `rg`; use `query.ps1` or `Read`).

**Status:** `[x]` done

---

### Step 04.3 - Journal every code change

**Files:** `dev/CHANGELOG.md` (via tool)
**Depends on:** Step 04.2

**Prompt for developer:**

> Add dev-log entries for the ticket (one logical entry, batched across files via `close-and-log.ps1 -DevLogs`, never hand-edit `dev/CHANGELOG.md`). Cover: AppSettings + repository flag (P01), strip resources/layouts (P02), the new manager + wiring + test (P03). Target = `spec-dev`, description references S0781.

**Verification:**

- `Grep` - `S0781` matches in `dev/CHANGELOG.md`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 04.*` above is `[x] done`.
- [ ] `docs/ALL_FEATURES.jsonl` has an `S0781` record; `validate.ps1` green.
- [ ] `query.ps1` finds `MainResourceTabsCollapseManager`.
- [ ] `dev/CHANGELOG.md` covers all S0781 code changes.

---

## Handoff Notes to Next Phase

Final phase. After this, `/spec-dev` inserts the `Timber.d("S0781: ..")` debug tags at `collapse()`/`expand()`, builds once, sets status `BlockNeedUserTest`, and the pipeline runs the device-test gate. See INDEX.md Completion Gate.

---

## Rollback Plan

Docs/catalog only - revert the dev-log + ALL_FEATURES entries; catalog indexes are gitignored and regenerate from source.
