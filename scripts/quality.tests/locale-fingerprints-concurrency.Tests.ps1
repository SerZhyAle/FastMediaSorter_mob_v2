#requires -Version 7.0
<#
.SYNOPSIS
    S3008: tests that two processes writing the fingerprint registry at once both keep their entries.

.DESCRIPTION
    The registry is one JSON document and every writer rewrites it whole, so a writer that loaded the
    file before another writer's save and saved after it discarded everything the other had added.
    Both processes exit 0 and both print their own stamp count, so no output distinguishes the broken
    library from the fixed one - only an assertion that both writers' entries survive can.

    The interleave is forced rather than hoped for: the first child holds the store inside its
    mutation long enough that the second child's read would land in the middle of it. Against a
    library whose read sits outside the lock the second entry wins and the first is gone; against
    Edit-LocaleSourceFingerprints the second child waits, re-reads and adds to what it finds.

    Everything runs against a scratch store under temp/scratch, so the shipped registry is never
    opened for writing by a test.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  every assertion passed.
      1  at least one assertion failed.
#>

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path
. (Join-Path $repoRoot 'scripts/quality/lib/locale-fingerprints.ps1')

$script:pass = 0
$script:fail = 0

function Assert-Equal {
    param([Parameter(Mandatory)][string]$Name, $Expected, $Actual)
    if ($Expected -eq $Actual) {
        Write-Host ("PASS | {0} | {1}" -f $Name, $Actual)
        $script:pass++
    }
    else {
        Write-Host ("FAIL | {0} | expected: {1} | actual: {2}" -f $Name, $Expected, $Actual)
        $script:fail++
    }
}

$scratchDir = Join-Path $repoRoot 'temp/scratch/locale-fingerprints-concurrency'
if (Test-Path -LiteralPath $scratchDir) { Remove-Item -LiteralPath $scratchDir -Recurse -Force }
New-Item -ItemType Directory -Path $scratchDir -Force | Out-Null

$storePath = Join-Path $scratchDir 'store.json'
$writerPath = Join-Path $scratchDir 'writer.ps1'
$libPath = Join-Path $repoRoot 'scripts/quality/lib/locale-fingerprints.ps1'

# The sleep sits INSIDE the mutation so it widens the window the lock has to cover, not the window
# before it - a sleep before the call would serialize the two children by accident and pass either way.
$writerSource = @'
param([string]$Lib, [string]$Store, [string]$Locale, [string]$Identity, [string]$Hash, [int]$HoldMs)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. $Lib
Edit-LocaleSourceFingerprints -Path $Store -Mutate {
    param($fingerprints)
    Start-Sleep -Milliseconds $HoldMs
    Update-LocaleSourceFingerprint -Fingerprints $fingerprints -Locale $Locale -Identity $Identity -Hash $Hash
} | Out-Null
exit 0
'@
Set-Content -LiteralPath $writerPath -Value $writerSource -Encoding UTF8

function Start-Writer {
    param([string]$Identity, [string]$Hash, [int]$HoldMs)
    return Start-Process -FilePath 'pwsh' -PassThru -WindowStyle Hidden -ArgumentList @(
        '-NoProfile', '-File', $writerPath,
        '-Lib', $libPath, '-Store', $storePath, '-Locale', 'de',
        '-Identity', $Identity, '-Hash', $Hash, '-HoldMs', $HoldMs
    )
}

Write-Host '=== Test 1: two concurrent writers both keep their entry ==='

$idSlow = 'app_v2|main|strings.xml|slow_writer'
$idFast = 'app_v2|main|strings.xml|fast_writer'

$slow = Start-Writer -Identity $idSlow -Hash '1111222233334444' -HoldMs 2500
# Long enough that the first child is certainly inside its mutation, short enough that it is still
# holding when this one reads.
Start-Sleep -Milliseconds 700
$fast = Start-Writer -Identity $idFast -Hash '5555666677778888' -HoldMs 0

$slow.WaitForExit()
$fast.WaitForExit()

Assert-Equal 'slow writer exits 0' 0 $slow.ExitCode
Assert-Equal 'fast writer exits 0' 0 $fast.ExitCode

$store = Get-LocaleSourceFingerprints -Path $storePath
$deMap = if ($store.ContainsKey('de')) { $store['de'] } else { @{} }

Assert-Equal 'the slow writer entry survived' '1111222233334444' $(if ($deMap.ContainsKey($idSlow)) { $deMap[$idSlow] } else { '<absent>' })
Assert-Equal 'the fast writer entry survived' '5555666677778888' $(if ($deMap.ContainsKey($idFast)) { $deMap[$idFast] } else { '<absent>' })
Assert-Equal 'the locale holds exactly the two entries' 2 $deMap.Count

Write-Host ''
Write-Host '=== Test 2: no temporary file is left beside the store ==='

$leftovers = @(Get-ChildItem -LiteralPath $scratchDir -Filter '*.tmp' -ErrorAction SilentlyContinue)
Assert-Equal 'atomic replace left no .tmp behind' 0 $leftovers.Count

Write-Host ''
Write-Host '=== Test 3: the mutex is keyed on the store, so scratch stores never contend ==='

$nameA = Get-LocaleFingerprintsMutexName -Path $storePath
$nameB = Get-LocaleFingerprintsMutexName -Path (Join-Path $scratchDir 'other.json')
Assert-Equal 'the same path yields the same name' $nameA (Get-LocaleFingerprintsMutexName -Path $storePath)
Assert-Equal 'a different store yields a different name' $true ($nameA -ne $nameB)
Assert-Equal 'the name carries no path separator' $true ($nameA.Substring(6) -notmatch '[\\/]')

Remove-Item -LiteralPath $scratchDir -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ''
Write-Host ("TOTAL | pass: {0} | fail: {1}" -f $script:pass, $script:fail)
if ($script:fail -gt 0) { exit 1 }
exit 0
