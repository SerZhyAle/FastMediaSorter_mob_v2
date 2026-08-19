---
layout: default
title: "Privacy Policy for FastMediaSorter"
permalink: /docs/PRIVACY_POLICY.html
---
# Privacy Policy for FastMediaSorter

**Last updated: November 30, 2025**

## Overview

FastMediaSorter is a media manager and slideshow application for local, network, and cloud storage. We are committed to protecting your privacy and operating transparently.

## Data Collection and Storage

### What Data We Access

FastMediaSorter accesses the following data **only on your device and configured storage**:

1. **Local Storage**
   - Photos, videos, audio files on device and SD cards
   - Used only for displaying, sorting, and organizing media

2. **Network Storage (Optional - User Configured)**
   - SMB/CIFS, SFTP, FTP server credentials you provide
   - File metadata from network shares
   - Direct connections from your device to your servers

3. **Cloud Storage (Optional - Google Drive, OneDrive, Dropbox)**
   - File metadata: names, sizes, thumbnails, modification dates
   - Account email (for authentication display)
   - Limited to folders you explicitly select
   - OAuth tokens for authenticated access

4. **Location (Optional - Network Monitor)**
   - A GNSS track only while you turn on its separate setting and keep the Monitor's Satellites screen open
   - Stored on your device; it does not start in the background or leave the app unless you explicitly share its file

### What We Store Locally

All data stored in app's private, encrypted storage:

- **Connection settings**: server addresses, paths, credentials (encrypted)
- **User preferences**: language, sort order, playback settings
- **Thumbnail cache**: temporary images for faster loading
- **Database**: resource configurations, file metadata cache

### What We DO NOT Collect or Share

- ❌ No analytics or usage tracking
- ❌ No advertising data
- ❌ No behavioral tracking
- ❌ No background location tracking
- ❌ No personal information (names, emails, phone numbers)
- ❌ **No servers**: Your data never goes to our servers (we don't have any)
- ❌ No third-party data sharing

## Logging and Debug Files

- In DEBUG builds the app may optionally write diagnostic logs to a file in the app-specific external storage directory to help troubleshooting when ADB is not available.  
 	- Location (example): `/Android/data/com.sza.fastmediasorter.debug/files/logs/` on the device storage.  
 	- These log files may include timestamps, log levels, component tags, and messages produced by the app and may include non-sensitive metadata such as file names and server addresses used by the app during operation.  
 	- Logs do NOT contain stored passwords in plaintext (sensitive credentials are handled using secure storage).  
 	- File logging is only enabled in debug builds and can be disabled by using the release build or by changing debug settings.  

## Cloud Storage Integration (Google Drive, OneDrive, Dropbox)

When you connect cloud storage (optional):

### Authentication

- Uses OAuth 2.0 for secure sign-in (Google, Microsoft, Dropbox)
- You control which folders the app can access
- Tokens stored encrypted on your device only

### What We Access

- File metadata: names, sizes, thumbnails, modification dates
- File content: only when you view/copy/move files
- Your email address: displayed in UI for account identification

### What We DON'T Do

- ❌ Access files outside folders you select
- ❌ Share your cloud data with anyone
- ❌ Upload your data to our servers
- ❌ Scan your entire cloud storage
- ❌ Access other cloud services of that account (Gmail, Google Contacts, Calendar, etc.) - the optional on-device contacts permission below is separate and never reaches your cloud account

### Data Usage

- Thumbnails cached locally for performance
- File operations happen directly: Device ↔ Cloud Provider
- No intermediary servers or proxies

### Revoking Access

Disconnect anytime:

- In app: Settings → Cloud Resources → Sign Out
- Google Account: [Manage Permissions](https://myaccount.google.com/permissions)
- Microsoft Account: [App Permissions](https://account.microsoft.com/privacy/app-access)
- Dropbox: [Connected Apps](https://www.dropbox.com/account/connected_apps)

### Provider Privacy Policies

Cloud access is subject to each provider's privacy policy:

- [Google Privacy Policy](https://policies.google.com/privacy)
- [Microsoft Privacy Statement](https://privacy.microsoft.com/privacystatement)
- [Dropbox Privacy Policy](https://www.dropbox.com/privacy)

## Network Access

### Local Network (SMB/SFTP/FTP)

- Direct connections from device to your servers
- Credentials encrypted using Android Keystore
- No intermediary services or proxies
- Data never leaves your local network

### Internet Usage

- Google Drive API: only for authenticated access to your files
- External IP check: only after you ask for it, the Network Monitor contacts a third-party IP-echo service that can see your request address; the result is shown on screen and is not automatically saved
- No telemetry, analytics, or tracking servers
- All operations are user-initiated  

## Permissions Explained

This section lists everything the app can ask you about - each permission granted through a system dialog or through a dedicated system screen. Permissions granted silently at install, which you are never asked about (network access, vibration, keeping the screen awake, foreground service types), are not listed.

Your build may show fewer of these than the list: a permission appears only when the feature behind it exists in that build, and Settings > Permissions always shows exactly the ones your build can ask for.

### Storage

- `READ_EXTERNAL_STORAGE`: Find and show media files on the device (Android 6-12)
- `WRITE_EXTERNAL_STORAGE`: Move and delete files you sort (Android 6-9)
- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO`: The same access, split by media type (Android 13+)
- `MANAGE_EXTERNAL_STORAGE`: Optional. Granted on a system screen. Lets you pick any folder, including DCIM, Camera and app folders such as Android/media, instead of only Documents, Downloads and Pictures
- `MANAGE_MEDIA`: Optional. Granted on a system screen. Lets media moves and deletions go through without a separate confirmation each time (Android 12+)

### Network

- `ACCESS_LOCAL_NETWORK`: Optional. Reach SMB, SFTP, FTP and DLNA servers on your own network, and find them by looking for devices on it. Android introduces this permission in a release later than any shipped today, so no current Android version asks you for it

### Camera, microphone and location

- `CAMERA`: Optional. Shoot photos and video inside the app, recognize text, and scan a companion QR code
- `RECORD_AUDIO`: Optional. Record voice notes and the sound in a screen recording
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: Optional. Three features read it. It writes coordinates into the photos and videos you shoot in the app; on the launcher desktop, it gives the compass, speed and chart gadgets your speed and altitude while one of those tiles is on screen; and the Network Monitor can record a GNSS track only while its Satellites screen is open and you turned on the separate track setting. Position is never read in the background. Charts and an opted-in track stay on this device; nothing is sent anywhere unless you explicitly share a track file. Denying it leaves captures without coordinates and those tiles idle with a message saying so

### Notifications

- `POST_NOTIFICATIONS`: Optional. Show playback, transfer and recording progress, and the notification that carries their stop control (Android 13+)

### System

- `SYSTEM_ALERT_WINDOW`: Optional. Granted on a system screen. Draw the edge-gesture strip over other apps
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Optional. Granted on a system screen. Keep scheduled operations running on time instead of being paused in the background
- `REQUEST_INSTALL_PACKAGES`: Optional. Granted on a system screen. Install an APK file you opened from a browsed folder. Present only in the build without legal restrictions
- Screen capture consent: asked again by the system every time a screen recording starts, so it cannot be granted in advance. The app declares `FOREGROUND_SERVICE_MEDIA_PROJECTION` to keep the recording alive while it runs
- `READ_PHONE_STATE`: Optional. Show the SIM signal level in the launcher's own status area. The level is read on the device and never leaves it - nothing is stored, sent, or shared. Denying it hides both SIM indicators and changes nothing else
- `BIND_NOTIFICATION_LISTENER_SERVICE`: Optional. Turned on by you on a system screen. Serves two features of the launcher desktop, and nothing else. First, the Now Playing gadget shows and controls what other apps are playing, reading media sessions only. Second, if you switch it on yourself in the launcher settings, the top bar shows one icon per app that currently has notifications waiting, together with how many it has - the app reads which application posted a notification and how many are pending, never their title, text, attachments or actions. Both features are off until you turn them on, nothing about a notification is written to storage, and nothing leaves the device
- `READ_CONTACTS`: Optional. Show a pinned contact's name and photo on the launcher; denying it keeps a plain initial in place of the photo
- `ACTIVITY_RECOGNITION`: Optional. Asked only when you add the steps gadget to the launcher desktop. The count comes from the counter your phone already keeps, is read only while the tile is on screen, and is neither stored by the app nor sent anywhere. Denying it leaves the tile idle with a message saying so

## Data Security

### Encryption

- Network credentials encrypted using Android Keystore
- OAuth tokens stored in encrypted preferences
- Database protected by Android app sandbox
- HTTPS/TLS for all cloud connections

### Access Control

- No remote access to your data
- Each resource requires explicit user configuration
- Permissions follow Android security model

### Data Isolation

- App data isolated from other apps
- No cross-app data sharing
- Uninstalling removes all app data permanently

## Your Rights and Control

You have full control over your data:

### Access

- View all configured resources in Settings
- Check cached thumbnails size

### Modify

- Edit or remove resources anytime
- Change credentials, paths, settings
- Update cloud folder selections

### Delete

- **Clear Cache**: Settings → Clear Cache (removes thumbnails)
- **Remove Credentials**: Delete resources in Settings
- **Full Removal**: Uninstall app (removes all data)
- **Revoke Cloud Access**: Sign out or use Google Account settings

### Export/Backup

- Export settings to XML file
- Import on new device
- No automatic cloud sync

## Third-Party Services and Libraries

### Google Services

- **Google Drive API**: File access in folders you select
- **Google Sign-In**: OAuth 2.0 authentication
- Subject to [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy)

### Open-Source Libraries

The app uses open-source libraries for functionality (not data collection):

- **SMBJ**: SMB/CIFS network protocol
- **JSch**: SFTP protocol
- **Apache Commons Net**: FTP protocol
- **BouncyCastle**: Cryptography
- **Glide**: Image loading and caching
- **Room**: Local database
- **Hilt/Dagger**: Dependency injection
- **Timber**: Logging (debug builds only)

None of these libraries collect or transmit user data.

## Debug Logging (Debug Builds Only)

Debug builds may write diagnostic logs:

- **Location**: `/Android/data/com.sza.fastmediasorter.debug/files/logs/`
- **Content**: Timestamps, component tags, error messages, file names
- **NOT included**: Passwords, credentials, file content
- **Release builds**: No file logging

## Data Retention

- **Local cache**: Until cleared or uninstalled
- **Credentials**: Until resource removed
- **OAuth tokens**: Until revoked via Google Account
- **No server data**: We don't operate servers

## International Users

FastMediaSorter available globally. All data processing occurs:

- On your device
- On your configured servers
- In your cloud accounts (if connected)

No data transferred to external servers.

## Children's Privacy

FastMediaSorter does not knowingly collect information from children under 13. The app is designed for general media management and does not target children.

## Compliance

This app complies with:

- **Google Play Developer Program Policies**
- **Google API Services User Data Policy**
- **Android Security Best Practices**
- **GDPR Principles**: Data minimization, user control, transparency

## Changes to This Policy

We may update this Privacy Policy periodically. Changes reflected in:

- Updated "Last updated" date
- Published in app repository
- Notification in app update notes (for major changes)

Continued use after changes constitutes acceptance.

## Open Source

FastMediaSorter is open source. Review our code:

- **Repository**: <https://github.com/SerZhyAle/FastMediaSorter_mob_v2>
- **License**: [Project License]

## Contact

For privacy questions or concerns:

- **Website**: <https://serzhyale.github.io/FastMediaSorter_mob_v2/>
- **GitHub Issues**: <https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues>
- **Email**: <sza@ukr.net>

## Summary (Plain English)

**What this means:**

- ✅ Your files stay where they are (device/servers/cloud)
- ✅ Passwords stored encrypted on your device
- ✅ Google Drive: only folders you choose
- ✅ No tracking, no ads, no analytics
- ✅ Uninstall = all data gone
- ✅ You control everything

**We don't:**

- ❌ Have servers to store your data
- ❌ Sell or share your information
- ❌ Track your behavior
- ❌ Access more than you allow

---

## Consent

By using FastMediaSorter you acknowledge and consent to this Privacy Policy.

*This privacy policy is effective as of November 30, 2025.*
