#requires -Version 7.0
<#
.SYNOPSIS
    Deliver one built artifact to every sink a builder hands it to: Google Drive and Total Commander.

.DESCRIPTION
    A builder that produces something a person outside this machine installs puts it in two places:
    the Google Drive share (raw, plus a password ZIP for recipients whose mail or security policy
    refuses a bare .apk) and the Total Commander staging folder. S1707 extracted the Drive half into
    copy-to-drive.ps1 so it would be written once - but the extraction was never wired up, and on
    2026-09-02 the whole block was still hand-written in 26 places across 25 builders while
    copy-to-drive.ps1 had exactly two callers (S2332).

    This script is the entry point those builders call. It owns the Commander copy and delegates the
    Drive copy to copy-to-drive.ps1 rather than absorbing it: that script's name states its scope and
    two callers already read it as "the Drive mirror", so widening it to touch a second sink would
    silently change what they do - which is the very failure this ticket exists to stop.

    It never fails a build. An unreachable sink or a missing 7-Zip is reported and skipped, because
    an artifact that built correctly must not be thrown away over its courtesy copy.

.PARAMETER Path
    The artifact or artifacts to deliver. Every one must exist. Several paths land as several raw
    copies in the Drive folder and go into ONE archive together, which is what a release shipping an
    AAB beside its APK needs.

.PARAMETER Name
    File name to use in the sinks. Defaults to the source file's own name. Pass one explicitly to
    keep an established unversioned convention, which overwrites rather than accumulating.
    With several -Path values each artifact keeps its own name and -Name renames the archive only.

.PARAMETER CommanderPath
    Which artifact goes to the Commander folder. Defaults to the first -Path. Name it explicitly for
    a set whose members are not interchangeable: an AAB+APK release delivers the APK there, because
    the Commander folder is a sideload staging area and nobody sideloads an AAB.

.PARAMETER DriveDir
    Destination for the Drive half. Defaults to the resolved Drive artifact sink.

.PARAMETER CommanderDir
    Destination for the Commander half. Defaults to the resolved Commander artifact sink.

.PARAMETER ZipPassword
    Password for the ZIP copy. Defaults to the project's long-standing '1'. Not a secret - see
    copy-to-drive.ps1's own .PARAMETER ZipPassword for why (S2329 ADR-1).

.PARAMETER NoZip
    Copy raw files only. Use for an artifact nobody installs by hand, such as an AAB alone.

.PARAMETER NoCommander
    Skip the Commander copy. Use for an artifact that is not a sideload candidate.

.EXAMPLE
    & "$PSScriptRoot\..\utils\publish-artifact.ps1" -Path "$downloadsDir\$destName" -Name $destName

.EXAMPLE
    & "$PSScriptRoot\..\utils\publish-artifact.ps1" `
        -Path $destAabPath, $destApkPath -CommanderPath $destApkPath

.NOTES
    Invoke with the call operator, not `pwsh -File`: -File binds a comma-separated value as a single
    string and never produces an array, so a two-artifact delivery would look like one missing file.

    Exit codes:
      0 - the artifact was delivered, or a sink is not reachable on this machine and that half was
          skipped (the ZIP may also have been skipped, and says so)
      1 - a -Path does not exist, or a raw copy itself failed
#>
[CmdletBinding()]
[Diagnostics.CodeAnalysis.SuppressMessageAttribute(
    'PSAvoidUsingPlainTextForPassword', 'ZipPassword',
    Justification = 'Forwarded verbatim to copy-to-drive.ps1, where the reasoning is recorded: a courtesy formality for mail filters, not a secret.')]
param(
    [Parameter(Mandatory)] [string[]] $Path,
    [string] $Name,
    [string] $CommanderPath,
    [string] $DriveDir,
    [string] $CommanderDir,
    [string] $ZipPassword = '1',
    [switch] $NoZip,
    [switch] $NoCommander
)

. "$PSScriptRoot\project-paths.ps1"

$missing = $Path | Where-Object { -not (Test-Path -LiteralPath $_) }
if ($missing) {
    Write-Error "publish-artifact: artifact not found: $($missing -join ', ')" -ErrorAction Continue
    exit 1
}

$driveArgs = @{ Path = $Path }
if ($PSBoundParameters.ContainsKey('Name')) { $driveArgs.Name = $Name }
if ($PSBoundParameters.ContainsKey('DriveDir')) { $driveArgs.DriveDir = $DriveDir }
if ($PSBoundParameters.ContainsKey('ZipPassword')) { $driveArgs.ZipPassword = $ZipPassword }
if ($NoZip) { $driveArgs.NoZip = $true }

& "$PSScriptRoot\copy-to-drive.ps1" @driveArgs
$driveExit = $LASTEXITCODE
if ($driveExit -ne 0) { exit $driveExit }

if ($NoCommander) { exit 0 }

if (-not $PSBoundParameters.ContainsKey('CommanderDir')) {
    # The resolver warns and returns $null when the sink is absent. Skipping is this script's whole
    # contract: the artifact is already built and already on Drive, so its second courtesy copy
    # cannot fail the caller (S2326 ADR-3).
    $CommanderDir = Get-ArtifactSink -Kind Commander
    if (-not $CommanderDir) { exit 0 }
}
elseif (-not (Test-Path -LiteralPath $CommanderDir)) {
    try {
        New-Item -ItemType Directory -Path $CommanderDir -ErrorAction Stop | Out-Null
    }
    catch {
        Write-Error "publish-artifact: Commander folder unavailable ($CommanderDir): $_" -ErrorAction Continue
        exit 1
    }
}

$commanderSource = if ([string]::IsNullOrWhiteSpace($CommanderPath)) { $Path[0] } else { $CommanderPath }
if (-not (Test-Path -LiteralPath $commanderSource)) {
    Write-Error "publish-artifact: -CommanderPath does not exist: $commanderSource" -ErrorAction Continue
    exit 1
}

$source = Get-Item -LiteralPath $commanderSource
# -Name renames the Commander copy only when it is unambiguous which artifact it refers to: with a
# set, -Name belongs to the archive and each member keeps its own name (see copy-to-drive.ps1).
$commanderName = if ($Path.Count -eq 1 -and -not [string]::IsNullOrWhiteSpace($Name)) { $Name } else { $source.Name }
$destPath = Join-Path $CommanderDir $commanderName

try {
    Copy-Item -LiteralPath $source.FullName -Destination $destPath -Force -ErrorAction Stop
}
catch {
    Write-Error "publish-artifact: Commander copy failed: $_" -ErrorAction Continue
    exit 1
}
Write-Host "Commander: $destPath ($([math]::Round($source.Length / 1MB, 2)) MB)" -ForegroundColor Green
exit 0
