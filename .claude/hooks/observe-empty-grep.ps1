<#
observe-empty-grep.ps1 - Claude Code PostToolUse hook (matcher: Grep).

Speaks only when a Grep that found nothing WOULD HAVE found something without its
path. Measured over 2026-08-05..12 (S1599 research 01): 651 of 4,297 Grep calls
returned nothing, 611 of them (93.9%) carried an explicit path, and an unscoped Grep
missed zero times. A pattern that misses under a path but hits repo-wide is
indistinguishable, from the tool result alone, from a symbol that does not exist -
and in 66.1% of cases the session drew the second conclusion and never asked again.
That is a correctness failure, not a token failure, and no turn-count metric sees it.

Asymmetry is the whole design (S1599 strategic section 5). The hook attaches context
ONLY when the widened re-run finds something, i.e. only when the original verdict
would have been wrong. If the widened run also misses, it says nothing and the
"not here" verdict stands, now confirmed. A false positive is therefore impossible by
construction rather than by the quality of a heuristic.

A PostToolUse hook CANNOT rewrite the tool result the model sees; it can only attach
additionalContext, which IS surfaced (verified while shipping S1594). That is why the
correction arrives beside the result rather than inside it, and why this could not be
a PreToolUse rewrite: before the call, whether the symbol exists is unknown, so
stripping a path would silently break deliberately narrow searches.

Contract:
  stdout = JSON with hookSpecificOutput.additionalContext, or nothing at all
  exit 0 = always

Exit codes (CLAUDE.md Rule 7):
  0  always - allow, with or without attached context.
Fail-silent on any parse, IO, or search error: this hook changes what the agent READS,
so a malfunction corrupts reasoning rather than merely gating a call. Emitting nothing
leaves the unmodified tool result in place, which is always a safe outcome.

Registered per-project in .claude/settings.json behind a stdin pre-filter - it is
meaningless outside a repository and must not pay a pwsh start on the ~85% of Grep
calls that found something.
#>

function Quit { exit 0 }

$RepoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

# Patterns for which ZERO IS THE CORRECT ANSWER, so a widened re-run proves nothing.
# 45 of the 651 measured empty results (6.9%) are these, and every one is mandated by a
# repository rule: the probe-tag sweep of CLAUDE.md "Debug Verification Tags", the
# TODO(phase-NN) sweep in .claude/templates/phase-file.md "Phase Done Criteria", and the
# banned-API sweeps of Rule 19. They are scoped narrowly ON PURPOSE - a probe tag is
# checked in sources, where the widened search would only rediscover the same literal
# quoted in the spec that asked for it. Data, not logic, so a new absence check is one
# line here (S1599 strategic section 6.1).
$AbsenceChecks = @(
    'Timber\s*\\?\.\s*[dv]\s*\\?\(\s*\\?"S\d{4}',   # probe-tag sweep
    'S\d{4}\\?:',                                    # spec-marker sweep
    'TODO\\?\(\s*phase-',                            # phase template done-criteria sweep
    'Log\\?\.\s*[dviwe]\\?\(',                       # Rule 19: non-Timber logging
    'GlobalScope',                                   # Rule 19: forbidden scope
    'System\s*\\?\.\s*out',                          # Rule 19: non-Timber logging
    'NotImplementedError',                           # Rule 19: shipped runtime stub
    '=\\?"#'                                         # Rule 19: hardcoded hex in layouts
)

$MaxFilesListed = 5
$TimeoutSeconds = 10

try {
    $raw = [Console]::In.ReadToEnd()
    if ([string]::IsNullOrWhiteSpace($raw)) { Quit }
    $payload = $raw | ConvertFrom-Json
}
catch { Quit }

if ([string]$payload.tool_name -ne 'Grep') { Quit }

$toolInput = $payload.tool_input
if ($null -eq $toolInput) { Quit }
$props = @($toolInput.PSObject.Properties.Name)

$pathArg = if ($props -contains 'path') { [string]$toolInput.path } else { '' }
# No path means nothing to widen - the search already covered the whole repository.
if ([string]::IsNullOrWhiteSpace($pathArg)) { Quit }

$pattern = if ($props -contains 'pattern') { [string]$toolInput.pattern } else { '' }
if ([string]::IsNullOrWhiteSpace($pattern)) { Quit }

foreach ($absence in $AbsenceChecks) {
    try { if ($pattern -match $absence) { Quit } } catch { Quit }
}

# The result body is the only evidence the search came back empty. Both spellings the
# Grep tool uses are accepted; anything else is treated as "found something".
$response = $payload.tool_response
$body = if ($response -is [string]) { $response } else { ($response | ConvertTo-Json -Depth 6 -Compress) }
if ([string]::IsNullOrWhiteSpace($body)) { Quit }
if ($body -notmatch '(?i)no (matches|files) found') { Quit }

# A path that IS the repository root was never a narrowing, so widening changes nothing.
try {
    $full = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $pathArg.Trim()))
    if ($full.TrimEnd('\', '/') -eq $RepoRoot.TrimEnd('\', '/')) { Quit }
}
catch { Quit }

function Resolve-Ripgrep {
    $cmd = Get-Command rg -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

$rg = Resolve-Ripgrep
if (-not $rg) { Quit }

# Everything the original call said about WHICH FILES to look at is preserved; only the
# WHERE is dropped. Keeping glob and type is what stops the widened count from being a
# different question than the one the session asked.
$rgArgs = @('--files-with-matches', '--no-messages', '--color', 'never')
if ($props -contains 'glob' -and -not [string]::IsNullOrWhiteSpace([string]$toolInput.glob)) {
    $rgArgs += @('--glob', [string]$toolInput.glob)
}
if ($props -contains 'type' -and -not [string]::IsNullOrWhiteSpace([string]$toolInput.type)) {
    $rgArgs += @('--type', [string]$toolInput.type)
}
if ($props -contains '-i' -and $toolInput.'-i') { $rgArgs += '--ignore-case' }
if ($props -contains 'multiline' -and $toolInput.multiline) { $rgArgs += @('--multiline', '--multiline-dotall') }
$rgArgs += @('--regexp', $pattern, '--', $RepoRoot)

$stdoutFile = [System.IO.Path]::GetTempFileName()
$matches = @()
try {
    $proc = Start-Process -FilePath $rg -ArgumentList $rgArgs -NoNewWindow -PassThru `
        -RedirectStandardOutput $stdoutFile -RedirectStandardError ([System.IO.Path]::GetTempFileName())
    if (-not $proc.WaitForExit($TimeoutSeconds * 1000)) {
        try { $proc.Kill() } catch { }
        Quit
    }
    $matches = @(Get-Content -LiteralPath $stdoutFile -ErrorAction SilentlyContinue | Where-Object { $_ })
}
catch { Quit }
finally { Remove-Item -LiteralPath $stdoutFile -ErrorAction SilentlyContinue }

# The widened search missed too, so the original verdict was right. Say nothing:
# a confirmation the session did not ask for is noise, and noise here is what would
# train the reader to stop reading this hook's output.
if ($matches.Count -eq 0) { Quit }

$shown = $matches | Select-Object -First $MaxFilesListed | ForEach-Object {
    $_.Replace($RepoRoot, '').TrimStart('\', '/').Replace('\', '/')
}
$more = if ($matches.Count -gt $MaxFilesListed) { " (+$($matches.Count - $MaxFilesListed) more)" } else { '' }

$context = @(
    "This Grep found nothing under path '$pathArg', but the SAME pattern matches " +
    "$($matches.Count) file(s) elsewhere in the repository. The pattern is fine; the path was too narrow.",
    "Matches: $($shown -join ', ')$more",
    "Re-run without 'path' (or with one of the paths above) before concluding the symbol does not exist."
) -join "`n"

$out = [PSCustomObject]@{
    hookSpecificOutput = [PSCustomObject]@{
        hookEventName     = 'PostToolUse'
        additionalContext = $context
    }
}
$out | ConvertTo-Json -Depth 5 -Compress
exit 0
