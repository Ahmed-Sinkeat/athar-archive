# Athar Mobile App — Agreed Home & Navigation Direction

## 1. Overall Design Direction

The current design should not be refined or slightly adjusted. The mobile experience should be **redesigned from the ground up**.

The visual direction should feel like a combination of:

* Notion’s simplicity and restraint
* A premium reading app
* A modern Arabic knowledge library
* Strong Arabic typography
* Large amounts of whitespace
* Minimal visual noise
* Very limited use of color

The goal is **not to copy Notion**, but to adopt the qualities that make it feel clean and elegant:

* Generous whitespace
* Strong typography
* Very few borders
* Minimal card usage
* Warm neutral colors
* Clear hierarchy
* Simple interactions
* Content-first layouts

The app should feel more like a **modern digital library/document environment** than a dashboard.

---

# 2. Main Navigation

The five most important destinations in the app are:

1. Books
2. Articles
3. Issues
4. Poetry
5. Kannashah / Notebook

These should always be extremely easy and fast to reach.

Therefore, they should occupy the **entire bottom navigation bar**.

The bottom navigation should be:

**Books · Articles · Issues · Poetry · Kannashah**

There should be no Search tab and no Home tab in the bottom navigation.

The bottom bar exists specifically for the five core content areas.

---

# 3. Bottom Navigation Visual Style

The current Android-style active pill should be removed.

The new bottom navigation should be much cleaner.

Each destination should contain:

* A simple monochrome icon
* A short label underneath
* Gray styling when inactive
* Dark/black styling when active

The selected destination can be indicated using:

* Slightly heavier text
* A small line
* Or a very subtle indicator

There should not be a large gray capsule behind the active icon.

The bar should feel quiet, elegant, and lightweight.

---

# 4. Home Is Not Part of the Bottom Navigation

The Home screen still has an important purpose, but it does not need to consume one of the five bottom-navigation positions.

The Home screen acts as the **main gateway into Athar**.

The user can return to Home by tapping the **Athar name/logo in the top header**.

This allows all five bottom-navigation positions to remain dedicated to the five primary content areas.

---

# 5. Global Header

The top area of the application should remain extremely simple.

On Home:

**Athar**　　　　　　　　　Search · Settings

On an internal section:

**Books**　　　　　　　　　Search · Settings

or:

**Articles**　　　　　　　　Search · Settings

and so on.

The positions of Search and Settings should remain consistent throughout the application so that users quickly learn where global actions are located.

There should be:

* No unnecessary divider under the header
* No colored app bar
* No large icon backgrounds
* No permanent download icon

---

# 6. Search Is a Core Feature

Search is extremely important and should never feel hidden.

However, it should be treated as a **global action**, not as one of the main content sections.

This allows the bottom navigation to remain dedicated to the five main destinations.

## On the Home Screen

A large search field should appear near the top:

**Search in Athar…**

or something more descriptive such as:

**Search books, articles, issues, poetry…**

This should be one of the strongest elements on the Home screen.

## On Other Screens

A search icon remains in the header.

For example, while browsing Books, tapping Search can initially search within Books.

The search screen can then allow the user to remove that filter and search the entire archive.

Search filters can include:

**All · Books · Articles · Issues · Poetry · Kannashah**

This gives users both:

* Fast contextual search
* Powerful global search

---

# 7. Downloads Should Not Be a Permanent Global Header Button

A permanent download icon in the main app header is unnecessary.

Downloads should exist in **two different contexts**.

## A. Section-Specific Downloads

Each major content section can have its own downloaded/offline view.

For example, Books could contain:

**All · Downloaded · Reading**

Articles could contain:

**All · Downloaded · Saved**

The exact tabs should depend on the nature of each section instead of forcing identical tabs everywhere.

This creates a natural mental model:

> “I want my downloaded books.”

The user goes to:

**Books → Downloaded**

instead of going to a disconnected global download manager.

---

# 8. Global Downloads and Storage

Settings should contain a complete download management area.

For example:

## Downloads & Storage

* 32 downloaded books
* 18 downloaded articles
* 420 MB used
* Download over Wi-Fi only
* Storage management
* Remove individual downloads
* Remove downloads by category
* Remove all offline content

This global download manager is primarily for **management and storage**, while section-specific downloaded tabs are for **browsing and using content**.

---

# 9. Active Downloads

Active downloads do not need a permanent destination on Home.

Instead, when something is currently downloading, the interface can temporarily show a subtle status row such as:

**↓ Downloading 3 items · 64%**

Tapping it opens the current download activity.

If nothing is being downloaded, the element disappears entirely.

---

# 10. Settings

Settings should be an important global destination accessible from the header.

It should contain more than appearance settings.

Potential areas include:

* Account
* Sync
* Appearance
* Reading preferences
* Arabic font settings
* Text size
* Downloads & Storage
* Download over Wi-Fi only
* Notifications, if needed
* Kannashah backup/sync
* General application preferences
* About Athar

The Settings button does not need a large visual treatment. A small, clean icon in the header is enough.

---

# 11. Purpose of the Home Screen

The Home screen should **not repeat the five primary navigation destinations**.

Because Books, Articles, Issues, Poetry, and Kannashah are already always available in the bottom navigation, displaying another large grid containing the same five categories would waste space and make the app feel like a dashboard.

Home should instead answer four simple questions:

1. Do I want to search for something?
2. Do I want to continue where I stopped?
3. Do I want to access one of Athar’s special experiences?
4. Do I want to return to something I recently used?

That should define the Home screen.

---

# 12. Proposed Home Screen Structure

A strong initial structure would be:

## Header

**Athar**　　　　　　　　　Search · Settings

---

## Main Search

A large search field:

**Search books, articles, issues, poetry…**

This should be highly visible and immediately accessible.

---

## Continue

A very simple continuation area.

For example:

**Continue**

**Al-Aqidah Al-Tahawiyyah**

Chapter Three · Page 131 of 284
Reading progress

**Continue Reading →**

This should not look like a complex dashboard card.

It should feel integrated into the page.

A subtle warm-gray background such as `#F7F7F5`, generous spacing, and a strong book title are enough.

Only the important information should remain visible.

---

# 13. Quick Access / Special Experiences

Home is the correct place for destinations that are important but do not belong to the five core archive categories.

Examples include:

* Adhkar
* Matn / Classical Texts
* Audio
* Saved / Favorites

These are not simply content categories. They can have their own specialized experiences.

For example:

### Adhkar

A dedicated reading and daily-use experience.

### Matn

A focused place for classical texts, reading, review, and potentially memorization-related functionality.

### Audio

Audio library, lessons, playlists, and listening history.

### Saved

Content the user has intentionally bookmarked or saved across different areas of the application.

These can appear under a section such as:

**Quick Access**

They should not necessarily be displayed as a traditional 2×2 dashboard grid.

Better possibilities include:

* A clean horizontal row
* Small editorial cards
* Compact icon + text destinations
* Horizontally scrollable items

They should have more personality than generic utility buttons while remaining visually restrained.

---

# 14. Recently Viewed

Another useful Home section is:

**Recent**

This can mix content types.

For example:

* A recently opened book
* A recently viewed issue
* A recently read article
* A piece of poetry
* A recent Kannashah page

This is more useful than duplicating category navigation.

Only a few recent items should be shown.

The Home screen does not need to become a long activity feed.

---

# 15. Archive Discovery

Home may also contain a small discovery section such as:

**From the Archive**

or:

**Selected from Athar**

This should contain only one or two carefully presented items.

The goal is discovery, not showing a large “latest additions” feed.

The existing approach of displaying many recently added items makes the Home screen unnecessarily long and visually busy.

---

# 16. Archive Statistics

Showing the size of the archive is useful because it communicates the depth of Athar.

However, the counts should **not be presented as another large navigation grid**.

Instead, use quiet typography.

For example:

**4,731 items in the archive**

or:

**1,189 books · 1,984 articles · 950 issues · …**

or:

**4,731 items · 43 topics · Updated today**

The statistics should provide context and scale, not compete with the actual content.

---

# 17. Home Screen Summary

The Home screen could therefore follow this structure:

**Athar**　　　　　　　　　⌕　⚙

**Search in Athar…**

### Continue

Al-Aqidah Al-Tahawiyyah
Chapter Three · Page 131
**Continue Reading →**

### Quick Access

Adhkar · Matn · Audio · Saved

### Recent

A few recently used items

### From the Archive

One or two selected items

**4,731 items in the archive**

Bottom Navigation:

**Books · Articles · Issues · Poetry · Kannashah**

This is enough.

The Home screen should not be filled simply because there is available space.

**Whitespace is part of the design.**

---

# 18. Button Philosophy

The current interface uses too many:

* Pills
* Bordered buttons
* Gray-on-gray controls
* Capsules
* Icon + text containers
* Similar button shapes for unrelated actions

This weakens hierarchy.

The new interface should have approximately **three action levels**.

## Primary Actions

Only important actions should look like real filled buttons.

For example:

**Continue Reading →**

Style:

* Warm black/dark background
* White text
* Approximately 44–48 px tall
* 10–12 px corner radius
* Not a fully rounded pill

Primary buttons should be relatively rare.

---

## Secondary Actions

Actions such as:

* Table of Contents
* View All
* Sort
* Filter

often do not need containers at all.

They can be clean text or icon actions:

**View All →**

**Table of Contents**

This keeps the interface much lighter.

---

## Tabs

Tabs should look like actual tabs rather than a collection of pills.

Instead of:

`[ All ] [ Downloaded ] [ Reading ]`

use something closer to:

**All　 Downloaded　 Reading**
━━━━

The active tab can have:

* Dark text
* Slightly increased weight
* A small underline

Inactive tabs remain gray.

This is cleaner and more premium than placing every option inside a capsule.

---

# 19. Cards

Cards should be used much less frequently.

Not every section needs:

* A border
* A rounded rectangle
* A shadow
* A background color

Many sections can simply live directly on the page with spacing and typography.

When cards are used, they should have a reason.

For example, the Continue Reading area may use a subtle warm-gray surface because it represents an active state.

Generic category navigation should not become a wall of cards.

---

# 20. Typography

Arabic typography should become one of the strongest parts of Athar’s identity.

However, traditional Arabic serif fonts should not be used for every part of the interface.

A better system is:

### UI Text

A modern Arabic sans-serif for:

* Navigation
* Buttons
* Labels
* Metadata
* Settings
* Tabs

### Editorial / Reading Typography

A high-quality Arabic reading font for:

* Book titles where appropriate
* Poetry
* Long-form reading
* Classical texts
* Selected editorial headings

This creates a clear distinction between the **application interface** and the **content being read**.

---

# 21. Color System

The application should remain primarily neutral.

Suggested base direction:

* Background: white
* Primary text: warm near-black such as `#242424`
* Secondary surfaces: warm gray around `#F7F7F5`
* Secondary text: neutral gray
* Borders: extremely subtle or absent

A single accent color may eventually be selected, such as:

* A restrained blue
* A muted olive
* Another subtle color that fits Athar’s identity

But the accent should only appear where it communicates something important.

The app should not use multiple decorative colors simply to make sections look different.

---

# 22. The Five Main Areas Can Have Different Personalities

Although Books, Articles, Issues, Poetry, and Kannashah share the same design language, their internal layouts do not need to be identical.

This is important.

## Books

Can emphasize:

* Covers where useful
* Authors
* Reading progress
* Chapters
* Download status
* Reading states

## Articles

Can feel more editorial and lightweight.

## Issues

Can emphasize:

* Question/title
* Topic
* Scholar/source
* Answer structure

## Poetry

Should give significantly more importance to typography and reading space.

## Kannashah

Should feel more personal and notebook-like, potentially closer to a structured writing/database environment.

The system should remain coherent, but each content type should be designed around the way people actually use it.

---

# 23. Mini Audio Player

The mini audio player should **not permanently occupy space** at the bottom of the screen.

It should only appear when audio is actually active.

When nothing is playing, it should disappear.

This prevents the lower portion of the interface from constantly feeling crowded.

---

# 24. Core Product Principle

The old approach tries to show the user almost everything at once.

The new approach should do the opposite.

At any moment, the interface should make the user’s next action obvious.

The user should immediately understand:

* Search for something
* Continue something
* Open a special destination
* Return to something recent
* Jump directly into Books, Articles, Issues, Poetry, or Kannashah

Everything else can exist one level deeper.

The result should feel **calm, fast, premium, content-focused, and distinctly designed for an Arabic knowledge library** rather than like a compressed website or administrative dashboard.

