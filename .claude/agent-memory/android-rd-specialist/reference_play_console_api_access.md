---
name: play-console-api-access
description: How to read live Google Play track/bundle states via the androidpublisher service-account key, and what the API can NOT see
metadata:
  type: reference
---

I CAN query live Play Console state read-only via the Google Play Developer API (androidpublisher v3), using the service-account key already in the repo. This is "how I check Play states" - I do NOT have web-console UI access.

**Setup (already present):**
- Key: `.secrets/play-console-key.json` (root `play-console-key.json` is a fallback). Same key the publisher uses.
- Python venv: `.venv/Scripts/python.exe` with `google-auth` + `google-api-python-client` (verify: `import google.oauth2, googleapiclient`).
- Package: `com.sza.fastmediasorter`.

**Read-only probe pattern** (open edit -> `tracks().list()` / `bundles().list()` -> `edits().delete()`, NEVER `commit`):
- Reusable script saved at `temp/play_status.py` - run `.venv/Scripts/python.exe temp/play_status.py`.
- Returns per-track release name/versionCode/status (production/beta/alpha/internal) and the App Bundle Explorer versionCode list.
- Deleting the edit (not committing) guarantees zero mutation.

**What the API CANNOT see** (web console UI only - ask the owner for a screenshot):
- App content declarations incl. Foreground service permissions form, `Need attention` / `Actioned` tabs.
- Policy review verdicts / rejection reasons / approval state.
- Note: `internal` testing track gets NO policy review, so a bundle sitting there has no verdict regardless.

**Why:** I initially (wrongly) told the owner I can't look at Play at all; the androidpublisher key makes track/bundle state fully readable. The publish script `scripts/release/publish-play-release.py` is write-only (upload+commit) but proves the same creds/venv exist.

**How to apply:** When asked "what's the state on Play / which build is on which track" -> run the read-only probe. When asked about FGS/app-content review approval -> the API can't answer; request a console screenshot. Related: [[project_skill_release_gotchas]], S0629/S0628 (FGS declaration), S0214.
