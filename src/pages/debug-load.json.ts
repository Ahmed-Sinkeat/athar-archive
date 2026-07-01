export async function GET() {
  let status: any = null;
  let textPreview: any = null;
  let fetchError: any = null;
  
  try {
    const res = await fetch("http://localhost:4321/content/book/al-arbaeen-al-nawawiyyah.md");
    status = res.status;
    textPreview = res.ok ? (await res.text()).slice(0, 100) : null;
  } catch (e: any) {
    fetchError = e.message || String(e);
  }
  
  return new Response(
    JSON.stringify({
      status,
      fetchError,
      textPreview
    }),
    { headers: { "content-type": "application/json" } }
  );
}
export const prerender = false;
