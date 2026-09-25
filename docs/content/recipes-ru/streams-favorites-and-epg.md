---
page_id: streams.favorites-and-epg
title: Pinned and Favorite Channels
nav_title: Pin and favorite channels
description: How to pin a channel to the top of Streams, reorder your pinned channels, tell a pinned tile apart at a glance, filter down to pinned-only, and star a channel into the shared Favorites list.
category: Интернет-трансляции
category_slug: streams
ticket: S2954
flavor: Standard, noLegal, Legacy and VR
recipe_number: "04"
canonical_url: documentation/streams/favorites-and-epg-ru.html
why: |
  Once you have more than a handful of channels, the ones you actually check every day get buried among the rest. FastMediaSorter gives you two separate ways to keep track of them: pinning, which is local to the Streams screen and puts a channel at the top of your own list in whatever order you choose, and [Favorites](term:favorites), the star-marked list shared across the whole app. Neither changes what a channel plays - both are just faster ways back to it.
ingredients:
  - "FastMediaSorter installed in the Standard, noLegal, Legacy or VR [edition](term:edition). Streams and everything on this page is absent from Lite, Photos and FOSS."
  - "The [Streams](term:streams-screen) screen open, with some channels in it - your own or from the [catalog](term:catalog). See [Browsing internet stream channels](page:streams.channel-catalog-browsing) for how the catalog fills the list."
  - "For the Favorites steps: Favorites turned on in Settings (**Enable Favorites**)."
steps:
  - number: 1
    id: pin-a-channel
    title: Pin a channel to the top of your list
    text: |
      Long-press a channel's row or tile, or tap the small pin icon on a list row, and the channel jumps to a **Pinned** section at the top of Streams. The pin icon fills in and changes color once a channel is pinned, so you can tell pinned channels apart from the rest at a glance. Long-press again, or tap the same pin icon, to unpin it.
    image_bookmark:
      shot_id: streams.pin-channel-long-press
      device_profile: phone
      screen_state: streams-list-pin-toggle
      alt: A Streams list row with its pin icon filled in and colored to show the channel is pinned
      caption: "Long-press a channel, or tap its pin icon, to pin or unpin it."
      title: "Screenshot: Pinning a channel"
      desc: Streams list view, one row's pin icon filled and colored to indicate a pinned channel.
  - number: 2
    id: pinned-badge-grid
    title: Spot a pinned channel in grid view
    text: |
      Switch to grid view and a pinned channel carries a small red **Pinned** badge in the top-left corner of its tile. The badge itself is not tappable - you pin or unpin a tile from its ⋮ menu, which leads with **Pin** or **Unpin** depending on the channel's current state.
    image_bookmark:
      shot_id: streams.grid-pinned-badge
      device_profile: phone
      screen_state: streams-grid-pinned-badge
      alt: A Streams grid tile with a small red Pinned badge in its top-left corner
      caption: "The red Pinned badge marks a pinned tile."
      title: "Screenshot: Grid tile pinned badge"
      desc: Streams grid view, one tile showing the red Pinned badge in the top-left corner.
  - number: 3
    id: pinned-main-sections
    title: Pinned and everything else, as two sections
    text: |
      As soon as you pin your first channel, Streams splits into two independently scrolling sections: **Pinned** on top, **Channels** below, each with its own header. Tap a header to collapse that section and give the other the room - collapsing Pinned expands Channels and the other way round, so one of the two always stays visible. Unpin your last channel and the split goes away on its own.
    image_bookmark:
      shot_id: streams.pinned-sections-collapse
      device_profile: phone
      screen_state: streams-sections-split-view
      alt: The Streams screen split into a Pinned section above a collapsible Channels section, each with its own header
      caption: "Pinned and Channels - two sections, each collapsible on its own."
      title: "Screenshot: Pinned and Channels sections"
      desc: Streams screen with the Pinned section above the main Channels section, both headers with chevrons visible.
  - number: 4
    id: reorder-pinned
    title: Put your pinned channels in the order you want
    text: |
      With two or more channels pinned, each one's ⋮ menu gains three extra rows: **Move up**, **Move down** and **Move to top**. They move a channel only within the pinned block, and gray out at the ends - Move up and Move to top on the first pinned channel, Move down on the last - so there is never a row to tap that would not do anything. The order you set here is the same order the [streams panel](term:streams-panel) on the [main screen](page:getting-started.main-screen-overview) plays your pinned channels in.
    image_bookmark:
      shot_id: streams.reorder-pinned-menu
      device_profile: phone
      screen_state: streams-reorder-menu-open
      alt: A pinned channel's overflow menu open showing Move up, Move down and Move to top rows
      caption: "Move up, Move down, Move to top - reordering the pinned block."
      title: "Screenshot: Reorder pinned channel menu"
      desc: Streams screen, a pinned row's overflow menu open with the three reorder rows visible.
  - number: 5
    id: pinned-only-filter
    title: Show just your pinned channels
    text: |
      Tap **Filter** on the Streams screen and turn on **Pinned only** to hide everything else and see just the channels you pinned - handy once your list has grown past the ones you actually check every day. Turn the same switch off to see the whole list again.
    image_bookmark:
      shot_id: streams.pinned-only-filter-toggle
      device_profile: phone
      screen_state: streams-filter-pinned-only-on
      alt: The Streams Filter dialog with the Pinned only switch turned on
      caption: "Pinned only, in the Filter dialog."
      title: "Screenshot: Pinned only filter"
      desc: Filter dialog open over Streams, the Pinned only switch turned on.
  - number: 6
    id: favorite-a-channel
    title: Star a channel into Favorites
    text: |
      Pinning only affects Streams; [Favorites](term:favorites) is the star-marked list shared across the whole app. Open a channel's ⋮ menu and tap **Add to favorites** and the channel joins Favorites by name, reachable from the Favorites tab exactly like a favorited file - independent of whether that channel is also pinned. Tap **Remove from favorites** the same way to take it off again.
    image_bookmark:
      shot_id: streams.favorite-channel-menu
      device_profile: phone
      screen_state: streams-favorite-menu-open
      alt: A channel's overflow menu open with the Add to favorites row visible
      caption: "Add to favorites, from the channel's own menu."
      title: "Screenshot: Add channel to favorites"
      desc: Streams screen, a channel's overflow menu open with the Add to favorites row visible.
outcome: |
  You can pin the channels you check every day to the top of Streams in the order you want, tell a pinned channel apart at a glance in either view, narrow the list down to pinned-only, and keep a separate, shared shortlist of favorite channels alongside your favorite files.
tips:
  - "**A channel's pin, its place in the pinned order, its favorite star and its play history all survive small address changes.** When the catalog republishes a channel under a cosmetically different address - https instead of http, a trailing slash added, the port spelled out - FastMediaSorter still recognizes it as the same channel, so none of that resets."
  - "**Tapping the favorite star again never creates a second entry.** It always toggles the one Favorites entry you already have for that channel, even after an address like the above changes underneath it."
  - "**Favorites row missing from a channel's menu?** Turn on **Enable Favorites** in Settings first - the row only appears once Favorites itself is on."
  - "**Want to know why a tile looks the way it does?** Pinning and favouriting never touch a channel's picture - see [Channel Pictures, Logos and Badges](page:streams.channel-pictures-and-badges)."
next_recipes:
  - title: Adding Your Own Streams and M3U Playlists
    url: page:streams.custom-m3u-playlists
    badge: Streams
    badge_type: docs
    description: Add a channel by its web address, or import a whole station list from an M3U playlist.
  - title: Browsing internet stream channels
    url: page:streams.channel-catalog-browsing
    badge: Streams
    badge_type: docs
    description: Filter, sort and search the built-in channel catalog.
  - title: Playing Live Streams and Radio
    url: page:streams.live-stream-playback
    badge: Streams
    badge_type: docs
    description: What happens when you tap a channel, on the streams panel and in the full player.
---

Keep your everyday [channels](term:channel) within reach two ways: pin them to the top of [Streams](term:streams-screen) in the order you choose, and star the ones you want in the shared [Favorites](term:favorites) list. This page covers pinning, reordering, the pinned-only filter, and adding a channel to Favorites.
