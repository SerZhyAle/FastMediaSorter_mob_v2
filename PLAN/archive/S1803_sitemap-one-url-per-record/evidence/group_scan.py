import json, glob, re, os

recs = [json.loads(l) for l in open('docs/DOCUMENT_REGISTRY.jsonl', encoding='utf-8') if l.strip()]
idx = [r for r in recs if r.get('indexable')]
sm = open('sitemap.xml', encoding='utf-8').read()
urls = set(re.findall(r'<loc>([^<]+)</loc>', sm))

for r in idx:
    files = []
    for p in r.get('paths', []):
        files += [f.replace(os.sep, '/') for f in glob.glob(p, recursive=True)]
    files = sorted(set(files))
    print("%-20s paths=%-2d files=%3d  audience=%-9s category=%s"
          % (r['id'], len(r.get('paths', [])), len(files), r.get('audience'), r.get('category')))
    for f in files:
        print("      ", f)

print("total sitemap urls:", len(urls))
