#requires -Version 7.0
<#
.SYNOPSIS
    S3178: judge the two Wear merged manifests against wear/config/store-boundary-policy.json.

.DESCRIPTION
    The `standard` Wear flavor is what Google Play distributes and is an ALLOWLIST variant: nothing
    sensitive may reach it. `noLegal` is the sideload artifact and must keep every capability the
    policy catalogues. Source intent proves neither half - a library manifest, a transitive
    dependency or a merger rule can reintroduce a permission no source file in this repository
    declares - so this gate reads the MERGED manifests both variants actually build.

    Two directions, both fatal:
      1. A permission or component catalogued as noLegal-only appears in the standard merged
         manifest. That is the defect the ticket exists to prevent - it puts a Play review refusal
         on the store listing of a shipping product.
      2. A permission or component catalogued as noLegal-only is absent from the noLegal merged
         manifest. The store variant is not allowed to be simplified by deleting the capability
         from the sideload one (strategic §2 non-goal).
    A permission present in standard that the policy names nowhere is also fatal: the allowlist is
    closed, so an unreviewed addition must be a policy edit rather than a manifest edit.

    Manifest inputs are the merge task outputs under wear/build/intermediates/merged_manifests/.
    A missing input is exit 2 (cannot verify), never a pass: a gate that certifies an artifact
    nobody built is worse than no gate.

.PARAMETER PolicyPath
    Override the policy file. Defaults to wear/config/store-boundary-policy.json.

.PARAMETER StandardManifest
    Override the standard merged manifest path. Defaults to the debug merge output.

.PARAMETER NoLegalManifest
    Override the noLegal merged manifest path. Defaults to the debug merge output.

.PARAMETER RequireArtifacts
    Treat an absent merged manifest as a failure to verify (exit 2) instead of a printed advisory.
    The Wear release path passes it, because there the artifact exists by definition. The general
    release-scope batch does not: it runs on every phone release too, and a watch module nobody
    built in this checkout is not a defect of the phone release - collapsing that into FAIL would
    paint the whole batch red for a reason unrelated to what it is judging.

.PARAMETER Quiet
    Print only the verdict line, not the per-finding detail.

.PARAMETER Help
    Show help documentation and usage.

.EXAMPLE
    pwsh -NoProfile -File scripts/quality/assert-wear-store-boundary.ps1

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0  both artifacts agree with the policy.
      1  at least one boundary violation. The Wear release does not ship until it is fixed.
      2  cannot verify - the policy file is missing or unreadable, or a merged manifest is absent
         under -RequireArtifacts.
#>
[CmdletBinding()]
param(
    [string]$PolicyPath,
    [string]$StandardManifest,
    [string]$NoLegalManifest,
    [switch]$RequireArtifacts,
    [switch]$Quiet,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if ($Help) {
    Get-Help $PSCommandPath
    exit 0
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path

function Resolve-InputPath {
    param([string]$Explicit, [string]$Default)
    if ($Explicit) { return $Explicit }
    return (Join-Path $repoRoot $Default)
}

$policyFile = Resolve-InputPath $PolicyPath 'wear/config/store-boundary-policy.json'
$standardFile = Resolve-InputPath $StandardManifest `
    'wear/build/intermediates/merged_manifests/standardDebug/processStandardDebugManifest/AndroidManifest.xml'
$noLegalFile = Resolve-InputPath $NoLegalManifest `
    'wear/build/intermediates/merged_manifests/noLegalDebug/processNoLegalDebugManifest/AndroidManifest.xml'

if (-not (Test-Path -LiteralPath $policyFile)) {
    Write-Host "assert-wear-store-boundary: CANNOT VERIFY - policy not found at $policyFile" -ForegroundColor Yellow
    exit 2
}

try {
    $policy = Get-Content -LiteralPath $policyFile -Raw -Encoding UTF8 | ConvertFrom-Json
} catch {
    Write-Host "assert-wear-store-boundary: CANNOT VERIFY - policy is not valid JSON: $($_.Exception.Message)" -ForegroundColor Yellow
    exit 2
}

foreach ($pair in @(@{ n = 'standard'; p = $standardFile }, @{ n = 'noLegal'; p = $noLegalFile })) {
    if (Test-Path -LiteralPath $pair.p) { continue }
    if ($RequireArtifacts) {
        Write-Host "assert-wear-store-boundary: CANNOT VERIFY - $($pair.n) merged manifest not found at $($pair.p)" -ForegroundColor Yellow
        Write-Host "  Build it first: .\a.ps1 fw, or gradlew :wear:processStandardDebugManifest :wear:processNoLegalDebugManifest" -ForegroundColor Yellow
        exit 2
    }
    Write-Host "assert-wear-store-boundary: ADVISORY - $($pair.n) merged manifest is absent; nothing judged. Build the wear module, or re-run with -RequireArtifacts to make this fatal." -ForegroundColor Yellow
    exit 0
}

# The merged manifest is namespaced XML; reading the attribute through the XmlDocument avoids
# matching a permission name that only appears inside a comment, which a regex over the raw text
# cannot tell apart from a live declaration.
function Read-ManifestFacts {
    param([string]$Path)
    [xml]$doc = Get-Content -LiteralPath $Path -Raw -Encoding UTF8
    $ns = 'http://schemas.android.com/apk/res/android'
    # DocumentElement, not $doc.manifest: a manifest whose first node is a comment - which every
    # flavor manifest in this module has - makes the adapted property return the comment instead of
    # the root, and the walk below then finds nothing at all rather than failing.
    $root = $doc.DocumentElement
    $permissions = @()
    foreach ($node in $root.ChildNodes) {
        # LocalName, not Name: PowerShell's XML adapter answers Name with the element's own `name`
        # attribute when it has one, so every uses-permission node reports itself as the permission
        # string and a test against the tag name matches nothing at all.
        if ($node.LocalName -eq 'uses-permission' -or $node.LocalName -eq 'uses-permission-sdk-23') {
            $permissions += [string]$node.GetAttribute('name', $ns)
        }
    }
    $components = @()
    $app = $root.ChildNodes | Where-Object { $_.LocalName -eq 'application' } | Select-Object -First 1
    if ($null -ne $app) {
        foreach ($node in $app.ChildNodes) {
            if ($node.NodeType -ne [System.Xml.XmlNodeType]::Element) { continue }
            if ($node.LocalName -in @('activity', 'service', 'receiver', 'provider')) {
                $components += [string]$node.GetAttribute('name', $ns)
            }
        }
    }
    return [pscustomobject]@{
        Permissions = @($permissions | Where-Object { $_ })
        Components  = @($components | Where-Object { $_ })
    }
}

try {
    $standard = Read-ManifestFacts $standardFile
    $noLegal = Read-ManifestFacts $noLegalFile
} catch {
    Write-Host "assert-wear-store-boundary: CANNOT VERIFY - merged manifest unreadable: $($_.Exception.Message)" -ForegroundColor Yellow
    exit 2
}

# A component is declared as `.foo.Bar` in source and merges to the fully qualified name, so both
# ends are compared on the suffix rather than on the literal string.
function Test-ComponentPresent {
    param([string[]]$Declared, [string]$Wanted)
    foreach ($d in $Declared) {
        if ($d -eq $Wanted) { return $true }
        if ($Wanted.StartsWith('.') -and $d.EndsWith($Wanted)) { return $true }
        if (-not $Wanted.StartsWith('.') -and $d.EndsWith(".$Wanted")) { return $true }
    }
    return $false
}

$findings = @()
$allowedPermissions = @($policy.standardAllowlist.permissions) +
    @($policy.standardAllowlist.libraryContributed.permissions)
$allowedComponents = @($policy.standardAllowlist.components) +
    @($policy.standardAllowlist.libraryContributed.components)

foreach ($entry in $policy.noLegalOnly) {
    $category = [string]$entry.category

    foreach ($permission in @($entry.permissions)) {
        if ($standard.Permissions -contains $permission) {
            $findings += "standard declares noLegal-only permission '$permission' (category: $category)"
        }
        if ($noLegal.Permissions -notcontains $permission) {
            $findings += "noLegal lost catalogued permission '$permission' (category: $category)"
        }
    }

    foreach ($component in @($entry.components)) {
        if (Test-ComponentPresent -Declared $standard.Components -Wanted $component) {
            $findings += "standard declares noLegal-only component '$component' (category: $category)"
        }
        if (-not (Test-ComponentPresent -Declared $noLegal.Components -Wanted $component)) {
            $findings += "noLegal lost catalogued component '$component' (category: $category)"
        }
    }
}

foreach ($permission in $standard.Permissions) {
    if ($allowedPermissions -contains $permission) { continue }
    $catalogued = $false
    foreach ($entry in $policy.noLegalOnly) {
        if (@($entry.permissions) -contains $permission) { $catalogued = $true; break }
    }
    if (-not $catalogued) {
        $findings += "standard declares '$permission', which the allowlist does not name - decide it in $((Resolve-Path -Relative $policyFile))"
    }
}

foreach ($component in $standard.Components) {
    $allowed = $false
    foreach ($a in $allowedComponents) {
        if (Test-ComponentPresent -Declared @($component) -Wanted $a) { $allowed = $true; break }
    }
    if ($allowed) { continue }
    $catalogued = $false
    foreach ($entry in $policy.noLegalOnly) {
        foreach ($c in @($entry.components)) {
            if (Test-ComponentPresent -Declared @($component) -Wanted $c) { $catalogued = $true; break }
        }
        if ($catalogued) { break }
    }
    if (-not $catalogued) {
        $findings += "standard declares component '$component', which the allowlist does not name - decide it in $((Resolve-Path -Relative $policyFile))"
    }
}

if ($findings.Count -gt 0) {
    Write-Host "assert-wear-store-boundary: FAIL - $($findings.Count) boundary violation(s)." -ForegroundColor Red
    if (-not $Quiet) {
        foreach ($f in $findings) { Write-Host "  - $f" -ForegroundColor Red }
        Write-Host "  standard: $standardFile" -ForegroundColor DarkGray
        Write-Host "  noLegal:  $noLegalFile" -ForegroundColor DarkGray
    }
    exit 1
}

Write-Host ("assert-wear-store-boundary: PASS - standard holds {0} permission(s) and {1} component(s), all allowlisted; noLegal keeps every catalogued capability." -f
    $standard.Permissions.Count, $standard.Components.Count) -ForegroundColor Green
exit 0
