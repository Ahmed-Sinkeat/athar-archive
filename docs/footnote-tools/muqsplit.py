# تفسير مقاتل: the print shares one note between refs, "(3، 4، 5) فى أ: وصدق.", and the import kept it
# inside the note before. Split it out and repeat it once per number it covers, so ref k opens entry k.
# Only on pages where the split notes then line up 1..max(refs) exactly; the shared label is kept.
import re, json, sys
f = 'src/content/book-lg/tfsyr-mqatl-bn-slyman.md'; write = '--write' in sys.argv
s = open(f).read()
LABEL = re.compile(r'\((\d+(?:\s*[،,]\s*\d+)+)\)\s*')
def split(notes):
    out = []
    for n in notes:
        pieces = LABEL.split(n)   # [before, label, text, label, text, ...]
        if pieces[0].strip() or len(pieces) == 1: out.append(pieces[0].strip())
        for j in range(1, len(pieces), 2):
            nums = [int(x) for x in re.split(r'\s*[،,]\s*', pieces[j])]
            out += [f'({pieces[j]}) {pieces[j + 1].strip()}'] * len(nums)
    return out
fixed = skipped = 0
parts = re.split(r'(<hr class="page-sep"[^>]*/>)', s)
for i in range(1, len(parts), 2):
    m = re.search(r"data-notes='(.*?)'", parts[i])
    if not m or not LABEL.search(m.group(1)): continue
    notes = json.loads(m.group(1)); new = split(notes)
    body = re.sub(r'<!--[\s\S]*?-->', '', parts[i - 1])
    refs = {int(x) for x in re.findall(r'\(\^(\d+)\)', body)}
    if new != notes and refs and max(refs) == len(new):
        parts[i] = parts[i].replace(m.group(0), "data-notes='" + json.dumps(new, ensure_ascii=False).replace("'", "&#39;") + "'"); fixed += 1
    else: skipped += 1
print('pages fixed:', fixed, 'left alone:', skipped)
if write: open(f, 'w').write(''.join(parts))
