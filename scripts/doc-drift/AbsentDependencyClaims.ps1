# AbsentDependencyClaims.ps1 - a document may not credit a capability to a dependency no build ships (S3445)
#
# Dot-sourced by scripts/quality/assert-doc-pin-drift.ps1 and by tests/Run-Tests.ps1. Defines
# functions only, so it is safe under the wrapper's strict mode.
#
# The pin comparison next door checks version NUMBERS a document repeats. This checks a claim of
# EXISTENCE: the user documents described ML Kit text recognition for months after S0386 had removed
# the artifact, in three locales, because nothing tied the sentence to the dependency set.

function Test-DependencyDeclared {
    <# True when `group:artifact` is declared in the version catalog or named in a non-comment line
       of any build.gradle.kts. A catalog entry counts even if unused - the check errs towards
       silence, since a present dependency makes the claim possibly true. #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $RepoRoot,
        [Parameter(Mandatory)][string] $Coordinate
    )

    $group, $artifact = $Coordinate -split ':', 2
    $catalog = Join-Path $RepoRoot 'gradle/libs.versions.toml'
    if (Test-Path -LiteralPath $catalog) {
        $splitForm = 'group\s*=\s*"{0}"\s*,\s*name\s*=\s*"{1}"' -f [regex]::Escape($group), [regex]::Escape($artifact)
        $moduleForm = 'module\s*=\s*"{0}"' -f [regex]::Escape($Coordinate)
        foreach ($line in [System.IO.File]::ReadAllLines($catalog)) {
            if ($line.TrimStart().StartsWith('#')) { continue }
            if ($line -match $splitForm -or $line -match $moduleForm) { return $true }
        }
    }

    $buildFiles = @(Get-ChildItem -LiteralPath $RepoRoot -Filter 'build.gradle.kts' -Recurse -Depth 1 -File -ErrorAction SilentlyContinue)
    $literal = '"' + [regex]::Escape($Coordinate) + '[:"]'
    foreach ($file in $buildFiles) {
        foreach ($line in [System.IO.File]::ReadAllLines($file.FullName)) {
            $trimmed = $line.TrimStart()
            if ($trimmed.StartsWith('//') -or $trimmed.StartsWith('*')) { continue }
            if ($line -match $literal) { return $true }
        }
    }
    return $false
}

function Get-AbsentDependencyClaimDocPaths {
    <# Every document the manifest judges, absolute. The wrapper adds them to its fixed-input set. #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $RepoRoot,
        [string] $ManifestPath = (Join-Path $RepoRoot 'scripts/doc-drift/absent-dependency-claims.psd1')
    )

    if (-not (Test-Path -LiteralPath $ManifestPath)) { return @() }
    $manifest = Import-PowerShellDataFile -LiteralPath $ManifestPath
    $paths = foreach ($claim in $manifest.Claims) {
        foreach ($doc in $claim.docs) { Join-Path $RepoRoot $doc }
    }
    return @($paths | Sort-Object -Unique)
}

function Get-AbsentDependencyClaimFindings {
    <# One `CLAIM | doc:line | name | text` string per document line that makes a claim whose
       coordinate is absent. A missing document is skipped: the pin checker owns MISSING. #>
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $RepoRoot,
        [string] $ManifestPath = (Join-Path $RepoRoot 'scripts/doc-drift/absent-dependency-claims.psd1')
    )

    if (-not (Test-Path -LiteralPath $ManifestPath)) { return @() }
    $manifest = Import-PowerShellDataFile -LiteralPath $ManifestPath
    $findings = [System.Collections.Generic.List[string]]::new()

    foreach ($claim in $manifest.Claims) {
        if (Test-DependencyDeclared -RepoRoot $RepoRoot -Coordinate $claim.coordinate) { continue }
        $ignoreMarker = 'absent-dependency-ignore: ' + $claim.name

        foreach ($doc in $claim.docs) {
            $docPath = Join-Path $RepoRoot $doc
            if (-not (Test-Path -LiteralPath $docPath)) { continue }
            $lines = [System.IO.File]::ReadAllLines($docPath)
            for ($i = 0; $i -lt $lines.Count; $i++) {
                $line = $lines[$i]
                if ($line.Contains($ignoreMarker)) { continue }
                foreach ($pattern in $claim.patterns) {
                    if ($line -match $pattern) {
                        $text = $line.Trim()
                        if ($text.Length -gt 140) { $text = $text.Substring(0, 140) + '..' }
                        $findings.Add(('CLAIM | {0}:{1} | {2} ({3} {4}) | {5}' -f $doc, ($i + 1), $claim.name, $claim.coordinate, $claim.reason, $text))
                        break
                    }
                }
            }
        }
    }
    return @($findings)
}
