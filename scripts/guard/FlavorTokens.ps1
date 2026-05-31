# FlavorTokens.ps1 (S0313) - exports Get-FlavorTokenPatterns.
#
# Data-only module: returns the ordered set of forbidden flavor-gate token
# patterns enforced by CLAUDE.md Rule 15 for src/main/java code. It performs no
# scanning and reads no files. Remediation categories are drawn from
# dev/FLAVOR_DEVELOPMENT_RULES.md (interface boundary / No-Op default /
# Hilt flavor module / res override / source-set impl).

Set-StrictMode -Version Latest

# Single anchored regex that matches every forbidden BuildConfig flavor gate.
# Exposed as a module-scope constant so the driver and tests can share the exact
# same expression instead of re-declaring it.
$script:FlavorTokenRegex = 'BuildConfig\.(IS_|SUPPORT_|ENABLE_)[A-Z0-9_]+'

function Get-FlavorTokenPatterns {
    # Returns one record per forbidden token family. Each record carries:
    #   pattern              - the shared anchored regex (same for every family;
    #                          tokenFamily is captured from the live match, not
    #                          from a per-family sub-pattern)
    #   tokenFamily          - IS / SUPPORT / ENABLE
    #   remediationCategory  - the dev/FLAVOR_DEVELOPMENT_RULES.md fix pattern
    #                          recommended when this family is hit in src/main
    [CmdletBinding()]
    param()

    # IS_*  -> a flavor-identity branch. The fix is to hide the identity behind a
    #          flavor-aware interface injected into the main class.
    # SUPPORT_* / ENABLE_*  -> a capability gate. The fix is a real impl in the
    #          flavor source set plus a No-Op default, bound by a flavor Hilt
    #          module; layout/string differences move to a res override.
    @(
        [pscustomobject]@{
            pattern             = $script:FlavorTokenRegex
            tokenFamily         = 'IS'
            remediationCategory = 'interface-boundary'
        }
        [pscustomobject]@{
            pattern             = $script:FlavorTokenRegex
            tokenFamily         = 'SUPPORT'
            remediationCategory = 'source-set-impl'
        }
        [pscustomobject]@{
            pattern             = $script:FlavorTokenRegex
            tokenFamily         = 'ENABLE'
            remediationCategory = 'source-set-impl'
        }
    )
}

function Get-FlavorTokenRegex {
    # Convenience accessor for the shared anchored regex string.
    [CmdletBinding()]
    param()
    $script:FlavorTokenRegex
}

function Get-RemediationCategoryForFamily {
    # Maps a captured token family (IS / SUPPORT / ENABLE) to its recommended
    # remediation category. Used by the scanner when it builds a violation from a
    # live match rather than iterating the pattern table.
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $TokenFamily
    )
    switch ($TokenFamily) {
        'IS'      { 'interface-boundary' }
        'SUPPORT' { 'source-set-impl' }
        'ENABLE'  { 'source-set-impl' }
        default   { 'source-set-impl' }
    }
}
