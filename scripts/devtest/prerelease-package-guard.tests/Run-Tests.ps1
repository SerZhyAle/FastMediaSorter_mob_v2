#requires -Version 7.0
<#
.SYNOPSIS
    S2709 regression suite for the pre-release configure step's package-presence guard.

.DESCRIPTION
    Hermetic: no device, no real adb, no network, no writes outside temp/. The suite drops a stub
    named `adb` on PATH, blanks ANDROID_HOME and ANDROID_SDK_ROOT so the script's own Get-Adb picks
    it up, and runs scripts/devtest/prerelease-configure.ps1 as a child process.

    The case that matters is the prefix neighbour. `pm list packages <id>` filters by substring, so
    a device carrying only the leftover com.sza.fastmediasorter.debug.test - the exact state the
    connected androidTest task leaves when it removes the app but not the test APK - answers a
    query about com.sza.fastmediasorter.debug with a non-empty line. A guard reading that as
    "installed" would walk straight back into the incident this ticket fixes, so the case asserts
    exit 11.

    The restore branch is covered too, without a device. The script resolves its dependencies from
    $PSScriptRoot, so the suite builds a sandbox holding a copy of the script, its config, its
    guard library and a STUB prerelease-prepare.ps1 - the script under test then reaches the stub
    through its own unmodified path expression, and production code carries no test seam. The stub
    answers three ways: installed, failed, and "reported success but installed nothing". What these
    cases assert is the branch that CALLS prepare, which is this ticket's own new code - the
    forwarded -DeviceId, the exit-code check, and the re-query that must not trust prepare's verdict.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/prerelease-package-guard.tests/Run-Tests.ps1

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$suiteDir  = $PSScriptRoot
$repoRoot  = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $suiteDir))
$configureScript = Join-Path $repoRoot 'scripts/devtest/prerelease-configure.ps1'
$guardLib        = Join-Path $repoRoot 'scripts/devtest/lib/prerelease-package-guard.ps1'

$APP  = 'com.sza.fastmediasorter.debug'
$TEST = 'com.sza.fastmediasorter.debug.test'

$script:passed = 0
$script:failed = 0

function Assert-Equal {
    param($Expected, $Actual, [string]$Label)
    $e = ($Expected | Out-String).Trim()
    $a = ($Actual   | Out-String).Trim()
    if ($e -eq $a) {
        Write-Host "PASS | $Label"
        $script:passed++
    } else {
        Write-Host "FAIL | $Label -> expected: $e | actual: $a"
        $script:failed++
    }
}

# Rule 10.1: scratch under temp/. One directory per run holding the stub copy.
$runDir = Join-Path $repoRoot ("temp/S2709/pkg-guard-tests-" + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $runDir -Force | Out-Null
Copy-Item -Path (Join-Path $suiteDir 'stub') -Destination (Join-Path $runDir 'stub') -Recurse -Force

# The sandbox: a copy of the script under test beside a STUB prerelease-prepare.ps1, so its own
# `Join-Path $PSScriptRoot 'prerelease-prepare.ps1'` resolves to something that installs nothing.
$sandbox = Join-Path $runDir 'devtest'
New-Item -ItemType Directory -Path (Join-Path $sandbox 'lib') -Force | Out-Null
Copy-Item $configureScript                                          (Join-Path $sandbox 'prerelease-configure.ps1') -Force
Copy-Item (Join-Path $repoRoot 'scripts/devtest/prerelease.config.psd1') $sandbox -Force
Copy-Item $guardLib                                                 (Join-Path $sandbox 'lib') -Force
Copy-Item (Join-Path $suiteDir 'stub/prepare-stub.ps1')             (Join-Path $sandbox 'prerelease-prepare.ps1') -Force
$sandboxScript = Join-Path $sandbox 'prerelease-configure.ps1'

# Get-Adb reads ANDROID_HOME and ANDROID_SDK_ROOT BEFORE PATH, so both must be blanked or the child
# resolves the real adb and the suite stops being hermetic.
$savedHome = $env:ANDROID_HOME
$savedRoot = $env:ANDROID_SDK_ROOT
$savedPath = $env:PATH
$env:ANDROID_HOME     = ''
$env:ANDROID_SDK_ROOT = ''
$env:PATH             = (Join-Path $runDir 'stub') + [System.IO.Path]::PathSeparator + $env:PATH

$pkgFile     = Join-Path $runDir 'installed-packages.txt'
$prepareLog  = Join-Path $runDir 'prepare-calls.txt'

function Invoke-Configure {
    param(
        [string]$Packages,
        [switch]$NoRestore,
        [string]$Prepare = 'install',
        [int]$PrepareExit = 10
    )
    # The package list is a FILE, not an env var: the restore branch installs the app between two
    # `pm list packages` calls inside one child, which a once-read variable cannot express.
    Set-Content -LiteralPath $pkgFile -Value (@($Packages -split ',' | Where-Object { $_ }) -join "`n")
    Remove-Item -LiteralPath $prepareLog -ErrorAction SilentlyContinue
    $env:FMS_STUB_PKG_FILE      = $pkgFile
    $env:FMS_STUB_PREPARE       = $Prepare
    $env:FMS_STUB_PREPARE_EXIT  = "$PrepareExit"
    $env:FMS_STUB_PREPARE_LOG   = $prepareLog

    $childArgs = @('-NoProfile', '-File', $sandboxScript, '-DeviceId', 'emulator-5554', '-Json')
    if ($NoRestore) { $childArgs += '-NoRestore' }
    $out = & pwsh @childArgs 2>&1
    $code = $LASTEXITCODE
    $calls = if (Test-Path -LiteralPath $prepareLog) { (Get-Content -LiteralPath $prepareLog -Raw) } else { '' }
    return [pscustomobject]@{ ExitCode = $code; Output = ($out | Out-String); PrepareCalls = $calls }
}

try {
    # --- Case 1: the app is installed. The guard passes and the run completes normally.
    $r = Invoke-Configure -Packages $APP
    Assert-Equal 0 $r.ExitCode 'app installed -> exit 0'
    Assert-Equal $true ($r.Output -match '"name":"package-present","status":"OK"') 'app installed -> stage package-present OK'

    # --- Case 2: only the leftover test APK. The substring match must NOT read it as the app.
    $r = Invoke-Configure -Packages $TEST -NoRestore
    Assert-Equal 11 $r.ExitCode 'only the .test neighbour installed -> exit 11'
    Assert-Equal $true ($r.Output -match '"name":"package-present","status":"FAIL"') 'neighbour only -> stage package-present FAIL'

    # --- Case 3: nothing installed, restore suppressed.
    $r = Invoke-Configure -Packages '' -NoRestore
    Assert-Equal 11 $r.ExitCode 'nothing installed with -NoRestore -> exit 11'
    Assert-Equal $true ($r.Output -match 'NoRestore') 'nothing installed -> detail names -NoRestore'

    # --- Case 4: app absent, restore succeeds. The gap step 1.4 leaves is crossed with no operator.
    $r = Invoke-Configure -Packages '' -Prepare 'install'
    Assert-Equal 0 $r.ExitCode 'absent + prepare installs -> exit 0'
    Assert-Equal $true ($r.Output -match '"name":"package-present","status":"OK".*restored') 'restore -> stage package-present OK names the restore'
    Assert-Equal $true ($r.PrepareCalls -match '-DeviceId emulator-5554') 'restore -> -DeviceId forwarded to prerelease-prepare.ps1'

    # --- Case 5: prepare fails. Its exit code is checked, not assumed.
    $r = Invoke-Configure -Packages '' -Prepare 'fail' -PrepareExit 10
    Assert-Equal 11 $r.ExitCode 'absent + prepare fails -> exit 11'
    Assert-Equal $true ($r.Output -match 'prerelease-prepare\.ps1 exit 10') 'prepare failure -> detail names its exit code'

    # --- Case 6: prepare returns 0 but installs nothing. The re-query must not trust its verdict.
    $r = Invoke-Configure -Packages '' -Prepare 'silent'
    Assert-Equal 11 $r.ExitCode 'absent + prepare silently installs nothing -> exit 11'
    Assert-Equal $true ($r.Output -match 'reported success but') 'silent prepare -> detail separates it from a prepare failure'

    # --- Case 7: the predicate itself, called directly. No process, no stub.
    . $guardLib
    Assert-Equal $true  (Test-PmPackagePresent -PmListOutput @("package:$APP")          -Package $APP) 'predicate: exact line present'
    Assert-Equal $false (Test-PmPackagePresent -PmListOutput @("package:$TEST")         -Package $APP) 'predicate: prefix neighbour is absent'
    Assert-Equal $true  (Test-PmPackagePresent -PmListOutput @("package:$TEST", "package:$APP") -Package $APP) 'predicate: app beside its neighbour is present'
    Assert-Equal $true  (Test-PmPackagePresent -PmListOutput @("package:$TEST`r`npackage:$APP") -Package $APP) 'predicate: several lines in one captured string'
    Assert-Equal $false (Test-PmPackagePresent -PmListOutput @()                        -Package $APP) 'predicate: empty output is absent'
    Assert-Equal $false (Test-PmPackagePresent -PmListOutput $null                      -Package $APP) 'predicate: null output is absent'
} finally {
    $env:ANDROID_HOME     = $savedHome
    $env:ANDROID_SDK_ROOT = $savedRoot
    $env:PATH             = $savedPath
    Remove-Item -Recurse -Force $runDir -ErrorAction SilentlyContinue
}

Write-Host ""
Write-Host ("prerelease-package-guard: {0} passed, {1} failed" -f $script:passed, $script:failed)
if ($script:failed -gt 0) { exit 1 }
exit 0
