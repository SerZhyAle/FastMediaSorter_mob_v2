---
layout: default
title: "Use the App as Your Home Screen - FastMediaSorter v2"
permalink: /docs/howto/scenario-launcher-mode.html
---
# 🖥️ Use the App as Your Home Screen

> **Level:** Beginner &bull; **Flavor:** Standard / noLegal

[Русский](scenario-launcher-mode-ru.md) | [Українська](scenario-launcher-mode-uk.md)

FastMediaSorter can replace your device's home screen with its own desktop - your folders, a clock, the weather, your apps, and a taskbar along one edge. If you have ever used a Windows desktop it will feel familiar: things stay where you put them, and a Start button opens the menu. This is called **launcher mode**.

**Real example:** an old tablet on the kitchen wall. Instead of a grid of app icons you get the clock, today's weather, the photo folder from your NAS and a button that starts the radio - all on the screen it shows when it wakes up.

---

## What You Will Need

- The **Standard** or **noLegal** build of the app - launcher mode is not in Lite, Photos, Legacy or VR
- A few minutes to arrange the desktop the way you like it
- Nothing else: no root, no extra app, no special permission

---

## Step 1 - Turn Launcher Mode On

1. Open **Settings → General**
2. Turn on **Make this app the home screen**
3. Android asks you to confirm. On Android 10 and newer it is a single question - "Allow FastMediaSorter to be your Home app?" - so allow it. On older versions the classic chooser appears the next time you press Home: pick FastMediaSorter and tap **Always**, or **Just once** to try it out first.
4. Press **Home**

> **Just installed the app?** There is a shortcut: tick **Use as home screen** on the first welcome page. Setup is not interrupted by a system dialog - Android's confirmation comes up the first time you open **Settings → General** afterwards.

---

## Step 2 - Look at What You Already Have

The desktop is not an empty grid on day one. It arrives holding about a dozen useful things: a clock, the weather where you are, your folders, a search box.

| Cell type | What it does |
|-----------|--------------|
| Resource shortcut | Opens a folder you added - and you choose whether it opens in browse, slideshow or play mode |
| Gadget | A clock with seconds (tap it for alarms), the weather, what is playing right now, a translator, and two dozen more |
| App shortcut | Starts any installed app; long-press lists that app's own quick actions |
| Contact cell | Opens a person's card, calls them, sends an SMS, or opens their messenger conversation |
| App widget | The same widgets the app offers for the Android home screen, placed here instead |

---

## Step 3 - Use the Taskbar and the Start Menu

The taskbar sits along the bottom edge. It holds the Start button, the apps you used recently, the ones you pinned, and a small tray with the clock, battery, network and SIM signal.

- **Prefer it along the top?** **Settings → General → System launcher settings → Taskbar → Taskbar position** switches between **Bottom** and **Top**. The Start menu follows the bar and drops down from above when the bar is up there.
- **The Start button** opens the menu: open FastMediaSorter, your resources, add a resource, Android settings, app settings, launcher settings, edit desktop contents, and at the end restart, power off and **Exit launcher mode**.
- **Your apps** are grouped into sections with small headers. Tap a header to collapse a section you rarely open. A fresh desktop puts your Google apps in a **Google** section and your own installed apps - messengers, games and the rest - in an **Apps** section, so the same icon never shows up twice. Long-press any app for **Put on desktop** and **Pin to taskbar**.

---

## Step 4 - Put Your Own Things on the Desktop

1. Long-press an empty square of the desktop
2. Four choices appear: **Add an item..**, **Edit the desktop**, **Wallpaper**, **Launcher settings**
3. Choose **Add an item..** and pick what goes there: an app, a feature, one of your folders, a radio stream, a person, a system action, a scheduled operation, a gadget, or an action

The new cell lands exactly on the square you pressed.

Among the gadgets are the **now-playing card** - it shows whatever is playing on the device and takes you to that player with one tap - and the **translator** cell.

---

## Step 5 - Rearrange It

**Edit the desktop** turns on edit mode, the same as **Edit desktop contents** in the Start menu. While editing:

- Drag a cell to move it
- Drag a gadget's corner handle to resize it
- Tap **+** to add something
- Choose **Remove from desktop** on a cell to take it off
- Tap **Done** when you are finished

> **Sharing the device with someone?** Turn on **Lock desktop** in launcher settings. The long-press then does nothing, so the layout cannot be nudged by accident.

> **Portrait and landscape are two separate desktops.** What you arrange upright is not what you get when you turn the device sideways - each orientation keeps its own layout and its own collapsed sections. The settings themselves (taskbar position, density, wallpaper) are shared by both.

**Want more, or fewer, cells on screen?** **Settings → General → System launcher settings → Desktop → Grid density** offers **Sparse**, **Standard**, **Dense** and **Very dense**.

**Want the wallpaper to show through?** **Settings → General → System launcher settings → Desktop → Widget backdrop opacity** sets how solid a gadget cell looks at rest - from fully transparent, so the wallpaper shows straight through, to an opaque card. Edit mode always draws the full card, so a cell stays easy to grab while you are arranging.

---

## Step 6 - Go Back to Your Old Home Screen

Any of these three works:

- Open the Start menu and choose **Exit launcher mode**, then confirm
- Turn **Make this app the home screen** off in **Settings → General**
- Go straight to Android's own list of home apps: **Settings → General → System launcher settings → System → Change home screen**

Your desktop layout is kept either way, so switching launcher mode back on brings it back exactly as you left it.

---

## Tips

> **Wall tablet or car head unit?** Pair launcher mode with **Prevent Sleep** in **Settings → General** so the desktop stays visible while the device is on a charger.

> **Left on all day?** **Settings → General → System launcher settings → Desktop → Screen timeout** sets how long the desktop waits while nobody touches it. It does not go dark at once: first comes a slow dimming, then the blackout - an app-private black overlay rather than the device switching itself off, so any touch brings the desktop straight back. Set it to **Off (never)** to keep the desktop lit.

> **Want the full reference?** Every option, table and corner case lives in the [Use the App as Your Home Screen](../HOW_TO.md#how-to-use-the-app-as-your-home-screen) section of the user manual. This guide covers switching it on and the first steps; that section covers the rest.

> **Other apps can put their own shortcuts here**, exactly as they would on any other home screen.

---

## Troubleshooting

| Problem | What to try |
|---------|------------|
| **Make this app the home screen** is missing from Settings → General | Your build does not include launcher mode. It ships in **Standard** and **noLegal** only - check **Settings → About** for the edition you have |
| The device goes back to its own home screen after a restart | Some aftermarket car radios and built-in Android boxes force their factory home screen back on every start. That is the device's firmware overriding you, and no app can work around it - pick FastMediaSorter again after the restart, and if it still will not stick, that device does not allow it |
| Android never asked which home app to use | Press **Home** once - on older Android versions the chooser appears then, not at the moment you flip the switch |
| The desktop looks empty after turning it on | Press **Home** rather than staying inside the app - the pre-filled desktop is what Android shows as the home screen |
| Cells move when you did not mean to move them | Turn on **Lock desktop** in **Settings → General → System launcher settings** |
| Rotating the device shows a different arrangement | Expected - portrait and landscape keep separate layouts. Arrange the one you use, or arrange both |
