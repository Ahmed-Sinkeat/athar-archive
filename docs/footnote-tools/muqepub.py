# تفسير مقاتل: epub 23614 pages → {(juz, page): [(body text with «n» marks, [[numbers], note text])]}
# Notes live in <p class="hamesh"> after the <hr/>, one per <br/> line; a line opening "(n)" starts a note.
import re, glob, html, pickle
D = '/tmp/claude-1000/-home-sinkeat--claude-spares-spare-JzFSp3/074a5745-03dd-4920-abe6-d6124ee9304b/scratchpad'
AR = str.maketrans('٠١٢٣٤٥٦٧٨٩', '0123456789')
LAB = re.compile(r'\(([0-9]+(?:\s*[،,-]\s*[0-9]+)*)\)\s*')
def nums(lab):
    p = [int(x) for x in re.split(r'\s*[،,-]\s*', lab)]
    return list(range(p[0], p[1] + 1)) if '-' in lab and len(p) == 2 and p[1] > p[0] else p
pages = {}
for f in sorted(glob.glob(f'{D}/muqepub/OEBPS/text/page_*.xhtml')):
    s = open(f).read().translate(AR)
    m = re.search(r'الجزء: (\d+) - الصفحة: (\d+)', s)
    if not m: continue
    body = s[s.find('<body'):]
    notes = []
    for h in re.findall(r'<p class="hamesh">([\s\S]*?)</p>', body):
        for line in re.split(r'<br\s*/?>', h):
            line = html.unescape(re.sub(r'<[^>]+>', '', line)).strip()
            if not line: continue
            mm = LAB.match(line)
            if mm: notes.append([nums(mm.group(1)), line])
            elif notes: notes[-1][1] += ' ' + line
            else: notes.append([None, line])   # runs on from the page before
    text = re.sub(r'<p class="hamesh">[\s\S]*?</p>|<p class="text-center">الجزء:[^<]*</p>', '', body)
    text = html.unescape(re.sub(r'<[^>]+>', ' ', text))
    pages.setdefault((m.group(1), m.group(2)), []).append((text, notes))
pickle.dump(pages, open(f'{D}/muqepub.pkl', 'wb'))
print('pages', len(pages), 'dup keys', sum(len(v) > 1 for v in pages.values()), 'with notes', sum(bool(v[0][1]) for v in pages.values()),
      'run-on notes', sum(1 for v in pages.values() for _, n in v if n and n[0][0] is None))
for x in pages[('5', '16')][0][1][:3]: print(x)
