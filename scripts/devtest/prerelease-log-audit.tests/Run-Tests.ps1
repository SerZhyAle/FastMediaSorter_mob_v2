#requires -Version 7.0
<#
.SYNOPSIS
    S1859 / S1969 regression suite for the pre-release log audit's classification rules.

.DESCRIPTION
    Hermetic: drives scripts/devtest/prerelease-log-audit.ps1 against two recorded threadtime
    fixtures. No adb call, no device, no network, no writes outside this folder.

    The case that matters is the foreign-pid one. It replays the 2026-08-20 sweep finding -
    two E/A clusters emitted by com.google.android.googlequicksearchbox:interactor - and
    asserts they no longer reach the actionable list, so the suite goes red the moment anyone
    makes the tag denylists the deciding filter again.

    The second fixture drops the `Start proc` announcement and asserts the audit says
    `heuristic` and does NOT hide the foreign cluster. That is the documented weakness of the
    fallback, not a defect: a capture that never saw the app start cannot attribute a line, and
    an audit that silently claimed otherwise is what this ticket fixed.

    The last pair covers S1969's software-render guard from both sides: the same in-process
    E/FrameEvents line is benign in a capture that carries EGL_emulation and stays actionable in
    one that does not. Suppressing the tag outright is what the pair exists to catch - on a
    physical device a missed frame release can be a real defect.

.EXAMPLE
    pwsh -NoProfile -File scripts/devtest/prerelease-log-audit.tests/Run-Tests.ps1

.EXIT CODES
    0 - every case passed.
    1 - at least one case failed.
#>
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# The S2394 cases assert on localized toast text, so the child process's stdout must be decoded as
# UTF-8 - the console default mangles Cyrillic and turns a correct verdict into a red case.
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$auditScript = Join-Path $repoRoot 'scripts/devtest/prerelease-log-audit.ps1'

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

function Invoke-Audit {
    param([string]$FixtureName)
    $fixture = Join-Path $PSScriptRoot "fixtures/$FixtureName"
    $stdout = & pwsh -NoProfile -File $auditScript -LogFile $fixture -Json
    return ($stdout | ConvertFrom-Json)
}

function Get-ActionableTags {
    param($Result)
    return @($Result.actionable | ForEach-Object { $_.tag })
}

# Case 1 - the capture announces the app process: pid decides.
$withStartProc = Invoke-Audit 'logcat_foreign_pid_sample.txt'
$tags = Get-ActionableTags $withStartProc

Assert-Equal 'pid' $withStartProc.attribution 'attribution is pid when Start proc is present'
Assert-Equal 1 $withStartProc.appPidCount 'exactly one app process id recovered'
Assert-Equal $true ($tags -contains 'ResourceScanUseCase') 'app-pid error stays actionable'
Assert-Equal $false ($tags -contains 'A') 'S1859: foreign-pid E/A cluster is not actionable'
Assert-Equal $false ($tags -contains 'GsaVoiceInteraction') 'foreign-pid cluster under an unlisted tag is not actionable'
Assert-Equal 1 $withStartProc.actionableCount 'the app line is the only actionable cluster'
Assert-Equal 1 $withStartProc.benignCount 'the app WifiRequiredException line is still classified benign'
Assert-Equal 1 $withStartProc.exitCode 'a real app error still exits 1'

# Case 2 - no announcement to attribute against: the audit falls back and says so.
$noStartProc = Invoke-Audit 'logcat_no_start_proc_sample.txt'
$fallbackTags = Get-ActionableTags $noStartProc

Assert-Equal 'heuristic' $noStartProc.attribution 'attribution is heuristic without Start proc'
Assert-Equal 0 $noStartProc.appPidCount 'no app process id recovered'
Assert-Equal $true ($fallbackTags -contains 'A') 'fallback admits the foreign cluster - the mode the report must disclose'

# Case 3 - the same decision in the other capture format. `-v time` puts the pid in parentheses
# after the tag instead of in its own column, so it needs its own case: the audit parses both
# formats and a regex that stopped capturing the pid there would fail silently, in heuristic
# mode, on exactly the sweeps that captured in the older format.
$timeFormat = Invoke-Audit 'logcat_time_format_sample.txt'
$timeTags = Get-ActionableTags $timeFormat

Assert-Equal 'pid' $timeFormat.attribution 'attribution is pid in -v time captures too'
Assert-Equal 1 $timeFormat.appPidCount '-v time: app process id recovered'
Assert-Equal $true ($timeTags -contains 'ResourceScanUseCase') '-v time: app-pid error stays actionable'
Assert-Equal $false ($timeTags -contains 'A') '-v time: foreign-pid E/A cluster is not actionable'

# Case 4 - S1969: emulator capture. EGL_emulation proves the run was software-rendered, so the
# in-process libgui frame-release miss is benign and the audit exits clean.
$emulatorGpu = Invoke-Audit 'logcat_emulator_frameevents_sample.txt'

Assert-Equal $true $emulatorGpu.softwareRendered 'S1969: EGL_emulation marks the capture software-rendered'
Assert-Equal 0 $emulatorGpu.actionableCount 'S1969: FrameEvents is not actionable on a software-rendered capture'
Assert-Equal 1 $emulatorGpu.benignCount 'S1969: the FrameEvents cluster is kept and reported as benign'
Assert-Equal 0 $emulatorGpu.exitCode 'S1969: an otherwise clean emulator run exits 0'

# Case 5 - the same line without the marker. A physical-device capture carries no EGL_emulation, so
# the miss keeps its defect meaning and still fails the step. This is the half a tag-only allowlist
# would silently lose.
$deviceGpu = Invoke-Audit 'logcat_device_frameevents_sample.txt'
$deviceTags = Get-ActionableTags $deviceGpu

Assert-Equal $false $deviceGpu.softwareRendered 'S1969: no EGL_emulation means the capture is not software-rendered'
Assert-Equal $true ($deviceTags -contains 'FrameEvents') 'S1969: FrameEvents stays actionable without the marker'
Assert-Equal 1 $deviceGpu.exitCode 'S1969: an unguarded frame-release miss still exits 1'

# Case 6 - S2394: informational toasts only. Maestro logs them at D from its own process, so every
# filter in the audit discards the line; the raw pass must still see them, and must not fail the run
# for a slideshow notice. Before this ticket the counter read 0 here because nothing looked at all.
$infoToast = Invoke-Audit 'logcat_toast_sample.txt'

Assert-Equal 2 $infoToast.infoToastCount 'S2394: both Maestro toasts are seen'
Assert-Equal $true ($infoToast.infoToasts -contains 'Слайд-шоу включено с интервалом 10 секунд') 'S2394: the informational text is reported verbatim'
Assert-Equal 0 $infoToast.toastCount 'S2394: an informational toast is not an error toast'
Assert-Equal 0 $infoToast.exitCode 'S2394: a run with only informational toasts stays clean'

# Case 7 - both error halves in one capture: a plain Toast whose text is a localized error string,
# and the app's own Snackbar record. The two paths do not overlap - Maestro never sees a Snackbar -
# so each has to be proven separately.
$errorToast = Invoke-Audit 'logcat_error_toast_sample.txt'
$errorTexts = @($errorToast.toasts | ForEach-Object { $_.msg })

Assert-Equal $true ($errorTexts -contains 'Не получилось скопировать файлы.') 'S2394: a toast matching a localized error string is an error toast'
Assert-Equal $true (@($errorTexts | Where-Object { $_ -match 'AppErrorNotifier: shown \[CRITICAL\]' }).Count -eq 1) 'S2394: the app-side CRITICAL Snackbar record is an error surface'
Assert-Equal 1 $errorToast.infoToastCount 'S2394: the informational toast in the same capture stays informational'
Assert-Equal 1 $errorToast.exitCode 'S2394: an error toast fails the run'

Write-Host ''
Write-Host ("passed: {0} | failed: {1}" -f $script:passed, $script:failed)
if ($script:failed -gt 0) { exit 1 }
exit 0
