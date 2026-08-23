import json, glob, re, os

recs = [json.loads(l) for l in open('docs/DOCUMENT_REGISTRY.jsonl', encoding='utf-8') if l.strip()]
idx = [r for r in recs if r.get('indexable')]
sm = open('sitemap.xml', encoding='utf-8').read()
urls = set(re.findall(r'<loc>([^<]+)</loc>', sm))


def declared_url(path):
    """Return the address the file's own front matter declares, if any."""
    try:
        head = open(path, encoding='utf-8', errors='ignore').read(2000)
    except OSError:
        return None
    m = re.search(r'^\s*(?:permalink|url|canonical)\s*:\s*["\']?([^"\'\n]+)', head, re.M)
    return m.group(1).strip() if m else None


for r in idx:
    files = []
    for p in r.get('paths', []):
        files += [f.replace(os.sep, '/') for f in glob.glob(p, recursive=True)]
    files = sorted(set(files))
    announced, silent = [], []
    for f in files:
        u = declared_url(f)
        hit = u and any(x.endswith(u) or u in x for x in urls)
        (announced if hit else silent).append((f, u))
    print("== %s  announced=%d silent=%d" % (r['id'], len(announced), len(silent)))
    for f, u in silent:
        print("   SILENT %-55s declared=%s" % (f, u))
