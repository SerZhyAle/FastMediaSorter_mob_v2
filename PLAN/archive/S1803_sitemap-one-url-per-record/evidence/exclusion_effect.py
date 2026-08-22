import json, re, os

recs = [json.loads(l) for l in open('docs/DOCUMENT_REGISTRY.jsonl', encoding='utf-8') if l.strip()]


def declared_url(path):
    try:
        head = open(path, encoding='utf-8', errors='ignore').read(2000)
    except OSError:
        return None
    m = re.search(r'^\s*(?:permalink|url|canonical)\s*:\s*["\']?([^"\'\n]+)', head, re.M)
    return m.group(1).strip() if m else None


load_bearing, documentary = [], []
for r in recs:
    for e in r.get('sitemap_exclude', []):
        p = e['path']
        u = declared_url(p)
        (load_bearing if u else documentary).append((r['id'], p, u))

print('LOAD-BEARING (declare an address, so the exclusion actually withholds a page):')
for rid, p, u in load_bearing:
    print('   %-20s %-45s -> %s' % (rid, p, u))
print()
print('DOCUMENTARY (declare no address, so nothing could have announced them):')
for rid, p, u in documentary:
    print('   %-20s %s' % (rid, p))
print()
print('load-bearing=%d documentary=%d total=%d' % (len(load_bearing), len(documentary),
                                                   len(load_bearing) + len(documentary)))
