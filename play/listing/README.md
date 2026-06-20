# Google Play listing source

This tree is the **Google Play** store-listing source. It is consumed by
`scripts/release/publish-play-listing.py` (via `publish-play-listing.ps1`) and is intentionally
**separate** from `fastlane/metadata/android/`.

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
      phoneScreenshots/<NN>.png    # composed by scripts/release/compose-play-screenshots.py
      featureGraphic.png           # optional
  captions.json                    # localized screenshot caption strings
  README.md
```

## Publishing

```
pwsh -NoProfile -File scripts/release/publish-play-listing.ps1 -Mode validate   # no commit, validates payload
pwsh -NoProfile -File scripts/release/publish-play-listing.ps1 -Mode commit      # publishes the listing (owner-gated)
```

`validate` never commits; it confirms auth + payload. `commit` publishes the listing (Play may route it
through review). The live `commit` is an owner-gated operational step.
