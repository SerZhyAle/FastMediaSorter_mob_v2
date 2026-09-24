#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: a persisted credential field must be declared as ciphertext, not as a plain String.

.DESCRIPTION
    Part of S3371, security profile. The encryption boundary exists - CryptoHelper wraps the Android
    Keystore and NetworkCredentialsEntity already stores its password through it, under a column
    named encryptedPassword - but nothing forced the NEXT credential-shaped column to go the same
    way. The convention lived in one entity's KDoc, which is not a control.

    Scope is the persisted models: any Kotlin file carrying a Room @Entity, plus the local database,
    preferences and DTO trees of both modules. A credential held in a view state or passed as a
    function parameter is out of scope by construction - it is not written to storage.

    A finding is one property declaration whose name is credential-shaped (password, secret, token,
    credential, passphrase, api key, private key, access key) and whose type is a plain String,
    unless one of two things is true:
      - the NAME marks the stored value as ciphertext (encrypted..., ...Encrypted, cipher, sealed,
        hashed), which is the convention NetworkCredentialsEntity already follows; or
      - the property is derived rather than stored - @Ignore / @get:Ignore, or a get() accessor -
        so no column exists to leak.

    The credit is deliberately per PROPERTY, not per file. Crediting the file because it mentions
    CryptoHelper somewhere would pass every new plaintext column added to the one entity that most
    needs the rule, which is the exact case this gate exists for.

    Baseline lives in scripts/quality/credential-encryption-baseline.txt (single int), may only
    shrink, and holds the residue: a field that IS encrypted but not named for it.

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

$baselineFile = if ($BaselinePath) { $BaselinePath } else { Join-Path $PSScriptRoot 'credential-encryption-baseline.txt' }

# Persistence trees. A file outside them is still scanned when it carries a Room @Entity, because an
# entity is a table wherever it is declared.
$rxPersistenceTree = [regex]'(?i)/(?:data/(?:local|db|database|preferences|prefs|store)|data/[^/]+/(?:dto|entity))/'
$rxEntityAnnotation = [regex]'@Entity\b'
$rxPersistedFileName = [regex]'(?i)(Entity|Dto|Prefs|Preferences|Store)\.kt$'

# A property declaration, at the start of a line OR straight after a constructor's ( or , -
# a single-line data class is valid Kotlin, and anchoring on the line start alone let one
# through unjudged (caught by this gate's own contract suite before it shipped).
$rxProperty = [regex]'(?m)(?:^[ \t]*|[(,]\s*)(?:@\w[\w:().,\s"''=\[\]]*?)?\b(?:val|var)\s+([A-Za-z_]\w*)\s*:\s*String\??'
$rxCredentialName = [regex]'(?i)(?:password|passwd|pwd|secret|token|credential|passphrase|api[_]?key|private[_]?key|access[_]?key|auth[_]?key)'
# The name says the stored value is already ciphertext or a digest.
$rxCiphertextName = [regex]'(?i)(?:^encrypted|encrypted$|cipher|sealed|hashed|digest|^enc[A-Z])'
# An identifier, not a credential: a row id or a foreign key leaks nothing at rest.
$rxIdentifierName = [regex]'(?i)id$'

function Get-CredentialEncryptionFindings {
    <# Findings in one file's text. $Relative decides scope, so a file that is not a persisted model
       counts zero without the caller having to filter first. #>
    param([string]$Text, [string]$Relative)
    $findings = [System.Collections.Generic.List[object]]::new()
    if ([string]::IsNullOrEmpty($Text)) { return $findings }

    $inScope = $rxPersistenceTree.IsMatch("/$Relative") -or
        $rxPersistedFileName.IsMatch($Relative) -or
        $rxEntityAnnotation.IsMatch($Text)
    if (-not $inScope) { return $findings }

    foreach ($m in $rxProperty.Matches($Text)) {
        $name = $m.Groups[1].Value
        if (-not $rxCredentialName.IsMatch($name)) { continue }
        if ($rxCiphertextName.IsMatch($name)) { continue }
        if ($rxIdentifierName.IsMatch($name)) { continue }

        # Derived rather than stored: an ignored property, or one backed by a get() accessor. Both
        # mean no column exists, so there is nothing at rest to encrypt. Judged on a text window
        # around the match rather than on line indexes - the property regex may begin on the
        # annotation line or on the declaration line, and a line-counted window was off by one in
        # both directions.
        $windowStart = [Math]::Max(0, $m.Index - 160)
        $before = $Text.Substring($windowStart, $m.Index - $windowStart)
        if ($before -match '@(?:get:)?Ignore\b[^\n]*\n(?:\s*@[^\n]*\n)*\s*$') { continue }
        $afterStart = $m.Index + $m.Length
        $afterLength = [Math]::Min(160, $Text.Length - $afterStart)
        $after = if ($afterLength -gt 0) { $Text.Substring($afterStart, $afterLength) } else { '' }
        if ($after -match '^\s*(?:=\s*\n)?\s*get\s*\(\s*\)') { continue }

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
    @(Get-CredentialEncryptionFindings -Text $text -Relative ($rel ?? '')).Count
}

if ($Gate -and $ChangedFiles -and (@(Expand-ChangedFiles -ChangedFiles $ChangedFiles).Count -gt 0)) {
    $delta = Measure-ChangedFileGrowth -ChangedFiles $ChangedFiles -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countInText
    Write-Host ("credential-encryption (changed set): new occurrence(s) {0}" -f $delta.Growth)
    if ($delta.Growth -gt 0) {
        foreach ($f in $delta.PerFile | Where-Object { $_.New -gt 0 }) {
            Write-Host ("  {0}: +{1}" -f $f.Path, $f.New)
        }
        Write-Host 'FAIL: a persisted credential field in your changed files is a plain String. Store the value through CryptoHelper and name the column for what it holds (encryptedPassword), or mark the property derived if nothing is written at rest.'
        Write-Host 'assert-credential-encryption: FAIL'
        exit 1
    }
    Write-Host 'assert-credential-encryption: PASS'
    exit 0
}

$current = 0
$hits = [System.Collections.Generic.List[string]]::new()

foreach ($module in @('app_v2', 'wear')) {
    $root = Join-Path $repoRoot "$module/src/main/java"
    if (-not (Test-Path -LiteralPath $root)) { continue }
    foreach ($file in (Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue)) {
        $rel = ($file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/')
        $text = Get-Content -LiteralPath $file.FullName -Raw
        foreach ($finding in (Get-CredentialEncryptionFindings -Text $text -Relative $rel)) {
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
        $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-credential-encryption.ps1 -UpdateBaseline'
        if (-not (Test-Path -LiteralPath $baselineFile)) {
            Set-Content -LiteralPath $baselineFile -Value "$current"
            Write-Host "credential-encryption baseline SEEDED: $current"
            exit 0
        }
        $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
        if ($current -lt $baseline) {
            Set-Content -LiteralPath $baselineFile -Value "$current"
            Write-Host "credential-encryption baseline ratcheted DOWN: $baseline -> $current"
        }
        elseif ($current -eq $baseline) {
            Write-Host "credential-encryption baseline unchanged ($baseline)"
        }
        else {
            Write-Error "Refusing to RAISE baseline ($baseline -> $current). Route the field through CryptoHelper instead."
            exit 1
        }
    }
    finally { Exit-CodeLockScope -Scope $scope }
    exit 0
}

if (-not (Test-Path -LiteralPath $baselineFile)) {
    Write-Host "credential-encryption: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    Write-Host 'assert-credential-encryption: PASS (no baseline to compare against)'
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("plaintext credential fields in persisted models: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host 'FAIL: a persisted credential field is a plain String. Store the value through CryptoHelper and name the column for what it holds (encryptedPassword), or mark the property derived if nothing is written at rest.'
    Write-Host 'assert-credential-encryption: FAIL'
    exit 1
}
if ($current -lt $baseline) {
    Write-Host 'Note: count is below baseline - run -UpdateBaseline to ratchet the cap down.'
}
# CHECK-VERDICT rule 5: the count line above is prose, so the run ends on a line a reader can match.
Write-Host 'assert-credential-encryption: PASS'
exit 0
