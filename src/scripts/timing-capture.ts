// Per-bayt timing capture for poem-timing/*.json — reachable from the reading
// settings panel (poems with audio but no timing yet show a "help time this
// poem" button, see reader.ts's isPoemPage/followTiming visibility check) or
// directly via ?tap in the URL. Replaces the two-tab workflow in
// tools/timing-tap.html: taps here read the real <audio> currentTime
// directly, so there's no separate stopwatch to start in sync with playback.
const REPORT_MAILTO = "tashih@arthurarchive.com"; // matches ahlalathar.config.ts reportErrorMailto

export function openTapPanel() {
  document.querySelector("[data-tap-panel]")?.remove();

  const audio = document.querySelector<HTMLAudioElement>("[data-audio-el]");
  const versesRoot = document.querySelector<HTMLElement>(".verses[data-matn]");
  const verseEls = Array.from(versesRoot?.querySelectorAll<HTMLElement>(".verse") ?? []);
  if (!audio || !verseEls.length) return;
  const poemSlug = versesRoot!.dataset.matn!;
  const poemTitle = document.querySelector("h1")?.textContent?.trim() || poemSlug;

  let cues: { v: number; t: number }[] = [];

  const panel = document.createElement("div");
  panel.dataset.tapPanel = "";
  // sit directly ABOVE the audio bar, not over it — otherwise the panel covers
  // play/scrub and you can't drive the very audio you're timing against. The
  // bar's own offset (mobile lifts it above the tab bar + safe-area) is folded
  // in by anchoring to its measured top edge; z-index 59 keeps it under the
  // bar's own z-index:60 as a safety net if they ever touch.
  const bar = document.querySelector<HTMLElement>(".audio-bar");
  const gapFromBottom = bar ? Math.round(window.innerHeight - bar.getBoundingClientRect().top) : 0;
  panel.style.cssText =
    `position:fixed;inset-inline:0;bottom:${gapFromBottom}px;z-index:59;background:#1b1b1b;color:#eee;` +
    "font:14px system-ui,sans-serif;padding:10px 12px;border-top:1px solid #444;border-bottom:1px solid #444;direction:rtl;";
  panel.innerHTML = `
    <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;margin-bottom:6px;">
      <span data-tap-status></span>
      <div style="display:flex;gap:6px;">
        <button type="button" data-tap-undo style="padding:6px 10px;">تراجع</button>
        <button type="button" data-tap-export style="padding:6px 10px;">تصدير JSON</button>
        <button type="button" data-tap-send hidden style="padding:6px 10px;background:#2a5c49;">إرسال</button>
        <button type="button" data-tap-close style="padding:6px 10px;" aria-label="إغلاق">✕</button>
      </div>
    </div>
    <button type="button" data-tap-next style="display:block;width:100%;font-size:22px;padding:16px;background:#2a5c49;color:#eee;border:1px solid #2a5c49;border-radius:10px;"></button>
    <textarea data-tap-out readonly hidden style="width:100%;height:120px;margin-top:6px;font-family:monospace;font-size:12px;background:#111;color:#9c9;border:1px solid #333;border-radius:6px;padding:6px;"></textarea>
  `;
  document.body.appendChild(panel);

  const statusEl = panel.querySelector<HTMLElement>("[data-tap-status]")!;
  const nextBtn = panel.querySelector<HTMLButtonElement>("[data-tap-next]")!;
  const outEl = panel.querySelector<HTMLTextAreaElement>("[data-tap-out]")!;
  const sendBtn = panel.querySelector<HTMLButtonElement>("[data-tap-send]")!;

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
    sendBtn.hidden = false;
    outEl.select();
    navigator.clipboard?.writeText(outEl.value).catch(() => {});
  });
  sendBtn.addEventListener("click", () => {
    const subject = `توقيت أبيات: ${poemTitle} (${poemSlug})`;
    const body = `مرفق توقيت الأبيات لقصيدة «${poemTitle}» (${poemSlug}) — الصقها في src/content/poem-timing/${poemSlug}.json:\n\n${outEl.value}`;
    location.href = `mailto:${REPORT_MAILTO}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
  });
  panel.querySelector("[data-tap-close]")!.addEventListener("click", () => panel.remove());

  render();
}

if (new URLSearchParams(location.search).has("tap")) openTapPanel();
document.addEventListener("astro:page-load", () => {
  document.querySelector("[data-tap-panel]")?.remove();
  if (new URLSearchParams(location.search).has("tap")) openTapPanel();
});
