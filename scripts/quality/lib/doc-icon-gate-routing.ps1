Set-StrictMode -Version Latest

function Test-DocIconGateRoute {
    param([string[]]$ChangedFiles)

    $patterns = @(
        '^docs/icons/doc-icon-map\.json$',
        '^docs/icons/doc/',
        '^scripts/docs/(apply-doc-icons|export-doc-icon-pngs|strip-landing-meta-icons)\.ps1$',
        '^scripts/docs/lib/vectordrawable-svg\.ps1$',
        '^scripts/quality/assert-doc-icons-sync\.ps1$',
        '^index(-ru|-uk)?\.html$',
        '^docs/howto/index(-ru|-uk)?\.md$',
        '^docs/DOCS_MAP\.md$',
        '^docs/SETTINGS_REFERENCE(_RU|_UK)?\.md$'
    )

    foreach ($changedFile in $ChangedFiles) {
        $normalized = ([string]$changedFile -replace '\\', '/') -replace '^\./', ''
        foreach ($pattern in $patterns) {
            if ($normalized -match $pattern) {
                return $true
            }
        }
    }

    return $false
}
