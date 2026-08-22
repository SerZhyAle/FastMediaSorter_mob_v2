---
name: sza-resources-absent-in-this-checkout
description: /spec-prerelease step 2 configure always fails here - sza_resources.xml is gitignored owner data missing from this checkout, and it silently costs the Russian locale
metadata:
  type: project
---

`app_v2/src/main/res/xml/sza_resources.xml` does not exist in this checkout. It is gitignored owner-local
data (SMB/SFTP/FTP credentials), and per S0492 it is the only working resource-import path, so
`scripts/devtest/prerelease-configure.ps1` dies at its first stage `load-config` with exit 1. Confirmed
2026-08-20.

**Why:** the file carries live server credentials, so it was never committed. Its absence is an
environment fact about this machine, not a defect - do not park a ticket for it and do not try to
reconstruct it.

**How to apply:** when running `/spec-prerelease` here, expect step 2 to fail and plan around three
consequences rather than treating them as app defects:

- No network resource is registered, so **network-listing perf and every SMB/FTP/SFTP path go unmeasured**.
  Say so in the report instead of implying the sweep covered them.
- The `Channel='ui'` settings (theme, sort, grid, trash) never get applied; the suite runs on defaults.
- The `set:Language` stage never runs, so the app stays English while the Maestro flows search for Russian
  captions. That alone failed `local_browse` and `settings_toggle_sweep` on the 2026-08-20 run and looked
  exactly like two UI regressions. Apply the locale by hand before blaming the app:
  `adb -s <dev> shell cmd locale set-app-locales com.sza.fastmediasorter.debug --user current --locales ru`
  then force-stop and relaunch. All 6 smoke flows passed afterwards.

**Check the locale before restating this as a live risk (2026-08-21).** The locale consequence is the
one that gets misremembered, because it is easy to compress into "the missing file keeps the app in
English" - which is false. The file's only consumer is `ui/settings/helpers/SzaResourcesImporter.kt`,
which imports resources, network credentials and destinations and touches no locale at all; the language
is an ordinary in-app setting. What actually happens is longer: the missing file kills
`prerelease-configure.ps1` at `load-config`, so its later `set:Language` stage never runs. Once the
locale has been applied by hand it survives until the package is reinstalled - which every sweep does, because prerelease-prepare.ps1 uninstalls first and a per-app locale dies with the package. Measured 2026-08-22: get-app-locales answered [] immediately after prepare. Apply it after EVERY prepare, not once per device. One
command settles it:

    adb shell cmd locale get-app-locales com.sza.fastmediasorter.debug --user current

If that already answers `[ru]`, a failing Russian selector is a regression, not this environment - do
not write the caveat into a ticket as though it were still pending. Measured on the emulator that day:
locales `[ru]`, and all 6 smoke flows passed.

A sweep on the owner's own machine, where the file exists, is what actually covers the network paths.
