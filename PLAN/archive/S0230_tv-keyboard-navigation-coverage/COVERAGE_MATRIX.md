# S0230 Coverage Matrix

Static-grep + manual review of every Activity, DialogFragment, and dialog-helper call site against the 8 input modalities (touch, mouse, keyboard, D-pad, gamepad, car/media, hardware, a11y). Produced by `/spec-dev` Phase 01, 2026-05-17.

> Sources: `Grep` over `app_v2/src/main/java/**/*.kt` for `class \w+Activity`, `class \w+DialogFragment`; `dev/CATALOG/app_v2.md` for class-role lookup; manual inspection of `dispatchKeyEvent`, `onKeyDown`, `onTouchEvent` overrides.

---

## Surfaces

**Activities (17 total — 15 main + 2 vr flavor):**

1. `WelcomeActivity` — onboarding slider (overrides `onTvNavigation` + `getInitialFocusView`).
2. `MainActivity` — main hub with gamepad integration; tabbed fragments inside.
3. `BrowseActivity` — file browse with gamepad integration (Browser surface) + `BrowseDialogHelper`.
4. `AddResourceActivity` — manual resource entry form.
5. `ReceiveShareActivity` — share-intent handler; no UI dialog beyond Toast.
6. `SettingsActivity` — settings host (Fragment-based).
7. `OneDriveFolderPickerActivity` — OneDrive cloud folder picker (RecyclerView).
8. `DropboxFolderPickerActivity` — Dropbox cloud folder picker (RecyclerView).
9. `GoogleDriveFolderPickerActivity` — Drive cloud folder picker (RecyclerView).
10. `ResourceEditorActivity` — resource property editor form.
11. `StandalonePlayerActivity` — standalone video/audio player; own `onKeyDown` for media.
12. `AuthSessionsActivity` — auth session manager (list).
13. `DuplicatesActivity` — duplicates list (RecyclerView).
14. `KeybindingRemapActivity` — keybinding capture / remap UI.
15. `ResourceLaunchWidgetConfigActivity` — widget configuration.
16. `PlayerActivity` — main media player; gamepad integration (Player surface) + own `dispatchKeyEvent` + `onKeyDown` + `keyboardHandler`.
17. `VrPlayerActivity` (vr flavor) — VR media player; own `dispatchKeyEvent`.
18. `VrPhoneFallbackActivity` (vr flavor) — VR fallback splash.

**DialogFragments (5):**

19. `WebViewAuthDialogFragment` — OAuth web view.
20. `InputHelpDialogFragment` — input help / keybinding reference.
21. `PlaybackControlDialogFragment` — playback controls bottom sheet.
22. `CaptureDialogFragment` — keybinding capture flow.
23. `SlideshowSettingsDialogFragment` — slideshow timing settings.

**Dialog helpers (3 — each represents multiple dialog call sites):**

24. `BrowseDialogHelper` — filter, sort, rename, copy, move, delete, cloud-auth (≈8 dialog show sites; 689 LOC).
25. `PlayerDialogHelper` — player context dialogs (659 LOC).
26. `ErrorDialogHelper` — generic error dialog (67 LOC, single show site).

**Ad-hoc dialog show sites in helpers (4 — found via `: AppCompatActivity` grep co-incidence on helpers showing dialogs):**

27. `BrowseBinaryFileMenuAction` — binary file action menu.
28. `ImageCropManager` — image-crop UI dialog.
29. `AccountSelectionManager` — account picker.
30. `MainChromeOsBannerManager` — ChromeOS hint banner.
31. `MainStoragePermissionsHelper` — permission rationale dialog.
32. `MainResumePlaybackHelper` — resume playback prompt.
33. `LinkAutoDownloadProgressDialog` — share-flow progress dialog.

**Total: 33 surfaces** (Activities + DialogFragments + helper dialog clusters).

---

## Modality default rules

These defaults apply to every surface inheriting `BaseActivity` (or, for DialogFragments, hosted in such an Activity). Per-surface overrides below the rules table.

- **touch:** `handled` natively by Android framework. Custom touch overrides exist only on 6 files (audited in §Phase 03 audit results).
- **mouse:** `pass-through` — Android translates `SOURCE_MOUSE` into touch for standard Views. `gap` only where a custom touch handler drops `super.onTouchEvent()` (see §Phase 03 audit results).
- **keyboard:** `pass-through` — `BaseActivity.dispatchKeyEvent` routes via `TvKeyRouter`; default `onTvNavigation` returns `false` → falls through to AppCompat → focus traversal + screen-specific `onKeyDown` (where overridden).
- **D-pad:** same as keyboard (handled by the same router; non-gamepad sources).
- **gamepad:** `pass-through` (i.e. no action) for most surfaces; `handled` on 4 Activities with `GamepadInputManager` integration (Player, Standalone, Browse, Main).
- **car/media:** `pass-through` — `TvKeyRouter` translates `MEDIA_*`/`HEADSETHOOK` into `TvNavAction.Media`; default `onTvNavigation` returns `false` → AppCompat → system MediaSession.
- **hardware:** `pass-through` — `TvKeyRouter` translates `VOLUME_*`/`MENU`/`SEARCH` into `TvNavAction.Hardware`; default consumer returns `false` → AudioManager / system menu handler.
- **a11y:** `gap` for **every** surface until explicitly audited. No surface in the codebase currently overrides `onInitializeAccessibilityNodeInfo`, declares a `View.AccessibilityDelegate`, or routes through `ViewCompat.addAccessibilityAction` — so the default Android `contentDescription` machinery is the only a11y signal. 362 `contentDescription` occurrences across 77 layout files (good baseline); audit completion requires per-screen Voice Access / TalkBack walkthroughs (deferred to device test).

---

## Coverage matrix (compact: exceptions to the default rules)

Every row below records the **override** vs the default rule. Empty (omitted) cells mean "matches default rule above".

| # | Surface | touch | mouse | keyboard | D-pad | gamepad | car/media | hardware | a11y |
|--:|---------|:-----:|:-----:|:--------:|:-----:|:-------:|:---------:|:--------:|:----:|
| 1 | `WelcomeActivity` | | | handled | handled | n/a | | | gap |
| 2 | `MainActivity` | | | | | handled | | | gap |
| 3 | `BrowseActivity` | | | | | handled | | | gap |
| 4 | `AddResourceActivity` | | | | | | | | gap |
| 5 | `ReceiveShareActivity` | n/a | n/a | n/a | n/a | n/a | n/a | n/a | n/a |
| 6 | `SettingsActivity` | | | | | | | | gap |
| 7 | `OneDriveFolderPickerActivity` | | | | | | | | gap |
| 8 | `DropboxFolderPickerActivity` | | | | | | | | gap |
| 9 | `GoogleDriveFolderPickerActivity` | | | | | | | | gap |
| 10 | `ResourceEditorActivity` | | | | | | | | gap |
| 11 | `StandalonePlayerActivity` | | | handled | handled | | gap | | gap |
| 12 | `AuthSessionsActivity` | | | | | | | | gap |
| 13 | `DuplicatesActivity` | | | | | | | | gap |
| 14 | `KeybindingRemapActivity` | | | handled | handled | | | | gap |
| 15 | `ResourceLaunchWidgetConfigActivity` | | | | | | | | gap |
| 16 | `PlayerActivity` | | | handled | handled | handled | gap | | gap |
| 17 | `VrPlayerActivity` (vr) | | | handled | handled | handled | gap | | gap |
| 18 | `VrPhoneFallbackActivity` (vr) | | | | | | | | gap |
| 19 | `WebViewAuthDialogFragment` | | | | | | | | gap |
| 20 | `InputHelpDialogFragment` | | | | | | | | gap |
| 21 | `PlaybackControlDialogFragment` | | | | | | | | gap |
| 22 | `CaptureDialogFragment` | | | handled | handled | | | | gap |
| 23 | `SlideshowSettingsDialogFragment` | | | | | | | | gap |
| 24 | `BrowseDialogHelper` (8 sites) | | | | | | | | gap |
| 25 | `PlayerDialogHelper` | | | | | | | | gap |
| 26 | `ErrorDialogHelper` | | | | | | | | gap |
| 27 | `BrowseBinaryFileMenuAction` | | | | | | | | gap |
| 28 | `ImageCropManager` | gap | gap | | | | | | gap |
| 29 | `AccountSelectionManager` | | | | | | | | gap |
| 30 | `MainChromeOsBannerManager` | | | | | | | | gap |
| 31 | `MainStoragePermissionsHelper` | | | | | | | | gap |
| 32 | `MainResumePlaybackHelper` | | | | | | | | gap |
| 33 | `LinkAutoDownloadProgressDialog` | | | | | | | | gap |

**Footnotes:**

- Row 1 `WelcomeActivity` keyboard/D-pad — handled at `WelcomeActivity.kt:519` (`onTvNavigation` override), initial focus at `:556` (`getInitialFocusView`).
- Rows 2, 3, 16, 17 gamepad — handled via `GamepadInputManager.handleKeyEvent` / `handleMotionEvent` per surface enum (`MainActivity` Browser, `BrowseActivity` Browser, `PlayerActivity` Player, `VrPlayerActivity` Player).
- Row 5 `ReceiveShareActivity` — `n/a` across all modalities: no UI surface beyond an Intent-driven Toast; no focusable elements.
- Rows 11, 16, 17 keyboard/D-pad — handled via screen-specific `onKeyDown` / `dispatchKeyEvent` (own `keyboardHandler` for media seek; D-pad reaches it through the fall-through chain).
- Rows 11, 16, 17 car/media — `gap`: router emits `TvNavAction.Media.*` but Players don't override `onTvNavigation` to consume them; events pass through to system MediaSession (which may or may not own the playback). Follow-up spec planned.
- Row 22 `CaptureDialogFragment` — handles raw KeyEvents directly for keybinding capture (by design; not routed through `TvKeyRouter`).
- Row 28 `ImageCropManager` — depends on `CropOverlayView.onTouchEvent` (custom touch override; mouse pass-through unverified — flagged for Phase 03).

---

## Phase 02 work — list-screen focus polish

**Step 02.1 audit results (2026-05-17):**

| Activity | Layout | RV direct? | Landscape variant? | `getInitialFocusView` viable? |
|----------|--------|:----------:|:------------------:|:-----------------------------:|
| `BrowseActivity` | `activity_browse.xml` | yes | yes (`layout-land/activity_browse.xml`) | yes |
| `OneDriveFolderPickerActivity` | `activity_onedrive_folder_picker.xml` | yes | no | yes |
| `DropboxFolderPickerActivity` | `activity_dropbox_folder_picker.xml` | yes | no | yes |
| `GoogleDriveFolderPickerActivity` | `activity_google_drive_folder_picker.xml` | yes | no | yes |
| `DuplicatesActivity` | `activity_duplicates.xml` (hosts `fragment_duplicates.xml`) | no — RV in fragment | n/a | **no** — defer to fragment-level focus |
| `AuthSessionsActivity` | `activity_auth_sessions.xml` (hosts `fragment_auth_sessions_list.xml`) | no — RV in fragment | n/a | **no** — defer to fragment-level focus |

**Phase 02 actionable scope:**

- `activity_browse.xml` + `layout-land/activity_browse.xml` — apply `descendantFocusability="afterDescendants"` to primary RV. `BrowseActivity.getInitialFocusView() = binding.<rvId>`.
- `activity_onedrive_folder_picker.xml`, `activity_dropbox_folder_picker.xml`, `activity_google_drive_folder_picker.xml` — apply attribute (portrait-only, landscape variants absent — note in step body). Three matching Activity overrides.

`DuplicatesActivity` + `AuthSessionsActivity` are deferred — both host their list inside a fragment via `FrameLayout`; `getInitialFocusView()` on the Activity cannot reach the inner RecyclerView at the time it is called (fragment may not be attached yet). Fragment-level initial-focus pattern (`view.requestFocus()` in `onViewCreated`) is out of scope here; tracked as Phase 02 follow-up.

`MainActivity` and `SettingsActivity` are fragment-host shells — initial focus delegated to inner fragments (out of scope here). `AddResourceActivity`, `ResourceEditorActivity`, `KeybindingRemapActivity`, `ResourceLaunchWidgetConfigActivity`, `ReceiveShareActivity` are form/dialog screens — no list-focus issue.

## Phase 03 work — mouse safety

Custom touch overrides outside `BaseActivity` (BaseActivity calls super, OK):

- `ImageDrawOverlayManager.kt` — custom draw overlay (verify super.onTouchEvent + performClick).
- `CropOverlayView.kt` — crop UI (verify super.onTouchEvent + performClick).
- `VerticalSeekBar.kt` — custom vertical SeekBar for volume (verify mouse drag works).
- `PrefetchOverlayView.kt` — prefetch indicator (likely view-only, no click — verify).
- `TranslationOverlayView.kt` — translation overlay (verify super.onTouchEvent + performClick).

Five files. Each gets a code-review pass per Phase 03 step 03.1.

## Phase 04 work — dialog TalkBack helper

Every show site in the three DialogHelpers + standalone helpers:

- `BrowseDialogHelper` (8 show sites).
- `PlayerDialogHelper` (multiple show sites).
- `ErrorDialogHelper` (1 show site).
- `BrowseBinaryFileMenuAction` (1 ad-hoc menu).
- `ImageCropManager` (crop dialog).
- `AccountSelectionManager` (account picker).
- `MainChromeOsBannerManager` (banner — may be a snackbar, not dialog; verify).
- `MainStoragePermissionsHelper` (permission rationale).
- `MainResumePlaybackHelper` (resume prompt).
- `LinkAutoDownloadProgressDialog` (share progress).

Plus the 5 DialogFragments — adopt the same helper inside their `onCreateDialog` / `onCreateView` flow.

The `DialogAccessibilityHelper` (new class in Phase 04 step 04.1) wraps the focus-event posting; integration is the same per show site.

## Phase 05 work — accessibility content

Per-surface content-description audit + accessibility actions. Driven by Voice Access / TalkBack walkthrough (device-test gate). Phase 05 covers the static-grep slice:

- Find clickable `ImageView` / `ImageButton` without `contentDescription` — `Grep -L 'contentDescription' <files with ImageView clickable="true">`.
- Find Activities with custom long-press / swipe handlers without `ViewCompat.addAccessibilityAction` — Phase 05 step 05.3 codifies the fix.

Concrete known gaps (priority — visible interactive elements):

- `activity_welcome.xml` icon (the small picture-frame icon next to "Welcome to FastMediaSorter VR!") — likely decorative; mark `importantForAccessibility="no"`.
- All `item_*.xml` row icons (file-type icons, folder icons) — currently `contentDescription="..."` already declared 362 times across 77 files; spot-check items without it.
- Dialog `ImageView` icons in `dialog_*.xml` files — `Grep` shows several dialog layouts use icons; mark decoratives.

Plus per-surface content audit during device-test (Voice Access "Show numbers" → walk every screen).

