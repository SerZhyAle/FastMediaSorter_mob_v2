---
page_id: wear.watch-listening-and-broadcast
title: Listening Through the Watch and Broadcasting from It
nav_title: Listening and broadcasting
description: How to listen to your paired watch's microphone from the phone and save a recording, broadcast the watch's own microphone to Wi-Fi listeners or a Windows PC, and send a live broadcast from the phone straight to the watch.
category: Wear OS Watch
category_slug: wear
ticket: S2966
flavor: The full watch version (sideload only); the phone's Listen to the watch and Live Broadcast screens - Standard and noLegal.
recipe_number: "09"
canonical_url: documentation/wear/watch-listening-and-broadcast-ru.html
why: |
  Your watch is already on your wrist wherever you are - in the kitchen, in the kid's room, out on a walk. That makes its microphone useful for more than telling the time: your phone can listen through it from across the house, and the watch itself can [broadcast](term:live-broadcast) what it hears to anyone on the same Wi-Fi, including a Windows PC.

  Nothing here needs the internet. Every session runs over your own local network, between devices that are already paired.
ingredients:
  - "The [watch app](term:watch-app) installed and paired - see [installing and pairing the watch](page:wear.installation-and-pairing)."
  - "The full version of the watch app. *Sideload version only* - see the [noLegal edition](term:nolegal-edition)."
  - "The phone and the watch on the same Wi-Fi network - sound doesn't travel over Bluetooth."
  - "For turning the watch into a PC microphone: StreamsPlayer and, optionally, the free VB-CABLE virtual audio cable on the Windows PC."
steps:
  - number: 1
    id: listen-from-phone
    title: Listen to your watch from the phone
    text: |
      On the phone, open **Listen to the watch** - from the programs menu, the [quick-access panel](term:quick-access-panel), a launcher [shortcut](term:shortcut) or a home-screen [widget](term:widget) - and tap **Start listening**. The watch asks you to confirm; tap **Allow** on its screen and its microphone turns on, with an indicator that stays visible for as long as you're listening.

      Want to keep what you hear? Use **Record watch audio** instead of plain listening, and the recording is saved to a file when you stop, or to the local folder if nothing else was chosen.

      You never have to remember to stop it: a session with nobody actually taking the watch's audio for 90 seconds, or one where phone playback is paused or stopped from anywhere, ends itself. The watch's own notification carries a **Stop listening** button too, so the microphone never runs unheard by accident.
    image_bookmark:
      shot_id: wear.phone-listen-screen
      device_profile: phone
      screen_state: wear-listen-start-listening-idle
      alt: The phone's Listen to the watch screen with a Start listening button and a caption saying the watch will ask you to confirm
      caption: "Start listening - the watch confirms on its own screen."
      title: "Screenshot: Listen to the watch"
      desc: Phone, Listen to the watch screen, idle state, Start listening button and caption visible.
  - number: 2
    id: broadcast-from-watch
    title: Broadcast audio straight from the watch
    text: |
      Open **Broadcast** from the Programs grid, or from the watch's home screen if it was the [program](term:program) you last used. Tap **Start broadcast** and the watch opens its own microphone and serves the sound over Wi-Fi to anyone you share the address with - several listeners at once. The screen shows **On air** the whole time, and the ongoing notification carries a **Stop** button, so ending it never needs you to reopen the app.

      A second button, **Screen off**, blanks the watch's display while the broadcast keeps going; a double tap, a press and hold, or the watch's own button brings the controls back. Broadcasting with the screen on is the hungriest thing the watch does - budget about a third of its battery an hour, so keep it on the charger for a long session.

      Repeating a broadcast keeps the same connection address as last time, so its QR code doesn't change, and a phone that already has that stream simply refreshes the entry instead of adding a second one.
    image_bookmark:
      shot_id: wear.watch-broadcast-live
      device_profile: watch
      screen_state: wear-broadcast-on-air-with-controls
      alt: The watch's Broadcast screen on air with Stop, Show QR code and Screen off buttons
      caption: "On air, with the microphone open."
      title: "Screenshot: Broadcasting"
      desc: Round watch, Broadcast screen, On air label, Stop / Show QR code / Screen off buttons.
  - number: 3
    id: share-and-mic
    title: Share the address, and turn the watch into a PC microphone
    text: |
      Tap **Show QR code** and the address appears as a code sized to fit the round dial whole - scan it from any listening device. On a Windows PC, open the same address, or paste the QR code's text, in StreamsPlayer and it plays there directly.

      From there, the watch can feed any Windows program: install the free VB-CABLE virtual audio cable, set StreamsPlayer's output to **CABLE Input** in the Windows volume mixer, then pick **CABLE Output** as the microphone inside whichever program you want to use - a call, a stream, anything that takes a microphone input.
    image_bookmark:
      shot_id: wear.watch-broadcast-qr
      device_profile: watch
      screen_state: wear-broadcast-qr-code-round-dial
      alt: The watch's broadcast QR code sized to fit the round dial, ready to scan from a listening device
      caption: "Scan the code, or paste its text into StreamsPlayer."
      title: "Screenshot: Broadcast QR code"
      desc: Round watch, Show QR code screen, code sized to the round display.
  - number: 4
    id: send-broadcast-to-watch
    title: Send your phone's live broadcast to the watch
    text: |
      Already broadcasting from the phone? Open its **Live Broadcast** screen while the broadcast is running and tap **Send to watch**. If the watch app is open, it starts playing there immediately; if it isn't, the watch raises a notification instead, so you never wonder whether the tap did anything.
    image_bookmark:
      shot_id: wear.phone-broadcast-send-to-watch
      device_profile: phone
      screen_state: live-broadcast-control-send-to-watch-button
      alt: The phone's Live Broadcast screen with a Send to watch button while the broadcast is live
      caption: "Send to watch, while the broadcast runs."
      title: "Screenshot: Send to watch"
      desc: Phone, Live Broadcast control screen, live state, Send to watch button visible.
  - number: 5
    id: stays-alive
    title: The session survives sleep, a service restart, and even a system kill
    text: |
      None of this needs anything from you - it's simply how the watch behaves now. A broadcast or a listening session holds the processor awake for as long as it's serving sound, so the watch going to sleep on your wrist or on the charger no longer cuts the listeners off; Wear OS has no per-app background-work switch to grant here, so the app asks for nothing and just keeps its foreground service running instead.

      If the system kills that service anyway, a broadcast restarts itself rather than staying silently dead, and work the watch already received from the phone - a slow sync, a file transfer, a pin - survives the same kind of restart and still gets answered.

      A very long, completely undisturbed sleep can still end a session; if that happens, start it again from the watch.
outcome: |
  You can hear what's happening near your watch from anywhere on the same Wi-Fi, and turn the watch's own microphone into a broadcast anyone nearby - or a Windows PC - can pick up, all without leaving the room your phone is in.
tips:
  - "**Nothing plays on the PC?** Check that StreamsPlayer and the watch are on the same Wi-Fi network - the broadcast never reaches beyond it."
  - "**Watch screen off during a broadcast?** Double tap it, press and hold, or use the watch's own button - the broadcast itself keeps running the whole time."
  - "**A long call is draining the watch?** Broadcasting with the screen on costs about a third of the battery an hour - dim the screen or keep it on the charger."
next_recipes:
  - title: Syncing the phone and the watch
    url: page:wear.companion-data-sync
    badge: Watch
    badge_type: docs
    description: Pair the watch and turn Wear Companion on, if listening and broadcasting aren't offered yet.
  - title: Chromecast casting and Live Broadcast
    url: page:player.casting-and-broadcast
    badge: Player
    badge_type: docs
    description: Start a Live Broadcast from the phone in the first place.
  - title: Wrist programs and tools
    url: page:wear.wrist-mini-apps-and-tools
    badge: Watch
    badge_type: docs
    description: Everything else waiting in the watch's Programs grid.
---

Listen to your paired [watch](term:watch)'s microphone from the phone, [broadcast](term:live-broadcast) the watch's own microphone to listeners on the same Wi-Fi or into a Windows PC, and send a live broadcast from the phone straight to the watch.
