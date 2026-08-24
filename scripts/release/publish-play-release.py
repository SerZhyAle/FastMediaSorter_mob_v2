#!/usr/bin/env python
import sys
import os
import re
import socket
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

# Increase default timeout for slow connections during large file uploads
socket.setdefaulttimeout(120)

PACKAGE_NAME = 'com.sza.fastmediasorter'

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
        ).execute()
        return {int(b['versionCode']) for b in response.get('bundles', [])}
    except Exception as e:
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

    try:
        # 1. Initialize API Service
        creds = service_account.Credentials.from_service_account_file(
            KEY_FILE, 
            scopes=['https://www.googleapis.com/auth/androidpublisher']
        )
        service = build('androidpublisher', 'v3', credentials=creds)

        # 2. Start Edit Transaction
        print("\nStarting new edit transaction...")
        edit = service.edits().insert(packageName=PACKAGE_NAME, body={}).execute()
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
            print("\nUploading AAB (resumable with retry guard)...")
            media = MediaFileUpload(aab_path, mimetype='application/octet-stream', resumable=True)
            request = service.edits().bundles().upload(packageName=PACKAGE_NAME, editId=edit_id, media_body=media)

            response = None
            while response is None:
                retries = 5
                while retries > 0:
                    try:
                        status_progress, response = request.next_chunk()
                        if status_progress:
                            print(f"  Uploaded: {status_progress.progress() * 100:.1f}%")
                        break
                    except (socket.timeout, Exception) as chunk_error:
                        retries -= 1
                        print(f"  Warning: Chunk transfer error ({chunk_error}). Retrying block (attempts left: {retries})...")
                        if retries == 0:
                            raise chunk_error

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

        track_update = service.edits().tracks().update(
            packageName=PACKAGE_NAME,
            editId=edit_id,
            track=track_name,
            body=track_body
        ).execute()
        print("Track updated successfully.")

        # 6. Commit Edit
        # Which review mode Play accepts is a property of the app's state, not a constant:
        # normally changesNotSentForReview is rejected with HTTP 400, but while the app is under
        # a policy enforcement the commit is rejected WITHOUT it. Both refusals name the
        # parameter, so try the automatic path first and fall back to holding the changes
        # (S1989 - this was hardcoded to the automatic path and stopped working on 2026-08-24).
        print("\nCommitting changes to Google Play Console...")
        held = False
        try:
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
        
    except Exception as e:
        print(f"\nERROR: {e}")
        sys.exit(1)

if __name__ == '__main__':
    main()
