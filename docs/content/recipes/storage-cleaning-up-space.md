---
page_id: storage.cleaning-up-space
title: Freeing Space - Duplicates, Big Files and ZIP Archives
nav_title: Duplicates, big files and ZIP archives
description: How to find identical files across your folders and delete the extra copies, remove every file above or below a chosen size in one go, and pack selected files into a ZIP archive.
category: Sources, Destinations & File Operations
category_slug: storage
ticket: S2949
flavor: Standard, noLegal, Legacy and VR; ZIP archives in all editions
recipe_number: "08"
canonical_url: documentation/storage/cleaning-up-space.html
why: |
  The phone says the storage is almost full. Somewhere in the camera roll, the WhatsApp folder and the old backup from the laptop, the same photos sit three times over, and a few forgotten screen recordings take gigabytes each.

  This page shows three tools that deal with it: a finder for identical files, a way to delete files by size, and a ZIP packer that turns a pile of small files into one tidy archive.
ingredients:
  - "Finding duplicates and deleting by size: the Standard, noLegal, Legacy and VR [editions](term:edition)."
  - "ZIP archives: every edition - Standard, noLegal, Lite, Photos, Legacy, VR and FOSS."
  - "A [resource](term:resource) you can write to. ZIP archives are made from files on this device only."
steps:
  - number: 1
    id: open-duplicates
    title: Start the search for duplicates
    text: |
      Open a resource in the [file browser](term:file-browser), open the [three-dots menu](term:three-dots-menu) at the top and tap **Find Duplicates**. The **Duplicates** screen opens with a list of all your resources - "Select resources to scan for duplicates". The resource you came from is already ticked and sits at the top; tick any others that may hold copies, for example the camera folder and the WhatsApp folder together.

      Tap **Start Scan**. If a network or cloud resource is ticked, the app warns first: "Network scan may take a long time and consume a lot of traffic. Continue?"
    image_bookmark:
      shot_id: storage.duplicates-select-resources
      device_profile: phone
      screen_state: duplicates-resource-selection
      alt: The Duplicates screen with a list of resources, two of them ticked, and the Start Scan button
      caption: "Choose where to look for copies."
      title: "Screenshot: Duplicates - choose resources"
      desc: Duplicates screen opened from a local folder, two local resources ticked.
  - number: 2
    id: review-duplicates
    title: Look at the groups of identical files
    text: |
      The app compares files in three rounds - first by size, then by their first few bytes, and at last by their complete content - so only truly identical files end up together, however they are named. The scan keeps running with a notification if you leave the screen.

      The result is a list of groups, the biggest waste first: each group says how many files it has and how much space the extra copies take ("Wasted space: 240 MB"). In every group the app has already ticked all copies except the oldest one, which it treats as the original.
    image_bookmark:
      shot_id: storage.duplicates-groups
      device_profile: phone
      screen_state: duplicates-result-groups
      alt: The Duplicates result with groups of identical photos, each group showing its file count and wasted space, all copies except the oldest ticked
      caption: "Groups of identical files, extra copies already ticked."
      title: "Screenshot: Duplicate groups"
      desc: Duplicates result, three groups, the Delete Selected button at the bottom.
  - number: 3
    id: delete-duplicates
    title: Delete the extra copies
    text: |
      Change the ticks if you want to keep a different copy - you can also drag across several files to tick them at once - or delete one file with its own delete button. Then tap **Delete Selected**, which shows how many files and how much space it will free. Confirm, and the selected copies are deleted for good.

      In a hurry? **Find and Delete Duplicates** in the same menu opens the same screen, ready to delete right after the scan.
    callout:
      type: warning
      title: This deletion cannot be undone
      text: "The app asks 'Are you sure you want to permanently delete..?' before it deletes. Look through the ticks once more - especially in groups where the copies sit in different folders."
  - number: 4
    id: delete-by-size
    title: Delete every file above or below a size
    text: |
      In the file browser's three-dots menu tap **Delete by Size..**. In the window choose:

      - **Smaller than** or **Larger than**.
      - A number, and the unit **KB**, **MB** or **GB**.

      Tap **Analyze**. The **Confirm Deletion** window shows how many files match and how much space you will free, for example "Files found: 18, Space to free: 6.2 GB". If nothing matches, the app says "No matching files found". Tap **Delete Files** to delete them.

      Handy for two jobs: **Larger than 500 MB** finds forgotten videos, and **Smaller than 20 KB** finds tiny thumbnails and broken pictures that clutter a folder.
    image_bookmark:
      shot_id: storage.delete-by-size-dialog
      device_profile: phone
      screen_state: delete-by-size-settings
      alt: The Delete by Size window with Larger than selected, the value 500 and the unit MB, and the Analyze button
      caption: "Choose the size limit, then Analyze."
      title: "Screenshot: Delete by Size"
      desc: Delete by Size dialog, Larger than 500 MB.
    callout:
      type: warning
      title: Network and cloud folders have no trash
      text: "On a network or cloud resource the confirmation adds a red line: 'Warning: trash is not supported for this resource. Deletion will be permanent!' On the phone's own folders deleted files go to the [trash](term:trash) when it is switched on - see [Copying, moving and deleting files](page:storage.file-copy-move-delete)."
  - number: 5
    id: zip
    title: Pack files into a ZIP archive
    text: |
      In a folder on this device, select the files you want to pack (long-press the first one, then tap the others). Open the three-dots menu and tap **Archive**. The item is shown but grayed out until at least one file is selected.

      The **Archive Selected Files** window suggests a name - the first file's name, or today's date. Change it if you like (without `.zip`, the app adds it) and check the line **Destination folder** - the archive is saved in the folder you are looking at. Tap **Archive**.

      A progress window counts the files ("12 of 40: IMG_2031.jpg") and can be canceled; a canceled archive is removed, so no half-made file is left behind. At the end you see "Archive created: holiday.zip (40 files)". If an archive with that name already exists, the new one is named `holiday_1.zip` rather than replacing it.
    image_bookmark:
      shot_id: storage.archive-dialog
      device_profile: phone
      screen_state: browse-archive-dialog
      alt: The Archive Selected Files window with the archive name field and the destination folder line, over a file browser with several photos selected
      caption: "Name the archive and tap Archive."
      title: "Screenshot: Archive Selected Files"
      desc: Browse with 5 photos selected, Archive dialog open.
outcome: |
  The same photo is kept once instead of three times, the forgotten giant videos are gone, and the scans of old documents travel as one ZIP file. The storage bar on your phone has room again.
tips:
  - "**Duplicates between the phone and a network folder** can be found too - tick both resources. Expect it to take longer, because the network files have to be read."
  - "**Archives from network or cloud files** are not possible: 'Archiving is only supported for local files.' Copy the files to the phone first."
  - "**See how much you have already freed**: with statistics collection on, the **Freed space** card adds up every byte your deletions gave back - see [Usage statistics](page:programs.usage-statistics)."
next_recipes:
  - title: Copying, moving and deleting files
    url: page:storage.file-copy-move-delete
    badge: Storage
    badge_type: other
    description: Everyday file work, the trash and Undo.
  - title: Running file jobs on a schedule
    url: page:storage.scheduled-operations
    badge: Storage
    badge_type: other
    description: Let the app clean up or move files by itself every night.
  - title: Usage statistics
    url: page:programs.usage-statistics
    badge: Programs
    badge_type: docs
    description: See how many files you sorted and how much space your deletions freed.
---

Find identical files across your folders and delete the extra copies, remove every file above or below a chosen size in one go, and pack selected files into a ZIP archive.
