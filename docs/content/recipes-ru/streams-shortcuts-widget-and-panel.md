---
page_id: streams.shortcuts-widget-and-panel
title: Your Stations One Tap Away - Shortcuts, the Stream Widget and the Streams Panel
nav_title: Shortcuts, widget and streams panel
description: How to start a favorite radio station or TV channel with one tap - from a shortcut or a widget on the Android home screen, or from the streams panel on the FastMediaSorter main screen - and how to hide the panel again.
category: Интернет-трансляции
category_slug: streams
ticket: S2955
flavor: Standard, noLegal, Legacy and VR
recipe_number: "07"
canonical_url: documentation/streams/shortcuts-widget-and-panel-ru.html
why: |
  You probably come back to the same two or three stations every day. Opening the app, finding [Streams](term:streams-screen) and scrolling to the station each time is three steps too many.

  FastMediaSorter gives you three ways to skip them. A [shortcut](term:shortcut) or a [widget](term:widget) on the Android [home screen](term:home-screen) starts a station without opening any screen at all. The [streams panel](term:streams-panel) puts your pinned [channels](term:channel) right on the app's [main screen](term:main-screen), above your resources.
ingredients:
  - "FastMediaSorter in the Standard, noLegal, Legacy or VR [edition](term:edition), with Streams turned on."
  - "The channels you want at hand. For the streams panel they must be pinned - see [pinned and favorite channels](page:streams.favorites-and-epg)."
  - "For shortcuts and widgets: a home screen app that supports them. Almost every Android home screen does."
steps:
  - number: 1
    id: home-screen-shortcut
    title: Put a station on the home screen
    text: |
      In Streams, open the menu of the channel - touch and hold it, or tap its **More actions** button - and choose **Add to home screen**. Android asks where to place the icon; confirm, and a shortcut with the channel's name appears on your home screen.

      If your home screen cannot hold shortcuts, the app tells you: **Your launcher does not support home-screen shortcuts**.
    image_bookmark:
      shot_id: streams.channel-menu-add-to-home
      device_profile: phone
      screen_state: streams-channel-menu-open
      alt: The menu of a channel in Streams with Add to home screen, Send to watch, Open on watch and the other channel actions
      caption: "Add to home screen is in the channel's own menu."
      title: "Screenshot: Channel menu"
      desc: Streams list with one channel's action menu open, Add to home screen highlighted.
  - number: 2
    id: shortcut-plays
    title: Tap the shortcut to play, tap again to stop
    text: |
      Tap the shortcut of a radio station, and it simply starts playing - no screen opens, you stay on your home screen. The ordinary media notification shows that it plays and lets you control it. Tap the same shortcut again to stop the station.

      The Streams screen opens instead when:

      - the channel is a video channel - a picture needs a screen;
      - background playback is switched off (**Settings**, the **Player** tab, **Background Playback**);
      - there is no network, so the screen can tell you so.

      If the channel has been removed from your list meanwhile, you see **This stream is no longer in your list**. Shortcuts made with an older version of the app are brought up to date by themselves on the next start.
  - number: 3
    id: stream-widget
    title: Add the Stream widget
    text: |
      A widget does the same job and lets you pick the channel right on the home screen:

      1. Touch and hold an empty spot of the home screen and choose **Widgets**.
      2. Find FastMediaSorter and drag the **Stream** widget - **Open a saved channel from the home screen** - onto the screen.
      3. A picker opens. Type a few letters of the channel name; radio and video channels are listed separately. Tap the one you want.

      From now on one tap on the widget starts that channel - a radio station plays in the background the same way as a shortcut, a video channel opens. How to place and resize widgets in general is described in [placing Android widgets](page:launcher.android-widgets-placement).
    image_bookmark:
      shot_id: streams.widget-channel-picker
      device_profile: phone
      screen_state: stream-widget-configure-search
      alt: The channel picker of the Stream widget with a search field and radio and video channels listed separately
      caption: "Choose the channel for the Stream widget."
      title: "Screenshot: Stream widget channel picker"
      desc: Stream widget configuration screen, a search typed, radio and video sections visible.
  - number: 4
    id: streams-panel-on
    title: Turn on the streams panel on the main screen
    text: |
      Open **Settings**, the **General** tab, and switch on **Show streams panel on the main screen**. It is off until you turn it on, and it appears only while Streams is enabled.

      A horizontal bar appears above the resource list. It starts with the **Streams..** button, which opens the Streams screen, followed by your pinned channels in the order you pinned them. Each channel shows its logo, its short name or both - whatever fits the width of your screen.

      Nothing pinned yet? The panel then shows a small hint, **Pin streams to see them on this panel.**, which disappears as soon as you pin the first channel.
    image_bookmark:
      shot_id: streams.main-screen-panel
      device_profile: phone
      screen_state: main-screen-streams-panel
      alt: The main screen with the streams panel above the resource list, showing the Streams button and several pinned channels with logos
      caption: "Pinned channels on the main screen."
      title: "Screenshot: Streams panel on the main screen"
      desc: Main screen, streams panel on, the Streams.. button and four pinned channel chips with logos and short names.
  - number: 5
    id: streams-panel-play
    title: Play a station from the panel
    text: |
      Tap a radio channel on the panel. It plays right on the main screen: a bar at the bottom shows the channel name, the current song and a **Stop** button, and you keep browsing your files. Tap the same channel again to stop it.

      A video channel opens the Streams screen and plays there.
  - number: 6
    id: streams-panel-hide
    title: Fold, hide or switch off the panel
    text: |
      Touch and hold the **Streams..** button on the panel. Its menu offers:

      - **Collapse panel** - folds the panel to take less room;
      - **Hide panel** - removes only the panel; Streams stays on and its button moves back to the [programs panel](term:programs-panel) and the menu;
      - **Disable** - turns the whole Streams feature off.

      To bring a hidden panel back, switch on **Show streams panel on the main screen** in **Settings**, the **General** tab, again.
outcome: |
  Your everyday stations start with one tap from the home screen or the main screen, radio plays without opening anything, and the panel shows only when you want it.
tips:
  - "**A shortcut opens Streams instead of playing?** Check that **Background Playback** is on and that you are online; video channels always open a screen."
  - "**Want the panel in a different order?** It follows the order of your pinned channels. Reorder them in Streams - see [pinned and favorite channels](page:streams.favorites-and-epg)."
  - "**Using the FastMediaSorter home screen?** A channel can also sit on its desktop. See [desktop grid and icons](page:launcher.desktop-grid-and-icons)."
next_recipes:
  - title: Playing live streams and radio
    url: page:streams.live-stream-playback
    badge: Streams
    badge_type: docs
    description: Everything that happens after the tap.
  - title: Pinned and favorite channels
    url: page:streams.favorites-and-epg
    badge: Streams
    badge_type: docs
    description: Choose which channels appear on the panel.
  - title: Streams on a TV, a watch or a VR headset
    url: page:streams.tv-watch-vr-and-broadcast
    badge: Streams
    badge_type: docs
    description: Take a station to your wrist or a channel to the TV.
---

Your everyday stations, one tap away: a [shortcut](term:shortcut) or the **Stream** [widget](term:widget) on the Android home screen, or the [streams panel](term:streams-panel) with your pinned [channels](term:channel) on the FastMediaSorter main screen.
