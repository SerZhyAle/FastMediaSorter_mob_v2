# Google Play listing source

This tree is the **Google Play** store-listing source. It is consumed by
`scripts/release/publish-play-listing.py` (via `publish-play-listing.ps1`) and is intentionally
**separate** from `fastlane/metadata/android/`.

The store description is also half of the All files access argument. Google reviews the Permissions
Declaration Form against the description, so a full description that stops leading with file
organizing invalidates the declaration - that pair is what an update was rejected on, 2026-08-24
(S1989). The declaration's own text lives in `store_assets/PLAY_PERMISSIONS_DECLARATION.md`; edit the
two together.

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
  <locale>/                       # en-US, ru-RU, uk-UA
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

A screenshot type whose local folder is missing or empty is skipped, and its live set on Play is left
alone - so a phone-only refresh cannot wipe the tablet screenshots already published.
