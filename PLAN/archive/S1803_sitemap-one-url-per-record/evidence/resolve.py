import json, glob, re, os, io

recs = [json.loads(l) for l in open('docs/DOCUMENT_REGISTRY.jsonl', encoding='utf-8') if l.strip()]
sm = open('sitemap.xml', encoding='utf-8').read()
urls = re.findall(r'<loc>([^<]+)</loc>', sm)

SITE = 'https://serzhyale.github.io/FastMediaSorter_mob_v2'


def declared_url(path):
    try:
        head = open(path, encoding='utf-8', errors='ignore').read(2000)
    except OSError:
        return None
    m = re.search(r'^\s*(?:permalink|url|canonical)\s*:\s*["\']?([^"\'\n]+)', head, re.M)
    return m.group(1).strip() if m else None


# Map every declared address to the file that declares it.
by_addr = {}
for r in recs:
    for p in r.get('paths', []):
        for f in glob.glob(p, recursive=True):
            f = f.replace(os.sep, '/')
            u = declared_url(f)
            if u:
                by_addr.setdefault(u.rstrip('/'), []).append(f)

lines = []
unresolved = 0
for u in urls:
    rel = u[len(SITE):] if u.startswith(SITE) else u
    rel = rel.rstrip('/')
    src = by_addr.get(rel) or by_addr.get('/' + rel.lstrip('/'))
    if not src:
        # The landing group declares its addresses as real files rather than in front matter.
        cand = rel.lstrip('/') or 'index.html'
        if os.path.exists(cand):
            src = [cand]
    if src:
        lines.append('%-72s <- %s' % (u, ', '.join(src)))
    else:
        unresolved += 1
        lines.append('%-72s <- UNRESOLVED' % u)

lines.append('')
lines.append('entries=%d unresolved=%d' % (len(urls), unresolved))
with io.open('PLAN/S1803_sitemap-one-url-per-record/evidence/address-resolution.txt', 'w', encoding='utf-8', newline='\n') as f:
    f.write('\n'.join(lines) + '\n')
print('\n'.join(lines[-1:]))
print('unresolved rows:', unresolved)
