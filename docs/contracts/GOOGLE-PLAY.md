<sub class="doc-stamp">26.11.21 03:19</sub>

# Pointer - `GOOGLE-PLAY`

| | |
| --- | --- |
| **Id** | `GOOGLE-PLAY` |
| **Version** | 0.4, draft. Owner: this product |
| **Home** | `google-play/README.md` in the shared contracts catalog |
| **Role here** | owner and publisher - the console apps `com.sza.fastmediasorter` (phone and watch app) and `com.sza.fastmediasorter.watchface` |

## What this repository must do to stay conformant

- Keep the phone and watch app on one application id and one app signing key; the watch face is its own console app and its own review batch (rule 1).
- Record the app signing and upload key fingerprints of each console app - certificate fingerprints only, never key material (rules 2-3).
- Never delete a ReTrace mapping row in the console; recover a mapping from the shipped bundle (rule 5).
- Before every upload, check versionCode, the current target-API floor, device reach and new foreground-service types (rules 6, 8, 9, 22). Apply the first-artifact and first-declaration stops in section 8C.
- Serialize publishers, edit-based probes and Console writes per app (rule 19 and section 8A). Apply the rule 18 commit modes with the rule 29 review guard in both requests. Reconcile an uncertain result by read-back.
- Record track data, lifecycle summaries, Console review and public serving as separate evidence (rule 20 and section 8B). Split the phone and the watch into separate review batches with "Save for later" when one must not hold the other (rules 16-17).
- Publish a release as one submission: the track, the listing (only when it changed) and the release notes in one edit, committed once (`publish-play-release.ps1 -ListingRoot`); commit nothing while a submission is in review; never cancel a submission to add a change; a submission past seven days goes to Play support, not back into the queue (rules 28-30).
- Publish the watch only when its sources changed materially since its last release, as its own submission after the phone release is published or on alternate releases, so a phone fix never waits on a watch review (rule 31; `/skill-release-wear` Step 0 asks, from `measure-wear-change.ps1`).
- Prove a watch quality claim on the store flavor at 192 and 227 dp, mid-gesture; capture the watch face's icons from the face itself (rules 24-25).
- A Play refusal no rule predicted is amended in the catalog first, as the owner.

## Where it lives here

- `scripts/release/publish-play-release.ps1` and `publish-play-release.py` - the bundle upload and the two commit modes.
- `scripts/release/publish-play-listing.ps1` - the listing upload on its own; a release carries its listing through `publish-play-release.ps1 -ListingRoot` instead.
- `scripts/release/measure-wear-change.ps1` - the watch change since its last release (rule 31).
- `scripts/release/read-play-tracks.ps1`, `read-play-public-serve.ps1`, `refresh-play-publishing-state.ps1` - the read-back.
- `scripts/release/assert-play-review-batches.ps1` - the batch split plan.
- `scripts/release/assert-fgs-types-vs-previous.ps1`, `scripts/release/confirm-fgs-snapshot.ps1` - the foreground-service baseline.
- `scripts/release/assert-play-target-api-floor.ps1`, `scripts/release/assert-required-features-vs-previous.ps1` - the target-API floor and required-feature pre-upload gates (rules 8-9).
- `scripts/quality/assert-wear-phone-identity-parity.ps1`, `assert-orientation-implied-feature.ps1` - identity parity and the implied-feature class.
- `docs/PLAY_PUBLISHING_STATE.md` - the publishing record.
