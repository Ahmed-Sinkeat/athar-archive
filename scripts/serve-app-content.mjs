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

// ponytail: whole-file responses, no Range support; add it if audio seeking needs it locally.
https.createServer({ key: fs.readFileSync(KEY), cert: fs.readFileSync(CERT) }, (req, res) => {
  const file = path.join(ROOT, decodeURIComponent(new URL(req.url, "https://localhost").pathname));
  if (!file.startsWith(ROOT + path.sep) || !fs.statSync(file, { throwIfNoEntry: false })?.isFile()) {
    res.writeHead(404).end();
    console.log(404, req.url);
    return;
  }
  res.writeHead(200, { "content-type": TYPES[path.extname(file)] ?? "application/octet-stream", "cache-control": "no-cache" });
  fs.createReadStream(file).pipe(res);
}).listen(PORT, "127.0.0.1", () => console.log(`serving ${ROOT} on https://localhost:${PORT}/app/v2/`));
