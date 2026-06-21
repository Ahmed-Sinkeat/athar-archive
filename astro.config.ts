import { defineConfig } from "astro/config";

export default defineConfig({
  output: "static",
  site: "https://ahlalathar.com",
  trailingSlash: "never",
  build: {
    format: "directory",
    // keep all CSS external (no inlined <style>) so the CSP can use style-src 'self'
    inlineStylesheets: "never",
  },
  i18n: {
    defaultLocale: "ar",
    locales: ["ar"],
  },
  // Content bodies are rendered through src/lib/sanitize.ts (Prose.astro),
  // the canonical sanitized path — see src/lib/sanitize-schema.ts.
});
