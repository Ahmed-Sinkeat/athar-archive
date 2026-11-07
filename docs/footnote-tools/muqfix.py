# تفسير مقاتل: per page, make ref (^k) open the k-th entry of the page's data-notes. Three import faults:
#  A. a shared note "(3، 4، 5) فى أ: وصدق." was glued to the note before → split it out (no text added)
#  B. one note cut into lines ("سورة آل عمران:" | "4.") → join an entry ending in : / ، - or followed
#     by one opening with a digit
#  C. the editor's intro numbers refs across pages (5..11 on one page) → count from the page's first ref
# Then body refs are renumbered to the entry that carries their number. Applied only when every ref
# maps and the entries cover exactly the refs' numbers; anything else is left as it is.
import re, json, sys
f = 'src/content/book-lg/tfsyr-mqatl-bn-slyman.md'; write = '--write' in sys.argv
s = open(f).read()
LABEL = re.compile(r'\((\d+(?:\s*[،,]\s*\d+)+)\)\s*')
REF = re.compile(r'\(\^(\d+)\)')
def nums(label): return [int(x) for x in re.split(r'\s*[،,]\s*', label)]
def split(notes):  # → [(text, label numbers or None)]
    out = []
    for n in notes:
        p = LABEL.split(n)
        if p[0].strip() or len(p) == 1: out.append([p[0].rstrip(), None])
        for j in range(1, len(p), 2): out.append([f'({p[j]}) {p[j + 1].strip()}', nums(p[j])])
    return out
def cover(entries, start):  # numbers each entry carries
    k = start - 1; res = []
    for t, lab in entries:
        if lab: res.append(lab); k = max(lab)
        else: k += 1; res.append([k])
    return res
def merge_once(entries, weak=False):
    # weak (the editor's intro only, which is cut at every lost number): any entry not ending a sentence
    for i in range(len(entries) - 1):
        a, b = entries[i], entries[i + 1]
        if a[1] is None and b[1] is None and (re.search(r'[:/،\-]\s*$', a[0]) or re.match(r'\s*\d', b[0]) or weak and not re.search(r'[.»)\]!؟"]\s*$', a[0])):
            return entries[:i] + [[a[0].rstrip() + ' ' + b[0].strip(), None]] + entries[i + 2:]
    return None
stats = {'A': 0, 'B': 0, 'C': 0, 'pages': 0, 'skipped': 0}; skipped = []
parts = re.split(r'(<hr class="page-sep"[^>]*/>)', s)
for i in range(1, len(parts), 2):
    m = re.search(r"data-notes='(.*?)'", parts[i])
    if not m: continue
    notes = json.loads(m.group(1))
    vis = re.sub(r'<!--[\s\S]*?-->', '', parts[i - 1])
    refs = sorted({int(x) for x in REF.findall(vis)})
    if not refs: continue
    lo, hi = refs[0], refs[-1]
    entries = split(notes); A = len(entries) != len(notes)
    start = 1
    # C only for continuous numbering: refs start past 1 and the page has too few notes for 1..hi
    if lo > 1 and len(entries) < hi: start = lo
    B = 0
    while cover(entries, start)[-1][-1] > hi and hi not in cover(entries, start)[-1]:
        e = merge_once(entries) or (merge_once(entries, True) if start > 1 else None)
        if e is None: break
        entries = e; B += 1
    cov = cover(entries, start)
    where = {n: idx + 1 for idx, c in enumerate(cov) for n in c}
    if hi not in cov[-1] or any(r not in where for r in refs):
        if hi > len(notes): stats['skipped'] += 1; skipped.append(re.search(r'data-page="(\d+)"', parts[i]).group(1))
        continue
    if not (A or B or start > 1) and all(where[r] == r for r in refs): continue
    stats['A'] += A; stats['B'] += bool(B); stats['C'] += start > 1; stats['pages'] += 1
    new = [t for t, _ in entries]
    # renumber refs outside comments only
    chunks = re.split(r'(<!--[\s\S]*?-->)', parts[i - 1])
    parts[i - 1] = ''.join(c if c.startswith('<!--') else REF.sub(lambda r: f'(^{where[int(r.group(1))]})', c) for c in chunks)
    parts[i] = parts[i].replace(m.group(0), "data-notes='" + json.dumps(new, ensure_ascii=False, separators=(',', ':')) + "'")
out = ''.join(parts)
# gate: outside ref digits and whitespace, not one character changes
flat = lambda x: re.sub(r"data-notes='(.*?)'", lambda m: ''.join(json.loads(m.group(1))), x)
strip = lambda x: re.sub(r'\s', '', REF.sub('(^)', flat(x)))
a_, b_ = strip(s), strip(out)
if a_ != b_:
    k = next(i for i in range(min(len(a_), len(b_))) if a_[i] != b_[i]); print(repr(a_[k-60:k+40])); print(repr(b_[k-60:k+40])); sys.exit(1)
print(stats, 'still off (refs past notes):', len(skipped), skipped[:15])
if write: open(f, 'w').write(out)
