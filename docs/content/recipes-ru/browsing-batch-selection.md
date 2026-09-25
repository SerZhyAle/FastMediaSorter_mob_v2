---
page_id: browsing.batch-selection
title: Multi-Selection and Batch Operations
nav_title: Select and batch actions
description: How to select several files at once with long-press and range-select, run copy, move, delete, share or archive on the whole batch in one tap, act on a single file from its own menu or a swipe, and keep a large copy or move running in the background.
category: Browsing & Sorting
category_slug: browsing
ticket: S2948
flavor: Все 7 редакций
recipe_number: "03"
canonical_url: documentation/browsing/batch-selection-ru.html
why: |
  Some jobs need more than one file at a time - clearing out a folder of screenshots, moving a week of photos to a backup drive, sharing five files with a friend in one go. FastMediaSorter lets you tick as many files as you need and run one action on all of them at once, while a swipe or the row's own menu still covers the times you only want to act on a single file.

  A copy or move of many files can take a while, especially over a network. It keeps running in the background once you send it there, so you are never stuck staring at a progress bar.
ingredients:
  - "FastMediaSorter installed, with a [resource](term:resource) open in the [file browser](page:browsing.grid-and-list-views)."
  - "Multi-selection, the grouped file menu, row swipe actions and the background transfer strip are part of every [edition](term:edition): Standard, noLegal, Lite, Photos, Legacy, VR and FOSS."
  - "Write access to the resource for Move, Rename, Delete and Archive - a read-only resource still allows Copy and Share."
steps:
  - number: 1
    id: select-multiple
    title: Select more than one file
    text: |
      Long-press any file to enter selection mode. A checkbox appears on every row, and the file you pressed is already ticked. Long-press the checkbox of another file and every file between the two gets selected in one go, instead of ticking each one by hand.

      **Select All** and **Deselect All** in the top bar cover the two extremes, and tapping a checked box again removes just that one file from the selection.
    image_bookmark:
      shot_id: browsing.range-select-checkbox
      device_profile: phone
      screen_state: browse-range-select
      alt: The file browser in selection mode with several files ticked in a row after a long-press range selection
      caption: "Long-press a checkbox to select every file in between."
      title: "Screenshot: Range selection"
      desc: File browser in selection mode, a contiguous block of rows checked, checkboxes visible on every row.
  - number: 2
    id: batch-actions
    title: Run one action on the whole selection
    text: |
      Once you have files selected, a bar of actions appears at the bottom: **Copy**, **Move**, **Delete**, **Share** and, on this device's own storage, **Archive**. Tap one and it runs on every selected file at once. If you deleted or moved something by mistake, **Undo** appears in the same bar right after, for as long as FastMediaSorter still remembers the operation.

      **Rename** shows up on the bar too, but it only opens a dialog when exactly one file is selected - renaming a batch of files at once is a separate tool. Copying and moving need a [destination](term:destination) resource to send files to; see [Moving, copying and deleting files](page:storage.file-copy-move-delete).
    image_bookmark:
      shot_id: browsing.multiselect-actionbar
      device_profile: phone
      screen_state: browse-selection-action-bar
      alt: The Browse batch action bar with Copy, Move, Delete, Share and Archive buttons after several files are selected
      caption: "The batch action bar appears once you select files."
      title: "Screenshot: Batch action bar"
      desc: Browse bottom bar with Copy, Move, Delete, Share and Archive buttons, several files checked above it.
  - number: 3
    id: single-file-menu
    title: Act on one file with the three-dots menu
    text: |
      Tap the [three-dots menu](term:three-dots-menu) on any row to open its own menu instead. The most common actions - Open, Copy, Move, Rename and Delete - sit right at the top, one tap away. Everything else is grouped so the menu never turns into a long scroll: **Organize** (Favorite and similar), **Text** (search, translate and OCR for documents, books and images), **Editing** (crop, draw over, save a frame) and **Share and info** (send elsewhere, file information, print, lyrics). Only the groups that make sense for that file's type show up.
    image_bookmark:
      shot_id: browsing.grouped-overflow-menu
      device_profile: phone
      screen_state: browse-grouped-overflow-menu
      alt: A file's three-dots menu open in the file browser showing top-level actions plus the Organize, Text, Editing and Share and info submenus
      caption: "The per-file menu, grouped into submenus."
      title: "Screenshot: Grouped file menu"
      desc: Popup menu open from a file row's overflow button, top-level items and group headers visible.
  - number: 4
    id: swipe-actions
    title: Swipe a row for an instant action
    text: |
      In the file list, a swipe left or right runs one action without opening any menu. Out of the box, one direction deletes and the other opens **Send to..**, but you can pick a different action for either direction - or turn a direction off - in Settings, the General tab, File browser interface section, under **Swipe left action** and **Swipe right action**.

      While the row slides, it names the action it is about to run, so a swipe you did not mean to start can be let go before it fires. Deleting by swipe asks for the same confirmation as deleting from a menu, and an action turned off in Settings stays unavailable to the gesture too.
    image_bookmark:
      shot_id: browsing.swipe-action-reveal
      device_profile: phone
      screen_state: browse-swipe-reveal
      alt: A file row swiped partway to the left in the file browser, revealing the Delete action label behind it
      caption: "Swiping a row reveals the action before it runs."
      title: "Screenshot: Row swipe action"
      desc: File browser row mid-swipe, the Delete label and icon visible in the space behind the row.
    callout:
      type: tip
      title: Reaches every edition
      text: "Row swipe actions work in every edition."
  - number: 5
    id: background-transfer
    title: Let a big copy or move run in the background
    text: |
      Starting a copy or move on a batch opens a progress dialog. Tap **Background** and the operation keeps running while you carry on browsing. A slim strip then sits at the bottom of Browse, naming the operation, the percent done and the file currently being copied - tap the strip any time to bring the progress dialog back.
    image_bookmark:
      shot_id: browsing.background-transfer-strip
      device_profile: phone
      screen_state: browse-background-transfer-strip
      alt: A thin strip at the bottom of the file browser showing a copy operation in progress with a percentage and the current file name
      caption: "A backgrounded copy stays visible on a tappable strip."
      title: "Screenshot: Background transfer strip"
      desc: Browse screen with the bottom transfer strip visible, percent and current file name shown.
outcome: |
  You can gather up a whole batch with long-press and range-select and send it off with one tap, reach for a single file's own menu or a swipe when that is all you need, and keep a big transfer going in the background without losing sight of it.
tips:
  - "**Changed your mind about a swipe?** Slide the row back before letting go, or turn that direction off in Settings if you never use it."
  - "**Renaming several files at once?** That needs its own tool - see [Batch Renaming Patterns](page:storage.batch-renaming)."
  - "**Selecting with a keyboard or D-pad?** Extend the selection up or down without touching the screen - see [Keyboard, D-pad and TV navigation](page:general.keyboard-dpad-tv-navigation)."
  - "**Only seeing Copy and Share on the bar?** Move, Rename, Delete and Archive need write access to the resource - a read-only resource hides them on purpose."
next_recipes:
  - title: Sorting, filtering and quick search
    url: page:browsing.sorting-and-filtering
    badge: Browsing
    badge_type: docs
    description: Order a folder your way, shuffle it, filter it down and find one file fast.
  - title: Moving, copying and deleting files
    url: page:storage.file-copy-move-delete
    badge: Storage
    badge_type: docs
    description: Where copied and moved files go, and how undo and destinations work.
  - title: Browsing media collections
    url: page:browsing.grid-and-list-views
    badge: Browsing
    badge_type: docs
    description: Switch between grid and list, open folders, and find your way around the file browser.
---

Tick as many files as you need with long-press and range-select, then run Copy, Move, Delete, Share or Archive on the whole batch at once from the [file browser](term:file-browser) - or reach for a single file's own three-dots menu or a row swipe when that is all the job calls for.
