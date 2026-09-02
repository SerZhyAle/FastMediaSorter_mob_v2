"""Remove the release records from one Google Play track.

The Play Developer API offers no "cancel" and no "discard changes" - the only way to take a
release record off a track is to update that track with an empty release list. This script does
exactly that for a single named track and nothing else: it never uploads a bundle, never touches
another track, and never edits a listing.

It refuses by default unless every release on the target track has status `draft`. A draft was
never submitted and never distributed, so removing it costs nothing; a `completed` or
`inProgress` release is live or in review, and taking it off a track is not the same action as
cancelling it in the Console. Pass --allow-non-draft to override, deliberately.

Exit codes:
  0 - the track was cleared and the edit committed
  1 - refused: the track carries a non-draft release and --allow-non-draft was not given
  2 - could not verify: key file missing, credentials refused, or the API call failed
  3 - nothing to do: the track already holds no release records
"""
import argparse
import json
import os
import sys

PACKAGE_NAME = "com.sza.fastmediasorter"
KEY_PATH = os.path.join(".secrets", "play-console-key.json")
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def parse_args(argv):
    parser = argparse.ArgumentParser(description="Clear the release records of one Play track.")
    parser.add_argument("--track", required=True, help="Track name, e.g. wear:internal.")
    parser.add_argument("--package", default=PACKAGE_NAME)
    parser.add_argument("--key", default=KEY_PATH)
    parser.add_argument("--allow-non-draft", action="store_true",
                        help="Also clear a track whose releases are not all drafts.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Report what would be cleared, then delete the edit unchanged.")
    return parser.parse_args(argv)


def main(argv):
    args = parse_args(argv)

    if not os.path.exists(args.key):
        print(f"clear-play-track-release: service-account key not found at '{args.key}'.", file=sys.stderr)
        return 2

    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
    except ImportError as exc:
        print(f"clear-play-track-release: Google API client unavailable ({exc}).", file=sys.stderr)
        return 2

    try:
        creds = service_account.Credentials.from_service_account_file(args.key, scopes=[SCOPE])
        service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
        edit_id = service.edits().insert(packageName=args.package, body={}).execute()["id"]
    except Exception as exc:
        print(f"clear-play-track-release: could not open a Play edit ({exc}).", file=sys.stderr)
        return 2

    committed = False
    try:
        try:
            track = service.edits().tracks().get(
                packageName=args.package, editId=edit_id, track=args.track).execute()
        except Exception as exc:
            print(f"clear-play-track-release: could not read track '{args.track}' ({exc}).", file=sys.stderr)
            return 2

        releases = track.get("releases", []) or []
        print(f"Track '{args.track}' before: {json.dumps(releases)}")

        if not releases:
            print(f"clear-play-track-release: '{args.track}' already holds no release records.")
            return 3

        non_draft = [r.get("status") for r in releases if r.get("status") != "draft"]
        if non_draft and not args.allow_non_draft:
            print(f"clear-play-track-release: refused - '{args.track}' carries {non_draft}, "
                  "not drafts. Re-run with --allow-non-draft only if that is the intent.",
                  file=sys.stderr)
            return 1

        if args.dry_run:
            print(f"clear-play-track-release: dry run - would clear {len(releases)} release record(s).")
            return 0

        try:
            service.edits().tracks().update(
                packageName=args.package, editId=edit_id, track=args.track,
                body={"track": args.track, "releases": []}).execute()
            # While the app carries a policy rejection Play refuses to open a review from the API,
            # and the retry is what the other publishers here already do (publish-play-release.py).
            try:
                service.edits().commit(packageName=args.package, editId=edit_id).execute()
            except Exception as exc:
                if "changesNotSentForReview" not in str(exc):
                    raise
                print("clear-play-track-release: Play refused an automatic review - committing with "
                      "changesNotSentForReview, so the change waits in Publishing overview.")
                service.edits().commit(
                    packageName=args.package, editId=edit_id, changesNotSentForReview=True).execute()
            committed = True
        except Exception as exc:
            print(f"clear-play-track-release: could not clear '{args.track}' ({exc}).", file=sys.stderr)
            return 2

        print(f"clear-play-track-release: '{args.track}' cleared - {len(releases)} release record(s) removed.")
        return 0
    finally:
        # A committed edit no longer exists; deleting it would fail and mean nothing.
        if not committed:
            try:
                service.edits().delete(packageName=args.package, editId=edit_id).execute()
            except Exception as exc:
                print(f"clear-play-track-release: warning - edit {edit_id} was not deleted ({exc}).",
                      file=sys.stderr)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
