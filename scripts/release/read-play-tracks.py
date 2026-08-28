"""Read the live Google Play track state without mutating anything.

The Play Developer API has no read path that does not open an edit, so this script opens one,
lists the tracks, and deletes the edit in a finally block. It never uploads, never updates a
track and never commits - the delete is what makes an aborted run harmless.

Emits one JSON object on stdout:

    {
      "package": "com.sza.fastmediasorter",
      "tracks": [{"track": "production", "releases": [{"status": .., "versionCodes": [..],
                                                       "name": ..}]}, ..],
      "phone_production": {"version_code": 260825013, "version_name": "2.60.8250.134"},
      "wear_production":  {"version_code": 26082501,  "version_name": "2.60.8250.134"}
    }

The two convenience members carry the completed release of `production` / `wear:production`,
or null when that track holds none. `version_name` is the release's `name` field, which is the
versionName the store shows; a release that states no name reports null.

Exit codes:
  0 - state read and printed
  2 - could not read: key file missing, credentials refused, or the API call failed
"""
import argparse
import json
import os
import sys

PACKAGE_NAME = "com.sza.fastmediasorter"
KEY_PATH = os.path.join(".secrets", "play-console-key.json")
SCOPE = "https://www.googleapis.com/auth/androidpublisher"

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

    if not os.path.exists(args.key):
        print(f"read-play-tracks: service-account key not found at '{args.key}'.", file=sys.stderr)
        return 2

    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
    except ImportError as exc:
        print(f"read-play-tracks: Google API client unavailable ({exc}).", file=sys.stderr)
        return 2

    try:
        creds = service_account.Credentials.from_service_account_file(args.key, scopes=[SCOPE])
        service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
        edit = service.edits().insert(packageName=args.package, body={}).execute()
        edit_id = edit["id"]
    except Exception as exc:
        print(f"read-play-tracks: could not open a Play edit ({exc}).", file=sys.stderr)
        return 2

    try:
        listed = service.edits().tracks().list(packageName=args.package, editId=edit_id).execute()
        tracks = listed.get("tracks", []) or []
        payload = {
            "package": args.package,
            "tracks": [
                {
                    "track": track.get("track"),
                    "releases": [
                        {
                            "status": release.get("status"),
                            "versionCodes": release.get("versionCodes"),
                            "name": release.get("name"),
                        }
                        for release in (track.get("releases", []) or [])
                    ],
                }
                for track in tracks
            ],
            "phone_production": completed_release(tracks, PHONE_TRACK),
            "wear_production": completed_release(tracks, WEAR_TRACK),
        }
        print(json.dumps(payload, indent=2))
        return 0
    except Exception as exc:
        print(f"read-play-tracks: could not list tracks ({exc}).", file=sys.stderr)
        return 2
    finally:
        # Discarding the edit is the whole reason this script is safe to run at any time.
        try:
            service.edits().delete(packageName=args.package, editId=edit_id).execute()
        except Exception as exc:
            print(f"read-play-tracks: warning - edit {edit_id} was not deleted ({exc}).", file=sys.stderr)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
