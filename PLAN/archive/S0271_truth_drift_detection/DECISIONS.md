# S0271 - Locked Decisions

Strategic spec: [`../S0271_truth_drift_detection.md`](../S0271_truth_drift_detection.md)
Owning skill: `/spec-tech` for the contracts, `/spec-dev` for the implementation that consumes them.

Five locked decisions. Each section closes one of the four open Research items in strategic §6 (D-1..D-4), plus D-5 freezes the output grammar so tests and downstream tooling can pattern-match on it.

---

## D-1: Manifest Format = Variant A (external PSD1)

Resolves strategic §6.3.

External PowerShell data file (`scripts/doc-drift/pins.psd1`) co-located with the checker. Variant B (inline document markers) and Variant C (hybrid name-matching) rejected.

Why Variant A wins:

- PSD1 is native to PowerShell 7. Loaded via `Import-PowerShellDataFile` from the standard distribution. Zero external module dependency, respecting strategic §3.2 ("no modules outside standard distribution").
- Adding a new pin = one PSD1 entry. No edits to documentation files, no risk of breaking unrelated prose.
- Survives document refactors (markdown table reshuffles, section renames) as long as the entry's regex matchers still hit. Variant B would die on any markdown restructure.
- Zero markdown pollution. Variant B would scatter `<!-- pin: hilt -->` markers across three documents, polluting human reading.
- Explicit failure surface. A broken matcher fails loudly on a named pin during chequer run, not silently as a missing capture. Variant C's "guess by Maven coordinate" path makes failures harder to attribute.

Schema (per entry under root key `Pins = @(...)`):

```powershell
@{
    name        = 'agp'                       # canonical pin id, used in output records
    gradleKey   = 'agp'                       # key into Get-GradlePins output hashtable
    gradleSource = 'build.gradle.kts'         # informational - where the pin is canonical
    docs        = @{
        'docs/TECH_STACK.md'        = @{ required = $false; matcher = $null }
        'dev/TECH_REQUIREMENTS.md'  = @{ required = $true;  matcher = 'AGP\s+\(Android Gradle Plugin\)[^\d]*(?<v>[\d\.]+)' }
        'CLAUDE.md'                 = @{ required = $false; matcher = $null }
    }
    policy      = 'allMustMatch'              # 'allMustMatch' (default) | 'firstOnly'
    exclude     = @()                         # array of regex patterns; matched spans stripped before match
}
```

Required schema fields: `name`, `gradleKey`, `docs`. Optional: `gradleSource`, `policy` (default `allMustMatch`), `exclude` (default empty array). Every per-document hashtable has `required` and `matcher`; either may be `$null` for `matcher` only (then the pair is informational, never produces MISSING regardless of `required`).

---

## D-2: Missing-Pin Semantics

Resolves strategic §6.4.

Each pin's manifest entry declares per-document `required: $true | $false`. The comparator classifies the absence of a captured mention according to that flag.

Classification rules:

- Pin missing from a required doc (mention list empty, `required = $true`, matcher non-null) → record `MISSING`. Contributes to non-zero exit code (FAIL subclass).
- Pin missing from a non-required doc (mention list empty, `required = $false`) → record `SKIP` with `Reason = 'not required in this doc'`. Informational only, does not affect exit code.
- Pin where `matcher` is `$null` → never produces MISSING, only SKIP with `Reason = 'no matcher declared for this doc'`. This is the explicit "the document is not expected to mention the pin" escape hatch even when `required = $true` was set elsewhere.

Required-flag policy per document:

| Document | When `required = $true` |
|----------|-------------------------|
| `dev/TECH_REQUIREMENTS.md` | For every Class-1, Class-2, and Class-3 pin. The document is the canonical full-inventory source ("Single source of truth for the full technical stack"). |
| `docs/TECH_STACK.md` | Only for pins the cheat-sheet explicitly enumerates today (`play-services-cast-framework`, `mediarouter`, `nanohttpd`). The doc is a short reference and is not expected to cover the full inventory; demanding completeness produces noise. |
| `CLAUDE.md` | Only for the headline pins it currently mentions: `kotlin`, `lib.androidx.room:room-runtime`, `lib.com.github.bumptech.glide:glide`, `lib.androidx.media3:media3-exoplayer`, `lib.com.google.dagger:hilt-android`, `compile-sdk`, `min-sdk.standard`, `min-sdk.legacy`. |

Rationale: making `docs/TECH_STACK.md` required everywhere would turn it into a duplicate of `dev/TECH_REQUIREMENTS.md` by enforcement pressure; that contradicts its purpose as a brief cheat-sheet. Making `CLAUDE.md` required everywhere would explode the prompt that ships with every Claude session - the same anti-pattern.

---

## D-3: Range Declarations

Resolves strategic §6.5.

A documented version may be either an exact pin (`2.2.10`, `9.2.1`) or a range marker. Recognized range syntaxes:

- Plus suffix: `1.9+`, `2.0+`, `9+` (regex: `^\d+(\.\d+)*\+$`).
- Lower-bound operator: `>=1.9`, `>= 1.9.0` (regex: `^>=\s*\d+(\.\d+)*$`).
- Wildcard suffix: `1.9.x`, `2.0.x` (regex: `^\d+(\.\d+)*\.x$`).

If the captured doc token is a range expression and the actual Gradle version falls **inside** the range, classify as `WARN`. The chequer prints the WARN line but **does NOT flip exit code from 0 to 1** on its own. WARN exists to surface stale-but-technically-correct documentation that misleads agents into using outdated knowledge. It is a soft signal, not a gate.

Canonical case from strategic §4: `CLAUDE.md` declares "Kotlin 1.9+"; Gradle Kotlin is `2.2.10`. `2.2.10` falls inside `1.9+` → WARN.

If the captured doc token is a range and Gradle version is **outside** the range (e.g. doc says `>= 3.0`, Gradle says `2.2.10`) → classify as `FAIL` like any other contradiction. WARN only applies when the range is technically satisfied.

Range-inside check helper: `Test-VersionInsideRange -Range <string> -Actual <string>`. Compares dotted version segments numerically (up to 4 segments; missing segments pad with `0`). Returns `$true` / `$false`. Lives in `Comparator.ps1` as a private helper.

Strategic spec acceptance criterion #2 ("checker outputs ≥1 FAIL on current repo") is not satisfied by WARN alone - the chequer must surface at least one true FAIL (from non-range drift like AGP `9.2.1` vs `9.2.0`) on top of any WARN records.

---

## D-4: Multi-Mention Inside One Document

Resolves strategic §6.6.

Per-pin `policy` field controls behavior when one document contains more than one regex match for the same pin:

- `allMustMatch` (default) - every captured value is compared to Gradle. If captured values diverge from each other → record `INCONSISTENT` (FAIL subclass) with `DocValue = '<v1> vs <v2> [vs <v3>...]'`. If all captured values are identical → behave as if there were one mention.
- `firstOnly` - truncate the capture list to the first match. Subsequent matches in the document are ignored. Used when the document legitimately contains historical/contextual mentions that should not block classification.
- `exclude` (array of regex patterns) - applied **before** matching. Any span matching an exclude regex is stripped from the document text (`[regex]::Replace(..., '')`) before the matcher runs. Used to surgically remove a known "Version History" block or any documented-but-not-current section. The match is case-insensitive, multiline. Use sparingly: heavy excludes mask real drift.

Selection guide for the operator authoring a pin entry:

- Default to `allMustMatch` + empty `exclude`. Most pins will have one canonical mention.
- If the document has a real intent to show historical/old values (e.g. `dev/TECH_REQUIREMENTS.md` §11 "Version History"), use `exclude = @('##.*Version History[\s\S]*?(?=^##|\z)')` to fence the history section. Document the exclude rationale inline in the PSD1.
- Avoid `firstOnly` unless the structure of the document makes "first match is the canonical" semantically obvious. It silently hides divergence rather than reporting it.

Records emitted:

- All values identical → one `PASS` / `FAIL` / `WARN` record per the rest of D-3.
- Values divergent under `allMustMatch` → one `INCONSISTENT` record with the divergent values listed.
- One value after `firstOnly` truncation → behaves as single-mention.

---

## D-5: Output Format Contract (locked)

Freezes strategic §5.3 grammar so tests, downstream tools, and operators pattern-match on stable strings.

Record line templates (one per record, in this order within a single chequer run, then alphabetic by `(pin, doc-path)` within a class):

```text
FAIL | <pin> | gradle: <X> | <doc-path>: <Y>
WARN | <pin> | gradle: <X> | <doc-path>: <Y> (range)
INCONSISTENT | <pin> | <doc-path>: <Y1> vs <Y2>
MISSING | <pin> | <doc-path>: required mention not found
SKIP | <pin> | reason: <text>
PASS | <pin> | <X>                              # emitted only when -VerboseOutput is set
```

Final summary line, always emitted last:

```text
SUMMARY | total: N | pass: A | fail: B | warn: C | skip: D | inconsistent: E | missing: F
```

Counters are computed from the records array, never from print-side counting. `total` equals `A + B + C + D + E + F`.

Exit code:

- `0` iff `fail + inconsistent + missing == 0`.
- `1` otherwise.
- `-AsBootstrapWarning` forces exit `0` after the print, regardless of the record totals. Use only from the bootstrap warning surface (S0268).

Formatting rules:

- Pure ASCII. No emoji. No box-drawing characters.
- No ANSI color escapes by default. `-Color` flag enables them: `[91m` (red) for FAIL/INCONSISTENT/MISSING, `[93m` (yellow) for WARN, `[92m` (green) for PASS, `[0m` reset after each colored line.
- `<X>` and `<Y>` are version strings as captured. No quoting, no padding.
- `<doc-path>` is relative to repo root, forward slashes (`docs/TECH_STACK.md`, not `docs\TECH_STACK.md`).
- `<pin>` is the canonical name from the manifest entry's `name` field.
- Idempotent: two consecutive runs with no document edits produce byte-identical output.

This grammar is the contract Phase 04 implements verbatim and Phase 05 tests against by string match.
