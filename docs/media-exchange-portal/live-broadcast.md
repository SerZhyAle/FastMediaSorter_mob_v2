---
layout: doc
title: "Live broadcast: any device on air, your other devices listening"
description: "Who can go on air and who can play a live broadcast, how it travels on one Wi-Fi network and beyond, Chromecast today and Cast between your own devices later."
category: "Media exchange"
permalink: /docs/media-exchange-portal/live-broadcast.html
---
<sub class="doc-stamp">26.10.10 14:45</sub>

# Live broadcast: any device on air, your other devices listening

A live broadcast sends what a device sees or hears right now - its camera, its microphone - to your other devices while it happens. **It works today on one Wi-Fi network.** The relay for other networks, Cast between your own devices and broadcasting from a PC are not available yet; each is marked below.

![A phone camera, a watch microphone and a Windows PC screen broadcast through the exchange server relay to a phone, a watch, an Android TV and a Windows PC.](diagrams/broadcast-simple-en.svg)

## Who goes on air

- **An Android phone** - camera and microphone: video with sound, video only or audio only. The lens can be switched while on air. Available in the Standard, noLegal and Legacy editions, see [Editions](editions.html).
- **A Wear OS watch** - its microphone, to up to four listeners at once, from a tile or the programs grid of the watch.
- **A Windows PC** - screen, camera and system sound. **Not available yet:** the picture shows the PC on air, but Fast Media Sorter & Sharing for Windows does not broadcast yet. It is planned for Share Manager, after the [exchange server](exchange-server.html).

## Who plays it

- **Another Android phone or tablet** - in the Streams section of FastMediaSorter: scan the broadcast's QR code, type the address shown under it, or open a shared broadcast link or a `.fmsbcast` file.
- **A Wear OS watch** - the phone's broadcast screen sends the running broadcast straight to the paired watch.
- **An Android TV or a VR headset** - in the Streams section of FastMediaSorter, the same way as on a phone.
- **A Windows PC** - in [STREAMS Player](https://serzhyale.github.io/StreamsPlayer/). **Development version only:** the viewer of Fast Media Sorter & Sharing for Windows can already listen to the microphone broadcast of a phone or a watch on the same network. It is not in a release yet, it plays audio only, and no delay is promised.

## How it travels

- **On one Wi-Fi network** a receiver connects to the broadcasting device directly, with the lowest delay. This is how broadcasting works today.
- **Outside that network** a receiver will go through the exchange server's relay: one upload from the broadcaster, any number of listeners, and only the last two seconds kept, so a slow listener skips ahead instead of falling behind. **Not available yet**, because the [exchange server](exchange-server.html) is not available yet.

![Detailed scheme. Broadcasters: an Android phone with camera and microphone, a Wear OS watch microphone with up to four listeners, a Windows PC screen, camera and system sound. The exchange server lists live broadcasts, relays one upload to any number of listeners keeping only the last two seconds, routes Cast requests and offers tunnels. Receivers: an Android phone, a watch, an Android TV and a Windows PC. On one Wi-Fi the stream goes direct.](diagrams/broadcast-scheme-en.svg)

## Cast

**Chromecast works today.** FastMediaSorter casts an open video, audio file, photo or a live stream to a Chromecast device on your network.

**Cast between your own devices is not available yet.** It will let one of your devices ask another to start playing a broadcast - for example, an office PC asks the living-room TV. The receiving device always asks before it plays.

![An office Windows PC broadcasts its screen through the exchange server and casts it to the living-room TV, which asks, accepts and plays the stream from the relay.](diagrams/broadcast-cast-en.svg)

## Step-by-step guides

- [Chromecast casting and live broadcast](../../documentation/player/casting-and-broadcast.html)
- [Streams on a TV, a watch or a VR headset, and tuning in to a live broadcast](../../documentation/streams/tv-watch-vr-and-broadcast.html)
- [Listening through the watch and broadcasting from it](../../documentation/wear/watch-listening-and-broadcast.html)

Back to the [portal start page](index.html).
