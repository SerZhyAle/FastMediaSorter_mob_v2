# 01 - Row anatomy, the not-applicable question, and the pieces the plan builds on

Research performed 2026-08-06 for S1426, read-only sweep of `app_v2`. Every claim carries `file:line`.

## Answer to strategic section 6, item 1

The open question was whether an item that does not apply to the device should be shown as a dimmed
"not required" row or hidden entirely, and how much vertical space either choice buys.

**Neither: such a row does not exist today, on any supported Android version.** Both hosts source their
entries exclusively from `PermissionRegistryRepositoryImpl.getEntries()` / `getWelcomeEntries()`
(`PermissionsManagementFragment.kt:164-189`, `WelcomePermissionsManager.kt:298-323`), and that method already
applies the SDK window and the flavor gates *upstream* (`PermissionRegistryRepositoryImpl.kt:155-160`). An
inapplicable permission therefore never becomes a row at all. Consequently
`CheckPermissionStatusUseCase.kt:23-25`'s own `NOT_APPLICABLE` guard can never fire for either host, and the
`NOT_APPLICABLE` branches in `PermissionRowAdapter.kt:86,95,108` are unreachable in practice.

Implications the plan takes as settled:

- The density goal gains nothing from hiding inapplicable rows - there are zero of them to hide.
- The four-state indicator still models "not required", because the state exists in the enum and costs
  nothing to render, but no layout or spacing decision may depend on it appearing.
- Making that state actually visible would require a change *above* the registry filter. That is out of this
  ticket's scope and is not planned.

## Row anatomy

`app_v2/src/main/res/layout/item_permission_entry.xml` (55 lines) is a vertical `LinearLayout` with exactly
three stacked children, which is what sets the item height:

- `tv_perm_entry_title` (`:13`) - 14sp bold, full width.
- `tv_perm_entry_desc` (`:20`) - 12sp secondary, `visibility="gone"` by default (`:26`), shown by the adapter
  only when `descriptionRes != 0`.
- a nested horizontal `LinearLayout` (`:28-53`) holding `tv_perm_entry_status` (`:36`, weight 1) and
  `btn_perm_action` (`:44`, `Widget.FastMediaSorter.Button.Filled`, `minWidth=80dp`). This is the block the
  strategic spec deletes.

Every dp and sp value in this file is a literal - there is no `@dimen` indirection here, unlike the two host
layouts around it, which already use `@dimen/welcome_*` tokens (`page_welcome_permissions.xml:15,21,32,36`).

**No landscape variant of the row exists.** Checked every configuration bucket under `app_v2/src/main/res`
(`layout`, `layout-land`, `layout-sw480dp`, `layout-sw720dp`, `layout-w600dp`): `item_permission_entry.xml`
and `item_permission_group_header.xml` exist only in plain `layout/`. CLAUDE.md Rule 11 therefore does not
apply to the row - one edit serves both orientations. The two *host* layouts do have `layout-land/`
counterparts, but this ticket changes neither.

## Status computation and the ambiguity

`CheckPermissionStatusUseCase.kt:19-53` is a synchronous, non-suspend `operator fun` with no I/O beyond
platform calls. Its three branches:

- SDK window guard (`:23-25`) - unreachable in practice, see above.
- Three special permissions (`:26-37`) - `MANAGE_EXTERNAL_STORAGE`, `MANAGE_MEDIA` and
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` resolve through `PermissionHelper` / `PowerManager` and return only
  `GRANTED` or `DENIED`. They never enter the ambiguity, which confirms the strategic spec's "these keep their
  current rule" without any new machinery.
- Every other permission (`:38-51`) - granted, else `shouldShowRequestPermissionRationale == false` is read as
  `PERMANENTLY_DENIED`. The code says so itself at `:43`: "PERMANENTLY_DENIED is indistinguishable from
  never-requested without caller tracking".

Second, narrower asymmetry worth knowing: when `context as? Activity` is null (`:42`), the ambiguous case
falls through to `DENIED` rather than `PERMANENTLY_DENIED`. So a softer status is already produced by a second
path that has nothing to do with "never requested".

`PermissionStatus` (`domain/model/PermissionEntry.kt:7-9`) has exactly four values and no spare slot.

## Where the marker should live

Three existing shapes were compared:

1. `ContextualRationaleRepository` + impl (`domain/repository/ContextualRationaleRepository.kt:1-6`,
   `data/permissions/ContextualRationaleRepositoryImpl.kt:1-26`) - `isShown(permissionId)` /
   `markShown(permissionId)` over `SharedPreferences("perm_rationale_prefs")`, synchronous, already has a Hilt
   binding (`di/PermissionModule.kt:21-23`) and a passing unit test with three cases. It has **zero production
   callers**. Exact structural match for a per-permission-id synchronous boolean.
2. `InstalledSetMarkerStore` (`data/delivery/InstalledSetMarkerStore.kt:25-46`) - DataStore-backed. Every
   DataStore call site in this repository is `suspend`/`Flow`; none offers a synchronous read.
3. `MainStoragePermissionsHelper` (`ui/main/helpers/MainStoragePermissionsHelper.kt:54-63,119-122`) - a third,
   independent precedent for the same idea, but a single global boolean scoped to the startup flow.

Option 2 conflicts with the strategic constraint that status computation stays synchronous and cheap
(section 3.2), because `CheckPermissionStatusUseCase` is called synchronously from `buildRows()` on the main
thread. Option 1 is the fit; its only problem is naming - `isShown`/`markShown` means "the rationale UI was
shown", not "the system request was fired". With no callers to migrate, renaming it is free.

## The two hosts, and a pre-existing divergence

Both hosts implement the same four-branch click routing independently, with no shared helper
(`PermissionsManagementFragment.kt:101-111`, `WelcomePermissionsManager.kt:154-164`), and both carry an
identical `specialGrantPermissions` set (`:42-46` and `:47-51`).

The bulk-request filters differ, and this predates the ticket:

- Welcome includes `PERMANENTLY_DENIED` in the batch on purpose
  (`WelcomePermissionsManager.kt:189-201`), with a comment explaining that a never-requested permission is
  misclassified as permanently denied and that the system still shows the first-time dialog for it. Its
  grant-all CTA visibility uses `status != GRANTED && status != NOT_APPLICABLE` (`:285-296`).
- Settings filters strictly `== DENIED` (`PermissionsManagementFragment.kt:125-129`), and its CTA visibility
  does the same (`:194-197`). A permanently denied entry never re-triggers the CTA there.

Once the new state exists, both filters must be restated in its terms or the divergence widens.

## Indicator pattern to reuse

`StreamSourceAdapter.bindPlayStatus()` (`ui/streams/StreamSourceAdapter.kt:290-306`) already implements the
exact accessibility contract this ticket needs, and says so in its KDoc (`:285-289`): shape and colour both
encode the state, and the content description carries it for TalkBack. Its three 24dp vector drawables are
`ic_stream_status_ok.xml` (filled circle plus check), `ic_stream_status_failed.xml` (filled circle plus
exclamation) and `ic_stream_status_unknown.xml` (hollow ring), tinted at the call site through
`ImageViewCompat.setImageTintList` with `R.color.stream_status_*` (`values/colors.xml:410-412`).

Three shapes exist against four states. `ic_lock.xml` is already in the icon inventory
(`docs/icons/icon-inventory.json:766`) and carries "locked" semantics elsewhere, so it is the near-fit for the
blocked state rather than a fourth invented asset.

`PermissionEntry.iconRes` (`domain/model/PermissionEntry.kt:16`) is a dead per-entry icon slot - every entry
passes `0` and no code reads it. It is not the indicator slot and is not repurposed here.

## Test coverage

Only two permission tests exist: `PermissionRegistryRepositoryImplTest` (registry filtering and gate-name
validity) and `ContextualRationaleRepositoryImplTest` (three cases on the unused store). Nothing covers
`CheckPermissionStatusUseCase`, `PermissionRowAdapter`, either host, or `PermissionDenialHandler`. No Maestro
flow targets either screen - the 28 flows matching "permission" only handle incidental runtime dialogs.

## Out of scope, already ticketed

Orphaned string keys found while reading the `perm_*` set - the VR remnants left by S0241
(`perm_title_hand_tracking`, `perm_desc_hand_tracking`, `perm_title_headset_camera`,
`perm_desc_headset_camera`) and `perm_battery_optim_grant` / `_manage` / `_hint`. These are **not** parked as
new tickets: S1436's section 5.1 already claims exactly this cleanup.
