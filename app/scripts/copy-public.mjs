// Copy the page and the pinned consent broker into dist/. The broker is the
// canonical kotoba-lang/kotoba source at vendor/PINS.json's revision; a
// digest mismatch stops the build (no product-local consent code).
import { cpSync, mkdirSync, readFileSync } from "node:fs";
import { createHash } from "node:crypto";
const pins = JSON.parse(readFileSync("vendor/PINS.json", "utf8"));
mkdirSync("dist", { recursive: true });
for (const [name, digest] of Object.entries(pins.files)) {
  const bytes = readFileSync(`vendor/${name}`);
  const actual = createHash("sha256").update(bytes).digest("hex");
  if (actual !== digest) throw new Error(`canonical broker digest mismatch: ${name}`);
  cpSync(`vendor/${name}`, `dist/${name}`);
}
cpSync("public", "dist", { recursive: true });
console.log(`broker ${pins.revision.slice(0, 12)} verified`);
