---
page_id: launcher.built-in-gadgets
title: Desktop Gadgets - Clocks, Weather, Map, Sensors, Device Status and More
nav_title: Desktop gadgets
description: What each gadget of the launcher desktop shows and how to use it - the large clock, the world clock, weather for several cities, sunrise and dew point, the map, the compass and the speed charts, the battery, network, storage and memory cells, and the translator.
category: "Launcher: Gadgets and Widgets"
category_slug: launcher
ticket: S2959
flavor: Standard and noLegal
recipe_number: "01"
canonical_url: documentation/launcher/built-in-gadgets-ru.html
why: |
  A [gadget](term:gadget) is a live square on the [desktop](term:desktop): it shows something fresh the moment you look at it, without opening an app. A kitchen tablet can show the time in large digits and the weather in two cities; a phone in a car holder can show the speed and the direction; a spare phone on a shelf can watch its own battery and free space.

  Each gadget is added with one long press and knows what to do when you tap it - the clock opens your alarms, the map opens your map app, the weather opens your weather app.
ingredients:
  - "FastMediaSorter in the [Standard edition](term:standard-edition) or the [noLegal edition](term:nolegal-edition), with the [launcher](term:launcher) desktop switched on - see [Your launcher desktop](page:launcher.desktop-grid-and-icons). The Lite, Photos, Legacy, VR and FOSS [editions](term:edition) have no launcher - see [The seven editions of FastMediaSorter](page:flavors.overview-and-comparison)."
  - "An empty square on the desktop, or a moment to make one - see [Placing, resizing and styling gadgets](page:launcher.android-widgets-placement)."
  - "For the map, the compass and the speed gadgets: permission for the app to know the location. For weather, world clock and sunrise: nothing extra, you type the place yourself."
steps:
  - number: 1
    id: open-picker
    title: Open the list of gadgets
    text: |
      Touch and hold an empty square on the desktop, tap **Add an item..** and choose **Gadget**. A list of every gadget your device can show appears.

      A gadget whose sensor your device does not have is not offered at all - a tablet without a compass simply has no **Compass** in the list.
    image_bookmark:
      shot_id: launcher.gadgets-picker-full
      device_profile: phone
      screen_state: launcher-gadget-picker-scrolled
      alt: The gadget list opened from Add an item, scrolled to show Clock, World clock, Weather, Sun and dew point, Map, Compass, Speed, Network, Battery, Storage and Resources
      caption: "The gadget list."
      title: "Screenshot: Gadget list"
      desc: Add-item picker switched to Gadget, scrolled halfway, portrait.
  - number: 2
    id: clocks
    title: Keep the time in view - Clock and World clock
    text: |
      **Clock** shows the local time in large digits with seconds. It starts four squares wide and two high, and you can shrink it down to two squares by one. **Tap** it to open your alarms; **touch and hold** it to open your calendar.

      **World clock** shows the time in another place next to your local clock, not instead of it. When you add it, a searchable list of every time zone your device knows opens - type a city and pick it. The square is captioned with the city and how many hours it is ahead of or behind you. Tap it to open your clock app; touch and hold it to pick a different time zone. Add several to follow several places.
    image_bookmark:
      shot_id: launcher.gadgets-clocks
      device_profile: phone
      screen_state: launcher-clock-and-world-clock
      alt: The desktop with the large Clock gadget showing hours, minutes and seconds, and a World clock gadget captioned Tokyo, 7 hours ahead
      caption: "Clock and World clock side by side."
      title: "Screenshot: Clock gadgets"
      desc: Desktop with a 4x2 Clock and a World clock set to Tokyo, portrait.
  - number: 3
    id: weather
    title: Watch the weather and the sun - Weather and Sun and dew point
    text: |
      **Weather** shows the current weather for a place you choose. A new square says **Long press to choose a place**: touch and hold it and type the city. No location permission is needed, and the data comes from Open-Meteo. Metric or US units follow **Unit system** in **Settings**, **General**.

      Add as many Weather squares as you like - each keeps its own city, even after a restart. Under the reading the square shows when it was last brought up to date (**Updated** and the time). Without a connection it keeps the last reading marked **Last known**, and if nothing arrives it says **Weather is unavailable right now**. Tap it to open your weather app; touch and hold it to change the place.

      **Sun and dew point** shows today's **Sunrise** and **Sunset** and the **Dew point** for a place you pick from the same searchable list. The dew point uses the same temperature unit as Weather. **Tap** it to refresh the reading, and **touch and hold** it to change the place. Several squares can show several places.
    image_bookmark:
      shot_id: launcher.gadgets-weather
      device_profile: phone
      screen_state: launcher-weather-two-cities-and-sun
      alt: Two Weather gadgets for two different cities, each with an Updated time, and a Sun and dew point gadget with sunrise, sunset and dew point
      caption: "Weather for two cities, and the sun."
      title: "Screenshot: Weather gadgets"
      desc: Desktop with two Weather gadgets and one Sun and dew point gadget, portrait.
  - number: 4
    id: map
    title: See where you are - Map
    text: |
      **Map** draws your current position on an OpenStreetMap map and writes the town and country under it, or the coordinates when there is no name. Tap it to open the map app of your device.

      The first time, Android asks whether the app may know the location - allow it. Without the permission the square says **Location permission is off**; without a connection it keeps the last picture it had, marked **Last known**. The map pieces are kept on the device, so a familiar area appears quickly. To find the place name, the app asks only about the centre of the map piece it already shows, never about your exact position.
    image_bookmark:
      shot_id: launcher.gadgets-map
      device_profile: phone
      screen_state: launcher-map-gadget
      alt: The Map gadget showing a street map with a position marker, captioned with the town and country
      caption: "Map with the town name."
      title: "Screenshot: Map gadget"
      desc: Desktop with a Map gadget, location allowed, portrait.
  - number: 5
    id: sensors
    title: Measure the way - Compass, Speed and the charts
    text: |
      These gadgets use the sensors and the location of your device:

      - **Compass** - the heading in degrees with the direction written out (for example **North**), and the height above sea level. If it asks you to **Wave the phone in a figure eight**, do it once to calibrate.
      - **Speed** - how fast you are moving, in km/h.
      - **Speed chart** and **Altitude chart** - how speed and height changed over time. Each chart collects readings since you last tapped its **Reset** button, and keeps them after a restart. The altitude chart also shows the distance travelled.
      - **Altitude** and **Satellites** - the height alone, and how many satellites are used out of how many are visible.
      - **Steps** - the steps counted by your device, from its own step counter. Only in the sideload version (the [noLegal edition](term:nolegal-edition)), because only that edition asks for the physical activity permission this square needs - see [The seven editions of FastMediaSorter](page:flavors.overview-and-comparison).

      While the device looks for a position, the square says **Waiting for a position..**. If you refused the permission, the square stays on the desktop and says **Location is off. Allow it in app permissions.** Location and steps are read only while the square is on screen, never in the background, and nothing is sent anywhere.
    image_bookmark:
      shot_id: launcher.gadgets-sensors
      device_profile: phone
      screen_state: launcher-compass-speed-chart
      alt: The Compass gadget showing heading and altitude, the Speed gadget in km/h and the Altitude chart with a Reset button
      caption: "Compass, speed and the altitude chart."
      title: "Screenshot: Sensor gadgets"
      desc: Desktop with Compass, Speed and Altitude chart gadgets during a walk, portrait.
  - number: 6
    id: device-status
    title: Keep an eye on the device - Network, Battery, Storage and Resources
    text: |
      Four gadgets tell you how the device itself is doing:

      - **Network** - the kind of connection, the Wi-Fi or mobile network name when Android shares it, and whether the internet is reachable: **connected** or **no internet**.
      - **Battery** - the charge in percent and about how long it will last (**About 5 h left**), or **Charging**. The time is an estimate: Android does not report one of its own.
      - **Storage** - free and total space on the device, and on the memory card when one is inserted.
      - **Resources** - free memory and how long the device has been running since it was last switched on.

      All four look the same, ask for no permission, and refresh only while they are on screen. A value the device does not give is shown as **Unknown**, never as zero. With TalkBack on, each square reads out both the number and what it means.
    image_bookmark:
      shot_id: launcher.gadgets-device-status
      device_profile: phone
      screen_state: launcher-technical-status-gadgets
      alt: The Network, Battery, Storage and Resources gadgets in a row, showing the connection, the charge with time left, free space and free memory
      caption: "The four device status gadgets."
      title: "Screenshot: Device status gadgets"
      desc: Desktop with Network, Battery, Storage and Resources gadgets, Wi-Fi connected, portrait.
  - number: 7
    id: translator
    title: Translate a phrase on the spot - Translator
    text: |
      **Translator** is an offline translator right on the desktop. Its caption shows the language pair from the app's translation settings; tap the caption to change it, or swap the two languages for the moment. How languages are chosen and downloaded is in [Translating extracted text](page:tools.inline-translation).

      The square is always at least two rows high, so the text field and the result both fit. The first time a language is used, its language pack is downloaded; when the first translation is ready the caption says **Translated by Google**.
  - number: 8
    id: more
    title: Try the other gadgets
    text: |
      The list holds more squares that bring your own content to the desktop:

      - **Search** - a web search field that opens the results in your browser.
      - **Audio window**, **Video window**, **Document window** and **Image window** - play or show a file from one of your [resources](term:resource) inside the square.
      - **Stream window** - a live [channel](term:channel) playing inside the square.
      - **Playlist**, **Streams**, **Folder preview** and **Favorites** - your playlists, channels, a folder as a small show, and your [favorites](term:favorites).
      - **YouTube**, **YouTube Music**, **YouTube channel window**, **Google Maps Live Frame**, **Google Keep Live Frame** and **Google Calendar Live Frame** - the web pages of these services inside a square.
outcome: |
  Your desktop shows the clock, the weather, the way ahead and the state of the device at a glance, and each square opens the right app with one tap.
tips:
  - "**Numbers do not jump.** Changing values are drawn in digits of equal width and kept to the lower right corner, so a clock or a speed that changes every second stays still to the eye."
  - "**One gadget failed?** The rest of the desktop keeps working, and a short message names the square that could not start - see [Placing, resizing and styling gadgets](page:launcher.android-widgets-placement)."
  - "**Want the card behind the gadgets lighter or heavier?** **Widget backdrop opacity** in the launcher settings changes it for every gadget at once."
  - "**Want the same kind of information on another home screen?** FastMediaSorter also offers widgets for the Android home screen - see [FastMediaSorter widgets for your home screen](page:launcher.home-screen-widgets)."
next_recipes:
  - title: Placing, resizing and styling gadgets and widgets
    url: page:launcher.android-widgets-placement
    badge: Launcher
    badge_type: docs
    description: Put a gadget where you want it, make it bigger or smaller, and choose how solid its card is.
  - title: FastMediaSorter widgets for your home screen
    url: page:launcher.home-screen-widgets
    badge: Launcher
    badge_type: docs
    description: Calculator, camera, photo frame, music, recorder and more on any home screen.
  - title: Your launcher desktop
    url: page:launcher.desktop-grid-and-icons
    badge: Launcher
    badge_type: docs
    description: Turn the desktop on and put apps, folders, channels and gadgets on it.
---

The launcher [desktop](term:desktop) can hold live gadgets: clocks for here and elsewhere, weather for several cities, sunrise and dew point, a map of where you are, a compass and speed charts, the battery, network, storage and memory of the device, and an offline translator. This page shows what each one does and how to set it up.
