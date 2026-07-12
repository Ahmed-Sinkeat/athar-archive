// PWA install prompt banner. Android/desktop Chrome fires `beforeinstallprompt`
// only under its own engagement heuristic, and doing nothing with it means
// the user never sees an install affordance until Chrome feels like showing
// its own mini-infobar. iOS Safari never fires that event at all — there is
// no programmatic install, only manual Share → "Add to Home Screen", which
// most people don't know exists. This banner covers both cases explicitly.
//
// Dismissing the banner is meant to be permanent (no repeat nagging), so the
// drawer button (data-action="install:reopen") is the way back if someone
// dismisses by mistake — it bypasses the dismissed flag and re-shows whatever
// state is currently known (iOS instructions, or the captured install event).
const LS_DISMISSED = "aa-install-dismissed";

const banner = document.querySelector<HTMLElement>("[data-install-banner]");
const text = banner?.querySelector<HTMLElement>("[data-install-text]");
const acceptBtn = banner?.querySelector<HTMLButtonElement>('[data-action="install:accept"]');
const dismissBtn = banner?.querySelector<HTMLButtonElement>('[data-action="install:dismiss"]');
const reopenBtn = document.querySelector<HTMLButtonElement>('[data-action="install:reopen"]');

if (banner && text && acceptBtn && dismissBtn) {
  const isStandalone =
    window.matchMedia("(display-mode: standalone)").matches || (navigator as any).standalone === true;
  const isIOS = /iphone|ipad|ipod/i.test(navigator.userAgent);

  let deferredPrompt: any = null;
  let message = "";
  let withAccept = false;

  function render(forceIgnoreDismissed: boolean) {
    if (isStandalone || (!forceIgnoreDismissed && localStorage.getItem(LS_DISMISSED))) return;
    text!.textContent = message;
    acceptBtn!.hidden = !withAccept;
    banner!.hidden = false;
  }

  function setEligible(msg: string, accept: boolean) {
    message = msg;
    withAccept = accept;
    if (reopenBtn) reopenBtn.hidden = isStandalone;
    render(false);
  }

  dismissBtn.addEventListener("click", () => {
    localStorage.setItem(LS_DISMISSED, "1");
    banner!.hidden = true;
  });
  acceptBtn.addEventListener("click", async () => {
    if (!deferredPrompt) return;
    deferredPrompt.prompt();
    await deferredPrompt.userChoice;
    deferredPrompt = null;
    banner!.hidden = true;
  });
  reopenBtn?.addEventListener("click", () => render(true));

  window.addEventListener("beforeinstallprompt", (e) => {
    e.preventDefault();
    deferredPrompt = e;
    setEligible("ثبّت التطبيق على جهازك للوصول السريع ومحتوى بلا اتصال.", true);
  });
  window.addEventListener("appinstalled", () => {
    banner!.hidden = true;
    if (reopenBtn) reopenBtn.hidden = true;
    localStorage.setItem(LS_DISMISSED, "1");
  });

  if (isIOS && !isStandalone) {
    setEligible("لإضافة التطبيق: اضغط زر المشاركة ثم «إضافة إلى الشاشة الرئيسية».", false);
  }
}
