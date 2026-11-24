"""Remove the draft release records from one Google Play track.

The Play Developer API offers no "cancel" and no "discard changes". Updating a track with an empty
release list removes its `draft` releases and nothing else: a `completed` record survived the
committed edit every time it was measured (internal 2026-09-12, 2026-11-19 and 2026-11-24;
wear:production 2026-09-12), and each run only queued one more pending change. This script
therefore clears a single named track only when every release on it is a `draft` - never submitted,
never distributed, so removing it costs nothing. Any other status is refused, whatever flag is
passed. It never uploads a bundle, never touches another track, and never edits a listing.

Taking a live release off a track is done by publishing a new `completed` release on that track:
it replaces the old record (Console: Create new release, then Add from library).

Exit codes:
  0 - the track was cleared and the edit committed
  1 - refused: the track carries a release that is not a draft
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
                        help="Retired: Play removes drafts only, so a non-draft track is refused "
                             "whatever this says. Kept so an old invocation reaches the refusal.")
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
    if non_draft:
        print(f"Refused: '{args.track}' carries {non_draft}. An empty-release update removes "
              "drafts only; a committed clear left such a record in place every time it was "
              "measured. To take a live release off the track, publish a new completed release "
              "on it (Console: Create new release, then Add from library) - it replaces the "
              "old record.", file=sys.stderr)
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
    # The key stays in the planned operations: an approved preflight must match them exactly.
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
