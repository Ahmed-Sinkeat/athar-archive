// Publish only the books you edited to the phone: app:gen --only, then (with --go) R2 upload + D1 search rows.
//
//   pnpm publish:books                 # books changed since tag app-published; DRY RUN (generates locally, uploads nothing)
//   pnpm publish:books slug1 slug2     # explicit books (bare slug = book/, or coll/id)
//   pnpm publish:books --go            # upload to R2, move the app-published tag
//   pnpm publish:books --go --search   # also refresh those books' rows in D1 (incremental by doc_hash)
//   pnpm publish:books --selftest
//
// --only keeps the previous catalog from dist/app-content, so run it on the machine that last published.
// A deleted book needs a full `pnpm app:gen` (tombstone); this refuses instead of silently leaving it live.
import assert from "node:assert";
import { execFileSync, spawnSync } from "node:child_process";
import fs from "node:fs";

const COLLECTIONS = new Map([["article", "article"], ["book", "book"], ["book-lg", "book"], ["question", "question"], ["poem", "poem"]]);
const TAG = "app-published";

/** git-diff paths -> app targets; `gone` are removed files that need a full gen. */
export function targetsFromPaths(paths, exists = fs.existsSync) {
  const targets = new Set();
  const gone = [];
  for (const p of paths) {
    const m = /^src\/content\/([^/]+)\/([^/]+)\.md$/.exec(p);
    if (!m || !COLLECTIONS.has(m[1])) continue;
    if (!exists(p)) { gone.push(p); continue; }
    targets.add(`${COLLECTIONS.get(m[1])}/${m[2]}`);
  }
  return { targets: [...targets].sort(), gone };
}

function selftest() {
  const keep = new Set(["src/content/book/a.md", "src/content/book-lg/b.md", "src/content/poem/p.md"]);
  const r = targetsFromPaths(
    ["src/content/book/a.md", "src/content/book-lg/b.md", "src/content/poem/p.md", "src/content/person/x.md", "src/content/book/del.md", "docs/x.md"],
    (p) => keep.has(p),
  );
  assert.deepEqual(r.targets, ["book/a", "book/b", "poem/p"]);
  assert.deepEqual(r.gone, ["src/content/book/del.md"]);
  console.log("publish-books selftest ok");
}

function run(cmd, args, env = {}) {
  console.log(`$ ${cmd} ${args.join(" ")}`);
  const r = spawnSync(cmd, args, { stdio: "inherit", env: { ...process.env, ...env } });
  if (r.status !== 0) { console.error(`failed: ${cmd} ${args.join(" ")}`); process.exit(r.status ?? 1); }
}

/** KEY=VALUE lines into process.env (signing + R2 keys); never printed, existing env wins. */
function loadEnv(file) {
  if (!fs.existsSync(file)) return;
  for (const line of fs.readFileSync(file, "utf8").split("\n")) {
    const m = /^\s*(?:export\s+)?([A-Z0-9_]+)=(.*)$/.exec(line);
    if (m && process.env[m[1]] === undefined) process.env[m[1]] = m[2].replace(/^(['"])(.*)\1$/, "$2");
  }
}

function main() {
  const argv = process.argv.slice(2);
  if (argv.includes("--selftest")) return selftest();
  const go = argv.includes("--go");
  const search = argv.includes("--search");
  loadEnv(`${process.env.HOME}/.config/athar/publish.env`);
  const names = argv.filter((a) => !a.startsWith("--"));

  let targets;
  if (names.length) targets = names.map((n) => (n.includes("/") ? n : `book/${n}`));
  else {
    const hasTag = spawnSync("git", ["rev-parse", "-q", "--verify", `refs/tags/${TAG}`]).status === 0;
    if (!hasTag) { console.error(`no ${TAG} tag yet: name the books, or tag the last published commit: git tag ${TAG} <sha>`); process.exit(1); }
    const paths = execFileSync("git", ["diff", "--name-only", `${TAG}..HEAD`, "--", "src/content"], { encoding: "utf8" }).split("\n").filter(Boolean);
    // uncommitted edits count too
    paths.push(...execFileSync("git", ["status", "--porcelain", "--", "src/content"], { encoding: "utf8" }).split("\n").filter(Boolean).map((l) => l.slice(3)));
    const { targets: t, gone } = targetsFromPaths(paths);
    if (gone.length) { console.error(`removed files need a full "pnpm app:gen" (tombstones):\n  ${gone.join("\n  ")}`); process.exit(1); }
    targets = t;
  }
  if (!targets.length) { console.log("nothing changed since the last publish"); return; }
  console.log(`${targets.length} target(s): ${targets.slice(0, 10).join(", ")}${targets.length > 10 ? " …" : ""}`);

  run("node_modules/.bin/tsx", ["scripts/gen-app-content.ts", `--only=${targets.join(",")}`]);
  if (!go) { console.log("dry run: generated under dist/app-content, nothing uploaded. Re-run with --go to publish."); return; }
  run("node", ["scripts/upload-app-content.mjs"]);
  if (search) {
    run("pnpm", ["search:hashes"]);
    run("pnpm", ["search:gen"]);
    for (const f of fs.readdirSync("dist/search-index").filter((x) => x.endsWith(".sql")).sort())
      run("node_modules/.bin/wrangler", ["d1", "execute", "athar-search", "--remote", "-y", "--file", `dist/search-index/${f}`]);
  }
  run("git", ["tag", "-f", TAG]);
  console.log("published");
}

main();
