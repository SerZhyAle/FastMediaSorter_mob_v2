#requires -Version 7.0
<#
.SYNOPSIS
    Re-stamp .sza-canon.json from the installed canon plugin (S3455, S4060): canon.version,
    canon.coreDigest and canon.reconciledOn, written together by one command.

.DESCRIPTION
    The stamp's three freshness fields must move as one set. Hand edits moved version and digest
    without adoptedOn, leaving a stamp whose adoption date preceded the canon version it claimed -
    a state the compliance gate cannot tell from a genuine re-adoption. This script reads the pair
    from the plugin's own reader (`tools/check-compliance.ps1 -PrintDigest`), so the digest is
    never copied from anywhere else, and stamps today's date only when the pair actually changes.

    The date goes to canon.reconciledOn (contract REPO-STAMP rule 8): adoptedOn is the date of first
    adoption and a re-sync never moves it. A stamp without reconciledOn gets the key inserted right
    after adoptedOn. Fallback: a reader whose text never mentions reconciledOn ages the stamp from
    adoptedOn alone, so against such a reader adoptedOn moves too - otherwise a reconciled stamp would
    still read as 180 days stale (contract RULE-DELIVERY rule 5).

    The file is rewritten as text, the values in place, so its `$comment`, key order and layout
    survive byte for byte. Everything else in the stamp - overlay, channels, exemptions - is
    content the adopting session decides, not a freshness field, and is never touched here.

    What the re-stamp does NOT do is the reconciliation the adopt-canon skill asks for (re-reading
    the rule docs whose digest changed). Run that skill's step 7; this is its final write.

.PARAMETER RepoRoot
    Repository holding .sza-canon.json. Default: this script's repository.

.PARAMETER PluginRoot
    Canon plugin root. Default: the `sza@sza-unified-rules` entry of
    ~/.claude/plugins/installed_plugins.json with the newest lastUpdated.

.PARAMETER Date
    The reconciledOn value to write (and adoptedOn, against an old reader), yyyy-MM-dd.
    Default: today. Exists for the contract suite.

.PARAMETER DryRun
    Report what would be written, write nothing.

.NOTES
    Exit codes:
      0  stamp written, already current, or reported under -DryRun.
      1  the write failed.
      2  could not verify: no stamp, a stamp missing a field, no plugin root, or a reader output
         that does not parse.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot,
    [string]$PluginRoot,
    [string]$Date,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Stop-Unverified([string]$Text) {
    Write-Host "restamp-canon: COULD NOT VERIFY - $Text" -ForegroundColor Yellow
    exit 2
}

if (-not $RepoRoot) { $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$stampPath = Join-Path $RepoRoot '.sza-canon.json'
if (-not (Test-Path -LiteralPath $stampPath)) { Stop-Unverified "no stamp at $stampPath" }

if (-not $PluginRoot) {
    $homeDir = if ($env:USERPROFILE) { $env:USERPROFILE } else { $env:HOME }
    $installed = Join-Path $homeDir '.claude/plugins/installed_plugins.json'
    if (-not (Test-Path -LiteralPath $installed)) { Stop-Unverified "no plugin install record at $installed" }
    $entries = @((Get-Content -LiteralPath $installed -Raw | ConvertFrom-Json).plugins.'sza@sza-unified-rules')
    $newest = $entries | Where-Object { $_ } | Sort-Object lastUpdated -Descending | Select-Object -First 1
    if (-not $newest) { Stop-Unverified 'sza@sza-unified-rules is not installed; run: claude plugin install sza@sza-unified-rules' }
    $PluginRoot = $newest.installPath
}
$reader = Join-Path $PluginRoot 'tools/check-compliance.ps1'
if (-not (Test-Path -LiteralPath $reader)) { Stop-Unverified "no compliance reader at $reader" }
$readerHonoursReconciled = (Get-Content -LiteralPath $reader -Raw) -match 'reconciledOn'

$pwshExe = (Get-Process -Id $PID).Path
$readerOut = (& $pwshExe -NoProfile -File $reader -PrintDigest -RepoRoot $RepoRoot 2>&1 | Out-String)
$versionMatch = [regex]::Match($readerOut, '(?m)^canon version:\s*(\S+)')
$digestMatch = [regex]::Match($readerOut, '(?m)^coreDigest:\s*(sha256:[0-9a-f]{64})')
if (-not $versionMatch.Success -or -not $digestMatch.Success) {
    Stop-Unverified "the reader's -PrintDigest output did not parse: $($readerOut.Trim())"
}
$newVersion = $versionMatch.Groups[1].Value
$newDigest = $digestMatch.Groups[1].Value
if (-not $Date) { $Date = (Get-Date).ToString('yyyy-MM-dd') }

$raw = [System.IO.File]::ReadAllBytes($stampPath)
$hadBom = $raw.Length -ge 3 -and $raw[0] -eq 0xEF -and $raw[1] -eq 0xBB -and $raw[2] -eq 0xBF
$text = [System.IO.File]::ReadAllText($stampPath)
$fields = [ordered]@{ version = $null; coreDigest = $null; adoptedOn = $null }
foreach ($key in @($fields.Keys)) {
    $m = [regex]::Match($text, '"' + $key + '"\s*:\s*"([^"]*)"')
    if (-not $m.Success) { Stop-Unverified "the stamp carries no canon.$key string" }
    $fields[$key] = $m.Groups[1].Value
}

Write-Host "restamp-canon: plugin $PluginRoot"
Write-Host "  canon.version    $($fields.version) -> $newVersion"
Write-Host "  canon.coreDigest $($fields.coreDigest) -> $newDigest"

if ($fields.version -eq $newVersion -and $fields.coreDigest -eq $newDigest) {
    Write-Host 'restamp-canon: CURRENT - the stamp already names the installed canon; nothing written.'
    exit 0
}
$reconciledMatch = [regex]::Match($text, '"reconciledOn"\s*:\s*"([^"]*)"')
$oldReconciled = if ($reconciledMatch.Success) { $reconciledMatch.Groups[1].Value } else { '(absent)' }
Write-Host "  canon.reconciledOn $oldReconciled -> $Date"
if ($readerHonoursReconciled) {
    Write-Host "  canon.adoptedOn  $($fields.adoptedOn) (kept: the reader ages the stamp from reconciledOn)"
}
else {
    Write-Host "  canon.adoptedOn  $($fields.adoptedOn) -> $Date (the reader does not know reconciledOn)"
}
if ($DryRun) {
    Write-Host 'restamp-canon: DRY RUN - nothing written.'
    exit 0
}

$targets = @{ version = $newVersion; coreDigest = $newDigest }
if (-not $readerHonoursReconciled) { $targets.adoptedOn = $Date }
if ($reconciledMatch.Success) { $targets.reconciledOn = $Date }
foreach ($key in $targets.Keys) {
    $pattern = '("' + $key + '"\s*:\s*")[^"]*(")'
    $value = $targets[$key]
    $text = [regex]::new($pattern).Replace($text, { param($m) $m.Groups[1].Value + $value + $m.Groups[2].Value }, 1)
}
if (-not $reconciledMatch.Success) {
    $eol = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $anchor = [regex]::new('(?m)^([ \t]*)("adoptedOn"\s*:\s*"[^"]*")(,?)')
    $text = $anchor.Replace($text, {
            param($m)
            $indent = $m.Groups[1].Value
            $line = $indent + '"reconciledOn": "' + $Date + '"' + $m.Groups[3].Value
            $indent + $m.Groups[2].Value + ',' + $eol + $line
        }, 1)
}

try {
    [System.IO.File]::WriteAllText($stampPath, $text, [System.Text.UTF8Encoding]::new($hadBom))
}
catch {
    Write-Host "restamp-canon: FAIL - could not write ${stampPath}: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
Write-Host 'restamp-canon: WRITTEN - re-run check-compliance.ps1 to confirm SZA-CANON03 is gone.'
exit 0
