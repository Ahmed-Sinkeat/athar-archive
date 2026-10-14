import { setTimeout } from "node:timers/promises";

export async function fetchAppAudio(url: string): Promise<Buffer> {
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
