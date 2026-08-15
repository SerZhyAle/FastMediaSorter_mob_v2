# Phase 05 - Docs & catalog cleanup

**Strategic spec:** [`../S0500_unify-buttons.md`](../S0500_unify-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-06-18
**Completed:** 2026-06-18

**Step Log:**

- 2026-06-18 - 05.1 string audit: no new keys created (literals repointed to existing keys), nothing to audit. 05.2 catalog_sync (scan+render) + 4-entry dev-log batch via close-and-log.ps1. 05.3 repo-wide invariants PASS: only widget_scheduled_tasks.xml has plain `<Button>` (RemoteViews); 0 MaterialComponents.Button parents in SettingsButton family; 0 SettingsButton.OutlinedM3/TextM3; standard debug BUILD SUCCESSFUL. Status -> BlockNeedUserTest (device verification per strategic §11.6).

---

## Objective

Finalise the change: regenerate the class catalog, record the dev-log entry, and run the strings audit. No `docs/FEATURES*` update (strategic §8: no user-visible feature).

---

## Prerequisites

- [ ] Phases 01-04 ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `dev/CHANGELOG.md` | Modified (via script) | n/a |
| `dev/CATALOG/app_v2.jsonl` + `.md` | Regenerated (gitignored index) | n/a |

> No layout edits in this phase - no `layout-land` parity concern.

---

## Steps

### Step 05.1 - String locale audit

**Files:** (none - validation)
**Depends on:** - start of phase

**Prompt for developer:**

> Run the locale audit for any string keys added in Phases 03-04. Skip only if no new keys were introduced.

**Verification:**

- `pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"` exits 0 for every new key prefix.

**Status:** `[x]` done

---

### Step 05.2 - Regenerate catalog + dev log

**Files:** `dev/CATALOG/app_v2.jsonl`, `dev/CHANGELOG.md`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run the catalog sync for `app_v2` and add one dev-log entry covering the button-unification change set.
>
> - `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2`
> - `.\scripts\add_to_dev_log.ps1 "app_v2/src/main/res/values/themes.xml" "spec-dev" "S0500: unify button styles + migrate plain Button to MaterialButton across layouts"`

**Verification:**

- `catalog_sync.ps1` exits 0.
- `Grep` - `dev/CHANGELOG.md` contains an entry mentioning button unification.

**Status:** `[x]` done

---

### Step 05.3 - Final repo-wide invariant check

**Files:** (none - validation)
**Depends on:** Step 05.2

**Prompt for developer:**

> Confirm the unification invariants hold repo-wide.

**Verification:**

- `Grep` (`-oE "<Button\b"`, `app_v2/src`, `*.xml`) - only `widget_scheduled_tasks.xml` matches (RemoteViews, intentional).
- `Grep` - zero `Widget.MaterialComponents.Button` parents in the `SettingsButton.*` family (`Calculator.*` styles remain MC - out of scope).
- `Grep` - zero `SettingsButton.OutlinedM3` / `SettingsButton.TextM3` repo-wide.
- `/build` -> `standard debug` PASS (final confirmation).

**Status:** `[x]` done

---

## Phase Done Criteria

- [ ] Every `Step 05.*` above is `[x] done`.
- [ ] Catalog regenerated; dev log entry present.
- [ ] Repo-wide invariants confirmed.

---

## Handoff Notes to Next Phase

Final phase - see INDEX.md Completion Gate. Next: `/spec-check S0500`. Device verification of settings, dialogs, welcome, destinations, and player screens is the remaining manual gate (strategic §11 criterion 6).

---

## Rollback Plan

Catalog regen and dev-log are non-code bookkeeping - no rollback needed. Code rollback handled per earlier phases.
