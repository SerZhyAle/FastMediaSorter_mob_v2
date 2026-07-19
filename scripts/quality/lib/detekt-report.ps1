# S1077: reading detekt's Checkstyle XML report, split out of assert-detekt.ps1 so it can be tested.
#
# In assert-detekt.ps1 this logic sat between a gradle invocation and an `exit`, so the only way to
# exercise it was a full build - which is why it shipped untested and fail-open for so long.
#
# Dot-source it:  . (Join-Path $repoRoot 'scripts/quality/lib/detekt-report.ps1')

<#
.SYNOPSIS
    Collect the files detekt reported NEW findings in, for the given modules.

.DESCRIPTION
    Reads <module>/build/reports/detekt/detekt.xml (Checkstyle format, baseline already applied by
    detekt itself, so every file present carries a genuinely-new finding).

    Fail-closed contract (S1077): "no report" and "unreadable report" are NOT "no findings". A caller
    that cannot read the report cannot narrow a project-wide failure down to its own files, and must
    say so instead of passing. The previous version returned an empty list in both cases, which the
    caller then read as "clean" - printing PASS on top of a detekt run that had just failed.

.OUTPUTS
    Hashtable:
      Ok     [bool]     - $false when at least one requested module's report is missing or unparseable.
      Files  [string[]] - lowercased, forward-slashed paths of files carrying new findings.
      Reason [string]   - why Ok is $false; empty when Ok.
#>
function Get-DetektFindingFiles {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$RepoRoot,

        [Parameter(Mandatory)]
        [string[]]$Modules
    )

    $files = [System.Collections.Generic.List[string]]::new()
    $problems = [System.Collections.Generic.List[string]]::new()

    foreach ($m in $Modules) {
        $reportPath = Join-Path $RepoRoot "$m/build/reports/detekt/detekt.xml"
        if (-not (Test-Path $reportPath)) {
            $problems.Add("$m - no report at $reportPath")
            continue
        }
        try {
            [xml]$xml = Get-Content -LiteralPath $reportPath -Raw
        }
        catch {
            $problems.Add("$m - report unparseable ($($_.Exception.Message))")
            continue
        }
        # An empty <checkstyle/> is a legitimate "this module is clean", not a problem - only a
        # missing or malformed document means we could not look. Probe for the property rather than
        # touching it: a report with no <file> children has no `file` member at all, and under
        # Set-StrictMode -Version Latest (which every caller here sets) that access throws. The old
        # code took the same access inside its try/catch, so a clean module was mis-reported as an
        # unparseable one - and then fell through to PASS anyway. Caught by this file's own harness.
        $checkstyle = $xml.checkstyle
        if ($null -eq $checkstyle) { continue }
        if ($checkstyle.PSObject.Properties.Name -notcontains 'file') { continue }
        $fileNodes = $checkstyle.file
        if ($null -eq $fileNodes) { continue }
        foreach ($fn in $fileNodes) {
            if ($fn.error) { $files.Add((([string]$fn.name) -replace '\\', '/').ToLower()) }
        }
    }

    return @{
        Ok     = ($problems.Count -eq 0)
        Files  = @($files)
        Reason = ($problems -join '; ')
    }
}
