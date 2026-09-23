#requires -Version 7.0
<#
.SYNOPSIS
    Every tracked path of the repository matches a lock path rule (S3456).

.DESCRIPTION
    The shipped lock harness resolves a changed path through `locks.pathRules` in .sza-profile.json,
    first match wins, and a path no rule matches fails closed to every code domain. That default is
    the safe direction for a new module, but for a content root it silently serialises phone, watch
    and scripts work behind one site edit: before S3456, documentation/ and _layouts/ matched no
    rule. This gate turns "no rule" from a silent widening into a finding.

    A rule that deliberately takes every domain (`"domain": "Code"`) counts as a match: benchmark/,
    watchface/ and corex/ fail closed by decision (S2342), and the decision is now written down as a
    rule instead of inferred from an absence.

    Entries come from the git index, not the disk, because an ignored build/, .gradle/ or .venv/ is
    never handed to the lock and needs no rule. Each path is matched with the same case-insensitive
    `-match` the harness uses, and findings are grouped by top-level entry.

    Release scope (Rule 33): the subject is the repository root rather than any changed file, an
    unmatched root only over-serialises locks until then, each finding names its own entry, and
    adding a batch of rules costs one edit. Registered in scripts/quality/gate-placement.jsonl.

.PARAMETER ProfilePath
    Profile to read. Default: .sza-profile.json at the repository root.

.PARAMETER Entries
    Paths to judge instead of the git index; a trailing '/' is probed as '<dir>/probe'. Used by the
    contract suite so it never judges the tree it runs in.

.PARAMETER Quiet
    Print only the verdict line and the findings.

.NOTES
    Exit codes:
      0 - every path matches a rule.
      1 - at least one path matches no rule.
      2 - cannot verify: the profile is missing, unreadable or has no pathRules, or git failed.
#>
[CmdletBinding()]
param(
    [string]$ProfilePath,
    [string[]]$Entries,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'

$repoRoot = if ($env:FMS_REPO_ROOT) { $env:FMS_REPO_ROOT } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$profileFile = if ($ProfilePath) { $ProfilePath } else { Join-Path $repoRoot '.sza-profile.json' }

if (-not (Test-Path -LiteralPath $profileFile -PathType Leaf)) {
    Write-Error "assert-lock-path-coverage: profile not found at $profileFile" -ErrorAction Continue
    exit 2
}
try {
    $profileData = Get-Content -LiteralPath $profileFile -Raw | ConvertFrom-Json
} catch {
    Write-Error "assert-lock-path-coverage: profile is not valid JSON - $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}
$patterns = @($profileData.locks.pathRules | ForEach-Object { [string]$_.pattern } | Where-Object { $_ })
if ($patterns.Count -eq 0) {
    Write-Error "assert-lock-path-coverage: $profileFile declares no locks.pathRules" -ErrorAction Continue
    exit 2
}

if ($null -ne $Entries) {
    # pwsh -File hands a comma list over as ONE string, so the split happens here.
    $Entries = @($Entries | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() })
} else {
    $tracked = & git -C $repoRoot -c core.quotepath=off ls-files
    if ($LASTEXITCODE -ne 0) {
        Write-Error "assert-lock-path-coverage: git ls-files failed with exit $LASTEXITCODE" -ErrorAction Continue
        exit 2
    }
    # A path deleted in the work tree but not yet staged is still in the index; the tree is the
    # truth, so it is not judged.
    $deleted = [System.Collections.Generic.HashSet[string]]::new(
        [string[]]@(& git -C $repoRoot -c core.quotepath=off ls-files --deleted))
    # Every tracked path, not one probe per root: a rule may name a subtree only (config/detekt/),
    # and a sibling beside it (config/<new>/) must not pass because the root "looked" covered.
    $Entries = @($tracked | Where-Object { -not $deleted.Contains($_) })
}

. (Join-Path $PSScriptRoot 'lib/check-subject.ps1')
$source = if ($PSBoundParameters.ContainsKey('Entries')) { 'entries' } else { 'git-index' }
Write-CheckSubject -Axes ([ordered]@{ scope = 'repo'; paths = $source; profile = (Split-Path -Leaf $profileFile) })

$unmatched = [ordered]@{}
foreach ($entry in $Entries) {
    if ([string]::IsNullOrWhiteSpace($entry)) { continue }
    $probe = if ($entry.EndsWith('/')) { "${entry}probe" } else { $entry }
    $hit = $false
    foreach ($pattern in $patterns) {
        if ($probe -match $pattern) { $hit = $true; break }
    }
    if ($hit) { continue }
    $slash = $entry.IndexOf('/')
    $root = if ($slash -lt 0) { $entry } else { $entry.Substring(0, $slash + 1) }
    if (-not $unmatched.Contains($root)) { $unmatched[$root] = [System.Collections.Generic.List[string]]::new() }
    $unmatched[$root].Add($entry)
}

$judged = @($Entries | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }).Count
if ($unmatched.Count -gt 0) {
    foreach ($root in $unmatched.Keys) {
        $paths = $unmatched[$root]
        Write-Output "  UNMATCHED $root - $($paths.Count) path(s), e.g. $($paths[0]); no locks.pathRules pattern names it, so an edit there takes every code domain"
    }
    Write-Output "assert-lock-path-coverage: FAIL - $($unmatched.Count) top-level entr(ies) hold paths no rule matches; add a rule to .sza-profile.json (a Code.* domain, 'Code' for a deliberate full set, null for an exemption)"
    exit 1
}
if (-not $Quiet) {
    Write-Output "  judged $judged tracked paths against $($patterns.Count) path rules"
}
Write-Output "assert-lock-path-coverage: PASS - every tracked path matches a path rule"
exit 0