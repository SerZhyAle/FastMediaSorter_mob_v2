---
page_id: programs.device-diagnostics-and-logs
title: System Information and the Debug Log - Helping the Author Find a Problem
nav_title: System information and the debug log
description: How to open the System information report about your phone and the app, copy, save or share it, read the exact build from the version number, and save or send the debug log when something goes wrong - with passwords kept out of it.
category: "Programs, Statistics and Diagnostics"
category_slug: programs
ticket: S2961
flavor: All editions; the watch report with FastMedia Wear in the Standard and noLegal editions
recipe_number: "05"
canonical_url: documentation/programs/device-diagnostics-and-logs-ru.html
why: |
  When a video will not play or a network folder will not open, the author needs to know two things: what kind of phone it happened on, and what the app was doing at that moment. **System information** answers the first question, the **debug log** the second.

  Neither is sent anywhere by itself. You create the file, you look at it if you like, and you decide where it goes.
ingredients:
  - "FastMediaSorter in any [edition](term:edition) - see [The seven editions of FastMediaSorter](page:flavors.overview-and-comparison)."
  - "An app that can send a file - email, a messenger or cloud storage - if you want to send a report."
  - "For the watch report: a paired [watch](term:watch) with FastMedia Wear - see [Installing on Wear OS and pairing with the phone](page:wear.installation-and-pairing)."
steps:
  - number: 1
    id: open-report
    title: Open System information
    text: |
      There are two ways:

      - **As a program.** Switch on **System information** in **[Settings](term:settings)**, the **Management** tab, **Additional programs and scenarios**. It then appears in the programs menu, on the [programs panel](term:programs-panel) and on the [launcher](term:launcher) desktop, next to the calculator and [Network Monitor](term:network-monitor) - see [Built-in programs](page:programs.built-in-mini-apps). It opens as a full screen.
      - **From Settings.** **Settings**, the **General** tab, **About system**. This shows the same report in a window, with private values hidden.
    image_bookmark:
      shot_id: programs.system-info-screen
      device_profile: phone
      screen_state: system-info-sections-collapsed
      alt: The System information screen with its folded sections Device, OS, App, Hardware, Cameras, Battery, Memory, Storage, Network and Display, and the Copy, Save and Share buttons
      caption: "System information, section by section."
      title: "Screenshot: System information"
      desc: System information full screen opened from the programs menu, all sections folded, portrait.
  - number: 2
    id: read-report
    title: Read the report
    text: |
      The report is split into sections you fold and unfold with a tap: **Device**, **User**, **OS**, **App**, **Hardware**, **Cameras**, **Battery**, **Memory**, **Storage**, **Network**, **Display**, **Locale**, **Time**, **System** and **Benchmarks**.

      - **Cameras** lists every camera of the phone, including the separate lenses hidden behind one camera, with the direction it faces, the focal length, the sensor size, the zoom range and the **Active sensor area** - the part of the sensor a picture is really taken from. More about lenses: [Lenses, zoom, shooting profiles and camera settings](page:capture.camera-lenses-zoom-and-profiles).
      - **App** shows the app version and the build.

      Touch and hold any line to copy just that value. The whole screen works with a keyboard, a TV remote or a D-pad as well: the line in focus is highlighted - see [Keyboard, D-pad and Android TV control](page:general.keyboard-dpad-tv-navigation).
    image_bookmark:
      shot_id: programs.system-info-cameras
      device_profile: phone
      screen_state: system-info-cameras-expanded
      alt: The Cameras section of System information unfolded, listing a rear and a front camera with focal length, sensor size, zoom range and active sensor area
      caption: "The Cameras section."
      title: "Screenshot: Cameras section"
      desc: System information, Cameras section expanded, rear camera with two physical lenses, portrait.
  - number: 3
    id: copy-save-share
    title: Copy, save or share the whole report
    text: |
      At the bottom of the full screen:

      - **Copy** puts the whole report on the clipboard, ready to paste into a message.
      - **Save** writes it as a text file into your Downloads folder, named with the date and time.
      - **Share** opens the Android share menu with the report.

      In the **About system** window from Settings, private values - the app signature, local network addresses, storage paths - are hidden. **Copy full report** includes them, but first asks: **Copy full report?** **The copy will include sensitive values (signature hash, local addresses, mount paths). Continue?**
  - number: 4
    id: version
    title: Find out exactly which build you have
    text: |
      In **Settings**, the **General** tab, a line shows the version, the build number and the author's email address, for example **2.60.9240.148 | Build 260924014**. The same numbers are in the **App** section of System information.

      The version number is the date and time the app was built: 2.60.9240.148 was built on 24 September 2026 at 01:48. When you write to the author, give this number - it tells exactly which build you are running, whether it came from a store or from an APK file.
  - number: 5
    id: debug-log
    title: Save or send the debug log
    text: |
      The app keeps a debug log - a diary of what it was doing, what went wrong and why. When something fails, do the thing once more so the log catches it, then open **Settings**, the **General** tab, the group **Backups, restore and settings export**:

      - **Share Debug Logs** packs the log into one archive, `fastmediasorter_logs.zip`, and opens the Android share menu - pick your email app or messenger and send it to the author.
      - **Save Debug Logs** saves the same archive to a folder you pick. If your device has no folder picker, the app offers to share the archive instead.

      Touching and holding the version line on the same tab shares the log as well.

      Passwords, keys and tokens are replaced by stars before a line is written, so they never reach the file, even for settings with new names. The log also carries what happened with live [channels](term:channel) and audio - which stream quality was chosen, when it stepped down, which decoder played it - because that is where most playback problems hide. Each error is written once, with a single explanation, so the log stays short enough to read.
    image_bookmark:
      shot_id: programs.debug-log-buttons
      device_profile: phone
      screen_state: settings-general-backups-group-log-buttons
      alt: The General tab of Settings with the Backups, restore and settings export group showing the Share Debug Logs and Save Debug Logs buttons
      caption: "Share or save the debug log."
      title: "Screenshot: Debug log buttons"
      desc: Settings, General tab, scrolled to Backups restore and settings export, both log buttons visible, portrait.
  - number: 6
    id: watch-report
    title: Get a report from your watch
    text: |
      FastMedia Wear has its own **System information** screen in the list of watch programs, next to About. It shows the watch model, the Wear OS version, the app version, free memory and storage, and whether a phone is connected.

      From that screen you can send the report to the phone. The phone then shows a notification, **Watch report received** - **Tap to open or send the report** - and keeps the report next to its own logs, so you can send both together. More about the watch programs: [Wrist programs, timers and tools](page:wear.wrist-mini-apps-and-tools).
outcome: |
  You can tell the author in one message what phone you have, which build you run and what the app was doing when it went wrong - without giving away a single password.
tips:
  - "**Want the details in the error message itself?** Turn on **Show detailed errors** in **Settings**, the **Management** tab: error messages then add the technical code and context under the plain explanation."
  - "**Send the log right after the problem.** The app keeps only its last few log files and starts a new one as each fills up, so a log sent a week later may no longer contain the problem."
  - "**How much you use the app** is a different report - see [Usage statistics](page:programs.usage-statistics)."
  - "**Checking your connection** is the job of [Network Monitor](term:network-monitor), another program you switch on in the same list."
next_recipes:
  - title: Usage statistics
    url: page:programs.usage-statistics
    badge: Programs
    badge_type: docs
    description: How many files you sorted, viewed and freed - and how to send the summary.
  - title: Built-in programs
    url: page:programs.built-in-mini-apps
    badge: Programs
    badge_type: docs
    description: Switch on System information and the other programs.
  - title: Understanding app permissions
    url: page:getting-started.permissions-guide
    badge: Getting started
    badge_type: docs
    description: What each permission is for, when a feature does not work.
---

Two tools help when something in FastMediaSorter does not work as it should: the **System information** report about your phone and the app, and the **debug log** of what the app was doing. This page shows how to open, read, copy, save and send both, and how to read the exact build from the version number.
