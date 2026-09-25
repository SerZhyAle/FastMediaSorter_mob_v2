---
page_id: network.phone-as-sftp-server
title: Turning the Phone into an SFTP Server
nav_title: Sharing folders from this phone
description: How to share folders from this phone itself, so another device on the network can browse, download, upload, rename and delete inside them over SFTP - picking folders with the system folder picker, signing in with a password or an SSH key, and pairing another FastMediaSorter with a QR code.
category: Network & Cloud
category_slug: network
ticket: S2950
flavor: All editions except Lite; needs Android 8.0 or newer.
recipe_number: "04"
canonical_url: documentation/network/phone-as-sftp-server.html
why: |
  Usually this app reaches out to a folder somewhere else - a PC, a NAS, a cloud account. Sometimes it is the other way around: the photos worth sharing are on the phone, and it is another device that should reach in and get them. Turn on the phone's own [SFTP](term:sftp) server, pick the folders to share, and any device that speaks SFTP - including another phone running this app - can open them.
ingredients:
  - "This app in any [edition](term:edition) except Lite, on Android 8.0 (Oreo) or newer."
  - "The folders you want to share, picked through the system's own folder picker."
  - "Either a password (the app can generate one) or the public half of an SSH key from whoever will connect."
  - "For pairing another FastMediaSorter with a scan: a camera on the connecting device."
steps:
  - number: 1
    id: turn-on
    title: Turn the server on
    text: |
      Open **Settings**, the **General** tab, and find the **Share over SFTP** card. Switch on **SFTP server** - "Other devices on your network can open the folders you pick" - and the status line below moves from **Off** to **Starting..** and then to **Running at 192.168.1.42:2222**, its address and port. Turning it on with nothing shared yet, or with no way to sign in configured, is caught before it starts: "Add at least one folder to share, then turn the server on" or "Add a public key or switch to password login, then turn the server on."

      A phone with no Wi-Fi connection can still switch the server on, but it says so plainly: "Running, but the phone is not on Wi-Fi - other devices cannot reach it yet." A foreground notification, "SFTP server is on", stays up the whole time with the address to connect to and a one-tap **Stop** button.
  - number: 2
    id: pick-folders
    title: Choose which folders to share
    text: |
      Under **Shared folders**, tap **Add folder** to open the system's own folder picker and choose a folder on the phone or its memory card. Until you add one, the section simply reads "No folders yet - add one to share it." Each folder you add gets its own row with a remove button next to it, and the grant survives a restart, so the same folders are still shared after the phone reboots.

      A folder you add while the server is already running joins it immediately - no need to turn the server off and on again for a new folder.
    image:
      src: assets/images/network/phone-as-sftp-server-shared-folders.png
      alt: The Share over SFTP card's Shared folders section with two folders added and their remove buttons, and the Add folder button
      caption: "Picking which folders the server shares."
  - number: 3
    id: choose-login
    title: Decide how people sign in
    text: |
      Right below the folder list, choose **Password** or **SSH key**. With **Password**, the app already generated one; tap **New password** any time to replace it. With **SSH key**, paste the public keys of whoever should connect into **Public keys, one per line** - the phone never needs their private key, only what proves it.

      While the server is running, the current sign-in details are shown right there: "Login: fms, password: 7f3kQ2mN" or "Login: fms, sign in with your SSH key" - so you can read them off to whoever is connecting without hunting for them elsewhere. The login name itself is fixed; only the password or the accepted keys change.
    image_bookmark:
      shot_id: network.phone-as-sftp-server-auth-mode
      device_profile: phone
      screen_state: settings-sftp-server-auth-password-mode
      alt: The Share over SFTP card's authentication section with Password selected, the New password button, and the running credentials line showing the login and password
      caption: "Password sign-in, with the current password shown while the server runs."
      title: "Screenshot: SFTP server sign-in mode"
      desc: Settings, General tab, Share over SFTP card, Password mode selected, server running, credentials line visible.
  - number: 4
    id: change-while-running
    title: Change a setting without losing the connection
    text: |
      The **Port** field defaults to a free one and can be changed - a port already used by another app is refused, for example "Port 2222 is taken by another app - choose another port", and a value outside the allowed range with "Use a port from 1024 to 65535." Changing the port, the sign-in mode or the keys while the server is already running does not restart it on its own: "Turn the server off and on again to apply the change" - so a client already connected is not dropped by a setting you are still adjusting.
  - number: 5
    id: pair-by-qr
    title: Pair another FastMediaSorter with a scan
    text: |
      While the server is running, tap **Show pairing code** to reveal a QR code - "Pairing code: scan it in FastMediaSorter on another device" - and **Hide pairing code** to put it away again. The code is rebuilt fresh every time you show it, so it always carries the server's current address and password.

      On the other device, open **Add Resource**, the **SFTP / FTP** card, and tap **Import by barcode** - the same scanner that reads a [Windows companion](term:windows-companion) code also recognizes this one. A message confirms the form was filled in: "Connection details filled in from the pairing code - check them and save," with the host, port, username, password or key and the server's host key already in place, suggested as "Device server 192.168.1.42." Review it and save - the host key is pinned from that first scan, so a later attempt from a different machine using the same address is refused rather than silently accepted. A damaged or incomplete code is called out too: "This code is not a complete FastMediaSorter pairing code - show it again on the server phone and rescan."
    image_bookmark:
      shot_id: network.phone-as-sftp-server-pairing-qr
      device_profile: phone
      screen_state: settings-sftp-server-pairing-qr-visible
      alt: The Share over SFTP card with the pairing QR code shown and the Hide pairing code button
      caption: "The pairing code, ready to scan from another device."
      title: "Screenshot: SFTP server pairing QR"
      desc: Settings, General tab, Share over SFTP card, server running, pairing QR code revealed below the Hide pairing code button.
outcome: |
  The phone shares exactly the folders you picked, with everyone else needing either a password or an accepted SSH key to get in, and pairing another FastMediaSorter takes one scan instead of typing an address by hand. A tap on Stop, in the app or the notification, ends it.
tips:
  - "**Bringing in a PC's folders instead?** [Sharing PC folders with Fast Media Sorter for Windows](page:network.windows-companion) covers the same pairing idea from the other side."
  - "**Setting up a server by typing its address yourself?** [Connecting SFTP and FTP servers](page:network.sftp-ftp-servers) covers that manual route, on either end of the connection."
  - "**New to SSH keys?** The [OpenSSH](https://www.openssh.com/) project's own documentation explains how a key pair works if the wording here is unfamiliar."
next_recipes:
  - title: Sharing PC Folders with Fast Media Sorter for Windows
    url: page:network.windows-companion
    badge: Network
    badge_type: docs
    description: The same idea in reverse - a Windows PC sharing folders to this phone.
  - title: Connecting SFTP and FTP Servers
    url: page:network.sftp-ftp-servers
    badge: Network
    badge_type: docs
    description: Add an SFTP or FTP server folder by hand, with a password or an SSH key.
  - title: Adding Network Folders and Cloud Storage as Sources
    url: page:storage.network-and-cloud-sources
    badge: Storage
    badge_type: other
    description: The overview of every kind of network and cloud place this app can open.
---

Share folders straight from this phone over SFTP - pick them with the folder picker, sign in with a password or an SSH key, and pair another FastMediaSorter in one scan instead of typing an address.
