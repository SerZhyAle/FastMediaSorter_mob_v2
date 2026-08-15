"""S1599 research pass 2: is a zero-hit Grep waste, or is zero the answer?

Splits the 651 zero-hit calls into:
  - absence-check   : zero IS the expected verdict (probe tags, banned APIs)
  - recovered-wider : a later Grep for a related pattern in a WIDER scope hit
  - abandoned       : no follow-up Grep at all
and measures how often the recovery was purely a re-scope of the same pattern.
"""
import json
import os
import re
from collections import Counter

ROOT = "C:/Users/serzh/.claude/projects/p--ANDROID-FastMediaSorter-mob-v2"
SINCE = "2026-08-05"
UNTIL = "2026-08-12"

ABSENCE = re.compile(
    r"Timber\\?\.d\\?\(\"?S\d{4}|"      # probe-tag sweeps
    r"S\d{4}:|"                          # spec marker sweeps
    r"Log\\?\.d\\?\(|GlobalScope|System\.out|"   # neuroslop assertions
    r'="#|TODO\\?\(|NotImplementedError'
)


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


files = []
for dirpath, _d, filenames in os.walk(ROOT):
    for fn in filenames:
        if fn.endswith(".jsonl"):
            files.append(os.path.join(dirpath, fn))


def norm_path(p):
    return (p or "").replace("\\", "/").lower().rstrip("/")


seen = set()
verdict = Counter()
recovery_shape = Counter()
widen_examples = []
zero_total = 0

for path in files:
    pending = {}
    calls = []   # ordered [{inp, zero}]
    for e in iter_records(path):
        ts = (e.get("timestamp") or "")[:10]
        if ts and (ts < SINCE or ts > UNTIL):
            continue
        msg = e.get("message") or {}
        role = msg.get("role") or e.get("type")
        if role == "assistant":
            for b in msg.get("content") or []:
                if not isinstance(b, dict) or b.get("type") != "tool_use":
                    continue
                bid = b.get("id")
                if bid in seen or b.get("name") != "Grep":
                    continue
                seen.add(bid)
                rec = {"inp": b.get("input") or {}, "zero": None}
                calls.append(rec)
                pending[bid] = rec
        elif role == "user":
            content = msg.get("content")
            if not isinstance(content, list):
                continue
            for b in content:
                if not isinstance(b, dict) or b.get("type") != "tool_result":
                    continue
                rec = pending.pop(b.get("tool_use_id"), None)
                if rec is None:
                    continue
                body = b.get("content")
                if isinstance(body, list):
                    body = " ".join(x.get("text", "") for x in body
                                    if isinstance(x, dict))
                body = body if isinstance(body, str) else ""
                low = body.lower()
                rec["zero"] = ("no matches found" in low
                               or low.strip() in ("", "no files found"))

    for i, rec in enumerate(calls):
        if not rec["zero"]:
            continue
        zero_total += 1
        pat = rec["inp"].get("pattern") or ""
        if ABSENCE.search(pat):
            verdict["absence-check (zero is the answer)"] += 1
            continue
        # look ahead up to 4 Grep calls for a recovery
        found = None
        for nxt in calls[i + 1:i + 5]:
            if nxt["zero"]:
                continue
            npat = nxt["inp"].get("pattern") or ""
            p0, p1 = norm_path(rec["inp"].get("path")), norm_path(nxt["inp"].get("path"))
            same_pat = npat == pat
            token_overlap = bool(
                set(re.findall(r"[A-Za-z_]{4,}", pat.lower()))
                & set(re.findall(r"[A-Za-z_]{4,}", npat.lower())))
            wider = (p1 == "" and p0 != "") or (p0.startswith(p1) and p1 != p0)
            if same_pat and wider:
                found = "same pattern, wider scope"
            elif same_pat:
                found = "same pattern, different scope"
            elif token_overlap and wider:
                found = "related pattern, wider scope"
            elif token_overlap:
                found = "related pattern, same scope"
            if found:
                if wider and len(widen_examples) < 25:
                    widen_examples.append(
                        (pat[:50], rec["inp"].get("path", "")[:55],
                         npat[:50], nxt["inp"].get("path", "")[:55]))
                break
        if found:
            verdict["recovered by a later Grep"] += 1
            recovery_shape[found] += 1
        else:
            verdict["abandoned / no related follow-up"] += 1

out = []
w = out.append
w(f"zero-hit Grep calls analysed = {zero_total}")
w("")
w("## verdict")
for k, v in verdict.most_common():
    w(f"{v:5d}  {v/max(zero_total,1)*100:5.1f}%  {k}")
w("")
w("## recovery shape (of the recovered ones)")
tot = sum(recovery_shape.values())
for k, v in recovery_shape.most_common():
    w(f"{v:5d}  {v/max(tot,1)*100:5.1f}%  {k}")
w("")
w("## sample widen-recoveries  (zero: pattern @ path) -> (hit: pattern @ path)")
for a, ap, b, bp in widen_examples:
    w(f"  {a!r} @ {ap or '(repo)'}")
    w(f"    -> {b!r} @ {bp or '(repo)'}")

txt = "\n".join(out)
with open("temp/S1599/dig-grep2.txt", "w", encoding="utf-8") as fh:
    fh.write(txt)
print(txt)
