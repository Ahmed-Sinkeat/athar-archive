// Force-directed knowledge graph for /graph. Reads the embedded #gdata JSON and
// renders an interactive canvas (drag / zoom / click → open). Re-inits on each
// view-transition navigation; guarded so it builds once per fresh page.
import ForceGraph from "force-graph";

// type → node color (theme-agnostic, readable on light/sepia/dark)
const COLORS: Record<string, string> = {
  book: "#9c3b32", poem: "#9c3b32", matn: "#9c3b32", benefit: "#c06a2c",
  person: "#2f6f6a", series: "#b07b00", lesson: "#b07b00",
  subject: "#5a4632", topic: "#7a8a52", article: "#2f6f6a", question: "#777",
};

function init() {
  const el = document.getElementById("graph") as HTMLElement | null;
  const dataEl = document.getElementById("gdata");
  if (!el || !dataEl || el.dataset.ready) return;
  el.dataset.ready = "1";

  const data = JSON.parse(dataEl.textContent || '{"nodes":[],"links":[]}') as {
    nodes: { id: string; label: string; type: string; href: string; val?: number }[];
    links: { source: string; target: string }[];
  };
  // node size by degree (connections)
  const deg: Record<string, number> = {};
  for (const l of data.links) { deg[l.source] = (deg[l.source] || 0) + 1; deg[l.target] = (deg[l.target] || 0) + 1; }
  for (const n of data.nodes) n.val = 1 + (deg[n.id] || 0);

  const g = new ForceGraph(el)
    .graphData(data)
    .nodeId("id")
    .nodeLabel("label")
    .nodeVal("val")
    .nodeColor((n: any) => COLORS[n.type] || "#888")
    .nodeRelSize(4)
    .linkColor(() => "rgba(120,110,95,0.25)")
    .linkWidth(1)
    .onNodeClick((n: any) => { if (n.href) location.href = n.href; })
    .width(el.clientWidth)
    .height(el.clientHeight);

  const resize = () => g.width(el.clientWidth).height(el.clientHeight);
  window.addEventListener("resize", resize);
}

init();
document.addEventListener("astro:page-load", init);
