# ViolationRecord.ps1 (S0313) - exports New-FlavorViolation.
#
# Builds one ordered violation record from raw inputs. It does no scanning and no
# classification: the `classification` field defaults to 'unset' and is filled
# later by the Phase 02 classifier. Field order is fixed so JSON output and human
# summaries stay stable.

Set-StrictMode -Version Latest

function New-FlavorViolation {
    # Fields (in declaration order):
    #   file                - repo-relative, forward-slash path
    #   line                - 1-based line number of the match
    #   matchedToken        - literal BuildConfig.* substring that matched
    #   tokenFamily         - IS / SUPPORT / ENABLE
    #   remediationCategory - fix pattern (from FlavorTokens.ps1)
    #   classification      - 'unset' until the classifier resolves
    #                         new-or-touched vs legacy
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string] $File,
        [Parameter(Mandatory)][int] $Line,
        [Parameter(Mandatory)][string] $MatchedToken,
        [Parameter(Mandatory)][string] $TokenFamily,
        [Parameter(Mandatory)][string] $RemediationCategory,
        [string] $Classification = 'unset'
    )

    [pscustomobject][ordered]@{
        file                = ($File -replace '\\', '/')
        line                = $Line
        matchedToken        = $MatchedToken
        tokenFamily         = $TokenFamily
        remediationCategory = $RemediationCategory
        classification      = $Classification
    }
}
