---
page_id: storage.batch-renaming
title: Renaming Files and Folders
nav_title: Renaming files and folders
description: How to rename one file, a folder or several files at once in the file browser or in the player - on the phone, on network folders and in the cloud - and how to take a rename back.
category: Sources, Destinations & File Operations
category_slug: storage
ticket: S2949
flavor: All editions
recipe_number: "07"
canonical_url: documentation/storage/batch-renaming.html
why: |
  Camera names like `IMG_20260712_183412.jpg` tell you nothing a year later. "Anna's first bike ride.jpg" does. A good name makes a photo easy to find by search and easy to recognize in any app.

  FastMediaSorter renames right where you look at your files: one photo in the player, or a whole batch in the [file browser](term:file-browser), each with its own new name. It works on the phone, on the memory card, on [network folders](term:network-folder) and in [cloud storage](term:cloud-storage).
ingredients:
  - "FastMediaSorter in any [edition](term:edition): Standard, noLegal, Lite, Photos, Legacy, VR or FOSS."
  - "A [resource](term:resource) the app can write to. On a read-only resource the Rename button does not appear."
steps:
  - number: 1
    id: one-file
    title: Rename one file
    text: |
      In the file browser tick the file and tap **Rename** in the bar at the bottom. The **Renaming..** window shows the current name with the part before the extension already selected, so you can simply type the new name - the `.jpg` stays. Tap **Apply**.

      If the name is empty, the app says "File name cannot be empty." If another file already has that name, you see "File already exists: .." and can choose a different one.
    image_bookmark:
      shot_id: storage.rename-single
      device_profile: phone
      screen_state: browse-rename-single-dialog
      alt: The Renaming.. window with one name field, the part before .jpg selected, and the Cancel and Apply buttons
      caption: "Type the new name; the extension stays."
      title: "Screenshot: Rename one file"
      desc: Browse on a local folder, one photo selected, Rename tapped.
  - number: 2
    id: several-files
    title: Rename several files at once
    text: |
      Tick several files and tap **Rename**. The window now lists every selected file with its own name field, filled with its current name. Go down the list and change the names you want - you can leave some as they are. Tap **Apply**, and all changes are made in one go.

      If one of the new names is already taken, that file keeps its old name and the app lists it afterwards; the others are renamed.
    image_bookmark:
      shot_id: storage.rename-multiple
      device_profile: phone
      screen_state: browse-rename-multiple-dialog
      alt: The rename window with a list of five photos, each in its own editable name field, and the Cancel and Apply buttons
      caption: "Every selected file gets its own name field."
      title: "Screenshot: Rename several files"
      desc: Browse, 5 photos selected, Rename tapped, two names already edited.
  - number: 3
    id: folder
    title: Rename a folder
    text: |
      Folders are renamed the same way: tick the folder and tap **Rename**, or use **Rename** in the folder's own [three-dots menu](term:three-dots-menu). Everything inside the folder stays as it is.
  - number: 4
    id: player
    title: Rename while you look at a photo
    text: |
      In the player tap **Rename** on the [command panel](term:command-panel). The same **Renaming..** window opens for the file on the screen. This is the quickest way to give good names to photos while you go through them one by one. With a keyboard you can also use its rename shortcut, and a [touch zone](term:touch-zones) can be set to rename.
  - number: 5
    id: undo
    title: Take a rename back
    text: |
      Right after renaming in the file browser, tap **Undo** in the message at the bottom or in the operations bar, and the old name comes back. Other apps such as the gallery see the new names too: after a rename the app tells Android about the change, so the file does not appear twice or vanish from them.
outcome: |
  The birthday photos are called "Anna's birthday 01.jpg" to "Anna's birthday 12.jpg", the scans have readable names, and you can find any of them later by typing a word into search.
tips:
  - "**Keep the extension.** Changing `.jpg` to something else does not convert the file - it only confuses other apps. The app selects just the name part for you, so you do not remove it by accident."
  - "**Names on network folders** follow the rules of the computer they live on; Windows, for example, does not allow characters like `?` or `:` in names."
  - "**Want files in a certain order?** Start their names with a number, like `01 Arrival.jpg`, `02 Beach.jpg` - and sort the folder by name (see [Sorting, filtering and quick search](page:browsing.sorting-and-filtering))."
next_recipes:
  - title: Copying, moving and deleting files
    url: page:storage.file-copy-move-delete
    badge: Storage
    badge_type: other
    description: The other everyday operations of the file browser.
  - title: Sorting, filtering and quick search
    url: page:browsing.sorting-and-filtering
    badge: Browsing
    badge_type: image
    description: Find your newly named files in a moment.
  - title: Quick Sort destinations
    url: page:storage.destination-targets-setup
    badge: Storage
    badge_type: other
    description: Sort renamed photos into their folders with one tap.
---

Rename one file, a folder or several files at once in the file browser or in the player - on the phone, on network folders and in the cloud - and take a rename back with Undo.
