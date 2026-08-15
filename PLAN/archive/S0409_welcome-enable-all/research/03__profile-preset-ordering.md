# Research 03 - Profile OTHER preset vs enable-all settings ordering

Resolves strategic §6 item 3.

## Question

Setting the profile to OTHER applies that profile's preset. In what order does that combine with the
bulk enable-all settings write, so the preset does not overwrite the just-enabled functions?

## Findings

- `WelcomeViewModel.saveDeviceProfile(isSkipped = false)` persists the selected profile and, on a first
  run (`!reentry`), calls `applyProfilePreset(finalType)`, which runs `ApplyProfilePresetUseCase.apply`.
- `ApplyProfilePresetUseCase` applies only the non-empty cells of the profile's CSV column over the
  current settings (empty cell = keep current). The OTHER column is the "no strong opinion" fallback;
  `DeviceProfile.appliedAtInstallTime` is even set to `false` for OTHER, signalling OTHER carries no
  install-time overrides.
- Both the preset apply and the functionality writes go through `SettingsRepository.updateSettings` as
  read-modify-write of the latest snapshot, so the last writer wins per field.

## Decision

Apply the OTHER profile (and therefore its preset) first, then layer the enable-all settings write on
top.

- The orchestrator triggers `saveDeviceProfile(isSkipped = false)` for OTHER first.
- Then it runs the enable-all settings use case, which read-modify-writes the latest snapshot and forces
  every whitelisted flag on. Because it runs after the preset and is a snapshot read-modify-write, it
  deterministically wins for its fields regardless of what the OTHER column contains.
- Ordering is sequenced on the application scope so the profile/preset write completes before the
  enable-all write begins, avoiding a lost-update race between the two snapshot writes.

This guarantees the "everything on" end state without depending on the contents of the OTHER CSV column.

## Implications for phases

- The enable-all settings use case must run after the profile save, not concurrently.
- The use case is a snapshot read-modify-write (same pattern as `WelcomeFunctionalityController.persist`).
