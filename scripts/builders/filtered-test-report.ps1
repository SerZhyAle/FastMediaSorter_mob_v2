<#
.SYNOPSIS
    Gives a filtered unit run its OWN copy of the reports it produced, so the path printed under a
    green line cannot be read after a sibling session has overwritten it.

.DESCRIPTION
    S2588: app_v2/build/test-results/test<Variant>UnitTest-filtered is one directory shared by every
    session on the machine. Build.Phone serialises the gradle invocations but not the ownership of
    the output directory between them, so the window between "my gradle released the domain" and "I
    read the report" is unprotected. Measured 2026-09-05 during S1714: a filtered run for
    TranslationOverlayViewTest returned exit 0, and by the time the printed directory was read it
    held one file - TEST-..SettingsManifestExportTest.xml, written by a sibling's reindex-settings
    run in the same minute. The requested class's report existed nowhere under app_v2/build.

    The damage is not the lost file, it is that a green check looks proven. A reader who compares
    counts under the printed path takes a foreign class for their own; a reader who finds nothing
    cannot tell "my tests passed and were wiped" from "my filter matched nothing".

    So the reports are COPIED into a per-run directory under temp/ while the build domain is still
    held, and that copy's path is what gets printed. Holding the domain is the entire safety
    argument: no sibling gradle can be running in this module, so the harvest observes only this
    run's own output. Released first, the same copy would race the very overwrite it prevents.

    Ownership is decided by write time against a mark taken before gradle started, never by the
    file's name. A name test would have to re-implement Gradle's --tests pattern matching (globs,
    package form, method selectors) and would answer "not mine" for a report that is, which is the
    false negative that sends a reader back to the shared directory this file exists to replace.
    No margin is subtracted from the mark on purpose: a file written seconds BEFORE this run began
    belongs to whoever held the domain last, and admitting it is the original defect in a new place.

    Both candidate directories are scanned rather than one being named from a rule. S1946 measured
    the same call reporting into `<task>-filtered` on app_v2 and into the plain `<task>` directory
    on wear, so a composed path is right for one module and silently empty for the other - and an
    empty scan reports "nothing ran", which is the wrong half of the ambiguity this file resolves.

    Dot-source this file; it defines functions and never exits.
#>

# Deliberately no Set-StrictMode here: this file is dot-sourced, so it would impose strict mode on
# every consumer's whole scope - a side effect none of them asked for. Same reasoning as
# build-output-holder.ps1 and gradle-run-verdict.ps1 next to it.

function Get-FilteredTestReportCandidateDir {
    <#
        Every directory a filtered unit run of this task could have reported into. Returned as a set
        rather than a single answer because which one Gradle picks is a property of the module's
        build script, not of the call - see the S1946 note above.
    #>
    param(
        [Parameter(Mandatory)][string] $ProjectRoot,
        [Parameter(Mandatory)][string] $Module,
        [Parameter(Mandatory)][string] $TaskDir
    )
    $resultsRoot = Join-Path $ProjectRoot "$Module\build\test-results"
    return @(
        (Join-Path $resultsRoot "$TaskDir-filtered")
        (Join-Path $resultsRoot $TaskDir)
    )
}

function Get-FilteredTestReportFile {
    <#
        The XML this run wrote: every TEST-*.xml under either candidate directory whose last write is
        at or after the mark. Directories that do not exist are skipped silently - on a module whose
        filtered run reports into the plain directory the `-filtered` one is simply never created,
        which is an ordinary shape rather than a fault.
    #>
    param(
        [Parameter(Mandatory)][string] $ProjectRoot,
        [Parameter(Mandatory)][string] $Module,
        [Parameter(Mandatory)][string] $TaskDir,
        [Parameter(Mandatory)][datetime] $Since
    )
    $files = [System.Collections.Generic.List[System.IO.FileInfo]]::new()
    foreach ($dir in (Get-FilteredTestReportCandidateDir -ProjectRoot $ProjectRoot -Module $Module -TaskDir $TaskDir)) {
        if (-not (Test-Path -LiteralPath $dir)) { continue }
        $found = @(Get-ChildItem -LiteralPath $dir -Filter 'TEST-*.xml' -File -ErrorAction SilentlyContinue |
            Where-Object { $_.LastWriteTime -ge $Since })
        foreach ($file in $found) { $files.Add($file) }
    }
    return @($files | Sort-Object Name)
}

function Remove-StaleFilteredTestReport {
    <#
        Drops harvests older than the retention window.

        This directory cannot be left to scripts/utils/archive-temp.ps1, which owns temp/ retention
        everywhere else: that script judges ROOT entries, and temp/TEST-REPORTS is a root entry whose
        write time is refreshed by every new harvest inside it. So it reads as perpetually fresh, its
        age rule never fires, and the dated directories beneath it are never examined at all - the one
        shape its policy has no case for. Pruning here keeps the growth bounded at the only place
        that knows these directories are per-run and disposable.

        Best-effort and silent: a harvest that cannot be deleted is clutter, and refusing this run's
        verdict over clutter would be a worse trade than leaving it.
    #>
    param(
        [Parameter(Mandatory)][string] $Root,
        [int] $RetentionDays = 7
    )
    if (-not (Test-Path -LiteralPath $Root)) { return 0 }
    $cutoff = (Get-Date).AddDays(-$RetentionDays)
    $removed = 0
    foreach ($dir in @(Get-ChildItem -LiteralPath $Root -Directory -ErrorAction SilentlyContinue)) {
        if ($dir.LastWriteTime -ge $cutoff) { continue }
        try {
            Remove-Item -LiteralPath $dir.FullName -Recurse -Force -ErrorAction Stop
            $removed++
        }
        catch {
            # Left in place on purpose - see the note above.
        }
    }
    return $removed
}

function Save-FilteredTestReport {
    <#
        Copies this run's reports to temp/TEST-REPORTS/<run>/ and describes the outcome.

        Returns an object carrying Outcome, Files, Path and Message. Three outcomes, and the caller
        prints all three, because the whole point is that they stop looking alike:

          Harvested - the copy holds this run's reports and Path names it.
          NoneWritten - the run wrote no report. Gradle refuses a --tests pattern that matches
                        nothing ("No tests found for given includes") and fails, so a GREEN run
                        reaching here was UP-TO-DATE: the results on disk are a previous run's and
                        no count from them belongs to this call.
          CopyFailed - the reports were found and could not be copied. Deliberately not fatal and
                       deliberately not silent: a filesystem refusal is not a statement about the
                       code under test, so it must not change the verdict, but a caller told
                       nothing would fall back to the shared directory and be misled there.

        Never throws. The caller is a check whose exit code answers a question about the code, and a
        copy that failed has not observed anything about the code.
    #>
    param(
        [Parameter(Mandatory)][string] $ProjectRoot,
        [Parameter(Mandatory)][string] $Module,
        [Parameter(Mandatory)][string] $TaskDir,
        [Parameter(Mandatory)][datetime] $Since,
        [Parameter(Mandatory)][string] $RunId,
        [int] $RetentionDays = 7
    )

    $files = @()
    try {
        $files = @(Get-FilteredTestReportFile -ProjectRoot $ProjectRoot -Module $Module -TaskDir $TaskDir -Since $Since)
    }
    catch {
        return [pscustomobject]@{
            Outcome = 'CopyFailed'
            Files   = 0
            Path    = $null
            Message = "Could not read this run's test reports: $($_.Exception.Message)"
        }
    }

    if ($files.Count -eq 0) {
        return [pscustomobject]@{
            Outcome = 'NoneWritten'
            Files   = 0
            Path    = $null
            Message = 'No test report was written by this run (the task was up to date). Any XML under build/test-results belongs to an earlier run - do not read counts from it as this run''s evidence.'
        }
    }

    try {
        $harvestRoot = Join-Path $ProjectRoot 'temp\TEST-REPORTS'
        $null = Remove-StaleFilteredTestReport -Root $harvestRoot -RetentionDays $RetentionDays
        $destination = Join-Path $harvestRoot $RunId
        if (-not (Test-Path -LiteralPath $destination)) {
            $null = New-Item -ItemType Directory -Path $destination -Force
        }
        foreach ($file in $files) {
            Copy-Item -LiteralPath $file.FullName -Destination (Join-Path $destination $file.Name) -Force
        }
        return [pscustomobject]@{
            Outcome = 'Harvested'
            Files   = $files.Count
            Path    = $destination
            Message = $null
        }
    }
    catch {
        return [pscustomobject]@{
            Outcome = 'CopyFailed'
            Files   = $files.Count
            Path    = $null
            Message = "Found $($files.Count) report(s) for this run and could not copy them: $($_.Exception.Message)"
        }
    }
}
