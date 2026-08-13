# Recovery hints for the closure facade (S1598).
#
# Key   = the gate label exactly as scripts/post-change.ps1 prints it.
# Repro = one command that runs THAT gate alone, so the fix loop costs seconds
#         instead of a full facade run.
# Fix   = one sentence naming what to do with the finding.
#
# A label with no entry here prints without a hint - that is not an error, but
# scripts/quality/assert-gate-hints-sync.ps1 reports the gap so it does not go
# unnoticed until the gate next fails.
#
# This file is DATA on purpose (S1598 ADR-2): registering a gate must never mean
# editing the facade's output logic.

@{
    'ticket-log-audit' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-no-ticket-logs.ps1'
        Fix   = 'Each listed File:Line holds a ticket id in a permanent log, or a probe whose ticket left BlockNeedUserTest - delete the Timber line, or flip the ticket back if the device test is still pending.'
    }

    'neuroslop-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-neuroslop.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'A banned pattern grew in your files (CLAUDE.md Rule 19): trivial comment, empty catch, hardcoded layout colour, lifecycle-unsafe collect, GlobalScope, non-Timber log, shipped TODO(), long dash. Remove it - the baseline only ratchets down.'
    }

    'listener-symmetry-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-listener-symmetry.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'A register/add call in your files has no matching unregister/remove on the paired lifecycle callback - add the removal, or move both to the same owner.'
    }

    'doc-pin-drift' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-doc-pin-drift.ps1'
        Fix   = 'A version pin quoted in the docs no longer matches the build files - update the doc line to the value the report names, never the other way round.'
    }

    'settings-doc-sync-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-settings-doc-sync.ps1'
        Fix   = 'A settings surface changed without regenerating its docs (CLAUDE.md Rule 22) - regenerate docs/settings/settings-manifest.json and docs/SETTINGS_REFERENCE*.md, and annotate the new key.'
    }

    'detekt-baseline-absorption' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-detekt-baseline-absorption.ps1 -Gate'
        Fix   = 'A committed detekt baseline absorbed live findings wholesale - re-freeze only the intended entries, or fix the findings instead of accepting them.'
    }

    'flavor-matrix-doc-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-flavor-matrix-docs.ps1'
        Fix   = 'A doc restates the flavor grid from memory instead of the generated one - regenerate docs/FLAVOR_MATRIX.md via scripts/docs/generate-flavor-matrix.ps1 and align the offending line to it.'
    }

    'script-cheatsheet-sync-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-script-cheatsheet-sync.ps1'
        Fix   = 'A repository script was added, renamed or removed without re-rendering the cheatsheet - regenerate it, do not hand-edit the render target.'
    }

    'new-lexeme-count' = @{
        Repro = 'pwsh -NoProfile -File scripts/utils/list-new-lexemes.ps1'
        Fix   = 'Advisory only - a new string does not yet reach all thirteen declared locales. Nothing to fix at close time: the pre-release step 0.8 translates the whole release in one bulk round trip. Fill values early only if they are already known, via set-android-string.ps1 -Translations.'
    }

    'strings-audit' = @{
        Repro = 'pwsh -NoProfile -File scripts/check_strings_localized.ps1 -KeyPrefix "<prefix>"'
        Fix   = 'A string key is missing from EN, RU or UK - add the missing locales via scripts/utils/set-android-string.ps1 -Action add, which enforces parity.'
    }

    'string-format-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-string-format.ps1 -Gate'
        Fix   = 'Format specifiers disagree between locales of the same key - make every locale carry the same set, in positional form when there is more than one.'
    }

    'oss-notices-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-oss-notices.ps1'
        Fix   = 'A dependency changed without its third-party notice - regenerate the notices artifact so the shipped package keeps declaring what it bundles.'
    }

    'flavor-flag-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-flavor-flags-not-growing.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'Your change added a BuildConfig.IS_* flavor guard in src/main (CLAUDE.md Rule 14) - move the branch behind a contract interface with a per-flavor binding.'
    }

    'detekt-preflight' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/detekt-scoped.ps1 -ChangedFiles "<your,files>"'
        Fix   = 'detekt found NEW findings in your files - fix them in the source; never widen the baseline to absorb them.'
    }

    'detekt-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-detekt.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'detekt found findings above the committed baseline - fix the rule lines printed above; ReturnCount, MagicNumber and the 120-char limit are the usual three.'
    }

    'acceptance-probe-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-ticket-acceptance-probes.ps1 -Gate'
        Fix   = 'An acceptance predicate names a literal that no source line carries - grep the literal in its Timber form and correct the predicate to what the code prints.'
    }

    'public-mutable-flow-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-public-mutable-flow.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'A MutableStateFlow / MutableLiveData / MutableSharedFlow is exposed publicly - make the backing property private and expose the read-only projection.'
    }

    'deprecated-pm-flags-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-deprecated-pm-flags.ps1 -Gate -ChangedFiles "<your,files>"'
        Fix   = 'A raw-int PackageManager overload is back in src/main (CLAUDE.md Rule 21) - call the matching helper in util/PackageManagerCompat.kt instead.'
    }

    'fgs-notification-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-fgs-notifications.ps1 -Gate'
        Fix   = 'A foreground-service notification uses a ?attr-tinted small icon or skips ensuring its channel - use a solid drawable and ensure the channel before startForeground.'
    }

    'focus-highlight-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-focus-highlight.ps1 -Gate'
        Fix   = 'An interactive view in your layout has no visible focus indication (CLAUDE.md Rule 16) - apply the project focus frame; the baseline only ratchets down.'
    }

    'dialog-cancel-style-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-dialog-cancel-style.ps1 -Gate'
        Fix   = 'A confirm/cancel pair uses a one-off button style - switch to Widget.FastMediaSorter.Button.DialogConfirm / DialogCancel / DialogDestructive.'
    }

    'rtl-layout-attrs-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-rtl-layout-attrs.ps1 -Gate'
        Fix   = 'A layout uses left/right attributes without their start/end counterpart - add the start/end form so RTL locales lay out correctly.'
    }

    'orientation-implied-feature-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-orientation-implied-feature.ps1 -Gate'
        Fix   = 'A manifest orientation lock implies a hardware feature requirement that would shrink device reach - declare the feature as not required.'
    }

    'all-features-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/all_features/validate.ps1'
        Fix   = 'The capability inventory is invalid or lost records - fix the JSONL row; add capabilities through scripts/all_features/add.ps1, never by hand.'
    }

    'howto-settings-paths-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-howto-settings-paths.ps1 -Gate'
        Fix   = 'A guide names a settings path that no longer exists - correct the path to the one the settings manifest records, in every locale of that guide.'
    }

    'launcher-reset-coverage-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-launcher-reset-coverage.ps1 -Gate'
        Fix   = 'A launcher preference is not covered by the reset path - add it there so a reset leaves no stale state behind.'
    }

    'icon-inventory-sync-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-icon-inventory-sync.ps1'
        Fix   = 'The icon inventory render is stale - regenerate it; repo-wide, so the finding may belong to another ticket in flight and stays advisory under -ScopeToFile.'
    }

    'device-profile-matrix-gate' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/assert-device-profile-matrix.ps1'
        Fix   = 'The device-profile matrix render is stale - regenerate it; repo-wide, so it stays advisory under -ScopeToFile.'
    }

    'doc-pins-sync' = @{
        Repro = 'pwsh -NoProfile -File scripts/quality/generate-toolchain-pins.ps1'
        Fix   = 'The generated toolchain pins are stale - regenerate them; the generated block is a render target and is never hand-edited.'
    }

    'document-registry' = @{
        Repro = 'pwsh -NoProfile -File scripts/document_registry/query.ps1 -ProductArea "<area>"'
        Fix   = 'Your changed set touches registered documents - read the named records and pass their ids back as -RegistryAck on the same run.'
    }

    'catalog-sync' = @{
        Repro = 'pwsh -NoProfile -File scripts/catalog_sync.ps1 -Module app_v2'
        Fix   = 'The class catalog could not be rebuilt - read the error above; the index is a gitignored artifact, so re-running after the fix is safe.'
    }

    'dev-log' = @{
        Repro = 'pwsh -NoProfile -File scripts/add_to_dev_log.ps1 "<path>" "<target>" "<description>"'
        Fix   = 'The changelog row could not be written - read the error above; never edit dev/CHANGELOG.md by hand to work around it.'
    }
}
