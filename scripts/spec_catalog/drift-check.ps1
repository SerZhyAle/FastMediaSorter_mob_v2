# drift-check.ps1 - Detect "fix already in code" drift for a spec
#
# Pattern this catches:
#   Spec is written at time T0 describing a broken behaviour.
#   Code fix is committed at time T1 > T0 with a // Sxxxx: marker.
#   No one updates the spec.
#   /spec-all on the spec wastes 5+ minutes "implementing" what is already done.
#
# Usage:
#   pwsh -File scripts/spec_catalog/drift-check.ps1 -Id Sxxxx
#   pwsh -File scripts/spec_catalog/drift-check.ps1 -Id Sxxxx -Format json
#
# Output (table):
#   S0235 drift-check (since 2026-05-17 01:52)
#     git commits with Sxxxx marker:  2  (most recent: ace216c4 2026-05-17 05:17)
#     code lines with // Sxxxx::      3  in 2 files
#     verdict: DRIFT - fix likely in code, spec stale
#
# Output (json):
#   {"id":"S0235","verdict":"DRIFT","commits":[...],"code_markers":[...]}
#
# Exit codes:
#   0 - no drift detected (clean)
#   1 - drift detected (commits and/or markers found post-spec-creation)
#   2 - usage / resolution error

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Id,
    [ValidateSet('table', 'json')]
    [string]$Format = 'table'
)

$ErrorActionPreference = 'Stop'

if ($Id -notmatch '^S\d{4}$') {
    Write-Error "Invalid -Id '$Id' (must match S####)"
    exit 2
}

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$pwshExe = if (Test-Path "$env:ProgramFiles\PowerShell\7\pwsh.exe") {
    "$env:ProgramFiles\PowerShell\7\pwsh.exe"
} else {
    'pwsh'
}

# Resolve spec metadata via select.ps1
$selectPath = Join-Path $PSScriptRoot 'select.ps1'
$json = & $pwshExe -File $selectPath -Id $Id -Format json 2>$null
if (-not $json -or $json -eq '[]') {
    Write-Error "Spec $Id not found in catalog"
    exit 2
}
$rec = $json | ConvertFrom-Json
if ($rec -is [array]) { $rec = $rec[0] }

$created = $rec.created
$updated = $rec.updated
$specFile = Join-Path $root $rec.file

# git log -S works best with an iso-style date; fall back to "created" if "updated" malformed
$gitSince = $created
if ($updated -match '^\d{4}-\d{2}-\d{2}') {
    # Prefer the earliest of created/updated so we don't miss in-flight commits.
    $gitSince = ($created, $updated | Sort-Object | Select-Object -First 1)
}

Push-Location $root
try {
    # 1. git commits whose patch contains the spec id marker
    $rawCommits = git log --since="$gitSince" -S "$Id" --pretty=format:"%H|%ai|%s" 2>$null
    $commits = @()
    if ($rawCommits) {
        $commits = @($rawCommits | ForEach-Object {
                $parts = $_ -split '\|', 3
                if ($parts.Count -eq 3) {
                    [PSCustomObject]@{
                        sha     = $parts[0].Substring(0, [Math]::Min(8, $parts[0].Length))
                        date    = $parts[1]
                        subject = $parts[2]
                    }
                }
            } | Where-Object { $_ })
    }

    # 2. inline code markers across .kt / .py / .xml / .md (excluding PLAN/ to avoid spec itself)
    $markers = @()
    $patterns = @(
        @{ Ext = '*.kt';  Glob = 'app_v2/src/**/*.kt' },
        @{ Ext = '*.py';  Glob = 'app_v2/src/**/*.py' },
        @{ Ext = '*.xml'; Glob = 'app_v2/src/**/*.xml' }
    )

    # Use ripgrep if available (much faster on Windows); fall back to git grep.
    $rgExe = Get-Command rg -ErrorAction SilentlyContinue
    if ($rgExe) {
        $rgOut = & rg --no-heading --line-number --color=never "(//|#|<!--)\s*$Id[:\s]" `
            'app_v2/src' 2>$null
        if ($rgOut) {
            $markers = @($rgOut | ForEach-Object {
                    $parts = $_ -split ':', 3
                    if ($parts.Count -ge 3) {
                        [PSCustomObject]@{
                            file = $parts[0]
                            line = [int]$parts[1]
                            text = ($parts[2].Trim() -replace '\s+', ' ')
                        }
                    }
                } | Where-Object { $_ })
        }
    }
    else {
        # git grep fallback
        $ggOut = git grep -n -E "(//|#|<!--)\s+$Id[:\s]" -- 'app_v2/src/**' 2>$null
        if ($ggOut) {
            $markers = @($ggOut | ForEach-Object {
                    $parts = $_ -split ':', 3
                    if ($parts.Count -ge 3) {
                        [PSCustomObject]@{
                            file = $parts[0]
                            line = [int]$parts[1]
                            text = ($parts[2].Trim() -replace '\s+', ' ')
                        }
                    }
                } | Where-Object { $_ })
        }
    }

    # 3. Verdict
    $verdict = if ($commits.Count -gt 0 -or $markers.Count -gt 0) { 'DRIFT' } else { 'CLEAN' }

    if ($Format -eq 'json') {
        $result = [PSCustomObject]@{
            id            = $Id
            verdict       = $verdict
            spec_file     = $rec.file
            spec_status   = $rec.status
            spec_created  = $created
            spec_updated  = $updated
            git_since     = $gitSince
            commits       = $commits
            code_markers  = $markers
            commits_count = $commits.Count
            markers_count = $markers.Count
        }
        $result | ConvertTo-Json -Depth 6 -Compress
    }
    else {
        Write-Host "$Id drift-check (since $gitSince, status=$($rec.status))" -ForegroundColor Cyan
        Write-Host "  spec file: $($rec.file)" -ForegroundColor DarkGray
        $cColor = if ($commits.Count -gt 0) { 'Yellow' } else { 'DarkGray' }
        Write-Host "  git commits with Sxxxx marker: $($commits.Count)" -ForegroundColor $cColor
        if ($commits.Count -gt 0) {
            $commits | Select-Object -First 3 | ForEach-Object {
                Write-Host "    $($_.sha) $($_.date) $($_.subject)" -ForegroundColor DarkGray
            }
            if ($commits.Count -gt 3) {
                Write-Host "    .. ($($commits.Count - 3) more)" -ForegroundColor DarkGray
            }
        }
        $mColor = if ($markers.Count -gt 0) { 'Yellow' } else { 'DarkGray' }
        $files = ($markers | Select-Object -ExpandProperty file -Unique).Count
        Write-Host "  code markers ($Id`:): $($markers.Count) in $files file(s)" -ForegroundColor $mColor
        if ($markers.Count -gt 0) {
            $markers | Select-Object -First 3 | ForEach-Object {
                Write-Host "    $($_.file):$($_.line)" -ForegroundColor DarkGray
            }
            if ($markers.Count -gt 3) {
                Write-Host "    .. ($($markers.Count - 3) more)" -ForegroundColor DarkGray
            }
        }
        $vColor = if ($verdict -eq 'DRIFT') { 'Yellow' } else { 'Green' }
        Write-Host "  verdict: $verdict" -ForegroundColor $vColor
        if ($verdict -eq 'DRIFT') {
            Write-Host "  -> /spec-all may be partly/fully redundant; consider review-only audit + BlockNeedUserTest." -ForegroundColor DarkYellow
        }
    }

    if ($verdict -eq 'DRIFT') { exit 1 } else { exit 0 }
}
finally {
    Pop-Location
}
