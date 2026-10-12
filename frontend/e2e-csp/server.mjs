// A static server for `dist/` that sends the production Content-Security-Policy from docs/architecture.md section 15 (read from
// the document itself, so the check cannot drift from what is documented) and falls back to index.html for unknown paths
// outside /api, as the production reverse proxy does. It serves no API: the CSP spec mocks `/api` with page.route.
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { dirname, extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'dist');
const architecture = join(
  dirname(fileURLToPath(import.meta.url)),
  '..',
  '..',
  'docs',
  'architecture.md',
);
const port = Number(process.env.CSP_PORT ?? 4175);

const document = await readFile(architecture, 'utf8');
// The document also quotes the API's own header (`default-src 'none'`); the app's is the one that allows scripts of its own.
const policies = [...document.matchAll(/`Content-Security-Policy: ([^`]+)`/g)].map(([, policy]) =>
  (policy ?? '').replace(/\s+/g, ' ').trim(),
);
const csp = policies.find((policy) => policy.includes("script-src 'self'"));
if (!csp)
  throw new Error("No app Content-Security-Policy (script-src 'self') in docs/architecture.md");

const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.woff2': 'font/woff2',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.webmanifest': 'application/manifest+json',
};

createServer(async (request, response) => {
  const path = normalize(
    decodeURIComponent(new URL(request.url ?? '/', 'http://localhost').pathname),
  );
  const file = path === '/' || path.startsWith('/..') ? 'index.html' : path.slice(1);
  let body;
  let name = file;
  try {
    body = await readFile(join(root, file));
  } catch {
    name = 'index.html';
    body = await readFile(join(root, name));
  }
  response.writeHead(200, {
    'Content-Type': types[extname(name)] ?? 'application/octet-stream',
    'Content-Security-Policy': csp,
    'Referrer-Policy': 'no-referrer',
    'X-Content-Type-Options': 'nosniff',
    'Cache-Control': name === 'index.html' ? 'no-cache' : 'public, max-age=31536000, immutable',
  });
  response.end(body);
}).listen(port, () => {
  console.log(`dist/ with CSP on http://localhost:${String(port)}`);
});
