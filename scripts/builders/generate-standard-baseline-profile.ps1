param(
    [switch]$DryRun,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
# S2170: Build.Phone, not the full set. The :benchmark module builds against app_v2's release
# variant and never touches wear, so the only real collision is with a phone build - holding the
# watch domain here would queue a wear session behind a benchmark it shares nothing with.
Enter-BuildLockOrExit -Reason "generate-standard-baseline-profile.ps1" -Domain Build.Phone
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
    Exit-AgentLock -Name 'Build' -Domains @('Build.Phone')
}
