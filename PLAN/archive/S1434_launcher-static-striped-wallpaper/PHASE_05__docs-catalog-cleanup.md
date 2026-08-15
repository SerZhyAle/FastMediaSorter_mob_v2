# Phase 05 - Docs, settings manifest, catalog and inventory

**Strategic spec:** [`../S1434_launcher-static-striped-wallpaper.md`](../S1434_launcher-static-striped-wallpaper.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 0 / 4
**Started:** -
**Completed:** -

---

## Objective

Regenerate the settings manifest and reference, record the new capability in the feature inventory, refresh the class catalog and close the ticket through the post-change facade.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/settings/settings-manifest.json` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE.md` + locale mirrors | Modified (generated) | n/a |
| `docs/settings/settings-annotations.json` | Modified | n/a |
| `docs/ALL_FEATURES.jsonl` | Modified (via script) | +1 record |
| `dev/CATALOG/app_v2.jsonl` | Regenerated (gitignored) | n/a |

---

## Steps

### Step 05.1 - Regenerate the settings manifest and reference

**Files:** `docs/settings/settings-manifest.json`, `docs/SETTINGS_REFERENCE*.md`
**Depends on:** - start of phase

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/quality/reindex-settings.ps1` and commit whatever it regenerates. Exit 0 means the docs were already fresh; exit 2 means drift was regenerated and the new files are the ones to keep.

**Why:**

Strategic §3.2 records that the wallpaper row is already registered in the settings manifest and reference, so changing what the setting offers is a behaviour change that CLAUDE.md Rule 22 requires the generated documentation to reflect.

**Verification:**

- `pwsh -NoProfile -File scripts/quality/reindex-settings.ps1` exits 0 or 2.
- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 05.2 - Update the wallpaper setting's annotation

**Files:** `docs/settings/settings-annotations.json`
**Depends on:** Step 05.1

**Prompt for developer:**

> Find the annotation record for the launcher desktop-wallpaper setting and extend its description so it names all four options rather than three. Change nothing else in the file.

**Why:**

CLAUDE.md Rule 22 requires the annotation to be updated alongside the manifest whenever a setting's behaviour changes, and an annotation that still lists three options describes a setting that no longer exists.

**Verification:**

- `Grep` - the launcher wallpaper record in `settings-annotations.json` mentions the frozen/still option.
- `pwsh -NoProfile -File scripts/docs/check-settings-annotations.ps1` exits 0.

**Status:** `[ ]` not done

---

### Step 05.3 - Record the capability in the feature inventory

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** Step 05.2

**Prompt for developer:**

> Add one record through `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the still branded wallpaper that draws a new frame each time the launcher comes back to the foreground, with `spec` set to S1434 and the flavors limited to `standard` and `noLegal`. Validate with `scripts/all_features/validate.ps1`. Do not touch `docs/FEATURES*.md`.

**Why:**

Strategic §8 states that the record in `docs/ALL_FEATURES.jsonl` is mandatory for this ticket and names `standard` and `noLegal` as the flavors that ship it.

**Verification:**

- `Grep` - `S1434` matches at least once in `docs/ALL_FEATURES.jsonl`.
- `pwsh -NoProfile -File scripts/all_features/validate.ps1` exits 0.
- `Grep` - `S1434` returns zero hits in `docs/FEATURES.md`.

**Status:** `[ ]` not done

---

### Step 05.4 - Sync the catalog and close through the facade

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 05.3

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the ticket, then close through `pwsh -NoProfile -File scripts/post-change.ps1 -Files "<every file this ticket changed>" -Target "S1434" -Description "Launcher wallpaper: still branded frame, re-rolled on return" -ChangeType Mixed -ScopeToFile`. Read the verdict line: only a bare `post-change: PASS` is clean.

**Why:**

CLAUDE.md section 12 routes mechanical closure through the facade so the changelog, the catalog and every scoped gate are judged against the whole changed set at once.

**Verification:**

- `post-change.ps1` exits 0 and prints `post-change: PASS`.
- `Grep` - `S1434` matches in `dev/CHANGELOG.md`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Project compiles - run `/build` (do not invoke gradle directly).
- [ ] `Grep` for `TODO(phase-05)` returns zero hits.
- [ ] Dev log entry added for every file this ticket changed via `.\scripts\add_to_dev_log.ps1` or the facade.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md` "Phase-boundary audits"; see `/spec-dev` "Phase-boundary audit" step).

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate.

---

## Rollback Plan

Revert phase commit(s) - documentation and inventory only; the generated files can be re-rendered from source at any time.
