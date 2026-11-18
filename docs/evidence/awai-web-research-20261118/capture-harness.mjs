// One-off evidence harness for AWAI-3164 (not product code, not a CI path).
// Runs Kumade's own Common Crawl search and bounded crawl in Node with an explicit
// approval list, logs every network request, and writes Kumade snapshot exports.
//
// node capture.mjs <kumade-checkout> <out-dir> <approve: none|index,data> <collection>
import { writeFile, mkdir } from "node:fs/promises";

const [kumade, out, approveArg, collection] = process.argv.slice(2);
const cc = await import(`${kumade}/src/commoncrawl.js`);
const { runCrawl } = await import(`${kumade}/src/crawl.js`);
const approved = new Set(approveArg === "none" ? [] : approveArg.split(","));
const requests = [];

const broker = {
  operations: [
    { ...cc.INDEX, endpoint: { origin: cc.INDEX_ORIGIN } },
    { ...cc.DATA, endpoint: { origin: cc.DATA_ORIGIN } },
  ],
  async invoke(selector, { input, init }) {
    const key = selector === cc.INDEX ? "index" : "data";
    if (!approved.has(key)) throw new Error(`${selector.service}/${selector.operation} not approved`);
    const url = new URL(input);
    for (let attempt = 1; attempt <= 6; attempt += 1) {
      const started = new Date().toISOString();
      const response = await fetch(url, { ...init, signal: AbortSignal.timeout(60000) }).catch((e) => ({ ok: false, status: `error:${e.name}` }));
      requests.push({ started, method: init.method, origin: url.origin, path: url.pathname, query: url.search, range: init.headers?.Range ?? null, status: response.status, attempt });
      if (response.status !== 504 && response.status !== 503 && !String(response.status).startsWith("error")) return response;
      await new Promise((r) => setTimeout(r, 5000 * attempt));
    }
    throw new Error(`${selector.service} kept failing`);
  },
};

await mkdir(out, { recursive: true });
const saved = [];
const save = async (s) => { saved.push(s.id); await writeFile(`${out}/${s.id.replace(":", "-")}.json`, JSON.stringify(s, null, 2) + "\n"); };
const result = { approved: [...approved], collection };

if (!process.env.SKIP_SEARCH) {
// 1. Search: look up captures of one URL in the Common Crawl index, fetch the first one.
try {
  const captures = await cc.lookupCaptures("https://example.com/", collection, broker, { limit: 3 });
  result.search = { target: "https://example.com/", captures: captures.length };
  if (captures[0]) { const s = await cc.fetchCapture(captures[0], broker); await save(s); result.search.snapshot = s.id; result.search.provenance = s.provenance; }
} catch (e) { result.search = { error: String(e.message) }; }

}
// 2. Bounded crawl (depth 1, 3 pages) whose pages come from Common Crawl captures.
const fetchPageDirect = async (url) => {
  const [capture] = await cc.lookupCaptures(url, collection, broker, { limit: 1 });
  if (!capture) throw new Error("no capture in this collection");
  const endpoint = new URL(`/${capture.filename}`, cc.DATA_ORIGIN);
  const response = await broker.invoke(cc.DATA, { input: endpoint, init: { method: "GET", headers: { Range: `bytes=${capture.offset}-${capture.offset + capture.length - 1}` } } });
  if (response.status !== 206) throw new Error(`data fetch ${response.status}`);
  const parsed = await cc.parseWarcResponse(new Uint8Array(await response.arrayBuffer()), capture);
  return { html: parsed.html, finalUrl: capture.url, robots: { checked: false, allowed: true }, blockDigestVerified: parsed.blockDigestVerified };
};
result.crawl = await runCrawl({ seedUrl: process.env.SEED ?? "https://info.cern.ch/", options: { maxDepth: 1, maxPages: 3 }, fetchPage: fetchPageDirect, save });
result.saved = saved;
result.requests = requests;
await writeFile(`${out}/../capture-result-${approveArg.replace(",", "-")}.json`, JSON.stringify(result, null, 2) + "\n");
console.log(JSON.stringify({ approved: result.approved, search: result.search?.captures ?? result.search, crawl: result.crawl.status, pages: result.crawl.pages.length, saved: saved.length, requests: requests.length }));
