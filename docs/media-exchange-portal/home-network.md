---
layout: doc
title: "Home network: your storage, reachable from outside"
description: "Share folders from a media PC or a phone through your own router: a direct connection at home, and the same folders from anywhere after one setting in the router and an address on the internet."
category: "Media exchange"
permalink: /docs/media-exchange-portal/home-network.html
---
<sub class="doc-stamp">26.10.22 01:24</sub>

# Home network: your storage, reachable from outside

The home network way needs no server of anyone else: your own router, and one device that keeps the folders - a media PC or a phone. **It works today.**

![A home network with a router, a media PC sharing folders, a phone and a TV on Wi-Fi. Outside, a phone on mobile data and a laptop in a hotel reach the PC through the router's address on the internet.](diagrams/home-simple-en.svg)

## Who shares, who opens

- **A Windows PC shares** the folders you pick with **Share Manager**, the tray program that is the folder-sharing part of [Fast Media Sorter & Sharing for Windows](windows-servers.html#three-parts). Start [viewer first](windows-servers.html#viewer-first) for a PC you also work on, or [server first](windows-servers.html#server-first) with the Server edition for a PC that has to stay available with nobody signed in - it runs the same sharing in the background, as a Windows service. Folder sharing ships switched off and starts only once you switch it on. The exchange server is a different part and is not needed for the home network.
- **A phone shares** its own folders: FastMediaSorter switches the sharing on from **Settings**, the **General** tab, the **Share from phone** card, with the **SFTP server** switch (SFTP is the standard way devices share folders).
- **A NAS (a small storage box on your network) or a PC with Windows sharing** offers its shared folders as they are (SMB, the sharing built into Windows), with nothing to install.
- **Every other device opens them**: a phone, a tablet, an Android TV and a VR headset add the folder as a network resource in FastMediaSorter; the watch opens network folders too.

## At home

Inside the home network the devices connect to each other directly, at full local speed. FastMediaSorter finds the sharing devices on your network by itself (SMB, FTP and SFTP servers), so in most cases you pick one from a list instead of typing an address. A folder shared from a PC or a phone arrives by scanning its QR code, with the address, the login and the key that proves it is the right device already filled in.

## From outside, in six steps

![Six steps: fix the storage address, turn on folder sharing, let one connection in through the router, get an address on the internet, pair devices by QR code, use the same folder at home and away.](diagrams/home-steps-en.svg)

1. **Fix the storage address.** Reserve an address for the PC or the phone in the router's settings (the DHCP list - look for "address reservation"), for example `192.168.1.20`, so that it never changes.
2. **Turn on folder sharing** (the **SFTP server** switch) on the device that keeps the folders.
3. **Let one connection in through the router.** In the router this is called port forwarding: send, for example, port 2222 from the internet to `192.168.1.20:2222`. Each sharing device needs its own port.
4. **Get an address on the internet**: a fixed address from your provider, or a free name such as `myhome.example.net` that follows a changing address (a "dynamic DNS" service).
5. **Pair by QR code.** The folder arrives with both addresses and the key that proves it is the right device.
6. **Use it anywhere.** At home the app uses the local address, away the internet one, and picks by itself.

**Share Manager can try step 3 for you.** When the router allows it, Share Manager asks the router to open the port by itself (the router features are called UPnP and NAT-PMP), unless you have switched it to "local network only". It then tests the port from the internet and tells you plainly which of three things is true: it works from the internet, the port does not answer and the router setting needs a look, or the router opened it but your provider blocks incoming connections. Not every router allows automatic opening, so setting up the port by hand in the router stays the reliable route.

**One code, three addresses.** The QR code or the file from Share Manager carries every address the PC can be reached at: the local one, the IPv6 one (a newer kind of internet address) and the internet one. The phone tries them all and prefers the local one, so a single scan works at home and away. IPv6 can work even when your provider shares one internet address among many customers, where a router setting cannot help, as long as both ends have IPv6. The port is chosen once and never changes by itself, so a router setting and a printed code stay valid. With "local network only" switched on, the code carries the local address alone.

![One pairing code from Share Manager carries three addresses - home Wi-Fi, IPv6 and the internet - and the phone tries them at once and keeps the fastest.](diagrams/home-pairing-en.svg)

<div class="doc-callout doc-callout-note" markdown="1">
**Does your provider give you no address of your own?** Many mobile and some home providers share one internet address among many customers, and then nothing you set up in the router can be reached from outside. The first thing to try is IPv6: when the PC has an IPv6 address, the code from Share Manager already carries it, and it can reach the PC where the router setting cannot. A VPN to your home network still works; the [exchange server](exchange-server.html) is the way meant for this case: every device connects out to it, so nothing has to be set up in the router. How a folder opens through it, step by step: [Folders through the exchange server](folders-exchange-server.html).
</div>

## Step-by-step guides

- [Connecting SMB/Windows shares](../../documentation/network/smb-samba-shares.html)
- [Connecting SFTP and FTP servers](../../documentation/network/sftp-ftp-servers.html)
- [Sharing PC folders with Fast Media Sorter & Sharing for Windows](../../documentation/network/windows-companion.html)
- [Turning the phone into an SFTP server](../../documentation/network/phone-as-sftp-server.html)
- [The phone as a server: how long it lasts, launcher mode and mobile data](phone-sharing.html)
- [Folders through the exchange server](folders-exchange-server.html)
- [Adding network folders and cloud storage as sources](../../documentation/storage/network-and-cloud-sources.html)
- [Publishing PC folders to Android](https://serzhyale.github.io/FastMediaSorter_Lite/publish-folders-android.html), on the Windows product's site

Back to the [portal start page](index.html).

## Settings windows of the Windows program {#windows-settings-windows}

First work out how the devices are connected: directly to this PC's shared folders, or through an account on an exchange server. For the direct way, open Network and Hosting in **SFTP sharing settings**. For an account on an exchange server, open **Exchange account** in **Manager settings**. For the exchange server installed on this PC, open **Exchange server settings**. Installed, switched on, running and reachable are four different states: opening a settings window switches nothing on and starts nothing.
