"""Fixture tests for scripts/metrics/ticket_cost.py (S3147).

Run: python -m unittest discover -s scripts/metrics/tests
"""
import contextlib
import io
import json
import os
import shutil
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

import ticket_cost as tc  # noqa: E402

FIX = os.path.join(HERE, "fixtures")
ROOT = "P:/repo"


def temp_dir(test):
    path = tempfile.mkdtemp()
    test.addCleanup(shutil.rmtree, path, True)
    return path


class ClaudeTests(unittest.TestCase):
    def test_window_dedup_and_fields(self):
        fields, lists = tc.extract_claude(os.path.join(FIX, "claude.jsonl"), "S3147", ROOT).row()
        self.assertEqual(fields["model"], "claude-opus-5")
        self.assertEqual(fields["turns"], 6)
        self.assertEqual(fields["inputFresh"], 11)
        self.assertEqual(fields["cacheRead"], 15000)
        self.assertEqual(fields["cacheCreate"], 50)
        self.assertEqual(fields["output"], 29)
        self.assertEqual(fields["contextPeak"], 3000)
        self.assertEqual(fields["compactions"], 1)
        self.assertEqual(fields["toolCalls"], 6)
        self.assertEqual(fields["hardFails"], 2)
        self.assertEqual(fields["readsBeforeFirstEdit"], 2)
        self.assertEqual(fields["filesRead"], 2)
        self.assertEqual(fields["filesEdited"], 1)
        self.assertEqual(fields["readCharsNotEdited"], 60)
        self.assertEqual(fields["maxFailStreak"], 2)
        self.assertIn("post-change.ps1", fields["streakCommand"])
        self.assertEqual(lists["edited"], ["a.md"])
        self.assertEqual(lists["readNotEdited"], [("b.md", 60)])

    def test_subagent_tier_is_added(self):
        tmp = temp_dir(self)
        main = os.path.join(tmp, "sess.jsonl")
        shutil.copy(os.path.join(FIX, "claude.jsonl"), main)
        sub = os.path.join(tmp, "sess", "subagents")
        os.makedirs(sub)
        record = {"type": "assistant", "timestamp": "2026-09-15T10:05:00.000Z", "requestId": "sub1",
                  "message": {"model": "claude-sonnet-5", "content": [],
                              "usage": {"input_tokens": 0, "output_tokens": 0,
                                        "cache_read_input_tokens": 500, "cache_creation_input_tokens": 0}}}
        with open(os.path.join(sub, "agent-1.jsonl"), "w", encoding="utf-8") as fh:
            fh.write(json.dumps(record) + "\n")
        fields, _ = tc.extract_claude(main, "S3147", ROOT).row()
        self.assertEqual(fields["cacheRead"], 15500)
        self.assertEqual(fields["contextPeak"], 3000)

    def test_find_claude_matches_project_dir_case_insensitively(self):
        home = temp_dir(self)
        project = os.path.join(home, ".claude", "projects", "p--REPO")
        os.makedirs(project)
        path = os.path.join(project, "sid.jsonl")
        open(path, "w", encoding="utf-8").close()
        self.assertEqual(tc.find_claude(home, "P:\\repo", "sid"), path)


class CodexTests(unittest.TestCase):
    def test_tokens_patch_edit_and_shell_read(self):
        fields, lists = tc.extract_codex(os.path.join(FIX, "codex.jsonl"), "S3147", ROOT).row()
        self.assertEqual(fields["model"], "gpt-5")
        self.assertEqual(fields["turns"], 2)
        self.assertEqual(fields["inputFresh"], 500)
        self.assertEqual(fields["cacheRead"], 2000)
        self.assertEqual(fields["output"], 80)
        self.assertEqual(fields["contextPeak"], 1500)
        self.assertEqual(fields["toolCalls"], 4)
        self.assertEqual(fields["hardFails"], 1)
        self.assertEqual(fields["readsBeforeFirstEdit"], 1)
        self.assertEqual(fields["readCharsNotEdited"], 11)
        # docs/w.md comes from a patch embedded in an exec-tool JavaScript string.
        self.assertEqual(lists["edited"], ["docs/w.md", "scripts/a.ps1"])
        self.assertEqual(fields["maxFailStreak"], 1)


class GeminiTests(unittest.TestCase):
    def test_reads_edits_and_null_tokens(self):
        fields, lists = tc.extract_gemini(os.path.join(FIX, "gemini.jsonl"), "S3147", ROOT).row()
        self.assertEqual(fields["turns"], 2)
        self.assertIsNone(fields["cacheRead"])
        self.assertEqual(fields["toolCalls"], 3)
        self.assertEqual(fields["hardFails"], 1)
        self.assertEqual(fields["readsBeforeFirstEdit"], 1)
        self.assertEqual(fields["readCharsNotEdited"], 10)
        self.assertEqual(lists["edited"], ["docs/z.md"])


class LedgerTests(unittest.TestCase):
    def test_record_marks_repeat_entry_and_writes_map(self):
        tmp = temp_dir(self)
        ledger = os.path.join(tmp, "agent-cost", "ticket-cost.jsonl")
        map_dir = os.path.join(tmp, "S3147")
        base = ["record", "--ticket", "S3147", "--runtime", "claude", "--repo-root", tmp,
                "--ledger", ledger, "--map-dir", map_dir,
                "--transcript", os.path.join(FIX, "claude.jsonl")]
        self.assertEqual(tc.main(base + ["--session", "one"]), 0)
        self.assertEqual(tc.main(base + ["--session", "two"]), 0)
        self.assertEqual(tc.main(base + ["--session", "one"]), 0)
        with open(ledger, encoding="utf-8") as fh:
            rows = [json.loads(line) for line in fh]
        self.assertEqual([r["entry"] for r in rows], ["first", "repeat", "first"])
        self.assertTrue(os.path.isfile(os.path.join(map_dir, "context-map.md")))

    def test_unknown_runtime_writes_unavailable_row(self):
        tmp = temp_dir(self)
        ledger = os.path.join(tmp, "ticket-cost.jsonl")
        code = tc.main(["record", "--ticket", "S3147", "--runtime", "zcode", "--repo-root", tmp,
                        "--ledger", ledger, "--map-dir", os.path.join(tmp, "S3147"), "--home", tmp])
        self.assertEqual(code, 0)
        with open(ledger, encoding="utf-8") as fh:
            row = json.loads(fh.readline())
        self.assertEqual(row["transcript"], "unavailable")

    def test_map_marks_changed_file(self):
        tmp = temp_dir(self)
        with open(os.path.join(tmp, "a.md"), "w", encoding="utf-8") as fh:
            fh.write("one")
        row = {"ticket": "S3147", "recordedAt": "t", "runtime": "claude", "sessionId": "s", "entry": "first"}
        path = os.path.join(tmp, "context-map.md")
        tc.write_map(path, row, {"edited": ["a.md"], "readNotEdited": [], "failing": []}, tmp)
        argv = ["map", "--map", path, "--repo-root", tmp]
        before = io.StringIO()
        with contextlib.redirect_stdout(before):
            self.assertEqual(tc.main(argv), 0)
        self.assertNotIn("(changed)", before.getvalue())
        with open(os.path.join(tmp, "a.md"), "w", encoding="utf-8") as fh:
            fh.write("two")
        after = io.StringIO()
        with contextlib.redirect_stdout(after):
            self.assertEqual(tc.main(argv), 0)
        self.assertIn("a.md (changed)", after.getvalue())

    def test_map_absent_exits_3(self):
        tmp = temp_dir(self)
        with contextlib.redirect_stdout(io.StringIO()):
            code = tc.main(["map", "--map", os.path.join(tmp, "none.md"), "--repo-root", tmp])
        self.assertEqual(code, 3)


class SummaryTests(unittest.TestCase):
    def test_latest_row_wins_and_entry_averages(self):
        rows = [
            {"ticket": "S0001", "sessionId": "a", "runtime": "claude", "recordedAt": "2026-09-15T10:00:00Z",
             "entry": "first", "inputFresh": 0, "cacheRead": 100, "cacheCreate": 0,
             "readsBeforeFirstEdit": 9, "readCharsNotEdited": 5, "maxFailStreak": 0},
            {"ticket": "S0001", "sessionId": "a", "runtime": "claude", "recordedAt": "2026-09-15T11:00:00Z",
             "entry": "first", "inputFresh": 0, "cacheRead": 300, "cacheCreate": 0,
             "readsBeforeFirstEdit": 2, "readCharsNotEdited": 50, "maxFailStreak": 3, "streakCommand": "fg"},
            {"ticket": "S0001", "sessionId": "b", "runtime": "codex", "recordedAt": "2026-09-15T12:00:00Z",
             "entry": "repeat", "inputFresh": 10, "cacheRead": 90, "cacheCreate": 0,
             "readsBeforeFirstEdit": 1, "readCharsNotEdited": 0, "maxFailStreak": 1},
            {"ticket": "S0002", "sessionId": "c", "runtime": "gemini", "recordedAt": "2026-09-15T12:00:00Z",
             "entry": "first", "inputFresh": None, "cacheRead": None, "cacheCreate": None,
             "readsBeforeFirstEdit": 4, "readCharsNotEdited": 7, "maxFailStreak": 0},
        ]
        summary = tc.build_summary(tc.latest_rows(rows, "", ""), 10)
        self.assertEqual(summary["rows"], 3)
        self.assertEqual([h["ticket"] for h in summary["readsBeforeFirstEdit"]], ["S0002", "S0001", "S0001"])
        self.assertEqual(summary["maxFailStreak"],
                         [{"ticket": "S0001", "runtime": "claude", "value": 3, "command": "fg"}])
        self.assertEqual(summary["entryInput"]["first"], {"count": 1, "average": 300})
        self.assertEqual(summary["entryInput"]["repeat"], {"count": 1, "average": 100})
        self.assertIn("Longest failure streak", tc.render_summary(summary))

    def test_window_filter_drops_rows_outside_dates(self):
        rows = [{"ticket": "S0001", "sessionId": "a", "runtime": "claude", "recordedAt": "2026-09-01T10:00:00Z"}]
        self.assertEqual(tc.latest_rows(rows, "2026-09-15", ""), [])


if __name__ == "__main__":
    unittest.main()
