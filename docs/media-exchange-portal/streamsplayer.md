---
layout: doc
title: "Media exchange in STREAMS Player for Windows"
description: "Sign in to your exchange server, play or start a broadcast, and diagnose connection problems in STREAMS Player."
category: "Media exchange"
permalink: /docs/media-exchange-portal/streamsplayer.html
---

<sub class="doc-stamp">26.11.23 11:22</sub>

# Media exchange in STREAMS Player for Windows

Use [STREAMS Player](https://serzhyale.github.io/StreamsPlayer/) to play broadcasts on a Windows PC or send its screen, camera, microphone or system sound to other devices.

## Sign in {#sign-in}

1. Open **Settings** and choose **Exchange server**.
2. Import a connection string, a connection file or a QR image from a file or the clipboard. If Windows detects a camera, **Scan with the camera** reads the QR code directly. The scan processes the image in memory without saving, sending or logging it.
3. Check the server address and port under **Details**, enter your login and password, and choose **Sign in**. Importing the connection details only fills the form.

If you need an account, [create one from an invitation](exchange-accounts.html#invitation) in Fast Media Sorter & Organizer for Android or Fast Media Sorter & Sharing for Windows, or ask the server administrator.

## Receive through your account {#receive-through-your-account}

Sign in and enable the exchange server connection in **Settings** to show **My broadcasts** in the main window. A guest sees only the broadcasts shared with that guest and cannot announce broadcasts from this PC. The first line of the **Broadcast** window shows the exchange server status.

**Sign out on this device** clears the local sign-in and stops this PC's broadcasts for the account. Imported channels stay. If the server cannot be reached, the app warns that the PC may stay listed until you remove it from another device.

## Open the broadcast list {#open-the-broadcast-list}

Choose **My broadcasts** in the main window. The list groups broadcasts by their source device, with a separate heading for guest access. It updates automatically while open. Closing the list stops its requests to the server.

Choose a broadcast and select **Play** to open video in its own player window or audio in a smaller window with volume and stop controls. Choose **Add to my channels** to keep the broadcast. You can play several broadcasts while the main window keeps playing its station. The list tells you when the PC reaches its playback limit.

To play a shared broadcast directly, paste its link into **Open by link** at the bottom of the list or choose **Paste channel** in the main window. These routes work without an account on the sender's server. If prompted, enter the password from the sender. Keep private broadcast links private.

## Broadcast from this PC {#broadcast-from-this-pc}

1. Open **Settings**, choose **Broadcasting**, and allow this PC to broadcast.
2. Open the **Broadcast** window from the main window. On the source card, choose **Broadcast microphone**, **Broadcast system sound**, **Broadcast screen** or **Broadcast camera**. For screen or camera, pick a monitor or camera. Each can carry either microphone or system sound.
3. Use your own exchange server account or explicitly allow listeners on the local network. A guest-only sign-in cannot broadcast.

Capture starts when you start a source. A screen broadcast shows the whole selected monitor, including notifications and passwords. Content protected by Windows may appear blank. The Windows encoder sends H.264 video at up to 1280 × 720 and 30 frames per second, at about 2 Mbit/s.

Each card names the live source and lets you stop it, cast it to one of your devices or open **Share**. That menu offers **Copy link**, **Copy password**, **Show QR code** and **Save descriptor file**. Send the password separately from the link, QR code or file. You can limit how many people watch or listen. Each direct connection uses this PC's upload. Through the exchange server, the PC sends one copy for everyone.

Under **Link password**, choose **Made for each broadcast** (the default), **Set by me** (1 to 64 characters, kept in your Windows account's secret store) or **No password** (anyone with the link can play the broadcast). Changing the password while on air immediately revokes the previous one.

## If a connection does not work {#connection-troubleshooting}

- Check that the exchange server is enabled and reachable, then check your login and password. Expand **Details** to see the address and port.
- If the broadcaster changed a link password, ask for the new one. If a certificate does not match, ask the server administrator or broadcaster to confirm the server and give you new connection details before trying again.
- Choose **Check the connection** in Settings, in the broadcast list or on a broadcast that stopped. The check runs on demand. It stops at the first failure and tells you what to try. The log keeps one line of diagnostic codes without the address, login or password.

The app's **Media exchange** links use Russian or Ukrainian when either is the interface language, and English for all other languages.

Back to the [media exchange portal](./).
