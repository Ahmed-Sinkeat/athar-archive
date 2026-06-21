// Canonical content-id (slug) format. Single source of truth shared by the Zod
// schema (content.config.ts), the cross-entity validator (validate.ts) and the
// composer (content-forms.ts) so they can never drift on what a valid id is.
// Single hyphens for word segments; double hyphens separate parent from child
// e.g., "sharh-al-wasitiyyah--lesson-1", "alfiyyah-ibn-malik--v1--sharh".
// NOTE: scripts/new-content.mjs keeps its own copy (plain .mjs, no TS import) —
// keep it in sync with this regex.
export const SLUG_RE = /^[a-z0-9]+(--?[a-z0-9]+)*$/;
