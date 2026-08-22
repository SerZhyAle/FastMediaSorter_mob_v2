#requires -Version 7.0
<#
.SYNOPSIS
    S1547 research probe: how large is the blind spot of assert-exit-contract.ps1 rule A?

.DESCRIPTION
    Measures three things over the same roots the gate scans:
      1. how many scripts inherit $ErrorActionPreference = 'Stop' through a dot-source
         instead of setting it themselves;
      2. how many rule-A findings (Write-Error without -ErrorAction, followed by exit N != 1)
         those inheriting scripts carry today - the debt the gate cannot see;
      3. the same count for files that set Stop themselves, as a control.

    Read-only. Prints a report; writes nothing.
#>
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..' '..')).Path
$roots = @(
    (Join-Path $repoRoot 'scripts'),
    (Join-Path $repoRoot 'dev/CATALOG/scripts'),
    (Join-Path $repoRoot 'dev/ACTIVITY_CATALOG/scripts')
) | Where-Object { Test-Path $_ }

$files = @(foreach ($root in $roots) {
    Get-ChildItem -LiteralPath $root -Recurse -File -Filter *.ps1 -ErrorAction SilentlyContinue |
        Where-Object { $_.DirectoryName -notmatch '\.tests($|[\\/])' }
})

function Test-SetsStop([string[]]$lines) {
    foreach ($l in $lines) {
        if ($l -match '^\s*#') { continue }
        if ($l -match '\$ErrorActionPreference\s*=\s*[''"]Stop[''"]') { return $true }
    }
    return $false
}

# One level of dot-sourcing, literal sibling path only.
function Get-DotSourced([string[]]$lines, [string]$dir) {
    $out = @()
    foreach ($l in $lines) {
        if ($l -match '^\s*#') { continue }
        if ($l -notmatch '^\s*\.\s') { continue }
        $m = [regex]::Match($l, "Join-Path\s+\`$PSScriptRoot\s+'([^']+)'")
        if ($m.Success) { $out += (Join-Path $dir $m.Groups[1].Value); continue }
        $m = [regex]::Match($l, "^\s*\.\s+\`"?\`$PSScriptRoot[\\/]([^\`"']+)\`"?\s*$")
        if ($m.Success) { $out += (Join-Path $dir $m.Groups[1].Value) }
    }
    return $out
}

function Get-RuleAFindings([string[]]$lines, [string]$rel) {
    $found = @()
    $lookahead = 3
    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line -match '^\s*#') { continue }
        if ($line -notmatch 'Write-Error') { continue }
        if ($line -match '-ErrorAction') { continue }
        for ($k = $i; $k -lt [Math]::Min($i + $lookahead + 1, $lines.Count); $k++) {
            if ($lines[$k] -match '\bexit\s+([2-9])\b') {
                $found += [pscustomobject]@{ File = $rel; Line = $i + 1; Code = [int]$Matches[1]; Text = $line.Trim() }
                break
            }
        }
    }
    return $found
}

$inherited = @()
$own = @()
$neither = 0

foreach ($f in $files) {
    $lines = Get-Content -LiteralPath $f.FullName -ErrorAction SilentlyContinue
    if (-not $lines) { continue }
    $rel = $f.FullName.Replace($repoRoot + [IO.Path]::DirectorySeparatorChar, '')

    if (Test-SetsStop $lines) { $own += [pscustomobject]@{ Rel = $rel; Lines = $lines }; continue }

    $libs = Get-DotSourced $lines $f.DirectoryName
    $viaLib = $null
    foreach ($lib in $libs) {
        if (-not (Test-Path -LiteralPath $lib)) { continue }
        $libLines = Get-Content -LiteralPath $lib -ErrorAction SilentlyContinue
        if ($libLines -and (Test-SetsStop $libLines)) {
            $viaLib = $lib.Replace($repoRoot + [IO.Path]::DirectorySeparatorChar, '')
            break
        }
    }
    if ($viaLib) { $inherited += [pscustomobject]@{ Rel = $rel; Lines = $lines; Via = $viaLib } }
    else { $neither++ }
}

Write-Host ''
Write-Host ("Scanned {0} script(s) under {1} root(s)." -f $files.Count, $roots.Count)
Write-Host ("  sets Stop itself          : {0}" -f $own.Count)
Write-Host ("  inherits Stop via dot-source: {0}" -f $inherited.Count)
Write-Host ("  no Stop at all            : {0}" -f $neither)
Write-Host ''

$byLib = $inherited | Group-Object Via | Sort-Object Count -Descending
Write-Host 'Inheritance sources:'
foreach ($g in $byLib) { Write-Host ("  {0,-52} {1} script(s)" -f $g.Name, $g.Count) }
Write-Host ''

$blind = @()
foreach ($x in $inherited) { $blind += Get-RuleAFindings $x.Lines $x.Rel }
$visible = @()
foreach ($x in $own) { $visible += Get-RuleAFindings $x.Lines $x.Rel }

Write-Host ("Rule A findings in files that set Stop themselves (gate sees these): {0}" -f $visible.Count)
foreach ($x in $visible) { Write-Host ("  {0}:{1}  exit {2}" -f $x.File, $x.Line, $x.Code) }
Write-Host ''
Write-Host ("Rule A findings in files that INHERIT Stop (gate is blind to these): {0}" -f $blind.Count)
foreach ($x in $blind) {
    Write-Host ("  {0}:{1}  exit {2}" -f $x.File, $x.Line, $x.Code) -ForegroundColor Yellow
    Write-Host ("      {0}" -f $x.Text) -ForegroundColor DarkGray
}
Write-Host ''
Write-Host ("Distinct files carrying blind findings: {0}" -f (($blind | Select-Object -ExpandProperty File -Unique) | Measure-Object).Count)
exit 0
