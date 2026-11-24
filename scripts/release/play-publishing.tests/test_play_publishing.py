import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import Mock, patch

SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
import play_publishing as play


PACKAGE = "com.example.app"
OPERATIONS = [{"track": "production", "versionCode": 7}]
PREFLIGHT = {
    "packageName": PACKAGE,
    "operations": OPERATIONS,
    "authorization": "release order 7",
    "acceptedArtifact": "accepted version 6",
    "manifestComparison": "merged manifest comparison",
    "newForegroundServices": {},
}


class ApiError(Exception):
    def __init__(self, status, message, reason=""):
        super().__init__(message)
        self.resp = type("Response", (), {"status": status})()
        self.content = json.dumps({"error": {"details": [{"reason": reason}]}}).encode()


class FakeRequest:
    def __init__(self, result):
        self.result = result

    def execute(self, **kwargs):
        if isinstance(self.result, Exception):
            raise self.result
        return self.result


class FakeEdits:
    def __init__(self, *outcomes, edit_id="edit-7"):
        self.outcomes = iter(outcomes)
        self.calls = []
        self.edit_id = edit_id

    def edits(self):
        return self

    def insert(self, **kwargs):
        self.calls.append(("insert", kwargs))
        return FakeRequest({"id": self.edit_id})

    def commit(self, **kwargs):
        self.calls.append(("commit", kwargs))
        return FakeRequest(next(self.outcomes))

    def delete(self, **kwargs):
        self.calls.append(("delete", kwargs))
        return FakeRequest({})

    def get(self, **kwargs):
        self.calls.append(("get", kwargs))
        return FakeRequest(next(self.outcomes))


class SafeguardsTest(unittest.TestCase):
    def setUp(self):
        scratch = tempfile.TemporaryDirectory()
        self.addCleanup(scratch.cleanup)
        self.root = Path(scratch.name)
        self.confirmed = []
        self.pending = play.pending_path(self.root, PACKAGE)

    def run_commit(self, service):
        with play.open_edit(service, PACKAGE, self.root) as edit:
            return play.commit_edit(service, PACKAGE, edit, self.root, PREFLIGHT,
                                    confirm=lambda _, __, mode, ___: self.confirmed.append(mode))

    def test_automatic_and_held_share_the_guard(self):
        refusal = ApiError(400, "Changes cannot be sent for review automatically. "
                           "Set changesNotSentForReview to true.")
        service = FakeEdits(refusal, {"id": "edit-7"})
        self.assertTrue(self.run_commit(service))
        self.assertEqual(self.confirmed, ["automatic", "held"])
        kinds, calls = zip(*service.calls)
        self.assertEqual(kinds, ("insert", "commit", "commit"))
        self.assertEqual([c["changesInReviewBehavior"] for c in calls[1:]], [play.GUARD] * 2)
        self.assertNotIn("changesNotSentForReview", calls[1])
        self.assertIs(calls[2]["changesNotSentForReview"], True)
        self.assertFalse(self.pending.exists())
        with self.assertRaisesRegex(play.PlayStop, "already has a commit receipt"):
            play.ensure_writes_allowed(self.root, PACKAGE, PREFLIGHT)

    def test_automatic_success_sends_once(self):
        service = FakeEdits({"id": "edit-7"})
        self.assertFalse(self.run_commit(service))
        self.assertEqual([kind for kind, _ in service.calls], ["insert", "commit"])

    def test_review_and_wrong_direction_never_fall_back(self):
        review = ApiError(400, "Request rejected", "CHANGES_ALREADY_IN_REVIEW")
        wrong_direction = ApiError(400, "changesNotSentForReview cannot be set to true.")
        unrelated = ApiError(403, "changesNotSentForReview")
        for error, expected in [(review, play.PlayStop), (wrong_direction, ApiError), (unrelated, ApiError)]:
            with self.subTest(error=str(error)):
                service = FakeEdits(error)
                with self.assertRaises(expected):
                    self.run_commit(service)
                self.assertEqual([kind for kind, _ in service.calls], ["insert", "commit", "delete"])
                self.assertFalse(self.pending.exists())

    def test_lost_response_blocks_writes_until_matching_readback(self):
        failures = [TimeoutError("lost response"), ApiError(503, "server disconnected"), {"id": "wrong"}]
        for failure in failures:
            with self.subTest(failure=failure), tempfile.TemporaryDirectory() as directory:
                self.root = Path(directory)
                self.pending = play.pending_path(self.root, PACKAGE)
                service = FakeEdits(failure)
                with self.assertRaisesRegex(play.PlayStop, "outcome unknown"):
                    self.run_commit(service)
                self.assertEqual([kind for kind, _ in service.calls], ["insert", "commit"])
                with self.assertRaisesRegex(play.PlayStop, "unresolved commit"):
                    play.ensure_writes_allowed(self.root, PACKAGE)
                with patch.object(play, "state_root", return_value=self.root):
                    with self.assertRaisesRegex(play.PlayStop, "must match"):
                        play.reconcile(PACKAGE, "other-edit", "not-committed", "independent read")
                    path = play.reconcile(PACKAGE, "edit-7", "not-committed", "Console and tracks")
                self.assertEqual(play.read_object(path)["outcome"], "not-committed")
                play.ensure_writes_allowed(self.root, PACKAGE)

    def test_edit_invalidation_is_a_coordination_stop(self):
        service = FakeEdits(ApiError(400, "This Edit has been deleted."))
        with self.assertRaisesRegex(play.PlayStop, "invalidated"):
            self.run_commit(service)
        self.assertFalse(self.pending.exists())

    def test_probe_preserves_a_still_open_unresolved_edit(self):
        play.save_record(self.pending, {"editId": "edit-7"})
        with patch.object(play, "state_root", return_value=self.root):
            service = FakeEdits({"id": "edit-7"})
            with patch.object(play, "connect_reader", return_value=service):
                with self.assertRaisesRegex(play.PlayStop, "is still open"):
                    with play.probe_edit(PACKAGE, "unused"):
                        self.fail("Probe opened a competing edit")
            self.assertEqual([kind for kind, _ in service.calls], ["get"])
            service = FakeEdits(ApiError(404, "edit closed"), edit_id="new-probe")
            with patch.object(play, "connect_reader", return_value=service):
                with play.probe_edit(PACKAGE, "unused") as (_, edit_id):
                    self.assertEqual(edit_id, "new-probe")
            self.assertEqual([kind for kind, _ in service.calls], ["get", "insert", "delete"])

    def test_failed_cleanup_stops_future_inserts_until_the_old_edit_is_closed(self):
        broken = Mock(return_value=FakeRequest(ApiError(503, "delete unavailable")))
        service = FakeEdits()
        service.delete = broken
        with self.assertRaisesRegex(play.PlayStop, "closure must be verified"):
            with play.open_edit(service, PACKAGE, self.root):
                pass
        self.assertTrue((self.root / f"{PACKAGE}.active.json").exists())
        with self.assertRaisesRegex(play.PlayStop, "closure must be verified"):
            with play.open_edit(service, PACKAGE, self.root):
                self.fail("Inserted while cleanup was blocked")
        self.assertEqual([kind for kind, _ in service.calls], ["insert"])
        recovered = FakeEdits()
        with play.open_edit(recovered, PACKAGE, self.root):
            self.assertEqual([kind for kind, _ in recovered.calls], ["delete", "insert"])

    def test_commit_transport_never_replays_after_connection_loss(self):
        credentials = Mock()
        with patch.object(play, "HTTPSConnection") as server:
            connection = server.return_value
            connection.getresponse.side_effect = ConnectionResetError("response lost")
            with self.assertRaises(ConnectionResetError):
                play.SingleRequestHttp(credentials).request(
                    "https://androidpublisher.googleapis.com/test?guard=1", method="POST"
                )
            connection.request.assert_called_once()
            connection.getresponse.assert_called_once()
            connection.close.assert_called_once()

    def test_publisher_checks_evidence_before_connecting(self):
        with patch.object(play, "connect_publisher") as connector:
            with self.assertRaisesRegex(play.PlayStop, "preflight"):
                with play.publishing_edit(PACKAGE, OPERATIONS, None):
                    self.fail("Opened a Play edit without evidence")
            connector.assert_not_called()

    def test_first_use_stops_before_an_edit(self):
        path = self.root / "preflight.json"
        for key, value in [("acceptedArtifact", ""), ("newForegroundServices", {"mediaPlayback": None}),
                           ("manifestComparison", ""), ("packageName", "com.example.other"),
                           ("operations", []), ("authorization", "")]:
            with self.subTest(key=key):
                path.write_text(json.dumps({**PREFLIGHT, key: value}), encoding="utf-8")
                with self.assertRaises(play.PlayStop):
                    play.load_preflight(path, PACKAGE, OPERATIONS)
        path.write_text(json.dumps(PREFLIGHT), encoding="utf-8")
        self.assertEqual(play.load_preflight(path, PACKAGE, OPERATIONS), PREFLIGHT)

    def test_credentials_require_a_file_outside_git(self):
        repo = self.root / "checkout"
        repo.mkdir()
        (repo / ".git").write_text("gitdir: elsewhere", encoding="utf-8")
        key = repo / "key.json"
        key.touch()
        with patch.dict(os.environ, {"SZA_PLAY_SERVICE_ACCOUNT_JSON": str(key)}):
            with self.assertRaisesRegex(play.PlayStop, "outside all repositories"):
                play.external_key()
        external = self.root / "external.json"
        external.touch()
        with patch.dict(os.environ, {"SZA_PLAY_SERVICE_ACCOUNT_JSON": str(external)}):
            self.assertEqual(play.external_key(), external.resolve())

    def test_app_lock_serializes_another_process_for_the_same_app(self):
        probe = (
            "from pathlib import Path\n"
            "import play_publishing as play\n"
            f"play.state_root = lambda: Path({str(self.root)!r})\n"
            "try:\n"
            "    with play.app_lock('com.example.app'):\n"
            "        pass\n"
            "except play.PlayStop:\n"
            "    with play.app_lock('com.example.other'):\n"
            "        print('busy same app; acquired other app')\n"
        )
        with patch.object(play, "state_root", return_value=self.root), play.app_lock(PACKAGE):
            result = subprocess.run([sys.executable, "-B", "-c", probe], cwd=SCRIPTS,
                                    capture_output=True, text=True, timeout=10, check=True)
        self.assertEqual(result.stdout.strip(), "busy same app; acquired other app")

    def test_lock_refusal_names_the_local_holder_and_the_open_edit(self):
        play.save_record(self.root / f"{PACKAGE}.active.json", {"editId": "05270925241039895275"})
        with patch.object(play, "state_root", return_value=self.root), play.app_lock(PACKAGE):
            with self.assertRaises(play.PlayStop) as refusal:
                with play.app_lock(PACKAGE):
                    pass
        message = str(refusal.exception)
        self.assertIn("local file lock, not a Play-side condition", message)
        self.assertIn(str(self.root / f"{PACKAGE}.lock"), message)
        self.assertIn(f"Holder is running - pid {os.getpid()}", message)
        self.assertIn(sys.argv[0], message)
        self.assertIn("05270925241039895275", message)

    def test_lock_refusal_reads_a_dead_holder_as_stale(self):
        dead = subprocess.Popen([sys.executable, "-B", "-c", "pass"])
        dead.wait()
        with patch.object(play, "state_root", return_value=self.root), play.app_lock(PACKAGE):
            play.save_record(play.holder_path(self.root, PACKAGE),
                             {"pid": dead.pid, "argv": ["gone.py"], "acquiredAt": play.utc_now()})
            with self.assertRaises(play.PlayStop) as refusal:
                with play.app_lock(PACKAGE):
                    pass
        self.assertIn(f"Holder record is stale - pid {dead.pid}", str(refusal.exception))

    def test_lock_refusal_without_a_holder_record_says_the_holder_is_unknown(self):
        with patch.object(play, "state_root", return_value=self.root), play.app_lock(PACKAGE):
            play.holder_path(self.root, PACKAGE).unlink()
            with self.assertRaises(play.PlayStop) as refusal:
                with play.app_lock(PACKAGE):
                    pass
        self.assertIn("No usable holder record", str(refusal.exception))

    def test_holder_record_is_removed_when_the_lock_is_released(self):
        with patch.object(play, "state_root", return_value=self.root):
            with play.app_lock(PACKAGE):
                self.assertTrue(play.holder_path(self.root, PACKAGE).exists())
            self.assertFalse(play.holder_path(self.root, PACKAGE).exists())

    def test_waiting_lock_acquires_once_the_holder_releases(self):
        held, release = threading.Event(), threading.Event()

        def hold():
            with play.app_lock(PACKAGE):
                held.set()
                release.wait(5)

        with patch.object(play, "state_root", return_value=self.root):
            holder = threading.Thread(target=hold)
            holder.start()
            self.assertTrue(held.wait(5))
            threading.Timer(0.5, release.set).start()
            with play.app_lock(PACKAGE, wait=5):
                pass
            holder.join(5)

    def test_zero_wait_lock_refuses_at_once(self):
        with patch.object(play, "state_root", return_value=self.root), play.app_lock(PACKAGE):
            started = time.monotonic()
            with self.assertRaises(play.PlayStop):
                with play.app_lock(PACKAGE):
                    pass
        self.assertLess(time.monotonic() - started, 1)


if __name__ == "__main__":
    unittest.main()
