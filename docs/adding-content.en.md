# Adding or Editing a Book (Beginner's Guide)

*النسخة العربية: [adding-content.ar.md](./adding-content.ar.md)*

This guide is for people with **no programming background**. You don't need to
install anything, know Git, or open a terminal. All the work happens **on the
GitHub website, in your browser**.

> **The whole idea in one line:** every book on the site is **one text file**.
> Edit the file on GitHub → the site rebuilds itself → your change appears.
> There is no database and no control panel. The files *are* the site.

**What you need:** a GitHub account, and the site owner must grant you write
access to the repository. Ask for that first — without it, no edit button
appears.

---

## Where the books live

Books sit in two folders — this is the most important thing to understand:

| Folder | What's in it | Count |
| --- | --- | --- |
| `src/content/book/` | Small books | 284 |
| `src/content/book-lg/` | Large imported texts | 955 |

Both appear on the site identically; the split exists for technical reasons
only. **The only rule you need:** if a book already exists, never move it
between folders — leave it where it is. If you're creating a new book, put it in
`src/content/book/`, unless it's a huge text (over roughly 100KB, i.e. a full
printed book), which goes in `src/content/book-lg/`.

---

## Part 1: editing an existing book

### 1) Find the book's file

Open the book on the site and look at its URL. The last part is the filename:

```
athar.arthurarchive.com/book/adab-al-ishrah
                             └──────┬─────┘
                          filename = adab-al-ishrah.md
```

Then to reach it:

1. Open the repo: <https://github.com/Ahmed-Sinkeat/athar-archive>
2. Press the **`t`** key (this opens GitHub's file finder).
3. Type the name you copied from the URL. Click the file when it appears.

> `t` not working? Go straight to the address — try this first:
> `https://github.com/Ahmed-Sinkeat/athar-archive/blob/main/src/content/book/<name>.md`
> If you get a 404, change `book` to `book-lg` in that same address.

### 2) Edit and save

1. Click the pencil **✏️** icon at the top right of the file.
2. Make your change.
3. Scroll to the bottom and click the green **Commit changes** button.

That's it. The change is now part of the site and appears after the next rebuild.

---

## Part 2: adding a new book

1. Open the books folder:
   <https://github.com/Ahmed-Sinkeat/athar-archive/tree/main/src/content/book>
2. Click **Add file** → **Create new file** (top right).
3. For the filename, type: **`book-name-in-latin.md`**
   - Lowercase Latin letters, digits, and hyphens `-` only.
   - No spaces, no Arabic characters, no capitals.
   - **Don't forget the `.md` at the end.**
   - Valid example: `adab-al-ishrah.md`
4. Paste the template below into the big box, then edit it.
5. Scroll down and click **Commit changes**.

### The template — copy this as-is

```markdown
---
title: "أدب العِشرة"
status: published
published_at: 2026-08-11
person: jmal-al-dyn-abn-hsham
kind: كتاب
topics: ["al-nahw"]
---

## المقدمة

Introduction text here…

## الباب الأول: في كذا

Chapter text here…
```

### What the template means

The part between the `---` lines is the book's **info card**. The first three
are **required**:

| Line | What goes in it |
| --- | --- |
| `title` | The Arabic title, **in quotes** `" "`. |
| `status` | Type exactly `published`. Anything else means the book **will not appear** on the site. |
| `published_at` | Date as `year-month-day`, e.g. `2026-08-11`. |

Everything after that is **optional** — delete any line you don't need:

| Line | What goes in it |
| --- | --- |
| `person` | The author's filename, copied exactly from `src/content/person/`. **If you don't know it, delete the line** — an invented name here **fails the build**, whereas omitting it is harmless. |
| `kind` | Only `كتاب`, `متن`, `مرجع`, or `مجموع`. Leave it as `كتاب` or delete the line. Use `متن` only for well-known memorization texts, not every short treatise. |
| `topics` | Topics in brackets, **five maximum**, e.g. `["al-nahw", "al-balaghah"]`. The available names are the filenames in `src/content/topic/` — copy them exactly; a name that doesn't exist **fails the build**. |
| `authored_year` | Hijri year of authorship, digits only: `761`. |
| `description` | A short description in quotes. |

**By far the easiest approach:** open an existing book similar to yours, copy its
info card, and change the values. Much safer than typing one from scratch.

### The book text

Everything after the closing `---` is the book text. Write normal Arabic prose:

- `##` before a line makes it a **main chapter heading**.
- `###` makes it a subheading under that.
- Leave a blank line between paragraphs, or they run together.
- Long books are split into pages automatically based on the `##` headings — the
  better you mark them, the better the reading experience.

---

## Rules that break the site — avoid these

1. **Never rename a published book's file.** The filename *is* the URL; renaming
   it breaks every existing link. If you must, add the old name to the card:
   `aliases: ["old-name"]`
2. **Never invent values** for `kind`. The allowed values are listed above,
   exactly. Anything else silently drops the book out of filtering and search.
   There is no `genre` line any more — every book is an ordinary book, including
   works of تفسير and حديث; use `topics` to classify them.
3. **Don't forget `status: published`** and then wonder why the book isn't showing.
4. **Never delete the `---` lines** or their contents unless you know what
   you're doing. Without them the file isn't read at all.
5. **Keep the title in quotes** `"..."` — especially if it contains a colon `:`.
6. **Change one thing at a time**, save, then check. Far easier to fix.

---

## When does the change appear?

| Stage | How long |
| --- | --- |
| Rebuild + deploy | **1–2 hours** (the archive is large — not a minutes-long build) |
| Appears in browse pages | With that deploy |
| Appears in **search** | Possibly days later — the index refreshes gradually under a limited daily quota |

If your book isn't searchable right away, **you did nothing wrong** — the page
works, and indexing catches up later.

**To watch what's happening:** <https://github.com/Ahmed-Sinkeat/athar-archive/actions>

- ✅ green tick = deployed successfully.
- 🟡 spinning yellow circle = still building, wait.
- ❌ red cross = something failed. Usually a mistake in the `---` card. Tell the
  site owner.

---

## I made a mistake — how do I undo it?

Nothing is ever lost. Every save is recorded in the repo's history:

1. Open <https://github.com/Ahmed-Sinkeat/athar-archive/commits/main>
2. Click the bad change.
3. Click **Revert** — GitHub puts everything back as it was.

If you're unsure, ask the site owner before reverting; don't stack more attempts
on top of the mistake.

---

## No write access?

You can still propose content without any permissions:

1. Open <https://github.com/Ahmed-Sinkeat/athar-archive/issues/new/choose>
2. Choose **اقتراح محتوى** (propose content) for a new book, or
   **الإبلاغ عن خطأ** (report a correction) for existing text.
3. Fill the form, paste your text, **Submit**.

A team member converts it to the correct format and publishes it.

---

## Where to go next

| You want to… | Read |
| --- | --- |
| Understand every field in detail, and work via Pull Request | [`CONTRIBUTING.md`](../CONTRIBUTING.md) |
| Change the site's interface text (menus, buttons) | [`editing-text.en.md`](./editing-text.en.md) |
| Understand the repo layout | [`structure.md`](./structure.md) |

> **Historical note:** the site used to have an editing panel at `/admin`. It was
> deleted on 2026-08-11 — no content was ever added through it, and it could only
> see 284 of the 1,239 books. If you find it mentioned in an older document,
> ignore that: all work goes through GitHub as described in this guide.
