#requires -Version 7.0
<#
.SYNOPSIS
    Mirror one built artifact to the Google Drive share, raw and as a password ZIP.

.DESCRIPTION
    Every builder that hands a file to someone outside this machine puts it in the same
    Google Drive folder twice: the raw file, for recipients whose fast path is a direct
    install, and a password-protected ZIP, for recipients whose mail or security policy
    refuses a bare .apk. That pair of copies was written out by hand in fifteen builder
    scripts and in none of the release scripts - which is how the watch shipped in
    release 2.60.8232.251 while the Drive copy stayed at the 15 August build, looking
    current and being a month stale (S1707).

    This script is that duplicated block, in one place, so a build path either calls it or
    visibly does not. It never fails a build: a missing 7-Zip or an unavailable Drive
    folder is reported and skipped, because an artifact that built correctly must not be
    thrown away over its courtesy copy.

.PARAMETER Path
    The artifact or artifacts to mirror. Every one must exist. Several paths land as several raw
    copies and go into ONE archive together, which is what a release that ships an AAB beside its
    APK needs: the pair travels as a unit, and a recipient who unzips gets both (S2332).

.PARAMETER Name
    File name to use in the Drive folder. Defaults to the source file's own name.
    Pass an explicit name to keep the established unversioned convention
    (FastMediaSorter_wear_release.apk), which overwrites rather than accumulating.

    With several -Path values each artifact keeps its own name and -Name renames the ARCHIVE only.
    Renaming one member of a set would silently decide which of them is the real artifact.

.PARAMETER DriveDir
    Destination folder. Defaults to the resolved Drive artifact sink. Passing one explicitly
    keeps the old create-it-if-absent behaviour; the resolved default does not, because a sink
    that does not exist on this machine is a machine without that delivery target (S2326).

.PARAMETER ZipPassword
    Password for the ZIP copy. Defaults to the project's long-standing '1'.

    Not a secret, which is why PSAvoidUsingPlainTextForPassword is suppressed on it below. The
    ZIP exists to get past mail and security filters that refuse a bare .apk, not to protect the
    artifact: the password is printed to the console on success because the recipient has to know
    it, and it reaches 7-Zip as a "-p" command-line argument, where the process list shows it
    whatever type the parameter carries. A SecureString would have to be unwrapped back to plain
    text to build that argument, buying ceremony and no protection (S2329 ADR-1). Should this
    parameter ever carry a real secret, that reasoning no longer holds and the suppression goes
    with it.

.PARAMETER NoZip
    Copy the raw file only. Use for an artifact nobody installs by hand, such as an AAB.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/copy-to-drive.ps1 `
        -Path wear/build/outputs/apk/standard/release/wear-standard-release.apk `
        -Name FastMediaSorter_wear_release.apk

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/copy-to-drive.ps1 `
        -Path app_v2/build/.../FastMediaSorter_standard_release.aab, `
              app_v2/build/.../FastMediaSorter_standard_release.apk

.NOTES
    Exit codes:
      0 - every raw copy landed (the ZIP may have been skipped, and says so), or the Drive sink
          is not reachable on this machine and the whole mirror was skipped
      1 - a -Path does not exist, or a raw copy itself failed
#>
[CmdletBinding()]
[Diagnostics.CodeAnalysis.SuppressMessageAttribute(
    'PSAvoidUsingPlainTextForPassword', 'ZipPassword',
    Justification = 'A courtesy formality for mail filters, not a secret: the value is printed to the console for the recipient and handed to 7-Zip as a -p command-line argument, so the process list shows it whatever type it carries. See .PARAMETER ZipPassword.')]
param(
    [Parameter(Mandatory)] [string[]] $Path,
    [string] $Name,
    [string] $DriveDir,
    [string] $ZipPassword = '1',
    [switch] $NoZip
)

. "$PSScriptRoot\project-paths.ps1"

$missing = $Path | Where-Object { -not (Test-Path -LiteralPath $_) }
if ($missing) {
    Write-Error "copy-to-drive: artifact not found: $($missing -join ', ')" -ErrorAction Continue
    exit 1
}

$sources = @($Path | ForEach-Object { Get-Item -LiteralPath $_ })
$single = $sources.Count -eq 1
# -Name renames the artifact only when there is exactly one; with a set it names the archive, so
# the members keep the names the build gave them (see .PARAMETER Name).
$destName = if ($single -and -not [string]::IsNullOrWhiteSpace($Name)) { $Name } else { $sources[0].Name }
$zipBaseName = if (-not $single -and -not [string]::IsNullOrWhiteSpace($Name)) { $Name } else { $destName }

$explicitDir = $PSBoundParameters.ContainsKey('DriveDir')
if (-not $explicitDir) {
    # The resolver warns and returns $null when the sink is absent. Skipping is the whole point
    # of this script's contract: the artifact is already built, so its courtesy copy cannot fail
    # the caller (S2326 ADR-3).
    $DriveDir = Get-ArtifactSink -Kind Drive
    if (-not $DriveDir) { exit 0 }
}
elseif (-not (Test-Path -LiteralPath $DriveDir)) {
    try {
        New-Item -ItemType Directory -Path $DriveDir -ErrorAction Stop | Out-Null
    }
    catch {
        Write-Error "copy-to-drive: Drive folder unavailable ($DriveDir): $_" -ErrorAction Continue
        exit 1
    }
}
# An explicitly passed -DriveDir may be relative, and 7-Zip stores a member under the path it was
# handed - so a relative destination would put "temp/x/drive/app.apk" inside the archive instead of
# "app.apk". The resolver's own return is already absolute; normalising here makes both entry paths
# agree before anything is copied or zipped (S2332).
$DriveDir = (Resolve-Path -LiteralPath $DriveDir).Path

$destPaths = @()
foreach ($source in $sources) {
    $destPath = Join-Path $DriveDir ($(if ($single) { $destName } else { $source.Name }))
    try {
        Copy-Item -LiteralPath $source.FullName -Destination $destPath -Force -ErrorAction Stop
    }
    catch {
        Write-Error "copy-to-drive: copy failed: $_" -ErrorAction Continue
        exit 1
    }
    Write-Host "Drive: $destPath ($([math]::Round($source.Length / 1MB, 2)) MB)" -ForegroundColor Green
    $destPaths += $destPath
}

if ($NoZip) { exit 0 }

# The resolver raises when 7-Zip is nowhere to be found; this script's whole contract is that a
# courtesy copy never fails a build, so the raise becomes $null and the existing skip runs (S2326).
$sevenZip = try { Get-ToolPath -Tool SevenZip -Quiet } catch { $null }
if (-not $sevenZip) {
    Write-Host "Drive: 7-Zip not found - raw copy only, no ZIP. Install from https://www.7-zip.org/" -ForegroundColor Yellow
    exit 0
}

$zipPath = Join-Path $DriveDir ([System.IO.Path]::ChangeExtension($zipBaseName, '.zip'))
# 7-Zip appends to an existing archive, so a stale member of the same name would survive
# a rebuild and the ZIP would disagree with the raw copy beside it.
if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }

# Zip from inside the Drive folder with bare member names. Relying on 7-Zip to strip the root of an
# absolute path would leave the archive's internal layout to a switch default that differs by
# version; naming the members directly makes it this script's decision (S2332).
$members = @($destPaths | ForEach-Object { Split-Path -Leaf $_ })
Push-Location -LiteralPath $DriveDir
try {
    & $sevenZip a -tzip "-p$ZipPassword" $zipPath @members | Out-Null
}
finally {
    Pop-Location
}
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $zipPath)) {
    Write-Host "Drive: ZIP step failed (7z exit $LASTEXITCODE) - raw copy is in place." -ForegroundColor Yellow
    exit 0
}
Write-Host "Drive: $zipPath (password $ZipPassword)" -ForegroundColor Cyan
exit 0
