// Hidden per-bayt timing capture for poem-timing/*.json. Activate a poem page
// by opening it with ?tap in the URL — nothing renders otherwise, so regular
// visitors never see this. Replaces the two-tab workflow in tools/timing-tap.html:
// taps here read the real <audio> currentTime directly, so there's no separate
// stopwatch to start in sync with playback.
function init() {
  document.querySelector("[data-tap-panel]")?.remove();
  if (!new URLSearchParams(location.search).has("tap")) return;

  const audio = document.querySelector<HTMLAudioElement>("[data-audio-el]");
  const verseEls = Array.from(document.querySelectorAll<HTMLElement>(".verses[data-matn] .verse"));
  if (!audio || !verseEls.length) return;

  let cues: { v: number; t: number }[] = [];

  const panel = document.createElement("div");
  panel.dataset.tapPanel = "";
  panel.style.cssText =
    "position:fixed;inset-inline:0;bottom:0;z-index:9999;background:#1b1b1b;color:#eee;" +
    "font:14px system-ui,sans-serif;padding:10px 12px;border-top:1px solid #444;direction:rtl;";
  panel.innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:6px;">
      <span data-tap-status></span>
      <div style="display:flex;gap:6px;">
        <button type="button" data-tap-undo style="padding:6px 10px;">تراجع</button>
        <button type="button" data-tap-export style="padding:6px 10px;">تصدير JSON</button>
      </div>
    </div>
    <button type="button" data-tap-next style="display:block;width:100%;font-size:22px;padding:16px;background:#2a5c49;color:#eee;border:1px solid #2a5c49;border-radius:10px;"></button>
    <textarea data-tap-out readonly hidden style="width:100%;height:120px;margin-top:6px;font-family:monospace;font-size:12px;background:#111;color:#9c9;border:1px solid #333;border-radius:6px;padding:6px;"></textarea>
  `;
  document.body.appendChild(panel);

  const statusEl = panel.querySelector<HTMLElement>("[data-tap-status]")!;
  const nextBtn = panel.querySelector<HTMLButtonElement>("[data-tap-next]")!;
  const outEl = panel.querySelector<HTMLTextAreaElement>("[data-tap-out]")!;

  function render() {
    const idx = cues.length;
    const finished = idx >= verseEls.length;
    nextBtn.textContent = finished ? "✅ انتهت كل الأبيات" : `بيت ${idx + 1} — اضغط هنا`;
    nextBtn.disabled = finished;
    statusEl.textContent = `${idx} / ${verseEls.length}`;
  }

  nextBtn.addEventListener("click", () => {
    if (cues.length >= verseEls.length) return;
    cues.push({ v: cues.length + 1, t: Math.round(audio.currentTime * 10) / 10 });
    render();
  });
  panel.querySelector("[data-tap-undo]")!.addEventListener("click", () => {
    cues.pop();
    render();
  });
  panel.querySelector("[data-tap-export]")!.addEventListener("click", () => {
    outEl.value = JSON.stringify(cues, null, 2);
    outEl.hidden = false;
    outEl.select();
    navigator.clipboard?.writeText(outEl.value).catch(() => {});
  });

  render();
}

init();
document.addEventListener("astro:page-load", init);
