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
- ❌ No location data
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
- No telemetry, analytics, or tracking servers
- All operations are user-initiated  

## Permissions Explained

The app requests minimum necessary permissions:

### Storage Permissions

- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`: Access local media files (Android ≤12)
- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO`: Access media (Android 13+)
- `MANAGE_EXTERNAL_STORAGE`: Optional for full storage access

### Network Permissions

- `INTERNET`: Connect to network shares and Google Drive
- `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE`: Check connectivity

### Contacts Permissions

- `READ_CONTACTS`: Optional, used only to show a pinned contact's name and photo on the launcher (Settings > Permissions > Contacts); denying it keeps a plain initial in place of the photo.

### Phone State Permission

- `READ_PHONE_STATE`: Optional, used only to show the SIM signal level in the launcher's own status area. The level is read on the device and never leaves it - nothing is stored, sent, or shared. Denying it simply hides both SIM indicators; the rest of the status area is unaffected.

### Other Permissions

- `WAKE_LOCK`: Keep screen on during playback (user-controlled in Settings)  

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
- **SSHJ**: SFTP protocol
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
