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

    $report = Get-DetektFindings -RepoRoot $RepoRoot -Modules $Modules
    return @{
        Ok     = $report.Ok
        Files  = @(@($report.Findings) | ForEach-Object { $_.File } | Select-Object -Unique)
        Reason = $report.Reason
    }
}

<#
.SYNOPSIS
    Collect the individual NEW detekt findings for the given modules.

.DESCRIPTION
    S1338: the caller used to be handed file paths only, so a failure printed the file and
    discarded the rule, line and message the report had already parsed - costing 2.38 extra
    gradle round-trips per failure just to see what was wrong. Same fail-closed contract as
    Get-DetektFindingFiles, which now delegates here.

.OUTPUTS
    Hashtable:
      Ok       [bool]   - $false when at least one requested module's report is missing or unparseable.
      Findings [array]  - objects with File (lowercased, forward-slashed), Line, Column, RuleId, Message.
      Reason   [string] - why Ok is $false; empty when Ok.
#>
function Get-DetektFindings {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$RepoRoot,

        [Parameter(Mandatory)]
        [string[]]$Modules
    )

    $reports = [ordered]@{}
    foreach ($m in $Modules) { $reports[$m] = (Join-Path $RepoRoot "$m/build/reports/detekt/detekt.xml") }
    return Get-DetektFindingsFromReports -Reports $reports
}

<#
.SYNOPSIS
    Collect the individual NEW detekt findings from explicitly-named Checkstyle reports.

.DESCRIPTION
    S1595 split this out of Get-DetektFindings, which could only ever read the gradle task's own
    output path. The scoped runner writes its report elsewhere on purpose - assert-detekt.ps1
    narrows a project-wide failure against <module>/build/reports/detekt/detekt.xml and judges that
    file's staleness by mtime, so a second writer there would corrupt an unrelated gate's verdict.

    Both callers share this one parser deliberately: its fail-closed contract and its StrictMode
    handling of a clean <checkstyle/> document were both bought by defects, and a second copy would
    be a second place to lose them.

.PARAMETER Reports
    Ordered dictionary of label -> report path. The label names the source in Reason, so a caller
    reading the failure can tell which report was missing.

.OUTPUTS
    Hashtable:
      Ok       [bool]   - $false when at least one named report is missing or unparseable.
      Findings [array]  - objects with File (lowercased, forward-slashed), Line, Column, RuleId, Message.
      Reason   [string] - why Ok is $false; empty when Ok.
#>
function Get-DetektFindingsFromReports {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [System.Collections.IDictionary]$Reports
    )

    $findings = [System.Collections.Generic.List[object]]::new()
    $problems = [System.Collections.Generic.List[string]]::new()

    foreach ($m in @($Reports.Keys)) {
        $reportPath = $Reports[$m]
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
            if (-not $fn.error) { continue }
            $path = (([string]$fn.name) -replace '\\', '/').ToLower()
            foreach ($err in @($fn.error)) {
                # detekt writes the rule as source="detekt.<RuleId>"; keep the bare rule id,
                # which is what the operator greps for in config/detekt/detekt.yml.
                $rule = ([string]$err.source) -replace '^detekt\.', ''
                $findings.Add([pscustomobject]@{
                        File    = $path
                        Line    = [string]$err.line
                        Column  = [string]$err.column
                        RuleId  = $rule
                        Message = [string]$err.message
                    })
            }
        }
    }

    return @{
        Ok       = ($problems.Count -eq 0)
        Findings = @($findings)
        Reason   = ($problems -join '; ')
    }
}
