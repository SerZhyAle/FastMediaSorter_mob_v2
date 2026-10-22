---
layout: doc
title: "Windows servers: sharing PC folders to your devices"
description: "What Fast Media Sorter & Sharing for Windows does for exchange: its three optional parts - viewer, folder sharing over SFTP and the exchange server - two ways to start, what each package holds, pairing a phone by QR code or file, one code with several addresses, and the always-on Server edition."
category: "Media exchange"
permalink: /docs/media-exchange-portal/windows-servers.html
---
<sub class="doc-stamp">26.10.22 01:27</sub>

# Windows servers: sharing PC folders to your devices

On a Windows PC the exchange is done by **[Fast Media Sorter & Sharing for Windows](https://serzhyale.github.io/FastMediaSorter_Lite/)**. It is one program with **three optional parts**, and you install the ones you came for: a **viewer and sorter** for photos and videos, **folder sharing** that opens the PC's folders on your phone, tablet, TV or headset, and the **Media Exchange Server**. This page is kept together with the Windows product. It keeps the two server roles apart and says which package holds which part.

## Three optional parts {#three-parts}

- **Viewer and sorter.** Looks through and sorts photos and videos on the PC, one key per file. It needs no server and no account.
- **Folder sharing.** Shares the folders you pick over SFTP (the standard way devices share folders), so a phone, a tablet, a TV, a headset or any SFTP app opens them straight from the PC - on your own network, or from anywhere after one setting in the router (a forwarded port). It needs no account and no exchange server. FileDO virtual disks are an optional extra of this sharing, off until you register a disk. Details: [what you share](#what-you-share).
- **Media Exchange Server.** A different part, not a mode of folder sharing. Folder sharing hands a PC's folders to a device that can reach the PC. The exchange server is a meeting point for devices that cannot: each one dials out to it, and one account lists all your folders and broadcasts. See [what each package holds](#availability) and [Exchange server](exchange-server.html).

You do not need all three. Start with the viewer and add folder sharing if you want it, or start with a server and add the viewer if you want it: choosing the viewer does not require a server, and choosing a server does not require the viewer.

## Two ways to start {#editions}

There are two starting points for the same program. They differ in what is installed first and in when sharing runs; they are not two products to choose between for good.

### Viewer first {#viewer-first}

For a PC you also work on. The viewer and sorter is always installed. Folder sharing is a part of the Full setup type: it stays switched off until you switch it on, and then it works while you are signed in. It can also switch itself over to run as a Windows service.

- **Get it:** winget `SerZhyAle.FastMediaSorter`, the setup file or ZIP from GitHub, or the Microsoft Store. Start at the [product site](https://serzhyale.github.io/FastMediaSorter_Lite/).
- **Only the viewer:** the Compact setup type installs the viewer with no sharing at all.
- **The Microsoft Store version** carries the viewer and folder sharing in user mode only. It cannot run as a Windows service.

### Server first {#server-first}

For a PC whose job is to stay available. Folder sharing runs as a Windows service that starts at boot, with nobody signed in. The viewer is an optional part, unticked by default, so this start does not require it.

- **Get it:** winget `SerZhyAle.FastMediaSorter.Server`, or the setup file from the [Server edition page](https://serzhyale.github.io/FastMediaSorter_Lite/server.html).
- **One visible approval.** The installer registers the service and asks for administrator approval once, in a prompt you see.
- **What it holds:** folder sharing as a service, with the exchange server and the viewer as optional parts.

## What each package holds {#availability}

| | Viewer and sorter | Folder sharing | Media Exchange Server |
|:--|:--|:--|:--|
| Viewer first (full package) | Always installed | In the Full setup type, left out of Compact; off until you switch it on | Optional part, unticked |
| Server first (Server edition) | Optional part, unticked | The whole job: a Windows service from boot | Optional part, unticked |
| Microsoft Store version | Yes | Yes, in user mode only | Not in it |
| Where it runs | While you are signed in | While you are signed in, or as a Windows service | As a Windows service |
| Administrator prompt | None, unless you install for all users | Once to switch sharing on, which adds a firewall rule; once more to register a service | Once, to register the service and add a firewall rule |
{: .doc-table}

Folder sharing and the exchange server need 64-bit Windows 10 version 1607 or newer. The 32-bit fallback viewer for Windows 7, Windows 8.1 and 32-bit Windows can open and sort media, but it cannot share.

## Folder sharing: what you share {#what-you-share}

- **You pick the folders.** Sharing is managed by **Share Manager**, the tray program that comes with Fast Media Sorter & Sharing for Windows. Only the folders you add are visible, and the share cannot reach outside them.
- **Each folder has its own settings** - a name you give it, read-only or writable, an optional PIN, and filters by media type.
- **It ships switched off.** Nothing listens until you enable the server features: a checkbox during installation, or a one-time button in Share Manager. That step shows one Windows administrator prompt and adds one Windows Firewall rule, limited to the sharing program. It is never done silently, and the rule is removed when you uninstall.
- **The connection is encrypted and direct**, device to PC over SFTP. The phone pins the PC's host key at the first pairing, so a substituted server is noticed rather than trusted. The key is created once and is not replaced by an update, a restart or a switch between starts.
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
- the **internet address** - an address on the internet (a fixed one, or a free name that follows a changing address) plus the forwarded port.

The phone tries them and prefers the fast local one, so one scan works at home and away. On the same Wi-Fi network it also finds the PC by itself if the PC's address changed, because it recognises the PC by its host key.

Two settings keep this predictable:

- **The port does not move by itself.** The PC picks the port it answers on once, on the first start, and keeps it. A router setting and a printed QR code stay valid. If that port cannot be taken, the server stays down and says so, rather than quietly moving to another port.
- **A "local network only" switch** leaves only the local address in the code and stops any attempt to open the router. Use it for a share that must never be reachable from outside.

## Opening the way from outside {#from-outside}

For access away from home the router has to pass the port to the PC. Share Manager helps in three steps:

1. It asks the router to open the port by itself, through UPnP or NAT-PMP, when the router allows that. Many routers do not, or have it switched off. This step is skipped in "local network only" mode.
2. It then checks the port **from the internet**, through an outside checking service (check-host.net), and tells you plainly what it found: that the share works from the internet, that the port does not answer and the forwarding should be checked, or that the router opened the port but your provider blocks incoming connections.
3. When the router does not open the port itself, it shows the router's address, has an **Open router** button, and can search for a guide for your router model.

If the provider blocks incoming connections altogether (one internet address shared among many customers), Share Manager warns about that, and a forwarded port will not help; the IPv6 address above may. Setting up the port by hand is described in [Home network](home-network.html).

## A PC that serves with nobody signed in {#always-on}

By default the folders are reachable while you are signed in and Share Manager runs - right for an ordinary PC. A machine whose job is to stay available can host the same service as a Windows service that starts at boot: switch it over in Share Manager (**Manager settings**, **Hosting**), or start [server first](#server-first) with the separate Server edition.

- **Nothing changes for the phones.** The service uses the same host key, the same port and the same folder list, so paired phones need no new pairing.
- **One visible approval.** Registering the service asks for administrator approval once, in a prompt you see.
- **Never both at once.** The service and the interactive Share Manager worker do not run together; when the service is registered, it is the one that serves.
- **Not in the Store version.** The Microsoft Store build runs in user mode only and cannot be a service.

## The PC as a client too {#pc-as-client}

- **Opening SFTP servers like a folder.** The viewer opens an SFTP server - a NAS, another PC's Share Manager, or the built-in SFTP server of a phone - as a folder from its list of resources.
- **Configuration files.** It imports a `.fmscfg` file and exports a saved server as one (64-bit version only).
- **Playing straight from an address.** The viewer's **Open URL..** command plays a video straight from an `smb://`, `sftp://`, `ftp://`, `http(s)://` or `rtsp://` address, without copying the file first.

<a id="planned"></a>
## The PC in your exchange account {#exchange-member}

- **Media Exchange Server** - the third part, separate from folder sharing: a connect service for a share that has neither a reachable address nor a forwarded port, and the meeting point of the devices on one account. It is an optional part, unticked, in both starts, and it is not in the Microsoft Store version. It is administered from Share Manager. Whoever runs a server is its operator. See [Exchange server](exchange-server.html).
- **The PC as a member of an exchange account** - it publishes its shares and opens the folders of other devices through the server.
- **Broadcasting from the PC** - screen, camera, microphone and system sound. See [Live broadcast](live-broadcast.html).

Live broadcasts also play on a Windows PC in [STREAMS Player](https://serzhyale.github.io/StreamsPlayer/).

## Step-by-step guides {#guides}

- [Sharing PC folders with Fast Media Sorter & Sharing for Windows](../../documentation/network/windows-companion.html), in the Android app's documentation
- [Publishing PC folders to Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html), on the Windows product's site

Back to the [portal start page](index.html).

## Settings windows of the Windows program {#windows-settings-windows}

The program has three settings windows, one per part. **Manager settings** holds startup, theme, language, your account on an exchange server, publishing, the clipboard and scheduled file operations. **SFTP sharing settings** holds the port that folder sharing uses, the choice to share on the local network only, the limit on connections and the Hosting page. **Exchange server settings** administers the exchange server installed on this PC; to join somebody else's server you do not need it installed.

Use the links at the lower left to move to another installed part. Each part opens in one window that remembers its page, groups, scroll position and size, and the windows can stay open together. Opening or closing a settings window starts or stops no server. A port number you typed but have not applied is kept while you switch windows; on closing you can apply it, discard it or keep editing.
