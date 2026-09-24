---
page_id: streams.channel-pictures-and-badges
title: Channel Pictures, Logos and Badges
nav_title: Channel pictures and badges
description: Why a channel in Streams shows a live preview, a station logo, a flag or a plain icon, what the play-status dot and the region badges mean, and how to download the picture packs that fill in the pictures you are missing.
category: Internet Streams
category_slug: streams
ticket: S2954
flavor: Standard, noLegal, Legacy and VR
recipe_number: "02"
canonical_url: documentation/streams/channel-pictures-and-badges.html
why: |
  Radio and TV [channels](term:channel) do not come with a photo attached the way a video file does, so FastMediaSorter has to find or capture a picture for each one - a live frame, the station's own logo, a small site icon, or failing all of that, just a flag or a generic symbol. None of this is random: every tile always shows something, and what it shows tells you a little about that channel before you even press play.

  The same channels also carry two kinds of small labels: a play-status dot that remembers whether a channel worked last time, and an access badge that warns you a channel might not play from where you are. Knowing what these mean saves you a wasted tap on a channel that is unlikely to work.
ingredients:
  - "FastMediaSorter installed in the Standard, noLegal, Legacy or VR [edition](term:edition). Streams and everything on this page is absent from Lite, Photos and FOSS."
  - "The [Streams](term:streams-screen) screen open, with some channels in it - your own or from the [catalog](term:catalog). See [Browsing internet stream channels](page:streams.channel-catalog-browsing) for how the catalog fills the list."
  - "An internet connection the first time you want the picture packs - after that, everything on this page works from what is already on the phone."
steps:
  - number: 1
    id: live-tiles
    title: See channels come to life in grid view
    text: |
      Switch [Streams](term:streams-screen) to [grid view](term:grid-view) and video channels start filling in with a real picture: a frame captured from the live feed itself, refreshed while you browse. FastMediaSorter keeps that frame on the phone, so the next time you open Streams the channel already shows it instead of a blank tile while a fresh one is captured in the background.

      Every tile in the grid fills in at once when the screen opens, rather than one picture trickling in after another while you wait.
    image_bookmark:
      shot_id: streams.channel-grid
      device_profile: phone
      screen_state: streams-grid-live-tiles
      alt: The Streams grid view with several video channel tiles showing live captured frames and station logos
      caption: "Grid view: live channels show a captured frame as soon as it is available."
      title: "Screenshot: Streams grid with live tiles"
      desc: Streams screen in grid view, a mix of tiles with captured frames and station logos, phone portrait.
  - number: 2
    id: picture-order
    title: How FastMediaSorter picks a picture when there is no live frame
    text: |
      Not every channel has a frame to show - radio has no picture at all, and a video channel only gets one after it has been reachable. For those, FastMediaSorter tries a fixed order of pictures, stopping at the first one it can supply:

      1. A tile from the downloadable **Channel preview atlas** - a still picture for a video channel, shown before you have watched it even once.
      2. The channel's own **station logo**, from the downloadable **Station logos** pack - this is the only picture a radio station can get, since it has no video to capture from.
      3. A small **favicon** - the site icon for that channel, bundled with the channel catalog itself, no separate download needed.
      4. The channel's **country flag**, when none of the above is available.
      5. A plain audio or video icon, when even the country is unknown.

      So a logo means the station has its own artwork in the pack; a flag means FastMediaSorter knows the channel's country but has no picture for it; a plain icon means neither is available yet. Every tier keeps trying quietly in the background, so a tile that starts as a flag can still turn into a proper logo moments later.
    image_bookmark:
      shot_id: streams.channel-picture-tiers
      device_profile: phone
      screen_state: streams-grid-fallback-tiers
      alt: Streams grid tiles side by side showing a station logo, a small favicon, a country flag and a plain audio icon for channels with no captured frame
      caption: "Four channels, four fallback pictures - logo, favicon, flag, plain icon."
      title: "Screenshot: Streams grid fallback tiers"
      desc: Streams grid view, four tiles each demonstrating a different fallback picture tier.
  - number: 3
    id: list-view-icons
    title: Favicons and flags in list view
    text: |
      Switch to [list view](term:list-view) and each row keeps a small leading icon instead of a full tile. It shows the channel's favicon when the catalog has one, or the country flag in the same spot when it does not - the row never collapses or leaves an empty gap either way. A channel with neither shows the plain audio or video icon, same as in grid view.

      Favicons in list view come from the same bundle as the catalog itself, so they are already there the moment you import channels - no extra download needed for this one.
    image_bookmark:
      shot_id: streams.list-view-favicon-flag
      device_profile: phone
      screen_state: streams-list-favicon-flag
      alt: The Streams list view with channel favicons in the leading icon slot, one row showing a country flag in that slot instead
      caption: "List view: a favicon, or a flag when there is none."
      title: "Screenshot: Streams list favicons and flag fallback"
      desc: Streams screen in list view, favicons on most rows, one row with a flag in the leading slot.
  - number: 4
    id: status-dot-and-menu
    title: Read the grid tile's status dot and open its menu
    text: |
      Every grid tile carries a small dot in the bottom-left corner: a hollow ring means the channel has not been played yet, a check mark means it played back fine last time, and an exclamation mark means the last attempt failed. Shape and colour both change, so the meaning still comes through in black and white.

      Tap the three-dot button in the top-right corner of a tile to open its menu - the same commands as the list row's overflow menu, including editing, sharing the link and removing the channel.
    image_bookmark:
      shot_id: streams.grid-tile-status-menu
      device_profile: phone
      screen_state: streams-grid-status-dot-menu
      alt: A Streams grid tile with the play-status dot visible in the bottom-left corner and its three-dot overflow menu open in the top-right corner
      caption: "The status dot, bottom-left; the tile menu, top-right."
      title: "Screenshot: Grid tile status dot and menu"
      desc: Streams grid tile close-up, status dot visible, overflow menu open with edit/share/remove actions.
  - number: 5
    id: access-badges
    title: Understand the region and access badges
    text: |
      Some channels in the catalog carry a badge next to their name in list view. A **🌐 Region-locked** badge means the channel answered with an access error during the catalog's own checks and may not play from your location - it is kept in the catalog because it can still work for someone in its home region. A **🔒 Restricted** badge covers any other access limit the catalog found, without naming a specific reason.

      Neither badge means the channel is broken everywhere - only that it is worth trying with that in mind. Tap the channel to open **About this channel** for the same information spelled out in full.
    image_bookmark:
      shot_id: streams.access-restriction-badges
      device_profile: phone
      screen_state: streams-list-access-badges
      alt: The Streams list view showing the Region-locked globe badge on one channel row and the generic Restricted lock badge on another
      caption: "Region-locked and Restricted - two different access warnings."
      title: "Screenshot: Access restriction badges"
      desc: Streams list view, one row with the Region-locked badge, another with the generic Restricted badge.
  - number: 6
    id: download-picture-packs
    title: Get the channel pictures and station logos
    text: |
      The **Channel preview atlas** and **Station logos** are downloadable picture packs, not part of the app itself - they are [extensions](term:extension), like the channel catalog they ride along with. After a catalog refresh finds new channels, FastMediaSorter offers each pack by name, states its download size in megabytes up front, and lets you say **Download** or **Later**.

      New pictures reach an installed pack as soon as they are published on the server - you do not have to wait for an app update to see them. If the pack you already have is out of date, the next catalog refresh offers a smaller update instead of the whole pack again.

      You can also manage both packs by hand any time from the Downloadable Extensions screen - see [Downloadable extensions](page:flavors.extensions-and-plugins) for how to install, update and delete them there.
    image_bookmark:
      shot_id: streams.channel-pictures-download-prompt
      device_profile: phone
      screen_state: streams-channel-pictures-download-dialog
      alt: The Channel pictures download dialog offering to download channel preview pictures, with the download size in megabytes and Download and Later buttons
      caption: "The offer to download channel pictures, size included."
      title: "Screenshot: Channel pictures download prompt"
      desc: Channel pictures dialog over the Streams screen, message text and size shown, Download/Later buttons visible.
  - number: 7
    id: thumbnails-stay-fresh
    title: Thumbnails keep themselves fresh
    text: |
      Watching a video channel updates its own tile: the frame you were just looking at becomes its thumbnail, so the grid shows what you actually saw last, not an older capture. This works whether you stop by pressing Back or by leaving the fullscreen player.

      An unreachable channel is not hammered with repeated capture attempts - FastMediaSorter backs off and tries it less often instead, and a status change repaints only the dot, so scrolling the grid never flickers. None of this needs a network connection to stay calm: with no connectivity the grid simply waits rather than timing out every visible tile, and previews pick back up on their own once the connection returns.

      The pictures themselves are kept in a small on-disk cache with its own size budget, cleared out automatically as needed - there is nothing to configure or clean up by hand.
    image_bookmark:
      shot_id: streams.player-viewed-frame-thumbnail
      device_profile: phone
      screen_state: streams-grid-tile-refreshed-thumbnail
      alt: A Streams grid tile now showing the frame captured while the channel was being watched, in place of its earlier thumbnail
      caption: "The frame you just watched becomes the tile's thumbnail."
      title: "Screenshot: Thumbnail refreshed after watching"
      desc: Streams grid view after returning from fullscreen playback, the played channel's tile showing the freshly captured frame.
outcome: |
  You can tell at a glance why a channel shows a live frame, a logo, a flag or a plain icon, what its status dot and access badges mean, and how to fetch the channel preview and station-logo packs so more channels get their own picture.
tips:
  - "**A tile still shows a flag or a plain icon after a while?** That channel is not in the current picture pack yet, or you have not downloaded the pack - see step 6 above."
  - "**Want to know exactly why a channel is badged?** Open its **About this channel** window from the tile or row menu for the full explanation."
  - "**Only care about the channels you already trust?** Pinning keeps your favourites at the top regardless of pictures or badges - see [Pinned and favorite channels](page:streams.favorites-and-epg)."
next_recipes:
  - title: Browsing internet stream channels
    url: page:streams.channel-catalog-browsing
    badge: Streams
    badge_type: docs
    description: Filter, sort and search the channel catalog to find the stations you want.
  - title: Importing M3U/IPTV playlists
    url: page:streams.custom-m3u-playlists
    badge: Streams
    badge_type: docs
    description: Add your own channels by typing a URL or importing an M3U playlist.
  - title: Downloadable extensions
    url: page:flavors.extensions-and-plugins
    badge: Editions
    badge_type: docs
    description: Install, update and delete the channel preview atlas, station logos and other downloadable extras by hand.
---

Every [channel](term:channel) in [Streams](term:streams-screen) always shows something - a live frame, a downloaded logo, a small favicon, a flag, or a plain icon - and a small dot and badge next to it tell you whether it is worth a tap. This page explains what each picture and badge means and where the channel picture packs come from.
