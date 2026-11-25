# Dot-sourced by scripts/docs/stamp-portal-translation.ps1 and scripts/quality/assert-portal-pages.ps1 (S4653).
# The fingerprint of an English portal page body is the one value both must compute identically: the stamp
# records it in the translation's front matter, the freshness dimension compares it with the current page.

function Get-PortalBodyFingerprint {
    <# SHA-256 of the page body: front matter removed, LF line ends, the doc-stamp line removed. #>
    param([Parameter(Mandatory)][string] $Path)
    $text = [IO.File]::ReadAllText($Path).Replace("`r`n", "`n")
    if ($text.StartsWith("---`n")) {
        $end = $text.IndexOf("`n---`n", 4)
        if ($end -ge 0) { $text = $text.Substring($end + 5) }
    }
    $lines = @($text -split "`n" | Where-Object { $_ -notmatch '^<sub class="doc-stamp">' })
    $bytes = [Text.Encoding]::UTF8.GetBytes(($lines -join "`n").Trim())
    return ([Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes))).ToLowerInvariant()
}

function Get-PortalSourceStamp {
    <# The source_sha256 value a translation carries in its front matter, or '' when it carries none. #>
    param([Parameter(Mandatory)][string] $Path)
    $text = [IO.File]::ReadAllText($Path).Replace("`r`n", "`n")
    if (-not $text.StartsWith("---`n")) { return '' }
    $end = $text.IndexOf("`n---`n", 4)
    if ($end -lt 0) { return '' }
    $match = [regex]::Match($text.Substring(0, $end), '(?m)^source_sha256:\s*"?([0-9a-f]{64})"?\s*$')
    if ($match.Success) { return $match.Groups[1].Value }
    return ''
}
