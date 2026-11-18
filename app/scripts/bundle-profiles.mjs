// Bundle the repository's itonami profiles for the app: the six definition
// files the Otehon core reads, nothing else. `.env`, `secrets.command`,
// scripts, state and DBs are never shipped.
import { readdirSync, readFileSync, existsSync, mkdirSync, writeFileSync, statSync } from "node:fs";
import { join } from "node:path";

const FILES = ["SOUL.md", "config.yaml", "profile.yaml", "cron/jobs.json", "itonami.edn", "yakuwari.edn"];
const SECRET = /(api[_-]?key|secret|token|password)\s*[:=]\s*["']?[A-Za-z0-9_\-]{16,}/i;
const root = new URL("../../itonami/profiles/", import.meta.url).pathname;
const out = process.argv[2] ?? "dist/profiles.json";

const bundle = {};
for (const name of readdirSync(root).sort()) {
  const dir = join(root, name);
  if (!statSync(dir).isDirectory()) continue;
  const files = {};
  for (const f of FILES) {
    const p = join(dir, f);
    if (!existsSync(p)) continue;
    const text = readFileSync(p, "utf8");
    if (SECRET.test(text)) throw new Error(`refusing to bundle ${name}/${f}: looks like a credential`);
    files[f] = text;
  }
  bundle[name] = files;
}
mkdirSync(new URL(".", `file://${process.cwd()}/${out}`).pathname, { recursive: true });
writeFileSync(out, JSON.stringify(bundle));
console.log(`bundled ${Object.keys(bundle).length} profiles -> ${out}`);
