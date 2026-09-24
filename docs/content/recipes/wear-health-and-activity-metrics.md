---
page_id: wear.health-and-activity-metrics
title: Viewing Health and Sensor Info
nav_title: Health and sensor info
description: Check your heart rate, get a blood-pressure estimate, read the Motion Monitor's movement sensors and steps, and see light, pressure and magnetic-field readings on the watch.
category: Wear OS Watch
category_slug: wear
ticket: S2966
flavor: Motion Monitor's movement sensors and System information's Environment readings - both watch versions; Heart Rate, Blood Pressure and Motion Monitor's step readings - the full watch version (sideload only).
recipe_number: "05"
canonical_url: documentation/wear/health-and-activity-metrics.html
why: |
  Your watch already sits against your skin all day with a heart-rate sensor and an accelerometer built in. FastMediaSorter reads what they are seeing and shows it to you in plain numbers - a quick check, not a full fitness suite - and says so honestly whenever a sensor cannot answer.

  These readings are for general awareness only. They are not a medical device, they do not diagnose or treat anything, and they are no substitute for a doctor or a purpose-built health device.
ingredients:
  - "The [watch app](term:watch-app) installed - see [installing and pairing the watch](page:wear.installation-and-pairing)."
  - "For Heart Rate, Blood Pressure and the step readings in Motion Monitor: the full version of the watch app. *Sideload version only* - see the [noLegal edition](term:nolegal-edition). The Google Play version does not carry the health permissions they need."
  - "The watch worn snugly against the wrist, for a reading the sensor can trust."
steps:
  - number: 1
    id: heart-rate
    title: Check your heart rate
    text: |
      *Sideload version only.* Open **Heart Rate** from [Programs](page:wear.wrist-mini-apps-and-tools) - its screen is titled **Heart rate check**. The reading starts on its own the moment you open the screen, which is also when the watch asks for heart-rate access, and keeps updating until you leave the screen or it goes dark; a **Measure** button is there too, in case you want to nudge a fresh reading without leaving and reopening the screen. Readings save once a minute as they come in, so a few minutes of wear leaves a row of values instead of a single number, and a trend chart of the recent readings appears directly under the current figure once there are two or more.

      Cannot get a reading? The screen says exactly why instead of leaving you looking at a blank one: no heart-rate sensor on this watch, access not granted, the watch is not being worn, the Wear OS version is too old for the health service, or the measurement timed out or failed.

      **History** keeps every past measurement with its time, up to the newest five hundred. From there, **History & Analytics** adds the average, lowest and highest reading and which zone your latest one falls in - Low, Normal, Elevated, Cardio, Peak or Max, with 60-100 bpm named as the normal resting range.
    image_bookmark:
      shot_id: wear.heart-rate-trend
      device_profile: watch
      screen_state: wear-heart-rate-current-reading-trend-chart
      alt: The watch Heart rate check screen showing the current bpm reading with a trend chart of recent readings underneath
      caption: "The current heart rate, with the recent trend drawn underneath."
      title: "Screenshot: Heart rate check"
      desc: Round watch, Heart rate check screen, large bpm number, trend chart of recent readings below it.
  - number: 2
    id: blood-pressure
    title: Estimate your blood pressure
    text: |
      *Sideload version only.* Open **Blood Pressure** from Programs. Keep the arm still at heart level and tap **Record pulse wave** - the watch records a 30-second pulse wave from its optical sensor and turns it into a systolic and diastolic estimate, always labelled **Estimate** together with how many calibrations it rests on, how old the newest one is, and the model's margin of error.

      The estimate needs calibrating first: open **Calibration**, start a real blood-pressure cuff on the other arm, and type in what the cuff reads while the watch records alongside it - each pair of cuff numbers and watch pulse is saved as one calibration, and the more you add, the tighter the estimate gets. A cuff reading typed in earlier versions of this screen now feeds a calibration rather than being kept as its own tracked value.

      A reading can also be refused, always with its own reason: no permission granted, the watch off the wrist, the wrist moved during the 30 seconds, too weak a pulse signal, or too few calibrations recorded yet. **History & Analytics** adds the average, lowest and highest estimate and which category the latest one falls in - Low, Normal, Elevated, Stage 1, Stage 2 or Crisis, with 120/80 mmHg named as normal.
    image_bookmark:
      shot_id: wear.blood-pressure-estimate
      device_profile: watch
      screen_state: wear-blood-pressure-estimate-with-calibration-count
      alt: The watch Blood pressure screen showing an estimated systolic and diastolic reading labelled Estimate, with the calibration count and model error underneath
      caption: "A blood-pressure estimate, with its calibration count and margin of error."
      title: "Screenshot: Blood pressure estimate"
      desc: Round watch, Blood pressure screen, Estimate label, systolic/diastolic reading, calibration count and error range shown.
  - number: 3
    id: motion-monitor
    title: Watch your movement sensors - Motion Monitor
    text: |
      Open **Activity & Motion** from Programs to see what the watch's movement sensors are reporting right now. The **Motion** group covers the accelerometer, gyroscope and rotation vector, each with its live reading, how many events have arrived, the delivery rate, and how long ago the last one came in - a sensor that stopped answering shows a growing age instead of looking exactly like a healthy one. A sensor this watch does not have says so, rather than sitting at zero, which is itself a real measurement. Every subscription stops the moment you leave the screen.

      *Sideload version only:* the **Activity** group adds the step counter and step detector, asking for activity access only when you open that group rather than at startup. On the Google Play build, or if access is declined, the group names which of three reasons applies instead of showing a step count of zero: no such sensor, access declined, or not carried by this edition.
    image_bookmark:
      shot_id: wear.motion-monitor-activity
      device_profile: watch
      screen_state: wear-motion-monitor-motion-and-activity-groups
      alt: The watch Activity and Motion screen with the Motion group showing live accelerometer, gyroscope and rotation vector readings and the Activity group showing step counts
      caption: "Motion Monitor's live sensor readings, with the Activity group's step counts."
      title: "Screenshot: Activity & Motion"
      desc: Round watch, Activity & Motion screen, Motion group with per-sensor delivery stats, Activity group with step counter and detector.
  - number: 4
    id: environmental-sensors
    title: Check light, pressure and the magnetic field
    text: |
      **System information**'s **Environment** section - see [wrist programs and tools](page:wear.wrist-mini-apps-and-tools) for the rest of that screen - shows live readings from the light, pressure and magnetic-field sensors, and states plainly for each one whether it answered, answered but is not to be trusted, stayed silent, or simply is not fitted to this watch.
    image_bookmark:
      shot_id: wear.system-info-environment
      device_profile: watch
      screen_state: wear-system-info-environment-section
      alt: The watch System information Environment section listing live light, pressure and magnetic-field readings with a status word for each sensor
      caption: "System information's Environment section, one reading and one status per sensor."
      title: "Screenshot: Environment readings"
      desc: Round watch, System information, Environment section expanded, light/pressure/magnetic-field rows each with a status word.
outcome: |
  A quick, honest read of what your watch's sensors are seeing - your pulse, an estimated blood pressure, how you are moving and a few readings about the room around you - with nothing hidden from you when a sensor cannot answer.
tips:
  - "**These are general-wellbeing readings, not a medical device.** They do not diagnose or treat anything and are no substitute for a blood-pressure cuff or a doctor."
  - "**Blood-pressure estimate not close enough?** Add another calibration pair - the more cuff readings behind it, the smaller its margin of error."
  - "**No Heart Rate or Blood Pressure program in your Programs grid?** You are on the Google Play watch build - these need the full sideload edition's health permissions."
  - "**A movement sensor's age keeps growing instead of updating?** That sensor stopped answering; leaving and reopening the screen restarts the subscription."
next_recipes:
  - title: Wrist programs and tools
    url: page:wear.wrist-mini-apps-and-tools
    badge: Watch
    badge_type: docs
    description: The rest of the Programs grid, including System information's other sections.
  - title: One swipe from the watch face
    url: page:wear.tiles-and-complications
    badge: Watch
    badge_type: docs
    description: Pin the Programs grid one swipe from the watch face.
  - title: Syncing the phone and the watch
    url: page:wear.companion-data-sync
    badge: Watch
    badge_type: docs
    description: Set up the watch, and the full sideload edition these readings need.
---

Your watch already carries a heart-rate sensor and an accelerometer against your skin. This page checks your pulse, estimates blood pressure, reads the Motion Monitor [program](term:program)'s movement sensors and steps, and shows light, pressure and magnetic-field readings - general-wellbeing numbers, not a medical device.
