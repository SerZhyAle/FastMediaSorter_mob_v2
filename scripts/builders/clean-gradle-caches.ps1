# Gradle caches clean script (project-level)

$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path "$PSScriptRoot\..\..\"

Write-Host "Cleaning Gradle caches and build artifacts..." -ForegroundColor Cyan

$pathsToRemove = @(
    (Join-Path $projectRoot ".gradle"),
    (Join-Path $projectRoot "build"),
    (Join-Path $projectRoot "app_v2\build")
)

foreach ($path in $pathsToRemove) {
    if (Test-Path -Path $path) {
        Write-Host "Removing: $path" -ForegroundColor Gray
        Remove-Item -Path $path -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$gradleWrapper = Join-Path $projectRoot "gradlew.bat"

Write-Host "Stopping Gradle daemons..." -ForegroundColor Gray
& $gradleWrapper --stop | Out-Null

Write-Host "Running gradlew clean --no-build-cache..." -ForegroundColor Gray
& $gradleWrapper clean --no-build-cache

if ($LASTEXITCODE -ne 0) {
    Write-Host "Gradle clean failed with exit code $LASTEXITCODE" -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host "Gradle cache cleanup completed." -ForegroundColor Green
