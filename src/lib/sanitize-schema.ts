// Shared rehype-sanitize schema. Imported by both the standalone pipeline
// (sanitize.ts) and Astro's markdown config (astro.config.ts) so authored
// and snippet-rendered content are neutralized identically.

import { defaultSchema, type Options as SanitizeSchema } from "rehype-sanitize";

const globalAttrs = [...(defaultSchema.attributes?.["*"] ?? []), "data*"];

export const sanitizeSchema: SanitizeSchema = {
  ...defaultSchema,
  clobberPrefix: "",
  attributes: {
    ...defaultSchema.attributes,
    "*": globalAttrs,
  },
  // script/style are absent from tagNames → stripped. Keep defaults otherwise.
};
