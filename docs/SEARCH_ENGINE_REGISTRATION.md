# Search Engine Registration - Operator Runbook

How to get the project site into Google, Bing and Yandex, and how to keep them informed after every
change. Every service here is free. Ticket: **S1268**.

Site under registration: `https://serzhyale.github.io/FastMediaSorter_mob_v2/`

---

## 0. The one constraint that shapes everything

The site is a **GitHub Pages project site**: it lives in a subdirectory of `serzhyale.github.io`,
not on its own domain. Two consequences:

- **DNS verification is impossible.** The `github.io` DNS zone belongs to GitHub.
- **The domain root is not ours.** `https://serzhyale.github.io/` is served by a different repository
  (or nothing at all). Only `/FastMediaSorter_mob_v2/` belongs to this repo.

So when a webmaster panel asks for the site address, always enter the **full path including the
subdirectory**, and verify with a **meta tag** (or a file placed in this repo's root, which lands in
the subdirectory). Never pick the DNS option.

---

## 1. Where verification codes go in this repo

Every panel gives you a code. Paste it into the `<head>` of **all three** landing pages, next to the
Google one that is already there:

- `index.html`
- `index-ru.html`
- `index-uk.html`

The existing Google line looks like this, and the others sit beside it:

```html
<meta name="google-site-verification" content="qwkzLSJrJ7adxFYz-95YY-Uhs0yX6QakWxZWFWtN-ks" />
<meta name="msvalidate.01" content="PASTE_BING_CODE_HERE" />
<meta name="yandex-verification" content="PASTE_YANDEX_CODE_HERE" />
```

Commit and push, wait for GitHub Pages to redeploy (about a minute), then press the verify button in
the panel. If a panel offers a file instead of a meta tag, drop that file in the repository root -
it becomes `https://serzhyale.github.io/FastMediaSorter_mob_v2/<file>`, which is what the panel
checks for a subdirectory property.

---

## 2. Google Search Console

The site is already verified here - the `google-site-verification` tag is in place. What remains:

1. Open [Google Search Console](https://search.google.com/search-console) with the Google account
   that owns the verification.
2. Make sure the property is the **URL prefix** type for
   `https://serzhyale.github.io/FastMediaSorter_mob_v2/`. A domain property cannot work here (DNS).
3. Go to **Sitemaps**, submit `sitemap.xml`, and check it reports "Success" with a page count.
4. Use **URL inspection** on the home page and request indexing. Do this once for the Russian and
   Ukrainian home pages too - they are separate URLs.
5. Come back after a few days and read **Pages** (indexing report). Anything under "Not indexed"
   with a reason other than "Excluded by noindex" is worth fixing; the `nolegal*` pages are
   deliberately `noindex` and should stay excluded.

---

## 3. Bing Webmaster Tools

Bing results also feed Yahoo and DuckDuckGo, so this one buys more reach than its market share
suggests.

1. Open [Bing Webmaster Tools](https://www.bing.com/webmasters) and sign in with a Microsoft,
   Google or Facebook account.
2. **Fastest path:** choose to **import from Google Search Console**. Bing pulls the property and
   trusts the existing Google verification, so no code is needed at all.
3. **If you prefer not to link the accounts:** add the site manually with the full address
   `https://serzhyale.github.io/FastMediaSorter_mob_v2/`, pick the **HTML meta tag** option, copy the
   `msvalidate.01` value into the three landing pages (section 1), push, then verify.
4. Once verified, open **Sitemaps** and submit
   `https://serzhyale.github.io/FastMediaSorter_mob_v2/sitemap.xml`.
5. Optional but useful: **URL submission** lets you push individual URLs for immediate crawling, and
   the daily quota is generous.

---

## 4. Yandex Webmaster

Worth doing because a large share of the audience for the Russian and Ukrainian pages searches here.

1. Open [Yandex Webmaster](https://webmaster.yandex.com/) and sign in with a Yandex account (create
   one if needed - it is free and requires no phone in most regions).
2. Add a site and enter the full address with the subdirectory.
3. Choose **Meta tag** verification, copy the `yandex-verification` value into the three landing
   pages (section 1), push, wait for the deploy, then press verify. The **HTML file** option also
   works: put the file Yandex gives you in the repository root.
4. After verification, go to **Indexing -> Sitemap files** and add
   `https://serzhyale.github.io/FastMediaSorter_mob_v2/sitemap.xml`.
5. In **Indexing -> Page crawl** you can submit individual pages for a faster first pass.
6. Set the site's regions and language in the site settings so the Russian and Ukrainian pages are
   shown to the right audience.

---

## 5. IndexNow - instant notification

IndexNow is a free open protocol: one HTTP request tells Bing, Yandex, Seznam and Naver that a URL
changed, instead of waiting for a crawl. Google does not participate, which is why sections 2 to 4
still matter.

The key file lives at the site root and must stay reachable - if it 404s, every submission is
rejected with HTTP 403:

- Key file: `16c034b6579b4e3eb8cecedb80f9ac79.txt` in the repository root, served as
  `https://serzhyale.github.io/FastMediaSorter_mob_v2/16c034b6579b4e3eb8cecedb80f9ac79.txt`
- Its content is the key itself and nothing else - no newline, no quotes.
- The key is public by design. It is not a secret and needs no protection.

To notify the engines after publishing a site change:

```powershell
pwsh -NoProfile -File scripts/site/ping-indexnow.ps1
```

The script reads the URL list from `sitemap.xml`, so it always matches what the registry generates.
Add `-WhatIf` to see the payload without sending it.

---

## 6. Order of operations after any site change

1. Regenerate the derived views: `pwsh -NoProfile -File scripts/document_registry/generate.ps1`
2. Commit and push, wait for the GitHub Pages deploy to finish.
3. Ping IndexNow (section 5).
4. Only if the change is large or structural: request indexing in Google Search Console for the
   affected pages.

---

## 7. Checklist

- [ ] Google Search Console: property is URL-prefix, sitemap submitted, home pages requested.
- [ ] Bing: verified (imported or meta tag), sitemap submitted.
- [ ] Yandex: verified, sitemap submitted, regions set.
- [ ] `16c034b6579b4e3eb8cecedb80f9ac79.txt` reachable over HTTPS.
- [ ] Verification meta tags present in all three landing pages.
- [ ] `nolegal*` pages still `noindex` - they are sideload pages and must not be indexed.

---

## 8. What not to do

- Do not submit the `nolegal*` pages anywhere. They are intentionally hidden.
- Do not buy links, submit to link directories, or use paid "indexing" services. They range from
  useless to actively harmful, and this project ships without paid services by policy.
- Do not hand-edit `sitemap.xml`. It is generated from `docs/DOCUMENT_REGISTRY.jsonl`; edit the
  registry and regenerate.
