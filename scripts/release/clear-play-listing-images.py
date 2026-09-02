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
import os
import sys

PACKAGE_NAME = "com.sza.fastmediasorter"
KEY_PATH = os.path.join(".secrets", "play-console-key.json")
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
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
    parser.add_argument("--key", default=KEY_PATH)
    parser.add_argument("--dry-run", action="store_true",
                        help="Report what would be deleted, then delete the edit unchanged.")
    return parser.parse_args(argv)


def main(argv):
    args = parse_args(argv)

    if not os.path.exists(args.key):
        print(f"clear-play-listing-images: service-account key not found at '{args.key}'.", file=sys.stderr)
        return 2

    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build
    except ImportError as exc:
        print(f"clear-play-listing-images: Google API client unavailable ({exc}).", file=sys.stderr)
        return 2

    try:
        creds = service_account.Credentials.from_service_account_file(args.key, scopes=[SCOPE])
        service = build("androidpublisher", "v3", credentials=creds, cache_discovery=False)
        edit_id = service.edits().insert(packageName=args.package, body={}).execute()["id"]
    except Exception as exc:
        print(f"clear-play-listing-images: could not open a Play edit ({exc}).", file=sys.stderr)
        return 2

    committed = False
    try:
        if args.locales.strip():
            locales = [t.strip() for t in args.locales.split(",") if t.strip()]
        else:
            try:
                listings = service.edits().listings().list(
                    packageName=args.package, editId=edit_id).execute().get("listings", []) or []
                locales = [entry["language"] for entry in listings]
            except Exception as exc:
                print(f"clear-play-listing-images: could not list locales ({exc}).", file=sys.stderr)
                return 2

        found = 0
        for language in locales:
            try:
                images = service.edits().images().list(
                    packageName=args.package, editId=edit_id,
                    language=language, imageType=args.image_type).execute().get("images", []) or []
            except Exception as exc:
                print(f"clear-play-listing-images: could not read {args.image_type} for {language} ({exc}).",
                      file=sys.stderr)
                return 2

            if not images:
                continue
            found += len(images)
            print(f"  {language}: {len(images)} {args.image_type} image(s)")

            if args.dry_run:
                continue
            try:
                service.edits().images().deleteall(
                    packageName=args.package, editId=edit_id,
                    language=language, imageType=args.image_type).execute()
            except Exception as exc:
                print(f"clear-play-listing-images: could not delete {args.image_type} for {language} ({exc}).",
                      file=sys.stderr)
                return 2

        if found == 0:
            print(f"clear-play-listing-images: no {args.image_type} image in any requested locale.")
            return 3

        if args.dry_run:
            print(f"clear-play-listing-images: dry run - would delete {found} image(s).")
            return 0

        try:
            # A standing policy rejection makes Play refuse an automatic review, exactly as it does
            # for the track publisher next door; the change then waits in Publishing overview.
            try:
                service.edits().commit(packageName=args.package, editId=edit_id).execute()
            except Exception as exc:
                if "changesNotSentForReview" not in str(exc):
                    raise
                print("clear-play-listing-images: Play refused an automatic review - committing with "
                      "changesNotSentForReview, so the change waits in Publishing overview.")
                service.edits().commit(
                    packageName=args.package, editId=edit_id, changesNotSentForReview=True).execute()
            committed = True
        except Exception as exc:
            print(f"clear-play-listing-images: could not commit ({exc}).", file=sys.stderr)
            return 2

        print(f"clear-play-listing-images: deleted {found} {args.image_type} image(s) "
              f"across {len(locales)} locale(s).")
        return 0
    finally:
        if not committed:
            try:
                service.edits().delete(packageName=args.package, editId=edit_id).execute()
            except Exception as exc:
                print(f"clear-play-listing-images: warning - edit {edit_id} was not deleted ({exc}).",
                      file=sys.stderr)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
