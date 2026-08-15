# S1337 - The permission rationale sheet has no reachable entry point

**Status:** Archived
**Priority:** 45

<!-- discovered by /spec-next S1331 phase 05 - 2026-07-31, parked per CLAUDE.md 3.1 -->

## 0. Raw capture

Found while converting `PermissionRationaleBottomSheet` to the `FragmentResult` API for S1331. The
conversion is correct and compiles, but the sheet cannot be opened by any user action.

The producer chain, walked bottom-up from the only `newInstance` call site:

- `PermissionRationaleBottomSheet.newInstance(..)` is called from exactly one place:
  `domain/usecase/RequestContextualPermissionUseCase.kt:24`.
- `RequestContextualPermissionUseCase.invoke(..)` is called from exactly one place:
  `ui/settings/helpers/GeneralSettingsPermissionsHelper.kt:45`, inside `handleNetworkPermissionAction()`.
- `handleNetworkPermissionAction()` has **zero callers** anywhere in `app_v2/src`.

So the whole chain is dead. The sheet ships in the APK and can never appear.

Two neighbours in the same helper are dead the same way:

- `handleLocalFilesPermissionAction()` - zero callers.
- `updatePermissionButtonsState()` - body is literally `= Unit`, yet three call sites in
  `GeneralSettingsFragment` still call it. The comment above it says the permission buttons were removed
  and the section replaced by `btnPermissionsManagement`, which is the likely moment the entry points
  were dropped without removing what they fed.

`registerRationaleListener()` **is** called, from `GeneralSettingsFragment.kt:253`, so the receiving half
of the flow is live and armed. Only the trigger is missing.

## 1. Why this is its own ticket

S1331's scope is "a dialog must not lose its result callback across a host recreation". Whether that
dialog can be opened at all is a separate question, and answering it needs a product decision this
ticket cannot make: does the network-permission rationale still belong in the settings flow, or was
removing its button the intended outcome and the sheet is simply leftover?

It also changes what S1331's device test can claim. Four of its five conversions are verifiable on a
device; this one is not reachable, so its device test will be recorded as not-executable rather than
passing.

## 2. Scope sketch (to be settled at Approval)

- Whether the rationale sheet is wanted at all. If it is, which control opens it - the permissions
  management entry (`btnPermissionsManagement`), a per-permission row inside
  `PermissionsManagementFragment`, or the point where a network resource actually needs the permission.
- If it is not wanted: delete `PermissionRationaleBottomSheet`, `RequestContextualPermissionUseCase`,
  `handleNetworkPermissionAction`, `handleLocalFilesPermissionAction`, `registerRationaleListener` and
  the associated strings and layout, per Rule 20 dead-weight hygiene.
- `updatePermissionButtonsState()` is decided either way: a no-op called three times is dead weight and
  goes with whichever branch is taken.
- Whether contextual rationale should instead be shown at the point of need rather than in settings,
  which is where the "contextual" in `RequestContextualPermissionUseCase` points.

## 3. Related

- **S1331** - the conversion that surfaced it; phase 05 converted this sheet's callback and its plan
  records the sheet as untestable for that reason.
- **S1335** - `read-contacts-permission-plumbing`. Adjacent, not a duplicate: S1335 adds a new permission
  to the management surfaces, this ticket is about an existing rationale surface having no trigger.
  Whichever lands second inherits the other's answer about where rationale belongs.

### 3.3 Owner inputs (Approval gate)

- **Related tickets:** S1331, S1335 as described in §3.
- **Scope:** `ui/common/permissions/PermissionRationaleBottomSheet.kt`,
  `domain/usecase/RequestContextualPermissionUseCase.kt`,
  `ui/settings/helpers/GeneralSettingsPermissionsHelper.kt`,
  `ui/settings/fragments/GeneralSettingsFragment.kt`. User-visible either way - a surface appears or a
  dead one is removed.
- **Flavors:** all - every file is in `src/main` and carries no flavor gate.
- **Localization:** the removal branch retires string keys; the revive branch adds none.

---

## 4. Verification

- Reach the rationale sheet from a fresh install by a documented sequence of taps, or confirm no code
  path can construct it and the class is gone.
- `Grep` - `handleNetworkPermissionAction` and `handleLocalFilesPermissionAction` either have a caller or
  do not exist.

---

## Goal

Удаляем мёртвую цепочку `PermissionRationaleBottomSheet`, а не возрождаем её. Причина - на любом
поставляемом устройстве запись `access_local_network` в реестре разрешений имеет `minSdk = 37`, которого
не существует ни на одном устройстве при текущем `compileSdk/targetSdk = 36`, так что кнопка действия для
неё скрыта в любом случае, вызывать шторку было бы не для чего. Более того, контекстное объяснение этого
разрешения уже показывается пользователю двумя независимыми живыми путями прямо в точке необходимости
(`BrowseEventHandler`, `AddResourceConnectionManager`) - шторка в настройках была бы третьим, избыточным
поверхностным слоем для того же разрешения. При удалении обнаружился каскад: после удаления четырёх
мёртвых методов у `GeneralSettingsPermissionsHelper` не остаётся ни одного живого метода - весь класс,
его поле в `GeneralSettingsFragment`, его проходной (тоже неиспользуемый) параметр в
`GeneralSettingsViewSetupHelper` и два `ActivityResultLauncher` в `GeneralSettingsFragment`, которые
дёргали только этот класс, удаляются в этом же изменении - оставлять пустую оболочку класса или
осиротевшие параметры конструктора прямо в строках, которые уже редактируются, было бы хуже, чем довести
чистку до конца.

## Phase 1 - Delete the unreachable rationale chain and everything it leaves dead

- [x] Delete `app_v2/src/main/java/com/sza/fastmediasorter/ui/common/permissions/PermissionRationaleBottomSheet.kt`.
- [x] Delete `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/RequestContextualPermissionUseCase.kt`.
- [x] Delete `app_v2/src/main/java/com/sza/fastmediasorter/domain/usecase/MarkContextualShownUseCase.kt`
  (used only by the two files above).
- [x] Delete `app_v2/src/main/res/layout/bottom_sheet_permission_rationale.xml`.
- [x] Delete `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsPermissionsHelper.kt`
  in full - once `handleNetworkPermissionAction`, `handleLocalFilesPermissionAction`,
  `registerRationaleListener` and the no-op `updatePermissionButtonsState` are gone, its remaining two
  methods (`handlePermissionPermanentlyDenied`, `navigateToPermissionsManagement`) lose their only callers
  too, leaving zero live methods in the class.
  - **Verification:** `Glob` - none of the 4 files above exist.
- [x] `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/GeneralSettingsFragment.kt` -
  remove the `GeneralSettingsPermissionsHelper` import and its `by lazy` property; remove the
  `@Inject lateinit var requestContextualPermission`/`permissionRegistry` fields (used only to build that
  helper); remove the `mediaPermissionsLauncher`/`notificationPermissionLauncher` `registerForActivityResult`
  blocks (their only callback action was the no-op `updatePermissionButtonsState()`, and neither is ever
  `.launch()`-ed); remove the `registerRationaleListener()` call in `onViewCreated` and its comment; remove
  the `updatePermissionButtonsState()` call in `onResume`; drop the now-removed `permissionsHelper` argument
  from the `GeneralSettingsViewSetupHelper(...)` constructor call.
  - **Verification:** `Grep "permissionsHelper\|GeneralSettingsPermissionsHelper\|requestContextualPermission\|permissionRegistry\|mediaPermissionsLauncher\|notificationPermissionLauncher"` returns zero hits in this file.
- [x] `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/helpers/GeneralSettingsViewSetupHelper.kt` -
  remove the `permissionsHelper: GeneralSettingsPermissionsHelper` constructor parameter (never read in
  the class body) and its import.
  - **Verification:** `Grep "permissionsHelper\|GeneralSettingsPermissionsHelper"` returns zero hits in this file.
- **Verification:** `standard debug` compiles clean. No device test needed - this is a pure deletion with
  a static, not on-device, verification contract (§4: the class is gone, not that a UI path reaches it).

**Notes from implementation:**

- Deleting `GeneralSettingsPermissionsHelper.registerRationaleListener()` removed the
  `Timber.d("S1331: permission rationale result received")` line inside it - S1331's own
  `BlockNeedUserTest` note already excluded this exact dialog from its testable checklist ("NOT
  testable: the permission rationale sheet has no reachable entry point .. parked as S1337"), so this
  is the expected removal of a probe for code that no longer exists, not a premature strip of a
  still-needed S1331 tag.
- `GeneralSettingsViewSetupHelper.kt`'s constructor dropped from 13 to 12 parameters (removing the now
  dead `permissionsHelper` param), which is still over detekt's `LongParameterList` threshold of 10 -
  pre-existing debt this ticket only reduced, not introduced. Re-baselined via `detektBaseline` rather
  than forcing a class decomposition inline; parked as **S1351** for the real fix.

## Last Audit

**Date:** 2026-08-02
**Mode:** strategic (compact spec, Simple path)
**Flags:** -
**Outcome:** Verified
**Counts:** PASS 10 · WARN 0 · FAIL 0 · MANUAL 0 · EXEMPT 0

Two independent adversarial verification rounds (3 lenses, then 2 more for a cascade discovered
mid-implementation) unanimously found zero refutation before any file was touched. All 5 target
files confirmed deleted (`Glob`); zero residual references to any of the 7 removed symbols
(`PermissionRationaleBottomSheet`, `RequestContextualPermissionUseCase`,
`MarkContextualShownUseCase`, `handleNetworkPermissionAction`, `handleLocalFilesPermissionAction`,
`registerRationaleListener`, `updatePermissionButtonsState`) anywhere in `app_v2/src`; zero orphaned
string keys (both layout string refs used elsewhere); zero test breakage (none referenced the deleted
symbols). `standard debug` compiles and packages clean (full `assembleStandardDebug`, Hilt graph
included). `post-change.ps1 -ScopeToFile` PASS on both edited files. Catalog synced (2402 -> 2398
records, matching the 5 deleted classes/composables). Pre-existing `LongParameterList` debt on
`GeneralSettingsViewSetupHelper` (13 -> 12 params after this ticket's own reduction, still over
threshold) re-baselined and parked as S1351 rather than fixed inline - out of this ticket's contract.
No debug-tag probe needed or inserted - the verification contract is static (class is gone), not
on-device.

### Manual / on-device

- None. Verification contract is purely static per §4 ("confirm no code path can construct it and the
  class is gone").
