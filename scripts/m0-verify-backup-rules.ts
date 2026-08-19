import { readFile } from "node:fs/promises";

const root = new URL("../android/app/src/main/", import.meta.url);

const api26 = await readFile(new URL("res/xml/backup_rules.xml", root), "utf8");
const api28 = await readFile(new URL("res/xml-v28/backup_rules.xml", root), "utf8");
const api31 = await readFile(new URL("res/xml/data_extraction_rules.xml", root), "utf8");

function requireRule(condition: boolean, message: string): void {
  if (!condition) throw new Error(`R6 static assertion failed: ${message}`);
}

function section(xml: string, tag: string): string {
  const match = xml.match(new RegExp(`<${tag}>[\\s\\S]*?</${tag}>`));
  if (!match) throw new Error(`R6 static assertion failed: missing <${tag}>`);
  return match[0];
}

// API 26–27: D2D-only flags do not exist. Neither database may be included.
requireRule(!/<include\b[^>]*domain="database"/m.test(api26), "API 26–27 includes a database");

// API 28–30: only the user DB moves device-to-device.
requireRule(
  /<include\b[\s\S]*?domain="database"[\s\S]*?path="athar_user\.db"[\s\S]*?requireFlags="deviceToDeviceTransfer"[\s\S]*?\/>/m.test(api28),
  "API 28–30 user DB is not D2D-only",
);
requireRule(
  !/<include\b[^>]*path="athar_content\.db"/m.test(api28),
  "API 28–30 includes rebuildable content DB",
);

// API 31+: cloud and transfer channels are independent.
const cloud = section(api31, "cloud-backup");
const transfer = section(api31, "device-transfer");
requireRule(!/<include\b[^>]*path="athar_user\.db"/m.test(cloud), "API 31+ cloud includes user DB");
requireRule(/<exclude\b[^>]*path="athar_user\.db"/m.test(cloud), "API 31+ cloud lacks user DB exclusion");
requireRule(/<include\b[^>]*path="athar_user\.db"/m.test(transfer), "API 31+ transfer lacks user DB");
for (const xml of [cloud, transfer]) {
  requireRule(/<exclude\b[^>]*path="athar_content\.db"/m.test(xml), "API 31+ lacks content DB exclusion");
  requireRule(/<exclude\b[^>]*path="books\/"/m.test(xml), "API 31+ lacks package exclusion");
}

console.log("R6 static assertions: PASS");
console.log("API 26–27: no database includes");
console.log("API 28–30: athar_user.db is D2D-only; content DB omitted");
console.log("API 31+: user DB cloud-excluded and transfer-included; rebuildable data excluded");
