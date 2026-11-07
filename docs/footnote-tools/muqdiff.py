import re,json,pickle,difflib,sys
D='/tmp/claude-1000/-home-sinkeat--claude-spares-spare-JzFSp3/074a5745-03dd-4920-abe6-d6124ee9304b/scratchpad'
E=pickle.load(open(f'{D}/muqepub.pkl','rb'))  # our own cache from muqepub.py
s=open('src/content/book-lg/tfsyr-mqatl-bn-slyman.md').read()
for juz,pg in [a.split('/') for a in sys.argv[1:]]:
    m=re.search(r'data-page="%s" data-juz="%s"[^>]*?data-notes=\'(.*?)\''%(pg,juz),s)
    md=''.join(json.loads(m.group(1))); ep=' '.join(n[1] for n in E[(juz,pg)][0][1])
    a,b=re.sub(r'\s','',md),re.sub(r'\s','',ep)
    print('==',juz,pg)
    for op,i1,i2,j1,j2 in difflib.SequenceMatcher(None,a,b,autojunk=False).get_opcodes():
        if op!='equal': print('  ',op,repr(a[max(0,i1-8):i2+3]),'→',repr(b[max(0,j1-8):j2+3]))
