// Share composer — dynamically imported from the مشاركة button (marks.ts
// only keeps a cheap "is this page citable at all" check) so none of this,
// including citation-metadata extraction, ships in the site-wide critical
// bundle (render-budget gate).

// book/author/kind come from the nearest [data-cite-book] ancestor (set
// server-side per reading page type): plain book/article/question pages cite
// by page number, poem pages by بيت number (Verse.astro's data-vn).
type CiteKind = "book" | "poem";
interface CiteMeta {
  kind: CiteKind; book: string; author: string; url: string;
  page?: string; pageTo?: string; juz?: string;
  vn?: number; vnTo?: number;
}
function elOf(node: Node): Element | null {
  return node.nodeType === 1 ? (node as Element) : node.parentElement;
}
function citeMeta(range: Range): { meta: CiteMeta; container: HTMLElement } | null {
  const startEl = elOf(range.startContainer);
  const endEl = elOf(range.endContainer);
  const container = startEl?.closest<HTMLElement>("[data-cite-book]");
  if (!container) return null;
  const kind = (container.dataset.citeKind as CiteKind) || "book";
  const book = container.dataset.citeBook || "";
  const author = container.dataset.citeAuthor || "";
  const url = location.origin + location.pathname;

  if (kind === "poem") {
    const vnStr = startEl?.closest<HTMLElement>(".verse[data-vn]")?.dataset.vn;
    const vnToStr = endEl?.closest<HTMLElement>(".verse[data-vn]")?.dataset.vn;
    const vn = vnStr ? Number(vnStr) : undefined;
    return { meta: { kind, book, author, url, vn, vnTo: vnToStr ? Number(vnToStr) : vn }, container };
  }
  let page: string | undefined, juz: string | undefined;
  container.querySelectorAll<HTMLElement>(".page-sep[data-page]").forEach((el) => {
    const pos = el.compareDocumentPosition(range.startContainer);
    if (pos === 0 || pos & Node.DOCUMENT_POSITION_FOLLOWING) { page = el.dataset.page; juz = el.dataset.juz; }
  });
  return { meta: { kind, book, author, url, page, pageTo: page, juz }, container };
}

function buildCitation(meta: CiteMeta, fallbackText: string): { text: string; url: string } {
  if (meta.kind === "poem" && meta.vn) {
    const ref = meta.vnTo && meta.vnTo !== meta.vn ? `الأبيات ${meta.vn}-${meta.vnTo}` : `البيت ${meta.vn}`;
    const parts = [meta.book, meta.author, ref].filter(Boolean);
    return { text: `"${fallbackText}"\n— ${parts.join("، ")}`, url: meta.url };
  }
  const pageRef = meta.page ? (meta.pageTo && meta.pageTo !== meta.page ? `ص ${meta.page}-${meta.pageTo}` : `ص ${meta.page}`) : "";
  const parts = [meta.book, meta.author, pageRef, meta.juz ? `ج ${meta.juz}` : ""].filter(Boolean);
  return { text: `"${fallbackText}"\n— ${parts.join("، ")}`, url: meta.url };
}

// lets the reader widen/narrow the cited range (page for a book, بيت for a
// poem) before copying or handing off to the share sheet.
export function openShare(toolbar: HTMLElement, range: Range, text: string, done: () => void) {
  const cite = citeMeta(range);
  if (!cite) return;
  const { meta } = cite;
  toolbar.innerHTML = "";
  const wrap = document.createElement("div");
  wrap.className = "aa-share";

  let fromInput: HTMLInputElement | null = null, toInput: HTMLInputElement | null = null;
  const rangeRow = (label1: string, v1: string | number, label2: string, v2: string | number) => {
    const row = document.createElement("div");
    row.className = "aa-share-range";
    const l1 = document.createElement("span"); l1.textContent = label1;
    fromInput = document.createElement("input"); fromInput.type = "number"; fromInput.min = "1"; fromInput.value = String(v1);
    const l2 = document.createElement("span"); l2.textContent = label2;
    toInput = document.createElement("input"); toInput.type = "number"; toInput.min = "1"; toInput.value = String(v2);
    row.append(l1, fromInput, l2, toInput);
    wrap.appendChild(row);
  };
  if (meta.kind === "poem" && meta.vn) rangeRow("من بيت", meta.vn, "إلى", meta.vnTo || meta.vn);
  else if (meta.kind === "book" && meta.page) rangeRow("من صفحة", meta.page, "إلى", meta.pageTo || meta.page);

  const currentMeta = (): CiteMeta => {
    if (!fromInput || !toInput) return meta;
    const m = { ...meta };
    if (meta.kind === "poem") { m.vn = Number(fromInput.value); m.vnTo = Number(toInput.value); }
    else { m.page = fromInput.value; m.pageTo = toInput.value; }
    return m;
  };

  const actions = document.createElement("div");
  actions.className = "aa-share-actions";
  const copyBtn = document.createElement("button");
  copyBtn.type = "button";
  copyBtn.textContent = "نسخ النص";
  const linkBtn = document.createElement("button");
  linkBtn.type = "button";
  linkBtn.textContent = "نسخ الرابط";
  const shareBtn = document.createElement("button");
  shareBtn.type = "button";
  shareBtn.textContent = "مشاركة";
  shareBtn.classList.add("aa-share-primary");
  actions.append(shareBtn, copyBtn, linkBtn);
  wrap.appendChild(actions);
  toolbar.appendChild(wrap);
  toolbar.setAttribute("data-open", "");

  copyBtn.addEventListener("mousedown", (e) => e.preventDefault());
  copyBtn.addEventListener("click", () => {
    const { text: cited, url } = buildCitation(currentMeta(), text);
    navigator.clipboard?.writeText(`${cited}\n${url}`).then(() => { copyBtn.textContent = "تم النسخ ✓"; setTimeout(done, 900); });
  });
  linkBtn.addEventListener("mousedown", (e) => e.preventDefault());
  linkBtn.addEventListener("click", () => {
    navigator.clipboard?.writeText(meta.url).then(() => { linkBtn.textContent = "تم النسخ ✓"; setTimeout(done, 900); });
  });
  shareBtn.addEventListener("mousedown", (e) => e.preventDefault());
  shareBtn.addEventListener("click", async () => {
    const { text: cited, url } = buildCitation(currentMeta(), text);
    if (navigator.share) {
      // url goes INSIDE text: some Android targets (Telegram, WhatsApp) take
      // only one of {text, url} from the share intent, dropping the link
      try { await navigator.share({ text: `${cited}\n${url}` }); done(); } catch { /* user cancelled */ }
    } else {
      navigator.clipboard?.writeText(`${cited}\n${url}`).then(() => { shareBtn.textContent = "تم النسخ ✓"; setTimeout(done, 1200); });
    }
  });
}
