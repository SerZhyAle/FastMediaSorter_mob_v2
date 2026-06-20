---
name: release-no-coverage-regression-gate
description: Hard release gate - STOP and do not release if supported countries, age ratings, or device coverage regress vs the prior release
metadata:
  type: feedback
---

Release gate (hard stop): if a release would **reduce** any of these vs. the currently-published build, do NOT release - stop and surface it to the owner:

- **Countries** - number/set of supported Play Store countries (availability/distribution).
- **Age ratings** - content-rating / age-group coverage (a narrower or higher age gate is a regression).
- **Devices** - supported-device count / reach: minSdk bump, dropped ABI, removed screen/form-factor support, added `<uses-feature required="true">`, tightened manifest filters, flavor reach shrink.

**Why:** owner stated this as an explicit release rule (2026-06-19). Coverage regressions silently shrink the install base / cut off existing users and are far costlier than holding a release. Expanding coverage is fine; shrinking it is a blocker.

**How to apply:** during `/spec-prerelease`, `/skill-release`, and any spec that touches flavors/minSdk/manifest/Play listing/distribution - diff the new build's country list, age rating, and device-support surface against the previous release. On any decrease, treat it as a release blocker (like a FAIL verdict): report the specific regression and the prior-vs-new delta, do not propose/run the release until the owner explicitly accepts the trade-off. Relates to [[release-no-coverage-regression-gate]] sibling release gates and the prerelease sweep.
