# S3371: produce the CycloneDX SBOM for one module.
#
# Exists so nothing has to invoke gradlew by hand for it. The SBOM is a dependency-admission
# artifact, not a build output: the weekly dependency-scan workflow uploads it, and a human asking
# "what ships in this module" runs the same command the workflow does. Routing it through a builder
# also keeps it inside Rule 23 - the task resolves every configuration of the module, which is the
# same daemon a compile would take.
#
# Exit codes (CLAUDE.md Rule 7):
#   0 - the SBOM was written; its path is printed.
#   1 - the gradle task failed, or it reported success and wrote no file.
#   2 - the module name is not one this repository has.
#   4 - the module's build domain is held by another session (Enter-BuildLockOrExit).

[CmdletBinding()]
param(
    [ValidateSet('app_v2', 'wear')]
    [string]$Module = 'app_v2',
    [string]$OutDir
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. "$PSScriptRoot\..\utils\agent-lock.ps1"

$projectRoot = (Resolve-Path "$PSScriptRoot\..\..\").Path.TrimEnd('\')
$domain = if ($Module -eq 'wear') { 'Build.Wear' } else { 'Build.Phone' }
$gradlew = Join-Path $projectRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradlew)) {
    Write-Host "build-sbom: gradlew.bat not found at $gradlew" -ForegroundColor Red
    exit 2
}

Enter-BuildLockOrExit -Reason "build-sbom.ps1 ($Module)" -Domain $domain
try {
    Write-Host "Producing the CycloneDX SBOM for $Module .." -ForegroundColor Cyan
    & $gradlew ":${Module}:cyclonedxBom" --configuration-cache
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'build-sbom: the cyclonedxBom task failed.' -ForegroundColor Red
        exit 1
    }

    # The plugin's own default output directory. Resolved rather than assumed: a task that reports
    # success and writes nothing is the failure this check exists to catch.
    $produced = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot "$Module/build/reports") -Recurse -File -Filter 'bom.json' -ErrorAction SilentlyContinue)
    if ($produced.Count -eq 0) {
        Write-Host 'build-sbom: the task reported success but no bom.json was written.' -ForegroundColor Red
        exit 1
    }

    foreach ($file in $produced) {
        $destination = $file.FullName
        if ($OutDir) {
            if (-not (Test-Path -LiteralPath $OutDir)) { New-Item -ItemType Directory -Path $OutDir -Force | Out-Null }
            $destination = Join-Path $OutDir "sbom-$Module.json"
            Copy-Item -LiteralPath $file.FullName -Destination $destination -Force
        }
        Write-Host "SBOM: $destination" -ForegroundColor Green
    }
    exit 0
}
finally {
    Exit-AgentLock -Name 'Build' -Domains @($domain)
}
