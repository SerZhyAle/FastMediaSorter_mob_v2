<#
.SYNOPSIS
    S1392 - flavor capability matrix generator (single source of truth renderer).

.DESCRIPTION
    Derives the per-flavor capability grid from app_v2/build.gradle.kts and emits
    two artifacts:

      docs/flavors/flavor-matrix.json  machine-readable snapshot (gate input)
      docs/FLAVOR_MATRIX.md            the one human-readable table

    The build file stays the only source of truth (S1392 ADR-1). Every other
    document defers to the rendered table instead of restating the grid, and
    scripts/quality/assert-flavor-matrix-docs.ps1 enforces that deferral.

    Cell states are three-valued (S1392 ADR-3), because collapsing "not declared"
    into "false" is what made the VR rows in docs/DEV_OPS.md read as enabled:

      declared in the flavor block          -> true / false, origin "flavor"
      inherited from defaultConfig          -> true / false, origin "defaultConfig"
      declared nowhere for that flavor      -> null (the field is absent from that
                                               flavor's BuildConfig entirely)

    Parsing is pure text with brace-depth tracking - no gradle invocation, so the
    script is safe inside the fast-gate batch. It refuses to emit a partial grid:
    a missing productFlavors block, fewer flavors than expected, a flavor with no
    boolean field, or an unparsable buildConfigField line all abort with exit 2.

.PARAMETER Check
    Render into memory and compare against the files on disk. Exit 1 on drift,
    print the differing artifact. Does not write.

.EXAMPLE
    pwsh -NoProfile -File scripts/docs/generate-flavor-matrix.ps1
    pwsh -NoProfile -File scripts/docs/generate-flavor-matrix.ps1 -Check

.NOTES
    Exit codes:
      0  artifacts written, or -Check found them current
      1  -Check found drift (regenerate without -Check)
      2  could not verify: build file missing, productFlavors block not found,
         unparsable declaration, or fewer than -MinFlavorCount flavors parsed
#>
[CmdletBinding()]
param(
    [string] $RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string] $GradleFile,
    [string] $Json,
    [string] $Markdown,
    [int]    $MinFlavorCount = 6,
    [switch] $Check,
    [switch] $Quiet
)

$ErrorActionPreference = 'Stop'

if (-not $GradleFile) { $GradleFile = Join-Path $RepoRoot 'app_v2/build.gradle.kts' }
if (-not $Json)       { $Json       = Join-Path $RepoRoot 'docs/flavors/flavor-matrix.json' }
if (-not $Markdown)   { $Markdown   = Join-Path $RepoRoot 'docs/FLAVOR_MATRIX.md' }

function Write-Info([string] $Message) {
    if (-not $Quiet) { Write-Host $Message }
}

function Fail-Unverifiable([string] $Message) {
    Write-Error "generate-flavor-matrix: $Message" -ErrorAction Continue
    exit 2
}

# Strip a line comment without touching a "//" that sits inside a quoted string
# (abiFilters and applicationIdSuffix values are quoted; comments carry URLs).
function Remove-LineComment([string] $Line) {
    $inString = $false
    for ($i = 0; $i -lt $Line.Length; $i++) {
        $ch = $Line[$i]
        if ($ch -eq '"' -and ($i -eq 0 -or $Line[$i - 1] -ne '\')) {
            $inString = -not $inString
            continue
        }
        if (-not $inString -and $ch -eq '/' -and $i + 1 -lt $Line.Length -and $Line[$i + 1] -eq '/') {
            return $Line.Substring(0, $i)
        }
    }
    return $Line
}

function Get-BraceDelta([string] $CodeOnly) {
    $open  = ([regex]::Matches($CodeOnly, '\{')).Count
    $close = ([regex]::Matches($CodeOnly, '\}')).Count
    return $open - $close
}

# Returns the 0-based line range [Start, End] of the body of a block whose header
# line matches $HeaderPattern, searched from $From. End is the line carrying the
# closing brace. Returns $null when the header is not found.
function Get-BlockRange([string[]] $Lines, [string] $HeaderPattern, [int] $From = 0) {
    for ($i = $From; $i -lt $Lines.Count; $i++) {
        $code = Remove-LineComment $Lines[$i]
        if ($code -notmatch $HeaderPattern) { continue }
        if ($code -notmatch '\{') { continue }

        $depth = Get-BraceDelta $code
        for ($j = $i + 1; $j -lt $Lines.Count; $j++) {
            $depth += Get-BraceDelta (Remove-LineComment $Lines[$j])
            if ($depth -le 0) {
                return [pscustomobject]@{ Header = $i; Start = $i + 1; End = $j }
            }
        }
        Fail-Unverifiable "unbalanced braces after line $($i + 1) while reading '$HeaderPattern'."
    }
    return $null
}

function Get-BooleanFields([string[]] $Lines, [int] $Start, [int] $End, [string] $Context) {
    $fields = [ordered]@{}
    for ($i = $Start; $i -le $End; $i++) {
        $code = Remove-LineComment $Lines[$i]
        if ($code -notmatch 'buildConfigField\s*\(') { continue }
        if ($code -notmatch 'buildConfigField\s*\(\s*"(?<type>[A-Za-z\[\]]+)"\s*,\s*"(?<name>[A-Za-z0-9_]+)"\s*,\s*(?<value>.+?)\s*\)\s*$') {
            Fail-Unverifiable "unparsable buildConfigField at $Context line $($i + 1): $($code.Trim())"
        }
        if ($Matches['type'] -ne 'boolean') { continue }
        $raw = $Matches['value'].Trim().Trim('"')
        if ($raw -ne 'true' -and $raw -ne 'false') {
            Fail-Unverifiable "non-literal boolean for $($Matches['name']) at $Context line $($i + 1): $raw"
        }
        $fields[$Matches['name']] = ($raw -eq 'true')
    }
    return $fields
}

function Get-MinSdk([string[]] $Lines, [int] $Start, [int] $End) {
    for ($i = $Start; $i -le $End; $i++) {
        $code = Remove-LineComment $Lines[$i]
        if ($code -match '^\s*minSdk\s*=\s*(?<v>\d+)\s*$') { return [int]$Matches['v'] }
    }
    return $null
}

# ---------------------------------------------------------------- parse

if (-not (Test-Path -LiteralPath $GradleFile)) {
    Fail-Unverifiable "build file not found: $GradleFile"
}
$lines = Get-Content -LiteralPath $GradleFile

$defaultBlock = Get-BlockRange -Lines $lines -HeaderPattern '^\s*defaultConfig\s*\{'
if (-not $defaultBlock) { Fail-Unverifiable 'defaultConfig block not found.' }
$defaultFields = Get-BooleanFields -Lines $lines -Start $defaultBlock.Start -End $defaultBlock.End -Context 'defaultConfig'
$defaultMinSdk = Get-MinSdk -Lines $lines -Start $defaultBlock.Start -End $defaultBlock.End
if ($null -eq $defaultMinSdk) { Fail-Unverifiable 'defaultConfig declares no minSdk.' }

$flavorsBlock = Get-BlockRange -Lines $lines -HeaderPattern '^\s*productFlavors\s*\{'
if (-not $flavorsBlock) { Fail-Unverifiable 'productFlavors block not found.' }

$flavors     = [ordered]@{}
$flavorMin   = [ordered]@{}
$i = $flavorsBlock.Start
while ($i -le $flavorsBlock.End) {
    $code = Remove-LineComment $lines[$i]
    if ($code -notmatch 'create\s*\(\s*"(?<name>[A-Za-z0-9_]+)"\s*\)\s*\{') { $i++; continue }
    $name = $Matches['name']

    $depth = Get-BraceDelta $code
    $end = -1
    for ($j = $i + 1; $j -le $flavorsBlock.End; $j++) {
        $depth += Get-BraceDelta (Remove-LineComment $lines[$j])
        if ($depth -le 0) { $end = $j; break }
    }
    if ($end -lt 0) { Fail-Unverifiable "flavor '$name' block never closes (opened line $($i + 1))." }

    $fields = Get-BooleanFields -Lines $lines -Start ($i + 1) -End $end -Context "flavor '$name'"
    if ($fields.Count -eq 0) {
        Fail-Unverifiable "flavor '$name' declares no boolean buildConfigField - refusing a partial grid."
    }
    $flavors[$name] = $fields
    $min = Get-MinSdk -Lines $lines -Start ($i + 1) -End $end
    $flavorMin[$name] = if ($null -ne $min) { $min } else { $defaultMinSdk }

    $i = $end + 1
}

if ($flavors.Count -lt $MinFlavorCount) {
    Fail-Unverifiable "parsed only $($flavors.Count) flavor(s), expected at least $MinFlavorCount - refusing a partial grid."
}

# Flag order: defaultConfig booleans first, then first-seen order across flavors.
$flagOrder = [System.Collections.Generic.List[string]]::new()
foreach ($k in $defaultFields.Keys) { if (-not $flagOrder.Contains($k)) { $flagOrder.Add($k) } }
foreach ($f in $flavors.Keys) {
    foreach ($k in $flavors[$f].Keys) { if (-not $flagOrder.Contains($k)) { $flagOrder.Add($k) } }
}

$matrix = [ordered]@{}
$origin = [ordered]@{}
foreach ($f in $flavors.Keys) {
    $row = [ordered]@{}
    $orow = [ordered]@{}
    foreach ($flag in $flagOrder) {
        if ($flavors[$f].Contains($flag)) {
            $row[$flag]  = [bool]$flavors[$f][$flag]
            $orow[$flag] = 'flavor'
        }
        elseif ($defaultFields.Contains($flag)) {
            $row[$flag]  = [bool]$defaultFields[$flag]
            $orow[$flag] = 'defaultConfig'
        }
        else {
            $row[$flag]  = $null
            $orow[$flag] = 'absent'
        }
    }
    $matrix[$f] = $row
    $origin[$f] = $orow
}

# ---------------------------------------------------------------- render

$snapshot = [ordered]@{
    generatedBy = 'scripts/docs/generate-flavor-matrix.ps1'
    spec        = 'S1392'
    source      = 'app_v2/build.gradle.kts'
    doNotEdit   = 'Generated artifact. Change app_v2/build.gradle.kts, then regenerate.'
    flavors     = @($flavors.Keys)
    flags       = @($flagOrder)
    minSdk      = $flavorMin
    defaults    = $defaultFields
    matrix      = $matrix
    origin      = $origin
}
$jsonText = ($snapshot | ConvertTo-Json -Depth 8)

$glyph = {
    param($Value, $Origin)
    switch ($Origin) {
        'absent' { 'n/a' }
        'defaultConfig' { if ($Value) { '[+]*' } else { '[-]*' } }
        default { if ($Value) { '[+]' } else { '[-]' } }
    }
}

$md = [System.Text.StringBuilder]::new()
[void]$md.AppendLine('<!-- GENERATED by scripts/docs/generate-flavor-matrix.ps1 from app_v2/build.gradle.kts. Do not edit by hand. -->')
[void]$md.AppendLine('# Flavor capability matrix')
[void]$md.AppendLine()
[void]$md.AppendLine('This table is the canonical answer to "which capability is available in which flavor". It is')
[void]$md.AppendLine('generated from the `productFlavors` block of `app_v2/build.gradle.kts`, which is the only source')
[void]$md.AppendLine('of truth - a capability exists exactly insofar as it is compiled in. Every other document in this')
[void]$md.AppendLine('repository defers to this table instead of restating the grid, and')
[void]$md.AppendLine('`scripts/quality/assert-flavor-matrix-docs.ps1` fails the build when one of them drifts.')
[void]$md.AppendLine()
[void]$md.AppendLine('Regenerate with `pwsh -NoProfile -File scripts/docs/generate-flavor-matrix.ps1`.')
[void]$md.AppendLine()
[void]$md.AppendLine('## Legend')
[void]$md.AppendLine()
[void]$md.AppendLine('- `[+]` - declared `true` in the flavor block.')
[void]$md.AppendLine('- `[-]` - declared `false` in the flavor block.')
[void]$md.AppendLine('- `[+]*` / `[-]*` - not declared by the flavor, inherited from `defaultConfig`.')
[void]$md.AppendLine('- `n/a` - not declared for this flavor at all; the field is absent from its `BuildConfig`, so only')
[void]$md.AppendLine('  a flavor-specific source set can reference it.')
[void]$md.AppendLine()
[void]$md.AppendLine('## Matrix')
[void]$md.AppendLine()
[void]$md.AppendLine('| Flag | ' + (@($flavors.Keys) -join ' | ') + ' |')
[void]$md.AppendLine('|:-----|' + ((@($flavors.Keys) | ForEach-Object { ':----:' }) -join '|') + '|')
foreach ($flag in $flagOrder) {
    $cells = foreach ($f in $flavors.Keys) { & $glyph $matrix[$f][$flag] $origin[$f][$flag] }
    [void]$md.AppendLine('| `' + $flag + '` | ' + ($cells -join ' | ') + ' |')
}
[void]$md.AppendLine('| `minSdk` | ' + ((@($flavors.Keys) | ForEach-Object { $flavorMin[$_] }) -join ' | ') + ' |')
[void]$md.AppendLine()
[void]$md.AppendLine('## How the app reads this')
[void]$md.AppendLine()
[void]$md.AppendLine('Shared code never reads these fields directly (CLAUDE.md Rule 14). They are folded into the typed')
[void]$md.AppendLine('capability record bound per flavor, and section availability is resolved through the single')
[void]$md.AppendLine('capability-availability entry point. A `[-]` therefore means the surface has no entry point in that')
[void]$md.AppendLine('flavor, not that it is merely restricted.')
[void]$md.AppendLine()
$mdText = $md.ToString()

# ---------------------------------------------------------------- write / check

function Read-IfExists([string] $Path) {
    if (Test-Path -LiteralPath $Path) { return (Get-Content -LiteralPath $Path -Raw) }
    return $null
}

function Normalize([string] $Text) {
    if ($null -eq $Text) { return $null }
    return ($Text -replace "`r`n", "`n").TrimEnd("`n")
}

if ($Check) {
    $drift = @()
    if ((Normalize (Read-IfExists $Json))     -ne (Normalize $jsonText)) { $drift += $Json }
    if ((Normalize (Read-IfExists $Markdown)) -ne (Normalize $mdText))   { $drift += $Markdown }
    if ($drift.Count -gt 0) {
        Write-Error "generate-flavor-matrix: stale artifact(s): $($drift -join ', '). Regenerate without -Check." -ErrorAction Continue
        exit 1
    }
    Write-Info 'generate-flavor-matrix: artifacts current.'
    exit 0
}

$jsonDir = Split-Path -Parent $Json
if (-not (Test-Path -LiteralPath $jsonDir)) { New-Item -ItemType Directory -Force -Path $jsonDir | Out-Null }

Set-Content -LiteralPath $Json     -Value $jsonText -Encoding utf8NoBOM
Set-Content -LiteralPath $Markdown -Value $mdText   -Encoding utf8NoBOM

Write-Info "generate-flavor-matrix: $($flavors.Count) flavor(s), $($flagOrder.Count) flag(s) -> $Json, $Markdown"
exit 0
