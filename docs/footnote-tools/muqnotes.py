# تفسير مقاتل: per page, refs "(^n)" in the body vs notes on the page marker. Classify the mismatches.
import re, json, sys, collections
s = open('src/content/book-lg/tfsyr-mqatl-bn-slyman.md').read()
s = re.sub(r'<!--[\s\S]*?-->', '', s)
parts = re.split(r'(<hr class="page-sep"[^>]*/>)', s)
cnt = collections.Counter(); ex = collections.defaultdict(list)
for i in range(1, len(parts), 2):
    body, marker = parts[i - 1], parts[i]
    m = re.search(r"data-notes='(.*?)'", marker)
    notes = json.loads(m.group(1)) if m else []
    refs = sorted({int(x) for x in re.findall(r'\(\^(\d+)\)', body)})
    page = re.search(r'data-page="([^"]*)"', marker).group(1)
    if not refs or max(refs) <= len(notes): continue
    combined = [n for n in notes if re.search(r'\(\d+\s*[،,-]\s*\d+', n)]
    k = 'shared note «(3، 4، 5) …» merged into the one before' if combined else 'fewer notes than refs, no shared note'
    cnt[k] += max(refs) - len(notes)
    if len(ex[k]) < 4: ex[k].append(f'p{page}: refs {refs} notes {len(notes)} | ' + ' ‖ '.join(x[:60] for x in notes))
for k, v in cnt.items():
    print(v, 'refs past the last note —', k)
    for e in ex[k]: print('    ', e[:230])
