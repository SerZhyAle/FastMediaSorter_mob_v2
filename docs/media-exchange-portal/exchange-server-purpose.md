---
layout: doc
title: "What the exchange server is for and everything you can do with it"
description: "What an exchange server is for, in plain sentences, and the full list of what you can do with it: open folders from any network, give a folder to a guest, put a watch on your account, broadcast and cast, send the clipboard and schedule copies."
category: "Media exchange"
permalink: /docs/media-exchange-portal/exchange-server-purpose.html
---
<sub class="doc-stamp">26.10.20 12:03</sub>

# What the exchange server is for

<img src="exchange-mark/full-256.png" alt="Media Exchange Server" width="128">

An exchange server connects your own devices when they are on different networks. It gives all of them one account and one list of folders and broadcasts. Nothing is opened in a router on the devices' side, because every device dials out to the server.

To run one, see [Deploy your own exchange server](exchange-server-quick-start.html). How it works, what it sees and what it keeps: [Exchange server](exchange-server.html).

## When you need it {#when-you-need-it}

- Your provider gives you no public address (carrier NAT), so no forwarded port can work.
- You are on a hotel Wi-Fi or a mobile network and want a folder from home.
- You want one account for the phone, the tablet, the TV, the watch and the PC.
- You want to give a relative or a friend a folder or a broadcast without opening your home network.

## Everything you can do {#scenarios}

### Folders {#scenarios-folders}

- **Open the folders of a phone on another device.** A phone shares a folder, and any other device of the account opens it from any network. See [Folders through the exchange server](folders-exchange-server.html).
- **Open the shared folders of a home PC from anywhere.** A Windows PC publishes the folders it shares, and the phone opens them from mobile data with no port forwarded. See [Windows servers](windows-servers.html).
- **Run a schedule over folders of other devices.** A phone fetches every new photo of another one overnight, by copy or by move. See [A schedule over folders of other devices](schedule-remote-folders.html).
- **Give a folder to a guest.** The guest registers with an invitation of the kind **Guest** and sees only that folder. See [People outside your account](exchange-accounts.html#outside).
- **Give a folder to a person with no account.** Send a link with a password and tell the password another way. See [People outside your account](exchange-accounts.html#outside).

### Broadcasts {#scenarios-broadcasts}

- **Go on air from a phone.** Broadcast sound, the camera, the screen or a ready file. Listeners on the same Wi-Fi connect directly, and listeners on any other network connect through the server. See [Going on air](broadcast-start.html).
- **Go on air from a watch.** The watch broadcasts its microphone to up to four listeners at once. See [Going on air](broadcast-start.html).
- **Open a broadcast of another of your devices.** It is listed under **Broadcasts of my devices** and plays in **Streams**. See [Receiving a broadcast](broadcast-receive.html).
- **Cast a broadcast to your own device.** One device asks another to play it, and the receiving device asks before it plays. See [Receiving a broadcast](broadcast-receive.html).
- **Give a broadcast to someone outside your account.** The same two ways as for a folder: a guest or a link with a password. See [People outside your account](exchange-accounts.html#outside).

### The watch {#scenarios-watch}

- **Put a watch on your account.** The phone hands the watch a one-use code, and the watch then works on its own Wi-Fi or LTE. See [The watch on your account](watch-exchange.html).
- **Send files from the watch.** **Send to..** puts a recording or a picture into the folders of the account, with no phone involved. See [The watch on your account](watch-exchange.html).

### The clipboard {#scenarios-clipboard}

- **Move what you copied to another device.** Press **Send the clipboard to the server** on one device and **Get the clipboard from the server** on another. See [Clipboard on the server](exchange-clipboard.html).

### Accounts and devices {#scenarios-accounts}

- **Let relatives and friends use your server.** You make an invitation for each person and choose its kind: own device or family, or guest. See [Accounts, invitations and passwords](exchange-accounts.html#invitation).
- **Add a new device.** Paste the connection string, scan its QR code or choose its file, then sign in. A pairing code replaces the password on a device where typing one is hard. See [Adding your exchange server in My devices](my-devices.html).
- **Cut off a lost or sold device.** Change the password from another device and revoke the lost one. See [A lost or sold device](exchange-accounts.html#lost-device).
- **Leave an account.** Sign out on one device, or delete the account and everything it holds on the server. See [How to delete an account](account-deletion.html).

## What the server is not for {#not-for}

- **It is not storage.** Your files pass through a tunnel and never touch the server's disk.
- **It cannot read what goes through it.** Folders, broadcasts and the clipboard are encrypted on your device.
- **It is not a public service.** Nobody runs a server for everybody: you run your own, or you join the one of a person you know.
- **It is not needed on one Wi-Fi network.** Your devices connect to each other directly there.

## Ways that need no server {#no-server}

- [Home network](home-network.html) - a forwarded port and a public address or a dynamic DNS name.
- [Files and folders via Google Drive](folders-google-drive.html) - between your own devices, with no server and no address.
- [Clouds](clouds.html) - Google Drive, Dropbox and OneDrive as a shared box.
- [Windows servers](windows-servers.html) - the Server edition shares a PC's folders around the clock with no exchange server.

## Next {#next}

- [Deploy your own exchange server](exchange-server-quick-start.html)
- [Installing and checking your exchange server](exchange-server-install.html)
- [Accounts, invitations and passwords](exchange-accounts.html)

Back to the [exchange server](exchange-server.html) or to the [portal start page](index.html).
