import json, glob, re, os

recs = [json.loads(l) for l in open('docs/DOCUMENT_REGISTRY.jsonl', encoding='utf-8') if l.strip()]
idx = [r for r in recs if r.get('indexable')]
sm = open('sitemap.xml', encoding='utf-8').read()
urls = set(re.findall(r'<loc>([^<]+)</loc>', sm))


def declared_url(path):
    try:
        head = open(path, encoding='utf-8', errors='ignore').read(2000)
    except OSError:
        return None
    m = re.search(r'^\s*(?:permalink|url|canonical)\s*:\s*["\']?([^"\'\n]+)', head, re.M)
    return m.group(1).strip() if m else None


total_files = 0
excluded = 0
announced = 0
unaccounted = []

for r in idx:
    ex = {e['path'] for e in r.get('sitemap_exclude', [])}
    files = []
    for p in r.get('paths', []):
        files += [f.replace(os.sep, '/') for f in glob.glob(p, recursive=True)]
    for f in sorted(set(files)):
        total_files += 1
        if f in ex:
            excluded += 1
            continue
        u = declared_url(f)
        if u and any(x.endswith(u) or u in x for x in urls):
            announced += 1
        else:
            unaccounted.append((r['id'], f, u))

print('files under indexable records :', total_files)
print('excluded with a reason        :', excluded)
print('announced via own front matter:', announced)
print('sitemap <url> entries         :', len(urls))
print()
print('neither announced nor excluded (%d):' % len(unaccounted))
for rid, f, u in unaccounted:
    print('   %-18s %-45s declared=%s' % (rid, f, u))
