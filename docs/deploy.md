# athar-archive — Deployment (Cloudflare Workers)

**Current (2026-07):** the site deploys as a **Worker with Static Assets**, not Pages.
`@astrojs/cloudflare` emits the Worker; static pages ship as assets alongside it.

```sh
pnpm build       # validate:content → astro build (prerenders ~78k chapter pages; CI shards this 8 ways, ~14 min) → copy-content-assets → gen-book-chapters (gzips pages into dist/r2-upload/pages-v2) → redirects → headers
pnpm deploy      # r2:upload (dist/r2-upload → BOOK_ASSETS bucket, md5-diffed + prunes stale) → wrangler deploy -c dist/server/wrangler.json
pnpm r2:inventory # READ-ONLY: object count + bytes per top-level R2 prefix, and which prefixes this repo owns
```

## Chapter prerender architecture (2026-07-11, the 1102 fix)

The free Workers plan allows ~10ms CPU/request; the old on-demand chapter route
(knowledge-graph build + markdown render per request) blew it under load →
error 1102 on a third of requests. Now **nothing renders at request time**:

1. `src/pages/book-pages/[slug]/[chapter].astro` prerenders every chapter of
   every chunked book at build time (shadow path; analysis in `src/lib/book-build.ts`).
2. `scripts/gen-book-chapters.ts` (post-build) moves those ~78k HTML files to
   `dist/r2-upload/pages-v2/book/` as **`<chapter>.html.gz`** and rewrites
   `/book-pages/` → `/book/` in them (canonical/og URLs). They can't stay static
   assets — 20k-file deploy ceiling.
3. `src/pages/book/[slug]/[chapter].ts` (the real URL) is a thin on-demand
   route: one R2 read, inflate, substitute. `src/middleware.ts` adds edge
   caching on top, so this runs on cache misses only. (Middleware alone can't do
   this — Astro runs no middleware for URLs that match no route, so the thin
   route must exist.)

## Gzipped chapter storage (`pages-v2/`)

Chapter pages are stored gzipped: measured **~84% smaller** than the
uncompressed `pages/` prefix they replace (16.15% of raw over 400 real pages at
zlib level 6). The same change roughly quarters the r2-staging copy the finish
runner has to hold on its very tight disk.

- **Deterministic gzip is load-bearing.** The uploader skips a page whose md5
  matches R2's ETag, so one volatile byte would re-upload the entire corpus on
  every deploy. `gzipStable()` pins the gzip MTIME and OS header bytes;
  `tsx scripts/gen-book-chapters.ts --selftest` checks it (CI runs this).
- **Compression runs on the libuv threadpool** (`zlib.gzip`, async, pool sized
  to the CPU count) — 9.8 MB/s single-threaded vs 22+ MB/s here. `build:finish`
  sets `UV_THREADPOOL_SIZE=8`; without it this silently caps at libuv's 4.
- **`CHAPTERS_V2` is the rollout switch**, written into the Worker's vars by
  `gen-book-chapters.ts` from the CI variable of the same name:
  `""` (default) serves the old `pages/`, `"slug-a,slug-b"` canaries those
  books, `"*"` serves everything from `pages-v2/`. The route falls back to
  `pages/` whenever a v2 object is missing **or fails to inflate**, so flipping
  it can never 404 or 500 a chapter.
- The Worker inflates with a native `DecompressionStream` and returns plain
  HTML, so clients need no gzip support and nothing about content negotiation
  changes.

**Known, unrelated:** this route sends `no-transform` (to stop Bot Fight Mode
injecting a per-request inline script the hash-based CSP then blocks), and
Cloudflare honours it by *also* skipping compression — a chapter measured
917,689 bytes on the wire uncompressed, where the same page as a static asset
comes back brotli'd at ~107 KB. Storing gzipped does not change that; fixing it
means either dropping `no-transform` or serving the stored bytes with
`Content-Encoding: gzip` (which needs the per-request substitution below to go
away first).

**Ordering rule: R2 upload always BEFORE `wrangler deploy`.** The pages
reference the build's hashed `/_astro/*.css`; deploying the Worker first would
serve chapters pointing at deleted CSS. CI does this in the right order.

**Volatile-bytes rule (2026-07-25).** Stored pages must be byte-identical across
builds when their content hasn't changed, or every deploy re-uploads all 78k of
them. Two things are therefore *blanked/tokenised* by `gen-book-chapters.ts` and
restamped per request by the thin route:

| In the stored page | Restored at serve time from |
|---|---|
| `/_astro-live/<name>.<ext>` placeholders | `vars.CHAPTER_ASSETS` (this deploy's hashes) |
| `<meta name="aa-build" content="">` | `__AA_BUILD__` |

The build id was missed originally, which silently defeated the whole scheme —
`Date.now()` at config load meant every page's md5 changed every build, so all
78,130 re-uploaded every deploy (~14 min of an ~58 min pipeline). **Anything else
per-build added to these pages must be tokenised the same way.**

**What a deploy uploads:**

| Change | R2 upload | Time |
|---|---|---|
| add/edit one book (CMS or git) | just that book's chapter pages | seconds |
| add articles/questions/poems | nothing (static assets only) | — |
| design change (CSS/JS/layout) | **nothing** (placeholders absorb it) | ~3 min (scan only) |
| chapter markup/template change | all ~78k pages (~2.4GB) | ~15–45 min |

- Live host: `athar.arthurarchive.com` — a **subdomain**, added via Worker →
  **Settings → Domains & Routes → Add custom domain** (not Pages, and not the
  apex). `site`/`siteUrl` in `astro.config.ts` / `ahlalathar.config.ts` match
  this. `arthurarchive.com` is itself a placeholder domain (real one,
  `athararchive.com`, pending purchase) — when that's bought, repeat the same
  Domains & Routes step with the new domain/subdomain and flip `site`/`siteUrl`
  again.
- The DNS/nameserver steps in the historical Pages section below still apply
  to getting a new zone onto Cloudflare in the first place, but the actual
  "add custom domain" step happens on the **Worker**, not a Pages project.
- Deploys **do** run through GitHub: `.github/workflows/ci.yml` builds, tests, validates,
  uploads R2 pages, and deploys to Cloudflare (+ refreshes the D1 search index) on every
  push to `main`, gated on `CLOUDFLARE_API_TOKEN`/`CLOUDFLARE_ACCOUNT_ID` repo secrets.
  `pnpm deploy` from a local machine also works but skips CI's checks — prefer pushing
  to `main` unless you need a manual out-of-band deploy.
- Limits and current usage (2026-07): **25 MiB per asset**; **20,000 files per
  deploy** (at ~8.5k — chapter pages in R2 don't count);
  **R2 10GB free** (at ~2.5GB; doubling the library ≈ 5GB — fine); R2 writes
  1M/month free (a full design re-upload is ~10k). GitHub only stores source
  markdown (~200MB) — generated pages never touch it.

## R2 prefix ownership

`scripts/lib/r2.mjs` declares three lists, and they are the whole contract:

- **`OWNED_PREFIXES`** (`pages-v2/`) — the uploader lists these on every deploy and
  prunes anything under them that the build no longer emits. Ownership is
  *declared*, not inferred from `dist/r2-upload`: the old code listed only the
  prefixes that happened to exist locally, so a prefix the build stopped
  emitting silently stopped being listed and its objects stayed in R2 forever.
  A local directory that is **not** in this list now fails the upload loudly
  instead of being uploaded into a corner nothing manages.
- **`LEGACY_PREFIXES`** (`pages/`) — still *serving* as the route's fallback,
  no longer generated. Deliberately neither owned (the uploader would try to
  prune all ~78k objects) nor retired (`r2-cleanup.yml` must keep refusing it).
  Move it to `RETIRED_PREFIXES` only after `CHAPTERS_V2="*"` has been live and
  monitored and the route's fallback is deleted — that is what makes it dead.
- **`RETIRED_PREFIXES`** (`tafsir-frag/`, `app/`) — prefixes this repo used to
  own and no longer generates. The uploader never touches them. `pnpm
  r2:inventory` reports their object count and bytes so the deletion is a
  reviewed decision made against real numbers. Delete them deliberately, then
  drop the entry from the list.

Anything in neither list (e.g. `build-data/`, written by a different job) is
left strictly alone.

The mass-deletion guard is unchanged and independent of all this: a prune of
more than 500 objects **and** more than 10% of the bucket is refused, because a
deletion that large almost always means an incomplete local build rather than a
real removal (a half-empty shard merge once pruned ~37k live chapter pages).
Override only with `PRUNE_ALLOW_LARGE=1`, deliberately.

## Search index (D1) — resumable, budgeted

`gen-search-index.ts` diffs against the remote `doc_hash` table and emits SQL for
changed docs only. Two rules keep a corpus-wide change survivable:

1. **One URL = one self-contained unit** — its `DELETE`s, its rows, then its
   `doc_hash` upsert — and a new SQL file only ever starts *between* units. A file
   that fails, or is never reached, leaves exactly those URLs' hashes untouched,
   so the next deploy resumes precisely there.
2. **`SEARCH_ROW_BUDGET`** (default 40,000) caps *real rows written per run*,
   deletes included — D1 bills a deleted row like an inserted one, so a changed
   URL costs roughly `rows × 2 + 2`.

**Why it matters.** D1's free plan refuses writes past 100k rows/day. Before this,
every `DELETE` (including `DELETE FROM doc_hash`) was emitted first and every hash
upsert last, so a quota-truncated run advanced **no** hashes while having emptied
the hash table — the next run then saw an empty hash set, fell into full-rebuild
mode, `DROP`ped `docs`, and got no further. A thrash loop that broke search rather
than merely delaying it.

**Operational notes**

- CI logs `::warning::search index budget reached — N of M url(s) emitted` when
  work is deferred. **That warning is expected progress, not a failure.**
- A corpus-wide change (e.g. folding author names into the title, which dirtied
  ~124k of 126.5k URLs) takes several daily runs to converge. Search stays fully
  usable throughout — old rows are replaced in place, never dropped.
- Deploying more than once a day does not speed it up; later runs hit the quota,
  fail, and get retried, which is now harmless.
- On Workers Paid (50M rows written/month included), raise `SEARCH_ROW_BUDGET`
  and the whole corpus lands in one run.
- The D1 refresh step runs **after** the Worker deploy, so a slow or partial
  reindex never delays the site going live.

---

## Historical: original Cloudflare Pages runbook (superseded — never went live)

The rest of this file documents the earlier fully-static Pages plan, kept for the
DNS/domain steps. The build has since gained an adapter and on-demand routes, so the
"no adapter" claims below no longer hold.

## Repo readiness (done)

- `astro.config.ts`: `output: static`, `site: https://arthurarchive.com`, `trailingSlash: never`, `build.format: directory`.
- `pnpm build` emits `dist/` including `_headers` (CSP, from `scripts/gen-headers.mjs`) and
  `_redirects` (aliases → 301, from `scripts/gen-redirects.ts`) — Cloudflare Pages applies both natively.
- `.node-version` pins Node 22 (matches CI; local dev may run newer).

## 1. Create the Pages project

Cloudflare dashboard → **Workers & Pages → Create → Pages → Connect to Git**.

- Authorize the **Cloudflare GitHub App** on `Ahmed-Sinkeat/athar-archive` (private repo → grant access to this repo).
- Select the repo. Production branch: **`main`**.

**Build settings:**

| Setting | Value |
|---|---|
| Framework preset | Astro |
| Build command | `pnpm build` |
| Build output directory | `dist` |
| Root directory | `/` (default) |

**Environment variables** (Production *and* Preview):

| Variable | Value |
|---|---|
| `NODE_VERSION` | `22` |
| `PNPM_VERSION` | `11.8.0` |

Save & Deploy. The build runs `pnpm install` then `pnpm build`
(`validate:content → astro build → pagefind → _redirects → _headers`).

## 2. Verify the preview (`*.pages.dev`)

- Pages: home `/`, a content page `/poem/al-bayquniyyah`, a list `/poems`, `/search` (Pagefind loads its index).
- Redirect: `/poem/bayquniyyah` → `/poem/al-bayquniyyah` (301).
- Headers: `curl -sI https://<project>.pages.dev/ | grep -i content-security-policy` returns the CSP.
- **CSP / CSSOM check (issue #4):** confirm reading-prefs (font scale, theme) and the progress bar work under the
  *enforced* CSP. Expected fine — CSP does not govern CSSOM `.style` — but this is the first enforced-CSP run.
- **Audio 404 (expected, issue #12):** R2 is not provisioned yet, so audio URLs won't resolve.

## 3. Custom domain + DNS

Pages project → **Custom domains → Set up a custom domain**. Add `arthurarchive.com` and `www.arthurarchive.com`.

- **If `arthurarchive.com` is already a Cloudflare zone:** Pages auto-creates the DNS record (CNAME; apex via
  CNAME flattening). Just confirm and proceed.
- **If the domain is registered elsewhere (not on Cloudflare):** dashboard → **Add a site** → `arthurarchive.com` →
  copy the two Cloudflare **nameservers** → set them at your **registrar** → wait for activation → then add the custom domain.
- **If the domain isn't registered:** register it first (Cloudflare Registrar or any registrar), then follow the step above.

Canonical host = **apex** `arthurarchive.com` (matches `site` in `astro.config.ts`). Redirect `www` → apex with a
Cloudflare Redirect Rule.

## 4. Production checks

- HTTPS active (auto certificate); HTTP → HTTPS redirect on.
- `https://arthurarchive.com/sitemap.xml` and `/robots.txt` resolve; canonical tags read `https://arthurarchive.com/...`.
- Re-run the §2 checks on the apex domain.

## 5. Editor access (`/admin` removed)

The Sveltia CMS at `/admin` was **deleted on 2026-08-11**. It had never been the
authoring path — every commit in the repo's history is plain Git — and it could
only ever see the books under `src/content/book/`, not the far larger
`src/content/book-lg/`. Content is authored through the GitHub web UI; see
[`adding-content.ar.md`](./adding-content.ar.md).

**Access control is therefore just GitHub's:** grant or revoke **write access**
to the repo under GitHub → repo → **Settings → Collaborators**.

**Leftovers to clean up outside this repo** (deleting the panel does not touch them):

- The `sveltia-cms-auth` Cloudflare Worker at
  `sveltia-cms-auth.ahmedsinkeat2002.workers.dev` is still deployed and still
  holds `GITHUB_CLIENT_ID` / `GITHUB_CLIENT_SECRET` / `ALLOWED_DOMAINS`.
  Delete it: `wrangler delete --name sveltia-cms-auth`
- The corresponding **GitHub OAuth App** can then be deleted under GitHub →
  Settings → Developer settings → OAuth Apps.
- `.env` may still carry unused `KEYSTATIC_*` keys from the earlier CMS.

## Auto-deploy & rollback

- Push to `main` → Pages builds & deploys automatically; PRs get preview URLs.
- **Rollback:** Pages → Deployments → choose a previous successful deployment → **Rollback**
  (or revert the commit and let Pages rebuild). Satisfies NFR-04 "rebuild from Git alone."

## Notes

- Pages builds **independently** of GitHub Actions CI. CI stays the quality gate
  (tests → validate:content → build → smoke → check:links → perf:budget → tsc); Pages only needs `pnpm build` to succeed.
- `_headers` / `_redirects` are regenerated into `dist/` every build — edit the generator scripts, never `dist/`.
- Deploying does not require branch protection (#13); they're independent.
