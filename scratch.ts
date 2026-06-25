import { markdownToSafeHtml } from "./src/lib/sanitize.js";
console.log(markdownToSafeHtml("Here is a note[^1].\n\n[^1]: The note itself."));
