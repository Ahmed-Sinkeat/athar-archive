# Per md page: are its notes the epub's notes (same letters), a subsequence of them (import lost text), or other?
import re, json, pickle, collections
D = '/tmp/claude-1000/-home-sinkeat--claude-spares-spare-JzFSp3/074a5745-03dd-4920-abe6-d6124ee9304b/scratchpad'
E = pickle.load(open(f'{D}/muqepub.pkl', 'rb'))
s = open('src/content/book-lg/tfsyr-mqatl-bn-slyman.md').read()
parts = re.split(r'(<hr class="page-sep"[^>]*/>)', s)
sq = lambda x: re.sub(r'\s', '', x)
def subseq(a, b):
    it = iter(b); return all(c in it for c in a)
def label_off(n):  # epub note text without its own single "(n)" label
    return re.sub(r'^\(\d+\)\s*', '', n[1]) if n[0] and len(n[0]) == 1 else n[1]
c = collections.Counter(); ex = collections.defaultdict(list)
for i in range(1, len(parts), 2):
    g = re.search(r'data-page="(\d+)" data-juz="(\d+)"', parts[i]); key = (g.group(2), g.group(1))
    m = re.search(r"data-notes='(.*?)'", parts[i]); md = json.loads(m.group(1)) if m else []
    ep = [label_off(n) for n in E.get(key, [[None, []]])[0][1]]
    a, b = sq(''.join(md)), sq(''.join(ep))
    k = 'no epub page' if key not in E else 'same' if a == b else 'md ⊂ epub' if subseq(a, b) else 'epub ⊂ md' if subseq(b, a) else 'differ'
    c[k] += 1
    if len(ex[k]) < 3 and k not in ('same',):
        ex[k].append((key, len(md), len(ep), len(b) - len(a)))
print(c); [print(k, v) for k, v in ex.items()]
