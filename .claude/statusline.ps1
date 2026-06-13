# Claude Code statusline: model + context-window usage + rate-limit quota.
# Input: status JSON on stdin (fields: model, context_window, rate_limits).
# Goal: surface context fill so the operator knows when to /compact or /clear,
# and 5h/7d quota so Opus burn is visible at a glance.
$ErrorActionPreference = 'SilentlyContinue'
$data = $input | Out-String | ConvertFrom-Json

$model = $data.model.display_name
if (-not $model) { $model = 'Claude' }

function Bar([int]$pct) {
    $filled = [math]::Floor($pct / 10)
    if ($filled -gt 10) { $filled = 10 }
    if ($filled -lt 0) { $filled = 0 }
    ('#' * $filled) + ('-' * (10 - $filled))
}

$parts = @("[$model]")

# Context-window fill: prefer the pre-calculated percentage, else derive it.
$ctx = $data.context_window
$ctxPct = $null
if ($ctx) {
    if ($null -ne $ctx.used_percentage) {
        $ctxPct = [math]::Round([double]$ctx.used_percentage)
    } elseif ([double]$ctx.context_window_size -gt 0) {
        $ctxPct = [math]::Round(([double]$ctx.total_input_tokens / [double]$ctx.context_window_size) * 100)
    }
}
if ($null -ne $ctxPct) {
    $parts += "ctx $(Bar $ctxPct) $ctxPct%"
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
