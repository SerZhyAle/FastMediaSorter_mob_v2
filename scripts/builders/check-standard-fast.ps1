param(
    [ValidateSet("Code", "Resources", "CodeAndResources", "Unit", "Assemble")]
    [string]$Mode = "CodeAndResources",
    [string]$Tests,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $projectRoot

function Get-GradleTaskList {
    switch ($Mode) {
        "Code" { return @(":app_v2:compileStandardDebugKotlin") }
        "Resources" { return @(":app_v2:processStandardDebugResources") }
        "CodeAndResources" { return @(":app_v2:compileStandardDebugKotlin", ":app_v2:processStandardDebugResources") }
        "Unit" { return @(":app_v2:testStandardDebugUnitTest") }
        "Assemble" { return @(":app_v2:assembleStandardDebug") }
        default { throw "Unsupported mode: $Mode" }
    }
}

$gradleArgs = New-Object System.Collections.Generic.List[string]
Get-GradleTaskList | ForEach-Object { $null = $gradleArgs.Add($_) }
$null = $gradleArgs.Add("-Pchaquopy.enabled=false")
$null = $gradleArgs.Add("--configuration-cache")

if ($Tests) {
    if ($Mode -ne "Unit") {
        throw "-Tests is supported only with -Mode Unit"
    }
    $null = $gradleArgs.Add("--tests")
    $null = $gradleArgs.Add($Tests)
}

Write-Host "Fast standard check.." -ForegroundColor Cyan
Write-Host "Mode: $Mode" -ForegroundColor Yellow
if ($Tests) {
    Write-Host "Tests filter: $Tests" -ForegroundColor Yellow
}
Write-Host "Command: .\\gradlew.bat $($gradleArgs -join ' ')" -ForegroundColor DarkGray

& "$projectRoot\gradlew.bat" @gradleArgs 2>&1 | ForEach-Object {
    $line = [string]$_
    if ($Quiet -and ($line -match " UP-TO-DATE$" -or $line -match " NO-SOURCE$" -or $line -match " FROM-CACHE$")) {
        return
    }
    Write-Host $line
}

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nFast check failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "`nFast check passed." -ForegroundColor Green
