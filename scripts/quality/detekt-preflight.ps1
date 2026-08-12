#requires -Version 7.0
<#
  detekt-preflight.ps1 (S1338 phase 04 step 04.7; reworked by S1595)

  Front-runs the expensive detekt gate on the changed files. Since S1595 it does that by
  running the REAL analyser over just those files (scripts/quality/detekt-scoped.ps1,
  1.3-2.5 s), and only falls back to the lexical scan below when the analyser cannot be
  started at all.

  Why the change: the lexical scan reproduces three rules by hand - MaxLineLength,
  ImportOrdering and MagicNumber - and measured over the transcript corpus it fired on
  35.7% of attributable gate failures while fully covering 13.9%, so in 86% of failures
  the gradle round-trip happened anyway. Hand-listing more rules does not fix that: nine
  hand-listed rules reach 48.1%, the biggest misses (ReturnCount, ArgumentListWrapping)
  were not the ones anyone predicted, and the size rules cannot be reproduced lexically
  at all - the classes detekt flags and the ones it does not overlap by a 240-line band
  under every line metric tried. See PLAN/S1595_detekt-preflight-coverage-gap/research/.

  It NEVER replaces the detekt gate: the scoped run has no type information and sees a
  narrowed input, so the gate remains the project-wide verdict.

  Measured cost (S1595, 2026-08-12): 2.1 s for one file and 3.3 s for two directly, and
  3.1 s as the `[detekt-preflight]` step inside post-change.ps1, which adds one pwsh
  start. Comfortably inside the foreground budget, and it takes no BUILD.LOCK, so it does
  not queue behind a sibling session's build - see docs/BUILD_TEST_FAST_PATH.md.

  The lexical scan below is retained deliberately, as the degraded path. It is also the
  reason the degraded path never blocks: its false positives are exactly what the
  research measured, and blocking a closure on one would cost an edit for nothing.

  Thresholds come from config/detekt/detekt.yml. That file relies on
  buildUponDefaultConfig = true and overrides neither rule, so the documented detekt
  defaults apply and are reported as such rather than silently hardcoded.

  ImportOrdering follows the ktlint layout the `formatting` ruleset selects with
  android = true: `*,java.**,javax.**,kotlin.**,^` - everything else first, then
  java, javax, kotlin, then aliases, no blank lines between groups, and a
  case-SENSITIVE ordinal sort inside each group (kotlinx and timber are NOT kotlin).

  Findings already carried by the detekt baseline are subtracted, because the gate
  they front-run does not fail on them: reporting pre-existing debt on a file you
  just opened is the noise that teaches an agent to ignore gate output. Matching is
  by baseline signature - exact for ImportOrdering (the signature is the whole import
  block, so ANY edit to the imports re-reports it), by code fragment for
  MaxLineLength, and by literal value per file for MagicNumber. Pass -IgnoreBaseline
  to see the unfiltered set.

  Exit codes:
    0 - the analyser found nothing in the checked files, or there was nothing to check,
        or it could not run and the degraded lexical scan stood in for it. The degraded
        path ALWAYS exits 0 even when the lexical scan found something: those findings
        print, but a lexical guess must not abort a closure.
    1 - the real analyser found at least one new finding (only under -Gate; without it
        the findings print and the script still exits 0, so it can front-run without
        blocking).
    2 - cannot verify: the config file is missing, or a named file does not exist.
#>
[CmdletBinding()]
param(
    [string[]] $ChangedFiles,
    [switch]   $Gate,
    [string]   $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]   $ConfigPath,
    [string]   $BaselinePath,
    [switch]   $IgnoreBaseline,
    [switch]   $Json
)

$ErrorActionPreference = 'Stop'

# detekt's documented defaults; used only when detekt.yml does not override them.
$defaultMaxLineLength = 120
$defaultIgnoreNumbers = @('-1', '0', '1', '2')

if (-not $ConfigPath) { $ConfigPath = Join-Path $RepoRoot 'config/detekt/detekt.yml' }
if (-not (Test-Path -LiteralPath $ConfigPath)) {
    $msg = "detekt-preflight: config not found at $ConfigPath - cannot read thresholds."
    Write-Error $msg -ErrorAction Continue
    exit 2
}

$configText = Get-Content -LiteralPath $ConfigPath -Raw
$maxLineLength = $defaultMaxLineLength
$m = [regex]::Match($configText, '(?m)^\s*maxLineLength:\s*(\d+)\s*$')
if ($m.Success) { $maxLineLength = [int]$m.Groups[1].Value }
$ignoreNumbers = $defaultIgnoreNumbers
$m = [regex]::Match($configText, "(?m)^\s*ignoreNumbers:\s*\[?\s*'?([^\]'#\r\n]+)'?\s*\]?\s*$")
if ($m.Success) { $ignoreNumbers = ($m.Groups[1].Value -split ',') | ForEach-Object { $_.Trim().Trim("'") } }

# S1184: `pwsh -File` binds a [string[]] parameter to its FIRST element only, so every caller
# passes one comma-joined argument and every consumer splits it back here.
$expanded = @()
foreach ($entry in ($ChangedFiles | Where-Object { $_ })) { $expanded += ($entry -split ',') | ForEach-Object { $_.Trim() } }

$targets = @()
foreach ($f in ($expanded | Where-Object { $_ })) {
    $p = if ([System.IO.Path]::IsPathRooted($f)) { $f } else { Join-Path $RepoRoot $f }
    if (-not (Test-Path -LiteralPath $p)) {
        $msg = "detekt-preflight: file not found: $f - refusing to report a clean preflight over a file it could not read."
        Write-Error $msg -ErrorAction Continue
        exit 2
    }
    if ([System.IO.Path]::GetExtension($p) -eq '.kt') { $targets += (Resolve-Path -LiteralPath $p).Path }
}

if ($targets.Count -eq 0) {
    Write-Host 'detekt-preflight: no .kt file in the changed set - nothing to check.'
    exit 0
}

# --- S1595: delegate to the real analyser ------------------------------------
# A child process rather than a dot-source, because the runner ends in `exit` and dot-sourcing it
# would take this script's process down with it.
$degradedReason = $null
$scopedRunner = Join-Path $PSScriptRoot 'detekt-scoped.ps1'
if (-not (Test-Path -LiteralPath $scopedRunner)) {
    $degradedReason = "scoped runner not found at $scopedRunner"
}
else {
    $scopedOutput = @(& pwsh -NoProfile -File $scopedRunner -ChangedFiles ($targets -join ',') 2>&1)
    switch ($LASTEXITCODE) {
        0 {
            $scopedOutput | ForEach-Object { Write-Host $_ }
            exit 0
        }
        1 {
            $scopedOutput | ForEach-Object { Write-Host $_ }
            if ($Gate) { exit 1 }
            exit 0
        }
        default {
            # Exit 2 from the runner means it could not check, which is neither a pass nor a
            # failure. Keep its own words - they name the missing piece - then fall through to the
            # lexical scan so the step still says something rather than nothing.
            $degradedReason = (@($scopedOutput | Where-Object { $_ -match 'CANNOT VERIFY' }) |
                Select-Object -First 1)
            if (-not $degradedReason) { $degradedReason = "scoped runner exited $LASTEXITCODE" }
        }
    }
}

Write-Host ''
Write-Host "detekt-preflight: DEGRADED - the real analyser could not run, falling back to the lexical scan." -ForegroundColor Magenta
Write-Host "  reason: $degradedReason" -ForegroundColor Magenta
Write-Host "  The lexical scan checks three rules out of the whole set and cannot see types, so it" -ForegroundColor Magenta
Write-Host "  never blocks a closure. Anything it prints below is a hint; the detekt gate is the verdict." -ForegroundColor Magenta
Write-Host ''

# Blank out string literals and comments so a number or a brace inside them is not code.
function Get-MaskedLines([string[]] $lines) {
    $masked = New-Object string[] $lines.Count
    $inRaw = $false
    $inBlockComment = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        $sb = [System.Text.StringBuilder]::new()
        $j = 0
        while ($j -lt $line.Length) {
            $rest = $line.Substring($j)
            if ($inRaw) {
                if ($rest.StartsWith('"""')) { $inRaw = $false; $j += 3 } else { [void]$sb.Append(' '); $j++ }
                continue
            }
            if ($inBlockComment) {
                if ($rest.StartsWith('*/')) { $inBlockComment = $false; [void]$sb.Append('  '); $j += 2 } else { [void]$sb.Append(' '); $j++ }
                continue
            }
            if ($rest.StartsWith('"""')) { $inRaw = $true; $j += 3; continue }
            if ($rest.StartsWith('/*')) { $inBlockComment = $true; $j += 2; [void]$sb.Append('  '); continue }
            if ($rest.StartsWith('//')) { break }
            if ($line[$j] -eq '"' -or $line[$j] -eq "'") {
                $quote = $line[$j]
                $j++
                while ($j -lt $line.Length -and $line[$j] -ne $quote) {
                    if ($line[$j] -eq '\') { $j++ }
                    $j++
                }
                $j++
                [void]$sb.Append('""')
                continue
            }
            [void]$sb.Append($line[$j]); $j++
        }
        $masked[$i] = $sb.ToString()
    }
    return , $masked
}

# Which lines sit inside a raw string, so MaxLineLength can skip them (excludeRawStrings).
function Get-RawStringLineFlags([string[]] $lines) {
    $flags = New-Object bool[] $lines.Count
    $inRaw = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $flags[$i] = $inRaw
        $ticks = ([regex]::Matches($lines[$i], '"""')).Count
        if ($ticks % 2 -eq 1) { if ($inRaw) { $inRaw = $false } else { $inRaw = $true; $flags[$i] = $true } }
        elseif ($ticks -gt 0) { $flags[$i] = $true }
    }
    return , $flags
}

function Get-ImportGroup([string] $import) {
    if ($import -match '\s+as\s+\S+$') { return 4 }
    if ($import.StartsWith('java.')) { return 1 }
    if ($import.StartsWith('javax.')) { return 2 }
    if ($import.StartsWith('kotlin.')) { return 3 }
    return 0
}

# --- Baseline ---------------------------------------------------------------
# key "<rule>|<FileName.kt>" -> list of signature tails (the text after the last '$').
$baseline = @{}
if (-not $IgnoreBaseline) {
    $baselineFiles = @()
    if ($BaselinePath) { $baselineFiles = @($BaselinePath) }
    else {
        foreach ($module in 'app_v2', 'wear') {
            $p = Join-Path $RepoRoot "config/detekt/baseline-$module.xml"
            if (Test-Path -LiteralPath $p) { $baselineFiles += $p }
        }
    }
    foreach ($bp in $baselineFiles) {
        foreach ($id in [regex]::Matches((Get-Content -LiteralPath $bp -Raw), '<ID>(.*?)</ID>', 'Singleline')) {
            $text = $id.Groups[1].Value
            $colon = $text.IndexOf(':')
            if ($colon -lt 0) { continue }
            $rule = $text.Substring(0, $colon)
            $rest = $text.Substring($colon + 1)
            $dollar = $rest.IndexOf('$')
            if ($dollar -lt 0) { continue }
            $fileName = $rest.Substring(0, $dollar)
            $tail = $rest.Substring($rest.LastIndexOf('$') + 1)
            $key = "$rule|$fileName"
            if (-not $baseline.ContainsKey($key)) { $baseline[$key] = [System.Collections.Generic.List[string]]::new() }
            $baseline[$key].Add([System.Net.WebUtility]::HtmlDecode($tail))
        }
    }
}

# A short tail ('val', 'override') would match almost any line by substring, so it
# suppresses only when the line STARTS with it - positional evidence instead of
# incidental. detekt stores one entry per distinct signature, so two identical long
# lines share a single baseline entry and both must match it.
$minTailForMatch = 12

function Test-Baselined([string] $rule, [string] $fileName, [string] $context) {
    $tails = $baseline["$rule|$fileName"]
    if (-not $tails) { return $false }
    switch ($rule) {
        'ImportOrdering' { return $tails -contains $context }
        'MagicNumber' { return $tails -contains $context }
        default {
            foreach ($t in $tails) {
                if ($t.Length -ge $minTailForMatch) {
                    if ($context.Contains($t)) { return $true }
                } elseif ($t.Length -gt 0 -and $context.StartsWith($t)) { return $true }
            }
            return $false
        }
    }
}

$findings = New-Object System.Collections.Generic.List[object]
function Add-Finding([string] $file, [int] $line, [string] $rule, [string] $detail) {
    $rel = $file.Replace($RepoRoot, '').TrimStart('\', '/').Replace('\', '/')
    $findings.Add([pscustomobject]@{ file = $rel; line = $line; rule = $rule; detail = $detail })
}

foreach ($path in $targets) {
    $lines = [System.IO.File]::ReadAllLines($path)
    if ($lines.Count -eq 0) { continue }
    $fileName = [System.IO.Path]::GetFileName($path)
    $masked = Get-MaskedLines $lines
    $rawFlags = Get-RawStringLineFlags $lines

    # --- MaxLineLength -------------------------------------------------------
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $text = $lines[$i]
        if ($text.Length -le $maxLineLength) { continue }
        if ($rawFlags[$i]) { continue }
        $trimmed = $text.TrimStart()
        if ($trimmed.StartsWith('package ') -or $trimmed.StartsWith('import ')) { continue }
        if (Test-Baselined 'MaxLineLength' $fileName ($text -replace '\s+', ' ').Trim()) { continue }
        Add-Finding $path ($i + 1) 'MaxLineLength' ("{0} chars > {1}" -f $text.Length, $maxLineLength)
    }

    # --- ImportOrdering ------------------------------------------------------
    $importIdx = @()
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i].TrimStart().StartsWith('import ')) { $importIdx += $i }
    }
    if ($importIdx.Count -gt 1) {
        $first = $importIdx[0]; $last = $importIdx[-1]
        $blankInside = $false
        for ($i = $first; $i -le $last; $i++) {
            if (-not $lines[$i].TrimStart().StartsWith('import ') -and $lines[$i].Trim() -ne '') { $blankInside = $false; break }
            if ($lines[$i].Trim() -eq '') { $blankInside = $true }
        }
        [string[]] $imports = $importIdx | ForEach-Object { $lines[$_].Trim().Substring(7).Trim() }
        $prevGroup = -1
        $groupBreak = $false
        for ($i = 0; $i -lt $imports.Count; $i++) {
            $g = Get-ImportGroup $imports[$i]
            if ($g -lt $prevGroup) { $groupBreak = $true; break }
            $prevGroup = $g
        }
        $orderBreak = $false
        if (-not $groupBreak) {
            for ($i = 1; $i -lt $imports.Count; $i++) {
                if ((Get-ImportGroup $imports[$i]) -ne (Get-ImportGroup $imports[$i - 1])) { continue }
                if ([string]::CompareOrdinal($imports[$i - 1], $imports[$i]) -gt 0) { $orderBreak = $true; break }
            }
        }
        if ($groupBreak -or $orderBreak -or $blankInside) {
            $why = if ($groupBreak) { 'group out of layout order (*, java, javax, kotlin, aliases)' }
            elseif ($orderBreak) { 'not ordinal-ascending inside its group (case-sensitive)' }
            else { 'blank line inside the import block' }
            $block = (($importIdx | ForEach-Object { $lines[$_].Trim() }) -join ' ')
            if (-not (Test-Baselined 'ImportOrdering' $fileName $block)) {
                Add-Finding $path ($first + 1) 'ImportOrdering' $why
            }
        }
    }

    # --- MagicNumber ---------------------------------------------------------
    # detekt excludes whole test source dirs from MagicNumber by DIRECTORY. This preflight has to
    # mirror whatever config/detekt/detekt.yml declares, or it reports findings the authoritative
    # gate does not - and a preflight that disagrees with the gate it previews trains people to
    # ignore it. S1450 widened the config's pattern to '**/test*/**' because the bundled default
    # ('**/test/**') matched src/test but missed every flavor unit-test source set - src/testStandard,
    # src/testNoLegal, src/testStreamingEnabled, src/testCloudEnabled. The 'test[A-Za-z0-9]*'
    # alternative below tracks that widening; androidTest and the rest stay listed separately
    # because they do not start with 'test'.
    $relPath = $path.Replace($RepoRoot, '').TrimStart('\', '/').Replace('\', '/')
    if ($relPath -match '/(test[A-Za-z0-9]*|androidTest|commonTest|jvmTest|jsTest|iosTest|androidUnitTest|androidInstrumentedTest)/') { continue }
    $companionDepth = -1
    $depth = 0
    $inCompanionProperty = $false
    $propertyParenDepth = 0
    for ($i = 0; $i -lt $masked.Count; $i++) {
        $code = $masked[$i]
        $trimmed = $code.TrimStart()
        $opens = ([regex]::Matches($code, '\{')).Count
        $closes = ([regex]::Matches($code, '\}')).Count
        if ($trimmed -match '\bcompanion\s+object\b' -and $companionDepth -lt 0) { $companionDepth = $depth }
        $depthBefore = $depth
        $depth += $opens - $closes
        if ($companionDepth -ge 0 -and $depth -le $companionDepth -and $closes -gt 0) { $companionDepth = -1 }

        # A companion-object property is ignoreCompanionObjectPropertyDeclaration, on by
        # default, and it keeps ignoring across the continuation lines of a multi-line one.
        if ($inCompanionProperty) {
            $propertyParenDepth += ([regex]::Matches($code, '[\(\[]')).Count - ([regex]::Matches($code, '[\)\]]')).Count
            if ($propertyParenDepth -le 0) { $inCompanionProperty = $false }
            continue
        }
        if ($trimmed -eq '' -or $trimmed.StartsWith('@') -or $trimmed.StartsWith('package ') -or $trimmed.StartsWith('import ')) { continue }
        if ($trimmed -match '^(private |internal |public |protected )*const\s+val\b') { continue }
        if ($companionDepth -ge 0 -and $depthBefore -gt $companionDepth -and $trimmed -match '^(private |internal |public |protected )*(val|var)\b') {
            $propertyParenDepth = ([regex]::Matches($code, '[\(\[]')).Count - ([regex]::Matches($code, '[\)\]]')).Count
            if ($propertyParenDepth -gt 0) { $inCompanionProperty = $true }
            continue
        }
        foreach ($num in [regex]::Matches($code, '(?<![\w.])(0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d[\d_]*(\.\d+)?([eE][+-]?\d+)?)[fFdDlLuU]*')) {
            $raw = $num.Value
            $norm = ($raw -replace '_', '') -replace '[fFdDlLuU]+$', ''
            if ($ignoreNumbers -contains $norm) { continue }
            $before = $code.Substring(0, $num.Index)
            # Anything to the right of a single `=` is a named argument, a default parameter
            # value or an assignment. detekt ignores the first outright and treats the other
            # two by context this scan cannot see, so all three are left to the real gate:
            # a preflight that cries wolf costs an edit, while a miss only costs the round-trip
            # the gate was going to pay anyway. Comparisons (==, >=, <=, !=) are not assignments.
            if ($before -match '(?<![=!<>])=(?!=)') { continue }
            if (Test-Baselined 'MagicNumber' $fileName $raw) { continue }
            Add-Finding $path ($i + 1) 'MagicNumber' ("literal $raw")
        }
    }
}

if ($Json) {
    $findings | ConvertTo-Json -Depth 4
} else {
    # S1600: same prefix rule as assert-detekt / detekt-scoped - an unprefixed indented line does
    # not survive the caller's `Select-String`, so the hint is printed and then never read.
    foreach ($f in $findings) {
        Write-Host ("detekt-preflight:   {0}:{1} - {2} ({3})" -f $f.file, $f.line, $f.rule, $f.detail) -ForegroundColor Yellow
    }
}

$byRule = $findings | Group-Object rule | ForEach-Object { "$($_.Name) $($_.Count)" }
if ($findings.Count -eq 0) {
    Write-Host ("detekt-preflight: DEGRADED PASS - {0} file(s), no MaxLineLength / ImportOrdering / MagicNumber finding (maxLineLength {1}). Three rules of the set were checked, not all of them." -f $targets.Count, $maxLineLength) -ForegroundColor Yellow
    exit 0
}

# Never exit 1 here. Reaching this line means the real analyser did not run, so every finding
# below came from the lexical scan - and S1595 measured that scan's false-positive rate on the
# size rules as higher than its true-positive rate. Blocking a closure on a guess costs an edit
# for nothing, which is the failure mode this whole ticket exists to remove.
$summary = "detekt-preflight: DEGRADED - {0} lexical hint(s) over {1} file(s) - {2}. Not a verdict and not blocking; the detekt gate decides." -f $findings.Count, $targets.Count, ($byRule -join ', ')
Write-Host $summary -ForegroundColor Yellow
exit 0
