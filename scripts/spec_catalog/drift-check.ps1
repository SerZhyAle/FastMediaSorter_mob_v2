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
#     commit history: not consulted (S1634)
#     code lines with // Sxxxx::      3  in 2 files
#     verdict: DRIFT - fix likely in code, spec stale
#
# Output (json):
#   {"id":"S0235","verdict":"DRIFT","commits":[],"code_markers":[...]}
#
# Verdicts (S1429, narrowed by S1634):
#   CLEAN       - no inline marker carries the id.
#   DRIFT       - inline `// Sxxxx:` markers exist: work is in the tree that nothing accounts for.
#   COMMIT_ONLY - retired. Kept in the contract so an older caller still parses, never returned.
#                 It reported that a commit mentioned the id, which in a one-developer,
#                 one-branch, commit-the-whole-tree repository was true of nearly every ticket.
#
# Exit codes:
#   0 - no drift detected (CLEAN)
#   1 - drift detected (inline markers found post-spec-creation)
#   2 - usage / resolution error

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Id,
    [ValidateSet('table', 'json')]
    [string]$Format = 'table'
)

$ErrorActionPreference = 'Stop'

if ($Id -notmatch '^S\d{4}$') {
    Write-Error "Invalid -Id '$Id' (must match S####)" -ErrorAction Continue
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
    Write-Error "Spec $Id not found in catalog" -ErrorAction Continue
    exit 2
}
$rec = $json | ConvertFrom-Json
if ($rec -is [array]) { $rec = $rec[0] }

$created = $rec.created
$updated = $rec.updated
$specFile = Join-Path $root $rec.file

# Retained for the JSON contract only - it is no longer a git window, just the earlier of the two
# catalog dates, reported so a reader can see how old the ticket is.
$gitSince = $created
if ($updated -match '^\d{4}-\d{2}-\d{2}') {
    $gitSince = ($created, $updated | Sort-Object | Select-Object -First 1)
}

Push-Location $root
try {
    # 1. S1634: this check no longer asks git anything.
    #
    # It used to run `git log -S "<id>"` and report every commit whose patch mentioned the id. That
    # answer was never usable here: this repository has one developer on one branch who commits the
    # whole tree under a timestamp subject, so a routine snapshot carries dozens of ticket ids and
    # dates later than every tactical plan. The one consumer of the result - the freshness proof in
    # spec-next-preflight.ps1 - then declared every in-flight plan stale at once, and on 2026-08-14
    # deferred the two top tickets of the release package in consecutive rounds, both false, both
    # provable false by reading the tree.
    #
    # The working tree is the truth (CLAUDE.md "Working tree = truth"), and the tree already carries
    # the fact this check exists for: an inline `// Sxxxx:` marker is work sitting in the source that
    # nothing has accounted for. That is measured below, without a subprocess and without history.
    $commits = @()

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
    # S1429 split markers from commits so that a commit carrying the id could not park a ticket on
    # its own. S1634 finished the job: the commit half is gone, so only the tree can produce a
    # verdict. COMMIT_ONLY is kept in the contract as a value that is never returned any more -
    # a caller that still branches on it keeps working, it just never takes that branch.
    $verdict = if ($markers.Count -gt 0) { 'DRIFT' } else { 'CLEAN' }

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
        Write-Host "$Id drift-check (spec dated $gitSince, status=$($rec.status))" -ForegroundColor Cyan
        Write-Host "  spec file: $($rec.file)" -ForegroundColor DarkGray
        Write-Host "  commit history: not consulted (S1634)" -ForegroundColor DarkGray
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
