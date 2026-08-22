# Phase 01 - Seeder merge and flavor source sets

**Strategic spec:** [`../S1420_locale-bulk-translation-remaining-tranches.md`](../S1420_locale-bulk-translation-remaining-tranches.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** none - foundation phase
**Blocks:** Phase 02, Phase 03, Phase 06, Phase 07, Phase 08
**Steps done:** 4 / 4
**Started:** 2026-08-09
**Completed:** 2026-08-09

---

## Objective

Teach `seed-locale-tranche.ps1` to add a tranche to a locale file that already holds one, and to reach resource roots outside `src/main`. No translations are written in this phase.

---

## Prerequisites

- [ ] All phases in "Depends on" are ✅ Done.
- [ ] Strategic §6 research items blocking this phase are Resolved - §6.1 is Resolved; §6.2 blocks Phase 08 only.
- [ ] Working tree is clean or on a feature branch.

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/utils/seed-locale-tranche.ps1` | Modified | ≤ 320 |

> The file is 225 lines - under the 500-line backup threshold, so no backup sub-step is required.

---

## Steps

### Step 01.1 - Add `-Merge` so a second tranche does not erase the first

**Files:** `scripts/utils/seed-locale-tranche.ps1`
**Depends on:** - start of phase

**Prompt for developer:**

> Add a `[switch]$Merge` parameter. When it is set and the target locale file already exists, parse that file with the same `(?s)<(string|plurals|string-array)\s+name="([^"]+)"([^>]*)>(.*?)</\1>` regex the seeder already uses, and treat the parsed entries as a base layer under the supplied map. A key present in both takes the supplied translation; a key present only in the existing file is re-emitted verbatim.
> Entries that come from the existing file bypass the placeholder check and are never added to `$rejected` - they are already shipped, and dropping one because a later validation tightened would silently delete a live translation. Validate only entries the supplied map contributes.
> Emission still walks the source `values/` file in its own order, so the merged output keeps source order rather than appending the new tranche at the end.
> `-KeyPrefix` must scope only what the supplied map may contribute, not what survives from the existing file. Under `-Merge` an existing entry outside the prefix is still emitted; otherwise seeding `strings.xml` one prefix at a time would delete every prefix seeded before it, which is the exact failure this switch exists to prevent.
> Without `-Merge` the current whole-file overwrite behaviour stays exactly as it is - existing callers must not change meaning.

**Why:**

Strategic §3 records the measurement that makes this step the precondition for the whole ticket: an empty map on `strings_setup.xml` for `de` plans `written 0` over a file that currently holds 95 keys, so loading the remainder into any already-seeded file would erase the S1190 tranche.

**Verification:**

- `Grep` - `\[switch\]\$Merge` matches once in the `param(` block.
- Run `pwsh -NoProfile -File scripts/utils/seed-locale-tranche.ps1 -SourceFile strings_setup.xml -Locale de -Merge -DryRun` with no map; expected `written 95`, actual must equal expected. Without `-Merge` the same call still reports `written 0`.
- `Grep` - the existing-entry path does not call `Get-FormatSignature`; zero hits for `Get-FormatSignature` inside the merge base-layer block.
- Prefix interaction: run `-SourceFile strings_settings.xml -Locale de -Merge -KeyPrefix zzz_nonexistent_ -DryRun`; expected `written 247` (every existing entry survives a prefix that matches nothing), actual must equal expected.

**Status:** `[x]` done

---

### Step 01.2 - Add `-SourceSet` so flavor resource roots are reachable

**Files:** `scripts/utils/seed-locale-tranche.ps1`
**Depends on:** Step 01.1

**Prompt for developer:**

> Replace the hardcoded `$resDir = Join-Path $repoRoot "$Module/src/main/res"` with a `[string]$SourceSet = 'main'` parameter feeding `"$Module/src/$SourceSet/res"`. Reject a source set whose `res/values` directory does not exist with exit 1 and a message naming the path tried, so a typo fails loudly instead of writing into a directory Gradle never reads.

**Why:**

Strategic §2 sets covering the `vr` and `noLegal` sets as a goal and strategic §3 records that the seeder's resource root is pinned to `src/main/res`, so without this parameter goal §2.2 has no tooling at all.

**Verification:**

- `Grep` - `src/\$SourceSet/res` present; zero remaining hits for `src/main/res` in the script.
- Run `-SourceSet vr -SourceFile strings.xml -DumpSource`; expected 56 keys in the dump, actual must equal expected.
- Run `-SourceSet nope -SourceFile strings.xml -DumpSource`; expected exit 1, actual must equal expected.

**Status:** `[x]` done

---

### Step 01.3 - Update the header contract for both parameters

**Files:** `scripts/utils/seed-locale-tranche.ps1`
**Depends on:** Step 01.2

**Prompt for developer:**

> Add `.PARAMETER Merge` and `.PARAMETER SourceSet` blocks, extend `.DESCRIPTION` with the merge layering rule, and add one `.EXAMPLE` showing a second tranche into an already-seeded file. Re-check the `.OUTPUTS` exit-code list against what the script now returns and add the new exit-1 case from step 01.2.

**Why:**

CLAUDE.md Rule 7 (S1070) requires a script header to list the exit codes it actually returns, and step 01.2 adds a new exit-1 case that the current header does not mention.

**Verification:**

- `Grep` - `.PARAMETER Merge` and `.PARAMETER SourceSet` each match once.
- `Grep` - `.OUTPUTS` block names exit codes 0, 1 and 3.
- Run `pwsh -NoProfile -File scripts/utils/help.ps1 -Name seed-locale-tranche.ps1`; expected both new parameters listed, actual must equal expected.

**Status:** `[x]` done

---

### Step 01.4 - Prove the round trip on a real locale file

**Files:** `scripts/utils/seed-locale-tranche.ps1`
**Depends on:** Step 01.3

**Prompt for developer:**

> Run a non-destructive round trip against `values-de/strings_setup.xml`: dump the source, build a one-key map for a key that is not in the locale file yet, seed with `-Merge`, and confirm the file now holds 96 keys with the original 95 byte-identical to before. Restore the file afterwards if the probe key is not part of Phase 02's tranche.

**Why:**

Strategic §7 names the half-translated screen as the top risk and this phase's whole purpose is to make merging safe, so the merge path needs proof on a real file before six phases of translation rely on it.

**Verification:**

- Key count in `app_v2/src/main/res/values-de/strings_setup.xml` after the probe: expected 96, actual must equal expected.
- Seeder exit code for the probe run: expected 0, actual must equal expected.
- After restore, key count is back to expected 95.

**Status:** `[x]` done

---

## Phase Done Criteria

- [x] Every `Step 01.*` above is `[x] done`.
- [x] `-Merge` and `-SourceSet` both exercised against a real file with the expected counts recorded.
- [x] No `values-*` file changed by this phase - it edits one script only.

---

## Outcome - 2026-08-09

Measured, not asserted:

- `-Merge` dry-run with an empty map on `values-de/strings_setup.xml`: `written 95` - expected 95.
- Same call without `-Merge`: `written 0` - expected 0, so the pre-existing overwrite contract is unchanged.
- `-Merge -KeyPrefix zzz_nonexistent_` on `strings_settings.xml`: `eligible 0 | written 247` - every already-shipped entry survived a prefix matching nothing, which is the predicate that makes seeding `strings.xml` prefix by prefix safe.
- `-SourceSet vr -DumpSource`: 56 keys - expected 56. `-SourceSet nope`: exit 1 - expected 1.
- `help.ps1` lists both new parameters.

Step 01.4's round trip was satisfied by Phase 02's real run rather than a throwaway probe: seeding all ten locales preserved the 95 pre-existing entries byte-identical and added 74, which is a stronger proof on more data than the planned single-key probe. A rejected-translation fallback was added beyond the written plan - a rejected key now re-emits the entry the locale already had instead of dropping it, so exit 3 never leaves the file worse than it found it.
