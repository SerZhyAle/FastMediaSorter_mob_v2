# Phase 02 - Class B Conversion

**Strategic spec:** [`../S1693_findviewbyid-vs-viewbinding.md`](../S1693_findviewbyid-vs-viewbinding.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** Phase 03
**Steps done:** 3 / 3
**Started:** 2026-08-21
**Completed:** 2026-08-21

---

## Objective

Every confirmed class-B `findViewById` call uses its already-generated typed binding field; calls
whose field does not materialize stay as they are with a reason comment.

---

## Prerequisites

- [ ] Phase 01 is ✅ Done.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/DocumentStandaloneActivity.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/PhotoVideoStandaloneActivity.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/StandalonePlayerActivity.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/player/standalone/AudioStandaloneActivity.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GoogleAccountSettingsHelper.kt` | Modified | n/a |
| `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/SettingsActivity.kt` | Modified | n/a |

> Backups: every listed file over 500 LOC gets a timestamped copy per CLAUDE.md Rule 5 before
> editing (done 2026-08-21 for the five oversize files).

---

## Steps

### Step 02.1 - Convert the bottom-panels include lookups (4 player files)

**Files:** `DocumentStandaloneActivity.kt`, `PhotoVideoStandaloneActivity.kt`, `StandalonePlayerActivity.kt`, `AudioStandaloneActivity.kt` (paths above)
**Depends on:** - start of phase

**Prompt for developer:**

> Replace `binding.root.findViewById(R.id.bottomPanelsContainer / copyToPanel / moveToPanel /
> draw_overlay_toolbar_stub)` with the typed nested-binding access
> (`binding.bottomPanelsContainer.root`, `binding.bottomPanelsContainer.copyToPanel`, ..) per the
> AGP include-with-id rule. Keep the surrounding nullable semantics: where the old call was
> null-tolerant (`?.let`), the new access stays null-safe in exactly the same branch shape. A
> lookup whose typed field does not exist after `fk` stays `findViewById` with a one-line
> `// S1693: no generated field - <reason>` comment. `PhotoVideoStandaloneActivity:254`
> (`btnDocumentFullscreenExit`) is category A3b - do not touch it.

**Why:**

Strategic goal 1: these ids already carry compile-time-checked accessors; reaching around them via
the untyped root lookup is the exact defect class the ticket exists to remove.

**Verification:**

- `Grep` - `findViewById(R.id.bottomPanelsContainer` zero hits in the four files.
- `Grep` - `findViewById` count in each file did not grow.
- `.\a.ps1 fk` exit 0 (record command + exit code).

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - 10 calls converted (Document 3, PhotoVideo 4 incl. draw stub, Audio 1, Text 1 - research had missed Text, added in-scope, SettingsActivity titleRow 1 as nullable field). Survivors with S1693 reason comments: StandalonePlayerActivity x2 (S1549 trimmed-layout rebinding), GeneralSettingsFragment+GoogleAccountSettingsHelper x8 (card included twice, no unambiguous binding field). fk BUILD SUCCESSFUL 31s exit 0; gate ratcheted 362 -> 352

---

### Step 02.2 - Convert the Google-account card lookups

**Files:** `GeneralSettingsFragment.kt`, `GoogleAccountSettingsHelper.kt` (paths above)
**Depends on:** - independent of 02.1

**Prompt for developer:**

> `GeneralSettingsFragment:282`: use the fragment binding's `cardGoogleAccount` field instead of
> `view.findViewById`. `GoogleAccountSettingsHelper`: change `bind(cardView: View)` to accept the
> generated `CardGoogleAccountBinding` (the include's own binding type) and replace the 7 child
> `cardView.findViewById` calls with typed fields; update the single caller. If `fk` shows the
> nested field or binding type does not exist, leave that call with the reason comment instead.

**Why:**

Strategic goal 1 - same defect class, settings-screen instance; the helper receives the exact
bound subtree, so the typed form removes seven untyped lookups in one signature change.

**Verification:**

- `Grep` - `findViewById` zero hits in `GoogleAccountSettingsHelper.kt` (or each survivor carries
  an `// S1693:` reason).
- `.\a.ps1 fk` exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - 10 calls converted (Document 3, PhotoVideo 4 incl. draw stub, Audio 1, Text 1 - research had missed Text, added in-scope, SettingsActivity titleRow 1 as nullable field). Survivors with S1693 reason comments: StandalonePlayerActivity x2 (S1549 trimmed-layout rebinding), GeneralSettingsFragment+GoogleAccountSettingsHelper x8 (card included twice, no unambiguous binding field). fk BUILD SUCCESSFUL 31s exit 0; gate ratcheted 362 -> 352

---

### Step 02.3 - Resolve the `titleRow` candidate in `SettingsActivity`

**Files:** `SettingsActivity.kt` (path above)
**Depends on:** - independent

**Prompt for developer:**

> `SettingsActivity:472` `binding.root.findViewById<View>(R.id.titleRow)`: if the binding exposes
> a typed field for it, convert; otherwise leave with the `// S1693:` reason comment. Do NOT touch
> `:594-595` - those take a runtime `viewId` parameter (category A3).

**Why:**

Research flagged this call as a lower-confidence class-B candidate; the compiler is the arbiter
the spec names for exactly this situation.

**Verification:**

- `Grep` - line 472's lookup either uses a binding field or carries an `// S1693:` comment.
- `.\a.ps1 fk` exit 0.

**Status:** `[x]` done

**Step Log:**

- 2026-08-21 - 10 calls converted (Document 3, PhotoVideo 4 incl. draw stub, Audio 1, Text 1 - research had missed Text, added in-scope, SettingsActivity titleRow 1 as nullable field). Survivors with S1693 reason comments: StandalonePlayerActivity x2 (S1549 trimmed-layout rebinding), GeneralSettingsFragment+GoogleAccountSettingsHelper x8 (card included twice, no unambiguous binding field). fk BUILD SUCCESSFUL 31s exit 0; gate ratcheted 362 -> 352

---

## Phase Done Criteria

- [x] Every `Step 02.*` above is `[x] done`.
- [x] `.\a.ps1 fk` green after the full set.
- [x] `scripts/quality/assert-source-gates.ps1 -Only findviewbyid` shows the count dropped and the
      baseline ratcheted down.
- [x] Dev log entry added for the file set.
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Any call left unconverted (hypothesis refuted by the compiler) is listed in the Step Log with its
reason - Phase 03 copies that list into the spec's Last Audit block.

---

## Rollback Plan

Revert phase commit(s) - access-path change only, no behavior intended to change.
