#requires -Version 7.0
<#
.SYNOPSIS
    Refuse a secret-shaped literal before it reaches version control (S3371, security profile).

.DESCRIPTION
    The security and privacy profile in docs/RULES_DIGEST.md states that no credential, token,
    API key or private-key block reaches a tracked file. Nothing scanned a diff or the tree for
    that before this gate: the mechanisms that PROTECT credentials at runtime exist (CryptoHelper,
    EncryptedCookieStore, SecretMasker), but nothing stopped one being pasted into source.

    Two selections, one rule set:
      -Changed (default)  the changed set handed by the closure - what THIS change could add.
      -Full               every file git's index knows - what the tree holds, for the scheduled
                          security-scan workflow.

    Four detectors, each named in its finding so the refusal says what was matched:
      private-key-block     a PEM private-key header.
      provider-token        a literal in a shape a known provider issues (Google, GitHub, AWS,
                            Slack, OpenAI-style).
      secret-assignment     a credential-shaped name assigned a literal that is neither a
                            placeholder nor an identifier.
      high-entropy-literal  a long base64/hex literal whose Shannon entropy is above the
                            threshold, which is how an unlabelled key looks.

    EXCEPTIONS ARE NAMED, NEVER COUNTED. There is no baseline here: a count would let a real
    secret hide behind a number, and a secret differs from a style finding in that ONE is already
    too many. scripts/quality/no-secrets-allowlist.txt carries an entry per admitted literal, and
    an entry with no reason comment is itself refused - an unexplained exemption is the shape this
    gate exists to prevent.

.PARAMETER Gate
    Fail-closed: exit 1 when any finding survives the allowlist.

.PARAMETER Full
    Scan every file in git's index instead of the changed set.

.PARAMETER ChangedFiles
    The changed set, as a real array or a comma-separated string (both collapse to the same list).

.PARAMETER List
    Print every finding as path:line [detector] matched-text.

.PARAMETER RepoRoot
    The tree to scan. Exists so the contract suite can point the gate at a fixture tree instead of
    this repository; a refusal path that is never executed is the same unobserved green it was
    written to prevent.

.PARAMETER AllowlistPath
    The allowlist to read. Same reason as RepoRoot: the suite must be able to execute the
    malformed-allowlist refusal without breaking this repository's own allowlist to do it.

.NOTES
    Exit codes (CLAUDE.md Rule 7):
      0 - pass: no finding survived the allowlist, or a report run.
      1 - fail: at least one finding survived, or the allowlist holds an entry with no reason.
      2 - cannot verify: the allowlist is missing, or -Full was asked for outside a git work tree.
#>
[CmdletBinding()]
param(
    [switch]$Gate,
    [switch]$Full,
    [AllowEmptyCollection()][AllowNull()][string[]]$ChangedFiles,
    [switch]$List,
    [string]$RepoRoot,
    [string]$AllowlistPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = if ($RepoRoot) { $RepoRoot } else { Split-Path -Parent (Split-Path -Parent $PSScriptRoot) }
$repoRootSlash = ($repoRoot -replace '\\', '/').TrimEnd('/')
. (Join-Path $PSScriptRoot 'lib/changed-files.ps1')

$allowlistFile = if ($AllowlistPath) { $AllowlistPath } else { Join-Path $PSScriptRoot 'no-secrets-allowlist.txt' }
if (-not (Test-Path -LiteralPath $allowlistFile)) {
    Write-Error "assert-no-secrets: allowlist not found: $allowlistFile" -ErrorAction Continue
    exit 2
}

# ---------------------------------------------------------------------------
# Allowlist
# ---------------------------------------------------------------------------
$allowFiles = [System.Collections.Generic.List[string]]::new()
$allowLiterals = [System.Collections.Generic.List[string]]::new()
$allowMatches = [System.Collections.Generic.List[string]]::new()
$allowlistErrors = [System.Collections.Generic.List[string]]::new()

$allowLineNo = 0
foreach ($rawLine in (Get-Content -LiteralPath $allowlistFile)) {
    $allowLineNo++
    $line = ([string]$rawLine).Trim()
    if (-not $line -or $line.StartsWith('#')) { continue }
    $hashAt = $line.IndexOf('#')
    if ($hashAt -lt 0 -or -not $line.Substring($hashAt + 1).Trim()) {
        $allowlistErrors.Add("line ${allowLineNo}: no reason comment - an exemption without a stated reason is refused")
        continue
    }
    $entry = $line.Substring(0, $hashAt).Trim()
    if (-not $entry) { continue }
    $colonAt = $entry.IndexOf(':')
    if ($colonAt -lt 0) {
        $allowlistErrors.Add("line ${allowLineNo}: expected file:<path>, literal:<value> or match:<regex>")
        continue
    }
    $kind = $entry.Substring(0, $colonAt).Trim().ToLowerInvariant()
    $value = $entry.Substring($colonAt + 1).Trim()
    switch ($kind) {
        'file' { $allowFiles.Add(($value -replace '\\', '/').Trim('/').ToLowerInvariant()) }
        'literal' { $allowLiterals.Add($value) }
        'match' { $allowMatches.Add($value) }
        default { $allowlistErrors.Add("line ${allowLineNo}: unknown entry kind $kind") }
    }
}

if ($allowlistErrors.Count -gt 0) {
    Write-Host 'assert-no-secrets: the allowlist itself is malformed -'
    foreach ($e in $allowlistErrors) { Write-Host "  $e" }
    exit 1
}

# ---------------------------------------------------------------------------
# Selection
# ---------------------------------------------------------------------------
# Extensions worth reading. A binary is skipped by extension rather than by sniffing: a scan that
# guesses at content reports a different set on every machine, and an image never holds a literal.
$textExtensions = @(
    '.kt', '.kts', '.java', '.ps1', '.psd1', '.psm1', '.py', '.sh', '.bat', '.cmd',
    '.xml', '.json', '.jsonl', '.yml', '.yaml', '.toml', '.properties', '.pro', '.cfg', '.conf',
    '.md', '.txt', '.gradle', '.env', '.ini', '.sql', '.html', '.js', '.ts', '.css', '.csv'
)
# Roots whose content is quoted evidence rather than source: a log excerpt or an archived spec that
# QUOTES a finding must not become a finding of its own, or reporting one secret would create
# another. The change journal is excluded for the same reason - it echoes every description.
$excludedRoots = @('temp/', 'dev/archive/', 'v1/', 'v2_6/', 'spec_v2/', 'plan/archive/')
$excludedNames = @('CHANGELOG.md')
# Vendored third-party code, at any depth. It is not ours to fix, and one lock file's integrity
# hashes alone accounted for most of the first full run's findings.
$excludedSegments = @('/node_modules/')
# Test sources run the shape detectors but not secret-assignment: a credential-shaped assignment in
# a test IS the fixture, so the label that makes the detector useful in product code makes it noise
# here, while a REAL key pasted into a test still trips private-key-block, provider-token or
# high-entropy-literal.
$rxTestSource = [regex]'(?i)/src/(test|androidTest|testDebug|testFixtures)[^/]*/'

function Test-ScannablePath {
    param([string]$Relative)
    $lower = ($Relative -replace '\\', '/').ToLowerInvariant()
    foreach ($root in $excludedRoots) { if ($lower.StartsWith($root)) { return $false } }
    foreach ($segment in $excludedSegments) { if ("/$lower".Contains($segment)) { return $false } }
    $leaf = Split-Path -Leaf $Relative
    foreach ($name in $excludedNames) { if ($leaf -eq $name) { return $false } }
    $ext = [System.IO.Path]::GetExtension($Relative).ToLowerInvariant()
    return ($textExtensions -contains $ext)
}

$selection = [System.Collections.Generic.List[string]]::new()

if ($Full) {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
        Write-Error 'assert-no-secrets: -Full needs git on PATH to read the index.' -ErrorAction Continue
        exit 2
    }
    $inside = & git -C $repoRoot rev-parse --is-inside-work-tree 2>&1
    if ($LASTEXITCODE -ne 0 -or ("$inside").Trim() -ne 'true') {
        Write-Error "assert-no-secrets: -Full needs a git work tree at $repoRoot." -ErrorAction Continue
        exit 2
    }
    $printed = & git -C $repoRoot -c core.quotepath=off ls-files 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error 'assert-no-secrets: git ls-files failed.' -ErrorAction Continue
        exit 2
    }
    foreach ($line in $printed) {
        $rel = ([string]$line).Trim()
        if ($rel -and (Test-ScannablePath -Relative $rel)) { $selection.Add($rel) }
    }
}
else {
    foreach ($path in (Expand-ChangedFiles -ChangedFiles $ChangedFiles)) {
        $rel = ($path -replace '\\', '/').Trim()
        if ($rel.StartsWith($repoRootSlash, [System.StringComparison]::OrdinalIgnoreCase)) {
            $rel = $rel.Substring($repoRootSlash.Length).TrimStart('/')
        }
        if ($rel -and (Test-ScannablePath -Relative $rel)) { $selection.Add($rel) }
    }
}

# ---------------------------------------------------------------------------
# Detectors
# ---------------------------------------------------------------------------
$rxPrivateKey = [regex]'-----BEGIN (?:[A-Z]+ )*PRIVATE KEY-----'
$rxProviderToken = [regex]'(AIza[0-9A-Za-z_\-]{35}|gh[pousr]_[A-Za-z0-9]{36,}|AKIA[0-9A-Z]{16}|xox[abprs]-[A-Za-z0-9\-]{10,}|sk-[A-Za-z0-9]{32,})'
$rxSecretAssignment = [regex]'(?i)\b(pass(?:word|wd)?|secret|token|api[_-]?key|apikey|access[_-]?key|private[_-]?key|client[_-]?secret|auth[_-]?token|credential)\b\s*(?:==|=|:|:=)\s*@?"([^"\r\n]{8,})"'
$rxQuoted = [regex]'"([A-Za-z0-9+/=_\-]{40,})"'
# A value matching one of these is a name, a placeholder or a format string - never a secret.
$rxPlaceholder = [regex]'(?i)^(?:your[_\- ]|xxx|changeme|placeholder|example|dummy|none|null|empty|test|sample|todo|<|\$\{|%[sd]|@string/|@\{)'
$rxIdentifierValue = [regex]'^[a-z][a-z0-9_]*$'
$rxProseValue = [regex]'^[^A-Za-z0-9]*$|^(?:\S+\s+){2,}'
# A value carrying an interpolation or a format placeholder is a template, not a literal: its real
# runtime content is decided elsewhere, so the characters on this line are not the secret.
$rxTemplateValue = [regex]'\$\(|\$\{|\{\d+\}|\{\}|%[sdf]'

function Get-ShannonEntropy {
    param([string]$Text)
    if (-not $Text) { return 0.0 }
    $counts = @{}
    foreach ($ch in $Text.ToCharArray()) {
        if ($counts.ContainsKey($ch)) { $counts[$ch] = $counts[$ch] + 1 } else { $counts[$ch] = 1 }
    }
    $len = [double]$Text.Length
    $entropy = 0.0
    foreach ($n in $counts.Values) {
        $p = $n / $len
        $entropy -= $p * [Math]::Log($p, 2)
    }
    return $entropy
}

function Test-Allowlisted {
    param([string]$Relative, [string]$Matched)
    $lower = ($Relative -replace '\\', '/').Trim('/').ToLowerInvariant()
    foreach ($f in $allowFiles) { if ($lower -eq $f) { return $true } }
    foreach ($l in $allowLiterals) { if ($Matched -eq $l) { return $true } }
    foreach ($m in $allowMatches) { if ($Matched -match $m) { return $true } }
    return $false
}

$findings = [System.Collections.Generic.List[pscustomobject]]::new()

foreach ($rel in $selection) {
    $absolutePath = Join-Path $repoRoot $rel
    if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) { continue }
    $lines = @(Get-Content -LiteralPath $absolutePath -ErrorAction SilentlyContinue)
    if ($lines.Count -eq 0) { continue }

    for ($i = 0; $i -lt $lines.Count; $i++) {
        $line = [string]$lines[$i]
        if (-not $line) { continue }
        $lineNo = $i + 1

        $hit = $rxPrivateKey.Match($line)
        if ($hit.Success) {
            if (-not (Test-Allowlisted -Relative $rel -Matched $hit.Value)) {
                $findings.Add([pscustomobject]@{ File = $rel; Line = $lineNo; Detector = 'private-key-block'; Text = $hit.Value })
            }
            continue
        }

        $hit = $rxProviderToken.Match($line)
        if ($hit.Success) {
            if (-not (Test-Allowlisted -Relative $rel -Matched $hit.Value)) {
                $findings.Add([pscustomobject]@{ File = $rel; Line = $lineNo; Detector = 'provider-token'; Text = $hit.Value })
            }
            continue
        }

        $hit = $rxSecretAssignment.Match($line)
        if ($hit.Success -and -not $rxTestSource.IsMatch("/$rel")) {
            $value = $hit.Groups[2].Value
            $isNoise = $rxPlaceholder.IsMatch($value) -or $rxIdentifierValue.IsMatch($value) -or
                $rxProseValue.IsMatch($value) -or $rxTemplateValue.IsMatch($value)
            if (-not $isNoise -and (Get-ShannonEntropy -Text $value) -ge 3.2 -and -not (Test-Allowlisted -Relative $rel -Matched $value)) {
                $findings.Add([pscustomobject]@{ File = $rel; Line = $lineNo; Detector = 'secret-assignment'; Text = $value })
                continue
            }
        }

        foreach ($qm in $rxQuoted.Matches($line)) {
            $value = $qm.Groups[1].Value
            if ($value -notmatch '[0-9]') { continue }
            # A literal whose characters are all distinct is an enumeration - an alphabet, a
            # permutation table - not a key. A key of this length repeats characters by chance; an
            # alphabet cannot, because repeating one would break what it is for.
            if (($value.ToCharArray() | Sort-Object -Unique).Count -eq $value.Length) { continue }
            if ((Get-ShannonEntropy -Text $value) -lt 4.6) { continue }
            if (Test-Allowlisted -Relative $rel -Matched $value) { continue }
            $findings.Add([pscustomobject]@{ File = $rel; Line = $lineNo; Detector = 'high-entropy-literal'; Text = $value })
        }
    }
}

$scope = if ($Full) { 'tracked tree' } else { 'changed set' }
Write-Host ("no-secrets: scanned {0} file(s) in the {1} | finding(s) {2}" -f $selection.Count, $scope, $findings.Count)

if ($List -or ($Gate -and $findings.Count -gt 0)) {
    foreach ($f in $findings) {
        $shown = if ($f.Text.Length -gt 24) { $f.Text.Substring(0, 8) + '..(' + $f.Text.Length + ' chars)' } else { $f.Text }
        Write-Host ("  {0}:{1} [{2}] {3}" -f $f.File, $f.Line, $f.Detector, $shown)
    }
}

if ($Gate -and $findings.Count -gt 0) {
    Write-Host 'FAIL: a secret-shaped literal is in the scanned set. Move it out of the source (a local properties file, an ambient CI token, or the encrypted store), or - only for a fixture or test-only literal - add an entry with its reason to scripts/quality/no-secrets-allowlist.txt.'
    exit 1
}
exit 0
