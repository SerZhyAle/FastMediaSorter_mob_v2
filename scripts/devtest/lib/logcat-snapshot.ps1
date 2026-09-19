<#
.SYNOPSIS
  Bounded logcat snapshot for the builders that install and launch what they just built (S3297).

.DESCRIPTION
  Dot-source this file to get one function:
    . "$PSScriptRoot/../devtest/lib/logcat-snapshot.ps1"
    $snap = Save-DeviceLogcatSnapshot -Adb $adb -Serial $serial -Path $logFile
    # $snap.Ok, $snap.Path, $snap.Lines, $snap.ExitCode

  WHY IT EXISTS. Five builders under scripts/builders/ started `adb logcat -v threadtime` through
  Start-Process, redirected it into temp/logcat_<flavor>_<stamp>.log, printed "To stop: Stop-Process
  -Id <pid>" and returned. Nothing stopped them: on 2026-09-18 eight captures from one day were
  writing to the same device at once, and temp/ held 1.8 GB across 16 files, the largest 471 MB.
  The printed stop instruction is addressed to a human who never reads it, because the builds are
  started by a pipeline.

  WHY A DUMP AND NOT A STREAM. The stream was started AFTER `am start` - in
  build-standard-device.ps1 the activity launched on line 118 and the capture began on line 153,
  past the APK copy, the build journal and publish-artifact.ps1 - so it never contained the launch
  it was meant to record, only an endless tail after it. The builders already run `logcat -c` before
  installing, so by this point the ring buffer holds exactly the install and the launch, and one
  bounded dump takes that window whole and terminates on its own.

  WHY IT WAITS FIRST. `am start` returns when the intent is dispatched, not when the activity has
  run, so a dump taken immediately can end before the first line it is supposed to carry.

  WHY IT NEVER THROWS. Every caller reaches this after a successful install and launch. A device
  that refuses the dump is worth one printed line, not a failed build, so the outcome is returned
  and the caller decides.

  WHY A LIBRARY AND NOT A COPY. The same block sat in five builders, which is how one defect became
  five. scripts/devtest/lib/target-device.ps1 (S3169) established the shape for exactly these
  callers.
#>

[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Save-DeviceLogcatSnapshot {
    param(
        [Parameter(Mandatory)][string]$Adb,
        [Parameter(Mandatory)][string]$Serial,
        [Parameter(Mandatory)][string]$Path,
        [int]$Lines = 4000,
        [int]$SettleSeconds = 2
    )

    if ($SettleSeconds -gt 0) { Start-Sleep -Seconds $SettleSeconds }

    $parent = Split-Path -Parent $Path
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }

    $captured = @()
    $code = 0
    try {
        # -t implies -d; both are passed so the call reads as a dump at a glance and cannot be
        # turned back into a stream by dropping one flag.
        $captured = @(& $Adb -s $Serial logcat -d -v threadtime -t $Lines 2>$null)
        $code = $LASTEXITCODE
    }
    catch {
        # The adb binary itself could not be run. The build already succeeded, so this is reported
        # through the return value and the caller prints it; see WHY IT NEVER THROWS above.
        return [pscustomobject]@{
            Ok       = $false
            Path     = $Path
            Lines    = 0
            ExitCode = -1
            Message  = $_.Exception.Message
        }
    }

    Set-Content -LiteralPath $Path -Value $captured -Encoding utf8

    return [pscustomobject]@{
        Ok       = ($code -eq 0)
        Path     = $Path
        Lines    = $captured.Count
        ExitCode = $code
        Message  = if ($code -eq 0) { '' } else { "adb logcat exited $code" }
    }
}
