[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string[]] $Paths,

    [Parameter(Mandatory = $false)]
    [string[]] $ExtraHighRiskPaths
)

# Agent Continuity Layer (S0268) - Dirty-tree guard.
# Classifies an intended set of edit paths against the current dirty tree.
# Informs only - never blocks. The agent owns the decision.
# See: PLAN/S0268_agent_continuity_layer.md  §5.5
#      scripts/agent_continuity/README.md    §6.4 (high-risk list)

$ErrorActionPreference = 'Stop'

# Baseline high-risk shared-infrastructure files (per README §6.4).
$baselineHighRisk = @(
    'CLAUDE.md',
    'AGENTS.md',
    'app_v2/build.gradle.kts'
)

$highRisk = @($baselineHighRisk)
if ($ExtraHighRiskPaths) {
    foreach ($p in $ExtraHighRiskPaths) { $highRisk += $p }
}

function Normalize([string]$p) {
    if ([string]::IsNullOrWhiteSpace($p)) { return $p }
    $p = $p.Trim('"').Trim("'").Trim()
    return ($p -replace '\\', '/').Trim('/')
}

# Resolve repo root and run git from there.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $repoRoot
try {
    try {
        $rawDirty = git status --porcelain 2>$null
    } catch {
        Write-Output "ERROR: git status failed: $($_.Exception.Message)"
        exit 1
    }

    $dirtySet = New-Object System.Collections.Generic.HashSet[string]
    if ($null -ne $rawDirty) {
        foreach ($line in $rawDirty) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            # Porcelain format: XY <space> path  (or  XY <space> orig -> new for renames)
            $payload = $line.Substring(3)
            if ($payload -match '\s->\s(.+)$') { $payload = $Matches[1] }
            $norm = Normalize $payload
            if ($norm) { [void]$dirtySet.Add($norm) }
        }
    }

    $normalizedTargets = @()
    foreach ($t in $Paths) {
        $n = Normalize $t
        if ($n) { $normalizedTargets += $n }
    }
    $normalizedHighRisk = @()
    foreach ($h in $highRisk) {
        $n = Normalize $h
        if ($n) { $normalizedHighRisk += $n }
    }

    $evidenceHighRisk = New-Object System.Collections.Generic.List[string]
    $evidenceSameFile = New-Object System.Collections.Generic.List[string]
    $evidenceSameArea = New-Object System.Collections.Generic.List[string]

    foreach ($t in $normalizedTargets) {
        $isHighRisk = $normalizedHighRisk -contains $t
        $isDirty = $dirtySet.Contains($t)
        if ($isHighRisk -and $isDirty) {
            $evidenceHighRisk.Add($t)
        } elseif ($isDirty) {
            $evidenceSameFile.Add($t)
        } else {
            # Same area: share a parent directory with any dirty path.
            $parent = Split-Path -Path $t -Parent
            if ($parent) {
                $parent = ($parent -replace '\\', '/').Trim('/')
                foreach ($d in $dirtySet) {
                    $dParent = Split-Path -Path $d -Parent
                    if ($dParent) {
                        $dParent = ($dParent -replace '\\', '/').Trim('/')
                        if ($parent -ne '' -and $parent -eq $dParent) {
                            $evidenceSameArea.Add("$t (overlaps with $d)")
                            break
                        }
                    }
                }
            }
        }
    }

    # Precedence: high-risk overlap > same file > same area > clean.
    $category = 'clean'
    $evidence = @()
    if ($evidenceHighRisk.Count -gt 0) {
        $category = 'high-risk overlap'
        $evidence = $evidenceHighRisk.ToArray()
    } elseif ($evidenceSameFile.Count -gt 0) {
        $category = 'same file'
        $evidence = $evidenceSameFile.ToArray()
    } elseif ($evidenceSameArea.Count -gt 0) {
        $category = 'same area'
        $evidence = $evidenceSameArea.ToArray()
    }

    Write-Output "category=$category"
    if ($evidence.Count -gt 0) {
        Write-Output ("evidence: " + ($evidence -join '; '))
    } else {
        Write-Output 'evidence:'
    }
    Write-Output "Guard informs - it does not block. Decision is the agent's."
    exit 0
}
finally {
    Pop-Location
}
