// Force-directed knowledge graph for /graph. Reads the embedded #gdata JSON and
// renders an interactive canvas: drag a node to move/pin it, hover to highlight its
// neighbourhood, click to open it, and toggle types via the legend. Re-inits on
// each view-transition navigation; guarded so it builds once per fresh page.
import ForceGraph from "force-graph";

// Node colors come from the design tokens so the graph wears the same palette
// as the rest of the site (and re-themes with paper/noir/mono). One accent per
// tradition (HANDOFF §1): كتب/مسائل maroon, شعر copper, أعلام green, موضوعات gold.
const TOKEN_OF: Record<string, string> = {
  book: "--brand", question: "--brand", series: "--brand2",
  poem: "--copper", article: "--copper",
  person: "--green",
  topic: "--gold", benefit: "--gold",
  subject: "--ink2",
};
function themeColors(): Record<string, string> {
  const cs = getComputedStyle(document.documentElement);
  const out: Record<string, string> = {};
  for (const [t, v] of Object.entries(TOKEN_OF)) out[t] = cs.getPropertyValue(v).trim() || "#888";
  return out;
}
let COLORS = themeColors();
new MutationObserver(() => { COLORS = themeColors(); }).observe(document.documentElement, { attributeFilter: ["data-theme"] });
const FADE = "rgba(120,110,95,0.18)";

function normalizeArabic(str: string): string {
  return str
    .replace(/[أإآا]/g, "ا")
    .replace(/ة/g, "ه")
    .replace(/ى/g, "ي")
    .replace(/[ؐ-ًؚ-ٰٟۖ-ۭـ]/g, "") // remove diacritics
    .toLowerCase()
    .trim();
}

function cleanLatin(s: string): string {
  let cleaned = s.toLowerCase();
  cleaned = cleaned.replace(/\bal-/g, "").replace(/\bel-/g, "");
  cleaned = cleaned
    .replace(/[aeiouy]/g, "")
    .replace(/th/g, "t")
    .replace(/dh/g, "d")
    .replace(/z/g, "s")
    .replace(/q/g, "k")
    .replace(/g/g, "j")
    .replace(/h$/g, "")
    .replace(/[^a-z0-9]/g, "");
  return cleaned;
}

function isMatch(label: string, id: string, query: string): boolean {
  const isArabic = /[\u0600-\u06FF]/.test(query);
  if (isArabic) {
    return normalizeArabic(label).includes(normalizeArabic(query));
  } else {
    const cleanedQuery = cleanLatin(query);
    if (!cleanedQuery) return false;
    return cleanLatin(id).includes(cleanedQuery) || cleanLatin(label).includes(cleanedQuery);
  }
}

interface GNode { id: string; label: string; type: string; href: string; val?: number; x?: number; y?: number; fx?: number; fy?: number }
interface GLink { source: any; target: any }
const lid = (e: any) => (typeof e === "object" ? e.id : e);

function init() {
  const el = document.getElementById("graph") as HTMLElement | null;
  const dataEl = document.getElementById("gdata");
  if (!el || !dataEl || el.dataset.ready) return;
  el.dataset.ready = "1";

  const full = JSON.parse(dataEl.textContent || '{"nodes":[],"links":[]}') as { nodes: GNode[]; links: GLink[] };
  const deg: Record<string, number> = {};
  for (const l of full.links) { deg[lid(l.source)] = (deg[lid(l.source)] || 0) + 1; deg[lid(l.target)] = (deg[lid(l.target)] || 0) + 1; }
  for (const n of full.nodes) n.val = 1 + (deg[n.id] || 0);

  let ink = getComputedStyle(document.documentElement).getPropertyValue("--ink").trim() || "#3a322a";
  const obs = new MutationObserver(() => { ink = getComputedStyle(document.documentElement).getPropertyValue("--ink").trim() || "#3a322a"; });
  obs.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });

  // hover-highlight state
  let hoverId: string | null = null;
  const near = new Set<string>();
  const hot = new Set<GLink>();
  
  // search-highlight state
  let searchQuery = "";
  let wasSearched = false;
  const searchNear = new Set<string>();
  const searchHot = new Set<GLink>();
  const searchMatch = new Set<string>();

  const graphSearch = document.getElementById("graph-search") as HTMLInputElement | null;

  function setHover(node: GNode | null) {
    hoverId = node ? node.id : null;
    near.clear(); hot.clear();
    if (!node) {
      // Restore search highlights if hover ended
      g.nodeColor(g.nodeColor());
      g.linkColor(g.linkColor());
      g.linkWidth(g.linkWidth());
      return;
    }
    near.add(node.id);
    for (const l of g.graphData().links as GLink[]) {
      if (lid(l.source) === node.id || lid(l.target) === node.id) { hot.add(l); near.add(lid(l.source)); near.add(lid(l.target)); }
    }
    g.nodeColor(g.nodeColor());
    g.linkColor(g.linkColor());
    g.linkWidth(g.linkWidth());
  }

  function setSearchQuery(q: string) {
    searchQuery = q.trim();
    searchNear.clear();
    searchHot.clear();
    searchMatch.clear();

    if (searchQuery) {
      wasSearched = true;
      const graphData = g.graphData();
      for (const n of graphData.nodes as GNode[]) {
        if (isMatch(n.label, n.id, searchQuery)) {
          searchMatch.add(n.id);
          searchNear.add(n.id);
        }
      }

      if (searchMatch.size > 0) {
        for (const l of graphData.links as GLink[]) {
          const s = lid(l.source);
          const t = lid(l.target);
          if (searchMatch.has(s) || searchMatch.has(t)) {
            searchHot.add(l);
            searchNear.add(s);
            searchNear.add(t);
          }
        }

        // Center / zoom on match
        const matches = [...searchMatch].map(id => (g.graphData().nodes as GNode[]).find(n => n.id === id)).filter(Boolean) as GNode[];
        let sumX = 0, sumY = 0, count = 0;
        for (const m of matches) {
          if (m.x !== undefined && m.y !== undefined) {
            sumX += m.x;
            sumY += m.y;
            count++;
          }
        }
        if (count > 0) {
          const avgX = sumX / count;
          const avgY = sumY / count;
          const zoomLevel = matches.length === 1 ? 2.5 : (matches.length <= 5 ? 1.8 : 1.0);
          g.centerAt(avgX, avgY, 800);
          g.zoom(zoomLevel, 800);
        }
      }
    } else {
      if (wasSearched) {
        wasSearched = false;
        g.zoomToFit(800, 50);
      }
    }

    g.nodeColor(g.nodeColor());
    g.linkColor(g.linkColor());
    g.linkWidth(g.linkWidth());
  }

  graphSearch?.addEventListener("input", () => {
    setSearchQuery(graphSearch.value);
  });

  const g = new ForceGraph(el)
    .graphData({ nodes: full.nodes, links: full.links })
    .nodeId("id")
    .nodeVal("val")
    .nodeRelSize(5)
    .enableNodeDrag(true)
    .nodeColor((n: any) => {
      if (hoverId) return near.has(n.id) ? COLORS[n.type] || "#888" : FADE;
      if (searchQuery) return searchNear.has(n.id) ? COLORS[n.type] || "#888" : FADE;
      return COLORS[n.type] || "#888";
    })
    .nodeCanvasObjectMode(() => "after")
    .nodeCanvasObject((n: any, ctx: CanvasRenderingContext2D, scale: number) => {
      // If search is active, hide label completely if not matched or connected
      if (searchQuery && !searchNear.has(n.id)) return;

      // declutter: when zoomed out, label only hubs + the highlighted/searched neighbourhood
      const isHighlighted = hoverId ? near.has(n.id) : (searchQuery ? searchNear.has(n.id) : false);
      if (scale < 0.6 && (deg[n.id] || 0) < 3 && !isHighlighted) return;
      
      const r = Math.sqrt(n.val) * 5;
      ctx.font = `${Math.min(5, 12 / scale)}px "IBM Plex Sans Arabic", system-ui, sans-serif`;
      ctx.textAlign = "center"; ctx.textBaseline = "top";
      
      let fillStyle = ink;
      if (hoverId) {
        fillStyle = near.has(n.id) ? ink : FADE;
      } else if (searchQuery) {
        fillStyle = searchNear.has(n.id) ? ink : FADE;
      }
      ctx.fillStyle = fillStyle;
      ctx.fillText(n.label, n.x, n.y + r + 1.5);
    })
    .linkColor((l: any) => {
      if (hoverId) return hot.has(l) ? "rgba(156,59,50,0.6)" : "rgba(120,110,95,0.06)";
      if (searchQuery) return searchHot.has(l) ? "rgba(156,59,50,0.6)" : "rgba(120,110,95,0.06)";
      return "rgba(120,110,95,0.22)";
    })
    .linkWidth((l: any) => {
      if (hoverId) return hot.has(l) ? 1.8 : 1;
      if (searchQuery) return searchHot.has(l) ? 1.8 : 1;
      return 1;
    })
    .linkCurvature(0.08)
    .onNodeHover((n: any) => { setHover(n); el.style.cursor = n ? "pointer" : ""; })
    .onNodeDragEnd((n: any) => { n.fx = n.x; n.fy = n.y; }) // pin where dropped
    .onNodeClick((n: any) => { if (n.href) location.href = n.href; })
    .width(el.clientWidth)
    .height(el.clientHeight);

  // floaty, elastic Obsidian-style bubble physics
  (g.d3Force("charge") as any)?.strength(-220);
  (g.d3Force("link") as any)?.distance(35);
  g.d3VelocityDecay(0.16);

  // type filter from the legend checkboxes and person checklist
  const legend = document.querySelector("[data-graph-legend]");
  const personSearch = document.getElementById("person-search") as HTMLInputElement;
  const personList = document.querySelector(".graph-person-list");

  function updateGraph() {
    const totalPersons = document.querySelectorAll(".person-toggle").length;
    const checkedPersons = document.querySelectorAll(".person-toggle:checked").length;
    const counterEl = document.getElementById("scholar-counter");
    if (counterEl) {
      const toArabicDigitsStr = (num: number) => String(num).replace(/[0-9]/g, (d) => "٠١٢٣٤٥٦٧٨٩"[parseInt(d)]);
      counterEl.textContent = `(${toArabicDigitsStr(checkedPersons)} / ${toArabicDigitsStr(totalPersons)})`;
    }

    const active = new Set(
      [...document.querySelectorAll<HTMLInputElement>("[data-graph-legend] input:checked")]
        .map((c) => c.closest("[data-type]")?.getAttribute("data-type") || ""),
    );
    const activePersons = new Set(
      [...document.querySelectorAll<HTMLInputElement>(".person-toggle:checked")]
        .map((c) => `person:${c.value}`),
    );
    
    const nodes = full.nodes.filter((n) => {
      if (n.type === "person") return activePersons.has(n.id);
      return active.has(n.type);
    });
    
    const ids = new Set(nodes.map((n) => n.id));
    const links = full.links.filter((l) => ids.has(lid(l.source)) && ids.has(lid(l.target)));
    setHover(null);
    g.graphData({ nodes, links });

    // Also re-apply search highlight on newly updated graph data
    setSearchQuery(graphSearch?.value || "");
  }

  legend?.addEventListener("change", updateGraph);
  personList?.addEventListener("change", updateGraph);

  const legendSelectAll = document.getElementById("legend-select-all");
  const legendClearAll = document.getElementById("legend-clear-all");
  const personSelectAll = document.getElementById("person-select-all");
  const personClearAll = document.getElementById("person-clear-all");

  legendSelectAll?.addEventListener("click", () => {
    document.querySelectorAll<HTMLInputElement>("[data-graph-legend] input").forEach((cb) => cb.checked = true);
    updateGraph();
  });
  legendClearAll?.addEventListener("click", () => {
    document.querySelectorAll<HTMLInputElement>("[data-graph-legend] input").forEach((cb) => cb.checked = false);
    updateGraph();
  });
  personSelectAll?.addEventListener("click", () => {
    document.querySelectorAll<HTMLInputElement>(".person-toggle").forEach((cb) => cb.checked = true);
    updateGraph();
  });
  personClearAll?.addEventListener("click", () => {
    document.querySelectorAll<HTMLInputElement>(".person-toggle").forEach((cb) => cb.checked = false);
    updateGraph();
  });

  personSearch?.addEventListener("input", () => {
    const q = personSearch.value.trim();
    const normalizedQ = normalizeArabic(q);
    document.querySelectorAll<HTMLElement>(".graph-person-leg").forEach((leg) => {
      if (!normalizedQ) { leg.style.display = ""; return; }
      const match = normalizeArabic(leg.dataset.name || "").includes(normalizedQ);
      leg.style.display = match ? "" : "none";
    });
  });

  // Close details dropdown when clicking outside
  document.addEventListener("click", (e) => {
    const dropdown = document.querySelector(".pop-dropdown") as HTMLDetailsElement | null;
    if (dropdown && dropdown.open && !dropdown.contains(e.target as Node)) {
      dropdown.open = false;
    }
  });

  // initial filter
  updateGraph();

  const resize = () => g.width(el.clientWidth).height(el.clientHeight);
  window.addEventListener("resize", resize);
}

init();
document.addEventListener("astro:page-load", init);
