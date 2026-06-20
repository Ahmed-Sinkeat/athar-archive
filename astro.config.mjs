import { defineConfig } from "astro/config";

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
});
