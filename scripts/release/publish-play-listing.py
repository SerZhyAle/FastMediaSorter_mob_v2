#!/usr/bin/env python
"""Publish the Google Play store listing (texts + images) from play/listing/.

Separate from publish-play-release.py (which uploads the AAB + changelogs). This script only
touches the store listing: edits().listings() for title/short/full and edits().images() for
screenshots / feature graphic. Reuses the same service-account key.

Usage:
    python publish-play-listing.py [validate|commit]

    validate (default) - create an edit, push listing+images, call edits().validate(), do NOT commit.
    commit             - same, then edits().commit() -> listing goes live (Play may route via review).
"""
import os
import sys
import socket
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

socket.setdefaulttimeout(120)

PACKAGE_NAME = 'com.sza.fastmediasorter'

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..'))
SECRETS_DIR = os.path.join(REPO_ROOT, '.secrets')
LISTING_ROOT = os.path.join(REPO_ROOT, 'play', 'listing')

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

# folder name under play/listing/ -> Play Console language (BCP-47) code.
# Folders keep the fastlane-style names; Play uses 'uk' (not 'uk-UA') for Ukrainian.
LOCALES = {'en-US': 'en-US', 'ru-RU': 'ru-RU', 'uk-UA': 'uk'}

LIMITS = {'title.txt': 30, 'short_description.txt': 80, 'full_description.txt': 4000}

# imageType -> source path under play/listing/<locale>/images/
SINGLE_IMAGES = {'featureGraphic': 'featureGraphic.png', 'icon': 'icon.png'}
# Each is its own Play slot with its own live set. A type whose local folder is absent or empty is
# left untouched, so publishing a phone-only refresh never wipes the tablet screenshots already live.
SCREENSHOT_TYPES = ('phoneScreenshots', 'sevenInchScreenshots', 'tenInchScreenshots')

IMAGE_MIME = {'.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg'}


def _read_text(path):
    with open(path, 'r', encoding='utf-8') as f:
        return f.read().strip()


def load_listing(locale):
    """Read title/short/full for a locale; enforce Play char limits."""
    base = os.path.join(LISTING_ROOT, locale)
    out = {}
    over = []
    for fn, field in (('title.txt', 'title'),
                      ('short_description.txt', 'shortDescription'),
                      ('full_description.txt', 'fullDescription')):
        path = os.path.join(base, fn)
        if not os.path.exists(path):
            return None, [f"{locale}: missing {fn}"]
        text = _read_text(path)
        if len(text) > LIMITS[fn]:
            over.append(f"{locale}/{fn}: {len(text)} > {LIMITS[fn]}")
        out[field] = text
    return out, over


def upload_images(service, edit_id, folder, language):
    """Replace screenshots + single images for a locale if present. Returns count uploaded.

    `folder` is the play/listing/ subdir name; `language` is the Play BCP-47 code.
    """
    images_dir = os.path.join(LISTING_ROOT, folder, 'images')
    uploaded = 0

    for shot_type in SCREENSHOT_TYPES:
        shots_dir = os.path.join(images_dir, shot_type)
        if not os.path.isdir(shots_dir):
            continue
        shots = sorted(f for f in os.listdir(shots_dir)
                       if os.path.splitext(f)[1].lower() in IMAGE_MIME)
        if not shots:
            continue
        service.edits().images().deleteall(
            packageName=PACKAGE_NAME, editId=edit_id,
            language=language, imageType=shot_type).execute()
        for name in shots:
            path = os.path.join(shots_dir, name)
            mime = IMAGE_MIME[os.path.splitext(name)[1].lower()]
            service.edits().images().upload(
                packageName=PACKAGE_NAME, editId=edit_id,
                language=language, imageType=shot_type,
                media_body=MediaFileUpload(path, mimetype=mime)).execute()
            uploaded += 1

    for image_type, fname in SINGLE_IMAGES.items():
        path = os.path.join(images_dir, fname)
        if os.path.exists(path):
            mime = IMAGE_MIME[os.path.splitext(fname)[1].lower()]
            service.edits().images().deleteall(
                packageName=PACKAGE_NAME, editId=edit_id,
                language=language, imageType=image_type).execute()
            service.edits().images().upload(
                packageName=PACKAGE_NAME, editId=edit_id,
                language=language, imageType=image_type,
                media_body=MediaFileUpload(path, mimetype=mime)).execute()
            uploaded += 1

    return uploaded


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else 'validate'
    if mode not in ('validate', 'commit'):
        print(f"ERROR: unknown mode '{mode}' (expected 'validate' or 'commit')")
        sys.exit(2)

    print(f"Mode: {mode}")
    print(f"Service account key: {KEY_FILE}")
    print(f"Package name: {PACKAGE_NAME}")
    print(f"Listing source: {LISTING_ROOT}")

    # Load + validate all locales before touching the API.
    payloads = {}
    over_limit = []
    for folder in LOCALES:
        listing, over = load_listing(folder)
        if over and any('missing' in o for o in over):
            print(f"ERROR: {folder}: {'; '.join(over)}")
            sys.exit(1)
        over_limit.extend(over)
        payloads[folder] = listing
    if over_limit:
        print("ERROR: char-limit violations:")
        for o in over_limit:
            print(f"  - {o}")
        sys.exit(1)

    try:
        creds = service_account.Credentials.from_service_account_file(
            KEY_FILE, scopes=['https://www.googleapis.com/auth/androidpublisher'])
        service = build('androidpublisher', 'v3', credentials=creds)

        print("\nStarting new edit transaction...")
        edit = service.edits().insert(packageName=PACKAGE_NAME, body={}).execute()
        edit_id = edit['id']
        print(f"Edit transaction created: {edit_id}")

        for folder, language in LOCALES.items():
            service.edits().listings().update(
                packageName=PACKAGE_NAME, editId=edit_id,
                language=language, body=payloads[folder]).execute()
            imgs = upload_images(service, edit_id, folder, language)
            title = payloads[folder]['title']
            print(f"  {folder} -> {language}: listing updated (title='{title}'), images uploaded: {imgs}")

        if mode == 'validate':
            service.edits().validate(packageName=PACKAGE_NAME, editId=edit_id).execute()
            print("\nSUCCESS: edit validated. NOT committed (validate mode).")
            print("Run with 'commit' to publish the listing live.")
        else:
            service.edits().commit(packageName=PACKAGE_NAME, editId=edit_id).execute()
            print("\nSUCCESS: edit committed. Listing is now published (Play may route via review).")

    except Exception as e:  # noqa: BLE001 - surface the API error and fail non-zero
        print(f"\nERROR: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()
