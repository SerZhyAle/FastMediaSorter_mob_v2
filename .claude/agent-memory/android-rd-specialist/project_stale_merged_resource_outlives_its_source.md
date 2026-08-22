---
name: stale-merged-resource-outlives-its-source
description: A deleted res/ file keeps shipping - its .flat artifact stays in merged_res forever, so an old layout variant can win over the current one at runtime
metadata:
  type: project
---

Deleting a file under `app_v2/src/*/res/` does **not** remove its artifact from
`app_v2/build/intermediates/merged_res/<variant>/merge<Variant>Resources/`. The merger updates what
changed and adds what appeared, but leaves behind what vanished, and packaging still picks it up. The
orphan then competes as a real resource variant: on a device matching its qualifier, Android prefers it
over the current file.

**Measured 2026-08-20 (S1825, found while verifying S1823):** `layout-w600dp/activity_streams.xml` had
no source anywhere in the twelve source sets, yet `layout-w600dp-v13_activity_streams.xml.flat` sat in
**every** variant - standardDebug and noLegalDebug (08-10), vrDebug (08-11), legacyDebug (08-13),
**standardRelease (08-14)**, liteDebug and photosDebug (08-09). Any device wider than 600dp - the
owner's S21 in landscape - inflated that nine-day-old layout. It was invisible while the stale copy was
merely old; it became a hard crash the moment the current layout gained a view the old one lacks:
`NullPointerException: Missing required view with ID: stubCatalogBanner` from `ActivityStreamsBinding.bind`,
on every open of the screen.

**How to apply:**
- A `Missing required view with ID` from a generated `*Binding.bind` when the id **is** in the layout you
  edited means you are looking at a different variant of that layout. Before doubting the edit, list the
  packaged copies: open the APK and enumerate `res/*<layout name>*` - two entries with different sizes is
  the whole diagnosis. `Glob` over the source tree is NOT sufficient; the stale copy has no source.
- Recovery: delete `app_v2/build/intermediates/merged_res/*` and
  `app_v2/build/intermediates/incremental/merge*Resources`, then rebuild. Deleting a single `.flat`
  behind the merger's back leaves its incremental state inconsistent - drop both directories.
- Auditing for orphans: a flat is named `<qualifier-folder>_<file>.flat`; strip a trailing `-vNN` from
  the folder and look for that path in every `app_v2/src/*/res`. Legitimate no-source hits to ignore:
  `values-*/*.arsc` and `mipmap-anydpi/ic_launcher*.xml`, which are generated aggregates.
- This is why a release must not be built on a long-lived incremental tree without this check - S1825
  carries the gate.
