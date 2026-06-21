# athar-archive — Governance & Branch Protection

How content moves from contribution to published, and the GitHub settings that
enforce the P7 Definition-of-Done: **merge-to-`published` is restricted to the team.**

Contributor-facing companion: [`../CONTRIBUTING.md`](../CONTRIBUTING.md).

## Roles

| Role | Can do | Cannot do |
|---|---|---|
| **Contributor / volunteer** | Propose content (Issue form, or a PR from a fork/branch) | Merge; set `published` |
| **Team member (editor)** | Review accuracy + rules, set `status: published`, merge to `main` | Change repo settings |
| **Maintainer / admin** | Repo settings, CODEOWNERS, branch protection | — |

## The publish gate

"Published" is two things, **both team-only**:

1. The entity's `status: published` in frontmatter (a content decision).
2. The merge of that change into `main` (the deploy trigger).

Branch protection makes #2 impossible without a Code Owner review, so #1 can never
ship unilaterally — even if a contributor sets `published` themselves, it can't merge.

## Required GitHub settings (apply once — admin)

**Settings → Branches → add a branch protection rule for `main`:**

- ☑︎ Require a pull request before merging
  - ☑︎ Require approvals: **1**
  - ☑︎ Require review from **Code Owners**
  - ☑︎ Dismiss stale pull request approvals when new commits are pushed
- ☑︎ Require status checks to pass before merging
  - ☑︎ Require branches to be up to date before merging
  - Required check: **`build`** (from `.github/workflows/ci.yml`)
- ☑︎ Require conversation resolution before merging
- ☑︎ Do not allow bypassing the above settings
- ☑︎ Restrict who can push to matching branches → **team only**
- ☐ Allow force pushes — **off**
- ☐ Allow deletions — **off**

**Settings → General:**

- Merge button: **Squash** only (keeps `main` history one-commit-per-material).
- ☑︎ Automatically delete head branches after merge.

## CODEOWNERS

[`.github/CODEOWNERS`](../.github/CODEOWNERS) routes review of `src/content/**`, the
schemas/validator, and these governance files to the owner. Swap the handle for a
GitHub **team** (`@org/editors`) as the team grows — that is the only change needed
to scale review to multiple editors.

## Volunteer intake

Untrusted text → a team member curates it into a valid `.md` (sanitize, set the id,
link topics, format verses/paragraphs) → opens/labels the PR → review → set
`published` → merge. Full flow in [CONTRIBUTING §01](../CONTRIBUTING.md#01--مسار-المتطوع).
Issue forms live in [`.github/ISSUE_TEMPLATE/`](../.github/ISSUE_TEMPLATE).

## Status — not yet enabled (plan-gated)

Branch protection on a **private** repo requires **GitHub Pro/Team** — a `gh api` attempt
returned HTTP 403: *"Upgrade to GitHub Pro or make this repository public."* Until the repo
goes public at launch (protection is then free) or the account upgrades, the rules above are
convention, not enforcement — acceptable while there are no external contributors yet.

**Solo-maintainer note:** when enabling, keep `enforce_admins: false` until a second editor
exists. GitHub forbids approving your own PR, so enforcing reviews on the lone admin would
lock merges entirely. Flip it on once the team is ≥2. Tracked in [`issue.md`](./issue.md) (#13).
