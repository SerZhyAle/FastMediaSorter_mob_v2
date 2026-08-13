# 03 - What tells the cache an app appeared, vanished or changed

Research for S1401 §6 item 3. Performed 2026-08-05 against the current working tree.

## Question

Which signal drives the app-list cache refresh: the launcher-apps service callback, which only works
while this app holds the home role, or the system package broadcasts?

## Constraints found in the tree

- `AppShortcutDataSource` documents the role hazard first-hand: `hasShortcutHostPermission()` is true
  only while this app is the active launcher, and the role can be revoked between the check and the
  call. Anything built on `LauncherApps` inherits that fragility.
- The cache is deliberately flavor-neutral (strategic ADR-1) and serves `AppPickerDialogFragment` in
  the shared layer, which exists in every flavor - including the four that have no launcher mode at
  all and therefore can never hold the home role.
- `ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED` and `ACTION_PACKAGE_REPLACED` are exempt from the
  implicit-broadcast restrictions that stop most manifest-declared receivers from firing, so a
  manifest receiver for them still works on current Android.
- App labels are locale-dependent: a system language change makes every cached label stale without
  any package event firing at all.

## Decision

Drive the cache from manifest-declared package broadcasts (`ADDED` / `REMOVED` / `REPLACED`, with the
`package` data scheme) plus `ACTION_LOCALE_CHANGED`, all in the shared layer. Do not build the refresh
path on `LauncherApps.Callback`:

- it would leave the cache stale for `AppPickerDialogFragment` in every flavor without launcher mode,
- and it would go silent the moment the user hands the home role back to another launcher, which is
  precisely when a stale cache is hardest to notice.

A package event refreshes only the affected package; a locale change re-reads every label but reuses
every cached icon, because an icon is not locale-dependent.

The cache also carries a schema/format version. A version mismatch after an app update triggers one
full background rebuild rather than a migration, since every row is recoverable from the system.
