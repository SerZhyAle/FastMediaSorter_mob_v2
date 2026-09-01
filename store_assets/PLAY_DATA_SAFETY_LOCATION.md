# Play Console - Data safety, location section (S2083)

Source of truth for the **location rows of the Data safety form** in Play Console
(`App content -> Data safety -> Data types -> Location`), for both form factors.

The form lives only in the web console - the Play Developer API exposes no endpoint for it, exactly
as with the All files access declaration (`PLAY_PERMISSIONS_DECLARATION.md`). Decide **here**, then
answer in the console. Do not decide inside the console: the previous form was filled that way and
nobody could re-read what it said.

The listing half of the same argument lives in `play/listing/<locale>/full_description.txt`. Google's
location policy judges a declared permission against what the app tells the user, so the form, the
store description, the in-app rationale string and the privacy policy are read as one set. Edit them
together.

---

## The answer

**Location is not collected and not shared - on the phone, on the watch, and in the background.**
Every location row of the form stays unchecked for both form factors.

Each form factor is filled separately in the console; the Wear answer is not inherited from the
phone, even though the two now say the same thing.

| Form factor | Approximate location | Precise location | Background |
| --- | --- | --- | --- |
| Phone / tablet | unchecked | unchecked | unchecked |
| Wear OS | unchecked | unchecked | unchecked |

---

## Why "not collected" is the accurate answer

Google defines collection as transmission **off the device**, and states the exemption in as many
words:

> User data accessed by your app that is only processed locally on the user's device and not sent
> off device does not need to be disclosed.

- Source: `https://support.google.com/googleplay/android-developer/answer/10787469`

Nothing in the tree retains a device fix off the device. The two network paths that touch a
coordinate at all are both ephemeral in that page's own terms - held for the length of one request,
quantized to a map tile, nothing retained - and Google's worked example on that page is a weather app
doing exactly this shape:

- the static map gadget's tile fetch to `https://tile.openstreetmap.org`
  (`app_v2/.../data/map/OsmMapTileProvider.kt:162`), whose index is derived from the fix
  (`app_v2/.../data/map/MapRepositoryImpl.kt:91`);
- the live map gadget's page URL, which since S2292 carries the **centre of the tile**, not the fix
  (see below).

---

## The live map gadget, and why it no longer changes the answer

S2241 landed a **Google Maps Live Frame** launcher gadget on 2026-09-01 in a shape that would have
forced a `Sharing` declaration: it put the fix into a Google URL at full precision, and it
auto-granted the embedded page its own geolocation, which let that page poll live position on its own
schedule independently of this app.

S2292 removed both, and the tree carries the fix - measured 2026-09-01 in
`app_v2/src/main/java/com/sza/fastmediasorter/ui/launcher/widget/GoogleMapsLiveFrameView.kt`:

- `setGeolocationEnabled(false)` at `:59` - the page has no geolocation API to call.
- `onGeolocationPermissionsShowPrompt` answers `callback?.invoke(origin, false, false)` at `:73-79` -
  an explicit denial kept deliberately even though the setting above already makes it unreachable, so
  a later edit of the settings block cannot silently restore the grant.
- `updateLocation` coarsens through `WebMercatorTile.coarseLatitude/coarseLongitude` at `:126-127`
  before anything is stored or published, and the precise value is never written to a field - the
  recenter button reads the coarsened pair, so the exact coordinate has no second route into a URL.

That returns the live gadget to the same precision class the static map gadget already shipped, so
one answer covers both.

---

## Tree-side evidence, measured 2026-09-01

- **The phone declares two location permissions, not one.** `ACCESS_FINE_LOCATION` and
  `ACCESS_COARSE_LOCATION`, `app_v2/src/main/AndroidManifest.xml:34-35`. Both live in `src/main`, so
  they are present in every flavor - the form is answered per app and form factor, never per flavor.
- **No background location anywhere.** `ACCESS_BACKGROUND_LOCATION` is declared in no manifest of
  either module.
- **The watch declares no location permission at all.** No `uses-permission` line for location exists
  anywhere under `wear/src`; the only mention is a comment recording its removal in S2013.
- **What the permission is used for, phone:** coordinates written into photos and videos shot with
  the built-in camera; the compass, speed, altitude, map and Google Maps Live gadgets on the launcher
  desktop; the GNSS and Wi-Fi sections of the network monitor. The camera geotag exists in every
  flavor; the gadgets and the monitor are `standard` and `noLegal` only (`SUPPORT_LAUNCHER`,
  `SUPPORT_NETWORK_MONITOR` in `docs/FLAVOR_MATRIX.md`).
- **The camera geotag stays on the device.** The coordinate is written into the file's own metadata
  and travels only if the user shares that file, which is not app collection.
- **The Wi-Fi section reads no coordinate.** It only checks whether the grant exists, because the
  platform hides SSID and BSSID without it
  (`app_v2/.../data/networkmonitor/ConnectivitySnapshotDataSource.kt:216-227`).
- **The recorded GNSS track is a local file.** Written under the app's private `filesDir`
  (`app_v2/.../data/networkmonitor/GnssTrackRecorder.kt:141-157`) and leaves only when the user taps
  share. On-device storage is not collection; a user-initiated export is not app collection either.
- **The weather gadget is not a location consumer.** It sends a coordinate to `api.open-meteo.com`,
  but that coordinate is a city the user searched for, not the device fix - `WeatherRepositoryImpl`
  never references `DeviceLocationSource`.

---

## Superseded answer, kept so it is not re-derived

On 2026-08-31, through `/spec-quiz`, the answer recorded in S2083 §2.1 was to declare **both** phone
permissions as collected, background as not collected, and Wear separately as not collected.

That answer was taken against §1's *suspicion* that the form diverged from the tree, before Google's
definition of collection had been read - the quiz entry records it as
`recommended per §1: заранее заподозрено расхождение, не проверено`. Its Wear half and its background
half are confirmed. Its phone half was superseded on 2026-09-01, first by the definition and then by
the S2292 fix that removed the one consumer capable of refuting it.

**Do not re-open the phone rows without re-reading `GoogleMapsLiveFrameView.kt`.** The answer here is
true because of three specific lines in that file; a change that restores the geolocation grant or
the full-precision coordinate makes it false, and then the form, the store description, the in-app
rationale strings in thirteen locales and the privacy policy in three all have to move together.

---

## Operator steps

1. Open `App content -> Data safety` and pick the form factor first - phone and Wear OS are filled
   separately.
2. **Phone / tablet:** leave `Approximate location`, `Precise location` and background all unchecked.
3. **Wear OS:** the same - all three unchecked.
4. Confirm the store description still names why location is asked for (section A of
   `PLAY_CONSOLE_CHECKLIST.md`); the form and the description are reviewed as a pair, exactly as the
   All files access declaration and the description were in S1989.
5. Under a policy enforcement Play refuses automatic review - send the held edit from
   `Publishing overview -> Send changes for review`.

**Timing.** Do not touch the console while a submission is under review; an edit restarts the review
from the beginning. The 2026-08-31 verdict cleared that hold (S2272 §1.1), so this work is unblocked,
but re-check the publishing state before editing.
