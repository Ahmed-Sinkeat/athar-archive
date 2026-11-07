# تفسير مقاتل: each "## [سورة X (n) : الآيات a الى b]" heading is followed by the whole surah's text as
# one paragraph "… (1) … (b)". Hide that paragraph in an HTML comment (the app skips comments; the text
# stays in the file). Only when its ayah numbers run a..b in order and it holds no other markup.
import re, sys
f = sys.argv[1]; write = '--write' in sys.argv
s = open(f).read()
HEAD = re.compile(r'^## \[سورة (.+?) \((\d+)\) : الآيات (\d+) الى (\d+)\]\n\n', re.M)
out = []; pos = 0; ok = bad = 0
for m in HEAD.finditer(s):
    start = m.end(); end = s.find('\n\n', start); end = len(s) if end < 0 else end
    para = s[start:end]
    nums = [int(x) for x in re.findall(r'\((\d+)\)', para)]
    a, b = int(m.group(3)), int(m.group(4))
    # ayah numbers in runs that each restart at 1 (الرعد / القمر ran into the next surahs' text),
    # and no tafsir between them: no «يعني», no "- n -" numbering
    runs_ok = bool(nums) and nums[0] == a and all(y == x + 1 or y == 1 for x, y in zip(nums, nums[1:]))
    pure = 'يعني' not in para and not re.search(r'- ?\d+ ?-', para)
    if runs_ok and pure and '<' not in para and '--' not in para and '(^' not in para:
        out.append(s[pos:start] + '<!-- نص السورة كاملا، مخفي: يأتي مفرقا في التفسير\n' + para + '\n-->'); pos = end; ok += 1
    else:
        bad += 1; print('SKIP', m.group(1), m.group(2), f'{a}-{b}', 'nums:', nums[:3], '…', nums[-3:], len(nums))
out.append(s[pos:])
new = ''.join(out)
print('hidden:', ok, 'skipped:', bad)
if write and ok: open(f, 'w').write(new)
