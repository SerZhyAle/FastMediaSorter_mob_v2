---
layout: default
title: "Open Your PC's Folders by Scanning One Code - FastMediaSorter v2"
permalink: /docs/howto/scenario-companion-share.html
---
# <img src="../icons/doc/ic_resource_smb.png" alt="" width="20" height="20" style="vertical-align:text-bottom"> Open Your PC's Folders by Scanning One Code

> **Level:** Beginner &bull; **Flavor:** Standard, Photos, Legacy, XR/noLegal (scan needs a camera; the file method works everywhere)

[Русский](scenario-companion-share-ru.md) | [Українська](scenario-companion-share-uk.md)

You run a small helper program on your Windows PC, pick the folders with your videos, music, documents, or photos, and it puts a code on the screen. On the phone you tap **Add**, hold the camera up to that code, and the PC folders are instantly connected - no typing an address, no port, no password, no cables.

> **Plain English explanation:** The Windows helper turns your chosen folders into a private, read-only share on your home Wi-Fi and prints a code that already contains everything the phone needs to reach them. Scanning that code is the same as filling in a long connection form by hand - the phone just reads it in one glance. Files then open on demand, streamed over Wi-Fi; nothing is copied to the phone until you ask.

---

## The Helper Program

The "companion" is a built-in feature of **[Fast Media Sorter for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/)** (formerly FastMediaSorter LITE) - the free Windows media sorter from the same author. When you share folders with it, it:

- Starts a private SFTP server for just those folders on your PC.
- Generates its own keys and sets up autostart, so the share is there next time too.
- Shows a **QR code** on screen and can also save a small `.fmscfg` config file.

**Where to get it:**

- Website: [serzhyale.github.io/FastMediaSorter_Lite](https://serzhyale.github.io/FastMediaSorter_Lite/)
- Publishing folders (step-by-step): [How to publish PC folders to Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html)
- GitHub: [latest release](https://github.com/SerZhyAle/FastMediaSorter_Lite/releases/latest) (installer or portable ZIP)
- winget: `winget install SerZhyAle.FastMediaSorter`
- Microsoft Store: search for "FastMediaSorter LITE" (still listed under the former name)

---

## What You Will Need

- A Windows PC with **Fast Media Sorter for Windows** installed
- Your phone and PC on the **same Wi-Fi network** (same router)
- For the fastest path: a phone **camera** to scan the code (a file-based path is available if the camera is not)

---

## Step 1 - Share the Folders on the PC

1. Install and run **Fast Media Sorter for Windows**, then open the **Share** tab in settings.
2. Pick the folder(s) you want on the phone - Movies, Music, Documents, Photos, anything.
3. The app starts the SFTP server, generates the keys, and sets up autostart on its own. Nothing else to configure.
4. It now shows a **QR code** on the PC screen. Leave that window open for Step 2.

> Prefer a file instead of a code? Use **Save .fmscfg** in the same window and send that file to the phone (email, Telegram, or any shared folder). See [Step 2, Method B](#step-2-method-b---import-the-file).

---

## Step 2, Method A - Scan the Code (fastest)

1. Open FastMediaSorter and tap the **Add (⊕)** button on the main screen.
2. Tap **"Import by barcode"** - it sits next to the four resource-type cards (Local, SMB, SFTP/FTP, Cloud) and in the SFTP form header.
3. The camera opens with the hint *"Point the camera at the companion QR code"*. Hold the phone up to the QR on your PC. In a dark room, tap **Torch**.
4. A confirmation appears - *"Import access - Add the SFTP resource .. with N folder(s)?"*. Tap **Import**.
5. Done. One read-only resource per shared folder appears on the main screen, with the server's key pinned automatically.

> The **Import by barcode** entry is hidden on devices without a camera and on VR headsets - use Method B there.

<!-- TODO screenshot: Add-resource screen with the four type cards plus "Import from file" and "Import by barcode" entries -->

<!-- TODO screenshot: QR scan screen with the "Point the camera at the companion QR code" hint and Torch button -->

---

## Step 2, Method B - Import the File

Use this when the phone has no camera, or when the PC and phone are not side by side.

1. On the PC, use **Save .fmscfg** and get the file to the phone (email, Telegram, cloud, or a shared folder).
2. **If the file is already on the phone:** tap **Add (⊕)** -> **"SFTP / FTP"** -> **"Import from file"**, then pick the `.fmscfg` file.
3. **If you received it as an attachment** (Telegram or email): just tap the `.fmscfg` attachment - the app opens a confirm dialog directly.
4. Confirm the same *"Import access"* dialog and tap **Import**. The read-only resources appear.

> **Treat the code and the file like a key.** Both embed the access password so the phone can connect with zero typing. Do not post the QR screenshot or the `.fmscfg` file publicly.

---

## Done! You Can Now..

The shared folders behave like any other resource in the app. For example:

- **Watch movies and series** from the PC on your phone, tablet, or Android TV box - streamed, nothing copied. See [Home Cinema & VR Streaming](scenario-home-cinema.md).
- **Play your music library** on the go or on a car head unit.
- **Read PDFs and EPUBs** stored on the PC, keeping your last position.
- **Browse a photo archive** and sort it with Quick Sort, or show it as a [digital photo frame](scenario-photo-frame.md).
- **Hand a file to a specialist app** - open a network file's Info sheet and tap Download and Open.
- **Copy or move files** between the PC and the phone in either direction.

---

## How It Works (under the hood)

- The Windows helper runs a lightweight **SFTP server** bound to the folders you picked, on your local network only.
- The QR code (or `.fmscfg` file) encodes the connection: host, port, credential, the shared folder paths, and the server's host-key fingerprint. Dense shares are sent compressed, so even many folders fit in one code.
- The phone reads that payload, verifies it, and creates one **read-only SFTP resource per folder**. The server key is pinned on first use (TOFU), so the phone can warn you if the PC is ever impersonated.
- Because it is your local Wi-Fi and read-only, the phone browses and streams the files without changing anything on the PC.
- **On the same Wi-Fi, the phone finds the PC by itself.** The companion announces the share on the local network, and the phone matches it by the pinned key - so even if the PC's address on the network changes, the share keeps working without re-scanning.
- **One import can work at home and away.** The code can carry more than one address - the local one, an IPv6 one, and an internet port-forward. The phone tries them and uses whichever is reachable right now: the local address at home, the internet one on mobile data. The same resource keeps working as you move between networks, as long as the PC is actually reachable from where you are.
- **If it cannot connect, the app explains what to do** - get on the same Wi-Fi, or set up access on the PC - instead of a bare error. When the companion includes a note about access, the phone shows it.

---

## Troubleshooting

| Problem | What to try |
|---------|------------|
| No "Import by barcode" entry | The device has no camera, or it is a VR build. Use [Method B - Import the File](#step-2-method-b---import-the-file) |
| Camera says access is needed | Grant the camera permission when prompted - it is used only for the scan |
| "This file is not a valid companion config" | The code or file is not from the Windows companion. Re-export it from the **Share** tab |
| "Created by a newer companion version" | Update FastMediaSorter on the phone, or re-export from a matching companion version |
| Resource added but folders are empty | Make sure the PC helper is still running. On the **same Wi-Fi** the app finds the PC on its own; if it still fails, the app shows what to check |
| Works on Wi-Fi but not on mobile data | To reach the PC from another network, it must be reachable from the internet - set up port forwarding or IPv6 in the companion's **Share** settings. Without that, the share works on the same Wi-Fi only |

→ More help: [TROUBLESHOOTING.md](../TROUBLESHOOTING.md) &bull; Foundations: [Connect to NAS / Windows Share (SMB)](scenario-smb-setup.md)
