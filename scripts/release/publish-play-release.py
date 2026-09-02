#!/usr/bin/env python
"""Publish a FastMediaSorter AAB to a Google Play track (bundle + track + commit).

Separate from publish-play-listing.py, which pushes the store listing texts and images. This
script uploads the bundle, attaches it to a track together with the fastlane changelogs, and
commits the edit. Both reuse the same service-account key.

Usage:
    python publish-play-release.py [track] [status] [--aab PATH] [--version-code N] [--notes-code N]

Exit codes:
    0 - the bundle is on the track and the edit was committed (Play may route it via review).
    1 - the release is at fault: the AAB is missing, or Play rejected the payload. That includes
        the Foreground-service-permissions 403 on commit, which needs an owner action in the
        Console and has to stay visible as a finding rather than as "could not verify".
    2 - could not verify: a sustained transient failure (5xx, rate limit, network). The release is
        NOT implicated - re-run when the API recovers.
"""
import os
import re
import socket
import ssl
import sys
import httplib2
from google.auth.exceptions import TransportError
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

# Increase default timeout for slow connections during large file uploads
socket.setdefaulttimeout(120)

PACKAGE_NAME = 'com.sza.fastmediasorter'

# Passed to every .execute() that is safe to repeat. google-api-python-client carries its own
# randomized exponential backoff, but it engages only when the caller asks for it: with no
# num_retries the first refusal is raised straight out. Which calls are excluded, and why the
# commit is one of them, is written at the call sites (S2346).
API_NUM_RETRIES = 5

# A failure carrying one of these is Google's or the network's, never the release's, so it maps onto
# exit 2 - "could not verify" - instead of exit 1. Deliberately narrow, and the narrowness earns its
# keep here more than for the listing: 403 on commit is the Foreground-service-permissions gate, the
# one step of a release the owner must take by hand, and demoting it to "could not verify" would
# hide the only thing that run is asking for.
TRANSIENT_STATUSES = frozenset((408, 429, 500, 502, 503, 504))


def _is_transient(exc):
    """True when a failure is the network's or Google's rather than the release's.

    The local checks that can implicate the artifact - the AAB is missing, its versionCode cannot be
    read - run and exit before the edit transaction opens, so what is raised inside the transaction
    splits in two: payload Play rejected, which is a real finding, and infrastructure, which is not.

    The status is read defensively: an exception with no `resp` must answer the question, not raise
    a second one from inside the handler that is trying to describe the first.

    ServerNotFoundError (the hostname did not resolve) and TransportError (the network dropped while
    fetching the OAuth token) are named separately because neither derives from OSError, so the
    socket-level tuple does not reach them (S2345).
    """
    network_level = (
        socket.timeout, TimeoutError, ConnectionError, ssl.SSLError,
        httplib2.ServerNotFoundError, TransportError,
    )
    if isinstance(exc, network_level):
        return True
    return getattr(getattr(exc, 'resp', None), 'status', None) in TRANSIENT_STATUSES

# Resolve absolute paths relative to script location
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..'))
SECRETS_DIR = os.path.join(REPO_ROOT, '.secrets')
KEY_FILE = next(
    (
        path for path in (
            os.path.join(SECRETS_DIR, 'play-console-key.json'),
            os.path.join(REPO_ROOT, 'play-console-key.json'),
        )
        if os.path.exists(path)
    ),
    os.path.join(SECRETS_DIR, 'play-console-key.json'),
)
AAB_PATH = os.path.join(REPO_ROOT, 'DOWNLOADS', 'FastMediaSorter_standard_release.aab')

def get_version_name():
    """Reads the current versionName from app_v2/build.gradle.kts."""
    build_gradle = os.path.join(REPO_ROOT, 'app_v2', 'build.gradle.kts')
    try:
        with open(build_gradle, 'r', encoding='utf-8') as f:
            content = f.read()
            match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
            if match:
                return match.group(1)
    except Exception as e:
        print(f"Warning: Could not read versionName from gradle files: {e}")
    return None

def parse_args(argv):
    """Positional track/status, plus the flags a non-standard artifact needs.

    The phone AAB is the default because it is the only artifact this script published for
    its first two years. The watch bundle goes to its own form-factor track under the same
    applicationId, carries its own versionCode, and cannot be read out of app_v2's
    build.gradle.kts - hence the explicit --aab / --version-code pair (S1707).
    """
    aab_path = AAB_PATH
    version_code = None
    notes_code = None
    positional = []
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg == '--aab':
            aab_path = os.path.abspath(argv[i + 1])
            i += 2
        elif arg == '--version-code':
            version_code = int(argv[i + 1])
            i += 2
        elif arg == '--notes-code':
            notes_code = int(argv[i + 1])
            i += 2
        else:
            positional.append(arg)
            i += 1
    track = positional[0] if positional else 'production'
    status = positional[1] if len(positional) > 1 else 'completed'
    return track, status, aab_path, version_code, notes_code


def get_expected_version_code():
    """Reads the release versionCode the build stamped into app_v2/build.gradle.kts.

    The release build (a.ps1 r / build-release-spectrum.ps1) writes the resolved
    code into `defaultAppVersionCode`, so the first `versionCode = <digits>` match
    is the version of the AAB on disk. Used to pre-check the Play library without
    uploading first. Returns the int code, or None if it cannot be resolved.
    """
    build_gradle = os.path.join(REPO_ROOT, 'app_v2', 'build.gradle.kts')
    try:
        with open(build_gradle, 'r', encoding='utf-8') as f:
            content = f.read()
            match = re.search(r'versionCode\s*=\s*(\d+)', content)
            if match:
                return int(match.group(1))
    except Exception as e:
        print(f"Warning: Could not read versionCode from gradle files: {e}")
    return None

def list_existing_bundle_codes(service, edit_id):
    """Returns the set of versionCodes already present in the App Bundle Explorer.

    A re-run after a rejected commit (e.g. the Foreground-service-permissions 403)
    finds the previously uploaded bundle here, so we can attach it instead of
    re-uploading a versionCode Play would refuse as a duplicate.
    """
    try:
        response = service.edits().bundles().list(
            packageName=PACKAGE_NAME, editId=edit_id
        ).execute(num_retries=API_NUM_RETRIES)
        return {int(b['versionCode']) for b in response.get('bundles', [])}
    except Exception as e:
        # A transient refusal here means "the library is unreadable", not "the library is empty",
        # and the two lead opposite ways: falling back to upload would push a versionCode that may
        # already be published, and Play's refusal of the duplicate would then be reported against
        # the artifact. Nothing has been uploaded at this point, so re-raising costs only an
        # abandoned edit, which expires on its own (S2346).
        if _is_transient(e):
            raise
        print(f"Warning: Could not list existing bundles ({e}). Falling back to upload.")
        return set()

def get_release_notes(version_code):
    """Checks for fastlane changelogs for the given version_code."""
    notes = []
    locales = {
        'en-US': 'en-US',
        'ru-RU': 'ru-RU',
        'uk-UA': 'uk-UA'
    }
    for folder, lang in locales.items():
        changelog_path = os.path.join(
            REPO_ROOT, 'fastlane', 'metadata', 'android', folder, 'changelogs', f"{version_code}.txt"
        )
        if os.path.exists(changelog_path):
            try:
                with open(changelog_path, 'r', encoding='utf-8') as f:
                    text = f.read().strip()
                    if text:
                        notes.append({
                            'language': lang,
                            'text': text
                        })
                        print(f"Found changelog for {lang} ({len(text)} chars)")
            except Exception as e:
                print(f"Warning: Failed to read changelog at {changelog_path}: {e}")
    return notes

def main():
    track_name, status, aab_path, forced_version_code, notes_code = parse_args(sys.argv[1:])

    if not os.path.exists(aab_path):
        print(f"ERROR: AAB file not found at {aab_path}")
        sys.exit(1)

    print(f"Target track: {track_name} (status: {status})")
    print(f"Service account key: {KEY_FILE}")
    print(f"Package name: {PACKAGE_NAME}")
    print(f"AAB Path: {aab_path} ({os.path.getsize(aab_path) / 1024 / 1024:.2f} MB)")

    # Read by the handler below to tell "the run never got that far" from "the bundle is in the
    # library and only the commit is unaccounted for" - two situations that need opposite first
    # moves from the operator, and which one exit code alone cannot separate (S2346).
    commit_started = False

    try:
        # 1. Initialize API Service
        creds = service_account.Credentials.from_service_account_file(
            KEY_FILE, 
            scopes=['https://www.googleapis.com/auth/androidpublisher']
        )
        service = build('androidpublisher', 'v3', credentials=creds)

        # 2. Start Edit Transaction
        print("\nStarting new edit transaction...")
        edit = service.edits().insert(packageName=PACKAGE_NAME, body={}).execute(
            num_retries=API_NUM_RETRIES)
        edit_id = edit['id']
        print(f"Edit transaction created: {edit_id}")

        # 3. Attach existing bundle or upload a fresh one.
        # If the build's versionCode is already in the App Bundle Explorer (e.g. a
        # prior run uploaded it but the commit was rejected by the FGS-permissions
        # gate), skip the upload and attach that bundle to the track - Play refuses
        # re-uploading a versionCode that already exists. Otherwise upload as usual.
        expected_version_code = forced_version_code if forced_version_code else get_expected_version_code()
        existing_codes = list_existing_bundle_codes(service, edit_id)
        version_code = None

        if expected_version_code is not None and expected_version_code in existing_codes:
            version_code = expected_version_code
            print(f"\nBundle {version_code} already in library - skipping upload, attaching existing bundle.")
        else:
            if expected_version_code is not None:
                print(f"\nBundle {expected_version_code} not in library (have: {sorted(existing_codes) or 'none'}) - uploading.")
            print("\nUploading AAB (resumable, library-managed retry)...")
            media = MediaFileUpload(aab_path, mimetype='application/octet-stream', resumable=True)
            request = service.edits().bundles().upload(packageName=PACKAGE_NAME, editId=edit_id, media_body=media)

            # The retry belongs to the library, not to this loop. A resumable upload addresses a
            # repeated chunk by the byte offset the server confirms, so retrying one is safe - and
            # next_chunk's own backoff is randomized and spread out. The hand-rolled loop this
            # replaced retried ANY exception five times with no pause at all, which spent five extra
            # requests on payload Play had already rejected and gave a real 5xx five instant
            # attempts instead of spaced ones (S2346).
            response = None
            while response is None:
                status_progress, response = request.next_chunk(num_retries=API_NUM_RETRIES)
                if status_progress:
                    print(f"  Uploaded: {status_progress.progress() * 100:.1f}%")

            version_code = response['versionCode']
            print(f"SUCCESS: AAB uploaded. Version Code: {version_code}")

        # 4. Read release notes
        # A form-factor artifact ships the same release notes as the phone build but carries a
        # different versionCode, so the fastlane changelog is filed under the phone's number.
        # --notes-code names that number instead of shipping a Wear release with no notes at all.
        release_notes = get_release_notes(notes_code if notes_code else version_code)
        version_name = get_version_name()

        # 5. Update Track
        print(f"\nAdding bundle {version_code} to track '{track_name}' as {status}...")
        release_body = {
            'versionCodes': [str(version_code)],
            'status': status
        }
        if version_name:
            release_body['name'] = version_name
        if release_notes:
            release_body['releaseNotes'] = release_notes

        track_body = {
            'track': track_name,
            'releases': [release_body]
        }

        service.edits().tracks().update(
            packageName=PACKAGE_NAME,
            editId=edit_id,
            track=track_name,
            body=track_body
        ).execute(num_retries=API_NUM_RETRIES)
        print("Track updated successfully.")

        # 6. Commit Edit
        # Which review mode Play accepts is a property of the app's state, not a constant:
        # normally changesNotSentForReview is rejected with HTTP 400, but while the app is under
        # a policy enforcement the commit is rejected WITHOUT it. Both refusals name the
        # parameter, so try the automatic path first and fall back to holding the changes
        # (S1989 - this was hardcoded to the automatic path and stopped working on 2026-08-24).
        print("\nCommitting changes to Google Play Console...")
        held = False
        commit_started = True
        try:
            # No num_retries on either commit, unlike every call above. The commit is the one
            # one-way step: if a response is lost after Play has already accepted the edit, the
            # retry arrives at an edit that no longer exists and comes back 4xx - which reads
            # from outside as a rejected release. That is the same false accusation this ticket
            # removes, entering through the other door (S2346).
            service.edits().commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
        except Exception as exc:  # noqa: BLE001 - the API surfaces this as a generic HttpError
            if 'changesNotSentForReview' not in str(exc):
                raise
            print("Play refuses automatic review for this app - committing with changes held.")
            service.edits().commit(
                packageName=PACKAGE_NAME, editId=edit_id, changesNotSentForReview=True
            ).execute()
            held = True

        if held:
            print(f"SUCCESS: AAB version {version_code} committed to '{track_name}' as '{status}', but HELD.")
            print("Send it from the Console: Publishing overview -> Send changes for review.")
        else:
            print(f"SUCCESS: Edit transaction committed. AAB version {version_code} is now published on '{track_name}' track as '{status}'!")
        
    except Exception as e:  # noqa: BLE001 - surface the API error and pick the honest exit code
        if _is_transient(e):
            print(f"\nCANNOT VERIFY: the Play API refused the request transiently: {e}")
            print(f"Already retried {API_NUM_RETRIES} times with backoff, so this is a sustained")
            print("outage rather than one hiccup. The release itself is not implicated.")
            if commit_started:
                print("\nThe failure happened AT THE COMMIT, so the bundle is already in the")
                print("App Bundle Explorer and only the edit's fate is unknown. Open Publishing")
                print("overview in the Console before re-running: a re-run attaches the uploaded")
                print("versionCode instead of uploading it again, but it cannot tell you whether")
                print("the previous commit landed.")
            else:
                print("Nothing was committed - re-run when the API recovers.")
            sys.exit(2)
        print(f"\nERROR: {e}")
        sys.exit(1)

if __name__ == '__main__':
    main()
