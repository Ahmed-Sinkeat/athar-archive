# athar-archive — Deployment (Cloudflare Workers)

**Current (2026-07):** the site deploys as a **Worker with Static Assets**, not Pages.
`@astrojs/cloudflare` emits the Worker; static pages ship as assets alongside it.

```sh
pnpm build     # validate:content → gen-takhrij → astro build (prerenders ~10k chapter pages, ~3 min) → copy-content-assets → tafsir-frags → gen-book-chapters (moves pages to dist/r2-upload) → redirects → headers
pnpm deploy    # r2:upload (dist/r2-upload → BOOK_ASSETS bucket, md5-diffed + prunes stale) → wrangler deploy -c dist/server/wrangler.json
```

## Chapter prerender architecture (2026-07-11, the 1102 fix)

The free Workers plan allows ~10ms CPU/request; the old on-demand chapter route
(knowledge-graph build + markdown render per request) blew it under load →
error 1102 on a third of requests. Now **nothing renders at request time**:

1. `src/pages/book-pages/[slug]/[chapter].astro` prerenders every chapter of
   every chunked book at build time (shadow path; analysis in `src/lib/book-build.ts`).
2. `scripts/gen-book-chapters.ts` (post-build) moves those ~10k HTML files to
   `dist/r2-upload/pages/book/` and rewrites `/book-pages/` → `/book/` in them
   (canonical/og URLs). They can't stay static assets — 20k-file deploy ceiling.
3. `src/pages/book/[slug]/[chapter].ts` (the real URL) is a thin on-demand
   route: one R2 read, ~1ms CPU. `src/middleware.ts` adds edge caching on top.
   (Middleware alone can't do this — Astro runs no middleware for URLs that
   match no route, so the thin route must exist.)

**Ordering rule: R2 upload always BEFORE `wrangler deploy`.** The pages
reference the build's hashed `/_astro/*.css`; deploying the Worker first would
serve chapters pointing at deleted CSS. CI does this in the right order.

**What a deploy uploads:**

| Change | R2 upload | Time |
|---|---|---|
| add/edit one book (CMS or git) | just that book's chapter pages | seconds |
| add articles/questions/poems | nothing (static assets only) | — |
| design change (CSS/JS/layout) | all ~10k pages (~2.4GB) | ~15–45 min |

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
  deploy** (at ~8.5k — chapter pages and tafsir frags in R2 don't count);
  **R2 10GB free** (at ~2.5GB; doubling the library ≈ 5GB — fine); R2 writes
  1M/month free (a full design re-upload is ~10k). GitHub only stores source
  markdown (~200MB) — generated pages never touch it.

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

## 5. `/admin` access control

`/admin` (Sveltia CMS, see `docs/technology-stack.md`) writes straight to `main` via
GitHub — access is controlled by GitHub itself, not a site-level gate:

- **Who can sign in:** anyone with **write access** to the repo (GitHub → repo →
  **Settings → Collaborators**). Add/remove editors there.
- **Auth backend:** `public/admin/config.yml`'s `backend.base_url` points at a
  deployed `sveltia-cms-auth` Cloudflare Worker (GitHub OAuth). That worker holds
  `GITHUB_CLIENT_ID`/`GITHUB_CLIENT_SECRET`/`ALLOWED_DOMAINS` as secrets — rotate
  them there, not in this repo, if the OAuth App is ever recreated.
- `/admin` is `noindex` and unlinked from the site nav, but the page itself is
  publicly reachable — that's fine, since it's useless without a GitHub account
  that has repo write access. Add Cloudflare Access in front of it only if you
  want a second factor before the GitHub sign-in screen even loads.

## Auto-deploy & rollback

- Push to `main` → Pages builds & deploys automatically; PRs get preview URLs.
- **Rollback:** Pages → Deployments → choose a previous successful deployment → **Rollback**
  (or revert the commit and let Pages rebuild). Satisfies NFR-04 "rebuild from Git alone."

## Notes

- Pages builds **independently** of GitHub Actions CI. CI stays the quality gate
  (tests → validate:content → build → smoke → check:links → perf:budget → tsc); Pages only needs `pnpm build` to succeed.
- `_headers` / `_redirects` are regenerated into `dist/` every build — edit the generator scripts, never `dist/`.
- Deploying does not require branch protection (#13); they're independent.
