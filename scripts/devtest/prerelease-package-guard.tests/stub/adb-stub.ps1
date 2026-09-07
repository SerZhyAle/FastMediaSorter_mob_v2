# Stub `adb` for scripts/devtest/prerelease-package-guard.tests/Run-Tests.ps1 (S2709).
#
# Reached only through the sibling adb.cmd, which prerelease-configure.ps1's Get-Adb picks off PATH
# once the suite has blanked ANDROID_HOME and ANDROID_SDK_ROOT. Answers every call that script
# makes, so the whole run reaches its -Json branch with no device attached.
#
# DELIBERATELY NO param() BLOCK, for the reason recorded in adb.tests/stub/adb-stub.ps1: with one,
# PowerShell binds adb's own `-s <id>` as a parameter name and the call dies before reaching the
# table below.
#
# `pm list packages <id>` here filters by SUBSTRING, which is what the real pm does and is the
# whole point of the suite - an exact-matching stub would pass a guard that reads a leftover
# .test package as the app.
#
# Per-case input comes from the environment, set by the suite before each child process:
#   FMS_STUB_PKG_FILE  file holding the installed package ids, one per line. Takes precedence over
#                      FMS_STUB_PACKAGES and is re-read on every call, because the restore branch
#                      installs the app BETWEEN two `pm list packages` calls in one child process -
#                      an environment variable read once could not express that.
#   FMS_STUB_PACKAGES  comma-separated installed package ids (default the debug id; empty = none)
#   FMS_STUB_SDK       ro.build.version.sdk (default 34)
#   FMS_STUB_LOCALE    what get-app-locales reports (default ru)
#
# Exit codes:
#   0  - the call matched the table
#   99 - the call matched nothing. Silence would turn any change in the script's adb usage into a
#        silently green suite, which is the defect class this suite exists for (adb.tests ADR-3).

$ErrorActionPreference = 'Stop'

$call = @($args)

# Drop adb's own device selector; nothing this stub answers depends on which device asked.
if ($call.Count -ge 2 -and $call[0] -eq '-s') {
    $call = if ($call.Count -gt 2) { $call[2..($call.Count - 1)] } else { @() }
}
$sig = ($call -join ' ')

$packages =
    if ($env:FMS_STUB_PKG_FILE -and (Test-Path -LiteralPath $env:FMS_STUB_PKG_FILE)) {
        @(Get-Content -LiteralPath $env:FMS_STUB_PKG_FILE | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    } elseif ($env:FMS_STUB_PKG_FILE) {
        @()   # the file is the state: absent means nothing is installed, not "fall back to the env"
    } elseif ($null -ne $env:FMS_STUB_PACKAGES) {
        @($env:FMS_STUB_PACKAGES -split ',' | Where-Object { $_ })
    } else {
        @('com.sza.fastmediasorter.debug')
    }
$sdk    = if ($env:FMS_STUB_SDK)    { $env:FMS_STUB_SDK }    else { '34' }
$locale = if ($env:FMS_STUB_LOCALE) { $env:FMS_STUB_LOCALE } else { 'ru' }

switch -Regex ($sig) {

    '^shell pm list packages (?<pkg>\S+)$' {
        $wanted = $Matches['pkg']
        foreach ($p in $packages) {
            if ($p -like "*$wanted*") { Write-Output "package:$p" }
        }
        exit 0
    }

    '^shell getprop ro\.build\.version\.sdk$' { Write-Output $sdk; exit 0 }

    '^shell cmd locale set-app-locales .+$'   { exit 0 }
    '^shell cmd locale get-app-locales .+$'   { Write-Output $locale; exit 0 }

    '^shell am force-stop \S+$'               { exit 0 }
    '^shell am start -n \S+$'                 { Write-Output 'Starting: Intent'; exit 0 }
}

Write-Error "adb-stub: unmatched call '$sig'"
exit 99
