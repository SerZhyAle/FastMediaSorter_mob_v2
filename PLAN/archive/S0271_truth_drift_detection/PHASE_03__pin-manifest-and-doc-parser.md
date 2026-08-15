# Phase 03 - Pin Manifest and Document Parser

**Strategic spec:** [`../S0271_truth_drift_detection.md`](../S0271_truth_drift_detection.md)
**Tactical index:** [`INDEX.md`](INDEX.md)
**Status:** ✅ Done
**Depends on:** Phase 01, Phase 02
**Blocks:** Phase 04, Phase 05
**Steps done:** 4 / 4
**Started:** 2026-05-20
**Completed:** 2026-05-20

---

## Objective

Author two coupled artefacts: the declarative pin manifest (`pins.psd1`) per D-1 schema, and the document parser (`DocParser.ps1`) that resolves each manifest entry against each declared document and returns structured mention records.

---

## Prerequisites

- [ ] Phase 01 ✅ Done (D-1 schema, D-2 required-flag, D-4 policy fields all locked).
- [ ] Phase 02 ✅ Done (`Get-GradlePins` returns the canonical hashtable).

---

## Files Touched

| File | New / Modified | Line budget |
|------|:--------------:|------------:|
| `scripts/doc-drift/pins.psd1` | New | ≤ 450 |
| `scripts/doc-drift/DocParser.ps1` | New | ≤ 220 |

---

## Steps

### Step 03.1 - Author `pins.psd1` skeleton + Class-1 entries

**Files:** `scripts/doc-drift/pins.psd1`
**Depends on:** - start of phase

**Prompt for developer:**

> Create `scripts/doc-drift/pins.psd1` as a PowerShell data file (returns a single hashtable on `Import-PowerShellDataFile`). Top of file: header comment naming the contract source (`PLAN/S0271_truth_drift_detection/DECISIONS.md D-1`). Schema for each entry under root key `Pins = @(...)`:
>
> ```powershell
> @{
>     name        = 'agp'
>     gradleKey   = 'agp'                    # key into Get-GradlePins output
>     docs        = @{
>         'docs/TECH_STACK.md'        = @{ required = $false; matcher = $null }
>         'dev/TECH_REQUIREMENTS.md'  = @{ required = $true;  matcher = 'AGP\s+\(Android Gradle Plugin\)[^\d]*(?<v>[\d\.]+)' }
>         'CLAUDE.md'                 = @{ required = $false; matcher = $null }
>     }
>     policy      = 'allMustMatch'           # | 'firstOnly'
>     exclude     = @()                      # array of regex patterns; matched spans are skipped before mention extraction
> }
> ```
>
> Populate Class-1 entries: `gradle.wrapper`, `agp`, `kotlin`, `ksp`, `hilt-plugin`, `compose-plugin`, `navigation-safe-args`, `chaquopy`. Set `required = $true` for `dev/TECH_REQUIREMENTS.md` on all eight; `required = $false` for `docs/TECH_STACK.md` on all eight; `required = $true` for `CLAUDE.md` only on `kotlin` (per D-2 - CLAUDE.md headline pins). The matcher regex captures the version into a named group `v`. When `matcher = $null` and `required = $false`, the entry is informational and never produces MISSING.

**Verification:**

- `Glob` - `scripts/doc-drift/pins.psd1` exists.
- Run `pwsh -NoProfile -Command 'Import-PowerShellDataFile ./scripts/doc-drift/pins.psd1 | ForEach-Object { $_.Pins.Count }'` - expected: `>= 8` | actual: capture value.
- `Grep` - all eight Class-1 names present as `name = '<pin>'` lines: `gradle.wrapper`, `agp`, `kotlin`, `ksp`, `hilt-plugin`, `compose-plugin`, `navigation-safe-args`, `chaquopy`.
- `Grep` - `required = \$true` appears at least 9 times (8 for TECH_REQUIREMENTS + 1 for CLAUDE.md kotlin).

**Status:** `[x] done`

---

### Step 03.2 - Add Class-2 (SDK) and Class-3 (library) entries

**Files:** `scripts/doc-drift/pins.psd1`
**Depends on:** Step 03.1

**Prompt for developer:**

> Extend `pins.psd1` with Class-2 SDK pins (`compile-sdk`, `target-sdk`, `min-sdk.standard`, `min-sdk.lite`, `min-sdk.photos`, `min-sdk.legacy`, `ndk-version`, `jvm-target`) and Class-3 library pins. For Class-3, cover every library pin currently declared with a version in `dev/TECH_REQUIREMENTS.md` §4 (4.1..4.17) and §5: `lib.com.google.dagger:hilt-android`, `lib.androidx.room:room-runtime`, `lib.com.github.bumptech.glide:glide`, `lib.androidx.media3:media3-exoplayer`, `lib.androidx.core:core-ktx`, `lib.androidx.appcompat:appcompat`, `lib.com.google.android.material:material`, `lib.com.github.mwiede:jsch`, `lib.com.hierynomus:smbj`, `lib.commons-net:commons-net`, `lib.com.squareup.okhttp3:okhttp`, `lib.com.squareup.retrofit2:retrofit`, plus any library §4 documents with an explicit version pin. Use the same schema as Step 03.1. Required-flag policy per D-2:
>
> - `dev/TECH_REQUIREMENTS.md`: `required = $true` for every Class-2 and Class-3 entry.
> - `docs/TECH_STACK.md`: `required = $true` only for the three pins it explicitly enumerates (`play-services-cast-framework`, `mediarouter`, `nanohttpd`); `required = $false` for everything else (D-2 default for the short cheat-sheet).
> - `CLAUDE.md`: `required = $true` only for the headline pins it explicitly mentions: `kotlin`, `lib.androidx.room:room-runtime` (declared as "Room v6"), `lib.com.github.bumptech.glide:glide` (declared as "Glide 4.15.1"), `lib.androidx.media3:media3-exoplayer` (declared as "Media3 1.2.1"), `lib.com.google.dagger:hilt-android` (declared as "Hilt"), `compile-sdk`, `min-sdk.standard`, `min-sdk.legacy`; `required = $false` for everything else.
>
> For pins that may appear twice inside `dev/TECH_REQUIREMENTS.md` (notably `hilt-android` - present both in §4.5 inventory and §11 history), set `policy = 'allMustMatch'` and use `exclude = @('§11.*Version History')` to skip the history span. Document this exclude convention in the manifest header.

**Verification:**

- `Grep` - all eight Class-2 names present (`compile-sdk`, `target-sdk`, four `min-sdk.*`, `ndk-version`, `jvm-target`).
- `Grep` - `lib\.com\.google\.dagger:hilt-android` present.
- `Grep` - `lib\.androidx\.room:room-runtime` present.
- `Grep` - `exclude\s*=` present at least once (multi-mention guard in place).
- Run `pwsh -NoProfile -Command 'Import-PowerShellDataFile ./scripts/doc-drift/pins.psd1 | ForEach-Object { $_.Pins.Count }'` - expected: `>= 25` (8 Class-1 + 8 Class-2 + ≥9 library entries) | actual: capture value.

**Status:** `[x] done`

---

### Step 03.3 - Author `DocParser.ps1` skeleton + reader

**Files:** `scripts/doc-drift/DocParser.ps1`
**Depends on:** Step 03.2

**Prompt for developer:**

> Create `scripts/doc-drift/DocParser.ps1`. Public function `Get-DocMentions` accepts `-Manifest <hashtable>` (the imported `pins.psd1` hashtable) and `-RepoRoot <string>` (defaults to current). Returns an array of `[pscustomobject]` records, one per (pin, doc) pair:
>
> ```powershell
> [pscustomobject]@{
>     Pin       = 'agp'
>     DocPath   = 'dev/TECH_REQUIREMENTS.md'
>     Required  = $true
>     Mentions  = @('9.2.0')   # array - may be empty, may be multiple
>     Policy    = 'allMustMatch'
> }
> ```
>
> Top of file: comment block citing `DECISIONS.md D-1, D-2, D-4`. Same `# No external module dependencies. PowerShell 7+.` note. Reader sub-function `Read-DocText` loads each document once into memory and caches by absolute path so re-querying the same doc for multiple pins does not re-read the file. Missing document file → throw with the offending pin and doc path in the message.

**Verification:**

- `Glob` - `scripts/doc-drift/DocParser.ps1` exists.
- `Grep` - `function Get-DocMentions` matches exactly once.
- `Grep` - `Read-DocText` (the cache helper) present.
- `Grep` - `Pin\s*=` and `Mentions\s*=` both present in the pscustomobject schema.

**Status:** `[x] done`

---

### Step 03.4 - Implement matcher application + policy enforcement

**Files:** `scripts/doc-drift/DocParser.ps1`
**Depends on:** Step 03.3

**Prompt for developer:**

> Implement the body of `Get-DocMentions`. For each manifest entry, for each `(doc, spec)` pair in its `docs` hashtable:
>
> 1. Load doc text via `Read-DocText`.
> 2. Apply each pattern in the entry's `exclude` array using `[regex]::Replace($text, $pattern, '')` (case-insensitive, single-line) to strip excluded spans before matching.
> 3. If `spec.matcher` is `$null`: skip the doc entirely (no mention possible; classified later as SKIP, not MISSING, by the comparator regardless of `required`).
> 4. Otherwise: run `[regex]::Matches($filteredText, $spec.matcher, 'IgnoreCase, Multiline')` and collect each named group `v` capture.
> 5. Apply `policy`: `firstOnly` truncates the mentions list to the first match; `allMustMatch` keeps all matches (the comparator decides what to do with multiple values).
> 6. Emit one record per (pin, doc) with `Mentions` (possibly empty).
>
> Reject silently swallowing parse errors - if a regex compile fails, throw with the pin name and the offending pattern.

**Verification:**

- `Grep` - `\[regex\]::Matches` present (matcher application).
- `Grep` - `firstOnly` and `allMustMatch` both present (policy enforcement branches).
- `Grep` - `exclude` referenced in code (not just in manifest schema comment).
- Run `pwsh -NoProfile -Command '. ./scripts/doc-drift/GradleParser.ps1; . ./scripts/doc-drift/DocParser.ps1; $m = Import-PowerShellDataFile ./scripts/doc-drift/pins.psd1; $r = Get-DocMentions -Manifest $m -RepoRoot (Get-Location); ($r | Where-Object Pin -eq ''agp'' | Where-Object DocPath -eq ''dev/TECH_REQUIREMENTS.md'').Mentions[0]'` - expected: `9.2.0` (the documented but drifted value from strategic §4) | actual: capture value.
- Run the same command targeting pin `kotlin`, doc `CLAUDE.md` - expected: `1.9+` or `1.9` (range marker per strategic §4) | actual: capture value.

**Status:** `[x] done`

---

## Phase Done Criteria

- [ ] Every `Step 03.*` above is `[x] done`.
- [ ] `pins.psd1` imports cleanly: `pwsh -NoProfile -Command 'Import-PowerShellDataFile ./scripts/doc-drift/pins.psd1 | Out-Null; $LASTEXITCODE'` returns `0`.
- [ ] `DocParser.ps1` dot-sources cleanly (no syntax error, no side effects).
- [ ] `Grep` for `TODO(phase-03)` returns zero hits.
- [ ] Dev log entries added for `pins.psd1` and `DocParser.ps1`.

---

## Handoff Notes to Next Phase

`Get-DocMentions` and `Get-GradlePins` together cover input extraction. Phase 04 is pure comparison + output formatting; it does not read any source files directly.

---

## Rollback Plan

Delete `scripts/doc-drift/pins.psd1` and `scripts/doc-drift/DocParser.ps1`. No data migration. Comparator (Phase 04) will refuse to run without them, which is the intended failure mode.
