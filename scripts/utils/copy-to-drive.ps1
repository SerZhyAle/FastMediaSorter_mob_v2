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
    The artifact to mirror. Must exist.

.PARAMETER Name
    File name to use in the Drive folder. Defaults to the source file's own name.
    Pass an explicit name to keep the established unversioned convention
    (FastMediaSorter_wear_release.apk), which overwrites rather than accumulating.

.PARAMETER DriveDir
    Destination folder. Defaults to the project's Google Drive work folder.

.PARAMETER ZipPassword
    Password for the ZIP copy. Defaults to the project's long-standing '1'.

.PARAMETER NoZip
    Copy the raw file only. Use for an artifact nobody installs by hand, such as an AAB.

.EXAMPLE
    pwsh -NoProfile -File scripts/utils/copy-to-drive.ps1 `
        -Path wear/build/outputs/apk/release/wear-release.apk `
        -Name FastMediaSorter_wear_release.apk

.NOTES
    Exit codes:
      0 - the raw copy landed (the ZIP may have been skipped, and says so)
      1 - -Path does not exist, or the raw copy itself failed
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string] $Path,
    [string] $Name,
    [string] $DriveDir = 'c:\GD\WORK\FastMediaSorter',
    [string] $ZipPassword = '1',
    [switch] $NoZip
)

if (-not (Test-Path -LiteralPath $Path)) {
    Write-Error "copy-to-drive: artifact not found: $Path" -ErrorAction Continue
    exit 1
}

$source = Get-Item -LiteralPath $Path
$destName = if ([string]::IsNullOrWhiteSpace($Name)) { $source.Name } else { $Name }

if (-not (Test-Path -LiteralPath $DriveDir)) {
    try {
        New-Item -ItemType Directory -Path $DriveDir -ErrorAction Stop | Out-Null
    }
    catch {
        Write-Error "copy-to-drive: Drive folder unavailable ($DriveDir): $_" -ErrorAction Continue
        exit 1
    }
}

$destPath = Join-Path $DriveDir $destName
try {
    Copy-Item -LiteralPath $source.FullName -Destination $destPath -Force -ErrorAction Stop
}
catch {
    Write-Error "copy-to-drive: copy failed: $_" -ErrorAction Continue
    exit 1
}
Write-Host "Drive: $destPath ($([math]::Round($source.Length / 1MB, 2)) MB)" -ForegroundColor Green

if ($NoZip) { exit 0 }

$sevenZip = 'C:\Program Files\7-Zip\7z.exe'
if (-not (Test-Path -LiteralPath $sevenZip)) {
    Write-Host "Drive: 7-Zip not found - raw copy only, no ZIP. Install from https://www.7-zip.org/" -ForegroundColor Yellow
    exit 0
}

$zipPath = Join-Path $DriveDir ([System.IO.Path]::ChangeExtension($destName, '.zip'))
# 7-Zip appends to an existing archive, so a stale member of the same name would survive
# a rebuild and the ZIP would disagree with the raw copy beside it.
if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }

& $sevenZip a -tzip "-p$ZipPassword" $zipPath $destPath | Out-Null
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $zipPath)) {
    Write-Host "Drive: ZIP step failed (7z exit $LASTEXITCODE) - raw copy is in place." -ForegroundColor Yellow
    exit 0
}
Write-Host "Drive: $zipPath (password $ZipPassword)" -ForegroundColor Cyan
exit 0
