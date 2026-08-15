# Phase 01 - Move Tooling

**Strategic spec:** [`../S0339_strings-thematic-split.md`](../S0339_strings-thematic-split.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ⬜ Not started
**Depends on:** none - foundation phase
**Blocks:** Phase 02, 03, 04
**Steps done:** 0 / 3
**Started:** -
**Completed:** -

---

## Objective

Extend `scripts/utils/set-android-string.ps1` so it treats all `strings*.xml` of a locale as one logical database: add a `move` action that relocates a key (in all three locales in lockstep) from its current file into a target thematic file, byte-preserving, with key-presence and target-creation guards.

---

## Prerequisites

- [ ] Working tree clean or on a feature branch.
- [ ] `scripts/utils/set-android-string.ps1` present (it is).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/set-android-string.ps1` | Modified | ≤ 520 |

---

## Steps

### Step 01.1 - Add `move` action accepting `-Key`/`-Prefix` and `-File`

**Files:** `scripts/utils/set-android-string.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add `move` to the `-Action` ValidateSet. Implement single-key move: locate the key in each locale's `strings*.xml` (reuse `Find-Key`/`Get-StringFiles`), excise its exact `<string ..>..</string>` line block from the source file (reuse the `remove` regex), and append it verbatim (same decoded body, re-escaped identically) into the target thematic file `-File` in the same locale. The move must run for EN/RU/UK in lockstep; if the key is missing in any locale, abort before writing anything. Create the target thematic file (with `<resources>` skeleton, matching BOM/EOL of the locale's `strings.xml`) if it does not exist. Support `-DryRun`.

**Verification:**

- `Grep` - `'move'` present in the `-Action` `ValidateSet` line.
- `Grep` - `Invoke-Move` (or a `'move'` switch arm) defined exactly once.
- Manual: `pwsh -NoProfile -File scripts/utils/set-android-string.ps1 -Action move -Key app_name -File strings_smoke.xml -DryRun` prints a planned 3-locale move, exit 0, writes nothing.

**Status:** `[ ]` not done

---

### Step 01.2 - Add `-Prefix` bulk move mode

**Files:** `scripts/utils/set-android-string.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Extend `move` to accept `-Prefix` instead of `-Key`: enumerate every key in the residual `strings.xml` whose name starts with the prefix and is NOT already in a non-residual thematic file, and move each through the single-key path into `-File`. Skip (do not abort) keys absent in a locale only when they are absent in all three; a key present in some locales but not others is a hard error reported per key. Print a per-key summary and a final moved-count. Honour `-DryRun`.

**Verification:**

- `Grep` - `\$Prefix` parameter declared in `param(..)`.
- Manual: `... -Action move -Prefix zzz_nonexistent_ -File strings_smoke.xml -DryRun` reports `0` candidates, exit 0.

**Status:** `[ ]` not done

---

### Step 01.3 - Add union-invariant guard helper

**Files:** `scripts/utils/set-android-string.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add an `audit` action (or `-Verify` switch) that prints, per locale, the sorted union of all `<string name>` keys across every `strings*.xml` and the total count. This is the before/after snapshot oracle used by Phase 04 to prove no key was lost or duplicated. Output must be diff-friendly (one key per line, locale-tagged) so two runs can be compared with a plain text diff.

**Verification:**

- Manual: `... -Action audit` prints three locale blocks with counts; EN count equals current total (3471 = 3405 strings.xml + 33 google_account + 21 link_auth + 4 resource_operations + 8 vr, i.e. all files) — record `expected | actual`.

**Status:** `[ ]` not done

---

## Phase Done Criteria

- [ ] Every `Step 01.*` is `[x] done`.
- [ ] Script parses: `pwsh -NoProfile -Command "& { . ./scripts/utils/set-android-string.ps1 -Action list | Out-Null }"` exit 0 (or `-Action list` runs clean).
- [ ] `Grep -n "Log\.d\("` - not applicable (PowerShell, no Timber).
- [ ] Dev log entry added for `set-android-string.ps1`.

---

## Handoff Notes to Next Phase

The `move` engine (single + prefix) and the `audit` snapshot oracle are the only primitives Phases 03–04 use. No strings have moved yet.

---

## Rollback Plan

Revert the single-file edit to `set-android-string.ps1` - no resource files touched in this phase.
