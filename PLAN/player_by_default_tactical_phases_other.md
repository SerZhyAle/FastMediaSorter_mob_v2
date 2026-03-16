# Tactical Plan: Phases 2–7 — Intent Filters, Settings & Background Setup

This document breaks down the remaining phases of the "Default Player" implementation into incremental, buildable micro-steps. Each step serves as a self-contained prompt for an AI agent or developer.

---

## 📅 Phase 2: Intent Filters & Activity Aliases
**Objective**: Safe and modular registration of the activity in the Android system ecosystem.

### 🛠️ Step 2.1: Activity Alias declarations Setup
> **Prompt**:
> 1. In `AndroidManifest.xml`, create **four** `<activity-alias>` tags targeting `.ui.player.StandalonePlayerActivity`.
> 2. Assign names: `.StandaloneAudioPlayer`, `.StandaloneVideoPlayer`, `.StandaloneImagePlayer`, `.StandaloneDocsPlayer`.
> 3. Set `android:enabled="false"` on all alias nodes by default (to prevent interfering unless toggled on by settings later).
> 4. Assign `android:exported="true"`.
> 5. **Verification**: Compile ensuring manifest XML doesn't contain alias duplicates.

### 🛠️ Step 2.2: Audio & Video Filters Mapping
> **Prompt**:
> 1. To `.StandaloneAudioPlayer`, add an `<intent-filter>` with `<action android:name="android.intent.action.VIEW" />`, `<category android:name="android.intent.category.DEFAULT" />`, and `<category android:name="android.intent.category.BROWSABLE" />`.
> 2. Add `<data>` elements for each of the following audio MIME types: `audio/mpeg`, `audio/flac`, `audio/aac`, `audio/ogg`, `audio/mp4`, `audio/x-ms-wma`, `audio/opus`, `audio/3gpp`, `audio/*`.
> 3. Add both `android:scheme="content"` and `android:scheme="file"` to the data elements.
> 4. Repeat the same pattern for `.StandaloneVideoPlayer` with video MIME types: `video/mp4`, `video/x-matroska`, `video/avi`, `video/quicktime`, `video/webm`, `video/*`.
> 5. **Verification**: Run `.\.scripts\builders\build-debug.PS1`. Open a `.mp3` and `.mp4` file from a file manager — both should offer FastMediaSorter in the chooser.
> 6. **Logging**: `.\.scripts\add_to_dev_log.ps1 "app_v2/src/main/AndroidManifest.xml" "AndroidManifest" "Phase 2 Step 2.2: audio/video intent filters added"`

### 🛠️ Step 2.3: Image & Documents Filters Integration
> **Prompt**:
> 1. Add to `.StandaloneImagePlayer`: MIME types `image/jpeg`, `image/png`, `image/webp`, `image/heic`, `image/heif`, `image/bmp`, `image/avif`, `image/*`. Both schemes.
> 2. Add to `.StandaloneDocsPlayer`: MIME types `application/pdf`, `application/epub+zip`, `text/plain`. Both schemes.
> 3. **Do NOT add `image/gif`** — GIF is not in the project's `IMAGE_EXTENSIONS` list.
> 4. **Verification**: Run `assembleStandardDebug`. Open a `.pdf` and a `.jpg` from a file manager — both should offer FastMediaSorter.
> 5. **Logging**: `add_to_dev_log.ps1` for `AndroidManifest.xml` — "Phase 2 Step 2.3: image/docs intent filters added"

---

## 📅 Phase 3: Settings Screen Default Associations UI
**Objective**: Let users manually set the app as the system default player from the Settings screen.

### 🛠️ Step 3.1: Localization strings
> **Prompt**:
> 1. Add the following string keys to `strings.xml` (EN), `strings-ru.xml` (RU), `strings-uk.xml` (UK):
>    - `settings_set_as_default_player` — e.g. "Set as default player"
>    - `settings_already_default_player` — e.g. "Already set as default"
>    - `settings_default_player_dialog_message` — short explanation of what setting the default does
>    - `settings_default_player_dialog_confirm` — "Open settings" / "Открыть настройки"
> 2. **Verification**: No lint string-reference errors.

### 🛠️ Step 3.2: Settings button in fragment layouts
> **Prompt**:
> 1. Add a `Button` (or `Preference` if using `PreferenceFragmentCompat`) labeled `@string/settings_set_as_default_player` to the layouts of:
>    - `AudioSettingsFragment` (audio default player)
>    - `VideoSettingsFragment` (video default player)
>    - `MediaSettingsFragment` (image/document default player)
> 2. On click: show a brief `AlertDialog` using `settings_default_player_dialog_message`, then navigate the user to system Default Apps via:
>    ```kotlin
>    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
>    ```
> 3. **Verification**: Build and tap the button on a device — system Default Apps screen opens.
> 4. **Logging**: `add_to_dev_log.ps1` for each modified fragment.

### 🛠️ Step 3.3: RoleManager state check (API 29+)
> **Prompt**:
> 1. In each fragment's `onResume()`, check if the app is already the default:
>    ```kotlin
>    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
>        val roleManager = requireContext().getSystemService(RoleManager::class.java)
>        val isDefault = roleManager.isRoleHeld(RoleManager.ROLE_MEDIA)
>        btnSetDefault.isEnabled = !isDefault
>        btnSetDefault.text = if (isDefault)
>            getString(R.string.settings_already_default_player)
>        else
>            getString(R.string.settings_set_as_default_player)
>    }
>    ```
> 2. **Verification**: After setting the app as default, return to settings — button dims and label changes.
> 3. **Logging**: `add_to_dev_log.ps1` for each modified fragment — "Phase 3 Step 3.3: RoleManager check in onResume"

---

## 📅 Phase 4: Welcome Screen Onboarding Defaults
**Objective**: Offer users the option to set FastMediaSorter as the default player during first-launch onboarding.

### 🛠️ Step 4.1: Add onboarding page to existing WelcomeActivity
> **Prompt**:
> 1. **Do NOT create a new Activity.** Add a new page (step) to the existing `WelcomeActivity` onboarding flow.
> 2. This page should contain:
>    - A title: "Set as default player"
>    - A short description of the benefit
>    - 4 buttons: "Audio", "Video", "Images", "Documents"
>    - A "Skip" link/button at the bottom
> 3. Each of the 4 buttons calls `startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))` for the respective type.
> 4. The page is shown **only on first install**: gate it behind a `SharedPreferences` boolean flag `onboarding_default_player_shown`. Set to `true` after the page is displayed (whether skipped or interacted with).
> 5. "Skip" advances to the next page / closes onboarding normally.
> 6. **Verification**: Fresh install (clear app data) → onboarding shows new page. Second launch → page is skipped.
> 7. **Logging**: `add_to_dev_log.ps1` for `WelcomeActivity` and its layout — "Phase 4: default player onboarding page added"

---

## 📅 Phase 5: Hardware Buttons Control (MediaButtonReceiver)
**Objective**: Intercept hardware media buttons (car stereo, headphones) so they control `AudioPlaybackService` instead of the system default player.

### 🛠️ Step 5.1: Receiver declaration in AndroidManifest.xml
> **Prompt**:
> 1. Add to `AndroidManifest.xml` (inside `<application>`):
>    ```xml
>    <receiver android:name="androidx.media.session.MediaButtonReceiver"
>              android:exported="true">
>        <intent-filter>
>            <action android:name="android.intent.action.MEDIA_BUTTON" />
>        </intent-filter>
>    </receiver>
>    ```
> 2. This receiver is only active when the **"Use as primary system media player"** toggle (Playback Settings) is ON. Do NOT enable it unconditionally — gate via `PackageManager.setComponentEnabledSetting()` the same way as the `ACTION_VIEW` aliases.
> 3. **Verification**: Build passes. Receiver appears in `adb shell pm dump com.sza.fastmediasorter | grep MediaButton`.
> 4. **Logging**: `add_to_dev_log.ps1` for `AndroidManifest.xml` — "Phase 5 Step 5.1: MediaButtonReceiver declared"

### 🛠️ Step 5.2: MediaSession registration in AudioPlaybackService
> **Prompt**:
> 1. Inside `AudioPlaybackService.onCreate()`, create and initialize a `MediaSessionCompat`:
>    ```kotlin
>    mediaSession = MediaSessionCompat(this, "FastMediaSorterSession").apply {
>        setCallback(mediaSessionCallback)
>        setMediaButtonReceiver(PendingIntent for MediaButtonReceiver)
>        isActive = true
>    }
>    ```
> 2. Implement `mediaSessionCallback` handling at minimum: `onPlay()`, `onPause()`, `onSkipToNext()`, `onSkipToPrevious()`, `onStop()`.
> 3. Call `mediaSession.release()` in `onDestroy()`.
> 4. **Verification**: Press play/pause on connected Bluetooth headphones while audio plays — `AudioPlaybackService` responds instead of system player.
> 5. **Logging**: `add_to_dev_log.ps1` for `AudioPlaybackService`.

### 🛠️ Step 5.3: Quiet service restart on hardware Play
> **Prompt**:
> 1. When `AudioPlaybackService` is killed and a hardware Play button event arrives via `MediaButtonReceiver`, the service restarts **silently** (no notification, no UI).
> 2. In `onStartCommand()`, check if the intent is a media button event: `MediaButtonReceiver.handleIntent(mediaSession, intent)`. If the action is `KEY_EVENT_ACTION = ACTION_DOWN` and keyCode is `KEYCODE_MEDIA_PLAY` — load resume state via `SaveResumeStateUseCase` and begin playback.
> 3. Do **not** show a playback notification until the user explicitly taps play from the notification shade or inside the app.
> 4. **Verification**: Kill the app → press Play on car stereo → audio resumes silently.
> 5. **Logging**: `add_to_dev_log.ps1` for `AudioPlaybackService` — "Phase 5 Step 5.3: quiet resume on MediaButton play event"

---

## 📅 Phase 6: ACTION_SEND & Sharing Intakes
**Objective**: Allow other apps to open media files in FastMediaSorter via the system Share sheet.

### 🛠️ Step 6.1: Localization string & Playback Settings toggle
> **Prompt**:
> 1. Add string keys to `strings.xml` / `strings-ru.xml` / `strings-uk.xml`:
>    - `settings_accept_via_share` — e.g. "Accept files via Share sheet"
>    - `settings_accept_via_share_summary` — brief description
> 2. Add a `SwitchPreference` (or Toggle) to the **Playback Settings** screen using these strings.
> 3. Store the value in `SharedPreferences` as `pref_accept_via_share` (Boolean, default `false`).
> 4. On toggle change, call `PackageManager.setComponentEnabledSetting()` on each of the 4 ACTION_SEND-capable aliases (enable/disable accordingly).
> 5. **Logging**: `add_to_dev_log.ps1` for Playback Settings fragment.

### 🛠️ Step 6.2: ACTION_SEND intent filters on aliases
> **Prompt**:
> 1. To each of the 4 `<activity-alias>` entries, add a second `<intent-filter>` for `ACTION_SEND`:
>    ```xml
>    <intent-filter>
>        <action android:name="android.intent.action.SEND" />
>        <category android:name="android.intent.category.DEFAULT" />
>        <data android:mimeType="audio/*" />  <!-- adjust per alias type -->
>    </intent-filter>
>    ```
> 2. In `StandalonePlayerActivity`, if `intent.action == Intent.ACTION_SEND`, extract the URI from `intent.getParcelableExtra(Intent.EXTRA_STREAM)` (instead of `intent.data`) and route it through the same `viewModel.loadFromUri()` flow.
> 3. **Verification**: Share an audio/video/image/doc from another app → FastMediaSorter appears in the share sheet and plays the file.
> 4. **Logging**: `add_to_dev_log.ps1` for `AndroidManifest.xml` and `StandalonePlayerActivity`.

---

## 📅 Phase 7: Build-Time Flavor Exclusion
**Objective**: Exclude default player components from product flavors that do not support this feature.

### 🛠️ Step 7.1: BuildConfig flag per flavor
> **Prompt**:
> 1. In `app_v2/build.gradle.kts`, add a `buildConfigField` per flavor:
>    ```kotlin
>    // standard, photos flavors:
>    buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "true")
>    // lite, legacy flavors:
>    buildConfigField("boolean", "SUPPORTS_DEFAULT_PLAYER", "false")
>    ```
> 2. In `AudioSettingsFragment`, `VideoSettingsFragment`, `MediaSettingsFragment`: gate the "Set as default player" button visibility:
>    ```kotlin
>    btnSetDefault.isVisible = BuildConfig.SUPPORTS_DEFAULT_PLAYER
>    ```
> 3. In `WelcomeActivity`: gate the onboarding page with the same flag.
> 4. **Verification**: Build lite flavor → default player button absent. Build standard → button present.
> 5. **Logging**: `add_to_dev_log.ps1` for `build.gradle.kts` — "Phase 7: SUPPORTS_DEFAULT_PLAYER flag per flavor"

### 🛠️ Step 7.2: Manifest overlay for lite/legacy flavors
> **Prompt**:
> 1. Create `app_v2/src/lite/AndroidManifest.xml` (and `app_v2/src/legacy/AndroidManifest.xml`) with only the nodes that **override or remove** the default player components:
>    ```xml
>    <!-- Disable all default player aliases and MediaButtonReceiver for this flavor -->
>    <activity-alias android:name=".StandaloneAudioPlayer" android:enabled="false" />
>    <activity-alias android:name=".StandaloneVideoPlayer" android:enabled="false" />
>    <activity-alias android:name=".StandaloneImagePlayer" android:enabled="false" />
>    <activity-alias android:name=".StandaloneDocsPlayer"  android:enabled="false" />
>    <receiver android:name="androidx.media.session.MediaButtonReceiver" android:enabled="false" />
>    ```
> 2. **Verification**: Build `assembleLiteDebug` → confirm via `aapt dump badging` that aliases are not exported in lite APK.
> 3. **Logging**: `add_to_dev_log.ps1` for `src/lite/AndroidManifest.xml` and `src/legacy/AndroidManifest.xml`.
