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
KEY_FILE = os.path.join(REPO_ROOT, 'play-console-key.json')
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
    track_name = 'production'
    status = 'completed' # Default to completed to fully automate the rollout
    
    if len(sys.argv) > 1:
        track_name = sys.argv[1]
    if len(sys.argv) > 2:
        status = sys.argv[2]

    if not os.path.exists(AAB_PATH):
        print(f"ERROR: AAB file not found at {AAB_PATH}")
        sys.exit(1)

    print(f"Target track: {track_name} (status: {status})")
    print(f"Service account key: {KEY_FILE}")
    print(f"Package name: {PACKAGE_NAME}")
    print(f"AAB Path: {AAB_PATH} ({os.path.getsize(AAB_PATH) / 1024 / 1024:.2f} MB)")

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

        # 3. Resumable Upload of AAB with Chunk Retries
        print("\nUploading AAB (resumable with retry guard)...")
        media = MediaFileUpload(AAB_PATH, mimetype='application/octet-stream', resumable=True)
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
        release_notes = get_release_notes(version_code)
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
        print("\nCommitting changes to Google Play Console...")
        commit_response = service.edits().commit(
            packageName=PACKAGE_NAME, 
            editId=edit_id,
            changesNotSentForReview=True
        ).execute()
        print(f"SUCCESS: Edit transaction committed. AAB version {version_code} is now published on '{track_name}' track as '{status}'!")
        
    except Exception as e:
        print(f"\nERROR: {e}")
        sys.exit(1)

if __name__ == '__main__':
    main()
