# DocParser.ps1 - extracts pin mentions from documentation files per manifest.
#
# Public entry: Get-DocMentions -Manifest <hashtable> -RepoRoot <string>
# Returns: array of [pscustomobject] records (one per (pin, doc) pair):
#     Pin       = '<pin-name>'
#     DocPath   = '<doc/path.md>'
#     Required  = $true | $false
#     Mentions  = @('1.2.3', '4.5.6')        # may be empty, may be multiple
#     Policy    = 'allMustMatch' | 'firstOnly'
#
# Contract sources:
#   PLAN/S0271_truth_drift_detection/DECISIONS.md  (D-1 schema, D-2 required, D-4 policy)
#   PLAN/S0271_truth_drift_detection.md            (strategic spec)
#
# No external module dependencies. PowerShell 7+. -NoProfile safe.

# --- Private cache + helpers -------------------------------------------------

$script:DocTextCache = @{}

function script:Read-DocText {
    param(
        [Parameter(Mandatory)][string] $AbsolutePath,
        [Parameter(Mandatory)][string] $PinHint
    )
    if ($script:DocTextCache.ContainsKey($AbsolutePath)) {
        return $script:DocTextCache[$AbsolutePath]
    }
    if (-not (Test-Path -LiteralPath $AbsolutePath)) {
        throw "DocParser: document file not found for pin '$PinHint': $AbsolutePath"
    }
    $text = Get-Content -LiteralPath $AbsolutePath -Raw -Encoding UTF8
    $script:DocTextCache[$AbsolutePath] = $text
    return $text
}

function script:Apply-Excludes {
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string] $Text,
        [AllowEmptyCollection()][string[]] $Excludes = @()
    )
    $filtered = $Text
    foreach ($pattern in $Excludes) {
        if ([string]::IsNullOrEmpty($pattern)) { continue }
        try {
            $filtered = [regex]::Replace(
                $filtered,
                $pattern,
                '',
                ([System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor
                 [System.Text.RegularExpressions.RegexOptions]::Singleline)
            )
        } catch {
            throw ("DocParser: exclude pattern compile failed: '{0}' :: {1}" -f $pattern, $_.Exception.Message)
        }
    }
    return $filtered
}

function script:Get-MatchValues {
    param(
        [Parameter(Mandatory)][AllowEmptyString()][string] $Text,
        [Parameter(Mandatory)][string] $Pattern,
        [Parameter(Mandatory)][string] $PinHint
    )
    $results = @()
    try {
        $matches = [regex]::Matches(
            $Text,
            $Pattern,
            ([System.Text.RegularExpressions.RegexOptions]::IgnoreCase -bor
             [System.Text.RegularExpressions.RegexOptions]::Multiline)
        )
    } catch {
        throw ("DocParser: matcher compile failed for pin '{0}': '{1}' :: {2}" -f $PinHint, $Pattern, $_.Exception.Message)
    }
    foreach ($m in $matches) {
        if ($m.Groups['v'].Success) {
            $results += $m.Groups['v'].Value
        }
    }
    return ,$results
}

# --- Public entry ------------------------------------------------------------

function Get-DocMentions {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][hashtable] $Manifest,
        [string] $RepoRoot = (Get-Location).Path
    )

    # Reset cache per call so stale data does not leak between consecutive runs.
    $script:DocTextCache = @{}

    $records = @()
    foreach ($pin in $Manifest.Pins) {
        $excludes = @()
        if ($pin.ContainsKey('exclude') -and $pin.exclude) { $excludes = $pin.exclude }
        $policy = 'allMustMatch'
        if ($pin.ContainsKey('policy') -and $pin.policy) { $policy = $pin.policy }

        foreach ($docKey in $pin.docs.Keys) {
            $spec = $pin.docs[$docKey]
            $required = [bool]$spec.required
            $matcher = $spec.matcher

            $mentions = @()
            if ($null -ne $matcher) {
                $absPath = Join-Path $RepoRoot $docKey
                $raw = Read-DocText -AbsolutePath $absPath -PinHint $pin.name
                $filtered = Apply-Excludes -Text $raw -Excludes ([string[]]$excludes)
                $captured = Get-MatchValues -Text $filtered -Pattern $matcher -PinHint $pin.name
                if ($policy -eq 'firstOnly' -and $captured.Count -gt 0) {
                    $mentions = @($captured[0])
                } else {
                    $mentions = @($captured)
                }
            }

            $records += [pscustomobject]@{
                Pin      = $pin.name
                DocPath  = $docKey
                Required = $required
                Mentions = $mentions
                Policy   = $policy
            }
        }
    }
    return ,$records
}
