<sub class="doc-stamp">26.11.15 19:23</sub>

# Pointer - `GOOGLE-PLAY`

| | |
| --- | --- |
| **Id** | `GOOGLE-PLAY` |
| **Version** | 0.1, draft. Owner: this product |
| **Home** | `google-play/README.md` in the shared contracts catalog |
| **Role here** | owner and publisher - the console apps `com.sza.fastmediasorter` (phone and watch app) and `com.sza.fastmediasorter.watchface` |

## What this repository must do to stay conformant

- Keep the phone and watch app on one application id and one app signing key; the watch face is its own console app and its own review batch (rule 1).
- Record the app signing and upload key fingerprints of each console app - certificate fingerprints only, never key material (rules 2-3).
- Never delete a ReTrace mapping row in the console; recover a mapping from the shipped bundle (rule 5).
- Before every upload: a versionCode above every code the app used, the target-API floors read that release, no required feature the previous release did not carry, no new foreground-service type in the store build (rules 6, 8, 9, 22).
- Publish with one edit per application at a time; try the automatic commit, fall back to the held one only on a refusal that names `changesNotSentForReview`, never retry a commit (rules 18-19).
- Prove a publication by the public store page, never by the API alone (rule 20); split the phone and the watch into separate review batches with "Save for later" when one must not hold the other (rules 16-17).
- Prove a watch quality claim on the store flavor at 192 and 227 dp, mid-gesture; capture the watch face's icons from the face itself (rules 24-25).
- A Play refusal no rule predicted is amended in the catalog first, as the owner.

## Where it lives here

- `scripts/release/publish-play-release.ps1` and `publish-play-release.py` - the bundle upload and the two commit modes.
- `scripts/release/publish-play-listing.ps1` - the listing upload.
- `scripts/release/read-play-tracks.ps1`, `read-play-public-serve.ps1`, `refresh-play-publishing-state.ps1` - the read-back.
- `scripts/release/assert-play-review-batches.ps1` - the batch split plan.
- `scripts/release/assert-fgs-types-vs-previous.ps1`, `scripts/release/confirm-fgs-snapshot.ps1` - the foreground-service baseline.
- `scripts/release/assert-play-target-api-floor.ps1`, `scripts/release/assert-required-features-vs-previous.ps1` - the target-API floor and required-feature pre-upload gates (rules 8-9).
- `scripts/quality/assert-wear-phone-identity-parity.ps1`, `assert-orientation-implied-feature.ps1` - identity parity and the implied-feature class.
- `docs/PLAY_PUBLISHING_STATE.md` - the publishing record.
