#requires -Version 7.0
<#
.SYNOPSIS
    Audit Maestro flow YAML for violations of the oracle convention (S1612).

.DESCRIPTION
    A Maestro flow is green only when it PROVES the behaviour happened. Three
    authoring mistakes make a flow green while proving nothing, which is how the
    earlier generation of flows became fictitious. This gate rejects all three,
    statically, before a flow ever reaches a device.

    Authoritative convention text: maestro/WRITING_TESTS.md, "Oracle convention".
    This script encodes exactly those rules and must not drift from them.

    Rules enforced:
      1. optional-proof   - `optional: true` attached to assertVisible /
                            assertNotVisible. `optional` is legal on a navigation
                            tapOn whose target genuinely varies (a system
                            permission dialog, a skippable onboarding page), and
                            illegal on the assertion that is the point of the test.
      2. regex-selector   - a regex in an `id:` / `text:` selector value (the `.*`
                            sequence). Maestro does not reliably match these, so
                            the step silently never fires.
      3. coordinate-tap   - a `point:` selector. It proves nothing about which
                            element was hit and breaks on the next layout change.

    Scanned: maestro/ and scripts/devtest/maestro/, *.yaml and *.yml.

    Exempt (see $exemptRelativePaths): maestro/_shared/permissions.yaml - a
    fragment consisting solely of optional system-permission taps, which the
    convention sanctions explicitly. The exemption is a path list, deliberately
    not a filename convention: a name-based rule would silently exempt any future
    file someone happens to call permissions.yaml.

    Exit codes (S1070):
      0 - clean: no violation found.
      1 - substantive failure: at least one violation remains.
      2 - the gate itself cannot run (no scan root exists / a file is unreadable).
          Distinct from 1 on purpose: "found a defect" and "did not look" are
          different answers.

.PARAMETER Gate
    Accepted for uniformity with the assert-fast-gates.ps1 batch, which passes -Gate to every
    entry. This gate is fail-closed by construction - a violation always exits 1 - so the switch
    changes nothing. It exists because the batch would otherwise fail on an unknown parameter.

.PARAMETER Quiet
    Suppress the per-finding list; print only the expected/actual summary line.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-maestro-oracle.ps1
.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-maestro-oracle.ps1 -Quiet
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Quiet
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Repo root = two levels up from scripts/quality/
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

$scanRoots = @(
    (Join-Path $repoRoot 'maestro'),
    (Join-Path $repoRoot 'scripts/devtest/maestro')
) | Where-Object { Test-Path $_ }

if ($scanRoots.Count -eq 0) {
    Write-Error 'assert-maestro-oracle: no Maestro flow directory found - nothing to verify.' -ErrorAction Continue
    exit 2
}

# Paths the convention exempts, repo-relative with forward slashes.
# Every entry states WHY it is exempt and what removes the exemption.
$exemptRelativePaths = @(
    # Fragment of nothing but optional system-permission taps, sanctioned by the convention.
    # Permanent exemption.
    'maestro/_shared/permissions.yaml',

    # S1618: both flows drive the "Playback Settings" dialog, which is unreachable from the
    # player UI (showPlayerSettingsDialog() has no call site). Their regex selectors cannot be
    # replaced with real ids because the ids do not exist. Temporary - remove both entries when
    # S1618 either restores the entry point or removes the feature path.
    'maestro/device_only/3d-video-sbs.yaml',
    'maestro/device_only/3d-video-switching.yaml'
)

function Get-RelativePath {
    param([string]$FullPath)
    $rel = $FullPath.Substring($repoRoot.Length).TrimStart('\', '/')
    return $rel -replace '\\', '/'
}

# An assertion command opens a block; `optional: true` belongs to the most recent
# command opener at the same or shallower indent. Tracking the opener is what
# separates rule 1 from a legal optional tapOn, so a per-line regex is not enough.
$assertionOpener = '^\s*-\s*(assertVisible|assertNotVisible)\s*:'
$anyOpener       = '^\s*-\s*([A-Za-z]+)\s*:'
$optionalLine    = '^\s*optional\s*:\s*true\s*$'
$regexSelector   = '^\s*(id|text)\s*:\s*"[^"]*\.\*'
$pointSelector   = '^\s*point\s*:'

$findings = [System.Collections.Generic.List[object]]::new()
$scanned = 0

foreach ($root in $scanRoots) {
    $files = Get-ChildItem -LiteralPath $root -Recurse -File |
        Where-Object { $_.Extension -in @('.yaml', '.yml') }

    foreach ($file in $files) {
        $rel = Get-RelativePath $file.FullName
        if ($exemptRelativePaths -contains $rel) { continue }

        try {
            $lines = Get-Content -LiteralPath $file.FullName
        } catch {
            Write-Error "assert-maestro-oracle: cannot read $rel - $($_.Exception.Message)" -ErrorAction Continue
            exit 2
        }

        $scanned++
        $lastOpenerWasAssertion = $false

        for ($i = 0; $i -lt $lines.Count; $i++) {
            $line = $lines[$i]
            $lineNo = $i + 1

            # Comments carry the convention text itself - never a violation.
            if ($line -match '^\s*#') { continue }

            if ($line -match $anyOpener) {
                $lastOpenerWasAssertion = $line -match $assertionOpener
            }

            if ($line -match $optionalLine -and $lastOpenerWasAssertion) {
                $findings.Add([pscustomobject]@{
                    File = $rel; Line = $lineNo; Rule = 'optional-proof'
                    Text = 'optional: true on a proof assertion'
                })
            }

            if ($line -match $regexSelector) {
                $findings.Add([pscustomobject]@{
                    File = $rel; Line = $lineNo; Rule = 'regex-selector'
                    Text = $line.Trim()
                })
            }

            if ($line -match $pointSelector) {
                $findings.Add([pscustomobject]@{
                    File = $rel; Line = $lineNo; Rule = 'coordinate-tap'
                    Text = $line.Trim()
                })
            }
        }
    }
}

if (-not $Quiet -and $findings.Count -gt 0) {
    foreach ($f in $findings) {
        Write-Host ("  {0}:{1} [{2}] {3}" -f $f.File, $f.Line, $f.Rule, $f.Text)
    }
}

Write-Host ("assert-maestro-oracle: scanned {0} flow file(s)." -f $scanned)
Write-Host ("expected: 0 oracle violation(s) | actual: {0}" -f $findings.Count)

if ($findings.Count -gt 0) {
    Write-Host 'assert-maestro-oracle: FAIL (see maestro/WRITING_TESTS.md "Oracle convention").'
    exit 1
}

Write-Host 'assert-maestro-oracle: PASS'
exit 0
