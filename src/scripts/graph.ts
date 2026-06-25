// Force-directed knowledge graph for /graph. Reads the embedded #gdata JSON and
// renders an interactive canvas: drag a node to move/pin it, hover to highlight its
// neighbourhood, click to open it, and toggle types via the legend. Re-inits on
// each view-transition navigation; guarded so it builds once per fresh page.
import ForceGraph from "force-graph";

const COLORS: Record<string, string> = {
  book: "#9c3b32", poem: "#9c3b32", benefit: "#c06a2c", person: "#2f6f6a",
  series: "#b07b00", subject: "#5a4632", topic: "#7a8a52", article: "#3f7d6e", question: "#8a8275",
};
const FADE = "rgba(120,110,95,0.18)";

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
  function setHover(node: GNode | null) {
    hoverId = node ? node.id : null;
    near.clear(); hot.clear();
    if (!node) return;
    near.add(node.id);
    for (const l of g.graphData().links as GLink[]) {
      if (lid(l.source) === node.id || lid(l.target) === node.id) { hot.add(l); near.add(lid(l.source)); near.add(lid(l.target)); }
    }
  }

  const g = new ForceGraph(el)
    .graphData({ nodes: full.nodes, links: full.links })
    .nodeId("id")
    .nodeVal("val")
    .nodeRelSize(5)
    .enableNodeDrag(true)
    .nodeColor((n: any) => (hoverId && !near.has(n.id) ? FADE : COLORS[n.type] || "#888"))
    .nodeCanvasObjectMode(() => "after")
    .nodeCanvasObject((n: any, ctx: CanvasRenderingContext2D, scale: number) => {
      // declutter: when zoomed out, label only hubs + the hovered neighbourhood
      if (scale < 0.6 && (deg[n.id] || 0) < 3 && !near.has(n.id)) return;
      const r = Math.sqrt(n.val) * 5;
      ctx.font = `${Math.min(5, 12 / scale)}px "IBM Plex Sans Arabic", system-ui, sans-serif`;
      ctx.textAlign = "center"; ctx.textBaseline = "top";
      ctx.fillStyle = hoverId && !near.has(n.id) ? FADE : ink;
      ctx.fillText(n.label, n.x, n.y + r + 1.5);
    })
    .linkColor((l: any) => (hot.has(l) ? "rgba(156,59,50,0.6)" : hoverId ? "rgba(120,110,95,0.06)" : "rgba(120,110,95,0.22)"))
    .linkWidth((l: any) => (hot.has(l) ? 1.8 : 1))
    .linkCurvature(0.08)
    .onNodeHover((n: any) => { setHover(n); el.style.cursor = n ? "pointer" : ""; })
    .onNodeDragEnd((n: any) => { n.fx = n.x; n.fy = n.y; }) // pin where dropped
    .onNodeClick((n: any) => { if (n.href) location.href = n.href; })
    .width(el.clientWidth)
    .height(el.clientHeight);

  // airier, calmer layout
  (g.d3Force("charge") as any)?.strength(-170);
  (g.d3Force("link") as any)?.distance(48);
  g.d3VelocityDecay(0.32);

  // type filter from the legend checkboxes and person checklist
  const legend = document.querySelector("[data-graph-legend]");
  const personSearch = document.getElementById("person-search") as HTMLInputElement;
  const personList = document.querySelector(".graph-person-list");

  function updateGraph() {
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
  }

  legend?.addEventListener("change", updateGraph);
  personList?.addEventListener("change", updateGraph);

  personSearch?.addEventListener("input", () => {
    const q = personSearch.value.trim();
    document.querySelectorAll<HTMLElement>(".graph-person-leg").forEach((leg) => {
      if (!q) { leg.style.display = ""; return; }
      const match = (leg.dataset.name || "").includes(q);
      leg.style.display = match ? "" : "none";
    });
  });

  // initial filter
  updateGraph();

  const resize = () => g.width(el.clientWidth).height(el.clientHeight);
  window.addEventListener("resize", resize);
}

init();
document.addEventListener("astro:page-load", init);
