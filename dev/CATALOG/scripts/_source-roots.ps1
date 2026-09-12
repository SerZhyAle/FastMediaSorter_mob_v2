# Shared source-root derivation for the class catalogue (S2837).
#
# Exit codes:
#   0  dot-sourced successfully; this library defines Get-CatalogSourceRoots.
#   2  invoked as a script instead of being dot-sourced.
#
# The set of indexed source sets is DERIVED from disk, never declared. It was declared twice before, and
# both copies drifted silently in opposite directions: scan.ps1's array was six product source sets behind
# the tree while still naming `translationDynamicFeature`, a directory that no longer exists, and
# render.ps1's `$candidateRoots` was twenty-seven sets behind while still naming `screenCapturePlay`. Both
# failures are invisible at the call site - a missing set makes query.ps1 answer "No records matched" for a
# class that is on disk, which reads as proof of absence, and a stale set simply never matches. A gate over
# a hand-kept list would have been a third copy of the same list; there is nothing deliberate to protect
# here, because the catalogue wants every product source set and that is exactly what enumeration returns.

if ($MyInvocation.InvocationName -ne '.') {
    Write-Error '_source-roots.ps1 is a dot-source library; load it from a catalog script.' -ErrorAction Continue
    exit 2
}

function Get-CatalogSourceRoots {
    <#
    .SYNOPSIS
        Every product source root of a module: <Root>/<Module>/src/<name>/java, tests and build types removed.
    .PARAMETER Relative
        Return forward-slashed '<Module>/src/<name>/java' strings instead of absolute filesystem paths.
    #>
    param(
        [Parameter(Mandatory = $true)][string]$Module,
        [Parameter(Mandatory = $true)][string]$Root,
        [switch]$Relative
    )

    $srcDir = Join-Path $Root "$Module\src"
    if (-not (Test-Path -LiteralPath $srcDir)) { return @() }

    $names = New-Object System.Collections.Generic.List[string]
    foreach ($dir in (Get-ChildItem -LiteralPath $srcDir -Directory -ErrorAction SilentlyContinue)) {
        if (-not (Test-Path -LiteralPath (Join-Path $dir.FullName 'java'))) { continue }
        $name = $dir.Name
        # Test sets: 'test', 'androidTest' and the testCloudSdk-shaped per-variant unit-test sets.
        if ($name -like 'test*' -or $name -like 'androidTest*') { continue }
        # Build types and their flavor combinations ('noLegalDebug'). Not product code in any variant.
        if ($name -in @('debug', 'release', 'benchmark')) { continue }
        if ($name -cmatch '(Debug|Release)$') { continue }
        $names.Add($name)
    }

    # 'main' first, then alphabetical. A seam pair such as cloudSdk / cloudNoSdk holds the same relative
    # path in two sets, and render.ps1 resolves a source link by that path alone - a fixed order is what
    # makes the link it picks stable across runs rather than dependent on directory enumeration.
    $ordered = @($names | Where-Object { $_ -eq 'main' }) + @($names | Where-Object { $_ -ne 'main' } | Sort-Object)

    $result = New-Object System.Collections.Generic.List[string]
    foreach ($name in $ordered) {
        if ($Relative) { $result.Add("$Module/src/$name/java") }
        else { $result.Add((Join-Path $Root "$Module\src\$name\java")) }
    }
    return , $result.ToArray()
}
