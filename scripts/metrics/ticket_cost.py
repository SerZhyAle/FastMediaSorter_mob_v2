#!/usr/bin/env python3
"""Per-ticket agent token-cost journal (S3147).

Scripts the agent calls never see its reads, edits or token usage; only the
runtime's own transcript does. This module turns one ticket's window of a
Claude Code, Codex or Gemini/Antigravity transcript into one ledger row and a
context map, and summarises the ledger.

Subcommands (called by scripts/metrics/ticket-cost.ps1):
  record   append one row to the ledger and rewrite <map-dir>/context-map.md
  map      print a context map, marking file lines whose content changed since
  summary  rank tickets by chars read but never edited, reads before the first
           edit and the longest failure streak of one command

Codex reads go through shell commands, so a Codex read is recognised only for
Get-Content and read-window.ps1; other shell reads count as plain calls.

Exit codes: 0 done; 1 extractor error; 2 bad invocation; 3 no map recorded.
"""
import argparse
import datetime
import hashlib
import json
import os
import re
import sys
from collections import Counter, defaultdict

EDIT_TOOLS_CLAUDE = {"Edit", "Write", "MultiEdit", "NotebookEdit"}
SHELL_TOOLS_CLAUDE = {"Bash", "PowerShell"}
READ_TOOLS_GEMINI = {"view_file"}
EDIT_TOOLS_GEMINI = {"write_to_file", "replace_file_content", "multi_replace_file_content"}
GEMINI_PATH_KEYS = ("AbsolutePath", "TargetFile", "FilePath")
# Codex often edits through its `exec` tool with the patch inside a JavaScript string literal,
# where line breaks arrive as a literal backslash-n, so a line anchor alone misses every edit.
PATCH_FILE = re.compile(r"(?:^|\\n)\*\*\* (?:Update|Add|Delete) File: (.+?)\s*(?=\\n|$|\")", re.M)
SHELL_READ = re.compile(
    r"(?:read-window\.ps1|Get-Content)\s+(?:-(?:LiteralPath|Path)\s+)?[\"']?([^\s\"'|;]+)", re.I)
EXIT_CODE = re.compile(r"(?i)exit code:?\s*(-?\d+)")
MAP_LINE = re.compile(r"^- ([0-9a-f]{12}|missing) (.+?)(?: \(\d+ chars\))?$")
TICKET = re.compile(r"S\d{4}")
MAP_LIST_CAP = 30
RECENT_DAYS = 7


def iter_jsonl(path):
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                rec = json.loads(line)
            except ValueError:
                # A live session may leave a partial last line; the rest of the file is still valid.
                continue
            if isinstance(rec, dict):
                yield rec


def norm_cmd(text):
    return re.sub(r"\s+", " ", (text or "").strip())[:120]


def first_line(text):
    for line in (text or "").splitlines():
        if line.strip():
            return line.strip()[:160]
    return ""


def rel_path(path, repo_root):
    p = (path or "").replace("\\", "/")
    root = (repo_root or "").replace("\\", "/").rstrip("/")
    if root and p.lower().startswith(root.lower() + "/"):
        return p[len(root) + 1:]
    return p


class Tally:
    """Token totals and ordered tool events for one ticket window."""

    def __init__(self, repo_root):
        self.repo_root = repo_root
        self.window_start = ""
        self.events = []
        self.seq = 0
        self.turns = 0
        self.tokens = None
        self.context_peak = None
        self.context_last = None
        self.compactions = 0
        self.calls = 0
        self.fails = 0
        self.result_chars = 0
        self.models = Counter()

    def add_tokens(self, fresh, cache_read, cache_create, output):
        if self.tokens is None:
            self.tokens = [0, 0, 0, 0]
        for i, n in enumerate((fresh, cache_read, cache_create, output)):
            self.tokens[i] += n

    def context(self, size):
        self.context_last = size
        if self.context_peak is None or size > self.context_peak:
            self.context_peak = size

    def count_call(self, failed, chars):
        self.calls += 1
        self.result_chars += chars
        if failed:
            self.fails += 1

    def event(self, ts, kind, key, chars=0, failed=False, detail=""):
        if kind in ("read", "edit"):
            key = rel_path(key, self.repo_root)
        self.events.append((ts or "", self.seq, kind, key, chars, failed, detail))
        self.seq += 1

    def row(self):
        events = sorted(self.events, key=lambda e: (e[0], e[1]))
        edited = {}
        for _, _, kind, key, _, failed, _ in events:
            if kind == "edit" and not failed and key:
                edited.setdefault(key.lower(), key)
        reads_before_edit = 0
        seen_edit = False
        read_chars = defaultdict(int)
        read_names = {}
        streak = Counter()
        max_streak = Counter()
        last_fail = {}
        for _, _, kind, key, chars, failed, detail in events:
            if kind == "read" and not failed and key:
                if not seen_edit:
                    reads_before_edit += 1
                read_chars[key.lower()] += chars
                read_names.setdefault(key.lower(), key)
            elif kind == "edit" and not failed:
                seen_edit = True
            elif kind == "call":
                if failed:
                    streak[key] += 1
                    last_fail[key] = detail
                    max_streak[key] = max(max_streak[key], streak[key])
                else:
                    streak[key] = 0
        read_not_edited = sorted(
            ((read_names[k], c) for k, c in read_chars.items() if k not in edited),
            key=lambda kc: -kc[1])
        best_cmd, best_streak = max(max_streak.items(), key=lambda kv: kv[1], default=("", 0))
        tokens = self.tokens
        fields = {
            "model": self.models.most_common(1)[0][0] if self.models else None,
            "windowStart": self.window_start,
            "turns": self.turns,
            "inputFresh": tokens[0] if tokens else None,
            "cacheRead": tokens[1] if tokens else None,
            "cacheCreate": tokens[2] if tokens else None,
            "output": tokens[3] if tokens else None,
            "contextPeak": self.context_peak,
            "contextLast": self.context_last,
            "compactions": self.compactions,
            "toolCalls": self.calls,
            "hardFails": self.fails,
            "toolResultChars": self.result_chars,
            "readsBeforeFirstEdit": reads_before_edit,
            "filesRead": len(read_chars),
            "filesEdited": len(edited),
            "readCharsNotEdited": sum(c for _, c in read_not_edited),
            "maxFailStreak": best_streak,
            "streakCommand": best_cmd or None,
        }
        lists = {
            "edited": sorted(edited.values(), key=str.lower),
            "readNotEdited": read_not_edited,
            "failing": [(cmd, n, last_fail.get(cmd, ""))
                        for cmd, n in sorted(max_streak.items(), key=lambda kv: -kv[1])],
        }
        return fields, lists


# ---- Claude Code -------------------------------------------------------------

def claude_files(main_path):
    files = [main_path]
    sub_dir = os.path.join(os.path.splitext(main_path)[0], "subagents")
    for root, _, names in sorted(os.walk(sub_dir)):
        files.extend(os.path.join(root, n) for n in sorted(names) if n.endswith(".jsonl"))
    return files


def mentions(value, ticket):
    text = value if isinstance(value, str) else json.dumps(value, ensure_ascii=False)
    return ticket in text


def claude_window_start(main_path, ticket):
    for rec in iter_jsonl(main_path):
        content = (rec.get("message") or {}).get("content")
        if rec.get("type") == "user":
            if isinstance(content, str) and ticket in content:
                return rec.get("timestamp") or ""
            for c in content if isinstance(content, list) else []:
                if isinstance(c, dict) and c.get("type") == "text" and ticket in (c.get("text") or ""):
                    return rec.get("timestamp") or ""
        elif rec.get("type") == "assistant" and isinstance(content, list):
            for c in content:
                if isinstance(c, dict) and c.get("type") == "tool_use" and mentions(c.get("input"), ticket):
                    return rec.get("timestamp") or ""
    return ""


def claude_usage(tally, msg, request_id, seen, is_main):
    u = msg.get("usage") or {}
    usage = (u.get("input_tokens") or 0, u.get("cache_read_input_tokens") or 0,
             u.get("cache_creation_input_tokens") or 0, u.get("output_tokens") or 0)
    # One API response is written as several records repeating one usage object;
    # summing records instead of requestIds inflates tokens about 3x.
    prev = seen.get(request_id) if request_id else None
    if request_id:
        if prev is not None and sum(usage) <= sum(prev):
            return
        seen[request_id] = usage
    delta = usage if prev is None else tuple(n - p for n, p in zip(usage, prev))
    if prev is None:
        tally.turns += 1
        model = msg.get("model")
        if model and not model.startswith("<"):
            tally.models[model] += 1
    tally.add_tokens(*delta)
    if is_main:
        tally.context(usage[0] + usage[1] + usage[2])


def claude_result(tally, ts, call, block):
    name, inp = call
    body = block.get("content")
    if isinstance(body, list):
        body = " ".join(x.get("text", "") for x in body if isinstance(x, dict))
    body = body if isinstance(body, str) else ""
    failed = bool(block.get("is_error"))
    tally.count_call(failed, len(body))
    if name == "Read":
        tally.event(ts, "read", inp.get("file_path") or "", len(body), failed)
    elif name in EDIT_TOOLS_CLAUDE:
        tally.event(ts, "edit", inp.get("file_path") or inp.get("notebook_path") or "", len(body), failed)
    elif name in SHELL_TOOLS_CLAUDE:
        tally.event(ts, "call", norm_cmd(inp.get("command")), len(body), failed, first_line(body))
    else:
        tally.event(ts, "call", name, len(body), failed, first_line(body))


def extract_claude(main_path, ticket, repo_root):
    tally = Tally(repo_root)
    start = claude_window_start(main_path, ticket)
    tally.window_start = start
    seen = {}
    for path in claude_files(main_path):
        is_main = path == main_path
        pending = {}
        for rec in iter_jsonl(path):
            ts = rec.get("timestamp") or ""
            if start and ts < start:
                continue
            kind = rec.get("type")
            msg = rec.get("message") or {}
            if kind == "assistant":
                claude_usage(tally, msg, rec.get("requestId"), seen, is_main)
                for c in msg.get("content") or []:
                    if isinstance(c, dict) and c.get("type") == "tool_use":
                        pending[c.get("id")] = (c.get("name") or "?", c.get("input") or {})
            elif kind == "system" and rec.get("subtype") == "compact_boundary":
                tally.compactions += 1
            elif kind == "user" and isinstance(msg.get("content"), list):
                for c in msg["content"]:
                    if isinstance(c, dict) and c.get("type") == "tool_result":
                        call = pending.pop(c.get("tool_use_id"), None)
                        if call:
                            claude_result(tally, ts, call, c)
    return tally


# ---- Codex -------------------------------------------------------------------

def codex_usage(u):
    u = u or {}
    total_in = u.get("input_tokens") or 0
    cached = u.get("cached_input_tokens") or 0
    return (total_in - cached, cached, u.get("cache_write_input_tokens") or 0, u.get("output_tokens") or 0)


def codex_output(output):
    if isinstance(output, dict):
        data = output
    else:
        text = str(output or "")
        try:
            data = json.loads(text)
        except ValueError:
            match = EXIT_CODE.search(text[:400])
            return text, int(match.group(1)) if match else None
    if not isinstance(data, dict):
        return str(output), None
    body = str(data.get("output") or "")
    meta = data.get("metadata")
    code = meta.get("exit_code") if isinstance(meta, dict) else None
    if code is None:
        match = EXIT_CODE.search(body[:400])
        code = int(match.group(1)) if match else None
    return body, code


def codex_command(text):
    try:
        data = json.loads(text)
    except ValueError:
        return text
    if isinstance(data, dict):
        command = data.get("command") or data.get("cmd")
        if isinstance(command, list):
            return " ".join(str(part) for part in command)
        if command:
            return str(command)
    return text


def codex_result(tally, ts, call, output):
    name, text = call
    body, code = codex_output(output)
    failed = code not in (None, 0)
    tally.count_call(failed, len(body))
    edits = PATCH_FILE.findall(text)
    if edits:
        for path in edits:
            tally.event(ts, "edit", path, len(body), failed)
        return
    command = codex_command(text)
    read = SHELL_READ.search(command)
    if read:
        tally.event(ts, "read", read.group(1), len(body), failed)
    else:
        tally.event(ts, "call", norm_cmd(command) or name, len(body), failed, first_line(body))


def extract_codex(path, ticket, repo_root):
    tally = Tally(repo_root)
    records = list(iter_jsonl(path))
    start = 0
    for i, rec in enumerate(records):
        pl = rec.get("payload") or {}
        if rec.get("type") == "event_msg" and pl.get("type") == "user_message" \
                and ticket in str(pl.get("message") or ""):
            start = i
            tally.window_start = rec.get("timestamp") or ""
            break
    base = (0, 0, 0, 0)
    last_total = None
    pending = {}
    for i, rec in enumerate(records):
        pl = rec.get("payload") or {}
        kind, ptype = rec.get("type"), pl.get("type")
        if kind == "turn_context" and pl.get("model"):
            tally.models[pl["model"]] += 1
        elif kind == "event_msg" and ptype == "token_count" and pl.get("info"):
            # Codex reports cumulative session totals, so the window is last-minus-before-window.
            total = codex_usage(pl["info"].get("total_token_usage"))
            if i < start:
                base = total
                continue
            last_total = total
            tally.turns += 1
            tally.context((pl["info"].get("last_token_usage") or {}).get("input_tokens") or 0)
        elif i >= start and kind == "response_item":
            if ptype in ("custom_tool_call", "function_call", "local_shell_call"):
                if ptype == "custom_tool_call":
                    text = str(pl.get("input") or "")
                elif ptype == "function_call":
                    text = str(pl.get("arguments") or "")
                else:
                    text = json.dumps(pl.get("action") or {})
                pending[pl.get("call_id")] = (pl.get("name") or ptype, text)
            elif ptype in ("custom_tool_call_output", "function_call_output"):
                call = pending.pop(pl.get("call_id"), None)
                if call:
                    codex_result(tally, rec.get("timestamp") or "", call, pl.get("output"))
    if last_total is not None:
        tally.add_tokens(*(n - b for n, b in zip(last_total, base)))
    return tally


# ---- Gemini / Antigravity ----------------------------------------------------

def gemini_args(args):
    if isinstance(args, dict):
        return args
    try:
        data = json.loads(str(args or ""))
    except ValueError:
        return {}
    return data if isinstance(data, dict) else {}


def gemini_result(tally, rec, name, args):
    body = rec.get("content")
    if not isinstance(body, str):
        body = "" if body is None else json.dumps(body, ensure_ascii=False)
    status = str(rec.get("status") or "").upper()
    failed = "ERROR" in status or "FAIL" in status
    ts = str(rec.get("created_at") or "")
    tally.count_call(failed, len(body))
    path = next((str(args[k]) for k in GEMINI_PATH_KEYS if args.get(k)), "")
    if name in READ_TOOLS_GEMINI:
        tally.event(ts, "read", path, len(body), failed)
    elif name in EDIT_TOOLS_GEMINI:
        tally.event(ts, "edit", path, len(body), failed)
    else:
        tally.event(ts, "call", norm_cmd(args.get("CommandLine")) or name, len(body), failed, first_line(body))


def extract_gemini(path, ticket, repo_root):
    tally = Tally(repo_root)
    records = list(iter_jsonl(path))
    start = 0
    for i, rec in enumerate(records):
        if rec.get("source") == "USER_EXPLICIT" and mentions(rec, ticket):
            start = i
            break
    if records:
        tally.window_start = str(records[start].get("created_at") or "")
    pending = []
    for rec in records[start:]:
        if rec.get("type") == "PLANNER_RESPONSE":
            tally.turns += 1
            for call in rec.get("tool_calls") or []:
                if isinstance(call, dict):
                    pending.append((call.get("name") or "?", gemini_args(call.get("args"))))
        elif rec.get("source") != "USER_EXPLICIT" and pending:
            name, args = pending.pop(0)
            gemini_result(tally, rec, name, args)
    return tally


EXTRACTORS = {"claude": extract_claude, "codex": extract_codex, "gemini": extract_gemini}


# ---- Transcript discovery ----------------------------------------------------

def recent_files(root, accept):
    cutoff = datetime.datetime.now().timestamp() - RECENT_DAYS * 86400
    found = []
    for base, _, names in os.walk(root):
        for name in names:
            if not accept(name):
                continue
            path = os.path.join(base, name)
            try:
                mtime = os.path.getmtime(path)
            except OSError:
                continue
            if mtime >= cutoff:
                found.append((mtime, path))
    return [p for _, p in sorted(found, reverse=True)]


def find_claude(home, repo_root, session):
    projects = os.path.join(home, ".claude", "projects")
    if not session or not os.path.isdir(projects):
        return None
    # Claude Code names the directory after the project path with ':', separators and
    # underscores replaced by '-', and the drive letter's case follows the launching shell.
    wanted = re.sub(r"[:\\/_]", "-", repo_root.rstrip("\\/")).lower()
    for name in sorted(os.listdir(projects), key=lambda n: n.lower() != wanted):
        candidate = os.path.join(projects, name, session + ".jsonl")
        if os.path.isfile(candidate):
            return candidate
    return None


def find_codex(home, ticket):
    for path in recent_files(os.path.join(home, ".codex", "sessions"), lambda n: n.endswith(".jsonl")):
        for rec in iter_jsonl(path):
            pl = rec.get("payload") or {}
            if rec.get("type") == "event_msg" and pl.get("type") == "user_message":
                # Only the first user message names the session's ticket; a later one may just discuss another.
                if ticket in str(pl.get("message") or ""):
                    return path
                break
    return None


def find_gemini(home, ticket):
    brain = os.path.join(home, ".gemini", "antigravity", "brain")
    for path in recent_files(brain, lambda n: n == "transcript_full.jsonl"):
        with open(path, "r", encoding="utf-8", errors="replace") as fh:
            if ticket in fh.read():
                return path
    return None


def discover(runtime, home, repo_root, session, ticket):
    if runtime == "claude":
        return find_claude(home, repo_root, session)
    if runtime == "codex":
        return find_codex(home, ticket)
    if runtime == "gemini":
        return find_gemini(home, ticket)
    return None


def session_from_path(runtime, path):
    if runtime == "gemini":
        # <brain>/<task-id>/.system_generated/logs/transcript_full.jsonl
        return os.path.basename(os.path.dirname(os.path.dirname(os.path.dirname(path))))
    return os.path.splitext(os.path.basename(path))[0]


# ---- Ledger and map ----------------------------------------------------------

def now_iso():
    return datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def entry_kind(ledger, ticket, session):
    other_session_seen = False
    if os.path.isfile(ledger):
        for row in iter_jsonl(ledger):
            if row.get("ticket") != ticket:
                continue
            if row.get("sessionId") == session:
                return row.get("entry") or "first"
            other_session_seen = True
    return "repeat" if other_session_seen else "first"


def append_row(ledger, row):
    os.makedirs(os.path.dirname(ledger), exist_ok=True)
    with open(ledger, "a", encoding="utf-8", newline="\n") as fh:
        fh.write(json.dumps(row, ensure_ascii=False) + "\n")


def file_hash(path, repo_root):
    full = path if os.path.isabs(path) else os.path.join(repo_root, path)
    try:
        with open(full, "rb") as fh:
            return hashlib.sha256(fh.read()).hexdigest()[:12]
    except OSError:
        return "missing"


def write_map(path, row, lists, repo_root):
    lines = [f"# Context map {row['ticket']}", "",
             f"recorded: {row['recordedAt']} | runtime: {row['runtime']} | "
             f"session: {row['sessionId']} | entry: {row['entry']}",
             "", "## Edited", ""]
    edited = lists["edited"][:MAP_LIST_CAP]
    lines += [f"- {file_hash(p, repo_root)} {p}" for p in edited] or ["- none"]
    lines += ["", "## Read, not edited (largest first)", ""]
    reads = lists["readNotEdited"][:MAP_LIST_CAP]
    lines += [f"- {file_hash(p, repo_root)} {p} ({c} chars)" for p, c in reads] or ["- none"]
    lines += ["", "## Commands that failed (longest streak, last output)", ""]
    failing = lists["failing"][:MAP_LIST_CAP]
    lines += [f"- x{n} `{cmd}` - {detail}" for cmd, n, detail in failing] or ["- none"]
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write("\n".join(lines) + "\n")


# ---- Summary -----------------------------------------------------------------

def latest_rows(rows, since, until):
    latest = {}
    for row in rows:
        day = str(row.get("recordedAt") or "")[:10]
        if (since and day < since) or (until and day > until):
            continue
        key = (row.get("ticket"), row.get("sessionId"), row.get("runtime"))
        if key not in latest or str(row.get("recordedAt")) >= str(latest[key].get("recordedAt")):
            latest[key] = row
    return list(latest.values())


def input_total(row):
    return sum((row.get(k) or 0) for k in ("inputFresh", "cacheRead", "cacheCreate"))


def build_summary(rows, top):
    def ranked(field, minimum=1, command=None):
        hits = sorted((r for r in rows if (r.get(field) or 0) >= minimum),
                      key=lambda r: -(r.get(field) or 0))[:top]
        result = []
        for r in hits:
            item = {"ticket": r.get("ticket"), "runtime": r.get("runtime"), "value": r.get(field)}
            if command:
                item["command"] = r.get(command)
            result.append(item)
        return result

    runtimes = defaultdict(Counter)
    entries = defaultdict(list)
    for r in rows:
        totals = runtimes[r.get("runtime") or "unknown"]
        totals["rows"] += 1
        for key in ("turns", "inputFresh", "cacheRead", "cacheCreate", "output"):
            totals[key] += r.get(key) or 0
        if r.get("cacheRead") is not None:
            entries[r.get("entry") or "first"].append(input_total(r))
    return {
        "rows": len(rows),
        "readCharsNotEdited": ranked("readCharsNotEdited"),
        "readsBeforeFirstEdit": ranked("readsBeforeFirstEdit"),
        # One failure is ordinary iteration; two in a row of the same command is the circling to surface.
        "maxFailStreak": ranked("maxFailStreak", 2, "streakCommand"),
        "runtimes": {k: dict(v) for k, v in runtimes.items()},
        "entryInput": {k: {"count": len(v), "average": round(sum(v) / len(v))} for k, v in entries.items()},
    }


def render_summary(summary):
    out = [f"ticket-cost summary: {summary['rows']} row(s)"]
    sections = (("Read but never edited (chars)", "readCharsNotEdited"),
                ("Reads before the first edit", "readsBeforeFirstEdit"),
                ("Longest failure streak of one command", "maxFailStreak"))
    for title, key in sections:
        out.append(f"{title}:")
        hits = summary[key]
        if not hits:
            out.append("  none")
        for h in hits:
            tail = f"  {h['command']}" if h.get("command") else ""
            out.append(f"  {h['ticket']}  {h['runtime']}  {h['value']}{tail}")
    out.append("By runtime:")
    for name, c in sorted(summary["runtimes"].items()):
        out.append(f"  {name}  rows={c.get('rows', 0)} turns={c.get('turns', 0)} "
                   f"cache_read={c.get('cacheRead', 0)} fresh={c.get('inputFresh', 0)} "
                   f"output={c.get('output', 0)}")
    out.append("Input tokens per entry:")
    for kind in ("first", "repeat"):
        e = summary["entryInput"].get(kind)
        out.append(f"  {kind}  " + (f"n={e['count']} avg={e['average']}" if e else "n=0"))
    return "\n".join(out)


# ---- Commands ----------------------------------------------------------------

def cmd_record(args):
    if args.transcript and not os.path.isfile(args.transcript):
        print(f"ticket-cost: transcript not found: {args.transcript}", file=sys.stderr)
        return 2
    runtime = args.runtime
    extractor = EXTRACTORS.get(runtime)
    path = args.transcript or discover(runtime, args.home, args.repo_root, args.session, args.ticket)
    row = {"schema": 1, "ticket": args.ticket, "runtime": runtime, "sessionId": args.session or "",
           "recordedAt": now_iso(), "transcript": "unavailable"}
    lists = None
    if extractor and path:
        fields, lists = extractor(path, args.ticket, args.repo_root).row()
        row["transcript"] = path
        if runtime != "claude" or not row["sessionId"]:
            row["sessionId"] = session_from_path(runtime, path)
        row.update(fields)
    row["entry"] = entry_kind(args.ledger, args.ticket, row["sessionId"])
    append_row(args.ledger, row)
    if lists is not None:
        write_map(os.path.join(args.map_dir, "context-map.md"), row, lists, args.repo_root)
    return 0


def cmd_map(args):
    if not os.path.isfile(args.map):
        print(f"ticket-cost: no context map at {args.map}")
        return 3
    with open(args.map, "r", encoding="utf-8") as fh:
        for raw in fh.read().splitlines():
            match = MAP_LINE.match(raw)
            if match and file_hash(match.group(2), args.repo_root) != match.group(1):
                raw += " (changed)"
            print(raw)
    return 0


def cmd_summary(args):
    rows = list(iter_jsonl(args.ledger)) if os.path.isfile(args.ledger) else []
    summary = build_summary(latest_rows(rows, args.since, args.until), args.top)
    print(json.dumps(summary, ensure_ascii=False) if args.json else render_summary(summary))
    return 0


def main(argv=None):
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(prog="ticket_cost.py")
    sub = parser.add_subparsers(dest="command", required=True)
    rec = sub.add_parser("record")
    rec.add_argument("--ticket", required=True)
    rec.add_argument("--runtime", required=True, choices=["claude", "codex", "gemini", "zcode", "unknown"])
    rec.add_argument("--session", default="")
    rec.add_argument("--transcript", default="")
    rec.add_argument("--home", default=os.path.expanduser("~"))
    rec.add_argument("--repo-root", required=True)
    rec.add_argument("--ledger", required=True)
    rec.add_argument("--map-dir", required=True)
    mp = sub.add_parser("map")
    mp.add_argument("--map", required=True)
    mp.add_argument("--repo-root", required=True)
    sm = sub.add_parser("summary")
    sm.add_argument("--ledger", required=True)
    sm.add_argument("--since", default="")
    sm.add_argument("--until", default="")
    sm.add_argument("--top", type=int, default=10)
    sm.add_argument("--json", action="store_true")
    args = parser.parse_args(argv)
    if args.command == "record" and not TICKET.fullmatch(args.ticket):
        parser.error(f"--ticket must look like Sxxxx, got {args.ticket!r}")
    handlers = {"record": cmd_record, "map": cmd_map, "summary": cmd_summary}
    try:
        return handlers[args.command](args)
    except (OSError, ValueError, KeyError, TypeError) as exc:
        print(f"ticket-cost: {args.command} failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
