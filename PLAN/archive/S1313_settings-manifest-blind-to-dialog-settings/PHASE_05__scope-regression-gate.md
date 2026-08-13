# Phase 05 - Close the blind spot mechanically

**Strategic spec:** [`../S1313_settings-manifest-blind-to-dialog-settings.md`](../S1313_settings-manifest-blind-to-dialog-settings.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 04
**Blocks:** Phase 06
**Steps done:** 3 / 3
**Started:** 2026-08-01
**Completed:** 2026-08-01

---

## Objective

Replace the gate's `fragment_settings_*.xml` filename filter with a widget-based sweep, so any layout holding a settings row must be classified - indexed, documented, or explicitly excluded with a reason. This is the deliverable that stops the ticket from recurring.

---

## Prerequisites

- [ ] Phase 04 is ✅ Done and the full settings-doc chain is green.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/assert-settings-catalog-complete.ps1` | Modified | ≤ 140 |
| `docs/settings/settings-scope-exclusions.json` | New | ≤ 60 |

---

## Steps

### Step 05.1 - Declare the excluded surfaces and why

**Files:** `docs/settings/settings-scope-exclusions.json`
**Depends on:** - start of phase

**Prompt for developer:**

> Create a JSON object keyed by layout base name, each value an object with a `reason` string and a `category` of `entity-editor`, `onboarding`, `flavor-scoped` or `row-less-host`. Seed it with the surfaces the owner ruled out plus the mechanical exclusions: `fragment_settings_media_container` (`row-less-host`), `dialog_add_stream`, `dialog_scheduled_operation`, `dialog_filter_resource` (`entity-editor`), `page_welcome_enhanced`, `page_welcome_functionality`, `page_welcome_networks` (`onboarding`), `fragment_vr_settings_block` (`flavor-scoped` - lives in `app_v2/src/vr/res/layout/`, so its resource id does not resolve in the `standardDebug` variant the manifest test runs under). Every reason must state the fact that justifies the exclusion, not merely repeat the category.

**Verification:**

- `Glob` - `docs/settings/settings-scope-exclusions.json` exists.
- Value equality - the file parses as JSON and contains 10 keys, not the planned 8: `fragment_settings_media_container`
  turned out to need NO entry (the content-based sweep in Step 05.2 never discovers it - zero settings-row
  widget tags - so the old filename-driven "allowed exclusion" concept does not apply under the new
  mechanism), and `dialog_player_settings`/`dialog_playback_control`/`dialog_slideshow_settings` were
  added (Correction 2, two new categories: `session-scoped`, `unsupported-widget`). Net: 8 planned - 1
  (media_container) + 3 (the three moved-to-excluded surfaces) = 10.
- `Grep` - `fragment_vr_settings_block` matches once in that file.
- `Grep` - each of the 10 entries has a non-empty `reason` and a `category` from six allowed values now
  (the original four plus `session-scoped`/`unsupported-widget`).

**Status:** `[x] done`

---

### Step 05.2 - Sweep by widget, not by filename

**Files:** `scripts/quality/assert-settings-catalog-complete.ps1`
**Depends on:** Step 05.1

**Prompt for developer:**

> Rewrite the enumeration. Instead of `Get-ChildItem -Filter 'fragment_settings_*.xml'` over `app_v2/src/main/res/layout`, recurse every `res/layout*` directory under `app_v2/src` and select each layout whose text contains a settings-row widget tag: `SettingsToggleRow`, `SettingsDropdownRow`, `SettingsInputRow` or `SettingsSelectionRow`. Deduplicate by layout base name so a portrait/landscape pair counts once. Fail (exit 1) when a discovered layout is in neither `SettingsSearchLayoutCatalog.layoutResIds`, nor `SettingsDocScopeCatalog.surfaces`, nor `settings-scope-exclusions.json`, and name the three ways to resolve it in the failure output. Also fail when an exclusion entry no longer matches any layout on disk, so the list cannot rot. Keep the existing regex-over-source approach for reading the two Kotlin catalogs. Update the `.SYNOPSIS`/`.DESCRIPTION` header and the exit-code list to match the new behaviour, and follow CLAUDE.md Rule 7 for reachable exit codes: `Write-Error $msg -ErrorAction Continue` before any `exit N` where N is not 1.

**Verification:**

- `Grep` - `SettingsToggleRow`, `SettingsDropdownRow`, `SettingsInputRow`, `SettingsSelectionRow` each match in `scripts/quality/assert-settings-catalog-complete.ps1`. Confirmed (matched via a `<...` anchored tag pattern, not a bare mention, to avoid false-positiving on the widgets' own internal `view_settings_*.xml` layouts, which mention the class names only in comments).
- `Grep` - `fragment_settings_\*` filename filter no longer appears in that file. Confirmed (0 hits).
- `Grep` - `SettingsDocScopeCatalog` matches in that file. Confirmed.
- `Grep` - `settings-scope-exclusions.json` matches in that file. Confirmed.
- `pwsh -NoProfile -File scripts/quality/assert-settings-catalog-complete.ps1` exits 0 on the current tree. Confirmed: "25 layout(s) with settings rows, all classified (9 search-scope, 6 doc-scope, 10 excluded)."
- `pwsh -NoProfile -File scripts/quality/assert-exit-contract.ps1` exits 0. Confirmed PASS (one pre-existing, unrelated finding in `spec-next-session.ps1` already inside its baseline).

**Status:** `[x] done`

---

### Step 05.3 - Prove the gate actually catches a regression

**Files:** `scripts/quality/assert-settings-catalog-complete.ps1` (no edit expected - fault injection only)
**Depends on:** Step 05.2

**Prompt for developer:**

> A gate that has never gone red is not known to work. Temporarily remove one entry from `docs/settings/settings-scope-exclusions.json` (for example `dialog_add_stream`), re-run the gate, confirm it exits 1 and names that layout, then restore the file byte-for-byte. Record `expected: exit 1 naming dialog_add_stream | actual: <observed>` in the step notes. Restore before moving on - the working tree must end this step unchanged from the end of step 05.2.

**Verification:**

- Value equality - gate run with the entry removed exits 1 and its output contains `dialog_add_stream`.
  **Actual:** `expected: exit 1 naming dialog_add_stream | actual: exit 1, "settings catalog INCOMPLETE - 1 layout(s)... dialog_add_stream - resolve by ONE of: ..."` - matched.
- Value equality - gate run after restoring exits 0. **Actual:** confirmed, back to "25 layout(s)... 10 excluded."
- `Grep` - `dialog_add_stream` matches once again in `docs/settings/settings-scope-exclusions.json`. Confirmed (`grep -c '"category"'` = 10 entries total, restored).

**Status:** `[x] done`

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] `pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1` exits 0 (verified `-SkipManifestTest`, the gradle-manifest sub-stage was already verified fresh in Phase 03/04; full non-skip run also passed earlier in this session).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" (batched with Phase 06 closure entry).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Adding a settings row to any layout now forces a classification decision at gate time. The gate is reachable from `post-change.ps1` through `assert-settings-doc-sync.ps1` stage 1, so no new call site is needed.

---

## Rollback Plan

Revert the gate script and delete the exclusions file. No data migration or user-facing surface changed.
