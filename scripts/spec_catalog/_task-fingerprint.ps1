# Shared fingerprint of a spec's TASK TEXT - the thing an audit claims to have judged (S2367).
#
# Dot-sourced, never invoked. Holds no mutable state and does not read the catalog, so a
# consumer inherits only these functions. Separate from `_lib.ps1` for the same reason
# `_research-items.ps1` is: the gate and the writer must agree byte for byte, and a second
# implementation of "which part of the file is the task" would let the gate refuse a
# transition the writer considers stamped (the S1621 rule).
#
# What counts as the task text: the strategic spec file MINUS the parts the pipeline writes
# into it on its own.
#   - `**Status:**`, `**Status note:**` and `**Priority:**` header lines. Sync-SpecHeaderStatus
#     rewrites the first two on every transition and the closure may recompute the third, so
#     including them would make the fingerprint differ from itself across the very status flip
#     it guards.
#   - The whole `## Last Audit` block. It is the audit's own output; hashing it would mean the
#     stamp could never match the file that carries it.
# Everything else is the task: the captured material, the problem, the goals, the constraints,
# the owner inputs, the research items and the acceptance criteria.
#
# The tactical folder is deliberately NOT part of it. Those files are the agent's own plan and
# its step ticks move continuously during implementation; folding them in would fire the gate
# on the pipeline's own work rather than on an edit to the task.
#
# Compatible with PowerShell 5.1 and 7+, and safe to load under Set-StrictMode Latest.

# Loadable standalone: a consumer that already has _research-items.ps1 (every path through
# _lib.ps1) must not re-source it, because that would reset its module-scoped root variable
# under a different $PSScriptRoot binding.
if (-not (Get-Command -Name 'Get-AuditSectionHeadingPattern' -ErrorAction SilentlyContinue)) {
    . (Join-Path $PSScriptRoot '_research-items.ps1')
}

$script:TaskFingerprintRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Get-TaskFingerprintLinePattern {
    # The line inside `## Last Audit` that records which task text the verdict judged.
    # Bold marker optional and the label case-insensitive: the block is hand-maintained
    # by /spec-check and by the pre-handoff writers, and a stamp that is readable to a
    # human but not to the gate would refuse a ticket that did everything asked.
    return '(?im)^\s*\*{0,2}Task\s+fingerprint:?\*{0,2}\s*[:\-]?\s*([0-9a-f]{12})\b'
}

function Resolve-TaskSpecPath {
    # Repo-relative ('PLAN/S2367_*.md') or already-absolute -> absolute path.
    param([Parameter(Mandatory)][string] $PathRef)
    $p = $PathRef -replace '/', '\'
    if ([System.IO.Path]::IsPathRooted($p)) { return $p }
    return (Join-Path $script:TaskFingerprintRoot $p)
}

function Get-SpecTaskText {
    # The normalized task text of one spec file, or $null when the file is unreadable.
    #
    # Normalization exists because a spec file can be written by two different agents in
    # one session and end up with a newline seam mid-file (S2357 found exactly that on
    # S1884). A fingerprint that moves on a line ending would report an edit nobody made.
    param([Parameter(Mandatory)][string] $Path)

    $abs = Resolve-TaskSpecPath -PathRef $Path
    if (-not (Test-Path -LiteralPath $abs -PathType Leaf)) { return $null }

    # -Encoding UTF8: spec bodies are Russian prose, and a mis-decoded body would hash to a
    # value that changes with the reader rather than with the task.
    $lines = @(Get-Content -LiteralPath $abs -Encoding UTF8)
    $auditPatterns = Get-AuditSectionHeadingPattern

    $kept = New-Object System.Collections.Generic.List[string]
    $inAudit = $false
    foreach ($line in $lines) {
        if ($line -match '^##\s+(.+)$') {
            $heading = $Matches[1].Trim()
            $inAudit = $false
            foreach ($pattern in $auditPatterns) {
                if ($heading -match $pattern) { $inAudit = $true; break }
            }
            if ($inAudit) { continue }
        }
        if ($inAudit) { continue }
        if ($line -match '^\*\*(Status|Status note|Priority):\*\*') { continue }
        $kept.Add($line.TrimEnd())
    }

    # Trailing blank lines and horizontal rules go with the block they introduce, not with the
    # task. Specs separate the audit block from the body with a `---`, and that rule is written
    # by the same hand that writes the block: counting it would make writing the verdict change
    # the fingerprint of the task the verdict is about, which is the one thing this must never
    # do. Only the TAIL is trimmed, so a rule between two task sections still counts.
    for ($i = $kept.Count - 1; $i -ge 0; $i--) {
        $t = $kept[$i].Trim()
        if ($t -eq '' -or $t -match '^([-*_])\1{2,}$') { $kept.RemoveAt($i) } else { break }
    }

    $text = ($kept -join "`n")
    return $text.Trim("`n")
}

function Get-SpecTaskFingerprint {
    # Twelve lowercase hex characters of SHA-256 over the task text, or $null when the
    # file is unreadable. Twelve rather than the full digest because the value is written
    # into a document a human reads: it must fit on the line beside the outcome, and the
    # collision it guards against is an accidental edit, not an adversary.
    param([Parameter(Mandatory)][string] $Path)

    $text = Get-SpecTaskText -Path $Path
    if ($null -eq $text) { return $null }

    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($text)
        $hash = $sha.ComputeHash($bytes)
    } finally {
        $sha.Dispose()
    }
    return -join ($hash[0..5] | ForEach-Object { $_.ToString('x2') })
}

function Get-RecordedTaskFingerprint {
    # The fingerprint the spec's own `## Last Audit` block claims, or $null when the block
    # is absent or carries no stamp. Read from the block only, never from the file at large:
    # a fingerprint quoted in prose elsewhere - a spec about this mechanism will quote one -
    # is documentation, not a verdict.
    param([Parameter(Mandatory)][string] $Path)

    $section = @(Get-SpecSectionLines -Path $Path -HeadingPattern (Get-AuditSectionHeadingPattern))
    if ($section.Count -eq 0) { return $null }

    $pattern = Get-TaskFingerprintLinePattern
    foreach ($entry in $section) {
        $m = [regex]::Match($entry.Text, $pattern)
        if ($m.Success) { return $m.Groups[1].Value.ToLowerInvariant() }
    }
    return $null
}
