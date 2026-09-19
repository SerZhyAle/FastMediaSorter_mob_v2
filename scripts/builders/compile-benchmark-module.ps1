# Compiles the :benchmark module's Kotlin without a device.
#
# S3322: the phone fast checks (.\a.ps1 fk / fc) compile :app_v2 and exit 0 without touching a single
# benchmark file, so a change to the macrobenchmark journeys had no cheap verdict of its own - the only
# command that compiled them was a full connected benchmark run, which needs a device.
#
# Exit codes:
#   0 - the module's Kotlin compiled
#   1 - gradle failed (compile error, or the variant does not exist - gradle names the candidates)
#   2 - refused: the Build.Phone domain is held by another session (Enter-BuildLockOrExit exits on its own)

param(
    # Measured 2026-09-19, the module offers exactly three Kotlin compile variants -
    # `BenchmarkBenchmark`, `BenchmarkRelease` and `NonMinifiedBenchmark` - and no Debug one, so a bare
    # `Benchmark` is ambiguous and a `Debug` task name does not exist. BenchmarkRelease is the variant
    # the connected benchmark run itself uses.
    [string]$Variant = "BenchmarkRelease",
    [switch]$DryRun,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
# Build.Phone only, as in run-standard-macrobenchmark.ps1 (S2170): the module builds against app_v2 and
# never touches wear.
Enter-BuildLockOrExit -Reason "compile-benchmark-module.ps1" -Domain Build.Phone
try {

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $projectRoot

$gradleCommand = New-Object System.Collections.Generic.List[string]
$null = $gradleCommand.Add(":benchmark:compile${Variant}Kotlin")
$null = $gradleCommand.Add("-Pchaquopy.enabled=false")

if ($GradleArgs) {
    $GradleArgs | ForEach-Object { $null = $gradleCommand.Add($_) }
}

Write-Host "Compile :benchmark module (variant $Variant).." -ForegroundColor Cyan
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
