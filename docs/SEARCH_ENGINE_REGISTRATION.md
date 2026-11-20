<sub class="doc-stamp">26.11.20 10:40</sub>

# Search Engine Registration - Operator Runbook

How the project site gets into Google, Bing and Yandex, and how the engines are told about every change. Every service here is free. Ticket: **S1268**.

Site under registration: `https://serzhyale.github.io/FastMediaSorter_mob_v2/`

---

## 0. The one constraint that shapes everything

The site is a **GitHub Pages project site**: it lives in a subdirectory of `serzhyale.github.io`, not on its own domain.

- **DNS verification is impossible.** The `github.io` DNS zone belongs to GitHub, so a Search Console domain property cannot exist for this site.
- **The host root is not this repository's.** `https://serzhyale.github.io/` is served by the user-site repository, which also owns the host-root `robots.txt`. A crawler reads `robots.txt` only at the host root, so this repository's own `robots.txt` is never read.
- **No custom domain.** No CNAME is added to the user-site repository.

Host-root work - the host-root `robots.txt` with its `Sitemap:` lines, the host-root Search Console property, the Bing import and Yandex host verification - belongs to the sza.od.ua hub's promotion ticket, not to this repository.

---

## 1. What this repository holds

- The Google verification meta tag in the head of all 13 landing pages (`index.html` and its 12 locale pages). It verifies the URL-prefix property for `/FastMediaSorter_mob_v2/`. Removing it un-verifies the property, so it stays.
- `sitemap.xml`, generated from `docs/DOCUMENT_REGISTRY.jsonl` by `scripts/document_registry/generate.ps1`. It is never edited by hand.
- The IndexNow key file at the repository root (section 4).

No Bing or Yandex verification code is placed in the landing pages: Bing imports the Google property, and Yandex verifies hosts, not paths.

---

## 2. Google Search Console - the product property

The URL-prefix property for `https://serzhyale.github.io/FastMediaSorter_mob_v2/` is verified by the meta tag.

1. Open [Google Search Console](https://search.google.com/search-console) with the Google account that owns the verification, and pick the URL-prefix property for the full path.
2. **Sitemaps:** submit `sitemap.xml` and check it reports "Success" with a discovered-URL count. If it stays on "Couldn't fetch" while a URL Inspection live test of the same address passes, submit `sitemap-pages.xml`, which the deploy workflow publishes as a copy of `sitemap.xml`.
3. **URL Inspection:** inspect the landing page and request indexing; do the same for `index-ru.html` and `index-uk.html`, which are separate URLs.
4. A few days later read **Pages** (the indexing report). A "Not indexed" reason other than "Excluded by noindex" is worth fixing; the `nolegal*` pages are `noindex` on purpose.
5. Once the hub's host-root property exists, submit this product's `sitemap.xml` there as well. Submitting it to both properties is harmless.

---

## 3. Bing and Yandex

- **Bing Webmaster Tools** imports properties from Google Search Console, so it needs no verification code here. The import is done once, at the host root, by the hub ticket. Bing results also feed Yahoo and DuckDuckGo.
- **Yandex Webmaster** verifies a host, not a path, so this subdirectory cannot be verified on its own. Host verification is the hub ticket's. Yandex reaches the Russian-speaking audience outside Ukraine; it is blocked in Ukraine.

---

## 4. IndexNow - instant notification

IndexNow is a free open protocol: one HTTP request tells Bing, Yandex, Seznam and Naver that a URL changed, instead of waiting for a crawl. Google does not take part, which is why section 2 still matters.

- Key file: `16c034b6579b4e3eb8cecedb80f9ac79.txt` in the repository root, served as `https://serzhyale.github.io/FastMediaSorter_mob_v2/16c034b6579b4e3eb8cecedb80f9ac79.txt`.
- Its content is the key itself and nothing else - no newline, no quotes. The key is public by design.
- **Live check before any ping:** the key URL must answer 200. While it answers 404 every submission is rejected with HTTP 403, and IndexNow is recorded as "not live".

To notify the engines after a published site change:

```powershell
pwsh -NoProfile -File scripts/site/ping-indexnow.ps1
```

The script reads its URLs from `sitemap.xml`; `-Url` sends a subset and `-WhatIf` prints the payload without sending it.

---

## 5. Order of operations after any site change

1. Regenerate the derived views: `pwsh -NoProfile -File scripts/document_registry/generate.ps1`.
2. Push and wait for the GitHub Pages deploy to finish.
3. Check the IndexNow key URL answers 200, then ping IndexNow (section 4).
4. Only for a large or structural change: request indexing in Search Console for the affected pages.

---

## 6. Checklist

- [ ] Search Console product property: sitemap submitted and read with "Success", the three home pages inspected.
- [ ] IndexNow key URL answers 200; a ping was accepted.
- [ ] The Google verification meta tag present in all 13 landing pages.
- [ ] `nolegal*` pages still `noindex`.
- [ ] Host-root items (robots.txt, host property, Bing import, Yandex host): tracked in the hub ticket.

---

## 7. What not to do

- Do not submit the `nolegal*` pages anywhere.
- Do not buy links, submit to link directories, or use paid "indexing" services.
- Do not hand-edit `sitemap.xml`; edit the registry and regenerate.
- Do not add a `Disallow` for assets, a `<meta name="keywords">`, or markup for rich results the page does not show.
- Do not remove the Google verification meta tag.
