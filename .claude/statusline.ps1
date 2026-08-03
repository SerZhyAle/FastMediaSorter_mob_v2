# Claude Code statusline: model + context-window usage + rate-limit quota.
# Input: status JSON on stdin (fields: model, context_window, rate_limits).
# Goal: surface context fill so the operator knows when to /compact or /clear,
# and 5h/7d quota so Opus burn is visible at a glance.
$ErrorActionPreference = 'SilentlyContinue'
$data = $input | Out-String | ConvertFrom-Json

$model = $data.model.display_name
if (-not $model) { $model = 'Claude' }

# Warning band. Absolute magnitude and window fraction are both reported, and
# whichever is worse decides the marker: a large window hides an expensive
# session behind a small percentage, while a 200k-window session would sit
# permanently unmarked at 75% fill if only the absolute mattered.
function Band([double]$tokens, $pct) {
    $byAbs = if ($tokens -ge 250000) { 2 } elseif ($tokens -ge 150000) { 1 } else { 0 }
    $byPct = 0
    if ($null -ne $pct) {
        $byPct = if ($pct -ge 85) { 2 } elseif ($pct -ge 70) { 1 } else { 0 }
    }
    switch ([math]::Max($byAbs, $byPct)) {
        2 { ' [!!]' }
        1 { ' [!]' }
        default { '' }
    }
}

$parts = @("[$model]")

# Context load. Report magnitude first: the operator compacts on how much
# context is actually carried, not on how full an arbitrary window looks.
$ctx = $data.context_window
$ctxPct = $null
if ($ctx) {
    if ($null -ne $ctx.used_percentage) {
        $ctxPct = [math]::Round([double]$ctx.used_percentage)
    } elseif ([double]$ctx.context_window_size -gt 0) {
        $ctxPct = [math]::Round(([double]$ctx.total_input_tokens / [double]$ctx.context_window_size) * 100)
    }
}
$ctxTok = [double]$ctx.total_input_tokens
if ($ctxTok -gt 0) {
    $k = [math]::Round($ctxTok / 1000)
    $frac = if ($null -ne $ctxPct) { " ($ctxPct%)" } else { '' }
    $parts += "ctx ${k}k$frac$(Band $ctxTok $ctxPct)"
} elseif ($null -ne $ctxPct) {
    # No token count in the payload - report the fraction alone rather than
    # printing a misleading 0k.
    $parts += "ctx $ctxPct%$(Band 0 $ctxPct)"
}

# Rate-limit quota (Pro/Max only, populated after the first API call of the session).
$rl = $data.rate_limits
if ($rl) {
    $q = @()
    if ($null -ne $rl.five_hour.used_percentage) { $q += "5h $([math]::Round([double]$rl.five_hour.used_percentage))%" }
    if ($null -ne $rl.seven_day.used_percentage) { $q += "7d $([math]::Round([double]$rl.seven_day.used_percentage))%" }
    if ($q.Count -gt 0) { $parts += ('| ' + ($q -join ' ')) }
}

Write-Output ($parts -join ' ')
