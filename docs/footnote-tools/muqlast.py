# تفسير مقاتل, last unreferenced notes (list from muqunref.ts → unref.txt):
#  run-on: the epub's first note on the page continues the last note of the page before → mark it «= »
#          (the parser joins it there) and shift the page's refs down by one
#  mark:   the epub has «k» for this note → put (^k) after the same 12 letters in the md page, if they
#          occur exactly once and the page has as many notes as the epub
import re, json, pickle, sys
D = '/tmp/claude-1000/-home-sinkeat--claude-spares-spare-JzFSp3/074a5745-03dd-4920-abe6-d6124ee9304b/scratchpad'
E = pickle.load(open(f'{D}/muqepub.pkl', 'rb'))   # our own cache, written by muqepub.py
F = 'src/content/book-lg/tfsyr-mqatl-bn-slyman.md'; write = '--write' in sys.argv
s = open(F).read()
REF = re.compile(r'\(\^(\d+)\)')
todo = {}
for l in open(f'{D}/unref.txt'):
    juz, pg, k = l.split(' |')[0].split(':')[:3]
    todo.setdefault((juz, pg), []).append(int(re.match(r'\d+', k).group(0)))
LET = lambda x: re.sub(r'[^ء-ي]', '', x)   # bare letters: no marks, digits, spaces or punctuation
parts = re.split(r'(<hr class="page-sep"[^>]*/>)', s)
st = {'run-on': 0, 'placed': 0, 'not placed': 0}; miss = []
for i in range(1, len(parts), 2):
    g = re.search(r'data-page="(\d+)" data-juz="(\d+)"', parts[i]); key = (g.group(2), g.group(1))
    if key not in todo: continue
    m = re.search(r"data-notes='(.*?)'", parts[i]); notes = json.loads(m.group(1))
    ebody, enotes = E[key][0]
    body = parts[i - 1]
    keep = lambda f: ''.join(c if c.startswith('<!--') else f(c) for c in re.split(r'(<!--[\s\S]*?-->)', body))
    if 1 in todo[key] and enotes and enotes[0][0] is None and not notes[0].lstrip().startswith('='):
        notes[0] = '= ' + notes[0]
        body = keep(lambda c: REF.sub(lambda r: f'(^{int(r.group(1)) - 1})', c))
        todo[key] = [k - 1 for k in todo[key] if k != 1]; st['run-on'] += 1
        shift = 1
    else: shift = 0
    real = len(notes) - shift
    for k in todo[key]:
        # epub note number for the md's k-th (real) note: same order, single numbers only
        en = [n for n in enotes if n[0]]
        if len(en) != real or len(en[k - 1][0]) != 1: st['not placed'] += 1; miss.append(f'{key}#{k}'); continue
        num = en[k - 1][0][0]
        mm = re.search(r'«%d»' % num, ebody)
        if not mm: st['not placed'] += 1; miss.append(f'{key}#{k}'); continue
        before = LET(ebody[:mm.start()])[-12:]
        # positions in md body where those letters end
        vis = [(j, c) for j, c in enumerate(body) if 'ء' <= c <= 'ي']
        txt = ''.join(c for _, c in vis)
        hits = [x.end() for x in re.finditer(re.escape(before), txt)] if len(before) == 12 else []
        if len(hits) != 1: st['not placed'] += 1; miss.append(f'{key}#{k}'); continue
        at = vis[hits[0] - 1][0] + 1
        while at < len(body) and 'ً' <= body[at] <= 'ْ' or at < len(body) and body[at] == 'ٰ': at += 1
        if REF.match(body[at:].lstrip()[:8]): st['not placed'] += 1; miss.append(f'{key}#{k}'); continue
        body = body[:at] + f' (^{k})' + body[at:]; st['placed'] += 1
    parts[i - 1] = body
    parts[i] = parts[i].replace(m.group(0), "data-notes='" + json.dumps(notes, ensure_ascii=False, separators=(',', ':')) + "'")
print(st, miss[:20])
if write: open(F, 'w').write(''.join(parts))
