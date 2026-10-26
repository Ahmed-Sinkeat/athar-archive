// Invisible structure markers in book Markdown (docs/book-markup-spec.md). They are HTML comments on
// their own line, so the site and the app show nothing; a later style change reads them instead of
// guessing. The formatting gate ignores them (it strips every <...>); this checks they are well formed.
export const MARKER_KINDS = ["verse", "hadith", "athar", "riwayah", "ayah"] as const;
export type MarkerKind = (typeof MARKER_KINDS)[number];

const MARKER_LINE = /^<!--\s*(\/)?\s*([a-z]+)\s*-->$/;

/** Problems with the markers in `body` (empty when well formed). Card markers are not ours and are skipped. */
export function checkMarkers(body: string): string[] {
  const problems: string[] = [];
  const lines = body.split("\n");
  let open: { kind: string; line: number } | undefined;
  let content = 0;
  lines.forEach((line, index) => {
    const at = index + 1;
    const match = line.trim().match(MARKER_LINE);
    if (!match || match[2] === "card") {
      if (open && line.trim() && !line.includes("page-sep")) content++;
      if (!match && /<!--\s*\/?\s*(verse|hadith|athar|riwayah|ayah)\b/.test(line)) problems.push(`line ${at}: marker must stand alone on its line`);
      return;
    }
    const [, closing, kind] = match;
    if (!(MARKER_KINDS as readonly string[]).includes(kind!)) { problems.push(`line ${at}: unknown marker «${kind}»`); return; }
    const blank = (i: number) => i < 0 || i >= lines.length || lines[i]!.trim() === "";
    if (!blank(index - 1) || !blank(index + 1)) problems.push(`line ${at}: marker needs a blank line before and after`);
    if (!closing) {
      if (open) problems.push(`line ${at}: <!-- ${kind} --> inside <!-- ${open.kind} --> from line ${open.line}`);
      open = { kind: kind!, line: at };
      content = 0;
    } else if (!open || open.kind !== kind) {
      problems.push(`line ${at}: <!-- /${kind} --> without a matching opener`);
    } else {
      if (!content) problems.push(`line ${open.line}: empty <!-- ${kind} --> block`);
      open = undefined;
    }
  });
  if (open) problems.push(`line ${open.line}: <!-- ${open.kind} --> never closed`);
  return problems;
}
