---
layout: doc
title: "Editions: which app has which way of exchange"
description: "Which edition of FastMediaSorter for Android has network folders, My devices, clouds, files via Google Drive, live broadcasting, Chromecast and the watch companion."
category: "Media exchange"
permalink: /docs/media-exchange-portal/editions.html
---
<sub class="doc-stamp">26.10.15 15:04</sub>

# Editions: which app has which way of exchange

FastMediaSorter for Android comes in several editions. Not every edition carries every way of exchange: an edition without a capability shows no button for it at all.

## Which edition has what

<div class="doc-table-wrapper" markdown="1">

| Capability | Standard | noLegal | Lite | Photos | Legacy | VR | XR | FOSS |
|:-----------|:--------:|:-------:|:----:|:------:|:------:|:--:|:--:|:----:|
| Network folders (SMB, SFTP, FTP) and the phone's SFTP server | yes | yes | no | yes | yes | yes | yes | yes |
| My devices and the exchange server | yes | yes | no | yes | yes | yes | yes | yes |
| Clouds (Google Drive, Dropbox, OneDrive) | yes | yes | no | yes | yes | yes | yes | no |
| Files via Google Drive between your devices | yes | yes | no | yes | yes | yes | yes | no |
| Going live from this device (sound, camera, screen, file) | yes | yes | no | no | yes | no | no | no |
| Playing live broadcasts and internet streams | yes | yes | no | no | yes | yes | yes | no |
| Chromecast | yes | yes | yes | yes | yes | no | yes | no |
| Watch companion | yes | yes | no | no | no | no | no | no |
{: .doc-table}

</div>

This table is checked against the build of every edition, so it changes when an edition changes.

Going live covers the microphone, the camera, the screen of the phone and a ready file. The camera keeps broadcasting in the background in Standard and noLegal; in Legacy the app is covered with a black screen while the broadcast runs. See [Going on air](broadcast-start.html).

## Choosing an edition for exchange

- **Every way of exchange on one phone** - Standard, or noLegal for a copy installed outside the app stores.
- **An old phone or tablet on Android 6 or 7** - Legacy keeps network folders, clouds and broadcasting.
- **No closed-source parts** - FOSS keeps network folders, the phone's SFTP server and My devices, without clouds and without files via Google Drive.
- **Lite** works with the media on the device itself only.

The Windows programs are separate products and do not follow this table: Fast Media Sorter & Sharing for Windows comes as the full package and as the Server edition that keeps a PC's folders available around the clock, see [Windows servers](windows-servers.html), and [STREAMS Player](https://serzhyale.github.io/StreamsPlayer/) is a program of its own.

## Step-by-step guides

- [The editions of FastMediaSorter](../../documentation/flavors/overview-and-comparison.html)
- [Putting FastMediaSorter on your watch](../../documentation/wear/installation-and-pairing.html)

Back to the [portal start page](index.html).
