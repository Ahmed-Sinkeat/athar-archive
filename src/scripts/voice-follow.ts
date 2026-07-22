// Voice follow-along (poem page prototype) — Chrome's built-in SpeechRecognition,
// no server round-trip. As the reader recites, each bayt lights up once its words
// show up in the running transcript. ponytail: word-overlap match, not phoneme-level
// tajweed scoring — good enough for plain poem Arabic, revisit if false-advances happen.
import { stripTashkeel } from "../lib/display";

// SpeechRecognition isn't in TS's DOM lib (non-standard, Chrome-only) — `any` at this boundary only.
const SR: any = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
const OVERLAP_THRESHOLD = 0.6;

function words(text: string): string[] {
  return stripTashkeel(text).replace(/[^ء-ي\s]/g, "").split(/\s+/).filter(Boolean);
}

// A soft nav away from the poem page (Astro ClientRouter) must not leave a mic
// listening on a page the reader has already left.
let stopActive: (() => void) | null = null;

function init() {
  stopActive?.();
  stopActive = null;

  const btn = document.querySelector<HTMLElement>("[data-voice-toggle]");
  const container = document.querySelector<HTMLElement>(".verses[data-matn]");
  const status = document.querySelector<HTMLElement>("[data-voice-status]");
  if (!btn || !container) return;

  const ERROR_LABEL: Record<string, string> = {
    "not-allowed": "الإذن بالميكروفون مرفوض — فعّله من إعدادات الموقع في المتصفح.",
    "permission-denied": "الإذن بالميكروفون مرفوض — فعّله من إعدادات الموقع في المتصفح.",
    "service-not-allowed": "تعذّر الوصول لخدمة التعرف على الصوت من المتصفح (شائع في نسخ Chromium غير الرسمية).",
    network: "تعذّر الاتصال بخدمة التعرف على الصوت — تحقق من الإنترنت.",
    "audio-capture": "لا يوجد ميكروفون متاح.",
    "no-speech": "لم يُسمع صوت — تأكد أن الميكروفون يعمل.",
  };

  const verseEls = Array.from(container.querySelectorAll<HTMLElement>(".verse"));
  const verseWords = verseEls.map((el) =>
    words(`${el.querySelector(".sadr")?.textContent ?? ""} ${el.querySelector(".ajz")?.textContent ?? ""}`),
  );

  let idx = 0;
  let heard: string[] = [];
  let recognition: any = null;
  let wantOn = false;

  function overlapRatio(target: string[]): number {
    if (!target.length) return 0;
    const recent = new Set(heard.slice(-40));
    return target.filter((w) => recent.has(w)).length / target.length;
  }

  function advance() {
    while (idx < verseEls.length && overlapRatio(verseWords[idx]) >= OVERLAP_THRESHOLD) {
      verseEls[idx].classList.add("voice-read");
      verseEls[idx].scrollIntoView({ behavior: "smooth", block: "center" });
      idx++;
      heard = []; // fresh window per bayt — stops stale words matching two verses ahead
    }
  }

  function reset() {
    idx = 0;
    heard = [];
    verseEls.forEach((el) => el.classList.remove("voice-read"));
  }

  function start() {
    if (!SR) {
      alert("هذه الميزة تجريبية وتعمل في متصفح Chrome فقط حاليًا.");
      return;
    }
    reset();
    recognition = new SR();
    recognition.lang = "ar-SA";
    recognition.continuous = true;
    recognition.interimResults = true;
    recognition.onresult = (e: SpeechRecognitionEvent) => {
      if (status) status.textContent = "يستمع…";
      let text = "";
      for (let i = e.resultIndex; i < e.results.length; i++) text += ` ${e.results[i][0].transcript}`;
      heard.push(...words(text));
      advance();
    };
    recognition.onerror = (e: any) => {
      console.error("voice-follow: SpeechRecognition error:", e.error);
      if (status) status.textContent = ERROR_LABEL[e.error] ?? `خطأ في التعرف على الصوت: ${e.error}`;
      // fatal errors (permission/service) won't fix themselves on retry — stop instead of looping onend->start->error
      if (e.error === "not-allowed" || e.error === "permission-denied" || e.error === "service-not-allowed") stop();
    };
    // Chrome auto-stops the session after a silence gap — restart transparently
    // while the toggle is still on, so the reader doesn't have to re-tap it.
    recognition.onend = () => { if (wantOn) recognition?.start(); };
    recognition.start();
    if (status) status.textContent = "بانتظار الإذن بالميكروفون…";
  }

  function stop() {
    wantOn = false;
    btn!.setAttribute("aria-pressed", "false");
    recognition?.stop();
    recognition = null;
  }

  btn.addEventListener("click", () => {
    wantOn = !wantOn;
    btn.setAttribute("aria-pressed", String(wantOn));
    if (wantOn) start();
    else { stop(); if (status) status.textContent = ""; }
  });

  stopActive = stop;
}

init();
document.addEventListener("astro:page-load", init);
