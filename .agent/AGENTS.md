# Agent Rules — Athar Archive

Read [.agent/instructions.md](.agent/instructions.md) before writing any code.

- Trace data flow from Markdown source to every render surface before fixing display bugs.
- Grep the content files to measure scope before claiming a fix is complete.
- Run `pnpm test && pnpm validate:content && pnpm build` before saying "done."
- Don't patch symptoms. Fix at the source.
- Don't leave dead code, scratch files, or congratulatory methodology documents.
- Follow ponytail principles: delete > shrink > simplify.
