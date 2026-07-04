param(
    [switch]$DryRun,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "run-standard-macrobenchmark.ps1"
try {

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $projectRoot

$gradleCommand = New-Object System.Collections.Generic.List[string]
$null = $gradleCommand.Add(":benchmark:connectedBenchmarkReleaseAndroidTest")
$null = $gradleCommand.Add("-Pchaquopy.enabled=false")

if ($GradleArgs) {
    $GradleArgs | ForEach-Object { $null = $gradleCommand.Add($_) }
}

Write-Host "Run standard macrobenchmark.." -ForegroundColor Cyan
Write-Host "Command: .\gradlew.bat $($gradleCommand -join ' ')" -ForegroundColor DarkGray

if ($DryRun) {
    exit 0
}

& "$projectRoot\gradlew.bat" @gradleCommand
exit $LASTEXITCODE

}
finally {
    Exit-AgentLock -Name Build
}
