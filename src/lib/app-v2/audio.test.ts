import { afterEach, expect, it, vi } from "vitest";
import { fetchAppAudio } from "./audio.js";

afterEach(() => vi.unstubAllGlobals());

it("restarts an interrupted response body and returns only the complete recording", async () => {
  const fetch = vi.fn()
    .mockResolvedValueOnce({ ok: true, arrayBuffer: async () => { throw new Error("ECONNRESET"); } })
    .mockResolvedValueOnce(new Response("complete recording"));
  vi.stubGlobal("fetch", fetch);
  expect((await fetchAppAudio("https://example.com/recording.opus")).toString()).toBe("complete recording");
  expect(fetch).toHaveBeenCalledTimes(2);
});
