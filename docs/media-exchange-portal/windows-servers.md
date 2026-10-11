---
layout: doc
title: "Windows servers: sharing PC folders to your devices"
description: "What Fast Media Sorter & Sharing for Windows does for exchange: sharing chosen PC folders over SFTP, pairing a phone by QR code or file, one code with several addresses, the always-on Server edition, and what is planned."
category: "Media exchange"
permalink: /docs/media-exchange-portal/windows-servers.html
---
<sub class="doc-stamp">26.10.10 14:46</sub>

# Windows servers: sharing PC folders to your devices

On a Windows PC the exchange is done by **[Fast Media Sorter & Sharing for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/)**. Besides sorting and viewing media, it can be the server your phone, tablet, TV or headset opens. This page is kept together with the Windows product, and it separates what is released from what exists only in the development version.

## Two editions {#editions}

Both editions run the same sharing service. They differ in what else is installed and in when the service runs.

| | Full package | Server edition |
|:--|:--|:--|
| What it is for | Viewing and sorting media on the PC, and sharing folders from **Share Manager** while you are signed in. It can also switch itself to run as a Windows service. | A PC whose job is only to be available: the share runs as a Windows service that starts at boot, with nobody signed in. The viewer is an optional component, unticked by default. |
| How to get it | winget `SerZhyAle.FastMediaSorter`, the setup file or ZIP from GitHub, or the Microsoft Store (the Store version runs in user mode only). Start at the [product site](https://serzhyale.github.io/FastMediaSorter_Lite/). | winget `SerZhyAle.FastMediaSorter.Server`, or the setup file from the [Server edition page](https://serzhyale.github.io/FastMediaSorter_Lite/server.html). |
{: .doc-table}

Sharing needs 64-bit Windows 10 version 1607 or newer. The 32-bit fallback viewer for Windows 7, Windows 8.1 and 32-bit Windows can open and sort media, but it cannot share.

## What you share {#what-you-share}

- **You pick the folders.** Sharing is managed by **Share Manager**, the tray program that comes with Fast Media Sorter & Sharing for Windows. Only the folders you add are visible, and the share cannot reach outside them.
- **Each folder has its own settings** - a name you give it, read-only or writable, an optional PIN, and filters by media type.
- **It ships switched off.** Nothing listens until you enable the server features: a checkbox during installation, or a one-time button in Share Manager. That step shows one Windows administrator prompt and adds one Windows Firewall rule, limited to the sharing program. It is never done silently, and the rule is removed when you uninstall.
- **The connection is encrypted and direct**, device to PC over SFTP. The phone pins the PC's host key at the first pairing, so a substituted server is noticed rather than trusted. The key is created once and is not replaced by an update, a restart or a switch between editions.
- **Any SFTP app opens the folders too** - not only FastMediaSorter, but also WinSCP, FileZilla or a file manager on another computer. Share Manager shows the address, port, login, password and the key fingerprint, each with a copy button.
- **Connection statistics stay on the PC.** Share Manager has a window with the counters, and nothing in it is sent anywhere.

## Bringing the folders to the phone {#to-the-phone}

1. In Share Manager, share the folders and show the QR code, or export the configuration file (`.fmscfg`). The same data can also go by e-mail or as a text code from the clipboard.
2. On the phone, in FastMediaSorter, open **Add resource** and choose **Import by barcode** or **Import from file**.
3. The shared folders appear as network resources, with the login and the PC's key already filled in.

## One code, several addresses {#one-code}

The QR code and the `.fmscfg` file carry every address the PC can be reached at:

- the **local address** on your Wi-Fi or cable network;
- an **IPv6 address**, when the PC has one - it can work where the provider shares one internet address among many customers and a forwarded port cannot, provided the phone's network has IPv6 too;
- the **internet address** - the public address or a dynamic DNS name plus the forwarded port.

The phone tries them and prefers the fast local one, so one scan works at home and away. On the same Wi-Fi network it also finds the PC by itself if the PC's address changed, because it recognises the PC by its host key.

Two settings keep this predictable:

- **The port does not move by itself.** The PC picks the listening port once, on the first start, and keeps it. A router forwarding rule and a printed QR code stay valid. If that port cannot be taken, the server stays down and says so, rather than quietly moving to another port.
- **A "local network only" switch** leaves only the local address in the code and stops any attempt to open the router. Use it for a share that must never be reachable from outside.

## Opening the way from outside {#from-outside}

For access away from home the router has to pass the port to the PC. Share Manager helps in three steps:

1. It asks the router to open the port by itself, through UPnP or NAT-PMP, when the router allows that. Many routers do not, or have it switched off. This step is skipped in "local network only" mode.
2. It then checks the port **from the internet**, through an outside checking service (check-host.net), and tells you plainly what it found: that the share works from the internet, that the port does not answer and the forwarding should be checked, or that the router opened the port but your provider blocks incoming connections.
3. When the router does not open the port itself, it shows the router's address, has an **Open router** button, and can search for a guide for your router model.

If the provider blocks incoming connections altogether (a carrier-grade address shared among customers), Share Manager warns about that, and a forwarded port will not help; the IPv6 address above may. Setting up the port by hand is described in [Home network](home-network.html).

## A PC that serves with nobody signed in {#always-on}

By default the folders are reachable while you are signed in and Share Manager runs - right for an ordinary PC. A machine whose job is to stay available can host the same service as a Windows service that starts at boot: switch it over in Share Manager (**Manager settings**, **Hosting**), or install the separate [Server edition](https://serzhyale.github.io/FastMediaSorter_Lite/server.html).

- **Nothing changes for the phones.** The service uses the same host key, the same port and the same folder list, so paired phones need no new pairing.
- **One visible approval.** Registering the service asks for administrator approval once, in a prompt you see.
- **Never both at once.** The service and the interactive Share Manager worker do not run together; when the service is registered, it is the one that serves.
- **Not in the Store version.** The Microsoft Store build runs in user mode only and cannot be a service.

## The PC as a client too {#pc-as-client}

<div class="doc-callout doc-callout-warn" markdown="1">
**Development version, not in the latest release.** The two points below describe the Windows viewer as it is being built. A released Fast Media Sorter & Sharing for Windows does not have them yet.
</div>

- **Opening SFTP servers like a folder.** The viewer opens an SFTP server - a NAS, another PC's Share Manager, or the built-in SFTP server of a phone - as a folder from its list of resources.
- **Configuration files.** It imports a `.fmscfg` file and exports a saved server as one (64-bit version only).

What is released today is the viewer's **Open URL..** command: it plays a video straight from an `smb://`, `sftp://`, `ftp://`, `http(s)://` or `rtsp://` address, without copying the file first.

## Planned {#planned}

<div class="doc-callout doc-callout-warn" markdown="1">
**Not available yet.** Nothing below is in a release, and no exchange server is running anywhere. The Windows side is being prepared in the development version so that it is ready when the server is.
</div>

- **Media Exchange Server** - a connect service for a share that has neither a reachable address nor a forwarded port. The Windows package will carry the server program as an optional component, unticked by default, and it will be administered from Share Manager. Whoever runs a server is its operator. See [Exchange server](exchange-server.html).
- **The PC as a member of an exchange account** - publishing its shares and opening the folders of other devices through the server.
- **Broadcasting from the PC** - screen, camera, microphone and system sound. Fast Media Sorter & Sharing for Windows does not broadcast today. See [Live broadcast](live-broadcast.html).

Playing live broadcasts on a Windows PC works today in [STREAMS Player](https://serzhyale.github.io/StreamsPlayer/).

## Step-by-step guides {#guides}

- [Sharing PC folders with Fast Media Sorter & Sharing for Windows](../../documentation/network/windows-companion.html), in the Android app's documentation
- [Publishing PC folders to Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html), on the Windows product's site

Back to the [portal start page](index.html).
