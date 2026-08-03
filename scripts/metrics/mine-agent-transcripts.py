"""Mine Claude Code transcripts for process-cost metrics.

Streams every .jsonl session file under a transcript root, aggregates token
usage, tool-call mix, failure signals and user-correction signals. Writes a
JSON metrics file plus a readable text digest.

Stack-agnostic by design: it reads Claude Code transcripts, which every
project has. Nothing here may reference a particular project, language or
toolchain - the transcript root and output directory are arguments.

Usage:
    python mine-agent-transcripts.py --root <transcript-dir> --out <output-dir>
                                     [--since YYYY-MM-DD] [--until YYYY-MM-DD]

Exit codes: 0 report written; 1 error; 2 cannot verify (root missing/empty).
"""
import argparse
import json
import os
import re
import sys
from collections import Counter, defaultdict

CORRECTION = re.compile(
    r"(?i)\b(нет,|не так|стоп|неверно|неправильно|опять|снова|я же (сказал|просил)|"
    r"почему ты|зачем ты|перестань|хватит|не надо|верни|откати|ты (сломал|не понял)|"
    r"это не то|ерунда|бред|плохо|исправь)\b"
)
ERR = re.compile(
    r"(?i)(FAILURE: Build failed|error:|Exception|is not recognized|"
    r"ParameterBindingException|cannot be found|No such file|exit code [1-9]|"
    r"syntax error|CommandNotFound|Compilation error|FAILED)"
)
# Only these carry process output in the result body, so only these may be
# judged by the ERR regex. Applying it to Read/Grep/Edit scores file content
# as a tool failure.
SOFT_BAND_TOOLS = frozenset({"Bash", "PowerShell", "Agent", "Task", "Skill"})


def parse_args(argv):
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--root", required=True,
                   help="Claude Code transcript directory for one project.")
    p.add_argument("--out", required=True,
                   help="Directory to write transcript_metrics.json and digest.")
    p.add_argument("--since", default=None, help="Inclusive YYYY-MM-DD lower bound.")
    p.add_argument("--until", default=None, help="Inclusive YYYY-MM-DD upper bound.")
    return p.parse_args(argv)


def discover_sessions(root):
    """Return (path, session_id, tier) for every .jsonl under root.

    Walks recursively so <session-id>/subagents/** is included - those files
    carry a sixth of all traffic with no requestId overlap, so they are purely
    additive. Tier is reported separately because subagent conversations are
    structurally shorter, and a flat average across both corrupts the headline
    per-turn figure.
    """
    found = []
    for dirpath, _dirnames, filenames in os.walk(root):
        for fn in filenames:
            if not fn.endswith(".jsonl"):
                continue
            path = os.path.join(dirpath, fn)
            rel = os.path.relpath(path, root).replace("\\", "/")
            tier = "nested" if "/" in rel else "main"
            found.append((path, rel[:-6], tier))
    return found


def in_window(ts, since, until):
    if not ts:
        return True
    if since and ts < since:
        return False
    if until and ts > until:
        return False
    return True


def iter_records(path):
    try:
        fh = open(path, "r", encoding="utf-8", errors="replace")
    except OSError:
        return
    with fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                yield json.loads(line)
            except Exception:
                continue


class Aggregate:
    """Accumulates every metric across all sessions."""

    def __init__(self):
        self.sess = {}
        self.tool_counts = Counter()
        self.tool_fail = Counter()
        self.tool_soft_fail = Counter()
        self.bash_cmds = Counter()
        self.bash_fail_cmds = Counter()
        self.corrections = []
        self.big_results = []
        self.model_tokens = Counter()
        self.per_tool_result_chars = Counter()
        self.skill_invocations = Counter()
        self.agent_spawns = Counter()
        self.file_reads = Counter()
        self.daily = defaultdict(Counter)
        # requestId -> usage tuple already credited, so a repeated record for
        # the same API response corrects rather than re-adds. Global, not
        # per-session: a forked session replays another session's records.
        self.seen_requests = {}
        self.tier_totals = {"main": Counter(), "nested": Counter()}
        self.tier_files = Counter()
        self.session_tier = {}
        self.compactions = []

    def session(self, sid, size, tier):
        s = self.sess.setdefault(sid, Counter())
        s["_size"] = size
        self.session_tier[sid] = tier
        self.tier_files[tier] += 1
        return s

    def record_assistant(self, s, msg, ts, request_id):
        """Fold one assistant record into the totals, deduplicated by requestId.

        One API response is emitted as several JSONL records - thinking, text,
        and each tool_use - all repeating the same usage object verbatim, and a
        forked session replays them on top. Summing records therefore counts the
        same response many times. Keep the maximum usage seen per requestId and
        subtract the previously-credited amount, so a later record for the same
        id corrects the running total instead of adding to it.
        """
        u = msg.get("usage") or {}
        usage = (
            u.get("input_tokens", 0) or 0,
            u.get("output_tokens", 0) or 0,
            u.get("cache_read_input_tokens", 0) or 0,
            u.get("cache_creation_input_tokens", 0) or 0,
        )
        model = msg.get("model") or "?"
        if request_id:
            prev = self.seen_requests.get(request_id)
            if prev is not None and sum(usage) <= sum(prev):
                return
            self.seen_requests[request_id] = usage
            delta = usage if prev is None else tuple(
                n - p for n, p in zip(usage, prev))
            first_sighting = prev is None
        else:
            delta = usage
            first_sighting = True

        it, ot, cr, cc = delta
        if first_sighting:
            s["assistant_turns"] += 1
        s["in"] += it
        s["out"] += ot
        s["cache_read"] += cr
        s["cache_create"] += cc
        self.model_tokens[model] += ot
        if ts:
            d = self.daily[ts]
            d["out"] += ot
            d["cache_read"] += cr
            d["cache_create"] += cc
            d["in"] += it
            if first_sighting:
                d["turns"] += 1

    def record_tool_use(self, s, block, pending):
        name = block.get("name", "?")
        self.tool_counts[name] += 1
        s["tool_calls"] += 1
        inp = block.get("input") or {}
        cmd = ""
        if name in ("Bash", "PowerShell"):
            cmd = inp.get("command") or ""
            self.bash_cmds[re.sub(r"\s+", " ", cmd.strip())[:90]] += 1
        elif name == "Read":
            self.file_reads[inp.get("file_path") or ""] += 1
        elif name == "Skill":
            self.skill_invocations[inp.get("skill", "?")] += 1
        elif name in ("Agent", "Task"):
            self.agent_spawns[inp.get("subagent_type", "?")] += 1
        pending[block.get("id")] = (name, cmd)

    def record_tool_result(self, s, block, pending, sid):
        tool, cmd = pending.pop(block.get("tool_use_id"), ("?", ""))
        body = block.get("content")
        if isinstance(body, list):
            body = " ".join(x.get("text", "") for x in body if isinstance(x, dict))
        body = body if isinstance(body, str) else ""
        self.per_tool_result_chars[tool] += len(body)
        s["result_chars"] += len(body)

        # Hard failure is is_error alone. The body regex cannot be trusted for
        # a general tool: a source file containing "catch (e: Exception)" reads
        # as a failed Read, which over-counted Read failures 24.7x.
        hard = bool(block.get("is_error"))
        # The soft band stays mandatory but is restricted to tools whose result
        # body is process output. A gradle build runs backgrounded and reports
        # its failure inside a clean tool_result with no is_error, so dropping
        # the regex entirely would hide exactly the failures that matter most.
        soft = (not hard
                and tool in SOFT_BAND_TOOLS
                and bool(ERR.search(body[:4000])))

        if hard:
            self.tool_fail[tool] += 1
            s["tool_fail"] += 1
        if soft:
            self.tool_soft_fail[tool] += 1
            s["tool_soft_fail"] += 1
        if (hard or soft) and cmd:
            self.bash_fail_cmds[re.sub(r"\s+", " ", cmd.strip())[:90]] += 1
        if len(body) > 20000:
            self.big_results.append(
                (len(body), tool, sid, re.sub(r"\s+", " ", (cmd or body))[:120])
            )

    def record_compaction(self, s, event, ts):
        """Record a compaction boundary and the context it was carrying.

        The preTokens series is the acceptance metric for the bounded-loop
        ticket: it is the distribution of how much context had accumulated
        before a reset actually happened.
        """
        s["compactions"] += 1
        meta = event.get("compactMetadata") or {}
        pre = meta.get("preTokens")
        if isinstance(pre, (int, float)) and pre > 0:
            self.compactions.append({
                "pre_tokens": int(pre),
                "trigger": meta.get("trigger") or "unknown",
                "date": ts,
            })

    def record_user_text(self, s, text, sid, ts):
        s["user_msgs"] += 1
        if CORRECTION.search(text) and len(text) < 2000:
            self.corrections.append((sid, ts, text.strip()[:400]))


def scan_session(agg, path, sid, tier, since, until):
    s = agg.session(sid, os.path.getsize(path), tier)
    pending = {}
    for e in iter_records(path):
        ts = (e.get("timestamp") or "")[:10]
        if not in_window(ts, since, until):
            continue
        t = e.get("type")
        msg = e.get("message") or {}
        if t == "assistant":
            agg.record_assistant(s, msg, ts, e.get("requestId"))
            for c in msg.get("content") or []:
                if isinstance(c, dict) and c.get("type") == "tool_use":
                    agg.record_tool_use(s, c, pending)
        elif t == "system" and e.get("subtype") == "compact_boundary":
            agg.record_compaction(s, e, ts)
        elif t == "user":
            content = msg.get("content")
            if isinstance(content, str):
                agg.record_user_text(s, content, sid, ts)
            elif isinstance(content, list):
                for c in content:
                    if not isinstance(c, dict):
                        continue
                    if c.get("type") == "text":
                        agg.record_user_text(s, c.get("text") or "", sid, ts)
                    elif c.get("type") == "tool_result":
                        agg.record_tool_result(s, c, pending, sid)


def percentiles(values, points=(50, 75, 90, 95, 99)):
    """Nearest-rank percentiles. Empty input yields an empty dict."""
    if not values:
        return {}
    ordered = sorted(values)
    out = {}
    for p in points:
        idx = max(0, min(len(ordered) - 1,
                         int(round(p / 100.0 * len(ordered))) - 1))
        out[f"p{p}"] = ordered[idx]
    return out


def build_result(agg):
    tot = Counter()
    for s in agg.sess.values():
        tot.update(s)
    for sid, s in agg.sess.items():
        agg.tier_totals[agg.session_tier.get(sid, "main")].update(s)
    tiers = {
        name: {
            "files": agg.tier_files.get(name, 0),
            "totals": dict(counts),
            "tokens_per_turn": round(
                counts["out"] / counts["assistant_turns"], 1)
            if counts["assistant_turns"] else 0,
        }
        for name, counts in agg.tier_totals.items()
    }
    return tot, {
        "sessions": len(agg.sess),
        "requests": len(agg.seen_requests),
        "tiers": tiers,
        "compaction": {
            "count": len(agg.compactions),
            "pre_tokens_percentiles": percentiles(
                [c["pre_tokens"] for c in agg.compactions]),
            "by_trigger": dict(Counter(c["trigger"] for c in agg.compactions)),
            "events": agg.compactions,
        },
        "totals": dict(tot),
        "tool_counts": agg.tool_counts.most_common(),
        "tool_fail": agg.tool_fail.most_common(),
        "tool_soft_fail": agg.tool_soft_fail.most_common(),
        "failure_rates": {
            "calls": sum(agg.tool_counts.values()),
            "hard": sum(agg.tool_fail.values()),
            "soft": sum(agg.tool_soft_fail.values()),
            "hard_pct": round(
                100.0 * sum(agg.tool_fail.values())
                / max(1, sum(agg.tool_counts.values())), 2),
            "hard_plus_soft_pct": round(
                100.0 * (sum(agg.tool_fail.values())
                         + sum(agg.tool_soft_fail.values()))
                / max(1, sum(agg.tool_counts.values())), 2),
        },
        "tool_result_chars": agg.per_tool_result_chars.most_common(),
        "top_bash": agg.bash_cmds.most_common(120),
        "top_bash_fail": agg.bash_fail_cmds.most_common(80),
        "skills": agg.skill_invocations.most_common(),
        "agents": agg.agent_spawns.most_common(),
        "models": agg.model_tokens.most_common(),
        "top_reads": agg.file_reads.most_common(60),
        "big_results": sorted(agg.big_results, reverse=True)[:60],
        "corrections": agg.corrections[-400:],
        "daily": {k: dict(v) for k, v in sorted(agg.daily.items())},
        "per_session": {
            k: dict(v) for k, v in sorted(
                agg.sess.items(), key=lambda kv: -kv[1]["out"])[:40]
        },
    }


def render_digest(agg, tot):
    L = [f"sessions={len(agg.sess)} requests={len(agg.seen_requests)}"]
    L.append(
        "TOTALS: in={in} out={out} cache_read={cache_read} cache_create={cache_create} "
        "assistant_turns={assistant_turns} tool_calls={tool_calls} tool_fail={tool_fail} "
        "user_msgs={user_msgs} result_chars={result_chars}".format_map(
            defaultdict(int, tot))
    )
    pre = percentiles([c["pre_tokens"] for c in agg.compactions])
    trig = Counter(c["trigger"] for c in agg.compactions)
    L.append(
        f"\nCOMPACTIONS: n={len(agg.compactions)} "
        + " ".join(f"{k}={v}" for k, v in sorted(trig.items()))
        + (" preTokens " + " ".join(f"{k}={v}" for k, v in pre.items())
           if pre else " preTokens: none recorded")
    )
    L.append("\n== TIERS ==")
    for name in ("main", "nested"):
        c = agg.tier_totals.get(name, Counter())
        L.append(
            f"{name:7s} files={agg.tier_files.get(name, 0):5d} "
            f"turns={c['assistant_turns']:7d} out={c['out']:10d} "
            f"cache_read={c['cache_read']:13d}"
        )
    calls = sum(agg.tool_counts.values())
    hard = sum(agg.tool_fail.values())
    soft = sum(agg.tool_soft_fail.values())
    L.append(
        f"\nFAILURES: hard={hard} ({100.0 * hard / max(1, calls):.2f}%) "
        f"soft={soft} combined={100.0 * (hard + soft) / max(1, calls):.2f}% "
        f"of {calls} calls"
    )
    L.append("\n== TOOL MIX (calls / hard / soft / result MB) ==")
    for name, n in agg.tool_counts.most_common():
        L.append(
            f"{name:34s} calls={n:6d} hard={agg.tool_fail.get(name, 0):5d} "
            f"soft={agg.tool_soft_fail.get(name, 0):5d} "
            f"resultMB={agg.per_tool_result_chars.get(name, 0) / 1e6:8.1f}"
        )
    L.append("\n== TOP BASH/PWSH COMMANDS ==")
    for c, n in agg.bash_cmds.most_common(60):
        L.append(f"{n:5d}  {c}")
    L.append("\n== TOP FAILING COMMANDS ==")
    for c, n in agg.bash_fail_cmds.most_common(50):
        L.append(f"{n:5d}  {c}")
    L.append("\n== SKILLS ==")
    for c, n in agg.skill_invocations.most_common():
        L.append(f"{n:5d}  {c}")
    L.append("\n== SUBAGENTS ==")
    for c, n in agg.agent_spawns.most_common():
        L.append(f"{n:5d}  {c}")
    L.append("\n== MODELS (output tokens) ==")
    for c, n in agg.model_tokens.most_common():
        L.append(f"{n:12d}  {c}")
    L.append("\n== MOST RE-READ FILES ==")
    for c, n in agg.file_reads.most_common(40):
        L.append(f"{n:5d}  {c}")
    L.append("\n== BIGGEST TOOL RESULTS ==")
    for sz, tool, sid, snip in sorted(agg.big_results, reverse=True)[:40]:
        L.append(f"{sz / 1000:8.0f}k {tool:12s} {snip}")
    L.append("\n== DAILY ==")
    for d, v in sorted(agg.daily.items()):
        L.append(
            f"{d} turns={v['turns']:6d} out={v['out']:9d} "
            f"cache_read={v['cache_read']:12d} cache_create={v['cache_create']:10d}"
        )
    return L


def main(argv):
    args = parse_args(argv)
    root = os.path.expanduser(args.root)
    if not os.path.isdir(root):
        print(f"transcript root not found: {root}", file=sys.stderr)
        return 2
    sessions = discover_sessions(root)
    if not sessions:
        print(f"no .jsonl transcripts under: {root}", file=sys.stderr)
        return 2
    print(f"sessions: {len(sessions)}", file=sys.stderr)

    agg = Aggregate()
    for path, sid, tier in sessions:
        scan_session(agg, path, sid, tier, args.since, args.until)

    tot, res = build_result(agg)
    os.makedirs(args.out, exist_ok=True)
    with open(os.path.join(args.out, "transcript_metrics.json"), "w",
              encoding="utf-8") as f:
        json.dump(res, f, ensure_ascii=False, indent=1)

    lines = render_digest(agg, tot)
    with open(os.path.join(args.out, "transcript_digest.txt"), "w",
              encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("\n".join(lines[:80]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
