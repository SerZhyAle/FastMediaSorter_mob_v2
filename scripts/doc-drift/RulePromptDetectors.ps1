# RulePromptDetectors.ps1 - executable-mismatch detectors for the rule/prompt drift audit (S0315).
#
# Reuse boundary: rule/prompt executable drift only. Version-pin drift -> check-doc-vs-gradle.ps1;
# spec-id-marker drift -> spec_catalog/drift-check.ps1 (S0278/S0279 own doc-vs-gradle scope).
#
# Public detectors (each returns a list of New-RulePromptRecord objects):
#   Find-MissingDocumentedScript     -Sources <list> -Inventory <list>
#   Find-MissingNoProfile            -Sources <list>
#   Find-AbolishedArtifactReference  -Sources <list>
#   Find-OutdatedValidationRule      -Sources <list> -Canonical <string>
#   Find-ConflictingRouteName        -Sources <list> -Canonical <string>
#
# Depends on New-RulePromptRecord (RulePromptRecord.ps1) being dot-sourced first.
# Detectors never invent a mismatchKind outside $script:RulePromptMismatchKinds.
#
# Precision over recall (strategic §7): a noisy audit gets ignored. Each detector excludes
# teaching examples (Wrong:/Right: pairs, negation lines) and placeholder tokens so the audit
# reports executable conflicts only, never prose or style.
#
# No external module dependencies. PowerShell 7+. -NoProfile safe.

# --- Shared body cache + helpers --------------------------------------------

$script:RulePromptBodyCache = @{}

function script:Get-SourceBody {
    param([Parameter(Mandatory)][object] $Source)
    $key = [string]$Source.path
    if ($script:RulePromptBodyCache.ContainsKey($key)) {
        return $script:RulePromptBodyCache[$key]
    }
    $text = ''
    if (Test-Path -LiteralPath $key -PathType Leaf) {
        $text = Get-Content -LiteralPath $key -Raw -Encoding UTF8
    }
    $script:RulePromptBodyCache[$key] = $text
    return $text
}

# A teaching line demonstrates an anti-pattern rather than prescribing it. Such lines pair a
# "Wrong:" demo with a "Right:" fix, or negate the token on the same line. They must not flag.
$script:RulePromptNegationMarkers = @(
    'wrong', 'bad', 'avoid', 'never', "don't", 'do not', 'forbidden', 'prohibited',
    'abolished', 'deprecated', 'instead of', 'no longer', 'not '
)

function script:Test-NegationLine {
    param([Parameter(Mandatory)][AllowEmptyString()][string] $Line)
    $low = $Line.ToLowerInvariant()
    foreach ($mark in $script:RulePromptNegationMarkers) {
        if ($low.Contains($mark)) { return $true }
    }
    return $false
}

# Self-owned audit files must not flag their own example tokens.
function script:Test-SelfOwnedSource {
    param([Parameter(Mandatory)][object] $Source)
    $rel = [string]$Source.relPath
    if ($rel -like 'scripts/doc-drift/*') { return $true }
    if ($rel -like 'PLAN/S0315_*') { return $true }
    return $false
}

# Repo top-level directories that mark a "/seg" token as a file path, not an actionable route.
$script:RulePromptPathDirs = @(
    'scripts', 'docs', 'dev', 'PLAN', 'src', 'app_v2', 'wear', 'temp', 'res', 'java',
    'com', 'sza', 'main', 'gradle', 'build', 'utils', 'devtest', 'network', 'player',
    'spec_catalog', 'CATALOG', 'fastmediasorter'
)

function script:Split-IntoLines {
    param([Parameter(Mandatory)][AllowEmptyString()][string] $Text)
    return ($Text -split "\r?\n")
}

# --- Detector 1: documented-but-absent scripts ------------------------------

function Find-MissingDocumentedScript {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]] $Sources,
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]] $Inventory
    )

    # Present-on-disk lookup: by full repo-relative path AND by basename, so a narrative mention
    # of "insert.ps1" resolves against "scripts/spec_catalog/insert.ps1".
    $relSet  = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $baseSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($inv in $Inventory) {
        [void]$relSet.Add(($inv.relPath -replace '\\', '/'))
        [void]$baseSet.Add([System.IO.Path]::GetFileName([string]$inv.relPath))
    }

    $records = @()
    foreach ($src in $Sources) {
        if (Test-SelfOwnedSource -Source $src) { continue }
        $body = Get-SourceBody -Source $src
        if ([string]::IsNullOrEmpty($body)) { continue }

        foreach ($line in (Split-IntoLines -Text $body)) {
            $hits = [regex]::Matches($line, '[\w][\w./-]*\.(?:ps1|sh)')
            foreach ($h in $hits) {
                $token = $h.Value.Trim('`', '"', "'", '(', ')', ',', ';')
                if ([string]::IsNullOrWhiteSpace($token)) { continue }
                # Skip globs / placeholders.
                if ($token -match '[\*<>]' -or $token -match 'Sxxxx' -or $token -match '\$\{') { continue }
                $normalized = ($token -replace '^\./', '') -replace '\\', '/'
                # Bare basenames (no directory separator) are narrative mentions or teaching
                # placeholders (foo.ps1, b.ps1); only path-like references assert a real file.
                if ($normalized -notmatch '/') { continue }
                if ($relSet.Contains($normalized)) { continue }
                $base = [System.IO.Path]::GetFileName($normalized)
                if ($baseSet.Contains($base)) { continue }

                $records += New-RulePromptRecord `
                    -MismatchKind 'MissingDocumentedScript' `
                    -SourceA $src.relPath `
                    -SourceB 'disk' `
                    -Evidence $token `
                    -Expected 'script present on disk' `
                    -Actual 'absent' `
                    -Severity 'missing'
            }
        }
    }
    return ,$records
}

# --- Detector 2: missing -NoProfile in documented pwsh invocations ----------

function Find-MissingNoProfile {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]] $Sources
    )

    $records = @()
    foreach ($src in $Sources) {
        if (Test-SelfOwnedSource -Source $src) { continue }
        $body = Get-SourceBody -Source $src
        if ([string]::IsNullOrEmpty($body)) { continue }

        foreach ($line in (Split-IntoLines -Text $body)) {
            if ($line -notmatch 'pwsh\s') { continue }
            # A line that mentions -NoProfile anywhere (e.g. a Wrong:/Right: pair) is already
            # self-consistent with the rule.
            if ($line -match '-NoProfile') { continue }
            # Teaching anti-pattern lines ("Wrong: pwsh -File ..") are demonstrations, not rules.
            if (Test-NegationLine -Line $line) { continue }
            # Only real invocations against a repo script or a -Command block.
            $isFileInvoke    = $line -match 'pwsh\s+.*-File\s+\S+\.ps1'
            $isCommandInvoke = $line -match 'pwsh\s+.*-Command'
            if (-not ($isFileInvoke -or $isCommandInvoke)) { continue }

            $records += New-RulePromptRecord `
                -MismatchKind 'MissingNoProfile' `
                -SourceA $src.relPath `
                -SourceB $src.relPath `
                -Evidence $line `
                -Expected 'pwsh -NoProfile' `
                -Actual 'pwsh without -NoProfile' `
                -Severity 'stale'
        }
    }
    return ,$records
}

# --- Abolished-token table (shared by detectors 3a and 3b) ------------------
# Each entry: a regex that matches the abolished prescription, plus its canonical replacement.
# Sourced from current CLAUDE.md rules (strict rules 13, validation table).

$script:RulePromptAbolishedTokens = @(
    @{
        name        = 'spec-filename-segment'
        pattern     = '_spec_'
        replacement = 'PLAN/Sxxxx_<slug>.md (the id identifies the artefact)'
    },
    @{
        name        = 'separate-audit-fix-file'
        pattern     = '(?i)(audit|fix)[-_]?(file|report)s?\s+under\s+PLAN'
        replacement = "findings live in the ticket's ## Last Audit block"
    },
    @{
        name        = 'pwsh-file-without-noprofile'
        pattern     = '(?<![\w-])pwsh\s+-File\b'
        replacement = 'pwsh -NoProfile -File'
    }
)

function script:Get-AbolishedHit {
    param([Parameter(Mandatory)][AllowEmptyString()][string] $Line)
    foreach ($entry in $script:RulePromptAbolishedTokens) {
        $m = [regex]::Match($Line, $entry.pattern)
        if ($m.Success) {
            return [pscustomobject]@{
                token       = $m.Value
                replacement = $entry.replacement
            }
        }
    }
    return $null
}

# --- Detector 3a: abolished-artifact references -----------------------------

function Find-AbolishedArtifactReference {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]] $Sources
    )

    $records = @()
    foreach ($src in $Sources) {
        if (Test-SelfOwnedSource -Source $src) { continue }
        $body = Get-SourceBody -Source $src
        if ([string]::IsNullOrEmpty($body)) { continue }

        foreach ($line in (Split-IntoLines -Text $body)) {
            $hit = Get-AbolishedHit -Line $line
            if ($null -eq $hit) { continue }
            # A line that prohibits / negates the token is a rule statement, not a prescription.
            if (Test-NegationLine -Line $line) { continue }

            $records += New-RulePromptRecord `
                -MismatchKind 'AbolishedArtifactReference' `
                -SourceA $src.relPath `
                -SourceB 'CLAUDE.md' `
                -Evidence $line `
                -Expected $hit.replacement `
                -Actual $hit.token `
                -Severity 'conflict'
        }
    }
    return ,$records
}

# --- Detector 3b: outdated validation rules ---------------------------------
# Reuses the abolished-token table: a surface that prescribes a per-step closure command
# the canonical no longer lists is an outdated validation rule.

function Find-OutdatedValidationRule {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]] $Sources,
        [Parameter(Mandatory)][string] $Canonical
    )

    $records = @()
    foreach ($src in $Sources) {
        if (Test-SelfOwnedSource -Source $src) { continue }
        # The canonical authority defines the rules; it cannot be outdated against itself.
        if (($src.relPath -replace '\\', '/') -ieq ($Canonical -replace '\\', '/')) { continue }
        $body = Get-SourceBody -Source $src
        if ([string]::IsNullOrEmpty($body)) { continue }

        foreach ($line in (Split-IntoLines -Text $body)) {
            # Only validation-context lines: a prescribed closure / validation command.
            if ($line -notmatch '(?i)\b(validate|validation|closure|must run|required closure)\b') { continue }
            $hit = Get-AbolishedHit -Line $line
            if ($null -eq $hit) { continue }
            if (Test-NegationLine -Line $line) { continue }

            $records += New-RulePromptRecord `
                -MismatchKind 'OutdatedValidationRule' `
                -SourceA $src.relPath `
                -SourceB $Canonical `
                -Evidence $line `
                -Expected $hit.replacement `
                -Actual $hit.token `
                -Severity 'stale'
        }
    }
    return ,$records
}

# --- Detector 4: conflicting route names ------------------------------------

function Find-ConflictingRouteName {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][AllowEmptyCollection()][object[]] $Sources,
        [Parameter(Mandatory)][string] $Canonical,
        [string] $RepoRoot = (Get-Location).Path
    )

    # Build the set of routes that physically exist as .claude/commands/<name>.md. This is an
    # on-disk fact independent of which Sources were passed, so an isolated source citing an
    # existing route (e.g. /git) never false-flags.
    $routeSet = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    $commandsDir = Join-Path $RepoRoot '.claude/commands'
    if (Test-Path -LiteralPath $commandsDir -PathType Container) {
        foreach ($cmd in Get-ChildItem -LiteralPath $commandsDir -Filter '*.md' -File -ErrorAction SilentlyContinue) {
            [void]$routeSet.Add([System.IO.Path]::GetFileNameWithoutExtension($cmd.Name))
        }
    }
    # Also honor any skill surfaces passed in (manifest-declared), in case the commands dir
    # location differs from the default.
    foreach ($s in $Sources) {
        if ([string]$s.kind -eq 'skill') {
            $name = [System.IO.Path]::GetFileNameWithoutExtension([string]$s.relPath)
            [void]$routeSet.Add($name)
        }
    }

    $records = @()
    $emitted = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)

    foreach ($src in $Sources) {
        if (Test-SelfOwnedSource -Source $src) { continue }
        $body = Get-SourceBody -Source $src
        if ([string]::IsNullOrEmpty($body)) { continue }

        # Actionable routes in this repo are written in backtick notation: `/route`.
        # Path segments (/java, /main, /scripts) are never backtick-wrapped as routes, so this
        # excludes file paths and URLs by construction (precision over recall).
        foreach ($m in [regex]::Matches($body, '`/([a-z][a-z0-9-]+)`')) {
            $route = $m.Groups[1].Value
            if ($script:RulePromptPathDirs -contains $route) { continue }
            if ($routeSet.Contains($route)) { continue }
            $dedupKey = ($src.relPath + '|' + $route)
            if (-not $emitted.Add($dedupKey)) { continue }

            $records += New-RulePromptRecord `
                -MismatchKind 'ConflictingRouteName' `
                -SourceA $src.relPath `
                -SourceB '.claude/commands' `
                -Evidence ('/' + $route) `
                -Expected 'route file present' `
                -Actual ('no .claude/commands/' + $route + '.md') `
                -Severity 'conflict'
        }
    }
    return ,$records
}
