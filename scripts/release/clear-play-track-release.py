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
from play_publishing import commit_edit, probe_edit, publishing_edit

PACKAGE_NAME = "com.sza.fastmediasorter"
KEY_PATH = os.path.join(".secrets", "play-console-key.json")


def parse_args(argv):
    parser = argparse.ArgumentParser(description="Clear the release records of one Play track.")
    parser.add_argument("--track", required=True, help="Track name, e.g. wear:internal.")
    parser.add_argument("--package", default=PACKAGE_NAME)
    parser.add_argument("--key", default=KEY_PATH, help="Legacy key for read-only previews.")
    parser.add_argument("--preflight", help="Approved preflight JSON required for writes.")
    parser.add_argument("--allow-non-draft", action="store_true",
                        help="Also clear a track whose releases are not all drafts.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Report what would be cleared, then delete the edit unchanged.")
    return parser.parse_args(argv)


def clear_track(service, edit_id, args):
    track = service.edits().tracks().get(
        packageName=args.package, editId=edit_id, track=args.track
    ).execute()
    releases = track.get("releases", []) or []
    print(f"Track '{args.track}' before: {json.dumps(releases)}")
    if not releases:
        print(f"'{args.track}' already holds no release records.")
        return 3

    non_draft = [release.get("status") for release in releases if release.get("status") != "draft"]
    if non_draft and not args.allow_non_draft:
        print(f"Refused: '{args.track}' carries {non_draft}. Pass --allow-non-draft only "
              "when clearing those statuses is authorized.", file=sys.stderr)
        return 1
    if args.dry_run:
        print(f"Would clear {len(releases)} release record(s).")
        return 0
    service.edits().tracks().update(
        packageName=args.package, editId=edit_id, track=args.track,
        body={"track": args.track, "releases": []}
    ).execute()
    return 0


def main(argv):
    args = parse_args(argv)
    operations = [{"clearTrack": args.track, "allowNonDraft": args.allow_non_draft}]
    print(f"Planned operations: {json.dumps(operations)}")
    try:
        if args.dry_run:
            with probe_edit(args.package, args.key) as (service, edit_id):
                return clear_track(service, edit_id, args)
        with publishing_edit(args.package, operations, args.preflight) as (service, edit, root, evidence, http):
            result = clear_track(service, edit["id"], args)
            if result:
                return result
            held = commit_edit(service, args.package, edit, root, evidence, http)
        print(f"Committed track clear for '{args.track}' (held={int(held)}).")
        return 0
    except Exception as exc:
        print(f"clear-play-track-release: stopped ({exc}).", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
