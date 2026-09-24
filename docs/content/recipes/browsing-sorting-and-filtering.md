---
page_id: browsing.sorting-and-filtering
title: Sorting, Filtering and Quick Search
nav_title: Sort, filter and search
description: How to order a folder by name, date, size, type or your own drag order, shuffle it and reshuffle on demand, narrow it down with a filter that stays with that resource, and find one file fast by typing a few letters.
category: Browsing & Sorting
category_slug: browsing
ticket: S2948
flavor: All editions
recipe_number: "02"
canonical_url: documentation/browsing/sorting-and-filtering.html
why: |
  A folder that grew to a few hundred photos, downloads or recordings stops being useful the moment you cannot find anything in it. FastMediaSorter lets you put that folder in an order that makes sense - by name, by date, by size, or in whatever order you drag the files into yourself - and narrow it down to just the files you are looking for, without ever leaving the file browser.

  None of this touches your files. Sorting, filtering and searching only change what you see and in what order; the files on disk stay exactly where and as they were.
ingredients:
  - "FastMediaSorter installed, with a [resource](term:resource) open in the [file browser](page:browsing.grid-and-list-views)."
  - "Sort modes, shuffle, manual reordering, filters and live search are part of every [edition](term:edition): Standard, noLegal, Lite, Photos, Legacy, VR and FOSS."
  - "More than a couple of files to look at - sorting and filtering an almost-empty folder has little to show for it."
steps:
  - number: 1
    id: choose-sort-order
    title: Choose how your files are sorted
    text: |
      Open a resource in the file browser and tap the sort button in the top bar - it already shows the current order, such as "Date ↓". Tap **Sort by** and pick a mode: by name, date, size or type, or - for a folder of one kind of file - by artist, title, duration or the date a photo was taken.

      FastMediaSorter remembers the sort mode for each resource on its own, so the folder greets you with the same order the next time you open it.
    image_bookmark:
      shot_id: browsing.sort-by-menu-open
      device_profile: phone
      screen_state: browse-sort-dialog-open
      alt: The Sort by menu open over the file browser, listing sort modes such as Name, Date, Size, Artist, Title, Duration and Random
      caption: "The Sort by menu in the file browser."
      title: "Screenshot: Sort by menu"
      desc: File browser toolbar with the Sort by dialog open, several sort modes visible, phone portrait.
  - number: 2
    id: random-shuffle
    title: Shuffle the list, then shuffle it again
    text: |
      Pick **Random** in the Sort by menu and your files line up in a fresh, mixed order. Change your mind about the mix without leaving the screen: tap **Random** again - it is already selected - and FastMediaSorter reshuffles on the spot, landing on a different order every time, with no folder rescan needed.
    image_bookmark:
      shot_id: browsing.random-shuffle-active
      device_profile: phone
      screen_state: browse-random-shuffle
      alt: The file browser sorted with the Random mode selected in the Sort by menu
      caption: "Random sort - tap it again any time for a new mix."
      title: "Screenshot: Random sort active"
      desc: File browser with Random sort mode highlighted, phone portrait.
  - number: 3
    id: manual-reorder
    title: Put files in the order you choose
    text: |
      Pick **Manual Order** to take the sequence into your own hands. Long-press a file and drag it to a new spot; a small drag handle appears on each row while manual order is active, showing you where dragging is allowed.

      FastMediaSorter keeps the order you set for that folder, so it stays exactly as you left it the next time you open it.
    image_bookmark:
      shot_id: browsing.manual-sort-drag-handle
      device_profile: phone
      screen_state: browse-manual-sort-drag
      alt: A file being dragged to a new position in the file browser with a drag handle shown on each row in Manual Order mode
      caption: "Dragging a file to a new spot in Manual Order."
      title: "Screenshot: Manual reorder drag handle"
      desc: File browser list in Manual Order mode, one row lifted mid-drag with the handle icon visible.
  - number: 4
    id: filter-files
    title: Narrow the list with a filter
    text: |
      Tap the filter icon in the top bar to open **Filter**. Set a date range, a size range in megabytes, or tick only the media types you want - photos, videos, music, GIFs, documents and more, depending on what the resource holds. Tap **Apply filter** and the list narrows right away.

      The filter belongs to that one resource and never leaks into another one you open afterwards. Come back to the same resource later and FastMediaSorter tells you it restored the filter you left it with.
    image_bookmark:
      shot_id: browsing.filter-dialog-open
      device_profile: phone
      screen_state: browse-filter-dialog
      alt: The Filter dialog over the file browser with date range, file size range and media type checkboxes
      caption: "The Filter dialog - date, size and media type."
      title: "Screenshot: Filter dialog"
      desc: Filter dialog open over Browse, date and size fields visible, media type checkboxes below.
  - number: 5
    id: filter-summary
    title: See at a glance what is filtered
    text: |
      Whenever a filter is on, Browse restates it in plain words right under the toolbar, and the filter button itself carries a small number showing how many filters are active. Tap the filter button any time to change them, or tap **Clear filter** in the dialog to drop them all at once.
    image_bookmark:
      shot_id: browsing.active-filter-strip
      device_profile: phone
      screen_state: browse-active-filter-strip
      alt: A text strip under the Browse toolbar spelling out the active filter, with a numbered badge on the filter button
      caption: "The active filter, spelled out under the toolbar."
      title: "Screenshot: Active filter strip"
      desc: Browse toolbar with the filter summary strip visible and a badge count on the filter icon.
  - number: 6
    id: filtered-empty-state
    title: When the list comes up empty
    text: |
      If a folder looks empty after filtering, FastMediaSorter says why instead of leaving you guessing. When the **Maximum file size** setting is the reason files are missing, Browse names the setting and tells you how many files it is hiding, with a direct hint to raise the limit in Settings.
    image_bookmark:
      shot_id: browsing.filtered-empty-state
      device_profile: phone
      screen_state: browse-filtered-empty
      alt: The Browse empty state naming the maximum file size setting and the number of files it is hiding, with a hint to raise the limit
      caption: "An empty list that explains itself."
      title: "Screenshot: Filtered empty state"
      desc: Browse empty-state view with the maximum-file-size explanation and hint text shown.
  - number: 7
    id: live-search
    title: Find one file fast with live search
    text: |
      Tap the search icon and a search box drops into the top bar. Type a few letters of the name and the list narrows as you type, filtered right there in memory with no rescan to wait on. Tap the close button on the search box to see everything again.
    image_bookmark:
      shot_id: browsing.live-search-box
      device_profile: phone
      screen_state: browse-live-search-open
      alt: The Browse search box open in the top bar with a partial file name typed in and the list narrowed to matching files
      caption: "Live search narrows the list as you type."
      title: "Screenshot: Live search box"
      desc: Browse toolbar with the search box expanded, a few letters typed, filtered list beneath.
outcome: |
  You can put a folder in the order that makes sense for it, shuffle it for something new any time, narrow it down with a filter that stays with that one resource, and jump to a single file by typing a few letters - all without leaving the file browser.
tips:
  - "**Want every new resource to start already sorted your way?** Set a default in Settings, the Player tab, Sorting, slideshow and playback order section, field Default sort mode."
  - "**Sorting works the same in grid and list.** The Sort by menu and the Filter dialog behave identically whether Browse is showing thumbnails or a detail list - see [Browsing media collections](page:browsing.grid-and-list-views)."
  - "**A big folder feels slow to sort on first open?** See [Thumbnail caching and indexing](page:browsing.media-indexing-and-caching) for how FastMediaSorter keeps large folders responsive."
  - "**Filtering by file name too?** That comes from the same box as live search - type a name there and Filter keeps it alongside your date, size and type choices."
next_recipes:
  - title: Browsing media collections
    url: page:browsing.grid-and-list-views
    badge: Browsing
    badge_type: docs
    description: Switch between grid and list, open folders, and find your way around the file browser.
  - title: Multi-selection and batch operations
    url: page:browsing.batch-selection
    badge: Browsing
    badge_type: docs
    description: Select several files at once and copy, move, delete or share them together.
  - title: Thumbnail caching and indexing
    url: page:browsing.media-indexing-and-caching
    badge: Browsing
    badge_type: docs
    description: How FastMediaSorter keeps thumbnails and large folders fast to browse.
---

Put a [resource](term:resource) in the order that suits it, narrow it down with a filter that stays with that resource, or jump straight to one file with live search - all from the same toolbar in the [file browser](term:file-browser).
