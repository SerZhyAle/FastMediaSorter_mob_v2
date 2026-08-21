# Shared lifecycle-status sets for spec_catalog scripts.
#
# Dot-sourced, never invoked: this file sets no preferences, holds no mutable state and
# knows nothing about the catalog journal, so a consumer inherits only these functions.
# Same shape and same reason as `_research-items.ps1` (S1621) - `preview.ps1` sits on the
# `/spec-next` hot path and cannot afford `_lib.ps1`'s `Set-StrictMode -Version Latest`,
# under which its own `$rec.statusNote` read throws on any record without a note.
#
# Consumers: `_lib.ps1` (which re-exports these to the whole spec_catalog CLI) and
# `preview.ps1` (the auto-skip verdict). One definition, one answer - before S1864 the two
# disagreed: `preview.ps1` released a dependent only at `Verified`/`Archived` while this
# library already counted `BlockNeedUserTest` as finished content, so a ticket whose blocker
# had shipped stayed out of the selection for up to seven more days.
#
# Compatible with PowerShell 5.1 and 7+, and safe to load under Set-StrictMode Latest.

function Test-ReleaseReadyStatus {
    # Ready = the ticket's code is done as far as this release is concerned. Implemented and
    # Verified are self-evident; BlockNeedUserTest counts too, because some flows are very hard
    # to verify and the owner treats a long-pending device check as shipped - if it later turns
    # out broken it simply comes back as fresh work in a later package.
    param([Parameter(Mandatory)][string] $Status)
    return $Status -in @('Implemented', 'Verified', 'BlockNeedUserTest')
}

function Test-BlockerReleasedStatus {
    # Released = this blocker no longer holds its dependents back. That is the release-ready set
    # plus Archived, which is a soft-delete: an archived blocker will never advance, so waiting on
    # it is waiting forever.
    #
    # Why the set is not narrower (S1864): the owner ruling in PLAN/RELEASE_QUEUE.md releases a
    # dependent the moment the blocker reaches BlockNeedUserTest, on the grounds that the code is
    # in the tree by then and only the device pass is left. That reasoning is at least as true of
    # Implemented, and CLAUDE.md section 4 already splits the two release files on exactly this
    # set - so this is that same set, not a second opinion about it.
    param([Parameter(Mandatory)][string] $Status)
    return (Test-ReleaseReadyStatus -Status $Status) -or $Status -eq 'Archived'
}
