<sub class="doc-stamp">26.10.22 00:33</sub>

# Media exchange and broadcasting portal - illustration set

Status: **picture set selected; the portal pages are written** (tickets S4140, S4243 and S4242). The pages are
`index`, `home-network`, `exchange-server`, `exchange-server-purpose`, `exchange-server-quick-start`,
`exchange-server-install`, `exchange-accounts`, `exchange-clipboard`, `account-deletion`, `live-broadcast`, `clouds`, `editions` and `windows-servers`, and
the eight scenario pages `folders-exchange-server`, `folders-google-drive`, `broadcast-start`,
`broadcast-receive`, `watch-exchange`, `schedule-remote-folders`, `phone-sharing` and `my-devices`,
each in English, Russian (`-ru`) and Ukrainian (`-uk`); they embed the pictures listed below, and each
page has its owner in `scripts/docs/portal-sections.json`.

This folder holds the illustrations for the shared portal about media exchange and live broadcasting.
Every product that takes part - FastMediaSorter for Android (phone, watch, TV, VR), Fast Media Sorter for
Windows and StreamsPlayer - links to that one portal. This file is the picture brief: what each image
shows, where it came from, what it may and may not claim, and where it belongs on a page.

## Folder layout

- `diagrams/` - the generated set, 17 pictures in English, Russian and Ukrainian (`<name>-<lang>.svg`).
  Render targets: never edit them, change the drawing or the labels and re-render.
- `labels/labels.<lang>.json` - every word on the generated pictures, one file per language.
- `exchange-mark/` - vendored identity artwork of the Media Exchange Server per contract `EXCHANGE-MARK` 0.3 (`mark-24.png`, `mark-32.png`, `full-256.png`, `banner-1200x630.png`). Byte-exact copies from the catalog; never scaled or altered.
- Drawing code: `scripts/docs/render-usage-schemes.ps1` (driver), `scripts/docs/usage-schemes/SvgKit.ps1`
  (primitives), `scripts/docs/usage-schemes/Diagrams.ps1` (one function per picture).
- The pages use the pictures by relative path - `diagrams/<name>-<lang>.svg` - and take the picture's `*.desc` label as the base of the alt text. The page
  texts belong to the portal, not to this file; renaming a picture here breaks every page that embeds it,
  so a name never changes once a page uses it.

## Regenerating

- `pwsh -NoProfile -File scripts/docs/render-usage-schemes.ps1` - render all languages.
- `pwsh -NoProfile -File scripts/docs/render-usage-schemes.ps1 -Check` - exit 1 when a picture is stale.
- A new language is one more `labels.<lang>.json` with the same keys; the renderer refuses a file with a
  missing or extra key.

## Facts the pictures rest on

- Contract `DEVICE-EXCHANGE` (shared contracts catalog): accounts, enrolled devices, the directory, tunnels,
  the fan-out relay, Cast, what the server keeps on disk and in memory.
- Contract `ANYWHERE-ACCESS`: the SFTP share, the local-first address order.
- Contract `LIVE-BROADCAST`: broadcast modes, AAC and H.264 in MPEG-TS, the watch cap of four listeners.
- Shipped Android records in `docs/ALL_FEATURES.jsonl`: the embedded SFTP server, access from anywhere through
  the exchange server, companion import by file and QR, the auto-reachable companion address, phone and watch
  broadcasts, sending a broadcast to the watch.
- Shipped Windows product (release `26.9.20.1206` and before), checked against its `CHANGELOG.md`:
  - Share Manager and the bundled SFTP worker serve the folders you add - each with its own name, read-only
    or writable, optional PIN and media-type filters. It ships off: nothing listens until the owner turns
    server features on (one visible UAC prompt for one program-scoped firewall rule).
  - One pairing code (QR, `.fmscfg` file, text code) carries every address of the PC: the local Wi-Fi/LAN
    one, an IPv6 one (the way in behind carrier NAT, where port forwarding cannot work) and the internet
    one (public IP or a dynamic DNS name plus the forwarded port). The Android app races them and prefers
    the fast local one. The host key is pinned at the first pairing and never regenerated.
  - A phone on the same Wi-Fi finds the PC again by host-key fingerprint (mDNS) when its address changed.
  - The listening port is chosen once and then never moves on its own.
  - Router help: automatic port opening through UPnP / NAT-PMP when the router allows it, then a check of
    the port from the internet that says plainly whether it answers; manual forwarding stays the reliable
    route.
  - The Server edition runs the same worker as a Windows service that starts at boot; the full package
    can switch itself to that mode. The 32-bit fallback viewer cannot share.

## Windows behaviour the pictures show

Every picture shows shipped behaviour, and the pages describe it as available. Owner statement, 2026-10-21:
nothing in the family is held back.

- Broadcasting from a Windows PC (`broadcast-simple` and `broadcast-cast`): Fast Media Sorter & Sharing for Windows broadcasts its screen, camera, microphone and
  system sound. Live broadcasts play on Windows in the viewer and in StreamsPlayer. No page promises a delay.
- The exchange server (a Go program, `fms-exchange-server`, administered in Share Manager) is an optional
  part of both Windows starts; its wire is the contract `DEVICE-EXCHANGE` with `ANYWHERE-ACCESS`. No public
  deployment exists by decision: each person runs a server, or joins one that a person they know runs.
- A Windows PC as a member of the exchange - the home and office PCs of
  `exchange-simple`: the PC enrols into an account,
  publishes its shares and opens other devices' folders.
- Cast between devices of one account, per the same contract.
- A Windows laptop opens the home storage (`home-simple`) as a built-in resource of the viewer, or with any
  SFTP client.

## Addresses on the pictures

All addresses come from documentation ranges and never point at a real host:

- `198.51.100.7:443` - the exchange server.
- `203.0.113.25` and `myhome.example.net` - the home router's public address.
- `192.168.1.0/24` - the home network; `.20` the media PC, `.42` the phone acting as a server.
- `2001:db8:25::20` - the media PC's IPv6 address (RFC 3849 documentation prefix), in `home-pairing`.
- `D:\Media` - the shared folder on the media PC.

## Selection

The first selection compared picture by picture the 13 generated here with the 15 in
`P:\WINDOWS\FastMediaSorter_Lite\assets\diagrams` (Fast Media Sorter & Sharing for Windows, 2026-10). `home-pairing`
was added afterwards (picture 15).

- **Selected for the portal: 8** generated pictures in three languages, plus the phone form of the cover.
- **Rejected: 12** pictures of the Windows set, reasons below.
- **Removed by S4415:** `three-ways`, the detailed `exchange-scheme`, `broadcast-scheme` and `home-scheme`,
  the reserve `exchange-steps` and `broadcast-order`, and the three Russian Windows cards - each repeated
  another picture of the same page.

### The rule of the set

- One idea per picture: a page never shows two pictures of the same connection.
- The picture shows who connects to whom and names the devices; explanations, conditions and the names of
  technologies live in the page text, where they are translated, searched, read aloud and fitted to the
  screen.
- A picture no page shows is deleted together with its drawing and its labels in three languages.

### Proposed order on the portal page

1. Cover - `ecosystem-hero`, with `ecosystem-hero-phone` at 768 pixels and below.
2. Exchange server - `exchange-simple`, then `exchange-keeps`.
3. Live broadcast - `broadcast-simple`, then `broadcast-cast`.
4. Home network - `home-simple`, then `home-pairing`, then `home-steps`.
## Selected pictures

### 1. ecosystem-hero - your files on all your devices

- Files: `diagrams/ecosystem-hero-en.svg`, `-ru.svg`, `-uk.svg`.
- Style: presentation slide, 1920 x 1080, dark brand gradient with the waves-and-particles motif.
- Shows: six devices on an oval around a glowing hub - phone, watch, TV, VR headset, laptop, Windows PC -
  joined to the hub by dotted lines coloured by role. The router and the phone acting as a server are means,
  not devices a person opens files on, so the cover leaves them to the section pictures.
- Title: the benefit, "Your files on all your devices"; the hub reads "Your devices, together". No subtitle.
- Text size: 44 for device names and the hub, 64 for the title, so every word is at least 12 pixels in the
  630-pixel column of a 1280-pixel window.
- Use: the start page's only picture and its social preview.
- Alt text: the `hero.desc` label of the chosen language.

### 1a. ecosystem-hero-phone - the cover in portrait

- Files: `diagrams/ecosystem-hero-phone-<lang>.svg`.
- Style: the same slide look, 1080 x 1350; the title on two lines, two rows of three devices around the hub.
- Text size: 48 for device names and the hub, 64 for the title, so every word is at least 12 pixels in the
  280-pixel column of a 360-pixel window.
- Use: the start page embeds both forms in one `<picture>`; the browser takes this one at 768 pixels and
  below and loads only one of the two.
- Why: scaled into a phone column the wide cover shows its words at about 3 pixels.
### 3. exchange-simple - your devices, connected from any network

- Files: `diagrams/exchange-simple-<lang>.svg`.
- Style: simplified light scheme, 1600 x 900, white cards with soft shadows.
- Shows: the exchange server card in the middle with a log badge, labelled "account, devices, folders,
  log". Left: a home PC and an office PC that share folders over SFTP, and a laptop that sends and receives
  media. Right: a phone that opens any of your folders and a phone acting as a server that shares its own.
  Bottom: a watch and a TV that receive media. Two-way arrows run from every device to the server.
- Footer: "No port forwarding: every device dials out to the server, from any network."
- Use: the opening picture of the exchange server section; also fits a store listing or a short how-to.
- Why selected: the clearest one-glance answer to "how do my devices find each other"; light background
  reads well on both themes and in print.
- Alt text: the `ex.simple.desc` label of the chosen language.

### 5. exchange-keeps - what the exchange server keeps

- Files: `diagrams/exchange-keeps-<lang>.svg`.
- Style: presentation slide, 1920 x 1080.
- Shows: three columns.
  - On its disk: accounts (a login and a salted password hash), enrolled devices (name, type, last seen),
    names of shared folders, the user log with ids hashed.
  - In memory only: folder access codes while the device is online, live tunnels and the last 2 seconds of
    a broadcast, one-time pairing codes valid for 10 minutes.
  - Never: your files, photos and recordings; passwords, tokens or pairing codes in the log; your list for
    anyone outside your account.
- Use: the privacy block of the portal; the answer to "what does the server operator see".
- Why selected: the owner asked for the server's user log on the scheme; this slide states what that log
  holds and what it never holds, which neither Windows picture does.
- Alt text: the `ex.keeps.desc` label.

### 6. broadcast-simple - any device on air, every device listening

- Files: `diagrams/broadcast-simple-<lang>.svg`.
- Style: simplified light scheme, 1600 x 900.
- Shows: three broadcasters on the left - a phone (camera and microphone), a watch (microphone), a Windows PC
  (screen, camera, sound); the exchange server relay as a round hub in the middle; four receivers on the
  right - a phone, a watch, an Android TV, a Windows PC. Orange arrows run from every broadcaster through the
  hub to every receiver. A note under the hub: on one Wi-Fi the stream goes direct.
- Footer: Cast asks a chosen device to start playing, and the device asks before it plays.
- Use: the opening picture of the broadcast section.
- Why selected: shows Windows as a broadcaster, as the owner asked, at the same weight as the phone and the
  watch.
- Alt text: the `br.simple.desc` label.

### 8. broadcast-cast - start playback on another device

- Files: `diagrams/broadcast-cast-<lang>.svg`.
- Style: presentation slide, 1920 x 1080.
- Shows: an office PC, the exchange server and a living-room TV as three large circles, joined by five
  numbered arrows, each repeated as a numbered line below.
  1. The PC goes on air and uploads once.
  2. On the PC: Cast to the living-room TV.
  3. The TV asks whether to play the broadcast from the office PC.
  4. Accepted - the answer returns to the PC.
  5. The TV plays the stream from the relay.
- Use: the Cast block of the broadcast section.
- Why selected: the only picture of the Cast handshake; it also shows that a receiver asks before it plays,
  which the Windows cards get wrong.
- Alt text: the `br.cast.desc` label.

### 9. home-simple - your storage, reachable from outside

- Files: `diagrams/home-simple-<lang>.svg`.
- Style: simplified light scheme, 1600 x 900.
- Shows: a home network zone holding a router, a media PC with an SFTP server and a fixed address, a phone
  and an Android TV on Wi-Fi that connect directly; an outside zone with a phone on mobile data and a laptop
  in a hotel that reach the PC through the router's public address.
- Footer: at home the app uses the local address, away the public one, and picks by itself.
- Use: the opening picture of the home network section.
- Why selected: shows both halves the owner asked for - reliable exchange inside the home network and access
  to the home storage from outside - with no exchange server in the picture.
- Alt text: the `home.simple.desc` label.

### 11. home-steps - home storage reachable from outside in six steps

- Files: `diagrams/home-steps-<lang>.svg`.
- Style: presentation slide, 1920 x 1080, six cards in two rows.
- Shows:
  1. Fix the storage address - reserve `192.168.1.20` in the router's DHCP settings.
  2. Turn on the SFTP server - Fast Media Sorter & Sharing for Windows shares folders, a phone shares its own from
     Settings.
  3. Forward one port - TCP 2222 from the internet to `192.168.1.20:2222`, one port per server.
  4. Get a public address - a static IP from the provider or a free dynamic DNS name.
  5. Pair by QR code - the folder arrives with all its addresses and its trusted key.
  6. Use it anywhere - local at home, public away, the same folder.
- Use: the setup checklist closing the home network section.
- Alt text: the `home.steps.desc` label.

### 15. home-pairing - one code, three addresses

- Files: `diagrams/home-pairing-<lang>.svg`.
- Style: presentation slide, 1920 x 1080, the same family as `home-steps`.
- Shows: on the left a Windows PC running Share Manager (folder `D:\Media`, SFTP on `192.168.1.20:2222`)
  that produces a QR code or an `.fmscfg` file; on the right a phone that scanned it and remembers all
  three addresses; between them three numbered lanes the phone tries at once, in order of preference:
  1. Home Wi-Fi - `192.168.1.20:2222`, the fastest, a direct connection over the home network.
  2. IPv6 - `2001:db8:25::20`, works even behind carrier NAT when the provider gives the PC an IPv6
     address.
  3. Internet - `myhome.example.net:2222`, through the router's forwarded port, from anywhere.
- Footer: "The phone picks the fastest address that answers; the PC's host key is pinned at the first
  pairing." and a second line: a phone on the same Wi-Fi also finds the PC by itself if its address
  changed.
- Position: the home network section, right after `home-simple`;
  it is the picture for the paragraph that explains why one scan works at home and away.
- Why added: `home-simple` shows two addresses; the shipped Windows product puts three
  into one code (local, IPv6, internet), and the IPv6 lane is what works behind carrier NAT, which no
  other picture shows.
- Scope: facts only from the shipped Windows product and the shipped Android import; no exchange server
  and no development-tree feature is involved.
- Alt text: the `home.pair.desc` label of the chosen language.
- Embed snippet (alt text shortened for a page):
  `![One pairing code from Share Manager carries three addresses - home Wi-Fi, IPv6 and the internet -
  and the phone tries them at once and keeps the fastest.](diagrams/home-pairing-en.svg)`

## Scenario step pictures - one per how-to page (S4242)

Eight slides, all of one form: 1920 x 1080, the dark brand gradient with the waves-and-particles motif, a
heading with a subtitle, and four numbered cards in two columns or six in three, drawn like `home-steps` by
the one builder `Build-ScenarioSteps`. Each card has an icon, a title and a body of at most three lines. The
label keys are `sc.<name>.title`, `.desc`, `.sub` and `.<n>.t` / `.<n>.b`; the `*.desc` label is the base of
the alt text. The facts come from the contract `DEVICE-EXCHANGE` and the interface strings of the tree, and
no picture claims anything beyond what a page of the same name says.

- `steps-folders-exchange` (six cards) - add the server on both devices, share a folder, switch on Access
  from anywhere, find the folder under From my devices, attach it, open it. Embedded by
  `folders-exchange-server`.
- `steps-folders-drive` (four cards) - the same Google account, the switch Files via Google Drive, send or
  open, wait for the answer. Embedded by `folders-google-drive`.
- `steps-broadcast-start` (four cards) - choose the source, start, share the link or the QR code, listeners
  find it; the subtitle says the password travels separately. Embedded by `broadcast-start`.
- `steps-broadcast-receive` (four cards) - the four ways to a broadcast: the list of your devices, a link or
  a QR code or a file, a link with a password, a cast to this device. Embedded by `broadcast-receive`.
- `steps-watch` (six cards) - the server on the phone, Connect watch, a code and not a password, the watch
  joins, its own network, the phone may stay away. Embedded by `watch-exchange`. Facts drawn only from items
  CF, CG and CH of the contract: the data goes over the watch's own network, Bluetooth carries the hand-over
  and commands, the watch broadcasts only sound, no password is typed on it.
- `steps-schedule` (four cards) - open the schedules, choose the folders, what and when, check History; the
  subtitle says an unreachable side skips the run. Embedded by `schedule-remote-folders`.
- `steps-phone-sharing` (four cards) - switch the server on, add the folders, leave it running, mind the
  limits (the Android 15 daily limit and mobile data). Embedded by `phone-sharing`.
- `steps-my-devices` (six cards) - open My devices, paste or scan or choose, trust the certificate, sign in,
  pass it on, choose the outgoing server. Embedded by `my-devices`.

The words follow the terms table of the contract (item CW): exchange server, via Google Drive, broadcast and
broadcasting, clipboard, cast to your own device. A picture added here needs its page, its manifest section
and its three label sets in the same change.

## Rejected - the Windows set

- Six JPG slides and infographics (`slide-*.jpg`, `presentation-*.jpg`): generated imagery with broken
  lettering ("CLOULF FILES", duplicated captions), a fake "SECURE CLOUD PLATFORM / CONFIDENTIAL / Q3 2024"
  footer, third-party marks on screens (a video service logo, the Android robot), and a "zero delay" claim.
  Unfit for a page every product links to. `slide-home-storage.jpg` is the least damaged and could serve
  as mood art after a clean-up, never as a scheme.
- Three detailed SVG schemes (`scheme-*-detailed.svg`): Russian only; overlapping text in the scheme 1
  legend and the scheme 3 tablet card; numbers the contracts do not hold (an 8 MB relay buffer, Opus audio,
  a 1-second keyframe); a real public IP (`93.184.216.34`) as the example address; UPnP offered as the setup
  path (it is a convenience that a router may refuse, not a route to promise). Their content is told by the section pages' text.
- Three simple SVG schemes (`scheme-*-simple.svg`): Russian only, thinner than the generated simple
  pictures, and scheme 1 names a "server edition" as a separate device role the contracts do not define.
