"""S1599 research: characterise zero-hit Grep calls and Grep->Grep series.

Fixes the instrument bug in temp/scratch/week-audit/dig2.py, which reported the
most frequent Grep patterns overall under the heading "zero-hit patterns".
"""
import json
import os
import re
from collections import Counter

ROOT = "C:/Users/serzh/.claude/projects/p--ANDROID-FastMediaSorter-mob-v2"
SINCE = "2026-08-05"
UNTIL = "2026-08-12"


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

seen = set()
total = 0
zero = 0
zero_patterns = Counter()
zero_scope = Counter()          # how the zero-hit call was scoped
zero_kind = Counter()           # semantic class of the zero-hit pattern
pairs_total = 0
pairs_same_scope = 0
pairs_after_zero = 0
pairs_after_zero_same_scope = 0
pair_examples = []
zero_examples = []
# recovery: zero-hit call -> what the very next Grep did
recovery = Counter()

HEADING = re.compile(r"^\^?#{1,6}[ |]|^\^?\\#")
DOCSTRUCT = re.compile(
    r"^\^?(#{1,6}|\\#)|"
    r"^(Stage|Step|Phase|Section|Implementation State|Last Audit|Step Log)\b",
    re.I)
KOTLIN_ID = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")
REGEXY = re.compile(r"[\\\[\]()|*+?^$]")


def classify(pat, inp):
    if not pat:
        return "empty"
    if DOCSTRUCT.search(pat):
        return "doc-structure"
    if REGEXY.search(pat):
        return "regex"
    if KOTLIN_ID.match(pat):
        if pat[0].isupper():
            return "identifier-type"
        return "identifier-member"
    return "literal-phrase"


def scope_of(inp):
    return (inp.get("path") or "", inp.get("glob") or "", inp.get("type") or "")


for path in files:
    pending = {}
    last_grep = None        # (inp, was_zero)
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
                if bid in seen:
                    continue
                seen.add(bid)
                if b.get("name") != "Grep":
                    continue
                inp = b.get("input") or {}
                total += 1
                if last_grep is not None:
                    prev_inp, prev_zero = last_grep
                    pairs_total += 1
                    same = scope_of(prev_inp) == scope_of(inp)
                    if same:
                        pairs_same_scope += 1
                    if prev_zero:
                        pairs_after_zero += 1
                        if same:
                            pairs_after_zero_same_scope += 1
                        p0 = prev_inp.get("pattern") or ""
                        p1 = inp.get("pattern") or ""
                        if p1 and p0 and (p0 in p1 or p1 in p0):
                            recovery["substring-of-previous"] += 1
                        elif same:
                            recovery["same-scope-new-pattern"] += 1
                        else:
                            recovery["rescoped"] += 1
                        if len(pair_examples) < 40:
                            pair_examples.append((p0, p1, same))
                    elif same:
                        if len(pair_examples) < 40:
                            pass
                pending[bid] = inp
                last_grep = (inp, False)
        elif role == "user":
            content = msg.get("content")
            if not isinstance(content, list):
                continue
            for b in content:
                if not isinstance(b, dict) or b.get("type") != "tool_result":
                    continue
                inp = pending.pop(b.get("tool_use_id"), None)
                if inp is None:
                    continue
                body = b.get("content")
                if isinstance(body, list):
                    body = " ".join(x.get("text", "") for x in body
                                    if isinstance(x, dict))
                body = body if isinstance(body, str) else ""
                low = body.lower()
                is_zero = ("no matches found" in low
                           or low.strip() in ("", "no files found"))
                if is_zero:
                    zero += 1
                    pat = (inp.get("pattern") or "")
                    zero_patterns[pat[:60]] += 1
                    zero_kind[classify(pat, inp)] += 1
                    sc = []
                    if inp.get("path"):
                        sc.append("path")
                    if inp.get("glob"):
                        sc.append("glob")
                    if inp.get("type"):
                        sc.append("type")
                    zero_scope["+".join(sc) or "unscoped"] += 1
                    if len(zero_examples) < 60:
                        zero_examples.append(
                            (pat[:70], inp.get("path", "")[:60],
                             inp.get("glob", "")))
                    if last_grep is not None and last_grep[0] is inp:
                        last_grep = (inp, True)

out = []
w = out.append
w(f"window {SINCE}..{UNTIL}  files={len(files)}")
w(f"Grep total={total} zero={zero} ({zero/total*100:.1f}%)" if total else "no data")
w("")
w("## zero-hit pattern KIND")
for k, v in zero_kind.most_common():
    w(f"{v:5d}  {v/max(zero,1)*100:5.1f}%  {k}")
w("")
w("## zero-hit SCOPE")
for k, v in zero_scope.most_common():
    w(f"{v:5d}  {v/max(zero,1)*100:5.1f}%  {k}")
w("")
w("## top zero-hit patterns")
for k, v in zero_patterns.most_common(35):
    w(f"{v:5d}  {k}")
w("")
w(f"## Grep->Grep pairs total={pairs_total}")
w(f"  same scope (path/glob/type identical) = {pairs_same_scope} "
  f"({pairs_same_scope/max(pairs_total,1)*100:.1f}%)")
w(f"  pair where FIRST returned zero        = {pairs_after_zero} "
  f"({pairs_after_zero/max(pairs_total,1)*100:.1f}%)")
w(f"  ... of those, same scope              = {pairs_after_zero_same_scope}")
w("  recovery shape after a zero-hit:")
for k, v in recovery.most_common():
    w(f"{v:5d}  {k}")
w("")
w("## sample zero-hit calls (pattern | path | glob)")
for p, pa, g in zero_examples:
    w(f"  {p!r} | {pa} | {g}")
w("")
w("## sample zero-then-next pairs (prev -> next, same_scope)")
for a, b, s in pair_examples[:30]:
    w(f"  {a!r} -> {b!r}  same={s}")

txt = "\n".join(out)
os.makedirs("temp/S1599", exist_ok=True)
with open("temp/S1599/dig-grep.txt", "w", encoding="utf-8") as fh:
    fh.write(txt)
print(txt)
