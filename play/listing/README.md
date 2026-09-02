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
      featureGraphic.png               # optional
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
python scripts/release/compose-play-screenshots.py
```

Navigate the app to the target screen yourself, then snap it into a slot. Raw shots land in
`temp/play-shots/<locale>/<slot>.png`; compose falls back to `temp/play-shots/<slot>.png` when a
locale has no own capture, so a shared set and per-locale sets can coexist.

`-SetAppLocale` applies the Android 13+ per-app locale override, which needs no reboot but does
recreate the running activity - run it once before a locale batch, never between navigating and
capturing.

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
