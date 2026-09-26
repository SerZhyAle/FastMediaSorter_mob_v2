---
page_id: streams.channel-catalog-browsing
title: Browsing the Channel Catalog
nav_title: Browse the channel catalog
description: How the channel catalog gets onto your device and stays fresh, how to switch between grid and list, narrow it down with filters and curated collections, sort it, trust the green and red refresh marks, manage a channel from its own menu, clear everything downloaded, and keep flipping channels with your chosen tracks in the player.
category: Internet Streams
category_slug: streams
ticket: S2954
flavor: Standard, noLegal, Legacy and VR
recipe_number: "01"
canonical_url: documentation/streams/channel-catalog-browsing.html
why: |
  A hand-typed list of radio stations is fine when you have three of them. FastMediaSorter's built-in [catalog](term:catalog) can hand you thousands of [channels](term:channel) at once - curated stations, community radio, public webcams - and a list that size is only useful if you can bring in what you want, tell it apart from what you do not, and find your way back to the same view every time you open [Streams](term:streams-screen).

  This page covers the catalog end to end: getting it onto the device, keeping it fresh, and browsing what is in it with filters, collections and sorting. It stops short of what each channel actually looks like on screen and how you pin your favorites - those are their own pages, linked below.
ingredients:
  - "FastMediaSorter in the Standard, noLegal, Legacy or VR [edition](term:edition), with [Streams](term:streams-screen) turned on. Streams and its catalog do not exist in the Lite, Photos or FOSS editions."
  - "An internet connection to bring the catalog in the first place. Once channels are on the device, browsing, filtering and sorting all work from what is already there."
  - "A few channels in the list already - your own, or from the catalog - so there is something to filter, sort and manage. See step 1 if the list is still empty."
steps:
  - number: 1
    id: turn-on-and-download-catalog
    title: Turn Streams on and bring in the catalog
    text: |
      Turn Streams on in **Settings**, the **Media** tab, the **Streams** toggle. A [device profile](term:device-profile) can do this for you too: switching Streams on during the first-launch wizard starts the catalog download by itself, showing **Downloading..** under the row while it works. Turning the row back off mid-download cancels it, and opening a device that already has a catalog stored does not download anything extra.

      Once Streams is on, **Update catalog** sits directly in the toolbar - no overflow menu to hunt through. Tap it and FastMediaSorter reports what changed, something like "Catalog: +48 new, 12 updated, 3 removed". If a catalog source turns out to be dead or painfully slow, the import now fails fast with a clear message instead of leaving the spinner turning.

      Left the list for a while? A banner offers **Update the channel list?** with an **Update** button, and it clears itself once you act on it. Decide how eager that banner should be in **Settings**, the **Streams** section, **Updating the channel list**: **Only when I ask**, **Suggest when I open**, or **Automatically on Wi-Fi**.
    image_bookmark:
      shot_id: streams.catalog-update-toolbar
      device_profile: phone
      screen_state: streams-catalog-update-toolbar
      alt: The Streams toolbar with the Update catalog button and a catalog update summary message showing new, updated and removed channel counts
      caption: "Update catalog, right there in the toolbar."
      title: "Screenshot: Update catalog in the toolbar"
      desc: Streams screen toolbar with the Update catalog icon, a recent catalog update summary message visible below it, phone portrait.
  - number: 2
    id: whats-in-the-catalog
    title: What is actually in the catalog
    text: |
      The catalog is more than one curated list. Alongside FastMediaSorter's own picks, it folds in private community radio and public webcams from open, keyless directories - laut.fm, the Xiph Icecast directory, WebRadioDB, Radio Paradise, iptv-org's weather and nature cams, AKC live cams, and Transport for London traffic cameras, the last marked as short clips rather than live streams.

      Every name has been cleaned up at the source. Entries that used to show as "(null)", a bare dash, raw HTML codes instead of accented letters, or hundreds of stations all called "Online Radio" now arrive readable, with a nameless station listed by its own address instead. Nothing was dropped to get there - the only rows that left were exact duplicates, the same station filed once under http and once under https. A title of the form "Name (Name)" also collapses to plain "Name"; a parenthetical that actually says something different, such as a region or a quality, stays as it is.

      Every channel carries one of 31 rubrics instead of a loose, free-text topic, shown in whichever language the interface is running in - things like News, Jazz & Blues or Webcam. Sorting by rubric follows the alphabet of that language.
  - number: 3
    id: grid-or-list
    title: Grid tiles or a scrolling list
    text: |
      Switch the whole catalog between [grid view](term:grid-view), a picture tile for every channel, and [list view](term:list-view), one line per channel with more room for text - the same **Grid view** / **List view** toggle you already know from Browse. In landscape, list view grows extra columns on its own and works the count out again whenever you rotate the device.

      What each tile or row actually shows - a live frame, a logo, a flag or a plain icon - is its own page: see [Channel pictures, logos and badges](page:streams.channel-pictures-and-badges).
    image_bookmark:
      shot_id: streams.channel-grid
      device_profile: phone
      screen_state: streams-grid-view
      alt: The channel catalog in grid view, tiles for radio and video channels with category chips
      caption: "The channel catalog in grid view."
      title: "Screenshot: Channel catalog grid view"
      desc: Streams screen in grid view, phone portrait, catalog channels shown as picture tiles with category chips.
  - number: 4
    id: narrow-with-filters
    title: Narrow it down with filters
    text: |
      Tap **Filter** to open rows for **Category**, **Language**, **Country** and **Topic** - Topic only shows up when the catalog actually carries topics. Language and Country open a type-to-filter searchable picker with flags: the language picker pins English, Russian and Ukrainian to the top, and a country's flag-and-code chip sits before its language chip in the list. Values are normalized behind the scenes, so aliases and typos in the source data fold into one option and nothing unrecognized gets hidden.

      Two small icons beside the search box split the list by media kind in one tap - **Audio**, **Video**, or **Own** for channels you added yourself - and agree with whatever the Filter dialog already has selected. The filter button carries a **Filter active** mark when something is on; **Clear filters** in the dialog drops it all at once.
    image_bookmark:
      shot_id: streams.catalog-filter-sheet
      device_profile: phone
      screen_state: streams-filter-dialog-open
      alt: The Streams filter dialog open with Category, Language, Country and Topic rows and Audio/Video/Own media type choices
      caption: "The filter dialog - category, language, country, topic and media kind."
      title: "Screenshot: Streams filter dialog"
      desc: Filter dialog open over the Streams screen, category/language/country/topic rows visible, media kind chips below.
  - number: 5
    id: sort-your-way
    title: Sort it your way
    text: |
      Tap **Sort** and pick **By name**, **By topic**, **By language**, **By country** or **Recently added**. Set a default order and a default media kind for every new visit in **Settings**, the **Streams** section, fields **Default order** and **Show by default**.

      Whatever you land on - filter, media kind, sort and search text alike - is restored the next time you open Streams, and the list even opens back at the same channel you last scrolled to.
  - number: 6
    id: curated-collections
    title: Jump into a curated collection
    text: |
      When the catalog carries them, named collections such as Russian TV, radio of the former USSR, African TV or a regional set show up as a chip strip under the toolbar, with an **All** chip to leave it. Pick one and the list narrows to its members in the curator's own order, while search, sorting and your other filters keep working right alongside it. A channel can belong to more than one collection, and the strip stays out of the way entirely when the catalog has none.
    image_bookmark:
      shot_id: streams.collection-chip-strip
      device_profile: phone
      screen_state: streams-collections-strip
      alt: A row of collection chips under the Streams toolbar, including All and several named curated collections
      caption: "Curated collections, as a chip strip under the toolbar."
      title: "Screenshot: Streams collections strip"
      desc: Streams screen toolbar area with the collections chip strip visible, one collection selected.
  - number: 7
    id: check-channel-health
    title: See which channels are alive before you tap one
    text: |
      Refreshing the list does more than fetch new rows: FastMediaSorter checks every visible channel for reachability while it works, showing **Checking streams..** and marking each one green or red as the answer comes in - a channel that fails shows **No signal**. Touching the screen stops the probe where it is, and video tiles get a fresh thumbnail out of the same pass.

      The marks are informational, not permanent - clear them any time with **Settings**, the **Streams** section, **Clear play marks**; the channels themselves stay exactly where they were.
    image_bookmark:
      shot_id: streams.refresh-health-marks
      device_profile: phone
      screen_state: streams-refresh-health-probe
      alt: The Streams list mid-refresh with the Checking streams message showing and channel rows marked green or red for reachability
      caption: "Checking streams.. - green for reachable, red for No signal."
      title: "Screenshot: Streams refresh health probe"
      desc: Streams list during a refresh health probe, some rows marked green, one marked red as No signal.
  - number: 8
    id: card-overflow-menu
    title: The three-dot menu on every card
    text: |
      Every card, in grid or list, carries a three-dot **More actions** button of its own: **Edit** for a channel you added by hand, **Send link**, and **Remove**. Where FastMediaSorter's launcher is set up, **Add to home screen** is there too - no long-press or hidden gesture needed for any of it.
    image_bookmark:
      shot_id: streams.card-overflow-menu
      device_profile: phone
      screen_state: streams-card-overflow-menu
      alt: A Streams channel card's three-dot menu open showing Edit, Send link, Remove and Add to home screen
      caption: "The three-dot menu on a channel card."
      title: "Screenshot: Streams card overflow menu"
      desc: Streams list row with its overflow menu open, Edit/Send link/Remove/Add to home screen options visible.
  - number: 9
    id: about-this-channel
    title: "About this channel: what the app knows, right now"
    text: |
      Open **About this channel** from a card's menu, in either view, or from the player's own menu while a stream plays - a channel already on screen is read straight off the running player instead of being opened a second time. It lays out what the list stores about the channel next to what its transmission is actually carrying: codecs, picture size, frame rate, bitrates and the incoming data rate, all measured the moment the window opens. **Copy all** puts the whole readout on the clipboard as plain text.
    image_bookmark:
      shot_id: streams.about-channel-window
      device_profile: phone
      screen_state: streams-about-channel-window
      alt: The About this channel window showing Channel, Catalog record and This connection groups with codec, picture size and bitrate details
      caption: "About this channel - what the app knows, and what it is measuring right now."
      title: "Screenshot: About this channel window"
      desc: About this channel dialog open, Channel/Catalog record/This connection groups visible with measured stream details.
  - number: 10
    id: clear-all-downloaded
    title: Starting fresh - clear every downloaded channel
    text: |
      Tap **Clear all downloaded** to remove, in one confirmed action, everything that came from the catalog or an imported playlist. Channels you typed in by hand are never touched. FastMediaSorter counts what it removed when it is done, so you know the clean-up actually happened.
    image_bookmark:
      shot_id: streams.clear-downloaded-confirm
      device_profile: phone
      screen_state: streams-clear-downloaded-confirm
      alt: The Clear downloaded channels confirmation dialog warning that catalog and imported channels will be removed while hand-added ones stay
      caption: "Clearing every downloaded channel, hand-added ones kept."
      title: "Screenshot: Clear downloaded channels confirmation"
      desc: Confirmation dialog over the Streams screen for Clear all downloaded, message and confirm/cancel buttons visible.
  - number: 11
    id: empty-state
    title: An empty list that helps you start
    text: |
      A Streams screen with nothing in it says so plainly - "No streams yet. Add a URL or import a list." - with **Add stream** and **Import list** buttons built right into that message. Both open the exact same dialogs as their toolbar counterparts, so getting started never means hunting for the right icon first.
    image_bookmark:
      shot_id: streams.catalog-empty-state
      device_profile: phone
      screen_state: streams-catalog-empty-state
      alt: The Streams empty state with No streams yet text and inline Add stream and Import list buttons
      caption: "An empty list with a way straight back in."
      title: "Screenshot: Streams empty state"
      desc: Streams screen empty state, message text with inline Add stream and Import list buttons.
  - number: 12
    id: in-the-player
    title: In the player - flip channels, keep your chosen tracks
    text: |
      Open a video channel and the previous/next buttons at the top of the fullscreen player move you to the previous or next video channel in the catalog's own order, re-sourcing the player each time instead of stepping through unrelated files. The title bar and the filename overlay both show the channel's real name instead of a raw address, falling back to a name built from the URL for anything the catalog does not recognize.

      Video channels also remember the audio and subtitle track you picked, per channel. Set a general default in **Settings** - **Audio language for streams** and **Subtitle language for streams** - and override it for one channel at a time in that channel's own edit dialog, fields **Audio language**, **Subtitle language** and a **Subtitles** switch.
    image_bookmark:
      shot_id: streams.player-channel-nav
      device_profile: phone
      screen_state: streams-player-channel-nav
      alt: The fullscreen stream player with previous and next channel buttons at the top and the channel name shown in the title bar
      caption: "Flipping to the next channel, right from the player."
      title: "Screenshot: Player channel navigation"
      desc: Fullscreen video stream player, previous/next channel buttons visible at top, channel name in the title bar.
outcome: |
  You can turn Streams on and let the catalog fetch itself, keep it fresh on your own schedule, tell grid from list and get extra columns in landscape, narrow thousands of channels down with filters and curated collections, sort them your way, trust the green and red health marks before you tap play, manage a channel from its own menu or the About window, clear everything downloaded and start over, and keep flipping channels and your chosen tracks without leaving the player.
tips:
  - "**A fresh start every visit.** Leave the streams screen and come back, and the search box and the filter are cleared - the full list is shown again."
  - "**Want fewer surprises when you open Streams?** Set Updating the channel list to Only when I ask in Settings, the Streams section, and nothing changes until you tap Update catalog yourself."
  - "**A channel already playing looks unfamiliar?** Open About this channel from the player's own menu - it reads live measurements off the channel that is already on screen instead of opening it a second time."
  - "**Curious what a tile's logo, flag or status dot actually means?** See [Channel pictures, logos and badges](page:streams.channel-pictures-and-badges)."
  - "**Want your own station in the list, not just the catalog's?** See [Importing M3U/IPTV playlists](page:streams.custom-m3u-playlists) for adding a channel or a whole playlist by hand."
next_recipes:
  - title: Channel pictures, logos and badges
    url: page:streams.channel-pictures-and-badges
    badge: Streams
    badge_type: docs
    description: What a live frame, a logo, a flag or a plain icon on a channel tile actually means.
  - title: Importing M3U/IPTV playlists
    url: page:streams.custom-m3u-playlists
    badge: Streams
    badge_type: docs
    description: Add your own channels by typing a URL or importing an M3U playlist.
  - title: Pinned and favorite channels
    url: page:streams.favorites-and-epg
    badge: Streams
    badge_type: docs
    description: Keep your favorite channels at the top, independent of any filter or sort.
  - title: Playing live streams and radio
    url: page:streams.live-stream-playback
    badge: Streams
    badge_type: docs
    description: What happens once you actually tap a channel and playback starts.
---

The [catalog](term:catalog) is how [Streams](term:streams-screen) goes from empty to thousands of [channels](term:channel) worth browsing: download it, keep it fresh, then find your way around with filters, curated collections, sorting and a list that remembers exactly where you left it.
