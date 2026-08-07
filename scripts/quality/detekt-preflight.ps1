#requires -Version 7.0
<#
  detekt-preflight.ps1 (S1338 phase 04 step 04.7)

  A purely lexical pre-check for the three detekt rules that dominate this repo's
  findings - MaxLineLength, ImportOrdering and MagicNumber. Each of them currently
  costs a ~23 s gradle round-trip to discover, and `assert-detekt` rejects half of
  the runs it is given. This front-runs the cheap majority in well under a second.

  It NEVER replaces the detekt gate: it is lexical, it reads no type information,
  and it is deliberately conservative - a clean preflight is not a promise that
  detekt will pass, only that these three rules are unlikely to be why it does not.

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
    0 - no finding in the checked files (or nothing to check).
    1 - at least one finding (only under -Gate; without it the findings print and
        the script still exits 0, so it can front-run without blocking).
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
    foreach ($f in $findings) {
        Write-Host ("  {0}:{1} - {2} ({3})" -f $f.file, $f.line, $f.rule, $f.detail) -ForegroundColor Yellow
    }
}

$byRule = $findings | Group-Object rule | ForEach-Object { "$($_.Name) $($_.Count)" }
if ($findings.Count -eq 0) {
    Write-Host ("detekt-preflight: PASS - {0} file(s), no MaxLineLength / ImportOrdering / MagicNumber finding (maxLineLength {1})." -f $targets.Count, $maxLineLength) -ForegroundColor Green
    exit 0
}

$summary = "detekt-preflight: {0} finding(s) over {1} file(s) - {2}. Lexical only; the detekt gate remains the verdict." -f $findings.Count, $targets.Count, ($byRule -join ', ')
if ($Gate) {
    Write-Error $summary -ErrorAction Continue
    exit 1
}
Write-Host $summary -ForegroundColor Yellow
exit 0
