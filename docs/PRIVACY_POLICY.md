---
layout: doc
title: "Privacy Policy for FastMediaSorter"
permalink: /docs/PRIVACY_POLICY.html
---
<sub class="doc-stamp">26.10.15 09:55</sub>

# Privacy Policy for FastMediaSorter

**Last updated: October 15, 2026**

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

4. **Location (Optional - map gadgets and Network Monitor)**
   - A GNSS track only while you turn on its separate setting and keep the Monitor's Satellites screen open
   - Stored on your device; it does not start in the background or leave the app unless you explicitly share its file
   - The desktop map gadgets turn your position into a map-tile address and ask an online map service for the picture of that tile, and ask Android for the name of the place. That is the only case where your position leaves the device; it is used for the request and not stored by us

5. **Exchange Between Your Devices (Optional - exchange server, Google Drive, live broadcast)**
   - Which of your own devices are online, the names of the folders and broadcasts you publish, and what you send from one of your devices to another: files, the clipboard, live camera, microphone and screen
   - Goes only to the exchange server you connected to, or to your own Google account, as described in "Exchange Between Your Devices" below; none of it goes to the developer

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
- ❌ **No servers**: Your data never goes to our servers (we don't have any). The optional exchange server is never ours: you run it, or a person you know and chose runs it for you
- ❌ No third-party data sharing

### What the App Counts on Your Device

"No analytics" above means nothing is sent anywhere. The app does keep a few numbers for you, on your
device only:

- **Always**: when it first ran, which app version and edition was installed then, and how many times it
  has been opened. Four values, and they never leave the device.
- **Only if you turn on statistics**: totals of what you did with the app - files copied, moved, deleted
  or renamed, photos taken, minutes watched or listened to, and similar counts. Plain numbers, no file
  names and no history of individual actions.
- **Turning statistics off deletes those totals**, and the statistics screen disappears with them. The
  four values above stay.
- **Nothing is ever sent on its own.** The statistics screen has a "send to author" button; the report
  is written for you to read, and it is your email app that sends it, only when you press send. It
  carries the app version, edition, your device model and Android version, and no identifier of you.
- **Nothing links one session to another**: no user id, no device id, no install id.

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

## Google User Data

This section describes exactly what FastMediaSorter does with data it receives from your Google Account when you sign in to Google Drive. It applies to every edition of the app that offers Google Drive.

### Limited Use disclosure

FastMediaSorter's use and transfer to any other app of information received from Google APIs will adhere to the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including the Limited Use requirements.

### Permissions the app asks Google for, and why

- **See, edit, create and delete your Google Drive files** (`drive`): FastMediaSorter is a file manager. You pick any folder of your Drive, and the app lists it, shows its photos, videos, audio and documents, and copies, moves, renames or deletes the files you choose. It also saves the backup file you create in Settings to the folder My Drive / FastMediaSorter. A narrower permission cannot do this, because it would let the app see only files it created itself or files you pick one by one.
- **See and download your Google Drive files** (`drive.readonly`): used to fetch preview pictures (thumbnails) of the files in the folder you are looking at.
- **The app's own hidden folder in your Drive** (`drive.appdata`): a private space that only FastMediaSorter and its companion apps can read. It holds files you send from one of your devices to another, until the other device accepts them; the clipboard value, when you use the Google Drive way of sending it; the requests and answers used when one of your devices browses the files of another; and the small records your devices use to find each other and to announce folders and broadcasts. You cannot see this folder in Drive, and no other app can read it. It is the folder of your own Google Account only: nothing is ever addressed to another person's account.
- **Your email address, name and profile picture** (`email`, `profile`, `openid`): shown on the Settings card so you can see which Google Account is connected.

### What the app does with this data

- It reads only what you open: the folders you browse and the files you view, copy, move, rename, delete or back up. The permission covers your whole Drive because you may open any folder, but the app never scans, indexes or reads folders you have not opened.
- Every action is started by you, either directly or as a scheduled operation you set up yourself. Apart from those, the app only checks the hidden app folder described above for files and records sent between your devices.
- Google user data is used only to provide these features inside the app. It is not used for advertising, it is not sold, and it is not used to build user profiles.
- Google user data is not used to train or improve any artificial intelligence or machine learning model.
- No person reads your Google user data. The developer has no access to it at all.

### Where it is stored

- File contents, file names, folder lists and thumbnails go directly between your device and Google's servers over an encrypted (HTTPS) connection. The developer runs no server, and no proxy or third party sits in between.
- On your device the app keeps the sign-in token in encrypted storage, cached thumbnails and a cache of file names and sizes of the folders you opened, all inside the app's private storage.
- Copies of files you download or move to your device are saved where you chose to put them.

### Sharing

- Google user data is never sent to the developer and is never shared with, transferred to or sold to anyone.
- The only exception is a file you send yourself: when you share a file to another app, the file goes to the app you picked, and only that file.

### Keeping and deleting it

- The sign-in token is kept until you sign out in Settings, remove the app's access in your Google Account, or uninstall the app. Each of these deletes it.
- Cached thumbnails and file lists are deleted by Settings -> Clear Cache or by uninstalling the app.
- Files in the hidden app folder are deleted when the receiving device accepts them; a file that no device collects is deleted after 7 days. The clipboard value stays until a newer one replaces it or you delete it in the queue screen of the app, which lists everything waiting in the folder and lets you delete any of it. You can delete the whole hidden folder at any time in Google Drive on the web: Settings -> Manage apps -> FastMediaSorter -> Options -> Delete hidden app data.
- Removing the app's access at [Manage Permissions](https://myaccount.google.com/permissions) stops all further access at once.
- To ask a question or request help with deleting data, write to <sza@ukr.net>.

## Exchange Between Your Devices

This section applies when you connect your own devices to each other, the way you choose: directly on your network, through an exchange server, or through your own Google Drive. All of it is off until you set it up, and the developer receives none of it.

### The exchange server

- An exchange server is a program that connects your devices when they cannot reach each other directly. You run it yourself, or you connect to one run by a person you know, who invited you to it. The developer of this app does not run an exchange server and offers none to the public.
- The app connects with a connection string or QR code and a login and password. The connection is encrypted (TLS) and checked against the certificate fingerprint saved when you first connected. The server keeps only a salted hash of the password, never the password.
- **What the server sees, and what it does not.** This is the one text every program of this family gives about it, in these words:

  > The exchange server connects your devices, so it knows the names of your account, devices, folders and broadcasts, sees which devices are online, and how much is sent and when. It cannot open what goes through it: folders and their files, broadcasts and the clipboard are encrypted on your device and opened only on your other devices or by the people you gave access to. To look inside, whoever runs the server would have to add a device of their own to your account, and it would appear in your list of devices.

- **How long the server keeps things.** The account, its devices and the records of folders and broadcasts stay until you delete the account. The clipboard is one value at a time, held in the server's memory only: the next value replaces it, and a restart of the server loses it. The server's own log follows the retention period its operator set and carries no password, token, invitation or content.
- Folders you open on another device pass through the server inside an encrypted tunnel, and the server joins the two ends without reading the bytes.

### The clipboard

- The app sends the clipboard only when you press the button or the shortcut for it, and gets the server's clipboard only when you press the matching one. Nothing is read in the background, previewed, filtered or kept as a history.
- Only the devices of your own account receive it, and it is one value at a time: text, an image or one file. A send goes to every account the device is signed in to, except a guest account.
- The Google Drive way of sending the clipboard is a separate option, off until you switch it on. The value is then one file in the hidden folder of your own Google Account, and it stays there until a newer value replaces it.

### Via Google Drive

- Files you send between your own devices, the clipboard, the requests that browse a folder of another of your devices, and the announcements of folders and broadcasts, travel through the hidden app folder of your own Google Account when you switch this channel on. No exchange server and no direct address is involved.
- The Google Drive path is not encrypted by this app. Its text, in these words: "Via Google Drive, files and the clipboard are kept in your own Google account, like your other files on Google Drive; no exchange server takes part."
- A live broadcast is only announced there. No part of the stream is ever written to Google Drive.

### Live broadcast: camera, microphone, screen and file

- You start every broadcast yourself - from the app, its tile or widget - or accept the request of your own watch.
- A broadcast can carry the camera and the microphone, the sound of your watch, the phone's screen, or an audio or video file stored on the phone.
- While one runs, the app shows a notification that says it is live and has a Stop button. Before the screen is shared, Android asks you every time and shows its own indicator. When the camera is used, Android shows its camera indicator.
- In the editions that offer it, a camera broadcast keeps running after you leave the app or turn the screen off, until you press Stop. That is the only case in which the camera is used while the app is out of sight, and it needs your tap to start.
- Receivers on your network connect to the address the broadcast announced, or receive it through the exchange server. The app records nothing and keeps no copy; whoever receives the broadcast may record it.

### Your watch

- The watch gets its sign-in from your paired phone and then connects over its own Wi-Fi or mobile connection. Bluetooth carries only the start of a connection and its secrets.
- What the watch sends itself is the sound of its microphone as a broadcast, and the files and recordings you hand to "Send to.." - including to the clipboard on the server. It goes to the same places as on the phone, under the same rules.

### Deleting your account

- **In the app:** open My devices, open the menu of the server card, and choose Delete my account. The server then erases at once the account and its password hash, every device record and token, every folder and broadcast record, the clipboard, any pairing codes, and whatever was given to or by the account; its broadcasts, tunnels and uploads end, and the login becomes free. The app forgets the server, its token and the stored PIN codes. Folders you had connected stay in your list and show as no longer published until you remove them.
- **Without the app,** for instance after losing the phone: the page "How to delete an account" of the media exchange portal, <https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/media-exchange-portal/exchange-server.html>, lists both ways. The person who runs a server can delete any account on it, so for a server that is not yours, ask them.
- **What stays.** The server's log, for the retention period its operator set; copies of files already delivered to your other devices or to the people you gave access to; and what is in your own Google Drive, which you delete there.
- **Signing out on this device** only forgets the sign-in on this device. The account and its other devices stay.

## Network Access

### Local Network (SMB/SFTP/FTP)

- Direct connections from device to your servers
- Credentials encrypted using Android Keystore
- No intermediary services or proxies
- Data never leaves your local network

### Internet Usage

- Google Drive API: only for authenticated access to your files, and for the hidden app folder of your own account when you use the Google Drive way of exchange
- Exchange server: only the one you connected to, only to connect your own devices - see "Exchange Between Your Devices"
- External IP check: only after you ask for it, the Network Monitor contacts a third-party IP-echo service that can see your request address; the result is shown on screen and is not automatically saved
- On-device translation, in editions that offer it: turning on Enable Translation downloads the Translation Module extension once, and picking a new target language downloads that language's model from Google's ML Kit servers. The download carries none of the text you translate; recognized text, PDF pages and EPUB chapters are then translated on the phone and are not sent anywhere
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

- `CAMERA`: Optional. Shoot photos and video inside the app, recognize text, scan a companion QR code, and send live video as a broadcast. In the editions that offer it the app also declares `FOREGROUND_SERVICE_CAMERA`, so that a camera broadcast you started keeps running after you leave the app or turn the screen off, until you press Stop; the app shows a notification with that button and Android shows its camera indicator the whole time
- `RECORD_AUDIO`: Optional. Record voice notes and the sound in a screen recording, and send live sound as a broadcast
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`: Optional. Five features read it. It writes coordinates into the photos and videos you shoot in the app; on the launcher desktop, it gives the compass, speed and chart gadgets your speed and altitude while one of those tiles is on screen; also on the desktop, the map gadgets draw the map around you; and the Network Monitor can record a GNSS track only while its Satellites screen is open and you turned on the separate track setting. The fifth is the name of the Wi-Fi network you are connected to, shown in the Monitor's Wi-Fi section: Android treats that name as location data and hides it from every app without this permission, so the row says why instead of showing it. Position is never read in the background. All of it stays on this device except the map gadgets: to draw the map they ask an online map service for a picture of your area, and ask Android for the name of the place. Nothing else is sent anywhere unless you explicitly share a track file, and none of it goes to a server of ours - we have none. Denying it leaves captures without coordinates and those tiles idle with a message saying so

### Notifications

- `POST_NOTIFICATIONS`: Optional. Show playback, transfer, recording and live broadcast progress, and the notification that carries their stop control (Android 13+). On the watch it also keeps a running stopwatch visible on the watch face and in recent apps; the watch asks for it when you first start the stopwatch, and denying it leaves the stopwatch working without that indicator

### System

- `SYSTEM_ALERT_WINDOW`: Optional. Granted on a system screen. Draw the edge-gesture strip over other apps
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Optional. Granted on a system screen. Keep scheduled operations running on time instead of being paused in the background
- `REQUEST_INSTALL_PACKAGES`: Optional. Granted on a system screen. Install an APK file you opened from a browsed folder. Present only in the build without legal restrictions
- `REQUEST_DELETE_PACKAGES`: Hand an app you picked in the launcher's app menu to the system uninstall screen. Android shows its own confirmation there and removes the app itself - this app deletes nothing on its own, reads nothing about your other apps beyond the name and icon already shown in the list, stores nothing and sends nothing anywhere. Present only in the builds that have launcher mode
- Screen capture consent: asked again by the system every time a screen recording starts or the phone's screen is broadcast, so it cannot be granted in advance. The app declares `FOREGROUND_SERVICE_MEDIA_PROJECTION` to keep the recording or the screen broadcast alive while it runs; either ends when you press Stop or when you end the sharing from the system's own indicator
- `READ_PHONE_STATE`: Optional. Show the SIM signal level in the launcher's own status area. The level is read on the device and never leaves it - nothing is stored, sent, or shared. Denying it hides both SIM indicators and changes nothing else
- `BIND_NOTIFICATION_LISTENER_SERVICE`: Optional. Turned on by you on a system screen. Serves two features of the launcher desktop, and nothing else. First, the Now Playing gadget shows and controls what other apps are playing, reading media sessions only. Second, if you switch it on yourself in the launcher settings, the top bar shows one icon per app that currently has notifications waiting, together with how many it has - the app reads which application posted a notification and how many are pending, never their title, text, attachments or actions. Both features are off until you turn them on, nothing about a notification is written to storage, and nothing leaves the device
- `READ_CONTACTS`: Optional. Show the current name and photo of the one contact you pinned on the launcher and, when you pin a shortcut that opens a messenger, find that person's chat in the messenger you chose. The app never lists or searches your address book and reads nothing else through this permission. Pinning a contact card, a call or an SMS works without it, and so does a messenger shortcut pinned earlier; a new messenger shortcut cannot be pinned without it. Denying it keeps the name saved with the pin and a plain initial in place of the photo. The name and photo are read on demand, kept only in memory and never sent anywhere
- `ACTIVITY_RECOGNITION`: Optional. Asked only when you add the steps gadget to the launcher desktop. The count comes from the counter your phone already keeps, is read only while the tile is on screen, and is neither stored by the app nor sent anywhere. Denying it leaves the tile idle with a message saying so

## Data Security

### Encryption

- Network credentials encrypted using Android Keystore
- OAuth tokens stored in encrypted preferences
- Database protected by Android app sandbox
- HTTPS/TLS for all cloud connections
- Folders, broadcasts and the clipboard sent through an exchange server are encrypted on your device and opened only on your own devices or by the people you gave access to; the connection to the server is encrypted as well

### Access Control

- No remote access to your data
- Each resource requires explicit user configuration
- Permissions follow Android security model

### Data Isolation

- App data isolated from other apps
- No cross-app data sharing
- Uninstalling removes all app data on the device permanently

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
- **Full Removal**: Uninstall app (removes all data on the device; an account on an exchange server and files in the hidden folder of your Google Drive are not on the device, so delete the account in the app before uninstalling - see "Deleting your account")
- **Revoke Cloud Access**: Sign out or use Google Account settings
- **Delete Your Exchange Account**: My devices -> menu of the server card -> Delete my account

### Export/Backup

- Backup is a JSON file you create on demand in Settings -> Backup
- You choose where it goes: a local file on your device, or your own Google Drive (folder My Drive / FastMediaSorter)
- The Google Drive copy is uploaded only when you start it; there is no automatic cloud sync
- The backup holds your settings and resources, including network passwords, SSH private keys, the default network password and saved website sign-ins (cookies), stored in plain text - keep the file private and do not share it
- Restore it on the same or a new device from the same screen
- The backup never reaches the developer
- A contact you pinned on the launcher is saved on the device with its name, phone number and messenger identifier, as part of the launcher data. That copy travels with your own backup file, with Android's own backup and device transfer when you have them switched on in the phone settings, and with the settings transfer between your devices. None of it is sent to the developer or to any third party

## Third-Party Services and Libraries

### Google Services

- **Google Drive API**: File access in folders you select, and the hidden app folder of your own account for the exchange between your devices
- **Google Sign-In**: OAuth 2.0 authentication
- **ML Kit Translation**: translation on the device; Google's servers supply only the language models, the text you translate stays on the phone
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
- **Exchange server**: Kept by whoever runs the server, until you delete the account; the clipboard only in the server's memory, one value at a time
- **No server data of ours**: We don't operate servers

## International Users

FastMediaSorter available globally. All data processing occurs:

- On your device
- On your configured servers, including an exchange server you connected to
- In your cloud accounts (if connected)

Nothing is transferred to servers of the developer: there are none.

## This Website

The app and its website are separate, and everything above is about the app. The website is a set of static pages served by GitHub Pages; we run no server behind it and keep no data about visitors. When you open a page, your browser contacts the addresses below on its own. Each one receives what any web request carries - your IP address, your browser's name and version, and the address of the page you came from - and handles it under its own privacy policy.

- **fonts.googleapis.com** and **fonts.gstatic.com** (Google Fonts), on every page: they deliver the typefaces the pages are set in.
- **pagead2.googlesyndication.com** (Google AdSense), on the landing pages only - the home page and its language versions: it shows an advertisement. While the page runs, the ad script contacts further Google addresses on its own: **ep1.adtrafficquality.google** and **ep2.adtrafficquality.google** check the ad traffic for invalid clicks and impressions, and **fundingchoicesmessages.google.com** shows the consent message for advertising cookies and keeps your choice. They receive the same request data, the page address and Google's advertising cookies - the consent address also receives the choice you make. The ad script may use cookies. Third-party vendors, including Google, use cookies to serve ads based on your earlier visits to this and other websites. You can turn off personalised advertising in [Google Ads Settings](https://adssettings.google.com), and [How Google uses information from sites that use its services](https://policies.google.com/technologies/partner-sites) explains the rest. No other page carries advertising: the documentation, the install-trust pages and this privacy page never load it.
- **api.github.com** (GitHub), on the landing and sideload pages: the page asks it for the list of published releases, so the download buttons point at the latest build.
- **img.shields.io**, **gitlab.com** and **raw.githubusercontent.com**, on the README pages: they serve the version and licence badges, the IzzyOnDroid badge and the GitHub Store badge.

GitHub Pages, as the host, receives every page request ([GitHub Privacy Statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement)). The website runs no analytics or tracking script of its own. It keeps two values in your browser - the theme and the language you picked - and they never leave it.

To avoid these requests, block third-party fonts or scripts in your browser. The pages still open: the text falls back to a system typeface, the advertisement does not load, and the download buttons keep the link to the list of all releases.

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
- ✅ Exchange between your devices: the server only connects them and cannot open what goes through it; you can delete your account in the app
- ✅ The app has no tracking, no ads, no analytics (the website's landing pages show an ad - see "This Website")
- ✅ Uninstall = all data on the device gone (delete an exchange account in the app first)
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
