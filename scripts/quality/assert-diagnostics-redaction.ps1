#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: a diagnostics or log-report payload field carrying a path or a credential must be masked.

.DESCRIPTION
    Part of S3371, security profile. A diagnostic export leaves the device: the watch ships its log
    to the phone as WearLogReportPayload, and the phone's system-info report is the artifact the
    /newlog intake reads. Both are built by hand, field by field, and nothing checked whether a
    field carried a user's folder path, account name or credential in the clear.

    Scope is declared, not guessed: the two diagnostic trees (core/systeminfo, core/logging in both
    modules) plus the report payload and log-tree types named in $declaredSurfaces below. A new
    export surface is a deliberate act, so it is added here in the same change - the gate refuses
    nothing it was not pointed at, and an undeclared surface is invisible rather than wrongly green,
    which is why the surface list is printed on every run.

    A finding is one property whose name carries a path, a resource identity or a credential (path,
    uri, url, file, folder, directory, resourceName, account, email, user, password, token,
    credential, host, server, serial, ssid) and whose name does not mark it as already masked
    (masked..., ...Masked, redacted..., ...Hash, ...Digest). A field that must stay readable - a
    device model, an app version - is not path-shaped and never matches.

    Baseline lives in scripts/quality/diagnostics-redaction-baseline.txt (single int) and may only
    shrink.

    Modes:
      (default)         Report current count vs baseline.
      -Gate             Exit 1 if current > baseline; with -ChangedFiles, if the changed files
                        introduced an occurrence relative to their HEAD copies.
      -UpdateBaseline   Ratchet DOWN only (also seeds the file when missing).
      -List             Print every finding as path:line <property>.

.PARAMETER RepoRoot
    The tree to scan.

.PARAMETER BaselinePath
    The baseline to read or write. Both exist so the contract suite can run the gate against a
    fixture tree and a fixture baseline: a refusal path that is never executed is the same
    unobserved green it was written to prevent.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - pass: at or below baseline, a report/list run, or a completed baseline write.
      1 - fail: the count rose above the baseline, or -UpdateBaseline was asked to RAISE it.
      4 - Code.Scripts is held by another session, so no baseline was written. The queue place is
          held - wait for the turn in the background and rerun.
#>
[CmdletBinding(DefaultParameterSetName = 'Report')]
param(
    [Parameter(ParameterSetName = 'Gate')][switch]$Gate,
    [Parameter(ParameterSetName = 'Update')][switch]$UpdateBaseline,
    [Parameter(ParameterSetName = 'Report')][switch]$List,
    [Parameter(ParameterSetName = 'Gate')][AllowEmptyCollection()][AllowNull()][string[]]$ChangedFiles,
    [string]$RepoRoot,
    [string]$BaselinePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = if ($RepoRoot) { $RepoRoot } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
. (Join-Path $PSScriptRoot '../utils/code-lock-scope.ps1')
. (Join-Path $PSScriptRoot 'lib/changed-files-delta.ps1')

$baselineFile = if ($BaselinePath) { $BaselinePath } else { Join-Path $PSScriptRoot 'diagnostics-redaction-baseline.txt' }

# The export surfaces this gate judges, by file name. Both modules, both directions of the watch
# log shipment, plus the phone report the diagnostics intake reads.
$declaredSurfaces = @(
    'WearLogReportPayload.kt',
    'WearLogReportReceiver.kt',
    'WearLogTree.kt',
    'SystemInfoReport.kt',
    'ExtendedDiagnostics.kt'
)
# The diagnostic trees. A file here is in scope whatever it is called - the whole tree exists to
# produce exports.
$rxDiagnosticTree = [regex]'(?i)/core/(?:systeminfo|logging)/'

# A property declaration, at the start of a line OR straight after a constructor's ( or , -
# a single-line data class is valid Kotlin, and anchoring on the line start alone let one
# through unjudged (caught by this gate's own contract suite before it shipped).
$rxProperty = [regex]'(?m)(?:^[ \t]*|[(,]\s*)(?:@\w[\w:().,\s"''=\[\]]*?)?\b(?:val|var)\s+([A-Za-z_]\w*)\s*:\s*String\??'
$rxSensitiveName = [regex]'(?i)(?:path|uri|url|filename|filepath|file$|folder|directory|resourcename|account|email|user(?:name)?|password|token|credential|host|server|serial|ssid|imei)'
# The name says the value was already put through the seam, or is a digest rather than the value.
$rxMaskedName = [regex]'(?i)(?:^masked|masked$|^redacted|redacted$|hash$|digest$|^sanitized|sanitized$)'

function Get-DiagnosticsRedactionFindings {
    <# Findings in one file's text. $Relative decides scope, so a file that is not an export surface
       counts zero without the caller having to filter first. #>
    param([string]$Text, [string]$Relative)
    $findings = [System.Collections.Generic.List[object]]::new()
    if ([string]::IsNullOrEmpty($Text)) { return $findings }

    $leaf = ($Relative -replace '\\', '/').Split('/')[-1]
    $inScope = ($declaredSurfaces -contains $leaf) -or $rxDiagnosticTree.IsMatch("/$Relative")
    if (-not $inScope) { return $findings }

    foreach ($m in $rxProperty.Matches($Text)) {
        $name = $m.Groups[1].Value
        if (-not $rxSensitiveName.IsMatch($name)) { continue }
        if ($rxMaskedName.IsMatch($name)) { continue }
        $lineNo = ($Text.Substring(0, $m.Index) -split "`n").Count
        $findings.Add([pscustomobject]@{ Line = $lineNo; Property = $name })
    }
    return $findings
}

$countInText = {
    param([string]$text, [string]$rel)
    # @() is load-bearing: PowerShell UNROLLS a returned List, so a file with no finding
    # returns $null and .Count on it throws under Set-StrictMode. That crash reached the
    # closure because the contract suites only ever exercised the full-scan path.
    @(Get-DiagnosticsRedactionFindings -Text $text -Relative ($rel ?? '')).Count
}

if ($Gate -and $ChangedFiles -and (@(Expand-ChangedFiles -ChangedFiles $ChangedFiles).Count -gt 0)) {
    $delta = Measure-ChangedFileGrowth -ChangedFiles $ChangedFiles -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countInText
    Write-Host ("diagnostics-redaction (changed set): new occurrence(s) {0}" -f $delta.Growth)
    if ($delta.Growth -gt 0) {
        foreach ($f in $delta.PerFile | Where-Object { $_.New -gt 0 }) {
            Write-Host ("  {0}: +{1}" -f $f.Path, $f.New)
        }
        Write-Host 'FAIL: a diagnostics or log-report field in your changed files carries a path, an account or a credential unmasked. Put the value through SecretMasker before it reaches the payload and name the field for it (maskedPath), or drop the field from the export.'
        exit 1
    }
    exit 0
}

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()
$scannedFiles = 0

foreach ($module in @('app_v2', 'wear')) {
    $root = Join-Path $repoRoot "$module/src/main/java"
    if (-not (Test-Path -LiteralPath $root)) { continue }
    foreach ($file in (Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue)) {
        $rel = ($file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/')
        $leaf = $rel.Split('/')[-1]
        if (-not (($declaredSurfaces -contains $leaf) -or $rxDiagnosticTree.IsMatch("/$rel"))) { continue }
        $scannedFiles++
        $text = Get-Content -LiteralPath $file.FullName -Raw
        foreach ($finding in (Get-DiagnosticsRedactionFindings -Text $text -Relative $rel)) {
            $current++
            $hits.Add(("{0}:{1} {2}" -f $rel, $finding.Line, $finding.Property))
        }
    }
}

if ($List) {
    foreach ($h in $hits) { Write-Host $h }
    Write-Host ''
}

if ($PSCmdlet.ParameterSetName -eq 'Update') {
    $scope = $null
    try {
        $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-diagnostics-redaction.ps1 -UpdateBaseline'
        if (-not (Test-Path -LiteralPath $baselineFile)) {
            Set-Content -LiteralPath $baselineFile -Value "$current"
            Write-Host "diagnostics-redaction baseline SEEDED: $current"
            exit 0
        }
        $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
        if ($current -lt $baseline) {
            Set-Content -LiteralPath $baselineFile -Value "$current"
            Write-Host "diagnostics-redaction baseline ratcheted DOWN: $baseline -> $current"
        }
        elseif ($current -eq $baseline) {
            Write-Host "diagnostics-redaction baseline unchanged ($baseline)"
        }
        else {
            Write-Error "Refusing to RAISE baseline ($baseline -> $current). Mask the value before it reaches the payload."
            exit 1
        }
    }
    finally { Exit-CodeLockScope -Scope $scope }
    exit 0
}

# The scanned count is printed on every run: an export surface renamed out of the declared list
# would otherwise show the same clean line as a surface that was actually judged.
if (-not (Test-Path -LiteralPath $baselineFile)) {
    Write-Host "diagnostics-redaction: NO BASELINE yet | surfaces scanned $scannedFiles | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("unmasked path/credential fields in diagnostic exports: baseline {0} | actual {1} | delta {2:+#;-#;0} | surfaces scanned {3}" -f $baseline, $current, $delta, $scannedFiles)
if ($Gate -and $current -gt $baseline) {
    Write-Host 'FAIL: a diagnostics or log-report field carries a path, an account or a credential unmasked. Put the value through SecretMasker before it reaches the payload and name the field for it (maskedPath), or drop the field from the export.'
    exit 1
}
if ($current -lt $baseline) {
    Write-Host 'Note: count is below baseline - run -UpdateBaseline to ratchet the cap down.'
}
exit 0
