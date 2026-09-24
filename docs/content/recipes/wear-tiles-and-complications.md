---
page_id: wear.tiles-and-complications
title: One Swipe from the Watch Face - Tiles and Complications
nav_title: Tiles and complications
description: How to add the FastMediaSorter tiles to the watch's tile carousel, point a Resource or Stream tile at the place you want, and put the Last Resource, Favorites and Now Playing complications on your watch face.
category: Wear OS Watch
category_slug: wear
ticket: S2964
flavor: Programs tile - both watch versions; Resource, Stream, Favorites and Sections tiles and all complications - the full watch version (sideload only)
recipe_number: "03"
canonical_url: documentation/wear/tiles-and-complications.html
why: |
  Opening an app on a watch means pressing the button, scrolling the apps list, tapping the icon and then finding your place inside. For the one server you listen to every evening, or the radio station you start every morning, that is too long.

  [Tiles](term:tile) and [complications](term:complication) cut it to one gesture. A tile is a whole card you reach by swiping sideways from the watch face; a complication is a small spot on the watch face itself. FastMediaSorter offers five tiles and three complications, and each one opens the watch app exactly where you need it.
ingredients:
  - "The [watch app](term:watch-app) installed - see [installing and pairing the watch](page:wear.installation-and-pairing)."
  - "For every tile except Programs, and for the complications: the full version of the watch app. *Sideload version only* - see the [noLegal edition](term:nolegal-edition)."
  - "For the Resource tile: at least one [network resource](term:network-resource) on the watch - sent from the phone as described in [syncing the phone and the watch](page:wear.companion-data-sync), or added on the watch."
  - "A watch face that has complication slots, for the complications."
steps:
  - number: 1
    id: add-tile
    title: Add a tile to the carousel
    text: |
      1. On the watch face, swipe left to the tiles.
      2. Swipe to the end of the tiles and tap **+** (**Add tile**), or touch and hold any tile to edit the carousel.
      3. Pick one of the FastMediaSorter tiles: **Programs**, **Sections**, **Resource Tile**, **Stream Tile** or **Favorites Tile**.

      The exact wording of the carousel editor comes from your watch maker; Google describes the standard steps in the [Wear OS Help Center](https://support.google.com/wearos). You can also add and arrange tiles from the watch maker's app on the phone.
    image_bookmark:
      shot_id: wear.tile-picker-carousel
      device_profile: watch
      screen_state: wear-system-add-tile-list
      alt: The Wear OS Add tile list with the FastMediaSorter tiles Programs, Sections, Resource Tile, Stream Tile and Favorites Tile
      caption: "The FastMediaSorter tiles in the Add tile list."
      title: "Screenshot: Adding a tile"
      desc: Round watch, system Add tile list scrolled to the FastMediaSorter entries.
  - number: 2
    id: grid-tiles
    title: Programs and Sections - ready-made shortcut grids
    text: |
      These two need no setting up: add them, and they work.

      - **Programs** shows the built-in [programs](term:program) of the watch - [Calculator](term:calculator), [Network Monitor](term:network-monitor), the mini-game, the voice recorder, system information and the others - as a grid of icons. One tap opens the program. It is the one tile the Google Play version carries too.
      - **Sections** shows the sections of the watch app: **Resources**, **Phone**, **Local**, **Streams**, **Apps** and **Favorites**. **Streams** appears only while that section is switched on.

      A grid has room for seven icons. When there is more to show, the last cell reads **More** and opens the watch app, where the rest are listed.
    image_bookmark:
      shot_id: wear.programs-tile
      device_profile: watch
      screen_state: wear-tile-programs-grid
      alt: The Programs tile on a round watch, a grid of program icons with the More cell last
      caption: "The Programs tile."
      title: "Screenshot: Programs tile"
      desc: Round watch, Programs tile, seven cells including Calculator, Network Monitor and More.
  - number: 3
    id: assign-tile
    title: Point a Resource or Stream tile at its target
    text: |
      *Sideload version only.* A freshly added **Resource Tile** says **No resource assigned**, and a **Stream Tile** says **No stream assigned**. Tap **Select Target** on the tile.

      The watch app opens a list - **Select Resource** with your network resources, or **Select Stream** with your [channels](term:channel). Tap the one you want. From now on the tile shows its name, and one tap opens that resource or starts that stream, with or without the phone nearby.

      If the list is empty, the watch says what to do: "No resources found. Add a network resource on watch or sync from phone." for resources, "No streams found. Add a stream or sync catalog from phone." for streams.

      To change a target later, open **Settings** on the watch and then **Tile Targets**: it lists each tile with its current target, or **Not selected**, and lets you pick again. If a target is deleted, the tile says **Target no longer exists** until you choose a new one.
    image_bookmark:
      shot_id: wear.tile-target-picker
      device_profile: watch
      screen_state: wear-tile-picker-select-resource
      alt: The Select Resource list of the watch app with several network resources to choose from
      caption: "Choose what the Resource tile opens."
      title: "Screenshot: Select Resource"
      desc: Round watch, Select Resource picker, three network resources listed.
  - number: 4
    id: favorites-tile
    title: Keep your favorites on a tile
    text: |
      *Sideload version only.* The **Favorites Tile** lists the files and channels you marked with the heart on the watch - your [Favorites](term:favorites). Tap one to open it. Nothing marked yet? The tile says **No favorites marked**; tap the heart while a file plays to add it.
  - number: 5
    id: complications
    title: Put a complication on the watch face
    text: |
      *Sideload version only.* Touch and hold the watch face, tap **Customize** (or the pencil), move to the complication slots and tap the one to fill. Choose FastMediaSorter and one of its three complications:

      - **Last Resource** - "Shortcut to open the most recently used media resource". Shows its name; a tap opens it.
      - **Favorites** - "Shows favorites count and opens favorites list".
      - **Now Playing** - "Shows currently playing or last played track". A tap brings you back to the player.

      Which slots exist and how the editor looks depends on your watch face; the [Wear OS Help Center](https://support.google.com/wearos) describes the usual steps. The word "complication" appears only in that editor, never in the app itself.
    image_bookmark:
      shot_id: wear.complication-now-playing
      device_profile: watch
      screen_state: wear-watch-face-now-playing-complication
      alt: A watch face with the FastMediaSorter Now Playing complication showing the current track name
      caption: "The Now Playing complication on the watch face."
      title: "Screenshot: Now Playing complication"
      desc: Round watch face with one complication slot filled by Now Playing, a track title visible.
outcome: |
  Your programs, your sections, the one network folder you use most, your favorite station and the track playing right now are each one swipe or one tap from the watch face.
tips:
  - "**A tile opens the home screen instead of the resource?** Update the watch app: tapping the Resource tile opens the pinned resource directly."
  - "**Tiles speak your language.** The Favorites tile and the grids follow the language of the watch app."
  - "**No Streams cell on the Sections tile?** Turn on **Show streams** in the **Media types** group - on the watch, or in the Wear Companion window on the phone."
  - "**Want a station on the phone's home screen too?** See [shortcuts, the widget and the streams panel](page:streams.shortcuts-widget-and-panel)."
next_recipes:
  - title: Streaming radio on your wrist
    url: page:wear.wrist-stream-player
    badge: Watch
    badge_type: docs
    description: What happens after the Stream tile starts a station.
  - title: Wrist programs and tools
    url: page:wear.wrist-mini-apps-and-tools
    badge: Watch
    badge_type: docs
    description: Everything behind the Programs tile.
  - title: Syncing the phone and the watch
    url: page:wear.companion-data-sync
    badge: Watch
    badge_type: docs
    description: Get the network resources a Resource tile can point at.
---

Add the FastMediaSorter [tiles](term:tile) to the watch's tile carousel, point a Resource or Stream tile at the place you want, and put the Last Resource, Favorites and Now Playing [complications](term:complication) on your watch face.
