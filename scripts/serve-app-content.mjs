// Serves dist/app-content over HTTPS on localhost, so the phone's benchmark build reads
// unpublished content through `adb reverse tcp:8443 tcp:8443` instead of R2.
// The key stays outside the repo; its certificate is android/app/src/benchmark/res/raw/local_content_cert.pem.
import fs from "node:fs";
import https from "node:https";
import os from "node:os";
import path from "node:path";

const ROOT = path.resolve(process.env.ATHAR_LOCAL_ROOT ?? "dist/app-content");
const PORT = Number(process.env.PORT ?? 8443);
const KEY = process.env.ATHAR_LOCAL_KEY ?? path.join(os.homedir(), ".config/athar/local-content.key");
const CERT = "android/app/src/benchmark/res/raw/local_content_cert.pem";
const TYPES = { ".json": "application/json; charset=utf-8", ".idx": "application/json; charset=utf-8",
  ".athar": "application/vnd.athar+gzip", ".opus": "audio/ogg; codecs=opus", ".mp3": "audio/mpeg" };

// The app downloads content in byte ranges and rejects a 200 answer to a Range request, as R2 would never send one.
https.createServer({ key: fs.readFileSync(KEY), cert: fs.readFileSync(CERT) }, (req, res) => {
  const file = path.join(ROOT, decodeURIComponent(new URL(req.url, "https://localhost").pathname));
  const stat = file.startsWith(ROOT + path.sep) && fs.statSync(file, { throwIfNoEntry: false });
  if (!stat?.isFile()) {
    res.writeHead(404).end();
    console.log(404, req.url);
    return;
  }
  const headers = { "content-type": TYPES[path.extname(file)] ?? "application/octet-stream", "cache-control": "no-cache", "accept-ranges": "bytes" };
  const range = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range ?? "");
  if (!range) {
    res.writeHead(200, { ...headers, "content-length": stat.size });
    fs.createReadStream(file).pipe(res);
    return;
  }
  // bytes=a-b, bytes=a- or bytes=-n (the last n bytes)
  const start = range[1] ? Number(range[1]) : Math.max(0, stat.size - Number(range[2]));
  const end = range[1] && range[2] ? Math.min(Number(range[2]), stat.size - 1) : stat.size - 1;
  if (start > end || start >= stat.size) {
    res.writeHead(416, { "content-range": `bytes */${stat.size}` }).end();
    console.log(416, req.headers.range, req.url);
    return;
  }
  res.writeHead(206, { ...headers, "content-range": `bytes ${start}-${end}/${stat.size}`, "content-length": end - start + 1 });
  fs.createReadStream(file, { start, end }).pipe(res);
}).listen(PORT, "127.0.0.1", () => console.log(`serving ${ROOT} on https://localhost:${PORT}/app/v2/`));
