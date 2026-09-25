---
page_id: documents.text-code-editor
title: Built-in Text and Markdown Editor
nav_title: Text and Markdown editor
description: How to read text, Markdown, log and settings files, change the encoding, font size and colors, edit and save a file, find and replace words, undo mistakes, create a new text note, and send the text to Google Keep, the calculator, a speech voice or a translator.
category: Документы и текстовый редактор
category_slug: documents
ticket: S2953
flavor: Standard, noLegal, Legacy, VR and FOSS
recipe_number: "03"
canonical_url: documentation/documents/text-code-editor-ru.html
why: |
  A shopping list, a note with a Wi-Fi password, a README from a download, a log file from a router - plain text files are everywhere. In FastMediaSorter you can open them with one tap, read them comfortably and fix a line or two without looking for another app.

  The same place is a small notebook: create a [text note](term:text-note) right in a folder, write it, save it - on the phone, on your home computer or in the cloud.
ingredients:
  - "FastMediaSorter in an [edition](term:edition) with the [reader](term:reader): Standard, noLegal, Legacy, VR or FOSS. The Lite and Photos editions do not open text files."
  - "**Support text files (.txt, .md, .log, .json, .xml)** switched on in **Settings**, the **Media** tab, **Documents**."
  - "A text file up to 100 MB: `.txt`, `.md`, `.log`, `.json`, `.xml`, `.csv`, `.conf`, `.ini`, `.properties`, `.yml` or `.yaml` - on this device, in a [network folder](term:network-folder) or in [cloud storage](term:cloud-storage)."
  - "To change a file: a folder you are allowed to write to."
  - "For translation: an edition with translation (all of the above except FOSS) and **Enable Translation** switched on in **Settings**. For **Send to Keep**: the Google Keep app."
steps:
  - number: 1
    id: open
    title: Open a text file
    text: |
      In the [file browser](term:file-browser) tap the text file. It opens in the reader as plain, easy-to-read text. A very long file is shown in parts: swipe up and down on the text to go to the next or the previous part.

      Text files from other apps open too: choose FastMediaSorter in the **Open with** list, and the file opens in a separate window with the same tools. A file handed over by another app can be read there, but only a file stored in an ordinary folder you can write to can also be changed.
    image_bookmark:
      shot_id: documents.text-viewer
      device_profile: phone
      screen_state: text-viewer-read-mode
      alt: A text file open in the reader with the Copy, Search and Edit buttons in the bottom bar
      caption: "A text file in the reader."
      title: "Screenshot: text viewer"
      desc: A short local .txt note open in read mode, light theme, bottom command bar visible.
  - number: 2
    id: make-readable
    title: Make it easy to read
    text: |
      - **Letter size** - swipe left or right across the text to make the letters smaller or larger.
      - **Colors** - open the [three-dots menu](term:three-dots-menu), tap **Reader Settings** and pick **Light**, **Dark** or **Sepia**. Until you pick one, the text follows the app's own theme.
      - **Markdown** - a `.md` file is shown nicely formatted: headings, bold text, lists and links. Tap **Toggle Markdown** in the three-dots menu to see the plain source and tap it again to go back. JSON and XML files get colored keys and values so they are easier to follow.
      - **Line numbers** - switch on **Number lines** in **Settings**, the **Media** tab, **Documents** to see a number in front of every line - handy when someone says "look at line 42".
      - **Strange letters?** If the text shows question marks or odd symbols instead of letters, the file was saved in a different encoding. Open the three-dots menu, tap **Re-open with Encoding..** and choose another one in the **Select Encoding** window - for old Russian or Ukrainian text, for example, try **Windows-1251 (Cyrillic)** or **KOI8-R (Russian)**; there are also choices for Western, Japanese, Chinese and Korean text. The app guesses the right encoding by itself in most cases.
    image_bookmark:
      shot_id: documents.markdown-rendered
      device_profile: phone
      screen_state: text-viewer-markdown
      alt: A Markdown file shown formatted, with a large heading, bold words and a bulleted list
      caption: "A Markdown file shown formatted."
      title: "Screenshot: Markdown in the reader"
      desc: README.md open in read mode with Markdown rendering on, heading and bullet list visible.
  - number: 3
    id: edit-save
    title: Edit a file and save it
    text: |
      Tap **Edit** (the pencil) in the bottom bar. The text becomes editable and the keyboard opens. While you type, the line and column of the cursor are shown, for example **Ln 12, Col 5**.

      When you are done, tap one of the buttons of the editor:

      - **Save** - saves the changes and keeps the editor open.
      - **Save & close** - saves and goes back to reading.
      - **Save & send** - saves and opens the send menu, so you can pass the file to another app or person.
      - **Cancel** - leaves the editor without saving.

      Before saving, the **Save note** window shows the **File name**, so you can keep it or give the file a new name. A file in a network folder or cloud storage is saved on the phone first and then uploaded back; if the upload fails, you see "Saved on this device, but couldn't upload the file back to the server." and your text is not lost.
    image_bookmark:
      shot_id: documents.text-editor-edit-mode
      device_profile: phone
      screen_state: text-editor-edit-mode
      alt: A text file in edit mode with the keyboard open and the Save, Save and close, Save and send and Cancel buttons
      caption: "Editing a text file."
      title: "Screenshot: text editor"
      desc: Text editor in edit mode on a local .txt file, cursor in line 3, action panel visible above the keyboard.
    callout:
      type: warning
      title: Very long files are read-only
      text: "A file that the reader shows in several parts cannot be edited - you will see \"Editing is not available for files with multiple pages\". Short and medium files, which is almost every note, can be edited."
  - number: 4
    id: undo-find
    title: Undo mistakes, find and replace words
    text: |
      - **Undo** and **Redo** in the editor bar take your last changes back or bring them again - up to 50 steps.
      - **Find** looks for a word in the text: type it in the **Find..** field, and the counter shows, for example, **2 / 7**; use the arrows to go from one match to the next. Capital letters do not matter.
      - **Find and Replace** adds a **Replace with..** field: tap **Replace** to change the current match or **Replace All** to change every one at once. You then see how many places were replaced.

      If a word is not in the text, you see "No results. The search came back empty-handed."
    image_bookmark:
      shot_id: documents.text-find-replace
      device_profile: phone
      screen_state: text-editor-find-replace
      alt: The Find and Replace panel over a text file with a found word highlighted and the Replace and Replace All buttons
      caption: "Find and Replace."
      title: "Screenshot: Find and Replace"
      desc: Find and Replace panel open in edit mode, query found 3 times, counter "1 / 3".
  - number: 5
    id: drafts
    title: Do not lose your text
    text: |
      While you edit, the app quietly keeps a draft of your text every 15 seconds when something has changed. If the app is closed before you save - the battery runs out, a call comes in, Android closes the app in the background - open the same file and tap **Edit** again: you see "Draft restored. Tap Save to keep changes." Tap **Save** to keep what you had written.

      The draft is only a safety net. Leaving with **Cancel** or the back button does not ask for confirmation, so save before you leave.
  - number: 6
    id: new-note
    title: Create a new text note
    text: |
      Open the folder where the note should live, open the three-dots menu of the file browser and tap **Create text note**. On a wide screen the button is right on the bar. An empty note opens straight in the editor.

      Write your text and tap **Save**. The suggested file name is the date and time, for example `26-09-24_15-30.txt`; change it if you like. If you leave out the ending, `.txt` is added for you. The file appears in the folder only when you save it for the first time.

      Notes can be created in any folder you may write to - on the phone, in a network folder or in cloud storage. In the [All Documents](term:all-documents) list a new note goes into the phone's **Documents** folder. If the folder cannot take a new file, you see "Couldn't create the note here. Please try a different folder."
  - number: 7
    id: send-calculate-listen-translate
    title: Send the text to Keep, calculate, listen or translate
    text: |
      - **Send to Keep** - in the editor, this button sends the text to Google Keep as a new note, so you have it on all your devices. It is shown only when Google Keep is installed.
      - **Calculator** - in the editor, select a sum written in the text, for example `12*7+5`, and tap **Calculator** in the selection menu. The [calculator](term:calculator) opens with it; the result is put back into the text where the cursor was.
      - **Read Aloud** - in the reader, tap **Read Aloud** in the three-dots menu, and your phone's speech voice reads the part of the text on the screen. Tap it again to stop. You can also select a piece of text and choose **Read Aloud** from the selection menu.
      - **Translate** - in the reader, tap the **Translate** button to see the text in your language in a side panel, made on the phone without the internet. Swipe left or right on the panel to change its letter size. Selected text can be translated from the selection menu too. More in [translating text on screen](page:tools.inline-translation).
outcome: |
  Any text file opens with one tap and reads comfortably - in large letters, dark colors, with formatting or with line numbers. You can fix it on the spot, find and replace words, start new notes in any folder, and send the text on to Keep, the calculator, a voice or a translator.
tips:
  - "**Keep a copy of important files.** Saving replaces the old text. To keep the original, copy the file first - see [copying, moving and deleting files](page:storage.file-copy-move-delete)."
  - "**Working with notes on your computer?** Create and edit them in a [network folder](term:network-folder) on your home computer - see [Windows network shares](page:network.smb-samba-shares)."
  - "**Need a PDF instead?** See [reading PDF documents](page:documents.pdf-epub-viewing)."
  - "**Only want to read, never edit?** Just do not tap **Edit** - nothing in the file changes while you read, swipe or search."
next_recipes:
  - title: Reading PDF documents
    url: page:documents.pdf-epub-viewing
    badge: Documents
    badge_type: docs
    description: Turn pages, read at night, search, copy, translate and export PDF pages.
  - title: Reading EPUB books and Office documents
    url: page:documents.office-docs-support
    badge: Documents
    badge_type: docs
    description: Read books in your favorite font and colors, and open Office files.
  - title: Built-in programs
    url: page:programs.built-in-mini-apps
    badge: Programs
    badge_type: docs
    description: The calculator and the other small tools that come with the app.
---

Read text, Markdown and log files comfortably, fix them on the spot, find and replace words, create new text notes in any folder, and pass the text on to Google Keep, the calculator, a speech voice or a translator - without a separate editor app.
