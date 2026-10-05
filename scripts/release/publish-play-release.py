#!/usr/bin/env python
"""Publish a FastMediaSorter AAB to a Google Play track (bundle + track + commit).

Separate from publish-play-listing.py, which pushes the store listing texts and images. This
script uploads the bundle, attaches it to a track together with the fastlane changelogs, and
commits the edit. Both reuse the same service-account key.

Usage:
    python publish-play-release.py [track] [status] [--aab PATH] [--version-code N] [--notes-code N]
                                   [--package NAME] [--notes-file PATH] [--dry-run]

--package addresses another Play app (the watch face, S4009); the default is the phone package.
The fastlane changelogs belong to the default package only: another package reads its release notes
from --notes-file and nowhere else, so a face release never borrows the phone's text. The notes file
holds the Play Console paste format - `<en-US>..</en-US>` blocks, one per language; a file with no
block is filed as en-US. --dry-run resolves and prints the plan, then exits before any API call.

Exit codes:
    0 - the bundle is on the track and the edit was committed (Play may route it via review), or
        --dry-run printed the plan.
    1 - the release is at fault: the AAB is missing, a non-default package has no notes file, or
        Play rejected the payload. That includes
        the Foreground-service-permissions 403 on commit, which needs an owner action in the
        Console and has to stay visible as a finding rather than as "could not verify".
    2 - could not verify: a sustained transient failure (5xx, rate limit, network). The release is
        NOT implicated - re-run when the API recovers.
"""
import json
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
# The release worktree `a.ps1 r` builds in - the sibling FastMediaSorter_release, resolved the
# same way a.ps1's Get-SiblingPath resolves it. S4095: the phone release builds there and
# generates its fastlane changelogs only there, so REPO_ROOT's build outputs describe some
# local build, never the release, and its changelogs for a new versionCode appear only after
# the post-publish commit lands on main and merges back.
RELEASE_WORKTREE = os.path.abspath(os.path.join(REPO_ROOT, '..', 'FastMediaSorter_release'))
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

def bundle_metadata_path(aab_path):
    """Resolves the AGP output-metadata.json belonging to the bundle being uploaded.

    The metadata must come from the module that produced THIS artifact. The watch releases on its
    own cadence, so app_v2/build/outputs usually holds a metadata file from some earlier phone
    release; reading it for a wear upload files the watch release under the phone's versionName.

    The uploaded path is a DOWNLOADS copy and carries no variant, only the module in its name, so
    the module is taken from the file name and the newest metadata under that module's bundle root
    is used - which is the bundle the release just built. Returns None when the module produced no
    bundle at all, so the caller can say "did not look" rather than guess.

    S4095: for the phone module the release worktree is searched before the development tree, and
    its standard APK outputs beside its bundle outputs, because the phone release always builds
    in the worktree (`a.ps1 r`) and AGP writes no output-metadata.json next to a bundle - only
    next to an APK. The wear and watchface modules keep the development-tree search: their
    campaigns build there, and a worktree copy from some earlier joint release would file a new
    watch build under a stale versionName - the exact failure S2788 removed from the watch track.
    """
    base_name = os.path.basename(aab_path).lower()
    # 'watchface' first: it does not contain 'wear', and read as app_v2 it would file the face
    # release under the phone's versionName.
    if 'watchface' in base_name:
        module = 'watchface'
    elif 'wear' in base_name:
        module = 'wear'
    else:
        module = 'app_v2'
    search_roots = []
    if module == 'app_v2':
        # Worktree first: bundle outputs, then the standard APK outputs the same release built
        # (the fallback assert-release-tree-binding.ps1 already uses for the same reason).
        search_roots.append(os.path.join(RELEASE_WORKTREE, module, 'build', 'outputs', 'bundle'))
        search_roots.append(os.path.join(RELEASE_WORKTREE, module, 'build', 'outputs', 'apk', 'standard', 'release'))
    # Development tree: bundle outputs only, as before this fix.
    search_roots.append(os.path.join(REPO_ROOT, module, 'build', 'outputs', 'bundle'))
    for root in search_roots:
        found = []
        for dirpath, _dirnames, filenames in os.walk(root):
            if 'output-metadata.json' in filenames:
                found.append(os.path.join(dirpath, 'output-metadata.json'))
        if found:
            return max(found, key=os.path.getmtime)
    print(f"Warning: no output-metadata.json for {module} under {RELEASE_WORKTREE} or {REPO_ROOT} "
          "- the bundle was not built in either tree.")
    return None


def read_bundle_metadata_element(aab_path):
    """Returns the first element of the release bundle's AGP output-metadata.json, or None.

    S1873: the version of a release is the version its artifact carries. Reading it out of
    app_v2/build.gradle.kts stopped being possible at ADR-4 - the version travels to gradle as a
    property and nothing writes those constants any more, so the build file holds a deliberate
    non-releasable sentinel. Any element answers: AGP gives every slice of one build the same
    version pair.

    None means "no metadata to read", which is a different answer from "the version is wrong".
    """
    path = bundle_metadata_path(aab_path)
    if not path:
        return None
    try:
        with open(path, 'r', encoding='utf-8') as f:
            elements = json.load(f).get('elements') or []
            if elements:
                return elements[0]
        print(f"Warning: {path} declares no elements.")
    except Exception as e:
        print(f"Warning: Could not read {path}: {e}")
    return None


def get_version_name(aab_path):
    """Reads the versionName the release build packaged into the bundle being uploaded."""
    element = read_bundle_metadata_element(aab_path)
    return element['versionName'] if element else None

def parse_args(argv):
    """Positional track/status, plus the flags a non-standard artifact needs.

    The phone AAB is the default because it is the only artifact this script published for
    its first two years. The watch bundle goes to its own form-factor track under the same
    applicationId, carries its own versionCode, and is not described by the phone bundle's
    output-metadata.json - hence the explicit --aab / --version-code pair (S1707).
    """
    aab_path = AAB_PATH
    version_code = None
    notes_code = None
    package_name = PACKAGE_NAME
    notes_file = None
    dry_run = False
    positional = []
    i = 0
    while i < len(argv):
        arg = argv[i]
        if arg == '--package':
            package_name = argv[i + 1]
            i += 2
        elif arg == '--notes-file':
            notes_file = os.path.abspath(argv[i + 1])
            i += 2
        elif arg == '--dry-run':
            dry_run = True
            i += 1
        elif arg == '--aab':
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
    return {
        'track': track,
        'status': status,
        'aab_path': aab_path,
        'version_code': version_code,
        'notes_code': notes_code,
        'package_name': package_name,
        'notes_file': notes_file,
        'dry_run': dry_run,
    }


def get_expected_version_code(aab_path):
    """Reads the versionCode the release build actually packaged into the bundle.

    S1873: this used to parse `defaultAppVersionCode` out of app_v2/build.gradle.kts, on the
    assumption that the release build had just written it there. ADR-4 removed that writer - the
    version travels to gradle as a property and the checked-in constants are a deliberate
    non-releasable sentinel - so the build file would now answer with a code no AAB carries, and
    the Play pre-check would compare the library against a version nobody built.

    AGP writes output-metadata.json beside the bundle it produced, so that file cannot disagree
    with the artifact. Used to pre-check the Play library without uploading first. Returns the int
    code, or None if it cannot be resolved.
    """
    element = read_bundle_metadata_element(aab_path)
    return int(element['versionCode']) if element else None

def list_existing_bundle_codes(service, edit_id, package_name=PACKAGE_NAME):
    """Returns the set of versionCodes already present in the App Bundle Explorer.

    A re-run after a rejected commit (e.g. the Foreground-service-permissions 403)
    finds the previously uploaded bundle here, so we can attach it instead of
    re-uploading a versionCode Play would refuse as a duplicate.
    """
    try:
        response = service.edits().bundles().list(
            packageName=package_name, editId=edit_id
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
    """Checks for fastlane changelogs for the given version_code.

    S4095: the release worktree is searched before the development tree because `a.ps1 r`
    generates the changelogs in the worktree during the build, while /skill-release commits
    them to main only after the Play publish - so at publish time the text exists only in the
    worktree, and reading REPO_ROOT first committed a production release without notes. The
    development tree serves the wear and watchface campaigns, which write their changelogs
    there directly, and any run whose worktree is missing.
    """
    notes = []
    locales = {
        'en-US': 'en-US',
        'ru-RU': 'ru-RU',
        'uk-UA': 'uk-UA'
    }
    for folder, lang in locales.items():
        for root in (RELEASE_WORKTREE, REPO_ROOT):
            changelog_path = os.path.join(
                root, 'fastlane', 'metadata', 'android', folder, 'changelogs', f"{version_code}.txt"
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
                            print(f"Found changelog for {lang} ({len(text)} chars) in {root}")
                            break
                except Exception as e:
                    print(f"Warning: Failed to read changelog at {changelog_path}: {e}")
    return notes


NOTES_BLOCK = re.compile(r'<([a-z]{2,3}(?:-[A-Za-z0-9]{2,4})?)>\s*(.*?)\s*</\1>', re.DOTALL)


def read_notes_file(notes_path):
    """Reads release notes in the Play Console paste format: one `<xx-YY>..</xx-YY>` block per
    language. A file with no block at all is one language, filed as en-US. Returns the list the
    track body takes, empty when the file holds no text."""
    with open(notes_path, 'r', encoding='utf-8') as f:
        raw = f.read()
    blocks = NOTES_BLOCK.findall(raw)
    if not blocks and raw.strip():
        blocks = [('en-US', raw.strip())]
    return [{'language': lang, 'text': text} for lang, text in blocks if text.strip()]


def main():
    args = parse_args(sys.argv[1:])
    track_name = args['track']
    status = args['status']
    aab_path = args['aab_path']
    forced_version_code = args['version_code']
    notes_code = args['notes_code']
    package_name = args['package_name']
    notes_file = args['notes_file']

    if not os.path.exists(aab_path):
        print(f"ERROR: AAB file not found at {aab_path}")
        sys.exit(1)

    # Checked before any API call: a missing notes file for another app is the release's fault,
    # and an edit opened first would only have to be abandoned.
    if package_name != PACKAGE_NAME and not notes_file:
        print(f"ERROR: package {package_name} reads release notes from --notes-file only "
              "- the phone's fastlane changelogs are not its text.")
        sys.exit(1)
    if notes_file and not os.path.exists(notes_file):
        print(f"ERROR: notes file not found at {notes_file}")
        sys.exit(1)
    file_notes = read_notes_file(notes_file) if notes_file else None
    if file_notes is not None and not file_notes:
        print(f"ERROR: {notes_file} holds no release text.")
        sys.exit(1)

    print(f"Target track: {track_name} (status: {status})")
    print(f"Service account key: {KEY_FILE}")
    print(f"Package name: {package_name}")
    print(f"AAB Path: {aab_path} ({os.path.getsize(aab_path) / 1024 / 1024:.2f} MB)")
    if notes_file:
        print(f"Notes file: {notes_file}")

    if args['dry_run']:
        print("DRY RUN: no edit opened, nothing uploaded, nothing committed.")
        sys.exit(0)

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
        edit = service.edits().insert(packageName=package_name, body={}).execute(
            num_retries=API_NUM_RETRIES)
        edit_id = edit['id']
        print(f"Edit transaction created: {edit_id}")

        # 3. Attach existing bundle or upload a fresh one.
        # If the build's versionCode is already in the App Bundle Explorer (e.g. a
        # prior run uploaded it but the commit was rejected by the FGS-permissions
        # gate), skip the upload and attach that bundle to the track - Play refuses
        # re-uploading a versionCode that already exists. Otherwise upload as usual.
        expected_version_code = forced_version_code if forced_version_code else get_expected_version_code(aab_path)
        existing_codes = list_existing_bundle_codes(service, edit_id, package_name)
        version_code = None

        if expected_version_code is not None and expected_version_code in existing_codes:
            version_code = expected_version_code
            print(f"\nBundle {version_code} already in library - skipping upload, attaching existing bundle.")
        else:
            if expected_version_code is not None:
                print(f"\nBundle {expected_version_code} not in library (have: {sorted(existing_codes) or 'none'}) - uploading.")
            print("\nUploading AAB (resumable, library-managed retry)...")
            media = MediaFileUpload(aab_path, mimetype='application/octet-stream', resumable=True)
            request = service.edits().bundles().upload(packageName=package_name, editId=edit_id, media_body=media)

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
        # The artifact's OWN changelog wins, and --notes-code is only the fallback for an artifact
        # that has none of its own - a form-factor build that ships the phone's text rather than no
        # text at all. It used to be the other way round, and that left a watch release exactly one
        # place to put watch-specific notes: on top of the phone changelog the fallback names. The
        # watch notes of 2026-09-05 landed in the phone's 260902195.txt that way, where they were
        # what Play and IzzyOnDroid showed for the phone version until a merge collided (S3027).
        if file_notes is not None:
            release_notes = file_notes
            print(f"Release notes: {len(release_notes)} language(s) from {notes_file}")
        else:
            release_notes = get_release_notes(version_code)
            if release_notes:
                print(f"Release notes: from this artifact's own versionCode {version_code}")
            elif notes_code:
                release_notes = get_release_notes(notes_code)
                if release_notes:
                    print(f"Release notes: none under {version_code}, falling back to --notes-code {notes_code}")
        if not release_notes:
            print("Release notes: none found - the release is committed without notes")
        version_name = get_version_name(aab_path)

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
            packageName=package_name,
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
            service.edits().commit(packageName=package_name, editId=edit_id).execute()
        except Exception as exc:  # noqa: BLE001 - the API surfaces this as a generic HttpError
            if 'changesNotSentForReview' not in str(exc):
                raise
            print("Play refuses automatic review for this app - committing with changes held.")
            service.edits().commit(
                packageName=package_name, editId=edit_id, changesNotSentForReview=True
            ).execute()
            held = True

        if held:
            print(f"SUCCESS: AAB version {version_code} committed to '{track_name}' as '{status}', but HELD.")
            print("Send it from the Console: Publishing overview -> Send changes for review.")
        else:
            print(f"SUCCESS: Edit transaction committed. AAB version {version_code} is now published on '{track_name}' track as '{status}'!")
        
    except Exception as e:  # noqa: BLE001 - surface the API error and pick the honest exit code
        if _is_transient(e):
            print(f"\nCOULD NOT VERIFY: the Play API refused the request transiently: {e}")
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
