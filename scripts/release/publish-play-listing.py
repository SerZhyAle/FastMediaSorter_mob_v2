#!/usr/bin/env python
"""Stage Google Play listing texts and images for one application.

Use --dry-run to print the listing plan without credentials or an API call. Validate and commit
require --preflight evidence for the exact operation list and SZA_PLAY_SERVICE_ACCOUNT_JSON.
Only supplied locales and image slots are considered. Existing matching content remains untouched.

Exit codes: 0 planned, validated or committed; 1 invalid or rejected listing;
2 unable to verify preconditions or API outcome.
"""
import argparse
import json
import os
from pathlib import Path
import ssl
import sys
import socket
import httplib2
from google.auth.exceptions import TransportError
from googleapiclient.http import MediaFileUpload

from play_publishing import (
    API_NUM_RETRIES, PlayStop, commit_edit, connect_publisher, file_sha256, json_sha256,
    package_id, publishing_edit, validate_edit,
)

socket.setdefaulttimeout(120)

# The progress line below prints each locale's title, and on Windows the console's default codepage
# cannot encode most of them: with thirteen locales the set includes Chinese, Arabic, Hindi, Bangla
# and Urdu. print() then raises UnicodeEncodeError ("'charmap' codec can't encode characters"), the
# outer handler catches it, and the run reports "Google Play listing publication failed" - a
# reporting bug indistinguishable from a real rejection, after the listings had already uploaded
# successfully. Measured 2026-09-02 (S2340): the run died on the locale after 'uk-UA', and Cyrillic
# had already been printed as mojibake before that. errors='replace' keeps a console that still
# cannot render a script from turning a cosmetic limitation back into a failed exit code.
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, 'reconfigure'):
        _stream.reconfigure(encoding='utf-8', errors='replace')

PACKAGE_NAME = 'com.sza.fastmediasorter'

# A failure carrying one of these is Google's or the network's, never the listing's, so it maps onto
# exit 2 - "could not verify" - instead of exit 1. Deliberately narrow: the rest of 4xx stays a
# defect, because a 400 from listings().update() IS rejected payload (a language code Play does not
# know, a character it refuses in a title), and calling that "could not verify" would hide the very
# thing this script exists to catch.
TRANSIENT_STATUSES = frozenset((408, 429, 500, 502, 503, 504))

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..'))
LISTING_ROOT = os.path.join(REPO_ROOT, 'play', 'listing')

# folder name under play/listing/ -> Play Console language (BCP-47) code.
#
# This set is expected to cover every locale declared in app_v2/src/main/res/xml/locales_config.xml,
# which is the single declaration of the languages the app offers. Wear App Quality Guidelines WO-G2
# requires the listing to be localized in those languages, and the parity is enforced by
# scripts/quality/assert-play-listing-locales.ps1 (S2340). This dict - not the directory listing - is
# what the publisher iterates, so a folder with no row here is skipped silently and never published.
#
# The folder name equals the Play code for every locale except 'uk-UA': Play calls Ukrainian 'uk' and
# takes no region suffix for it. Play's codes are a fixed list, not free-form BCP-47 - some languages
# require a region ('de-DE', 'hi-IN'), some forbid one ('ar', 'ur'), and Chinese has no script-only
# code, so the app's 'zh-Hans' maps onto 'zh-CN'.
LOCALES = {
    'en-US': 'en-US',
    'ru-RU': 'ru-RU',
    'uk-UA': 'uk',
    'zh-CN': 'zh-CN',
    'hi-IN': 'hi-IN',
    'es-419': 'es-419',
    'fr-FR': 'fr-FR',
    'ar': 'ar',
    'bn-BD': 'bn-BD',
    'pt-BR': 'pt-BR',
    'ur': 'ur',
    'de-DE': 'de-DE',
    'it-IT': 'it-IT',
}

LIMITS = {'title.txt': 30, 'short_description.txt': 80, 'full_description.txt': 4000}

# imageType -> source path under play/listing/<locale>/images/
SINGLE_IMAGES = {'featureGraphic': 'featureGraphic.png', 'icon': 'icon.png'}
# Each is its own Play slot with its own live set. A type whose local folder is absent or empty is
# left untouched, so publishing a phone-only refresh never wipes the tablet screenshots already live.
SCREENSHOT_TYPES = ('phoneScreenshots', 'sevenInchScreenshots', 'tenInchScreenshots', 'wearScreenshots')

IMAGE_MIME = {'.png': 'image/png', '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg'}


def _is_transient(exc):
    """True when a failure is the network's or Google's rather than the listing's.

    Every local content check - a missing file, a text over its Play limit - runs and exits before
    the edit transaction opens, so by construction nothing raised inside that transaction can be a
    defect in the listing TEXTS. What is left splits in two: payload Play rejected, which is a real
    defect, and infrastructure, which is not. Only the second is transient, and conflating them is
    what let two clean runs report exit 1 on 2026-09-02 (S2345).

    The status is read defensively: an exception with no `resp` must answer the question, not raise
    a second one from inside the handler that is trying to describe the first.

    ServerNotFoundError (the hostname did not resolve) and TransportError (the network dropped while
    fetching the OAuth token) are named separately because neither derives from OSError - checked
    against the live hierarchy in the project venv - so the socket-level tuple above does not reach
    them. Both are as far from a listing defect as a 503 is.
    """
    network_level = (
        socket.timeout, TimeoutError, ConnectionError, ssl.SSLError,
        httplib2.ServerNotFoundError, TransportError,
    )
    if isinstance(exc, network_level):
        return True
    return getattr(getattr(exc, 'resp', None), 'status', None) in TRANSIENT_STATUSES


def _read_text(path):
    with open(path, 'r', encoding='utf-8') as f:
        return f.read().strip()


def load_listing(locale, listing_root=LISTING_ROOT):
    """Read title/short/full for a locale; enforce Play char limits."""
    base = os.path.join(listing_root, locale)
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


def listing_images(folder, listing_root):
    """Return the supplied image slots with file paths and their approved hashes."""
    directory = Path(listing_root) / folder / 'images'
    slots = {}
    for image_type in SCREENSHOT_TYPES:
        slot = directory / image_type
        if slot.is_dir():
            files = sorted(path for path in slot.iterdir()
                           if path.is_file() and path.suffix.lower() in IMAGE_MIME)
            if files:
                slots[image_type] = [(path, file_sha256(path)) for path in files]
    for image_type, name in SINGLE_IMAGES.items():
        path = directory / name
        if path.is_file():
            slots[image_type] = [(path, file_sha256(path))]
    return slots


def prepare_listing(listing_root):
    locales, reason = resolve_locales(listing_root)
    if locales is None:
        raise ValueError(reason)

    listings = {}
    errors = []
    for folder, language in locales.items():
        payload, violations = load_listing(folder, listing_root)
        errors.extend(violations)
        if payload is not None:
            listings[language] = (payload, listing_images(folder, listing_root))
    if errors:
        raise ValueError('; '.join(errors))
    return listings


def listing_operation(listings):
    return {"listing": [
        {"language": language, "textSha256": json_sha256(payload), "imageSha256": {
            kind: [digest for _, digest in files] for kind, files in images.items()
        }}
        for language, (payload, images) in sorted(listings.items())
    ]}


def stage_images(service, fields, slots):
    count = 0
    for kind, files in slots.items():
        slot = {**fields, 'imageType': kind}
        existing = service.edits().images().list(**slot).execute(num_retries=API_NUM_RETRIES)
        if [image.get('sha256', '').lower() for image in existing.get('images', [])] == [
            digest for _, digest in files
        ]:
            continue

        service.edits().images().deleteall(**slot).execute(num_retries=API_NUM_RETRIES)
        for path, digest in files:
            if file_sha256(path) != digest:
                raise ValueError(f"Listing image changed after preflight: {path}")
            media = MediaFileUpload(str(path), mimetype=IMAGE_MIME[path.suffix.lower()])
            result = service.edits().images().upload(**slot, media_body=media).execute(
                num_retries=API_NUM_RETRIES
            )
            if result.get('image', {}).get('sha256', '').lower() != digest:
                raise ValueError(f"Play returned a different hash for listing image {path.name}.")
            count += 1
    return count


def stage_listing(service, package, edit_id, listings):
    fields = {'packageName': package, 'editId': edit_id}
    existing = service.edits().listings().list(**fields).execute(num_retries=API_NUM_RETRIES)
    by_language = {entry['language']: entry for entry in existing.get('listings', [])}
    changed = False
    for language, (payload, slots) in listings.items():
        old = by_language.get(language, {})
        updates = {key: value for key, value in payload.items() if old.get(key) != value}
        locale = {**fields, 'language': language}
        if updates and old:
            service.edits().listings().patch(**locale, body=updates).execute(num_retries=API_NUM_RETRIES)
        elif updates:
            service.edits().listings().update(**locale, body=payload).execute(num_retries=API_NUM_RETRIES)
        images = stage_images(service, locale, slots)
        if updates or images:
            print(f"  {language}: {len(updates)} text field(s) changed, {images} image(s) uploaded")
            changed = True
    return changed


def parse_args(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', nargs='?', default='validate')
    parser.add_argument('--package', default=PACKAGE_NAME)
    parser.add_argument('--listing-root', type=os.path.abspath, default=LISTING_ROOT)
    parser.add_argument('--preflight')
    parser.add_argument('--dry-run', action='store_true')
    args = parser.parse_args(argv)
    return {**vars(args), 'package_name': args.package}


def resolve_locales(listing_root):
    """Folder -> Play language for the locales this root publishes, or (None, reason).

    The default root publishes every row of LOCALES and a missing folder is a defect found later by
    load_listing. Any other root publishes the folders it actually holds, so a three-locale tree
    touches three Play languages and leaves every other one exactly as it is live.
    """
    if os.path.normcase(os.path.abspath(listing_root)) == os.path.normcase(LISTING_ROOT):
        return dict(LOCALES), None
    if not os.path.isdir(listing_root):
        return None, f"listing root not found: {listing_root}"
    folders = sorted(d for d in os.listdir(listing_root)
                     if os.path.isdir(os.path.join(listing_root, d)))
    unknown = [d for d in folders if d not in LOCALES]
    if unknown:
        return None, f"no Play language mapped for folder(s) {', '.join(unknown)} in {listing_root}"
    if not folders:
        return None, f"no locale folder in {listing_root}"
    return {d: LOCALES[d] for d in folders}, None


def main(argv=None):
    args = parse_args(argv)
    mode = args['mode']
    if mode not in ('validate', 'commit'):
        print(f"Unknown mode '{mode}'. Choose 'validate' or 'commit'.")
        return 2

    try:
        package = package_id(args['package_name'])
        listings = prepare_listing(args['listing_root'])
        operations = [listing_operation(listings)]
        print(f"Listing plan for {package} ({mode}): {', '.join(listings)}")
        print(f"Planned operations: {json.dumps(operations)}")
        if args['dry_run']:
            print("Dry run completed without an API call.")
            return 0

        with publishing_edit(package, operations, args['preflight'], connect_publisher) as (
            service, edit, root, evidence, http
        ):
            changed = stage_listing(service, package, edit['id'], listings)
            if mode == 'commit' and not changed:
                print("Listing matches the requested content. No commit was needed.")
                return 0
            validated = validate_edit(service, package, edit['id'])
            if mode == 'validate':
                if not validated:
                    raise PlayStop("Play requires the held mode, which validate cannot request.")
                print("Listing edit validated. The edit will be discarded.")
                return 0
            held = commit_edit(service, package, edit, root, evidence, http)
        print(f"Listing edit committed (held={int(held)}). Check Publishing overview for its disposition.")
        return 0
    except PlayStop as exc:
        print(f"Google Play stopped: {exc}", file=sys.stderr)
        return 2
    except Exception as exc:
        if _is_transient(exc):
            print(f"Could not verify the listing because the Play request failed transiently: {exc}",
                  file=sys.stderr)
            return 2
        print(f"Listing rejected: {exc}", file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
