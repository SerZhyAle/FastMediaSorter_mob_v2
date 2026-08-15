# Research 01 - Sequencing the default-player system dialogs

Resolves strategic §6 item 1.

## Question

How can the enable-all orchestrator reliably know that the user closed the default-player
"Open with / Always" dialog for one media type, so it can open the dialog for the next type one at a
time, given the current code launches that dialog fire-and-forget?

## Findings

- `DefaultPlayerHelper.openChooserOrFallbackFromActivity(activity, mimeType)` launches the system
  picker via `activity.startActivity(...)` - no result callback, so there is no signal to advance.
- `RoleManager` exposes no requestable role for generic media `ACTION_VIEW` defaults (its roles are
  browser, dialer, sms, home, assistant, call-screening, etc.). There is no `ROLE_MUSIC`/`ROLE_VIDEO`
  to request. So the role-request approach is not applicable to "default player for all media types".
- The permissions page already solves the same shape of problem: `WelcomePermissionsManager` wraps each
  special-permission settings screen in an `ActivityResultLauncher<Intent>`
  (`ActivityResultContracts.StartActivityForResult`) registered on the host Activity. Its result
  callback fires when the user returns from the screen, and the manager then launches the next pending
  item. The run state (`grantAllInProgress`, `shownSpecialInRun`) survives rotation via
  `onSaveInstanceState`/`onRestoreInstanceState`.

## Decision

Adopt the same `ActivityResultLauncher<Intent>` pattern for the default-player sequence.

- Add a launch-for-result variant alongside `openChooserOrFallbackFromActivity` that launches the
  resolved chooser / fallback `Intent` through a caller-supplied `ActivityResultLauncher<Intent>`
  instead of `startActivity`. The existing sample-file / probe / fallback resolution is reused
  unchanged.
- The orchestrator registers one `ActivityResultLauncher<Intent>` on the Activity. For each applicable
  MIME type (gated by `MediaCapabilities`) it enables the player aliases, then launches that type's
  intent through the launcher. The result callback (user returned) advances to the next type. When the
  list is exhausted, the orchestrator proceeds to completion.
- The current type index and an in-progress flag are saved in the Activity's `onSaveInstanceState` and
  restored on recreation, mirroring the permissions run. A rotation mid-sequence resumes at the same
  type rather than restarting.

The stock-Android result code is always `RESULT_CANCELED` for a chooser, so the result code is ignored;
the callback is used only as the "user returned, advance" signal. This is exactly how the permissions
special-settings walk already behaves.

## Addendum - device-test revision (2026-06-13)

Device test surfaced that the reused sample/probe/fallback resolution in `DefaultPlayerHelper` was
itself broken, independent of the sequencing.

- The sample-file branch fired a bare `ACTION_VIEW`, which on a type that already has a foreign default
  silently opens that app (observed: audio opened the system file explorer, never a picker).
- The no-sample branch fired `Intent.createChooser` over a 1-byte probe whose `content://` URI resolves
  only to this app's own just-enabled alias; on Android 8.1 a single-target chooser auto-launches, so
  the app opened its own probe and the standalone host short-circuited it to `finish()` - the user saw
  nothing (observed: documents).
- `Intent.createChooser` never renders an "Always" button, so the probe-chooser path could not register
  a default even when it did show.

Revised resolution (hybrid): a real sample of the type AND no foreign default already set -> bare
`ACTION_VIEW` so the OS may present its native "Open with / Always" sheet; otherwise route to the system
default-apps screen with a toast instruction. The app no longer launches its own probe. The probe
producer in `DefaultPlayerHelper` is removed; the `DefaultPlayerProbe` guards in the standalone hosts
are kept as cheap defence against any leftover probe file from an earlier install.

## Implications for phases

- A seam on `DefaultPlayerHelper` (launch-for-result variant) is a prerequisite of the orchestrator.
- The orchestrator owns the launcher + the sequence state and the Activity delegates
  save/restore to it, the same way it already delegates to `WelcomePermissionsManager`.
