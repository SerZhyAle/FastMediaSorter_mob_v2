"""Read the live Google Play track state without mutating anything.

The Play Developer API has no read path that does not open an edit, so this script opens one,
lists the tracks, and deletes the edit in a finally block. It never uploads, never updates a
track and never commits - the delete is what makes an aborted run harmless.

Emits one JSON object on stdout:

    {
      "package": "com.sza.fastmediasorter",
      "tracks": [{"track": "production", "releases": [{"status": .., "versionCodes": [..],
                                                       "name": .., "releaseNotesLanguages": [..]}]}, ..],
      "phone_production": {"version_code": 260825013, "version_name": "2.60.8250.134"},
      "wear_production":  {"version_code": 26082501,  "version_name": "2.60.8250.134"}
    }

The two convenience members carry the completed release of `production` / `wear:production`,
or null when that track holds none. `version_name` is the release's `name` field, which is the
versionName the store shows; a release that states no name reports null.

`releaseNotesLanguages` lists the language code of each release-notes block exactly as Play stores
it, in stored order. It answers GOOGLE-PLAY rule 26: a release sent through the API under `uk-UA`
reads back as `uk-UA` or as `uk`, and the codes are the only part of the notes this reader emits.

Exit codes:
  0 - state read and printed
  2 - could not read: key file missing, credentials refused, or the API call failed
"""
import argparse
import json
import os
import sys
from play_publishing import probe_edit

PACKAGE_NAME = "com.sza.fastmediasorter"
KEY_PATH = os.path.join(".secrets", "play-console-key.json")

PHONE_TRACK = "production"
WEAR_TRACK = "wear:production"


def parse_args(argv):
    parser = argparse.ArgumentParser(description="Read Google Play track state (read-only).")
    parser.add_argument("--package", default=PACKAGE_NAME)
    parser.add_argument("--key", default=KEY_PATH)
    return parser.parse_args(argv)


def summarise_release(release):
    """The highest versionCode of a release, plus the name the store shows for it."""
    codes = [int(code) for code in release.get("versionCodes", []) or []]
    return {
        "version_code": max(codes) if codes else None,
        "version_name": release.get("name"),
    }


def completed_release(tracks, track_name):
    for track in tracks:
        if track.get("track") != track_name:
            continue
        for release in track.get("releases", []) or []:
            if release.get("status") == "completed":
                return summarise_release(release)
    return None


def main(argv):
    args = parse_args(argv)
    try:
        with probe_edit(args.package, args.key) as (service, edit_id):
            payload = read_tracks(service, args.package, edit_id)
    except Exception as exc:
        print(f"read-play-tracks: could not read tracks ({exc}).", file=sys.stderr)
        return 2
    print(json.dumps(payload, indent=2))
    return 0


def read_tracks(service, package, edit_id):
    listed = service.edits().tracks().list(packageName=package, editId=edit_id).execute()
    tracks = listed.get("tracks", []) or []
    return {
        "package": package,
        "tracks": [
            {
                "track": track.get("track"),
                "releases": [
                    {
                        "status": release.get("status"),
                        "versionCodes": release.get("versionCodes"),
                        "name": release.get("name"),
                        "releaseNotesLanguages": [
                            note.get("language") for note in (release.get("releaseNotes", []) or [])
                        ],
                    }
                    for release in (track.get("releases", []) or [])
                ],
            }
            for track in tracks
        ],
        "phone_production": completed_release(tracks, PHONE_TRACK),
        "wear_production": completed_release(tracks, WEAR_TRACK),
    }


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
