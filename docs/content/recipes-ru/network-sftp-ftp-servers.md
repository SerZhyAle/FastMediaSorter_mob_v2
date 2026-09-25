---
page_id: network.sftp-ftp-servers
title: Connecting SFTP and FTP Servers
nav_title: Connecting SFTP and FTP
description: Adding a server by address, signing in with a password or an SSH key, letting the app remember and re-check the server's identity, sharing access to a folder as a file, importing one someone sent you, and testing the connection from your watch.
category: Сетевые папки и облака
category_slug: network
ticket: S2950
flavor: SFTP and FTP - every edition except Lite. Signing in with an SSH key, pinning the server and sharing or importing access files - Standard, noLegal, Photos, Legacy and VR (not Lite, not FOSS); the watch's own connection test - Standard, noLegal, Lite, Photos, Legacy, VR and FOSS.
recipe_number: "02"
canonical_url: documentation/network/sftp-ftp-servers-ru.html
why: |
  A home NAS with SMB turned off, a server that only opens once you are outside your own house, a friend's computer whose folder they want to hand you without reading out a password over the phone - these are jobs for [FTP or SFTP](term:sftp) rather than [SMB](term:smb). Unlike a shared Windows folder, an FTP or SFTP server is reached by its address from anywhere, which is exactly what makes it worth setting up carefully: with SFTP, the app can also confirm it is still talking to the same server every single time, not just the first.

  If you have not decided between SMB and SFTP yet, [Connecting SMB/Windows shares](page:network.smb-samba-shares) has a short comparison at the end.
ingredients:
  - "FastMediaSorter in any [edition](term:edition) except Lite - see [The seven editions](page:flavors.overview-and-comparison)."
  - "The server's address and port, and either a user name with a password or an SSH key."
  - "For key sign-in: the private key file, and its passphrase if it has one."
  - "To share or import access to a folder: any app that can send a file, such as Telegram or email."
steps:
  - number: 1
    id: add-a-server
    title: Add a server by address
    text: |
      On the main screen tap **Add**, then **SFTP / FTP** - the **Add S/FTP Resource** screen opens. Under **Select Protocol:** choose **SFTP (port 22)** or **FTP (port 21)**; the **Port** field fills itself in, with a hint of "SFTP: 22, FTP: 21" if you change your mind. Enter the **Host** (the server's address) - the field itself explains it wants a "Server IP address", though a domain name works too.

      SFTP encrypts the whole conversation; plain FTP does not, so pick FTP only when a server does not offer SFTP at all.
    image:
      src: assets/images/network/sftp-ftp-servers-add-form.png
      alt: The Add S/FTP Resource screen with the SFTP/FTP protocol switch and the Host and Port fields filled in
      caption: "Pick a protocol, then the server's address."
  - number: 2
    id: sign-in
    title: Sign in with a password or an SSH key
    text: |
      Under **Sign-in method** choose **Password** or **SSH Key**. Password sign-in just needs **Username** and **Password**, the same as any other account.

      For **SSH Key**, paste the key into **Private SSH key** or tap **Load File** to pick the file from storage, and fill in **Key Passphrase (optional)** if the key itself is encrypted - leave it empty otherwise. A key that will not parse says "Invalid SSH private key format" right away, before you waste a connection attempt on it. If your device already came with some server folders pre-configured through a bundled setup file, those can use a key the same way, with nothing left for you to type.
    image:
      src: assets/images/network/sftp-ftp-servers-ssh-key-auth.png
      alt: The SSH Key sign-in section with the Private SSH key field, Load File button and Key Passphrase field
      caption: "Paste a key or load it from a file."
  - number: 3
    id: test-and-trust
    title: Test the connection, and let the app remember this server
    text: |
      Tap **Test Connection**. The first time an SFTP server answers, its identity is worth pinning: open **Server verification (optional)** - "Optional security that confirms the server is genuine" - where the app can show "Server host key fingerprint: <fingerprint>" with a **Pin Host Key** button, or you can paste one yourself into **Expected host key fingerprint (SHA256, optional)** if you already know it. Pinning it means "Server host key fingerprint pinned" and the app will only ever trust that one key for this resource from now on.

      This step is optional by design - most people can skip it and just add the resource - but it is the one thing that turns "probably the right server" into "definitely the right server."
    image:
      src: assets/images/network/sftp-ftp-servers-host-key-pin.png
      alt: The Connection Test - Success dialog for an SFTP server, showing the server host key fingerprint and the Pin Host Key button
      caption: "Pin the server's identity once, on the first connection."
    callout:
      type: tip
      title: In plain words, what a host key fingerprint is
      text: "Every SFTP server has its own private identity key, and the fingerprint is a short code computed from it. Pinning it is like remembering a face instead of just a name - if someone else's server later answers under the same address, the fingerprint gives it away."
  - number: 4
    id: checked-every-time
    title: The server's identity is checked every time, not just once
    text: |
      A pinned host key is verified on every connection this resource makes from then on - browsing, thumbnails, playback and its reconnects alike, not only when you press Test Connection. If a server ever answers with a different key than the one you pinned, the app refuses it outright: "This shared folder's server looks different from the one you paired with. Nothing was loaded, to keep you safe." That is what a genuine man-in-the-middle attempt looks like from the inside, and it is also exactly what happens, harmlessly, if a server was reinstalled and issued itself a new key - re-pin it once you are sure it is really the same place.
  - number: 5
    id: share-access
    title: Send someone access to a folder you already added
    text: |
      Open an SFTP resource's menu and tap **Share access..**. On the **Share SFTP access** screen, "This file contains the access password. Send it only to people you trust." - tick **Do not include the password** to leave it out and tell the other person separately instead. If the server only answers on your home network, the screen adds "This server is on your local network - the recipient can connect only from the same network."

      Tap **Share** and the system share sheet opens - send the file over Telegram, email, or anything else installed on your phone.
    image:
      src: assets/images/network/sftp-ftp-servers-share-access.png
      alt: The Share SFTP access screen with the password warning, the Do not include the password checkbox and the Share button
      caption: "Share access to a folder, password optional."
  - number: 6
    id: import-shared-access
    title: Add a folder someone shared with you
    text: |
      Tap a received access file, in Telegram or in an email attachment, and **Import access** opens on its own, asking to add the SFTP resource with however many folders it carries; if the file left the password out, you are asked for it there. Tap **Import** and the resource is ready to open, no address or key to type by hand.

      You can start the same import deliberately, too: the Add Resource screen and the SFTP form itself both offer **Import from file** next to the usual resource cards, and **Import by barcode** to scan a QR code with the camera instead, when there is no file to open.
    image:
      src: assets/images/network/sftp-ftp-servers-import-access.png
      alt: The Import access dialog confirming the SFTP resource name, server and folder count, with a password field shown
      caption: "One tap on a received file adds the resource."
    callout:
      type: tip
      title: This is also how a shared PC folder arrives
      text: "A folder shared from [Fast Media Sorter for Windows](term:windows-companion) uses the same file format and the same import dialog - see [Sharing PC folders with Fast Media Sorter for Windows](page:network.windows-companion)."
  - number: 7
    id: quietly-resilient
    title: A few things the app handles quietly
    text: |
      - **A folder scan that cannot finish says so.** Scanning an SFTP folder ends in bounded time even when the network changes mid-scan or a connection goes half-open - "The folder is taking too long to load. Check your network and try again." instead of a spinner that never stops.
      - **A server that was just unreachable is not retried the hard way every time.** For a short cooldown after a failed connection, the app answers fast instead of making every thumbnail or file wait through a full connection timeout again.
outcome: |
  A server you reach by address - on your own network or anywhere on the internet - opens with a password or a key, its identity is confirmed once and checked on every visit after that, and handing someone else access to a folder, or receiving access to theirs, is one shared file away.
tips:
  - "**Not sure whether you need SMB or SFTP?** [Connecting SMB/Windows shares](page:network.smb-samba-shares) compares the two from a user's point of view."
  - "**Want the phone to be the server instead?** See [Turning the phone into an SFTP server](page:network.phone-as-sftp-server)."
  - "**Testing an SFTP or FTP resource from your watch?** The watch's own **Test** button really connects and reports the server's actual reason on failure - see [Opening the phone's and network folders on the watch](page:wear.phone-and-network-folders-on-watch)."
  - "**Everything in one move to a new phone.** Resources added here, including keys and pinned fingerprints, travel in the same backup as the rest of your resources - see [Sharing and backing up your resources](page:storage.sharing-and-backing-up-resources)."
next_recipes:
  - title: Connecting SMB/Windows shares
    url: page:network.smb-samba-shares
    badge: Network
    badge_type: docs
    description: Share a folder from Windows and see how SMB compares to SFTP and FTP.
  - title: Turning the phone into an SFTP server
    url: page:network.phone-as-sftp-server
    badge: Network
    badge_type: docs
    description: Share folders from your own phone instead of connecting to someone else's.
  - title: Sharing PC folders with Fast Media Sorter for Windows
    url: page:network.windows-companion
    badge: Network
    badge_type: docs
    description: The PC-side companion that publishes folders as ready-made SFTP resources.
---

Add an FTP or SFTP server by address, sign in with a password or an SSH key, let the app confirm the server's identity once and re-check it on every visit, and hand a folder to someone else - or receive one from them - as a single shared file. Testing the same kind of resource from a paired [watch](term:watch) is covered too.
