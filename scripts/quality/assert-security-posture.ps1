#requires -Version 7.0
<#
.SYNOPSIS
    Assert docs/SECURITY_POSTURE.md still describes what the app declares and links (S3380).

.DESCRIPTION
    The promise the product makes lives in three public forms - the privacy page, the in-app
    perm_rationale_* strings and the store-form sources - and all three render from one inventory,
    docs/SECURITY_POSTURE.md. This gate is the mechanism that makes a divergence between the
    inventory and the code a state somebody sees, instead of a store rejection months later
    (canon rules/SECURITY_AND_PRIVACY.md section 7, contract source ticket S3380).

    Four checks, all of them derivable and none of them a judgement:

      permission-coverage   every <uses-permission> / <uses-permission-sdk-23> declared in any
                            AndroidManifest.xml of app_v2 or wear has a row in the inventory, and
                            every row names a permission some manifest declares. A declaration
                            carrying tools:node="remove" is a removal, not a declaration, and is
                            ignored on both sides.
      rationale-keys        every perm_rationale_* key a row names resolves to a <string> in
                            app_v2/src/main/res/values/strings.xml. A row with no key is legal -
                            the inventory's own section 4 lists those gaps deliberately.
      telemetry-claim       no analytics, crash-reporting or advertising artifact appears in
                            gradle/libs.versions.toml or either module's build.gradle.kts while
                            the document claims none. The "no telemetry" claim is proven by what is
                            linked in, never by the page saying so (canon INVARIANTS 14).
      document-shape        the document exists and carries its reconciliation date, so an
                            inventory nobody has looked at in a year says so.

    Release scope by CLAUDE.md Rule 33: the subject is the whole tree's declarations against one
    document, no changed file can be blamed for a divergence, the finding names the permission or
    the key itself, and reconciling costs the same whenever it is done. It therefore runs from
    assert-release-scope-gates.ps1 (step 0.4 of /spec-prerelease), not from post-change.ps1.

.PARAMETER RepoRoot
    Repository root to judge. Defaults to this script's grandparent.

.PARAMETER Quiet
    Print the verdict line only. Used by assert-release-scope-gates.ps1.

.PARAMETER Gate
    Accepted and ignored. assert-release-scope-gates.ps1 passes -Gate to every gate it runs, and a
    [CmdletBinding()] script that does not declare it dies on a binding error before its first line.

Exit codes:
    0  every check passed.
    1  at least one check failed; each finding is printed with its subject.
    2  could not verify - the inventory document or a required input is missing or unreadable.
#>
[CmdletBinding()]
param(
    [string]$RepoRoot,
    [switch]$Quiet,
    [switch]$Gate
)

$ErrorActionPreference = 'Stop'

if (-not $RepoRoot) {
    $RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
}

$docPath = Join-Path $RepoRoot 'docs/SECURITY_POSTURE.md'
$stringsPath = Join-Path $RepoRoot 'app_v2/src/main/res/values/strings.xml'

if (-not (Test-Path -LiteralPath $docPath)) {
    Write-Host "assert-security-posture: CANNOT VERIFY - docs/SECURITY_POSTURE.md is missing."
    exit 2
}
if (-not (Test-Path -LiteralPath $stringsPath)) {
    Write-Host "assert-security-posture: CANNOT VERIFY - app_v2/src/main/res/values/strings.xml is missing."
    exit 2
}

$docText = Get-Content -LiteralPath $docPath -Raw

# --- declared permissions, from every manifest of both modules ----------------------------------
$manifestRoots = @('app_v2/src', 'wear/src') |
    ForEach-Object { Join-Path $RepoRoot $_ } |
    Where-Object { Test-Path -LiteralPath $_ }

if (-not $manifestRoots) {
    Write-Host "assert-security-posture: CANNOT VERIFY - neither app_v2/src nor wear/src is present."
    exit 2
}

$declared = [System.Collections.Generic.HashSet[string]]::new()
foreach ($manifest in Get-ChildItem -Path $manifestRoots -Recurse -Filter 'AndroidManifest.xml' -File) {
    try {
        [xml]$xml = Get-Content -LiteralPath $manifest.FullName -Raw
    } catch {
        Write-Host "assert-security-posture: CANNOT VERIFY - $($manifest.FullName) is not parsable XML."
        exit 2
    }
    foreach ($node in $xml.manifest.ChildNodes) {
        if ($node.LocalName -notin @('uses-permission', 'uses-permission-sdk-23')) { continue }
        if ($node.GetAttribute('tools:node') -eq 'remove') { continue }
        $name = $node.GetAttribute('android:name')
        if ($name) { [void]$declared.Add($name) }
    }
}

# --- inventory rows -----------------------------------------------------------------------------
# A row starts with a backticked permission name in its first cell; the rationale keys are the
# backticked perm_rationale_* tokens of the same line.
$rowPattern = '(?m)^\|\s*`([A-Za-z0-9_.]+\.permission\.[A-Za-z0-9_.]+|com\.oculus\.permission\.[A-Za-z0-9_]+)`\s*\|(.*)$'
$inventory = @{}
foreach ($match in [regex]::Matches($docText, $rowPattern)) {
    $perm = $match.Groups[1].Value
    $keys = [regex]::Matches($match.Groups[2].Value, '`(perm_rationale_[A-Za-z0-9_]+)`') |
        ForEach-Object { $_.Groups[1].Value }
    $inventory[$perm] = @($keys)
}

if ($inventory.Count -eq 0) {
    Write-Host "assert-security-posture: CANNOT VERIFY - the permission table of docs/SECURITY_POSTURE.md parsed to zero rows."
    exit 2
}

$findings = New-Object System.Collections.Generic.List[string]

foreach ($perm in ($declared | Sort-Object)) {
    if (-not $inventory.ContainsKey($perm)) {
        $findings.Add("permission-coverage: $perm is declared in a manifest and has no row in docs/SECURITY_POSTURE.md.")
    }
}
foreach ($perm in ($inventory.Keys | Sort-Object)) {
    if (-not $declared.Contains($perm)) {
        $findings.Add("permission-coverage: $perm has a row in docs/SECURITY_POSTURE.md and no manifest declares it.")
    }
}

# --- rationale keys resolve ---------------------------------------------------------------------
$stringsText = Get-Content -LiteralPath $stringsPath -Raw
foreach ($perm in ($inventory.Keys | Sort-Object)) {
    foreach ($key in $inventory[$perm]) {
        if ($stringsText -notmatch [regex]::Escape("name=`"$key`"")) {
            $findings.Add("rationale-keys: $perm names the string $key, which does not exist in app_v2/src/main/res/values/strings.xml.")
        }
    }
}

# --- the no-telemetry claim against the dependency set ------------------------------------------
$claimsNoTelemetry = $docText -match 'No analytics, crash-reporting or advertising SDK is linked'
if ($claimsNoTelemetry) {
    $buildInputs = @(
        'gradle/libs.versions.toml',
        'app_v2/build.gradle.kts',
        'wear/build.gradle.kts'
    ) | ForEach-Object { Join-Path $RepoRoot $_ } | Where-Object { Test-Path -LiteralPath $_ }

    # Each token names a product, not a word that happens to appear in prose: a match is an artifact
    # coordinate or a version alias, which is why the search is over build inputs only.
    $telemetryTokens = @(
        'firebase', 'crashlytics', 'appcenter', 'app-center', 'sentry',
        'adjust', 'appsflyer', 'amplitude', 'mixpanel', 'flurry',
        'play-services-ads', 'admob', 'google-analytics', 'play-services-analytics'
    )
    foreach ($buildInput in $buildInputs) {
        $text = Get-Content -LiteralPath $buildInput -Raw
        foreach ($token in $telemetryTokens) {
            foreach ($line in ($text -split "`r?`n")) {
                if ($line -match [regex]::Escape($token)) {
                    # A removal of a transitive component is the opposite of consuming it.
                    if ($line -match 'tools:node\s*=\s*"remove"' -or $line.TrimStart().StartsWith('//') -or $line.TrimStart().StartsWith('#')) { continue }
                    $rel = [System.IO.Path]::GetRelativePath($RepoRoot, $buildInput)
                    $findings.Add("telemetry-claim: $rel carries '$token' while docs/SECURITY_POSTURE.md claims no analytics, crash-reporting or advertising SDK is linked.")
                }
            }
        }
    }
}

# --- document shape -----------------------------------------------------------------------------
if ($docText -notmatch '(?m)^\*\*Last reconciled:\*\*\s*\d{4}-\d{2}-\d{2}\s*$') {
    $findings.Add("document-shape: docs/SECURITY_POSTURE.md carries no '**Last reconciled:** YYYY-MM-DD' line, so nobody can tell how old the inventory is.")
}

if ($findings.Count -gt 0) {
    foreach ($finding in $findings) { Write-Host "  $finding" }
    Write-Host "assert-security-posture: FAIL ($($findings.Count) finding(s)); $($declared.Count) declared permission(s), $($inventory.Count) inventory row(s)."
    exit 1
}

if (-not $Quiet) {
    Write-Host "  permission-coverage: $($declared.Count) declared permission(s), all present in the inventory."
    Write-Host "  rationale-keys: every named perm_rationale_* string resolves."
    Write-Host "  telemetry-claim: no analytics, crash-reporting or advertising artifact in the build inputs."
}
Write-Host "assert-security-posture: PASS ($($inventory.Count) inventory row(s))."
exit 0
