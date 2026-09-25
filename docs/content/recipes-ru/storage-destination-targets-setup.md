---
page_id: storage.destination-targets-setup
title: Sorting Files into Destinations with Quick Sort
nav_title: Quick Sort destinations
description: How to turn folders into numbered, colored destinations, and then copy or move the photo or video you are looking at into one of them with a single tap or a number key - the heart of sorting with FastMediaSorter.
category: Источники, назначения и операции с файлами
category_slug: storage
ticket: S2949
flavor: Все 7 редакций
recipe_number: "04"
canonical_url: documentation/storage/destination-targets-setup-ru.html
why: |
  Three thousand photos from the summer, all in one camera folder. You want the good ones in "Best of 2026", the family ones in "For grandma", the receipts in "Taxes" and the rest left where they are. Dragging files one by one in a file manager would take a week.

  [Quick Sort](term:quick-sort) turns this into a rhythm: look at a photo, tap the button of the folder it belongs to, and the next photo is already on the screen. Each folder you sort into is a [destination](term:destination) - a resource with its own number and color.
ingredients:
  - "FastMediaSorter in any [edition](term:edition). Network destinations need any edition except Lite; cloud destinations any edition except Lite and FOSS."
  - "The folders you want to sort into, added as [resources](term:resource) the app can write to - see [Adding folders from this device](page:storage.storage-sources-setup). Read-only resources cannot be destinations."
steps:
  - number: 1
    id: first-destination
    title: Meet your first destination - Downloads
    text: |
      On first launch the app already makes the phone's **Downloads** folder destination number one, so Quick Sort works out of the box: there is always at least one place to send a file.
  - number: 2
    id: make-destination
    title: Turn a folder into a destination
    text: |
      There are three ways, use whichever is closest:

      - When adding a folder, tick **Add to Quick Sort** (for a network folder or an SFTP server: **Mark for Quick Sort**).
      - On the main screen or in the [file browser](term:file-browser), open the resource's [three-dots menu](term:three-dots-menu) and tap **Add to Quick Sort**.
      - Open **Settings**, the **Management** tab, the group **Quick Sort destinations**, and tap **+ Add**. The list offers every resource you can write to that is not a destination yet.

      Each new destination gets the next free number and its own color from a set of ten.
  - number: 3
    id: arrange
    title: Arrange, recolor and limit your destinations
    text: |
      In **Settings**, **Management**, **Quick Sort destinations** you see all destinations in their order. For each one:

      - Tap the color square to pick another color - handy when "Delete later" should be red and "Keep" green.
      - Use the up and down arrows to change its place, and so its number.
      - Tap the delete button to take it off the list ("Remove Destination"). The folder and its files stay; it only stops being a destination.

      **Max destinations (1-30)** decides how many destinations you can have; the default is 10.
    image_bookmark:
      shot_id: storage.settings-quick-sort-destinations
      device_profile: phone
      screen_state: settings-management-quick-sort-list
      alt: The Quick Sort destinations group in the Management settings with five colored destinations, arrows to reorder them, the + Add button and Max destinations set to 10
      caption: "Your destinations, their colors and their order."
      title: "Screenshot: Quick Sort destinations"
      desc: Settings, Management tab, Quick Sort destinations with 5 entries.
  - number: 4
    id: sort-in-player
    title: Sort while you look at files
    text: |
      Open a folder full of unsorted photos and tap the first one. In the player, the [command panel](term:command-panel) shows two rows of colored buttons, one for each destination:

      - **Copy to..** - puts a copy into that destination and leaves the original where it is.
      - **Move to..** - moves the file there; it leaves the current folder.

      Tap a destination button and the file goes there straight away - no extra question. With **Go to next file after copying** (on by default) the next photo appears at once, so you can keep a steady pace: look, tap, look, tap. After a move the next file appears by itself, because the moved one has left the folder. The folder you are sorting from never shows a button of its own.

      The transfer runs in the background, so even a large video to the home computer does not hold you up.
    image_bookmark:
      shot_id: storage.player-quick-sort-panels
      device_profile: phone
      screen_state: player-command-panel-copy-move-rows
      alt: A photo in the player with the Copy to.. and Move to.. rows of colored destination buttons at the bottom
      caption: "Copy to.. and Move to.. with your destinations."
      title: "Screenshot: Quick Sort buttons in the player"
      desc: Player on a local photo, command panel open, 5 destinations in each row.
  - number: 5
    id: panel-layout
    title: Make the panel fit your screen
    text: |
      - Tap the **Copy to..** or **Move to..** title to fold that row away when you only copy or only move. When both are folded, they sit side by side in one line.
      - The buttons share the width of the screen by themselves: a few destinations get wide buttons, many destinations get more rows of smaller ones, and the text shrinks so that every name stays readable - on a phone, a tablet or a TV.
      - Each button keeps its destination's color, with dark or light text chosen so the name is always easy to read.
  - number: 6
    id: other-folder
    title: Send a file to a folder that is not a destination
    text: |
      At the end of each row there is a **..** button. It opens Android's folder window: pick any folder, and the file is copied or moved there just once, without adding the folder to your destinations.
  - number: 7
    id: keyboard
    title: Sort with number keys on a keyboard or TV remote
    text: |
      With a keyboard, a TV remote or a game pad, each visible button gets a small number badge. Keys **1** to **9** and **0** (for the tenth) fire the buttons in order - first all **Copy to..** buttons, then the **Move to..** buttons. Fold the Copy row away, and the numbers go to the Move buttons. The key badges show the keys you actually assigned, if you changed them in the keyboard settings (see [Keyboard, D-pad and Android TV control](page:general.keyboard-dpad-tv-navigation)).
  - number: 8
    id: overwrite-undo
    title: Same names, and changing your mind
    text: |
      - When the destination already has a file with the same name, the file is left out by default, and nothing at the destination is replaced. Turn on **Overwrite existing file when copying** or **Overwrite existing file when moving** in **Settings**, **Management**, **Copy, move and overwrite behavior** if the new file should replace the old one.
      - Tapped the wrong button? A short message with **Undo** appears at the bottom right after the copy or move. Tap it to take the last step back.
outcome: |
  The summer's three thousand photos are sorted in one evening: the best in "Best of 2026", the family ones in "For grandma", the receipts in "Taxes" - each with one tap, in colors you chose, and the camera folder is finally tidy.
tips:
  - "**Colors are a memory aid.** Give the same color to destinations that belong together, and sorting becomes a matter of reflex."
  - "**Copy first, move later.** When you sort for somebody else, copy - your own folder stays complete."
  - "**Tap zones instead of buttons.** You can also let taps on parts of the screen copy or move - see [Touch zones](term:touch-zones) in [Video playback and navigation](page:player.video-playback-controls)."
  - "**Many files at once** are copied from the file browser instead - see [Copying, moving and deleting files](page:storage.file-copy-move-delete)."
next_recipes:
  - title: Copying, moving and deleting files
    url: page:storage.file-copy-move-delete
    badge: Storage
    badge_type: other
    description: Work with many files and folders at once, the trash and Undo.
  - title: Viewing photos, GIFs and zoom gestures
    url: page:images.viewer-and-gestures
    badge: Photos
    badge_type: image
    description: Swipe through the photos you are sorting.
  - title: Running file jobs on a schedule
    url: page:storage.scheduled-operations
    badge: Storage
    badge_type: other
    description: Let the app fill your destinations by itself.
---

Turn your folders into numbered, colored destinations, then copy or move the photo or video you are looking at into one of them with a single tap or a number key.
