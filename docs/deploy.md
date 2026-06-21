# athar-archive — Deployment (Cloudflare Pages)

Static `dist/` → Cloudflare Pages, Git-connected. Every push to `main` builds & deploys;
pull requests get preview deployments. No adapter — the output is fully static.

## Repo readiness (done)

- `astro.config.ts`: `output: static`, `site: https://ahlalathar.com`, `trailingSlash: never`, `build.format: directory`.
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

Pages project → **Custom domains → Set up a custom domain**. Add `ahlalathar.com` and `www.ahlalathar.com`.

- **If `ahlalathar.com` is already a Cloudflare zone:** Pages auto-creates the DNS record (CNAME; apex via
  CNAME flattening). Just confirm and proceed.
- **If the domain is registered elsewhere (not on Cloudflare):** dashboard → **Add a site** → `ahlalathar.com` →
  copy the two Cloudflare **nameservers** → set them at your **registrar** → wait for activation → then add the custom domain.
- **If the domain isn't registered:** register it first (Cloudflare Registrar or any registrar), then follow the step above.

Canonical host = **apex** `ahlalathar.com` (matches `site` in `astro.config.ts`). Redirect `www` → apex with a
Cloudflare Redirect Rule.

## 4. Production checks

- HTTPS active (auto certificate); HTTP → HTTPS redirect on.
- `https://ahlalathar.com/sitemap.xml` and `/robots.txt` resolve; canonical tags read `https://ahlalathar.com/...`.
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
| Application domain | `ahlalathar.com` · path `/compose` |

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
