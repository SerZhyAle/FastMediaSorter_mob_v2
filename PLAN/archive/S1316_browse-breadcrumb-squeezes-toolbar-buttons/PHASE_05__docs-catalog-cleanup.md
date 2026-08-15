# Phase 05 - Docs & Catalog Cleanup

**Strategic spec:** [`../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md`](../S1316_browse-breadcrumb-squeezes-toolbar-buttons.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02, Phase 03, Phase 04
**Blocks:** none - final phase
**Steps done:** 3 / 3
**Started:** 2026-07-31
**Completed:** 2026-07-31

---

## Objective

Record the delivered capability, refresh the class catalog after one class was added and one deleted, and close the change through the mechanical gates.

---

## Prerequisites

- [x] All phases in "Depends on" are ✅ Done.
- [x] The S1316 probe from Step 02.4 is still present - the ticket goes to `BlockNeedUserTest` after this phase.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `docs/ALL_FEATURES.jsonl` | Modified | +1 |
| `dev/CATALOG/app_v2.jsonl` | Regenerated | - |
| `dev/CHANGELOG.md` | Modified (via script) | - |

> `dev/CATALOG/app_v2.jsonl` and `.md` are gitignored local indexes - regenerate, never commit.

---

## Steps

### Step 05.1 - Record the capability in `ALL_FEATURES`

**Files:** `docs/ALL_FEATURES.jsonl`
**Depends on:** - start of phase

**Prompt for developer:**

> Add one EN-only record through `pwsh -NoProfile -File scripts/all_features/add.ps1` describing the shipped capability: the Browse top bar shows the current path as a single fixed-width button whose menu lists every path segment and jumps to the tapped level. Do not hand-edit the JSONL and do not touch `docs/FEATURES*.md` - `/skill-release` owns the showcase. The feature is present in every flavor: the change lives entirely in `src/main` and is not gated by any `BuildConfig` field, so record all shipping flavors rather than guessing a subset.
> Validate with `pwsh -NoProfile -File scripts/all_features/validate.ps1`.

**Verification:**

- `Grep` - a record containing `path` and `Browse` was appended as the last line of `docs/ALL_FEATURES.jsonl`.
- `Script` - `scripts/all_features/validate.ps1` exits 0.

**Status:** `[x]` done

---

### Step 05.2 - Regenerate the class catalog

**Files:** `dev/CATALOG/app_v2.jsonl`
**Depends on:** Step 05.1

**Prompt for developer:**

> Run `pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2` once for the whole ticket. Then set role and status for the new class with `pwsh -NoProfile -File dev/CATALOG/scripts/set.ps1` for `BrowsePathMenuManager`.

**Verification:**

- `Script` - `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*BreadcrumbView*"` reports `0 records matched`.
- `Script` - `pwsh -NoProfile -File dev/CATALOG/scripts/query.ps1 -Module app_v2 -ClassMatches "*BrowsePathMenuManager*"` reports `1 records matched` with a non-empty `role`.

**Status:** `[x]` done

---

### Step 05.3 - Flip the status, then run mechanical closure

**Files:** `dev/CHANGELOG.md` (via script)
**Depends on:** Step 05.2

**Prompt for developer:**

> **Order is load-bearing - the status flip comes first.** `scripts/post-change.ps1` runs `scripts/quality/assert-no-ticket-logs.ps1 -Gate -Quiet` for `-ChangeType Kotlin|Mixed` (post-change.ps1 line 313). That gate treats a `Timber.d("Sxxxx:` probe as allowed only while its ticket is `BlockNeedUserTest` and exits 1 otherwise, so running the facade while S1316 is still `In Progress` fails on the Step 02.4 probe.
>
> 1. `pwsh -NoProfile -File scripts/spec_catalog/update.ps1 -Id S1316 -Status BlockNeedUserTest -StatusNote '<what the owner must check on device>'` - the note must name portrait, landscape and a `w600dp` target, three-plus levels deep, and the expectation that every bar command stays on the bar.
> 2. `pwsh -NoProfile -File scripts/post-change.ps1 -File "app_v2/src/main/java/com/sza/fastmediasorter/ui/browse/managers/BrowseManagerInitializer.kt" -Target "S1316" -Description "Browse path button replaces the breadcrumb ribbon" -ChangeType Mixed -Module app_v2 -ScopeToFile`.
> 3. `pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -ChangedFiles <every file touched in Phases 01-04> -Gate` - `-ScopeToFile` scopes the facade's detekt run to one file only, so the remaining touched files need their own pass.
>
> No `strings.xml` was edited (the button reuses `@string/current_path`), so the string-parity audit does not apply. No setting changed, so Rule 22 settings-doc regeneration does not apply. `post-change.ps1` releases `CODE.LOCK`.
> Finally record the document-registry outcome: `ui-communication` and `icon-legend` were both queried and are unchanged - no user-visible string was added or reworded, and the Browse top bar is outside the icon legend's five surfaces (`player-command`, `program-nav`, `send-to`, `settings-header`, `settings-row`).

**Verification:**

- `Script` - `pwsh -NoProfile -File scripts/spec_catalog/select.ps1 -Id S1316 -Format json` reports `"status":"BlockNeedUserTest"` with a non-empty `statusNote`.
- `Script` - `scripts/post-change.ps1` exits 0, and its `ticket-log-audit` step is reported as passed.
- `Script` - `scripts/quality/assert-detekt.ps1 -ChangedFiles <all touched files> -Gate` prints a PASS verdict line (read the printed verdict, not `$?` - the script exits 0 without `-Gate`).
- `Grep` - `dev/CHANGELOG.md` contains a new entry naming `S1316`.
- `Script` - `pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1 -Gate` exits 0, its summary line reading `expected: 0 | actual: 0  (allowed BlockNeedUserTest probes: 1)`.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 05.*` above is `[x] done`.
- [x] Project compiles - run `/build` (do not invoke gradle directly).
- [x] `Grep` for `TODO(phase-05)` returns zero hits.
- [x] `Grep` - `Timber.d("S1316:` still matches exactly once (the probe must survive into `BlockNeedUserTest`).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings (CLAUDE.md §13 / `docs/CODE_AUDIT_PROTOCOL.md`).

---

## Handoff Notes to Next Phase

Final phase - see [`INDEX.md`](INDEX.md) Completion Gate. The ticket is left at `BlockNeedUserTest` by Step 05.3 with the Step 02.4 probe still in the tree; `/spec-check` removes the probe when it flips the ticket to `Verified`.

---

## Rollback Plan

Revert the phase commit; the catalog indexes are gitignored and regenerate from source at any time.
