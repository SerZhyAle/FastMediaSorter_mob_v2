<#
assert-hook-inventory.ps1 - S1604.

Fails when the set of REGISTERED Claude Code hooks and the inventory in
docs/AGENT_HOOKS.md disagree, in either direction.

Why this exists: five of the eleven registered hooks were named in no file an
agent reads, and two of those five alter the tool call itself - one refuses a
Grep/Glob, one rewrites Read input. An agent refused by an undocumented guard
cannot find out what refused it. The divergence is mechanically detectable, and
the batch that produced these hooks measured ungated rules at 1-8% against ~99%
for gated ones, so this is a gate rather than a sentence (S1604 ADR-2).

Asymmetric scope, by design (S1604 strategic section 6 item 1):
  - The PROJECT half (.claude/settings.json) is judged strictly and always. It
    is version-controlled, so the verdict reproduces on any machine.
  - The GLOBAL half (~/.claude/settings.json) is judged only when that file is
    readable. Where it is absent the gate prints one advisory line and does not
    fail - a red that cannot be fixed from the repository is a red that teaches
    people to bypass the gate.

A top-level .ps1 in .claude/hooks/ that is registered nowhere is reported as an
advisory, not a failure: it is dead weight rather than a documentation gap, and
the inventory deliberately lists live hooks only.

Usage:
    pwsh -NoProfile -File scripts/quality/assert-hook-inventory.ps1
    pwsh -NoProfile -File scripts/quality/assert-hook-inventory.ps1 -Gate

Exit codes (CLAUDE.md Rule 7):
  0  in sync, or a divergence was reported without -Gate (advisories may print in both)
  1  a real divergence between the registered set and the inventory, with -Gate
  2  could not verify - the inventory or .claude/settings.json is missing or unparsable
#>

[CmdletBinding()]
param(
    # Set by scripts/quality/assert-fast-gates.ps1. Turns a reported divergence into
    # a failing exit code, matching the convention of every sibling gate.
    [switch]$Gate,

    [string]$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,

    # Overridable so the contract tests can exercise the "global half absent"
    # branch against a fixture instead of the real per-machine settings file.
    [string]$GlobalSettingsPath = (Join-Path $HOME '.claude/settings.json')
)

$ErrorActionPreference = 'Stop'

$InventoryPath = Join-Path $RepoRoot 'docs/AGENT_HOOKS.md'
$ProjectSettings = Join-Path $RepoRoot '.claude/settings.json'
$ProjectHookDir = Join-Path $RepoRoot '.claude/hooks'
$GlobalSettings = $GlobalSettingsPath

$advisories = New-Object System.Collections.Generic.List[string]
$failures = New-Object System.Collections.Generic.List[string]

function Get-HookNamesFromSettings([string]$Path) {
    # Returns the set of <name>.ps1 referenced by any hook command in a settings file.
    $names = New-Object System.Collections.Generic.HashSet[string]
    $json = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    if (-not $json.PSObject.Properties['hooks']) { return $names }
    foreach ($event in $json.hooks.PSObject.Properties) {
        foreach ($group in @($event.Value)) {
            foreach ($hook in @($group.hooks)) {
                if (-not $hook.command) { continue }
                foreach ($m in [regex]::Matches($hook.command, '([A-Za-z0-9._-]+)\.ps1')) {
                    [void]$names.Add($m.Groups[1].Value)
                }
            }
        }
    }
    return $names
}

function Get-HookNamesFromInventory([string]$Path) {
    # Reads ONLY the "## Inventory" table's first column, so hook names mentioned
    # in the surrounding prose (this gate, a.ps1, query.ps1) are never mistaken
    # for inventory entries.
    $names = New-Object System.Collections.Generic.HashSet[string]
    $inTable = $false
    foreach ($line in (Get-Content -LiteralPath $Path)) {
        if ($line -match '^##\s') {
            $inTable = ($line -match '^##\s+Inventory\s*$')
            continue
        }
        if (-not $inTable) { continue }
        if ($line -notmatch '^\|') { continue }
        $first = ($line -split '\|')[1]
        if ($null -eq $first) { continue }
        $m = [regex]::Match($first.Trim(), '^`([A-Za-z0-9._-]+)\.ps1`$')
        if ($m.Success) { [void]$names.Add($m.Groups[1].Value) }
    }
    return $names
}

function Compare-Half([string]$Label, $Registered, $Inventory) {
    foreach ($n in ($Registered | Sort-Object)) {
        if (-not $Inventory.Contains($n)) {
            $failures.Add("$Label hook '$n.ps1' is registered but absent from docs/AGENT_HOOKS.md")
        }
    }
    foreach ($n in ($Inventory | Sort-Object)) {
        if (-not $Registered.Contains($n)) {
            $failures.Add("inventory names '$n.ps1' but it is registered in no settings file")
        }
    }
}

# --- read the inventory -------------------------------------------------------

if (-not (Test-Path -LiteralPath $InventoryPath)) {
    Write-Error "hook-inventory: docs/AGENT_HOOKS.md not found - cannot verify" -ErrorAction Continue
    exit 2
}

try {
    $inventory = Get-HookNamesFromInventory $InventoryPath
} catch {
    Write-Error "hook-inventory: could not parse docs/AGENT_HOOKS.md - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

if ($inventory.Count -eq 0) {
    Write-Error "hook-inventory: the '## Inventory' table in docs/AGENT_HOOKS.md yielded no hook names - cannot verify" -ErrorAction Continue
    exit 2
}

# --- project half: strict, always --------------------------------------------

if (-not (Test-Path -LiteralPath $ProjectSettings)) {
    Write-Error "hook-inventory: .claude/settings.json not found - cannot verify the project half" -ErrorAction Continue
    exit 2
}

try {
    $projectRegistered = Get-HookNamesFromSettings $ProjectSettings
} catch {
    Write-Error "hook-inventory: could not parse .claude/settings.json - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}

# --- global half: only when the per-machine settings file is readable ---------

$globalRegistered = New-Object System.Collections.Generic.HashSet[string]
$globalJudged = $false

if (Test-Path -LiteralPath $GlobalSettings) {
    try {
        $globalRegistered = Get-HookNamesFromSettings $GlobalSettings
        $globalJudged = $true
    } catch {
        $advisories.Add("~/.claude/settings.json is present but unparsable - the global half was not judged")
    }
} else {
    $advisories.Add("~/.claude/settings.json is absent on this machine - the global half was not judged")
}

# The inventory covers both homes, so compare it against the union of whatever
# was actually judged. Judging each half against the whole inventory would
# report every global hook as missing whenever the global file is absent.
$registered = New-Object System.Collections.Generic.HashSet[string]
foreach ($n in $projectRegistered) { [void]$registered.Add($n) }
foreach ($n in $globalRegistered) { [void]$registered.Add($n) }

if ($globalJudged) {
    Compare-Half 'registered' $registered $inventory
} else {
    # Only the project half can be judged in both directions. An inventory entry
    # that is not registered in the project settings may legitimately be a global
    # hook this machine does not carry, so it cannot be called a failure here.
    foreach ($n in ($projectRegistered | Sort-Object)) {
        if (-not $inventory.Contains($n)) {
            $failures.Add("project hook '$n.ps1' is registered but absent from docs/AGENT_HOOKS.md")
        }
    }
}

# --- advisory: a hook file that nothing registers ------------------------------

if (Test-Path -LiteralPath $ProjectHookDir) {
    foreach ($f in (Get-ChildItem -LiteralPath $ProjectHookDir -Filter '*.ps1' -File)) {
        if (-not $registered.Contains($f.BaseName)) {
            $advisories.Add("'.claude/hooks/$($f.Name)' is registered in no settings file - dead hook")
        }
    }
}

# --- verdict ------------------------------------------------------------------

foreach ($a in $advisories) { Write-Host "hook-inventory: ADVISORY - $a" }

if ($failures.Count -gt 0) {
    foreach ($f in $failures) { Write-Host "hook-inventory: FAIL - $f" }
    Write-Host "hook-inventory: expected: 0 | actual: $($failures.Count) divergence(s). Fix docs/AGENT_HOOKS.md or the registration - CLAUDE.md Rule 29."
    if ($Gate) { exit 1 }
    exit 0
}

$scope = if ($globalJudged) { 'project + global' } else { 'project only' }
Write-Host "hook-inventory: PASS ($($registered.Count) registered hook(s), $scope)"
exit 0
