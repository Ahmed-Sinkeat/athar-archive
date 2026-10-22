# Sonnet cloud prompt: format the books outside the queue

Codex formats `docs/book-formatting-queue.tsv` locally. This session takes every **other** book,
in the cloud, from GitHub only. Start a cloud session on branch `android/m7` and paste everything
below the line.

---

You are formatting Arabic books in the Athar archive, alone, in a cloud session. Nobody answers
questions while you work: when something is unclear, skip it and write it down. One book at a
time, smallest first, until the list is empty or you run out of time.

## Setup

```bash
git fetch origin
git switch -c format/sonnet-cloud origin/android/m7   # or: git switch format/sonnet-cloud (resuming)
corepack enable && pnpm install --frozen-lockfile     # only tsx is needed; no build
mkdir -p /tmp/work
```

`<WORK>` for a book is `/tmp/work/<id>/`. Report files live in the repo, in `docs/format-sonnet/`
(create it), so they survive the session: `REPORT.md` and `SKIPPED.txt`.

## Read first (the standard; this prompt only adds how to work here)

1. `docs/book-formatting-rules.md`: the whole file.
2. `docs/book-formatting-agent-brief.md`: per-book steps, report format, "Mistakes reviewers
   caught". Follow it except where this prompt overrides it (git, paths, list).
3. Model commits, a few hundred lines each:
   `git show c6bc50a8 | head -400` · `git show d21641c7 | head -400` · `git show 15a42301` ·
   `git show 4d44a714 | head -200` · `git show b4b1698a`

**Rule changes newer than the repo copy of the rules (they win):**
- The card page is called «مقدمة المحقق». Cut **only the editor's** front matter into
  `<!-- card -->…<!-- /card -->`; the author's own مقدمة is part of the book and stays in the flow.
  Page markers inside the card leave the flow with it, so the text opens on its first page.
- The app parser never joins paragraphs across a page break (continuous reading only hides page
  dividers and page-end notes). Paragraphs stay as printed; leave page markers where they are.

## The list

Your books are every `src/content/book{,-lg}/*.md` **not** in the queue, not already formatted,
not in the exclusion list below (books someone else has uncommitted edits on), not skipped, and
at most 1 MB. Run before **each** book:

```bash
cat > /tmp/work/exclude.txt <<'IDS'
abnawna-walslah
aghathh-al-lhfan-fy-hkm-tlaq-al-ghdban
akhls-al-aml-fan-al-naqd-bsyr
al-abadh
al-abtla-wkyf-tstfyd-mnh-al-dawat
al-adab-al-aslamyh
al-ajrwmya
al-ajurrumiyyah
al-akhlaq-fy-al-aslam
al-fiqh-wal-shariah
al-lh-ltyf-babadh
al-qran-wawdh-al-mjd
al-risalah-al-mufidah
al-saadh
al-twbh-al-twbh-qbl-al-hsrat
al-zhr-al-fyah-qwt-al-qlwb-wghtha-al-arwah-mhbh-al-lh-jl-waz
aqeedat-ahl-al-sunnah-fil-sahabah-al-abbad
atwaq-al-hamamah
ayn-tkwn-atha-nwdy-llslah
br-al-waldyn-al-azhry
da-al-nfws-wsmwm-al-qlwb-al-maasy
dwa-al-qlwb-wsrtan-al-thnwb-al-twbh
ghayh-al-ghayat-wtryq-al-saadat-hsn-al-khatmh
hl-ant-mn-ahl-al-ghflh
hl-ant-mn-al-mraqbyn-llh-taala
hl-ant-mn-al-msarayn-ala-al-khyrat
hl-ant-mn-al-mtwklyn-ala-al-lh-taala
hl-jahdt-nfsk
hl-tfqdt-qlbk
hlyh-al-mtqyn-wthwb-al-salhyn-al-wra
hthh-hy-al-slah
kwnwa-ala-al-khyr-aawana
kyf-nhyy-qlwbna-fy-rmdan
kyf-tsbq
aaanh-al-talbyn-ala-hl-alfaz-fth-al-mayn
abnya-al-asma-walafaal-walmsadr
adwaa-al-bayan-shanqiti
adwat-al-iarab
ahkam-al-quran-ibn-al-firas
ajalh-al-mhtaj-ala-twjyh-al-mnhaj
akhbar-mka-llfakhy
akhsr-al-mkhtsrat-t-al-rkayz
akhtsar-shyh-al-bkhary-wbyan-ghrybh
al-alghaz-al-nhwya-al-traz-fy-al-alghaz
al-aqnaa-fy-fqh-al-amam-ahmd-bn-hnbl
al-aqtrah-fy-aswl-al-nhw-t-al-byrwty
al-ashraf-ala-nkt-msayl-al-khlaf
al-aswl-fy-al-nhw
al-awdh-ala-al-qran-lmatha-wkyf
al-azyz-shrh-al-wjyz-al-marwf-balshrh-al-kbyr-t-al-almyh
al-bdya-fy-alm-al-arbya
al-bida-al-hawliyyah
al-byan-fy-mthhb-al-amam-al-shafay
al-byan-walthsyl
al-dawat-al-kbyr
al-drwra-al-sharya-wmfhwmha-lda-al-nhwyyn-drasa-ala-alfya-bn-malk
al-durr-al-masun-samin-halabi
al-fatwa-al-hamawiyyah-al-kubra
al-fqh-al-mnhjy-ala-mthhb-al-amam-al-shafay
al-ftwhat-al-rbanyh-bshrh-al-drh-al-mdyh-fy-alm-al-qwaad-al
al-fwayd-al-mntkhbat-fy-shrh-akhsr-al-mkhtsrat
al-ghayh-fy-akhtsar-al-nhayh
al-hawy-al-kbyr
al-hdayh-ala-awham-al-kfayh
al-hdayh-ala-mthhb-al-amam-ahmd
al-hidayah-ila-bulugh-al-nihayah
al-hll-fy-shrh-abyat-al-jml
al-ibana-al-kbra-labn-bta
al-iman-al-awsat
al-iman-al-qasim-ibn-sallam
al-intisarat-al-islamiyyah
al-itiqad-al-khalis
al-iydah-fy-all-al-nhw
al-jama-al-shyh-llsnn-walmsanyd
al-jama-lalwm-al-amam-ahmd-al-fqh
al-khlafyat-byn-al-imamyn-al-shafay-waby-hnyfa-washabh-t-al-nhal
al-khsays
al-knash-fy-fny-al-nhw-walsrf
al-lbab-fy-al-fqh-al-shafay
al-lmha-fy-shrh-al-mlha
al-lubab-istiadhah-basmalah-fatihah
al-mawnh-ala-mthhb-aalm-al-mdynh
al-mdars-al-nhwya
al-mfsl-fy-snaa-al-iarab
al-mhmat-fy-shrh-al-rwdh-walrafay
al-mjalsa-wjwahr-al-alm
al-mjmwa-shrh-al-mhthb
al-mmta-fy-shrh-al-mqna-t-abn-dhysh-t-3
al-mnhaj-al-qwym-shrh-al-mqdmh-al-hdrmyh
al-mnh-al-shafyat-bshrh-mfrdat-al-amam-ahmd
al-mnsf-labn-jny-shrh-ktab-al-tsryf-laby-athman-al-mazny
al-mntkhb-mn-msnd-abd-bn-hmyd-t-mstfa-al-adwy
al-mnwr-fy-rajh-al-mhrr
al-mqasd-al-nhwya-fy-shrh-shwahd-shrwh-al-alfya
al-mqna-fy-fqh-al-amam-ahmd-t-al-arnawwt
al-mqsd-al-aly-fy-zwayd-aby-yala-al-mwsly
al-mrtjl-fy-shrh-al-jml-labn-al-khshab
al-msayl-al-fqhyh-mn-ktab-al-rwaytyn-walwjhyn
al-msayl-al-nhwya-fy-ktab-al-twdyh-lshrh-al-jama-al-shyh
al-msnd-al-mwdway-al-jama-llktb-al-ashra
al-mthhb-al-hnbly-drash-fy-tarykhh-wsmath
al-mtla-ala-dqayq-zad-al-mstqna-almaamlat-al-malyh
al-mtla-ala-dqayq-zad-al-mstqna-fqh-al-asrh
al-mtla-ala-dqayq-zad-al-mstqna-fqh-al-jnayat-walhdwd
al-mtla-ala-dqayq-zad-al-mstqna-fqh-al-qda-walshhadat
al-mtlb-al-hmyd-fy-byan-mqasd-al-twhyd
al-muharrar
al-mwjz-fy-qwaad-al-lgha-al-arbya
al-nasikh-wal-mansukh-al-qasim-ibn-sallam
al-nhw-al-msfa
al-nhw-al-wadh-fy-qwaad-al-lgha-al-arbya
al-nhw-al-wafy
al-njm-al-whaj-fy-shrh-al-mnhaj
al-nubuwwat
al-qadar-al-firyabi
al-qtwf-al-danya-fyma-anfrd-bh-al-darmy-an-al-thmanya
al-qwaad-al-ttbyqya-fy-al-lgha-al-arbya
al-qym-al-aslamyh
al-rawd-al-basim
al-rwd-al-mrba-bshrh-zad-al-mstnqa-mkhtsr-al-mqna-t-al-rkayz
al-rwd-al-ndy-shrh-kafy-al-mbtdy
al-sahih-al-masbur
al-sayf-al-maslul
al-shrh-al-kbyr-ala-al-mqna-t-al-trky
al-shrh-al-mkhtsr-ala-nzm-al-ajrwmya
al-shrh-al-mkhtsr-ala-nzm-al-mqswd
al-shrh-al-mmta-ala-zad-al-mstqna
al-shrh-al-mysr-lzad-al-mstqna-ktab-al-tharh-llhazmy
al-shrh-al-swty-lzad-al-mstqna-abn-athymyn
al-shyh-al-msnd-mn-athar-al-shabh-fy-al-zhd-walrqayq-walakhl
al-smaa-walqyas
al-tadmuriyyah
al-tafsir-al-nabawi-batili
al-tafsir-al-qayyim
al-talyqa-ala-ktab-sybwyh
al-talyq-al-kbyr-fy-al-msayl-al-khlafyh-byn-al-aymh-t-al-fry
al-talyqat-al-hsan-ala-shyh-abn-hban
al-talyqh-al-kbyrh-fy-msayl-al-khlaf-aly-mthhb-ahmd
al-talyqh-llqady-hsyn
al-tbsrh-llkhmy
al-tdryb-fy-al-fqh-al-shafay
al-tfsyr-mn-snn-sayd-bn-mnswr-mhqqa
al-thb-an-mthhb-al-amam-malk
al-thfa-al-wsyma-shrh-ala-al-dra-al-ytyma
al-thkhyrh-llqrafy
al-thryr-shrh-al-dlyl-ktab-al-tharh
al-ththyb-fy-fqh-al-amam-al-shafay
al-tnbyh-ala-mbady-al-twjyh-qsm-al-abadat
al-tnbyhat-al-mstnbth-ala-al-ktb-al-mdwnh-walmkhtlth
al-tnbyh-fy-al-fqh-al-shafay
al-ttbyq-al-nhwy
al-tthkrh-fy-al-fqh-labn-aqyl
al-twdyh-an-twhyd-al-khlaq-fy-jwab-ahl-al-araq-wtdhkra-awly-al-albab-fy-tryqa-al-shykh-mhmd-bn-abd-al-whab
al-twdyh-fy-shrh-mkhtsr-abn-al-hajb
al-twst-byn-malk-wabn-al-qasm-fy-al-msayl-al-ty-akhtlfa-fyha
al-urwah-al-wuthqa-qahtani
al-wjyz-fy-al-fqh-ala-mthhb-al-amam-ahmd-bn-hnbl
amdh-al-hazm-fy-al-zwayd-ala-mkhtsr-aby-al-qasm
amdh-al-salk-wadh-al-nask
anh-al-qran-sr-nhdtna-kyf-ymkn-llqran-an-ynhd-balamh
aqd-al-jwahr-al-thmynh-fy-mthhb-aalm-al-mdynh
aqeedat-al-muslim-qahtani
arshad-al-salk-ala-afaal-al-mnask
ashl-al-mdark-shrh-arshad-al-salk-fy-mthhb-amam-al-aymh-malk
atyab-al-nashr-al-wasaya-al-ashr
awdh-al-msalk-ila-alfya-abn-malk
aywn-al-msayl-llqady-abd-al-whab-al-malky
bayan-talbis-al-jahmiyyah
bdayh-al-mjthd-wnhayh-al-mqtsd
bhr-al-mthhb-llrwyany
bulugh-al-maram
dar-taarud-al-aql-wal-naql
dayf-al-jama-al-sghyr-wzyadth
drws-trbwyh-mn-al-ahadyth-al-nbwyh
dya-al-salk-ila-awdh-al-msalk
dywan-al-ahkam-al-kbra-aw-al-aalam-bnwazl-al-ahkam-wqtr-mn-s
fqh-al-syam-walhj-mn-dlyl-al-talb
fsl-al-khtab-fy-al-zhd-walrqayq-waladab
ftawa-wrsayl-smaha-al-shykh-abd-al-rzaq-afyfy-qsm-al-aqyda
fth-al-aqfal-whl-al-ishkal-bshrh-lamya-al-afaal-al-mshhwr-balshrh-al-kbyr
fth-al-mtaal-ala-al-qsyda-al-msmaa-blamya-al-afaal
ghayh-al-mntha-fy-jma-al-aqnaa-walmntha
hashya-al-sban-ala-shrh-al-ashmwna-lalfya-abn-malk
hashyh-al-khlwty-ala-mntha-al-aradat
hashyh-al-lbdy-ala-nyl-al-marb
hashyh-al-rwd-al-mrba
hashyta-qlywby-wamyrh
hktha-kan-al-salhwn
hlyh-al-alma-fy-marfh-mthahb-al-fqha-t-al-rsalh-al-hdythh
hujjiyyat-al-qiyas-war-radd-ala-al-mukhalifin
iarab-ma-yshkl-mn-alfaz-al-hdyth
irab-al-quran-darwish
irshad-al-salk-ila-hl-alfya-abn-malk
ithaf-al-hthyth-biarab-ma-yshkl-mn-alfaz-al-hdyth
jama-mamr-bn-rashd
jwahr-al-aqwd
kfayh-al-nbyh-fy-shrh-al-tnbyh
klmat-al-sdad-ala-mtn-al-zad
kshf-ma-alqah-iblys-mn-al-bhrj-waltlbys-ala-qlb-dawd-bn-jrjys
ktab-al-afaal
kyf-nhb-al-lh-wnshtaq-alyh
maany-al-nhw
maarij-al-qubul
malmh-al-fqh-al-malky
masra-al-tasawwuf
matqd-ahl-al-sna-waljmaaa-fy-asma-al-lh-al-hsna
mhw-al-amyh-al-trbwyh
mkhtsr-al-afadat-fy-rba-al-abadat-waladab-wzyadat
mkhtsr-shyh-al-imam-al-bkhary
mkhtsr-shyh-mslm-llmndhry-t-al-albany
mkhtsr-snn-aby-dawd-llmndhry-t-hlaq
mnahj-al-thsyl-wntayj-ltayf-al-tawyl-fy-shrh-al-mdwnh-whl-ms
mnar-al-sbyl-fy-shrh-al-dlyl
mnhaj-al-tasys-waltqdys-fy-kshf-shbhat-dawd-bn-jrjys
mnhj-al-tlab-fy-fqh-al-amam-al-shafay-rdy-al-lh-anh
mn-tarykh-al-nhw-al-arby
mowatta-malik
msayl-al-amam-ahmd-washaq-bn-rahwyh
msayl-hrb-al-krmany-mn-ktab-al-nkah-ala-nhayh-al-ktab-t-fayz
msnd-aby-hnyfa-rwaya-aby-naym
msnd-al-bzar-al-bhr-al-zkhar
msnd-al-farwq-t-imam
mstkhrj-aby-awana-t-al-jamaa-al-islamya
mtmma-al-ajrwmya
mujam-wa-tafsir-lughawi
musnad-ahmad
mward-al-zman-ila-zwayd-abn-hban-t-hsyn-asd
mwswah-al-akhlaq-al-aslamyh-al-drr-al-snyh
mwswah-al-rqayq-waladb-yasr-al-hmdany
mwta-malk-rwaya-aby-msab-al-zhry
naqd-uthman-ibn-said-ala-al-marisi
nhayh-al-mtlb-fy-drayh-al-mthhb
nyl-al-marb-bshrh-dlyl-al-talb
nzrat-fy-al-trbyh-al-aymanyh
qaidah-jalilah-fil-tawassul
qryna-al-syaq-wdwrha-fy-al-tqayd-al-nhwy-waltwjyh-al-iaraby-fy-ktab-sybwyh
rfqa-balqwaryr-nsayh-llazwaj
rsala-al-mlayka
rsala-al-mlayka-t-al-jndy
rsayl-wftawa-abd-al-azyz-al-al-shykh
sahih-bukhari
sfh-al-mfty-walmstfty-t-aby-jnh
sfr-al-saada-wsfyr-al-ifada
shab-al-iyman
sharh-al-aqeedah-al-isfahaniyyah
sharh-al-waraqat-khudayr
sharh-hadith-al-nuzul
shrha-aby-al-ala-walkhtyb-al-tbryzy-ala-dywan-aby-tmam-drasa-nhwya-srfya
shrh-abn-al-nazm-ala-alfya-abn-malk
shrh-abn-aqyl-ala-alfya-abn-malk
shrh-akhsr-al-mkhtsrat
shrh-al-ajrwmya-abd-al-krym-al-khdyr
shrh-al-amdh-labn-tymyh-ktab-al-slah
shrh-al-amdh-labn-tymyh-ktab-al-syam
shrh-al-amdh-labn-tymyh-sfh-al-slah
shrh-al-ashmwna-lalfya-abn-malk
shrh-alfya-abn-malk-al-msma-thryr-al-khsasa-fy-tysyr-al-khlasa
shrh-alfya-abn-malk-llhazmy
shrh-al-kafya-al-shafya
shrh-al-mfsl-labn-yaysh
shrh-al-mnzwmh-al-mymyh-fy-al-adab-al-shrayh
shrh-al-mqdma-al-mhsba
shrh-al-thawya-lsalh-al-al-shykh-ithaf-al-sayl-bma-fy-al-thawya-mn-msayl
shrh-al-tsryf-llthmanyny
shrh-al-tsryh-ala-al-twdyh-aw-al-tsryh-bmdmwn-al-twdyh-fy-al-nhw
shrhan-ala-mrah-al-arwah-fy-alm-al-srf
shrh-ktab-al-alm-laby-khythmh-abd-al-krym-al-khdyr
shrh-ktab-al-hdwd-fy-al-nhw
shrh-ktab-al-twhyd-labn-khzyma-mhmd-hsn-abd-al-ghfar
shrh-ktab-sybwyh-llrmany
shrh-ktab-sybwyh
shrh-maany-al-athar
shrh-mkhtsr-al-khrqy-abd-al-krym-al-khdyr
shrh-mshkl-al-athar
shrh-mshkl-al-wsyt
shrh-qwaad-al-iarab
shrh-shafya-abn-al-hajb-al-rdy-al-astrabadhy
shrh-shafya-abn-al-hajb-rkn-al-dyn-al-astrabadhy
shrh-shdhwr-al-dhhb-lljwjry
shrh-zad-al-mstqna-abd-al-krym-al-khdyr
shrh-zad-al-mstqna-llkhlyl
shyh-abn-hban-mhqqa
shyh-abn-hban-mkhrja
shyh-abn-khzyma-t
shyh-knwz-al-snh-al-nbwyh
shyh-mward-al-zman-ila-zwayd-abn-hban
sirah-ibn-hisham
slslh-al-qss-al-mnjd
sunan-abi-dawud
sunan-al-darimi
sunan-al-nasai
sunan-ibn-majah
tafsir-al-samani
tafsir-baghawi
tafsir-ibn-abi-al-izz
tafsir-ibn-abi-hatim
tafsir-ibn-al-mundhir
tafsir-ibn-badis
tafsir-ibn-wahb
tafsir-juz-amma-musaid-tayyar
tafsir-qurtubi
tafsir-sufyan-thawri
talyq-al-frayd-ala-tshyl-al-fwayd
tariq-al-hidayah-tawhid
taysir-al-karim-al-rahman
thbyr-al-mkhtsr-whw-al-shrh-al-wst-lbhram-ala-mkhtsr-khlyl
thfh-al-mhtaj-fy-shrh-al-mnhaj-whwashy-al-shrwany-walabady
thfh-al-raka-walsajd-bahkam-al-msajd
thqyq-al-mtalb-bshrh-dlyl-al-talb
thqyq-al-wsal-byn-al-qlb-walqran
thryr-al-ftawa
thryr-al-klam-fy-msayl-al-altzam
tmhyd-al-qwaad-bshrh-tshyl-al-fwayd
twdyh-al-mqasd-walmsalk-bshrh-alfya-abn-malk
umdat-al-ahkam
wbl-al-ghmamh-fy-shrh-amdh-al-fqh-labn-qdamh
zad-al-mstqna-fy-akhtsar-al-mqna
zad-al-mustaqni
lughat-al-qabail-al-qasim-ibn-sallam
maqasid-al-shariah-al-islamiyyah
matha-aaddt-llmwt
matha-nryd-mn-rmdan
mlha-al-iarab
mnazl-al-ahzan-wabrh-al-yqzan-al-nar
mnazl-al-srwr-wdar-al-hbwr-al-jnh
mtn-aby-shjaa-al-msma-al-ghayh-waltqryb
muqaddimah-fi-usul-al-tafsir
nukhbat-al-fikar
qata-al-afrah-wmzhq-al-arwah-al-mwt
rsalh-ala-jar-al-msjd
sharh-al-sunnah-al-barbahari
shrh-ktab-al-syam-mn-amdh-al-fqh-abd-al-krym-al-khdyr
waslhna-lh-zwjh
IDS
done=$(git log --format=%s --grep='(book):' | sed -E 's/^[a-z]+\(book\): ([^ ]+).*/\1/' | sort -u)
skipped=$(cat docs/format-sonnet/SKIPPED.txt 2>/dev/null)
queue=$(grep -v '^#' docs/book-formatting-queue.tsv | cut -f1)
for p in src/content/book/*.md src/content/book-lg/*.md; do
  [ $(stat -c%s "$p") -le 1048576 ] || continue
  id=$(basename "$p" .md)
  echo "$queue" | grep -qx "$p" && continue
  echo "$done$skipped" | grep -qx "$id" && continue
  grep -qx "$id" /tmp/work/exclude.txt && continue
  echo "$(stat -c%s "$p") $p"
done | sort -n | head -1 | cut -d' ' -f2
```

The path printed is your next `<BOOK>`. Many of these books have few or no footnotes: then the
work is headings, verses, quotes, bold labels, numbered points and the card. A book that needs
nothing gets no commit; add its id to `SKIPPED.txt` with "nothing to format" in the report.

## Per book

1. Brief steps 1–5 for `<BOOK>` (survey, markup, strict gate, read every anchor/note pair,
   import fixes only if any). Gate: `./node_modules/.bin/tsx scripts/format-verify.ts <BOOK>`
2. **Commit the markup** (strict gate `ok` after your last edit):
   `git add <BOOK> && git commit -m "format(book): <id> — <short>" -m "<full report, brief's format>"`
3. **Import fixes, if any**, in a second commit, gated against the commit you just made:
   `./node_modules/.bin/tsx scripts/format-verify.ts --import-fix <BOOK>`, then
   `git commit -am "fix(book): <id> — <page: what>"`. Pure moves/renumbers/duplicate markers only.
4. Append one row to `docs/format-sonnet/REPORT.md`:
   `| <id> | committed / skipped | anchors N, headings N, … | import fixes | REPORT ONLY items |`
   and commit it: `git commit -am "docs(format-sonnet): <id>"`.
5. `git push origin format/sonnet-cloud` after every book. `git status --short` must be empty
   before the next one.

**Give up on a book** when the strict gate still fails after three honest attempts, the notes and
anchors don't line up for most of the book, or about 45 minutes have passed: `git restore <BOOK>`,
add its id to `docs/format-sonnet/SKIPPED.txt`, write why in `REPORT.md`, commit, next book. A
partly formatted book is fine when the parts you left are named in the report; an unchecked guess
is not.

## Hard rules

- Change markup, never text. The gate is the judge; never edit `scripts/format-verify.ts`,
  `src/lib/`, frontmatter (including `kind`), or any file except `<BOOK>` and `docs/format-sonnet/*`.
- Import errors matter most. Lost text, a book that stops early, cut hadith numbers, a note with
  no anchor: **report only**, with proof (quote the words on both sides). Never fill a gap, never
  use another edition's text.
- Git: only `add`/`commit`/`restore` of those files, and `push` of `format/sonnet-cloud`. Never
  push any other branch, never merge, rebase, reset, amend, force-push, or open a PR.
- No `pnpm build`, no `pnpm app:gen`, no dev server. Only `./node_modules/.bin/tsx` scratch
  scripts (`.mts`, kept in `<WORK>`). No network besides git and the one install.
- Read big Arabic files in slices; use scripts for mechanical edits, then read what they changed.
- Don't paste Arabic character classes into regexes by hand (bidi corrupts them); build them
  from `\u` escapes.

## When you stop

Finish the book you are on (commit or restore), then write the top of `REPORT.md`: books
committed, books skipped (and why), every REPORT ONLY item grouped by book. Commit and push.
