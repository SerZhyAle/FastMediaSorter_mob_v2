<#
guard-bash-slash-arg.ps1 - Claude Code PreToolUse hook (matcher: Bash). S1458.

Blocks a Bash command that hands `pwsh` an argument value beginning with a slash-command name:
  pwsh -NoProfile -File scripts/utils/wait-for-lock-turn.ps1 -Reason "/spec-dev S1458 phase 02"
MSYS reads the leading "/spec-dev .." as a POSIX path and rewrites it to the Git installation root,
so the script receives "C:/Program Files/Git/spec-dev S1458 phase 02". Nothing fails and the exit
code stays 0, so the corrupted text simply lands in the lock and lease files, where the reason is
the only diagnostic telling a reader who is working on what.

PERIMETER, measured 2026-08-09 over 279 transcripts - 21887 Bash calls, 12958 mentioning pwsh.
Slash-leading values are overwhelmingly legitimate POSIX paths (/c 2079, /ANDROID 1910, /dev 1298,
/scripts 799, /sdcard 448) against roughly 210 command names led by /spec-dev at 106. An exclusion
list would therefore be longer and more dangerous than a list of violations, so the predicate is
inverted: only a first segment matching a REAL command name is refused, and the names are read from
.claude/commands/*.md rather than restated here, so a new command is covered without editing this
file. `release` is both a command and a path segment; the two are separated by the next character -
"/release/x" is a path and passes, "/release 31" is a command name and is refused.

Contract:
  exit 2 = block the tool call (stderr is shown to Claude)
  exit 0 = allow
Fail-open on any parse error so a malformed payload never breaks the Bash tool globally.

See CLAUDE.md Rule 25 for the sibling guard on the same boundary. Unlike the guards behind rules
24-26 this one is registered by the PROJECT settings (.claude/settings.json), so it travels with
this checkout and is version-controlled with the code it protects.
#>

$ErrorActionPreference = 'Stop'

function Allow { exit 0 }
function Deny([string]$msg) { [Console]::Error.WriteLine($msg); exit 2 }

try {
    $raw = [Console]::In.ReadToEnd()
    if (-not $raw) { Allow }

    # Cheap literal pre-filter before any parsing: this hook runs on every Bash tool call, and the
    # overwhelming majority never mention pwsh at all.
    if ($raw -notmatch 'pwsh') { Allow }

    $payload = $raw | ConvertFrom-Json

    # The registration matches Bash only, but a hook that judges a payload it was not meant to see
    # would silently extend its reach if that registration ever widened.
    if ($payload.tool_name -and $payload.tool_name -ne 'Bash') { Allow }

    $command = $payload.tool_input.command
    if (-not $command) { Allow }
    if ($command -notmatch 'pwsh') { Allow }

    # The caller already applied the environment-variable measure, so the value reaches the script
    # verbatim and there is nothing left to refuse.
    if ($command -match 'MSYS2_ARG_CONV_EXCL') { Allow }

    $commandsDir = Join-Path $PSScriptRoot '..\commands'
    if (-not (Test-Path -LiteralPath $commandsDir)) { Allow }
    $names = @(Get-ChildItem -LiteralPath $commandsDir -Filter '*.md' -File -ErrorAction SilentlyContinue |
        ForEach-Object { $_.BaseName })
    if ($names.Count -eq 0) { Allow }

    $alternation = ($names | Sort-Object -Property Length -Descending |
        ForEach-Object { [regex]::Escape($_) }) -join '|'
    # A command name directly after a single slash, ended by whitespace or the closing quote.
    # A following slash means a path segment and is deliberately not matched.
    $rx = [regex]"(?<![\w/])/(?<name>$alternation)(?=\s|`"|'|$)"

    $hit = $rx.Match($command)
    if (-not $hit.Success) { Allow }

    $name = $hit.Groups['name'].Value
    Deny @"
guard-bash-slash-arg: refusing '/$name' as an argument value in a Bash-tool pwsh call (S1458).
MSYS rewrites a leading '/$name' into the Git installation root, so the script would receive
'C:/Program Files/Git/$name ..' and the corruption is silent - nothing fails and the exit code stays 0.
Use any of these instead:
  1. double the leading slash:  -Reason "//$name .."
  2. prefix the command:        MSYS2_ARG_CONV_EXCL='*' pwsh -NoProfile -File ..
  3. issue the call from the PowerShell tool, where the value arrives verbatim.
"@
}
catch {
    Allow
}
