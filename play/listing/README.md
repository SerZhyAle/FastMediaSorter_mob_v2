# Google Play listing source

This tree is the **Google Play** store-listing source. It is consumed by
`scripts/release/publish-play-listing.py` (via `publish-play-listing.ps1`) and is intentionally
**separate** from `fastlane/metadata/android/`.

The store description is also half of the All files access argument. Google reviews the Permissions
Declaration Form against the description, so a full description that stops leading with file
organizing invalidates the declaration - that pair is what an update was rejected on, 2026-08-24
(S1989). The declaration's own text lives in `store_assets/PLAY_PERMISSIONS_DECLARATION.md`; edit the
two together.

The full description carries a second permission paragraph, for location (S2083). Google's location
policy judges a declared permission against what the app tells the user, so that paragraph, the
in-app rationale string, the privacy policy and the Data safety form
(`store_assets/PLAY_DATA_SAFETY_LOCATION.md`) are read as one set. It deliberately does not claim
that no coordinate leaves the device - the map gadgets fetch map imagery over the network.

## Why separate from fastlane

`fastlane/metadata/android/` is the publication source for **IzzyOnDroid / GitHub-store**
(`scripts/release/publish-github-release.ps1`, S0214 / S0394). IzzyOnDroid requires the
"Anti-Features / non-free dependencies" block in the full description. That block is dead weight on
Google Play, so the Play texts here drop it and spend the budget on keywords and benefits.

Changelogs (release notes) stay shared: the AAB uploader (`publish-play-release.py`) keeps reading
them from `fastlane/metadata/android/<locale>/changelogs/`.

**The single images are shared too, and the split above does not argue otherwise (S2597).** The
reason the two trees diverge is entirely about text - IzzyOnDroid needs the Anti-Features block, Play
does not - and nothing in it applies to a picture, so both storefronts show one feature graphic and
one icon. They are kept identical by construction rather than by discipline:
`scripts/release/compose-feature-graphic.py` writes the Play copy and the fastlane copy in the same
run, and `scripts/quality/assert-play-listing-graphics.ps1` fails the release when a declared single
image has no source or when two copies of one artwork stop hashing alike. Until that ticket neither
file existed here at all, and the publisher's own silence hid it: an image whose file is absent is
skipped and the run still exits 0, so the live feature graphic was a hand upload that no run
reproduced and no check compared against anything.

## Layout

```
play/listing/
  <locale>/                       # one per language the app offers - see below
    title.txt                     # <= 30 chars
    short_description.txt          # <= 80 chars
    full_description.txt           # <= 4000 chars
    images/
      phoneScreenshots/<NN>.png        # composed by scripts/release/compose-play-screenshots.py
      sevenInchScreenshots/<NN>.png    # optional, own Play slot
      tenInchScreenshots/<NN>.png      # optional, own Play slot
      wearScreenshots/<NN>.png         # Wear OS listing; square, 384-3840 px, no device frame (S1707)
      featureGraphic.png               # 1024x500, composed by scripts/release/compose-feature-graphic.py
      icon.png                         # 512x512 store icon, the same artwork as store_assets/icon_512.png
  captions.json                    # localized screenshot caption strings
  README.md
```

## Which locales exist here

**The authority is `app_v2/src/main/res/xml/locales_config.xml`, never this file.** That is the single declaration of the languages the app offers (S1190: a language is added there and nowhere else), and Wear App Quality Guidelines WO-G2 requires the listing to be localized in exactly those languages. This README deliberately names no locale set of its own - a second list is what let the listing sit at three languages while the app grew to thirteen (S2340).

Three things that follow, and none of them is visible from the directory tree:

- **A locale folder needs a row in `LOCALES` in `scripts/release/publish-play-listing.py`, or it is skipped in silence.** The publisher iterates that dict, not the directory listing, so a folder created without its row is never published and nothing says so. The folder name equals the Play code for every locale except `uk-UA`, which Play calls `uk`.
- **A locale carries text only.** `title.txt`, `short_description.txt` and `full_description.txt` are all that is required. A locale with no `images/` directory inherits the graphics of the default language, which is how ten of the thirteen ship - screenshots and the feature graphic come from `en-US`.
- **Parity is gated.** `scripts/quality/assert-play-listing-locales.ps1` fails when a declared language has no listing, when a folder in the dict is missing one of the three files, or when a text is over its Play limit. It runs in release scope, from `assert-release-scope-gates.ps1`, which `/spec-prerelease` step 0.4 reaches - not on every ticket close.

## Capturing screenshots

```
pwsh -NoProfile -File scripts/release/capture-play-screenshots.ps1 -List
pwsh -NoProfile -File scripts/release/capture-play-screenshots.ps1 -Locale ru-RU -SetAppLocale -Launch -Slot browse
pwsh -NoProfile -File scripts/release/capture-play-screenshots.ps1 -Tablet -Locale ru-RU -Slot launcher
python scripts/release/compose-play-screenshots.py
python scripts/release/compose-play-screenshots.py --only launcher            # one slot, others untouched
python scripts/release/compose-play-screenshots.py --tablet --only launcher
```

Navigate the app to the target screen yourself, then snap it into a slot. Raw shots land in
`temp/play-shots/<locale>/<slot>.png`; compose falls back to `temp/play-shots/<slot>.png` when a
locale has no own capture, so a shared set and per-locale sets can coexist. `-Tablet` files the raw
shot under `temp/play-shots-tablet/` instead, which is the tree `compose --tablet` reads.

**A full compose run needs every slot's raw shot, and `temp/` does not keep them (S2398).** Compose
numbers its output over the shots actually present, so a run made after capturing one fresh screen
writes `01.png` and leaves a set of one - and publishing deletes all images of a type before
uploading, so that one file replaces the whole live set. Refreshing a single screenshot is
`--only <slot-id>`, which writes that slot at its position in `captions.json` and touches nothing
else.

**The caption sits above the frame, so a composed file is taller than its raw shot (S2573).** Google
allows a tagline at most 20% of the image, and the band used to be painted onto the screenshot
itself - 23% of a landscape tablet frame, burying the app bar of every `tenInchScreenshots` slot.
The band now occupies canvas the composer adds, and its height follows the canvas height, so it
costs about 10% on both form factors. The consequence is that a slot composed today does not match
a slot composed before that change: **refresh the whole set rather than one slot.** When a
single-slot refresh is unavoidable, read the `WARNING:` block an `--only` run prints - it names
every sibling whose shape disagrees with what was just written, and the carousel shows all of them
together. The set on disk still carries the old shape; recomposing it is S2602.

`-SetAppLocale` applies the Android 13+ per-app locale override, which needs no reboot but does
recreate the running activity - run it once before a locale batch, never between navigating and
capturing.

### Which AVD, and why it is not free choice

- **Phone: `Pixel_6`, 1080x2400.** The phone raw is taller than 2:1, so `fit_to_aspect` widens the
  canvas and `out_w = max(w, ceil(out_h / 2))` pins the composed result at exactly 2:1 whatever the
  capture resolution was. The phone set therefore survives a change of AVD; `Pixel_6` is named
  because it reproduces the resolution the shipped set was shot at.
- **Tablet: `Pixel_Tablet`, 2560x1600.** The tablet raw is 1.6:1, under the composer's 2:1 ceiling,
  so it passes through unpadded and **the set's aspect ratio is inherited straight from the AVD
  resolution**. Every tablet slot must come from one profile or the carousel shows two shapes.
- `Pixel_9` is 1080x2424 and `Pixel_10_Pro_Fold` is 2076x2152 - neither is a substitute for the
  tablet, and mixing them into one set is the defect S2602 was opened for.

### Staging the content

Never stage a store capture from `c:\Common\test_media`. That corpus is real personal material -
its PDFs include a signed consent form naming a real person - and a store frame is exactly the
"someone outside will see this" case the ban was written for (S1991). Use the generated one:

```
pwsh -NoProfile -File scripts/release/seed-store-shot-media.ps1 -DeviceId <serial>
```

It writes `temp/store-shot-media/` from arithmetic - wave-and-particle artwork, synthesised audio
with ID3 tags and cover art, an H.264 clip, a hand-written PDF and EPUB - pushes it to
`/sdcard/Download/FastMediaSorter_Store` and hands it to MediaStore. Nothing it produces is anyone's
photograph, recording or text, so any frame of it is publishable. It needs an `ffmpeg`; there is no
developer one on PATH here, so set `FMS_FFMPEG` if the candidate list misses yours.

### Per-slot runbook

`shared` means one raw frame serves all three locales and is filed at the FLAT path; `per-locale`
means one frame per locale, filed under `<locale>/`. The composer already implements the fallback -
`resolve_shot()` prefers a locale frame and falls back to the flat one - so a mixed set needs no
extra machinery. The split is S2602 ADR-1: a screen whose visible chrome is icon buttons plus the
user's own media can be shared, a screen whose visible text comes from `strings.xml` cannot.

| Slot | Screen | Staged with | Frame |
|---|---|---|---|
| `browse` | `MainActivity` resource list | at least one resource added, so the empty state is gone | per-locale |
| `image-viewer` | `PlayerActivity`, image | an image from `Photos/` opened | shared |
| `video-player` | `PlayerActivity`, video | the clip from `Videos/` playing | shared |
| `streams` | `StreamsActivity` | a curated channel list; dismiss the catalog-refresh banner first | per-locale |
| `music-player` | `PlayerActivity`, audio | a track from `Music/` playing, so the visualizer and info overlay are populated | shared |
| `cloud-connect` | `AddResourceActivity` type picker | nothing - it is the first screen of Add Resource | per-locale |
| `reader` | `PlayerActivity`, PDF or EPUB | a document from `Books/` opened, control bar surfaced by one tap | shared |
| `launcher` | `LauncherHomeActivity` | launcher mode on in Settings, then `am start` it directly - no HOME role needed | shared |

Two traps worth naming:

- `browse` looks language-neutral and is not. Every tile on it is a virtual resource whose name is a
  translated string (`recent_media`, `virtual_all_music`, `virtual_all_docs`), so a shared frame
  would show English tiles in the Russian and Ukrainian listings.
- Capturing a shared slot still needs the app in a known language. `-SetAppLocale` refuses without
  `-Locale`, so set the locale on its own call and then capture **without** `-Locale`, or the shot
  lands in the locale subdirectory and stops being shared.

### Staging each screen - learned at the screen, 2026-09-06 (S2602)

Every line here cost a wasted frame the first time. They are the knowledge ADR-2 keeps instead of the
raw pixels.

- **A locale change does not rename the resource tiles until several cold starts later.** The tile
  names are stored text, rewritten by a startup pass (`RenameVirtualResourcesUseCase`, S1265), so the
  launch that follows `cmd locale set-app-locales` still shows the previous language: measured twice
  needed for `en-US -> ru-RU` and four for `ru-RU -> uk-UA`. Restart until
  `adb.ps1 uidump | grep tvResourceName` prints the target language, then capture - never on a timer.
- **`secureSensitiveScreens` is ON by default and blackens the `cloud-connect` screen.** Add Resource,
  Edit Resource and Settings carry `FLAG_SECURE` while it is on, so `screencap` returns a black frame
  and `adb.ps1 shot` says so. Turn it off before the capture: Settings -> search "secure sensitive" ->
  tap the result -> tap the row's switch. Confirm with `adb.ps1 prefs` - the key
  `secure_sensitive_screens` must read `08 00`.
- **Documents need a SAF grant, and the action that gives one is unreachable on a tablet.** With only
  `READ_MEDIA_*` the app sees photos, video and audio but no PDF or EPUB, so `All Documents` is empty
  and the `reader` slot has nothing to open. The cure is the resource menu's "Reconnect resource",
  which lives in the overflow menu - and a tablet in landscape draws inline action buttons instead
  (`values-land` and `values-sw600dp` both set `is_resource_actions_inline`), where that entry does
  not exist. Rotate to portrait first (`settings put system user_rotation 1`), reconnect the folder,
  then rotate back: the grant survives the rotation. Reconnecting also leaves a second resource
  behind, because the built-in Downloads is re-provisioned when nothing points at `/Download` any
  more - delete or rename one, or the `browse` frame shows two identical tiles.
- **`Download` itself cannot be picked in the system folder picker** ("To protect your privacy, choose
  another folder"). Pick `Download/FastMediaSorter_Store`, the seeder's own root, and accept the
  "different folder selected" prompt.
- **The player opens paused and paused means black.** A video frame is only present once playback has
  started, so seek to about a third and press play immediately before the capture; a frame taken at
  position 0 is black and reads as a decode failure that is not there.
- **`music-player` wants the visualizer, not the cover art.** The artwork shows first and the
  visualizer replaces it after roughly twenty seconds of playback, which is the composition the
  published slot and its caption ("Audio with live visualizers") already use. Do not tap the screen
  while waiting - a tap hides the transport row and no second tap brings it back.
- **`streams` is a raw catalogue until you filter it.** A fresh "Update catalog" lists thousands of
  entries with visible URLs, `(null)` names and duplicates - the debug-dump look §3.4 of S2602
  rejected. Type a genre into the search box (`jazz` was used for the shipped set): the result is a
  short list with clean names, genre chips and country flags. Grid view is worse, not better - most
  channels carry no picture.
- **The reader needs a document long enough to fill the page.** The generated EPUB was extended for
  exactly this reason; a two-paragraph chapter reflows to a mostly white frame on a 2560px viewport.
- **Pick a bright image for `image-viewer`.** The corpus artwork darkens towards the top of each
  frame, so a dark palette leaves the toolbar sitting on near-black.

```
pwsh -NoProfile -File scripts/release/publish-play-listing.ps1 -Mode validate   # no commit, validates payload
pwsh -NoProfile -File scripts/release/publish-play-listing.ps1 -Mode commit      # publishes the listing (owner-gated)
```

`validate` never commits; it confirms auth + payload. `commit` publishes the listing (Play may route it
through review). The live `commit` is an owner-gated operational step.

**Read the exit code before blaming the listing (S2345).** `0` means validated or committed. `1` means
the listing is at fault - a missing text file, a text over its Play limit, or a payload Play rejected -
and the run names which. `2` means the check could not happen at all: the listing is not implicated and
nothing here needs editing, so re-run later. The publisher already retries a 5xx, a rate limit and a
dropped connection five times with exponential backoff, so a `2` reports a sustained outage rather than
one hiccup - two clean runs died on a single unretried 503 on 2026-09-02, both reporting `1`.

A screenshot type whose local folder is missing or empty is skipped, and its live set on Play is left
alone - so a phone-only refresh cannot wipe the tablet screenshots already published.
