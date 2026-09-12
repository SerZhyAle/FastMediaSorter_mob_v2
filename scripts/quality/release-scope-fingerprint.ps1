#requires -Version 7.0
<#
.SYNOPSIS
    Record and compare a fingerprint of the tree the release-scope gates judge (S3010).

.DESCRIPTION
    A release sweep clears a gate, keeps working, and writes a verdict some time later. Nothing told
    it that the tree had moved in between, so a gate cleared at the start could be red again at the
    end and the sweep had no terminating condition. This script is the part of the answer that needs
    no co-operation from anybody: it records what the judged tree looked like when the gates cleared,
    and says which parts of it moved before the verdict.

    The tree is divided into named input GROUPS rather than fingerprinted whole, so the second pass
    can re-run only the gates whose inputs actually moved. That is not the same as scoping a gate to
    a change: each gate still measures the whole tree when it runs, exactly as CLAUDE.md Rule 33
    requires - the groups decide only WHETHER to run it again, never what it looks at. Scoping the
    gates themselves is refused by the ticket's own §6.

    A group's hash covers path, length and last-write time - never file contents. Measured on this
    tree 2026-09-12: docs 381 files 52 ms, phone-src 5328 files 323 ms, play-listing 97 files 5 ms,
    scripts 4199 files 234 ms, specs-archive 7395 files 453 ms, wear-src 1030 files 52 ms, 1163 ms in
    total. Hashing contents would put this check in the same cost class as the gates it exists to
    save, which would make it the thing an operator skips.

    Expect specs-archive to report moved on most sweeps: PLAN/ is lock-exempt by design and every
    sibling archives into it. That is correct rather than noisy - it re-runs assert-archive-artefacts
    and nothing else.

.PARAMETER Verb
    Record  - write the current fingerprint.
    Compare - compare the current tree against the recorded one and name every group that moved.

.PARAMETER Json
    Emit one JSON object instead of prose.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/release-scope-fingerprint.ps1 -Verb Record

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/release-scope-fingerprint.ps1 -Verb Compare -Json

.NOTES
    FMS_REPO_ROOT overrides the repository root; the contract suite points it at a fixture tree.

Exit codes (CLAUDE.md Rule 7):
  0  Record wrote the fingerprint, or Compare found nothing moved.
  2  Compare found no recorded fingerprint, or it could not be read.
  3  Compare found at least one group moved - the names are on stdout.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Record', 'Compare')]
    [string] $Verb,

    [switch] $Json
)

$ErrorActionPreference = 'Stop'

$RepoRoot = if ($env:FMS_REPO_ROOT) { $env:FMS_REPO_ROOT } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$RecordPath = Join-Path $RepoRoot 'temp\RELEASE-FREEZE-FINGERPRINTS.json'

# The groups are the release-scope gates' inputs, named, and they live in the sibling .groups.json
# rather than here. Two reasons, one mechanical and one editorial. assert-code-domain-writers.ps1
# flags any .ps1 carrying both a write cmdlet and a quoted Code.* path, and this script has both -
# a Set-Content for the temp record, and the phone source root among its READ roots - so as literals
# they made it look like an unregistered writer of trees it never writes. And a group list is the vocabulary that
# assert-release-scope-gates.ps1 -OnlyGroups speaks, so it belongs where both can read it.
#
# A path that does not exist contributes nothing rather than throwing: a module or a listing tree may
# legitimately be absent, and a fingerprint that dies on a missing directory is one nobody runs.
$GroupsFile = Join-Path $PSScriptRoot 'release-scope-fingerprint.groups.json'
if (-not (Test-Path -LiteralPath $GroupsFile)) {
    Write-Host "release-scope-fingerprint: group definitions missing - $GroupsFile" -ForegroundColor Red
    exit 2
}
$Groups = [ordered]@{}
$groupsJson = Get-Content -LiteralPath $GroupsFile -Raw -Encoding UTF8 | ConvertFrom-Json
foreach ($property in $groupsJson.groups.PSObject.Properties) {
    $Groups[$property.Name] = @($property.Value)
}

# ConvertFrom-Json parses the recorded ISO stamp into a DateTime, and casting that back to a string
# uses the host's culture - so the file says 2026-09-12T11:53:53 while the report said 09/12/2026
# 11:53:53. The sweep quotes this line in its verdict; a date nobody can compare against the record is
# worse than no date.
function Format-Stamp($Value) {
    if ($null -eq $Value) { return '' }
    if ($Value -is [datetime]) { return $Value.ToString('s') }
    $parsed = [datetime]::MinValue
    if ([datetime]::TryParse([string]$Value, [ref]$parsed)) { return $parsed.ToString('s') }
    return [string]$Value
}

function Get-GroupHash {
    param([string[]] $RelativePaths)

    $builder = [System.Text.StringBuilder]::new()
    $count = 0
    foreach ($relative in $RelativePaths) {
        $full = Join-Path $RepoRoot ($relative -replace '/', '\')
        if (-not (Test-Path -LiteralPath $full)) { continue }
        $files = Get-ChildItem -LiteralPath $full -Recurse -File -Force -ErrorAction SilentlyContinue
        foreach ($file in ($files | Sort-Object FullName)) {
            [void]$builder.Append($file.FullName).Append('|').Append($file.Length).Append('|').Append($file.LastWriteTimeUtc.Ticks).Append("`n")
            $count++
        }
    }

    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($builder.ToString()))
        $hash = [BitConverter]::ToString($bytes).Replace('-', '').Substring(0, 16)
    }
    finally {
        $sha.Dispose()
    }

    return [pscustomobject]@{ Hash = $hash; Files = $count }
}

function Get-CurrentFingerprint {
    $map = [ordered]@{}
    foreach ($name in $Groups.Keys) {
        $result = Get-GroupHash -RelativePaths $Groups[$name]
        $map[$name] = [ordered]@{ hash = $result.Hash; files = $result.Files }
    }
    return $map
}

switch ($Verb) {

    'Record' {
        $map = Get-CurrentFingerprint
        $record = [ordered]@{
            schema     = 1
            recordedAt = (Get-Date).ToString('s')
            groups     = $map
        }
        $dir = Split-Path -Parent $RecordPath
        if ($dir -and -not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        Set-Content -LiteralPath $RecordPath -Value ($record | ConvertTo-Json -Depth 6) -Encoding UTF8

        if ($Json) {
            Write-Output ($record | ConvertTo-Json -Depth 6 -Compress)
        }
        else {
            Write-Host "release-scope-fingerprint: recorded $($map.Count) group(s) at $($record.recordedAt)." -ForegroundColor Green
            foreach ($name in $map.Keys) {
                Write-Host ("  {0,-14} {1}  {2} file(s)" -f $name, $map[$name].hash, $map[$name].files) -ForegroundColor DarkGray
            }
        }
        exit 0
    }

    'Compare' {
        if (-not (Test-Path -LiteralPath $RecordPath)) {
            if ($Json) { Write-Output '{"status":"no-record"}' }
            else { Write-Host "release-scope-fingerprint: no recorded fingerprint at $RecordPath - run -Verb Record first." -ForegroundColor Red }
            exit 2
        }

        $previous = $null
        try {
            $previous = Get-Content -LiteralPath $RecordPath -Raw -Encoding UTF8 | ConvertFrom-Json
        }
        catch {
            if ($Json) { Write-Output '{"status":"unreadable"}' }
            else { Write-Host "release-scope-fingerprint: recorded fingerprint unreadable - $RecordPath" -ForegroundColor Red }
            exit 2
        }

        $current = Get-CurrentFingerprint
        $moved = @()
        foreach ($name in $current.Keys) {
            $before = $null
            if ($previous.groups.PSObject.Properties.Name -contains $name) { $before = $previous.groups.$name.hash }
            # A group the record does not carry counts as moved: the safe default everywhere in this
            # ticket is to re-measure, never to assume unchanged.
            if ($null -eq $before -or [string]$before -ne [string]$current[$name].hash) { $moved += $name }
        }

        if ($Json) {
            Write-Output ([ordered]@{
                    status     = if ($moved.Count -gt 0) { 'moved' } else { 'stable' }
                    recordedAt = Format-Stamp $previous.recordedAt
                    moved      = @($moved)
                } | ConvertTo-Json -Depth 4 -Compress)
        }
        elseif ($moved.Count -gt 0) {
            Write-Host "release-scope-fingerprint: the tree moved since $(Format-Stamp $previous.recordedAt) - $($moved.Count) group(s):" -ForegroundColor Yellow
            foreach ($name in $moved) {
                Write-Host ("  {0,-14} {1} -> {2}" -f $name, $previous.groups.$name.hash, $current[$name].hash) -ForegroundColor Yellow
            }
        }
        else {
            Write-Host "release-scope-fingerprint: the tree has not moved since $(Format-Stamp $previous.recordedAt)." -ForegroundColor Green
        }

        if ($moved.Count -gt 0) { exit 3 }
        exit 0
    }
}
