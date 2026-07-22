param(
    [ValidateSet("Code", "Resources", "CodeAndResources", "Unit", "Assemble")]
    [string]$Mode = "CodeAndResources",
    # S0826: per-flavor fast compile check. Standard is the default; NoLegal needs its own
    # path because it bundles Python via Chaquopy (see flag handling below).
    # S0404: the capability-gated flavors (Lite / Photos / Legacy) compile the no-op source sets,
    # so a seam change needs a fast check on one of them too.
    # S0989: Vr compiles the src/vr source set (OpenXR immersive host) - needed when a change
    # lives only under src/vr, which the Standard/NoLegal checks never compile.
    [ValidateSet("Standard", "NoLegal", "Lite", "Photos", "Legacy", "Vr")]
    [string]$Flavor = "Standard",
    [string]$Tests,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

. "$PSScriptRoot\..\utils\agent-lock.ps1"
Enter-BuildLockOrExit -Reason "check-standard-fast.ps1"
try {

$projectRoot = Resolve-Path "$PSScriptRoot\..\.."
Set-Location $projectRoot

function Get-GradleTaskList {
    switch ($Mode) {
        "Code" { return @(":app_v2:compile${Flavor}DebugKotlin") }
        "Resources" { return @(":app_v2:process${Flavor}DebugResources") }
        "CodeAndResources" { return @(":app_v2:compile${Flavor}DebugKotlin", ":app_v2:process${Flavor}DebugResources") }
        "Unit" { return @(":app_v2:test${Flavor}DebugUnitTest") }
        "Assemble" { return @(":app_v2:assemble${Flavor}Debug") }
        default { throw "Unsupported mode: $Mode" }
    }
}

$gradleArgs = New-Object System.Collections.Generic.List[string]
Get-GradleTaskList | ForEach-Object { $null = $gradleArgs.Add($_) }
# Flavors without Python: disable Chaquopy and use the configuration cache for speed.
# NoLegal bundles Python via Chaquopy, whose API must stay on the compile classpath and
# whose tasks are not configuration-cache serialisable - so keep it enabled and skip the cache.
if ($Flavor -ne "NoLegal") {
    $null = $gradleArgs.Add("-Pchaquopy.enabled=false")
    $null = $gradleArgs.Add("--configuration-cache")
}

if ($Tests) {
    if ($Mode -ne "Unit") {
        throw "-Tests is supported only with -Mode Unit"
    }
    $null = $gradleArgs.Add("--tests")
    $null = $gradleArgs.Add($Tests)
}

Write-Host "Fast $Flavor check.." -ForegroundColor Cyan
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

}
finally {
    Exit-AgentLock -Name Build
}
