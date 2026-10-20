---
layout: doc
title: "Clipboard on the server"
description: "Send what you copied on one of your devices to the exchange server and get it on another: the two buttons, where they are, one object at a time, only the last value, the size limit and what the server can see."
category: "Media exchange"
permalink: /docs/media-exchange-portal/exchange-clipboard.html
---
<sub class="doc-stamp">26.10.15 14:25</sub>

# Clipboard on the server

Copy something on one device, press one button, and paste it on another. The [exchange server](exchange-server.html) holds one value for your account - the last one you sent - so every other device of the account can take it. The clipboard is yours alone: it is not shared with anyone outside the account.

## Two buttons {#two-buttons}

- **Send the clipboard to the server** takes what is in this device's clipboard and puts it on the server.
- **Get the clipboard from the server** takes the value from the server and puts it into this device's clipboard, ready to paste. The message names the device it came from and when it was sent.

## Where they are {#where}

- **Phone:** in the programs menu of the main window, on the quick-access panel, as launcher shortcuts and in the menu of the app icon.
- **Watch:** the watch has no clipboard of its own to read, so it sends through **Send to..** - a file, a picture or a voice recording. Getting the value saves it on the watch and opens it in the watch app's own player: text, PDF, picture, animation, audio or video.
- **Windows:** two buttons in the main window of Share Manager and shortcuts on the desktop. CyrFlip, a small Windows program of the same developer, takes part too.

FastMediaSorter on the phone and the watch, Fast Media Sorter & Sharing for Windows and CyrFlip send and receive. STREAMS Player and StreamsPlayer_mob do not take part.

## The rules {#rules}

- **One object at a time** - a text, a picture or one file. Several files or a folder do not go as they are: the program says so, and you put them into one archive first, copy the archive and send it.
- **Only the last value.** A new value replaces the old one. The server keeps it in its memory only, never on its disk, so a restart of the server loses it. A value replaces the old one only when it has fully arrived: a send that breaks or is cancelled leaves the previous value as it was.
- **Every account of the device.** A send goes to every account the device is signed in to, except a guest account, and the program names the accounts it did not reach. A get from several accounts takes the newest value.
- **Only your account.** Nobody outside the account can read it: the clipboard has no sharing, no link and no guest access.
- **No history, no clear button, no alert.** A value goes only by being replaced or when the account is deleted, and the server pushes nothing when it changes.

## Size {#size}

- **5 GiB by default.** The owner of the server may set another limit for an account, or 0 to switch the clipboard off for it. The server holds the value in its memory, and the owner answers for the memory of the PC.
- **A send above the limit is refused** with a message, and the previous value stays.

## What goes and how {#what-goes}

- **Text** goes as plain text; line breaks are brought to one form and each device puts them back in its own. A text too big for the system clipboard is saved as a `.txt` file and placed as a file.
- **A picture** goes as a PNG when it came from a bitmap, otherwise with its own type.
- **A file** goes with its name and type. A received file or picture is saved in the app's storage and put into the clipboard as a link to it. A received file never overwrites one that exists: a suffix is added to its name.

## Before you press {#before-pressing}

- **There is no preview, no filter and no confirmation.** What is in the clipboard at that moment goes - a password you copied a minute ago goes too, and a value another app marked as secret is not held back. Press **Send the clipboard to the server** only when you mean it.
- **Encrypted on your device.** The value is encrypted before it leaves the device and opened only on other devices of your account. All the server sees of it is its size, the time and which device sent it. A device that joined the account after the value was sent cannot read it; it reads the next one.
- **Android.** From Android 10 an app reads the clipboard only while its window is in front, so the buttons work from the open app or from a shortcut that opens a window of its own. Android may show its own notice that the app pasted from your clipboard; that is expected.

## When it does not work {#troubleshooting}

- **"There is nothing in this phone's clipboard. Copy something first."** - copy a text, a picture or one file, then send.
- **"First sign in to an account on an exchange server in My devices."** - the device has no account on a server yet: [Accounts, invitations and passwords](exchange-accounts.html).
- **"This server does not keep the clipboard."** - the server is older than the feature; ask its owner to update it.
- **"This server does not take a clipboard of this size, or has the clipboard turned off."** - send something smaller, or ask the owner of the server.
- **"The clipboard holds several objects."** - put them into one archive, copy the archive and send again.

## Via Google Drive {#drive}

Via Google Drive, files and the clipboard are kept in your own Google account, like your other files on Google Drive; no exchange server takes part. Those actions are named "via GD" and appear only where you switched the Drive channel on. The same rules hold: one object at a time, no preview, no history. The value stays in the hidden folder of your Drive until a newer one replaces it or you delete it in the queue screen of the app.

Back to [the exchange server](exchange-server.html) or to the [portal start page](index.html).
