import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import play_publishing as play

SPEC = importlib.util.spec_from_file_location("play_release", SCRIPTS / "publish-play-release.py")
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class BatchTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.package = release.PACKAGE_NAME
        self.phone = self.root / "phone.aab"
        self.wear = self.root / "wear.aab"
        self.phone.write_bytes(b"phone")
        self.wear.write_bytes(b"wear")
        (self.root / "phone-notes.txt").write_text("<en-US>Phone notes</en-US>", encoding="utf-8")
        (self.root / "wear-notes.txt").write_text("<uk-UA>Wear notes</uk-UA>", encoding="utf-8")
        locale = self.root / "listing" / "en-US"
        (locale / "images").mkdir(parents=True)
        for file, text in {"title.txt": "New title", "short_description.txt": "Same",
                           "full_description.txt": "Same full"}.items():
            (locale / file).write_text(text, encoding="utf-8")
        self.icon = locale / "images" / "icon.png"
        self.icon.write_bytes(b"same image")
        self.releases = [
            {"track": "production", "aab": "phone.aab", "versionCode": 7,
             "versionName": "2.7 phone", "notesFile": "phone-notes.txt"},
            {"track": "wear:production", "aab": "wear.aab", "versionCode": 8,
             "versionName": "2.8 wear", "status": "draft", "notesFile": "wear-notes.txt"},
            {"track": "internal", "aab": "wear.aab", "versionCode": 8,
             "versionName": "2.8 wear", "notesFile": "wear-notes.txt"},
        ]
        self.batch = self.root / "batch.json"
        self.preflight = self.root / "preflight.json"
        self.argv = ["--batch", str(self.batch), "--preflight", str(self.preflight)]
        self.write_batch()
        self.api = MagicMock()
        self.edits = self.api.edits.return_value
        self.bundles = self.edits.bundles.return_value
        self.tracks = self.edits.tracks.return_value
        self.listings = self.edits.listings.return_value
        self.images = self.edits.images.return_value
        self.edits.insert.return_value.execute.return_value = {"id": "edit-batch"}
        self.edits.commit.return_value.execute.return_value = {"id": "edit-batch"}
        self.bundles.list.return_value.execute.return_value = {
            "bundles": [{"versionCode": 7, "sha256": play.file_sha256(self.phone)}]
        }
        self.bundles.upload.return_value.next_chunk.return_value = (
            None, {"versionCode": 8, "sha256": play.file_sha256(self.wear)}
        )
        self.listings.list.return_value.execute.return_value = {"listings": [
            {"language": "en-US", "title": "Old", "shortDescription": "Same",
             "fullDescription": "Same full", "video": "existing video"}
        ]}
        self.images.list.return_value.execute.return_value = {
            "images": [{"sha256": play.file_sha256(self.icon)}]
        }

    def write_batch(self, **overrides):
        self.batch.write_text(json.dumps({
            "packageName": self.package, "releases": self.releases, "listingRoot": "listing",
            **overrides,
        }), encoding="utf-8")

    def run_release(self, argv=None, approve=True):
        argv = argv or self.argv
        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            if approve:
                *_, operations = release.prepare_plan(release.parse_args(argv))
                self.preflight.write_text(json.dumps({
                    "packageName": self.package, "authorization": "offline owner order",
                    "acceptedArtifact": "prior accepted version 6", "manifestComparison": "checked",
                    "newForegroundServices": {}, "operations": operations,
                }), encoding="utf-8")
            with patch.object(play, "state_root", return_value=self.root / "state"), \
                    patch.object(play, "confirm_console") as confirm, \
                    patch.object(release, "connect_publisher", return_value=(self.api, None)) as connect:
                result = release.main(argv)
        return result, stdout.getvalue(), stderr.getvalue(), connect, confirm

    def test_one_edit_one_commit_for_all_tracks_and_listing_delta(self):
        code, out, err, connect, confirm = self.run_release()
        self.assertEqual(code, 0, err)
        connect.assert_called_once_with(self.package)
        self.edits.insert.assert_called_once_with(packageName=self.package, body={})
        self.edits.commit.assert_called_once_with(packageName=self.package, editId="edit-batch",
                                                 changesInReviewBehavior=play.GUARD)
        self.edits.commit.return_value.execute.assert_called_once_with(http=None)
        confirm.assert_called_once()
        self.edits.delete.assert_not_called()
        self.bundles.upload.assert_called_once()
        self.bundles.upload.return_value.next_chunk.assert_called_once_with(num_retries=play.API_NUM_RETRIES)
        changes = [call.kwargs for call in self.tracks.update.call_args_list]
        self.assertEqual([call["track"] for call in changes], ["production", "wear:production", "internal"])
        for update, expected in zip(changes, self.releases):
            self.assertEqual((update["packageName"], update["editId"]), (self.package, "edit-batch"))
            staged = update["body"]["releases"][0]
            self.assertEqual(staged["versionCodes"], [str(expected["versionCode"])])
            self.assertEqual(staged["name"], expected["versionName"])
            self.assertEqual(staged["status"], expected.get("status", "completed"))
            notes = Path(self.root / expected["notesFile"])
            self.assertEqual(staged["releaseNotes"], release.read_notes_file(notes))
        self.listings.patch.assert_called_once_with(packageName=self.package, editId="edit-batch",
                                                   language="en-US", body={"title": "New title"})
        self.listings.update.assert_not_called()
        self.images.deleteall.assert_not_called()
        self.assertEqual(out.count("PLAY-COMMITTED"), 3)

    def test_library_mismatch_stops_before_any_payload(self):
        self.bundles.list.return_value.execute.return_value = {
            "bundles": [{"versionCode": 8, "sha256": "other bytes"}]
        }
        code, _, err, _, _ = self.run_release()
        self.assertEqual(code, 1, err)
        self.assertIn("does not match the planned AAB SHA-256", err)
        self.bundles.upload.assert_not_called()
        self.tracks.update.assert_not_called()
        self.edits.commit.assert_not_called()
        self.edits.delete.assert_called_once_with(packageName=self.package, editId="edit-batch")

    def test_upload_version_mismatch_stops_before_any_track_update(self):
        self.bundles.upload.return_value.next_chunk.return_value = (
            None, {"versionCode": 9, "sha256": play.file_sha256(self.wear)}
        )
        code, _, err, _, _ = self.run_release()
        self.assertEqual(code, 1, err)
        self.assertIn("expects 8", err)
        self.tracks.update.assert_not_called()
        self.edits.commit.assert_not_called()

    def test_missing_first_use_evidence_and_dry_run_never_open_an_edit(self):
        code, _, err, connect, _ = self.run_release(approve=False)
        self.assertEqual(code, 2, err)
        connect.assert_not_called()
        code, out, err, connect, _ = self.run_release(self.argv + ["--dry-run"], approve=False)
        self.assertEqual(code, 0, err)
        self.assertIn("Planned operations", out)
        connect.assert_not_called()

    def test_duplicate_tracks_and_cross_app_entries_are_rejected_locally(self):
        for new_entry in [self.releases[0], {**self.releases[0], "packageName": "com.other.app"}]:
            with self.subTest(entry=new_entry):
                self.write_batch(releases=[*self.releases, new_entry])
                code, _, err, connect, _ = self.run_release(approve=False)
                self.assertEqual(code, 1, err)
                connect.assert_not_called()


if __name__ == "__main__":
    unittest.main()
