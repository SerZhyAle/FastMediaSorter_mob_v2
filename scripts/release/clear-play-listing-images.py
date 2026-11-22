"""Remove one image type from the Play store listing, in every locale or in one.

Written for the case the release of 2026-09-02 left standing: a phone-only submission whose
listing still carries Wear OS screenshots, uploaded by publish-play-listing.py in an earlier
window. Deleting them is the only way to take that material out of the listing - the Play
Developer API has no discard, so an image, once uploaded, leaves only by being deleted.

What this does NOT do, and it matters before anyone reaches for it: deleting the images does not
remove the "Change Wear OS screenshots" row from Publishing overview. That row becomes a deletion
instead of an upload, so the batch keeps its size. Use this when the material itself must go, not
to tidy a pending-changes list.

The sources stay in the repository under play/listing/<locale>/images/, so a deletion is
reversible by re-running publish-play-listing.ps1 when the watch is submitted on its own.

Exit codes:
  0 - the images were deleted and the edit committed
  2 - could not verify: key file missing, credentials refused, or the API call failed
  3 - nothing to do: no image of that type exists in any requested locale
"""
import argparse
import json
import os
import sys
from play_publishing import commit_edit, probe_edit, publishing_edit

PACKAGE_NAME = "com.sza.fastmediasorter"
KEY_PATH = os.path.join(".secrets", "play-console-key.json")
IMAGE_TYPES = (
    "phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots",
    "wearScreenshots", "tvScreenshots", "featureGraphic", "icon", "tvBanner",
)


def parse_args(argv):
    parser = argparse.ArgumentParser(description="Delete one image type from the Play listing.")
    parser.add_argument("--image-type", required=True, choices=IMAGE_TYPES)
    parser.add_argument("--locales", default="",
                        help="Comma-separated BCP-47 tags. Empty means every locale the listing has.")
    parser.add_argument("--package", default=PACKAGE_NAME)
    parser.add_argument("--key", default=KEY_PATH, help="Legacy key for read-only previews.")
    parser.add_argument("--preflight", help="Approved preflight JSON required for writes.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Report what would be deleted, then delete the edit unchanged.")
    return parser.parse_args(argv)


def clear_images(service, edit_id, args, locales):
    if not locales:
        listings = service.edits().listings().list(
            packageName=args.package, editId=edit_id
        ).execute().get("listings", []) or []
        locales = [entry["language"] for entry in listings]

    found = 0
    for language in locales:
        images = service.edits().images().list(
            packageName=args.package, editId=edit_id, language=language, imageType=args.image_type
        ).execute().get("images", []) or []
        if not images:
            continue
        found += len(images)
        print(f"  {language}: {len(images)} {args.image_type} image(s)")
        if not args.dry_run:
            service.edits().images().deleteall(
                packageName=args.package, editId=edit_id, language=language, imageType=args.image_type
            ).execute()
    return found


def main(argv):
    args = parse_args(argv)
    locales = [tag.strip() for tag in args.locales.split(",") if tag.strip()]
    operations = [{"clearImageType": args.image_type, "locales": locales}]
    print(f"Planned operations: {json.dumps(operations)}")
    try:
        if args.dry_run:
            with probe_edit(args.package, args.key) as (service, edit_id):
                count = clear_images(service, edit_id, args, locales)
                print(f"Would delete {count} {args.image_type} image(s).")
                return 0 if count else 3
        with publishing_edit(args.package, operations, args.preflight) as (service, edit, root, evidence, http):
            count = clear_images(service, edit["id"], args, locales)
            if not count:
                print(f"No {args.image_type} images found.")
                return 3
            held = commit_edit(service, args.package, edit, root, evidence, http)
        print(f"Committed the deletion of {count} {args.image_type} image(s) (held={int(held)}).")
        return 0
    except Exception as exc:
        print(f"clear-play-listing-images: stopped ({exc}).", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
