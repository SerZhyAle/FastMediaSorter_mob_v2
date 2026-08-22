# Phase 01 - Style library

**Strategic spec:** [`../S1544_house-style-unenforced-where-it-applies.md`](../S1544_house-style-unenforced-where-it-applies.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 04
**Steps done:** 3 / 3
**Started:** 2026-08-14
**Completed:** 2026-08-14

---

## Objective

Introduce the single shared house-text-style normalizer with a data-driven rule set and an explicit area switch, and record the long-dash rule in the communication policy that already records the ellipsis rule.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done - none, this is the foundation phase.
- [ ] Strategic §6 research items blocking this phase are Resolved.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/quality/lib/house-text-style.ps1` | New | ≤ 220 |
| `docs/COMMUNICATION_POLICY.md` | Modified | ≤ 5 delta |
| `docs/COMMUNICATION_POLICY_RU.md` | Modified | ≤ 5 delta |
| `docs/COMMUNICATION_POLICY_UK.md` | Modified | ≤ 5 delta |

---

## Steps

### Step 01.1 - Write the normalizer library

**Files:** `scripts/quality/lib/house-text-style.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create the dot-sourced library `scripts/quality/lib/house-text-style.ps1`, modelled on the existing sibling `scripts/quality/lib/android-string-format.ps1`. Declare the rule set as data - one record per transformation carrying a name, a pattern and a replacement - covering three transformations: `...` and `…` to `..`, the long dashes `–` `—` `―` to a plain hyphen, and the Russian `ё` restoration. Merge the `ё` dictionaries of `scripts/utils/fix-yo.ps1` and `scripts/utils/fix-yo-letter.ps1` into that one data set and drop the dead self-mapping entries the second one carries (`'включает' = 'включает'`, `'выйдет' = 'выйдет'`, `'выдет' = 'выдет'`). Export `Get-HouseStyleRules`, plus `Convert-HouseStyleText -Text <string> -Area <Prose|ResourceValue> [-Rules <name[]>]` returning both the converted text and the list of rule names that fired, so a caller can report what changed without diffing.

**Why:**

Strategic ADR-3 requires the rule set to live in exactly one place, because the five existing fixers each carry their own partial copy and that is precisely why none of them knows about the long dash.

**Verification:**

- `Glob` - `scripts/quality/lib/house-text-style.ps1` exists.
- `Grep` - `function Convert-HouseStyleText` matches exactly once.
- `Grep` - `function Get-HouseStyleRules` matches exactly once.
- `Grep` - `[–—―]` present in the file (the long-dash rule is declared).
- `Grep` - `'включает'` returns zero hits (dead self-mapping entries not carried over).
- Run `pwsh -NoProfile -Command ". ./scripts/quality/lib/house-text-style.ps1; (Convert-HouseStyleText -Text 'a - b' -Area ResourceValue).Text"` - prints `a - b` with a plain hyphen.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - house-text-style.ps1 landed at 203 lines: three data-declared rules, Prose and ResourceValue areas, merged yo dictionary minus self-mappings and five ambiguous entries. harness at evidence/test-house-text-style.ps1 all PASS against the final path.

---

### Step 01.2 - Make the area switch skip what the canon excludes

**Files:** `scripts/quality/lib/house-text-style.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> In the same library, make `-Area Prose` strip fenced code blocks and inline backtick spans from consideration so their contents are returned untouched, and make `-Area ResourceValue` operate on the element value only, leaving attributes, key names and XML comments untouched. Give `-Area ResourceValue` one further exclusion: a value that is entirely a format placeholder, a URL or a file path is returned unchanged, because a literal `...` inside a path segment is not prose.

**Why:**

The canon excludes code from the style scope, so a normalizer that rewrote a fenced block or a URL path would introduce a defect while claiming to fix one.

**Verification:**

- `Grep` - fenced-block handling present in the Prose branch.
- A prose input whose only `...` sits inside a backtick span comes back unchanged.
- A resource value that is exactly `https://x/.../y` comes back unchanged.
- A prose input whose `...` sits in plain text comes back as `..`.

> Drive the three checks above from `evidence/test-house-text-style.ps1`, not from an inline `-Command` string: the inputs contain backticks and quotes that a nested command line mangles. That harness is the durable record of this phase - 28 cases, run it with `-LibPath scripts/quality/lib/house-text-style.ps1`.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - house-text-style.ps1 landed at 203 lines: three data-declared rules, Prose and ResourceValue areas, merged yo dictionary minus self-mappings and five ambiguous entries. harness at evidence/test-house-text-style.ps1 all PASS against the final path.

---

### Step 01.3 - Record the long-dash rule in the communication policy

**Files:** `docs/COMMUNICATION_POLICY.md`, `docs/COMMUNICATION_POLICY_RU.md`, `docs/COMMUNICATION_POLICY_UK.md`
**Depends on:** - start of phase (documentation only; consumes nothing from 01.1, corrected 2026-08-14)

**Prompt for developer:**

> In section 5 "Localization Rules" of all three communication-policy files, add one bullet next to the existing ellipsis bullet stating that a plain hyphen is used in all locales and the long dashes `–` `—` `―` never are. Match the wording and position of the neighbouring ellipsis bullet in each language. Check the addition against `docs/COMMUNICATION_POLICY.md` §2 and §6 before writing.

**Why:**

The policy already records the ellipsis rule "in all locales" but is silent on the dash, so the rule this ticket starts enforcing would be enforced without ever being written down for a human reader.

**Verification:**

- `Grep` - each of the three files contains a section 5 bullet naming the long dashes.
- `Grep` - the pre-existing ellipsis bullet is still present in each of the three files.
- Strings pass COMMUNICATION_POLICY §6 checklist.

**Status:** `[x]` done

**Step Log:**

- 2026-08-14 - Added the plain-hyphen bullet to section 5 of all three COMMUNICATION_POLICY files; verified dash bullet present and pre-existing ellipsis bullet intact in each.

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] No build required - this phase touches no compiled source.
- [x] `Grep` for `TODO(phase-01)` returns zero hits.
- [x] Dev log entry added for every file in "Files Touched" via `.\scripts\add_to_dev_log.ps1`.
- [x] `scripts/quality/assert-exit-contract.ps1` passes for the new library (CLAUDE.md Rule 7).
- [x] Phase-boundary audit run - no unresolved P0/P1 findings.

---

## Handoff Notes to Next Phase

The rule set exists in exactly one file and is addressable by name. Every later phase calls `Convert-HouseStyleText` and never re-declares a pattern.

---

## Rollback Plan

Delete the new library and revert the three policy files - no consumer exists yet at the end of this phase.
