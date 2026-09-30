# Watch face - Google Play source and owner runbook

This tree is the Play source of the watch face app `com.sza.fastmediasorter.watchface` (S4009). The phone and watch app `com.sza.fastmediasorter` is a different Play app with its own tree, `play/listing/`.

- `listing/<locale>/` - `title.txt`, `short_description.txt`, `full_description.txt` and `images/` (`icon.png` 512x512, `featureGraphic.png` 1024x500, `wearScreenshots/NN.png` square) for `en-US`, `ru-RU`, `uk-UA`.
- `release-notes.txt` - release text in the Play Console paste format: one `<en-US>`, `<ru-RU>`, `<uk>` block each.

The listing text must stay honest about a store-only install: the buttons show whatever data source the user picks in the watch face editor, and FastMediaSorter shortcuts and the phone battery bar appear only with the sideload (`noLegal`) watch build, because the services that fill them are declared in that build alone. It never names steps, heart rate, health or unread notifications - the app's Health declaration answer is "no health features".

Sources checked 2026-09-30:

- Create and distribute a watch face: https://support.google.com/googleplay/android-developer/answer/13560201
- Form factor releases on dedicated tracks (the Wear OS opt-in): https://support.google.com/googleplay/android-developer/answer/13295490

## What the campaign does

Every Wear release runs the face through `/skill-release-wear` (Steps 3b, 4c, 4d):

1. `.\a.ps1 wfr` (or `scripts/builders/build-watchface-release.ps1 -VersionName .. -VersionCode ..` from the campaign's stamp) builds `DOWNLOADS/FastMediaSorter_watchface_release.aab`, verifies the signature against `scripts/release/expected-signing-fingerprint.txt` and prints the versionCode. Nothing is bumped by hand.
2. `scripts/release/publish-play-release.ps1 -Package com.sza.fastmediasorter.watchface -Track 'wear:production' -NotesFile play/watchface/release-notes.txt ..` uploads the bundle to the face app.
3. `scripts/release/publish-play-listing.ps1 -Mode commit -Package com.sza.fastmediasorter.watchface -ListingRoot play/watchface/listing` publishes the three locales and their images.

Read the state back without changing anything: `pwsh -NoProfile -File scripts/release/read-play-tracks.ps1 -Json -Package com.sza.fastmediasorter.watchface`.

## First delivery - 2026-09-30

- Bundle `versionCode 260930119`, `versionName 2.60.9301.146`, signer SHA-256 `6A:A6:EA:72:75:2A:E9:44:09:29:F1:E0:BA:F8:F2:AD:13:CC:21:CE:C4:60:6B:F6:22:9D:69:02:06:C4:35:A6`.
- Uploaded to `wear:internal` as a **draft** release with the three-language notes; the API accepted it, so the Wear OS form factor is already added to the face app.
- Store listing committed for `en-US`, `ru-RU`, `uk`: texts, icon, feature graphic, three Wear screenshots.
- Track reader afterwards: `wear:internal` holds `{status: draft, versionCodes: [260930119], name: 2.60.9301.146}`; every other track is empty.

## Console actions left to the owner

The Play publishing API cannot opt an app into a form factor or send an app for its first review, so these stay manual:

1. **Wear OS opt-in.** Test and release -> Advanced settings -> Form factors tab. If Wear OS does not yet read as opted in, choose "Opt in to Wear OS and agree to the review policy". Its preconditions - a Wear OS screenshot and a Wear bundle on a test track - are now met (source: answer 13295490).
2. **Store settings.** Grow users -> Store presence -> Store settings: category **Personalization**, and the watch face tag, so the face is listed among watch faces (source: answer 13560201).
3. **Internal test (optional).** Testing -> Internal testing, form factor selector **Wear OS only**: open the draft release `260930119`, add yourself as a tester, roll it out, and install once from the tester link on the Galaxy Watch.
4. **Production.** Production, form factor selector **Wear OS only** -> Create new release -> add `260930119` from the library (or the newer bundle a later `/skill-release-wear` built) -> Review release -> Start rollout. The Wear review runs on top of the policy review.
5. **Go-live check.** After approval, `https://play.google.com/store/apps/details?id=com.sza.fastmediasorter.watchface` must answer before any app or site page carrying a link to it is released (strategic 5.1 "Порядок выхода").

## How the graphics were made

- `icon.png` - rendered from `watchface/src/main/res/drawable/ic_watch_face_launcher.xml` at 512x512.
- `wearScreenshots/` - captured on the reviewed `Wear_OS_Large_Round` emulator (454x454, API 37): `01` with three buttons bound in the face editor, `02` as a store-only install shows the face, `03` always-on.
- `featureGraphic.png` - drawn by `compose()` of `scripts/release/compose-feature-graphic.py` with the face icon, screenshot `01` as the mockup and the face headline per locale.
