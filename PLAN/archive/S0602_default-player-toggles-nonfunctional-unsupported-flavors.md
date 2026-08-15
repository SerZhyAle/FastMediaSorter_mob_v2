**Status:** Archived

# S0602 - Default-player toggles persist nothing on flavors without default-player support

## 0. Capture (raw)

Discovered during S0599 research. Behavioral bug, independent of settings search.

On flavors where `MediaCapabilities.supportsDefaultPlayer` is false, the toggle rows `rowPrimaryMediaPlayer` and `rowAcceptSharedFiles` (Operations tab) remain VISIBLE, but their `setOnCheckedChangeListener` is never registered - the wiring is guarded by `if (mediaCapabilities.supportsDefaultPlayer)` (`OperationsSettingsFragment.kt:343`). Tapping toggles the visual check state with no persistence and no effect.

## 1. Problem

A user-visible control that silently does nothing. Hide the default-player toggle rows on unsupported flavors (mirroring the other capability-gated rows) and ensure they do not surface as dead settings-search results.

## 2. Findings

- Only the `lite` flavor sets `SUPPORTS_DEFAULT_PLAYER=false`; standard, vr, photos, and legacy all set it true.
- The capture premise was inaccurate. At HEAD the container `layoutDefaultPlayerToggles` was already hidden on unsupported flavors, so both rows were not actually displayed; the listener guard never made stale, visible rows.
- The genuine residual defect was in settings search. The search index is a static XML scan (`LayoutSettingsSearchSource`), so both toggle rows and the four default-player registration buttons are indexed on every flavor regardless of runtime visibility. Without a capability gate they returned as dead results on `lite`: search finds the row, tapping lands on a screen where it is hidden.
- The capture assumption that hiding a row also drops it from search "as a side effect" does not hold for this architecture - the index is static, so an explicit gate is required.

## 3. Resolution

Default-player surface is now gated consistently in both UI and search on `lite`.

- `OperationsSettingsFragment.applyFlavorRestrictions()` hides `layoutDefaultPlayerToggles` plus both rows and resets the persisted `isPrimaryMediaPlayer` / `acceptSharedFiles` to false when `supportsDefaultPlayer` is false, mirroring the OCR/translation block above it.
- `SettingsSearchRegistry.isCapabilityAvailable()` excludes `rowPrimaryMediaPlayer`, `rowAcceptSharedFiles`, and the four `btnSettingsDefaultPlayer*` buttons from the index when the matching capability is absent.
- `DefaultPlayerSettingsManager.bind()` already hid the registration-button subgroup on unsupported flavors; no change needed there.
- This ticket also removed the redundant standalone `layoutDefaultPlayerToggles.isVisible` assignment in `setupViews()` so `applyFlavorRestrictions()` is the single source of truth for default-player gating.

## 4. Notes

- The `applyFlavorRestrictions` and `SettingsSearchRegistry` changes are part of the in-flight S0599 settings-search work in the same working tree; this ticket covers the default-player slice of that surface plus the source-of-truth cleanup.
- Build and on-device verification are pending (implemented under a NO BUILD request). Device test: build the `lite` flavor, open Operations settings and confirm the default-player toggles and registration buttons are absent, then open settings search and confirm no default-player entries are returned.
