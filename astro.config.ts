import { defineConfig } from "astro/config";
import rehypeSanitize from "rehype-sanitize";
import { sanitizeSchema } from "./src/lib/sanitize-schema.js";

export default defineConfig({
  output: "static",
  site: "https://ahlalathar.net",
  trailingSlash: "never",
  build: {
    format: "directory",
  },
  i18n: {
    defaultLocale: "ar",
    locales: ["ar"],
  },
  markdown: {
    // Neutralize raw HTML/scripts in any <Content /> rendered from markdown.
    rehypePlugins: [[rehypeSanitize, sanitizeSchema]],
  },
});
