---
page_id: network.smb-samba-shares
title: Connecting SMB/Windows Shares
nav_title: Connecting SMB shares
description: How to share a folder on Windows, find it from the app by scanning your network, sign in and test the connection, what the app quietly does to pick good transfer settings, and what a "cannot connect" message actually means.
category: Network & Cloud
category_slug: network
ticket: S2950
flavor: Network folders (SMB) - every edition except Lite. Finding a computer automatically, listing its shares, testing the connection and the quiet speed tuning - Standard, noLegal, Photos, Legacy and VR (not Lite, not FOSS).
recipe_number: "01"
canonical_url: documentation/network/smb-samba-shares.html
why: |
  [Adding a network folder as a resource](page:storage.network-and-cloud-sources) takes a few taps when everything lines up: the computer is awake, the password is right, the share name is spelled the way the server expects. This page is for the rest of the time - you are not sure of the computer's address, you typed the share name wrong last time, or the folder that worked yesterday says it cannot connect today.

  It also starts one step earlier than the overview: a folder has to be shared on the Windows side before the app can find it at all.
ingredients:
  - "FastMediaSorter in any [edition](term:edition) except Lite - see [The seven editions](page:flavors.overview-and-comparison)."
  - "A Windows PC or NAS with a folder already shared on it, or a few minutes to share one - step 1 below."
  - "The phone and the computer on the same Wi-Fi or network."
  - "The user name and password of the shared folder (on a home PC this is usually your own Windows account)."
steps:
  - number: 1
    id: share-on-windows
    title: Share a folder on Windows first
    text: |
      The app can only see a folder that Windows has already offered to the network. On the PC, right-click the folder, open **Properties**, go to the **Sharing** tab and share it with a specific user or with everyone on the network. Microsoft's own walk-through covers every version of Windows: [File sharing over a network in Windows](https://support.microsoft.com/en-us/windows/experience/connectivity-networking/file-sharing-over-a-network-in-windows).

      A NAS box shares folders the same way, from its own web interface instead of Windows - the wording differs by brand, but the idea (pick a folder, give it a name, decide who may open it) is identical.
    callout:
      type: tip
      title: Prefer not to touch Windows sharing at all?
      text: "[Fast Media Sorter for Windows](term:windows-companion) shares chosen folders in a couple of clicks and pairs by QR code, without turning on Windows' own network sharing. See [Sharing PC folders with Fast Media Sorter for Windows](page:network.windows-companion)."
  - number: 2
    id: find-and-add
    title: Find your computer and add the shared folder
    text: |
      On the main screen tap **Add**, then **Network Folder** - "Add SMB network shares". On the **Create Network Resource (SMB)** screen tap **Scan Network**: the app scans your [network](term:network-folder) subnet for SMB, FTP and SFTP hosts while "Scanning local subnet.." shows, then "Scan complete" and a list of the devices it found. Tap yours.

      With the **Server IP** field filled in (or typed by hand, for example `192.168.1.100`), enter **Username** and **Password**, then tap **Scan host**. The app lists the shared folders under "Shares on <computer name>" - tick the one you want. If nothing turns up, "No shares found on this server. Close this message and enter the share name manually." tells you exactly what to do next; if the scan itself fails, "Couldn't scan the shares. Try again." means just that - no shares were read yet, nothing is wrong with the folder itself.
    image:
      src: assets/images/network/smb-samba-shares-network-discovery.png
      alt: The Network Discovery dialog listing computers found on the local network after tapping Scan Network
      caption: "Scan Network finds computers on your Wi-Fi for you."
  - number: 3
    id: manual-entry
    title: Or type everything by hand
    text: |
      Scanning needs the phone and computer on the same network segment, and it is not offered in the FOSS edition, where every field is filled in yourself. Either way the fields are the same:

      - **Server IP** - the computer's address, for example `192.168.1.100`.
      - **Username** and **Password** of the shared folder, and **Domain (optional)** only if your network uses one ("Leave empty if not needed").
      - **Port** - leave it at its default (`445`) unless you were told otherwise.
      - **ShareName/subfolder** - the exact name Windows gave the share, for example `Common` or `Photos`.
      - **Resource Name** - what the folder is called on your main screen; leave it empty and the app names it for you.
  - number: 4
    id: test-connection
    title: Test the connection before you commit
    text: |
      **Test connection** checks the address, user name and password without adding anything, and answers in a moment - it does not just say "yes" or "no". A wrong password reports "authentication failed", a wrong or missing share name reports "share not found", and a computer that simply cannot be reached reports a timeout - three different problems, three different fixes, instead of one flat error.

      When everything checks out, tap **Add This Resource** to add it to the list below, or add several shares from the same computer before tapping **Add to Resources** once for all of them.
    image:
      src: assets/images/network/smb-samba-shares-test-result.png
      alt: The Create Network Resource (SMB) screen with the Connection Test - Failed dialog saying the app could not connect and asking to check the details
      caption: "A failed test tells you so before the resource is added."
  - number: 5
    id: speed-tuning
    title: Why you never have to tune transfer speed by hand
    text: |
      Right after a network folder is added, the app quietly measures how fast it can read and write to it and picks the number of parallel transfer threads and the buffer size that suit that particular computer - nothing to watch, nothing to tap. A fast NAS on a good connection gets more parallel threads; a slow or congested one gets fewer, so copying does not choke it. You can still set the number of threads by hand in **Settings**, but there is normally no need to.
  - number: 6
    id: when-it-goes-wrong
    title: When a shared folder cannot be reached
    text: |
      Opening a file from a resource that has gone offline no longer stops you with a raw error. It shows **Resource unavailable**: "<file> is on a resource that is not responding right now. Check the connection and try again." with **Retry** and **Cancel** - the computer is probably asleep or the phone left the home Wi-Fi. If the server itself says the file no longer exists, the dialog says **File is gone** instead, with an option to remove it from favorites - the app never offers to remove a resource just because it is temporarily unreachable.

      A share whose name contains a space, for example `My Photos`, opens and closes cleanly too, the same as one without.
    image_bookmark:
      shot_id: network.smb-samba-shares-unreachable-dialog
      device_profile: phone
      screen_state: player-resource-unavailable-dialog
      alt: The Resource unavailable dialog with Retry and Cancel, shown after opening a file whose network resource is not responding
      caption: "A calm message, with Retry right there."
      title: "Screenshot: Resource unavailable dialog"
      desc: Player screen, file open attempted while the SMB resource is offline, Resource unavailable dialog shown.
  - number: 7
    id: smb-vs-others
    title: How SMB compares to FTP and SFTP, in plain terms
    text: |
      [SMB](term:smb) is what Windows and most NAS boxes speak natively - you share a folder, and any computer or phone on the same network can browse it, no server address to remember beyond the computer's own name or IP. It only works on your local network, though: point it at a server on the internet and it will not connect.

      [FTP](term:ftp) and [SFTP](term:sftp) reach a server by its address instead of a share name, and they work just as well over the internet as on a home network - useful for a server that lives outside your house, or a NAS with FTP turned on instead of SMB. SFTP encrypts everything it sends; plain FTP does not, so use it only where SFTP is not offered. The full walk-through, including signing in with an SSH key, is in [Connecting SFTP and FTP servers](page:network.sftp-ftp-servers).
outcome: |
  The computer or NAS shows up on your main screen the moment you scan for it, a wrong password or a missing share tells you exactly what to fix, transfer speed tunes itself, and a resource that briefly drops off the network says so calmly instead of throwing an error at you.
tips:
  - "**Windows asks you to sign in and you are not sure with what.** Use the same user name and password you use to log into that Windows PC, not your Microsoft account email, unless you specifically created a separate share user."
  - "**The share works from a computer but not from the phone.** Confirm both are on the same Wi-Fi network, not one on Wi-Fi and one on mobile data, and that the PC has not gone to sleep."
  - "**Want the caching and background-refresh details?** They are the same for every kind of network resource - see [Adding network folders and cloud storage as sources](page:storage.network-and-cloud-sources)."
  - "**Sharing your whole resource list, passwords included, with another phone?** See [Sharing and backing up your resources](page:storage.sharing-and-backing-up-resources)."
next_recipes:
  - title: Connecting SFTP and FTP servers
    url: page:network.sftp-ftp-servers
    badge: Network
    badge_type: docs
    description: Server addresses, SSH keys, and pinning a server's identity.
  - title: Sharing PC folders with Fast Media Sorter for Windows
    url: page:network.windows-companion
    badge: Network
    badge_type: docs
    description: Share PC folders without touching Windows' own network sharing at all.
  - title: Adding network folders and cloud storage as sources
    url: page:storage.network-and-cloud-sources
    badge: Storage
    badge_type: other
    description: The overview - what kinds of places you can add, and how the app keeps them quick.
---

Share a folder on Windows, find it from the app with a network scan, sign in and test the connection before committing, and read what a "cannot connect" message actually means - the full walk-through behind [adding a network folder](page:storage.network-and-cloud-sources) as a [resource](term:resource).
