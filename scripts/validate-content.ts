// Build-time cross-entity validation. Run via: pnpm validate:content
// Called automatically before astro build by the "build" script.

import { loadContentFromDisk } from "../src/lib/load.js";
import { validate, formatErrors } from "../src/lib/validate.js";

function main() {
  const entries = loadContentFromDisk();
  const errors = validate(entries);

  if (errors.length === 0) {
    console.log(`✓ content validation passed (${entries.length} entries)`);
    process.exit(0);
  } else {
    console.error(`✗ content validation failed — ${errors.length} error(s):\n`);
    console.error(formatErrors(errors));
    process.exit(1);
  }
}

main();
