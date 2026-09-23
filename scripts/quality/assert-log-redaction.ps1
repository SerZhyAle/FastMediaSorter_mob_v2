#requires -Version 7.0
<#
.SYNOPSIS
    Ratchet gate: a credential interpolated into a Timber call in the network trees must be masked.

.DESCRIPTION
    Part of S3371, security profile. The masking seam exists - core/security/SecretMasker.kt, with
    mask, maskFull, maskPath and sanitize - and its KDoc even shows the intended call shape. Nothing
    enforced it: whether a connection log carried a password in the clear was a matter of whoever
    wrote the line remembering. The watch makes that worse, because its log tree SHIPS remotely.

    Scope is the trees where a credential is in scope at all: data/remote, data/network, data/cloud,
    data/link, data/transfer and the network monitors of both modules, plus the Wear logging tree.
    A Timber call elsewhere has no credential to leak, and widening the scan would trade findings
    for noise.

    A finding is one interpolation inside a Timber statement whose expression names a credential
    (user, password, token, secret, key, credential, auth) and does not pass through the seam. The
    statement is read with balanced parentheses, so a wrapped call is judged whole rather than by
    its first line.

    Baseline lives in scripts/quality/log-redaction-baseline.txt (single int) and may only shrink.

    Modes:
      (default)         Report current count vs baseline.
      -Gate             Exit 1 if current > baseline; with -ChangedFiles, if the changed files
                        introduced an occurrence relative to their HEAD copies.
      -UpdateBaseline   Ratchet DOWN only (also seeds the file when missing).
      -List             Print every finding as path:line <expression>.

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

$baselineFile = if ($BaselinePath) { $BaselinePath } else { Join-Path $PSScriptRoot 'log-redaction-baseline.txt' }

# The trees where a credential or a remote location is in scope. Anchored on the package path, not
# on a module, so the same rule reaches app_v2 and wear without being written twice.
$rxInScope = [regex]'(?i)/(?:data/(?:remote|network|cloud|link|transfer|networkmonitor|netmonitor)|core/logging|core/security)/'

# Timber entry points, including the tag(..) prefix form.
$rxTimberCall = [regex]'Timber(?:\.tag\([^()]*\))?\.(?:v|d|i|w|e|wtf|log)\s*\('
# An interpolation inside the statement: either ${ expression } or the bare $identifier form.
$rxInterpolation = [regex]'\$\{([^{}]*)\}|\$([A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)*)'
# The expression names a credential.
$rxCredentialExpression = [regex]'(?i)\b\w*(?:user(?:name)?|pass(?:word|wd)?|secret|token|api[_]?key|access[_]?key|private[_]?key|credential|auth[_]?token)\w*\b'
# An expression whose VALUE is not the credential: a predicate, a length, a comparison, a
# compile-time constant, a row id, or a safe terminal member. Logging "username.isNotBlank()" leaks
# nothing, and counting it would bury the lines that do - a gate whose baseline is mostly noise
# stops being read.
$rxNonValueExpression = [regex]'(?i)\.is(?:Not)?(?:Blank|Empty|NullOrBlank|NullOrEmpty)\(\)|\.length\b|\.size\b|\.count\(\)|[!=<>]=|^\s*!|\?:|\.any\(|\.none\('
$rxConstantExpression = [regex]'^[A-Z][A-Z0-9_]*$'
$rxSafeTerminalMember = [regex]'(?i)^(?:\w*id|type|size|count|length|port|scheme|mode|state|status|index|attempts?|retries|hash)$'
# The expression already goes through the seam.
$rxMasked = [regex]'(?i)SecretMasker\.|\bmask(?:Full|Path)?\s*\(|\bsanitize\s*\(|\bmasked\b|\bredact'

function Get-TimberStatements {
    <# Every Timber call in the text, as (Text, LineNumber) pairs, read with balanced parentheses so
       a wrapped call is judged whole. A statement whose parentheses never balance - a truncated or
       unparsable file - is skipped rather than guessed at. #>
    param([string]$Text)
    $result = [System.Collections.Generic.List[object]]::new()
    if ([string]::IsNullOrEmpty($Text)) { return $result }
    foreach ($m in $rxTimberCall.Matches($Text)) {
        $openAt = $Text.IndexOf('(', $m.Index + $m.Length - 1)
        if ($openAt -lt 0) { continue }
        $depth = 0
        $end = -1
        for ($i = $openAt; $i -lt $Text.Length; $i++) {
            $ch = $Text[$i]
            if ($ch -eq '(') { $depth++ }
            elseif ($ch -eq ')') {
                $depth--
                if ($depth -eq 0) { $end = $i; break }
            }
        }
        if ($end -lt 0) { continue }
        $statement = $Text.Substring($openAt, $end - $openAt + 1)
        $lineNo = ($Text.Substring(0, $m.Index) -split "`n").Count
        $result.Add([pscustomobject]@{ Text = $statement; Line = $lineNo })
    }
    return $result
}

function Get-LogRedactionFindings {
    <# Findings in one file's text. $Relative decides scope, so a file outside the network trees
       counts zero without the caller having to filter first. #>
    param([string]$Text, [string]$Relative)
    $findings = [System.Collections.Generic.List[object]]::new()
    if ([string]::IsNullOrEmpty($Text)) { return $findings }
    if (-not $rxInScope.IsMatch("/$Relative")) { return $findings }

    foreach ($statement in (Get-TimberStatements -Text $Text)) {
        foreach ($im in $rxInterpolation.Matches($statement.Text)) {
            $expression = if ($im.Groups[1].Success) { $im.Groups[1].Value } else { $im.Groups[2].Value }
            if (-not $expression) { continue }
            if (-not $rxCredentialExpression.IsMatch($expression)) { continue }
            if ($rxMasked.IsMatch($expression)) { continue }
            if ($rxNonValueExpression.IsMatch($expression)) { continue }
            $trimmed = $expression.Trim()
            if ($rxConstantExpression.IsMatch($trimmed)) { continue }
            $terminal = ($trimmed -split '\.')[-1]
            if ($rxSafeTerminalMember.IsMatch($terminal)) { continue }
            $findings.Add([pscustomobject]@{ Line = $statement.Line; Expression = $trimmed })
        }
    }
    return $findings
}

$countInText = {
    param([string]$text, [string]$rel)
    # @() is load-bearing: PowerShell UNROLLS a returned List, so a file with no finding
    # returns $null and .Count on it throws under Set-StrictMode. That crash reached the
    # closure because the contract suites only ever exercised the full-scan path.
    @(Get-LogRedactionFindings -Text $text -Relative ($rel ?? '')).Count
}

# ---------------------------------------------------------------------------
# Scoped run: judge what the changed files introduced against their HEAD copies.
# ---------------------------------------------------------------------------
if ($Gate -and $ChangedFiles -and (@(Expand-ChangedFiles -ChangedFiles $ChangedFiles).Count -gt 0)) {
    $delta = Measure-ChangedFileGrowth -ChangedFiles $ChangedFiles -RepoRoot $repoRoot -Extensions @('.kt') -CountInText $countInText
    Write-Host ("log-redaction (changed set): new occurrence(s) {0}" -f $delta.Growth)
    if ($delta.Growth -gt 0) {
        foreach ($f in $delta.PerFile | Where-Object { $_.New -gt 0 }) {
            Write-Host ("  {0}: +{1}" -f $f.Path, $f.New)
        }
        Write-Host 'FAIL: a Timber call in your changed files interpolates a credential without the masking seam. Wrap the value in SecretMasker.mask / maskFull / maskPath, or log the fact instead of the value.'
        exit 1
    }
    exit 0
}

# ---------------------------------------------------------------------------
# Full scan.
# ---------------------------------------------------------------------------
$current = 0
$hits = [System.Collections.Generic.List[string]]::new()

foreach ($module in @('app_v2', 'wear')) {
    $root = Join-Path $repoRoot "$module/src/main/java"
    if (-not (Test-Path -LiteralPath $root)) { continue }
    foreach ($file in (Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.kt' -ErrorAction SilentlyContinue)) {
        $rel = ($file.FullName.Substring($repoRoot.Length).TrimStart('\', '/') -replace '\\', '/')
        if (-not $rxInScope.IsMatch("/$rel")) { continue }
        $text = Get-Content -LiteralPath $file.FullName -Raw
        foreach ($finding in (Get-LogRedactionFindings -Text $text -Relative $rel)) {
            $current++
            $hits.Add(("{0}:{1} {2}" -f $rel, $finding.Line, $finding.Expression))
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
        $scope = Enter-CodeLockOrExit -Path $baselineFile -Reason 'assert-log-redaction.ps1 -UpdateBaseline'
        if (-not (Test-Path -LiteralPath $baselineFile)) {
            Set-Content -LiteralPath $baselineFile -Value "$current"
            Write-Host "log-redaction baseline SEEDED: $current"
            exit 0
        }
        $baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
        if ($current -lt $baseline) {
            Set-Content -LiteralPath $baselineFile -Value "$current"
            Write-Host "log-redaction baseline ratcheted DOWN: $baseline -> $current"
        }
        elseif ($current -eq $baseline) {
            Write-Host "log-redaction baseline unchanged ($baseline)"
        }
        else {
            Write-Error "Refusing to RAISE baseline ($baseline -> $current). Route the value through SecretMasker instead."
            exit 1
        }
    }
    finally { Exit-CodeLockScope -Scope $scope }
    exit 0
}

if (-not (Test-Path -LiteralPath $baselineFile)) {
    Write-Host "log-redaction: NO BASELINE yet | actual $current - run -UpdateBaseline to seed."
    exit 0
}
$baseline = [int]((Get-Content -LiteralPath $baselineFile -Raw).Trim())
$delta = $current - $baseline
Write-Host ("unmasked credential interpolations in network Timber calls: baseline {0} | actual {1} | delta {2:+#;-#;0}" -f $baseline, $current, $delta)
if ($Gate -and $current -gt $baseline) {
    Write-Host 'FAIL: a Timber call in the network trees interpolates a credential without the masking seam. Wrap the value in SecretMasker.mask / maskFull / maskPath, or log the fact instead of the value.'
    exit 1
}
if ($current -lt $baseline) {
    Write-Host 'Note: count is below baseline - run -UpdateBaseline to ratchet the cap down.'
}
exit 0
