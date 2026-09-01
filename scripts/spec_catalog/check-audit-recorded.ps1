[CmdletBinding()]
param(
    [Parameter(Mandatory)][string] $Id
)

# Gate for a transition INTO Verified - see S2298.
#
# Contract:
#   - `Verified` means "the audit passed". The audit's verdict lives in exactly one
#     place, the `## Last Audit` block of the spec file: /spec-check writes it, /spec-fix
#     reads it, and no separate audit registry exists. A ticket reaching Verified without
#     that block asserts a result it cannot show - the class CLAUDE.md section 12 forbids.
#   - Measured 2026-09-01 across every live Verified spec: 7 of 129 carry no block at all
#     (S1783, S1872, S2002, S2053, S2197, S2263, S2280). Their spec files attribute the
#     closure to three different paths - /spec-all, /spec-code and no marker at all - so
#     the missing check is not a defect of one command. Assert-ClosingGates is the one
#     point every path passes through, which is why the gate sits there.
#   - `Implemented` is deliberately NOT gated, unlike the two sibling closing gates:
#     it means "the code is done", and no audit has run by then by definition. Gating it
#     would demand a verdict before the thing that produces one.
#   - The block is found by HEADING TEXT through Get-AuditSectionHeadingPattern in
#     `_research-items.ps1`, shared verbatim with preview.ps1, so this gate's verdict and
#     the operator's `last_audit_present` flag cannot drift apart (the S1621 rule). Live
#     specs number their headings: `## 6. Last Audit` is a real spelling, and a literal
#     '## Last Audit' test reports it as absent.
#   - A heading with no content under it fails too. An empty block is the same absence of
#     evidence as no block, only with a table of contents.
#   - Archived is not reached: Assert-ClosingGates gates only Implemented and Verified.
#
# Exit codes: 0 = the spec carries a non-empty audit block. 1 = block absent or empty.
#             2 = bad invocation, or catalog / spec unreadable.

. (Join-Path $PSScriptRoot '_lib.ps1')

if ($Id -notmatch '^S\d{4}$') {
    # -ErrorAction Continue, not a bare Write-Error: _lib.ps1 sets $ErrorActionPreference = 'Stop',
    # under which a bare Write-Error throws and the documented `exit 2` is never reached (S1070).
    Write-Error "Invalid -Id '$Id' (must match S####)." -ErrorAction Continue
    exit 2
}

$record = Find-Record -Id $Id
if (-not $record) {
    Write-Error "No record with id '$Id' in the spec catalog." -ErrorAction Continue
    exit 2
}

$specPath = Resolve-SpecPath -PathRef $record.file
if (-not (Test-Path -LiteralPath $specPath -PathType Leaf)) {
    Write-Error "Spec file not found on disk: $specPath" -ErrorAction Continue
    exit 2
}

$rel = $record.file
$section = @(Get-SpecSectionLines -Path $record.file -HeadingPattern (Get-AuditSectionHeadingPattern))

# A horizontal rule is layout, not a verdict: a block holding only `---` says nothing an
# empty one does not, and specs end sections with one routinely.
$content = @($section | Where-Object {
    $t = $_.Text.Trim()
    $t -and ($t -notmatch '^([-*_])\1{2,}$')
})

if ($section.Count -eq 0 -or $content.Count -eq 0) {
    $reason = if ($section.Count -eq 0) {
        "carries no '## Last Audit' block"
    } else {
        "carries a '## Last Audit' heading with nothing under it"
    }
    Write-Output "FAIL $Id"
    Write-Output ("- {0}: {1}." -f $rel, $reason)
    Write-Output ""
    Write-Output "Verified means the audit passed, and the audit's verdict lives only in that block."
    Write-Output "Closing without it records a result nobody can read back."
    Write-Output "Do one of:"
    Write-Output ("  1. run the audit that produces it:  /spec-check {0}" -f $Id)
    Write-Output "  2. if the audit already ran, write its verdict into the block before closing."
    Write-Output "Implemented is not gated - use it when the code is done but no audit has run yet."
    exit 1
}

Write-Output "PASS $Id"
Write-Output ("Audit block present: {0} content line(s)." -f $content.Count)
exit 0
