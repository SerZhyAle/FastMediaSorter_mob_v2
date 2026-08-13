# Phase 04 - Teach the reference renderer the new sections

**Strategic spec:** [`../S1313_settings-manifest-blind-to-dialog-settings.md`](../S1313_settings-manifest-blind-to-dialog-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 03
**Blocks:** Phase 05
**Steps done:** 3 / 3
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Add the documentation-scope sections to the renderer's ordered whitelist with trilingual headings, note in each section where the settings live, and re-render the published reference pages.

---

## Prerequisites

- [ ] Phase 03 is ✅ Done and annotation coverage is green.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/docs/render-settings-reference.ps1` | Modified | ≤ 200 |
| `docs/SETTINGS_REFERENCE.md` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE_RU.md` | Modified (generated) | n/a |
| `docs/SETTINGS_REFERENCE_UK.md` | Modified (generated) | n/a |

---

## Steps

### Step 04.1 - Extend `$sectionOrder` and `$sectionLabel`

**Files:** `scripts/docs/render-settings-reference.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> `$sectionOrder` is a hard whitelist - a `sectionId` absent from it is silently dropped from every rendered page, which is the same class of blind spot this ticket fixes. Append the documentation-scope section ids from `SettingsDocScopeCatalog` after the existing nine, keeping the settings-screen sections first so the published page still opens with the main tabs. Add a matching `$sectionLabel` row per new id with `en`/`ru`/`uk` headings. Do not add the new ids to `$mediaSections` - they are not flavor-gated media sections and must not get an "Available in:" line.

**Verification:**

- `Grep` - `$sectionOrder` line in `scripts/docs/render-settings-reference.ps1` contains `'launcher'`.
- `Grep` - `$sectionOrder` line contains `'gestures'`, `'defaultApps'`, `'camera'`, `'translation'`
  (`'player'`/`'slideshow'` dropped - those surfaces were excluded, see Phase 01 "Deviation from plan").
- Value equality - every id present in `$sectionOrder` also has a `$sectionLabel` key, and every `$sectionLabel` entry defines non-empty `en`, `ru` and `uk`. Confirmed for all 14 ids (9 original + 5 new).
- `Grep` - `$mediaSections` line is unchanged and still lists exactly `'images','video','audio','documents'`.

**Status:** `[x] done`

---

### Step 04.2 - Emit a "where to find it" line per documentation-scope section

**Files:** `scripts/docs/render-settings-reference.ps1`
**Depends on:** Step 04.1

**Prompt for developer:**

> A reader who finds "Lock desktop" in the reference must be able to reach it. Under each documentation-scope section heading emit one localized italic line naming the path to the hosting surface, mirroring the existing `$availLead` pattern for media sections. Drive the text from a new trilingual lookup keyed by section id - do not hardcode a Russian or Ukrainian string inline. Where the owning surface has no settings-screen entry point (`hostKey` empty in Phase 01), the line must say the setting is reached from the media UI rather than naming a settings path that does not exist. Keep output deterministic: fixed order, LF newlines, UTF-8 no BOM, so the stage-4 byte-diff gate stays meaningful.

**Verification:**

- `Grep` - a new trilingual lookup hashtable with `en`/`ru`/`uk` keys for section paths matches once in the renderer (`$docScopePath`).
- `Grep` - no bare Cyrillic string literal appears outside a hashtable definition in the renderer. Confirmed.
- `Grep` - `$availLead` usage is unchanged and still guarded by `$sec -in $mediaSections`. Confirmed.

**Status:** `[x] done`

---

### Step 04.3 - Re-render the published reference pages

**Files:** `docs/SETTINGS_REFERENCE.md`, `docs/SETTINGS_REFERENCE_RU.md`, `docs/SETTINGS_REFERENCE_UK.md`
**Depends on:** Step 04.2

**Prompt for developer:**

> Re-render from the fresh manifest and annotations. These files are generated artifacts - never hand-edit them. The gitignored `docs/SETTINGS_REFERENCE_noLegal.md` is rewritten by the same call and is not committed.

```powershell
pwsh -NoProfile -File scripts/docs/render-settings-reference.ps1
```

**Verification:**

- `Grep` - the EN heading for the launcher section matches in `docs/SETTINGS_REFERENCE.md`. Confirmed (`## Launcher`).
- `Grep` - the RU heading for the launcher section matches in `docs/SETTINGS_REFERENCE_RU.md`. Confirmed (`## Лаунчер`).
- `Grep` - the UK heading for the launcher section matches in `docs/SETTINGS_REFERENCE_UK.md`. Confirmed (`## Лаунчер`).
- `Grep` - `docs/SETTINGS_REFERENCE.md` contains the EN title text of a `dialog_launcher_settings` row. Confirmed ("System launcher settings", 2 hits - the row and the "where to find it" line).
- `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1 -SkipManifestTest` exits 0. Confirmed.

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 04.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0.
- [x] `Grep` for `TODO(phase-04)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" (batched with Phase 06 closure entry).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The whole settings-doc chain is green end to end and the reference documents dialog-hosted settings. Nothing yet prevents the *next* dialog from re-opening the blind spot - Phase 05 adds that gate.

---

## Rollback Plan

Revert the renderer and re-run it to restore the previous pages. No data migration or user-facing app surface changed.
