# RulePromptRecord.ps1 - executable-mismatch record contract for the rule/prompt drift audit (S0315).
#
# Public entry: New-RulePromptRecord -MismatchKind <k> -SourceA <s> -SourceB <s>
#                                    -Evidence <e> -Expected <x> -Actual <y> -Severity <sev>
# Returns: a single [pscustomobject] with exactly these fields:
#     mismatchKind  - one of $script:RulePromptMismatchKinds (closed taxonomy).
#     sourceA       - repo-relative path / token that asserts something.
#     sourceB       - repo-relative path or on-disk reality it conflicts with;
#                     'disk' when the conflict is "documented but absent".
#     evidence      - the offending line or token, trimmed.
#     expected      - the value the canonical rule prescribes.
#     actual        - the value found on the audited surface or on disk.
#     severity      - one of conflict, stale, missing.
#
# Closed taxonomy (strategic ADR-1): six executable-mismatch kinds only - no prose / style /
# tone / wording category exists by construction. An unknown kind throws.
#
# No external module dependencies. PowerShell 7+. -NoProfile safe.
# Dot-sourcing this file defines the constant and the function only; it emits nothing.

# --- Closed mismatch-kind taxonomy ------------------------------------------

$script:RulePromptMismatchKinds = @(
    'MissingDocumentedScript'
    'ConflictingRouteName'
    'MissingNoProfile'
    'AbolishedArtifactReference'
    'StaleCommandExample'
    'OutdatedValidationRule'
)

# --- Allowed severities ------------------------------------------------------

$script:RulePromptSeverities = @('conflict', 'stale', 'missing')

# --- Public entry ------------------------------------------------------------

function New-RulePromptRecord {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]   $MismatchKind,
        [Parameter(Mandatory)][AllowEmptyString()][string] $SourceA,
        [Parameter(Mandatory)][AllowEmptyString()][string] $SourceB,
        [Parameter(Mandatory)][AllowEmptyString()][string] $Evidence,
        [Parameter(Mandatory)][AllowEmptyString()][string] $Expected,
        [Parameter(Mandatory)][AllowEmptyString()][string] $Actual,
        [Parameter(Mandatory)][string]   $Severity
    )

    if ($script:RulePromptMismatchKinds -notcontains $MismatchKind) {
        throw ("New-RulePromptRecord: unknown mismatchKind '{0}'. Allowed: {1}" -f `
            $MismatchKind, ($script:RulePromptMismatchKinds -join ', '))
    }
    if ($script:RulePromptSeverities -notcontains $Severity) {
        throw ("New-RulePromptRecord: unknown severity '{0}'. Allowed: {1}" -f `
            $Severity, ($script:RulePromptSeverities -join ', '))
    }

    return [pscustomobject]@{
        mismatchKind = $MismatchKind
        sourceA      = $SourceA
        sourceB      = $SourceB
        evidence     = $Evidence.Trim()
        expected     = $Expected
        actual       = $Actual
        severity     = $Severity
    }
}
