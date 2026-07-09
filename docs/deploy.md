# athar-archive — Deployment (Cloudflare Workers)

**Current (2026-07):** the site deploys as a **Worker with Static Assets**, not Pages.
`@astrojs/cloudflare` emits the Worker (on-demand reading routes + `ASSETS` binding);
static pages ship as assets alongside it.

```sh
pnpm build     # validate:content → astro build → copy-content-assets → tafsir-frags/book-chapters (dist/r2-upload) → redirects → headers
pnpm deploy    # r2:upload (dist/r2-upload → BOOK_ASSETS bucket) → wrangler deploy -c dist/server/wrangler.json → workers.dev host
```

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
  and deploys to Cloudflare (+ refreshes the D1 search index) on every push to `main`,
  gated on `CLOUDFLARE_API_TOKEN`/`CLOUDFLARE_ACCOUNT_ID` repo secrets. `pnpm deploy`
  from a local machine also works (same wrangler command) but skips CI's checks —
  prefer pushing to `main` unless you need a manual out-of-band deploy.
- Limits to respect: **25 MiB per asset**, **20,000 files per deploy** (see `technology-stack.md`).
  Book chapter bodies and tafsir fragments no longer count against this — they're
  uploaded to the `BOOK_ASSETS` R2 bucket by `pnpm deploy` instead of shipped as
  Worker assets (see `docs/HANDOFF-perf-size.md` §M4).

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

## 5. Lock down `/compose` (Cloudflare Access)

`/compose` is the maintainer authoring tool. It never writes to the site — it only
generates a `.md` file you copy/commit, so production is only ever changed through Git.
It's `noindex`, excluded from the sitemap, `Disallow`ed in `robots.txt`, and unlinked from
the site. To stop anyone who knows the URL from opening it, gate the path at the edge —
no app code, no password in the bundle:

Cloudflare dashboard → **Zero Trust → Access → Applications → Add an application → Self-hosted**.

| Field | Value |
|---|---|
| Application name | `Athar compose` |
| Session duration | `24 hours` (your choice) |
| Application domain | `arthurarchive.com` · path `/compose` |

Add a second domain row for `*.pages.dev` path `/compose` if you also want preview deploys gated.

Then **Add a policy**: Action **Allow**, Include → **Emails** → `ahmedsinkeat2002@gmail.com`.
For the login method, **One-time PIN** (email code) needs no identity-provider setup; or wire Google.

Save. Now `/compose` prompts for your email + a one-time code; everyone else is blocked at
Cloudflare's edge before the page loads. Zero Trust is free up to 50 users.

## Auto-deploy & rollback

- Push to `main` → Pages builds & deploys automatically; PRs get preview URLs.
- **Rollback:** Pages → Deployments → choose a previous successful deployment → **Rollback**
  (or revert the commit and let Pages rebuild). Satisfies NFR-04 "rebuild from Git alone."

## Notes

- Pages builds **independently** of GitHub Actions CI. CI stays the quality gate
  (tests → validate:content → build → smoke → check:links → perf:budget → tsc); Pages only needs `pnpm build` to succeed.
- `_headers` / `_redirects` are regenerated into `dist/` every build — edit the generator scripts, never `dist/`.
- Deploying does not require branch protection (#13); they're independent.
