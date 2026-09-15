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

      2. Closing-gate coverage - every closing gate that carries an 'Sxxxx' label in
         the '## Closing gates' section of .claude/rules/spec-catalog.md must be cited
         in every full digest by that literal token. Those gates carry no rule number,
         so the whole class sat outside check 1's 'Rule N' unit and any omission was
         silent: measured 2026-09-05, AGENTS.md stated none of the heading-uniqueness
         gate (S2357) and .github/copilot-instructions.md stated none of the six at
         all, while this gate reported PASS (S2583). An agent reading only its own
         digest met a closing refusal with no rule behind it.

         The unit is the bullet's leading BOLD LABEL, not its prose. The prose of that
         section names neighbours and incidents (S1884, S1955, S1621, S1912), and
         requiring those cited would demand a retelling of other people's tickets. The
         label token survives paraphrase exactly as 'Rule N' does. A bullet with no
         token is therefore out of reach by construction, and one bullet is - the
         dated owner ruling 'A found problem ends BlockNeedUserTest', which owns no
         ticket id. A bullet that later gains a label enters the check by itself.

      3. Pointer reachability - a pointer file carries no rules, so it must at least
         name the authority and every full digest, or a reader who starts there has
         no route to the rules at all.

    Not checked, deliberately: that the digest's wording matches the authority's. A
    digest is a paraphrase; only a human reading the pair can judge it.

    Exit codes (S1070):
      0 - every full digest cites every rule and every labelled closing gate, and every
          pointer is reachable, or audit mode.
      1 - substantive failure: a rule or a closing gate is uncited, or a pointer is a
          dead end (-Gate only).
      2 - the gate itself cannot run: a declared file is missing, the authority parses
          to no numbered rules, or the closing-gate detail file is missing or has no
          '## Closing gates' section, so nothing could be compared.
      3 - S2828: an uncited rule or gate stands, but no file of the role table is in
          -ChangedFiles, so it is not attributable to this run. The failures are printed.
          Distinct from 1 because the caller cannot fix it and from 0 because something
          IS wrong in the tree.

.PARAMETER Gate
    Fail-closed: exit 1 when any rule is uncited or any pointer is unreachable.

.PARAMETER ChangedFiles
    S2828: repo-relative paths of the files the caller changed, comma-joined. Supplying it lets the
    gate decline to charge a failure when no file of the role table is among them.

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
    [string]$RepoRoot,
    # S1184/S1340: `pwsh -File` binds only the first element of a [string[]] and rejects the rest as
    # positional args, so callers comma-join and Expand-ChangedFiles splits it back.
    [string[]]$ChangedFiles
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# S2828: the chargeability test, shared with the other fixed-input gates.
. (Join-Path $PSScriptRoot 'lib/fixed-input-scope.ps1')

$repoRoot = if ($RepoRoot) { $RepoRoot } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }

# The role table. Adding an agent-rules file is one line here plus a role decision;
# dev/RULE_AND_SKILL_AUTHORING.md carries the prose the roles mean.
$authorityPath = 'CLAUDE.md'
$fullDigestPaths = @('AGENTS.md', '.github/copilot-instructions.md')
$pointerPaths = @('GEMINI.md')

# The authority's rule list lives under this heading and ends at the next h2.
$authorityHeadingRx = '^##\s+\d+\.\s+Strict Rules\b'

# The closing gates are formulated in the authority's path-scoped detail file, which is
# part of the authority's text rather than a digest, and they are labelled by ticket id
# instead of by rule number - hence a second source and a second citation unit.
$closingGatePath = '.claude/rules/spec-catalog.md'
$closingGateHeadingRx = '^##\s+Closing gates\b'

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

function Get-ClosingGateLabelTokens([string] $text) {
    # $null = the section was never entered (caller exits 2); an empty map = the section
    # exists but no bullet carries a label token, which is a real, reportable state.
    # Token -> the label it came from, so a failure can name the bullet a reader must find.
    $lines = $text -split "`r?`n"
    $map = [ordered]@{}
    $inSection = $false
    $sectionSeen = $false
    foreach ($line in $lines) {
        if ($line -match $closingGateHeadingRx) { $inSection = $true; $sectionSeen = $true; continue }
        if (-not $inSection) { continue }
        if ($line -match '^##\s') { break }
        # Top-level bullet only, and only the leading bold label - the lazy match ends at
        # the first '**' that closes it, so the bullet's prose is never read.
        if ($line -notmatch '^-\s+\*\*(?<label>.+?)\*\*') { continue }
        $label = $matches['label']
        foreach ($m in [regex]::Matches($label, 'S\d{4}')) {
            if (-not $map.Contains($m.Value)) { $map[$m.Value] = $label }
        }
    }
    if (-not $sectionSeen) { return $null }
    return $map
}

function Test-TokenCited([string] $text, [string] $token) {
    return [regex]::IsMatch($text, "\b$token\b")
}

function Test-RuleCited([string] $text, [int] $number) {
    # 'Rule 3' must not be satisfied by 'Rule 30', and 'Rule 10' must not be satisfied
    # by the legacy 'Rule 10.1'; the lookahead rejects a longer number and a sub-rule.
    # 'Rules 24-29' cannot match at all: \b after 'Rule' fails against the 's'.
    return [regex]::IsMatch($text, "\bRule\s+$number(?![\d.])")
}

$failures = New-Object System.Collections.Generic.List[string]

. (Join-Path $PSScriptRoot 'lib/absent-input.ps1')

# S3075: the closing-gate detail file this gate reads lives under .claude/, which is gitignored
# whole - so on a fresh clone, a release worktree or a CI runner it is absent by design and the
# exit-2 branch below would report a published absence as a broken checkout.
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot '.claude'))) {
    Exit-InputAbsent -Gate 'assert-rule-digest-sync' -Path '.claude/' `
        -Reason 'gitignored - present only on a workstation checkout'
}

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

$closingGateText = Read-RepoFile $closingGatePath
if ($null -eq $closingGateText) {
    Write-Error "assert-rule-digest-sync: closing-gate detail file not found: $closingGatePath" -ErrorAction Continue
    exit 2
}

$closingGates = Get-ClosingGateLabelTokens $closingGateText
if ($null -eq $closingGates) {
    Write-Error "assert-rule-digest-sync: no 'Closing gates' section in $closingGatePath - the heading or its list shape changed." -ErrorAction Continue
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
    foreach ($token in $closingGates.Keys) {
        if (Test-TokenCited $text $token) { continue }
        [void]$failures.Add("[closing-gate] $digest does not cite $token - state the '$($closingGates[$token])' gate from $closingGatePath there and cite it by that token")
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
    # S2828: the role table above IS the declared input set, so no second path list appears here
    # (S1621). A rule stated in CLAUDE.md and not yet mirrored belongs to the session writing it;
    # a closure that opened none of the five files cannot be its cause.
    $declaredInputs = @($authorityPath) + $fullDigestPaths + $pointerPaths + @($closingGatePath) |
        ForEach-Object { Join-Path $repoRoot $_ }
    if (-not (Test-FixedInputsChargeable -ChangedFiles $ChangedFiles -InputPaths $declaredInputs)) {
        Write-NotChargedVerdict -GateName 'assert-rule-digest-sync' -Findings @($failures)
        exit 3
    }
    if ($Gate) { exit 1 }
    exit 0
}

Write-Host ("assert-rule-digest-sync: PASS - {0} rule(s) and {1} labelled closing gate(s) cited in {2} full digest(s), {3} pointer(s) reachable." -f $ruleNumbers.Count, $closingGates.Count, $fullDigestPaths.Count, $pointerPaths.Count) -ForegroundColor Green
exit 0
