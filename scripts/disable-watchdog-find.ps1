#requires -Version 5.1
<#
.SYNOPSIS
    Enable/disable the WatchdogRunawayFind scheduled task.

.DESCRIPTION
    WatchdogRunawayFind periodically kills runaway `find.exe`/`rg.exe`
    processes (excessive handles, or orphaned after the parent shell
    exited - see MSYS `find` orphan issue from agentic Bash sessions).
    This script toggles that task without touching its schedule/action.

.PARAMETER Enable
    Re-enable the task instead of disabling it.

.EXAMPLE
    ./disable-watchdog-find.ps1
    Disables the task (default).

.EXAMPLE
    ./disable-watchdog-find.ps1 -Enable
    Re-enables the task.
#>
param(
    [switch]$Enable
)

$taskName = "WatchdogRunawayFind"
$verb = if ($Enable) { "/ENABLE" } else { "/DISABLE" }

$task = schtasks /Query /TN $taskName 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Warning "Task '$taskName' not found. Nothing to do."
    exit 1
}

schtasks /Change /TN $taskName $verb
schtasks /Query /TN $taskName /FO LIST | Select-String "Status"
