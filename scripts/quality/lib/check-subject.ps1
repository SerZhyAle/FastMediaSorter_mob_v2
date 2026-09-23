#requires -Version 7.0
<#
.SYNOPSIS
    S3440: the one line a check prints to name the subject it checked - contract BUILD-EVIDENCE rule 1.

.DESCRIPTION
    Form: `subject: <key>=<value> [<key>=<value> ..]`, one line, keys lower-case, values without
    whitespace. Keys in use: module, flavor, buildtype, mode, scope, files. The phone and the watch
    targets print the same form, so one regex reads both - five incidents quoted a phone-module
    verdict as proof about the watch before the module was printed at all (S1807).

    scripts/quality/assert-check-subject.ps1 refuses a new check that prints no such line.

    Sourced, never executed directly, so it declares no exit codes of its own.
#>

function Format-CheckSubject {
    <# An empty value is dropped rather than printed as `key=`, which would read as a subject whose
       value got lost. Whitespace inside a value is folded to '_' so the line stays splittable. #>
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][System.Collections.Specialized.OrderedDictionary]$Axes)
    $parts = foreach ($key in $Axes.Keys) {
        $value = [string]$Axes[$key]
        if ([string]::IsNullOrWhiteSpace($value)) { continue }
        '{0}={1}' -f ([string]$key).ToLowerInvariant(), ($value.Trim() -replace '\s+', '_')
    }
    if (-not $parts) { $parts = @('scope=unknown') }
    return 'subject: ' + ($parts -join ' ')
}

function Write-CheckSubject {
    [CmdletBinding()]
    param([Parameter(Mandatory = $true)][System.Collections.Specialized.OrderedDictionary]$Axes)
    Write-Host (Format-CheckSubject -Axes $Axes)
}
