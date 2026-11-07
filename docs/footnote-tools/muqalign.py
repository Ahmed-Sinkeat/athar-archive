# تفسير مقاتل against epub 23614 (another print of the same edition: «وآله», «ـًا»), page by page.
# Notes: the md keeps its own text; from the epub it takes only where each note starts and the numbers
#   the import dropped inside notes («للآية (40)», «57- 58»).
# Body: every epub «n» mark is found in the md text; its (^k) is renumbered to the note now carrying n,
#   or added there when the import dropped it.
# A page is left as it is unless the texts align well. Gate: old text (no refs, no spaces) is a
# subsequence of the new, and what was added is digits and number punctuation only.
import re, json, pickle, difflib, sys, collections
D = '/tmp/claude-1000/-home-sinkeat--claude-spares-spare-JzFSp3/074a5745-03dd-4920-abe6-d6124ee9304b/scratchpad'
E = pickle.load(open(f'{D}/muqepub.pkl', 'rb'))   # our own cache, written by muqepub.py
F = 'src/content/book-lg/tfsyr-mqatl-bn-slyman.md'; write = '--write' in sys.argv
s = open(F).read()
REF = re.compile(r'\(\^\d+\)')
MARK = re.compile(r'«(\d+)»')
NUMCH = re.compile(r'^[0-9()\-،,/.\s]+$')

def chars(raw, skip=()):
    """non-space chars of raw with their raw index, leaving out the (start, end) spans in skip"""
    out, idx, k = [], [], 0
    spans = sorted(skip)
    for i, ch in enumerate(raw):
        while k < len(spans) and spans[k][1] <= i: k += 1
        if k < len(spans) and spans[k][0] <= i < spans[k][1]: continue
        if not ch.isspace(): out.append(ch); idx.append(i)
    return ''.join(out), idx

def ops(a, b):
    return difflib.SequenceMatcher(None, a, b, autojunk=False).get_opcodes()

def a_at(opc, j):
    """md position aligned to epub position j, if j falls in an equal block (or right after one)"""
    for op, i1, i2, j1, j2 in opc:
        if op == 'equal' and j1 <= j <= j2: return i1 + (j - j1)
    return None

def page_ok(old_body, old_notes, new_body, new_notes):
    """old text (no refs, no spaces) is a subsequence of the new; what was added is numbers only"""
    A = re.sub(r'\s', '', REF.sub('', old_body + ''.join(old_notes))); B = re.sub(r'\s', '', REF.sub('', new_body + ''.join(new_notes)))
    it = iter(B)
    if not all(c in it for c in A): return False
    added = ''.join(B[j1:j2] for op, i1, i2, j1, j2 in ops(A, B) if op != 'equal') if A != B else ''
    return bool(NUMCH.match(added or '0'))

stats = collections.Counter(); notes_added = []
parts = re.split(r'(<hr class="page-sep"[^>]*/>)', s)
for p in range(1, len(parts), 2):
    g = re.search(r'data-page="(\d+)" data-juz="(\d+)"', parts[p]); key = (g.group(2), g.group(1))
    if key not in E: stats['no epub page'] += 1; continue
    ebody, enotes = E[key][0]
    m = re.search(r"data-notes='(.*?)'", parts[p])
    md = json.loads(m.group(1)) if m else []
    if not enotes and not md: continue
    # ---- notes
    new_notes, nummap = md, {}
    if enotes:
        R = '\n'.join(md)
        a, ai = chars(R)
        # epub stream, single labels dropped (the md never had them); remember where each note starts
        bl, starts, nums = [], [], []
        for n, t in enotes:
            single = n and len(n) == 1
            t2 = re.sub(r'^\(\d+\)\s*', '', t) if single else t
            starts.append(len(''.join(bl))); nums.append(n); bl.append(re.sub(r'\s', '', t2))
        b = ''.join(bl)
        opc = ops(a, b)
        same = sum(i2 - i1 for op, i1, i2, j1, j2 in opc if op == 'equal')
        if not a or same < 0.9 * len(a) or same < 0.9 * len(b):
            stats['notes not aligned'] += 1; continue
        cuts = []
        for j in starts:
            i = a_at(opc, j)
            if i is None: break
            cuts.append(i)
        if len(cuts) != len(starts) or cuts != sorted(cuts) or cuts[0] != 0:
            stats['notes cut unclear'] += 1; continue
        # numbers the import lost: epub-only runs of digits and number punctuation
        ins = {}
        for op, i1, i2, j1, j2 in opc:
            if op == 'insert' and NUMCH.match(b[j1:j2]) and re.search(r'\d', b[j1:j2]) and j1 not in starts:
                ins[i1] = b[j1:j2]
        # rebuild: raw R split at cut points, with insertions
        rawcut = [ai[i] if i < len(ai) else len(R) for i in cuts] + [len(R)]
        out = []
        for k in range(len(cuts)):
            lo_r, hi_r = rawcut[k], rawcut[k + 1]
            piece, last = [], lo_r
            for i in sorted(x for x in ins if cuts[k] <= x < (cuts[k + 1] if k + 1 < len(cuts) else len(a) + 1)):
                if i == cuts[k] and i != 0: continue
                at = ai[i] if i < len(ai) else len(R)
                if i == len(a): at = len(R)
                piece.append(R[last:at]); L = R[at - 1] if at else ' '; Rt = R[at] if at < len(R) else ' '
                piece.append((' ' if L.isalpha() else '') + ins[i] + (' ' if Rt.isalpha() else '')); last = at
            piece.append(R[last:hi_r])
            out.append(re.sub(r'\s+', ' ', ''.join(piece)).strip())
        # a lost number glued between words: tidy "( 40 )" back to "(40)"
        out = [re.sub(r'\(\s+', '(', re.sub(r'\s+\)', ')', o)) for o in out]
        if any(not o for o in out): stats['empty note'] += 1; continue
        new_notes = out
        # epub note numbers → index of the md note now carrying them (a run-on first note keeps no number)
        for idx, n in enumerate(nums):
            for x in n or []: nummap.setdefault(x, idx + 1)
        if ins: stats['pages with numbers restored'] += 1; notes_added.append((key, list(ins.values())))
    # ---- body refs
    body = parts[p - 1]
    comments = [(mm.start(), mm.end()) for mm in re.finditer(r'<!--[\s\S]*?-->', body)]
    refs = [(mm.start(), mm.end()) for mm in REF.finditer(body) if not any(c0 <= mm.start() < c1 for c0, c1 in comments)]
    a, ai = chars(body, comments + refs)
    marks = [(mm.start(), int(mm.group(1))) for mm in MARK.finditer(ebody)]
    if not marks and not refs:
        if new_notes != md and page_ok(body, md, body, new_notes): parts[p] = parts[p].replace(m.group(0), "data-notes='" + json.dumps(new_notes, ensure_ascii=False, separators=(',', ':')) + "'"); stats['pages changed'] += 1
        continue
    b, bi = chars(MARK.sub(lambda mm: ' ' * len(mm.group(0)), ebody))
    opc = ops(a, b)
    same = sum(i2 - i1 for op, i1, i2, j1, j2 in opc if op == 'equal')
    if same < 0.85 * min(len(a), len(b)) or not nummap and enotes:
        stats['body not aligned'] += 1; continue
    # md position of each epub mark = after the aligned char before it
    import bisect
    want = []   # (raw index to place at, note index)
    ok = True
    for pos, n in marks:
        j = bisect.bisect_left(bi, pos)          # epub chars before the mark
        i = a_at(opc, j)
        if i is None or n not in nummap: ok = False; break
        at = ai[i - 1] + 1 if i > 0 else 0
        want.append((at, nummap[n]))
    if not ok: stats['mark not placed'] += 1; continue
    # pair each wanted spot with an existing md ref near it (only spaces or punctuation between)
    used, edits = set(), []
    for at, idx in want:
        hit = None
        for r0, r1 in refs:
            if r0 in used: continue
            gap = body[min(at, r0):max(at, r0)] if r0 >= at else body[r1:at]
            if len(gap) <= 6 and re.fullmatch(r'[\s\W]*', gap): hit = (r0, r1); break
        if hit: used.add(hit[0]); edits.append((hit[0], hit[1], f'(^{idx})'))
        else: edits.append((at, at, f' (^{idx})'))
    left = [r for r in refs if r[0] not in used]
    if left: stats['md refs with no epub mark (kept)'] += len(left)
    new_body = body
    for r0, r1, t in sorted(edits, reverse=True): new_body = new_body[:r0] + t + new_body[r1:]
    stats['refs added'] += sum(1 for e in edits if e[0] == e[1])
    if new_body != body or new_notes != md:
        if not page_ok(body, md, new_body, new_notes): stats['gate refused'] += 1; continue
        stats['pages changed'] += 1
        parts[p - 1] = new_body
        if m: parts[p] = parts[p].replace(m.group(0), "data-notes='" + json.dumps(new_notes, ensure_ascii=False, separators=(',', ':')) + "'")
out = ''.join(parts)
flat = lambda x: re.sub(r"data-notes='(.*?)'", lambda mm: ''.join(json.loads(mm.group(1))), x)
A = re.sub(r'\s', '', REF.sub('', flat(s))); B = re.sub(r'\s', '', REF.sub('', flat(out)))
it = iter(B); assert all(c in it for c in A), 'text lost'
added = len(B) - len(A)
print(dict(stats)); print('chars added:', added)
for k, v in notes_added[:6]: print('  ', k, v)
if write: open(F, 'w').write(out)
