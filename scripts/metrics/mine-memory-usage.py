"""Measure how much of an agent's memory directory is actually used.

Walks a Claude Code transcript corpus recursively (including
``<session-id>/subagents/**``) and, for every ``.md`` file in an agent-memory
directory, reports how many distinct sessions ever opened it, which ticket ids
it is anchored to, and whether the always-loaded ``MEMORY.md`` index points at
it. Adds a summary that quantifies the dead weight: files never opened, files
anchored only to dead (absent or Archived) tickets, index pointers whose target
was never opened, and files whose body restates the project preamble.

Measurement only - it never writes, edits or deletes a memory file.

Definitions used here:

* session - the top-level transcript id. A nested ``subagents/**`` file is
  credited to its parent session, so one conversation counts once no matter how
  many subagents it spawned.
* read - a ``tool_use`` block from a read-family tool whose input names the
  memory file (``Read``, ``NotebookRead``, MCP ``read_file`` /
  ``read_multiple_files``), plus a ``Bash``/``PowerShell`` command that names the
  file together with a read verb (``cat``, ``head``, ``Get-Content``, ...).
  ``Edit``/``Write``/``Grep``/``Glob`` are deliberately NOT reads: an edit is
  maintenance of the memory, not consumption of it, and a directory-wide grep
  does not mean any particular file was consulted.

Usage:
    python mine-memory-usage.py --root <transcript-dir>
                                --memory-dir <agent-memory-dir>
                                --out <output.json>
                                [--repo-root DIR] [--claude-md FILE]
                                [--catalog FILE] [--catalog-archive FILE]
                                [--since YYYY-MM-DD] [--until YYYY-MM-DD]

Exit codes: 0 report written; 1 error; 2 cannot verify (transcript root or
memory directory missing/empty, output not writable).
"""
import argparse
import json
import os
import re
import sys
from collections import defaultdict

TICKET = re.compile(r"\bS\d{4}\b")
MD_LINK = re.compile(r"\]\(([^)]+\.md)\)")
READ_TOOLS = frozenset({
    "Read",
    "NotebookRead",
    "mcp__plugin_desktop-commander_desktop-commander__read_file",
    "mcp__plugin_desktop-commander_desktop-commander__read_multiple_files",
    "mcp__filesystem__read_file",
    "mcp__filesystem__read_text_file",
})
SHELL_TOOLS = frozenset({"Bash", "PowerShell"})
SHELL_READ_VERB = re.compile(
    r"(?i)\b(cat|head|tail|less|more|type|bat|get-content|select-string|"
    r"get-item|grep|rg|sed|awk)\b"
)
# A line has to carry real content before it can "restate" anything.
MIN_LINE_TOKENS = 8
# Containment, not Jaccard: a memory paragraph that repeats a short rule line
# verbatim still shares only a fraction of the union, so Jaccard scores it near
# zero and finds nothing at all. Containment asks the right question - is one
# line's vocabulary already inside the other's.
CONTAINMENT_NEAR_DUPLICATE = 0.6
SENSITIVITY_THRESHOLDS = (0.5, 0.6, 0.7)
MIN_RESTATED_LINES = 2
STOPWORDS = frozenset("""
a an the and or but if then than that this these those is are was were be been
being to of in on at by for with from as it its into out over under not no do
does did done can could should would may might must will shall you your i we
us our they them he she his her when while so such only also very more most
""".split())


def parse_args(argv):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", required=True,
                   help="Claude Code transcript directory for one project.")
    p.add_argument("--memory-dir", required=True,
                   help="Agent-memory directory holding MEMORY.md and its pointees.")
    p.add_argument("--out", required=True,
                   help="Path of the JSON report to write.")
    p.add_argument("--repo-root", default=None,
                   help="Repository root used to render relative paths. "
                        "Defaults to two levels above this script.")
    p.add_argument("--claude-md", default=None,
                   help="Preamble file for the restatement check. "
                        "Defaults to <repo-root>/CLAUDE.md.")
    p.add_argument("--catalog", default=None,
                   help="Active spec journal. Defaults to "
                        "<repo-root>/PLAN/spec-catalog.jsonl.")
    p.add_argument("--catalog-archive", default=None,
                   help="Archived spec journal. Defaults to "
                        "<repo-root>/PLAN/spec-catalog-archive.jsonl.")
    p.add_argument("--since", default=None, help="Inclusive YYYY-MM-DD lower bound.")
    p.add_argument("--until", default=None, help="Inclusive YYYY-MM-DD upper bound.")
    return p.parse_args(argv)


def rel_to_repo(path, repo_root):
    try:
        return os.path.relpath(path, repo_root).replace("\\", "/")
    except ValueError:
        return path.replace("\\", "/")


def discover_sessions(root):
    """Return (path, session_id) for every .jsonl under root, recursively.

    A nested file (``<session-id>/subagents/x.jsonl``) is credited to the
    top-level session id so that one conversation counts once.
    """
    found = []
    for dirpath, _dirnames, filenames in os.walk(root):
        for fn in filenames:
            if not fn.endswith(".jsonl"):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, root).replace("\\", "/")
            sid = rel.split("/", 1)[0]
            if sid.endswith(".jsonl"):
                sid = sid[:-6]
            found.append((path, sid))
    return found


def in_window(ts, since, until):
    if not ts:
        return True
    if since and ts < since:
        return False
    if until and ts > until:
        return False
    return True


def normalize_blob(text):
    return text.replace("\\\\", "/").replace("\\", "/").lower()


def load_memory_files(memory_dir):
    names = sorted(fn for fn in os.listdir(memory_dir)
                   if fn.lower().endswith(".md")
                   and os.path.isfile(os.path.join(memory_dir, fn)))
    files = {}
    for fn in names:
        full = os.path.join(memory_dir, fn)
        with open(full, "r", encoding="utf-8", errors="replace") as fh:
            text = fh.read()
        files[fn] = {
            "full": full,
            "bytes": os.path.getsize(full),
            "text": text,
            "tickets": sorted(set(TICKET.findall(text))),
        }
    return files


def memory_marker(memory_dir, repo_root):
    """The path tail that must appear in a tool input for a hit to count."""
    rel = rel_to_repo(memory_dir, repo_root).lower()
    # Drop any leading "./" and keep the last two segments, which are stable
    # across absolute/relative and drive-letter-case variants.
    parts = [p for p in rel.split("/") if p not in (".", "")]
    return "/".join(parts[-2:]) + "/"


def scan_transcripts(sessions, marker, names, since, until):
    """sessions read per memory file name, plus corpus counters."""
    read_sessions = defaultdict(set)
    ref_sessions = defaultdict(set)
    stats = {"files": len(sessions), "lines_with_marker": 0, "read_hits": 0,
             "ref_hits": 0}
    lowered = {n.lower(): n for n in names}
    for path, sid in sessions:
        try:
            fh = open(path, "r", encoding="utf-8", errors="replace")
        except OSError:
            continue
        with fh:
            for line in fh:
                if "agent-memory" not in line:
                    continue
                try:
                    e = json.loads(line)
                except Exception:
                    continue
                if e.get("type") != "assistant":
                    continue
                ts = (e.get("timestamp") or "")[:10]
                if not in_window(ts, since, until):
                    continue
                msg = e.get("message") or {}
                for c in msg.get("content") or []:
                    if not isinstance(c, dict) or c.get("type") != "tool_use":
                        continue
                    tool = c.get("name") or "?"
                    blob = normalize_blob(
                        json.dumps(c.get("input") or {}, ensure_ascii=False))
                    if marker not in blob:
                        continue
                    stats["lines_with_marker"] += 1
                    is_read = tool in READ_TOOLS or (
                        tool in SHELL_TOOLS and SHELL_READ_VERB.search(blob))
                    for low, name in lowered.items():
                        if marker + low not in blob:
                            continue
                        ref_sessions[name].add(sid)
                        stats["ref_hits"] += 1
                        if is_read:
                            read_sessions[name].add(sid)
                            stats["read_hits"] += 1
    return read_sessions, ref_sessions, stats


def parse_pointers(memory_text):
    """Markdown link targets in MEMORY.md, per line."""
    per_line = []
    for i, line in enumerate(memory_text.splitlines(), start=1):
        targets = [os.path.basename(t.split("#", 1)[0])
                   for t in MD_LINK.findall(line)]
        if targets:
            per_line.append((i, line.strip(), targets))
    return per_line


def load_catalog_status(path):
    """id -> status, last record wins (the journal is append-only)."""
    status = {}
    if not path or not os.path.isfile(path):
        return status
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                rec = json.loads(line)
            except Exception:
                continue
            tid = rec.get("id")
            if tid:
                status[tid] = rec.get("status") or "?"
    return status


def content_tokens(line):
    line = re.sub(r"`+", " ", line)
    line = re.sub(r"\[([^\]]*)\]\([^)]*\)", r"\1", line)
    line = re.sub(r"[^0-9a-zA-Z_./-]+", " ", line.lower())
    toks = [t.strip("-./") for t in line.split()]
    return {t for t in toks if len(t) > 2 and t not in STOPWORDS}


def restatement_candidates(files, claude_md_path):
    """Memory files with >= MIN_RESTATED_LINES near-duplicates of a preamble line.

    Conservative on purpose: both lines must carry at least MIN_LINE_TOKENS
    content tokens (stopwords and punctuation dropped), and their token sets
    must reach a containment of CONTAINMENT_NEAR_DUPLICATE. Short headers, YAML
    front matter keys and one-word bullets can never match.

    Returns (candidates, preamble_line_count, sensitivity) where sensitivity
    reports the same count at each SENSITIVITY_THRESHOLDS value, so a reader can
    see how much the verdict depends on where the line is drawn.
    """
    if not claude_md_path or not os.path.isfile(claude_md_path):
        return {}, None, {}
    with open(claude_md_path, "r", encoding="utf-8", errors="replace") as fh:
        preamble = [content_tokens(x) for x in fh.read().splitlines()]
    preamble = [s for s in preamble if len(s) >= MIN_LINE_TOKENS]
    scored = {}
    for name, meta in files.items():
        best_per_line = []
        for line in meta["text"].splitlines():
            toks = content_tokens(line)
            if len(toks) < MIN_LINE_TOKENS:
                continue
            best = 0.0
            for ref in preamble:
                inter = len(toks & ref)
                if not inter:
                    continue
                c = inter / float(min(len(toks), len(ref)))
                if c > best:
                    best = c
            if best > 0:
                best_per_line.append(best)
        scored[name] = best_per_line

    total_bytes = sum(m["bytes"] for m in files.values())
    sensitivity = {}
    for thr in SENSITIVITY_THRESHOLDS:
        names = [n for n, scores in scored.items()
                 if sum(1 for v in scores if v >= thr) >= MIN_RESTATED_LINES]
        b = sum(files[n]["bytes"] for n in names)
        sensitivity[f"containment_{thr}"] = {
            "files": len(names),
            "byteSharePct": round(100.0 * b / total_bytes, 2) if total_bytes else 0.0,
        }

    out = {}
    for name, scores in scored.items():
        hits = [round(v, 3) for v in scores if v >= CONTAINMENT_NEAR_DUPLICATE]
        if len(hits) >= MIN_RESTATED_LINES:
            out[name] = hits
    return out, len(preamble), sensitivity


def pct(part, whole):
    return round(100.0 * part / whole, 2) if whole else 0.0


def main(argv):
    args = parse_args(argv)
    root = os.path.abspath(os.path.expanduser(args.root))
    memory_dir = os.path.abspath(os.path.expanduser(args.memory_dir))
    repo_root = os.path.abspath(args.repo_root) if args.repo_root else \
        os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    claude_md = args.claude_md or os.path.join(repo_root, "CLAUDE.md")
    catalog = args.catalog or os.path.join(repo_root, "PLAN", "spec-catalog.jsonl")
    archive = args.catalog_archive or os.path.join(
        repo_root, "PLAN", "spec-catalog-archive.jsonl")

    if not os.path.isdir(root):
        print(f"transcript root not found: {root}", file=sys.stderr)
        return 2
    if not os.path.isdir(memory_dir):
        print(f"memory directory not found: {memory_dir}", file=sys.stderr)
        return 2
    sessions = discover_sessions(root)
    if not sessions:
        print(f"no .jsonl transcripts under: {root}", file=sys.stderr)
        return 2
    files = load_memory_files(memory_dir)
    if not files:
        print(f"no .md memory files under: {memory_dir}", file=sys.stderr)
        return 2
    print(f"transcript files: {len(sessions)}  memory files: {len(files)}",
          file=sys.stderr)

    marker = memory_marker(memory_dir, repo_root)
    read_sessions, ref_sessions, scan_stats = scan_transcripts(
        sessions, marker, list(files), args.since, args.until)

    index_name = "MEMORY.md"
    index_text = files.get(index_name, {}).get("text", "")
    pointer_lines = parse_pointers(index_text)
    pointed = {t for _n, _l, targets in pointer_lines for t in targets}

    active = load_catalog_status(catalog)
    archived = load_catalog_status(archive)

    restates, preamble_lines, restate_sensitivity = restatement_candidates(
        files, claude_md)

    total_bytes = sum(m["bytes"] for m in files.values())
    never, dead, entries = [], [], []
    for name in sorted(files):
        meta = files[name]
        rel = rel_to_repo(meta["full"], repo_root)
        sread = len(read_sessions.get(name, ()))
        tickets = meta["tickets"]
        dead_flags = {}
        for t in tickets:
            st = active.get(t)
            dead_flags[t] = (st is None or st == "Archived")
        is_dead = bool(tickets) and all(dead_flags.values())
        entries.append({
            "path": rel,
            "bytes": meta["bytes"],
            "sessionsRead": sread,
            "sessionsReferenced": len(ref_sessions.get(name, ())),
            "ticketIds": tickets,
            "ticketStatuses": {t: (active.get(t) or archived.get(t) or "absent")
                               for t in tickets},
            "allTicketsDead": is_dead,
            "pointerInMemoryMd": name in pointed,
            "restatesPreambleLines": len(restates.get(name, ())),
        })
        if sread == 0:
            never.append(rel)
        if is_dead:
            dead.append(rel)
    restate_paths = [rel_to_repo(files[n]["full"], repo_root)
                     for n in sorted(restates)]

    never_bytes = sum(m["bytes"] for n, m in files.items()
                      if len(read_sessions.get(n, ())) == 0)
    dead_set = set(dead)
    dead_bytes = sum(m["bytes"] for n, m in files.items()
                     if rel_to_repo(m["full"], repo_root) in dead_set)
    restate_bytes = sum(files[n]["bytes"] for n in restates)

    unread_pointer_lines = [
        {"line": ln, "targets": [t for t in targets
                                 if len(read_sessions.get(t, ())) == 0],
         "text": txt}
        for ln, txt, targets in pointer_lines
        if all(len(read_sessions.get(t, ())) == 0 for t in targets)
    ]
    unread_pointer_targets = sorted({
        t for _ln, _txt, targets in pointer_lines for t in targets
        if len(read_sessions.get(t, ())) == 0
    })

    result = {
        "generated": {
            "transcriptRoot": root.replace("\\", "/"),
            "memoryDir": rel_to_repo(memory_dir, repo_root),
            "window": {"since": args.since, "until": args.until},
            "transcriptFiles": scan_stats["files"],
            "distinctSessions": len({sid for _p, sid in sessions}),
            "markerHits": scan_stats["lines_with_marker"],
            "readHits": scan_stats["read_hits"],
            "referenceHits": scan_stats["ref_hits"],
            "readDefinition": (
                "tool_use from Read/NotebookRead/MCP read_file(+multiple), or a "
                "Bash/PowerShell command naming the file together with a read "
                "verb. Edit/Write/Grep/Glob are excluded."),
            "sessionDefinition": (
                "top-level transcript id; subagents/** files credited to their "
                "parent session"),
        },
        "files": entries,
        "neverOpened": never,
        "deadTicketAnchored": dead,
        "restatesPreamble": restate_paths,
        "memoryMdPointerLines": unread_pointer_lines,
        "summary": {
            "totalFiles": len(files),
            "totalBytes": total_bytes,
            "memoryMdBytes": files.get(index_name, {}).get("bytes", 0),
            "memoryMdLines": len(index_text.splitlines()),
            "memoryMdPointerLineCount": len(pointer_lines),
            "memoryMdPointerTargets": len(pointed),
            "neverOpenedCount": len(never),
            "neverOpenedBytes": never_bytes,
            "neverOpenedByteSharePct": pct(never_bytes, total_bytes),
            "deadTicketAnchoredCount": len(dead),
            "deadTicketAnchoredBytes": dead_bytes,
            "deadTicketAnchoredByteSharePct": pct(dead_bytes, total_bytes),
            "filesWithNoTicketIds": sum(1 for m in files.values()
                                        if not m["tickets"]),
            "pointerLinesNeverRead": len(unread_pointer_lines),
            "pointerTargetsNeverRead": len(unread_pointer_targets),
            "restatesPreambleCount": len(restate_paths),
            "restatesPreambleBytes": restate_bytes,
            "restatesPreambleByteSharePct": pct(restate_bytes, total_bytes),
            "restatesPreambleMethod": (
                "For each memory-file line and each line of {claude}, drop "
                "markdown/punctuation, lowercase, drop stopwords, keep tokens "
                "longer than 2 chars. Only lines with at least {mint} content "
                "tokens on BOTH sides are compared. Similarity is CONTAINMENT - "
                "|A and B| / min(|A|,|B|) - not Jaccard: a memory paragraph that "
                "repeats a short rule verbatim still shares little of the union, "
                "and Jaccard scored zero files at every threshold tried. A pair "
                "counts as a near duplicate at containment >= {thr}; a file is a "
                "candidate when at least {minl} of its lines near-duplicate some "
                "preamble line. Deliberately conservative and lexical: a rule "
                "restated in different words is not counted, so this is a hard "
                "lower bound on restatement, not an estimate of it - see "
                "restatesPreambleSensitivity for how the count moves with the "
                "threshold. Preamble lines compared: {plines}."
            ).format(claude=rel_to_repo(claude_md, repo_root),
                     mint=MIN_LINE_TOKENS, thr=CONTAINMENT_NEAR_DUPLICATE,
                     minl=MIN_RESTATED_LINES, plines=preamble_lines),
            "restatesPreambleSensitivity": restate_sensitivity,
            "catalogActiveIds": len(active),
            "catalogArchivedIds": len(archived),
            # A file read in exactly one session was usually only read by the
            # session that wrote it, so the <=1 band is the honest upper bound
            # on "never actually consulted again".
            "readDistribution": {
                f"readInAtMost{k}Sessions": {
                    "files": sum(1 for e in entries if e["sessionsRead"] <= k),
                    "bytes": sum(e["bytes"] for e in entries
                                 if e["sessionsRead"] <= k),
                    "byteSharePct": pct(
                        sum(e["bytes"] for e in entries
                            if e["sessionsRead"] <= k), total_bytes),
                }
                for k in (0, 1, 2, 3)
            },
            "neverReferencedByAnyTool": sum(
                1 for e in entries if e["sessionsReferenced"] == 0),
        },
    }

    out_path = os.path.abspath(args.out)
    out_dir = os.path.dirname(out_path)
    try:
        if out_dir:
            os.makedirs(out_dir, exist_ok=True)
        with open(out_path, "w", encoding="utf-8") as fh:
            json.dump(result, fh, ensure_ascii=False, indent=1)
    except OSError as exc:
        print(f"cannot write report: {exc}", file=sys.stderr)
        return 2

    s = result["summary"]
    print(f"memory files {s['totalFiles']} bytes {s['totalBytes']}")
    print(f"never opened {s['neverOpenedCount']} "
          f"({s['neverOpenedByteSharePct']}% of bytes)")
    print(f"dead-ticket anchored {s['deadTicketAnchoredCount']} "
          f"({s['deadTicketAnchoredByteSharePct']}% of bytes)")
    print(f"restates preamble {s['restatesPreambleCount']} "
          f"({s['restatesPreambleByteSharePct']}% of bytes)")
    print(f"MEMORY.md {s['memoryMdBytes']} bytes / {s['memoryMdLines']} lines, "
          f"{s['memoryMdPointerLineCount']} pointer lines, "
          f"{s['pointerLinesNeverRead']} never read")
    print(f"report: {out_path}")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1:]))
    except KeyboardInterrupt:
        sys.exit(1)
    except Exception as exc:  # noqa: BLE001 - top-level guard, reports exit 1
        print(f"error: {exc}", file=sys.stderr)
        sys.exit(1)
