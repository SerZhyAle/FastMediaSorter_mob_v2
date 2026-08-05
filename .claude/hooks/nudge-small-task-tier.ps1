<#
nudge-small-task-tier.ps1 - Claude Code UserPromptSubmit hook (S1338 follow-up).

Injects a routing reminder when the owner's request reads as a micro-task, so the agent
reaches for /quick or /skill-fix instead of opening the full spec pipeline.

Why this exists: CLAUDE.md section 3 already documents the size tier, smallest first -
/quick, then /skill-fix, then /spec-next. It is an ungated sentence, and it behaves like
every other ungated sentence here. Measured over the whole transcript corpus on 2026-08-05:
434 slash-command invocations, of which /quick 0, /caveman 0, /skill-fix 2, against
/spec-next 91, /spec-draft 64, /spec-all 44, /spec-do 15. The cheapest tier has never once
been chosen. The 2026-07-31 process audit found gated rules hold at ~99% and ungated ones
at 1-8%, so the fix has to fire at the moment of routing rather than sit in the always-on
text - which is exactly when a UserPromptSubmit hook runs.

This is NOT the context-pricing hook that audit section 5 refuted. That one was rejected
as timing-blind: it tried to read accumulated context, and the tax it chased accrues inside
autonomous blocks where no prompt is ever submitted. Routing is the opposite case - it
happens precisely when the owner types, so this event is the right place for it.

Deliberately advisory, never blocking. A false fire that refused a prompt would be worse
than the miss it prevents, and the owner has to stay free to say "no, this is bigger than
it looks". The hook emits additionalContext and always exits 0.

Precision over recall by design. A nudge on real feature work trains the reader to ignore
it, so the trigger list stays short and obviously-small, a negative list vetoes anything
that smells like real work, and a length ceiling drops long briefs before either list runs.

Contract:
  exit 0 always - a prompt must never fail to submit because of this hook.
  stdout is either empty (no nudge) or one compact JSON object carrying additionalContext.

Exit codes (CLAUDE.md Rule 7):
  0  always, whether or not a nudge was emitted, and on any internal failure.

Registered per-project in .claude/settings.json.
#>

# Anything longer than this is a brief, not a one-liner - checked before the pattern lists
# so a long prompt that merely mentions a colour never fires.
$MaxPromptChars = 200

# Small, high-confidence signals. RU first because the owner writes RU; EN for pasted text.
$MicroTaskPatterns = @(
    'опечатк', 'очепятк', '\btypo\b'
    'переименуй', '\brename\b'
    '(помен|смени|измени|поправ|подкрут)\w*\s+(цвет|отступ|размер|шрифт|текст|надпис|иконк|подпис)'
    '(подвинь|сдвинь|выровняй|отцентр)'
    'убери\s+(лишн|пробел|точк|запят)'
    '(текст|надпись|подпись)\s+(кнопк|заголовк|подсказк|тултип)'
    'одн(у|ой)\s+(строк|строчк|букв)'
    '\bмелоч', 'по\s+мелоч'
    '(быстр\w*|просто)\s+(поправ|фикс|исправ|помен)'
    'one[- ]liner', 'single (string|line|colour|color)'
)

# Vetoes. Each of these means the request is real work no matter how short it is phrased.
$RealWorkPatterns = @(
    'крэш', 'краш', 'падает', 'вылетает', '\bcrash\b', '\bANR\b'
    'миграц', 'migration'
    'рефактор', 'refactor'
    '(нов|добав)\w*\s+(функци|фич|экран|настройк|возможност)'
    '\bfeature\b', 'new screen'
    'релиз', 'release'
    'спек', '\bspec\b', '\bS\d{4}\b'
    'тест', '\btest\b'
    'замер', 'измер', 'аудит', 'audit'
    'архитектур', 'architecture'
)

function Read-StdinUtf8 {
    # The prompt is Cyrillic; the console code page here is cp1251, so decoding through the
    # default reader corrupts it and every pattern silently misses. Decode the raw stream.
    try {
        $stream = [Console]::OpenStandardInput()
        $reader = New-Object System.IO.StreamReader($stream, (New-Object System.Text.UTF8Encoding $false))
        return $reader.ReadToEnd()
    }
    catch {
        return ''
    }
}

try {
    $raw = Read-StdinUtf8
    if ([string]::IsNullOrWhiteSpace($raw)) { exit 0 }

    $payload = $raw | ConvertFrom-Json -ErrorAction Stop
    $prompt = [string]$payload.prompt
    if ([string]::IsNullOrWhiteSpace($prompt)) { exit 0 }

    $trimmed = $prompt.Trim()

    # The owner already picked a tier - never second-guess an explicit slash command.
    if ($trimmed.StartsWith('/')) { exit 0 }
    if ($trimmed.Length -gt $MaxPromptChars) { exit 0 }

    $hit = $false
    foreach ($p in $MicroTaskPatterns) {
        if ($trimmed -imatch $p) { $hit = $true; break }
    }
    if (-not $hit) { exit 0 }

    foreach ($p in $RealWorkPatterns) {
        if ($trimmed -imatch $p) { exit 0 }
    }

    $note = @(
        'Size-tier check (CLAUDE.md section 3) - this request matched the micro-task shape:'
        'short, and about a string, a colour, a rename or a nudge of one element.'
        'Start at the smallest tier that can do it. /quick for a single string, typo, colour'
        'or design tweak - no spec, no build, no dev log. /skill-fix for a small bug or UI fix,'
        'or a doc/config/script tweak that needs no gradle. Do NOT open /spec-next, /spec-all'
        'or /spec-do for this.'
        'Measured 2026-08-05: /quick has been invoked 0 times in 434 command invocations while'
        '/spec-next took 91, so the default reach is the wrong one and this is the correction.'
        'If the work turns out to need a build, a spec, or more than one file, say so in one'
        'line and escalate - this is a reminder, not a rule you must obey against evidence.'
    ) -join ' '

    $out = [ordered]@{
        hookSpecificOutput = [ordered]@{
            hookEventName   = 'UserPromptSubmit'
            additionalContext = $note
        }
    }
    Write-Output ($out | ConvertTo-Json -Compress -Depth 5)
}
catch {
    # A malformed payload or a parser change must never cost the owner a prompt. Report on
    # stderr so the failure is visible in --debug, then submit exactly as before.
    [Console]::Error.WriteLine("nudge-small-task-tier: skipped - $_")
}

exit 0
