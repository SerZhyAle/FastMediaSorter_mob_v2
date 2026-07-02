param(
    [switch]$DryRun,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

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
