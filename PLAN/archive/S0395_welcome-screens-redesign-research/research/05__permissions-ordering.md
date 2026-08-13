# 05 - Permissions Map and Page Ordering

Strategic item: S0395 §6.5. Phase: 03, step 03.1.

## Question

Is "permissions (page 3) before functionality (page 4)" correct, given that file-manager mode needs all-files access and downloads may need notifications?

## Sources

- `app_v2/src/main/java/com/sza/fastmediasorter/data/permissions/PermissionRegistryRepositoryImpl.kt` (via Phase-01 report)
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/settings/fragments/PermissionsManagementFragment.kt` (grant-all run, special-permission walk)
- `app_v2/src/main/java/com/sza/fastmediasorter/core/util/PermissionHelper.kt`, `ui/main/helpers/MainStoragePermissionsHelper.kt`, `ui/browse/managers/BrowseLifecycleHelper.kt` (via artifact 06)
- `app_v2/src/main/AndroidManifest.xml` (MANAGE_EXTERNAL_STORAGE line 14, POST_NOTIFICATIONS line 53 - declared in main, inherited by all flavors)
- `research/03__page1-device-profiles.md` (preset → all-files implication), `research/06__page4-functionality-toggles.md` (lazy permission pattern)
- Play policy: [All files access permitted uses](https://support.google.com/googleplay/android-developer/answer/10467955), [Manage all files guidance](https://developer.android.com/training/data-storage/manage-all-files)

## Findings

### Permission map per API tier

- API 23-29 (legacy floor 23): runtime READ_EXTERNAL_STORAGE (+WRITE on the write paths); no all-files special permission (pre-30 the READ/WRITE pair is the "all files" equivalent).
- API 30-32: READ_EXTERNAL_STORAGE (still the media-read path) + MANAGE_EXTERNAL_STORAGE as a SPECIAL permission - grantable only on the system Settings screen (`ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`), no runtime dialog; MANAGE_MEDIA (31+) special.
- API 33+: READ_MEDIA_IMAGES/VIDEO/AUDIO replace storage read; POST_NOTIFICATIONS becomes a runtime permission; all-files special unchanged.
- Optional registry entries: CAMERA, RECORD_AUDIO (gated SUPPORT_AUDIO), battery-optimization exemption (special), POST_NOTIFICATIONS (currently gated by ENABLE_PERSISTENT_AUDIO_PLAYBACK).

### How the welcome permission step works today

- The grant-all run requests the regular batch in ONE system dialog sequence, then walks SPECIAL permissions one-by-one through system screens (state survives config change). MANAGE_EXTERNAL_STORAGE sits in the required-storage registry group on 30+, so today's welcome already routes every user through the all-files system screen regardless of any file-manager intent.
- The `allFiles` SETTING is permission-free at flip time: the special permission is requested lazily (once per session at main screen; again at Browse of a local resource). Toggling file-manager mode on page 4 therefore needs NO permission at the moment of toggling - today's app already works this way.
- Profile presets (page 1) can imply all-files (`profileImpliesAllFilesUseCase` for personal smartphone / home tablet) - a permission-relevant choice already happens BEFORE the permission step in both the current and the target order.

### Play policy

- All-files access is policy-permitted for file managers (core functionality includes file/folder management) but requires the Permissions Declaration Form and Play approval. The permission is already declared in the main manifest (`tools:ignore="ScopedStorage"`) and shipped - the declaration burden exists today and is not changed by the redesign; the welcome toggle only adds a UX entry point to an existing setting.

### The supposed ordering conflict, re-examined

- File-manager toggle: NO conflict in the lazy model - the toggle persists a setting; the special permission arrives at first real use (or via the page-3 grant-all walk, which today includes it unconditionally).
- Download notifications: POST_NOTIFICATIONS is declared and present in the optional registry batch; downloads themselves need NO storage permission (network → app-private storage, artifact 06). If onboarding downloads show notification progress on 33+, the permission is naturally part of the page-3 batch in the owner order.
- The real ordering cost of the owner order (3 before 4) is missed ADAPTIVITY, not a broken flow: page 3 cannot tailor its batch to choices made on page 4 (skip RECORD_AUDIO when audio off; include the all-files system screen only when file-manager mode chosen; request POST_NOTIFICATIONS only when a download is pending or audio persistence matters).

## Options

- A. Owner order (permissions 3 → functionality 4). Grant-all stays unconditional (today's behavior). Simple; zero changes to the shared fragment; users see the all-files system screen even when they never enable file-manager mode; downloads start only on page 4+ with notification permission already settled.
- B. Swap (functionality 3 → permissions 4). Permission step tailors its batch to the toggles: all-files screen only for file-manager users (or profile-implied), RECORD_AUDIO only when audio on, POST_NOTIFICATIONS when downloads were queued. Downloads enqueue one page earlier and run while the user handles permissions. Requires parameterizing the shared permissions fragment (Settings entry keeps the full list).
- C. Owner order + staged re-request: keep 3 → 4, and after page 4 run a micro-step requesting only what the toggles newly require (all-files screen if file-manager just enabled). Adds a second permission moment - more friction, no clear win over B.

## Conclusion

The owner's draft order does not break anything - the lazy all-files pattern absorbs it - but option B (functionality before permissions) is strictly better UX: fewer scary system screens for non-file-manager users, an adaptive batch, and downloads pipelining one page earlier; its cost is a parameterized permission list in the shared fragment. Recommended target order: 0 language+theme, 1 device profile, 2 networks, 3 functionality, 4 permissions, 5 default app. The default-app page benefits from following permissions (its sample-file chooser works once media read is granted). Per-API notes: legacy 23-25 has no special all-files screen (runtime pair instead) and no notification permission; 33+ adds POST_NOTIFICATIONS to the batch. This is a deviation from the owner draft and goes to SYNTHESIS as a flagged decision with B recommended.

## Impact on recommendation

- Recommended page order swaps owner pages 3 and 4; SYNTHESIS must present this as deviation #1 with the adaptivity rationale.
- Permissions dev ticket scope: parameterized permission set for the welcome mode of the shared fragment (Settings mode unchanged), conversion of the overlay into a real pager page (page indicator stays visible), landscape layout for the permissions page (currently absent - Rule 11).
- The all-files Play declaration is an existing operational fact - no new policy exposure from the toggle itself.
