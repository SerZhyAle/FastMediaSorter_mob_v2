#!/usr/bin/env python
"""Stage one application's Google Play tracks, release notes and optional listing in one edit.

Single-track usage: [track] [status] [--aab PATH --version-code N] [--version-name NAME]
                    [--package NAME] [--notes-file PATH | --notes-code N] [--listing-root PATH]
Batch usage: --batch PATH (JSON with packageName, releases and optional listingRoot).

Use --dry-run to print the operation list locally. Real edits require --preflight with the matching
operations and SZA_PLAY_SERVICE_ACCOUNT_JSON. Another application supplies its own release notes.
Each successfully committed track emits one PLAY-COMMITTED record for the PowerShell wrapper.

Exit codes: 0 planned or committed; 1 invalid or rejected payload (including an FGS 403);
2 unable to verify preconditions or API outcome.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import ssl
import sys
import httplib2
from google.auth.exceptions import TransportError
from googleapiclient.http import MediaIoBaseUpload

from play_publishing import (
    API_NUM_RETRIES, PlayStop, commit_edit, connect_publisher, file_sha256, json_sha256,
    package_id, publishing_edit, read_object, validate_edit,
)

# Increase default timeout for slow connections during large file uploads
socket.setdefaulttimeout(120)

PACKAGE_NAME = 'com.sza.fastmediasorter'

# A failure carrying one of these is Google's or the network's, never the release's, so it maps onto
# exit 2 - "could not verify" - instead of exit 1. Deliberately narrow, and the narrowness earns its
# keep here more than for the listing: 403 on commit is the Foreground-service-permissions gate, which
# answers a foreground-service type the standard bundle must not carry, and demoting it to "could not
# verify" would hide the build defect that run is reporting.
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


def parse_args(argv):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('track', nargs='?')
    parser.add_argument('status', nargs='?')
    parser.add_argument('--aab')
    parser.add_argument('--version-code', type=int)
    parser.add_argument('--version-name')
    parser.add_argument('--notes-code', type=int)
    parser.add_argument('--notes-file')
    parser.add_argument('--package', dest='package_name')
    parser.add_argument('--listing-root')
    parser.add_argument('--batch')
    parser.add_argument('--preflight')
    parser.add_argument('--dry-run', action='store_true')
    return vars(parser.parse_args(argv))


def list_existing_bundles(service, package, edit_id):
    response = service.edits().bundles().list(
        packageName=package, editId=edit_id
    ).execute(num_retries=API_NUM_RETRIES)
    return {int(bundle['versionCode']): (bundle.get('sha256') or '').lower()
            for bundle in response.get('bundles', [])}


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


def load_listing_module():
    """The listing publisher, loaded by path because its file name carries hyphens."""
    path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'publish-play-listing.py')
    spec = importlib.util.spec_from_file_location('publish_play_listing', path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def path_in(base, value):
    if not isinstance(value, str) or not value.strip():
        raise ValueError("Expected a file or directory path in the release plan.")
    return (base / value).resolve()


def release_notes(package, version, notes_file, fallback):
    if notes_file:
        notes = read_notes_file(notes_file)
        if not notes:
            raise ValueError(f"Notes file holds no release text: {notes_file}")
        print(f"Release notes for {version}: {notes_file}")
        return notes
    if package != PACKAGE_NAME:
        raise ValueError(f"Package {package} requires its own release notes file.")

    notes = get_release_notes(version)
    if not notes and fallback is not None:
        notes = get_release_notes(fallback)
        if notes:
            print(f"Release notes for {version}: fallback changelog {fallback}")
    return notes


def prepare_release(entry, package, base, batch):
    if not isinstance(entry, dict) or set(entry) - {
        'track', 'status', 'aab', 'versionCode', 'versionName', 'notesFile', 'notesVersionCode'
    }:
        raise ValueError("Each batch entry must describe one track of the application.")
    track = entry.get('track')
    status = entry.get('status', 'completed')
    if not isinstance(track, str) or not re.fullmatch(r'\S+', track):
        raise ValueError("A release needs a track without whitespace.")
    if status not in ('draft', 'completed'):
        raise ValueError(f"Unsupported track status for {track}: {status}")

    path = path_in(base, entry.get('aab'))
    if not path.is_file():
        raise ValueError(f"AAB file not found: {path}")
    expected = entry.get('versionCode')
    if (batch or expected is not None) and (type(expected) is not int or expected <= 0):
        raise ValueError(f"Track {track} requires a positive integer versionCode.")

    version_name = entry.get('versionName')
    if batch and not version_name:
        raise ValueError(f"Track {track} requires the artifact's own versionName.")
    if not batch and (expected is None or version_name is None):
        element = read_bundle_metadata_element(path)
        if element:
            metadata_code = int(element['versionCode'])
            if expected is None:
                expected = metadata_code
            if version_name is None and expected == metadata_code:
                version_name = element.get('versionName')
    if expected is None or expected <= 0:
        raise ValueError(f"Track {track} needs version metadata for this artifact or --version-code.")
    if version_name is not None and (
        not isinstance(version_name, str) or not version_name.strip()
        or '\r' in version_name or '\n' in version_name
    ):
        raise ValueError(f"Invalid versionName for track {track}.")

    notes_file = path_in(base, entry['notesFile']) if entry.get('notesFile') is not None else None
    fallback = entry.get('notesVersionCode')
    if fallback is not None and (type(fallback) is not int or fallback <= 0):
        raise ValueError(f"Invalid notesVersionCode for track {track}.")
    notes = release_notes(package, expected, notes_file, fallback)

    body = {'versionCodes': [str(expected)], 'status': status}
    if version_name:
        body['name'] = version_name
    if notes:
        body['releaseNotes'] = notes
    operation = {
        'track': track, 'status': status, 'versionCode': expected, 'versionName': version_name,
        'aabSha256': file_sha256(path),
        'releaseNotesSha256': json_sha256(notes),
        'releaseNotesLanguages': [note['language'] for note in notes],
    }
    return {'path': path, 'body': body, 'operation': operation}


def prepare_plan(args):
    if args['batch']:
        if any(args[key] is not None for key in (
            'track', 'status', 'package_name', 'aab', 'version_code', 'version_name',
            'notes_code', 'notes_file', 'listing_root'
        )):
            raise ValueError("--batch supplies the package, tracks, artifacts, notes and listing by itself.")
        batch = Path(args['batch']).resolve()
        plan = read_object(batch)
        if set(plan) - {'packageName', 'releases', 'listingRoot'}:
            raise ValueError("A batch accepts packageName, releases and an optional listingRoot.")
        base = batch.parent
    else:
        batch = None
        base = Path.cwd()
        if args['aab'] and args['version_code'] is None:
            raise ValueError("--version-code is required with --aab.")
        plan = {
            'packageName': args['package_name'] or PACKAGE_NAME,
            'listingRoot': args['listing_root'],
            'releases': [{
                'track': args['track'] or 'production', 'status': args['status'] or 'completed',
                'aab': args['aab'] or AAB_PATH, 'versionCode': args['version_code'],
                'versionName': args['version_name'], 'notesFile': args['notes_file'],
                'notesVersionCode': args['notes_code'],
            }],
        }

    package = package_id(plan.get('packageName') or '')
    entries = plan.get('releases')
    if not isinstance(entries, list) or not entries:
        raise ValueError("A batch must contain at least one release track.")
    releases = [prepare_release(entry, package, base, bool(batch)) for entry in entries]
    operations = [release['operation'] for release in releases]
    if len({operation['track'] for operation in operations}) != len(operations):
        raise ValueError("A batch can update each track only once.")
    identities = {}
    for operation in operations:
        code = operation['versionCode']
        identity = (operation['aabSha256'], operation['versionName'])
        if identities.setdefault(code, identity) != identity:
            raise ValueError(f"VersionCode {code} refers to conflicting artifact identities.")

    listing_module = listings = None
    if plan.get('listingRoot') is not None:
        listing_module = load_listing_module()
        listings = listing_module.prepare_listing(str(path_in(base, plan['listingRoot'])))
        operations.append(listing_module.listing_operation(listings))
    return package, releases, listing_module, listings, operations


def upload_bundle(service, package, edit_id, release):
    operation = release['operation']
    path = release['path']
    if file_sha256(path) != operation['aabSha256']:
        raise ValueError(f"AAB changed after preflight: {path}")
    print(f"Uploading AAB {operation['versionCode']} with resumable upload.")
    with path.open('rb') as stream:
        media = MediaIoBaseUpload(stream, mimetype='application/octet-stream', resumable=True)
        request = service.edits().bundles().upload(packageName=package, editId=edit_id, media_body=media)
        response = None
        while response is None:
            progress, response = request.next_chunk(num_retries=API_NUM_RETRIES)
            if progress:
                print(f"  Uploaded: {progress.progress() * 100:.1f}%")

    version = operation['versionCode']
    if int(response['versionCode']) != version:
        raise ValueError(f"Play uploaded versionCode {response['versionCode']}, but the plan expects {version}.")
    if (response.get('sha256') or '').lower() != operation['aabSha256']:
        raise ValueError(f"Play returned a different SHA-256 for AAB {version}.")


def stage_releases(service, package, edit_id, releases):
    bundles = list_existing_bundles(service, package, edit_id)
    for release in releases:
        operation = release['operation']
        code = operation['versionCode']
        if code in bundles and bundles[code] != operation['aabSha256']:
            raise ValueError(f"Play library versionCode {code} does not match the planned AAB SHA-256.")

    for release in releases:
        operation = release['operation']
        code = operation['versionCode']
        digest = operation['aabSha256']
        if code in bundles:
            print(f"Attaching verified library bundle {code}.")
        else:
            upload_bundle(service, package, edit_id, release)
            bundles[code] = digest

    for release in releases:
        operation = release['operation']
        track = operation['track']
        service.edits().tracks().update(
            packageName=package, editId=edit_id, track=track,
            body={'track': track, 'releases': [release['body']]}
        ).execute(num_retries=API_NUM_RETRIES)
        print(f"Staged versionCode {operation['versionCode']} on {track} as {operation['status']}.")


def main(argv=None):
    args = parse_args(argv)
    try:
        package, releases, listing_module, listings, operations = prepare_plan(args)
        print(f"Release plan for {package}")
        print(f"Planned operations: {json.dumps(operations)}")
        if args['dry_run']:
            print("Dry run completed without an API call.")
            return 0

        with publishing_edit(package, operations, args['preflight'], connect_publisher) as (
            service, edit, root, evidence, http
        ):
            stage_releases(service, package, edit['id'], releases)
            if listing_module is not None:
                listing_module.stage_listing(service, package, edit['id'], listings)
            validate_edit(service, package, edit['id'])
            held = commit_edit(service, package, edit, root, evidence, http)

        print(f"Edit committed for {package} (held={int(held)}). Verify disposition in Publishing overview.")
        for release in releases:
            operation = release['operation']
            name = operation['versionName'] or ''
            print(f"PLAY-COMMITTED versionCode={operation['versionCode']} versionName={name} "
                  f"package={package} track={operation['track']} status={operation['status']} held={int(held)}")
        return 0
    except PlayStop as exc:
        print(f"Google Play stopped: {exc}", file=sys.stderr)
        return 2
    except Exception as exc:
        if _is_transient(exc):
            print(f"Could not verify the release because the Play request failed transiently: {exc}",
                  file=sys.stderr)
            return 2
        print(f"Release rejected: {exc}", file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
