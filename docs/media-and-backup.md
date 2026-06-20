# Media Pipeline & Backup / Recovery

How audio and attachments are stored, served, and recovered. The **site** is
rebuildable from Git alone (`NFR-04`); **media** lives in object storage and is
backed up separately.

---

## 1. Storage — Cloudflare R2

- **Bucket:** `athar-media` (S3-compatible).
- **Public host:** `https://r2.ahlalathar.com` (R2 custom domain / public bucket binding). Already allowed by `media-src`/`img-src` in `public/_headers`.
- **Why R2:** no egress fees, S3 API, edge-served. Audio in **Opus** (small, high quality).

### Key convention

```
audio/<source-type>/<source-id>.opus     e.g. audio/lesson/sharh-al-wasitiyyah--lesson-1.opus
covers/<book-id>.webp
pdf/<book-id>.pdf      epub/<book-id>.epub
```

URLs are stored **explicitly** in content frontmatter (the `Audio` entity's `url`,
or a book's `attachments[].url`/`cover`) — not derived — so storage can move
without touching the model. Keep them under `r2.ahlalathar.com`.

---

## 2. Encoding audio → Opus

```bash
# voice/lecture (mono, ~32 kbps is plenty for speech)
ffmpeg -i input.mp3 -c:a libopus -b:a 32k -ac 1 -application voip \
  -vbr on -compression_level 10 output.opus

# recitation / richer audio (stereo, ~48–64 kbps)
ffmpeg -i input.wav -c:a libopus -b:a 48k -application audio output.opus
```

Then capture metadata for the `Audio` entity frontmatter:

```bash
ffprobe -v error -show_entries format=duration -of csv=p=0 output.opus   # → duration
stat -c%s output.opus                                                    # → size_bytes
```

`duration` is `h:mm:ss` or `mm:ss`; `size_bytes` is an integer. Both are validated
by Zod. A `Lesson` **cannot publish without a transcript** (its Markdown body) —
enforced at build time (`FR-W-05`).

---

## 3. Upload

Use `scripts/upload-media.sh` (rclone). Configure an `r2` remote once:

```bash
rclone config create r2 s3 provider Cloudflare \
  access_key_id "$R2_ACCESS_KEY_ID" secret_access_key "$R2_SECRET_ACCESS_KEY" \
  endpoint "https://<accountid>.r2.cloudflarestorage.com"
```

```bash
R2_BUCKET=athar-media ./scripts/upload-media.sh ./local-media   # mirrors into the bucket
```

Credentials live in the environment / a secrets manager — **never** in the repo
(`.env` is gitignored; `SEC-03`).

---

## 4. Backups

| What | Cadence | Method |
|---|---|---|
| **Repo (source of truth)** | every push + daily | Git remote (origin) + a daily mirror clone to cold storage |
| **R2 media** | weekly | `rclone sync r2:athar-media coldstorage:athar-media-backup` (second provider or R2 bucket in another region) |
| **R2 → integrity** | weekly | `rclone check` against the backup |

The Markdown corpus is small and fully in Git; media is the only large, separately
durable asset — hence the independent R2 backup.

---

## 5. Recovery (NFR-04) — rebuild from Git alone

The entire site regenerates from the repository:

```bash
git clone <remote> athar-archive && cd athar-archive
pnpm install
pnpm build            # validate:content → astro build → pagefind → _redirects
# deploy dist/ to Cloudflare Pages
```

- **Site/content/search/SEO**: fully reconstructed by `pnpm build` — no database, no external state.
- **Media**: links in content point at R2. If R2 is intact, nothing to do. If lost,
  restore from the R2 backup (§4) — the *links* are in Git, the *bytes* in the backup.
- **Rollback**: rebuild a previous commit; deploy. (Cloudflare Pages also keeps prior deploys.)

> Recovery rehearsal is part of the P8 launch checklist.
