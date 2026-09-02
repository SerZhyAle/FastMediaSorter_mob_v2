#!/usr/bin/env python
"""Publish the Google Play store listing (texts + images) from play/listing/.

Separate from publish-play-release.py (which uploads the AAB + changelogs). This script only
touches the store listing: edits().listings() for title/short/full and edits().images() for
screenshots / feature graphic. Reuses the same service-account key.

Usage:
    python publish-play-listing.py [validate|commit]

    validate (default) - create an edit, push listing+images, call edits().validate(), do NOT commit.
    commit             - same, then edits().commit() -> listing goes live (Play may route via review).

Exit codes:
    0 - the listing was validated, or committed in commit mode.
    1 - the listing is at fault: a missing text file, a text over its Play limit, or a payload Play
        rejected. Fix the listing.
    2 - could not verify: an unknown mode, Play refusing to validate under enforcement, or a
        sustained transient failure (5xx / rate limit / network). The listing is NOT implicated.
"""
import os
import ssl
import sys
import socket
import httplib2
from google.auth.exceptions import TransportError
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

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

# Passed to every .execute() below. google-api-python-client carries its own randomized exponential
# backoff, but it engages only when the caller asks for it - with no num_retries the first refusal is
# raised straight out. That matters more here than in a one-call script: a run makes one insert, then
# per locale a listings().update() plus a deleteall+upload pair for every non-empty image slot, so a
# full pass is over sixty calls inside ONE edit transaction and a single 5xx anywhere in it discards
# the whole transaction. Measured 2026-09-02: two consecutive runs died exactly that way, on a
# different locale each time (S2345).
API_NUM_RETRIES = 5

# A failure carrying one of these is Google's or the network's, never the listing's, so it maps onto
# exit 2 - "could not verify" - instead of exit 1. Deliberately narrow: the rest of 4xx stays a
# defect, because a 400 from listings().update() IS rejected payload (a language code Play does not
# know, a character it refuses in a title), and calling that "could not verify" would hide the very
# thing this script exists to catch.
TRANSIENT_STATUSES = frozenset((408, 429, 500, 502, 503, 504))

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
            language=language, imageType=shot_type).execute(num_retries=API_NUM_RETRIES)
        for name in shots:
            path = os.path.join(shots_dir, name)
            mime = IMAGE_MIME[os.path.splitext(name)[1].lower()]
            service.edits().images().upload(
                packageName=PACKAGE_NAME, editId=edit_id,
                language=language, imageType=shot_type,
                media_body=MediaFileUpload(path, mimetype=mime)).execute(num_retries=API_NUM_RETRIES)
            uploaded += 1

    for image_type, fname in SINGLE_IMAGES.items():
        path = os.path.join(images_dir, fname)
        if os.path.exists(path):
            mime = IMAGE_MIME[os.path.splitext(fname)[1].lower()]
            service.edits().images().deleteall(
                packageName=PACKAGE_NAME, editId=edit_id,
                language=language, imageType=image_type).execute(num_retries=API_NUM_RETRIES)
            service.edits().images().upload(
                packageName=PACKAGE_NAME, editId=edit_id,
                language=language, imageType=image_type,
                media_body=MediaFileUpload(path, mimetype=mime)).execute(num_retries=API_NUM_RETRIES)
            uploaded += 1

    return uploaded


def _execute_or_hold(request_factory):
    """Run an edits() call, retrying once with changesNotSentForReview when Play demands it.

    Which of the two modes Play accepts is a property of the app's current state, not of this
    script: normally an edit is sent for review automatically and passing the flag is rejected
    with HTTP 400, but while the app is under a policy enforcement the opposite holds and the
    call is rejected without it. Both refusals name the parameter in the message, so the mode is
    discovered rather than guessed - a hardcoded choice is wrong half the time, and it was
    hardcoded to the wrong half on 2026-08-24 (S1989).

    Returns True when the changes were committed but held for a manual send from the Console.
    """
    try:
        request_factory().execute(num_retries=API_NUM_RETRIES)
        return False
    except Exception as exc:  # noqa: BLE001 - the API surfaces this as a generic HttpError
        if 'changesNotSentForReview' not in str(exc):
            raise
        print("\nPlay refuses automatic review for this app - retrying with the changes held.")
        request_factory(changesNotSentForReview=True).execute(num_retries=API_NUM_RETRIES)
        return True


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
        edit = service.edits().insert(
            packageName=PACKAGE_NAME, body={}).execute(num_retries=API_NUM_RETRIES)
        edit_id = edit['id']
        print(f"Edit transaction created: {edit_id}")

        for folder, language in LOCALES.items():
            service.edits().listings().update(
                packageName=PACKAGE_NAME, editId=edit_id,
                language=language, body=payloads[folder]).execute(num_retries=API_NUM_RETRIES)
            imgs = upload_images(service, edit_id, folder, language)
            title = payloads[folder]['title']
            print(f"  {folder} -> {language}: listing updated (title='{title}'), images uploaded: {imgs}")

        if mode == 'validate':
            # validate() has no changesNotSentForReview parameter - only commit() does. So while
            # the app is under enforcement there is no way to validate at all, and saying so is
            # the honest answer: exit 2 means "could not verify", not "found a problem" (S1989).
            try:
                service.edits().validate(
                    packageName=PACKAGE_NAME, editId=edit_id).execute(num_retries=API_NUM_RETRIES)
            except Exception as exc:  # noqa: BLE001 - the API surfaces this as a generic HttpError
                if 'changesNotSentForReview' not in str(exc):
                    raise
                print("\nCANNOT VALIDATE: Play refuses automatic review while the app is under")
                print("enforcement, and validate() cannot hold changes the way commit() can.")
                print("The local checks above all passed. Use 'commit' to push, then send the")
                print("changes for review from Publishing overview in the Console.")
                sys.exit(2)
            print("\nSUCCESS: edit validated. NOT committed (validate mode).")
            print("Run with 'commit' to publish the listing live.")
        else:
            held = _execute_or_hold(
                lambda **kw: service.edits().commit(
                    packageName=PACKAGE_NAME, editId=edit_id, **kw
                )
            )
            if held:
                print("\nSUCCESS: edit committed, but HELD - not sent for review.")
                print("Play refuses automatic review while the app is under enforcement.")
                print("Send it from the Console: Publishing overview -> Send changes for review.")
            else:
                print("\nSUCCESS: edit committed. Listing is now published (Play may route via review).")

    except Exception as e:  # noqa: BLE001 - surface the API error and fail non-zero
        if _is_transient(e):
            print(f"\nCANNOT VERIFY: the Play API refused the request transiently: {e}")
            print(f"Already retried {API_NUM_RETRIES} times with backoff, so this is a sustained")
            print("outage rather than one hiccup. The local content checks above all passed and")
            print("the listing itself is not implicated - re-run when the API recovers.")
            sys.exit(2)
        print(f"\nERROR: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()
