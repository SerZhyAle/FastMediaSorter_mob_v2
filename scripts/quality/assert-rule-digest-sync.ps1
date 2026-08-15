#requires -Version 7.0
<#
.SYNOPSIS
    Keep the agent-rule digests in step with the numbered rules in CLAUDE.md (S1548).

.DESCRIPTION
    The repository-rules registry record lists eleven paths but never said what role
    each one plays, so a digest could fall behind the authority silently: measured
    2026-08-14, .github/copilot-instructions.md named neither Rule 25, 26, 27, 28 nor
    30, and an agent reading only that file met a hook refusal its rules never
    mentioned. The roles are written out in dev/RULE_AND_SKILL_AUTHORING.md; this gate
    enforces the two of them that are mechanically checkable.

      1. Full-digest coverage - every numbered rule of the authority must be cited in
         every full digest as the literal token 'Rule N'. The citation, not the
         wording, is the checkable unit: a digest paraphrases by definition, so
         comparing text would destroy the thing being checked, while the number
         survives any paraphrase. Same device as 'Blocker: Sxxxx' and 'Carrier: Sxxxx'.

         A RANGE ('Rules 24-29') deliberately does NOT count. That is exactly how
         rules 25-28 came to look covered while nothing stated them - the range sat
         in a paragraph about hooks and named no rule of its own.

      2. Pointer reachability - a pointer file carries no rules, so it must at least
         name the authority and every full digest, or a reader who starts there has
         no route to the rules at all.

    Not checked, deliberately: that the digest's wording matches the authority's. A
    digest is a paraphrase; only a human reading the pair can judge it.

    Exit codes (S1070):
      0 - every full digest cites every rule and every pointer is reachable, or audit mode.
      1 - substantive failure: a rule is uncited or a pointer is a dead end (-Gate only).
      2 - the gate itself cannot run: a declared file is missing, or the authority
          parses to no numbered rules, so nothing could be compared.

.PARAMETER Gate
    Fail-closed: exit 1 when any rule is uncited or any pointer is unreachable.

.PARAMETER RepoRoot
    Root to read the four role files from. Defaults to this repository. Overridable so the
    failure path can be exercised against a throwaway copy instead of by breaking the live
    rules files, which every concurrent session is reading.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-rule-digest-sync.ps1
    pwsh -NoProfile -File scripts/quality/assert-rule-digest-sync.ps1 -Gate
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [string]$RepoRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = if ($RepoRoot) { $RepoRoot } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }

# The role table. Adding an agent-rules file is one line here plus a role decision;
# dev/RULE_AND_SKILL_AUTHORING.md carries the prose the roles mean.
$authorityPath = 'CLAUDE.md'
$fullDigestPaths = @('AGENTS.md', '.github/copilot-instructions.md')
$pointerPaths = @('GEMINI.md')

# The authority's rule list lives under this heading and ends at the next h2.
$authorityHeadingRx = '^##\s+\d+\.\s+Strict Rules\b'

function Read-RepoFile([string] $relative) {
    $full = Join-Path $repoRoot $relative
    if (-not (Test-Path -LiteralPath $full)) { return $null }
    return [System.IO.File]::ReadAllText($full)
}

function Get-AuthorityRuleNumbers([string] $text) {
    $lines = $text -split "`r?`n"
    $numbers = New-Object System.Collections.Generic.List[int]
    $inSection = $false
    foreach ($line in $lines) {
        if ($line -match $authorityHeadingRx) { $inSection = $true; continue }
        if (-not $inSection) { continue }
        if ($line -match '^##\s') { break }
        if ($line -match '^(?<n>\d+)\.\s') { [void]$numbers.Add([int]$matches['n']) }
    }
    return , @($numbers | Sort-Object -Unique)
}

function Test-RuleCited([string] $text, [int] $number) {
    # 'Rule 3' must not be satisfied by 'Rule 30', and 'Rule 10' must not be satisfied
    # by the legacy 'Rule 10.1'; the lookahead rejects a longer number and a sub-rule.
    # 'Rules 24-29' cannot match at all: \b after 'Rule' fails against the 's'.
    return [regex]::IsMatch($text, "\bRule\s+$number(?![\d.])")
}

$failures = New-Object System.Collections.Generic.List[string]

$authorityText = Read-RepoFile $authorityPath
if ($null -eq $authorityText) {
    Write-Error "assert-rule-digest-sync: authority not found: $authorityPath" -ErrorAction Continue
    exit 2
}

$ruleNumbers = Get-AuthorityRuleNumbers $authorityText
if ($ruleNumbers.Count -eq 0) {
    Write-Error "assert-rule-digest-sync: no numbered rules parsed from $authorityPath - the 'Strict Rules' heading or its list shape changed." -ErrorAction Continue
    exit 2
}

foreach ($digest in $fullDigestPaths) {
    $text = Read-RepoFile $digest
    if ($null -eq $text) {
        Write-Error "assert-rule-digest-sync: declared full digest not found: $digest" -ErrorAction Continue
        exit 2
    }
    $uncited = @($ruleNumbers | Where-Object { -not (Test-RuleCited $text $_) })
    if ($uncited.Count -gt 0) {
        [void]$failures.Add("[full-digest] $digest does not cite Rule $($uncited -join ', Rule ') - state the rule there and cite it by number")
    }
}

foreach ($pointer in $pointerPaths) {
    $text = Read-RepoFile $pointer
    if ($null -eq $text) {
        Write-Error "assert-rule-digest-sync: declared pointer not found: $pointer" -ErrorAction Continue
        exit 2
    }
    foreach ($target in @($authorityPath) + $fullDigestPaths) {
        if (-not $text.Contains($target)) {
            [void]$failures.Add("[pointer] $pointer never names $target - a pointer that carries no rules must at least route the reader to them")
        }
    }
}

if ($failures.Count -gt 0) {
    Write-Host "assert-rule-digest-sync: FAIL ($($failures.Count) issue(s))" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host "  $_" }
    Write-Host "  roles: dev/RULE_AND_SKILL_AUTHORING.md 'Rule mirroring contract'"
    if ($Gate) { exit 1 }
    exit 0
}

Write-Host ("assert-rule-digest-sync: PASS - {0} rule(s) cited in {1} full digest(s), {2} pointer(s) reachable." -f $ruleNumbers.Count, $fullDigestPaths.Count, $pointerPaths.Count) -ForegroundColor Green
exit 0
