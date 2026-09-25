---
page_id: wear.installation-and-pairing
title: Putting FastMediaSorter on Your Watch - Installing, Pairing and the Wear Companion
nav_title: Installing and pairing the watch
description: How to install the FastMediaSorter watch app on a Wear OS watch, switch on Wear Companion on the phone, open its window and make the watch one of your resources.
category: Wear OS Watch
category_slug: wear
ticket: S2964
flavor: Phone side (Wear Companion) - Standard and noLegal; watch app - the Google Play version, or the full version installed from an APK
recipe_number: "01"
canonical_url: documentation/wear/installation-and-pairing-ru.html
why: |
  A [watch](term:watch) on your wrist is closer than the phone in your pocket. With the [watch app](term:watch-app) installed, you can play music, look at photos, open network folders and start the built-in [programs](term:program) right on the watch.

  The watch app works on its own, but it gets much more useful once it knows your [phone](term:phone). The phone side of that link is [Wear Companion](term:wear-companion) - a program inside FastMediaSorter that connects to the watch and decides what the phone shares with it. This page gets both halves installed and talking to each other.
ingredients:
  - "A [Wear OS](term:wear-os) watch built on Android 9 or newer - every Wear OS 3, 4 and 5 watch qualifies. Galaxy Watch 3, Galaxy Watch Active and Active 2 run Tizen, not Wear OS, and cannot install the app."
  - "The watch already paired with your Android phone in the watch maker's own app, such as Galaxy Wearable or Google Pixel Watch."
  - "FastMediaSorter on the phone in the Standard or [noLegal edition](term:nolegal-edition). The other [editions](term:edition) carry no Wear Companion."
  - "A few minutes and an internet connection on the watch or on the phone."
steps:
  - number: 1
    id: choose-watch-version
    title: Pick the version of the watch app
    text: |
      The watch app comes in two versions, and they do not do the same things:

      - **Google Play version** - a first, small release: Calculator, Stopwatch, the mini-game, the watch settings and the Programs [tile](term:tile).
      - **Full version** - an APK you install yourself from the [downloads page](https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/latest). It carries everything described in this section: music, photos, network folders, the phone link, every tile and the watch face complications. It is the watch counterpart of the [noLegal edition](term:nolegal-edition), so the parts marked *Sideload version only* on these pages need it.

      Both versions install under the same package name as the phone app, so the phone recognizes its watch partner either way.
  - number: 2
    id: install-from-play
    title: Install from Google Play
    text: |
      1. On the watch, open **Play Store** from the apps list.
      2. Search for **FastMediaSorter** and tap **Install**.
      3. Wait until the app appears in the watch's apps list.

      Typing on a small screen is awkward? Open Google Play on the phone instead, find the app and choose your watch as the device to install on - the watch downloads it by itself.
    image_bookmark:
      shot_id: wear.play-store-install
      device_profile: watch
      screen_state: wear-play-store-app-page
      alt: The Google Play page of FastMediaSorter on a round watch with the Install button
      caption: "Install straight from the watch's Play Store."
      title: "Screenshot: Play Store on the watch"
      desc: Watch Play Store, FastMediaSorter listing open, Install button visible.
  - number: 3
    id: install-apk
    title: Or install the full version from an APK
    text: |
      *Sideload version only.* A watch has no file manager to open an APK with, so the file travels from a computer over Wi-Fi with Google's free `adb` tool from the [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools):

      1. On the watch, open **Settings**, then **System** or **About watch**, and tap **Build number** seven times to unlock **Developer options**.
      2. In **Developer options**, turn on **ADB debugging** and **Wireless debugging**.
      3. In **Wireless debugging**, tap **Pair new device**. On the computer run `adb pair <address>:<pairing port> <code>` with the address and code the watch shows, then `adb connect <address>:<connection port>` with the port from the main Wireless debugging screen. The two ports are different.
      4. Run `adb install -r FastMediaSorter_wear_release.apk`. The `-r` keeps your app data when you update later.
      5. Turn **Wireless debugging** off again when you are done.

      Google explains every screen of this in [Debug a Wear OS app](https://developer.android.com/training/wearables/get-started/debugging). The computer and the watch must be on the same Wi-Fi network.
  - number: 4
    id: switch-on-companion
    title: Switch on Wear Companion on the phone
    text: |
      The phone keeps its watch features out of sight until you say you own a watch.

      1. On the phone, open FastMediaSorter and go to [Settings](term:settings), the **Management** tab.
      2. Expand the **Wear Companion** group.
      3. Turn on the **Wear Companion** switch - "Show the watch companion in the programs panel and menu".

      Below the switches, a line tells you whether the phone sees your watch right now: **Checking the link..**, then the watch's name followed by **- connected**, or **No watch on the link**. The same group holds the **How to install on a watch** button, which opens this documentation, and the **Wear Companion** button, which opens the companion window.
    image_bookmark:
      shot_id: wear.settings-companion-group
      device_profile: phone
      screen_state: settings-management-wear-group-expanded
      alt: The Management tab of Settings with the Wear Companion group expanded, its switch on and the connected watch named below
      caption: "The Wear Companion group in Settings."
      title: "Screenshot: Wear Companion in Settings"
      desc: Phone Settings, Management tab, Wear Companion group expanded, switch on, paired watch line reads connected.
  - number: 5
    id: open-companion-window
    title: Open the Wear Companion window
    text: |
      Once the switch is on, Wear Companion behaves like the other built-in programs such as [Calculator](term:calculator): it has its own entry in the [programs panel](term:programs-panel) and in the programs menu of the [main screen](term:main-screen). Tap **Wear Companion** there, or the button of the same name in Settings, and its window opens. It can also open in a new window, and you can take it off the panel like any other program.

      The window has the usual title bar with a back arrow. On the right of the title bar sit the **Sync settings** button and a line that says when the phone and watch last agreed on their settings - **Never synced** on a fresh pair. Below come the **Watch operations** group with the things you do with the watch, then the watch settings groups, and at the very bottom two links: **All about the watch** and **How to install on a watch**.

      Every group can be folded. A folded group shows a one-line summary of what is set inside it, so you can check the watch settings at a glance. On a wide screen or in landscape the buttons of **Watch operations** sit in two columns.
    image_bookmark:
      shot_id: wear.companion-window
      device_profile: phone
      screen_state: wear-companion-window-top
      alt: The Wear Companion window with the Sync settings button in the title bar, the Watch operations group open and the folded settings groups below with their summaries
      caption: "The Wear Companion window."
      title: "Screenshot: Wear Companion window"
      desc: Phone, Wear Companion window, toolbar with Sync settings and Last synced line, Watch operations expanded, Media types and Screen groups folded with summaries.
  - number: 6
    id: watch-as-resource
    title: Make the watch one of your resources
    text: |
      In **Watch operations**, tap **Add or open the watch**. The first tap adds your paired watch to the [resource list](term:resource-list) as a [resource](term:resource) of its own - "Send files to the watch and pick up what it records" - so it appears on the main screen and in the lists where you pick a [destination](term:destination) for copying and moving. Every later tap opens that same resource in the [file browser](term:file-browser); a second copy is never created.

      If the watch is not reachable at that moment, the phone says **Watch not connected**.
  - number: 7
    id: check-both-halves
    title: Check that both halves see each other
    text: |
      Open the watch app on the watch. Its home screen lists the sections **Resources**, **Phone**, **Local**, **Streams** (when that section is on), **Apps**, then a row with the program you opened last and **Favorites** at the end.

      Tap **Phone**: in the full version it shows the files of your phone, grouped the way the phone groups them. If the phone's Wear Companion switch is off, the watch does not pretend the phone is missing - it says exactly what to do: "The Wear Companion switch is off on your phone. Turn it on in FastMediaSorter settings, then retry."

      Which network folders the watch gets, and how the two keep their settings in step, is the subject of [syncing the phone and the watch](page:wear.companion-data-sync).
    image_bookmark:
      shot_id: wear.home-sections
      device_profile: watch
      screen_state: wear-home-screen-sections
      alt: The home screen of the watch app on a round watch with the Resources, Phone, Local, Streams and Apps sections
      caption: "The watch app's home screen."
      title: "Screenshot: Watch home screen"
      desc: Round watch, FastMediaSorter home screen, section list Resources, Phone, Local, Streams, Apps visible.
outcome: |
  The watch app is on your wrist, Wear Companion is switched on and reachable from the programs panel, the phone shows the watch as connected, and the watch is a resource on the main screen that you can send files to.
tips:
  - "**Using a screen reader?** Every control of the Wear Companion window says what it is: each switch reads its name and state, the group headers announce whether they are expanded and can be opened and closed from TalkBack, and each button says what it does."
  - "**No Wear Companion group in Settings?** Your phone runs an edition without the watch link - Lite, Photos, Legacy, VR or FOSS. Install the Standard or noLegal edition to use a watch."
  - "**The watch says the phone is not connected?** Check the pairing in the watch maker's app first; the FastMediaSorter link rides on it."
  - "**Getting around on the watch:** every watch screen has a back arrow at the middle of the left edge, and a black-screen button (a phone with a dark screen) opposite it that blanks the screen. A double tap, a press and hold or the watch's own button brings the screen back."
next_recipes:
  - title: Syncing the phone and the watch
    url: page:wear.companion-data-sync
    badge: Watch
    badge_type: docs
    description: Choose which network folders go to the watch and keep settings in step.
  - title: Tiles and complications
    url: page:wear.tiles-and-complications
    badge: Watch
    badge_type: docs
    description: Reach your favorite places on the watch in one swipe from the watch face.
  - title: Playing music on the watch
    url: page:wear.standalone-music-playback
    badge: Watch
    badge_type: docs
    description: Listen straight from the watch, with or without the phone.
---

Install the FastMediaSorter [watch app](term:watch-app), switch on [Wear Companion](term:wear-companion) on the phone, open its window, and add the [watch](term:watch) to your resources - the first half of every watch feature.
