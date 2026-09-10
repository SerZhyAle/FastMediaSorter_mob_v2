<#
run-tests.ps1 - contract tests for assert-hook-inventory.ps1 (S1604).

Every case builds a throwaway repository root under the system temp directory and
runs the gate against it, so no test touches the live tree or the real
~/.claude/settings.json. The "global half absent" branch is reachable only
because the gate accepts -GlobalSettingsPath.

Exit codes (CLAUDE.md Rule 7):
  0  every case passed
  1  at least one case failed
#>

[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

$Gate = (Resolve-Path (Join-Path $PSScriptRoot '..\assert-hook-inventory.ps1')).Path
$passed = 0
$failed = 0

function New-Fixture {
    param(
        [string[]]$ProjectHooks = @(),
        [string[]]$GlobalHooks = @(),
        [string[]]$InventoryHooks = @(),
        [switch]$OmitInventory,
        [switch]$OmitProjectSettings,
        [switch]$EmptyInventoryTable,

        # S2872 - the rule sheet the gate's third comparison reads. $null means "name exactly the
        # inventory hooks under '## The rules'", which is the shape every pre-S2872 case assumed
        # without knowing it, so those cases needed no edit beyond this default.
        [string[]]$SheetRuleHooks = $null,
        [string[]]$SheetNotPortableHooks = @(),
        [switch]$OmitRuleSheet
    )

    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("s1604-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path (Join-Path $root '.claude/hooks') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'docs') -Force | Out-Null

    if (-not $OmitProjectSettings) {
        $entries = @($ProjectHooks | ForEach-Object {
            [pscustomobject]@{ hooks = @([pscustomobject]@{ type = 'command'; command = "pwsh -NoProfile -File `"`$CLAUDE_PROJECT_DIR/.claude/hooks/$_.ps1`"" }) }
        })
        $settings = [pscustomobject]@{ hooks = [pscustomobject]@{ PreToolUse = $entries } }
        $settings | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath (Join-Path $root '.claude/settings.json') -Encoding utf8
    }

    $globalPath = Join-Path $root 'global-settings.json'
    if ($GlobalHooks.Count -gt 0) {
        $gentries = @($GlobalHooks | ForEach-Object {
            [pscustomobject]@{ hooks = @([pscustomobject]@{ type = 'command'; command = "pwsh -NoProfile -File `"~/.claude/hooks/$_.ps1`"" }) }
        })
        $gsettings = [pscustomobject]@{ hooks = [pscustomobject]@{ PreToolUse = $gentries } }
        $gsettings | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $globalPath -Encoding utf8
    }

    if (-not $OmitInventory) {
        $lines = New-Object System.Collections.Generic.List[string]
        $lines.Add('# Agent Hooks')
        $lines.Add('')
        $lines.Add('## Inventory')
        $lines.Add('')
        if (-not $EmptyInventoryTable) {
            $lines.Add('| Hook | Event / matcher | Verdict | Home | Rule | Contract tests |')
            $lines.Add('|------|-----------------|---------|------|:----:|----------------|')
            foreach ($h in $InventoryHooks) {
                $lines.Add("| ``$h.ps1`` | PreToolUse / Bash | refuses | project | - | - |")
            }
        }
        $lines.Add('')
        $lines.Add('## Contracts')
        $lines.Add('')
        # Prose naming a .ps1 that is NOT a hook - the gate must ignore it.
        $lines.Add('Enforced by ``scripts/quality/assert-hook-inventory.ps1`` inside ``a.ps1 fg``.')
        $lines | Set-Content -LiteralPath (Join-Path $root 'docs/AGENT_HOOKS.md') -Encoding utf8
    }

    if (-not $OmitRuleSheet) {
        $sheetRules = if ($null -ne $SheetRuleHooks) { $SheetRuleHooks } else { $InventoryHooks }
        $sheet = New-Object System.Collections.Generic.List[string]
        $sheet.Add('# Rules you enforce yourself')
        $sheet.Add('')
        $sheet.Add('## The rules')
        $sheet.Add('')
        $i = 0
        foreach ($h in $sheetRules) { $i++; $sheet.Add("$i. Do the thing (``$h``).") }
        $sheet.Add('')
        $sheet.Add('## Before you say done')
        $sheet.Add('')
        # Names a hook OUTSIDE the two covering sections: the gate must not count it as covered.
        $sheet.Add('Run ``scripts/utils/preflight-checks.ps1``, which is not ``guard-uncovered-elsewhere``.')
        $sheet.Add('')
        $sheet.Add('## Not portable')
        $sheet.Add('')
        foreach ($h in $SheetNotPortableHooks) { $sheet.Add("- ``$h`` gives you no rule to follow.") }
        $sheet | Set-Content -LiteralPath (Join-Path $root 'docs/NON_CLAUDE_RUNTIME_RULES.md') -Encoding utf8
    }

    return [pscustomobject]@{ Root = $root; GlobalPath = $globalPath }
}

function Invoke-Case {
    param(
        [string]$Name,
        [int]$ExpectedExit,
        [string]$ExpectedText,
        [hashtable]$Fixture
    )

    $fx = New-Fixture @Fixture
    try {
        # -Gate is what assert-fast-gates.ps1 passes, and it is the mode that turns a
        # reported divergence into a failing exit code - so it is the mode under test.
        $out = & pwsh -NoProfile -File $Gate -Gate -RepoRoot $fx.Root -GlobalSettingsPath $fx.GlobalPath 2>&1
        $code = $LASTEXITCODE
        $text = ($out | Out-String)

        $ok = ($code -eq $ExpectedExit)
        if ($ok -and $ExpectedText) { $ok = $text -match [regex]::Escape($ExpectedText) }

        if ($ok) {
            $script:passed++
            Write-Host "  PASS  $Name"
        } else {
            $script:failed++
            Write-Host "  FAIL  $Name"
            Write-Host "        expected: exit $ExpectedExit$(if ($ExpectedText) { " containing '$ExpectedText'" })"
            Write-Host "        actual:   exit $code"
            Write-Host "        output:   $($text.Trim())"
        }
    } finally {
        Remove-Item -LiteralPath $fx.Root -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "assert-hook-inventory contract tests"

Invoke-Case -Name 'matched set exits 0' -ExpectedExit 0 -ExpectedText 'PASS' -Fixture @{
    ProjectHooks   = @('guard-alpha')
    GlobalHooks    = @('guard-beta')
    InventoryHooks = @('guard-alpha', 'guard-beta')
}

Invoke-Case -Name 'registered but undocumented fails 1' -ExpectedExit 1 -ExpectedText 'absent from docs/AGENT_HOOKS.md' -Fixture @{
    ProjectHooks   = @('guard-alpha', 'guard-undocumented')
    GlobalHooks    = @('guard-beta')
    InventoryHooks = @('guard-alpha', 'guard-beta')
}

Invoke-Case -Name 'documented but unregistered fails 1' -ExpectedExit 1 -ExpectedText 'registered in no settings file' -Fixture @{
    ProjectHooks   = @('guard-alpha')
    GlobalHooks    = @('guard-beta')
    InventoryHooks = @('guard-alpha', 'guard-beta', 'guard-ghost')
}

Invoke-Case -Name 'absent global settings does not fail' -ExpectedExit 0 -ExpectedText 'the global half was not judged' -Fixture @{
    ProjectHooks   = @('guard-alpha')
    GlobalHooks    = @()
    InventoryHooks = @('guard-alpha', 'guard-beta')
}

Invoke-Case -Name 'absent global settings still judges the project half' -ExpectedExit 1 -ExpectedText 'absent from docs/AGENT_HOOKS.md' -Fixture @{
    ProjectHooks   = @('guard-alpha', 'guard-undocumented')
    GlobalHooks    = @()
    InventoryHooks = @('guard-alpha')
}

Invoke-Case -Name 'missing inventory cannot verify' -ExpectedExit 2 -ExpectedText 'not found' -Fixture @{
    ProjectHooks  = @('guard-alpha')
    OmitInventory = $true
}

Invoke-Case -Name 'empty inventory table cannot verify' -ExpectedExit 2 -ExpectedText 'no hook names' -Fixture @{
    ProjectHooks        = @('guard-alpha')
    InventoryHooks      = @()
    EmptyInventoryTable = $true
}

Invoke-Case -Name 'missing project settings cannot verify' -ExpectedExit 2 -ExpectedText 'settings.json not found' -Fixture @{
    InventoryHooks      = @('guard-alpha')
    OmitProjectSettings = $true
}

# --- S2872: the third comparison, the rule sheet ------------------------------
# The three cases below are the sheet's own failure modes. The first is the one the gate exists
# for; the second proves "Not portable" is a real answer and not a formality, so an author is
# never forced to invent an imperative for a hook that guards nothing they decide; the third
# keeps an absent sheet distinguishable from an incomplete one, because "the sheet is gone" and
# "the sheet forgot a hook" call for opposite reactions.

Invoke-Case -Name 'a hook missing from the rule sheet fails 1' -ExpectedExit 1 -ExpectedText 'named in neither section of docs/NON_CLAUDE_RUNTIME_RULES.md' -Fixture @{
    ProjectHooks   = @('guard-alpha', 'guard-beta')
    InventoryHooks = @('guard-alpha', 'guard-beta')
    SheetRuleHooks = @('guard-alpha')
}

Invoke-Case -Name 'a hook named only under Not portable is covered' -ExpectedExit 0 -ExpectedText 'PASS' -Fixture @{
    ProjectHooks          = @('guard-alpha', 'guard-beta')
    InventoryHooks        = @('guard-alpha', 'guard-beta')
    SheetRuleHooks        = @('guard-alpha')
    SheetNotPortableHooks = @('guard-beta')
}

Invoke-Case -Name 'a missing rule sheet cannot verify' -ExpectedExit 2 -ExpectedText 'NON_CLAUDE_RUNTIME_RULES.md not found' -Fixture @{
    ProjectHooks   = @('guard-alpha')
    InventoryHooks = @('guard-alpha')
    OmitRuleSheet  = $true
}

Write-Host ""
Write-Host "passed: $passed  failed: $failed"
if ($failed -gt 0) { exit 1 }
exit 0
