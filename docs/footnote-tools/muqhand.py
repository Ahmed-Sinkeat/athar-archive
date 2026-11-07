# تفسير مقاتل: the last notes no ref opens, fixed by hand after reading each page against epub 23614.
#  join   pieces of one note (the epub has one note there; a number the import lost is restored)
#  runon  the epub's first note continues the page before → «= » (parser joins it there)
#  ref    body edits: a mark's leftover ("2»", "1»") becomes the ref; refs renumbered after joins
#  move   al-Baqara's summary (epub p.82, body text with no mark) → page 41, ref on the surah title line,
#         the way the book does for المؤمنون («المؤمنون (^1)» → «مقصود السورة إجمالا…»)
import re, json
F = 'src/content/book-lg/tfsyr-mqatl-bn-slyman.md'
s = open(F).read()
parts = re.split(r'(<hr class="page-sep"[^>]*/>)', s)
idx = {re.search(r'data-page="(\d+)" data-juz="(\d+)"', parts[i]).groups()[::-1]: i for i in range(1, len(parts), 2)}
def notes(k): return json.loads(re.search(r"data-notes='(.*?)'", parts[idx[k]]).group(1))
def set_notes(k, n):
    i = idx[k]; dump = "data-notes='" + json.dumps(n, ensure_ascii=False, separators=(',', ':')) + "'"
    parts[i] = re.sub(r"data-notes='.*?'", lambda _: dump, parts[i]) if 'data-notes' in parts[i] else parts[i].replace(' />', ' ' + dump + ' />')
def join(k, first, last, glue=None):   # 1-based, notes first..last become one; glue[j] goes before piece j
    n = notes(k); glue = glue or {}
    merged = n[first - 1]
    for j in range(first + 1, last + 1): merged = merged.rstrip() + ' ' + glue.get(j, '') + n[j - 1].strip()
    n[first - 1:last] = [re.sub(r'\s+', ' ', merged).strip()]; set_notes(k, n)
def body(k, old, new, count=1):
    i = idx[k] - 1; assert parts[i].count(old) == count, (k, old, parts[i].count(old)); parts[i] = parts[i].replace(old, new)
def body_re(k, pat, new):
    i = idx[k] - 1; parts[i], n = re.subn(pat, new, parts[i]); assert n == 1, (k, pat, n)
def renum(k, mapping):
    i = idx[k] - 1
    parts[i] = re.sub(r'\(\^(\d+)\)', lambda m: f'(^{mapping.get(int(m.group(1)), int(m.group(1)))})', parts[i])

# pieces of one long note (the epub has one note)
join(('1', '545'), 1, 5)
join(('2', '513'), 1, 3)
join(('2', '641'), 1, 5)
join(('4', '409'), 2, 11)
join(('3', '223'), 2, 3, {3: '(25) '})          # «فى المصحف المتداول بيننا: (25) سورة الفرقان مكية…»
join(('4', '175'), 2, 3, {3: '(54) '})
join(('3', '151'), 3, 4, {4: '(23) '})
join(('2', '396'), 1, 2, {2: '(14) '}); renum(('2', '396'), {3: 2})
join(('5', '126'), 1, 2, {2: '100- '}); renum(('5', '126'), {2: 1})   # «سورة آل عمران: 100- 105.»
# first notes that run on from the page before
join(('3', '135'), 1, 2); n = notes(('3', '135')); n[0] = '= ' + n[0]; set_notes(('3', '135'), n)
join(('3', '641'), 1, 2, {2: '29- '}); n = notes(('3', '641')); n[0] = '= ' + n[0]; set_notes(('3', '641'), n)
# a mark's leftover is the ref
body_re(('5', '102'), r'(فهو قوله تعالى: «[^»]{5,40}») 2»', r'\1 (^5)')
body(('5', '10'), 'فهي سلسلة الكذب 1»', 'فهي سلسلة الكذب (^1)')
# al-Baqara's summary → page 41
n = notes(('1', '81')); summary = re.sub(r'\s+', ' ', ' '.join(x.strip() for x in n[2:5])); set_notes(('1', '81'), n[:2])
set_notes(('1', '41'), [summary])
i41 = idx[('1', '41')] - 1
assert parts[i41].count('(2) سورة البقرة مدنية') == 1
parts[i41] = parts[i41].replace('(2) سورة البقرة مدنية', '(2) سورة البقرة (^1) مدنية')
open(F, 'w').write(''.join(parts)); print('done')
