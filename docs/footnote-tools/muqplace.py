# تفسير مقاتل, notes no ref opens (unref.txt from muqunref.ts): the ref must sit between ref k-1 and the next
# higher ref on the page. Inside that span, look for the word the note is about:
#   «X» quoted, or the reading after "فى أ:" / "ل:" / "أ:" / "الأصل:" up to punctuation.
# One hit (on bare letters) → put (^k) right after that word. Anything else is printed for reading by hand.
# --apply FILE: also place hand-picked spots, lines "juz/page#k<TAB>words the ref follows".
import re, json, sys
D = '/tmp/claude-1000/-home-sinkeat--claude-spares-spare-JzFSp3/074a5745-03dd-4920-abe6-d6124ee9304b/scratchpad'
F = 'src/content/book-lg/tfsyr-mqatl-bn-slyman.md'; write = '--write' in sys.argv
hand = {}
if '--apply' in sys.argv:
    for l in open(sys.argv[sys.argv.index('--apply') + 1]):
        if '\t' in l: a, b = l.rstrip('\n').split('\t'); hand[a] = b
s = open(F).read()
REF = re.compile(r'\(\^(\d+)\)')
AR = lambda c: 'ء' <= c <= 'ي'
todo = {}
for l in open(f'{D}/unref.txt'):
    juz, pg, k = l.split(' |')[0].split(':')[:3]
    todo.setdefault((juz, pg), []).append(int(re.match(r'\d+', k).group(0)))
def words(note):
    w = re.findall(r'«\s*([^«»]{2,40}?)\s*»', note)
    w += [x.strip() for x in re.findall(r'(?:فى|في)?\s*(?:أ|ل|ز|ف|الأصل|الحاشية)\s*[،,]?\s*:\s*([^.،,:«»\[\]()]{2,30})', note)]
    return [x for x in w if len(re.sub(r'[^ء-ي]', '', x)) >= 2]
def place(body, lo, hi, needle):
    """raw index just after the one occurrence of needle's letters in body[lo:hi], else None"""
    vis = [j for j in range(lo, hi) if AR(body[j])]
    txt = ''.join(body[j] for j in vis); key = re.sub(r'[^ء-ي]', '', needle)
    hits = [m.end() for m in re.finditer(re.escape(key), txt)] if key else []
    if len(hits) != 1: return None, len(hits)
    at = vis[hits[0] - 1] + 1
    # to the end of the word, then past the closing » when the word sits inside a quote («X» (^k))
    while at < len(body) and (AR(body[at]) or 'ً' <= body[at] <= 'ْ' or body[at] == 'ٰ'): at += 1
    line = body[body.rfind(chr(10), 0, at) + 1:at]
    if line.count('«') > line.count('»'):
        close = body.find('»', at)
        if close != -1 and chr(10) not in body[at:close]: at = close + 1
    return at, 1
parts = re.split(r'(<hr class="page-sep"[^>]*/>)', s)
done, left = 0, []
for i in range(1, len(parts), 2):
    g = re.search(r'data-page="(\d+)" data-juz="(\d+)"', parts[i]); key = (g.group(2), g.group(1))
    if key not in todo: continue
    notes = json.loads(re.search(r"data-notes='(.*?)'", parts[i]).group(1))
    shift = 1 if notes[0].lstrip().startswith('=') else 0
    body = parts[i - 1]
    for k in sorted(todo[key], reverse=True):
        note = notes[k - 1 + shift]
        hidden = [(m.start(), m.end()) for m in re.finditer(r'<!--[\s\S]*?-->', body)]
        refs = [(int(m.group(1)), m.start(), m.end()) for m in REF.finditer(body) if not any(a <= m.start() < b for a, b in hidden)]
        lo = max([e for n, a, e in refs if n < k], default=0)
        hi = min([a for n, a, e in refs if n > k], default=len(body))
        tag = f'{key[0]}/{key[1]}#{k}'
        at = None
        if hand.get(tag) == '-': at = None
        elif tag in hand:
            at, n = place(body, lo, hi, hand[tag])
            if at is None: print('HAND MISS', tag, n)
        else:
            for w in words(note):
                at, n = place(body, lo, hi, w)
                if at is not None: break
        if at is not None and not any(a <= at < b for a, b in hidden):
            body = body[:at] + f' (^{k})' + body[at:]; done += 1
        else:
            left.append((tag, note, re.sub(r'\s+', ' ', body[lo:hi])))
    parts[i - 1] = body
print('placed', done, 'left', len(left))
if '-v' in sys.argv:
    for tag, note, seg in left: print(f'\n## {tag}  NOTE: {note[:160]}\n   SPAN: {seg[:700]}')
if write: open(F, 'w').write(''.join(parts))
