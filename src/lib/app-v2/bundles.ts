// Small packages of one kind, packed into a few zip files so «تنزيل الكل» is a handful of
// requests instead of two per item. Nothing here is signed: the app checks every package in a
// bundle against its own signed catalog entry, and fetches anything missing or stale one by one.
//
// The app derives the same bundle names from its catalog (ContentBundles.kt), so the rule below
// must stay identical on both sides: entries of the kind with pkg.size <= SMALL_PACKAGE_BYTES,
// sorted by id, cut greedily before a part would pass PART_BYTES (package + index bytes),
// named sha256 of the parts' package hashes joined by "\n".
import crypto from "node:crypto";
import { mkdir, readFile, readdir, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import zlib from "node:zlib";
import type { CatalogEntry } from "./contract.js";

export const BUNDLED_KINDS = ["article", "question", "poem", "book"] as const;
export const SMALL_PACKAGE_BYTES = 256 * 1024;
export const PART_BYTES = 8 * 1024 * 1024;

export function bundleParts(entries: readonly CatalogEntry[], coll: string): { name: string; items: CatalogEntry[] }[] {
  const items = entries
    .filter((entry) => entry.coll === coll && entry.pkg.size <= SMALL_PACKAGE_BYTES)
    .sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0));
  const parts: CatalogEntry[][] = [];
  let bytes = 0;
  for (const item of items) {
    const size = item.pkg.size + item.pkg.idxSize;
    if (parts.length === 0 || bytes + size > PART_BYTES) {
      parts.push([]);
      bytes = 0;
    }
    parts[parts.length - 1].push(item);
    bytes += size;
  }
  return parts.map((part) => ({
    name: crypto.createHash("sha256").update(part.map((item) => item.pkg.hash).join("\n")).digest("hex"),
    items: part,
  }));
}

// Stored (uncompressed: the frames are gzip already) zip, fixed timestamps, so a bundle's bytes
// depend only on its packages and its name can be cached as immutable.
function zip(files: { name: string; data: Buffer }[]): Buffer {
  const locals: Buffer[] = [];
  const centrals: Buffer[] = [];
  let offset = 0;
  for (const file of files) {
    const name = Buffer.from(file.name, "utf8");
    const crc = zlib.crc32(file.data);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(0x0800, 6); // UTF-8 names
    local.writeUInt16LE(0, 8); // stored
    local.writeUInt32LE(0x00210000, 10); // 1980-01-01 00:00
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(file.data.length, 18);
    local.writeUInt32LE(file.data.length, 22);
    local.writeUInt16LE(name.length, 26);
    local.writeUInt16LE(0, 28);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(0x0800, 8);
    central.writeUInt16LE(0, 10);
    central.writeUInt32LE(0x00210000, 12);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(file.data.length, 20);
    central.writeUInt32LE(file.data.length, 24);
    central.writeUInt16LE(name.length, 28);
    central.writeUInt32LE(offset, 42);
    locals.push(local, name, file.data);
    centrals.push(central, name);
    offset += local.length + name.length + file.data.length;
  }
  const directory = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(files.length, 8);
  end.writeUInt16LE(files.length, 10);
  end.writeUInt32LE(directory.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, directory, end]);
}

/** Writes `bundles/<kind>/<name>.zip` under the app root and removes bundles no catalog names any more. */
export async function writeBundles(appRoot: string, entries: readonly CatalogEntry[]): Promise<number> {
  let written = 0;
  for (const coll of BUNDLED_KINDS) {
    const directory = path.join(appRoot, "bundles", coll);
    await mkdir(directory, { recursive: true });
    const wanted = new Set<string>();
    for (const part of bundleParts(entries, coll)) {
      const file = `${part.name}.zip`;
      wanted.add(file);
      const files: { name: string; data: Buffer }[] = [];
      for (const item of part.items) {
        files.push({ name: `${item.id}.athar`, data: await readFile(path.join(appRoot, item.pkg.path)) });
        files.push({ name: `${item.id}.idx`, data: await readFile(path.join(appRoot, item.pkg.idxPath)) });
      }
      await writeFile(path.join(directory, file), zip(files));
      written++;
    }
    for (const stale of await readdir(directory)) {
      if (!wanted.has(stale)) await rm(path.join(directory, stale));
    }
  }
  return written;
}
