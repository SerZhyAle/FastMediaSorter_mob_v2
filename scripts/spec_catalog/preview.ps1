# preview.ps1 - One-shot Stage 3 metadata extractor for a spec
#
# Replaces this bash boilerplate run on every /spec-next round:
#   head -25 PLAN/Sxxxx_*.md
#   grep -n "^## " PLAN/Sxxxx_*.md
#   ls -la PLAN/ | grep Sxxxx
#   grep "Timber\.d\(.Sxxxx:" type=kt
#   select.ps1 -Id Sxxxx
#   select.ps1 -Id <blocker> (for §10 resolution)
#
# All collapsed into one pwsh invocation, one JSON blob.
#
# Usage:
#   pwsh -File scripts/spec_catalog/preview.ps1 -Id Sxxxx
#   pwsh -File scripts/spec_catalog/preview.ps1 -Id Sxxxx -Format json
#
# Output (json):
#   {
#     "id":"S0235", "name":"...", "status":"Draft", "priority":80, "tier":3,
#     "file":"PLAN/S0235_*.md", "created":"...", "updated":"...",
#     "frontmatter": { "Status":"Draft", "Priority":"80", "Tier":"3", ... },
#     "sections": ["## 1. Problem", "## 2. Goals", ...],
#     "tactical_folder": false,
#     "last_audit_present": false,
#     "timber_tags_kt": 0,
#     "auto_skip": null,                  # or "tier-5-epic" / "owner-gate" / "blocker-not-verified"
#     "auto_skip_reason": null,           # human-readable reason
#     "depends_on": [ {"id":"S0241","status":"Verified"}, ... ],
#     "research_open_count": 5
#   }

[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Id,
    [ValidateSet('table', 'json')]
    [string]$Format = 'json'
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

function Get-SectionBodyByHeading {
    param(
        [string]$Text,
        [string[]]$HeadingPatterns
    )

    foreach ($match in [regex]::Matches($Text, '(?ms)^##\s+([^\r\n]+)\r?\n(.*?)(?=^\s*##\s+|\z)')) {
        $heading = $match.Groups[1].Value.Trim()
        foreach ($pattern in $HeadingPatterns) {
            if ($heading -match $pattern) {
                return $match.Groups[2].Value
            }
        }
    }

    return $null
}

function Get-UnresolvedResearchItemCount {
    param([string]$Text)

    $researchBody = Get-SectionBodyByHeading -Text $Text -HeadingPatterns @(
        '(?i)\bResearch\s+items?\b',
        '(?i)\bOpen\s+Questions?\b',
        '(?i)Открытые\s+вопрос'
    )
    if (-not $researchBody) {
        return 0
    }

    $explicitOpenCount = [regex]::Matches(
        $researchBody,
        '(?im)^\s*[-*]\s+\*\*(?:Статус|Status):\*\*\s*Open\b|^\s*[-*]\s*(?:Статус|Status):\s*Open\b'
    ).Count
    if ($explicitOpenCount -gt 0) {
        return $explicitOpenCount
    }

    $itemMatches = [regex]::Matches($researchBody, '(?ms)^(?:[-*]|\d+\.)\s+.*?(?=^(?:[-*]|\d+\.)\s+|\z)')
    $fallbackCount = 0
    foreach ($item in $itemMatches) {
        $block = $item.Value
        $hasQuestionMarker = $block -match '(?im)\*\*(?:Вопрос|Question):\*\*' -or $block -match '\?'
        $isResolved = $block -match '(?im)(?:\*\*(?:Статус|Status):\*\*\s*Resolved\b|\bStatus:\s*Resolved\b)'
        if ($hasQuestionMarker -and -not $isResolved) {
            $fallbackCount++
        }
    }

    return $fallbackCount
}

# 1. Resolve catalog record
$selectPath = Join-Path $PSScriptRoot 'select.ps1'
$catJson = & $pwshExe -File $selectPath -Id $Id -Format json 2>$null
if (-not $catJson -or $catJson -eq '[]') {
    Write-Error "Spec $Id not found in catalog"
    exit 2
}
$rec = $catJson | ConvertFrom-Json
if ($rec -is [array]) { $rec = $rec[0] }

$specPath = Join-Path $root $rec.file
if (-not (Test-Path $specPath)) {
    Write-Error "Spec file not found on disk: $($rec.file)"
    exit 2
}

# 2. Read spec body
$specText = Get-Content -Path $specPath -Raw

# 3. Parse frontmatter and key fields (both YAML-block and "**Status:** X" forms supported).
$frontmatter = [ordered]@{}
$yamlMatch = [regex]::Match($specText, '(?s)^---\s*\n(.*?)\n---\s*\n')
if ($yamlMatch.Success) {
    foreach ($line in ($yamlMatch.Groups[1].Value -split "`n")) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_-]*)\s*:\s*(.+?)\s*$') {
            $frontmatter[$matches[1]] = $matches[2].Trim()
        }
    }
}
# Also pick up "**Status:** Draft" / "**Tier:** 5" header style.
foreach ($key in 'Status', 'Priority', 'Tier', 'Date') {
    $pattern = "^\*\*$key`:\*\*\s*([^\r\n]+?)\s*$"
    $m = [regex]::Match($specText, $pattern, 'Multiline')
    if ($m.Success -and -not $frontmatter.Contains($key)) {
        $frontmatter[$key] = $m.Groups[1].Value.Trim()
    }
}

# 4. Section list
$sections = @()
foreach ($line in ($specText -split "`n")) {
    if ($line -match '^##\s+(.+?)\s*$') {
        $sections += $line.Trim()
    }
}

# 5. Tactical folder
$tacticalFolder = $false
$folderCandidate = $specPath -replace '\.md$', ''
if (Test-Path $folderCandidate -PathType Container) {
    $tacticalFolder = $true
}

# 6. Last Audit block present?
$lastAuditPresent = ($specText -match '(?m)^##\s+Last\s+Audit\b')

# 7. Timber tag count across .kt
$timberCount = 0
Push-Location $root
try {
    $rgExe = Get-Command rg -ErrorAction SilentlyContinue
    if ($rgExe) {
        $tags = & rg --no-heading --count-matches -t kotlin "Timber\.d\(`"$Id`:" 'app_v2/src' 2>$null
        if ($tags) {
            $timberCount = (($tags | ForEach-Object { ($_ -split ':')[-1] }) | Measure-Object -Sum).Sum
        }
    }
    else {
        $tagFiles = git grep -l -E "Timber\.d\(`"$Id`:" -- 'app_v2/src/**/*.kt' 2>$null
        if ($tagFiles) {
            foreach ($f in $tagFiles) {
                $hits = git grep -c -E "Timber\.d\(`"$Id`:" -- $f 2>$null
                if ($hits -match ':(\d+)$') {
                    $timberCount += [int]$matches[1]
                }
            }
        }
    }
}
finally {
    Pop-Location
}

# 8. Depends-on resolution (§10 of strategic spec - look for "Depends on:" and Sxxxx tokens nearby)
$dependsOn = @()
$depMatch = [regex]::Match($specText, '(?ms)\*\*Depends on:\*\*\s*(.+?)(?:^\*\*|\r?\n##\s)')
if (-not $depMatch.Success) {
    # Alternative: §10 Связи / Related Specs section
    $sec10Match = [regex]::Match($specText, '(?ms)^##\s+10\.[^\n]*\n(.+?)(?:\r?\n##\s|\z)')
    if ($sec10Match.Success) { $depMatch = $sec10Match }
}
if ($depMatch.Success) {
    $depText = $depMatch.Groups[1].Value
    $depIds = [regex]::Matches($depText, '\bS\d{4}\b') |
        ForEach-Object { $_.Value } |
        Sort-Object -Unique |
        Where-Object { $_ -ne $Id }
    foreach ($dep in $depIds) {
        $depJson = & $pwshExe -File $selectPath -Id $dep -Format json 2>$null
        if ($depJson -and $depJson -ne '[]') {
            $depRec = $depJson | ConvertFrom-Json
            if ($depRec -is [array]) { $depRec = $depRec[0] }
            $dependsOn += [PSCustomObject]@{
                id     = $dep
                status = $depRec.status
            }
        }
        else {
            $dependsOn += [PSCustomObject]@{ id = $dep; status = '?' }
        }
    }
}

# 9. Research item count (heading-based, section-number agnostic; unresolved only)
$researchOpenCount = Get-UnresolvedResearchItemCount -Text $specText

# 10. Owner-gate detection
$ownerGate = $false
$ownerGatePatterns = @(
    'автоматическая\s+передача\s+отключена',
    'запуск\s+выполняется\s+отдельной\s+командой',
    'owner\s+directive',
    'manual\s+handoff\s+required'
)
foreach ($p in $ownerGatePatterns) {
    if ($specText -match $p) { $ownerGate = $true; break }
}

# 11. Auto-skip verdict
$autoSkip = $null
$autoSkipReason = $null
$tierVal = if ($frontmatter['Tier']) { $frontmatter['Tier'] } else { '' }
if ($tierVal -match '^\s*5') {
    $autoSkip = 'tier-5-epic'
    $autoSkipReason = 'Tier 5 epic-container, no code under its id'
}
elseif ($ownerGate) {
    $autoSkip = 'owner-gate'
    $autoSkipReason = 'Spec explicitly forbids automatic handoff (§12 owner directive)'
}
elseif ($rec.status -eq 'BlockByOtherTask' -and $dependsOn.Count -gt 0) {
    $unverifiedBlockers = $dependsOn | Where-Object { $_.status -ne 'Verified' -and $_.status -ne 'Archived' }
    if ($unverifiedBlockers.Count -gt 0) {
        $autoSkip = 'blocker-not-verified'
        $autoSkipReason = 'Depends on ' + ($unverifiedBlockers | ForEach-Object { "$($_.id)($($_.status))" }) -join ', '
    }
}
elseif ($researchOpenCount -ge 3 -and $rec.status -in @('Draft', 'Approved')) {
    $autoSkip = 'research-heavy'
    $autoSkipReason = "Research section has $researchOpenCount unresolved items; /spec-all would block"
}

# 12. Compose result
$result = [PSCustomObject]@{
    id                  = $rec.id
    name                = $rec.name
    status              = $rec.status
    priority            = $rec.priority
    tier                = $tierVal
    file                = $rec.file
    created             = $rec.created
    updated             = $rec.updated
    frontmatter         = $frontmatter
    sections            = $sections
    tactical_folder     = $tacticalFolder
    last_audit_present  = [bool]$lastAuditPresent
    timber_tags_kt      = $timberCount
    depends_on          = $dependsOn
    research_open_count = $researchOpenCount
    owner_gate          = $ownerGate
    auto_skip           = $autoSkip
    auto_skip_reason    = $autoSkipReason
}

if ($Format -eq 'json') {
    $result | ConvertTo-Json -Depth 6 -Compress
}
else {
    Write-Host "$($result.id) preview" -ForegroundColor Cyan
    Write-Host "  $($result.name)" -ForegroundColor White
    Write-Host "  status: $($result.status) | priority: $($result.priority) | tier: $($result.tier)" -ForegroundColor DarkGray
    Write-Host "  file: $($result.file)" -ForegroundColor DarkGray
    Write-Host "  tactical_folder: $($result.tactical_folder) | last_audit: $($result.last_audit_present) | timber: $($result.timber_tags_kt)" -ForegroundColor DarkGray
    if ($result.depends_on.Count -gt 0) {
        $depStr = ($result.depends_on | ForEach-Object { "$($_.id)($($_.status))" }) -join ', '
        Write-Host "  depends_on: $depStr" -ForegroundColor DarkGray
    }
    Write-Host "  sections: $($result.sections.Count) (research_open=$($result.research_open_count), owner_gate=$($result.owner_gate))" -ForegroundColor DarkGray
    if ($result.auto_skip) {
        Write-Host "  AUTO-SKIP: $($result.auto_skip) - $($result.auto_skip_reason)" -ForegroundColor Yellow
    }
}

exit 0
