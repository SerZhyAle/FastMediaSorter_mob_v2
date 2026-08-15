# S0571 - Harden ReceiveShareActivity dialog theming (latent MaterialButton inflate crash)

**Status:** Archived

## 0. Capture (raw evidence)

Source: device crash log `logs/fastmediasorter_crash_20260621_023706.log` (build 2.60.6210.225-NoLegal-DEBUG, Samsung SM-S731B, Android 16 / API 36).

Repro from log: user shared an Instagram link to the app.
- `ReceiveShareActivity.attachBaseContext` -> `UrlInTextDetector: matched 1 url(s)` -> `known social: host=www.instagram.com` -> `AccountSelectionManager: host=www.instagram.com accounts=1` -> `ReceiveShareActivity: enqueue worker url=https://www.instagram.com/p/...` -> FATAL.

Crash (verbatim):
```
android.view.InflateException: Binary XML file line #42 in layout/dialog_link_autodownload_progress: Error inflating class <unknown>
Caused by: java.lang.UnsupportedOperationException: Failed to resolve attribute at index 37:
  TypedValue{t=0x2/d=0x7f0401e7 a=18}, theme=[ThemeOverlay.Material3.Button.TextButton,
  Theme.FastMediaSorter.App, ThemeOverlay.Material3.DynamicColors.Dark,
  Theme.FastMediaSorter.Transparent, Theme.AppCompat.Empty, android:Theme.DeviceDefault.Light.DarkActionBar]
  at com.google.android.material.button.MaterialButton.<init>
  at com.sza.fastmediasorter.ui.share.LinkAutoDownloadProgressDialog.show(LinkAutoDownloadProgressDialog.kt:40)
  at com.sza.fastmediasorter.ui.share.ReceiveShareActivity.processLinkAutoDownload(ReceiveShareActivity.kt:439)
```

## 1. Scope note - the logged crash is already patched

The specific `LinkAutoDownloadProgressDialog` crash is already fixed in the working tree (commit `72b3fd2c`): `show()` now inflates and builds against `ContextThemeWrapper(activity, R.style.Theme_FastMediaSorter_App)`. This ticket is NOT that fix - it covers the residual systemic risk the same investigation surfaced.

## 2. Problem (residual latent crash)

`ReceiveShareActivity` runs under the translucent `Theme.FastMediaSorter.Transparent`. At runtime the AppCompat DayNight delegate flattens that window theme onto a non-Material base and `DynamicColors.applyToActivitiesIfAvailable` re-layers it, so the custom attr `?attr/dialogActionButtonMinHeight` (bound only on `Theme.FastMediaSorter.App`) is dropped from the Activity's effective theme. Any `MaterialButton` styled with `Widget.FastMediaSorter.Button.DialogCancel` (which sets `android:minHeight=?attr/dialogActionButtonMinHeight`) then fails to inflate.

The Activity declares a shared themed context for all its dialogs:
- `ReceiveShareActivity.kt:114` - `private val dialogContext: Context by lazy { ContextThemeWrapper(this, R.style.Theme_FastMediaSorter) }`
- KDoc (`:108-113`) states intent: "Every dialog spawned here .. must inflate against this guaranteed-Material3 context instead."

But the chosen style alias is `Theme.FastMediaSorter`, which is the bare parent-only alias. `LinkAutoDownloadProgressDialog.show()` KDoc (`:34-38`) explicitly warns this alias is unreliable: "the bare Theme.FastMediaSorter alias is parent-only, so applying it would not reliably re-inject the attr". So the shared `dialogContext` carries the very bug the sibling fix worked around.

`dialogContext` feeds three other dialog sites:
- `ReceiveShareActivity.kt:323` - `MaterialAlertDialogBuilder(dialogContext)`
- `ReceiveShareActivity.kt:604` - passed as `context = dialogContext` into a dialog
- `ReceiveShareActivity.kt:717` - `MaterialAlertDialogBuilder(dialogContext)`

Any of these whose view inflates a `Widget.FastMediaSorter.Button.*` referencing the custom attr is exposed to the same `UnsupportedOperationException`/`InflateException` FATAL.

## 3. Proposed direction (to refine on approval)

- Switch the shared `dialogContext` alias to the attr-declaring theme: `ContextThemeWrapper(this, R.style.Theme_FastMediaSorter_App)`.
- Route `LinkAutoDownloadProgressDialog` through the shared `dialogContext` instead of its own private wrapper, so there is a single themed-context source of truth for dialogs spawned by this Activity (pass a `Context` rather than the raw `AppCompatActivity`).
- Audit the layouts inflated at `:323`, `:604`, `:717` for `Widget.FastMediaSorter.Button.*` / custom-attr usage; confirm they resolve under the corrected context.
- Consider whether other transparent/translucent host activities (any `AppCompatActivity` under `Theme.FastMediaSorter.Transparent` that spawns Material dialogs) share this exposure.

## 4. Affected files

- `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/ReceiveShareActivity.kt`
- `app_v2/src/main/java/com/sza/fastmediasorter/ui/share/LinkAutoDownloadProgressDialog.kt`
- `app_v2/src/main/res/values/themes.xml` (reference only - theme/attr definitions)
- `app_v2/src/main/res/values/attrs.xml` (reference only)

## 5. Acceptance (draft)

- No `MaterialButton`/custom-attr inflate failure for any dialog spawned by `ReceiveShareActivity` under the Transparent theme, on API 23..36.
- Single themed-context source of truth for `ReceiveShareActivity` dialogs; alias points at the theme that declares `dialogActionButtonMinHeight`.
- Sharing an Instagram link (and re-running other share dialogs) does not crash.

## 6. Related

- Logged crash already patched in working tree (commit `72b3fd2c`).
- Affects all flavors (root cause in `src/main`); reported on `noLegal`.

## 7. Disposition - Archived as superseded by S0573

Closed without implementation on 2026-06-21. The verification pass that produced this ticket was based on a wrong premise, and the residual risk it described is already fully covered by S0573 (Implemented).

- §2 premise is factually wrong. The shared `dialogContext` uses `R.style.Theme_FastMediaSorter`, which is **not** a parent-only alias: the chain is `Theme.FastMediaSorter` -> `Theme.FastMediaSorter.Base` -> `Theme.FastMediaSorter.App`, so it fully inherits `dialogActionButtonMinHeight` and `materialAlertDialogTheme` from `.App`. The context does not drop the attr. The same "bare alias is parent-only" claim in `LinkAutoDownloadProgressDialog.show()` KDoc is likewise inaccurate.
- §3 primary fix is empirically known-ineffective. S0573 §3 records that wrapping in `Theme.FastMediaSorter.App` did not stop the crash. Switching the alias would fix nothing; worse, the bare alias gains `Theme.FastMediaSorter.Base`'s v35 `bottomSheetDialogTheme`, so moving to `.App` would be a minor regression.
- §3 audit is already done. S0573 §5 swept every `ReceiveShareActivity` dialog site plus `ResourceImportActivity`, `CameraQuickCaptureActivity`, and confirmed `QuickAudioRecorderActivity` / `CameraLaunchActivity` / `StandalonePlayerDispatcherActivity` have no dialog inflate path.
- Root cause is fixed at the source. S0573 rebound `Widget.FastMediaSorter.Button.DialogConfirm/DialogCancel/DialogDestructive` `android:minHeight` directly to `@dimen/dialog_action_button_min_height` (themes.xml), removing the `?attr/dialogActionButtonMinHeight` indirection. No layout references that attr directly, so the inflate-crash class is theme-independent dead regardless of any host context.

Only residual item was a cosmetic single-source-of-truth refactor (route `LinkAutoDownloadProgressDialog` through `dialogContext`), which has no functional effect after S0573 and is not worth a ticket.
