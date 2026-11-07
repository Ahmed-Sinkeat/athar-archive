# تفسير مقاتل: the import cut the epub's ref marks «k» down to a lone "»" that closes nothing. Such a » is
# where ref k stood. (1) a ref placed this turn right before one: the ref takes its place. (2) for each note
# no ref opens, if its span (between ref k-1 and the next higher ref) holds exactly one orphan », the ref
# goes there. (The epub lost these numbers too: it has the same lone » there, so it can't confirm them.) Prints the spans that hold none or several.
import re, json, sys
D = '/tmp/claude-1000/-home-sinkeat--claude-spares-spare-JzFSp3/074a5745-03dd-4920-abe6-d6124ee9304b/scratchpad'
F = 'src/content/book-lg/tfsyr-mqatl-bn-slyman.md'; write = '--write' in sys.argv
s = open(F).read()
REF = re.compile(r'\(\^(\d+)\)')
def orphans(body):
    """indices of » that close no « in their paragraph (comments skipped)"""
    out, depth = [], 0
    hidden = [(m.start(), m.end()) for m in re.finditer(r'<!--[\s\S]*?-->', body)]
    j = 0
    while j < len(body):
        h = next((b for a, b in hidden if a == j), None)
        if h: j = h; continue
        c = body[j]
        if c == '\n' and body[j + 1:j + 2] == '\n': depth = 0
        elif c == '«': depth += 1
        elif c == '»':
            if depth: depth -= 1
            else: out.append(j)
        j += 1
    return out
import pickle
E = pickle.load(open(f'{D}/muqepub.pkl', 'rb'))   # our own cache, written by muqepub.py
LET = lambda x: re.sub(r'[^ء-ي]', '', x)
def epub_marks(ebody):
    letters, at = [], set()
    for m in re.finditer(r'«(\d+)»|[ء-ي]', ebody):
        if m.group(1): at.add(len(letters))
        else: letters.append(m.group(0))
    return ''.join(letters), at
def mark_in_epub(key, body, o):
    """the epub has a «n» mark right after the 10 letters before position o"""
    if key not in E: return False
    txt, at = epub_marks(E[key][0][0]); tail = LET(body[:o])[-10:]
    return len(tail) == 10 and any(m.end() in at for m in re.finditer(re.escape(tail), txt))
todo = {}
for l in open(f'{D}/unref.txt'):
    juz, pg, k = l.split(' |')[0].split(':')[:3]
    todo.setdefault((juz, pg), []).append(int(re.match(r'\d+', k).group(0)))
parts = re.split(r'(<hr class="page-sep"[^>]*/>)', s)
st = {'stray after new ref': 0, 'placed at orphan »': 0}; left = []
for i in range(1, len(parts), 2):
    body = parts[i - 1]
    g = re.search(r'data-page="(\d+)" data-juz="(\d+)"', parts[i]); key = (g.group(2), g.group(1))
    # (1) "(^k)»" with an orphan »: the ref replaces it (only refs this turn added sit right before one)
    for o in reversed(orphans(body)):
        m = re.search(r'\(\^\d+\)$', body[:o])
        if m and int(m.group(0)[2:-1]) in todo.get(key, []): body = body[:o] + body[o + 1:]; st['stray after new ref'] += 1
    g = re.search(r'data-page="(\d+)" data-juz="(\d+)"', parts[i]); key = (g.group(2), g.group(1))
    for k in sorted(todo.get(key, []), reverse=True):
        hidden = [(m.start(), m.end()) for m in re.finditer(r'<!--[\s\S]*?-->', body)]
        refs = [(int(m.group(1)), m.start(), m.end()) for m in REF.finditer(body) if not any(a <= m.start() < b for a, b in hidden)]
        if any(n == k for n, _, _ in refs): continue   # placed by muqplace.py
        lo = max([e for n, a, e in refs if n < k], default=0)
        hi = min([a for n, a, e in refs if n > k], default=len(body))
        orph = [o for o in orphans(body) if lo <= o < hi]
        if len(orph) == 1:
            o = orph[0]; body = body[:o].rstrip(' ') + f' (^{k})' + body[o + 1:]; st['placed at orphan »'] += 1
        else:
            left.append((f'{key[0]}/{key[1]}#{k}', len(orph)))
    parts[i - 1] = body
print(st, 'left', len(left), left)
if write: open(F, 'w').write(''.join(parts))
