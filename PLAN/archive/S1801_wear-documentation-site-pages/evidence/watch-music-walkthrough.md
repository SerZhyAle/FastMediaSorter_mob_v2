# S1801 Wear OS Music Playback Walkthrough

Recorded from Wear OS implementation and emulator test run (`emulator-5554`, `FastMedia Wear`).

---

## 1. Home Screen (`HomeScreen`)

- **Title**: `FastMedia Wear`
- **Sections visible**:
  - `Last used` (appears when a resource or source was recently opened, with icon `ic_history`)
  - `Favourites` (icon `ic_resource_favorites`)
  - `Resources` (icon `ic_wifi`, opens saved network sources and sync from phone)
  - `Phone` (icon `ic_profile_personal_smartphone`, opens paired-phone virtual resources and phone folder browser)
  - `Local` (icon `ic_watch`, opens local watch storage)
  - `Streams` (icon `ic_cast`, internet radio and streams)
  - `Apps` (icon `ic_apps`, built-in watch mini-apps: Calculator, etc.)
- **Bottom Command Bar**:
  - `Settings` button (gear icon)

---

## 2. Phone Home Screen (`PhoneHomeScreen`)

- **Screen Title**: `Phone`
- **Options / Chips**:
  - `Video` (icon `VideoLibrary`, route `browsePhone("videos")`)
  - `Audio` (icon `MusicNote`, route `browsePhone("music")` - opens audio tracks shared from paired phone)
  - `Images` (icon `Image`, route `browsePhone("photos")`)
  - `Documents` (icon `Description`, route `browsePhone("documents")`)
  - `All files` (icon `SelectAll`, route `browsePhone("all")`)
  - `Phone` folder browser (icon `Folder`, opens `PhoneResourceScreen` to navigate phone folders directly)

---

## 3. Local Home Screen (`LocalHomeScreen`)

- **Screen Title**: `Local`
- **Options / Chips**:
  - `Music` (icon `MusicNote`, route `browse("music")`)
  - `Videos` (icon `VideoLibrary`, route `browse("videos")`)
  - `Photos` (icon `Image`, route `browse("photos")`)

---

## 4. Browse Screen (`BrowseScreen`)

- **Screen Title**: Name of category or source (e.g. `Music`, `Audio`, or source name)
- **Content**: Scrollable list or grid of audio files (`ScalingLazyColumn` with `WearViewMode.LIST` or grid)
- **Each item displays**:
  - Track title
  - Duration and artist / file details
  - Album art thumbnail or music note icon
- **Tap action**: Tapping an audio track launches `AudioPlayerScreen` for that file.

---

## 5. Audio Player Screen (`AudioPlayerScreen`)

- **Background**:
  - Album art image if available (with dimming scrim `COVER_SCRIM_ALPHA = 0.7f`)
  - Decorative animated wave particle background (`WaveParticleBackground`) if no album art
- **Elements from top to bottom**:
  1. **Track Title**: Text with marquee/ellipsis
  2. **Volume Readout**: Appears dynamically when turning rotary bezel (`Volume X of Y`)
  3. **Playback Time Row**:
     - `currentPositionFormatted` (e.g. `0:42`)
     - `SeekBar`: Draggable touch bar (touch height 24dp, bar height 4dp, rounded cap), dragging seeks to percentage of track duration
     - `durationFormatted` (e.g. `3:45`)
  4. **Primary Controls Row**:
     - **Previous**: Button with `SkipPrevious` icon (action: `wear_previous_file`)
     - **Play/Pause**: Highlighted button with `PlayArrow` / `Pause` icon (action: `play` / `pause`)
     - **Next**: Button with `SkipNext` icon (action: `wear_next_file`)
     - **Shuffle**: Button with `Shuffle` icon (action: `wear_shuffle_on` / `wear_shuffle_off`)
  5. **Secondary Controls Row**:
     - **Favorite**: Button with `Favorite` / `FavoriteBorder` icon (action: `wear_toggle_favorite`)
     - **Track Position**: Counter text (e.g. `3/12`)
  6. **Screen Off Control Row**:
     - Button with moon icon `🌙` (action: `wear_screen_off`)

---

## 6. Screen Off Mode (`DimOverlay`)

- Tapping `🌙` displays an opaque black overlay (`Color.Black`).
- OLED screen pixels are powered off, saving watch battery while audio playback continues uninterrupted in background.
- Semantic description: `wear_screen_off_exit` ("Tap to wake the screen" / "Коснитесь, чтобы вернуть экран" / "Торкніться, щоб повернути екран").
- Single touch anywhere on the screen wakes the display and returns to the full audio player controls.
