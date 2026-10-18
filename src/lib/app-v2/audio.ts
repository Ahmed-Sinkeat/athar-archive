import { existsSync } from "node:fs";
import { readdir, readFile } from "node:fs/promises";
import path from "node:path";
import { setTimeout } from "node:timers/promises";

// ATHAR_AUDIO_CACHE: a previous generation's audio/ dir (<slug>/<sha>.opus); used before the network.
async function cachedAudio(url: string): Promise<Buffer | undefined> {
  const root = process.env.ATHAR_AUDIO_CACHE;
  if (!root) return undefined;
  const name = path.basename(new URL(url).pathname, ".opus");
  // ponytail: some cached dirs use "--NN" where the URL has "-NN"; try both
  const dir = [name, name.replace(/-(\d+)$/, "--$1")].map((d) => path.join(root, d)).find((d) => existsSync(d)) ?? path.join(root, name);
  const files = await readdir(dir).catch(() => []);
  const opus = files.filter((file) => file.endsWith(".opus"));
  return opus.length === 1 ? readFile(path.join(dir, opus[0]!)) : undefined;
}

export async function fetchAppAudio(url: string): Promise<Buffer> {
  const cached = await cachedAudio(url);
  if (cached) return cached;
  for (let attempt = 1; ; attempt++) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(15 * 60 * 1_000) });
      if (!response.ok) {
        await response.body?.cancel();
        throw new Error(`HTTP ${response.status}`);
      }
      return Buffer.from(await response.arrayBuffer());
    } catch (cause) {
      if (attempt === 5) throw new Error(`audio fetch failed after ${attempt} attempts: ${url}`, { cause });
      console.warn(`audio transfer interrupted; retry ${attempt}/4: ${url}`);
      await setTimeout(attempt * 1_000);
    }
  }
}
