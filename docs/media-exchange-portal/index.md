---
layout: doc
title: "Media exchange between your devices"
description: "Open the photos, videos and music of one of your devices on your other devices, send the clipboard between them and watch live broadcasts."
category: "Media exchange"
permalink: /docs/media-exchange-portal/
---
<sub class="doc-stamp">26.10.22 00:25</sub>

# Your files on all your devices {#media-exchange-between-your-devices}

The photos, videos and music kept on one of your devices open on your other devices - your phone, tablet, watch, TV, VR headset and Windows PC.

- **Your folders on your other devices.** A folder of your home PC opens on your phone, and a folder of your phone opens on your PC.
- **The clipboard between your devices.** Copy a text, a picture or a file on one device, press one button, and paste it on another.
- **Live broadcasts.** A camera, a microphone or a screen of one of your devices plays live on another of your devices.

<picture>
<source media="(max-width: 768px)" srcset="diagrams/ecosystem-hero-phone-en.svg">
<img src="diagrams/ecosystem-hero-en.svg" alt="A phone, a watch, a TV, a VR headset, a laptop and a Windows PC side by side: each of them opens the photos, videos and music kept on the others.">
</picture>

## Choose your way {#choose-your-way}

Your devices reach each other in one of three ways. Next to each, one line says what it needs.

- **[Home network](home-network.html)** - your devices at home connect directly at full speed. *What it needs:* your home Wi-Fi; to reach the same folders away from home, your router is set up once.
- **![Exchange server](exchange-mark/mark-24.png) [Your own exchange server](exchange-server.html)** - one account for all your devices, wherever each of them is, with no router setup. *What it needs:* a Windows PC of yours that runs the exchange server. Each person runs their own server and may invite relatives and friends to it; nobody runs a server for everybody. More: [what it is for and everything you can do with it](exchange-server-purpose.html), [deploying your own server in steps](exchange-server-quick-start.html), [installing a server](exchange-server-install.html), [accounts and invitations](exchange-accounts.html), [the clipboard](exchange-clipboard.html) and [deleting an account](account-deletion.html).
- **Google Drive** - files and the clipboard go between your devices through your own Drive, with no server. *What it needs:* a Google account signed in on each device. More: [files and folders via Google Drive](folders-google-drive.html).

Live broadcasts use the first two ways: directly at home, and through your exchange server from anywhere else. More: [live broadcast](live-broadcast.html).

Two more sections cover what needs no setup of your own:

- **[Clouds](clouds.html)** - Google Drive, Dropbox and OneDrive as a shared box that every signed-in device opens.
- **[Editions](editions.html)** - which edition of the Android app has network folders, clouds, broadcasting and the watch companion.

## Which program does what {#which-program-does-what}

- **Android phone or tablet** - FastMediaSorter opens the folders of your other devices and your clouds, shares its own folders, sends files between your devices via Google Drive, goes live with its camera, microphone, screen or a ready file, plays broadcasts and sends them to your other devices and to a Chromecast.
- **Wear OS watch** - FastMedia Wear joins your account through the phone, then works on its own Wi-Fi or mobile data: it opens your folders, plays broadcasts, goes live with its microphone for up to four listeners, takes a broadcast another of your devices sends to it, and sends its own files with Send to.. More: [the watch on your account](watch-exchange.html).
- **Android TV** - FastMediaSorter opens your folders and clouds and plays broadcasts and internet streams.
- **VR headset** - FastMediaSorter, VR edition, opens your folders and clouds and plays broadcasts and internet streams inside the headset.
- **Windows PC** - [Fast Media Sorter & Sharing for Windows](windows-servers.html) has [three optional parts](windows-servers.html#three-parts): a viewer and sorter for photos and videos; folder sharing, which shares the folders you pick with your phones and pairs a phone by QR code or by a configuration file; and the exchange server, the meeting point of your devices on one account. Start [viewer first](windows-servers.html#viewer-first), or [server first](windows-servers.html#server-first) for a PC that only serves, with nobody signed in.
- **Windows PC** - [STREAMS Player](https://serzhyale.github.io/StreamsPlayer/) plays live broadcasts from a phone or a watch.

## Step-by-step guides by scenario {#step-by-step-guides-by-scenario}

- **[Your own exchange server in steps](exchange-server-quick-start.html)** - from installing the package to checking it from mobile data.
- **[Folders through the exchange server](folders-exchange-server.html)** - share a folder on one device and open it on another from any network.
- **[Files and folders via Google Drive](folders-google-drive.html)** - between your own devices, with no server and no address.
- **[Going on air](broadcast-start.html)** - broadcast sound, the camera, the screen or a ready file.
- **[Receiving a broadcast](broadcast-receive.html)** - the list of your devices, a link, a password and a cast.
- **[The watch on your account](watch-exchange.html)** - through the phone and without it.
- **[A schedule over folders of other devices](schedule-remote-folders.html)** - copy and move on a timetable.
- **[The phone as a server](phone-sharing.html)** - how long it lasts, launcher mode and mobile data.
- **[My devices](my-devices.html)** - adding your server, the pairing code and the connection string.

<a id="what-works-today-and-what-does-not-yet"></a>
## What is available {#what-is-available}

- Folders on a home PC, a network drive or a router, which the app finds on your home network by itself.
- A phone that shares its own folders with another phone, paired in one scan.
- A Windows PC that shares folders with your phone by QR code or by a file, and checks by itself that they also open away from home.
- A Windows PC that keeps sharing with nobody signed in.
- Google Drive, Dropbox and OneDrive, and files between your own devices via Google Drive with no server.
- Your own exchange server: one account for all your devices, your folders opened from anywhere, invitations for relatives and friends, and the clipboard sent between your devices. The server program runs on a Windows PC - see [what each package holds](windows-servers.html#availability).
- Live broadcasts from a phone or a watch - camera, microphone, screen or a ready file - directly at home and through your exchange server from anywhere else, and sending a broadcast to another of your devices, which asks before it plays.
- Chromecast for files, videos and live streams.

## The family of programs {#the-family-of-programs}

- [FastMediaSorter for Android](../../index.html) - phone, tablet, TV, watch and VR headset.
- [Fast Media Sorter & Sharing for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/) - on the PC: a viewer and sorter, folder sharing and the exchange server, each an optional part; [what each part is](windows-servers.html#three-parts).
- [STREAMS Player](https://serzhyale.github.io/StreamsPlayer/) - live streams and broadcasts on Windows.

The detailed guides of the Android app are in its [documentation](../../documentation/index.html).
