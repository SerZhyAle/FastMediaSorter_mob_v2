# Meta Horizon Store listing source

This tree is the **Meta Horizon Store** listing source for the `vr` flavor (S0555), the sibling of
`play/listing/` for Google Play. Meta exposes no API for listing metadata - the build goes up with
`ovr-platform-util`, everything here is entered by hand in the developer dashboard
(App submissions -> v1 -> App metadata). This tree is what that hand entry copies from.

## Layout

- `<locale>/title.txt` - app name. Meta forbids "VR" in it and requires the cover art to show it verbatim.
- `<locale>/short_description.txt` - at most 500 characters.
- `<locale>/full_description.txt` - at most 1500 characters; the field accepts markdown.
- `<locale>/keywords.txt` - one per line, at most 5 (the dashboard refuses a sixth).
- `images/` - every image the Assets section asks for, generated; never edit by hand.

## Images

Regenerate with `python scripts/release/compose-meta-store-assets.py`.

- Cover art (landscape 2560x1440, square 1440x1440, portrait 1008x1440), hero 3000x900, mini
  landscape 1080x360 and the transparent logo are rendered from the WAVE-PARTICLES backdrop (GREEN
  palette) with the launcher arrows and the title. The title stays inside the vertical 20-80% band.
- `icon_512.png` is the Google Play icon flattened to 24-bit - one product, one icon on every store.
- `screenshots/` are the raw Play tablet captures (`temp/play-shots-tablet/`) cropped to 2560x1440
  without the status bar or the gesture strip. They show only screens the `vr` flavor has; the
  launcher and the paired-watch row are left out. Meta asks for in-headset content, so a reviewer may
  ask for headset captures instead.

## Dashboard fields that are not files

- Website: https://serzhyale.github.io/FastMediaSorter_mob_v2/
- Privacy policy: https://serzhyale.github.io/FastMediaSorter_mob_v2/docs/PRIVACY_POLICY.html
- Support link: https://github.com/SerZhyAle/FastMediaSorter_mob_v2/issues
- Category: Apps; genres Utility, Entertainment, Productivity (or the nearest the form offers).
- Specs: no Early Access, stationary, sitting and standing, comfortable, no internet required, no
  subscription, Quest 1 purchase-blocked.
- Price: free; no ads; no in-app purchases; build age group Teens and adults.
- Content rating: a fresh IARC questionnaire for this app - never a rating id copied from another
  product (the watch face has its own).
