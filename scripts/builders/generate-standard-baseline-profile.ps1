param(
    [switch]$DryRun,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "generate-standard-baseline-profile.ps1"
try {

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $projectRoot

$gradleCommand = New-Object System.Collections.Generic.List[string]
$null = $gradleCommand.Add(":benchmark:collectNonMinifiedReleaseBaselineProfile")
$null = $gradleCommand.Add("-Pchaquopy.enabled=false")

if ($GradleArgs) {
    $GradleArgs | ForEach-Object { $null = $gradleCommand.Add($_) }
}

Write-Host "Generate standard baseline profile.." -ForegroundColor Cyan
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
