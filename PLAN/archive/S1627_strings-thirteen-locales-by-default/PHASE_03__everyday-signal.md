# Phase 03 - Everyday signal

**Strategic spec:** [`../S1627_strings-thirteen-locales-by-default.md`](../S1627_strings-thirteen-locales-by-default.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01
**Blocks:** none
**Steps done:** 2 / 2
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Make an incomplete new key visible the day it is written - named at the authoring call and counted at ticket closure - without blocking either.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/set-android-string.ps1` | Modified | ≤ 40 added |
| `scripts/post-change.ps1` | Modified | ≤ 30 added |

---

## Steps

### Step 03.1 - Name the missing locales at the authoring call

**Files:** `scripts/utils/set-android-string.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> After a successful `add`, print the best-effort locales the call did not supply, and a ready-to-paste `-Translations @{ .. }` fragment listing exactly those tags. Read the locale set through `locale-set.ps1` as the rest of the script already does. Keep the exit code at 0 and the action successful: the owner ruled that only the pre-release check refuses. Say nothing extra when every declared locale was supplied.

**Why:**

Strategic §5.1 makes the authoring point a hint rather than an obligation, and §4 identifies the silent skip as the exact place the gap opens - the tool already accepts the ten optional locales and says nothing when they are absent.

**Verification:**

- Run `set-android-string.ps1 -Action add -Key s1627_probe_key -En "Probe" -Ru "Проба" -Uk "Проба" -DryRun` - output names ten missing locales and prints a `-Translations` fragment.
- Run the same call with `-Translations @{ de='Probe'; it='Prova' }` - output names eight, not ten.
- Both runs exit 0.
- `Grep` - no hard-coded locale tag list is introduced: the added block references `Get-SupportedLocales` or `$optionalLocales`.

**Status:** `[x]` done - 2026-08-14. Bare `add` names ten missing locales; the same call with `-Translations @{ 'de'=..; 'it'=.. }` names eight. Both exit 0, and the block derives its list from `$optionalLocales`.

Two corrections the verification itself forced. The printed hashtable keys are quoted, because `zh-Hans` is not a bare hashtable key. And the fragment is printed as a call-operator invocation rather than a `pwsh -File` one, because `-File` cannot pass a hashtable at all - it arrives as the literal string `System.Collections.Hashtable` and the call fails. An unquoted, uncallable fragment would have been a trap rather than a hint.

---

### Step 03.2 - Count new lexemes at ticket closure

**Files:** `scripts/post-change.ps1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Extend the existing `strings-audit` gate: when the changed set contains a strings resource file, also run `list-new-lexemes.ps1 -Quiet` and print the count plus up to ten key names. Report it as an advisory - a non-empty list must not fail the closure, since the owner placed the refusal at the pre-release stage only. Keep the gate's current behaviour and exit code untouched when the list is empty.

**Why:**

Strategic §2.3 asks for the incomplete key to be a named count rather than silence, so the pre-release batch is never a surprise; §7 names the growth of an unnoticed gap as the failure mode that produced the 1887-key backlog in the first place.

**Verification:**

- Seed one throwaway key, run `post-change.ps1 -File <the strings file> -ChangeType Xml -ScopeToFile` - the output names the seeded key and the closure verdict is `PASS WITH ADVISORIES`, exit code 0.
- Remove the key, re-run - verdict is a bare `PASS`, exit code 0.
- `Grep` - `list-new-lexemes` matches inside the `strings-audit` gate block of `scripts/post-change.ps1`.

**Status:** `[x]` done - 2026-08-14. Seeded probe: `[new-lexeme-count] SKIP - advisory`, verdict `PASS WITH ADVISORIES (1)`, exit 0. After removal: `[new-lexeme-count] PASS`, bare `PASS`, exit 0.

Written as its own `new-lexeme-count` advisory step beside `strings-audit` rather than inside it, since `Invoke-AdvisoryStep` already carries the non-blocking contract this step needs and `strings-audit` is a FATAL `Invoke-Gate`. It runs without `-Quiet`, so the report names every key rather than the first ten - there is no truncation logic to add, and on a real ticket the list is a handful.

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] Project compiles - not applicable; run `.\a.ps1 fg` and record its exit code.
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entry added for every file in "Files Touched" via `scripts/post-change.ps1`.
- [ ] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

Both signals read the same producer from Phase 01, so "new lexeme" has one definition across authoring, closure and release.

---

## Rollback Plan

Revert the two added blocks. Both are additive and neither changes an exit code, so removal restores prior behaviour exactly.
