import { readFile } from "node:fs/promises";
import path from "node:path";
import { fetchAppAudio } from "../src/lib/app-v2/audio.js";
import { generateAppContent, publishedContentTargets, type ContentTarget } from "../src/lib/app-v2/generator.js";

const SLICE: ContentTarget[] = [
  { coll: "article", id: "byan-kdhb-athr-idha-safr-al-fqr-ila-mkan-ma-qal-al-kfr-khdhny-mak-ala-aby-dhr--v2" },
  { coll: "book", id: "nawaqid-al-islam" },
  { coll: "question", id: "swteat-866" },
  { coll: "poem", id: "qasidat-madh-al-sunnah-wa-ittiba-al-salaf" },
];

function option(name: string): string | undefined {
  const prefix = `${name}=`;
  return process.argv.find((argument) => argument.startsWith(prefix))?.slice(prefix.length);
}

const repositoryRoot = process.cwd();
const slice = process.argv.includes("--slice");
const outputRoot = path.resolve(option("--out") ?? (slice ? "dist/app-content-slice" : "dist/app-content"));
const sidecarRoot = path.resolve(option("--sidecars") ?? "content-ids");
const signingKeyFile = process.env.ATHAR_CONTENT_SIGNING_KEY_FILE;
const signingKeyId = process.env.ATHAR_CONTENT_SIGNING_KEY_ID;
if ((signingKeyFile == null) !== (signingKeyId == null)) {
  throw new Error("ATHAR_CONTENT_SIGNING_KEY_FILE and ATHAR_CONTENT_SIGNING_KEY_ID must be set together");
}

const signing = signingKeyFile && signingKeyId
  ? { keyId: signingKeyId, privateKey: await readFile(path.resolve(signingKeyFile), "utf8") }
  : undefined;

// --only=coll/id,coll/id: regenerate just those and keep the rest of the previous catalog
const only = option("--only")?.split(",").map((pair) => {
  const [coll, id] = pair.split("/");
  return { coll, id } as ContentTarget;
});
const result = await generateAppContent({
  repositoryRoot,
  outputRoot,
  sidecarRoot,
  targets: only ?? (slice ? SLICE : await publishedContentTargets(repositoryRoot)),
  keepOthers: only != null,
  signing,
  loadAudio: fetchAppAudio,
});

for (const entity of result.entities) {
  console.log(
    `${entity.coll}/${entity.id}: v${entity.version}, ${entity.blocks} blocks, ${entity.frames} frame(s), `
    + `${entity.packageBytes} bytes, IDs ${entity.reusedIds} reused/${entity.createdIds} new`,
  );
}
console.log(`catalog: ${result.catalog.entries.length} entries · generation ${result.rootPayload.generationId}`);
console.log(`root: ${result.envelope ? "signed index.json" : "unsigned index.payload.json (set ATHAR_CONTENT_SIGNING_KEY_FILE + ATHAR_CONTENT_SIGNING_KEY_ID to sign)"}`);
console.log(`output: ${result.outputDirectory}`);
