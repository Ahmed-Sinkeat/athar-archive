import { markdownToSafeHtml } from "./src/lib/sanitize.js";

const original = 'حين يقول: بدلناهم جلودًا غيرها<sup data-fn="1" data-sep-page="60" data-note="السطر الأول\n\nالسطر الثاني">1</sup>.';
const html = markdownToSafeHtml(original);
console.log("RENDERED WITH RAW NEWLINES:", html);
