---
page_id: wear.wrist-mini-apps-and-tools
title: Wrist Programs, Timers and Tools
nav_title: Wrist programs and tools
description: Everything behind the watch's Programs row - Calculator, Network Monitor, the mini-game, Stopwatch, Water flashlight, the voice recorder, System information, Tourist, Phone camera, Clipboard and on-request screenshots.
category: Wear OS Watch
category_slug: wear
ticket: S2966
flavor: Both watch versions, except the Extended signing fingerprint in System information and the shade lock during Water flashlight - the full watch version (sideload only).
recipe_number: "04"
canonical_url: documentation/wear/wrist-mini-apps-and-tools-ru.html
why: |
  Not every trip to the watch is about a song or a photo. Splitting a bill, timing a lap, checking why the connection is acting up, lighting your way in the dark, jotting a quick voice note - none of that needs the phone out of your pocket.

  The watch's [programs](term:program) turn the watch itself into a small toolbox: a [calculator](term:calculator), a [Network Monitor](term:network-monitor), a mini-game, a stopwatch, a water flashlight, a voice recorder, and a handful of other one-tap helpers, each reachable straight from the wrist, with or without the phone nearby.
ingredients:
  - "The [watch app](term:watch-app) installed - see [installing and pairing the watch](page:wear.installation-and-pairing)."
  - "For the Extended signing fingerprint in System information and the shade lock during Water flashlight: the full version of the watch app. *Sideload version only* - see the [noLegal edition](term:nolegal-edition)."
  - "For Phone camera, sending logs, the clipboard bridge and an on-request screenshot: the phone paired and nearby, awake."
steps:
  - number: 1
    id: programs-row
    title: Open Programs
    text: |
      On the watch, swipe to **Programs** to see every built-in tool as a grid of icons - **Calculator**, **Network Monitor**, **Mini-game**, **Stopwatch**, **Water flashlight**, **Voice recorder**, **Clipboard**, **System information**, **Tourist** and **Phone camera** among them, some only where this build offers them. You can also reach the whole grid from the **Programs** tile - see [tiles and complications](page:wear.tiles-and-complications). Each icon is drawn in the same accent color that program carries on the phone, so you recognize the same program at a glance on both devices; the shortcut tiles for programs and sections use that color too, as a filled circle behind the glyph, with the glyph tinted light or dark for contrast against it.

      The row on the watch home screen that always used to open the live broadcast now opens whichever program you opened most recently instead - the game, the water flashlight, the stopwatch, whatever it was - under that program's own name and icon. Nothing opened yet, or the broadcast itself was the last thing you opened? The row stays the broadcast shortcut it always was. A program this build does not offer never appears there, and the Programs tile itself is unchanged.
    image_bookmark:
      shot_id: wear.programs-grid-home-row
      device_profile: watch
      screen_state: wear-programs-grid-and-home-row
      alt: The watch Programs grid showing Calculator, Network Monitor, Mini-game and other program icons, each in its own accent color, above a home screen row that opens the last program used
      caption: "The Programs grid and the last-used shortcut on the home row."
      title: "Screenshot: Programs grid"
      desc: Round watch, Programs grid open, icons in matching accent colors, home row beneath showing the most recently opened program.
  - number: 2
    id: calculator
    title: Do the math - Calculator
    text: |
      Open **Calculator** for everyday sums without waking the phone. The keypad opens on its first row with every key at a full touch-sized target, the minus sign is drawn full-width so it is never mistaken for a hyphen, and clear and backspace sit on a red plate so neither is pressed by accident. The running value is right-aligned in its own color, like a calculator's own display register, and the operation you picked stays shown beside it - tap it again to repeat that operation on the next number. Its function menu lays its extra operations out in columns instead of one long list.

      A tap on the result copies it to the watch clipboard - handy for pasting into a chat or a note on the wrist without going through the phone. Watches older than Android 13 show their own short confirmation; where the system already shows one, the watch does not repeat it. Tapping the display does nothing while it is showing an error.

      The Google Play watch build draws the whole calculator inside the round glass; the full sideload build keeps its own established layout.
    image_bookmark:
      shot_id: wear.calculator-keypad
      device_profile: watch
      screen_state: wear-calculator-keypad-result-copied
      alt: The watch Calculator with a full-size keypad, a right-aligned running value and a confirmation that the result was copied to the clipboard
      caption: "The watch Calculator, result copied to the clipboard."
      title: "Screenshot: Calculator"
      desc: Round watch, Calculator keypad open first row, running value shown, copy confirmation visible.
  - number: 3
    id: mini-game
    title: Play the Mini-game
    text: |
      Open **Mini-game** from Programs for a quick round of dodging the shadows to the exit. The board is capped to the largest square the round display can draw whole, with room left under the header, so on a round watch every corner of the board and the level-finished button stay inside the glass. Score, level and turn count sit in a band around the board instead of stacking two lines above it.

      Every round deals a fresh random board, so two games in a row are never the same, and restarting a level draws a new layout rather than a second try at the one you just left - reopening a save from mid-game still returns the exact board and position you left it at. Each new board opens with a short arrow pointing from your figure to the nearest exit, then fades on its own. The board also gets harder as you climb: the number of shadows chasing you rises in steps up to a cap, and the level you have reached is shown beside the turn count. Finish a level and the next one starts by itself after a short pause; press and hold anywhere on the board for a menu to skip the turn, restart the level or exit.
    image_bookmark:
      shot_id: wear.mini-game-board
      device_profile: watch
      screen_state: wear-mini-game-board-in-play-menu
      alt: The watch Mini-game board filling the round display with score, level and turn count around it, and the in-play menu offering skip turn, restart level and exit
      caption: "The Mini-game board, sized to the round display, with its in-play menu open."
      title: "Screenshot: Mini-game"
      desc: Round watch, Mini-game board full size, band of counters around it, long-press menu overlay visible.
  - number: 4
    id: stopwatch
    title: Time it with Stopwatch
    text: |
      **Stopwatch** runs one, two or four independent timings on a single screen - handy for timing more than one runner, or more than one pot on the stove. Each participant gets its own **Start**/**Lap** and **Stop**/**Reset** button, and **Start all**, **Stop all** and **Reset all** control every one of them together. A results page lists every lap you took for each participant, and the reading stays correct even if the screen goes dark while it is running.
    image_bookmark:
      shot_id: wear.stopwatch-multi-participant
      device_profile: watch
      screen_state: wear-stopwatch-two-participants-running
      alt: The watch Stopwatch running two independent timings side by side, each with its own Start, Lap and Stop buttons
      caption: "Two timings running side by side in Stopwatch."
      title: "Screenshot: Stopwatch"
      desc: Round watch, Stopwatch screen, two participants running, per-participant Start/Lap/Stop buttons visible.
  - number: 5
    id: water-flashlight
    title: Light the way - Water flashlight
    text: |
      **Water flashlight** turns the screen itself into a light a wet wrist cannot switch off by accident: the display goes white at full brightness with the time and a short reminder over it, and touch and rotation stop doing anything else. Because a splash on the glass can register as a tap, no single input turns the light off - it takes three quick swipe-back gestures within two seconds, or a long press of a watch button on a watch whose button reaches the app. One stray back gesture, exactly what a wet glass produces, leaves the light burning.

      The watch has no flash unit, so the screen is the only light there is. It lives among the other Programs and does not replace the watch's own water-lock mode, which no app can turn on for you.

      *Sideload version only:* the full watch build also holds the system shade closed while the flashlight is open, so a wet swipe down from the top cannot reach quick settings.
    image_bookmark:
      shot_id: wear.water-flashlight-lit
      device_profile: watch
      screen_state: wear-water-flashlight-white-screen-time
      alt: The watch screen lit fully white as a flashlight, with the time and a short reminder shown over it
      caption: "Water flashlight, screen at full brightness."
      title: "Screenshot: Water flashlight"
      desc: Round watch, Water flashlight active, full white screen, time and reminder text overlaid.
  - number: 6
    id: voice-recorder
    title: Record a voice note
    text: |
      **Voice recorder** lets you record, play back and send a note without the phone in hand. Recording keeps running even if the screen goes dark or the watch slips off your wrist, because it runs as a background recording; while it runs, the status dot and the elapsed time carry their own color so you can tell at a glance that it is live. Play a finished note straight from the recorder screen or from any row in **My notes**, in the watch's own player.

      Every note gets a readable title built from when it was recorded, and it appears alongside your other audio. Press and hold a note for the same file menu an ordinary file gets, including rename - which is what tells apart two notes taken a second apart - as well as send and delete. A setting chooses whether a finished note is sent to the phone automatically or held until you send it yourself; a note recorded out of the phone's reach is marked as waiting and leaves on its own once the link is back.

      If the watch's own note store ever fails to open - a corrupt file, or an update it cannot read - the app rebuilds its list from the recordings still saved on the watch instead of losing them. The recovered notes wait for you to send them by hand so nothing goes out twice, and the list explains once that it was rebuilt.
    image_bookmark:
      shot_id: wear.voice-recorder-notes-list
      device_profile: watch
      screen_state: wear-voice-recorder-my-notes-list
      alt: The watch Voice recorder My notes list with recorded notes, their titles built from the recording time and their sending status
      caption: "My notes in Voice recorder, each note showing whether it reached the phone."
      title: "Screenshot: Voice recorder"
      desc: Round watch, Voice recorder, My notes list, several notes with sent/waiting status shown.
  - number: 7
    id: network-monitor
    title: Check the connection - Network Monitor
    text: |
      **Network Monitor** opens on a summary dashboard with your active link, local and external address, and a set of sections that each carry a live reading of their own - the sections wrap into a cloud of pill-shaped panels sized to their own text, so the whole set fits with far less scrolling than one full-width row per section would take. Every page shows its own title, its readings and the permission notice, and you leave a section with the usual edge swipe. The sections are **Wi-Fi** with frequency, standard and a restartable signal trend; **Mobile**, which explains itself on a watch with no modem; **Bluetooth**; **satellites** listed by constellation with coordinates, accuracy and fix time; **traffic** rates with resettable counters; **internet** with an on-demand reachability check; and **History**. Addresses copy to the clipboard with a tap, and each section can open its matching system settings screen.

      **History** records a row only when the active connection actually changes, with the time of that change shown above the transport name, so a session that stays on one network leaves one row instead of twenty copies of the same reading. On a watch running Android 12 or newer, tap the permission notice to grant the access the Bluetooth and visible-Wi-Fi readings need - the fields fill in on the next reading.
    image_bookmark:
      shot_id: wear.network-monitor-dashboard
      device_profile: watch
      screen_state: wear-network-monitor-summary-sections-cloud
      alt: The watch Network Monitor summary dashboard with the active link, addresses and a wrapping cloud of section panels each showing a live reading
      caption: "Network Monitor's summary, with its sections wrapped into a cloud."
      title: "Screenshot: Network Monitor"
      desc: Round watch, Network Monitor summary, active link and addresses at top, wrapped section panels below.
  - number: 8
    id: system-information
    title: Read System information
    text: |
      **System information**, found in **Applications** rather than Settings, lays out its groups two fields to a row - a field too wide for half the screen keeps a row of its own - and answers questions no watch settings screen does: thermal state, battery temperature, voltage and charge left in mAh, uptime, boot count, whether the watch is exempt from battery optimisation, whether the app is background-restricted, and the reason its process last stopped; the full sensor list with each sensor's maker, power draw and resolution; chipset, instruction set, core count and low-memory flag; and the Wi-Fi bands and standards the watch supports. The phone connection line names each linked device, whether it is nearby or reached through the cloud, both node IDs and the pair's shared capabilities. Storage measures the app's own data, cache and cache reserve, not the whole watch. Long lists collapse to a count you tap open, collapsed group headings sit in a wrapping row of their own so the whole report is reached with far less scrolling, and a **Refresh** button re-reads whatever changes while the screen is open. A section this watch cannot answer says why instead of just disappearing.

      Tap any value to copy it to the clipboard with a haptic tick; press and hold the sensor list to copy the whole thing at once. **Send to phone** carries the full report to the paired phone, which saves it as a text file next to its own logs and opens the share sheet from a notification - the watch tells you in words whether it arrived, including when the phone's notifications are off.

      *Sideload version only:* an **Extended** section adds the SHA-256 fingerprint of the certificate the installed app is signed with, for confirming you are running a genuine build.
    image_bookmark:
      shot_id: wear.system-info-report
      device_profile: watch
      screen_state: wear-system-info-two-column-headings-cloud
      alt: The watch System information screen with collapsed group headings in a wrapping row and a two-field-per-row report below
      caption: "System information, headings wrapped, two facts to a row."
      title: "Screenshot: System information"
      desc: Round watch, System information, collapsed headings cloud at top, Device and App sections expanded below.
  - number: 9
    id: tourist
    title: Lock the screen and check Tourist
    text: |
      **Tourist** wraps your speed, altitude, compass, coordinates, satellites, steps, trip distance, sun times and heart rate into a set of content-width tiles you can scroll and promote to the top; the compass draws as a large rotating needle, and a **Grant access** button appears only while the location permission is still missing. Body temperature shows up where the watch itself reports a temperature sensor. Its Steps and Heart Rate tiles mirror what Motion Monitor and Heart Rate measure in detail - see [viewing health and sensor info](page:wear.health-and-activity-metrics).

      Swimming or running with a wet or busy screen? Tap the lock icon on the active panel to lock touch - the readings keep updating, but nothing you brush against the glass can change them. Only a press of a watch button unlocks it again.
    image_bookmark:
      shot_id: wear.tourist-dashboard-lock
      device_profile: watch
      screen_state: wear-tourist-dashboard-touch-locked
      alt: The watch Tourist dashboard with content-width tiles for speed, altitude and compass, and a lock icon showing touch is locked
      caption: "Tourist's dashboard, touch locked for wet or busy hands."
      title: "Screenshot: Tourist"
      desc: Round watch, Tourist dashboard, rotating compass needle tile promoted to top, lock icon active.
  - number: 10
    id: send-logs
    title: Send watch logs to the developer
    text: |
      Something is not behaving and support asked for a log? Open **About** on the watch and tap **Send logs to developer**. The watch hands the log to your paired phone, which files it beside its own logs and offers both together by email - one message instead of hunting through two devices.
    image_bookmark:
      shot_id: wear.about-send-logs
      device_profile: watch
      screen_state: wear-about-send-logs-sent
      alt: The watch About screen with the Send logs to developer action and a confirmation that the log was sent
      caption: "Sending a watch log to the developer from About."
      title: "Screenshot: Send logs"
      desc: Round watch, About screen, Send logs to developer action, sent confirmation shown.
  - number: 11
    id: phone-camera
    title: Watch what your phone's camera sees
    text: |
      **Phone camera** streams a live picture, with sound, from your paired phone's camera to the watch over the shared local Wi-Fi network - every lens the phone offers shows up on the wrist, switching lenses keeps the stream running, and **Stop** on the watch closes the camera on the phone too.

      The full sideload build lets you arm this in advance from the phone's Wear OS settings, so the phone can stay in a pocket when you ask for the stream; the Google Play build confirms each request with one tap on the phone's notification instead. If a sideload phone has not been armed yet, the watch now says to start the camera broadcast on the phone, rather than the wrong advice to check the phone's notifications.
    image_bookmark:
      shot_id: wear.phone-camera-live
      device_profile: watch
      screen_state: wear-phone-camera-live-lens-switch
      alt: The watch showing a live video stream from the paired phone's camera, with a lens-switch control and a Stop button
      caption: "A live stream from the phone's camera, on the watch."
      title: "Screenshot: Phone camera"
      desc: Round watch, Phone camera live view, lens picker and Stop button visible.
  - number: 12
    id: clipboard
    title: Send text between the watch and the phone
    text: |
      **Clipboard** carries text both ways between the watch and the paired phone, one explicit action on each side. On the watch, open **Clipboard** to see what your watch's clipboard holds and send it to the phone, which drops the text straight onto its own clipboard and lets you know it arrived. From the phone, the Wear Companion window carries a matching action to send its own clipboard to the watch. Either side always states the outcome - taken, no device in reach, no answer, or the reason the other side declined - so a send never fails silently. Only text travels this way; files and pictures keep their own transfer - see [browsing files on the watch](page:wear.watch-file-manager).
    image_bookmark:
      shot_id: wear.clipboard-screen
      device_profile: watch
      screen_state: wear-clipboard-text-sent-to-phone
      alt: The watch Clipboard screen showing the held text and a confirmation that the phone took it
      caption: "Sending the watch clipboard to the phone."
      title: "Screenshot: Clipboard"
      desc: Round watch, Clipboard screen, held text visible, sent-to-phone confirmation shown.
  - number: 13
    id: watch-screenshot
    title: Grab a picture of the watch screen
    text: |
      Need to show someone exactly what the watch is displaying? From the phone's Wear Companion window, ask the paired watch for a [screenshot](term:screenshot) of itself. The watch photographs whatever the app is currently showing - never the watch face or another app - and sends the picture back the same way any other file from the watch arrives, announced by the usual notification. Nothing needs to be tapped on the watch itself; the phone states the outcome under the action - sent, no watch in reach, no answer, or the reason the watch declined.
    image_bookmark:
      shot_id: wear.watch-screenshot-request
      device_profile: phone
      screen_state: wear-companion-watch-screenshot-received
      alt: The phone's Wear Companion window after requesting a watch screenshot, with the outcome line stating it was sent
      caption: "Asking the watch for a screenshot of itself, from the phone."
      title: "Screenshot: Watch screenshot request"
      desc: Phone, Wear Companion window, screenshot action, outcome line confirms the watch sent it.
outcome: |
  The watch itself becomes a small toolbox: split a bill, time a run, check a slow connection, light your way, take a voice note, watch the phone's camera or grab a screenshot - all without waking the phone.
tips:
  - "**A program you expect is missing from the grid?** It is not offered by this build; nothing on the watch is broken."
  - "**Stuck in Water flashlight?** A single swipe back does nothing on purpose - it takes three fast ones, or a long press of a watch button."
  - "**Lost mid-game?** Press and hold the board for restart, skip turn or exit, and a fresh random board waits on the other side."
  - "**Programs speak your language.** Calculator, Stopwatch, Network Monitor and the rest follow the language of the watch app."
next_recipes:
  - title: One swipe from the watch face
    url: page:wear.tiles-and-complications
    badge: Watch
    badge_type: docs
    description: Pin the Programs grid one swipe from the watch face.
  - title: Viewing health and sensor info
    url: page:wear.health-and-activity-metrics
    badge: Watch
    badge_type: docs
    description: Heart rate, blood pressure, movement and environment readings.
  - title: Syncing the phone and the watch
    url: page:wear.companion-data-sync
    badge: Watch
    badge_type: docs
    description: The Wear Companion window these Send to phone and Clipboard actions land in.
---

The watch's [programs](term:program) turn the wrist into a small toolbox - a [calculator](term:calculator), a [Network Monitor](term:network-monitor), a mini-game, a stopwatch, a water flashlight, a voice recorder, System information, Tourist, Phone camera, Clipboard and an on-request [screenshot](term:screenshot) - each reachable straight from the watch, with or without the phone nearby.
