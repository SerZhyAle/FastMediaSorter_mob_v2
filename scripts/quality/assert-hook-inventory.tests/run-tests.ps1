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
        [switch]$OmitRuleSheet,

        # S2918 - the fourth comparison. $null servers = no MCP config file at all; $null rows = no
        # '## MCP servers' section. Both default to $null, so every earlier case sees neither.
        [string[]]$McpServers = $null,
        [object[]]$McpRows = $null,
        [string[]]$PreToolUseMatchers = @()
    )

    $root = Join-Path ([System.IO.Path]::GetTempPath()) ("s1604-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path (Join-Path $root '.claude/hooks') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $root 'docs') -Force | Out-Null

    if (-not $OmitProjectSettings) {
        $entries = @($ProjectHooks | ForEach-Object {
            [pscustomobject]@{ hooks = @([pscustomobject]@{ type = 'command'; command = "pwsh -NoProfile -File `"`$CLAUDE_PROJECT_DIR/.claude/hooks/$_.ps1`"" }) }
        })
        # A matcher group runs the first project hook, so it adds no hook name the inventory lacks.
        foreach ($m in $PreToolUseMatchers) {
            $entries += [pscustomobject]@{
                matcher = $m
                hooks   = @([pscustomobject]@{ type = 'command'; command = "pwsh -NoProfile -File `"`$CLAUDE_PROJECT_DIR/.claude/hooks/$($ProjectHooks[0]).ps1`"" })
            }
        }
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
        if ($null -ne $McpRows) {
            $lines.Add('## MCP servers')
            $lines.Add('')
            $lines.Add('| Server | Runtime | Config | Purpose | One-way tools | Guard |')
            $lines.Add('|--------|---------|--------|---------|---------------|-------|')
            foreach ($r in $McpRows) {
                $tools = if (@($r.Tools).Count -gt 0) { (@($r.Tools) | ForEach-Object { "``$_``" }) -join ', ' } else { 'none' }
                $lines.Add("| ``$($r.Server)`` | Claude Code | ``$($r.Config)`` | fixture | $tools | none |")
            }
            $lines.Add('')
        }
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

    # Deliberately NOT <root>/.mcp.json: a case passes only if the gate reads the -McpConfigPath it
    # is given, so the override is exercised rather than shadowed by the default location.
    $mcpPath = Join-Path $root 'fixture-mcp.json'
    if ($null -ne $McpServers) {
        $servers = [ordered]@{}
        foreach ($s in $McpServers) { $servers[$s] = [pscustomobject]@{ type = 'stdio'; command = 'fixture' } }
        [pscustomobject]@{ mcpServers = [pscustomobject]$servers } | ConvertTo-Json -Depth 6 |
            Set-Content -LiteralPath $mcpPath -Encoding utf8
    }

    return [pscustomobject]@{ Root = $root; GlobalPath = $globalPath; McpPath = $mcpPath }
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
        $out = & pwsh -NoProfile -File $Gate -Gate -RepoRoot $fx.Root -GlobalSettingsPath $fx.GlobalPath -McpConfigPath $fx.McpPath 2>&1
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

# --- S2918: the fourth comparison, MCP servers ---------------------------------
# The first three failures are the ways a tracked server and its guard drift apart; the
# unguarded-tool case uses a longer matcher so it also proves the matcher is anchored. The
# three passes keep the comparison from reaching past the tracked config: a VS Code row is
# prose, and a repository with no MCP config owes no section.

Invoke-Case -Name 'an .mcp.json server with no row fails 1' -ExpectedExit 1 -ExpectedText 'has no row' -Fixture @{
    ProjectHooks   = @('guard-alpha')
    InventoryHooks = @('guard-alpha')
    McpServers     = @('drv')
    McpRows        = @(@{ Server = 'docs'; Config = '.vscode/mcp.json'; Tools = @() })
}

Invoke-Case -Name 'an .mcp.json row naming an absent server fails 1' -ExpectedExit 1 -ExpectedText 'does not register it' -Fixture @{
    ProjectHooks   = @('guard-alpha')
    InventoryHooks = @('guard-alpha')
    McpServers     = @('drv')
    McpRows        = @(@{ Server = 'drv'; Config = '.mcp.json'; Tools = @() }, @{ Server = 'ghost'; Config = '.mcp.json'; Tools = @() })
}

Invoke-Case -Name 'a one-way tool no anchored matcher covers fails 1' -ExpectedExit 1 -ExpectedText 'matched by no PreToolUse matcher' -Fixture @{
    ProjectHooks       = @('guard-alpha')
    InventoryHooks     = @('guard-alpha')
    McpServers         = @('drv')
    McpRows            = @(@{ Server = 'drv'; Config = '.mcp.json'; Tools = @('wipe') })
    PreToolUseMatchers = @('mcp__drv__wipe_all')
}

Invoke-Case -Name 'servers in .mcp.json with no MCP section fail 1' -ExpectedExit 1 -ExpectedText "no '## MCP servers' section" -Fixture @{
    ProjectHooks   = @('guard-alpha')
    InventoryHooks = @('guard-alpha')
    McpServers     = @('drv')
}

Invoke-Case -Name 'a VS Code row with no counterpart anywhere passes' -ExpectedExit 0 -ExpectedText 'PASS' -Fixture @{
    ProjectHooks   = @('guard-alpha')
    InventoryHooks = @('guard-alpha')
    McpRows        = @(@{ Server = 'docs'; Config = '.vscode/mcp.json'; Tools = @() })
}

Invoke-Case -Name 'servers, rows and guards in sync pass' -ExpectedExit 0 -ExpectedText '1 .mcp.json server(s) listed and guarded' -Fixture @{
    ProjectHooks       = @('guard-alpha')
    InventoryHooks     = @('guard-alpha')
    McpServers         = @('drv')
    McpRows            = @(@{ Server = 'drv'; Config = '.mcp.json'; Tools = @('wipe', 'nuke') }, @{ Server = 'docs'; Config = '.vscode/mcp.json'; Tools = @() })
    PreToolUseMatchers = @('Grep|Glob', 'mcp__drv__wipe|mcp__drv__nuke')
}

Invoke-Case -Name 'no .mcp.json and no MCP section pass' -ExpectedExit 0 -ExpectedText 'PASS' -Fixture @{
    ProjectHooks   = @('guard-alpha')
    InventoryHooks = @('guard-alpha')
}

Write-Host ""
Write-Host "passed: $passed  failed: $failed"
if ($failed -gt 0) { exit 1 }
exit 0
