# Stub `prerelease-prepare.ps1` for the S2709 suite.
#
# The suite copies it into a sandbox UNDER THE NAME prerelease-prepare.ps1, beside a copy of
# prerelease-configure.ps1, so the script under test resolves it through its own
# `Join-Path $PSScriptRoot 'prerelease-prepare.ps1'` with no seam added to production code.
# Intercepting the `pwsh` that launches it is not possible: PowerShell resolves `pwsh` to its own
# binary before consulting PATH, so a stub named pwsh on PATH is never reached (measured 2026-09-07).
#
# The real script builds an APK and talks to a device, so it cannot run in a hermetic suite - but
# the branch that CALLS it is this ticket's own new code, and leaving it uncovered would mean the
# argument vector, the exit-code check and the re-query are asserted by nothing.
#
# Per-case input comes from the environment:
#   FMS_STUB_PREPARE     'install' (default) installs the app, 'fail' exits 10, 'silent' exits 0
#                        without installing anything - the "prepare says OK but the app is absent"
#                        case, which is a distinct FAIL detail in the script under test.
#   FMS_STUB_PREPARE_EXIT exit code used by 'fail' (default 10)
#   FMS_STUB_PKG_FILE    file holding the installed package list the adb stub reads back
#   FMS_STUB_PREPARE_LOG file this stub appends its received argument vector to, so a case can
#                        assert -DeviceId was forwarded
#
# Exit codes:
#   0  - the stubbed prepare "succeeded"
#   10 - the stubbed prepare failed (or FMS_STUB_PREPARE_EXIT)

$ErrorActionPreference = 'Stop'

if ($env:FMS_STUB_PREPARE_LOG) {
    Add-Content -LiteralPath $env:FMS_STUB_PREPARE_LOG -Value (@($args) -join ' ')
}

$mode = if ($env:FMS_STUB_PREPARE) { $env:FMS_STUB_PREPARE } else { 'install' }

switch ($mode) {
    'fail' {
        $code = if ($env:FMS_STUB_PREPARE_EXIT) { [int]$env:FMS_STUB_PREPARE_EXIT } else { 10 }
        exit $code
    }
    'silent' { exit 0 }
    default {
        if ($env:FMS_STUB_PKG_FILE) {
            Set-Content -LiteralPath $env:FMS_STUB_PKG_FILE -Value 'com.sza.fastmediasorter.debug'
        }
        exit 0
    }
}
